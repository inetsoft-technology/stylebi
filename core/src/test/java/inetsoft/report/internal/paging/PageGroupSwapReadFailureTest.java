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
package inetsoft.report.internal.paging;

import inetsoft.report.ReportSheet;
import inetsoft.report.StylePage;
import inetsoft.test.*;
import inetsoft.util.swap.SwapFileReadException;
import inetsoft.util.swap.XSwapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.*;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77984: a PageGroup whose swap file can not be read back must fail the read, stay invalid
 * so a swap can not rewrite the good file from the pages that were not read, and read the whole
 * file again in step on a retry.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PageGroupSwapReadFailureTest {
   @BeforeEach
   void createSwapFile() throws IOException {
      swapfile = Files.createTempFile("pg77984", ".tdat").toFile();
      assertTrue(swapfile.delete());
      moved = new File(swapfile.getPath() + ".moved");
   }

   @AfterEach
   void deleteSwapFile() {
      swapfile.delete();
      moved.delete();
   }

   // swapped while the report is uncompleted, the read fails, the report completes: the next
   // swap must not rewrite the good file from the pages that were not read
   @Test
   void failedReadDoesNotRewriteFile() {
      Proc proc = new Proc();
      PageGroup group = group(proc, page("a0", "a1"), readFailPage("b0", "b1"), page("c0", "c1"));
      assertTrue(group.swap());
      long length = swapfile.length();

      failReads[0] = 1;
      assertThrows(SwapFileReadException.class, () -> group.getPage(0, true));
      failReads[0] = 0;
      assertFalse(group.isValid(), "a failed read leaves the group invalid");

      proc.completed = true;
      assertFalse(group.swap(), "an invalid group is not swapped");
      assertEquals(length, swapfile.length(), "the good file is kept");

      assertValues(group);
   }

   // the swap file is missing for a while (a real file failure, no test hook)
   @Test
   void missingFileFailsAndRecovers() throws IOException {
      Proc proc = new Proc();
      PageGroup group = group(proc, page("a0", "a1"), page("b0", "b1"), page("c0", "c1"));
      assertTrue(group.swap());
      long length = swapfile.length();

      assertTrue(swapfile.renameTo(moved));
      SwapFileReadException ex =
         assertThrows(SwapFileReadException.class, () -> group.getPage(0, true));
      assertEquals(swapfile, ex.getFile());
      assertInstanceOf(FileNotFoundException.class, ex.getCause());
      assertTrue(moved.renameTo(swapfile));

      proc.completed = true;
      assertFalse(group.swap());
      assertEquals(length, swapfile.length());
      assertValues(group);
   }

   // pages restored before the failure must not stay in memory, or a retry skips them while
   // their bytes are still in the stream and puts the data on the wrong pages
   @Test
   void retryAfterFailedReadStaysInStep() {
      Proc proc = new Proc();
      PageGroup group = group(proc, page("a0", "a1"), readFailPage("b0", "b1"), page("c0", "c1"));
      assertTrue(group.swap());

      failReads[0] = 1;
      assertThrows(SwapFileReadException.class, () -> group.getPage(1, true));
      failReads[0] = 0;

      assertValues(group);
   }

   // a partly swapped page (a batch-waiting paintable is left out of the file) only reads the
   // slots that are empty, so the slots a failed read filled must be cleared again
   @Test
   void partlySwappedPageGetsItsOwnDataAfterFailedRead() {
      Proc proc = new Proc();
      StylePage pb = page("b0");
      pb.addPaintable(new WaitingPaintable("bw"));
      pb.addPaintable(new ReadFailPaintable("b2"));
      PageGroup group = group(proc, page("a0", "a1"), pb, page("c0", "c1"));
      assertTrue(group.swap());

      failReads[0] = 1;
      assertThrows(SwapFileReadException.class, () -> group.getPage(0, true));
      failReads[0] = 0;

      assertFalse(group.swap(), "an invalid group is not swapped");
      assertEquals("a0", value(group, 0, 0));
      assertEquals("b0", value(group, 1, 0));
      assertEquals("bw", value(group, 1, 1));
      assertEquals("b2", value(group, 1, 2));
      assertEquals("c0", value(group, 2, 0));
      assertEquals("c1", value(group, 2, 1));
   }

   // an Error while reading is rethrown unchanged, and the group is reset for a retry
   @Test
   void errorDuringReadIsRethrownAndGroupIsReset() {
      Proc proc = new Proc();
      PageGroup group = group(proc, page("a0", "a1"), readFailPage("b0", "b1"), page("c0", "c1"));
      assertTrue(group.swap());

      failReads[0] = 2;
      assertThrows(OutOfMemoryError.class, () -> group.getPage(0, true));
      failReads[0] = 0;
      assertFalse(group.isValid());

      proc.completed = true;
      assertFalse(group.swap());
      assertValues(group);
   }

   // dispose(false) restores the pages first; a failed read must not stop the disposal
   @Test
   void disposeAfterFailedReadStillDisposes() {
      PageGroup group = group(new Proc(), page("a0"), page("b0"));
      assertTrue(group.swap());
      assertTrue(swapfile.renameTo(moved));

      assertDoesNotThrow(() -> group.dispose(false));
      assertNull(group.getSwapFile(), "the group is disposed");
   }

   // the read failure reaches the reader of the report pages instead of a null page, which ends
   // the paged output at that page without an error
   @Test
   void reportCacheRethrowsReadFailure() {
      ReportCache cache = new ReportCache(1);

      try {
         PageGroup group = group(new Proc(), page("a0"), page("b0"));
         assertTrue(group.swap());
         assertTrue(swapfile.renameTo(moved));
         cache.pgmap.put(cache.getSwapFile("r77984", 0), group);

         assertThrows(SwapFileReadException.class, () -> cache.getPageResult("r77984", 0));

         assertTrue(moved.renameTo(swapfile));
         StylePage page = cache.getPageResult("r77984", 0).getPage();
         assertEquals("a0", ((PageGroupSwapWriteFailureTest.DataPaintable) page.getPaintable(0)).value);
      }
      finally {
         cache.dispose();
      }
   }

   private void assertValues(PageGroup group) {
      assertEquals("a0", value(group, 0, 0));
      assertEquals("a1", value(group, 0, 1));
      assertEquals("b0", value(group, 1, 0));
      assertEquals("b1", value(group, 1, 1));
      assertEquals("c0", value(group, 2, 0));
      assertEquals("c1", value(group, 2, 1));
   }

   private static StylePage page(String... values) {
      StylePage pg = new StylePage(new Dimension(100, 100));

      for(String v : values) {
         pg.addPaintable(new PageGroupSwapWriteFailureTest.DataPaintable(v));
      }

      return pg;
   }

   // the last paintable fails to deserialize while failReads[0] is set
   private StylePage readFailPage(String value, String failValue) {
      StylePage pg = page(value);
      pg.addPaintable(new ReadFailPaintable(failValue));
      return pg;
   }

   private PageGroup group(Proc proc, StylePage... pages) {
      PageGroup group = new PageGroup(0, (byte) pages.length, swapfile, proc, 0);

      for(StylePage pg : pages) {
         group.addPage(pg);
      }

      group.complete();
      XSwapper.getSwapper().deregister(group);
      return group;
   }

   private static String value(PageGroup group, int page, int idx) {
      Object pt = group.getPage(page, true).getPaintable(idx);
      return pt == null ? null : ((PageGroupSwapWriteFailureTest.DataPaintable) pt).value;
   }

   // 1: IOException, 2: OutOfMemoryError. static, the paintable is deserialized from the file
   private static final int[] failReads = { 0 };

   static class ReadFailPaintable extends PageGroupSwapWriteFailureTest.DataPaintable {
      ReadFailPaintable(String value) {
         super(value);
      }

      private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
         if(failReads[0] == 1) {
            throw new IOException("simulated read failure");
         }

         if(failReads[0] == 2) {
            throw new OutOfMemoryError("simulated");
         }

         in.defaultReadObject();
      }
   }

   // a paintable that waits for the report to complete, such as a page total
   static class WaitingPaintable extends PageGroupSwapWriteFailureTest.DataPaintable {
      WaitingPaintable(String value) {
         super(value);
      }

      @Override
      public boolean isBatchWaiting() {
         return true;
      }
   }

   // a report that waits for the batch, as one with a page total in its header
   static class Proc implements PagingProcessor {
      @Override
      public boolean isOneOff() {
         return false;
      }

      @Override
      public boolean isInitialOneOff() {
         return false;
      }

      @Override
      public boolean isInteractive() {
         return false;
      }

      @Override
      public boolean isBatchWaiting() {
         return true;
      }

      @Override
      public int getPageCount() {
         return 3;
      }

      @Override
      public boolean isCompleted() {
         return completed;
      }

      @Override
      public ReportSheet getReport() {
         return null;
      }

      @Override
      public void join(boolean internal) {
      }

      volatile boolean completed;
   }

   private File swapfile;
   private File moved;
}
