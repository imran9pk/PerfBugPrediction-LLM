package org.elasticsearch.client;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.elasticsearch.client.migration.DeprecationInfoRequest;
import org.elasticsearch.client.migration.IndexUpgradeInfoRequest;
import org.elasticsearch.client.migration.IndexUpgradeRequest;

final class MigrationRequestConverters {

    private MigrationRequestConverters() {
    }

    static Request getMigrationAssistance(IndexUpgradeInfoRequest indexUpgradeInfoRequest) {
        RequestConverters.EndpointBuilder endpointBuilder = new RequestConverters.EndpointBuilder()
            .addPathPartAsIs("_xpack", "migration", "assistance")
            .addCommaSeparatedPathParts(indexUpgradeInfoRequest.indices());
        String endpoint = endpointBuilder.build();
        Request request = new Request(HttpGet.METHOD_NAME, endpoint);
        RequestConverters.Params parameters = new RequestConverters.Params(request);
        parameters.withIndicesOptions(indexUpgradeInfoRequest.indicesOptions());
        return request;
    }

    static Request migrate(IndexUpgradeRequest indexUpgradeRequest) {
        return prepareMigrateRequest(indexUpgradeRequest, true);
    }

    static Request submitMigrateTask(IndexUpgradeRequest indexUpgradeRequest) {
        return prepareMigrateRequest(indexUpgradeRequest, false);
    }

    static Request getDeprecationInfo(DeprecationInfoRequest deprecationInfoRequest) {
        String endpoint = new RequestConverters.EndpointBuilder()
            .addCommaSeparatedPathParts(deprecationInfoRequest.getIndices())
            .addPathPartAsIs("_xpack", "migration", "deprecations")
            .build();

        return new Request(HttpGet.METHOD_NAME, endpoint);
    }

    private static Request prepareMigrateRequest(IndexUpgradeRequest indexUpgradeRequest, boolean waitForCompletion) {
        String endpoint = new RequestConverters.EndpointBuilder()
            .addPathPartAsIs("_xpack", "migration", "upgrade")
            .addPathPart(indexUpgradeRequest.index())
            .build();

        Request request = new Request(HttpPost.METHOD_NAME, endpoint);

        RequestConverters.Params params = new RequestConverters.Params(request)
            .withWaitForCompletion(waitForCompletion);

        return request;
    }
}
