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
import inetsoft.report.internal.BasePaintable;
import inetsoft.test.*;
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
 * Bug #77962: a failed PageGroup swap write must keep the pages in memory and must not leave a
 * file that a later swap reuses as complete.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PageGroupSwapWriteFailureTest {
   @BeforeEach
   void createSwapFile() throws IOException {
      swapfile = Files.createTempFile("pg77962", ".tdat").toFile();
      assertTrue(swapfile.delete());
   }

   @AfterEach
   void deleteSwapFile() {
      swapfile.delete();
   }

   // a paintable fails to serialize after an earlier one of the same page was written
   @Test
   void failedWriteKeepsPagesReadable() {
      StylePage p0 = page("a0");
      p0.addPaintable(new FailingPaintable("a1"));
      PageGroup group = group(p0, page("b0"));
      group.setUserObject("user-object");

      assertTrue(group.swap());
      assertEquals("a0", value(group, 0, 0));
      assertEquals("a1", value(group, 0, 1));
      assertEquals("b0", value(group, 1, 0));
      assertEquals("user-object", group.getUserObject());
      assertTrue(group.isValid(), "the pages are still in memory");
      assertFalse(swapfile.exists(), "the incomplete swap file is deleted");
   }

   // the failure is on the last paintable of the last page, after every other one was written
   @Test
   void failureAtLastPaintableKeepsWholeGroup() {
      StylePage p1 = page("b0");
      p1.addPaintable(new FailingPaintable("b1"));
      PageGroup group = group(page("a0", "a1"), p1);

      assertTrue(group.swap());
      assertEquals("a0", value(group, 0, 0));
      assertEquals("a1", value(group, 0, 1));
      assertEquals("b0", value(group, 1, 0));
      assertEquals("b1", value(group, 1, 1));
      assertFalse(swapfile.exists(), "the incomplete swap file is deleted");
   }

   // the write fails once on the first paintable (disk full), and the next swap writes a
   // complete file instead of reusing the stub
   @Test
   void nextSwapRewritesFileAfterFailedWrite() {
      StylePage p0 = page();
      p0.addPaintable(new FailingPaintable("a0", failWrites));
      p0.addPaintable(new DataPaintable("a1"));
      PageGroup group = group(p0, page("b0"));
      group.setUserObject("user-object");

      failWrites[0] = true;
      assertTrue(group.swap());
      assertEquals("a0", value(group, 0, 0));

      failWrites[0] = false;
      assertTrue(group.swap());
      assertFalse(group.isValid());
      assertEquals("a0", value(group, 0, 0));
      assertEquals("a1", value(group, 0, 1));
      assertEquals("b0", value(group, 1, 0));
      assertEquals("user-object", group.getUserObject());
      assertTrue(swapfile.length() > 0, "the next swap writes the file");
   }

   // the incomplete file could not be deleted, so it is still there for the fast swap of
   // getPage(last), which must rewrite it instead of reusing it
   @Test
   void fastSwapRewritesUndeletedIncompleteFile() throws IOException {
      PageGroup group = group(page("a0", "a1"), page("b0"));
      int[] writes = { 0 };

      // the pages are written, the failure is on the user object at the end of the file
      group.testBeforeWrite = () -> {
         if(++writes[0] == 3) {
            throw new UncheckedIOException(
               new IOException("No space left on device (simulated)"));
         }
      };

      assertTrue(group.swap());
      group.testBeforeWrite = null;
      // stands in for the stub a failed delete leaves behind
      if(!swapfile.exists()) {
         assertTrue(swapfile.createNewFile());
      }

      // the last page of the group runs swap0(true)
      StylePage last = group.getPage(1, false);
      assertEquals("a0", value(group, 0, 0));
      assertEquals("a1", value(group, 0, 1));
      assertEquals("b0", value(group, 1, 0));
      assertTrue(swapfile.length() > 0, "the stub is rewritten");
      assertTrue(last.isSwapped(), "the last page is swapped out by the rewrite");
   }

   // control: a successful swap round-trips every paintable and is reused by the next swap
   @Test
   void successfulSwapRoundTripsAndIsReused() {
      PageGroup group = group(page("a0", "a1"), page("b0"));
      group.setUserObject("user-object");

      assertTrue(group.swap());
      long length = swapfile.length();
      assertTrue(length > 0);
      assertEquals("a0", value(group, 0, 0));
      assertEquals("a1", value(group, 0, 1));
      assertEquals("b0", value(group, 1, 0));
      assertEquals("user-object", group.getUserObject());

      group.testBeforeWrite = () -> fail("a complete swap file is reused, not rewritten");
      assertTrue(group.swap());
      assertEquals(length, swapfile.length());
      assertEquals("a0", value(group, 0, 0));
      assertEquals("b0", value(group, 1, 0));
   }

   private static StylePage page(String... values) {
      StylePage pg = new StylePage(new Dimension(100, 100));

      for(String v : values) {
         pg.addPaintable(new DataPaintable(v));
      }

      return pg;
   }

   private PageGroup group(StylePage... pages) {
      PageGroup group = new PageGroup(0, (byte) pages.length, swapfile, new Proc(), 0);

      for(StylePage pg : pages) {
         group.addPage(pg);
      }

      // complete() so swap() accepts the group, then keep the background swapper away from it
      group.complete();
      XSwapper.getSwapper().deregister(group);
      return group;
   }

   private static String value(PageGroup group, int page, int idx) {
      // clone so the fast swap of getPage(last) does not clear the page that is read
      Object pt = group.getPage(page, true).getPaintable(idx);
      return pt == null ? null : ((DataPaintable) pt).value;
   }

   // failWrites[0] set: the FailingPaintable created with it fails to serialize
   private final boolean[] failWrites = { false };

   static class DataPaintable extends BasePaintable {
      DataPaintable(String value) {
         this.value = value;
      }

      @Override
      public void paint(Graphics g) {
      }

      @Override
      public Rectangle getBounds() {
         return new Rectangle(0, 0, 10, 10);
      }

      @Override
      public void setLocation(Point loc) {
      }

      @Override
      public Point getLocation() {
         return new Point(0, 0);
      }

      final String value;
   }

   // a paintable whose serialization fails, as a disk full error or a non-serializable field does
   static class FailingPaintable extends DataPaintable {
      FailingPaintable(String value) {
         this(value, new boolean[] { true });
      }

      FailingPaintable(String value, boolean[] fail) {
         super(value);
         this.fail = fail;
      }

      private void writeObject(ObjectOutputStream out) throws IOException {
         if(fail[0]) {
            throw new IOException("No space left on device (simulated)");
         }

         out.defaultWriteObject();
      }

      private final transient boolean[] fail;
   }

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
         return false;
      }

      @Override
      public int getPageCount() {
         return 2;
      }

      @Override
      public boolean isCompleted() {
         return true;
      }

      @Override
      public ReportSheet getReport() {
         return null;
      }

      @Override
      public void join(boolean internal) {
      }
   }

   private File swapfile;
}
