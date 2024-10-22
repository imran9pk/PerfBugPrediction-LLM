package org.elasticsearch.index.mapper;

import com.carrotsearch.hppc.ObjectObjectHashMap;
import com.carrotsearch.hppc.ObjectObjectMap;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.lucene.all.AllEntries;
import org.elasticsearch.Version;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.IndexSettings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public abstract class ParseContext implements Iterable<ParseContext.Document>{

    public static class Document implements Iterable<IndexableField> {

        private final Document parent;
        private final String path;
        private final String prefix;
        private final List<IndexableField> fields;
        private ObjectObjectMap<Object, IndexableField> keyedFields;

        private Document(String path, Document parent) {
            fields = new ArrayList<>();
            this.path = path;
            this.prefix = path.isEmpty() ? "" : path + ".";
            this.parent = parent;
        }

        public Document() {
            this("", null);
        }

        public String getPath() {
            return path;
        }

        public String getPrefix() {
            return prefix;
        }

        public Document getParent() {
            return parent;
        }

        @Override
        public Iterator<IndexableField> iterator() {
            return fields.iterator();
        }

        public List<IndexableField> getFields() {
            return fields;
        }

        public void add(IndexableField field) {
            assert field.name().startsWith("_") || field.name().startsWith(prefix) : field.name() + " " + prefix;
            fields.add(field);
        }

        public void addWithKey(Object key, IndexableField field) {
            if (keyedFields == null) {
                keyedFields = new ObjectObjectHashMap<>();
            } else if (keyedFields.containsKey(key)) {
                throw new IllegalStateException("Only one field can be stored per key");
            }
            keyedFields.put(key, field);
            add(field);
        }

        public IndexableField getByKey(Object key) {
            return keyedFields == null ? null : keyedFields.get(key);
        }

        public IndexableField[] getFields(String name) {
            List<IndexableField> f = new ArrayList<>();
            for (IndexableField field : fields) {
                if (field.name().equals(name)) {
                    f.add(field);
                }
            }
            return f.toArray(new IndexableField[f.size()]);
        }

        public final String[] getValues(String name) {
            List<String> result = new ArrayList<>();
            for (IndexableField field : fields) {
                if (field.name().equals(name) && field.stringValue() != null) {
                    result.add(field.stringValue());
                }
            }
            return result.toArray(new String[result.size()]);
        }

        public IndexableField getField(String name) {
            for (IndexableField field : fields) {
                if (field.name().equals(name)) {
                    return field;
                }
            }
            return null;
        }

        public String get(String name) {
            for (IndexableField f : fields) {
                if (f.name().equals(name) && f.stringValue() != null) {
                    return f.stringValue();
                }
            }
            return null;
        }

        public BytesRef getBinaryValue(String name) {
            for (IndexableField f : fields) {
                if (f.name().equals(name) && f.binaryValue() != null) {
                    return f.binaryValue();
                }
            }
            return null;
        }

    }

    private static class FilterParseContext extends ParseContext {

        private final ParseContext in;

        private FilterParseContext(ParseContext in) {
            this.in = in;
        }

        @Override
        public Iterable<Document> nonRootDocuments() {
            return in.nonRootDocuments();
        }

        @Override
        public DocumentMapperParser docMapperParser() {
            return in.docMapperParser();
        }

        @Override
        public boolean isWithinCopyTo() {
            return in.isWithinCopyTo();
        }

        @Override
        public boolean isWithinMultiFields() {
            return in.isWithinMultiFields();
        }

        @Override
        public IndexSettings indexSettings() {
            return in.indexSettings();
        }

        @Override
        public SourceToParse sourceToParse() {
            return in.sourceToParse();
        }

        @Override
        public ContentPath path() {
            return in.path();
        }

        @Override
        public XContentParser parser() {
            return in.parser();
        }

        @Override
        public Document rootDoc() {
            return in.rootDoc();
        }

        @Override
        public Document doc() {
            return in.doc();
        }

        @Override
        protected void addDoc(Document doc) {
            in.addDoc(doc);
        }

        @Override
        public RootObjectMapper root() {
            return in.root();
        }

        @Override
        public DocumentMapper docMapper() {
            return in.docMapper();
        }

        @Override
        public MapperService mapperService() {
            return in.mapperService();
        }

        @Override
        public Field version() {
            return in.version();
        }

        @Override
        public void version(Field version) {
            in.version(version);
        }

        @Override
        public SeqNoFieldMapper.SequenceIDFields seqID() {
            return in.seqID();
        }

        @Override
        public void seqID(SeqNoFieldMapper.SequenceIDFields seqID) {
            in.seqID(seqID);
        }

        @Override
        public AllEntries allEntries() {
            return in.allEntries();
        }

        @Override
        public boolean externalValueSet() {
            return in.externalValueSet();
        }

        @Override
        public Object externalValue() {
            return in.externalValue();
        }

        @Override
        public void addDynamicMapper(Mapper update) {
            in.addDynamicMapper(update);
        }

        @Override
        public List<Mapper> getDynamicMappers() {
            return in.getDynamicMappers();
        }

        @Override
        public Iterator<Document> iterator() {
            return in.iterator();
        }

        @Override
        public void addIgnoredField(String field) {
            in.addIgnoredField(field);
        }

        @Override
        public Collection<String> getIgnoredFields() {
            return in.getIgnoredFields();
        }
    }

    public static class InternalParseContext extends ParseContext {

        private final DocumentMapper docMapper;

        private final DocumentMapperParser docMapperParser;

        private final ContentPath path;

        private final XContentParser parser;

        private Document document;

        private final List<Document> documents;

        private final IndexSettings indexSettings;

        private final SourceToParse sourceToParse;

        private Field version;

        private SeqNoFieldMapper.SequenceIDFields seqID;

        private final AllEntries allEntries;

        private final List<Mapper> dynamicMappers;

        private boolean docsReversed = false;

        private final Set<String> ignoredFields = new HashSet<>();

        public InternalParseContext(IndexSettings indexSettings, DocumentMapperParser docMapperParser, DocumentMapper docMapper,
                                    SourceToParse source, XContentParser parser) {
            this.indexSettings = indexSettings;
            this.docMapper = docMapper;
            this.docMapperParser = docMapperParser;
            this.path = new ContentPath(0);
            this.parser = parser;
            this.document = new Document();
            this.documents = new ArrayList<>();
            this.documents.add(document);
            this.version = null;
            this.sourceToParse = source;
            this.allEntries = new AllEntries();
            this.dynamicMappers = new ArrayList<>();
        }

        @Override
        public DocumentMapperParser docMapperParser() {
            return this.docMapperParser;
        }

        @Override
        public IndexSettings indexSettings() {
            return this.indexSettings;
        }

        @Override
        public SourceToParse sourceToParse() {
            return this.sourceToParse;
        }

        @Override
        public ContentPath path() {
            return this.path;
        }

        @Override
        public XContentParser parser() {
            return this.parser;
        }

        @Override
        public Document rootDoc() {
            return documents.get(0);
        }

        List<Document> docs() {
            return this.documents;
        }

        @Override
        public Document doc() {
            return this.document;
        }

        @Override
        protected void addDoc(Document doc) {
            this.documents.add(doc);
        }

        @Override
        public RootObjectMapper root() {
            return docMapper.root();
        }

        @Override
        public DocumentMapper docMapper() {
            return this.docMapper;
        }

        @Override
        public MapperService mapperService() {
            return docMapperParser.mapperService;
        }

        @Override
        public Field version() {
            return this.version;
        }

        @Override
        public void version(Field version) {
            this.version = version;
        }

        @Override
        public SeqNoFieldMapper.SequenceIDFields seqID() {
            return this.seqID;
        }

        @Override
        public void seqID(SeqNoFieldMapper.SequenceIDFields seqID) {
            this.seqID = seqID;
        }

        @Override
        public AllEntries allEntries() {
            return this.allEntries;
        }

        @Override
        public void addDynamicMapper(Mapper mapper) {
            dynamicMappers.add(mapper);
        }

        @Override
        public List<Mapper> getDynamicMappers() {
            return dynamicMappers;
        }

        @Override
        public Iterable<Document> nonRootDocuments() {
            if (docsReversed) {
                throw new IllegalStateException("documents are already reversed");
            }
            return documents.subList(1, documents.size());
        }

        void postParse() {
            if (documents.size() > 1) {
                docsReversed = true;
                if (indexSettings.getIndexVersionCreated().onOrAfter(Version.V_6_5_0)) {
                    List<Document> newDocs = reorderParent(documents);
                    documents.clear();
                    documents.addAll(newDocs);
                } else {
                    Collections.reverse(documents);
                }
            }
        }

        private List<Document> reorderParent(List<Document> docs) {
            List<Document> newDocs = new ArrayList<>(docs.size());
            LinkedList<Document> parents = new LinkedList<>();
            for (Document doc : docs) {
                while (parents.peek() != doc.getParent()){
                    newDocs.add(parents.poll());
                }
                parents.add(0, doc);
            }
            newDocs.addAll(parents);
            return newDocs;
        }

        @Override
        public Iterator<Document> iterator() {
            return documents.iterator();
        }


        @Override
        public void addIgnoredField(String field) {
            ignoredFields.add(field);
        }

        @Override
        public Collection<String> getIgnoredFields() {
            return Collections.unmodifiableCollection(ignoredFields);
        }
    }

    public abstract Iterable<Document> nonRootDocuments();


    public abstract void addIgnoredField(String field);

    public abstract Collection<String> getIgnoredFields();

    public abstract DocumentMapperParser docMapperParser();

    public final ParseContext setIncludeInAllDefault(boolean includeInAll) {
        return new FilterParseContext(this) {
            @Override
            public Boolean getIncludeInAllDefault() {
                return includeInAll;
            }
        };
    }

    public Boolean getIncludeInAllDefault() {
        return null;
    }

    public final ParseContext createCopyToContext() {
        return new FilterParseContext(this) {
            @Override
            public boolean isWithinCopyTo() {
                return true;
            }
        };
    }

    public boolean isWithinCopyTo() {
        return false;
    }

    public final ParseContext createMultiFieldContext() {
        return new FilterParseContext(this) {
            @Override
            public boolean isWithinMultiFields() {
                return true;
            }
        };
    }

    public final ParseContext createNestedContext(String fullPath) {
        final Document doc = new Document(fullPath, doc());
        addDoc(doc);
        return switchDoc(doc);
    }

    public final ParseContext switchDoc(final Document document) {
        return new FilterParseContext(this) {
            @Override
            public Document doc() {
                return document;
            }
        };
    }

    public final ParseContext overridePath(final ContentPath path) {
        return new FilterParseContext(this) {
            @Override
            public ContentPath path() {
                return path;
            }
        };
    }

    public boolean isWithinMultiFields() {
        return false;
    }

    public abstract IndexSettings indexSettings();

    public abstract SourceToParse sourceToParse();

    public abstract ContentPath path();

    public abstract XContentParser parser();

    public abstract Document rootDoc();

    public abstract Document doc();

    protected abstract void addDoc(Document doc);

    public abstract RootObjectMapper root();

    public abstract DocumentMapper docMapper();

    public abstract MapperService mapperService();

    public abstract Field version();

    public abstract void version(Field version);

    public abstract SeqNoFieldMapper.SequenceIDFields seqID();

    public abstract void seqID(SeqNoFieldMapper.SequenceIDFields seqID);

    public final boolean includeInAll(Boolean includeInAll, FieldMapper mapper) {
        return includeInAll(includeInAll, mapper.fieldType().indexOptions() != IndexOptions.NONE);
    }

    private boolean includeInAll(Boolean includeInAll, boolean indexed) {
        if (isWithinCopyTo()) {
            return false;
        }
        if (isWithinMultiFields()) {
            return false;
        }
        if (!docMapper().allFieldMapper().enabled()) {
            return false;
        }
        if (includeInAll == null) {
            includeInAll = getIncludeInAllDefault();
        }
        if (includeInAll == null) {
            return indexed;
        }
        return includeInAll;
    }

    public abstract AllEntries allEntries();

    public final ParseContext createExternalValueContext(final Object externalValue) {
        return new FilterParseContext(this) {
            @Override
            public boolean externalValueSet() {
                return true;
            }
            @Override
            public Object externalValue() {
                return externalValue;
            }
        };
    }

    public boolean externalValueSet() {
        return false;
    }

    public Object externalValue() {
        throw new IllegalStateException("External value is not set");
    }

    public final <T> T parseExternalValue(Class<T> clazz) {
        if (!externalValueSet() || externalValue() == null) {
            return null;
        }

        if (!clazz.isInstance(externalValue())) {
            throw new IllegalArgumentException("illegal external value class ["
                    + externalValue().getClass().getName() + "]. Should be " + clazz.getName());
        }
        return clazz.cast(externalValue());
    }

    public abstract void addDynamicMapper(Mapper update);

    public abstract List<Mapper> getDynamicMappers();
}
