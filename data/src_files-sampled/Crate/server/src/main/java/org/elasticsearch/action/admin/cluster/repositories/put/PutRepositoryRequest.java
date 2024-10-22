package org.elasticsearch.action.admin.cluster.repositories.put;

import org.elasticsearch.ElasticsearchGenerationException;
import org.elasticsearch.action.support.master.AcknowledgedRequest;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;
import java.util.Map;

import static org.elasticsearch.common.settings.Settings.Builder.EMPTY_SETTINGS;
import static org.elasticsearch.common.settings.Settings.readSettingsFromStream;
import static org.elasticsearch.common.settings.Settings.writeSettingsToStream;

public class PutRepositoryRequest extends AcknowledgedRequest<PutRepositoryRequest> implements ToXContentObject {

    private String name;

    private String type;

    private boolean verify = true;

    private Settings settings = EMPTY_SETTINGS;

    public PutRepositoryRequest() {
    }

    public PutRepositoryRequest(String name) {
        this.name = name;
    }

    public PutRepositoryRequest name(String name) {
        this.name = name;
        return this;
    }

    public String name() {
        return this.name;
    }

    public PutRepositoryRequest type(String type) {
        this.type = type;
        return this;
    }

    public String type() {
        return this.type;
    }

    public PutRepositoryRequest settings(Settings settings) {
        this.settings = settings;
        return this;
    }

    public PutRepositoryRequest settings(Settings.Builder settings) {
        this.settings = settings.build();
        return this;
    }

    public PutRepositoryRequest settings(String source, XContentType xContentType) {
        this.settings = Settings.builder().loadFromSource(source, xContentType).build();
        return this;
    }

    public PutRepositoryRequest settings(Map<String, Object> source) {
        try {
            XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
            builder.map(source);
            settings(Strings.toString(builder), builder.contentType());
        } catch (IOException e) {
            throw new ElasticsearchGenerationException("Failed to generate [" + source + "]", e);
        }
        return this;
    }

    public Settings settings() {
        return this.settings;
    }

    public PutRepositoryRequest verify(boolean verify) {
        this.verify = verify;
        return this;
    }

    public boolean verify() {
        return this.verify;
    }

    public PutRepositoryRequest source(Map<String, Object> repositoryDefinition) {
        for (Map.Entry<String, Object> entry : repositoryDefinition.entrySet()) {
            String name = entry.getKey();
            if (name.equals("type")) {
                type(entry.getValue().toString());
            } else if (name.equals("settings")) {
                if (!(entry.getValue() instanceof Map)) {
                    throw new IllegalArgumentException("Malformed settings section, should include an inner object");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> sub = (Map<String, Object>) entry.getValue();
                settings(sub);
            }
        }
        return this;
    }

    public PutRepositoryRequest(StreamInput in) throws IOException {
        super(in);
        name = in.readString();
        type = in.readString();
        settings = readSettingsFromStream(in);
        verify = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(name);
        out.writeString(type);
        writeSettingsToStream(settings, out);
        out.writeBoolean(verify);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("name", name);
        builder.field("type", type);

        builder.startObject("settings");
        settings.toXContent(builder, params);
        builder.endObject();

        builder.field("verify", verify);
        builder.endObject();
        return builder;
    }
}
