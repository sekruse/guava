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

}
