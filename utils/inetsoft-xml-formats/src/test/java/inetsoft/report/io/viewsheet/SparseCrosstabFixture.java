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
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import org.mockito.Mockito;

import java.awt.*;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Shared fixture for the bug #77287 match-layout row-count tests: a sparse crosstab and
 * the equivalent freehand (calc) table with the same design geometry and the same data.
 *
 * <p>The data has {@link Shape#headerRows} header rows and 46 month rows over 14 state
 * columns, and every month row has exactly one blank state cell (QA77237_verify's shape).
 * Every row is 20px and the 20px title is visible. {@code VSTableLens} is mocked because
 * its constructor needs a Spring context that this module's tests do not have.</p>
 */
public final class SparseCrosstabFixture {
   public static final int DATA_ROWS = 46;
   private static final int PIXEL_WIDTH = 400;
   private static final int TITLE_HEIGHT = 20;
   private static final int ROW_HEIGHT = 20;
   private static final int COLS = 15;

   /**
    * Design shapes. The expected row count is header rows plus the data rows that fit in
    * {@code height - title - headers}, i.e. what the freehand table gets.
    */
   public enum Shape {
      /** The reported 400x144 table: 1 header + 5 data rows. */
      REPORTED(144, 1, 6),
      /** A taller design height: 1 header + 13 data rows. */
      TALL(300, 1, 14),
      /** Two column header rows: 2 headers + 4 data rows. */
      TWO_HEADER_ROWS(144, 2, 6);

      Shape(int height, int headerRows, int expectedRows) {
         this.height = height;
         this.headerRows = headerRows;
         this.expectedRows = expectedRows;
      }

      public final int height;
      public final int headerRows;
      public final int expectedRows;
   }

   private SparseCrosstabFixture() {
   }

   /**
    * Header rows followed by 46 data rows. Column 0 is the month label; columns 1-14 are
    * state measures, and each data row leaves exactly one of them blank, so every column
    * is non-blank somewhere and every data row has a blank cell.
    */
   public static TableLens sparseLens(Shape shape) {
      int headerRows = shape.headerRows;
      int rowCount = DATA_ROWS + headerRows;
      Object[][] cells = new Object[rowCount][COLS];

      for(int r = 0; r < headerRows; r++) {
         cells[r][0] = "Month";

         for(int c = 1; c < COLS; c++) {
            cells[r][c] = "H" + r + "S" + c;
         }
      }

      for(int r = headerRows; r < rowCount; r++) {
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

   public static CrosstabVSAssembly crosstab(Shape shape) {
      CrosstabVSAssemblyInfo info = Mockito.mock(CrosstabVSAssemblyInfo.class);
      VSCrosstabInfo crosstabInfo = Mockito.mock(VSCrosstabInfo.class);
      when(crosstabInfo.getRuntimeColHeaders()).thenReturn(new DataRef[shape.headerRows]);

      CrosstabVSAssembly table = Mockito.mock(CrosstabVSAssembly.class);
      when(table.getVSCrosstabInfo()).thenReturn(crosstabInfo);
      initTable(table, info, "Crosstab1", shape);
      return table;
   }

   public static CalcTableVSAssembly calc(Shape shape) {
      CalcTableVSAssemblyInfo info = Mockito.mock(CalcTableVSAssemblyInfo.class);
      when(info.getHeaderRowCount()).thenReturn(shape.headerRows);

      CalcTableVSAssembly table = Mockito.mock(CalcTableVSAssembly.class);
      initTable(table, info, "Crosstab2", shape);
      return table;
   }

   private static void initTable(TableDataVSAssembly table, TableDataVSAssemblyInfo info,
                                 String name, Shape shape)
   {
      Dimension size = new Dimension(PIXEL_WIDTH, shape.height);
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
}
