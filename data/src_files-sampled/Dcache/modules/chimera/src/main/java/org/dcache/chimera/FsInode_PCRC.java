package org.dcache.chimera;

import java.util.Iterator;
import java.util.Set;
import org.dcache.util.Checksum;

public class FsInode_PCRC extends FsInode_PGET {

    public FsInode_PCRC(FileSystemProvider fs, long ino) {
        super(fs, ino, FsInodeType.PCRC);
    }

    protected String value() throws ChimeraFsException {
        Set<Checksum> results = _fs.getInodeChecksums(this);
        StringBuilder sb = new StringBuilder();

        Iterator<Checksum> it = results.iterator();
        if (it.hasNext()) {
            Checksum result = it.next();
            sb.append(result.getType()).append(':').append(
                  result.getValue());
        }

        while (it.hasNext()) {
            Checksum result = it.next();
            sb.append(", ").append(result.getType()).append(':').append(
                  result.getValue());
        }

        sb.append(NEWLINE);
        return sb.toString();
    }
}
