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
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #80859: a wrapped table cell whose lines after a newline start with a blank was
 * measured with the blank kept, while the layout drops it. When the line only fits
 * without the blank, every such line was counted twice, the row became too tall and
 * the table took an extra page that only held the header.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TableElementDefLeadingBlankHeightTest {
   private static final Font FONT = new Font("Monospaced", Font.PLAIN, 10);
   private static final String BLANKS = "word01\n word02\n word03\n word04\n word05";
   private static final String PLAIN = "word01\nword02\nword03\nword04\nword05";
   private static final List<String> WORDS =
      List.of("word01", "word02", "word03", "word04", "word05");

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

   // the reporter's case: header + one row on a 8.5x2.5in page. " word02" does not
   // fit the column but "word02" does, so the row needs 5 lines and fits one page
   @ParameterizedTest(name = "colW={0}")
   @ValueSource(ints = { 40, 44 })
   void leadingBlankLinesFitOnePage(int colW) throws Exception {
      List<List<String>> pages = export(BLANKS, colW);

      assertEquals(1, pages.size(), "colW=" + colW + " drawn lines: " + pages);
      assertEquals(WORDS, drawnWords(pages), "colW=" + colW + " drawn lines: " + pages);
   }

   // control: the same lines without the blanks
   @ParameterizedTest(name = "colW={0}")
   @ValueSource(ints = { 40, 44 })
   void plainLinesFitOnePage(int colW) throws Exception {
      List<List<String>> pages = export(PLAIN, colW);

      assertEquals(1, pages.size(), "colW=" + colW + " drawn lines: " + pages);
      assertEquals(WORDS, drawnWords(pages), "colW=" + colW + " drawn lines: " + pages);
   }

   // the measured height of the blank lines is the height of the same lines
   // without the blanks, as the layout paints them
   @ParameterizedTest(name = "textW={0}")
   @ValueSource(ints = { 37, 40, 41 })
   void leadingBlankOfHardLineIsNotMeasured(int textW) throws Exception {
      TableElementDef table = createTable();

      assertEquals(height(table, PLAIN, textW, true), height(table, BLANKS, textW, true));
      assertEquals(height(table, "word01\nword02", textW, true),
                   height(table, "word01\n   word02", textW, true));
   }

   // a blank-only line wider than the cell is still one (empty) line
   @Test
   void blankOnlyLineIsOneLine() throws Exception {
      TableElementDef table = createTable();
      String blanks = " ".repeat(25);

      assertEquals(height(table, "word01\n\nword03", 40, true),
                   height(table, "word01\n" + blanks + "\nword03", 40, true));
   }

   // the layout keeps the blank of the first line and of lines that are not wrapped,
   // so their measured height does not change
   @Test
   void firstLineAndNonWrapUnchanged() throws Exception {
      TableElementDef table = createTable();

      // " word01" does not fit 40 and is broken in two lines by the layout
      assertEquals(height(table, "word01\nword01", 40, true),
                   height(table, " word01", 40, true));
      assertEquals(height(table, "word01\nword02", 40, false),
                   height(table, "word01\n word02", 40, false));
      // only ' ' is dropped by the layout, an ideographic space is kept and
      // "　word02" does not fit 40
      assertEquals(height(table, "word01\nword02\nword02", 40, true),
                   height(table, "word01\n　word02", 40, true));
   }

   private static float height(TableElementDef table, String text, int textW, boolean wrap)
      throws Exception
   {
      Method method = TableElementDef.class.getDeclaredMethod(
         "calcPreferredHeight", Font.class, Object.class, float.class, boolean.class,
         Insets.class);
      method.setAccessible(true);
      return (Float) method.invoke(table, FONT, text, (float) textW, wrap, null);
   }

   private static TableElementDef createTable() {
      TabularSheet report = createReport();
      String id = report.addTable(0, 0, new DefaultTableLens(1, 1));
      return (TableElementDef) report.getElement(id);
   }

   private static List<String> drawnWords(List<List<String>> pages) {
      List<String> drawn = new ArrayList<>();

      for(List<String> page : pages) {
         for(String line : page) {
            for(String token : line.trim().split("\\s+")) {
               if(token.matches("word\\d\\d")) {
                  drawn.add(token);
               }
            }
         }
      }

      return drawn;
   }

   private static List<List<String>> export(String text, int colW) throws Exception {
      TabularSheet report = createReport();
      DefaultTableLens lens = new DefaultTableLens(2, 2);
      lens.setHeaderRowCount(1);
      lens.setObject(0, 0, "H1");
      lens.setObject(0, 1, "H2");
      lens.setObject(1, 0, text);
      lens.setObject(1, 1, "x");

      for(int r = 0; r < 2; r++) {
         for(int c = 0; c < 2; c++) {
            lens.setFont(r, c, FONT);
            lens.setLineWrap(r, c, true);
         }
      }

      String id = report.addTable(0, 0, lens);
      TableElementDef table = (TableElementDef) report.getElement(id);
      table.setLayout(ReportSheet.TABLE_FIT_CONTENT);
      table.setFixedWidths(new int[] { colW, 100 });
      return generate(report);
   }

   private static TabularSheet createReport() {
      TabularSheet report = new TabularSheet(LibManagerProvider.getInstance(), Cluster.getInstance());
      report.setPageSize(new Size(8.5, 2.5));
      report.setMargin(new Margin(0.5, 0.5, 0.5, 0.5));
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
