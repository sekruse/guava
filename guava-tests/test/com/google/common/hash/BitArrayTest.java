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

import com.google.common.hash.data.BitArray;
import junit.framework.Assert;
import junit.framework.TestCase;

import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;


public class BitArrayTest extends TestCase {

  public void testClearingIterator() {
    int range = 10000;

    SortedSet<Integer> testValues = new TreeSet<Integer>();
    BitArray bitArray = new BitArray(range);
    Random random = new Random(1L);
    while (testValues.size() < 1000) {
      int value = random.nextInt(range);
      System.out.println("Adding " + value);
      Assert.assertEquals(testValues.add(value), bitArray.set(value));
    }

    BitArray.LongIterator iterator = bitArray.clearingIterator();
    for (int value : testValues) {
      Assert.assertTrue(iterator.hasNext());
      Assert.assertEquals(value, iterator.next());
    }

    Assert.assertFalse(iterator.hasNext());
    for (long block : bitArray.getRawData()) {
      Assert.assertEquals(0L, block);
    }
  }

}
