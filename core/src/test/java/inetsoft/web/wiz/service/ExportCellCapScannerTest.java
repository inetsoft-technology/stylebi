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
import inetsoft.report.composition.RegionTableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.filter.DefaultTableFilter;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.ExportedColumnsTableLens;
import inetsoft.report.lens.TextSizeLimitTableLens;
import org.junit.jupiter.api.Tag;
import inetsoft.test.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bug #79596: an export must say when max.cell.size cut a cell. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
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

   /** Bug #80182: the layers a viewsheet table lens is built from, outermost first. */
   private static TableLens viewsheetLens(int cap, Object[][] data) {
      TableLens worksheetLayer = capped(cap, data);
      TableLens query = new DefaultTableFilter(worksheetLayer);
      return new DefaultTableFilter(new TextSizeLimitTableLens(query, cap));
   }

   @Test
   void viewsheetTableWarnsNamingTheTableTheColumnAndTheCap() {
      TableLens lens = viewsheetLens(500, new Object[][] {
         {"id", "BIG"}, {"1", repeat(5000)}});

      List<String> warnings = ExportCellCapScanner.scanViewsheetTable("TV", lens, 500);

      assertEquals(1, warnings.size());
      assertTrue(warnings.get(0).contains("column(s) BIG of table TV"), warnings.get(0));
      assertTrue(warnings.get(0).contains("max.cell.size is 500"), warnings.get(0));
   }

   @Test
   void viewsheetTableListsAColumnCutAtTwoLayersOnce() {
      TableLens lens = viewsheetLens(500, new Object[][] {
         {"id", "BIG"}, {"1", repeat(5000)}, {"2", repeat(700)}});

      String warning = ExportCellCapScanner.scanViewsheetTable("TV", lens, 500).get(0);

      assertEquals(warning.indexOf("BIG"), warning.lastIndexOf("BIG"), warning);
      assertTrue(!warning.contains("id"), warning);
   }

   /** Bug #80500: header rows are never cut, so a long header must not warn. */
   @Test
   void viewsheetTableLongHeaderOnlyYieldsNoWarning() {
      TableLens lens = viewsheetLens(500, new Object[][] {
         {"id", repeat(5000)}, {"1", "short"}});

      assertTrue(ExportCellCapScanner.scanViewsheetTable("TV", lens, 500).isEmpty());
   }

   /** Bug #80500: a VSTableLens and a region lens over it keep the filter chain. */
   @Test
   void viewsheetTableBehindAVSTableLensAndRegionLensStillWarns() {
      VSTableLens vs = new VSTableLens(viewsheetLens(500, new Object[][] {
         {"id", "BIG"}, {"1", repeat(5000)}}));
      TableLens region = new RegionTableLens(vs, 2, 2);

      assertEquals(1, ExportCellCapScanner.scanViewsheetTable("TV", vs, 500).size());
      assertEquals(1, ExportCellCapScanner.scanViewsheetTable("TV", region, 500).size());
   }

   @Test
   void exportedColumnsLimitTheWarningToWrittenColumns() {
      TableLens lens = viewsheetLens(500, new Object[][] {
         {"id", "BIG", "HID"}, {"1", repeat(5000), repeat(5000)}});

      List<String> warnings = ExportCellCapScanner.scanViewsheetTable("TV",
         new ExportedColumnsTableLens(lens, Set.of("id", "BIG")), 500);

      assertEquals(1, warnings.size());
      assertTrue(warnings.get(0).contains("column(s) BIG of table"), warnings.get(0));
      assertTrue(!warnings.get(0).contains("HID"), warnings.get(0));
      assertTrue(ExportCellCapScanner.scanViewsheetTable("TV",
         new ExportedColumnsTableLens(lens, Set.of("id")), 500).isEmpty());
   }

   @Test
   void exportedHeadersThatMatchNoBaseColumnDoNotHideACut() {
      TableLens lens = viewsheetLens(500, new Object[][] {{"id", "BIG"}, {"1", repeat(5000)}});

      assertEquals(1, ExportCellCapScanner.scanViewsheetTable("TV",
         new ExportedColumnsTableLens(lens, Set.of("Renamed 1", "Renamed 2")), 500).size());
   }

   @Test
   void addDistinctKeepsEachWarningOnce() {
      List<String> target = new ArrayList<>();

      ExportCellCapScanner.addDistinct(target, List.of("a", "b"));
      ExportCellCapScanner.addDistinct(target, List.of("b", "c"));

      assertEquals(List.of("a", "b", "c"), target);
   }

   @Test
   void viewsheetTableWithShortCellsOrNoCapYieldsNoWarning() {
      assertTrue(ExportCellCapScanner.scanViewsheetTable("TV", viewsheetLens(500,
         new Object[][] {{"BIG"}, {repeat(500)}}), 500).isEmpty());
      assertTrue(ExportCellCapScanner.scanViewsheetTable("TV", viewsheetLens(0,
         new Object[][] {{"BIG"}, {repeat(5000)}}), 0).isEmpty());
      assertTrue(ExportCellCapScanner.scanViewsheetTable("TV",
         new DefaultTableLens(new Object[][] {{"BIG"}, {repeat(5000)}}), 500).isEmpty());
      assertTrue(ExportCellCapScanner.scanViewsheetTable("TV", null, 500).isEmpty());
   }
}
