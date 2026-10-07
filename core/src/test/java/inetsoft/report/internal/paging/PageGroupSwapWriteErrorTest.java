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

import inetsoft.report.StylePage;
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
 * Bug #77962: a PageGroup swap write that fails with an Error (not an IOException), such as an
 * OutOfMemoryError under the memory pressure that triggers swapping, must not leave a file that a
 * later swap reuses as complete.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PageGroupSwapWriteErrorTest {
   @BeforeEach
   void createSwapFile() throws IOException {
      swapfile = Files.createTempFile("pg77962e", ".tdat").toFile();
      assertTrue(swapfile.delete());
   }

   @AfterEach
   void deleteSwapFile() {
      swapfile.delete();
   }

   // the Error escapes swap(), and the next swap writes a complete file instead of reusing a stub
   @Test
   void nextSwapRewritesFileAfterError() {
      PageGroup group = group(page("a0", "a1"), errorPage("b0", "b1"));

      failWrites[0] = true;
      assertThrows(OutOfMemoryError.class, group::swap);
      failWrites[0] = false;
      assertTrue(group.isValid(), "the pages are still in memory");
      assertEquals("a0", value(group, 0, 0));
      assertEquals("b1", value(group, 1, 1));

      assertTrue(group.swap());
      assertFalse(group.isValid());
      assertTrue(swapfile.length() > 0, "the next swap writes the file");
      assertEquals("a0", value(group, 0, 0));
      assertEquals("a1", value(group, 0, 1));
      assertEquals("b0", value(group, 1, 0));
      assertEquals("b1", value(group, 1, 1));
   }

   // the file left by the Error could not be deleted, so getPage(last) finds it and runs
   // swap0(true), which must rewrite it instead of reusing it
   @Test
   void fastSwapRewritesFileLeftByError() throws IOException {
      PageGroup group = group(page("a0", "a1"), errorPage("b0", "b1"));

      failWrites[0] = true;
      assertThrows(OutOfMemoryError.class, group::swap);
      failWrites[0] = false;

      // stands in for the stub a failed delete leaves behind
      if(!swapfile.exists()) {
         assertTrue(swapfile.createNewFile());
      }

      group.getPage(1, false);
      assertTrue(swapfile.length() > 0, "the stub is rewritten");
      assertEquals("a0", value(group, 0, 0));
      assertEquals("a1", value(group, 0, 1));
      assertEquals("b0", value(group, 1, 0));
      assertEquals("b1", value(group, 1, 1));
   }

   private static StylePage page(String... values) {
      StylePage pg = new StylePage(new Dimension(100, 100));

      for(String v : values) {
         pg.addPaintable(new PageGroupSwapWriteFailureTest.DataPaintable(v));
      }

      return pg;
   }

   // the last paintable throws an Error while failWrites[0] is set
   private StylePage errorPage(String value, String errorValue) {
      StylePage pg = page(value);
      pg.addPaintable(new ErrorPaintable(errorValue, failWrites));
      return pg;
   }

   private PageGroup group(StylePage... pages) {
      PageGroup group = new PageGroup(0, (byte) pages.length, swapfile,
                                      new PageGroupSwapWriteFailureTest.Proc(), 0);

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

   static class ErrorPaintable extends PageGroupSwapWriteFailureTest.DataPaintable {
      ErrorPaintable(String value, boolean[] fail) {
         super(value);
         this.fail = fail;
      }

      private void writeObject(ObjectOutputStream out) throws IOException {
         if(fail != null && fail[0]) {
            throw new OutOfMemoryError("simulated");
         }

         out.defaultWriteObject();
      }

      private final transient boolean[] fail;
   }

   private final boolean[] failWrites = { false };
   private File swapfile;
}
