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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #81091: a worksheet join that ends up with no usable join column gives an empty table.
 * JoinQuery must also leave a user-visible message for it, without changing the empty table
 * and without warning for the by-design cartesian product of two tables with no operator.
 * The fix varies with the route into the empty-key state: column not found (skipped),
 * operator without any condition (list empty), table without any operator (no operator).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class JoinQueryNoJoinColumnsMessageTest {
   @BeforeEach
   void clearMessages() {
      Tool.clearUserMessage();
   }

   @AfterEach
   void tearDown() {
      Tool.clearUserMessage();
   }

   @Test
   void leftColumnNotFoundGivesEmptyTableAndMessage() throws Exception {
      TableLens result = run(2, lens("jkeyZ"), lens("jkeyZ"), Op.WITH_CONDITION);

      assertEquals(0, result.getRowCount());
      assertMessageMentions("81091-u.jkey");
   }

   @Test
   void rightColumnNotFoundGivesEmptyTableAndMessage() throws Exception {
      TableLens result = run(2, lens("jkey"), lens("jkeyZ"), Op.WITH_CONDITION);

      assertEquals(0, result.getRowCount());
      assertMessageMentions("jkey");
   }

   @Test
   void thirdTableWithoutOperatorGivesEmptyTableAndMessage() throws Exception {
      TableLens result = run(3, lens("jkey"), lens("jkey"), Op.WITH_CONDITION);

      assertEquals(0, result.getRowCount());
      assertMessageMentions("Third");
   }

   @Test
   void operatorWithoutConditionGivesEmptyTableAndMessage() throws Exception {
      TableLens result = run(2, lens("jkey"), lens("jkey"), Op.EMPTY);

      assertEquals(0, result.getRowCount());
      assertMessageMentions("81091-u");
   }

   @Test
   void twoTablesWithoutOperatorStayCartesianWithoutMessage() throws Exception {
      TableLens result = run(2, lens("jkey"), lens("jkey"), Op.NONE);

      assertTrue(result.getRowCount() > 0);
      assertNull(Tool.getUserMessage());
   }

   @Test
   void validJoinGivesRowsWithoutMessage() throws Exception {
      TableLens result = run(2, lens("jkey"), lens("jkey"), Op.WITH_CONDITION);

      assertTrue(result.getRowCount() > 0);
      assertNull(Tool.getUserMessage());
   }

   private enum Op { WITH_CONDITION, EMPTY, NONE }

   private static void assertMessageMentions(String text) {
      UserMessage message = Tool.getUserMessage();

      assertNotNull(message, "no user message for an unresolved join");
      assertFalse(message.getMessage().contains("common.join."),
                  "catalog key is not defined: " + message.getMessage());
      assertTrue(message.getMessage().contains(text), message.getMessage());
      assertTrue(message.getMessage().contains("Join1"), message.getMessage());
   }

   // join of [Query1, 81091-u (, Third)] with an operator Query1 -> 81091-u only
   private static TableLens run(int count, TableLens t0, TableLens t1, Op mode)
      throws Exception
   {
      JoinQuery query = mock(JoinQuery.class, CALLS_REAL_METHODS);
      AbstractJoinTableAssembly table = mock(AbstractJoinTableAssembly.class);
      when(table.getName()).thenReturn("Join1");
      TableAssemblyOperator top = new TableAssemblyOperator();

      if(mode == Op.WITH_CONDITION) {
         TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
         op.setOperation(TableAssemblyOperator.INNER_JOIN);
         op.setLeftTable("Query1");
         op.setRightTable("81091-u");
         op.setLeftAttribute(new ColumnRef(new AttributeRef("81091-u", "jkey")));
         op.setRightAttribute(new ColumnRef(new AttributeRef(null, "jkey")));
         top.addOperator(op);
      }

      when(table.getOperator(anyString(), anyString())).thenAnswer(inv -> {
         String l = inv.getArgument(0);
         String r = inv.getArgument(1);
         return mode != Op.NONE && "Query1".equals(l) && "81091-u".equals(r) ? top : null;
      });
      set(query, "table", table, JoinQuery.class);

      String[] names = { "Query1", "81091-u", "Third" };
      TableAssembly[] tables = new TableAssembly[count];
      AssetQuery[] queries = new AssetQuery[count];

      for(int i = 0; i < count; i++) {
         tables[i] = mock(TableAssembly.class);
         when(tables[i].getName()).thenReturn(names[i]);
         queries[i] = mock(AssetQuery.class);
         TableLens lens = i == 0 ? t0 : i == 1 ? t1 : lens("x");
         when(queries[i].getTableLens(any())).thenReturn(lens);
      }

      set(query, "tables", tables, JoinQuery.class);
      set(query, "queries", queries, JoinQuery.class);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.isIgnoreFiltering()).thenReturn(false);
      set(query, "box", box, PreAssetQuery.class);

      TableLens result = query.getPostBaseTableLens0(new VariableTable());
      result.moreRows(XTable.EOT);
      return result;
   }

   private static TableLens lens(String header) {
      return new DefaultTableLens(new Object[][] { { header, "w" }, { "a", "1" }, { "b", "2" } });
   }

   private static void set(Object target, String name, Object value, Class<?> cls)
      throws Exception
   {
      Field f = cls.getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
   }
}
