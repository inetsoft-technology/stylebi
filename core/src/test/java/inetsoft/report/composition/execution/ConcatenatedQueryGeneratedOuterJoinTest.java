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
 * Bug #82305. ConcatenatedQuery.mergeFrom turns its children into text, so the union it
 * writes keeps, as a hint, whether a child holds an outer join StyleBI created. A ClickHouse
 * statement selecting from that union then gets join_use_nulls = 1.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ConcatenatedQueryGeneratedOuterJoinTest {
   @Test
   void unionOfAMergedOuterJoinGetsTheSetting() throws Exception {
      UniformSQL nsql = merge(childQuery(mergedJoin("*=")), childQuery(parse("select c.k from c")));
      UniformSQL union = (UniformSQL) nsql.getSelectTable()[0].getName();

      assertEquals(Boolean.TRUE, union.getHint(UniformSQL.HINT_GENERATED_OUTER_JOIN, false));
      assertEquals("\nSETTINGS join_use_nulls = 1",
                   SQLHelper.getSQLHelper("clickhouse").getExecutionSuffix(nsql));
   }

   @Test
   void unionOfInnerJoinsAndUserLeftJoinsGetsNoSetting() throws Exception {
      UniformSQL nsql = merge(childQuery(mergedJoin("=")),
                              childQuery(parse("select a.k from a left join b on a.k = b.k")));
      UniformSQL union = (UniformSQL) nsql.getSelectTable()[0].getName();

      assertNull(union.getHint(UniformSQL.HINT_GENERATED_OUTER_JOIN, false));
      assertNull(SQLHelper.getSQLHelper("clickhouse").getExecutionSuffix(nsql));
   }

   private static UniformSQL merge(JDBCQuery child0, JDBCQuery child1) throws Exception {
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

      assertTrue(query.mergeFrom(new VariableTable()));
      return nsql;
   }

   /**
    * A worksheet join as JoinQuery.mergeFrom writes it.
    */
   private static UniformSQL mergedJoin(String op) throws Exception {
      UniformSQL top = new UniformSQL();
      UniformSQL left = parse("select a.k from a");
      UniformSQL right = parse("select b.k from b");
      left.setSubQuery(true);
      right.setSubQuery(true);
      top.addTable("PA", left);
      top.addTable("PB", right);
      top.getSelection().addColumn("PA.k");
      XJoin join = new XJoin(new XExpression("PA.k", XExpression.EXPRESSION),
                             new XExpression("PB.k", XExpression.EXPRESSION), op);
      join.setTable1("PA");
      join.setTable2("PB");
      top.addJoin(join);
      return top;
   }

   private static UniformSQL parse(String text) throws Exception {
      UniformSQL usql = new UniformSQL();
      // parse(String, int, long) is package private, PARSE_ALL is 0
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class,
                                                        long.class);
      parse.setAccessible(true);
      parse.invoke(usql, text, 0, 4000L);
      usql.setSQLString(text, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), text);
      return usql;
   }

   private static JDBCQuery childQuery(UniformSQL usql) {
      JDBCQuery query = new JDBCQuery();
      query.setName("bug82305");
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
