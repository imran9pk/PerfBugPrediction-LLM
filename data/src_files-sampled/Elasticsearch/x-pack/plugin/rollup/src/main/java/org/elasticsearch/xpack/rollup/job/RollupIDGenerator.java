package org.elasticsearch.xpack.rollup.job;

import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.elasticsearch.common.Numbers;
import org.elasticsearch.common.hash.MurmurHash3;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.CRC32;

public abstract class RollupIDGenerator {
    public abstract void add(Integer v);
    public abstract void add(Long v);
    public abstract void add(Double v);
    public abstract void add(String v);
    public abstract void addNull();
    public abstract String getID();

    private boolean generated = false;

    final boolean check(Object v) {
        if (generated) {
            throw new RuntimeException("Cannot update ID as it has already been generated.");
        }
        if (v == null) {
            addNull();
            return false;
        }
        return true;
    }

    final void setFlag() {
        if (generated) {
            throw new RuntimeException("Cannot generate ID as it has already been generated.");
        }
        generated = true;
    }

    @Deprecated
    public static class CRC extends RollupIDGenerator {
        private final CRC32 crc = new CRC32();

        @Override
        public void add(Integer v) {
            if (check(v)) {
                crc.update(v);
            }
        }

        @Override
        public void add(Long v) {
            if (check(v)) {
                crc.update(Numbers.longToBytes(v), 0, 8);
            }
        }

        @Override
        public void add(Double v) {
            if (check(v)) {
                crc.update(Numbers.doubleToBytes(v), 0, 8);
            }
        }

        @Override
        public void add(String v) {
            if (check(v)) {
                byte[] vs = (v).getBytes(StandardCharsets.UTF_8);
                crc.update(vs, 0, vs.length);
            }
        }

        @Override
        public void addNull() {
            crc.update(19);
        }

        @Override
        public String getID() {
            setFlag();
            return String.valueOf(crc.getValue());
        }
    }

    public static class Murmur3 extends RollupIDGenerator {
        private static final long SEED = 19;
        private static final BytesRef DELIM = new BytesRef("$");
        private static final BytesRef NULL_PLACEHOLDER = new BytesRef("__NULL_PLACEHOLDER__830f1de2__");
        private final BytesRefBuilder id = new BytesRefBuilder();
        private final String jobId;

        Murmur3(String jobId) {
            this.jobId = jobId;
        }

        @Override
        public void add(Integer v) {
            if (check(v)) {
                update(Numbers.intToBytes(v));
            }
        }

        @Override
        public void add(Long v) {
            if (check(v)) {
                update(Numbers.longToBytes(v));
            }
        }

        @Override
        public void add(Double v) {
            if (check(v)) {
                update(Numbers.doubleToBytes(v));
            }
        }

        @Override
        public void add(String v) {
            if (check(v)) {
                update((v).getBytes(StandardCharsets.UTF_8));
            }
        }

        @Override
        public void addNull() {
            update(NULL_PLACEHOLDER.bytes);
        }

        private void update(byte[] v) {
            id.append(v, 0, v.length);
            id.append(DELIM);
        }

        @Override
        public String getID() {
            setFlag();
            MurmurHash3.Hash128 hasher
                = MurmurHash3.hash128(id.bytes(), 0, id.length(), SEED, new MurmurHash3.Hash128());
            byte[] hashedBytes = new byte[16];
            System.arraycopy(Numbers.longToBytes(hasher.h1), 0, hashedBytes, 0, 8);
            System.arraycopy(Numbers.longToBytes(hasher.h2), 0, hashedBytes, 8, 8);
            return jobId + "$" + Base64.getUrlEncoder().withoutPadding().encodeToString(hashedBytes);

        }
    }
}
