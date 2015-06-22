package com.google.common.hash.data;

import com.google.common.math.LongMath;
import com.google.common.primitives.Ints;

import java.math.RoundingMode;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;

// Note: We use this instead of java.util.BitSet because we need access to the long[] data field
public final class BitArray implements HashSink<BitArray> {
  final long[] data;
  long bitCount;

  long size;

  /**
   * Creates a new bit array with a given minimum number of bits. The actual size of the bit array might be greater, thought.
   *
   * @param bits is the number of minimum bits in this bit array.
   */
  public BitArray(long bits) {
    this(bits, false);
  }

  /**
   * Creates a new bit array with a given minimum number of bits.
   *
   * @param bits       is the number of minimum bits in this bit array.
   * @param strictSize whether the array should have exactly the number of bits provided
   */
  public BitArray(long bits, boolean strictSize) {
    this(new long[Ints.checkedCast(LongMath.divide(bits, 64, RoundingMode.CEILING))], strictSize ? bits : Ints.checkedCast(LongMath.divide(bits, 64, RoundingMode.CEILING)));
  }

  /**
   * Creates a new bit array that wraps the given {@code long[]}.
   *
   * @param data are the bits to wrap
   */
  public BitArray(long[] data) {
    this(data, (long) data.length * Long.SIZE);
  }

  /**
   * Creates a new bit array that wraps the given {@code long[]}.
   *
   * @param data are the bits to wrap
   * @param size is the number of bits to be actually used within {@code data}
   */
  public BitArray(long[] data, long size) {
    checkArgument(data.length > 0, "data length is zero!");
    this.data = data;
    long bitCount = 0;
    for (long value : data) {
      bitCount += Long.bitCount(value);
    }
    this.bitCount = bitCount;
    this.size = size;
  }

  /**
   * Returns true if the bit changed value.
   */
  public boolean set(long index) {
    if (get(index) == 0) {
      data[(int) (index >>> 6)] |= (1L << index);
      bitCount++;
      return true;
    }
    return false;
  }

  public int get(long index) {
    return (int) ((data[(int) (index >>> 6)] >>> index) & 1L);
  }

  /**
   * Number of bits
   */
  public long positionSize() {
    return this.size;
  }

  /**
   * Number of set bits (1s)
   */
  public long positionCount() {
    return bitCount;
  }

  @Override
  public void clear() {
    if (this.bitCount > 0) {
      Arrays.fill(this.data, 0L);
      this.bitCount = 0;
    }
  }

  public BitArray copy() {
    return new BitArray(data.clone());
  }

  /**
   * Combines the two BitArrays using bitwise OR.
   */
  public void putAll(BitArray array) {
    checkArgument(data.length == array.data.length,
        "BitArrays must be of equal length (%s != %s)", data.length, array.data.length);
    bitCount = 0;
    for (int i = 0; i < data.length; i++) {
      data[i] |= array.data[i];
      bitCount += Long.bitCount(data[i]);
    }
  }

  /**
   * Combines the two BitArrays using bitwise AND.
   */
  public void intersect(BitArray array) {
    checkArgument(data.length == array.data.length,
        "BitArrays must be of equal length (%s != %s)", data.length, array.data.length);
    bitCount = 0;
    for (int i = 0; i < data.length; i++) {
      data[i] &= array.data[i];
      bitCount += Long.bitCount(data[i]);
    }
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
  public long[] getRawData() {
    return this.data;
  }

  public LongIterator clearingIterator() {
    return new ClearingIterator();
  }

  public interface LongIterator {
    boolean hasNext();
    long next();
  }

  private class ClearingIterator implements LongIterator {

    int currentIndex = 0;

    ClearingIterator() {
      moveToNext();
    }

    void moveToNext() {
      while (currentIndex < data.length && data[currentIndex] == 0) {
        currentIndex++;
      }
    }

    @Override
    public boolean hasNext() {
      return this.currentIndex < data.length;
    }

    @Override
    public long next() {
      long flipMask = Long.lowestOneBit(data[this.currentIndex]);
      data[this.currentIndex] ^= flipMask;
      long result = (this.currentIndex << 6) + Long.numberOfTrailingZeros(flipMask);
      moveToNext();
      return result;
    }
  }
}
