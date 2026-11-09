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

   // the column widths where the inner width is in [width("wNNx"), width(" wNNx")),
   // plus one width outside that window as a control
   // blanks = the number of blanks between two words
   @ParameterizedTest(name = "pad={0} colW={1} blanks={2}")
   @CsvSource({
      "0, 26, 1", "0, 27, 1", "0, 28, 1", "0, 29, 1",
      "2, 30, 1", "2, 31, 1", "2, 32, 1",
      "0, 27, 2", "2, 31, 2"
   })
   void everyWordPrintedOnceAcrossPages(int pad, int colW, int blanks) throws Exception {
      String sep = " ".repeat(blanks);
      StringBuilder text = new StringBuilder();
      List<String> expected = new ArrayList<>();

      for(int i = 1; i <= WORDS; i++) {
         String word = String.format("w%02dx", i);
         text.append(i == 1 ? "" : sep).append(word);
         expected.add(word);
      }

      List<List<String>> pages = export(text.toString(), colW, pad);
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

      assertEquals(expected, drawn);
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
