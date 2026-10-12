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
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

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

   @Test
   void dictionaryKeepsValuesWhenSwapFileCannotBeOpened() throws Exception {
      XDimDictionary dict = newDict();
      File file = dictFile(dict);

      try {
         swapInterrupted(dict::swap);
         // a stub that cannot be opened for write, so the rewrite fails with an IOException
         assertTrue(file.createNewFile());
         assertTrue(file.setReadOnly());
         assumeFalse(file.canWrite(), "the read-only file is writable here");

         assertFalse(dict.swap(), "a failed write must not count as swapped");
         assertEquals("Boston", dict.getValue(0), "values lost after a failed swap write");
         assertEquals("Chicago", dict.getValue(1), "values lost after a failed swap write");
         assertTrue(dict.isValid());

         // the failure path may already have deleted the stub
         file.setWritable(true);
         assertTrue(dict.swap());
         assertFalse(dict.isValid());
         assertTrue(file.length() > 0, "the stub must be rewritten, not taken as swapped");
         assertEquals("Boston", dict.getValue(0));
         assertEquals("Chicago", dict.getValue(1));
      }
      finally {
         file.setWritable(true);
         dict.dispose();
      }
   }

   @Test
   void indexRewritesStubLongerThanItsData() throws Exception {
      BitDimIndex index = newIndex();
      File file = indexFile(index);

      try {
         swapInterrupted(index::swap);
         assertIndexRows(index);
         // as if a partly written stub of other data could not be deleted
         byte[] garbage = new byte[4096];
         Arrays.fill(garbage, (byte) 0x7f);
         Files.write(file.toPath(), garbage);

         assertTrue(index.swap());
         assertFalse(index.isValid());
         assertIndexRows(index);
      }
      finally {
         index.dispose();
      }
   }

   @Test
   void measureFragmentSharesFileWithStubOfFailedWrite() throws Exception {
      int size = AbstractMeasureColumn.BLOCK_SIZE;
      MVDoubleColumn col = newMeasureColumn(size * 2);
      MVDecimalColumn.Fragment fragment0 = col.fragments[0];
      MVDecimalColumn.Fragment fragment1 = col.fragments[1];

      try {
         // the failed write of fragment 0 leaves the shared column file as a stub
         assertFalse(swapInterrupted(fragment0::swap));
         assertEquals(7.5, col.getValue(7), "values lost after a failed swap write");

         assertTrue(fragment1.swap());
         assertTrue(fragment0.swap());
         assertFalse(fragment0.isValid());
         assertFalse(fragment1.isValid());
         assertEquals(7.5, col.getValue(7));
         assertEquals(size - 0.5, col.getValue(size - 1));
         assertEquals(size + 3.5, col.getValue(size + 3));
         assertEquals(2 * size - 0.5, col.getValue(2 * size - 1));
      }
      finally {
         fragment0.dispose();
         fragment1.dispose();
         col.dispose();

         if(col.file != null) {
            col.file.delete();
         }
      }
   }

   // an Error (not an Exception) thrown after the swap file is created skips the catch,
   // so the stub must still be marked for rewrite

   @Test
   void dictionaryRewritesStubAfterErrorDuringWrite() throws Exception {
      XDimDictionary dict = newDict();
      File file = dictFile(dict);

      try {
         dict.testBeforeWrite = () -> {
            throw new OutOfMemoryError("simulated");
         };

         assertThrows(OutOfMemoryError.class, dict::swap);
         dict.testBeforeWrite = null;
         assertTrue(file.exists(), "setup: the failed write must leave a stub");
         assertEquals("Boston", dict.getValue(0), "values lost after a failed swap write");

         assertTrue(dict.swap());
         assertTrue(file.length() > 0, "the stub must be rewritten, not taken as swapped");
         assertEquals("Boston", dict.getValue(0));
         assertEquals("Chicago", dict.getValue(1));
      }
      finally {
         dict.dispose();
      }
   }

   @Test
   void indexRewritesStubAfterErrorDuringWrite() throws Exception {
      ErrorOnWriteDimIndex index = new ErrorOnWriteDimIndex();
      index.addKey(2, 0);
      index.addKey(5, 1);
      index.addKey(2, 2);
      index.complete();
      // only the test thread swaps it
      XSwapper.getSwapper().deregister(index);
      File file = indexFile(index);

      try {
         index.error = new OutOfMemoryError("simulated");

         assertThrows(OutOfMemoryError.class, index::swap);
         assertTrue(file.exists(), "setup: the failed write must leave a stub");
         assertDictIndexRows(index);

         assertTrue(index.swap());
         assertFalse(index.isValid());
         assertTrue(file.length() > 0, "the stub must be rewritten, not taken as swapped");
         assertDictIndexRows(index);
      }
      finally {
         index.dispose();
      }
   }

   @Test
   void measureFragmentFailsAfterAnotherFragmentWasSwapped() throws Exception {
      int size = AbstractMeasureColumn.BLOCK_SIZE;
      MVDoubleColumn col = newMeasureColumn(size * 2);
      MVDecimalColumn.Fragment fragment0 = col.fragments[0];
      MVDecimalColumn.Fragment fragment1 = col.fragments[1];

      try {
         // fragment 0 is written to the shared column file, then fragment 1 fails
         assertTrue(fragment0.swap());
         assertFalse(swapInterrupted(fragment1::swap));
         assertEquals(size + 3.5, col.getValue(size + 3), "values lost after a failed swap write");
         assertEquals(7.5, col.getValue(7), "the written fragment must still read back");

         assertTrue(fragment0.swap());
         assertTrue(fragment1.swap());
         assertFalse(fragment0.isValid());
         assertFalse(fragment1.isValid());
         assertEquals(7.5, col.getValue(7));
         assertEquals(size - 0.5, col.getValue(size - 1));
         assertEquals(size + 3.5, col.getValue(size + 3));
         assertEquals(2 * size - 0.5, col.getValue(2 * size - 1));
      }
      finally {
         fragment0.dispose();
         fragment1.dispose();
         col.dispose();

         if(col.file != null) {
            col.file.delete();
         }
      }
   }

   private static void assertDictIndexRows(DictDimIndex index) {
      BitSet rows2 = index.getRows(2, false);
      assertTrue(rows2.get(0));
      assertTrue(rows2.get(2));
      assertTrue(index.getRows(5, false).get(1));
   }

   /**
    * A dict index whose write throws the given Error once, after the swap file is created.
    */
   private static final class ErrorOnWriteDimIndex extends DictDimIndex {
      ErrorOnWriteDimIndex() {
         super(8);
      }

      @Override
      public ByteBuffer write(WritableByteChannel channel, ByteBuffer sbuf) throws IOException {
         Error error = this.error;

         if(error != null) {
            this.error = null;
            throw error;
         }

         return super.write(channel, sbuf);
      }

      Error error;
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
      return newMeasureColumn(AbstractMeasureColumn.BLOCK_SIZE);
   }

   private static MVDoubleColumn newMeasureColumn(int size) {
      MVDoubleColumn col = new MVDoubleColumn(null, 0, null, size, true);

      for(int i = 0; i < size; i++) {
         col.setValue(i, i + 0.5);
      }

      // only the test thread swaps it
      for(MVDecimalColumn.Fragment fragment : col.fragments) {
         XSwapper.getSwapper().deregister(fragment);
         assertTrue(fragment.isValid());
      }

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
