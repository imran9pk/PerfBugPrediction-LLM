package org.dcache.ftp.client.dc;

import org.dcache.ftp.client.DataSink;
import org.dcache.ftp.client.DataSource;
import org.dcache.ftp.client.vanilla.BasicServerControlChannel;

public interface TransferThreadFactory {

    TransferThread
    getTransferSinkThread(DataChannel dataChannel,
          SocketBox socketBox,
          DataSink sink,
          BasicServerControlChannel localControlChannel,
          TransferContext context) throws Exception;

    TransferThread
    getTransferSourceThread(DataChannel dataChannel,
          SocketBox socketBox,
          DataSource source,
          BasicServerControlChannel localControlChannel,
          TransferContext context) throws Exception;

}

