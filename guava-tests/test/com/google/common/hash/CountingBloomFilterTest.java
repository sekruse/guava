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

import com.google.common.collect.ImmutableSet;
import com.google.common.math.LongMath;
import com.google.common.primitives.Ints;
import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;
import com.google.common.testing.SerializableTester;
import junit.framework.TestCase;
import org.junit.Assert;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.hash.BloomFilterStrategies.BitArray;

/**
 * Tests for SimpleGenericBloomFilter and derived BloomFilter views.
 *
 * @author Dimitris Andreou
 */
public class CountingBloomFilterTest extends TestCase {

  public void testBasicFunctionality() {
    CountingBloomFilter<Integer> countingBloomFilter = CountingBloomFilter.create(4, Funnels.integerFunnel(), 10, 0.01);
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
          Assert.assertTrue(String.format("Asserted count for %d: %d, found %d.", test.getKey(), test.getValue(), approximateCount),
              approximateCount >= test.getValue());
    }
  }

}
