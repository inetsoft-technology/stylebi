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
 * Bug #82446: a logical-model measure bound with no aggregate takes the formula the model
 * attribute is defined with, as the Composer does, instead of the data type's Sum.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetModelMeasureAggregateDefaultTest {
   private static final String TABLE = "Sales";
   private static final SourceInfo SOURCE = new SourceInfo(SourceInfo.ASSET, null, TABLE);

   @Test
   void aMeasureTakesTheFormulaTheModelDefinesIt() {
      assertEquals("Average", defaultFormula("Avg Amt"));
      assertEquals("DistinctCount", defaultFormula("Distinct Cust"));
      assertEquals("Max", defaultFormula("Max Amt"));
   }

   @Test
   void aModelFormulaOfNoneIsKeptAsNone() {
      assertEquals("None", defaultFormula("Raw Amt"));
   }

   @Test
   void aMeasureWithNoModelFormulaStaysSum() {
      assertEquals("Sum", defaultFormula("Plain Amt"));
   }

   @Test
   void aFormulaTheBareColumnCannotExpressFallsBackToTheDataTypeDefault() {
      assertEquals("Sum", defaultFormula("Correlated Amt"));
      assertEquals("Sum", defaultFormula("Unknown Amt"));
   }

   @Test
   void aDimensionKeepsCountWhateverFormulaItCarries() {
      assertEquals("Count", defaultFormula("Category"));
   }

   @Test
   void anExplicitAggregateBeatsTheModelFormula() {
      BAggregateRefModel ref = new BAggregateRefModel();
      ref.setFormula("Sum");

      FieldRefFactory.applyAggregateDefaults(ref, model(), rvs(), SOURCE, "Avg Amt");

      assertEquals("Sum", ref.getFormula());
   }

   private static String defaultFormula(String column) {
      BAggregateRefModel ref = new BAggregateRefModel();
      FieldRefFactory.applyAggregateDefaults(ref, model(), rvs(), SOURCE, column);
      return ref.getFormula();
   }

   private static ChartBindingModel model() {
      ChartBindingModel model = new ChartBindingModel();
      BindingModel.SourceTable table = new BindingModel.SourceTable();
      table.setName(TABLE);
      table.setColumns(java.util.List.of(
         new BindingModel.SourceTableColumn("Category", "string"),
         new BindingModel.SourceTableColumn("Avg Amt", "double"),
         new BindingModel.SourceTableColumn("Distinct Cust", "integer"),
         new BindingModel.SourceTableColumn("Max Amt", "double"),
         new BindingModel.SourceTableColumn("Raw Amt", "double"),
         new BindingModel.SourceTableColumn("Plain Amt", "double"),
         new BindingModel.SourceTableColumn("Correlated Amt", "double"),
         new BindingModel.SourceTableColumn("Unknown Amt", "double")));
      model.setTables(java.util.List.of(table));
      return model;
   }

   private static void add(ColumnSelection columns, String name, int refType, String formula,
                           String dataType)
   {
      AttributeRef attr = new AttributeRef(null, name);
      attr.setRefType(refType);
      attr.setDefaultFormula(formula);
      ColumnRef column = new ColumnRef(attr);
      column.setDataType(dataType);
      columns.addAttribute(column);
   }

   private static RuntimeViewsheet rvs() {
      ColumnSelection columns = new ColumnSelection();
      add(columns, "Category", DataRef.DIMENSION, "Sum", "string");
      add(columns, "Avg Amt", DataRef.MEASURE, "Average", "double");
      add(columns, "Distinct Cust", DataRef.MEASURE, "DistinctCount", "integer");
      add(columns, "Max Amt", DataRef.MEASURE, "Max", "double");
      add(columns, "Raw Amt", DataRef.MEASURE, "None", "double");
      add(columns, "Plain Amt", DataRef.MEASURE, null, "double");
      add(columns, "Correlated Amt", DataRef.MEASURE, "Correlation", "double");
      add(columns, "Unknown Amt", DataRef.MEASURE, "NoSuchFormula", "double");

      Worksheet ws = new Worksheet();
      QueryBoundTableAssembly table = new QueryBoundTableAssembly(ws, TABLE);
      table.setColumnSelection(columns);
      ws.addAssembly(table);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getBaseWorksheet()).thenReturn(ws);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }
}
