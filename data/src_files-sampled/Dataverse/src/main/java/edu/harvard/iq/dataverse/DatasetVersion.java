package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.util.MarkupChecker;
import edu.harvard.iq.dataverse.DatasetFieldType.FieldType;
import edu.harvard.iq.dataverse.branding.BrandingUtil;
import edu.harvard.iq.dataverse.util.FileUtil;
import edu.harvard.iq.dataverse.util.StringUtil;
import edu.harvard.iq.dataverse.util.SystemConfig;
import edu.harvard.iq.dataverse.util.DateUtil;
import edu.harvard.iq.dataverse.util.json.NullSafeJsonBuilder;
import edu.harvard.iq.dataverse.workflows.WorkflowComment;
import java.io.Serializable;
import java.net.URL;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.validation.constraints.Size;
import org.apache.commons.lang3.StringUtils;

@Entity
@Table(indexes = {@Index(columnList="dataset_id")},
        uniqueConstraints = @UniqueConstraint(columnNames = {"dataset_id,versionnumber,minorversionnumber"}))
@ValidateVersionNote(versionNote = "versionNote", versionState = "versionState")
public class DatasetVersion implements Serializable {

    private static final Logger logger = Logger.getLogger(DatasetVersion.class.getCanonicalName());

    public static final Comparator<DatasetVersion> compareByVersion = new Comparator<DatasetVersion>() {
        @Override
        public int compare(DatasetVersion o1, DatasetVersion o2) {
            if ( o1.isDraft() ) {
                return o2.isDraft() ? 0 : 1;
            } else {
               return (int)Math.signum( (o1.getVersionNumber().equals(o2.getVersionNumber())) ?
                        o1.getMinorVersionNumber() - o2.getMinorVersionNumber()
                       : o1.getVersionNumber() - o2.getVersionNumber() );
            }
        }
    };

    public enum VersionState {
        DRAFT, RELEASED, ARCHIVED, DEACCESSIONED
    };

    public enum License {
        NONE, CC0
    }

    public static final int ARCHIVE_NOTE_MAX_LENGTH = 1000;
    public static final int VERSION_NOTE_MAX_LENGTH = 1000;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String UNF;

    @Version
    private Long version;

    private Long versionNumber;
    private Long minorVersionNumber;
    
    @Size(min=0, max=VERSION_NOTE_MAX_LENGTH)
    @Column(length = VERSION_NOTE_MAX_LENGTH)
    private String versionNote;
    
    @Enumerated(EnumType.STRING)
    private VersionState versionState;

    @ManyToOne
    private Dataset dataset;

    @OneToMany(mappedBy = "datasetVersion", cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.PERSIST})
    @OrderBy("label") private List<FileMetadata> fileMetadatas = new ArrayList();
    
    @OneToOne(cascade = {CascadeType.MERGE, CascadeType.PERSIST, CascadeType.REMOVE}, orphanRemoval=true)
    @JoinColumn(name = "termsOfUseAndAccess_id")
    private TermsOfUseAndAccess termsOfUseAndAccess;
    
    @OneToMany(mappedBy = "datasetVersion", orphanRemoval = true, cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.PERSIST})
    private List<DatasetField> datasetFields = new ArrayList();
    
    @Temporal(value = TemporalType.TIMESTAMP)
    @Column( nullable=false )
    private Date createTime;
    
    @Temporal(value = TemporalType.TIMESTAMP)
    @Column( nullable=false )
    private Date lastUpdateTime;
    
    @Temporal(value = TemporalType.TIMESTAMP)
    private Date releaseTime;
    
    @Temporal(value = TemporalType.TIMESTAMP)
    private Date archiveTime;
    
    @Size(min=0, max=ARCHIVE_NOTE_MAX_LENGTH)
    @Column(length = ARCHIVE_NOTE_MAX_LENGTH)
    private String archiveNote;
    
    @Column(nullable=true, columnDefinition = "TEXT")
    private String archivalCopyLocation;
    
    
    private String deaccessionLink;

    @Transient
    private String contributorNames;
    
    @Transient 
    private String jsonLd;

    @OneToMany(mappedBy="datasetVersion", cascade={CascadeType.REMOVE, CascadeType.MERGE, CascadeType.PERSIST})
    private List<DatasetVersionUser> datasetVersionUsers;
    
    @OneToMany(mappedBy = "datasetVersion", cascade={CascadeType.REMOVE, CascadeType.MERGE, CascadeType.PERSIST})
    private List<WorkflowComment> workflowComments;

    @Column(nullable=true)
    private String externalStatusLabel;
    
    @Transient
    private DatasetVersionDifference dvd;
    
    
    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUNF() {
        return UNF;
    }

    public void setUNF(String UNF) {
        this.UNF = UNF;
    }

    public Long getVersion() {
        return this.version;
    }

    public void setVersion(Long version) {
    }
    
    public List<FileMetadata> getFileMetadatas() {
        return fileMetadatas;
    }
    
    public List<FileMetadata> getFileMetadatasSorted() {
        Collections.sort(fileMetadatas, FileMetadata.compareByLabel);
        return fileMetadatas;
    }
    
    public List<FileMetadata> getFileMetadatasSortedByLabelAndFolder() {
        ArrayList<FileMetadata> fileMetadatasCopy = new ArrayList<>();
        fileMetadatasCopy.addAll(fileMetadatas);
        Collections.sort(fileMetadatasCopy, FileMetadata.compareByLabelAndFolder);
        return fileMetadatasCopy;
    }
    
    public List<FileMetadata> getFileMetadatasFolderListing(String folderName) {
        ArrayList<FileMetadata> fileMetadatasCopy = new ArrayList<>();
        HashSet<String> subFolders = new HashSet<>();

        for (FileMetadata fileMetadata : fileMetadatas) {
            String thisFolder = fileMetadata.getDirectoryLabel() == null ? "" : fileMetadata.getDirectoryLabel(); 
            
            if (folderName.equals(thisFolder)) {
                fileMetadatasCopy.add(fileMetadata);
            } else if (thisFolder.startsWith(folderName)) {
                String subFolder = "".equals(folderName) ? thisFolder : thisFolder.substring(folderName.length() + 1);
                if (subFolder.indexOf('/') > 0) {
                    subFolder = subFolder.substring(0, subFolder.indexOf('/'));
                }
                
                if (!subFolders.contains(subFolder)) {
                    fileMetadatasCopy.add(fileMetadata);
                    subFolders.add(subFolder);
                }
                
            }
        }
        Collections.sort(fileMetadatasCopy, FileMetadata.compareByFullPath);
                
        return fileMetadatasCopy; 
    }

    public void setFileMetadatas(List<FileMetadata> fileMetadatas) {
        this.fileMetadatas = fileMetadatas;
    }
    
    public TermsOfUseAndAccess getTermsOfUseAndAccess() {
        return termsOfUseAndAccess;
    }

    public void setTermsOfUseAndAccess(TermsOfUseAndAccess termsOfUseAndAccess) {
        this.termsOfUseAndAccess = termsOfUseAndAccess;
    }

    public List<DatasetField> getDatasetFields() {
        return datasetFields;
    }

    public void setDatasetFields(List<DatasetField> datasetFields) {
        for ( DatasetField dsf : datasetFields ) {
            dsf.setDatasetVersion(this);
        }
        this.datasetFields = datasetFields;
    }
    
    public boolean isInReview() {
        if (versionState != null && versionState.equals(VersionState.DRAFT)) {
            return getDataset().isLockedFor(DatasetLock.Reason.InReview);
        } else {
            return false;
        }
    }

    public Date getArchiveTime() {
        return archiveTime;
    }

    public void setArchiveTime(Date archiveTime) {
        this.archiveTime = archiveTime;
    }

    public String getArchiveNote() {
        return archiveNote;
    }

    public void setArchiveNote(String note) {
        if (note != null && note.length() > ARCHIVE_NOTE_MAX_LENGTH) {
            throw new IllegalArgumentException("Error setting archiveNote: String length is greater than maximum (" + ARCHIVE_NOTE_MAX_LENGTH + ")."
                    + "  StudyVersion id=" + id + ", archiveNote=" + note);
        }
        this.archiveNote = note;
    }
    
    public String getArchivalCopyLocation() {
        return archivalCopyLocation;
    }

    public void setArchivalCopyLocation(String location) {
        this.archivalCopyLocation = location;
    }

    public String getDeaccessionLink() {
        return deaccessionLink;
    }

    public void setDeaccessionLink(String deaccessionLink) {
        this.deaccessionLink = deaccessionLink;
    }

    public GlobalId getDeaccessionLinkAsGlobalId() {
        return new GlobalId(deaccessionLink);
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Date lastUpdateTime) {
        if (createTime == null) {
            createTime = lastUpdateTime;
        }
        this.lastUpdateTime = lastUpdateTime;
    }

    public String getVersionDate() {
        if (this.lastUpdateTime == null){
            return null; 
        }
        return DateUtil.formatDate(lastUpdateTime);
    }

    public String getVersionYear() {
        return new SimpleDateFormat("yyyy").format(lastUpdateTime);
    }

    public Date getReleaseTime() {
        return releaseTime;
    }

    public void setReleaseTime(Date releaseTime) {
        this.releaseTime = releaseTime;
    }

    public List<DatasetVersionUser> getDatasetVersionUsers() {
        return datasetVersionUsers;
    }

    public void setUserDatasets(List<DatasetVersionUser> datasetVersionUsers) {
        this.datasetVersionUsers = datasetVersionUsers;
    }

    public List<String> getVersionContributorIdentifiers() {
        if (this.getDatasetVersionUsers() == null) {
            return Collections.emptyList();
        }
        List<String> ret = new LinkedList<>();
        for (DatasetVersionUser contributor : this.getDatasetVersionUsers()) {
            ret.add(contributor.getAuthenticatedUser().getIdentifier());
        }
        return ret;
    }

    public String getContributorNames() {
        return contributorNames;
    }

    public void setContributorNames(String contributorNames) {
        this.contributorNames = contributorNames;
    }

 
    public String getVersionNote() {
        return versionNote;
    }

    public DatasetVersionDifference getDefaultVersionDifference() {
        if(dvd!=null) {
            return dvd;
        }
        int index = 0;
        int size = this.getDataset().getVersions().size();
        if (this.isDeaccessioned()) {
            return null;
        }
        for (DatasetVersion dsv : this.getDataset().getVersions()) {
            if (this.equals(dsv)) {
                if ((index + 1) <= (size - 1)) {
                    for (DatasetVersion dvTest : this.getDataset().getVersions().subList(index + 1, size)) {
                        if (!dvTest.isDeaccessioned()) {
                            dvd = new DatasetVersionDifference(this, dvTest);
                            return dvd;
                        }
                    }
                }
            }
            index++;
        }
        return null;
    }
    

    public VersionState getPriorVersionState() {
        int index = 0;
        int size = this.getDataset().getVersions().size();
        if (this.isDeaccessioned()) {
            return null;
        }
        for (DatasetVersion dsv : this.getDataset().getVersions()) {
            if (this.equals(dsv)) {
                if ((index + 1) <= (size - 1)) {
                    for (DatasetVersion dvTest : this.getDataset().getVersions().subList(index + 1, size)) {
                        return dvTest.getVersionState();
                    }
                }
            }
            index++;
        }
        return null;
    }

    public void setVersionNote(String note) {
        if (note != null && note.length() > VERSION_NOTE_MAX_LENGTH) {
            throw new IllegalArgumentException("Error setting versionNote: String length is greater than maximum (" + VERSION_NOTE_MAX_LENGTH + ")."
                    + "  StudyVersion id=" + id + ", versionNote=" + note);
        }
        this.versionNote = note;
    }
   
    public Long getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Long versionNumber) {
        this.versionNumber = versionNumber;
    }

    public Long getMinorVersionNumber() {
        return minorVersionNumber;
    }

    public void setMinorVersionNumber(Long minorVersionNumber) {
        this.minorVersionNumber = minorVersionNumber;
    }
    
    public String getFriendlyVersionNumber(){
        if (this.isDraft()) {
            return "DRAFT";
        } else {
            return versionNumber.toString() + "." + minorVersionNumber.toString();                    
        }
    }

    public VersionState getVersionState() {
        return versionState;
    }

    public void setVersionState(VersionState versionState) {
        this.versionState = versionState;
    }

    public boolean isReleased() {
        return versionState.equals(VersionState.RELEASED);
    }

    public boolean isPublished() {
        return isReleased();
    }

    public boolean isDraft() {
        return versionState.equals(VersionState.DRAFT);
    }

    public boolean isWorkingCopy() {
        return versionState.equals(VersionState.DRAFT);
    }

    public boolean isArchived() {
        return versionState.equals(VersionState.ARCHIVED);
    }

    public boolean isDeaccessioned() {
        return versionState.equals(VersionState.DEACCESSIONED);
    }

    public boolean isRetiredCopy() {
        return (versionState.equals(VersionState.ARCHIVED) || versionState.equals(VersionState.DEACCESSIONED));
    }

    public boolean isMinorUpdate() {
        if (this.dataset.getLatestVersion().isWorkingCopy()) {
            if (this.dataset.getVersions().size() > 1 && this.dataset.getVersions().get(1) != null) {
                if (this.dataset.getVersions().get(1).isDeaccessioned()) {
                    return false;
                }
            }
        }
        if (this.getDataset().getReleasedVersion() != null) {
            if (this.getFileMetadatas().size() != this.getDataset().getReleasedVersion().getFileMetadatas().size()){
                return false;
            } else {
                List <DataFile> current = new ArrayList<>();
                List <DataFile> previous = new ArrayList<>();
                for (FileMetadata fmdc : this.getFileMetadatas()){
                    current.add(fmdc.getDataFile());
                }
                for (FileMetadata fmdc : this.getDataset().getReleasedVersion().getFileMetadatas()){
                    previous.add(fmdc.getDataFile());
                }
                for (DataFile fmd: current){
                    previous.remove(fmd);
                }
                return previous.isEmpty();                
            }           
        }
        return true;
    }
    
    public boolean isHasPackageFile(){
        if (this.fileMetadatas.isEmpty()){
            return false;
        }
        if(this.fileMetadatas.size() > 1){
            return false;
        }
        return this.fileMetadatas.get(0).getDataFile().getContentType().equals(DataFileServiceBean.MIME_TYPE_PACKAGE_FILE);
    }

    public boolean isHasNonPackageFile(){
        if (this.fileMetadatas.isEmpty()){
            return false;
        }
        return !this.fileMetadatas.get(0).getDataFile().getContentType().equals(DataFileServiceBean.MIME_TYPE_PACKAGE_FILE);
    }

    public void updateDefaultValuesFromTemplate(Template template) {
        if (!template.getDatasetFields().isEmpty()) {
            this.setDatasetFields(this.copyDatasetFields(template.getDatasetFields()));
        }
        if (template.getTermsOfUseAndAccess() != null) {
            TermsOfUseAndAccess terms = template.getTermsOfUseAndAccess().copyTermsOfUseAndAccess();
            terms.setDatasetVersion(this);
            this.setTermsOfUseAndAccess(terms);
        } else {
            TermsOfUseAndAccess terms = new TermsOfUseAndAccess();
            terms.setDatasetVersion(this);
            terms.setLicense(TermsOfUseAndAccess.License.CC0);
            terms.setDatasetVersion(this);
            this.setTermsOfUseAndAccess(terms);
        }
    }
    
    public DatasetVersion cloneDatasetVersion(){
        DatasetVersion dsv = new DatasetVersion();
        dsv.setVersionState(this.getPriorVersionState());
        dsv.setFileMetadatas(new ArrayList<>());
        
           if (this.getUNF() != null){
                dsv.setUNF(this.getUNF());
            }
            
            if (this.getDatasetFields() != null && !this.getDatasetFields().isEmpty()) {
                dsv.setDatasetFields(dsv.copyDatasetFields(this.getDatasetFields()));
            }
            
            if (this.getTermsOfUseAndAccess()!= null){
                dsv.setTermsOfUseAndAccess(this.getTermsOfUseAndAccess().copyTermsOfUseAndAccess());
            } else {
                TermsOfUseAndAccess terms = new TermsOfUseAndAccess();
                terms.setDatasetVersion(dsv);
                terms.setLicense(TermsOfUseAndAccess.License.CC0);
                dsv.setTermsOfUseAndAccess(terms);
            }

            for (FileMetadata fm : this.getFileMetadatas()) {
                FileMetadata newFm = new FileMetadata();
                newFm.setCategories(fm.getCategories());
                newFm.setDescription(fm.getDescription());
                newFm.setLabel(fm.getLabel());
                newFm.setDirectoryLabel(fm.getDirectoryLabel());
                newFm.setRestricted(fm.isRestricted());
                newFm.setDataFile(fm.getDataFile());
                newFm.setDatasetVersion(dsv);
                newFm.setProvFreeForm(fm.getProvFreeForm());
                
                dsv.getFileMetadatas().add(newFm);
            }




        dsv.setDataset(this.getDataset());
        return dsv;
        
    }

    public void initDefaultValues() {
        this.setDatasetFields(new ArrayList<>());
        this.setDatasetFields(this.initDatasetFields());
        TermsOfUseAndAccess terms = new TermsOfUseAndAccess();
        terms.setDatasetVersion(this);
        terms.setLicense(TermsOfUseAndAccess.License.CC0);
        this.setTermsOfUseAndAccess(terms);

    }

    public DatasetVersion getMostRecentlyReleasedVersion() {
        if (this.isReleased()) {
            return this;
        } else {
            if (this.getDataset().isReleased()) {
                for (DatasetVersion testVersion : this.dataset.getVersions()) {
                    if (testVersion.isReleased()) {
                        return testVersion;
                    }
                }
            }
        }
        return null;
    }

    public DatasetVersion getLargestMinorRelease() {
        if (this.getDataset().isReleased()) {
            for (DatasetVersion testVersion : this.dataset.getVersions()) {
                if (testVersion.getVersionNumber() != null && testVersion.getVersionNumber().equals(this.getVersionNumber())) {
                    return testVersion;
                }
            }
        }

        return this;
    }

    public Dataset getDataset() {
        return dataset;
    }

    public void setDataset(Dataset dataset) {
        this.dataset = dataset;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof DatasetVersion)) {
            return false;
        }
        DatasetVersion other = (DatasetVersion) object;
        return !((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id)));
    }

    @Override
    public String toString() {
        return "[DatasetVersion id:" + getId() + "]";
    }

    public boolean isLatestVersion() {
        return this.equals(this.getDataset().getLatestVersion());
    }

    public String getTitle() {
        String retVal = "";
        for (DatasetField dsfv : this.getDatasetFields()) {
            if (dsfv.getDatasetFieldType().getName().equals(DatasetFieldConstant.title)) {
                retVal = dsfv.getDisplayValue();
            }
        }
        return retVal;
    }

    public String getProductionDate() {
        String retVal = null;
        for (DatasetField dsfv : this.getDatasetFields()) {
            if (dsfv.getDatasetFieldType().getName().equals(DatasetFieldConstant.productionDate)) {
                retVal = dsfv.getDisplayValue();
            }
        }
        return retVal;
    }

    public String getDescription() {
        for (DatasetField dsf : this.getDatasetFields()) {
            if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.description)) {
                String descriptionString = "";
                if (dsf.getDatasetFieldCompoundValues() != null && dsf.getDatasetFieldCompoundValues().get(0) != null) {
                    DatasetFieldCompoundValue descriptionValue = dsf.getDatasetFieldCompoundValues().get(0);
                    for (DatasetField subField : descriptionValue.getChildDatasetFields()) {
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.descriptionText) && !subField.isEmptyForDisplay()) {
                            descriptionString = subField.getValue();
                        }
                    }
                }
                logger.log(Level.FINE, "pristine description: {0}", descriptionString);
                return descriptionString;
            }
        }
        return "";
    }

    public List<String> getDescriptions() {
        List<String> descriptions = new ArrayList<>();
        for (DatasetField dsf : this.getDatasetFields()) {
            if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.description)) {
                String descriptionString = "";
                if (dsf.getDatasetFieldCompoundValues() != null && !dsf.getDatasetFieldCompoundValues().isEmpty()) {
                    for (DatasetFieldCompoundValue descriptionValue : dsf.getDatasetFieldCompoundValues()) {
                        for (DatasetField subField : descriptionValue.getChildDatasetFields()) {
                            if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.descriptionText) && !subField.isEmptyForDisplay()) {
                                descriptionString = subField.getValue();
                            }
                        }
                        logger.log(Level.FINE, "pristine description: {0}", descriptionString);
                        descriptions.add(descriptionString);
                    }
                }
            }
        }
        return descriptions;
    }

    public String getDescriptionPlainText() {
        return MarkupChecker.stripAllTags(getDescription());
    }

    public List<String> getDescriptionsPlainText() {
        List<String> plainTextDescriptions = new ArrayList<>();
        for (String htmlDescription : getDescriptions()) {
            plainTextDescriptions.add(MarkupChecker.stripAllTags(htmlDescription));
        }
        return plainTextDescriptions;
    }

    public String getDescriptionHtmlEscaped() {
        return MarkupChecker.escapeHtml(getDescription());
    }

    public List<String[]> getDatasetContacts() {
        boolean getDisplayValues = true;
        return getDatasetContacts(getDisplayValues);
    }

    public List<String[]> getDatasetContacts(boolean getDisplayValues) {
        List <String[]> retList = new ArrayList<>();
        for (DatasetField dsf : this.getDatasetFields()) {
            Boolean addContributor = true;
            String contributorName = "";
            String contributorAffiliation = "";
            if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.datasetContact)) {
                for (DatasetFieldCompoundValue authorValue : dsf.getDatasetFieldCompoundValues()) {
                    for (DatasetField subField : authorValue.getChildDatasetFields()) {
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.datasetContactName)) {
                            if (subField.isEmptyForDisplay()) {
                                addContributor = false;
                            }
                            contributorName = subField.getDisplayValue();
                        }
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.datasetContactAffiliation)) {
                            contributorAffiliation = getDisplayValues ? subField.getDisplayValue() : subField.getValue();
                        }

                    }
                    if (addContributor) {
                        String[] datasetContributor = new String[] {contributorName, contributorAffiliation};
                        retList.add(datasetContributor);
                    }
                }
            }
        }       
        return retList;        
    }

    public List<String[]> getDatasetProducers(){
        List <String[]> retList = new ArrayList<>();
        for (DatasetField dsf : this.getDatasetFields()) {
            Boolean addContributor = true;
            String contributorName = "";
            String contributorAffiliation = "";
            if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.producer)) {
                for (DatasetFieldCompoundValue authorValue : dsf.getDatasetFieldCompoundValues()) {
                    for (DatasetField subField : authorValue.getChildDatasetFields()) {
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.producerName)) {
                            if (subField.isEmptyForDisplay()) {
                                addContributor = false;
                            }
                            contributorName = subField.getDisplayValue();
                        }
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.producerAffiliation)) {
                            contributorAffiliation = subField.getDisplayValue();
                        }

                    }
                    if (addContributor) {
                        String[] datasetContributor = new String[] {contributorName, contributorAffiliation};
                        retList.add(datasetContributor);
                    }
                }
            }
        }       
        return retList;        
    }

    public List<DatasetAuthor> getDatasetAuthors() {
        List <DatasetAuthor> retList = new ArrayList<>();
        for (DatasetField dsf : this.getDatasetFields()) {
            Boolean addAuthor = true;
            if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.author)) {
                for (DatasetFieldCompoundValue authorValue : dsf.getDatasetFieldCompoundValues()) {                   
                    DatasetAuthor datasetAuthor = new DatasetAuthor();
                    for (DatasetField subField : authorValue.getChildDatasetFields()) {
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.authorName)) {
                            if (subField.isEmptyForDisplay()) {
                                addAuthor = false;
                            }
                            datasetAuthor.setName(subField);
                        }
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.authorAffiliation)) {
                            datasetAuthor.setAffiliation(subField);
                        }
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.authorIdType)){
                             datasetAuthor.setIdType(subField.getRawValue());
                        }
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.authorIdValue)){
                            datasetAuthor.setIdValue(subField.getDisplayValue());
                        }
                    }
                    if (addAuthor) {                       
                        retList.add(datasetAuthor);
                    }
                }
            }
        }
        return retList;
    }
    
    public List<String> getFunders() {
        List<String> retList = new ArrayList<>();
        for (DatasetField dsf : this.getDatasetFields()) {
            if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.contributor)) {
                boolean addFunder = false;
                for (DatasetFieldCompoundValue contributorValue : dsf.getDatasetFieldCompoundValues()) {
                    String contributorName = null;
                    String contributorType = null;
                    for (DatasetField subField : contributorValue.getChildDatasetFields()) {
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.contributorName)) {
                            contributorName = subField.getDisplayValue();
                        }
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.contributorType)) {
                            contributorType = subField.getRawValue();
                        }
                    }
                    if ("Funder".equals(contributorType)) {
                        retList.add(contributorName);
                    }
                }
            }
            if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.grantNumber)) {
                for (DatasetFieldCompoundValue grantObject : dsf.getDatasetFieldCompoundValues()) {
                    for (DatasetField subField : grantObject.getChildDatasetFields()) {
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.grantNumberAgency)) {
                            String grantAgency = subField.getDisplayValue();
                            if (grantAgency != null && !grantAgency.isEmpty()) {
                                retList.add(grantAgency);
                            }
                        }
                    }
                }
            }
        }
        return retList;
    }

    public List<String> getTimePeriodsCovered() {
        List <String> retList = new ArrayList<>();
        for (DatasetField dsf : this.getDatasetFields()) {
            if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.timePeriodCovered)) {
                for (DatasetFieldCompoundValue timePeriodValue : dsf.getDatasetFieldCompoundValues()) {
                    String start = "";
                    String end = "";
                    for (DatasetField subField : timePeriodValue.getChildDatasetFields()) {
                        if (subField.getDatasetFieldType().getName()
                                .equals(DatasetFieldConstant.timePeriodCoveredStart)) {
                            if (subField.isEmptyForDisplay()) {
                                start = null;
                            } else {
                                start = subField.getValue();
                            }
                        }
                        if (subField.getDatasetFieldType().getName()
                                .equals(DatasetFieldConstant.timePeriodCoveredEnd)) {
                            if (subField.isEmptyForDisplay()) {
                                end = null;
                            } else {
                                end = subField.getValue();
                            }
                        }

                    }
                    if (start != null && end != null) {
                        retList.add(start + "/" + end);
                    }
                }
            }
        }
        return retList;
    }

    public List<String> getDatesOfCollection() {
        List<String> retList = new ArrayList<>();
        for (DatasetField dsf : this.getDatasetFields()) {
            if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.dateOfCollection)) {
                for (DatasetFieldCompoundValue timePeriodValue : dsf.getDatasetFieldCompoundValues()) {
                    String start = "";
                    String end = "";
                    for (DatasetField subField : timePeriodValue.getChildDatasetFields()) {
                        if (subField.getDatasetFieldType().getName()
                                .equals(DatasetFieldConstant.dateOfCollectionStart)) {
                            if (subField.isEmptyForDisplay()) {
                                start = null;
                            } else {
                                start = subField.getValue();
                            }
                        }
                        if (subField.getDatasetFieldType().getName()
                                .equals(DatasetFieldConstant.dateOfCollectionEnd)) {
                            if (subField.isEmptyForDisplay()) {
                                end = null;
                            } else {
                                end = subField.getValue();
                            }
                        }

                    }
                    if (start != null && end != null) {
                        retList.add(start + "/" + end);
                    }
                }
            }
        }       
        return retList;        
    }
    
    public List<String> getDatasetAuthorNames() {
        List<String> authors = new ArrayList<>();
        for (DatasetAuthor author : this.getDatasetAuthors()) {
            authors.add(author.getName().getValue());
        }
        return authors;
    }

    public List<String> getDatasetSubjects() {
        List<String> subjects = new ArrayList<>();
        for (DatasetField dsf : this.getDatasetFields()) {
            if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.subject)) {
                subjects.addAll(dsf.getValues());
            }
        }
        return subjects;
    }
    
    public List<String> getTopicClassifications() {
        return getCompoundChildFieldValues(DatasetFieldConstant.topicClassification,
                DatasetFieldConstant.topicClassValue);
    }
    
    public List<String> getKindOfData() {
        List<String> kod = new ArrayList<>();
        for (DatasetField dsf : this.getDatasetFields()) {
            if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.kindOfData)) {
                kod.addAll(dsf.getValues());
            }
        }
        return kod;
    }
    
    public List<String> getLanguages() {
        List<String> languages = new ArrayList<>();
        for (DatasetField dsf : this.getDatasetFields()) {
            if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.language)) {
                languages.addAll(dsf.getValues());
            }
        }
        return languages;
    }
    
        public List<String> getSpatialCoverages() {
        List<String> retList = new ArrayList<>();
        for (DatasetField dsf : this.getDatasetFields()) {
            if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.geographicCoverage)) {
                for (DatasetFieldCompoundValue geoValue : dsf.getDatasetFieldCompoundValues()) {
                    List<String> coverage = new ArrayList<String>();
                    for (DatasetField subField : geoValue.getChildDatasetFields()) {
                        if (subField.getDatasetFieldType().getName()
                                .equals(DatasetFieldConstant.country)) {
                            if (!subField.isEmptyForDisplay()) {
                            } else {
                                coverage.add(subField.getValue());
                            }
                        }
                        if (subField.getDatasetFieldType().getName()
                                .equals(DatasetFieldConstant.state)) {
                            if (!subField.isEmptyForDisplay()) {
                                coverage.add(subField.getValue());
                            }
                        }
                        if (subField.getDatasetFieldType().getName()
                                .equals(DatasetFieldConstant.city)) {
                            if (!subField.isEmptyForDisplay()) {
                                coverage.add(subField.getValue());
                            }
                        }
                        if (subField.getDatasetFieldType().getName()
                                .equals(DatasetFieldConstant.otherGeographicCoverage)) {
                            if (!subField.isEmptyForDisplay()) {
                                coverage.add(subField.getValue());
                            }
                        }
                    }
                    if (!coverage.isEmpty()) {
                        retList.add(String.join(",", coverage));
                    }
                }
            }
        }
        return retList;
    }
 
    public List<String> getSpatialCoverages(boolean commaSeparated) {
        List<String> retList = new ArrayList<>();
        for (DatasetField dsf : this.getDatasetFields()) {
            if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.geographicCoverage)) {
                for (DatasetFieldCompoundValue geoValue : dsf.getDatasetFieldCompoundValues()) {
                    Map<String, String> coverageHash = new HashMap<>();
                    for (DatasetField subField : geoValue.getChildDatasetFields()) {
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.country)) {
                            if (!subField.isEmptyForDisplay()) {
                                coverageHash.put(DatasetFieldConstant.country, subField.getValue());
                            }
                        }
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.state)) {
                            if (!subField.isEmptyForDisplay()) {
                                coverageHash.put(DatasetFieldConstant.state, subField.getValue());
                            }
                        }
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.city)) {
                            if (!subField.isEmptyForDisplay()) {
                                coverageHash.put(DatasetFieldConstant.city, subField.getValue());
                            }
                        }
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.otherGeographicCoverage)) {
                            if (!subField.isEmptyForDisplay()) {
                                coverageHash.put(DatasetFieldConstant.otherGeographicCoverage, subField.getValue());
                            }
                        }
                    }
                    if (!coverageHash.isEmpty()) {
                        List<String> coverageSorted = sortSpatialCoverage(coverageHash);
                        if (commaSeparated) {
                            retList.add(String.join(", ", coverageSorted));
                        } else {
                            retList.addAll(coverageSorted);
                        }
                    }
                }
            }
        }
        return retList;
    }

    private List<String> sortSpatialCoverage(Map<String, String> hash) {
        List<String> sorted = new ArrayList<>();
        String city = hash.get(DatasetFieldConstant.city);
        if (city != null) {
            sorted.add(city);
        }
        String state = hash.get(DatasetFieldConstant.state);
        if (state != null) {
            sorted.add(state);
        }
        String country = hash.get(DatasetFieldConstant.country);
        if (country != null) {
            sorted.add(country);
        }
        String otherGeographicCoverage = hash.get(DatasetFieldConstant.otherGeographicCoverage);
        if (otherGeographicCoverage != null) {
            sorted.add(otherGeographicCoverage);
        }
        return sorted;
    }

    public List<String> getKeywords() {
        return getCompoundChildFieldValues(DatasetFieldConstant.keyword, DatasetFieldConstant.keywordValue);
    }
    
    public List<String> getRelatedMaterial() {
        List<String> relMaterial = new ArrayList<>();
        for (DatasetField dsf : this.getDatasetFields()) {
            if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.relatedMaterial)) {
                relMaterial.addAll(dsf.getValues());
            }
        }
        return relMaterial;
    } 
    
    public List<String> getDataSource() {
        List<String> dataSources = new ArrayList<>();
        for (DatasetField dsf : this.getDatasetFields()) {
            if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.dataSources)) {
                dataSources.addAll(dsf.getValues());
            }
        }
        return dataSources;
    }
    
    public List<String[]> getGeographicCoverage() {
        List<String[]> geoCoverages = new ArrayList<>();

        for (DatasetField dsf : this.getDatasetFields()) {
            if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.geographicCoverage)) {
                for (DatasetFieldCompoundValue geoCoverage : dsf.getDatasetFieldCompoundValues()) {
                    String country = null;
                    String state = null;
                    String city = null;
                    String other = null;
                    String[] coverageItem = null;
                    for (DatasetField subField : geoCoverage.getChildDatasetFields()) {
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.country)) {
                            country = subField.getDisplayValue();
                        }
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.state)) {
                            state = subField.getDisplayValue();
                        }
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.city)) {
                            city = subField.getDisplayValue();
                        }
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.otherGeographicCoverage)) {
                            other = subField.getDisplayValue();
                        }

                        coverageItem = new String[]{country, state, city, other};
                    }
                    geoCoverages.add(coverageItem);
                }

            }
        }
        return geoCoverages;
    }

    
    public List<DatasetRelPublication> getRelatedPublications() {
        List<DatasetRelPublication> relatedPublications = new ArrayList<>();
        for (DatasetField dsf : this.getDatasetFields()) {
            if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.publication)) {
                for (DatasetFieldCompoundValue publication : dsf.getDatasetFieldCompoundValues()) {
                    DatasetRelPublication relatedPublication = new DatasetRelPublication();
                    for (DatasetField subField : publication.getChildDatasetFields()) {
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.publicationCitation)) {
                            String citation = subField.getDisplayValue();
                            relatedPublication.setText(citation);
                        }

                        
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.publicationURL)) {
                            String url = subField.getValue();
                            if (StringUtils.isBlank(url) || DatasetField.NA_VALUE.equals(url)) {
                                relatedPublication.setUrl("");
                            } else {
                                relatedPublication.setUrl(MarkupChecker.sanitizeBasicHTML(url));
                            }
                        }
                    }
                    relatedPublications.add(relatedPublication);
                }
            }
        }
        return relatedPublications;
    }
    
    public List<String> getUniqueGrantAgencyValues() {

        return getCompoundChildFieldValues(DatasetFieldConstant.grantNumber, DatasetFieldConstant.grantNumberAgency)
                .stream().distinct().collect(Collectors.toList());
    }

    public String getSeriesTitle() {

        List<String> seriesNames = getCompoundChildFieldValues(DatasetFieldConstant.series,
                DatasetFieldConstant.seriesName);
        if (seriesNames.size() > 1) {
            logger.warning("More than one series title found for datasetVersion: " + this.id);
        }
        if (!seriesNames.isEmpty()) {
            return seriesNames.get(0);
        } else {
            return null;
        }
    }

    public List<String> getCompoundChildFieldValues(String parentFieldName, String childFieldName) {
        List<String> keywords = new ArrayList<>();
        for (DatasetField dsf : this.getDatasetFields()) {
            if (dsf.getDatasetFieldType().getName().equals(parentFieldName)) {
                for (DatasetFieldCompoundValue keywordFieldValue : dsf.getDatasetFieldCompoundValues()) {
                    for (DatasetField subField : keywordFieldValue.getChildDatasetFields()) {
                        if (subField.getDatasetFieldType().getName().equals(childFieldName)) {
                            String keyword = subField.getValue();
                            if (!StringUtil.isEmpty(keyword)) {
                                keywords.add(subField.getValue());
                            }
                        }
                    }
                }
            }
        }
        return keywords;
    }
    
    public List<String> getDatasetProducerNames(){
        List<String> producerNames = new ArrayList<String>();
        for (DatasetField dsf : this.getDatasetFields()) {
            if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.producer)) {
                for (DatasetFieldCompoundValue authorValue : dsf.getDatasetFieldCompoundValues()) {
                    for (DatasetField subField : authorValue.getChildDatasetFields()) {
                        if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.producerName)) {
                            producerNames.add(subField.getDisplayValue().trim());
                        }
                    }
                }
            }
        }
        return producerNames;
    }

    public String getCitation() {
        return getCitation(false);
    }

    public String getCitation(boolean html) {
        return getCitation(html, false);
    }
    
    public String getCitation(boolean html, boolean anonymized) {
        return new DataCitation(this).toString(html, anonymized);
    }
    
    public Date getCitationDate() {
        DatasetField citationDate = getDatasetField(this.getDataset().getCitationDateDatasetFieldType());        
        if (citationDate != null && citationDate.getDatasetFieldType().getFieldType().equals(FieldType.DATE)){          
            try {  
                return new SimpleDateFormat("yyyy").parse( citationDate.getValue() );
            } catch (ParseException ex) {
                Logger.getLogger(DatasetVersion.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        return null;
    }
    
    public DatasetField getDatasetField(DatasetFieldType dsfType) {
        if (dsfType != null) {
            for (DatasetField dsf : this.getFlatDatasetFields()) {
                if (dsf.getDatasetFieldType().equals(dsfType)) {
                    return dsf;
                }
            }
        }
        return null;
    }

    public String getDistributionDate() {
        for (DatasetField dsf : this.getDatasetFields()) {
            if (DatasetFieldConstant.distributionDate.equals(dsf.getDatasetFieldType().getName())) {
                String date = dsf.getValue();
                return date;
            }
            
        }
        return null;
    }

    public String getDistributorName() {
        for (DatasetField dsf : this.getFlatDatasetFields()) {
            if (DatasetFieldConstant.distributorName.equals(dsf.getDatasetFieldType().getName())) {
                return dsf.getValue();
            }
        }
        return null;
    }

    public List<DatasetDistributor> getDatasetDistributors() {
        return new ArrayList<>();
    }

    public void setDatasetDistributors(List<DatasetDistributor> distributors) {
        }

    public String getDistributorNames() {
        String str = "";
        for (DatasetDistributor sd : this.getDatasetDistributors()) {
            if (str.trim().length() > 1) {
                str += ";";
            }
            str += sd.getName();
        }
        return str;
    }

    public String getAuthorsStr() {
        return getAuthorsStr(true);
    }

    public String getAuthorsStr(boolean affiliation) {
        String str = "";
        for (DatasetAuthor sa : getDatasetAuthors()) {
            if (sa.getName() == null) {
                break;
            }
            if (str.trim().length() > 1) {
                str += "; ";
            }
            str += sa.getName().getValue();
            if (affiliation) {
                if (sa.getAffiliation() != null) {
                    if (!StringUtil.isEmpty(sa.getAffiliation().getValue())) {
                        str += " (" + sa.getAffiliation().getValue() + ")";
                    }
                }
            }
        }
        return str;
    }

    private DatasetField initDatasetField(DatasetField dsf) {
        if (dsf.getDatasetFieldType().isCompound()) {
            for (DatasetFieldCompoundValue cv : dsf.getDatasetFieldCompoundValues()) {
                for (DatasetFieldType dsfType : dsf.getDatasetFieldType().getChildDatasetFieldTypes()) {
                    boolean add = true;
                    for (DatasetField subfield : cv.getChildDatasetFields()) {
                        if (dsfType.equals(subfield.getDatasetFieldType())) {
                            add = false;
                            break;
                        }
                    }

                    if (add) {
                        cv.getChildDatasetFields().add(DatasetField.createNewEmptyChildDatasetField(dsfType, cv));
                    }
                }
            }
        }

        return dsf;
    }

    public List<DatasetField> initDatasetFields() {
        List<DatasetField> retList = new ArrayList<>();
        if (this.getDatasetFields() != null) {
            for (DatasetField dsf : this.getDatasetFields()) {
                retList.add(initDatasetField(dsf));
            }
        }

        for (MetadataBlock mdb : this.getDataset().getOwner().getMetadataBlocks()) {
            for (DatasetFieldType dsfType : mdb.getDatasetFieldTypes()) {
                if (!dsfType.isSubField()) {
                    boolean add = true;
                    for (DatasetField dsf : retList) {
                        if (dsfType.equals(dsf.getDatasetFieldType())) {
                            add = false;
                            break;
                        }
                    }

                    if (add) {
                        retList.add(DatasetField.createNewEmptyDatasetField(dsfType, this));
                    }
                }
            }
        }

        Collections.sort(retList, DatasetField.DisplayOrder);

        return retList;
    }

    public String getReturnToDatasetURL(String serverName, Dataset dset) {
        if (serverName == null) {
            return null;
        }
        if (dset == null) {
            dset = this.getDataset();
            if (dset == null) {        return null;
            }
        }
        return serverName + "/dataset.xhtml?id=" + dset.getId() + "&versionId=" + this.getId();
    } 

    public String getReturnToFilePageURL (String serverName, Dataset dset, DataFile dataFile){
        if (serverName == null || dataFile == null) {
            return null;
        }
        if (dset == null) {
            dset = this.getDataset();
            if (dset == null) {
                return null;
            }
        }
        return serverName + "/file.xhtml?fileId=" + dataFile.getId() + "&version=" + this.getSemanticVersion();        
    }
    
    public List<DatasetField> copyDatasetFields(List<DatasetField> copyFromList) {
        List<DatasetField> retList = new ArrayList<>();

        for (DatasetField sourceDsf : copyFromList) {
            retList.add(sourceDsf.copy(this));
        }

        return retList;
    }


    public List<DatasetField> getFlatDatasetFields() {
        return getFlatDatasetFields(getDatasetFields());
    }

    private List<DatasetField> getFlatDatasetFields(List<DatasetField> dsfList) {
        List<DatasetField> retList = new LinkedList<>();
        for (DatasetField dsf : dsfList) {
            retList.add(dsf);
            if (dsf.getDatasetFieldType().isCompound()) {
                for (DatasetFieldCompoundValue compoundValue : dsf.getDatasetFieldCompoundValues()) {
                    retList.addAll(getFlatDatasetFields(compoundValue.getChildDatasetFields()));
                }

            }
        }
        return retList;
    }

    public String getSemanticVersion() {
        if (this.isReleased()) {
            return versionNumber + "." + minorVersionNumber;
        } else if (this.isDraft()){
            return VersionState.DRAFT.toString();
        } else if (this.isDeaccessioned()){
            return versionNumber + "." + minorVersionNumber;
        } else{
            return versionNumber + "." + minorVersionNumber;            
        }
        }

    public List<ConstraintViolation<DatasetField>> validateRequired() {
        List<ConstraintViolation<DatasetField>> returnListreturnList = new ArrayList<>();
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        for (DatasetField dsf : this.getFlatDatasetFields()) {
            dsf.setValidationMessage(null); Set<ConstraintViolation<DatasetField>> constraintViolations = validator.validate(dsf);
            for (ConstraintViolation<DatasetField> constraintViolation : constraintViolations) {
                dsf.setValidationMessage(constraintViolation.getMessage());
                returnListreturnList.add(constraintViolation);
                 break; }
            
        }
        return returnListreturnList;
    }
    
    public Set<ConstraintViolation> validate() {
        Set<ConstraintViolation> returnSet = new HashSet<>();

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();

        for (DatasetField dsf : this.getFlatDatasetFields()) {
            dsf.setValidationMessage(null); Set<ConstraintViolation<DatasetField>> constraintViolations = validator.validate(dsf);
            for (ConstraintViolation<DatasetField> constraintViolation : constraintViolations) {
                dsf.setValidationMessage(constraintViolation.getMessage());
                returnSet.add(constraintViolation);
                break; }
            for (DatasetFieldValue dsfv : dsf.getDatasetFieldValues()) {
                dsfv.setValidationMessage(null); Set<ConstraintViolation<DatasetFieldValue>> constraintViolations2 = validator.validate(dsfv);
                for (ConstraintViolation<DatasetFieldValue> constraintViolation : constraintViolations2) {
                    dsfv.setValidationMessage(constraintViolation.getMessage());
                    returnSet.add(constraintViolation);
                    break; }
            }
        }
        List<FileMetadata> dsvfileMetadatas = this.getFileMetadatas();
        if (dsvfileMetadatas != null) {
            for (FileMetadata fileMetadata : dsvfileMetadatas) {
                Set<ConstraintViolation<FileMetadata>> constraintViolations = validator.validate(fileMetadata);
                if (constraintViolations.size() > 0) {
                    ConstraintViolation<FileMetadata> violation = constraintViolations.iterator().next();
                    String message = "Constraint violation found in FileMetadata. "
                            + violation.getMessage() + " "
                            + "The invalid value is \"" + violation.getInvalidValue().toString() + "\".";
                    logger.info(message);
                    returnSet.add(violation);
                    break; }
            }
        }

        return returnSet;
    }
    
    public List<WorkflowComment> getWorkflowComments() {
        return workflowComments;
    }

    public String getPublicationDateAsString() {
        if (DatasetVersion.VersionState.DRAFT == this.getVersionState()) {
            return "";
        }
        Date rel_date = this.getReleaseTime();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        String r = fmt.format(rel_date.getTime());
        return r;
    }

    public String getJsonLd() {
        if (!this.isPublished()) {
            return "";
        }
        
        if (jsonLd != null) {
            return jsonLd;
        }
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("@context", "http://schema.org");
        job.add("@type", "Dataset");
        job.add("@id", this.getDataset().getPersistentURL());
        job.add("identifier", this.getDataset().getPersistentURL());
        job.add("name", this.getTitle());
        JsonArrayBuilder authors = Json.createArrayBuilder();
        for (DatasetAuthor datasetAuthor : this.getDatasetAuthors()) {
            JsonObjectBuilder author = Json.createObjectBuilder();
            String name = datasetAuthor.getName().getDisplayValue();
            DatasetField authorAffiliation = datasetAuthor.getAffiliation();
            String affiliation = null;
            if (authorAffiliation != null) {
                affiliation = datasetAuthor.getAffiliation().getDisplayValue();
            }
            author.add("name", name);
            if (!StringUtil.isEmpty(affiliation)) {
                author.add("affiliation", affiliation);
            }
            String identifierAsUrl = datasetAuthor.getIdentifierAsUrl();
            if (identifierAsUrl != null) {
                author.add("@id", identifierAsUrl);
                author.add("identifier", identifierAsUrl);
            }
            authors.add(author);
        }
        JsonArray authorsArray = authors.build();
        job.add("creator", authorsArray);
        job.add("author", authorsArray);
        String datePublished = this.getDataset().getPublicationDateFormattedYYYYMMDD();
        if (datePublished != null) {
            job.add("datePublished", datePublished);
        }
        
         job.add("dateModified", this.getPublicationDateAsString());
        job.add("version", this.getVersionNumber().toString());

        JsonArrayBuilder descriptionsArray = Json.createArrayBuilder();
        List<String> descriptions = this.getDescriptionsPlainText();
        for (String description : descriptions) {
            descriptionsArray.add(description);
        }
        job.add("description", descriptionsArray);

        JsonArrayBuilder keywords = Json.createArrayBuilder();
        
        for (String subject : this.getDatasetSubjects()) {
            keywords.add(subject);
        }
        
        for (String topic : this.getTopicClassifications()) {
            keywords.add(topic);
        }
        
        for (String keyword : this.getKeywords()) {
            keywords.add(keyword);
        }
        
        job.add("keywords", keywords);
        
        List<DatasetRelPublication> relatedPublications = getRelatedPublications();
        if (!relatedPublications.isEmpty()) {
            JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();
            for (DatasetRelPublication relatedPub : relatedPublications) {
                boolean addToArray = false;
                String pubCitation = relatedPub.getText();
                String pubUrl = relatedPub.getUrl();
                if (pubCitation != null || pubUrl != null) {
                    addToArray = true;
                }
                JsonObjectBuilder citationEntry = Json.createObjectBuilder();
                citationEntry.add("@type", "CreativeWork");
                if (pubCitation != null) {
                    citationEntry.add("text", pubCitation);
                }
                if (pubUrl != null) {
                    citationEntry.add("@id", pubUrl);
                    citationEntry.add("identifier", pubUrl);
                }
                if (addToArray) {
                    jsonArrayBuilder.add(citationEntry);
                }
            }
            JsonArray jsonArray = jsonArrayBuilder.build();
            if (!jsonArray.isEmpty()) {
                job.add("citation", jsonArray);
            }
        }
        
        List<String> timePeriodsCovered = this.getTimePeriodsCovered();
        if (timePeriodsCovered.size() > 0) {
            JsonArrayBuilder temporalCoverage = Json.createArrayBuilder();
            for (String timePeriod : timePeriodsCovered) {
                temporalCoverage.add(timePeriod);
            }
            job.add("temporalCoverage", temporalCoverage);
        }
        
        TermsOfUseAndAccess terms = this.getTermsOfUseAndAccess();
        if (terms != null) {
            JsonObjectBuilder license = Json.createObjectBuilder().add("@type", "Dataset");
            
            if (TermsOfUseAndAccess.License.CC0.equals(terms.getLicense())) {
                license.add("text", "CC0").add("url", TermsOfUseAndAccess.CC0_URI);
            } else {
                String termsOfUse = terms.getTermsOfUse();
                if (termsOfUse != null) {
                    license.add("text", termsOfUse);
                }
            }
            
            job.add("license",license);
        }
        
        job.add("includedInDataCatalog", Json.createObjectBuilder()
                .add("@type", "DataCatalog")
                .add("name", BrandingUtil.getRootDataverseCollectionName())
                .add("url", SystemConfig.getDataverseSiteUrlStatic())
        );

        String installationBrandName = BrandingUtil.getInstallationBrandName();
        job.add("publisher", Json.createObjectBuilder()
                .add("@type", "Organization")
                .add("name", installationBrandName)
        );
        job.add("provider", Json.createObjectBuilder()
                .add("@type", "Organization")
                .add("name", installationBrandName)
        );

        List<String> funderNames = getFunders();
        if (!funderNames.isEmpty()) {
            JsonArrayBuilder funderArray = Json.createArrayBuilder();
            for (String funderName : funderNames) {
                JsonObjectBuilder funder = NullSafeJsonBuilder.jsonObjectBuilder();
                funder.add("@type", "Organization");
                funder.add("name", funderName);
                funderArray.add(funder);
            }
            job.add("funder", funderArray);
        }

        boolean commaSeparated = true;
        List<String> spatialCoverages = getSpatialCoverages(commaSeparated);
        if (!spatialCoverages.isEmpty()) {
            JsonArrayBuilder spatialArray = Json.createArrayBuilder();
            for (String spatialCoverage : spatialCoverages) {
                spatialArray.add(spatialCoverage);
            }
            job.add("spatialCoverage", spatialArray);
        }

        List<FileMetadata> fileMetadatasSorted = getFileMetadatasSorted();
        if (fileMetadatasSorted != null && !fileMetadatasSorted.isEmpty()) {
            JsonArrayBuilder fileArray = Json.createArrayBuilder();
            String dataverseSiteUrl = SystemConfig.getDataverseSiteUrlStatic();
            for (FileMetadata fileMetadata : fileMetadatasSorted) {
                JsonObjectBuilder fileObject = NullSafeJsonBuilder.jsonObjectBuilder();
                String filePidUrlAsString = null;
                URL filePidUrl = fileMetadata.getDataFile().getGlobalId().toURL();
                if (filePidUrl != null) {
                    filePidUrlAsString = filePidUrl.toString();
                }
                fileObject.add("@type", "DataDownload");
                fileObject.add("name", fileMetadata.getLabel());
                fileObject.add("fileFormat", fileMetadata.getDataFile().getContentType());
                fileObject.add("contentSize", fileMetadata.getDataFile().getFilesize());
                fileObject.add("description", fileMetadata.getDescription());
                fileObject.add("@id", filePidUrlAsString);
                fileObject.add("identifier", filePidUrlAsString);
                String hideFilesBoolean = System.getProperty(SystemConfig.FILES_HIDE_SCHEMA_DOT_ORG_DOWNLOAD_URLS);
                if (hideFilesBoolean != null && hideFilesBoolean.equals("true")) {
                    } else {
                    if (FileUtil.isPubliclyDownloadable(fileMetadata)) {
                        String nullDownloadType = null;
                        fileObject.add("contentUrl", dataverseSiteUrl + FileUtil.getFileDownloadUrlPath(nullDownloadType, fileMetadata.getDataFile().getId(), false, fileMetadata.getId()));
                    }
                }
                fileArray.add(fileObject);
            }
            job.add("distribution", fileArray);
        }
        jsonLd = job.build().toString();
        
        jsonLd = MarkupChecker.stripAllTags(jsonLd);
        
        return jsonLd;
    }

    public String getLocaleLastUpdateTime() {
        return DateUtil.formatDate(new Timestamp(lastUpdateTime.getTime()));
    }
    
    public String getExternalStatusLabel() {
        return externalStatusLabel;
    }

    public void setExternalStatusLabel(String externalStatusLabel) {
        this.externalStatusLabel = externalStatusLabel;
    }

}
