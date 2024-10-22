package org.sleuthkit.autopsy.keywordsearch;

import java.util.Date;
import java.util.List;

public class KeywordList {

    private String name;
    private Date created;
    private Date modified;
    private Boolean useForIngest;
    private Boolean postIngestMessages;
    private List<Keyword> keywords;
    private Boolean isEditable;

    KeywordList(String name, Date created, Date modified, Boolean useForIngest, Boolean postIngestMessages, List<Keyword> keywords, boolean isEditable) {
        this.name = name;
        this.created = created;
        this.modified = modified;
        this.useForIngest = useForIngest;
        this.postIngestMessages = postIngestMessages;
        this.keywords = keywords;
        this.isEditable = isEditable;
    }

    KeywordList(String name, Date created, Date modified, Boolean useForIngest, Boolean ingestMessages, List<Keyword> keywords) {
        this(name, created, modified, useForIngest, ingestMessages, keywords, false);
    }

    KeywordList(List<Keyword> keywords) {
        this("", new Date(0), new Date(0), false, false, keywords, false);
    }

    String getName() {
        return name;
    }

    Date getDateCreated() {
        return created;
    }

    Date getDateModified() {
        return modified;
    }

    Boolean getUseForIngest() {
        return useForIngest;
    }

    void setUseForIngest(boolean useForIngest) {
        this.useForIngest = useForIngest;
    }

    Boolean getIngestMessages() {
        return postIngestMessages;
    }

    void setIngestMessages(boolean postIngestMessages) {
        this.postIngestMessages = postIngestMessages;
    }

    public List<Keyword> getKeywords() {
        return keywords;
    }

    boolean hasKeyword(Keyword keyword) {
        return keywords.contains(keyword);
    }

    boolean hasSearchTerm(String searchTerm) {
        for (Keyword word : keywords) {
            if (word.getSearchTerm().equals(searchTerm)) {
                return true;
            }
        }
        return false;
    }

    Boolean isEditable() {
        return isEditable;
    }

}
