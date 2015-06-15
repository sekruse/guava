package com.google.common.hash;

import com.google.common.hash.data.HashSink;

/**
 * A strategy to translate T instances, to {@code numHashFunctions} bit indexes.
 *
 * <p>Implementations should be collections of pure functions (i.e. stateless).
 */
public interface BloomFilterStrategy extends java.io.Serializable {

  /**
   * Sets {@code numHashFunctions} bits of the given bit array, by hashing a user element.
   *
   * <p>Returns whether any bits changed as a result of this operation.
   */
  <T> boolean put(T object, Funnel<? super T> funnel, int numHashFunctions, HashSink sink);

  /**
   * Queries {@code numHashFunctions} bits of the given bit array, by hashing a user element;
   * returns {@code true} if and only if all selected bits are set.
   */
  <T> boolean mightContain(
      T object, Funnel<? super T> funnel, int numHashFunctions, HashSink bits);

  <T> int get(T object, Funnel<? super T> funnel, int numHashFunctions, HashSink bits);

  /**
   * Gets the minimum value positions for a given object in the hash sink.
   * @param collector is an array of the size equal to numHashFunctions that collects the positions
   * @return the number of valid entries in the collector
   */
  <T> int getMinPositions(T object, Funnel<? super T> funnel, int numHashFunctions, HashSink bits, long[] collector);

  /**
   * Gets the value positions for a given object in the hash sink.
   * @param collector is an array of the size equal to numHashFunctions that collects the positions
   * @return the number of valid entries in the collector
   */
  <T> int getPositions(T object, Funnel<? super T> funnel, int numHashFunctions, HashSink bits, long[] collector);

    /**
     * Identifier used to encode this strategy, when marshalled as part of a BloomFilter.
     * Only values in the [-128, 127] range are valid for the compact serial form.
     * Non-negative values are reserved for enums defined in BloomFilterStrategies;
     * negative values are reserved for any custom, stateful strategy we may define
     * (e.g. any kind of strategy that would depend on user input).
     */
  int ordinal();
}
