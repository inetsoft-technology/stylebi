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
package inetsoft.report.io.viewsheet.excel;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.awt.*;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link PoiExcelVSExporter#alignBottomTabsTables()} (Bug #75778).
 *
 * <p>The assembly infos are mocked rather than constructed: {@code VSAssemblyInfo}'s
 * constructor reads {@code SreeEnv} properties, which needs a bootstrapped server
 * this module's tests do not have.</p>
 */
class PoiExcelVSExporterTest {
   /**
    * A table in a bottom-tabs container whose top is off the sheet's row grid is
    * snapped down to the grid line, and its layout position is shifted by the
    * same amount so annotation placement stays anchored to the table.
    */
   @Test
   void alignsOffGridBottomTabsTable() {
      TableVSAssemblyInfo info = tableInfo(new Point(10, 250), new Point(30, 505));
      TestExporter exporter = exporterFor(mockTable(info, bottomTabs(true)));

      exporter.alignBottomTabsTables();

      verify(info).setPixelOffset(new Point(10, 240));
      verify(info).setLayoutPosition(new Point(30, 495));
   }

   /**
    * The offset is floored, never rounded: a top just below a grid line moves
    * back to the previous line rather than up to the next one.
    */
   @Test
   void floorsRatherThanRoundsOffGridOffset() {
      TableVSAssemblyInfo info = tableInfo(new Point(10, 259), null);
      TestExporter exporter = exporterFor(mockTable(info, bottomTabs(true)));

      exporter.alignBottomTabsTables();

      verify(info).setPixelOffset(new Point(10, 240));
      verify(info, never()).setLayoutPosition(any());
   }

   /**
    * A table already sitting on a grid line is left completely alone, so
    * grid-aligned exports stay byte-identical.
    */
   @Test
   void leavesGridAlignedBottomTabsTableUnchanged() {
      TableVSAssemblyInfo info =
         tableInfo(new Point(10, 4 * AssetUtil.defh), new Point(30, 505));
      TestExporter exporter = exporterFor(mockTable(info, bottomTabs(true)));

      exporter.alignBottomTabsTables();

      assertNotAdjusted(info);
   }

   /**
    * Top-tab tables keep their exact pixel position — the tab strip is above
    * them, so the grid rounding has room to absorb.
    */
   @Test
   void ignoresTopTabsTable() {
      TableVSAssemblyInfo info = tableInfo(new Point(10, 250), new Point(30, 505));
      TestExporter exporter = exporterFor(mockTable(info, bottomTabs(false)));

      exporter.alignBottomTabsTables();

      assertNotAdjusted(info);
   }

   @Test
   void ignoresTableNotInTab() {
      TableVSAssemblyInfo info = tableInfo(new Point(10, 250), new Point(30, 505));
      TestExporter exporter = exporterFor(mockTable(info, null));

      exporter.alignBottomTabsTables();

      assertNotAdjusted(info);
   }

   /**
    * Only tables are snapped: other assembly types are drawn as pictures at
    * their exact pixel position and must not move.
    */
   @Test
   void ignoresNonTableAssembly() {
      ChartVSAssemblyInfo info = Mockito.mock(ChartVSAssemblyInfo.class);
      when(info.getPixelOffset()).thenReturn(new Point(10, 250));
      TabVSAssembly tab = bottomTabs(true);
      ChartVSAssembly chart = Mockito.mock(ChartVSAssembly.class);
      when(chart.getVSAssemblyInfo()).thenReturn(info);
      when(chart.getContainer()).thenReturn(tab);

      exporterFor(chart).alignBottomTabsTables();

      verify(info, never()).setPixelOffset(any());
      verify(info, never()).setLayoutPosition(any());
   }

   /**
    * Assemblies that are not written to the sheet (tip views, pop components,
    * hidden assemblies) are skipped.
    */
   @Test
   void skipsAssemblyExcludedFromExport() {
      TableVSAssemblyInfo info = tableInfo(new Point(10, 250), new Point(30, 505));
      TestExporter exporter = exporterFor(mockTable(info, bottomTabs(true)));
      exporter.exportAll = false;

      exporter.alignBottomTabsTables();

      assertNotAdjusted(info);
   }

   @Test
   void handlesNullPixelOffset() {
      TableVSAssemblyInfo info = tableInfo(null, new Point(30, 505));
      TestExporter exporter = exporterFor(mockTable(info, bottomTabs(true)));

      assertDoesNotThrow(exporter::alignBottomTabsTables);
      assertNotAdjusted(info);
   }

   @Test
   void handlesNullViewsheet() {
      TestExporter exporter = new TestExporter(null);
      assertDoesNotThrow(exporter::alignBottomTabsTables);
   }

   private static void assertNotAdjusted(VSAssemblyInfo info) {
      verify(info, never()).setPixelOffset(any());
      verify(info, never()).setLayoutPosition(any());
   }

   private static TableVSAssemblyInfo tableInfo(Point pixelOffset, Point layoutPosition) {
      TableVSAssemblyInfo info = Mockito.mock(TableVSAssemblyInfo.class);
      when(info.getPixelOffset()).thenReturn(pixelOffset);
      when(info.getLayoutPosition()).thenReturn(layoutPosition);
      return info;
   }

   private static TableVSAssembly mockTable(TableVSAssemblyInfo info, TabVSAssembly container) {
      TableVSAssembly table = Mockito.mock(TableVSAssembly.class);
      when(table.getVSAssemblyInfo()).thenReturn(info);
      when(table.getContainer()).thenReturn(container);
      return table;
   }

   private static TabVSAssembly bottomTabs(boolean bottom) {
      TabVSAssemblyInfo tabInfo = Mockito.mock(TabVSAssemblyInfo.class);
      when(tabInfo.isBottomTabs()).thenReturn(bottom);
      TabVSAssembly tab = Mockito.mock(TabVSAssembly.class);
      when(tab.getVSAssemblyInfo()).thenReturn(tabInfo);
      return tab;
   }

   private static TestExporter exporterFor(Assembly... assemblies) {
      Viewsheet vs = Mockito.mock(Viewsheet.class);
      when(vs.getAssemblies(true)).thenReturn(assemblies);
      return new TestExporter(vs);
   }

   /**
    * Stubs out {@code needExport()} so the test does not depend on viewsheet
    * visibility state, tip-view/pop-component lookups, or a real workbook.
    */
   private static final class TestExporter extends PoiExcelVSExporter {
      TestExporter(Viewsheet vs) {
         super(Mockito.mock(ExcelContext.class), new ByteArrayOutputStream());
         this.viewsheet = vs;
      }

      @Override
      protected boolean needExport(VSAssembly assembly) {
         return exportAll;
      }

      private boolean exportAll = true;
   }
}
