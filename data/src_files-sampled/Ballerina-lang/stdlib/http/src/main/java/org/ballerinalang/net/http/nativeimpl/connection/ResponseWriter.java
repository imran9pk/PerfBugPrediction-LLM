package org.ballerinalang.net.http.nativeimpl.connection;

import org.ballerinalang.jvm.BallerinaErrors;
import org.ballerinalang.jvm.values.ArrayValue;
import org.ballerinalang.jvm.values.ErrorValue;
import org.ballerinalang.jvm.values.ObjectValue;
import org.ballerinalang.mime.util.EntityBodyHandler;
import org.ballerinalang.mime.util.HeaderUtil;
import org.ballerinalang.mime.util.MultipartDataSource;
import org.ballerinalang.net.http.DataContext;
import org.ballerinalang.net.http.HttpConstants;
import org.ballerinalang.net.http.HttpErrorType;
import org.ballerinalang.net.http.HttpUtil;
import org.wso2.transport.http.netty.contract.HttpConnectorListener;
import org.wso2.transport.http.netty.contract.HttpResponseFuture;
import org.wso2.transport.http.netty.message.HttpCarbonMessage;
import org.wso2.transport.http.netty.message.HttpMessageDataStreamer;
import org.wso2.transport.http.netty.message.PooledDataStreamerFactory;

import java.io.IOException;
import java.io.OutputStream;

import static org.ballerinalang.mime.util.MimeConstants.SERIALIZATION_ERROR_CODE;
import static org.ballerinalang.net.http.HttpUtil.extractEntity;

public class ResponseWriter {

    public static void sendResponseRobust(DataContext dataContext, HttpCarbonMessage requestMessage,
                                          ObjectValue outboundResponse, HttpCarbonMessage responseMessage) {
        String contentType = HttpUtil.getContentTypeFromTransportMessage(responseMessage);
        String boundaryString = null;
        if (HeaderUtil.isMultipart(contentType)) {
            boundaryString = HttpUtil.addBoundaryIfNotExist(responseMessage, contentType);
        }
        HttpMessageDataStreamer outboundMsgDataStreamer = getResponseDataStreamer(responseMessage);
        ObjectValue entityObj = extractEntity(outboundResponse);
        if (entityObj == null) {
            responseMessage.setPassthrough(true);
        }
        HttpResponseFuture outboundRespStatusFuture = HttpUtil.sendOutboundResponse(requestMessage, responseMessage);
        HttpConnectorListener outboundResStatusConnectorListener =
                new ResponseWriter.HttpResponseConnectorListener(dataContext, outboundMsgDataStreamer);
        outboundRespStatusFuture.setHttpConnectorListener(outboundResStatusConnectorListener);
        OutputStream messageOutputStream = outboundMsgDataStreamer.getOutputStream();
        if (entityObj != null) {
            if (boundaryString != null) {
                serializeMultiparts(boundaryString, entityObj, messageOutputStream);
            } else {
                Object outboundMessageSource = EntityBodyHandler.getMessageDataSource(entityObj);
                serializeDataSource(outboundMessageSource, entityObj, messageOutputStream);
            }
        }
    }

    private static void serializeMultiparts(String boundaryString, ObjectValue entity,
                                            OutputStream messageOutputStream) {
        ArrayValue bodyParts = EntityBodyHandler.getBodyPartArray(entity);
        try {
            if (bodyParts != null && bodyParts.size() > 0) {
                MultipartDataSource multipartDataSource = new MultipartDataSource(entity, boundaryString);
                serializeDataSource(multipartDataSource, entity, messageOutputStream);
                HttpUtil.closeMessageOutputStream(messageOutputStream);
            } else {
                EntityBodyHandler.writeByteChannelToOutputStream(entity, messageOutputStream);
                HttpUtil.closeMessageOutputStream(messageOutputStream);
            }
        } catch (IOException ex) {
            throw BallerinaErrors.createError(SERIALIZATION_ERROR_CODE, "error occurred while serializing " +
                    "byte channel content : " + ex.getMessage());
        }
    }

    static void serializeDataSource(Object outboundMessageSource, ObjectValue entity,
                                    OutputStream messageOutputStream) {
        try {
            if (outboundMessageSource != null) {
                HttpUtil.serializeDataSource(outboundMessageSource, entity, messageOutputStream);
                HttpUtil.closeMessageOutputStream(messageOutputStream);
            } else { EntityBodyHandler.writeByteChannelToOutputStream(entity, messageOutputStream);
                HttpUtil.closeMessageOutputStream(messageOutputStream);
            }
        } catch (IOException ex) {
            throw BallerinaErrors.createError(SERIALIZATION_ERROR_CODE, "error occurred while serializing message" +
                    " data source : " + ex.getMessage());
        }
    }

    static HttpMessageDataStreamer getResponseDataStreamer(HttpCarbonMessage outboundResponse) {
        final HttpMessageDataStreamer outboundMsgDataStreamer;
        final PooledDataStreamerFactory pooledDataStreamerFactory = (PooledDataStreamerFactory)
                outboundResponse.getProperty(HttpConstants.POOLED_BYTE_BUFFER_FACTORY);
        if (pooledDataStreamerFactory != null) {
            outboundMsgDataStreamer = pooledDataStreamerFactory.createHttpDataStreamer(outboundResponse);
        } else {
            outboundMsgDataStreamer = new HttpMessageDataStreamer(outboundResponse);
        }
        return outboundMsgDataStreamer;
    }

    static class HttpResponseConnectorListener implements HttpConnectorListener {

        private final DataContext dataContext;
        private HttpMessageDataStreamer outboundMsgDataStreamer;

        HttpResponseConnectorListener(DataContext dataContext) {
            this.dataContext = dataContext;
        }

        HttpResponseConnectorListener(DataContext dataContext, HttpMessageDataStreamer outboundMsgDataStreamer) {
            this.dataContext = dataContext;
            this.outboundMsgDataStreamer = outboundMsgDataStreamer;
        }

        @Override
        public void onMessage(HttpCarbonMessage httpCarbonMessage) {
            this.dataContext.notifyOutboundResponseStatus(null);
        }

        @Override
        public void onError(Throwable throwable) {
            ErrorValue httpConnectorError = HttpUtil.createHttpError(throwable.getMessage(),
                    HttpErrorType.GENERIC_LISTENER_ERROR);
            if (outboundMsgDataStreamer != null) {
                if (!(throwable instanceof IOException)) {
                    this.dataContext.getOutboundRequest()
                            .setIoException(new IOException(throwable.getMessage(), throwable));
                }
            }
            this.dataContext.notifyOutboundResponseStatus(httpConnectorError);
        }
    }
}
