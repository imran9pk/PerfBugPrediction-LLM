package org.dcache.pool.json;

import diskCacheV111.pools.json.PoolCostData;
import diskCacheV111.pools.json.PoolQueueData;
import dmg.cells.nucleus.CellInfo;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.InetAddress;
import java.util.Map;

public class PoolDataDetails implements Serializable {

    private static final long serialVersionUID = -3630909338100368554L;

    public enum OnOff {
        ON, OFF
    }

    public enum Lsf {
        NONE, VOLATILE, PRECIOUS
    }

    public enum P2PMode {
        CACHED, PRECIOUS
    }

    private String label;
    private InetAddress[] inetAddresses;
    private String baseDir;
    private String poolVersion;

    @Deprecated private OnOff reportRemovals;
    private boolean isRemovalReported;

    private String poolMode;
    private Integer poolStatusCode;
    private String poolStatusMessage;

    @Deprecated private OnOff suppressHsmLoad;
    private boolean isHsmLoadSuppressed;

    private Integer pingHeartbeatInSecs;
    private Double breakEven;
    private Lsf largeFileStore;

    private P2PMode p2pFileMode;
    private Integer hybridInventory;

    private int errorCode;
    private String errorMessage;
    private Map<String, String> tagMap;
    private PoolCostData costData;

    public String getBaseDir() {
        return baseDir;
    }

    public Double getBreakEven() {
        return breakEven;
    }

    public PoolCostData getCostData() {
        return costData;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Integer getHybridInventory() {
        return hybridInventory;
    }

    public InetAddress[] getInetAddresses() {
        return inetAddresses;
    }

    public String getLabel() {
        return label;
    }

    public Lsf getLargeFileStore() {
        return largeFileStore;
    }

    public P2PMode getP2pFileMode() {
        return p2pFileMode;
    }

    public Integer getPingHeartbeatInSecs() {
        return pingHeartbeatInSecs;
    }

    public String getPoolMode() {
        return poolMode;
    }

    public Integer getPoolStatusCode() {
        return poolStatusCode;
    }

    public String getPoolStatusMessage() {
        return poolStatusMessage;
    }

    public String getPoolVersion() {
        return poolVersion;
    }

    public boolean isRemovalReported() {
        return isRemovalReported;
    }

    public boolean isHsmLoadSuppressed() {
        return isHsmLoadSuppressed;
    }

    public Map<String, String> getTagMap() {
        return tagMap;
    }

    private String asOnOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    public void print(PrintWriter pw) {
        pw.println("Base directory    : " + baseDir);
        pw.println("Version           : " + poolVersion);
        pw.println("Report remove     : " + asOnOff(isRemovalReported));
        pw.println("Pool Mode         : " + poolMode);
        if (poolStatusCode != null) {
            pw.println("Detail            : [" + poolStatusCode + "] "
                  + poolStatusMessage);
        }
        pw.println("Hsm Load Suppr.   : " + asOnOff(isHsmLoadSuppressed));
        pw.println("Ping Heartbeat    : " + pingHeartbeatInSecs + " seconds");
        pw.println("Breakeven         : " + breakEven);
        pw.println("LargeFileStore    : " + largeFileStore);
        pw.println("P2P File Mode     : " + p2pFileMode);

        if (hybridInventory != null) {
            pw.println("Inventory         : " + hybridInventory);
        }

        if (costData != null) {
            Map<String, PoolQueueData> movers = costData.getExtendedMoverHash();
            if (movers != null) {
                movers.values().stream()
                      .forEach((q) -> pw.println(
                            "Mover Queue (" + q.getName() + ") "
                                  + q.getActive()
                                  + "(" + q.getMaxActive()
                                  + ")/" + q.getQueued()));
            }
        }
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public void setBreakEven(Double breakEven) {
        this.breakEven = breakEven;
    }

    public void setCostData(PoolCostData costData) {
        this.costData = costData;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setHybridInventory(Integer hybridInventory) {
        this.hybridInventory = hybridInventory;
    }

    public void setInetAddresses(InetAddress[] inetAddresses) {
        this.inetAddresses = inetAddresses;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setLargeFileStore(
          Lsf largeFileStore) {
        this.largeFileStore = largeFileStore;
    }

    public void setP2pFileMode(
          P2PMode p2pFileMode) {
        this.p2pFileMode = p2pFileMode;
    }

    public void setPingHeartbeatInSecs(Integer pingHeartbeatInSecs) {
        this.pingHeartbeatInSecs = pingHeartbeatInSecs;
    }

    public void setPoolMode(String poolMode) {
        this.poolMode = poolMode;
    }

    public void setPoolStatusCode(Integer poolStatusCode) {
        this.poolStatusCode = poolStatusCode;
    }

    public void setPoolStatusMessage(String poolStatusMessage) {
        this.poolStatusMessage = poolStatusMessage;
    }

    public void setPoolVersion(String poolVersion) {
        this.poolVersion = poolVersion;
    }

    public void setRemovalReported(boolean isReported) {
        isRemovalReported = isReported;
    }

    public void setHsmLoadSuppressed(boolean isSuppressed) {
        isHsmLoadSuppressed = isSuppressed;
    }

    public void setTagMap(Map<String, String> tagMap) {
        this.tagMap = tagMap;
    }

    private void readObject(ObjectInputStream aInputStream)
          throws ClassNotFoundException, IOException {
        aInputStream.defaultReadObject();
        if (reportRemovals != null) {
            isRemovalReported = reportRemovals == OnOff.ON;
        }
        if (suppressHsmLoad != null) {
            isHsmLoadSuppressed = suppressHsmLoad == OnOff.ON;
        }
    }
}
