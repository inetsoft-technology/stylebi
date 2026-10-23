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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.uql.viewsheet.internal.VizModernizeUtil;
import inetsoft.web.composer.vs.objects.event.ImmutableResizeTableCellEvent;
import inetsoft.web.composer.vs.objects.event.ImmutableResizeTableRowEvent;
import inetsoft.web.composer.vs.objects.event.ResizeTableColumnEvent;
import inetsoft.web.viewsheet.controller.table.BaseTableService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Set Cell Size's follow-the-default-density value, as the composer sends it for a marked table.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ComposerVSTableServiceRowHeightFollowTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aTickedOkLeavesAFollowingRowAlone() throws Exception {
      TableDataVSAssembly table = marked(dataTable(), "compact");
      TableDataVSAssemblyInfo info = table.getTableDataVSAssemblyInfo();
      VSTableLens lens = initLens(table);

      resize(table, lens, dataCell(16), true);

      assertFalse(info.isUserDataRowHeight());
      assertEquals(AssetUtil.defh, info.getDataRowHeight());
      // the column half of OK still runs
      assertEquals(120, BaseTableService.getColWidths(table, lens)[0]);
   }

   @Test
   void aTickedOkHandsAPinnedDataRowBack() throws Exception {
      TableDataVSAssembly table = marked(dataTable(), "compact");
      TableDataVSAssemblyInfo info = table.getTableDataVSAssemblyInfo();
      info.setUserDataRowHeight(true);
      info.setDataRowHeight(30);

      resize(table, initLens(table), dataCell(30), true);

      assertFalse(info.isUserDataRowHeight());
      assertEquals(AssetUtil.defh, info.getDataRowHeight());
   }

   // the wizard's table sync copies a data height without its flag
   @Test
   void aTickedOkHandsBackADataHeightStoredWithoutTheFlag() throws Exception {
      TableDataVSAssembly table = marked(dataTable(), "compact");
      TableDataVSAssemblyInfo info = table.getTableDataVSAssemblyInfo();
      info.setDataRowHeight(30);
      assertTrue(info.isDataRowHeightPinned(), "precondition: a stored height pins the rows");

      resize(table, initLens(table), dataCell(30), true);

      assertFalse(info.isDataRowHeightPinned());
      assertEquals(AssetUtil.defh, info.getDataRowHeight());
   }

   @Test
   void aTickedOkHandsPinnedHeaderRowsBack() throws Exception {
      TableDataVSAssembly table = marked(headerTable(), "compact");
      TableDataVSAssemblyInfo info = table.getTableDataVSAssemblyInfo();
      pinHeaders(info, 30, 30);

      resize(table, initLens(table), headerCell(0, 30), true);

      assertFalse(info.isUserHeaderRowHeight());
      assertEquals(AssetUtil.defh, info.getHeaderRowHeight(0));
      assertEquals(AssetUtil.defh, info.getHeaderRowHeight(1));
   }

   @Test
   void aTickedOkOnADataCellLeavesHeaderRowsAlone() throws Exception {
      TableDataVSAssembly table = marked(headerTable(), "compact");
      TableDataVSAssemblyInfo info = table.getTableDataVSAssemblyInfo();
      pinHeaders(info, 30, 30);

      resize(table, initLens(table), cell(2, false, 16), true);

      assertTrue(info.isUserHeaderRowHeight());
      assertEquals(30, info.getHeaderRowHeight(0));
      assertEquals(30, info.getHeaderRowHeight(1));
   }

   @Test
   void anUntickedOkPinsTheShownHeight() throws Exception {
      TableDataVSAssembly table = marked(dataTable(), "compact");
      TableDataVSAssemblyInfo info = table.getTableDataVSAssemblyInfo();

      resize(table, initLens(table), dataCell(16), false);

      assertTrue(info.isUserDataRowHeight());
      assertEquals(16, info.getDataRowHeight());
   }

   // the shared header flag would otherwise drop the unclicked row to defh plus its padding
   @ParameterizedTest
   @CsvSource({ "dense,16", "compact,18", "comfortable,18" })
   void anUntickedOkOnOneHeaderRowKeepsTheOthers(String mode, int tier) throws Exception {
      TableDataVSAssembly table = marked(headerTable(), mode);
      TableDataVSAssemblyInfo info = table.getTableDataVSAssemblyInfo();

      resize(table, initLens(table), headerCell(0, 25), false);

      assertTrue(info.isUserHeaderRowHeight());
      assertEquals(25, info.getHeaderRowHeight(0));
      assertEquals(tier, info.getHeaderRowHeight(1));
   }

   @Test
   void anUntickedOkOnAPinnedHeaderWritesOnlyTheClickedRow() throws Exception {
      TableDataVSAssembly table = marked(headerTable(), "compact");
      TableDataVSAssemblyInfo info = table.getTableDataVSAssemblyInfo();
      pinHeaders(info, 30, 40);

      resize(table, initLens(table), headerCell(0, 25), false);

      assertEquals(25, info.getHeaderRowHeight(0));
      assertEquals(40, info.getHeaderRowHeight(1));
   }

   // the lens rows date from the last render; the pin must be what render draws now
   @Test
   void anUntickedOkPinsTheOtherHeaderRowsAtTheDensityInForce() throws Exception {
      TableDataVSAssembly table = marked(headerTable(), "dense");
      TableDataVSAssemblyInfo info = table.getTableDataVSAssemblyInfo();
      VSTableLens lens = initLens(table);
      SreeEnv.setProperty("viewsheet.density", "compact");

      resize(table, lens, headerCell(0, 25), false);

      // compact's 26 less the 3 + 3 padding seeded at dense, which a density change leaves alone
      assertEquals(20, info.getHeaderRowHeight(1));
   }

   @Test
   void anUntickedOkWithNoLensRowsFallsBackToTheTier() throws Exception {
      TableDataVSAssembly table = marked(headerTable(), "compact");
      TableDataVSAssemblyInfo info = table.getTableDataVSAssemblyInfo();

      resize(table, lens(table), headerCell(0, 25), false);

      assertEquals(18, info.getHeaderRowHeight(1));
   }

   @Test
   void aMissingValueKeepsTodaysPin() throws Exception {
      TableDataVSAssembly table = marked(dataTable(), "compact");
      TableDataVSAssemblyInfo info = table.getTableDataVSAssemblyInfo();

      resize(table, initLens(table), dataCell(16), null);

      assertTrue(info.isUserDataRowHeight());
      assertEquals(16, info.getDataRowHeight());
   }

   @Test
   void anUnmarkedTableIgnoresTheValue() throws Exception {
      SreeEnv.setProperty("viewsheet.density", "compact");
      TableDataVSAssembly table = dataTable();
      TableDataVSAssemblyInfo info = table.getTableDataVSAssemblyInfo();
      // a new assembly takes its host sheet's mark, which the org gate may have stamped
      info.setVizMark(null);

      resize(table, initLens(table), dataCell(16), true);

      assertTrue(info.isUserDataRowHeight());
      assertEquals(16, info.getDataRowHeight());
   }

   private static TableDataVSAssembly dataTable() {
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      vs.addAssembly(table);
      sized(table.getTableDataVSAssemblyInfo());
      return table;
   }

   private static TableDataVSAssembly headerTable() {
      Viewsheet vs = new Viewsheet();
      CalcTableVSAssembly table = new CalcTableVSAssembly(vs, "Table1");
      vs.addAssembly(table);
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) table.getVSAssemblyInfo();
      info.setHeaderRowCount(2);
      sized(info);
      return table;
   }

   private static void sized(TableDataVSAssemblyInfo info) {
      info.setPixelOffset(new Point(0, 0));
      info.setPixelSize(new Dimension(400, 200));

      for(int i = 0; i < 2; i++) {
         info.setColumnWidthValue(i, 100);
      }
   }

   private static TableDataVSAssembly marked(TableDataVSAssembly table, String mode) {
      SreeEnv.setProperty("viewsheet.density", mode);
      TableDataVSAssemblyInfo info = table.getTableDataVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      VizModernizeUtil.reseedAfterRestore(info);
      return table;
   }

   private static void pinHeaders(TableDataVSAssemblyInfo info, int... heights) {
      info.setUserHeaderRowHeight(true);
      info.setHeaderRowHeights(heights);
   }

   private static VSTableLens lens(TableDataVSAssembly table) {
      DefaultTableLens base = new DefaultTableLens(new Object[][] {
         { "H1", "H1" },
         { "H2", "H2" },
         { 1, 2 }
      });
      base.setHeaderRowCount(table instanceof CalcTableVSAssembly ? 2 : 1);
      return new VSTableLens(base);
   }

   private static VSTableLens initLens(TableDataVSAssembly table) {
      VSTableLens lens = lens(table);
      lens.initTableGrid(table.getVSAssemblyInfo());
      return lens;
   }

   private static ImmutableResizeTableRowEvent dataCell(int height) {
      return cell(1, false, height);
   }

   private static ImmutableResizeTableRowEvent headerCell(int row, int height) {
      return cell(row, true, height);
   }

   private static ImmutableResizeTableRowEvent cell(int row, boolean header, int height) {
      return ImmutableResizeTableRowEvent.builder()
         .assemblyName("Table1")
         .start(0)
         .rowCount(3)
         .row(row)
         .rowHeight(height)
         .rowSpan(1)
         .header(header)
         .build();
   }

   private static void resize(TableDataVSAssembly table, VSTableLens lens,
                              ImmutableResizeTableRowEvent rowEvent, Boolean followsDensity)
      throws Exception
   {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getVSTableLens("Table1", false)).thenReturn(lens);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(table.getViewsheet());
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(any(), any())).thenReturn(rvs);

      ComposerVSTableService service = new ComposerVSTableService(
         mock(CoreLifecycleService.class), null, null, null, null, viewsheetService, null, null);

      ResizeTableColumnEvent colEvent = new ResizeTableColumnEvent();
      colEvent.setName("Table1");
      colEvent.setRow(rowEvent.row());
      colEvent.setStartCol(0);
      colEvent.setEndCol(1);
      colEvent.setWidths(new double[] { 120 });

      ImmutableResizeTableCellEvent event = ImmutableResizeTableCellEvent.builder()
         .colEvent(colEvent)
         .rowEvent(rowEvent)
         .followsDensity(followsDensity)
         .build();
      service.resizeTableCell("vs1", event, null, null, null);
   }
}
