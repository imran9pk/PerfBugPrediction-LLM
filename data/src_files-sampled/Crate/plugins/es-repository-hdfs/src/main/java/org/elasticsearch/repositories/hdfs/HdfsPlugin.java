package org.elasticsearch.repositories.hdfs;

import io.crate.analyze.repositories.TypeSettings;
import io.crate.common.SuppressForbidden;
import io.crate.sql.tree.GenericProperties;
import io.crate.sql.tree.GenericProperty;

import org.apache.hadoop.hdfs.protocolPB.ClientNamenodeProtocolPB;
import org.apache.hadoop.security.KerberosInfo;
import org.apache.hadoop.security.SecurityUtil;
import org.elasticsearch.cluster.metadata.RepositoryMetadata;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.env.Environment;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.RepositoryPlugin;
import org.elasticsearch.repositories.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Map;

public final class HdfsPlugin extends Plugin implements RepositoryPlugin {

    static {
        AccessController.doPrivileged((PrivilegedAction<Void>) HdfsPlugin::evilHadoopInit);
        AccessController.doPrivileged((PrivilegedAction<Void>) HdfsPlugin::eagerInit);
    }

    @SuppressForbidden(reason = "Needs a security hack for hadoop on windows, until HADOOP-XXXX is fixed")
    private static Void evilHadoopInit() {
        Path hadoopHome = null;
        String oldValue = null;
        try {
            hadoopHome = Files.createTempDirectory("hadoop").toAbsolutePath();
            oldValue = System.setProperty("hadoop.home.dir", hadoopHome.toString());
            Class.forName("org.apache.hadoop.security.UserGroupInformation");
            Class.forName("org.apache.hadoop.util.StringUtils");
            Class.forName("org.apache.hadoop.util.ShutdownHookManager");
            Class.forName("org.apache.hadoop.conf.Configuration");
        } catch (ClassNotFoundException | IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (oldValue == null) {
                System.clearProperty("hadoop.home.dir");
            } else {
                System.setProperty("hadoop.home.dir", oldValue);
            }
            try {
                if (hadoopHome != null) {
                    Files.delete(hadoopHome);
                }
            } catch (IOException thisIsBestEffort) {
                }
        }
        return null;
    }

    private static Void eagerInit() {
        ClassLoader oldCCL = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(HdfsRepository.class.getClassLoader());
            KerberosInfo info = SecurityUtil.getKerberosInfo(ClientNamenodeProtocolPB.class, null);
            if (info == null) {
                throw new RuntimeException("Could not initialize SecurityUtil: " +
                    "Unable to find services for [org.apache.hadoop.security.SecurityInfo]");
            }
        } finally {
            Thread.currentThread().setContextClassLoader(oldCCL);
        }
        return null;
    }

    @Override
    public Map<String, Repository.Factory> getRepositories(Environment env,
                                                           NamedXContentRegistry namedXContentRegistry,
                                                           ClusterService clusterService) {
        return Collections.singletonMap(
            "hdfs",
            new Repository.Factory() {

                @Override
                public TypeSettings settings() {
                    Map<String, Setting<?>> optionalSettings = Map.ofEntries(
                        Map.entry("uri", Setting.simpleString("uri", Setting.Property.NodeScope)),
                        Map.entry("security.principal", Setting.simpleString("security.principal", Setting.Property.NodeScope)),
                        Map.entry("path", Setting.simpleString("path", Setting.Property.NodeScope)),
                        Map.entry("load_defaults", Setting.boolSetting("load_defaults", true, Setting.Property.NodeScope)),
                        Map.entry("compress", Setting.boolSetting("compress", true, Setting.Property.NodeScope)),
                        Map.entry("chunk_size", Setting.simpleString("chunk_size"))
                    );
                    return new TypeSettings(Map.of(), optionalSettings) {

                        @Override
                        public GenericProperties<?> dynamicProperties(GenericProperties<?> genericProperties) {
                            if (genericProperties.isEmpty()) {
                                return genericProperties;
                            }
                            GenericProperties<?> dynamicProperties = new GenericProperties<>();
                            for (Map.Entry<String, ?> entry : genericProperties.properties().entrySet()) {
                                String key = entry.getKey();
                                if (key.startsWith("conf.")) {
                                    dynamicProperties.add(new GenericProperty(key, entry.getValue()));
                                }
                            }
                            return dynamicProperties;
                        }
                    };
                }

                @Override
                public Repository create(RepositoryMetadata metadata) throws Exception {
                    return new HdfsRepository(metadata, env, namedXContentRegistry, clusterService);
                }
            }
        );
    }
}
