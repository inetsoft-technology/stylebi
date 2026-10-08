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
package inetsoft.uql.viewsheet;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78087: a variable aggregate column value of the form "Agg(col)" must resolve
 * whatever the design formula is, for crosstab and chart.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSAggregateRefVariableFormulaTest {
   private static final String TOTAL = "Product:Total";
   private static final String QUANTITY = "Product:Quantity";

   @Test
   void crosstabSumFormulaResolvesAggOfColumnValue() {
      VSCrosstabInfo info = new VSCrosstabInfo();
      info.setDesignAggregates(new DataRef[] {
         variableRef(new VSAggregateRef(), "Sum", "Average(" + TOTAL + ")") });
      info.update(null, columns(), null, false, null, null);

      DataRef[] rt = info.getRuntimeAggregates();
      assertEquals(1, rt.length);
      VSAggregateRef aref = (VSAggregateRef) rt[0];
      assertNotNull(aref.getDataRef(), "variable value should resolve to a column");
      assertEquals(TOTAL, aref.getDataRef().getName());
      assertEquals(AggregateFormula.AVG, aref.getFormula());
      assertTrue(aref.isAggregateEnabled());
   }

   @Test
   void sumFormulaWithSumOfColumnValueResolves() {
      VSAggregateRef ref = variableRef(new VSAggregateRef(), "Sum", "Sum(" + TOTAL + ")");
      List<DataRef> refs = ref.update(null, columns());

      assertEquals(1, refs.size());
      VSAggregateRef aref = (VSAggregateRef) refs.get(0);
      assertEquals(TOTAL, aref.getDataRef().getName());
      assertEquals(AggregateFormula.SUM, aref.getFormula());
      assertEquals("Sum(" + TOTAL + ")", aref.getFullName());
   }

   @Test
   void noneFormulaParsedValueReportsAggregateEnabled() {
      VSAggregateRef ref = variableRef(new VSAggregateRef(), "None", "Sum(" + TOTAL + ")");
      List<DataRef> refs = ref.update(null, columns());

      VSAggregateRef aref = (VSAggregateRef) refs.get(0);
      assertEquals(TOTAL, aref.getDataRef().getName());
      assertEquals(AggregateFormula.SUM, aref.getFormula());
      assertTrue(aref.isAggregateEnabled(), "cached none formula must follow the parsed formula");
      assertEquals("Sum(" + TOTAL + ")", aref.getFullName());
      // the design ref is untouched
      assertFalse(ref.isAggregateEnabled());
   }

   @Test
   void plainColumnValueKeepsDesignFormula() {
      VSAggregateRef ref = variableRef(new VSAggregateRef(), "Max", TOTAL);
      List<DataRef> refs = ref.update(null, columns());

      VSAggregateRef aref = (VSAggregateRef) refs.get(0);
      assertEquals(TOTAL, aref.getDataRef().getName());
      assertEquals(AggregateFormula.MAX, aref.getFormula());
   }

   @Test
   void unknownFormulaNameStillFails() {
      VSAggregateRef ref = variableRef(new VSAggregateRef(), "Sum", "Ave(" + TOTAL + ")");
      assertThrows(ColumnNotFoundException.class, () -> ref.update(null, columns()));
   }

   @Test
   void multiValuedVariableAppliesParsedFormulaPerValue() {
      VSAggregateRef ref = new VSAggregateRef();
      ref.setColumnValue("$(ComboBox1)");
      ref.setFormulaValue("Sum");
      ref.getDynamicValues().get(0).setRValue(
         new Object[] { "Average(" + TOTAL + ")", QUANTITY });
      List<DataRef> refs = ref.update(null, columns());

      assertEquals(2, refs.size());
      assertEquals(TOTAL, ((VSAggregateRef) refs.get(0)).getDataRef().getName());
      assertEquals(AggregateFormula.AVG, ((VSAggregateRef) refs.get(0)).getFormula());
      assertEquals(QUANTITY, ((VSAggregateRef) refs.get(1)).getDataRef().getName());
      assertEquals(AggregateFormula.SUM, ((VSAggregateRef) refs.get(1)).getFormula());
   }

   @Test
   void multiValuedVariableKeepsParsedFormulaOnEveryUpdate() {
      VSAggregateRef ref = new VSAggregateRef();
      ref.setColumnValue("$(ComboBox1)");
      ref.setFormulaValue("Sum");
      ref.getDynamicValues().get(0).setRValue(
         new Object[] { "Average(" + TOTAL + ")", QUANTITY });

      // update() must not rewrite the variable's cached value array, or the next
      // update() reads the bare column and falls back to the design formula
      for(int n = 1; n <= 3; n++) {
         List<DataRef> refs = ref.update(null, columns());
         assertEquals(2, refs.size(), "update #" + n);
         VSAggregateRef first = (VSAggregateRef) refs.get(0);
         assertEquals(TOTAL, first.getDataRef().getName(), "update #" + n);
         assertEquals(AggregateFormula.AVG, first.getFormula(), "update #" + n);
         assertEquals(QUANTITY, ((VSAggregateRef) refs.get(1)).getDataRef().getName(),
                      "update #" + n);
         assertEquals(AggregateFormula.SUM, ((VSAggregateRef) refs.get(1)).getFormula(),
                      "update #" + n);
      }
   }

   @Test
   void chartWithNoneSiblingResolvesOnEveryUpdate() {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.addYField(variableRef(new VSChartAggregateRef(), "Sum", "Sum(" + TOTAL + ")"));
      info.addYField(plainChartRef(QUANTITY, "None"));

      for(int n = 1; n <= 3; n++) {
         info.update(null, columns(), false, null, null, null);
         VSChartAggregateRef rt = findRT(info, TOTAL);
         assertNotNull(rt, "variable measure unresolved on update #" + n);
         assertEquals(AggregateFormula.SUM, rt.getFormula());
         // the None sibling makes it a detail chart
         assertFalse(info.isAggregated(), "update #" + n);
      }
   }

   @Test
   void chartNoneVariableDoesNotDemoteSumSibling() {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.addYField(variableRef(new VSChartAggregateRef(), "None", "Sum(" + TOTAL + ")"));
      info.addYField(plainChartRef(QUANTITY, "Sum"));

      for(int n = 1; n <= 3; n++) {
         info.update(null, columns(), false, null, null, null);
         VSChartAggregateRef rt = findRT(info, TOTAL);
         assertNotNull(rt, "variable measure unresolved on update #" + n);
         assertTrue(rt.isAggregateEnabled(), "update #" + n);
         assertTrue(findRT(info, QUANTITY).isAggregateEnabled(), "update #" + n);
         assertTrue(info.isAggregated(), "update #" + n);
      }
   }

   private static <T extends VSAggregateRef> T variableRef(T ref, String formula, String value) {
      ref.setColumnValue("$(ComboBox1)");
      ref.setFormulaValue(formula);
      ref.setRefType(DataRef.MEASURE);
      ref.getDynamicValues().get(0).setRValue(value);
      return ref;
   }

   private static VSChartAggregateRef plainChartRef(String column, String formula) {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setColumnValue(column);
      ref.setFormulaValue(formula);
      ref.setRefType(DataRef.MEASURE);
      return ref;
   }

   private static VSChartAggregateRef findRT(DefaultVSChartInfo info, String column) {
      for(ChartRef ref : info.getRTYFields()) {
         if(ref instanceof VSChartAggregateRef aref && aref.getDataRef() != null &&
            column.equals(aref.getDataRef().getName()))
         {
            return aref;
         }
      }

      return null;
   }

   private static ColumnSelection columns() {
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(column(TOTAL));
      columns.addAttribute(column(QUANTITY));
      columns.addAttribute(column("Customer:Region", XSchema.STRING));
      return columns;
   }

   private static ColumnRef column(String name) {
      return column(name, XSchema.DOUBLE);
   }

   private static ColumnRef column(String name, String type) {
      ColumnRef col = new ColumnRef(new AttributeRef(null, name));
      col.setDataType(type);
      return col;
   }
}
