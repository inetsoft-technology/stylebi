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
package inetsoft.report.internal;

import inetsoft.report.*;
import inetsoft.report.io.ArabicTextUtil;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.pdf.PDF3Generator;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #80840: a wrapped table cell whose text flows over several pages of its span
 * must not start again from the beginning on the span's pages after the text ended.
 * A short merged label that fits each page piece is still repeated on every page.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TablePaintableSplitCellRepeatTest {
   private static final Font FONT = new Font("Helvetica", Font.PLAIN, 10);
   private static final int WORDS = 40;

   @BeforeEach
   void stubArabicTextUtil() throws Exception {
      // ArabicTextUtilImpl is not on core's test classpath
      Field field = ArabicTextUtil.class.getDeclaredField("instance");
      field.setAccessible(true);
      savedArabic = field.get(null);
      field.set(null, new ArabicTextUtil() {
         @Override
         public boolean containsArabic(String text) {
            return false;
         }

         @Override
         public String createSubstituteString(String text) {
            return text;
         }

         @Override
         public char getMirrorCharacter(char c) {
            return c;
         }
      });
   }

   @AfterEach
   void restoreArabicTextUtil() throws Exception {
      Field field = ArabicTextUtil.class.getDeclaredField("instance");
      field.setAccessible(true);
      field.set(null, savedArabic);
   }

   // one wrapped cell taller than the page: the layout splits its row into a span.
   // the span holds more lines than the text needs (rounded sub-row heights), so its
   // last page used to draw the start of the text again. the slack depends on the
   // font metrics, so a range of column widths and paddings is checked
   @ParameterizedTest(name = "pad={0}")
   @ValueSource(ints = { 0, 1, 2 })
   void splitCellPrintsEveryWordOnce(int pad) throws Exception {
      for(int colW = 50; colW <= 60; colW += 2) {
         List<List<String>> pages = exportSplit(colW, pad);
         assertTrue(pages.size() > 1, "the cell should be split across pages, colW=" + colW);
         assertEquals(words(), drawnWords(pages, null),
                      "pad=" + pad + " colW=" + colW + " drawn lines: " + pages);
      }
   }

   // a real merged cell (lens span) with text longer than a page: the text flows
   // over the pages of the span and must not restart (30 rows) or cycle (60 rows)
   @ParameterizedTest(name = "rows={0}")
   @ValueSource(ints = { 30, 60 })
   void mergedLongTextPrintsEveryWordOnce(int nrows) throws Exception {
      List<List<String>> pages = exportMerged(nrows, String.join(" ", words()), true);
      assertTrue(pages.size() > 1, "the merged cell should cross pages");
      assertEquals(words(), drawnWords(pages, Pattern.compile("w\\d\\dx")),
                   "rows=" + nrows + " drawn lines: " + pages);
   }

   // control: a short merged label is repeated at the top of every page of its span
   @Test
   void shortMergedLabelRepeatedOnEveryPage() throws Exception {
      List<List<String>> pages = exportMerged(40, "GroupA", false);
      assertTrue(pages.size() > 1, "the merged cell should cross pages");

      for(int i = 0; i < pages.size(); i++) {
         long count = pages.get(i).stream().filter(s -> s.trim().equals("GroupA")).count();
         assertEquals(1, count, "page " + (i + 1) + " should show the label once: " + pages);
      }
   }

   private static List<String> words() {
      List<String> words = new ArrayList<>();

      for(int i = 1; i <= WORDS; i++) {
         words.add(String.format("w%02dx", i));
      }

      return words;
   }

   // the words drawn on all pages in order. filter keeps only the matching tokens
   // (the other column of the merged table holds row labels)
   private static List<String> drawnWords(List<List<String>> pages, Pattern filter) {
      List<String> drawn = new ArrayList<>();

      for(List<String> page : pages) {
         for(String line : page) {
            for(String token : line.trim().split("\\s+")) {
               if(!token.isEmpty() && (filter == null || filter.matcher(token).matches())) {
                  drawn.add(token);
               }
            }
         }
      }

      return drawn;
   }

   private static List<List<String>> exportSplit(int colW, int pad) throws Exception {
      TabularSheet report = createReport();
      DefaultTableLens lens = new DefaultTableLens(1, 1);
      lens.setHeaderRowCount(0);
      lens.setObject(0, 0, String.join(" ", words()));
      lens.setLineWrap(0, 0, true);
      lens.setFont(0, 0, FONT);
      report.setCurrentTableWidth(colW / 72.0);
      String id = report.addTable(0, 0, lens);
      TableElementDef table = (TableElementDef) report.getElement(id);
      table.setLayout(ReportSheet.TABLE_EQUAL_WIDTH);
      table.setPadding(new Insets(pad, pad, pad, pad));
      return generate(report);
   }

   // col 0 rows 0..nrows-1 merged into one cell holding text, col 1 holds row labels
   private static List<List<String>> exportMerged(int nrows, String text, boolean wrap)
      throws Exception
   {
      TabularSheet report = createReport();
      DefaultTableLens lens = new DefaultTableLens(nrows, 2);
      lens.setHeaderRowCount(0);

      for(int r = 0; r < nrows; r++) {
         lens.setObject(r, 0, r == 0 ? text : null);
         lens.setObject(r, 1, String.format("r%02d", r));
         lens.setFont(r, 0, FONT);
         lens.setFont(r, 1, FONT);
         lens.setLineWrap(r, 0, wrap);
      }

      lens.setSpan(0, 0, new Dimension(1, nrows));
      report.setCurrentTableWidth((wrap ? 104 : 100) / 72.0);
      String id = report.addTable(0, 0, lens);
      TableElementDef table = (TableElementDef) report.getElement(id);
      table.setLayout(ReportSheet.TABLE_EQUAL_WIDTH);
      return generate(report);
   }

   private static TabularSheet createReport() {
      TabularSheet report = new TabularSheet(LibManagerProvider.getInstance(), Cluster.getInstance());
      report.setPageSize(new Size(3.0, 2.0));
      report.setMargin(new Margin(0.25, 0.25, 0.25, 0.25));
      return report;
   }

   private static List<List<String>> generate(TabularSheet report) throws Exception {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      new PDF3Generator(out).generate(report);
      return pages(out.toByteArray());
   }

   // the drawn text strings (one per Tj/TJ) of each page
   private static List<List<String>> pages(byte[] pdf) {
      String text = new String(pdf, StandardCharsets.ISO_8859_1);
      Matcher stream = Pattern.compile("(?s)stream\r?\n(.*?)endstream").matcher(text);
      Pattern op = Pattern.compile("\\(((?:\\\\.|[^\\\\)])*)\\)\\s*Tj|\\[([^\\]]*)\\]\\s*TJ");
      Pattern literal = Pattern.compile("\\(((?:\\\\.|[^\\\\)])*)\\)");
      List<List<String>> result = new ArrayList<>();

      while(stream.find()) {
         String content = inflate(stream.group(1).getBytes(StandardCharsets.ISO_8859_1));

         if(!content.contains("BT")) {
            continue;
         }

         List<String> strs = new ArrayList<>();
         Matcher m = op.matcher(content);

         while(m.find()) {
            if(m.group(1) != null) {
               strs.add(unescape(m.group(1)));
            }
            else {
               Matcher pm = literal.matcher(m.group(2));
               StringBuilder sb = new StringBuilder();

               while(pm.find()) {
                  sb.append(unescape(pm.group(1)));
               }

               strs.add(sb.toString());
            }
         }

         result.add(strs);
      }

      return result;
   }

   private static String unescape(String s) {
      return s.replace("\\(", "(").replace("\\)", ")").replace("\\\\", "\\");
   }

   private static String inflate(byte[] data) {
      Inflater inflater = new Inflater();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[8192];

      try {
         inflater.setInput(data);

         while(!inflater.finished()) {
            int n = inflater.inflate(buf);

            if(n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
               break;
            }

            out.write(buf, 0, n);
         }

         return out.toString(StandardCharsets.ISO_8859_1);
      }
      catch(Exception ex) {
         return new String(data, StandardCharsets.ISO_8859_1);
      }
      finally {
         inflater.end();
      }
   }

   private Object savedArabic;
}
