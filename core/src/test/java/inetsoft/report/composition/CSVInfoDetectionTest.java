/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.report.composition;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How the upload dialog guesses the settings of a delimited file ({@link CSVInfo#getCSVInfo}) and
 * its encoding ({@link CSVInfo#getFileEncode}). The expected values are worked out by hand from the
 * scoring rule of the code: every combination of the quote handling (on, then off) and the
 * delimiter (tab, ';', ',', '|') gets a score from the first {@link CSVInfo#CHECK_ROW} lines, a line
 * with the same number of columns as the first one, more than one column, scores the number of
 * columns plus 100, any other line the number of columns, a line with an unbalanced quote 0; the
 * score is the lowest of the lines, and a combination replaces the best one only when its score is
 * higher, so a tie goes to the combination that comes first.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CSVInfoDetectionTest {
   private static CSVInfo guess(String... lines) {
      return CSVInfo.getCSVInfo(lines);
   }

   // ---- the delimiter ----

   @ParameterizedTest(name = "[{0}]")
   @ValueSource(strings = { "\t", ";", ",", "|" })
   void delimiterWithTheSameNumberOfColumnsInEveryLineIsChosen(String delim) {
      CSVInfo info = guess("id" + delim + "name" + delim + "city",
                           "1" + delim + "ann" + delim + "NY",
                           "2" + delim + "bob" + delim + "LA");

      assertEquals(delim.charAt(0), info.getDelimiter());
      // nothing is quoted: the first of the two (quote handling on) wins the tie
      assertTrue(info.isRemoveQuotationMark());
   }

   // a line with another number of columns only lowers the score, it is not rejected
   @Test
   void delimiterIsStillChosenWhenALineHasFewerColumns() {
      CSVInfo info = guess("a,b,c", "1,2");

      assertEquals(',', info.getDelimiter());
   }

   // a file with one column has no delimiter: every combination scores 1, the first one is kept
   @Test
   void fileWithOneColumnIsReadAsTabSeparated() {
      CSVInfo info = guess("alpha", "beta", "gamma");

      assertEquals('\t', info.getDelimiter());
      assertTrue(info.isRemoveQuotationMark());
   }

   // two delimiters with the same score: the one that comes first in the order tab, ';', ',', '|'
   @Test
   void tieGoesToTheFirstDelimiterInTheOrder() {
      assertEquals(';', guess("a,b;c", "1,2;3").getDelimiter());
      assertEquals(',', guess("a,b|c", "1,2|3").getDelimiter());
      assertEquals('\t', guess("a\tb,c", "1\t2,3").getDelimiter());
   }

   @Test
   void noLinesOrOnlyEmptyLinesGiveNoGuess() {
      assertNull(CSVInfo.getCSVInfo(new String[0]));
      assertNull(guess("", ""));
   }

   // only the first CHECK_ROW (10) lines are looked at: a later line that breaks the columns of
   // one delimiter does not count. The ';' and ',' tie on 10 lines, ';' wins; when the line that
   // breaks the ';' columns is the 10th one it counts and ',' wins.
   @Test
   void onlyTheFirstTenLinesAreChecked() {
      String[] lines = new String[11];

      for(int i = 0; i < 10; i++) {
         lines[i] = "a,b;c";
      }

      lines[10] = "x;y;z,w";
      assertEquals(';', CSVInfo.getCSVInfo(lines).getDelimiter());

      lines[9] = "x;y;z,w";
      assertEquals(',', CSVInfo.getCSVInfo(lines).getDelimiter());
   }

   // ---- quotes ----

   // quotes that do not hold the delimiter are fine with the quote handling on
   @Test
   void quotedFieldsWithoutTheDelimiterKeepTheQuoteHandlingOn() {
      CSVInfo info = guess("\"a\",\"b\"", "\"c\",\"d\"");

      assertEquals(',', info.getDelimiter());
      assertTrue(info.isRemoveQuotationMark());
   }

   // a quoted field that holds the delimiter is one column when the quote handling is on, so the
   // line has 2 columns (score 102) and with it off 3 columns (score 103): when every line has the
   // same number of quoted commas, the guess is the one without quote handling and 3 columns.
   // Pins the current behavior, which may be unintended.
   @Test
   void quotedCommaInEveryLineIsGuessedWithoutQuoteHandling() {
      CSVInfo info = guess("\"a,b\",c", "\"d,e\",f");

      assertEquals(',', info.getDelimiter());
      assertFalse(info.isRemoveQuotationMark());
   }

   // when the lines differ in the number of quoted commas the quote handling wins: with it on
   // every line has 2 columns (102), with it off the lines have 3 and 2 columns (score 3)
   @Test
   void quotedCommaInSomeLinesKeepsTheQuoteHandlingOn() {
      CSVInfo info = guess("\"a,b\",c", "d,e");

      assertEquals(',', info.getDelimiter());
      assertTrue(info.isRemoveQuotationMark());
   }

   // a quote that starts in the middle of a field (not right after the delimiter) does not start a
   // quoted field: with the quote handling on the line scores 0, so the guess is the one without
   // quote handling
   @Test
   void quoteInTheMiddleOfAFieldIsNotAQuotedField() {
      CSVInfo info = guess("a\"b\",c", "d\"e\",f");

      assertEquals(',', info.getDelimiter());
      assertFalse(info.isRemoveQuotationMark());
   }

   // a line with an unbalanced quote scores 0 and the next line replaces a score of 0 (the 0 is
   // not kept), so the line is forgiven unless it is the last one that is checked: then the
   // quote handling is turned off. Pins the current behavior.
   @Test
   void unbalancedQuoteTurnsOffTheQuoteHandlingOnlyInTheLastCheckedLine() {
      assertTrue(guess("a,b", "c,\"d", "e,f").isRemoveQuotationMark());
      assertFalse(guess("a,b", "e,f", "c,\"d").isRemoveQuotationMark());
   }

   // ---- the first row as the header ----

   @Test
   void firstRowOfTextIsTheHeader() {
      assertTrue(guess("name,city", "ann,NY").isFirstRowAsHeader());
   }

   // one cell with only digits is enough: the first row is data
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "1,2,3", "name,2024", "007,x" })
   void firstRowWithACellOfOnlyDigitsIsNotTheHeader(String first) {
      assertFalse(guess(first, "a,b,c").isFirstRowAsHeader());
   }

   // a decimal point or a sign is not a digit, and an empty cell is ignored
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "1.5,2.5", "-1,-2", "1e3,2e3", ",b", " ,b", "$1,$2" })
   void firstRowWithoutACellOfOnlyDigitsIsTheHeader(String first) {
      assertTrue(guess(first, "a,b").isFirstRowAsHeader(), first);
   }

   @Test
   void headerDecisionLooksAtTheFirstLineOnly() {
      assertFalse(guess("1,2", "a,b").isFirstRowAsHeader());
      assertTrue(guess("a,b", "1,2").isFirstRowAsHeader());
   }

   // with the quote handling off the first row is split with String.split, which reads the
   // delimiter as a regular expression: '|' matches the empty string, the row is cut into single
   // characters and a digit in it is a cell of only digits. The same rows with ',' have a header.
   // Pins the current behavior, reported as Bug #81220.
   @Test
   void firstRowOfAPipeFileWithoutQuoteHandlingIsCutIntoCharacters() {
      CSVInfo pipe = guess("\"a|b\"|c1", "\"d|e\"|f2");
      CSVInfo comma = guess("\"a,b\",c1", "\"d,e\",f2");

      assertEquals('|', pipe.getDelimiter());
      assertFalse(pipe.isRemoveQuotationMark());
      assertFalse(pipe.isFirstRowAsHeader());
      assertEquals(',', comma.getDelimiter());
      assertFalse(comma.isRemoveQuotationMark());
      assertTrue(comma.isFirstRowAsHeader());
   }

   // ---- the encoding of a file ----

   @TempDir
   Path tempDir;

   private File write(String name, byte[] content) throws Exception {
      return Files.write(tempDir.resolve(name), content).toFile();
   }

   private static final String CHINESE =
      "这是一个用来检测文件编码的中文测试文件，里面有很多常用的汉字，比如：客户名称、订单数量、产品类别、销售日期。".repeat(8);

   @Test
   void utf8TextIsUtf8() throws Exception {
      File file = write("utf8.csv", CHINESE.getBytes(StandardCharsets.UTF_8));

      assertEquals("UTF-8", CSVInfo.getFileEncode(file));
   }

   // GB2312 and GB18030 are reported as GBK
   @Test
   void chineseTextInGbkIsGbk() throws Exception {
      File file = write("gbk.csv", CHINESE.getBytes(Charset.forName("GBK")));

      assertEquals("GBK", CSVInfo.getFileEncode(file));
   }

   // UTF-16 in either byte order is reported as "Unicode"
   @Test
   void utf16TextIsUnicode() throws Exception {
      byte[] le = ("﻿" + CHINESE).getBytes(StandardCharsets.UTF_16LE);
      byte[] be = ("﻿" + CHINESE).getBytes(StandardCharsets.UTF_16BE);

      assertEquals("Unicode", CSVInfo.getFileEncode(write("le.csv", le)));
      assertEquals("Unicode", CSVInfo.getFileEncode(write("be.csv", be)));
   }

   // plain ASCII has no encoding to detect: the encoding of the system is used
   @Test
   void asciiTextUsesTheSystemEncoding() throws Exception {
      File file = write("ascii.csv", "a,b\n1,2\n".getBytes(StandardCharsets.US_ASCII));

      assertEquals(CSVInfo.getSystemEncode(), CSVInfo.getFileEncode(file));
   }
}
