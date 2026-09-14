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
package inetsoft.web.binding.handler;

import inetsoft.report.TableDataPath;
import inetsoft.report.composition.VSTableLens;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.FormatInfo;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.VSFormat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Structural coverage only — nothing here renders a real crosstab/table, which is the one thing
 * a {@code MESSAGE_FORMAT}/{@code TableDataPath} write can only be proven against live (see
 * VTB-011's design doc S5/S6): whether the header actually changes on a rendered image is the
 * manual {@code get_viewsheet_image} check, not a JUnit fixture.
 */
@Tag("core")
class SetTableHeaderAliasHandlerTest {
   private static TableDataPath headerPath(String... path) {
      return new TableDataPath(-1, TableDataPath.HEADER, XSchema.STRING, path);
   }

   @Test
   void writesAMessageFormatOverrideAtThePath() {
      FormatInfo formatInfo = new FormatInfo();
      TableDataPath path = headerPath("REGION");

      SetTableHeaderAliasHandler.setAlias(path, formatInfo, "Sales Region");

      VSCompositeFormat format = formatInfo.getFormat(path);
      assertNotNull(format);
      assertEquals(VSFormat.MESSAGE_FORMAT, format.getUserDefinedFormat().getFormatValue());
      assertEquals("Sales Region", format.getUserDefinedFormat().getFormatExtentValue());
   }

   @Test
   void preservesAnExistingFormatsOtherAttributesWhenSettingTheAlias() {
      FormatInfo formatInfo = new FormatInfo();
      TableDataPath path = headerPath("REGION");
      VSCompositeFormat existing = new VSCompositeFormat();
      existing.getUserDefinedFormat().setBackgroundValue("16711680");
      formatInfo.setFormat(path, existing);

      SetTableHeaderAliasHandler.setAlias(path, formatInfo, "Sales Region");

      VSFormat written = formatInfo.getFormat(path).getUserDefinedFormat();
      assertEquals("16711680", written.getBackgroundValue());
      assertEquals("Sales Region", written.getFormatExtentValue());
   }

   /**
    * Mirrors {@code ComposerVSTableService.changeColumnTitle}'s own defensive dual write: a
    * crosstab summary header cell's path type flips between {@code HEADER} and
    * {@code GROUP_HEADER} depending on whether a group dimension is bound, and the two are never
    * live simultaneously, so writing both is cheap insurance against a pivot changing which one
    * renders.
    */
   @Test
   void writesBothHeaderAndGroupHeaderWhenEitherIsGiven() {
      FormatInfo formatInfo = new FormatInfo();
      TableDataPath headerPath = headerPath("Region");
      headerPath.setType(TableDataPath.HEADER);

      SetTableHeaderAliasHandler.setAliasWithHeaderDuality(headerPath, formatInfo, "Sales Region");

      TableDataPath groupHeaderPath = (TableDataPath) headerPath.clone();
      groupHeaderPath.setType(TableDataPath.GROUP_HEADER);

      assertEquals("Sales Region",
         formatInfo.getFormat(headerPath).getUserDefinedFormat().getFormatExtentValue());
      assertEquals("Sales Region",
         formatInfo.getFormat(groupHeaderPath).getUserDefinedFormat().getFormatExtentValue());
   }

   @Test
   void findsTheHeaderPathMatchingADimensionsShelfPosition() {
      VSTableLens lens = mock(VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getHeaderColCount()).thenReturn(2);

      TableDataPath col0 = headerPath("Cell [0,0]");
      TableDataPath col1 = headerPath("Cell [0,1]");
      when(lens.getTableDataPath(0, 0)).thenReturn(col0);
      when(lens.getTableDataPath(0, 1)).thenReturn(col1);

      VSDimensionRef dimension = new VSDimensionRef();

      assertSame(col1, SetTableHeaderAliasHandler.findHeaderPath(lens, dimension, 1));
      assertSame(col0, SetTableHeaderAliasHandler.findHeaderPath(lens, dimension, 0));
   }

   @Test
   void returnsNullWhenNoHeaderCellMatches() {
      VSTableLens lens = mock(VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getHeaderColCount()).thenReturn(1);
      when(lens.getTableDataPath(0, 0)).thenReturn(headerPath("Cell [0,0]"));

      VSDimensionRef dimension = new VSDimensionRef();

      assertNull(SetTableHeaderAliasHandler.findHeaderPath(lens, dimension, 5));
   }
}
