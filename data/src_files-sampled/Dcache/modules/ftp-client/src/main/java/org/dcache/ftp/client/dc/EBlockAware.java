package org.dcache.ftp.client.dc;

public class EBlockAware {

    public static final int
          EOF = 64,
          EOD = 8,
          WILL_CLOSE = 4;

    protected EBlockParallelTransferContext context;


    public void setTransferContext(EBlockParallelTransferContext context) {
        this.context = context;
    }

}
