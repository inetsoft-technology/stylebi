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
import inetsoft.uql.asset.AggregateFormula;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/** The text rules that read one free-SQL select item as an aggregate (turn-provenance spec 3.7). */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MetadataApiServiceSqlAggregateCallTest {
   private static void assertCall(String text, AggregateFormula formula, String field) {
      MetadataApiService.SqlAggregateCall call = MetadataApiService.sqlAggregateCall(text);
      assertNotNull(call, text);
      assertEquals(formula.getName(), call.formula(), text);
      assertEquals(field, call.field(), text);
   }

   @Test
   void theFiveAggregateFunctionsAreRecognizedInAnyCase() {
      assertCall("SUM(a.sales)", AggregateFormula.SUM, "a.sales");
      assertCall("avg(a.price)", AggregateFormula.AVG, "a.price");
      assertCall("Min(a.d)", AggregateFormula.MIN, "a.d");
      assertCall("MAX (a.d)", AggregateFormula.MAX, "a.d");
      assertCall("COUNT(*)", AggregateFormula.COUNT_ALL, "*");
   }

   @Test
   void countDistinctIsCountDistinct() {
      assertCall("COUNT(distinct cat.CATEGORY_ID)", AggregateFormula.COUNT_DISTINCT, "cat.CATEGORY_ID");
      assertCall("count( DISTINCT x )", AggregateFormula.COUNT_DISTINCT, "x");
   }

   @Test
   void sumOfCaseKeepsTheInnerTextAsField() {
      assertCall("SUM(CASE WHEN a.k = 'x' THEN a.amt ELSE 0 END)", AggregateFormula.SUM,
                 "CASE WHEN a.k = 'x' THEN a.amt ELSE 0 END");
   }

   @Test
   void parensInsideStringLiteralsAreSkipped() {
      assertCall("SUM(CASE WHEN a.k = ')' THEN 1 ELSE 0 END)", AggregateFormula.SUM,
                 "CASE WHEN a.k = ')' THEN 1 ELSE 0 END");
      assertCall("MAX(CASE WHEN a.k = 'it''s )' THEN 1 END)", AggregateFormula.MAX,
                 "CASE WHEN a.k = 'it''s )' THEN 1 END");
   }

   @Test
   void aCallWhoseCloseParenIsNotLastIsNotAFormula() {
      assertNull(MetadataApiService.sqlAggregateCall("SUM(a.x) / COUNT(a.y)"));
      assertNull(MetadataApiService.sqlAggregateCall("SUM(a.x) + 1"));
      assertNull(MetadataApiService.sqlAggregateCall("SUM(a.x) over (partition by a.r)"));
   }

   @Test
   void otherTextIsNotAFormula() {
      assertNull(MetadataApiService.sqlAggregateCall("UPPER(a.name)"));
      assertNull(MetadataApiService.sqlAggregateCall("a.name"));
      assertNull(MetadataApiService.sqlAggregateCall("(a.x)"));
      assertNull(MetadataApiService.sqlAggregateCall(null));
   }

   @Test
   void aggregateTokensAreDetectedInsideExpressions() {
      assertTrue(MetadataApiService.containsSqlAggregate("SUM(a.x) / COUNT(a.y)"));
      assertTrue(MetadataApiService.containsSqlAggregate("ROUND(avg (a.p), 2)"));
      assertFalse(MetadataApiService.containsSqlAggregate("a.summary"));
      assertFalse(MetadataApiService.containsSqlAggregate("a.count_total"));
      assertFalse(MetadataApiService.containsSqlAggregate("UPPER(a.name)"));
      assertFalse(MetadataApiService.containsSqlAggregate(null));
   }

   @Test
   void matchingParenHonorsNestingAndLiterals() {
      String text = "SUM(f(a, ')') + g(b))";
      assertEquals(text.length() - 1, MetadataApiService.matchingSqlParen(text, 3));
      assertEquals(-1, MetadataApiService.matchingSqlParen("SUM(a", 3));
   }
}
