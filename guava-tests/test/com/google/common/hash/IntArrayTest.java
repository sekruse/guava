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
import com.google.common.hash.data.IntArray;
import junit.framework.TestCase;
import org.junit.Assert;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;


public class IntArrayTest extends TestCase {

  public void testUnalignedArray() {
    final int numTests = 1000;
    List<Integer> testValues = new LinkedList<Integer>();
    IntArray intArray = new IntArray(numTests, 7);
    Random random = new Random(42L);
    for (int i = 0; i < numTests; i++) {
      int testValue = random.nextInt() & intArray.getValueBitMask();
      testValues.add(testValue);
      intArray.add(i, testValue);
      Assert.assertEquals("During creation at index " + i, testValue, intArray.get(i));
    }

    for (int i = 0; i < numTests; i++) {
      Assert.assertEquals("At index " + i, testValues.get(i).intValue(), intArray.get(i));
    }
    Map<Integer, Integer> tests = new HashMap<Integer, Integer>();
  }

  public void testClearingCursor() {
    int range = 10000;
    int numBitsPerInt = 7;
    int maxCount = (1 << (numBitsPerInt)) - 1;

    SortedMap<Long, Integer> testValues = new TreeMap<Long, Integer>();
    IntArray intArray = new IntArray(range, numBitsPerInt);
    Random random = new Random(1L);
    while (testValues.size() < range / 4) {
      long value = random.nextInt(range);
      if (!testValues.containsKey(value)) {
        int count = random.nextInt(maxCount + 1);
        if (count > 0) {
          intArray.add(value, count);
          testValues.put(value, count);
        }
      }

    }

    IntArray.LongIntCursor cursor = intArray.clearingCursor();
    for (Map.Entry<Long, Integer> entry : testValues.entrySet()) {
      junit.framework.Assert.assertTrue(cursor.moveToNext());
      junit.framework.Assert.assertEquals((long) entry.getKey(), (long) cursor.getLong());
      junit.framework.Assert.assertEquals((int) entry.getValue(), cursor.getInt());
    }

    junit.framework.Assert.assertFalse(cursor.moveToNext());
    for (long block : intArray.getRawData()) {
      junit.framework.Assert.assertEquals(0L, block);
    }
  }

  public void testDefaultCursor() {
    int range = 10000;
    int numBitsPerInt = 7;
    int maxCount = (1 << (numBitsPerInt)) - 1;

    SortedMap<Long, Integer> testValues = new TreeMap<Long, Integer>();
    IntArray intArray = new IntArray(range, numBitsPerInt);
    Random random = new Random(1L);
    while (testValues.size() < range / 4) {
      long value = random.nextInt(range);
      if (!testValues.containsKey(value)) {
        int count = random.nextInt(maxCount + 1);
        if (count > 0) {
          intArray.add(value, count);
          testValues.put(value, count);
        }
      }

    }

    IntArray.LongIntCursor cursor = intArray.cursor();
    for (Map.Entry<Long, Integer> entry : testValues.entrySet()) {
      junit.framework.Assert.assertTrue(cursor.moveToNext());
      junit.framework.Assert.assertEquals((long) entry.getKey(), (long) cursor.getLong());
      junit.framework.Assert.assertEquals((int) entry.getValue(), cursor.getInt());
    }

    junit.framework.Assert.assertFalse(cursor.moveToNext());
  }

}
