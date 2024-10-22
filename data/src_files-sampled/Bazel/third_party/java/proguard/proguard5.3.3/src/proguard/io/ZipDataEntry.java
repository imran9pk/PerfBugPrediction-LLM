package proguard.io;

import proguard.classfile.ClassConstants;

import java.io.*;
import java.util.zip.*;

public class ZipDataEntry implements DataEntry
{
    private final DataEntry      parent;
    private final ZipEntry       zipEntry;
    private       ZipInputStream zipInputStream;
    private       InputStream    bufferedInputStream;


    public ZipDataEntry(DataEntry      parent,
                        ZipEntry       zipEntry,
                        ZipInputStream zipInputStream)
    {
        this.parent         = parent;
        this.zipEntry       = zipEntry;
        this.zipInputStream = zipInputStream;
    }


    public String getName()
    {
        String name = zipEntry.getName()
            .replace(File.separatorChar, ClassConstants.PACKAGE_SEPARATOR);

        int length = name.length();
        return length > 0 &&
               name.charAt(length-1) == ClassConstants.PACKAGE_SEPARATOR ?
                   name.substring(0, length -1) :
                   name;
    }


    public boolean isDirectory()
    {
        return zipEntry.isDirectory();
    }


    public InputStream getInputStream() throws IOException
    {
        if (bufferedInputStream == null)
        {
            bufferedInputStream = new BufferedInputStream(zipInputStream);
        }

        return bufferedInputStream;
    }


    public void closeInputStream() throws IOException
    {
        zipInputStream.closeEntry();
        zipInputStream      = null;
        bufferedInputStream = null;
    }


    public DataEntry getParent()
    {
        return parent;
    }


    public String toString()
    {
        return parent.toString() + ':' + getName();
    }
}
