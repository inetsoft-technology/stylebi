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
import org.junit.jupiter.params.provider.CsvSource;
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
 * Bug #80731: a wrapped table cell that is split across pages (a span cell) must
 * print every word exactly once, in order, when the remainder on a page starts at
 * a blank and the next word fits the column but " " + word does not.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TablePaintableSpanWrapTest {
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

   // the bug shows when the inner width is in [width("wNNx"), width(" wNNx")). the font
   // metrics differ per platform, so the column widths are derived at runtime.
   // step = points added to the narrowest column width that fits every word: step 0 and
   // 1 are inside the window, step -1 is a control above it (" " + word fits).
   // blanks = the number of blanks between two words
   @ParameterizedTest(name = "pad={0} step={1} blanks={2}")
   @CsvSource({
      "0, 0, 1", "0, 1, 1", "0, -1, 1",
      "2, 0, 1", "2, 1, 1",
      "0, 0, 2", "2, 1, 2"
   })
   void everyWordPrintedOnceAcrossPages(int pad, int step, int blanks) throws Exception {
      List<String> expected = new ArrayList<>();

      for(int i = 1; i <= WORDS; i++) {
         expected.add(String.format("w%02dx", i));
      }

      FontMetrics fm = Common.getFractionalFontMetrics(FONT);
      float maxWord = 0;
      float minBlankWord = Float.MAX_VALUE;

      for(String word : expected) {
         maxWord = Math.max(maxWord, Common.stringWidth(word, FONT, fm));
         minBlankWord = Math.min(minBlankWord, Common.stringWidth(" " + word, FONT, fm));
      }

      float gap = minBlankWord - maxWord;
      // the window [width(word), width(" " + word)) must hold two integer widths
      assertTrue(gap > 2, "blank too narrow for the test: width(word)=" + maxWord +
         " width(' ' + word)=" + minBlankWord);

      int fitW = narrowestFittingWidth(String.join(" ", expected), pad, (int) maxWord);
      // fitW - overhead >= maxWord and fitW - 1 - overhead < maxWord, so fitW + 1 is
      // still below width(" " + word) as the gap is > 2, and fitW + ceil(gap) is at
      // or above it
      int colW = step >= 0 ? fitW + step : fitW + (int) Math.ceil(gap);
      String text = String.join(" ".repeat(blanks), expected);
      List<List<String>> pages = export(text, colW, pad);
      assertTrue(pages.size() > 1, "the cell should be split across pages");

      List<String> drawn = new ArrayList<>();

      for(List<String> page : pages) {
         for(String line : page) {
            String trimmed = line.trim();

            if(trimmed.isEmpty()) {
               continue;
            }

            // a merged line holding several words is clipped by the cell
            assertTrue(!trimmed.contains(" "), "line holds more than one word: '" + line +
               "', drawn lines: " + pages);
            drawn.add(trimmed);
         }
      }

      assertEquals(expected, drawn, "colW=" + colW + " drawn lines: " + pages);
   }

   // the narrowest integer column width (pt) whose first page shows every word whole.
   // the first page is a fresh layout of the text, so it does not depend on the fix
   private static int narrowestFittingWidth(String text, int pad, int from) throws Exception {
      for(int colW = from; colW < from + 2 * pad + 20; colW++) {
         List<List<String>> pages = export(text, colW, pad);

         if(!pages.isEmpty() && !pages.get(0).isEmpty() &&
            pages.get(0).stream().map(String::trim)
               .allMatch(s -> s.isEmpty() || s.matches("w\\d\\dx")))
         {
            return colW;
         }
      }

      throw new AssertionError("no column width fits a word, pad=" + pad);
   }

   // the drawn text strings (one per Tj/TJ) of each page
   private static List<List<String>> export(String text, int colW, int pad) throws Exception {
      TabularSheet report = new TabularSheet(LibManagerProvider.getInstance(), Cluster.getInstance());
      report.setPageSize(new Size(3.0, 2.0));
      report.setMargin(new Margin(0.25, 0.25, 0.25, 0.25));
      DefaultTableLens lens = new DefaultTableLens(1, 1);
      lens.setHeaderRowCount(0);
      lens.setObject(0, 0, text);
      lens.setLineWrap(0, 0, true);
      lens.setFont(0, 0, FONT);
      report.setCurrentTableWidth(colW / 72.0);
      String id = report.addTable(0, 0, lens);
      TableElementDef table = (TableElementDef) report.getElement(id);
      table.setLayout(ReportSheet.TABLE_EQUAL_WIDTH);
      table.setPadding(new Insets(pad, pad, pad, pad));

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      new PDF3Generator(out).generate(report);
      return pages(out.toByteArray());
   }

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
