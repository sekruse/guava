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

import com.google.common.hash.data.HashSink;
import com.google.common.primitives.Longs;

/**
 * Collections of strategies of generating the k * log(M) bits required for an element to
 * be mapped to a BloomFilter of M bits and k hash functions. These
 * strategies are part of the serialized form of the Bloom filters that use them, thus they must be
 * preserved as is (no updates allowed, only introduction of new versions).
 * <p/>
 * Important: the order of the constants cannot change, and they cannot be deleted - we depend
 * on their ordinal for BloomFilter serialization.
 *
 * @author Dimitris Andreou
 * @author Kurt Alfred Kluever
 */
public enum BloomFilterStrategies implements BloomFilterStrategy {
  /**
   * See "Less Hashing, Same Performance: Building a Better Bloom Filter" by Adam Kirsch and
   * Michael Mitzenmacher. The paper argues that this trick doesn't significantly deteriorate the
   * performance of a Bloom filter (yet only needs two 32bit hash functions).
   */
  MURMUR128_MITZ_32() {
    @Override
    public <T> boolean put(T object, Funnel<? super T> funnel,
                           int numHashFunctions, HashSink sink) {
      long bitSize = sink.positionSize();
      long hash64 = Hashing.murmur3_128().hashObject(object, funnel).asLong();
      int hash1 = (int) hash64;
      int hash2 = (int) (hash64 >>> 32);

      boolean bitsChanged = false;
      for (int i = 1; i <= numHashFunctions; i++) {
        int combinedHash = hash1 + (i * hash2);
        // Flip all the bits if it's negative (guaranteed positive number)
        if (combinedHash < 0) {
          combinedHash = ~combinedHash;
        }
        bitsChanged |= sink.set(combinedHash % bitSize);
      }
      return bitsChanged;
    }

    @Override
    public <T> int get(T object, Funnel<? super T> funnel, int numHashFunctions, HashSink bits) {
      long bitSize = bits.positionSize();
      long hash64 = Hashing.murmur3_128().hashObject(object, funnel).asLong();
      int hash1 = (int) hash64;
      int hash2 = (int) (hash64 >>> 32);

      int minValue = -1;
      for (int i = 1; i <= numHashFunctions; i++) {
        int combinedHash = hash1 + (i * hash2);
        // Flip all the bits if it's negative (guaranteed positive number)
        if (combinedHash < 0) {
          combinedHash = ~combinedHash;
        }
        int currentValue = bits.get(combinedHash % bitSize);
        if (currentValue == 0) {
          return 0;
        }

        minValue = minValue == -1 ? currentValue : Math.min(minValue, currentValue);
      }
      return minValue;
    }

    @Override
    public <T> int getMinPositions(T object, Funnel<? super T> funnel, int numHashFunctions, HashSink bits, long[] collector) {
      long bitSize = bits.positionSize();
      long hash64 = Hashing.murmur3_128().hashObject(object, funnel).asLong();
      int hash1 = (int) hash64;
      int hash2 = (int) (hash64 >>> 32);

      int minValue = -1;
      int nextMinPos = 0;
      for (int i = 1; i <= numHashFunctions; i++) {
        int combinedHash = hash1 + (i * hash2);
        // Flip all the bits if it's negative (guaranteed positive number)
        if (combinedHash < 0) {
          combinedHash = ~combinedHash;
        }
        long effectiveHash = combinedHash % bitSize;
        int currentValue = bits.get(effectiveHash);
        if (currentValue > minValue) {
          minValue = currentValue;
          collector[0] = effectiveHash;
          nextMinPos = 1;
        } else if (currentValue == minValue) {
          collector[nextMinPos++] = effectiveHash;
        }
      }

      return nextMinPos;
    }

    @Override
    public <T> int getPositions(T object, Funnel<? super T> funnel, int numHashFunctions, HashSink bits, long[] collector) {
      long bitSize = bits.positionSize();
      long hash64 = Hashing.murmur3_128().hashObject(object, funnel).asLong();
      int hash1 = (int) hash64;
      int hash2 = (int) (hash64 >>> 32);

      int nextPos = 0;
      for (int i = 1; i <= numHashFunctions; i++) {
        int combinedHash = hash1 + (i * hash2);
        // Flip all the bits if it's negative (guaranteed positive number)
        if (combinedHash < 0) {
          combinedHash = ~combinedHash;
        }
        long effectiveHash = combinedHash % bitSize;
        collector[nextPos++] = effectiveHash;
      }

      return nextPos;
    }


    // Keep this method for efficiency reasosn.
    @Override
    public <T> boolean mightContain(T object, Funnel<? super T> funnel,
                                    int numHashFunctions, HashSink bits) {
      long bitSize = bits.positionSize();
      long hash64 = Hashing.murmur3_128().hashObject(object, funnel).asLong();
      int hash1 = (int) hash64;
      int hash2 = (int) (hash64 >>> 32);

      for (int i = 1; i <= numHashFunctions; i++) {
        int combinedHash = hash1 + (i * hash2);
        // Flip all the bits if it's negative (guaranteed positive number)
        if (combinedHash < 0) {
          combinedHash = ~combinedHash;
        }
        if (bits.get(combinedHash % bitSize) == 0) {
          return false;
        }
      }
      return true;
    }
  },
  /**
   * This strategy uses all 128 bits of {@link Hashing#murmur3_128} when hashing. It looks
   * different than the implementation in MURMUR128_MITZ_32 because we're avoiding the
   * multiplication in the loop and doing a (much simpler) += hash2. We're also changing the
   * index to a positive number by AND'ing with Long.MAX_VALUE instead of flipping the bits.
   */
  MURMUR128_MITZ_64() {
    @Override
    public <T> boolean put(T object, Funnel<? super T> funnel,
                           int numHashFunctions, HashSink sink) {
      long bitSize = sink.positionSize();
      byte[] bytes = Hashing.murmur3_128().hashObject(object, funnel).getBytesInternal();
      long hash1 = lowerEight(bytes);
      long hash2 = upperEight(bytes);

      boolean bitsChanged = false;
      long combinedHash = hash1;
      for (int i = 0; i < numHashFunctions; i++) {
        // Make the combined hash positive and indexable
        bitsChanged |= sink.set((combinedHash & Long.MAX_VALUE) % bitSize);
        combinedHash += hash2;
      }
      return bitsChanged;
    }

    @Override
    public <T> boolean mightContain(T object, Funnel<? super T> funnel,
                                    int numHashFunctions, HashSink bits) {
      long bitSize = bits.positionSize();
      byte[] bytes = Hashing.murmur3_128().hashObject(object, funnel).getBytesInternal();
      long hash1 = lowerEight(bytes);
      long hash2 = upperEight(bytes);

      long combinedHash = hash1;
      for (int i = 0; i < numHashFunctions; i++) {
        // Make the combined hash positive and indexable
        if (bits.get((combinedHash & Long.MAX_VALUE) % bitSize) == 0) {
          return false;
        }
        combinedHash += hash2;
      }
      return true;
    }

    @Override
    public <T> int get(T object, Funnel<? super T> funnel, int numHashFunctions, HashSink bits) {
      long bitSize = bits.positionSize();
      byte[] bytes = Hashing.murmur3_128().hashObject(object, funnel).getBytesInternal();
      long hash1 = lowerEight(bytes);
      long hash2 = upperEight(bytes);

      int minValue = -1;
      long combinedHash = hash1;
      for (int i = 0; i < numHashFunctions; i++) {
        // Make the combined hash positive and indexable
        int currentValue = bits.get((combinedHash & Long.MAX_VALUE) % bitSize);
        if (currentValue == 0) {
          return 0;
        }
        minValue = minValue == -1 ? currentValue : Math.min(minValue, currentValue);
        combinedHash += hash2;
      }
      return minValue;
    }

    @Override
    public <T> int getMinPositions(T object, Funnel<? super T> funnel, int numHashFunctions, HashSink bits,
                                   long[] collector) {

      long bitSize = bits.positionSize();
      byte[] bytes = Hashing.murmur3_128().hashObject(object, funnel).getBytesInternal();
      long hash1 = lowerEight(bytes);
      long hash2 = upperEight(bytes);

      int minValue = -1;
      int nextMinPos = 0;

      long combinedHash = hash1;
      for (int i = 0; i < numHashFunctions; i++) {
        // Make the combined hash positive and indexable
        long effectiveHash = (combinedHash & Long.MAX_VALUE) % bitSize;
        int currentValue = bits.get(effectiveHash);
        if (currentValue < minValue || minValue == -1) {
          minValue = currentValue;
          collector[0] = effectiveHash;
          nextMinPos = 1;
        } else if (currentValue == minValue) {
          collector[nextMinPos++] = effectiveHash;
        }
        combinedHash += hash2;
      }

      return nextMinPos;
    }

    @Override
    public <T> int getPositions(T object, Funnel<? super T> funnel, int numHashFunctions, HashSink bits,
                                long[] collector) {

      long bitSize = bits.positionSize();
      byte[] bytes = Hashing.murmur3_128().hashObject(object, funnel).getBytesInternal();
      long hash1 = lowerEight(bytes);
      long hash2 = upperEight(bytes);

      int nextPos = 0;

      long combinedHash = hash1;
      for (int i = 0; i < numHashFunctions; i++) {
        // Make the combined hash positive and indexable
        long effectiveHash = (combinedHash & Long.MAX_VALUE) % bitSize;
        collector[nextPos++] = effectiveHash;
        combinedHash += hash2;
      }

      return nextPos;
    }

    private /* static */ long lowerEight(byte[] bytes) {
      return Longs.fromBytes(
          bytes[7], bytes[6], bytes[5], bytes[4], bytes[3], bytes[2], bytes[1], bytes[0]);
    }

    private /* static */ long upperEight(byte[] bytes) {
      return Longs.fromBytes(
          bytes[15], bytes[14], bytes[13], bytes[12], bytes[11], bytes[10], bytes[9], bytes[8]);
    }
  };


}
