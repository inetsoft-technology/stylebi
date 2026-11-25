/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.util.swap;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82040, an XIntFragment built from an int[] longer than 65535 kept its size and position
 * in char fields, so size() returned length mod 65536 (70000 -> 4464, 65536 -> 0), and a swap
 * wrote a char header and only that many ints, so the array itself was cut after one swap.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class XIntFragmentTest {
   @ParameterizedTest
   @ValueSource(ints = { 65535, 65536, 70000, 131079 })
   void sizeIsTheArrayLength(int n) {
      XIntFragment fragment = fragment(n);

      try {
         assertEquals(n, fragment.size());
         assertEquals(n, fragment.available());
         assertEquals(n, fragment.capacity());
         assertEquals(n - 1, fragment.getSafely(n - 1));
      }
      finally {
         fragment.dispose();
      }
   }

   @ParameterizedTest
   @ValueSource(ints = { 65536, 70000 })
   void swapRoundTripKeepsEveryValue(int n) {
      XIntFragment fragment = fragment(n);

      try {
         assertTrue(fragment.swap(), "fragment was not swapped");
         assertFalse(fragment.isValid(), "fragment is still in memory");

         int[] arr = fragment.getArray();

         assertTrue(fragment.isValid(), "fragment was not read back");
         assertEquals(n, arr.length, "values lost by the swap round trip");
         assertEquals(n, fragment.size());
         assertEquals(5000, arr[5000]);
         assertEquals(n - 1, fragment.getSafely(n - 1));
      }
      finally {
         fragment.dispose();
      }
   }

   @Test
   void serializedCopyKeepsEveryValue() throws Exception {
      XIntFragment fragment = fragment(70000);

      try {
         assertCopy(fragment, 70000);
      }
      finally {
         fragment.dispose();
      }
   }

   @Test
   void serializedCopyOfSwappedFragmentKeepsEveryValue() throws Exception {
      XIntFragment fragment = fragment(70000);

      try {
         assertTrue(fragment.swap(), "fragment was not swapped");
         assertFalse(fragment.isValid(), "fragment is still in memory");
         assertCopy(fragment, 70000);
      }
      finally {
         fragment.dispose();
      }
   }

   /**
    * The swap header holds the full position, so a buffer written by serialize() is read back by
    * validate() with every value.
    */
   @Test
   void swapHeaderHoldsPositionAbove65535() throws Exception {
      XIntFragment fragment = fragment(70000);

      try {
         Method length = XIntFragment.class.getDeclaredMethod("length");
         Method serialize = XIntFragment.class.getDeclaredMethod("serialize", ByteBuffer.class);
         Method validate = XIntFragment.class.getDeclaredMethod("validate", ByteBuffer.class);
         length.setAccessible(true);
         serialize.setAccessible(true);
         validate.setAccessible(true);

         long len = (long) length.invoke(fragment);
         assertEquals(4L * 70000 + 4, len);

         ByteBuffer buf = ByteBuffer.allocate((int) len);
         serialize.invoke(fragment, buf);
         assertEquals(len, buf.position(), "serialize() must fill exactly length() bytes");
         buf.flip();
         assertEquals(70000, buf.getInt(0), "swap header must hold the full position");

         XIntFragment copy = fragment(1);

         try {
            validate.invoke(copy, buf);
            assertEquals(70000, copy.size());
            assertEquals(69999, copy.getSafely(69999));
         }
         finally {
            copy.dispose();
         }
      }
      finally {
         fragment.dispose();
      }
   }

   private static void assertCopy(XIntFragment fragment, int n) throws Exception {
      XIntFragment copy = (XIntFragment) deserialize(serialize(fragment));

      try {
         assertEquals(n, copy.size());
         assertEquals(n, copy.getArray().length);
         assertEquals(n - 1, copy.getSafely(n - 1));
      }
      finally {
         copy.dispose();
      }
   }

   /**
    * A fragment with values 0..n-1. It is taken off the shared swapper so only this test swaps it.
    */
   private static XIntFragment fragment(int n) {
      int[] values = new int[n];

      for(int i = 0; i < n; i++) {
         values[i] = i;
      }

      XIntFragment fragment = new XIntFragment(values);
      XSwapper.getSwapper().deregister(fragment);
      return fragment;
   }

   private static byte[] serialize(Object obj) throws IOException {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(obj);
      }

      return bytes.toByteArray();
   }

   private static Object deserialize(byte[] bytes) throws Exception {
      try(ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
         return in.readObject();
      }
   }
}
