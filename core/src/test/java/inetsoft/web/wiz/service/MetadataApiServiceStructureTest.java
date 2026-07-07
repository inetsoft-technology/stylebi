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
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.web.wiz.model.WorksheetStructure;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@code getWorksheetStructure} extraction helpers on
 * {@link MetadataApiService}.
 *
 * <p>{@code getWorksheetStructure} itself is a thin wrapper — resolve the {@link AssetEntry},
 * fetch the {@link Worksheet} via {@code assetRepository.getSheet}, loop assemblies, catch
 * per-assembly — that mirrors the already-covered {@code getWorksheetMetaData} shape 1:1. All of
 * the new logic lives in the package-private static extraction helpers below, so (per the task
 * brief's guidance) this suite drives those helpers directly with hand-built
 * {@code ConditionList}/{@code AggregateInfo}/{@code SourceInfo}/table-assembly objects rather
 * than standing up a full {@code AssetRepository}-backed round trip.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MetadataApiServiceStructureTest {

   /**
    * Builds a {@link PhysicalBoundTableAssembly} with a private {@link ColumnSelection} holding
    * a {@link ColumnRef} per name. Deliberately avoids {@code EmbeddedTableAssembly} /
    * {@code TestWorksheets.tableWithColumns}: constructing an embedded table eagerly creates an
    * {@code XEmbeddedTable} backed by {@code XSwappableTable}, which needs an {@code XSwapper}
    * Spring bean this lightweight test context doesn't provide. A physical bound table needs no
    * such runtime machinery and is a more representative "real" worksheet table besides.
    */
   private static PhysicalBoundTableAssembly physicalTable(Worksheet ws, String name, String... cols) {
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, name);
      ColumnSelection cs = new ColumnSelection();

      for(String col : cols) {
         cs.addAttribute(new ColumnRef(new AttributeRef(null, col)));
      }

      table.setColumnSelection(cs, false);
      return table;
   }

   // ---- structureTableType ----

   @Test
   void classifiesPhysicalBoundTable() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly t = new PhysicalBoundTableAssembly(ws, "accounts");
      assertEquals("physical table", MetadataApiService.structureTableType(t));
   }

   @Test
   void classifiesSqlBoundTable() {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t = new SQLBoundTableAssembly(ws, "sqlTable");
      assertEquals("sql query table", MetadataApiService.structureTableType(t));
   }

   @Test
   void classifiesRelationalJoinTable() {
      Worksheet ws = new Worksheet();
      TableAssembly left = physicalTable(ws, "t1", "id");
      TableAssembly right = physicalTable(ws, "t2", "id");
      RelationalJoinTableAssembly join = new RelationalJoinTableAssembly(
         ws, "joined", new TableAssembly[] { left, right }, new TableAssemblyOperator[0]);
      assertEquals("relational join table", MetadataApiService.structureTableType(join));
   }

   @Test
   void classifiesMirrorTable() {
      Worksheet ws = new Worksheet();
      TableAssembly base = physicalTable(ws, "base", "id");
      ws.addAssembly(base);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "mirrored", base);
      assertEquals("mirror table", MetadataApiService.structureTableType(mirror));
   }

   @Test
   void fallsBackToSimpleClassNameForUnrecognizedType() {
      // UnpivotTableAssembly isn't one of the four classified types, so it exercises the
      // getClass().getSimpleName() fallback branch.
      Worksheet ws = new Worksheet();
      TableAssembly base = physicalTable(ws, "base", "id", "h1", "h2");
      ws.addAssembly(base);
      UnpivotTableAssembly t = new UnpivotTableAssembly(ws, "unpivoted", base);
      assertEquals("UnpivotTableAssembly", MetadataApiService.structureTableType(t));
   }

   // ---- extractStructureColumns ----

   @Test
   void extractsColumnsFromColumnSelection() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly t = physicalTable(ws, "accounts", "industry", "annual_revenue");

      var columns = MetadataApiService.extractStructureColumns(t);

      assertEquals(2, columns.size());
      assertEquals("industry", columns.get(0).getName());
      assertEquals("annual_revenue", columns.get(1).getName());
   }

   @Test
   void mapsAliasTypeRefTypeAndExpressionForColumns() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly t = new PhysicalBoundTableAssembly(ws, "accounts");
      ColumnSelection cs = new ColumnSelection();

      // Plain column: attribute-backed ColumnRef with an alias, explicit data type, and a
      // non-default ref type (MEASURE).
      AttributeRef attr = new AttributeRef(null, "annual_revenue");
      attr.setRefType(DataRef.MEASURE);
      ColumnRef plainColumn = new ColumnRef(attr);
      plainColumn.setAlias("Annual Revenue");
      plainColumn.setDataType(XSchema.DOUBLE);
      cs.addAttribute(plainColumn);

      // Expression column: ColumnRef wrapping an ExpressionRef, per the isExpression() &&
      // getDataRef() instanceof ExpressionRef branch in createStructureColumn.
      ExpressionRef expressionRef = new ExpressionRef(null, "revenue_plus_tax");
      expressionRef.setExpression("field['a'] + field['b']");
      ColumnRef expressionColumn = new ColumnRef(expressionRef);
      cs.addAttribute(expressionColumn);

      t.setColumnSelection(cs, false);

      var columns = MetadataApiService.extractStructureColumns(t);

      assertEquals(2, columns.size());

      WorksheetStructure.Column plain = columns.get(0);
      assertEquals("annual_revenue", plain.getName());
      assertEquals("Annual Revenue", plain.getAlias());
      assertEquals(XSchema.DOUBLE, plain.getType());
      assertEquals(DataRef.MEASURE, plain.getRefType());
      assertNull(plain.getExpression(), "non-expression column should not carry an expression");

      WorksheetStructure.Column expression = columns.get(1);
      assertEquals("field['a'] + field['b']", expression.getExpression());
   }

   // ---- extractStructureSource ----

   @Test
   void extractsSourceFromBoundTable() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly t = new PhysicalBoundTableAssembly(ws, "accounts");
      t.setSourceInfo(new SourceInfo(XSourceInfo.PHYSICAL_TABLE, "suitecrm", "accounts"));

      WorksheetStructure.SourceRef source = MetadataApiService.extractStructureSource(t);

      assertNotNull(source);
      assertEquals("suitecrm", source.getDatasource());
      assertEquals("accounts", source.getTable());
      assertEquals(XSourceInfo.PHYSICAL_TABLE, source.getSourceType());
      assertNull(source.getSqlText(), "non-SQL bound table should not carry SQL text");
   }

   @Test
   void extractsSqlTextFromSqlBoundTable() {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t = new SQLBoundTableAssembly(ws, "sqlTable");
      t.setSourceInfo(new SourceInfo(XSourceInfo.PHYSICAL_TABLE, "suitecrm", "suitecrm"));

      UniformSQL sql = new UniformSQL();
      sql.setParseSQL(false);
      sql.setSQLString("SELECT * FROM accounts", false);

      JDBCQuery query = new JDBCQuery();
      query.setSQLDefinition(sql);

      inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo info =
         (inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo) t.getInfo();
      info.setQuery(query);

      WorksheetStructure.SourceRef source = MetadataApiService.extractStructureSource(t);

      assertNotNull(source);
      assertEquals("SELECT * FROM accounts", source.getSqlText());
   }

   @Test
   void returnsNullSourceForComposedTable() {
      Worksheet ws = new Worksheet();
      TableAssembly base = physicalTable(ws, "base", "id");
      ws.addAssembly(base);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "mirrored", base);

      assertNull(MetadataApiService.extractStructureSource(mirror));
   }

   // ---- extractStructureBaseTables / extractStructureJoins ----

   @Test
   void extractsBaseTablesAndJoinEdgeFromJoinTable() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly left = physicalTable(ws, "t1", "id", "name");
      PhysicalBoundTableAssembly right = physicalTable(ws, "t2", "id", "value");

      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setLeftTable("t1");
      op.setRightTable("t2");
      op.setLeftAttribute(new AttributeRef(null, "id"));
      op.setRightAttribute(new AttributeRef(null, "id"));
      op.setOperation(TableAssemblyOperator.INNER_JOIN);

      TableAssemblyOperator top = new TableAssemblyOperator();
      top.addOperator(op);

      RelationalJoinTableAssembly join = new RelationalJoinTableAssembly(
         ws, "joined", new TableAssembly[] { left, right }, new TableAssemblyOperator[] { top });

      var baseTables = MetadataApiService.extractStructureBaseTables(join);
      assertTrue(baseTables.contains("t1"));
      assertTrue(baseTables.contains("t2"));

      var joins = MetadataApiService.extractStructureJoins(join);
      assertEquals(1, joins.size());
      WorksheetStructure.JoinEdge edge = joins.get(0);
      assertEquals("t1", edge.getLeftTable());
      assertEquals("t2", edge.getRightTable());
      assertEquals("id", edge.getLeftColumn());
      assertEquals("id", edge.getRightColumn());
      assertEquals("INNER_JOIN", edge.getJoinType());
   }

   @Test
   void baseTablesAndJoinsAreEmptyForNonComposedTable() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly t = new PhysicalBoundTableAssembly(ws, "accounts");

      assertTrue(MetadataApiService.extractStructureBaseTables(t).isEmpty());
      assertTrue(MetadataApiService.extractStructureJoins(t).isEmpty());
   }

   // ---- collectStructureConditions ----

   @Test
   void collectsSingleConditionLeaf() {
      ConditionList list = new ConditionList();
      Condition cond = new Condition();
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue(0);
      list.append(new ConditionItem(new AttributeRef(null, "deleted"), cond, 0));

      var out = new java.util.ArrayList<WorksheetStructure.ConditionLeaf>();
      MetadataApiService.collectStructureConditions(list, "pre", out);

      assertEquals(1, out.size());
      WorksheetStructure.ConditionLeaf leaf = out.get(0);
      assertEquals("deleted", leaf.getField());
      assertEquals("EQUAL_TO", leaf.getOperation());
      assertEquals("pre", leaf.getPhase());
      assertFalse(leaf.isNegated());
      assertEquals("0", leaf.getValues().get(0));
      assertNull(leaf.getJunction(), "first leaf in a phase has no preceding junction");
   }

   @Test
   void collectsSecondLeafJunctionAndNegation() {
      ConditionList list = new ConditionList();

      Condition c1 = new Condition();
      c1.setOperation(XCondition.EQUAL_TO);
      c1.addValue(0);
      list.append(new ConditionItem(new AttributeRef(null, "deleted"), c1, 0));

      list.append(new JunctionOperator(JunctionOperator.AND, 0));

      Condition c2 = new Condition();
      c2.setOperation(XCondition.ONE_OF);
      c2.setNegated(true);
      c2.addValue("Closed Lost");
      list.append(new ConditionItem(new AttributeRef(null, "sales_stage"), c2, 0));

      var out = new java.util.ArrayList<WorksheetStructure.ConditionLeaf>();
      MetadataApiService.collectStructureConditions(list, "pre", out);

      assertEquals(2, out.size());
      WorksheetStructure.ConditionLeaf second = out.get(1);
      assertEquals("sales_stage", second.getField());
      assertEquals("ONE_OF", second.getOperation());
      assertEquals("AND", second.getJunction());
      assertTrue(second.isNegated());
      assertEquals("Closed Lost", second.getValues().get(0));
   }

   @Test
   void collectStructureConditionsNoOpsOnNullOrEmptyWrapper() {
      var out = new java.util.ArrayList<WorksheetStructure.ConditionLeaf>();
      MetadataApiService.collectStructureConditions(null, "pre", out);
      MetadataApiService.collectStructureConditions(new ConditionList(), "pre", out);
      assertTrue(out.isEmpty());
   }

   // ---- extractStructureAggregate ----

   @Test
   void extractsGroupByAndAggregate() {
      ColumnRef groupCol = new ColumnRef(new AttributeRef(null, "industry"));
      ColumnRef sumCol = new ColumnRef(new AttributeRef(null, "annual_revenue"));

      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(groupCol));
      info.addAggregate(new AggregateRef(sumCol, AggregateFormula.SUM));

      WorksheetStructure.AggregateSummary summary = MetadataApiService.extractStructureAggregate(info);

      assertNotNull(summary);
      assertEquals(1, summary.getGroupBy().size());
      assertEquals("industry", summary.getGroupBy().get(0).getField());
      assertEquals(1, summary.getAggregates().size());
      assertEquals("annual_revenue", summary.getAggregates().get(0).getField());
      assertTrue("SUM".equalsIgnoreCase(summary.getAggregates().get(0).getFormula()));
   }

   @Test
   void returnsNullForEmptyOrNullAggregateInfo() {
      assertNull(MetadataApiService.extractStructureAggregate(null));
      assertNull(MetadataApiService.extractStructureAggregate(new AggregateInfo()));
   }
}
