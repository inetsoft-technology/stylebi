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
import inetsoft.report.internal.PagedEnumeration;
import inetsoft.report.pdf.PDF3Generator;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.swap.SwapFileReadException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77984: a page group of a batch-waiting report (a page total in the header) whose swap
 * file can not be read back while a PDF is written must fail the PDF export, not write blank
 * pages, and must keep the good swap file. Runs the real ReportCache paging processor,
 * PagedEnumeration and PDF3Generator.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PagedPdfSwapReadFailureTest {
   @BeforeEach
   void setWorkset() {
      oldWorkset = SreeEnv.getProperty("replet.cache.workset");
      // 3 pages in a page group
      SreeEnv.setProperty("replet.cache.workset", "3");
   }

   @AfterEach
   void resetWorkset() {
      SreeEnv.setProperty("replet.cache.workset", oldWorkset);
   }

   // the second page group is swapped while the report is generated and its file is missing
   // while the PDF is written: the export fails, and the good file survives the next swap
   @Test
   void failedPageReadFailsPdfExport() throws Exception {
      run(true);
   }

   // the same report with a readable swap file is written in full
   @Test
   void pdfExportReadsSwappedPages() throws Exception {
      run(false);
   }

   private void run(boolean fail) throws Exception {
      CountDownLatch reached = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      List<StylePage> source = new ArrayList<>();

      for(int i = 0; i < 9; i++) {
         StylePage pg = new StylePage(new Dimension(612, 792));
         pg.addPaintable(new PageGroupSwapReadFailureTest.WaitingPaintable("t" + i));
         pg.addPaintable(new PageGroupSwapWriteFailureTest.DataPaintable("p" + i));
         source.add(pg);
      }

      // stop the paging processor in the third group, so the test can swap the second group
      // while the report is not completed
      Enumeration<StylePage> pages = new Enumeration<>() {
         @Override
         public boolean hasMoreElements() {
            return next < source.size();
         }

         @Override
         public StylePage nextElement() {
            if(next == 7) {
               reached.countDown();

               try {
                  release.await(30, TimeUnit.SECONDS);
               }
               catch(InterruptedException ignore) {
               }
            }

            return source.get(next++);
         }

         private int next;
      };

      PagedEnumeration penum = null;

      try {
         // batch=false: the batch=true constructor waits for the report to complete, the test
         // waits for it below after the swap
         penum = new PagedEnumeration(pages, true, false);
         Object id = field(penum, "id");
         ReportCache cache = (ReportCache) field(penum, "cache");
         assertTrue(reached.await(30, TimeUnit.SECONDS));

         PageGroup group = cache.pgmap.get(cache.getSwapFile(id, 3));
         assertNotNull(group);
         assertTrue(group.swap(), "swapped while the report is not completed");
         release.countDown();

         for(int i = 0; i < 600 && cache.getProcessStatus(id) != ReportCache.COMPLETED; i++) {
            Thread.sleep(50);
         }

         assertEquals(ReportCache.COMPLETED, cache.getProcessStatus(id));
         File file = group.getSwapFile();
         File moved = new File(file.getPath() + ".moved");
         long length = file.length();
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         PDF3Generator generator = new PDF3Generator(out);

         if(!fail) {
            generator.generate(penum);
            assertEquals(9, countPages(out));
            return;
         }

         assertTrue(file.renameTo(moved));

         try {
            final PagedEnumeration penum0 = penum;
            assertThrows(SwapFileReadException.class, () -> generator.generate(penum0));
         }
         finally {
            assertTrue(moved.renameTo(file));
         }

         // the report is completed now, a swap must not rewrite the file from the unread pages
         assertFalse(group.swap());
         assertEquals(length, file.length());

         for(int i = 3; i < 6; i++) {
            StylePage pg = cache.getPage(id, i);
            assertEquals("t" + i, value(pg, 0));
            assertEquals("p" + i, value(pg, 1));
         }
      }
      finally {
         release.countDown();

         if(penum != null) {
            penum.dispose();
         }
      }
   }

   private static Object field(PagedEnumeration penum, String name) throws Exception {
      Field field = PagedEnumeration.class.getDeclaredField(name);
      field.setAccessible(true);
      return field.get(penum);
   }

   private static String value(StylePage pg, int idx) {
      Object pt = pg.getPaintable(idx);
      return pt == null ? null : ((PageGroupSwapWriteFailureTest.DataPaintable) pt).value;
   }

   private static int countPages(ByteArrayOutputStream out) {
      String pdf = new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
      Matcher matcher = Pattern.compile("/Type\s*/Page[^s]").matcher(pdf);
      int count = 0;

      while(matcher.find()) {
         count++;
      }

      return count;
   }

   private String oldWorkset;
}
