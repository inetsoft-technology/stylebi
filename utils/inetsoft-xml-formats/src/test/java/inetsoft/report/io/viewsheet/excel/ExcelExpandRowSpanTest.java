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
 * The space an expanded table reserves has to be the space the writer draws.
 * {@code ExcelTableHelper.initExcelRowCount} spans a row with {@code ceil(paddedHeight / defh)};
 * a reader that rounds the unpadded height under-reserves and the assemblies below overlap it.
 *
 * <p>The assembly infos are mocked rather than constructed: {@code VSAssemblyInfo}'s constructor
 * reads {@code SreeEnv} properties, which needs a bootstrapped server this module's tests do
 * not have.</p>
 */
class ExcelExpandRowSpanTest {
   /** 16 stored + 12 of cell inset = 28, which the writer spans as two 20px Excel rows. */
   @Test
   void aPaddedRowReservesBothRowsTheWriterDraws() {
      assertEquals(3 * 2 * AssetUtil.defh, expandHeight(16, 12, 3, 0));
   }

   /** An unpadded row keeps the historical rounding, so legacy exports do not move. */
   @Test
   void anUnpaddedRowKeepsItsHistoricalRounding() {
      assertEquals(3 * AssetUtil.defh, expandHeight(25, 0, 3, 0));
   }

   @Test
   void aShortUnpaddedRowIsOneRow() {
      assertEquals(3 * AssetUtil.defh, expandHeight(16, 0, 3, 0));
   }

   /** A 30px title merges two Excel rows, so two have to be reserved for it. */
   @Test
   void aDensityTitleReservesTwoRows() {
      assertEquals(2 * AssetUtil.defh + AssetUtil.defh, expandHeight(16, 0, 1, 30));
   }

   /** The legacy 20px title is one row, as it has always been. */
   @Test
   void aLegacyTitleReservesOneRow() {
      assertEquals(AssetUtil.defh + AssetUtil.defh, expandHeight(16, 0, 1, 20));
   }

   /** The offline exporter reserves the same space; it writes the same sheet. */
   @Test
   void theOfflineExporterAgreesOnAPaddedRow() {
      assertEquals(3 * 2 * AssetUtil.defh, expandHeight(16, 12, 3, 0, true));
   }

   @Test
   void theOfflineExporterAgreesOnAnUnpaddedRow() {
      assertEquals(3 * AssetUtil.defh, expandHeight(25, 0, 3, 0, true));
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

      VSTableLens lens = Mockito.mock(VSTableLens.class);
      when(lens.getRowCount()).thenReturn(rowCount);
      when(lens.getRowHeights()).thenReturn(new int[rowCount]);
      when(lens.getWrappedHeight(anyInt(), anyBoolean())).thenReturn(storedRowHeight);
      when(lens.getRowHeightWithPadding(anyDouble(), anyInt(), any()))
         .thenReturn((double) (storedRowHeight + padding));

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
}
