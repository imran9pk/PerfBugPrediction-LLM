package com.google.devtools.build.android.ziputils;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;

public class DirectoryEntry extends View<DirectoryEntry> {

  public static DirectoryEntry viewOf(ByteBuffer buffer) {
    DirectoryEntry view = new DirectoryEntry(buffer.slice());
    int size = view.getSize();
    buffer.position(buffer.position() + size);
    view.buffer.position(0).limit(size);
    return view;
  }

  private DirectoryEntry(ByteBuffer buffer) {
    super(buffer);
  }

  public static DirectoryEntry allocate(String name, byte[] extraData, String comment) {
    byte[] nameData = name.getBytes(UTF_8);
    byte[] commentData = comment != null ? comment.getBytes(UTF_8) : EMPTY;
    if (extraData == null) {
      extraData = EMPTY;
    }
    int size = SIZE + nameData.length + extraData.length + commentData.length;
    ByteBuffer buffer = ByteBuffer.allocate(size).order(LITTLE_ENDIAN);
    return new DirectoryEntry(buffer).init(nameData, extraData, commentData, size);
  }

  public static DirectoryEntry view(ByteBuffer buffer, String name, byte[] extraData,
      String comment) {
    byte[] nameData = name.getBytes(UTF_8);
    byte[] commentData = comment != null ? comment.getBytes(UTF_8) : EMPTY;
    if (extraData == null) {
      extraData = EMPTY;
    }
    int size = SIZE + nameData.length + extraData.length + commentData.length;
    DirectoryEntry view = new DirectoryEntry(buffer.slice()).init(nameData, extraData,
        commentData, size);
    buffer.position(buffer.position() + size);
    return view;
  }

  public DirectoryEntry clone(String name, byte[] extraData, String comment) {
    return DirectoryEntry.allocate(name, extraData, comment).copy(this, CENOFF, CENCRC, CENSIZ,
        CENLEN, CENFLG, CENHOW, CENTIM, CENVER, CENVER, CENDSK, CENATX, CENATT);
  }
  
  public DirectoryEntry copy(ByteBuffer buffer) {
    int size = getSize();
    DirectoryEntry view = new DirectoryEntry(buffer.slice());
    this.buffer.rewind();
    view.buffer.put(this.buffer).flip();
    buffer.position(buffer.position() + size);
    this.buffer.rewind().limit(size);
    return view;
  }

  private DirectoryEntry init(byte[] name, byte[] extra, byte[] comment, int size) {
    buffer.putInt(0, SIGNATURE);
    set(CENNAM, (short) name.length);
    set(CENEXT, (short) extra.length);
    set(CENCOM, (short) comment.length);
    buffer.position(SIZE);
    buffer.put(name);
    if (extra.length > 0) {
      buffer.put(extra);
    }
    if (comment.length > 0) {
      buffer.put(comment);
    }
    buffer.position(0).limit(size);
    return this;
  }

  public final int getSize() {
    return SIZE + get(CENNAM) + get(CENEXT) + get(CENCOM);
  }

  public static final int SIGNATURE = 0x02014b50; public static final int SIZE = 46;

  public static final IntFieldId<DirectoryEntry> CENSIG = new IntFieldId<>(0);

  public static final ShortFieldId<DirectoryEntry> CENVEM = new ShortFieldId<>(4);

  public static final ShortFieldId<DirectoryEntry> CENVER = new ShortFieldId<>(6);

  public static final ShortFieldId<DirectoryEntry> CENFLG = new ShortFieldId<>(8);

  public static final ShortFieldId<DirectoryEntry> CENHOW = new ShortFieldId<>(10);

  public static final IntFieldId<DirectoryEntry> CENTIM = new IntFieldId<>(12);

  public static final IntFieldId<DirectoryEntry> CENCRC = new IntFieldId<>(16);

  public static final IntFieldId<DirectoryEntry> CENSIZ = new IntFieldId<>(20);

  public static final IntFieldId<DirectoryEntry> CENLEN = new IntFieldId<>(24);

  public static final ShortFieldId<DirectoryEntry> CENNAM = new ShortFieldId<>(28);

  public static final ShortFieldId<DirectoryEntry> CENEXT = new ShortFieldId<>(30);

  public static final ShortFieldId<DirectoryEntry> CENCOM = new ShortFieldId<>(32);

  public static final ShortFieldId<DirectoryEntry> CENDSK = new ShortFieldId<>(34);

  public static final ShortFieldId<DirectoryEntry> CENATT = new ShortFieldId<>(36);

  public static final IntFieldId<DirectoryEntry> CENATX = new IntFieldId<>(38);

  public static final IntFieldId<DirectoryEntry> CENOFF = new IntFieldId<>(42);

  public final String getFilename() {
    return getString(SIZE, get(CENNAM));
  }

  public final byte[] getExtraData() {
    return getBytes(SIZE + get(CENNAM), get(CENEXT));
  }

  public final String getComment() {
    return getString(SIZE + get(CENNAM) + get(CENEXT), get(CENCOM));
  }

  public int dataSize() {
    return get(CENHOW) == 0 ? get(CENLEN) : get(CENSIZ);
  }
}
