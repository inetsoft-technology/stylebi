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
package inetsoft.report.io.viewsheet;

import inetsoft.report.TableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.filter.DefaultTableFilter;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.TextSizeLimitTableLens;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.wiz.service.ExportCellCapScanner;
import org.junit.jupiter.api.Tag;
import inetsoft.test.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80500: Excel, HTML, PDF and PowerPoint exports cut text cells to max.cell.size without
 * telling the caller. The exporter shows each table's lens to an observer before the region lens
 * is built.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AbstractVSExporterTableObserverTest {
   private static VSTableLens lens(int[] widths, Object[][] data) {
      TableLens worksheetLayer = new TextSizeLimitTableLens(new DefaultTableLens(data), 500);
      TableLens sandboxLayer =
         new TextSizeLimitTableLens(new DefaultTableFilter(worksheetLayer), 500);
      VSTableLens lens = new VSTableLens(sandboxLayer);
      lens.setColumnWidths(widths);
      return lens;
   }

   private static TableVSAssembly table() {
      TableVSAssembly table = mock(TableVSAssembly.class);
      when(table.getAbsoluteName()).thenReturn("TV");
      when(table.getVSAssemblyInfo()).thenReturn(mock(TableVSAssemblyInfo.class));
      return table;
   }

   private static AbstractVSExporter exporter(List<String> warnings) {
      AbstractVSExporter exporter = mock(AbstractVSExporter.class, CALLS_REAL_METHODS);
      exporter.setTableObserver((name, lens) -> ExportCellCapScanner.addDistinct(
         warnings, ExportCellCapScanner.scanViewsheetTable(name, lens, 500)));
      return exporter;
   }

   @Test
   void aCutColumnIsReportedForAPlainTable() {
      List<String> warnings = new ArrayList<>();

      exporter(warnings).observeTable(table(), lens(new int[] {100, 100}, new Object[][] {
         {"id", "BIG"}, {"1", "x".repeat(5000)}}));

      assertEquals(1, warnings.size());
      assertTrue(warnings.get(0).contains("column(s) BIG of table TV"), warnings.get(0));
   }

   @Test
   void aHiddenColumnIsNotReported() {
      List<String> warnings = new ArrayList<>();

      exporter(warnings).observeTable(table(), lens(new int[] {100, 0}, new Object[][] {
         {"id", "BIG"}, {"1", "x".repeat(5000)}}));

      assertTrue(warnings.isEmpty(), warnings.toString());
   }

   @Test
   void aLongHeaderAndShortCellsAreNotReported() {
      List<String> warnings = new ArrayList<>();

      exporter(warnings).observeTable(table(), lens(new int[] {100, 100}, new Object[][] {
         {"id", "x".repeat(5000)}, {"1", "short"}}));

      assertTrue(warnings.isEmpty(), warnings.toString());
   }

   @Test
   void aCrosstabLensIsShownUnfiltered() {
      List<TableLens> seen = new ArrayList<>();
      AbstractVSExporter exporter = mock(AbstractVSExporter.class, CALLS_REAL_METHODS);
      exporter.setTableObserver((name, lens) -> seen.add(lens));
      CrosstabVSAssembly crosstab = mock(CrosstabVSAssembly.class);
      when(crosstab.getAbsoluteName()).thenReturn("CT");
      VSTableLens lens = lens(new int[] {100}, new Object[][] {{"BIG"}, {"x".repeat(5000)}});

      exporter.observeTable(crosstab, lens);

      assertEquals(1, seen.size());
      assertSame(lens, seen.get(0));
   }

   @Test
   void noObserverOrAFailingObserverNeverBreaksTheExport() {
      AbstractVSExporter none = mock(AbstractVSExporter.class, CALLS_REAL_METHODS);
      VSTableLens lens = lens(new int[] {100}, new Object[][] {{"BIG"}, {"x"}});

      assertDoesNotThrow(() -> none.observeTable(table(), lens));

      AbstractVSExporter failing = mock(AbstractVSExporter.class, CALLS_REAL_METHODS);
      failing.setTableObserver((name, l) -> {
         throw new IllegalStateException("boom");
      });

      assertDoesNotThrow(() -> failing.observeTable(table(), lens));
   }
}
