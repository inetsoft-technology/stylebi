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
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.model.BAggregateRefModel;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.ChartBindingModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82387: a measure of a worksheet cube table bound to a chart or crosstab with no
 * aggregate gets the same default as the Composer UI gives it. Built from real assemblies, not
 * mocks: {@link CubeTableAssembly} rewrites its public column selection for a worksheet cube
 * (removes the entity, caption and ref type "so to process as normal table"), and the UI data tree
 * and the wiz binding layer both read that public selection, so the measure is an ordinary
 * numeric column there and defaults to Sum (None only applies to a native cube source).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetCubeTableAggregateDefaultTest {
   private static final String TABLE = "UnitSalesByTime";
   private static final SourceInfo SOURCE = new SourceInfo(SourceInfo.ASSET, null, TABLE);

   @Test
   void thePublicSelectionOfAWorksheetCubeTableCarriesNoCubeRefType() {
      CubeTableAssembly table = cubeTable();

      DataRef measureInPrivate = table.getColumnSelection(false).getAttribute("Unit Sales");
      DataRef measureInPublic = table.getColumnSelection(true).getAttribute("Unit Sales");

      assertEquals(DataRef.CUBE_MEASURE, measureInPrivate.getRefType() & DataRef.CUBE_MEASURE);
      assertEquals(0, measureInPublic.getRefType() & DataRef.CUBE_MEASURE);
   }

   @Test
   void aMeasureOfAWorksheetCubeTableWithNoAggregateDefaultsToSum() {
      assertEquals("Sum", defaultFormula(cubeTable(), "Unit Sales"));
   }

   @Test
   void aNumericColumnOfAnOrdinaryTableDefaultsToSumToo() {
      assertEquals("Sum", defaultFormula(ordinaryTable(), "Unit Sales"));
   }

   @Test
   void aDimensionLevelOfAWorksheetCubeTableIsNotGivenAMeasureDefault() {
      assertEquals("Count", defaultFormula(cubeTable(), "Time.Year"));
   }

   @Test
   void anExplicitAggregateIsKept() {
      BAggregateRefModel ref = new BAggregateRefModel();
      ref.setFormula("None");

      FieldRefFactory.applyAggregateDefaults(ref, model(), rvs(cubeTable()), SOURCE, "Unit Sales");

      assertEquals("None", ref.getFormula());
   }

   private static String defaultFormula(TableAssembly table, String column) {
      BAggregateRefModel ref = new BAggregateRefModel();
      FieldRefFactory.applyAggregateDefaults(ref, model(), rvs(table), SOURCE, column);
      return ref.getFormula();
   }

   private static ChartBindingModel model() {
      ChartBindingModel model = new ChartBindingModel();
      BindingModel.SourceTable table = new BindingModel.SourceTable();
      table.setName(TABLE);
      table.setColumns(java.util.List.of(
         new BindingModel.SourceTableColumn("Time.Year", "string"),
         new BindingModel.SourceTableColumn("Unit Sales", "double")));
      model.setTables(java.util.List.of(table));
      return model;
   }

   private static ColumnSelection cubeColumns() {
      ColumnSelection columns = new ColumnSelection();
      AttributeRef year = new AttributeRef("Time", "Year");
      year.setRefType(DataRef.CUBE_DIMENSION);
      ColumnRef yearColumn = new ColumnRef(year);
      yearColumn.setDataType("string");
      columns.addAttribute(yearColumn);
      AttributeRef units = new AttributeRef(null, "Unit Sales");
      units.setRefType(DataRef.CUBE_MEASURE);
      ColumnRef unitsColumn = new ColumnRef(units);
      unitsColumn.setDataType("double");
      columns.addAttribute(unitsColumn);
      return columns;
   }

   private static CubeTableAssembly cubeTable() {
      CubeTableAssembly table = new CubeTableAssembly(new Worksheet(), TABLE);
      table.setColumnSelection(cubeColumns());
      return table;
   }

   private static TableAssembly ordinaryTable() {
      QueryBoundTableAssembly table = new QueryBoundTableAssembly(new Worksheet(), TABLE);
      table.setColumnSelection(cubeColumns());
      return table;
   }

   private static RuntimeViewsheet rvs(TableAssembly table) {
      Worksheet ws = table.getWorksheet();
      ws.addAssembly(table);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getBaseWorksheet()).thenReturn(ws);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }
}
