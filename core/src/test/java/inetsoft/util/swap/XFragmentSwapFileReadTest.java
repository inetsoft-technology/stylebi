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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77626, a swapped fragment whose swap file could not be read back was marked valid with
 * no data, so XIntFragment returned 0 (the header row of the base table) for every row and the
 * next swap wrote that empty state over the swap file. A swap that failed after creating an
 * empty swap file also had the empty file reused by the next swap, losing the data for good.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class XFragmentSwapFileReadTest {
   @Test
   void intFragmentWithMissingSwapFileFails() {
      XIntFragment fragment = createSwappedIntFragment();
      File file = intSwapFile(fragment);
      assertTrue(file.delete(), "swap file was not deleted");

      SwapFileReadException ex =
         assertThrows(SwapFileReadException.class, () -> fragment.getSafely(5));
      assertEquals(file, ex.getFile());
      assertThrows(SwapFileReadException.class, fragment::size);
      assertFalse(fragment.isValid());
      // the empty state must not be written back as the swap file
      assertFalse(fragment.swap(), "fragment without data was swapped");
      assertFalse(file.exists(), "empty state was written to the swap file");
      fragment.dispose();
   }

   @Test
   void intFragmentWithTruncatedSwapFileFails() throws Exception {
      XIntFragment fragment = createSwappedIntFragment();
      truncate(intSwapFile(fragment), 0);

      assertThrows(SwapFileReadException.class, () -> fragment.getSafely(5));
      assertFalse(fragment.isValid());
      fragment.dispose();
   }

   @Test
   void intFragmentIsReadOnceSwapFileIsReadableAgain() {
      XIntFragment fragment = createSwappedIntFragment();
      File file = intSwapFile(fragment);
      assertTrue(file.setReadable(false), "swap file was not made unreadable");

      try {
         assertThrows(SwapFileReadException.class, () -> fragment.getSafely(5));
      }
      finally {
         assertTrue(file.setReadable(true));
      }

      assertEquals(1005, fragment.getSafely(5));
      assertEquals(100, fragment.size());
      fragment.dispose();
   }

   @Test
   void intFragmentKeepsDataWhenSwapFailsAfterCreatingSwapFile() {
      XIntFragment fragment = new XIntFragment(createValues());
      File file = intSwapFile(fragment);
      XSwapper swapper = spy(XSwapper.getSwapper());
      doThrow(new IllegalStateException("swap aborted")).when(swapper).waitForMemory();
      fragment.swapper = swapper;

      assertTrue(fragment.swap(), "fragment was not swapped");
      assertEquals(0, file.length(), "swap did not leave an empty swap file");
      fragment.swapper = XSwapper.getSwapper();

      assertEquals(1005, fragment.getSafely(5));

      // the next swap must write the data instead of reusing the empty swap file
      assertTrue(fragment.swap(), "fragment was not swapped again");
      assertEquals(1005, fragment.getSafely(5), "data was lost in the reused empty swap file");
      assertEquals(100, fragment.size());
      fragment.dispose();
   }

   @Test
   void intFragmentKeepsDataWhenSwapFileCannotBeCreated() {
      XIntFragment fragment = new XIntFragment(createValues());
      File file = intSwapFile(fragment);

      withReadOnlyDirectory(file.getParentFile(), () -> assertTrue(fragment.swap()));

      assertFalse(file.exists());
      assertEquals(1005, fragment.getSafely(5));
      assertEquals(100, fragment.size());
      fragment.dispose();
   }

   @Test
   void disposedIntFragmentDoesNotFail() {
      XIntFragment fragment = createSwappedIntFragment();
      fragment.dispose();

      assertEquals(0, fragment.getSafely(5));
   }

   @Test
   void objectFragmentWithMissingSwapFileFails() {
      XObjectFragment<String> fragment = createSwappedObjectFragment();
      File file = objectSwapFile(fragment);
      assertTrue(file.delete(), "swap file was not deleted");

      SwapFileReadException ex =
         assertThrows(SwapFileReadException.class, () -> fragment.getSafely(5));
      assertEquals(file, ex.getFile());
      assertFalse(fragment.isValid());
      assertFalse(fragment.swap(), "fragment without data was swapped");
      fragment.dispose();
   }

   @Test
   void objectFragmentWithTruncatedSwapFileFails() throws Exception {
      XObjectFragment<String> fragment = createSwappedObjectFragment();
      File file = objectSwapFile(fragment);
      truncate(file, file.length() / 2);

      assertThrows(SwapFileReadException.class, () -> fragment.getSafely(5));
      assertFalse(fragment.isValid());
      fragment.dispose();
   }

   @Test
   void emptyObjectFragmentIsReadBackWithoutSwapFile() {
      XObjectFragment<String> fragment = new XObjectFragment<>((char) 10, (char) 100, String.class);
      fragment.complete();

      assertTrue(fragment.swap(), "fragment was not swapped");
      assertFalse(objectSwapFile(fragment).exists());
      assertNull(fragment.getSafely(0));
      assertEquals(0, fragment.size());
      assertTrue(fragment.isValid());
      fragment.dispose();
   }

   @Test
   void objectFragmentKeepsDataWhenSwapFileCannotBeCreated() {
      XObjectFragment<String> fragment = createObjectFragment();
      File file = objectSwapFile(fragment);

      withReadOnlyDirectory(file.getParentFile(), () -> assertTrue(fragment.swap()));

      assertFalse(file.exists());
      assertEquals("v5", fragment.getSafely(5));
      assertEquals(100, fragment.size());
      fragment.dispose();
   }

   @Test
   void objectFragmentWithMissingLaterSwapFileFails() {
      XObjectFragment<String> fragment = createLargeObjectFragment();
      assertTrue(fragment.swap(), "fragment was not swapped");
      File file = fragment.getFile(fragment.prefix + "_1.tdat");
      assertTrue(file.exists(), "fragment was not written to several swap files");
      assertTrue(file.delete(), "swap file was not deleted");

      // the rows read from the first swap file must not be returned as the whole fragment
      SwapFileReadException ex =
         assertThrows(SwapFileReadException.class, () -> fragment.getSafely(0));
      assertEquals(file, ex.getFile());
      assertFalse(fragment.isValid());
      fragment.dispose();
   }

   @Test
   void objectFragmentKeepsDataWhenSwapFailsAfterFirstSwapFile() {
      XObjectFragment<String> fragment = createLargeObjectFragment();
      File file = objectSwapFile(fragment);
      File file1 = fragment.getFile(fragment.prefix + "_1.tdat");
      // a directory in place of the second swap file makes the swap fail after the first one
      assertTrue(file1.mkdir(), "second swap file was not blocked");

      try {
         assertTrue(fragment.swap(), "fragment was not swapped");
         assertTrue(file.exists(), "first swap file was not written");
      }
      finally {
         assertTrue(file1.delete());
      }

      assertEquals(largeValue(99), fragment.getSafely(99));
      assertEquals(largeValue(0), fragment.getSafely(0));
      assertFalse(file.exists(), "partial swap file was kept");

      // the next swap must write all the rows again
      assertTrue(fragment.swap(), "fragment was not swapped again");
      assertEquals(largeValue(99), fragment.getSafely(99));
      fragment.dispose();
   }

   @Test
   void disposedObjectFragmentDoesNotFail() {
      XObjectFragment<String> fragment = createSwappedObjectFragment();
      fragment.dispose();

      assertNull(fragment.getSafely(5));
   }

   private static XIntFragment createSwappedIntFragment() {
      XIntFragment fragment = new XIntFragment(createValues());
      assertTrue(fragment.swap(), "fragment was not swapped");
      assertFalse(fragment.isValid(), "fragment is still in memory");
      assertTrue(intSwapFile(fragment).exists(), "swap file was not written");
      return fragment;
   }

   private static XObjectFragment<String> createObjectFragment() {
      XObjectFragment<String> fragment = new XObjectFragment<>((char) 100, (char) 100, String.class);

      for(int i = 0; i < 100; i++) {
         fragment.add("v" + i);
      }

      fragment.complete();
      return fragment;
   }

   /**
    * Create a fragment too large for one swap file block.
    */
   private static XObjectFragment<String> createLargeObjectFragment() {
      XObjectFragment<String> fragment = new XObjectFragment<>((char) 100, (char) 100, String.class);

      for(int i = 0; i < 100; i++) {
         fragment.add(largeValue(i));
      }

      fragment.complete();
      return fragment;
   }

   private static String largeValue(int i) {
      // random letters, so the block is not compressed below the swap buffer size
      Random random = new Random(i);
      StringBuilder value = new StringBuilder().append(i).append(':');

      for(int k = 0; k < 6000; k++) {
         value.append((char) ('a' + random.nextInt(26)));
      }

      return value.toString();
   }

   private static XObjectFragment<String> createSwappedObjectFragment() {
      XObjectFragment<String> fragment = createObjectFragment();
      assertTrue(fragment.swap(), "fragment was not swapped");
      assertFalse(fragment.isValid(), "fragment is still in memory");
      assertTrue(objectSwapFile(fragment).exists(), "swap file was not written");
      return fragment;
   }

   private static File intSwapFile(XIntFragment fragment) {
      return fragment.getFile(fragment.prefix + ".tdat");
   }

   private static File objectSwapFile(XObjectFragment<?> fragment) {
      return fragment.getFile(fragment.prefix + "_0.tdat");
   }

   private static void truncate(File file, long length) throws Exception {
      try(RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
         raf.setLength(length);
      }
   }

   private static void withReadOnlyDirectory(File dir, Runnable action) {
      assertTrue(dir.setWritable(false), "cache directory was not made read-only");

      try {
         action.run();
      }
      finally {
         assertTrue(dir.setWritable(true));
      }
   }

   private static int[] createValues() {
      int[] values = new int[100];

      for(int i = 0; i < values.length; i++) {
         values[i] = 1000 + i;
      }

      return values;
   }
}
