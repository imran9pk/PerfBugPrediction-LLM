package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

@AutoValue
public abstract class JsMessage {

  private static final String PH_JS_PREFIX = "{$";
  private static final String PH_JS_SUFFIX = "}";

  public static final class PlaceholderFormatException extends Exception {

    public PlaceholderFormatException() {}
  }

  public enum Style {
    LEGACY, RELAX, CLOSURE; 

  private static final String MESSAGE_REPRESENTATION_FORMAT = "{$%s}";

  @Nullable
  public abstract String getSourceName();

  public abstract String getKey();

  public abstract boolean isAnonymous();

  public abstract boolean isExternal();

  public abstract String getId();

  public abstract ImmutableList<CharSequence> getParts();

  @Nullable
  public abstract String getAlternateId();

  @Nullable
  public abstract String getDesc();

  @Nullable
  public abstract String getMeaning();

  public abstract boolean isHidden();

  @Deprecated
  public final ImmutableList<CharSequence> parts() {
    return getParts();
  }

  public abstract ImmutableSet<String> placeholders();

  @Override
  public final String toString() {
    StringBuilder sb = new StringBuilder();
    for (CharSequence p : getParts()) {
      sb.append(p.toString());
    }
    return sb.toString();
  }

  public final boolean isEmpty() {
    for (CharSequence part : getParts()) {
      if (part.length() > 0) {
        return false;
      }
    }

    return true;
  }

  @AutoValue
  public abstract static class PlaceholderReference implements CharSequence {

    static PlaceholderReference create(String name) {
      return new AutoValue_JsMessage_PlaceholderReference(name);
    }

    @Override
    public int length() {
      return getName().length();
    }

    @Override
    public char charAt(int index) {
      return getName().charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return getName().subSequence(start, end);
    }

    public abstract String getName();

    @Override
    public final String toString() {
      return SimpleFormat.format(MESSAGE_REPRESENTATION_FORMAT, getName());
    }
  }

  @GwtIncompatible("java.util.regex")
  public static final class Builder {

    private static final String MSG_EXTERNAL_PREFIX = "MSG_EXTERNAL_";

    private static String getExternalMessageId(String identifier) {
      if (identifier.startsWith(MSG_EXTERNAL_PREFIX)) {
        int start = MSG_EXTERNAL_PREFIX.length();
        int end = start;
        for (; end < identifier.length(); end++) {
          char c = identifier.charAt(end);
          if (c > '9' || c < '0') {
            break;
          }
        }
        if (end > start) {
          return identifier.substring(start, end);
        }
      }
      return null;
    }

    private String key;

    private String meaning;

    private String desc;
    private boolean hidden;

    private String alternateId;

    private final List<CharSequence> parts = new ArrayList<>();
    private final Set<String> placeholders = new HashSet<>();

    private String sourceName;

    public Builder() {
      this(null);
    }

    public Builder(String key) {
      this.key = key;
    }

    public String getKey() {
      return key;
    }

    public Builder setKey(String key) {
      this.key = key;
      return this;
    }

    public Builder setSourceName(String sourceName) {
      this.sourceName = sourceName;
      return this;
    }

    public Builder setMsgText(String msgText) throws PlaceholderFormatException {
      checkState(this.parts.isEmpty(), "cannot parse msg text after adding parts");
      parseMsgTextIntoParts(msgText);
      return this;
    }

    private void parseMsgTextIntoParts(String msgText) throws PlaceholderFormatException {
      while (true) {
        int phBegin = msgText.indexOf(PH_JS_PREFIX);
        if (phBegin < 0) {
          appendStringPart(msgText);
          return;
        } else {
          if (phBegin > 0) {
            appendStringPart(msgText.substring(0, phBegin));
          }

          int phEnd = msgText.indexOf(PH_JS_SUFFIX, phBegin);
          if (phEnd < 0) {
            throw new PlaceholderFormatException();
          }

          String phName = msgText.substring(phBegin + PH_JS_PREFIX.length(), phEnd);
          appendPlaceholderReference(phName);
          int nextPos = phEnd + PH_JS_SUFFIX.length();
          if (nextPos < msgText.length()) {
            msgText = msgText.substring(nextPos);
          } else {
            return;
          }
        }
      }
    }

    public Builder appendPlaceholderReference(String name) {
      checkNotNull(name, "Placeholder name could not be null");
      parts.add(PlaceholderReference.create(name));
      placeholders.add(name);
      return this;
    }

    public Builder appendStringPart(String part) {
      checkNotNull(part, "String part of the message could not be null");
      parts.add(part);
      return this;
    }

    public Set<String> getPlaceholders() {
      return placeholders;
    }

    public Builder setDesc(String desc) {
      this.desc = desc;
      return this;
    }

    public Builder setMeaning(String meaning) {
      this.meaning = meaning;
      return this;
    }

    public Builder setAlternateId(String alternateId) {
      this.alternateId = alternateId;
      return this;
    }

    public Builder setIsHidden(boolean hidden) {
      this.hidden = hidden;
      return this;
    }

    public boolean hasParts() {
      return !parts.isEmpty();
    }

    public List<CharSequence> getParts() {
      return parts;
    }

    public JsMessage build() {
      return build(null);
    }

    public JsMessage build(IdGenerator idGenerator) {
      boolean isAnonymous = false;
      boolean isExternal = false;
      String id = null;

      if (getKey() == null) {
        key = JsMessageVisitor.MSG_PREFIX + fingerprint(getParts());
        isAnonymous = true;
      }

      if (!isAnonymous) {
        String externalId = getExternalMessageId(key);
        if (externalId != null) {
          isExternal = true;
          id = externalId;
        }
      }

      if (!isExternal) {
        String defactoMeaning = meaning != null ? meaning : key;
        id = idGenerator == null ? defactoMeaning : idGenerator.generateId(defactoMeaning, parts);
      }

      if ((alternateId != null) && alternateId.equals(id)) {
        alternateId = null;
      }

      return new AutoValue_JsMessage(
          sourceName,
          key,
          isAnonymous,
          isExternal,
          id,
          ImmutableList.copyOf(parts),
          alternateId,
          desc,
          meaning,
          hidden,
          ImmutableSet.copyOf(placeholders));
    }

    private static String fingerprint(List<CharSequence> messageParts) {
      StringBuilder sb = new StringBuilder();
      for (CharSequence part : messageParts) {
        if (part instanceof JsMessage.PlaceholderReference) {
          sb.append(part.toString());
        } else {
          sb.append(part);
        }
      }
      long nonnegativeHash = Long.MAX_VALUE & Hash.hash64(sb.toString());
      return Ascii.toUpperCase(Long.toString(nonnegativeHash, 36));
    }
  }

  static final class Hash {
    private Hash() {}

    private static final long SEED64 = 0x2b992ddfa23249d6L; private static final long CONSTANT64 = 0xe08c1d668b756f82L; static long hash64(@Nullable String value) {
      return hash64(value, SEED64);
    }

    private static long hash64(@Nullable String value, long seed) {
      if (value == null) {
        return hash64(null, 0, 0, seed);
      }
      return hash64(value.getBytes(UTF_8), seed);
    }

    private static long hash64(byte[] value, long seed) {
      return hash64(value, 0, value == null ? 0 : value.length, seed);
    }

    @SuppressWarnings("fallthrough")
    private static long hash64(byte[] value, int offset, int length, long seed) {
      long a = CONSTANT64;
      long b = a;
      long c = seed;
      int keylen;

      for (keylen = length; keylen >= 24; keylen -= 24, offset += 24) {
        a += word64At(value, offset);
        b += word64At(value, offset + 8);
        c += word64At(value, offset + 16);

        a -= b;
        a -= c;
        a ^= c >>> 43;
        b -= c;
        b -= a;
        b ^= a << 9;
        c -= a;
        c -= b;
        c ^= b >>> 8;
        a -= b;
        a -= c;
        a ^= c >>> 38;
        b -= c;
        b -= a;
        b ^= a << 23;
        c -= a;
        c -= b;
        c ^= b >>> 5;
        a -= b;
        a -= c;
        a ^= c >>> 35;
        b -= c;
        b -= a;
        b ^= a << 49;
        c -= a;
        c -= b;
        c ^= b >>> 11;
        a -= b;
        a -= c;
        a ^= c >>> 12;
        b -= c;
        b -= a;
        b ^= a << 18;
        c -= a;
        c -= b;
        c ^= b >>> 22;
      }

      c += length;
      if (keylen >= 16) {
        if (keylen == 23) {
          c += ((long) value[offset + 22]) << 56;
        }
        if (keylen >= 22) {
          c += (value[offset + 21] & 0xffL) << 48;
        }
        if (keylen >= 21) {
          c += (value[offset + 20] & 0xffL) << 40;
        }
        if (keylen >= 20) {
          c += (value[offset + 19] & 0xffL) << 32;
        }
        if (keylen >= 19) {
          c += (value[offset + 18] & 0xffL) << 24;
        }
        if (keylen >= 18) {
          c += (value[offset + 17] & 0xffL) << 16;
        }
        if (keylen >= 17) {
          c += (value[offset + 16] & 0xffL) << 8;
          }
        if (keylen >= 16) {
          b += word64At(value, offset + 8);
          a += word64At(value, offset);
        }
      } else if (keylen >= 8) {
        if (keylen == 15) {
          b += (value[offset + 14] & 0xffL) << 48;
        }
        if (keylen >= 14) {
          b += (value[offset + 13] & 0xffL) << 40;
        }
        if (keylen >= 13) {
          b += (value[offset + 12] & 0xffL) << 32;
        }
        if (keylen >= 12) {
          b += (value[offset + 11] & 0xffL) << 24;
        }
        if (keylen >= 11) {
          b += (value[offset + 10] & 0xffL) << 16;
        }
        if (keylen >= 10) {
          b += (value[offset + 9] & 0xffL) << 8;
        }
        if (keylen >= 9) {
          b += (value[offset + 8] & 0xffL);
        }
        if (keylen >= 8) {
          a += word64At(value, offset);
        }
      } else {
        if (keylen == 7) {
          a += (value[offset + 6] & 0xffL) << 48;
        }
        if (keylen >= 6) {
          a += (value[offset + 5] & 0xffL) << 40;
        }
        if (keylen >= 5) {
          a += (value[offset + 4] & 0xffL) << 32;
        }
        if (keylen >= 4) {
          a += (value[offset + 3] & 0xffL) << 24;
        }
        if (keylen >= 3) {
          a += (value[offset + 2] & 0xffL) << 16;
        }
        if (keylen >= 2) {
          a += (value[offset + 1] & 0xffL) << 8;
        }
        if (keylen >= 1) {
          a += (value[offset + 0] & 0xffL);
          }
      }
      return mix64(a, b, c);
    }

    private static long word64At(byte[] bytes, int offset) {
      return (bytes[offset + 0] & 0xffL)
          + ((bytes[offset + 1] & 0xffL) << 8)
          + ((bytes[offset + 2] & 0xffL) << 16)
          + ((bytes[offset + 3] & 0xffL) << 24)
          + ((bytes[offset + 4] & 0xffL) << 32)
          + ((bytes[offset + 5] & 0xffL) << 40)
          + ((bytes[offset + 6] & 0xffL) << 48)
          + ((bytes[offset + 7] & 0xffL) << 56);
    }

    private static long mix64(long a, long b, long c) {
      a -= b;
      a -= c;
      a ^= c >>> 43;
      b -= c;
      b -= a;
      b ^= a << 9;
      c -= a;
      c -= b;
      c ^= b >>> 8;
      a -= b;
      a -= c;
      a ^= c >>> 38;
      b -= c;
      b -= a;
      b ^= a << 23;
      c -= a;
      c -= b;
      c ^= b >>> 5;
      a -= b;
      a -= c;
      a ^= c >>> 35;
      b -= c;
      b -= a;
      b ^= a << 49;
      c -= a;
      c -= b;
      c ^= b >>> 11;
      a -= b;
      a -= c;
      a ^= c >>> 12;
      b -= c;
      b -= a;
      b ^= a << 18;
      c -= a;
      c -= b;
      c ^= b >>> 22;
      return c;
    }
  }

  public interface IdGenerator {
    String generateId(String meaning, List<CharSequence> messageParts);
  }
}
