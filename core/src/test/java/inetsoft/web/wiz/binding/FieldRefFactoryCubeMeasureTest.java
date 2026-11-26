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

package inetsoft.web.wiz.binding;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.model.BAggregateRefModel;
import inetsoft.web.binding.model.ChartBindingModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A chart or crosstab bound to a worksheet cube table: a numeric measure written without an
 * aggregate gets the formula a native cube drop gives it, None, not the Sum a numeric column of
 * an ordinary table gets. An explicit aggregate is left as asked.
 */
@Tag("core")
class FieldRefFactoryCubeMeasureTest {
   private static final String TABLE = "OLAP_Sales";
   private static final SourceInfo SOURCE = new SourceInfo(SourceInfo.ASSET, null, TABLE);

   @Test
   void aCubeMeasureWithNoAggregateDefaultsToNone() {
      BAggregateRefModel ref = new BAggregateRefModel();

      FieldRefFactory.applyAggregateDefaults(ref, model(), rvs(CubeTableAssembly.class),
                                             SOURCE, "Unit Sales");

      assertEquals("None", ref.getFormula());
   }

   @Test
   void positiveControl_aNumericColumnOfAnOrdinaryTableStillDefaultsToSum() {
      BAggregateRefModel ref = new BAggregateRefModel();

      FieldRefFactory.applyAggregateDefaults(ref, model(), rvs(TableAssembly.class),
                                             SOURCE, "Unit Sales");

      assertEquals("Sum", ref.getFormula());
   }

   @Test
   void anExplicitAggregateOnACubeMeasureIsNotOverridden() {
      BAggregateRefModel ref = new BAggregateRefModel();
      ref.setFormula("Max");

      FieldRefFactory.applyAggregateDefaults(ref, model(), rvs(CubeTableAssembly.class),
                                             SOURCE, "Unit Sales");

      assertEquals("Max", ref.getFormula());
   }

   @Test
   void aDimensionLevelOfACubeTableIsNotAMeasure() {
      assertFalse(FieldRefFactory.isCubeMeasure(model(), rvs(CubeTableAssembly.class), SOURCE,
                                                "Time.Year"));
      assertTrue(FieldRefFactory.isCubeMeasure(model(), rvs(CubeTableAssembly.class), SOURCE,
                                               "Unit Sales"));
   }

   @Test
   void aColumnThatIsNotInTheTableIsNotAMeasure() {
      assertFalse(FieldRefFactory.isCubeMeasure(model(), rvs(CubeTableAssembly.class), SOURCE,
                                                "Nope"));
   }

   @Test
   void withoutAWorksheetOrAnAssembly_nothingChanges() {
      RuntimeViewsheet noVs = mock(RuntimeViewsheet.class);
      RuntimeViewsheet noWs = mock(RuntimeViewsheet.class);
      when(noWs.getViewsheet()).thenReturn(mock(Viewsheet.class));

      assertFalse(FieldRefFactory.isCubeMeasure(model(), null, SOURCE, "Unit Sales"));
      assertFalse(FieldRefFactory.isCubeMeasure(model(), noVs, SOURCE, "Unit Sales"));
      assertFalse(FieldRefFactory.isCubeMeasure(model(), noWs, SOURCE, "Unit Sales"));
      assertFalse(FieldRefFactory.isCubeMeasure(model(), rvs(CubeTableAssembly.class), null,
                                                "Unit Sales"));
   }

   // ---- helpers ----------------------------------------------------------------------------

   private static ChartBindingModel model() {
      ChartBindingModel model = new ChartBindingModel();
      inetsoft.web.binding.model.BindingModel.SourceTable table =
         new inetsoft.web.binding.model.BindingModel.SourceTable();
      table.setName(TABLE);
      table.setColumns(java.util.List.of(
         new inetsoft.web.binding.model.BindingModel.SourceTableColumn("Time.Year", "string"),
         new inetsoft.web.binding.model.BindingModel.SourceTableColumn("Unit Sales", "double")));
      model.setTables(java.util.List.of(table));

      return model;
   }

   /** A viewsheet whose base worksheet holds one table of the given kind, with the cube columns. */
   private static <T extends TableAssembly> RuntimeViewsheet rvs(Class<T> kind) {
      ColumnSelection columns = new ColumnSelection();
      AttributeRef year = new AttributeRef("Time", "Year");
      year.setRefType(DataRef.CUBE_DIMENSION);
      columns.addAttribute(new inetsoft.uql.asset.ColumnRef(year));
      AttributeRef units = new AttributeRef(null, "Unit Sales");
      units.setRefType(DataRef.CUBE_MEASURE);
      columns.addAttribute(new inetsoft.uql.asset.ColumnRef(units));

      T table = mock(kind);
      when(table.getColumnSelection(true)).thenReturn(columns);
      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssembly(TABLE)).thenReturn(table);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getBaseWorksheet()).thenReturn(ws);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      return rvs;
   }
}
