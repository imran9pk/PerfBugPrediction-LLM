package com.google.devtools.build.lib.buildeventservice;

import com.google.common.base.MoreObjects;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.build.lib.buildeventstream.BuildEvent;
import com.google.devtools.build.lib.buildeventstream.PathConverter;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import javax.annotation.concurrent.Immutable;

public class BuildEventServiceUploaderCommands {
  interface EventLoopCommand {
    enum Type {
      OPEN_STREAM,
      SEND_REGULAR_BUILD_EVENT,
      ACK_RECEIVED,
      SEND_LAST_BUILD_EVENT,
      STREAM_COMPLETE
    }

    Type type();
  }

  @Immutable
  static final class OpenStreamCommand implements EventLoopCommand {
    @Override
    public Type type() {
      return Type.OPEN_STREAM;
    }
  }

  @Immutable
  static final class StreamCompleteCommand implements EventLoopCommand {
    private final Status status;

    StreamCompleteCommand(Status status) {
      this.status = status;
    }

    public Status status() {
      return status;
    }

    @Override
    public Type type() {
      return Type.STREAM_COMPLETE;
    }
  }

  @Immutable
  static final class AckReceivedCommand implements EventLoopCommand {
    private final long sequenceNumber;

    AckReceivedCommand(long sequenceNumber) {
      this.sequenceNumber = sequenceNumber;
    }

    public long getSequenceNumber() {
      return sequenceNumber;
    }

    @Override
    public Type type() {
      return Type.ACK_RECEIVED;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("seq_num", getSequenceNumber()).toString();
    }
  }

  abstract static class SendBuildEventCommand implements EventLoopCommand {
    abstract long getSequenceNumber();

    abstract Timestamp getCreationTime();

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("seq_num", getSequenceNumber()).toString();
    }
  }

  static final class SendRegularBuildEventCommand extends SendBuildEventCommand {
    private final BuildEvent event;
    private final ListenableFuture<PathConverter> localFileUpload;
    private final long sequenceNumber;
    private final Timestamp creationTime;

    SendRegularBuildEventCommand(
        BuildEvent event,
        ListenableFuture<PathConverter> localFileUpload,
        long sequenceNumber,
        Timestamp creationTime) {
      this.event = event;
      this.localFileUpload = localFileUpload;
      this.sequenceNumber = sequenceNumber;
      this.creationTime = creationTime;
    }

    BuildEvent getEvent() {
      return event;
    }

    ListenableFuture<PathConverter> localFileUploadProgress() {
      return localFileUpload;
    }

    @Override
    public long getSequenceNumber() {
      return sequenceNumber;
    }

    @Override
    Timestamp getCreationTime() {
      return creationTime;
    }

    @Override
    public Type type() {
      return Type.SEND_REGULAR_BUILD_EVENT;
    }

    @Override
    public String toString() {
      return super.toString() + " - [" + event + "]";
    }
  }

  @Immutable
  static final class SendLastBuildEventCommand extends SendBuildEventCommand {
    private final long sequenceNumber;
    private final Timestamp creationTime;

    SendLastBuildEventCommand(long sequenceNumber, Timestamp creationTime) {
      this.sequenceNumber = sequenceNumber;
      this.creationTime = creationTime;
    }

    @Override
    Timestamp getCreationTime() {
      return creationTime;
    }

    @Override
    public Type type() {
      return Type.SEND_LAST_BUILD_EVENT;
    }

    @Override
    public long getSequenceNumber() {
      return sequenceNumber;
    }
  }

}
