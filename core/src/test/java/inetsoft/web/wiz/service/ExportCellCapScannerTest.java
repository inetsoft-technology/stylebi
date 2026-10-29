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

package inetsoft.web.wiz.service;

import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.TextSizeLimitTableLens;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bug #79596: an export must say when max.cell.size cut a cell. */
@Tag("core")
class ExportCellCapScannerTest {
   private static TableLens capped(int cap, Object[][] data) {
      return new TextSizeLimitTableLens(new DefaultTableLens(data), cap);
   }

   private static String repeat(int n) {
      return "x".repeat(n);
   }

   @Test
   void warnsAboutACellOverTheCapAndNamesTheColumnAndTheLimit() {
      TableLens lens = capped(500, new Object[][] {
         {"id", "body"}, {"1", repeat(600)}, {"2", "short"}});

      List<String> warnings = ExportCellCapScanner.scan(lens, 500);

      assertEquals(1, warnings.size());
      assertTrue(warnings.get(0).startsWith("1 cell(s) in column(s) body"), warnings.get(0));
      assertTrue(warnings.get(0).contains("max.cell.size is 500"), warnings.get(0));
   }

   @Test
   void aCellExactlyAtTheCapIsNotCut() {
      TableLens lens = capped(500, new Object[][] {{"body"}, {repeat(500)}});

      assertTrue(ExportCellCapScanner.scan(lens, 500).isEmpty());
   }

   @Test
   void aCellOneOverTheCapIsReported() {
      TableLens lens = capped(500, new Object[][] {{"body"}, {repeat(501)}});

      assertEquals(1, ExportCellCapScanner.scan(lens, 500).size());
   }

   @Test
   void aCapOfZeroOrLessDisablesTheScan() {
      TableLens lens = capped(0, new Object[][] {{"body"}, {repeat(5000)}});

      assertTrue(ExportCellCapScanner.scan(lens, 0).isEmpty());
      assertTrue(ExportCellCapScanner.scan(lens, -1).isEmpty());
   }

   @Test
   void aHeaderOverTheCapIsExemptAndNonStringCellsAreIgnored() {
      TableLens lens = capped(500, new Object[][] {
         {repeat(600)}, {Integer.valueOf(7)}, {null}});

      assertTrue(ExportCellCapScanner.scan(lens, 500).isEmpty());
   }

   @Test
   void countsCellsAcrossRowsAndColumns() {
      TableLens lens = capped(10, new Object[][] {
         {"a", "b"}, {repeat(11), repeat(12)}, {repeat(13), "ok"}});

      String warning = ExportCellCapScanner.scan(lens, 10).get(0);

      assertTrue(warning.startsWith("3 cell(s) in column(s) a, b"), warning);
   }

   @Test
   void aLensThatIsNotCappedYieldsNoWarning() {
      TableLens lens = new DefaultTableLens(new Object[][] {{"body"}, {repeat(900)}});

      assertTrue(ExportCellCapScanner.scan(lens, 500).isEmpty());
   }

   @Test
   void headerValueIsAnAsciiUrlEncodedJsonArray() {
      String value = ExportCellCapScanner.toHeaderValue(List.of("cut to 500 é"));

      assertTrue(value.chars().allMatch(c -> c < 128), value);
      assertEquals("[\"cut to 500 é\"]", URLDecoder.decode(value, StandardCharsets.UTF_8));
      assertNull(ExportCellCapScanner.toHeaderValue(List.of()));
   }

   @Test
   void anOversizedHeaderIsReplacedByAShortWarning() {
      String value = ExportCellCapScanner.toHeaderValue(List.of(repeat(20000)));

      assertTrue(value.length() < 4000, "length " + value.length());
      assertTrue(URLDecoder.decode(value, StandardCharsets.UTF_8).contains("max.cell.size"));
   }
}
