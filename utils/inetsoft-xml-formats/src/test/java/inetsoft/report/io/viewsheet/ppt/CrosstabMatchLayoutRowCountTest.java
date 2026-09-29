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
package inetsoft.report.io.viewsheet.ppt;

import inetsoft.report.TableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.io.viewsheet.excel.ExcelContext;
import inetsoft.report.io.viewsheet.excel.PoiExcelVSExporter;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Bug #77287: in a match-layout Excel or PowerPoint export, a crosstab whose data rows
 * all contain a blank (sparse) measure cell must be clamped to its design height exactly
 * like the equivalent freehand table. The bug #53192 blank-row exemption in
 * {@code AbstractVSExporter.getRegionRowCount()} used to skip every such row from the
 * height budget, so the crosstab region lens was not clamped at all, and Excel and
 * PowerPoint (whose writers do not clip at the design pixel height) wrote one more data
 * row than the viewer shows.
 *
 * <p>The fixture is QA77237_verify's shape: 400x144 with a visible 20px title, 1 header
 * row, 20px rows, and 46 month rows over 14 state columns where every month row has one
 * blank state cell. Both tables fit 1 header + 5 data rows.</p>
 *
 * <p>This class lives in the {@code ppt} package because {@code PPTContext} is package
 * private. {@code VSTableLens} is mocked because its constructor needs a Spring context
 * that this module's tests do not have.</p>
 */
class CrosstabMatchLayoutRowCountTest {
   private static final int PIXEL_WIDTH = 400;
   private static final int PIXEL_HEIGHT = 144;
   private static final int TITLE_HEIGHT = 20;
   private static final int ROW_HEIGHT = 20;
   private static final int DATA_ROWS = 46;
   private static final int COLS = 15;
   private static final int EXPECTED_ROWS = 6; // 1 header + 5 data rows

   @Test
   void excelClampsSparseCrosstabLikeFreehand() {
      TableLens data = sparseLens();

      int crosstab = new TestExcelExporter().regionRowCount(crosstab(), data);
      int calc = new TestExcelExporter().regionRowCount(calc(), data);

      assertEquals(EXPECTED_ROWS, calc, "freehand table fits 1 header + 5 data rows");
      assertEquals(calc, crosstab,
         "Excel must clamp the sparse crosstab to the same row count as the freehand table");
   }

   @Test
   void powerPointClampsSparseCrosstabLikeFreehand() {
      TableLens data = sparseLens();

      int crosstab = new TestPPTExporter().regionRowCount(crosstab(), data);
      int calc = new TestPPTExporter().regionRowCount(calc(), data);

      assertEquals(EXPECTED_ROWS, calc, "freehand table fits 1 header + 5 data rows");
      assertEquals(calc, crosstab,
         "PowerPoint must clamp the sparse crosstab to the same row count as the freehand table");
   }

   /**
    * 1 header row + 46 data rows. Column 0 is the month label; columns 1-14 are state
    * measures, and each data row leaves exactly one of them blank, so every column is
    * non-blank somewhere and every data row has a blank cell.
    */
   private static TableLens sparseLens() {
      int rowCount = DATA_ROWS + 1;
      Object[][] cells = new Object[rowCount][COLS];
      cells[0][0] = "Month";

      for(int c = 1; c < COLS; c++) {
         cells[0][c] = "S" + c;
      }

      for(int r = 1; r < rowCount; r++) {
         cells[r][0] = "M" + r;
         int blank = 1 + r % (COLS - 1);

         for(int c = 1; c < COLS; c++) {
            cells[r][c] = c == blank ? null : r * 100 + c;
         }
      }

      int[] rowHeights = new int[rowCount];
      Arrays.fill(rowHeights, ROW_HEIGHT);

      VSTableLens lens = Mockito.mock(VSTableLens.class);
      when(lens.getRowCount()).thenReturn(rowCount);
      when(lens.getColCount()).thenReturn(COLS);
      when(lens.getObject(anyInt(), anyInt()))
         .thenAnswer(inv -> cells[(int) inv.getArgument(0)][(int) inv.getArgument(1)]);
      when(lens.getLineCount(anyInt())).thenReturn(1);
      when(lens.getRowHeights()).thenReturn(rowHeights);
      return lens;
   }

   private static CrosstabVSAssembly crosstab() {
      CrosstabVSAssemblyInfo info = Mockito.mock(CrosstabVSAssemblyInfo.class);
      VSCrosstabInfo crosstabInfo = Mockito.mock(VSCrosstabInfo.class);
      when(crosstabInfo.getRuntimeColHeaders()).thenReturn(new DataRef[1]);

      CrosstabVSAssembly table = Mockito.mock(CrosstabVSAssembly.class);
      when(table.getVSCrosstabInfo()).thenReturn(crosstabInfo);
      initTable(table, info, "Crosstab1");
      return table;
   }

   private static CalcTableVSAssembly calc() {
      CalcTableVSAssemblyInfo info = Mockito.mock(CalcTableVSAssemblyInfo.class);
      when(info.getHeaderRowCount()).thenReturn(1);

      CalcTableVSAssembly table = Mockito.mock(CalcTableVSAssembly.class);
      initTable(table, info, "Crosstab2");
      return table;
   }

   private static void initTable(TableDataVSAssembly table, TableDataVSAssemblyInfo info,
                                 String name)
   {
      Dimension size = new Dimension(PIXEL_WIDTH, PIXEL_HEIGHT);
      when(info.isTitleVisible()).thenReturn(true);
      when(info.getTitleHeight()).thenReturn(TITLE_HEIGHT);
      when(info.getPixelSize()).thenReturn(size);

      Viewsheet vs = Mockito.mock(Viewsheet.class);
      when(vs.getDisplayRowHeight(Mockito.eq(true), anyString(), anyInt())).thenReturn(ROW_HEIGHT);
      when(vs.getDisplayRowHeight(Mockito.eq(false), anyString())).thenReturn(ROW_HEIGHT);

      when(table.getInfo()).thenReturn(info);
      when(table.getVSAssemblyInfo()).thenReturn(info);
      when(table.getPixelSize()).thenReturn(size);
      when(table.getPixelOffset()).thenReturn(new Point(0, 218));
      when(table.getName()).thenReturn(name);
      when(table.getViewsheet()).thenReturn(vs);
   }

   private static final class TestExcelExporter extends PoiExcelVSExporter {
      TestExcelExporter() {
         super(Mockito.mock(ExcelContext.class), new ByteArrayOutputStream());
         setMatchLayout(true);
      }

      int regionRowCount(TableDataVSAssembly table, TableLens data) {
         return getRegionRowCount(table, data);
      }
   }

   private static final class TestPPTExporter extends PPTVSExporter {
      TestPPTExporter() {
         super(Mockito.mock(PPTContext.class), new ByteArrayOutputStream());
         setMatchLayout(true);
      }

      int regionRowCount(TableDataVSAssembly table, TableLens data) {
         return getRegionRowCount(table, data);
      }
   }
}
