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
import inetsoft.uql.viewsheet.VSAggregateRef;
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
      when(lens.getColCount()).thenReturn(2);
      when(lens.getRowCount()).thenReturn(1);

      TableDataPath col0 = headerPath("Cell [0,0]");
      TableDataPath col1 = headerPath("Cell [0,1]");
      when(lens.getTableDataPath(0, 0)).thenReturn(col0);
      when(lens.getTableDataPath(0, 1)).thenReturn(col1);

      VSDimensionRef dimension = new VSDimensionRef();

      assertSame(col1, SetTableHeaderAliasHandler.findHeaderPath(lens, dimension, 1));
      assertSame(col0, SetTableHeaderAliasHandler.findHeaderPath(lens, dimension, 0));
   }

   /**
    * {@code findHeaderCell} is {@code findHeaderPath} plus the {@code col} index a caller needs
    * to reach {@code CrosstabVSAssemblyInfo.addHiddenColumn}/{@code isColumnHidden} (bug #76869) --
    * same scan, same match, but the coordinate {@code scanHeaderRegion} finds internally is no
    * longer discarded.
    */
   @Test
   void findHeaderCellAlsoReturnsTheMatchedColumnIndex() {
      VSTableLens lens = mock(VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getHeaderColCount()).thenReturn(2);
      when(lens.getColCount()).thenReturn(2);
      when(lens.getRowCount()).thenReturn(1);

      TableDataPath col0 = headerPath("Cell [0,0]");
      TableDataPath col1 = headerPath("Cell [0,1]");
      when(lens.getTableDataPath(0, 0)).thenReturn(col0);
      when(lens.getTableDataPath(0, 1)).thenReturn(col1);

      VSDimensionRef dimension = new VSDimensionRef();

      SetTableHeaderAliasHandler.HeaderCell cell1 =
         SetTableHeaderAliasHandler.findHeaderCell(lens, dimension, 1);
      assertNotNull(cell1);
      assertSame(col1, cell1.path());
      assertEquals(0, cell1.row());
      assertEquals(1, cell1.col());

      SetTableHeaderAliasHandler.HeaderCell cell0 =
         SetTableHeaderAliasHandler.findHeaderCell(lens, dimension, 0);
      assertNotNull(cell0);
      assertSame(col0, cell0.path());
      assertEquals(0, cell0.row());
      assertEquals(0, cell0.col());
   }

   @Test
   void findHeaderCellReturnsNullWhenNoHeaderCellMatches() {
      VSTableLens lens = mock(VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getHeaderColCount()).thenReturn(1);
      when(lens.getColCount()).thenReturn(1);
      when(lens.getRowCount()).thenReturn(1);
      when(lens.getTableDataPath(0, 0)).thenReturn(headerPath("Cell [0,0]"));

      VSDimensionRef dimension = new VSDimensionRef();

      assertNull(SetTableHeaderAliasHandler.findHeaderCell(lens, dimension, 5));
   }

   @Test
   void returnsNullWhenNoHeaderCellMatches() {
      VSTableLens lens = mock(VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getHeaderColCount()).thenReturn(1);
      when(lens.getColCount()).thenReturn(1);
      when(lens.getRowCount()).thenReturn(1);
      when(lens.getTableDataPath(0, 0)).thenReturn(headerPath("Cell [0,0]"));

      VSDimensionRef dimension = new VSDimensionRef();

      assertNull(SetTableHeaderAliasHandler.findHeaderPath(lens, dimension, 5));
   }

   /**
    * Regression test for VTB-011 fix round 1, defect 1: a crosstab with a row dimension but no
    * column dimension lays its (side-by-side) aggregate header out in the same single header
    * *row* as the dimension header, but past the header *column* rectangle ({@code
    * headerColCount}) -- e.g. dimension at (0,0), aggregate at (0,1) when {@code
    * headerRowCount=1, headerColCount=1}. The pre-fix scan bound (col {@code <
    * getHeaderColCount()}) never reached column 1, so the aggregate's real, correctly-typed
    * {@code GROUP_HEADER} cell (segment {@code "Sum(PAID)"}, matching {@code
    * VSAggregateRef.getFullName()}) was never tested against {@code matchAgg} at all --
    * live-reproduced as "Could not find 'aggregates[0]'..." even though the column-identity
    * resolution itself had already succeeded.
    */
   @Test
   void findsAnAggregatesHeaderPathWhenItRendersPastTheHeaderColumnRectangle() {
      VSTableLens lens = mock(VSTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getHeaderColCount()).thenReturn(1);
      when(lens.getColCount()).thenReturn(2);
      when(lens.getRowCount()).thenReturn(3);

      TableDataPath dimHeader = headerPath("Cell [0,0]");
      TableDataPath aggHeader = headerPath("Sum(PAID)");
      aggHeader.setType(TableDataPath.GROUP_HEADER);
      when(lens.getTableDataPath(0, 0)).thenReturn(dimHeader);
      when(lens.getTableDataPath(0, 1)).thenReturn(aggHeader);

      // A mock, not a real VSAggregateRef -- constructing a real one touches AggregateFormula's
      // static initializer, which needs a Spring/Catalog context this plain unit test doesn't
      // have (see TableBindingMutator's own MULTI_ARG_FORMULA_NAMES comment for the same
      // constraint). Only getFullName() is needed for matchAgg's comparison.
      VSAggregateRef aggregate = mock(VSAggregateRef.class);
      when(aggregate.getFullName()).thenReturn("Sum(PAID)");

      assertSame(aggHeader, SetTableHeaderAliasHandler.findHeaderPath(lens, aggregate, 0));
   }
}
