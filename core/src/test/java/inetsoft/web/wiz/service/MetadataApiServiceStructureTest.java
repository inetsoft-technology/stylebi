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

   @Test
   void mapsDescriptionForColumns() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly t = new PhysicalBoundTableAssembly(ws, "accounts");
      ColumnSelection cs = new ColumnSelection();

      // A column carrying a StyleBI column description (the same source /ws/worksheet-model's
      // createColumnMeta already reads via columnRef.getDescription()).
      ColumnRef described = new ColumnRef(new AttributeRef(null, "industry"));
      described.setDescription("The industry sector of the account");
      cs.addAttribute(described);

      // A column with no description — its structure column description must stay null.
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "annual_revenue")));

      t.setColumnSelection(cs, false);

      var columns = MetadataApiService.extractStructureColumns(t);

      assertEquals(2, columns.size());
      assertEquals("The industry sector of the account", columns.get(0).getDescription());
      assertNull(columns.get(1).getDescription(), "column without a description should not carry one");
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

   // ---- parseGeneration ----

   @Test
   void parsesTrailingGenerationSuffix() {
      assertEquals(2, MetadataApiService.parseGeneration("sales_2"));
   }

   @Test
   void parseGenerationReturnsNullWhenNoSuffix() {
      assertNull(MetadataApiService.parseGeneration("sales"));
   }

   @Test
   void parseGenerationTakesOnlyTrailingNumber() {
      // A logical table name can itself contain underscores + digits mid-name; only the final
      // `_<n>` is the generation tag applied by applyGenerationToTables.
      assertEquals(3, MetadataApiService.parseGeneration("orders_1_3"));
   }

   @Test
   void parseGenerationReturnsNullForNullOrBlank() {
      assertNull(MetadataApiService.parseGeneration(null));
      assertNull(MetadataApiService.parseGeneration(""));
   }

   // ---- matchesGeneration ----

   @Test
   void matchesGenerationAllowsEverythingWhenGenerationNull() {
      // generation == null is the "no filter" path (MCP / legacy): every table is kept, whether or
      // not it carries a suffix.
      assertTrue(MetadataApiService.matchesGeneration("sales", null));
      assertTrue(MetadataApiService.matchesGeneration("sales_2", null));
   }

   @Test
   void matchesGenerationKeepsSameGeneration() {
      assertTrue(MetadataApiService.matchesGeneration("sales_2", 2));
   }

   @Test
   void matchesGenerationRejectsOtherGeneration() {
      assertFalse(MetadataApiService.matchesGeneration("sales_1", 2));
   }

   @Test
   void matchesGenerationRejectsUnsuffixedWhenGenerationSet() {
      // A legacy/unsuffixed table has no generation, so a specific-generation request excludes it.
      assertFalse(MetadataApiService.matchesGeneration("sales", 2));
   }

   // ---- resolvePrimaryTable ----

   private static WorksheetStructure.StructureTable structureTable(String name) {
      WorksheetStructure.StructureTable t = new WorksheetStructure.StructureTable();
      t.setName(name);
      return t;
   }

   @Test
   void resolvePrimaryTableKeepsPrimaryWhenStillPresent() {
      var tables = java.util.List.of(structureTable("a_2"), structureTable("b_2"));
      assertEquals("b_2", MetadataApiService.resolvePrimaryTable("b_2", tables));
   }

   @Test
   void resolvePrimaryTableFallsBackToLastWhenPrimaryFilteredOut() {
      // primary "b_1" belongs to a different generation and was filtered out; fall back to the last
      // kept table (the terminal/binding table by convention).
      var tables = java.util.List.of(structureTable("a_2"), structureTable("c_2"));
      assertEquals("c_2", MetadataApiService.resolvePrimaryTable("b_1", tables));
   }

   @Test
   void resolvePrimaryTableKeepsOriginalWhenNoTablesLeft() {
      assertEquals("b_1",
                   MetadataApiService.resolvePrimaryTable("b_1", java.util.List.of()));
   }

   @Test
   void resolvePrimaryTableHandlesNullPrimary() {
      assertNull(MetadataApiService.resolvePrimaryTable(null, java.util.List.of(structureTable("a_2"))));
   }

   // ---- collectUpstreamRefs ----

   @Test
   void collectUpstreamRefsMergesBaseTablesAndJoinTables() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly left = physicalTable(ws, "t1", "id");
      PhysicalBoundTableAssembly right = physicalTable(ws, "t2", "id");

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

      var refs = MetadataApiService.collectUpstreamRefs(join);
      assertTrue(refs.contains("t1"), "base/join left table should be an upstream ref");
      assertTrue(refs.contains("t2"), "base/join right table should be an upstream ref");
   }

   @Test
   void collectUpstreamRefsEmptyForPhysicalTable() {
      Worksheet ws = new Worksheet();
      assertTrue(MetadataApiService.collectUpstreamRefs(physicalTable(ws, "accounts", "id")).isEmpty(),
                 "a physical bound table depends on no other worksheet table");
   }

   // ---- expandToUpstreamClosure ----

   @Test
   void closurePullsInReusedUpstreamFromEarlierGeneration() {
      // sales_2 (this generation) reuses base_1 (a prior generation's physical table). base_1 must be
      // pulled into the result even though it does not belong to generation 2.
      var order = java.util.List.of("base_1", "sales_2");
      var upstream = java.util.Map.of(
         "base_1", java.util.Set.<String>of(),
         "sales_2", java.util.Set.of("base_1"));
      var seed = java.util.Set.of("sales_2");

      assertEquals(java.util.List.of("base_1", "sales_2"),
                   MetadataApiService.expandToUpstreamClosure(order, upstream, seed));
   }

   @Test
   void closurePullsInMultiLevelUpstreamChain() {
      // n_3 -> m_2 -> k_1: the whole transitive chain is included, not just the direct parent.
      var order = java.util.List.of("k_1", "m_2", "n_3");
      var upstream = java.util.Map.of(
         "k_1", java.util.Set.<String>of(),
         "m_2", java.util.Set.of("k_1"),
         "n_3", java.util.Set.of("m_2"));
      var seed = java.util.Set.of("n_3");

      assertEquals(java.util.List.of("k_1", "m_2", "n_3"),
                   MetadataApiService.expandToUpstreamClosure(order, upstream, seed));
   }

   @Test
   void closureExcludesOtherGenerationTablesNotDependedOn() {
      // unused_1 is another generation's table that nothing in the seed depends on — it stays out.
      var order = java.util.List.of("base_1", "unused_1", "sales_2");
      var upstream = java.util.Map.of(
         "base_1", java.util.Set.<String>of(),
         "unused_1", java.util.Set.<String>of(),
         "sales_2", java.util.Set.of("base_1"));
      var seed = java.util.Set.of("sales_2");

      assertEquals(java.util.List.of("base_1", "sales_2"),
                   MetadataApiService.expandToUpstreamClosure(order, upstream, seed));
   }

   @Test
   void closureOrdersUpstreamBeforeDependent() {
      // Regardless of iteration order in the upstream map, the RESULT follows enumerationOrder, which
      // places a pulled-in upstream table ahead of the dependent that references it.
      var order = java.util.List.of("base_1", "sales_2");
      var upstream = java.util.Map.of(
         "sales_2", java.util.Set.of("base_1"),
         "base_1", java.util.Set.<String>of());
      var seed = java.util.Set.of("sales_2");

      var result = MetadataApiService.expandToUpstreamClosure(order, upstream, seed);
      assertTrue(result.indexOf("base_1") < result.indexOf("sales_2"),
                 "upstream base_1 must precede its dependent sales_2");
   }

   @Test
   void closureSkipsDanglingReferenceToDeletedAssembly() {
      // sales_2 references ghost_1, which no longer exists in the sheet (a deleted orphan). The
      // reference is skipped silently: ghost_1 is absent from the result and no exception is thrown.
      var order = java.util.List.of("sales_2");
      var upstream = java.util.Map.of("sales_2", java.util.Set.of("ghost_1"));
      var seed = java.util.Set.of("sales_2");

      assertEquals(java.util.List.of("sales_2"),
                   MetadataApiService.expandToUpstreamClosure(order, upstream, seed));
   }

   @Test
   void closureKeepsSeedWhenNoUpstream() {
      var order = java.util.List.of("a_2", "b_2");
      var seed = java.util.Set.of("a_2", "b_2");

      assertEquals(java.util.List.of("a_2", "b_2"),
                   MetadataApiService.expandToUpstreamClosure(
                      order, java.util.Map.<String, java.util.Set<String>>of(), seed));
   }

   @Test
   void closureEmptyWhenSeedEmpty() {
      var order = java.util.List.of("a_1", "b_1");
      assertTrue(MetadataApiService.expandToUpstreamClosure(
                    order, java.util.Map.of(), java.util.Set.of()).isEmpty());
   }
}
