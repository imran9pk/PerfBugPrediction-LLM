package bisq.common.proto.network;

import bisq.common.proto.ProtoResolver;
import bisq.common.proto.ProtobufferException;

import java.time.Clock;


public interface NetworkProtoResolver extends ProtoResolver {
    NetworkEnvelope fromProto(protobuf.NetworkEnvelope proto) throws ProtobufferException;

    NetworkPayload fromProto(protobuf.StoragePayload proto);

    NetworkPayload fromProto(protobuf.StorageEntryWrapper proto);

    Clock getClock();
}
