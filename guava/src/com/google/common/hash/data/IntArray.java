package com.google.common.hash.data;

import com.google.common.math.LongMath;
import com.google.common.primitives.Ints;

import java.math.RoundingMode;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * @author sebastian.kruse
 * @since 12.06.2015
 */
public final class IntArray implements HashSink<IntArray> {

  public static long sizeFor(long minPositionSize, int bitsPerInt) {
    // Find out, how many longs we will need for our ints.
    long numLongs = ((long) minPositionSize * bitsPerInt + Long.SIZE - 1) / Long.SIZE;

    // Find out, how many ints fit into these longs.
    return numLongs * Long.SIZE / bitsPerInt;
  }

  final long[] data;
  final int bitsPerInt;
  final int intBitMask;
  int numSetPositions = -1;
  final int maxIndex;

  public IntArray(long ints, int bitsPerInt) {
    this(new long[Ints.checkedCast(LongMath.divide(LongMath.checkedMultiply(ints, bitsPerInt), 64, RoundingMode.CEILING))], bitsPerInt);
    this.numSetPositions = 0;
  }

  // Used by serialization
  public IntArray(long[] data, int bitsPerInt) {
    checkArgument(data.length > 0, "data length is zero!");
    this.data = data;
    this.bitsPerInt = bitsPerInt;
    this.intBitMask = (int) ((1L << this.bitsPerInt) - 1);
    this.maxIndex = (this.data.length << 6) / bitsPerInt;
  }

  /**
   * Returns true if the bit changed value.
   */
  public boolean set(long index) {
    // Load the current value.
    long value = get(index) & 0xFFFFFFFFL;
    if (value == 0) {
      if (this.numSetPositions != -1) this.numSetPositions++;
    } else if (value == this.intBitMask) {
      return false;
    }

    // Find the bits, we have to update.
    long incValue = value + 1;
    long updateBitMask = value ^ incValue;

    // Determine the first and last bit index.
    long startIndex = index * this.bitsPerInt;

    // Determine if the int is distributed among two longs.
    int startLong = (int) (startIndex >>> 6);
    int offset = (int) (64 + (startIndex & 0xFFFFFFC0L) - startIndex - this.bitsPerInt);
    if (offset >= 0) {
      // If the offset is positive, we need only update the first long.
      data[startLong] ^= updateBitMask << offset;
    } else {
      // If the offset is negative, we need to update two longs.
      data[startLong] ^= (updateBitMask >>> -offset);
      // Furthermore, we need to load the next long to provide the missing right-hand side bits.
      data[startLong + 1] ^= (updateBitMask << (64 + offset));
    }

    return true;
  }

  /**
   * Returns true if the bit changed value.
   */
  public boolean add(long index, int delta) {
    if (delta == 0) {
      return false;
    }
    // Load the current value.
    long value = get(index) & 0xFFFFFFFFL;
    if (value == 0) {
      if (this.numSetPositions != -1) this.numSetPositions++;
    } else if (value == this.intBitMask) {
      return false;
    }

    // Find the bits, we have to update.
    long incValue = Math.min(value + delta, this.intBitMask);
    long updateBitMask = value ^ incValue;

    // Determine the first and last bit index.
    long startIndex = index * this.bitsPerInt;

    // Determine if the int is distributed among two longs.
    int startLong = (int) (startIndex >>> 6);
    int offset = (int) (64 + (startIndex & 0xFFFFFFC0L) - startIndex - this.bitsPerInt);
    if (offset >= 0) {
      // If the offset is positive, we need only update the first long.
      data[startLong] ^= updateBitMask << offset;
    } else {
      // If the offset is negative, we need to update two longs.
      data[startLong] ^= (updateBitMask >>> -offset);
      // Furthermore, we need to load the next long to provide the missing right-hand side bits.
      data[startLong + 1] ^= (updateBitMask << (64 + offset));
    }

    return true;
  }

  public int get(long index) {
    // Determine the first and last bit index.
    long startIndex = index * this.bitsPerInt;

    // Determine if the int is distributed among two longs.
    int startLong = (int) (startIndex >>> 6);
    int offset = (int) (64 + (startIndex & 0xFFFFFFC0L) - startIndex - this.bitsPerInt);
    if (offset >= 0) {
      // If the offset is positive, we need to push the long to the right and scrap the left-hand remainder.
      return ((int) (data[startLong] >>> offset)) & this.intBitMask;
    } else {
      // If the offset is negative, we need to push the long to left, scrap the left hand remainder.
      int result = ((int) (data[startLong] << -offset)) & this.intBitMask;
      // Furthermore, we need to load the next long to provide the missing right-hand side bits.
      return result | (int) (data[startLong + 1] >>> (64 + offset));
    }
  }

  /**
   * Number of bits
   */
  public long positionSize() {
    return (long) data.length * Long.SIZE / this.bitsPerInt;
  }

  BitArray copy() {
    return new BitArray(data.clone());
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof BitArray) {
      BitArray bitArray = (BitArray) o;
      return Arrays.equals(data, bitArray.data);
    }
    return false;
  }

  @Override
  public long positionCount() {
    if (this.numSetPositions == -1) {
      this.numSetPositions = 0;
      for (int position = 0; position < positionSize(); position++) {
        if (get(position) != 0) this.numSetPositions++;
      }
    }

    return this.numSetPositions;
  }

  public int getNumBitsPerPosition() {
    return this.bitsPerInt;
  }

  @Override
  public long[] getRawData() {
    return this.data;
  }

  @Override
  public void clear() {
    if (positionCount() > 0)
      for (int i = 0; i < data.length; i++) {
        this.data[i] = 0L;
      }
    this.numSetPositions = 0;
  }

  @Override
  public void intersect(IntArray that) {
    throw new RuntimeException("Operation was not needed yet, so it is not implemented.");
  }

  @Override
  public void putAll(IntArray that) {
    long positionCount = positionCount();
    for (int position = 0; position < positionCount; position++) {
      int thatValue = that.get(position);
      this.add(position, thatValue);
    }

    // We could even cap the (numHashFunctions - 1) largest deltas and sizes iteratively.
  }

  public int getValueBitMask() {
    return intBitMask;
  }

  public LongIntCursor clearingCursor() {
    return new ClearingCursor();
  }

  public LongIntCursor cursor() {
    return new DefaultCursor();
  }

  public interface LongIntCursor {
    boolean moveToNext();

    long getLong();

    int getInt();
  }

  private class ClearingCursor implements LongIntCursor {

    int currentIndex = 0;

    long currentPos = -1L;

    int currentValue = -1;

    @Override
    public boolean moveToNext() {
      do {
        // Mini skip: Move within the current long until we find a non-zero entry.
        int newIndex = 0;
        do {
          this.currentPos++;
          newIndex = (int) (this.currentPos * bitsPerInt >> 6);
        } while (newIndex == this.currentIndex
            && this.currentPos < maxIndex
            && (this.currentValue = get(this.currentPos)) == 0);

        if (this.currentPos >= maxIndex) {
          // If we have reached the final position, we still need to clear the current block.
          data[this.currentIndex] = 0L;
          return false;
        }

        if (newIndex == this.currentIndex) {
          return true;
        }

        // If we moved out of a long, clear it.
        data[this.currentIndex] = 0L;

        // Fast-skip: Skip as long as the "current" long is 0 or does not exist.
        while (currentIndex < data.length && data[currentIndex] == 0) {
          currentIndex++;
        }

        // Adjust the position.
        if (currentIndex < data.length) {
          long highBitIndex = (this.currentIndex << 6) + Long.numberOfLeadingZeros(data[this.currentIndex]);
          int newPos = (int) (highBitIndex / bitsPerInt);
          if (newPos >= this.currentPos) {
            this.currentPos = newPos;
            this.currentValue = get(this.currentPos);
            return true;
          } else {
            // We might need to loop, as we could be facing pending bits of an already encountered item in the new
            // long. In that case, we simply reset the position and go back to the beginning of the function to
            // start over with a mini skip within the current longblock.
            this.currentPos = newPos;
            continue;
          }
        } else {
          return false;
        }
      } while (true);
    }

    @Override
    public long getLong() {
      return this.currentPos;
    }

    @Override
    public int getInt() {
      return get(this.currentPos);
    }
  }
  private class DefaultCursor implements LongIntCursor {

    int currentIndex = 0;

    long currentPos = -1L;

    int currentValue = -1;

    @Override
    public boolean moveToNext() {
      do {
        // Mini skip: Move within the current long until we find a non-zero entry.
        int newIndex = 0;
        do {
          this.currentPos++;
          newIndex = (int) (this.currentPos * bitsPerInt >> 6);
        } while (newIndex == this.currentIndex
            && this.currentPos < maxIndex
            && (this.currentValue = get(this.currentPos)) == 0);

        if (this.currentPos >= maxIndex) {
          return false;
        }

        if (newIndex == this.currentIndex) {
          return true;
        }

        // Fast-skip: Skip as long as the "current" long is 0 or does not exist.
        do {
          currentIndex++;
        } while (currentIndex < data.length && data[currentIndex] == 0);

        // Adjust the position.
        if (currentIndex < data.length) {
          long highBitIndex = (this.currentIndex << 6) + Long.numberOfLeadingZeros(data[this.currentIndex]);
          int newPos = (int) (highBitIndex / bitsPerInt);
          if (newPos >= this.currentPos) {
            this.currentPos = newPos;
            this.currentValue = get(this.currentPos);
            return true;
          } else {
            // We might need to loop, as we could be facing pending bits of an already encountered item in the new
            // long. In that case, we simply reset the position and go back to the beginning of the function to
            // start over with a mini skip within the current longblock.
            this.currentPos = newPos;
            continue;
          }
        } else {
          return false;
        }
      } while (true);
    }

    @Override
    public long getLong() {
      return this.currentPos;
    }

    @Override
    public int getInt() {
      return get(this.currentPos);
    }
  }
}
