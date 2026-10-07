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
package inetsoft.mv.data;

import inetsoft.test.*;
import inetsoft.util.FileSystemService;
import inetsoft.util.swap.XSwappable;
import inetsoft.util.swap.XSwapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77961: a swap whose file write fails must keep the in-memory data, and must not leave
 * a stub that a later swap takes as an already swapped copy.
 *
 * <p>The write failure is a real {@code ClosedByInterruptException}: the swap file is opened
 * with a non-interruptible call (so a stub is created), and the first interruptible channel
 * call throws. This is the technique of {@code XTableFragmentSwapInterruptTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XDimSwapWriteFailureTest {
   // XDimDictionary

   @Test
   void dictionaryControlSuccessfulSwap() throws Exception {
      XDimDictionary dict = newDict();
      File file = dictFile(dict);

      try {
         assertTrue(dict.swap());
         assertFalse(dict.isValid());
         assertTrue(file.length() > 0);
         assertEquals("Boston", dict.getValue(0));
         assertEquals("Chicago", dict.getValue(1));
      }
      finally {
         dict.dispose();
      }
   }

   @Test
   void dictionaryKeepsValuesWhenSwapWriteFails() throws Exception {
      XDimDictionary dict = newDict();
      File file = dictFile(dict);

      try {
         boolean swapped = swapInterrupted(dict::swap);

         assertEquals("Boston", dict.getValue(0), "values lost after a failed swap write");
         assertEquals("Chicago", dict.getValue(1), "values lost after a failed swap write");
         assertEquals(1, dict.indexOf("Chicago", 0));
         assertFalse(swapped, "a failed write must not count as swapped");
         assertTrue(dict.isValid());
         assertFalse(file.exists(), "the stub of the failed write must be removed");

         // a later swap writes the file and the values read back from it
         assertTrue(dict.swap());
         assertFalse(dict.isValid());
         assertTrue(file.length() > 0);
         assertEquals("Boston", dict.getValue(0));
         assertEquals("Chicago", dict.getValue(1));
      }
      finally {
         dict.dispose();
      }
   }

   @Test
   void dictionaryRewritesStubThatCouldNotBeDeleted() throws Exception {
      XDimDictionary dict = newDict();
      File file = dictFile(dict);

      try {
         swapInterrupted(dict::swap);
         assertEquals("Boston", dict.getValue(0), "values lost after a failed swap write");
         // as if the delete of the stub had failed
         assertTrue(file.createNewFile());

         assertTrue(dict.swap());
         assertTrue(file.length() > 0, "the stub must be rewritten, not taken as swapped");
         assertEquals("Boston", dict.getValue(0));
         assertEquals("Chicago", dict.getValue(1));
      }
      finally {
         dict.dispose();
      }
   }

   // XDimIndex

   @Test
   void indexControlSuccessfulSwap() throws Exception {
      BitDimIndex index = newIndex();
      File file = indexFile(index);

      try {
         assertTrue(index.swap());
         assertFalse(index.isValid());
         assertTrue(file.length() > 0);
         assertIndexRows(index);
      }
      finally {
         index.dispose();
      }
   }

   @Test
   void indexKeepsRowsWhenSwapWriteFails() throws Exception {
      BitDimIndex index = newIndex();
      File file = indexFile(index);

      try {
         boolean swapped = swapInterrupted(index::swap);

         assertIndexRows(index);
         assertFalse(swapped, "a failed write must not count as swapped");
         assertTrue(index.isValid());
         assertFalse(file.exists(), "the stub of the failed write must be removed");

         // a later swap writes the file and the rows read back from it
         assertTrue(index.swap());
         assertFalse(index.isValid());
         assertTrue(file.length() > 0);
         assertIndexRows(index);
      }
      finally {
         index.dispose();
      }
   }

   @Test
   void indexRewritesStubThatCouldNotBeDeleted() throws Exception {
      BitDimIndex index = newIndex();
      File file = indexFile(index);

      try {
         swapInterrupted(index::swap);
         assertIndexRows(index);
         // as if the delete of the stub had failed
         assertTrue(file.createNewFile());

         assertTrue(index.swap());
         assertTrue(file.length() > 0, "the stub must be rewritten, not taken as swapped");
         assertIndexRows(index);
      }
      finally {
         index.dispose();
      }
   }

   // MVDecimalColumn.Fragment

   @Test
   void measureFragmentControlSuccessfulSwap() throws Exception {
      MVDoubleColumn col = newMeasureColumn();
      MVDecimalColumn.Fragment fragment = col.fragments[0];

      try {
         assertTrue(fragment.swap());
         assertFalse(fragment.isValid());
         assertEquals(7.5, col.getValue(7));
         assertEquals(100.5, col.getValue(100));
      }
      finally {
         fragment.dispose();
         col.dispose();

         if(col.file != null) {
            col.file.delete();
         }
      }
   }

   @Test
   void measureFragmentKeepsValuesWhenSwapWriteFails() throws Exception {
      MVDoubleColumn col = newMeasureColumn();
      MVDecimalColumn.Fragment fragment = col.fragments[0];

      try {
         boolean swapped = swapInterrupted(fragment::swap);

         assertEquals(7.5, col.getValue(7), "values lost after a failed swap write");
         assertEquals(100.5, col.getValue(100), "values lost after a failed swap write");
         assertFalse(swapped, "a failed write must not count as swapped");
         assertTrue(fragment.isValid());

         // a later swap writes the block and the values read back from it
         assertTrue(fragment.swap());
         assertFalse(fragment.isValid());
         assertEquals(7.5, col.getValue(7));
         assertEquals(100.5, col.getValue(100));
      }
      finally {
         fragment.dispose();
         col.dispose();

         if(col.file != null) {
            col.file.delete();
         }
      }
   }

   /**
    * Swap with the thread interrupted, so the first interruptible write call throws.
    */
   private static boolean swapInterrupted(BooleanSupplier swap) {
      Thread.currentThread().interrupt();

      try {
         return swap.getAsBoolean();
      }
      finally {
         Thread.interrupted();
      }
   }

   private static XDimDictionary newDict() {
      XDimDictionary dict = new XDimDictionary();
      dict.addValue("Boston");
      dict.addValue("Chicago");
      dict.complete();
      // only the test thread swaps it
      XSwapper.getSwapper().deregister(dict);
      assertEquals("Boston", dict.getValue(0));
      return dict;
   }

   private static BitDimIndex newIndex() {
      BitDimIndex index = new BitDimIndex();
      index.addKey(5, 1);
      index.addKey(7, 3);
      index.complete();
      // only the test thread swaps it
      XSwapper.getSwapper().deregister(index);
      assertIndexRows(index);
      return index;
   }

   private static MVDoubleColumn newMeasureColumn() {
      int size = AbstractMeasureColumn.BLOCK_SIZE;
      MVDoubleColumn col = new MVDoubleColumn(null, 0, null, size, true);

      for(int i = 0; i < size; i++) {
         col.setValue(i, i + 0.5);
      }

      // only the test thread swaps it
      XSwapper.getSwapper().deregister(col.fragments[0]);
      assertTrue(col.fragments[0].isValid());
      return col;
   }

   private static void assertIndexRows(BitDimIndex index) {
      assertTrue(index.getRows(5, false).get(1));
      assertTrue(index.getRows(7, false).get(3));
   }

   private static File dictFile(XDimDictionary dict) throws Exception {
      File file = FileSystemService.getInstance().getCacheFile(prefix(dict) + "_dict.tdat");
      assertFalse(file.exists(), "setup: swap file already exists");
      return file;
   }

   private static File indexFile(XDimIndex index) throws Exception {
      File file = FileSystemService.getInstance().getCacheFile(prefix(index) + ".tdat");
      assertFalse(file.exists(), "setup: swap file already exists");
      return file;
   }

   private static String prefix(XSwappable obj) throws Exception {
      Field field = XSwappable.class.getDeclaredField("prefix");
      field.setAccessible(true);
      return (String) field.get(obj);
   }
}
