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
package inetsoft.web.wiz.service;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;

import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.util.XSourceInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that /ws/structure reports the WHERE/HAVING conditions of free-SQL tables, parsed with
 * the real StyleBI SQL parser.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MetadataApiServiceSqlConditionsTest {
   private static final String BUG_SQL =
      "SELECT\n  c.CUSTOMER_ID,\n  c.COMPANY_NAME,\n" +
      "  COUNT(DISTINCT cat.CATEGORY_ID) AS DISTINCT_CATEGORY_COUNT\n" +
      "FROM SA.CUSTOMERS c\nJOIN SA.ORDERS o\n  ON c.CUSTOMER_ID = o.CUSTOMER_ID\n" +
      "JOIN SA.ORDER_DETAILS od\n  ON o.ORDER_ID = od.ORDER_ID\n" +
      "JOIN SA.PRODUCTS p\n  ON od.PRODUCT_ID = p.PRODUCT_ID\n" +
      "JOIN SA.CATEGORIES cat\n  ON p.CATEGORY_ID = cat.CATEGORY_ID\n" +
      "GROUP BY\n  c.CUSTOMER_ID,\n  c.COMPANY_NAME\n" +
      "HAVING\n  COUNT(DISTINCT cat.CATEGORY_ID) = (\n    SELECT COUNT(*)\n" +
      "    FROM SA.CATEGORIES\n  )";

   private static SQLBoundTableAssembly sqlTable(String text) {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t = new SQLBoundTableAssembly(ws, "sqlTable");
      t.setSourceInfo(new SourceInfo(XSourceInfo.PHYSICAL_TABLE, "ds", "ds"));
      UniformSQL sql = new UniformSQL();
      sql.setParseSQL(false);
      sql.setSQLString(text, false);
      JDBCQuery query = new JDBCQuery();
      query.setSQLDefinition(sql);
      ((SQLBoundTableAssemblyInfo) t.getInfo()).setQuery(query);
      return t;
   }

   private static MetadataApiService.StructureConditions run(String text) {
      return MetadataApiService.extractStructureConditionsAndCoverage(sqlTable(text));
   }

   @Test
   void bugHavingSubqueryComparison() {
      var r = run(BUG_SQL);
      assertEquals("full", r.coverage());
      assertEquals(1, r.leaves().size());
      var l = r.leaves().get(0);
      assertEquals("post", l.getPhase());
      assertEquals("DISTINCT_CATEGORY_COUNT", l.getField());
      assertEquals("EQUAL_TO", l.getOperation());
      assertTrue(l.getValues().isEmpty());
      assertEquals("sql", l.getSource());
      assertTrue(l.getText().toUpperCase().contains("SELECT"), l.getText());
      System.out.println("HAVING_TEXT=[" + l.getText() + "]");
      assertNull(l.getJunction());
      assertNull(l.getGrouped());
   }

   @Test
   void whereAndWithInclusiveBound() {
      var r = run("SELECT c.STATE, o.YEAR FROM C c, O o WHERE c.STATE = 'NJ' AND o.YEAR >= 2024");
      assertEquals("full", r.coverage());
      assertEquals(2, r.leaves().size());
      var a = r.leaves().get(0);
      var b = r.leaves().get(1);
      assertEquals("STATE", a.getField());
      assertEquals("EQUAL_TO", a.getOperation());
      assertEquals(List.of("NJ"), a.getValues());
      assertEquals("pre", a.getPhase());
      assertNull(a.getJunction());
      assertEquals("YEAR", b.getField());
      assertEquals("GREATER_THAN", b.getOperation());
      assertEquals(Boolean.TRUE, b.getEqual());
      assertEquals(List.of("2024"), b.getValues());
      assertEquals("AND", b.getJunction());
   }

   @Test
   void strictBoundsAreNotEqual() {
      var r = run("SELECT a.x FROM T a WHERE a.x > 1 AND a.x < 5 AND a.x <= 4");
      assertEquals(Boolean.FALSE, r.leaves().get(0).getEqual());
      assertEquals("LESS_THAN", r.leaves().get(1).getOperation());
      assertEquals(Boolean.FALSE, r.leaves().get(1).getEqual());
      assertEquals(Boolean.TRUE, r.leaves().get(2).getEqual());
   }

   @Test
   void commaJoinPredicateIsNotReported() {
      var r = run("SELECT a.id, a.x FROM A a, B b WHERE a.id = b.id AND a.x = 1");
      assertEquals(1, r.leaves().size());
      assertEquals("x", r.leaves().get(0).getField().toLowerCase());
      assertEquals(List.of("1"), r.leaves().get(0).getValues());
      assertEquals("full", r.coverage());
   }

   @Test
   void nestedGroupLeavesAreGrouped() {
      var r = run("SELECT a.a, a.b, a.c FROM T a WHERE a.a = 1 OR (a.b = 2 AND a.c = 3)");
      assertEquals(3, r.leaves().size());
      assertNull(r.leaves().get(0).getGrouped());
      assertNull(r.leaves().get(0).getJunction());
      assertEquals("AND", r.leaves().get(1).getJunction());
      assertEquals(Boolean.TRUE, r.leaves().get(1).getGrouped());
      assertEquals("AND", r.leaves().get(2).getJunction());
      assertEquals(Boolean.TRUE, r.leaves().get(2).getGrouped());
      assertEquals("full", r.coverage());
   }

   @Test
   void notGroupIsTextOnlyAndPartial() {
      var r = run("SELECT a.a, a.b FROM T a WHERE NOT (a.a = 1 AND a.b = 2)");
      assertEquals("partial", r.coverage());
      assertEquals(1, r.leaves().size());
      var l = r.leaves().get(0);
      assertNull(l.getField());
      assertNull(l.getOperation());
      assertTrue(l.getValues().isEmpty());
      assertEquals("sql", l.getSource());
      assertNotNull(l.getText());
   }

   @Test
   void notEqualIsNegatedEqualTo() {
      var r = run("SELECT a.x FROM T a WHERE a.x <> 3");
      var l = r.leaves().get(0);
      assertEquals("EQUAL_TO", l.getOperation());
      assertTrue(l.isNegated());
      assertEquals(List.of("3"), l.getValues());
   }

   @Test
   void likeInBetweenNull() {
      var r = run("SELECT a.n, a.k, a.v, a.w FROM T a WHERE a.n LIKE 'A%' AND a.k IN (1, 2) " +
                  "AND a.v BETWEEN 5 AND 9 AND a.w IS NULL");
      assertEquals("full", r.coverage());
      assertEquals("LIKE", r.leaves().get(0).getOperation());
      assertEquals(List.of("A%"), r.leaves().get(0).getValues());
      assertEquals("ONE_OF", r.leaves().get(1).getOperation());
      assertEquals(List.of("1", "2"), r.leaves().get(1).getValues());
      assertEquals("BETWEEN", r.leaves().get(2).getOperation());
      assertEquals(List.of("5", "9"), r.leaves().get(2).getValues());
      assertEquals("NULL", r.leaves().get(3).getOperation());
   }

   @Test
   void existsIsTextOnlyAndPartial() {
      var r = run("SELECT a.x FROM T a WHERE EXISTS (SELECT 1 FROM U u WHERE u.id = a.x)");
      assertEquals("partial", r.coverage());
      assertEquals(1, r.leaves().size());
      var l = r.leaves().get(0);
      assertNull(l.getField());
      assertNull(l.getOperation());
      assertTrue(l.getText().toUpperCase().contains("EXISTS"), l.getText());
   }

   @Test
   void aliasIsUsedForSelectColumn() {
      var r = run("SELECT b.z AS zed FROM B b WHERE b.z = 'q'");
      assertEquals("zed", r.leaves().get(0).getField());
   }

   @Test
   void unparseableSqlIsUnavailable() {
      var r = run("WITH t AS (SELECT 1 AS x FROM D) SELECT x FROM t WHERE x = 1");
      assertEquals("unavailable", r.coverage());
      assertTrue(r.leaves().isEmpty());
   }

   @Test
   void sqlWithoutWhereOrHavingIsFull() {
      var r = run("SELECT a.x FROM T a");
      assertEquals("full", r.coverage());
      assertTrue(r.leaves().isEmpty());
   }

   @Test
   void storedDefinitionIsNotMutated() {
      SQLBoundTableAssembly t = sqlTable(BUG_SQL);
      UniformSQL stored = (UniformSQL) ((SQLBoundTableAssemblyInfo) t.getInfo()).getQuery()
         .getSQLDefinition();
      int before = stored.getParseResult();
      String text = stored.getSQLString();
      Object having = stored.getHaving();
      MetadataApiService.extractStructureConditionsAndCoverage(t);
      assertEquals(before, stored.getParseResult());
      assertEquals(text, stored.getSQLString());
      assertEquals(having, stored.getHaving());
   }

   @Test
   void nonSqlTableHasNoCoverage() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly t = new PhysicalBoundTableAssembly(ws, "p");
      var r = MetadataApiService.extractStructureConditionsAndCoverage(t);
      assertNull(r.coverage());
      assertTrue(r.leaves().isEmpty());
   }

   @Test
   void conditionListLeafCarriesEqualFlag() {
      ConditionList list = new ConditionList();
      Condition cond = new Condition();
      cond.setOperation(XCondition.LESS_THAN);
      cond.setEqual(true);
      cond.addValue(5);
      list.append(new ConditionItem(new AttributeRef(null, "n"), cond, 0));
      var out = new java.util.ArrayList<inetsoft.web.wiz.model.WorksheetStructure.ConditionLeaf>();
      MetadataApiService.collectStructureConditions(list, "pre", out);
      assertEquals(Boolean.TRUE, out.get(0).getEqual());
      assertNull(out.get(0).getSource());
   }
}
