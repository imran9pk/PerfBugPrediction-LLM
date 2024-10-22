package org.sleuthkit.autopsy.experimental.autoingest;

import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.ingest.Snapshot;
import org.sleuthkit.autopsy.ingest.IngestJob;
import org.sleuthkit.autopsy.ingest.IngestManager.IngestThreadActivitySnapshot;
import org.sleuthkit.autopsy.ingest.IngestProgressSnapshotProvider;

@ThreadSafe
final class AutoIngestJob implements Comparable<AutoIngestJob>, IngestProgressSnapshotProvider, Serializable {

    private static final long serialVersionUID = 1L;
    private static final int CURRENT_VERSION = 4;
    private static final int DEFAULT_PRIORITY = 0;
    private static final String LOCAL_HOST_NAME = NetworkUtils.getLocalHostName();

    private final Manifest manifest;
    @GuardedBy("this")
    private String nodeName;
    @GuardedBy("this")
    private String caseDirectoryPath;
    @GuardedBy("this")
    private Integer priority;
    @GuardedBy("this")
    private Stage stage;
    @GuardedBy("this")
    private Date stageStartDate;
    @GuardedBy("this")
    transient private DataSourceProcessor dataSourceProcessor;
    @GuardedBy("this")
    transient private IngestJob ingestJob;
    @GuardedBy("this")
    transient private boolean cancelled;
    @GuardedBy("this")
    transient private boolean completed;
    @GuardedBy("this")
    private Date completedDate;
    @GuardedBy("this")
    private boolean errorsOccurred;

    private final int version; @GuardedBy("this")
    private ProcessingStatus processingStatus;
    @GuardedBy("this")
    private int numberOfCrashes;
    @GuardedBy("this")
    private StageDetails stageDetails;

    @GuardedBy("this")
    private long dataSourceSize;

    private List<IngestThreadActivitySnapshot> ingestThreadsSnapshot;
    private List<Snapshot> ingestJobsSnapshot;
    private Map<String, Long> moduleRunTimesSnapshot;
    
    private boolean ocrEnabled;

    AutoIngestJob(Manifest manifest) throws AutoIngestJobException {
        try {
            this.manifest = manifest;
            this.nodeName = "";
            this.caseDirectoryPath = "";
            this.priority = DEFAULT_PRIORITY;
            this.stage = Stage.PENDING;
            this.stageStartDate = manifest.getDateFileCreated();
            this.dataSourceProcessor = null;
            this.ingestJob = null;
            this.cancelled = false;
            this.completed = false;
            this.completedDate = new Date(0);
            this.errorsOccurred = false;

            this.version = CURRENT_VERSION;
            this.processingStatus = ProcessingStatus.PENDING;
            this.numberOfCrashes = 0;
            this.stageDetails = this.getProcessingStageDetails();

            this.dataSourceSize = 0;

            this.ingestThreadsSnapshot = Collections.emptyList();
            this.ingestJobsSnapshot = Collections.emptyList();
            this.moduleRunTimesSnapshot = Collections.emptyMap();
        } catch (Exception ex) {
            throw new AutoIngestJobException(String.format("Error creating automated ingest job"), ex);
        }
    }

    AutoIngestJob(AutoIngestJobNodeData nodeData) throws AutoIngestJobException {
        try {
            this.manifest = new Manifest(nodeData.getManifestFilePath(), nodeData.getManifestFileDate(), nodeData.getCaseName(), nodeData.getDeviceId(), nodeData.getDataSourcePath(), Collections.emptyMap());
            this.nodeName = nodeData.getProcessingHostName();
            this.caseDirectoryPath = nodeData.getCaseDirectoryPath().toString();
            this.priority = nodeData.getPriority();
            this.stage = nodeData.getProcessingStage();
            this.stageStartDate = nodeData.getProcessingStageStartDate();
            this.dataSourceProcessor = null; this.ingestJob = null; this.cancelled = false; this.completed = false; this.completedDate = nodeData.getCompletedDate();
            this.errorsOccurred = nodeData.getErrorsOccurred();

            this.version = CURRENT_VERSION;
            this.processingStatus = nodeData.getProcessingStatus();
            this.numberOfCrashes = nodeData.getNumberOfCrashes();
            this.stageDetails = this.getProcessingStageDetails();

            this.dataSourceSize = nodeData.getDataSourceSize();

            this.ingestThreadsSnapshot = Collections.emptyList();
            this.ingestJobsSnapshot = Collections.emptyList();
            this.moduleRunTimesSnapshot = Collections.emptyMap();
            
            this.ocrEnabled = nodeData.getOcrEnabled();
            
        } catch (Exception ex) {
            throw new AutoIngestJobException(String.format("Error creating automated ingest job"), ex);
        }
    }

    Manifest getManifest() {
        return this.manifest;
    }

    synchronized void setCaseDirectoryPath(Path caseDirectoryPath) {
        if (null != caseDirectoryPath) {
            this.caseDirectoryPath = caseDirectoryPath.toString();
        } else {
            this.caseDirectoryPath = "";
        }
    }

    synchronized Path getCaseDirectoryPath() {
        return Paths.get(caseDirectoryPath);
    }

    synchronized void setPriority(Integer priority) {
        this.priority = priority;
    }

    synchronized Integer getPriority() {
        return this.priority;
    }
    
    synchronized boolean getOcrEnabled() {
        return this.ocrEnabled;
    }

    synchronized void setOcrEnabled(boolean enabled) {
        this.ocrEnabled = enabled;
    }    

    synchronized void setProcessingStage(Stage newStage, Date stageStartDate) {
        if (Stage.CANCELLING == this.stage && Stage.COMPLETED != newStage) {
            return;
        }
        this.stage = newStage;
        this.stageStartDate = stageStartDate;
    }

    synchronized Stage getProcessingStage() {
        return this.stage;
    }

    synchronized Date getProcessingStageStartDate() {
        return new Date(this.stageStartDate.getTime());
    }

    synchronized StageDetails getProcessingStageDetails() {
        String description;
        Date startDate;
        if (Stage.CANCELLING != this.stage && null != this.ingestJob) {
            IngestJob.ProgressSnapshot progress = this.ingestJob.getSnapshot();
            IngestJob.DataSourceIngestModuleHandle ingestModuleHandle = progress.runningDataSourceIngestModule();
            if (null != ingestModuleHandle) {
                startDate = ingestModuleHandle.startTime();
                if (!ingestModuleHandle.isCancelled()) {
                    description = ingestModuleHandle.displayName();
                } else {
                    description = String.format(Stage.CANCELLING_MODULE.getDisplayText(), ingestModuleHandle.displayName());
                }
            } else {
                description = Stage.ANALYZING_FILES.getDisplayText();
                startDate = progress.fileIngestStartTime();
            }
        } else {
            description = this.stage.getDisplayText();
            startDate = this.stageStartDate;
        }
        this.stageDetails = new StageDetails(description, startDate);
        return this.stageDetails;
    }

    synchronized void setDataSourceProcessor(DataSourceProcessor dataSourceProcessor) {
        this.dataSourceProcessor = dataSourceProcessor;
    }

    synchronized void setIngestJob(IngestJob ingestJob) {
        this.ingestJob = ingestJob;
    }

    synchronized IngestJob getIngestJob() {
        return this.ingestJob;
    }

    synchronized void setIngestThreadSnapshot(List<IngestThreadActivitySnapshot> snapshot) {
        this.ingestThreadsSnapshot = snapshot;
    }

    synchronized void setIngestJobsSnapshot(List<Snapshot> snapshot) {
        this.ingestJobsSnapshot = snapshot;
    }

    synchronized void setModuleRuntimesSnapshot(Map<String, Long> snapshot) {
        this.moduleRunTimesSnapshot = snapshot;
    }

    synchronized void cancel() {
        setProcessingStage(Stage.CANCELLING, Date.from(Instant.now()));
        cancelled = true;
        errorsOccurred = true;
        if (null != dataSourceProcessor) {
            dataSourceProcessor.cancel();
        }
        if (null != ingestJob) {
            ingestJob.cancel(IngestJob.CancellationReason.USER_CANCELLED);
        }
    }

    synchronized boolean isCanceled() {
        return cancelled;
    }

    synchronized void setCompleted() {
        setProcessingStage(Stage.COMPLETED, Date.from(Instant.now()));
        completed = true;
    }

    synchronized boolean isCompleted() {
        return completed;
    }

    synchronized void setCompletedDate(Date completedDate) {
        this.completedDate = new Date(completedDate.getTime());
    }

    synchronized Date getCompletedDate() {
        return new Date(completedDate.getTime());
    }

    synchronized void setErrorsOccurred(boolean errorsOccurred) {
        this.errorsOccurred = errorsOccurred;
    }

    synchronized boolean getErrorsOccurred() {
        return this.errorsOccurred;
    }

    synchronized String getProcessingHostName() {
        return nodeName;
    }

    synchronized void setProcessingHostName(String processingHostName) {
        this.nodeName = processingHostName;
    }

    synchronized ProcessingStatus getProcessingStatus() {
        return this.processingStatus;
    }

    synchronized void setProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    synchronized int getNumberOfCrashes() {
        return this.numberOfCrashes;
    }

    synchronized void setNumberOfCrashes(int numberOfCrashes) {
        this.numberOfCrashes = numberOfCrashes;
    }

    synchronized long getDataSourceSize() {
        return dataSourceSize;
    }

    synchronized void setDataSourceSize(long dataSourceSize) {
        this.dataSourceSize = dataSourceSize;
    }

    @Override
    public boolean equals(Object otherJob) {
        if (!(otherJob instanceof AutoIngestJob)) {
            return false;
        }
        if (otherJob == this) {
            return true;
        }
        return this.getManifest().getFilePath().equals(((AutoIngestJob) otherJob).getManifest().getFilePath());
    }

    @Override
    public int hashCode() {
        int hash = 71 * (Objects.hashCode(this.getManifest().getFilePath()));
        return hash;
    }

    @Override
    public int compareTo(AutoIngestJob otherJob) {
        int comparisonResult = -(this.getPriority().compareTo(otherJob.getPriority()));
        if (comparisonResult == 0) {
            comparisonResult = this.getManifest().getDateFileCreated().compareTo(otherJob.getManifest().getDateFileCreated());
            if (comparisonResult == 0) {
                comparisonResult = -this.getManifest().getCaseName().compareTo(otherJob.getManifest().getCaseName());
                if (comparisonResult == 0) {
                    comparisonResult = -this.getManifest().getDataSourcePath().getFileName().toString().compareTo(otherJob.getManifest().getDataSourcePath().getFileName().toString());
                    }
            }
        }
        return comparisonResult;
    }

    @Override
    public List<IngestThreadActivitySnapshot> getIngestThreadActivitySnapshots() {
        return this.ingestThreadsSnapshot;
    }

    @Override
    public List<Snapshot> getIngestJobSnapshots() {
        return this.ingestJobsSnapshot;
    }

    @Override
    public Map<String, Long> getModuleRunTimes() {
        return this.moduleRunTimesSnapshot;
    }

    static class LocalHostAndCaseComparator implements Comparator<AutoIngestJob> {

        @Override
        public int compare(AutoIngestJob aJob, AutoIngestJob anotherJob) {
            if (aJob.getProcessingHostName().equalsIgnoreCase(LOCAL_HOST_NAME)) {
                return -1;
            } else if (anotherJob.getProcessingHostName().equalsIgnoreCase(LOCAL_HOST_NAME)) {
                return 1;
            } else {
                return aJob.getManifest().getCaseName().compareToIgnoreCase(anotherJob.getManifest().getCaseName());
            }
        }

    }

    enum ProcessingStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        DELETED 

    enum Stage {

        PENDING("Pending"),
        STARTING("Starting"),
        UPDATING_SHARED_CONFIG("Updating shared configuration"),
        CHECKING_SERVICES("Checking services"),
        OPENING_CASE("Opening case"),
        IDENTIFYING_DATA_SOURCE("Identifying data source type"),
        ADDING_DATA_SOURCE("Adding data source"),
        ANALYZING_DATA_SOURCE("Analyzing data source"),
        ANALYZING_FILES("Analyzing files"),
        EXPORTING_FILES("Exporting files"),
        CANCELLING_MODULE("Cancelling module"),
        CANCELLING("Cancelling"),
        COMPLETED("Completed");

        private final String displayText;

        private Stage(String displayText) {
            this.displayText = displayText;
        }

        String getDisplayText() {
            return displayText;
        }

    }

    @Immutable
    static final class StageDetails implements Serializable {

        private static final long serialVersionUID = 1L;
        private final String description;
        private final Date startDate;

        StageDetails(String description, Date startDate) {
            this.description = description;
            this.startDate = startDate;
        }

        String getDescription() {
            return this.description;
        }

        Date getStartDate() {
            return new Date(this.startDate.getTime());
        }

    }

    final static class AutoIngestJobException extends Exception {

        private static final long serialVersionUID = 1L;

        private AutoIngestJobException(String message) {
            super(message);
        }

        private AutoIngestJobException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
