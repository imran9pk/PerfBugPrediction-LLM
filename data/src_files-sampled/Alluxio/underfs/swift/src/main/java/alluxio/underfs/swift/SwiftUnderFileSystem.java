package alluxio.underfs.swift;

import alluxio.AlluxioURI;
import alluxio.Constants;
import alluxio.conf.PropertyKey;
import alluxio.exception.ExceptionMessage;
import alluxio.exception.FileDoesNotExistException;
import alluxio.retry.RetryPolicy;
import alluxio.underfs.ObjectUnderFileSystem;
import alluxio.underfs.UnderFileSystem;
import alluxio.underfs.UnderFileSystemConfiguration;
import alluxio.underfs.options.OpenOptions;
import alluxio.underfs.swift.http.SwiftDirectClient;
import alluxio.util.UnderFileSystemUtils;
import alluxio.util.io.PathUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.javaswift.joss.client.factory.AccountConfig;
import org.javaswift.joss.client.factory.AccountFactory;
import org.javaswift.joss.client.factory.AuthenticationMethod;
import org.javaswift.joss.exception.CommandException;
import org.javaswift.joss.model.Access;
import org.javaswift.joss.model.Account;
import org.javaswift.joss.model.Container;
import org.javaswift.joss.model.DirectoryOrObject;
import org.javaswift.joss.model.PaginationMap;
import org.javaswift.joss.model.StoredObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class SwiftUnderFileSystem extends ObjectUnderFileSystem {
  private static final Logger LOG = LoggerFactory.getLogger(SwiftUnderFileSystem.class);

  private static final String ACL_SEPARATOR_REGEXP = "\\s*,\\s*";

  private static final String FOLDER_SUFFIX = PATH_SEPARATOR;

  private static final int NUM_RETRIES = 3;

  private final Account mAccount;

  private final String mContainerName;

  private final Access mAccess;

  private boolean mSimulationMode;

  private String mAccountOwner;

  private short mAccountMode;

  public SwiftUnderFileSystem(AlluxioURI uri, UnderFileSystemConfiguration conf)
      throws FileDoesNotExistException {
    super(uri, conf);
    String containerName = UnderFileSystemUtils.getBucketName(uri);
    LOG.debug("Constructor init: {}", containerName);
    AccountConfig config = new AccountConfig();

    mSimulationMode = false;
    if (conf.isSet(PropertyKey.SWIFT_SIMULATION)) {
      mSimulationMode = Boolean.valueOf(conf.get(PropertyKey.SWIFT_SIMULATION));
    }

    if (mSimulationMode) {
      config.setMock(true);
      config.setMockAllowEveryone(true);
    } else {
      if (conf.isSet(PropertyKey.SWIFT_PASSWORD_KEY)) {
        config.setPassword(conf.get(PropertyKey.SWIFT_PASSWORD_KEY));
      }
      config.setAuthUrl(conf.get(PropertyKey.SWIFT_AUTH_URL_KEY));
      String authMethod = conf.get(PropertyKey.SWIFT_AUTH_METHOD_KEY);
      if (authMethod != null) {
        config.setUsername(conf.get(PropertyKey.SWIFT_USER_KEY));
        config.setTenantName(conf.get(PropertyKey.SWIFT_TENANT_KEY));
        switch (authMethod) {
          case Constants.SWIFT_AUTH_KEYSTONE:
            config.setAuthenticationMethod(AuthenticationMethod.KEYSTONE);
            if (conf.isSet(PropertyKey.SWIFT_REGION_KEY)) {
              config.setPreferredRegion(conf.get(PropertyKey.SWIFT_REGION_KEY));
            }
            break;
          case Constants.SWIFT_AUTH_KEYSTONE_V3:
            if (conf.isSet(PropertyKey.SWIFT_REGION_KEY)) {
              config.setPreferredRegion(conf.get(PropertyKey.SWIFT_REGION_KEY));
            }
            config.setAuthenticationMethod(AuthenticationMethod.EXTERNAL);
            KeystoneV3AccessProvider accessProvider = new KeystoneV3AccessProvider(config);
            config.setAccessProvider(accessProvider);
            break;
          case Constants.SWIFT_AUTH_SWIFTAUTH:
            config.setAuthenticationMethod(AuthenticationMethod.BASIC);
            config.setTenantName(conf.get(PropertyKey.SWIFT_USER_KEY));
            config.setUsername(conf.get(PropertyKey.SWIFT_TENANT_KEY));
            break;
          default:
            config.setAuthenticationMethod(AuthenticationMethod.TEMPAUTH);
            config.setTenantName(conf.get(PropertyKey.SWIFT_USER_KEY));
            config.setUsername(conf.get(PropertyKey.SWIFT_TENANT_KEY));
        }
      }
    }

    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, true);
    mContainerName = containerName;
    mAccount = new AccountFactory(config).createAccount();
    mAccount.setAllowContainerCaching(false);
    mAccess = mAccount.authenticate();
    Container container = mAccount.getContainer(containerName);
    if (!container.exists()) {
      throw new FileDoesNotExistException(ExceptionMessage.PATH_DOES_NOT_EXIST
          .getMessage("Container %s does not exist", containerName));
    }

    mAccountOwner = conf.get(PropertyKey.SWIFT_USER_KEY);
    short mode = (short) 0;
    List<String> readAcl =
        Arrays.asList(container.getContainerReadPermission().split(ACL_SEPARATOR_REGEXP));
    if (readAcl.contains(mAccountOwner) || readAcl.contains("*") || readAcl.contains(".r:*")) {
      mode |= (short) 0500;
    }
    List<String> writeAcl =
        Arrays.asList(container.getcontainerWritePermission().split(ACL_SEPARATOR_REGEXP));
    if (writeAcl.contains(mAccountOwner) || writeAcl.contains("*") || writeAcl.contains(".w:*")) {
      mode |= (short) 0200;
    }
    if (mode == 0 && mAccess.getToken() != null) {
      mode = (short) 0700;
    }
    mAccountMode = mode;
  }

  @Override
  public String getUnderFSType() {
    return "swift";
  }

  @Override
  public void setOwner(String path, String user, String group) {}

  @Override
  public void setMode(String path, short mode) throws IOException {}

  @Override
  protected boolean copyObject(String source, String destination) {
    LOG.debug("copy from {} to {}", source, destination);
    for (int i = 0; i < NUM_RETRIES; i++) {
      try {
        Container container = mAccount.getContainer(mContainerName);
        container.getObject(source).copyObject(container, container.getObject(destination));
        return true;
      } catch (CommandException e) {
        LOG.error("Source path {} does not exist", source);
        return false;
      } catch (Exception e) {
        LOG.error("Failed to copy file {} to {}", source, destination, e);
        if (i != NUM_RETRIES - 1) {
          LOG.error("Retrying copying file {} to {}", source, destination);
        }
      }
    }
    LOG.error("Failed to copy file {} to {}, after {} retries", source, destination, NUM_RETRIES);
    return false;
  }

  @Override
  public boolean createEmptyObject(String key) {
    try {
      Container container = mAccount.getContainer(mContainerName);
      StoredObject object = container.getObject(key);
      object.uploadObject(new byte[0]);
      return true;
    } catch (CommandException e) {
      LOG.error("Failed to create object: {}", key, e);
      return false;
    }
  }

  @Override
  protected OutputStream createObject(String key) throws IOException {
    if (mSimulationMode) {
      return new SwiftMockOutputStream(mAccount, mContainerName, key,
          mUfsConf.getList(PropertyKey.TMP_DIRS, ","));
    }

    return SwiftDirectClient.put(mAccess,
        PathUtils.concatPath(PathUtils.normalizePath(mContainerName, PATH_SEPARATOR), key));
  }

  @Override
  protected boolean deleteObject(String path) throws IOException {
    try {
      Container container = mAccount.getContainer(mContainerName);
      StoredObject object = container.getObject(path);
      if (object != null) {
        object.delete();
        return true;
      }
    } catch (CommandException e) {
      LOG.debug("Object {} not found", path);
    }
    return false;
  }

  @Override
  protected String getFolderSuffix() {
    return FOLDER_SUFFIX;
  }

  @Override
  protected ObjectListingChunk getObjectListingChunk(String key, boolean recursive)
      throws IOException {
    Container container = mAccount.getContainer(mContainerName);
    String prefix = PathUtils.normalizePath(key, PATH_SEPARATOR);
    prefix = prefix.equals(PATH_SEPARATOR) ? "" : prefix;
    PaginationMap paginationMap = container.getPaginationMap(prefix,
        getListingChunkLength(mUfsConf));
    if (paginationMap != null && paginationMap.getNumberOfPages() > 0) {
      return new SwiftObjectListingChunk(paginationMap, 0, recursive);
    }
    return null;
  }

  private final class SwiftObjectListingChunk implements ObjectListingChunk {
    final PaginationMap mPaginationMap;
    final int mPage;
    final boolean mRecursive;

    SwiftObjectListingChunk(PaginationMap paginationMap, int page, boolean recursive) {
      mPaginationMap = paginationMap;
      mPage = page;
      mRecursive = recursive;
    }

    @Override
    public ObjectStatus[] getObjectStatuses() {
      ArrayDeque<DirectoryOrObject> objects = new ArrayDeque<>();
      Container container = mAccount.getContainer(mContainerName);
      if (!mRecursive) {
        objects.addAll(container.listDirectory(mPaginationMap.getPrefix(), PATH_SEPARATOR_CHAR,
            mPaginationMap.getMarker(mPage), mPaginationMap.getPageSize()));
      } else {
        objects.addAll(container.list(mPaginationMap, mPage));
      }
      int i = 0;
      ObjectStatus[] res = new ObjectStatus[objects.size()];
      for (DirectoryOrObject object : objects) {
        if (object.isObject()) {
          res[i++] = new ObjectStatus(object.getName(), object.getAsObject().getEtag(),
              object.getAsObject().getContentLength(),
              object.getAsObject().getLastModifiedAsDate().getTime());
        } else {
          res[i++] = new ObjectStatus(object.getName());
        }
      }
      return res;
    }

    @Override
    public String[] getCommonPrefixes() {
      return new String[0];
    }

    @Override
    public ObjectListingChunk getNextChunk() throws IOException {
      int nextPage = mPage + 1;
      if (nextPage >= mPaginationMap.getNumberOfPages()) {
        return null;
      }
      return new SwiftObjectListingChunk(mPaginationMap, nextPage, mRecursive);
    }
  }

  @Override
  protected ObjectStatus getObjectStatus(String key) {
    Container container = mAccount.getContainer(mContainerName);
    StoredObject meta = container.getObject(key);
    if (meta != null && meta.exists()) {
      return new ObjectStatus(key, meta.getEtag(), meta.getContentLength(),
          meta.getLastModifiedAsDate().getTime());
    }
    return null;
  }

  @Override
  protected ObjectPermissions getPermissions() {
    return new ObjectPermissions(mAccountOwner, mAccountOwner, mAccountMode);
  }

  @Override
  protected String getRootKey() {
    return Constants.HEADER_SWIFT + mContainerName + PATH_SEPARATOR;
  }

  @Override
  protected InputStream openObject(String key, OpenOptions options, RetryPolicy retryPolicy)
      throws IOException {
    return new SwiftInputStream(mAccount, mContainerName, key, options.getOffset(), retryPolicy,
        mUfsConf.getBytes(PropertyKey.UNDERFS_OBJECT_STORE_MULTI_RANGE_CHUNK_SIZE));
  }
}
