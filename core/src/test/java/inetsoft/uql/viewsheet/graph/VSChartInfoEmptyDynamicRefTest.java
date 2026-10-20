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
package inetsoft.uql.viewsheet.graph;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78882: a chart axis bound to a dynamic ref (CheckBox/ComboBox) that resolves to nothing
 * must keep an empty runtime axis instead of falling back to the raw design ref, which made
 * graph generation fail with "Aggregate not found: Sum()".
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSChartInfoEmptyDynamicRefTest {
   private static final String TOTAL = "Product:Total";
   private static final String REGION = "Customer:Region";

   @Test
   void emptyCheckBoxYStaysEmptyAfterUpdate() {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.addYField(dynamicMeasure(null));
      info.update(null, columns(), false, null, null, null);

      assertEquals(0, info.getRTYFields().length);
   }

   @Test
   void emptyCheckBoxXDimensionStaysEmptyAfterUpdate() {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.addXField(dynamicDimension(null));
      info.addYField(plainMeasure());
      info.update(null, columns(), false, null, null, null);

      assertEquals(0, info.getRTXFields().length);
      assertEquals(1, info.getRTYFields().length);
   }

   @Test
   void emptyCheckBoxGroupStaysEmptyAfterUpdate() {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.addGroupField(dynamicDimension(null));
      info.update(null, columns(), false, null, null, null);

      assertEquals(0, info.getRTGroupFields().length);
   }

   @Test
   void blankComboBoxValueYStaysEmptyAfterUpdate() {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.addYField(dynamicMeasure(""));
      info.update(null, columns(), false, null, null, null);

      assertEquals(0, info.getRTYFields().length);
   }

   @Test
   void blankComboBoxXDimensionStaysEmptyAfterUpdate() {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.addXField(dynamicDimension(""));
      info.addYField(plainMeasure());
      info.update(null, columns(), false, null, null, null);

      assertEquals(0, info.getRTXFields().length);
   }

   @Test
   void blankComboBoxGroupDimensionStaysEmptyAfterUpdate() {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.addGroupField(dynamicDimension(""));
      info.update(null, columns(), false, null, null, null);

      assertEquals(0, info.getRTGroupFields().length);
   }

   @Test
   void blankComboBoxValueDoesNotCreateBlankColumnRef() {
      assertTrue(dynamicMeasure("").update(null, columns()).isEmpty());
   }

   @Test
   void emptyArrayCheckBoxYStaysEmptyAfterUpdate() {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.addYField(dynamicMeasure(new Object[0]));
      info.update(null, columns(), false, null, null, null);

      assertEquals(0, info.getRTYFields().length);
   }

   @Test
   void selectedValueStillResolves() {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.addYField(dynamicMeasure(TOTAL));
      info.update(null, columns(), false, null, null, null);

      assertEquals(1, info.getRTYFields().length);
   }

   @Test
   void designFallbackBeforeUpdate() {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.addYField(dynamicMeasure(null));

      assertEquals(1, info.getRTYFields().length);
   }

   @Test
   void clearRuntimeRestoresDesignFallback() {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.addXField(dynamicDimension(null));
      info.addYField(dynamicMeasure(null));
      info.addGroupField(dynamicDimension(null));
      info.update(null, columns(), false, null, null, null);
      assertEquals(0, info.getRTYFields().length);

      info.clearRuntime();

      assertEquals(1, info.getRTXFields().length);
      assertEquals(1, info.getRTYFields().length);
      assertEquals(1, info.getRTGroupFields().length);
   }

   @Test
   void cloneKeepsResolvedEmptyState() {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.addYField(dynamicMeasure(null));
      info.update(null, columns(), false, null, null, null);

      DefaultVSChartInfo copy = (DefaultVSChartInfo) info.clone();

      assertEquals(0, copy.getRTYFields().length);
   }

   @Test
   void allDrillInvisibleYKeepsDesignFallback() {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      VSChartAggregateRef ref = plainMeasure();
      ref.setDrillVisible(false);
      info.addYField(ref);
      info.update(null, columns(), false, null, null, null);

      assertEquals(1, info.getRTYFields().length);
   }

   @Test
   void staticMissingColumnStillFallsBackToDesignField() {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("No Such Column"));
      info.update(null, columns(), false, null, null, null);

      assertEquals(1, info.getRTXFields().length);
   }

   private static VSChartDimensionRef dimension(String column) {
      VSChartDimensionRef ref = new VSChartDimensionRef();
      ref.setGroupColumnValue(column);
      return ref;
   }

   private static VSChartAggregateRef dynamicMeasure(Object value) {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setColumnValue("$(CheckBox1)");
      ref.setFormulaValue("Sum");
      ref.setRefType(DataRef.MEASURE);
      ref.getDynamicValues().get(0).setRValue(value);
      return ref;
   }

   private static VSChartDimensionRef dynamicDimension(Object value) {
      VSChartDimensionRef ref = new VSChartDimensionRef();
      ref.setGroupColumnValue("$(CheckBox1)");
      ref.getDynamicValues().get(0).setRValue(value);
      return ref;
   }

   private static VSChartAggregateRef plainMeasure() {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setColumnValue(TOTAL);
      ref.setFormulaValue("Sum");
      ref.setRefType(DataRef.MEASURE);
      return ref;
   }

   private static ColumnSelection columns() {
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(column(TOTAL, XSchema.DOUBLE));
      columns.addAttribute(column(REGION, XSchema.STRING));
      return columns;
   }

   private static ColumnRef column(String name, String type) {
      ColumnRef col = new ColumnRef(new AttributeRef(null, name));
      col.setDataType(type);
      return col;
   }
}
