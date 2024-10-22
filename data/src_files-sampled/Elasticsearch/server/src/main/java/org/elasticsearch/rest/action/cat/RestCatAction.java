package org.elasticsearch.rest.action.cat;

import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;

import java.io.IOException;
import java.util.List;

import static org.elasticsearch.rest.RestRequest.Method.GET;

public class RestCatAction extends BaseRestHandler {

    private static final String CAT = "=^.^=";
    private static final String CAT_NL = CAT + "\n";
    private final String HELP;

    @Inject
    public RestCatAction(Settings settings, RestController controller, List<AbstractCatAction> catActions) {
        super(settings);
        controller.registerHandler(GET, "/_cat", this);
        StringBuilder sb = new StringBuilder();
        sb.append(CAT_NL);
        for (AbstractCatAction catAction : catActions) {
            catAction.documentation(sb);
        }
        HELP = sb.toString();
    }

    @Override
    public String getName() {
        return "cat_action";
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.OK, HELP));
    }

}
