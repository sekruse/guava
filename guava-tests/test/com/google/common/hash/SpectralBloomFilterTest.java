/*
 * Copyright (C) 2011 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.hash;

import junit.framework.TestCase;
import org.junit.Assert;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Tests for SimpleGenericBloomFilter and derived BloomFilter views.
 *
 * @author Dimitris Andreou
 */
public class SpectralBloomFilterTest extends TestCase {

  public void testSinglePut() {
    SpectralBloomFilter<Integer> countingBloomFilter = SpectralBloomFilter.create(4, Funnels.integerFunnel(), 10, 0.01);
    Map<Integer, Integer> tests = new HashMap<Integer, Integer>();
    tests.put(1, 5);
    tests.put(2, 3);
    tests.put(7, 3);

    for (Map.Entry<Integer, Integer> test : tests.entrySet()) {
      for (int i = 0; i < test.getValue(); i++) {
        countingBloomFilter.put(test.getKey());
      }
    }

    for (Map.Entry<Integer, Integer> test : tests.entrySet()) {
      int approximateCount = countingBloomFilter.getCount(test.getKey());
      Assert.assertTrue(String.format("Asserted count for %s: >= %s, found %s.", test.getKey().intValue(), test.getValue().intValue(), approximateCount),
          approximateCount >= test.getValue());
    }
  }

  public void testLargerSinglePut() {
    SpectralBloomFilter<Integer> countingBloomFilter = SpectralBloomFilter.create(7, Funnels.integerFunnel(), 100, 0.1);
    Map<Integer, Integer> counter = new HashMap<Integer, Integer>();
    Random random = new Random(42L);

    for (int round = 0; round < 100000; round++) {
      Integer value = random.nextInt(200);
      Integer count = counter.get(value);
      if (count == null) count = 0;
      counter.put(value, count + 1);
      countingBloomFilter.put(value);
    }

    for (Map.Entry<Integer, Integer> test : counter.entrySet()) {
      int approximateCount = countingBloomFilter.getCount(test.getKey());
      int minAssertedCount = Math.min(countingBloomFilter.getMaxValue(), approximateCount);
      Assert.assertTrue(String.format("Asserted count for %s: >= %s, found %s.", test.getKey().intValue(), minAssertedCount, approximateCount),
          approximateCount >= minAssertedCount);
    }
  }

  public void testLargerBatchPut() {
    SpectralBloomFilter<Integer> countingBloomFilter = SpectralBloomFilter.create(7, Funnels.integerFunnel(), 100, 0.1);
    Map<Integer, Integer> counter = new HashMap<Integer, Integer>();
    Random random = new Random(42L);

    for (int round = 0; round < 100000; round++) {
      Set<Integer> roundSet = new HashSet<Integer>();
      for (int i = 0; i < 100; i++) {
        Integer value = random.nextInt(200);
        roundSet.add(value);
      }
      for (Integer value : roundSet) {
        Integer count = counter.get(value);
        if (count == null) count = 0;
        counter.put(value, count + 1);
        countingBloomFilter.putToBatch(value);
      }
      countingBloomFilter.executeBatch();
    }

    for (Map.Entry<Integer, Integer> test : counter.entrySet()) {
      int approximateCount = countingBloomFilter.getCount(test.getKey());
      int minAssertedCount = Math.min(countingBloomFilter.getMaxValue(), approximateCount);
      Assert.assertTrue(String.format("Asserted count for %s: >= %s, found %s.", test.getKey().intValue(), minAssertedCount, approximateCount),
          approximateCount >= minAssertedCount);
    }
  }

}
