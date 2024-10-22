package com.github.benmanes.caffeine.cache.simulator.parser.camelab;

import java.util.stream.LongStream;

import com.github.benmanes.caffeine.cache.simulator.parser.TextTraceReader;
import com.github.benmanes.caffeine.cache.simulator.parser.TraceReader.KeyOnlyTraceReader;

public final class CamelabTraceReader extends TextTraceReader implements KeyOnlyTraceReader {
  static final long BLOCK_SIZE = 512;

  public CamelabTraceReader(String filePath) {
    super(filePath);
  }

  @Override
  public LongStream keys() {
    return lines().flatMapToLong(line -> {
      String[] array = line.split(" ", 5);
      char readWrite = Character.toLowerCase(array[1].charAt(0));
      if (readWrite == 'w') {
        return LongStream.empty();
      }

      long startAddress = Long.parseLong(array[2]);
      int requestSize = Integer.parseInt(array[3]);
      long[] blocks = new long[requestSize];
      for (int i = 0; i < requestSize; i++) {
        blocks[i] = startAddress + (i * BLOCK_SIZE);
      }
      return LongStream.of(blocks);
    });
  }
}
