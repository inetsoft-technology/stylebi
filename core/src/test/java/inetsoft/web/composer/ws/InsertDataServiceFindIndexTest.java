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
package inetsoft.web.composer.ws;

import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #78314: inserting a column after a header must place the new embedded data column
 * where the column selection places it. The "insert after" walk in findIndex() has to
 * examine column 0, otherwise the data gets the new column in front of col0 while the
 * selection has it behind col0.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class InsertDataServiceFindIndexTest {
   @Test
   void insertAfterFirstColumnGoesBehindIt() throws Exception {
      XEmbeddedTable data = data("col0", "col1");
      ColumnSelection columns = selection(column("col0"), column("col1"));

      // the selection inserts at index + 1 = 1, so the data column must go to 1 as well
      assertEquals(1, findIndex(data, columns, 0, false));
   }

   @Test
   void insertAfterExpressionsFallsBackToFirstDataColumn() throws Exception {
      XEmbeddedTable data = data("col0", "col1");
      ColumnSelection columns = selection(column("col0"), expression("expr"), column("col1"));

      // nearest non-expression column at or left of index 1 is col0 (data position 0)
      assertEquals(1, findIndex(data, columns, 1, false));
   }

   @Test
   void insertAfterOnlyExpressionsGoesFirst() throws Exception {
      XEmbeddedTable data = data("col0");
      ColumnSelection columns = selection(expression("expr"), column("col0"));

      assertEquals(0, findIndex(data, columns, 0, false));
   }

   @Test
   void insertBeforeFirstColumnGoesInFrontOfIt() throws Exception {
      XEmbeddedTable data = data("col0", "col1");
      ColumnSelection columns = selection(column("col0"), column("col1"));

      assertEquals(0, findIndex(data, columns, 0, true));
   }

   private static int findIndex(XEmbeddedTable data, ColumnSelection columns, int index,
                                boolean insert) throws Exception
   {
      Method method = InsertDataService.class.getDeclaredMethod(
         "findIndex", XEmbeddedTable.class, ColumnSelection.class, int.class, boolean.class);
      method.setAccessible(true);
      InsertDataService service = new InsertDataService(null, null);
      return (Integer) method.invoke(service, data, columns, index, insert);
   }

   private static XEmbeddedTable data(String... headers) {
      String[] types = new String[headers.length];
      Object[] row = new Object[headers.length];

      for(int i = 0; i < headers.length; i++) {
         types[i] = XSchema.STRING;
         row[i] = headers[i];
      }

      return new XEmbeddedTable(types, new Object[][] { row });
   }

   private static ColumnSelection selection(ColumnRef... refs) {
      ColumnSelection columns = new ColumnSelection();

      for(ColumnRef ref : refs) {
         columns.addAttribute(ref);
      }

      return columns;
   }

   private static ColumnRef column(String name) {
      return new ColumnRef(new AttributeRef(null, name));
   }

   private static ColumnRef expression(String name) {
      return new ColumnRef(new ExpressionRef(null, name));
   }
}
