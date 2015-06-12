/*
 * Copyright (C) 2011 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.common.hash;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.hash.data.BitArray;
import com.google.common.hash.data.IntArray;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class SpectralBloomFilter<T> {

  private final long numPositions;

  /** The array that stores the ints for the spectral Bloom filter. */
  private IntArray ints;

  /** Number of hashes per element */
  private final int numHashFunctions;

  private final int numBitsPerPosition;

  /** The funnel to translate Ts to bytes */
  private final Funnel<? super T> funnel;

  /**
   * The strategy we employ to map an element T to {@code numHashFunctions} bit indexes.
   */
  private final BloomFilterStrategy strategy;

  /**
   * Aggregates operations into a transaction.
   */
  private BitArray transactionCache;

  /**
   * Reuse object to return the minimum count positions for a certain object. Operations that use this object should
   * clean it afterwards and must not be called recursively nor concurrently, of course!
   */
  private long[] minPositions;

  /**
   * Creates a BloomFilter.
   */
  private SpectralBloomFilter(long numPositions, int numBitsPerPosition, int numHashFunctions, Funnel<? super T> funnel,
                              BloomFilterStrategy strategy) {
    checkArgument(numHashFunctions > 0,
        "numHashFunctions (%s) must be > 0", numHashFunctions);
    checkArgument(numHashFunctions <= 255,
        "numHashFunctions (%s) must be <= 255", numHashFunctions);
    this.ints = new IntArray(numPositions, numBitsPerPosition);
    this.numPositions = numPositions;
    this.numBitsPerPosition = ints.getNumBitsPerPosition();
    this.numHashFunctions = numHashFunctions;
    this.funnel = checkNotNull(funnel);
    this.strategy = checkNotNull(strategy);
  }

  /**
   * Returns {@code true} if the element <i>might</i> have been put in this Bloom filter,
   * {@code false} if this is <i>definitely</i> not the case.
   */
  @CheckReturnValue
  public int getCount(T object) {
    return strategy.get(object, funnel, numHashFunctions, ints);
  }

  public void put(T object) {
    long[] minPositions = getMinPositions();
    int numMinPositions = strategy.getMinPositions(object, this.funnel, this.numHashFunctions, this.ints, minPositions);
    // It might happen that some positions occurred twice. Deal with it!
    Arrays.sort(minPositions, 0, numMinPositions);
    long lastPosition = -1;
    for (int minPositionIndex = 0; minPositionIndex < numMinPositions; minPositionIndex++) {
      long minPosition = minPositions[minPositionIndex];
      if (lastPosition != minPosition) {
        this.ints.set(minPosition);
        lastPosition = minPosition;
      }
    }
  }

  /**
   * For the given object, find all the current minimum counts in the current spectral filter and mark these to be
   * updated.
   */
  public void putToBatch(T object) {
    BitArray transactionCache = getTransactionCache();
    long[] minPositions = getMinPositions();
    int numMinPositions = strategy.getMinPositions(object, this.funnel, this.numHashFunctions, this.ints, minPositions);
    for (int minPositionIndex = 0; minPositionIndex < numMinPositions; minPositionIndex++) {
      transactionCache.set(minPositions[minPositionIndex]);
    }
  }

  public void executeBatch() {
    BitArray.LongIterator iterator = getTransactionCache().clearingIterator();
    while (iterator.hasNext()) {
      this.ints.set(iterator.next());
    }
  }

  public void clear() {
    this.ints.clear();
  }

//  /**
//   * Returns the probability that {@linkplain #mightContain(Object)} will erroneously return
//   * {@code true} for an object that has not actually been put in the {@code BloomFilter}.
//   *
//   * <p>Ideally, this number should be close to the {@code fpp} parameter
//   * passed in {@linkplain #create(Funnel, long, double, Strategy)}, or smaller. If it is
//   * significantly higher, it is usually the case that too many elements (more than
//   * expected) have been put in the {@code BloomFilter}, degenerating it.
//   *
//   * @since 14.0 (since 11.0 as expectedFalsePositiveProbability())
//   */
//  @CheckReturnValue
//  public double expectedFpp() {
//    // You down with FPP? (Yeah you know me!) Who's down with FPP? (Every last homie!)
//    return Math.pow((double) ints.bitCount() / positionSize(), numHashFunctions);
//  }

  /**
   * Returns the number of ints in the underlying bit array.
   */
  public long size() {
    return ints.positionSize();
  }

//  /**
//   * Returns the number of set ints in the underlying bit array.
//   */
//  public long bitCount() {
//    return ints.bitCount();
//  }

//  public void clear() {
//    this.ints.clear();
//  }

  /**
   * Determines whether a given bloom filter is compatible with this bloom filter. For two
   * bloom filters to be compatible, they must:
   *
   * <ul>
   * <li>not be the same instance
   * <li>have the same number of hash functions
   * <li>have the same bit size
   * <li>have the same strategy
   * <li>have equal funnels
   * <ul>
   *
   * @param that The bloom filter to check for compatibility.
   * @since 15.0
   */
  @CheckReturnValue
  public boolean isCompatible(SpectralBloomFilter<T> that) {
    checkNotNull(that);
    return (this != that) &&
        (this.numHashFunctions == that.numHashFunctions) &&
        (this.size() == that.size()) &&
        (this.strategy.equals(that.strategy)) &&
        (this.funnel.equals(that.funnel));
  }

  /**
   * Combines this bloom filter with another bloom filter by performing a bitwise OR of the
   * underlying data. The mutations happen to <b>this</b> instance. Callers must ensure the
   * bloom filters are appropriately sized to avoid saturating them.
   *
   * @param that The bloom filter to combine this bloom filter with. It is not mutated.
   * @throws IllegalArgumentException if {@code isCompatible(that) == false}
   *
   * @since 15.0
   */
  public void putAll(SpectralBloomFilter<T> that) {
    checkNotNull(that);
    checkArgument(this != that, "Cannot combine a BloomFilter with itself.");
    checkArgument(this.numHashFunctions == that.numHashFunctions,
        "BloomFilters must have the same number of hash functions (%s != %s)",
        this.numHashFunctions, that.numHashFunctions);
    checkArgument(this.size() == that.size(),
        "BloomFilters must have the same size underlying bit arrays (%s != %s)",
        this.size(), that.size());
    checkArgument(this.strategy.equals(that.strategy),
        "BloomFilters must have equal strategies (%s != %s)",
        this.strategy, that.strategy);
    checkArgument(this.funnel.equals(that.funnel),
        "BloomFilters must have equal funnels (%s != %s)",
        this.funnel, that.funnel);
    this.ints.putAll(that.ints);
  }

//  /**
//   * Combines this bloom filter with another bloom filter by performing a bitwise AND of the
//   * underlying data. The mutations happen to <b>this</b> instance. Callers must ensure the
//   * bloom filters are appropriately sized to avoid saturating them.
//   *
//   * @param that The bloom filter to combine this bloom filter with. It is not mutated.
//   * @throws IllegalArgumentException if {@code isCompatible(that) == false}
//   *
//   */
//  public void intersect(CountingBloomFilter<T> that) {
//    checkNotNull(that);
//    checkArgument(this != that, "Cannot combine a BloomFilter with itself.");
//    checkArgument(this.numHashFunctions == that.numHashFunctions,
//        "BloomFilters must have the same number of hash functions (%s != %s)",
//        this.numHashFunctions, that.numHashFunctions);
//    checkArgument(this.size() == that.size(),
//        "BloomFilters must have the same size underlying bit arrays (%s != %s)",
//        this.size(), that.size());
//    checkArgument(this.strategy.equals(that.strategy),
//        "BloomFilters must have equal strategies (%s != %s)",
//        this.strategy, that.strategy);
//    checkArgument(this.funnel.equals(that.funnel),
//        "BloomFilters must have equal funnels (%s != %s)",
//        this.funnel, that.funnel);
//    this.ints.intersect(that.ints);
//  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (object == this) {
      return true;
    }
    if (object instanceof SpectralBloomFilter) {
      SpectralBloomFilter<?> that = (SpectralBloomFilter<?>) object;
      return this.numHashFunctions == that.numHashFunctions
          && this.funnel.equals(that.funnel)
          && this.ints.equals(that.ints)
          && this.strategy.equals(that.strategy);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(numHashFunctions, funnel, strategy, ints);
  }

  /**
   * Creates a {@link SpectralBloomFilter BloomFilter<T>} with the expected number of
   * insertions and expected false positive probability.
   *
   * <p>Note that overflowing a {@code BloomFilter} with significantly more elements
   * than specified, will result in its saturation, and a sharp deterioration of its
   * false positive probability.
   *
   * <p>The constructed {@code BloomFilter<T>} will be serializable if the provided
   * {@code Funnel<T>} is.
   *
   * <p>It is recommended that the funnel be implemented as a Java enum. This has the
   * benefit of ensuring proper serialization and deserialization, which is important
   * since {@link #equals} also relies on object identity of funnels.
   *
   * @param funnel the funnel of T's that the constructed {@code BloomFilter<T>} will use
   * @param expectedInsertions the number of expected insertions to the constructed
   *     {@code BloomFilter<T>}; must be positive
   * @param fpp the desired false positive probability (must be positive and less than 1.0)
   * @return a {@code BloomFilter}
   */
  @CheckReturnValue
  public static <T> SpectralBloomFilter<T> create(int numBitsPerPosition,
      Funnel<? super T> funnel, long expectedInsertions, double fpp) {
    return create(numBitsPerPosition, funnel, expectedInsertions, fpp, BloomFilterStrategies.MURMUR128_MITZ_64);
  }

  @VisibleForTesting
  static <T> SpectralBloomFilter<T> create(int numBitsPerPosition,
      Funnel<? super T> funnel, long expectedInsertions, double fpp, BloomFilterStrategy strategy) {
    checkNotNull(funnel);
    checkArgument(expectedInsertions >= 0, "Expected insertions (%s) must be >= 0",
        expectedInsertions);
    checkArgument(fpp > 0.0, "False positive probability (%s) must be > 0.0", fpp);
    checkArgument(fpp < 1.0, "False positive probability (%s) must be < 1.0", fpp);
    checkNotNull(strategy);

    if (expectedInsertions == 0) {
      expectedInsertions = 1;
    }
    /*
     * TODO(user): Put a warning in the javadoc about tiny fpp values,
     * since the resulting size is proportional to -log(p), but there is not
     * much of a point after all, e.g. optimalM(1000, 0.0000000000000001) = 76680
     * which is less than 10kb. Who cares!
     */
    long numBits = optimalNumOfBits(expectedInsertions, fpp);
    int numHashFunctions = optimalNumOfHashFunctions(expectedInsertions, numBits);
    try {
      return new SpectralBloomFilter<T>(numBits, numBitsPerPosition,
          numHashFunctions, funnel, strategy);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Could not create BloomFilter of " + numBits + " ints", e);
    }
  }

  /**
   * Creates a {@link SpectralBloomFilter BloomFilter<T>} with the expected number of
   * insertions and a default expected false positive probability of 3%.
   *
   * <p>Note that overflowing a {@code BloomFilter} with significantly more elements
   * than specified, will result in its satugitration, and a sharp deterioration of its
   * false positive probability.
   *
   * <p>The constructed {@code BloomFilter<T>} will be serializable if the provided
   * {@code Funnel<T>} is.
   *
   * @param funnel the funnel of T's that the constructed {@code BloomFilter<T>} will use
   * @param expectedInsertions the number of expected insertions to the constructed
   *     {@code BloomFilter<T>}; must be positive
   * @return a {@code BloomFilter}
   */
  @CheckReturnValue
  public static <T> SpectralBloomFilter<T> create(int numBitPerPosition,
      Funnel<? super T> funnel, long expectedInsertions) {
    return create(numBitPerPosition, funnel, expectedInsertions, 0.03); // FYI, for 3%, we always get 5 hash functions
  }

  /*
   * Cheat sheet:
   *
   * m: total ints
   * n: expected insertions
   * b: m/n, ints per insertion
   * p: expected false positive probability
   *
   * 1) Optimal k = b * ln2
   * 2) p = (1 - e ^ (-kn/m))^k
   * 3) For optimal k: p = 2 ^ (-k) ~= 0.6185^b
   * 4) For optimal k: m = -nlnp / ((ln2) ^ 2)
   */

  /**
   * Computes the optimal k (number of hashes per element inserted in Bloom filter), given the
   * expected insertions and total number of ints in the Bloom filter.
   *
   * See http://en.wikipedia.org/wiki/File:Bloom_filter_fp_probability.svg for the formula.
   *
   * @param n expected insertions (must be positive)
   * @param m total number of ints in Bloom filter (must be positive)
   */
  @VisibleForTesting
  static int optimalNumOfHashFunctions(long n, long m) {
    // (m / n) * log(2), but avoid truncation due to division!
    return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
  }

  /**
   * Computes m (total ints of Bloom filter) which is expected to achieve, for the specified
   * expected insertions, the required false positive probability.
   *
   * See http://en.wikipedia.org/wiki/Bloom_filter#Probability_of_false_positives for the formula.
   *
   * @param n expected insertions (must be positive)
   * @param p false positive rate (must be 0 < p < 1)
   */
  @VisibleForTesting
  static long optimalNumOfBits(long n, double p) {
    if (p == 0) {
      p = Double.MIN_VALUE;
    }
    return (long) (-n * Math.log(p) / (Math.log(2) * Math.log(2)));
  }


	@Override
	public String toString() {
		return String.format("SpectralBloomFilter[%s %sbit-ints, %.2f%% filled, %s hash functions]", this.numPositions,
        this.numBitsPerPosition, this.ints.positionCount() * 100d / this.size(), this.numHashFunctions);
	}

  public void wrap(long[] data) {
    IntArray newBits = new IntArray(data, this.numBitsPerPosition);
    if (this.ints.positionSize() != newBits.positionSize()) {
      throw new IllegalArgumentException(String.format("Given %d-bit array, need %d ints.", newBits.positionSize(), this.ints.positionSize()));
    }
    this.ints = newBits;
  }

  public long[] exportBits() {
    return this.ints.getRawData();
  }

  private BitArray getTransactionCache() {
    if (this.transactionCache == null) {
      this.transactionCache = new BitArray(this.numPositions);
    }
    return this.transactionCache;
  }

  public long[] getMinPositions() {
    if (this.minPositions == null) {
      this.minPositions = new long[this.numHashFunctions];
    }
    return this.minPositions;
  }

  public int getMaxValue() {
    return this.ints.getValueBitMask();
  }
}
