package org.elasticsearch.action.admin.indices.template.get;

import org.elasticsearch.cluster.metadata.IndexTemplateMetadata;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.transport.TransportResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonMap;

public class GetIndexTemplatesResponse extends TransportResponse implements ToXContentObject {

    private final List<IndexTemplateMetadata> indexTemplates;

    GetIndexTemplatesResponse(List<IndexTemplateMetadata> indexTemplates) {
        this.indexTemplates = indexTemplates;
    }

    public List<IndexTemplateMetadata> getIndexTemplates() {
        return indexTemplates;
    }

    public GetIndexTemplatesResponse(StreamInput in) throws IOException {
        int size = in.readVInt();
        indexTemplates = new ArrayList<>(size);
        for (int i = 0 ; i < size ; i++) {
            indexTemplates.add(0, IndexTemplateMetadata.readFrom(in));
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeVInt(indexTemplates.size());
        for (IndexTemplateMetadata indexTemplate : indexTemplates) {
            indexTemplate.writeTo(out);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        params = new ToXContent.DelegatingMapParams(singletonMap("reduce_mappings", "true"), params);
        builder.startObject();
        for (IndexTemplateMetadata indexTemplateMetadata : getIndexTemplates()) {
            IndexTemplateMetadata.Builder.toXContent(indexTemplateMetadata, builder, params);
        }
        builder.endObject();
        return builder;
    }

    public static GetIndexTemplatesResponse fromXContent(XContentParser parser) throws IOException {
        final List<IndexTemplateMetadata> templates = new ArrayList<>();
        for (XContentParser.Token token = parser.nextToken(); token != XContentParser.Token.END_OBJECT; token = parser.nextToken()) {
            if (token == XContentParser.Token.FIELD_NAME) {
                final IndexTemplateMetadata templateMetadata = IndexTemplateMetadata.Builder.fromXContent(parser, parser.currentName());
                templates.add(templateMetadata);
            }
        }
        return new GetIndexTemplatesResponse(templates);
    }
}
