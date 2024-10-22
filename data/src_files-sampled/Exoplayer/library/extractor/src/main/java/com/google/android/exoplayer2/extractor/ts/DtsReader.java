package com.google.android.exoplayer2.extractor.ts;

import static java.lang.Math.min;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.DtsUtil;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ParsableByteArray;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

public final class DtsReader implements ElementaryStreamReader {

  private static final int STATE_FINDING_SYNC = 0;
  private static final int STATE_READING_HEADER = 1;
  private static final int STATE_READING_SAMPLE = 2;

  private static final int HEADER_SIZE = 18;

  private final ParsableByteArray headerScratchBytes;
  @Nullable private final String language;

  private @MonotonicNonNull String formatId;
  private @MonotonicNonNull TrackOutput output;

  private int state;
  private int bytesRead;

  private int syncBytes;

  private long sampleDurationUs;
  private @MonotonicNonNull Format format;
  private int sampleSize;

  private long timeUs;

  public DtsReader(@Nullable String language) {
    headerScratchBytes = new ParsableByteArray(new byte[HEADER_SIZE]);
    state = STATE_FINDING_SYNC;
    timeUs = C.TIME_UNSET;
    this.language = language;
  }

  @Override
  public void seek() {
    state = STATE_FINDING_SYNC;
    bytesRead = 0;
    syncBytes = 0;
    timeUs = C.TIME_UNSET;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
    idGenerator.generateNewId();
    formatId = idGenerator.getFormatId();
    output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_AUDIO);
  }

  @Override
  public void packetStarted(long pesTimeUs, @TsPayloadReader.Flags int flags) {
    if (pesTimeUs != C.TIME_UNSET) {
      timeUs = pesTimeUs;
    }
  }

  @Override
  public void consume(ParsableByteArray data) {
    Assertions.checkStateNotNull(output); while (data.bytesLeft() > 0) {
      switch (state) {
        case STATE_FINDING_SYNC:
          if (skipToNextSync(data)) {
            state = STATE_READING_HEADER;
          }
          break;
        case STATE_READING_HEADER:
          if (continueRead(data, headerScratchBytes.getData(), HEADER_SIZE)) {
            parseHeader();
            headerScratchBytes.setPosition(0);
            output.sampleData(headerScratchBytes, HEADER_SIZE);
            state = STATE_READING_SAMPLE;
          }
          break;
        case STATE_READING_SAMPLE:
          int bytesToRead = min(data.bytesLeft(), sampleSize - bytesRead);
          output.sampleData(data, bytesToRead);
          bytesRead += bytesToRead;
          if (bytesRead == sampleSize) {
            if (timeUs != C.TIME_UNSET) {
              output.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, sampleSize, 0, null);
              timeUs += sampleDurationUs;
            }
            state = STATE_FINDING_SYNC;
          }
          break;
        default:
          throw new IllegalStateException();
      }
    }
  }

  @Override
  public void packetFinished() {
    }

  private boolean continueRead(ParsableByteArray source, byte[] target, int targetLength) {
    int bytesToRead = min(source.bytesLeft(), targetLength - bytesRead);
    source.readBytes(target, bytesRead, bytesToRead);
    bytesRead += bytesToRead;
    return bytesRead == targetLength;
  }

  private boolean skipToNextSync(ParsableByteArray pesBuffer) {
    while (pesBuffer.bytesLeft() > 0) {
      syncBytes <<= 8;
      syncBytes |= pesBuffer.readUnsignedByte();
      if (DtsUtil.isSyncWord(syncBytes)) {
        byte[] headerData = headerScratchBytes.getData();
        headerData[0] = (byte) ((syncBytes >> 24) & 0xFF);
        headerData[1] = (byte) ((syncBytes >> 16) & 0xFF);
        headerData[2] = (byte) ((syncBytes >> 8) & 0xFF);
        headerData[3] = (byte) (syncBytes & 0xFF);
        bytesRead = 4;
        syncBytes = 0;
        return true;
      }
    }
    return false;
  }

  @RequiresNonNull("output")
  private void parseHeader() {
    byte[] frameData = headerScratchBytes.getData();
    if (format == null) {
      format = DtsUtil.parseDtsFormat(frameData, formatId, language, null);
      output.format(format);
    }
    sampleSize = DtsUtil.getDtsFrameSize(frameData);
    sampleDurationUs =
        (int)
            (C.MICROS_PER_SECOND * DtsUtil.parseDtsAudioSampleCount(frameData) / format.sampleRate);
  }
}
