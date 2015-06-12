package com.google.common.hash.data;

/**
 * Receives hash values from a Bloom filter strategy and updates accordingly.
 */
public interface HashSink<Self> {

  /** @return the number of settable positions in this sink. */
  long positionSize();

  /** Updates this sink at the given index and return if an effective change occurred. */
  boolean set(long index);

  int get(long index);

  /**
   * Returns the raw memory block that manages the data of this sink.
   */
  long[] getRawData();

  long positionCount();

  void clear();

  void intersect(Self that);

  void putAll(Self that);
}
