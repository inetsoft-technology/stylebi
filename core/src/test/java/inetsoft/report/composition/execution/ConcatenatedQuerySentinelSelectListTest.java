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

import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.jdbc.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #77620. A sentinel parameter in a scalar subquery of the select list of a concatenated
 * child is rewritten in the merged sql, and the child's own query is left as it is.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ConcatenatedQuerySentinelSelectListTest {
   private static final String CHILD =
      "select a.k, (select count(*) from b where b.k = $(p)) from a";

   @Test
   void mergeFromRewritesTheSelectListSubquery() throws Exception {
      JDBCQuery child0 = childQuery();
      JDBCQuery child1 = childQuery();
      UniformSQL sql0 = (UniformSQL) child0.getSQLDefinition();
      JDBCSelection selection = (JDBCSelection) sql0.getSelection();
      String column = selection.getColumn(1);
      assertTrue(column.contains("$(p)"), column);

      String first = merge(child0, child1, XConstants.CONDITION_NULL_VALUE);
      assertFalse(first.contains("$(p)"), first);
      assertTrue(first.contains("IS NULL"), first);
      assertEquals(column, selection.getColumn(1));
      assertNull(selection.getColumnSQL(1));

      String second = merge(child0, child1, "n1");
      assertTrue(second.contains("$(p)"), second);
      assertFalse(second.contains("IS NULL"), second);
   }

   private static String merge(JDBCQuery child0, JDBCQuery child1, String p) throws Exception {
      UniformSQL nsql = new UniformSQL();
      ConcatenatedQuery query = mock(ConcatenatedQuery.class, CALLS_REAL_METHODS);
      ConcatenatedTableAssembly table = mock(ConcatenatedTableAssembly.class);
      when(table.getOperator(anyString(), anyString())).thenReturn(union());
      setField(ConcatenatedQuery.class, query, "table", table);
      setField(ConcatenatedQuery.class, query, "tables", tables(2));
      setField(ConcatenatedQuery.class, query, "queries",
               new AssetQuery[] { subQuery(child0), subQuery(child1) });
      setField(PreAssetQuery.class, query, "box", mock(AssetQuerySandbox.class));
      setField(PreAssetQuery.class, query, "mode", AssetQuerySandbox.RUNTIME_MODE);
      doReturn(nsql).when(query).getUniformSQL();
      doReturn(new JDBCQuery()).when(query).getQuery();
      doReturn(mock(SQLHelper.class)).when(query).getSQLHelper(any());

      VariableTable vars = new VariableTable();
      vars.put("p", p);
      assertTrue(query.mergeFrom(vars));
      return nsql.getSelectTable()[0].getName().toString();
   }

   private static JDBCQuery childQuery() throws Exception {
      UniformSQL usql = new UniformSQL();
      // parse(String, int, long) is package private, PARSE_ALL is 0
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class,
                                                        long.class);
      parse.setAccessible(true);
      parse.invoke(usql, CHILD, 0, 4000L);
      usql.setSQLString(CHILD, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), CHILD);

      JDBCQuery query = new JDBCQuery();
      query.setName("bug77620");
      query.setSQLDefinition(usql);
      return query;
   }

   private static AssetQuery subQuery(JDBCQuery query) throws Exception {
      AssetQuery sub = mock(AssetQuery.class);
      when(sub.getQuery()).thenReturn(query);
      when(sub.getUniformSQL()).thenReturn((UniformSQL) query.getSQLDefinition());
      return sub;
   }

   private static TableAssemblyOperator union() {
      TableAssemblyOperator top = new TableAssemblyOperator();
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setOperation(TableAssemblyOperator.UNION);
      top.addOperator(op);
      return top;
   }

   private static TableAssembly[] tables(int count) {
      TableAssembly[] tables = new TableAssembly[count];

      for(int i = 0; i < count; i++) {
         tables[i] = mock(TableAssembly.class);
         when(tables[i].getName()).thenReturn("T" + i);
      }

      return tables;
   }

   private static void setField(Class<?> cls, Object target, String name, Object value)
      throws Exception
   {
      Field field = cls.getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
   }
}
