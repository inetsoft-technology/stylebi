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
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.GroupRef;
import inetsoft.uql.asset.PhysicalBoundTableAssembly;
import inetsoft.uql.asset.SQLBoundTableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.web.wiz.model.WorksheetStructure;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * sqlAggregate / sqlAggregateCoverage read from a free-SQL table's own text with the real StyleBI
 * parser (turn-provenance spec 3.7). The worksheet aggregate is never overwritten.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MetadataApiServiceSqlAggregateTest {
   private static SQLBoundTableAssembly sqlTable(String text, String... worksheetColumns) {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t = new SQLBoundTableAssembly(ws, "sqlTable");
      t.setSourceInfo(new SourceInfo(XSourceInfo.PHYSICAL_TABLE, "ds", "ds"));
      UniformSQL sql = new UniformSQL();
      sql.setParseSQL(false);
      sql.setSQLString(text, false);
      JDBCQuery query = new JDBCQuery();
      query.setSQLDefinition(sql);
      ((SQLBoundTableAssemblyInfo) t.getInfo()).setQuery(query);
      ColumnSelection columns = new ColumnSelection();

      for(String column : worksheetColumns) {
         columns.addAttribute(new ColumnRef(new AttributeRef(null, column)));
      }

      t.setColumnSelection(columns, false);
      return t;
   }

   private static MetadataApiService.StructureConditions run(String text, String... worksheetColumns) {
      return MetadataApiService.extractStructureConditionsAndCoverage(sqlTable(text, worksheetColumns));
   }

   private static List<String> groupFields(WorksheetStructure.AggregateSummary summary) {
      List<String> out = new ArrayList<>();

      for(WorksheetStructure.AggGroupBy groupBy : summary.getGroupBy()) {
         out.add(groupBy.getField());
      }

      return out;
   }

   @Test
   void plainGroupByMapsToTheWorksheetColumnsByPosition() {
      var r = run("SELECT a.region, SUM(a.sales) AS total FROM T a GROUP BY a.region",
                  "REGION", "TOTAL_SALES");

      assertEquals("full", r.sqlAggregateCoverage());
      assertEquals(List.of("REGION"), groupFields(r.sqlAggregate()));
      assertEquals(1, r.sqlAggregate().getAggregates().size());
      WorksheetStructure.AggMeasure measure = r.sqlAggregate().getAggregates().get(0);
      assertEquals(AggregateFormula.SUM.getName(), measure.getFormula());
      assertTrue("a.sales".equalsIgnoreCase(measure.getField()), measure.getField());
      assertEquals("TOTAL_SALES", measure.getColumn());
      assertNull(measure.getExpression());
   }

   @Test
   void ordinalGroupByMapsToTheSelectItemAtThatPosition() {
      var r = run("SELECT a.region, SUM(a.sales) AS total FROM T a GROUP BY 1", "R", "T");

      assertEquals("full", r.sqlAggregateCoverage());
      assertEquals(List.of("R"), groupFields(r.sqlAggregate()));
   }

   @Test
   void aliasGroupByMapsToTheAliasedSelectItem() {
      var r = run("SELECT a.region AS reg, SUM(a.sales) AS total FROM T a GROUP BY reg", "REG", "TOTAL");

      assertEquals("full", r.sqlAggregateCoverage());
      assertEquals(List.of("REG"), groupFields(r.sqlAggregate()));
   }

   @Test
   void countDistinctIsReadAsCountDistinct() {
      var r = run("SELECT c.id, COUNT(DISTINCT o.cat) AS n FROM C c, O o WHERE c.id = o.cid GROUP BY c.id",
                  "ID", "N");

      WorksheetStructure.AggMeasure measure = r.sqlAggregate().getAggregates().get(0);
      assertEquals(AggregateFormula.COUNT_DISTINCT.getName(), measure.getFormula());
      assertTrue("o.cat".equalsIgnoreCase(measure.getField()), measure.getField());
      assertEquals("N", measure.getColumn());
      assertEquals("full", r.sqlAggregateCoverage());
   }

   @Test
   void sumOfCaseIsASumWithTheCaseTextAsField() {
      var r = run("SELECT a.region, SUM(CASE WHEN a.kind = 'x' THEN a.amt ELSE 0 END) AS amt_x " +
                  "FROM T a GROUP BY a.region", "REGION", "AMT_X");

      WorksheetStructure.AggMeasure measure = r.sqlAggregate().getAggregates().get(0);
      assertEquals(AggregateFormula.SUM.getName(), measure.getFormula());
      assertTrue(measure.getField().toUpperCase().startsWith("CASE"), measure.getField());
      assertTrue(measure.getField().toUpperCase().endsWith("END"), measure.getField());
      assertEquals("AMT_X", measure.getColumn());
      assertEquals("full", r.sqlAggregateCoverage());
   }

   @Test
   void anExpressionOverAggregatesIsAnExpressionMeasure() {
      var r = run("SELECT a.region, SUM(a.sales) / COUNT(a.id) AS avg_sale FROM T a GROUP BY a.region",
                  "REGION", "AVG_SALE");

      WorksheetStructure.AggMeasure measure = r.sqlAggregate().getAggregates().get(0);
      assertNull(measure.getFormula());
      assertNull(measure.getField());
      assertEquals("AVG_SALE", measure.getColumn());
      assertTrue(measure.getExpression().contains("/"), measure.getExpression());
      assertEquals("full", r.sqlAggregateCoverage());
   }

   @Test
   void aWindowFunctionMakesCoveragePartial() {
      var r = run("SELECT a.region, SUM(a.sales) OVER (PARTITION BY a.region) AS s FROM T a",
                  "REGION", "S");

      assertEquals("partial", r.sqlAggregateCoverage());
   }

   @Test
   void aWindowFunctionWithoutASpaceBeforeTheParenMakesCoveragePartial() {
      var r = run("SELECT a.region, SUM(a.sales) OVER(PARTITION BY a.region) AS s FROM T a",
                  "REGION", "S");

      assertEquals("partial", r.sqlAggregateCoverage());
   }

   @Test
   void aNamedWindowClauseIsUnavailableBecauseTheParserRejectsIt() {
      // the StyleBI parser does not accept a WINDOW clause (parseSubquery returns null); the
      // OVER w item itself is still recognized as a window function (see the detection test)
      var r = run("SELECT a.region, SUM(a.sales) OVER w AS s FROM T a WINDOW w AS (PARTITION BY a.region)",
                  "REGION", "S");

      assertEquals("unavailable", r.sqlAggregateCoverage());
      assertNull(r.sqlAggregate());
   }

   @Test
   void windowDetectionIsCaseInsensitiveAndIgnoresQuotesAndLongerWords() {
      assertTrue(MetadataApiService.isSqlWindowItem("SUM(x) over (PARTITION BY y)"));
      assertTrue(MetadataApiService.isSqlWindowItem("SUM(x) OVER(PARTITION BY y)"));
      assertTrue(MetadataApiService.isSqlWindowItem("SUM(x) Over w"));
      assertFalse(MetadataApiService.isSqlWindowItem("SUM(a.overall)"));
      assertFalse(MetadataApiService.isSqlWindowItem("MAX(CASE WHEN a.k = ' over (' THEN 1 END)"));
      assertFalse(MetadataApiService.isSqlWindowItem("SUM(\"over (x\")"));
      assertFalse(MetadataApiService.isSqlWindowItem("a.over_flag"));
   }

   @Test
   void aStarSelectionMakesCoveragePartial() {
      assertEquals("partial", run("SELECT * FROM T").sqlAggregateCoverage());
      assertEquals("partial", run("SELECT a.* FROM T a").sqlAggregateCoverage());
   }

   @Test
   void rollupIsUnavailable() {
      var r = run("SELECT a.region, SUM(a.sales) AS s FROM T a GROUP BY ROLLUP(a.region)", "REGION", "S");

      assertEquals("unavailable", r.sqlAggregateCoverage());
      assertNull(r.sqlAggregate());
   }

   @Test
   void unparsableSqlIsUnavailable() {
      var r = run("WITH t AS (SELECT 1 AS x FROM D) SELECT x FROM t WHERE x = 1", "X");

      assertEquals("unavailable", r.sqlAggregateCoverage());
      assertNull(r.sqlAggregate());
   }

   @Test
   void distinctGroupsByEveryOutputColumn() {
      var r = run("SELECT DISTINCT a.region, a.year FROM T a", "REGION", "YEAR");

      assertEquals("full", r.sqlAggregateCoverage());
      assertEquals(List.of("REGION", "YEAR"), groupFields(r.sqlAggregate()));
      assertTrue(r.sqlAggregate().getAggregates().isEmpty());
   }

   @Test
   void anAggregateWithoutGroupByIsAWholeTableAggregate() {
      var r = run("SELECT SUM(a.sales) AS total FROM T a", "TOTAL");

      assertEquals("full", r.sqlAggregateCoverage());
      assertTrue(r.sqlAggregate().getGroupBy().isEmpty());
      assertEquals(AggregateFormula.SUM.getName(), r.sqlAggregate().getAggregates().get(0).getFormula());
      assertEquals("TOTAL", r.sqlAggregate().getAggregates().get(0).getColumn());
   }

   @Test
   void aDetailQueryHasNoSqlAggregateButFullCoverage() {
      var r = run("SELECT a.x FROM T a", "X");

      assertEquals("full", r.sqlAggregateCoverage());
      assertNull(r.sqlAggregate());
   }

   @Test
   void groupingByANonProjectedColumnIsPartial() {
      var r = run("SELECT SUM(a.sales) AS total FROM T a GROUP BY a.region", "TOTAL");

      assertEquals("partial", r.sqlAggregateCoverage());
      assertTrue(groupFields(r.sqlAggregate()).stream().anyMatch("a.region"::equalsIgnoreCase),
                 groupFields(r.sqlAggregate()).toString());
   }

   @Test
   void withoutWorksheetColumnsNamesFallBackToTheAliasAndCoverageIsPartial() {
      var r = run("SELECT a.region AS reg, SUM(a.sales) AS total FROM T a GROUP BY a.region");

      assertEquals("partial", r.sqlAggregateCoverage());
      assertTrue("reg".equalsIgnoreCase(groupFields(r.sqlAggregate()).get(0)));
      assertTrue("total".equalsIgnoreCase(r.sqlAggregate().getAggregates().get(0).getColumn()));
   }

   @Test
   void aReorderedWorksheetColumnListMapsByName() {
      var r = run("SELECT a.region, SUM(a.sales) AS total FROM T a GROUP BY a.region", "TOTAL", "REGION");

      assertEquals("full", r.sqlAggregateCoverage());
      assertEquals(List.of("REGION"), groupFields(r.sqlAggregate()));
      assertEquals("TOTAL", r.sqlAggregate().getAggregates().get(0).getColumn());
   }

   @Test
   void aNameMatchWinsEvenWhenTheColumnCountsDiffer() {
      var r = run("SELECT a.region AS Reg, COUNT(a.id) AS cnt FROM T a GROUP BY a.region",
                  "CNT", "EXTRA_EXPR", "REG");

      assertEquals("full", r.sqlAggregateCoverage());
      assertEquals(List.of("REG"), groupFields(r.sqlAggregate()));
      assertEquals("CNT", r.sqlAggregate().getAggregates().get(0).getColumn());
   }

   @Test
   void noNameMatchWithDifferentColumnCountsIsPartial() {
      var r = run("SELECT a.region, SUM(a.sales) AS total FROM T a GROUP BY a.region",
                  "REGION", "TOTAL_SALES", "EXTRA_EXPR");

      assertEquals("partial", r.sqlAggregateCoverage());
      assertEquals(List.of("REGION"), groupFields(r.sqlAggregate()));
      assertTrue("total".equalsIgnoreCase(r.sqlAggregate().getAggregates().get(0).getColumn()));
   }

   @Test
   void aPositionalColumnAlreadyTakenByNameIsNotReusedAndCoverageIsPartial() {
      // item 1 takes TOTAL by name, so item 0 must not also take TOTAL by position
      var r = run("SELECT a.region, SUM(a.sales) AS total FROM T a GROUP BY a.region", "TOTAL", "R2");

      assertEquals("partial", r.sqlAggregateCoverage());
      assertEquals("TOTAL", r.sqlAggregate().getAggregates().get(0).getColumn());
      assertEquals(1, groupFields(r.sqlAggregate()).size());
      assertTrue("region".equalsIgnoreCase(groupFields(r.sqlAggregate()).get(0)),
                 groupFields(r.sqlAggregate()).toString());
   }

   @Test
   void aDetailQueryWithoutWorksheetColumnsStaysNullAndFull() {
      var r = run("SELECT a.x FROM T a");

      assertEquals("full", r.sqlAggregateCoverage());
      assertNull(r.sqlAggregate());
   }

   @Test
   void aConditionsWalkFailureKeepsTheAggregate() {
      WorksheetStructure.AggregateSummary summary = new WorksheetStructure.AggregateSummary();
      MetadataApiService.SqlParse parse = MetadataApiService.sqlParse(
         new MetadataApiService.SqlAggregate(summary, "full"), "t",
         () -> { throw new IllegalStateException("walk failed"); });

      assertEquals("unavailable", parse.conditionsCoverage());
      assertSame(summary, parse.sqlAggregate());
      assertEquals("full", parse.sqlAggregateCoverage());
   }

   @Test
   void anUnqualifiedGroupByMatchesAQualifiedSelectItem() {
      var r = run("SELECT a.region, SUM(a.sales) AS total FROM T a GROUP BY region", "REGION", "TOTAL");

      assertEquals("full", r.sqlAggregateCoverage());
      assertEquals(List.of("REGION"), groupFields(r.sqlAggregate()));
   }

   @Test
   void aQualifiedGroupByDoesNotMatchTheSameNameUnderAnotherQualifier() {
      // a.region is not projected (only b.region is), so the grouping column is unmatched
      var r = run("SELECT b.region, SUM(a.sales) AS total FROM T a, U b WHERE a.id = b.id " +
                  "GROUP BY a.region", "REGION", "TOTAL");

      assertEquals("partial", r.sqlAggregateCoverage());
      assertEquals(1, groupFields(r.sqlAggregate()).size());
      assertTrue("a.region".equalsIgnoreCase(groupFields(r.sqlAggregate()).get(0)),
                 groupFields(r.sqlAggregate()).toString());
   }

   @Test
   void anOutOfRangeOrdinalIsLabelledAndPartial() {
      var r = run("SELECT a.region, SUM(a.sales) AS total FROM T a GROUP BY 5", "REGION", "TOTAL");

      assertEquals("partial", r.sqlAggregateCoverage());
      assertEquals(List.of("GROUP BY ordinal 5"), groupFields(r.sqlAggregate()));
   }

   @Test
   void aNonSqlTableHasNoSqlAggregate() {
      var r = MetadataApiService.extractStructureConditionsAndCoverage(
         new PhysicalBoundTableAssembly(new Worksheet(), "p"));

      assertNull(r.sqlAggregate());
      assertNull(r.sqlAggregateCoverage());
   }

   @Test
   void theStructureTableKeepsTheWorksheetAggregateNextToTheSqlAggregate() {
      SQLBoundTableAssembly t = sqlTable(
         "SELECT a.region, SUM(a.sales) AS total FROM T a GROUP BY a.region", "REGION", "TOTAL");
      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(new ColumnRef(new AttributeRef(null, "REGION"))));
      info.addAggregate(new AggregateRef(new ColumnRef(new AttributeRef(null, "TOTAL")), AggregateFormula.MAX));
      t.setAggregateInfo(info);

      WorksheetStructure.StructureTable table = MetadataApiService.buildStructureTable(t);

      assertEquals("REGION", table.getAggregate().getGroupBy().get(0).getField());
      assertEquals(AggregateFormula.MAX.getName(), table.getAggregate().getAggregates().get(0).getFormula());
      assertEquals("full", table.getSqlAggregateCoverage());
      assertEquals(AggregateFormula.SUM.getName(), table.getSqlAggregate().getAggregates().get(0).getFormula());
      assertEquals("full", table.getConditionsCoverage());
   }
}
