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

import inetsoft.report.composition.VSTableLens;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.awt.*;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Bug #78632: the space an expanded table reserves has to be the space the writer draws.
 * {@code ExcelTableHelper.initExcelRowCount} spans a row with {@code ceil(paddedHeight / defh)}
 * and stops writing rows once the reserved space is used up.
 *
 * <p>The assembly infos are mocked rather than constructed: {@code VSAssemblyInfo}'s constructor
 * reads {@code SreeEnv} properties, which needs a bootstrapped server this module's tests do
 * not have.</p>
 */
class ExcelExpandRowSpanTest {
   @Test
   void aShortRowIsOneRow() {
      assertEquals(3 * ROW, expandHeight(16, 0, 3, 0));
   }

   @Test
   void aRowOfOneAndAHalfExcelRowsIsTwoRows() {
      assertEquals(3 * 2 * ROW, expandHeight(30, 0, 3, 0));
   }

   /** 25px is drawn over two 20px Excel rows, so two have to be reserved. */
   @Test
   void a25pxRowReservesBothRowsTheWriterDraws() {
      assertEquals(3 * 2 * ROW, expandHeight(25, 0, 3, 0));
   }

   @Test
   void a45pxRowReservesThreeRows() {
      assertEquals(3 * 3 * ROW, expandHeight(45, 0, 3, 0));
   }

   /** 16 stored + 12 of stylesheet padding = 28, which the writer spans as two Excel rows. */
   @Test
   void stylesheetPaddingIsReservedToo() {
      assertEquals(3 * 2 * ROW, expandHeight(16, 12, 3, 0));
   }

   /** A 30px title is anchored two Excel rows above the body, so two have to be reserved. */
   @Test
   void a30pxTitleReservesTwoRows() {
      assertEquals(2 * ROW + ROW, expandHeight(16, 0, 1, 30));
   }

   @Test
   void a20pxTitleReservesOneRow() {
      assertEquals(ROW + ROW, expandHeight(16, 0, 1, 20));
   }

   /** The offline exporter writes the same sheet, so it reserves the same space. */
   @Test
   void theOfflineExporterReservesBothRowsOfA25pxRow() {
      assertEquals(3 * 2 * ROW, expandHeight(25, 0, 3, 0, true));
   }

   @Test
   void theOfflineExporterReservesStylesheetPadding() {
      assertEquals(3 * 2 * ROW, expandHeight(16, 12, 3, 0, true));
   }

   private static int expandHeight(int storedRowHeight, int padding, int rowCount,
                                   int titleHeight)
   {
      return expandHeight(storedRowHeight, padding, rowCount, titleHeight, false);
   }

   private static int expandHeight(int storedRowHeight, int padding, int rowCount,
                                   int titleHeight, boolean offline)
   {
      TableVSAssemblyInfo info = Mockito.mock(TableVSAssemblyInfo.class);
      when(info.isTitleVisible()).thenReturn(titleHeight > 0);
      when(info.getTitleHeight()).thenReturn(titleHeight);
      when(info.getPixelSize()).thenReturn(new Dimension(400, 240));

      VSTableLens lens = Mockito.mock(VSTableLens.class);
      when(lens.getRowCount()).thenReturn(rowCount);
      when(lens.getRowHeights()).thenReturn(new int[rowCount]);
      when(lens.getWrappedHeight(anyInt(), anyBoolean())).thenReturn(storedRowHeight);
      when(lens.getRowHeightWithPadding(anyDouble(), anyInt()))
         .thenAnswer(inv -> (double) inv.getArgument(0, Double.class) + padding);

      TableVSAssembly table = Mockito.mock(TableVSAssembly.class);
      when(table.getVSAssemblyInfo()).thenReturn(info);
      when(table.getInfo()).thenReturn(info);
      when(table.getPixelOffset()).thenReturn(new Point(0, 0));

      ExcelContext context = Mockito.mock(ExcelContext.class);
      PoiExcelVSExporter exporter = offline ?
         new OfflineExcelVSExporter(context, new ByteArrayOutputStream()) :
         new PoiExcelVSExporter(context, new ByteArrayOutputStream());
      exporter.setMatchLayout(false);

      return exporter.getExpandTableHeight(table, lens);
   }

   private static final int ROW = AssetUtil.defh;
}
