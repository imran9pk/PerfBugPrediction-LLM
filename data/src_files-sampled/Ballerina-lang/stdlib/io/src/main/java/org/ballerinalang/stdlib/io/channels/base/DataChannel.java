package org.ballerinalang.stdlib.io.channels.base;

import org.ballerinalang.stdlib.io.channels.base.data.LongResult;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class DataChannel implements IOChannel {
    private Channel channel;
    private ByteOrder order;

    private static final long BIT_64_LONG_MAX = 0xFFFFFFFFFFFFFFFFL;

    public DataChannel(Channel channel, ByteOrder order) {
        this.channel = channel;
        this.order = order;
    }

    @Override
    public boolean hasReachedEnd() {
        return channel.hasReachedEnd();
    }

    public Channel getChannel() {
        return channel;
    }

    private byte[] reverse(ByteBuffer buffer) {
        byte[] contentArr = buffer.array();
        int length = buffer.limit();
        byte[] reverseContent = new byte[length];
        for (int count = 0; count < length; count++) {
            reverseContent[count] = contentArr[(length - 1) - count];
        }
        return reverseContent;
    }

    private void readFull(ByteBuffer buffer, Representation representation) throws IOException {
        do {
            channel.read(buffer);
        } while (buffer.hasRemaining() && !channel.hasReachedEnd());
        if (order.equals(ByteOrder.LITTLE_ENDIAN) && !representation.equals(Representation.VARIABLE)) {
            int bufferPosition = buffer.position();
            int limit = buffer.limit();
            byte[] reverseContent = reverse(buffer);
            buffer.rewind();
            buffer.put(reverseContent, 0, limit);
            buffer.position(bufferPosition);
            buffer.limit(limit);
        }
    }

    private ByteBuffer readVarInt() throws IOException {
        int bufferLimit = 0;
        boolean hasRemainingBytes = true;
        byte[] content = new byte[Long.BYTES];
        ByteBuffer buf = ByteBuffer.wrap(content);
        do {
            buf.limit(++bufferLimit);
            readFull(buf, Representation.VARIABLE);
            buf.flip();
            byte b = buf.get(bufferLimit - 1);
            if ((b & 0x80) >> 7 == 0) {
                hasRemainingBytes = false;
            }
            buf.put(bufferLimit - 1, (byte) (b & 0x7F));
            buf.position(buf.limit());
        } while (hasRemainingBytes);
        return buf;
    }

    private LongResult decodeLong(Representation representation) throws IOException {
        ByteBuffer buffer;
        int requiredNumberOfBytes;
        if (Representation.VARIABLE.equals(representation)) {
            buffer = readVarInt();
            if (order.equals(ByteOrder.LITTLE_ENDIAN)) {
                byte[] reverse = reverse(buffer);
                buffer.rewind();
                buffer.put(reverse);
            }
        } else {
            requiredNumberOfBytes = representation.getNumberOfBytes();
            buffer = ByteBuffer.allocate(requiredNumberOfBytes);
            buffer.order(order);
            readFull(buffer, representation);
        }
        buffer.flip();
        return deriveLong(representation, buffer);
    }

    private long convertVarLongToFixedLong(long value, int nBytes) {
        int nBits = nBytes * Representation.VARIABLE.getBase() - 1;
        if (value >> nBits == 1) {
            long intercept = BIT_64_LONG_MAX << nBits;
            value = value | intercept;
        }
        return value;
    }

    private LongResult deriveLong(Representation representation, ByteBuffer buffer) {
        long value = 0;
        int maxNumberOfBits = 0xFFFF;
        int byteLimit = buffer.limit();
        int totalNumberOfBits = (byteLimit - 1) * representation.getBase();
        do {
            long shiftedValue = 0L;
            if (Representation.BIT_64.equals(representation)) {
                long flippedValue = (buffer.get() & maxNumberOfBits);
                shiftedValue = flippedValue << totalNumberOfBits;
            } else if (Representation.BIT_32.equals(representation)) {
                int flippedValue = (buffer.get() & maxNumberOfBits);
                shiftedValue = flippedValue << totalNumberOfBits;
            } else if (Representation.BIT_16.equals(representation)) {
                short flippedValue = (short) (buffer.get() & maxNumberOfBits);
                shiftedValue = flippedValue << totalNumberOfBits;
            } else if (Representation.VARIABLE.equals(representation)) {
                long flippedValue = (buffer.get() & maxNumberOfBits);
                shiftedValue = flippedValue << totalNumberOfBits;
            }
            maxNumberOfBits = 0xFF;
            value = value + shiftedValue;
            totalNumberOfBits = totalNumberOfBits - representation.getBase();
        } while (buffer.hasRemaining());
        if (Representation.VARIABLE.equals(representation)) {
            value = convertVarLongToFixedLong(value, byteLimit);
        }
        return new LongResult(value, byteLimit);
    }

    private byte[] reverseOrder(byte[] content) {
        byte[] reversedContent = new byte[content.length];
        int length = content.length;
        for (int count = 0; count < length; count++) {
            reversedContent[count] = (byte) (content[(length - 1) - count] & 0x7F);
            if (count < (length - 1)) {
                reversedContent[count] = (byte) (content[(length - 1) - count] | 0x80);
            }
        }
        return reversedContent;
    }

    private byte[] encodeLong(long value, Representation representation) {
        byte[] content;
        int nBytes;
        int totalNumberOfBits;
        if (Representation.VARIABLE.equals(representation)) {
            int nBits = (int) Math.abs(Math.round((Math.log(Math.abs(value)) / Math.log(2))));
            nBytes = nBits / representation.getBase() + 1;
            content = new byte[nBytes];
        } else {
            nBytes = representation.getNumberOfBytes();
            content = new byte[representation.getNumberOfBytes()];
        }
        totalNumberOfBits = (nBytes * representation.getBase()) - representation.getBase();
        for (int count = 0; count < nBytes; count++) {
            content[count] = (byte) (value >> totalNumberOfBits);
            if (Representation.VARIABLE.equals(representation) && order.equals(ByteOrder.BIG_ENDIAN)) {
                content[count] = (byte) (content[count] & 0x7F);
                if (count < (nBytes - 1)) {
                    content[count] = (byte) (content[count] | 0x80);
                }
            }
            totalNumberOfBits = totalNumberOfBits - representation.getBase();
        }
        return content;
    }

    private void write(ByteBuffer buffer, Representation representation) throws IOException {
        if (order.equals(ByteOrder.LITTLE_ENDIAN) && !Representation.VARIABLE.equals(representation)) {
            byte[] reverse = reverse(buffer);
            channel.write(ByteBuffer.wrap(reverse));
        } else if (order.equals(ByteOrder.LITTLE_ENDIAN) && Representation.VARIABLE.equals(representation)) {
            byte[] reverse = reverseOrder(buffer.array());
            channel.write(ByteBuffer.wrap(reverse));
        } else {
            channel.write(buffer);
        }
    }

    public void writeLong(long value, Representation representation) throws IOException {
        byte[] bytes = encodeLong(value, representation);
        write(ByteBuffer.wrap(bytes), representation);
    }

    public LongResult readLong(Representation representation) throws IOException {
        return decodeLong(representation);
    }

    public void writeDouble(double value, Representation representation) throws IOException {
        long lValue;
        if (Representation.BIT_32.equals(representation)) {
            lValue = Float.floatToIntBits((float) value);
        } else {
            lValue = Double.doubleToRawLongBits(value);
        }
        writeLong(lValue, representation);
    }

    public double readDouble(Representation representation) throws IOException {
        if (Representation.BIT_32.equals(representation)) {
            int fValue = (int) readLong(Representation.BIT_32).getValue();
            return Float.intBitsToFloat(fValue);
        } else {
            long lValue = readLong(representation).getValue();
            return Double.longBitsToDouble(lValue);
        }
    }

    public void writeBoolean(boolean value) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1);
        byte booleanValue = (byte) (value ? 1 : 0);
        buffer.put(booleanValue);
        buffer.flip();
        write(buffer, Representation.NONE);
    }

    public boolean readBoolean() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1);
        readFull(buffer, Representation.NONE);
        buffer.flip();
        return buffer.get() == 1;
    }

    public void writeString(String content, String encoding) throws IOException {
        CharacterChannel ch = new CharacterChannel(this.channel, encoding);
        ch.write(content, 0);
    }

    public String readString(int nBytes, String encoding) throws IOException {
        CharacterChannel ch = new CharacterChannel(this.channel, encoding);
        return ch.readAllChars(nBytes);
    }

    public int id() {
        return channel.id();
    }

    public void close() throws IOException {
        this.channel.close();
    }

    @Override
    public boolean remaining() {
        return false;
    }
}
