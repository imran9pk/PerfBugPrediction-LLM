package com.codename1.io.tar;

import com.codename1.io.FileSystemStorage;

public class TarHeader {

    public static final int NAMELEN = 100;
    public static final int MODELEN = 8;
    public static final int UIDLEN = 8;
    public static final int GIDLEN = 8;
    public static final int SIZELEN = 12;
    public static final int MODTIMELEN = 12;
    public static final int CHKSUMLEN = 8;
    public static final byte LF_OLDNORM = 0;

    public static final byte LF_NORMAL = (byte) '0';
    public static final byte LF_LINK = (byte) '1';
    public static final byte LF_SYMLINK = (byte) '2';
    public static final byte LF_CHR = (byte) '3';
    public static final byte LF_BLK = (byte) '4';
    public static final byte LF_DIR = (byte) '5';
    public static final byte LF_FIFO = (byte) '6';
    public static final byte LF_CONTIG = (byte) '7';

    public static final int MAGICLEN = 8;
    public static final String TMAGIC = "ustar";

    public static final String GNU_TMAGIC = "ustar  ";

    public static final int UNAMELEN = 32;
    public static final int GNAMELEN = 32;
    public static final int DEVLEN = 8;

    public StringBuffer name;
    public int mode;
    public int userId;
    public int groupId;
    public long size;
    public long modTime;
    public int checkSum;
    public byte linkFlag;
    public StringBuffer linkName;
    public StringBuffer magic;
    public StringBuffer userName;
    public StringBuffer groupName;
    public int devMajor;
    public int devMinor;

    public TarHeader() {
        this.magic = new StringBuffer( TarHeader.TMAGIC );

        this.name = new StringBuffer();
        this.linkName = new StringBuffer();

        String user = "";

        if (user.length() > 31)
            user = user.substring( 0, 31 );

        this.userId = 0;
        this.groupId = 0;
        this.userName = new StringBuffer( user );
        this.groupName = new StringBuffer( "" );
    }

    public static StringBuffer parseName(byte[] header, int offset, int length) {
        StringBuffer result = new StringBuffer( length );

        int end = offset + length;
        for (int i = offset; i < end; ++i) {
            if (header[i] == 0)
                break;
            result.append( (char) header[i] );
        }

        return result;
    }

    public static int getNameBytes(StringBuffer name, byte[] buf, int offset, int length) {
        int i;

        int nlen = name.length();
        for (i = 0; i < length && i < nlen; ++i) {
            buf[offset + i] = (byte) name.charAt( i );
        }

        for (; i < length; ++i) {
            buf[offset + i] = 0;
        }

        return offset + length;
    }

}