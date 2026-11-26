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
package inetsoft.mv;

import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.CalculateRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * When the MV of a table is made for the table beneath it (a sub-MV), whether an association MV
 * is made too ({@code VSMVAnalyzer.isAssociationCompatible}): only for a mirror without a row
 * limit that has the columns of the table beneath it. Calculated fields of the mirror do not
 * count, as they are not kept in an MV (Bug #53263).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSMVAnalyzerAssociationTest {
   @Test
   void aMirrorWithTheSameColumnsGetsAnAssociationMV() throws Exception {
      TableAssembly child = table(column("Region"), column("Amount"));
      TableAssembly mirror = mirror(0, column("Region"), column("Amount"));

      assertTrue(compatible(mirror, child));
   }

   @Test
   void calculatedFieldsOfTheMirrorAreNotCounted() throws Exception {
      // Bug #53263
      TableAssembly child = table(column("Region"), column("Amount"));
      TableAssembly mirror = mirror(0, column("Region"), column("Amount"), calc("Flag"),
                                    calc("Big"));

      assertTrue(compatible(mirror, child));
   }

   @Test
   void onlyTheNumberOfColumnsIsCompared() throws Exception {
      // the same number of columns is enough, calculated fields included and whatever the
      // names: the mirror lacks Amount but has as many columns as the table beneath it
      TableAssembly child = table(column("Region"), column("Amount"));
      TableAssembly mirror = mirror(0, column("Region"), calc("Flag"));

      assertTrue(compatible(mirror, child));
   }

   @Test
   void anotherColumnOfTheMirrorIsCounted() throws Exception {
      TableAssembly child = table(column("Region"), column("Amount"));
      TableAssembly mirror = mirror(0, column("Region"), column("Amount"), calc("Flag"),
                                    column("State"));

      assertFalse(compatible(mirror, child));
   }

   @Test
   void aMirrorWithFewerColumnsGetsNone() throws Exception {
      TableAssembly child = table(column("Region"), column("Amount"));
      TableAssembly mirror = mirror(0, column("Region"));

      assertFalse(compatible(mirror, child));
   }

   @Test
   void aMirrorWithARowLimitGetsNone() throws Exception {
      TableAssembly child = table(column("Region"), column("Amount"));
      TableAssembly mirror = mirror(10, column("Region"), column("Amount"));

      assertFalse(compatible(mirror, child));
   }

   @Test
   void aTableThatIsNotAMirrorGetsNone() throws Exception {
      TableAssembly child = table(column("Region"), column("Amount"));
      TableAssembly parent = table(column("Region"), column("Amount"));

      assertFalse(compatible(parent, child));
   }

   private static boolean compatible(TableAssembly parent, TableAssembly child)
      throws Exception
   {
      Method method = VSMVAnalyzer.class.getDeclaredMethod(
         "isAssociationCompatible", TableAssembly.class, TableAssembly.class);
      method.setAccessible(true);
      return (Boolean) method.invoke(null, parent, child);
   }

   /** A table with these public columns. */
   private static TableAssembly table(ColumnRef... columns) {
      TableAssembly table = mock(TableAssembly.class);
      ColumnSelection selection = selection(columns);
      when(table.getColumnSelection(true)).thenReturn(selection);
      return table;
   }

   private static TableAssembly mirror(int maxRows, ColumnRef... columns) {
      MirrorTableAssembly mirror = mock(MirrorTableAssembly.class);
      ColumnSelection selection = selection(columns);
      when(mirror.getColumnSelection(true)).thenReturn(selection);
      when(mirror.getMaxRows()).thenReturn(maxRows);
      return mirror;
   }

   private static ColumnSelection selection(ColumnRef... columns) {
      ColumnSelection selection = new ColumnSelection();

      for(ColumnRef column : columns) {
         selection.addAttribute(column);
      }

      return selection;
   }

   private static ColumnRef column(String name) {
      ColumnRef column = new ColumnRef(new AttributeRef(null, name));
      column.setDataType(XSchema.STRING);
      return column;
   }

   /** A calculated field on the detail rows. */
   private static ColumnRef calc(String name) {
      CalculateRef calc = new CalculateRef(true);
      ExpressionRef expression = new ExpressionRef(null, name);
      expression.setExpression("field['Amount'] > 100");
      calc.setDataRef(expression);
      calc.setDataType(XSchema.BOOLEAN);
      return calc;
   }
}
