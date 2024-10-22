package com.google.devtools.build.lib.util;

import com.google.common.annotations.VisibleForTesting;
import java.math.BigInteger;
import javax.annotation.Nullable;

public final class BigIntegerFingerprintUtils {
  private static final int BITS = 128;
  public static final int BYTES = BITS / 8;

  private static final BigInteger UINT128_LIMIT = BigInteger.ONE.shiftLeft(BITS);
  @VisibleForTesting public static final int RELATIVE_PRIME_INT = 31;
  private static final BigInteger RELATIVE_PRIME = BigInteger.valueOf(RELATIVE_PRIME_INT);

  private BigIntegerFingerprintUtils() {}

  public static BigInteger compose(BigInteger v1, BigInteger v2) {
    return v1.add(v2).mod(UINT128_LIMIT);
  }

  @Nullable
  public static BigInteger composeNullable(@Nullable BigInteger v1, @Nullable BigInteger v2) {
    if (v1 == null || v2 == null) {
      return null;
    }
    return compose(v1, v2);
  }

  public static BigInteger composeOrdered(BigInteger accumulator, BigInteger v) {
    return compose(accumulator.multiply(RELATIVE_PRIME).mod(UINT128_LIMIT), v);
  }

  @Nullable
  public static BigInteger composeOrderedNullable(
      @Nullable BigInteger accumulator, @Nullable BigInteger v) {
    if (accumulator == null || v == null) {
      return null;
    }
    return compose(accumulator.multiply(RELATIVE_PRIME).mod(UINT128_LIMIT), v);
  }

}
