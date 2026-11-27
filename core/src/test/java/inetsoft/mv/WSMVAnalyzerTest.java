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

import inetsoft.mv.trans.UserInfo;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.User;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.ConditionListWrapper;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.Catalog;
import inetsoft.util.IndexedStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * {@link WSMVAnalyzer#analyze()}: which tables of a worksheet get a worksheet MV. Every
 * bound table does, except a cube table and the bound tables beneath a composed table with an
 * SQL formula; an embedded table is only reported. A crosstab bound table keeps its detail data.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                 WSMVAnalyzerTest.ManagerConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class WSMVAnalyzerTest {
   /** The MV manager that the analyzer reads the default cycle from, and its storage. */
   @Configuration
   static class ManagerConfiguration {
      @Bean
      IndexedStorage indexedStorage(BlobStorageManager manager) {
         return new BlobIndexedStorage(manager);
      }

      @Bean
      MVManager mvManager() {
         return new MVManager(mock(Cluster.class));
      }
   }

   private static final String WS_ID = "1^2^__NULL__^mvtest/sales";

   @BeforeEach
   void setUp() {
      savedCycle = manager.getDefaultCycle();
      manager.setDefaultCycle(null);
      ws = new Worksheet();
   }

   @AfterEach
   void tearDown() {
      manager.setDefaultCycle(savedCycle);
   }

   @Test
   void everyBoundTableGetsAnMV() {
      TableAssembly t1 = bound("T1");
      bound("T2");
      add(new MirrorTableAssembly(ws, "M1", t1));

      MVDef[] defs = analyzer(false).analyze();

      assertEquals(List.of("T1", "T2"), boundTables(defs));

      for(MVDef def : defs) {
         assertEquals(WS_ID, def.getWsId());
         assertTrue(def.isWSMV());
         assertNull(def.getCycle());
      }
   }

   @Test
   void theDefaultCycleIsSetOnEveryMV() {
      manager.setDefaultCycle("nightly");
      bound("T1");
      bound("T2");

      for(MVDef def : analyzer(false).analyze()) {
         assertEquals("nightly", def.getCycle());
      }
   }

   @Test
   void bypassIsKeptInTheMV() {
      bound("T1");

      assertTrue(analyzer(true).analyze()[0].getMetaData().isBypassVPM());
      assertFalse(analyzer(false).analyze()[0].getMetaData().isBypassVPM());
   }

   @Test
   void aCubeTableGetsNoMV() {
      bound("T1");
      add(new CubeTableAssembly(ws, "Cube1"));
      WSMVAnalyzer analyzer = analyzer(false);

      MVDef[] defs = analyzer.analyze();

      assertEquals(List.of("T1"), boundTables(defs));
      assertFalse(analyzer.getDescriptor().isEmptyFault());
      assertInfo(analyzer, "Cube1", Catalog.getCatalog().getString("mv.vs.cube", "mvtest/sales"));
   }

   @Test
   void anEmbeddedTableIsReportedAndTheOtherTablesKeepTheirMV() {
      bound("T1");
      add(new EmbeddedTableAssembly(ws, "Embedded1"));
      WSMVAnalyzer analyzer = analyzer(false);

      MVDef[] defs = analyzer.analyze();

      assertEquals(List.of("T1"), boundTables(defs));
      assertFalse(analyzer.getDescriptor().isEmptyFault());
      assertInfo(analyzer, "Embedded1",
                 Catalog.getCatalog().getString("mv.ws.embed.table", "Embedded1"));
   }

   @Test
   void anSQLFormulaRemovesEveryBoundTableBeneathIt() {
      TableAssembly t1 = bound("T1");
      TableAssembly t2 = bound("T2");
      bound("T3");
      // the formula is on a mirror of a join: both bound tables of the join are beneath it
      TableAssembly join = add(join("J1", t1, t2));
      MirrorTableAssembly mirror = add(new MirrorTableAssembly(ws, "M1", join));
      addFormula(mirror, true);
      WSMVAnalyzer analyzer = analyzer(false);

      MVDef[] defs = analyzer.analyze();

      assertEquals(List.of("T3"), boundTables(defs));
      assertFalse(analyzer.getDescriptor().isEmptyFault());
      assertInfo(analyzer, "T1",
                 Catalog.getCatalog().getString("mv.ws.sql.formula", "T1", "M1"));
      assertInfo(analyzer, "T2",
                 Catalog.getCatalog().getString("mv.ws.sql.formula", "T2", "M1"));
   }

   @Test
   void aScriptFormulaKeepsTheMV() {
      TableAssembly t1 = bound("T1");
      MirrorTableAssembly mirror = add(new MirrorTableAssembly(ws, "M1", t1));
      addFormula(mirror, false);
      WSMVAnalyzer analyzer = analyzer(false);

      assertEquals(List.of("T1"), boundTables(analyzer.analyze()));
      assertTrue(analyzer.getDescriptor().isEmptyFault());
      assertTrue(analyzer.getDescriptor().getUserInfo().isEmpty());
   }

   @Test
   void aCrosstabTableKeepsItsDetailData() {
      // the analyzer works on its own copy: the MV holds the detail rows, the caller's
      // worksheet is left as it was
      PhysicalBoundTableAssembly t1 = crosstab("T1");
      ColumnSelection pubBefore = t1.getColumnSelection(true);
      int privateCount = t1.getColumnSelection(false).getAttributeCount();

      MVDef[] defs = analyzer(false).analyze();

      assertEquals(List.of("T1"), boundTables(defs));
      assertTrue(t1.isCrosstab());
      assertFalse(t1.getAggregateInfo().isEmpty());
      assertEquals(1, size(t1.getPreConditionList()));
      assertEquals(1, size(t1.getPostConditionList()));
      assertEquals(1, size(t1.getRankingConditionList()));
      assertSame(pubBefore, t1.getColumnSelection(true));
      assertEquals(privateCount, t1.getColumnSelection(false).getAttributeCount());

      TableAssembly wrapped = (TableAssembly) defs[0].getWorksheet().getAssembly("T1");
      assertTrue(wrapped.getAggregateInfo().isEmpty());
      assertEquals(0, size(wrapped.getPreConditionList()));
   }

   @Test
   void theClearedCrosstabColumnsAreRemovedColumns() {
      crosstab("T1");

      List<String> removed = removedColumns(analyzer(false).analyze()[0]);

      assertEquals(List.of("PivotA", "PivotB"), removed);
   }

   @Test
   void twoIdentitiesAnalysedInARowGetTheSameDef() {
      crosstab("T1");

      MVDef first = analyzer(false).analyze()[0];
      MVDef second = analyzer(false).analyze()[0];

      assertEquals(columnNames(first), columnNames(second));
      assertEquals(removedColumns(first), removedColumns(second));
      assertEquals(List.of("PivotA", "PivotB"), removedColumns(second));
   }

   /** A crosstab whose public (pivoted) columns differ from its detail columns. */
   private PhysicalBoundTableAssembly crosstab(String name) {
      PhysicalBoundTableAssembly t1 = bound(name);
      AggregateInfo crosstab = new AggregateInfo();
      // a crosstab has a row and a column group
      crosstab.addGroup(new GroupRef(column(t1, "Region")));
      crosstab.addGroup(new GroupRef(column(t1, "State")));
      crosstab.addAggregate(new AggregateRef(column(t1, "Sales"), AggregateFormula.SUM));
      crosstab.setCrosstab(true);
      t1.setAggregateInfo(crosstab);
      t1.setPreConditionList(condition(t1));
      t1.setPostConditionList(condition(t1));
      t1.setRankingConditionList(condition(t1));
      ColumnSelection pub = new ColumnSelection();
      pub.addAttribute(typed(new AttributeRef(name, "PivotA"), XSchema.DOUBLE));
      pub.addAttribute(typed(new AttributeRef(name, "PivotB"), XSchema.DOUBLE));
      pub.addAttribute(column(t1, "Region"));
      t1.setColumnSelection(pub, true);
      assertTrue(t1.isCrosstab());
      return t1;
   }

   private static List<String> removedColumns(MVDef def) {
      List<String> names = new ArrayList<>();

      for(MVColumn col : def.getContainer().rcolumns) {
         names.add(col.getName());
      }

      return names;
   }

   private static List<String> columnNames(MVDef def) {
      List<String> names = new ArrayList<>();

      for(MVColumn col : def.getColumns()) {
         names.add(col.getName());
      }

      return names;
   }

   // ---------------------------------------------------------------- helpers

   private WSMVAnalyzer analyzer(boolean bypass) {
      User user = new User(new IdentityID("admin", Organization.getDefaultOrganizationID()));
      return new WSMVAnalyzer(WS_ID, ws, user, bypass);
   }

   private static List<String> boundTables(MVDef[] defs) {
      List<String> names = new ArrayList<>();

      for(MVDef def : defs) {
         names.add(def.getBoundTable());
      }

      return names;
   }

   private static void assertInfo(WSMVAnalyzer analyzer, String table, String message) {
      List<UserInfo> infos = analyzer.getDescriptor().getUserInfo();

      for(UserInfo info : infos) {
         if(table.equals(info.getBoundTable())) {
            assertEquals("mvtest/sales", info.getSheetName());
            assertEquals(message, info.getMessage());
            return;
         }
      }

      fail("no user info for " + table + ": " + infos);
   }

   private PhysicalBoundTableAssembly bound(String name) {
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, name);
      table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "mvtest", "SALES"));
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(typed(new AttributeRef(name, "Region"), XSchema.STRING));
      cols.addAttribute(typed(new AttributeRef(name, "State"), XSchema.STRING));
      cols.addAttribute(typed(new AttributeRef(name, "Sales"), XSchema.DOUBLE));
      table.setColumnSelection(cols);
      return add(table);
   }

   /** An inner join of the two tables on Region; a join column has its table as entity. */
   private TableAssembly join(String name, TableAssembly left, TableAssembly right) {
      TableAssemblyOperator op = new TableAssemblyOperator(
         left.getName(), column(left, "Region"), right.getName(), column(right, "Region"),
         TableAssemblyOperator.INNER_JOIN);
      RelationalJoinTableAssembly table = new RelationalJoinTableAssembly(
         ws, name, new TableAssembly[] { left, right }, new TableAssemblyOperator[] { op });
      ColumnSelection cols = new ColumnSelection();

      for(TableAssembly sub : new TableAssembly[] { left, right }) {
         ColumnSelection subcols = sub.getColumnSelection(true);

         for(int i = 0; i < subcols.getAttributeCount(); i++) {
            ColumnRef col = (ColumnRef) subcols.getAttribute(i);
            cols.addAttribute(typed(new AttributeRef(sub.getName(), col.getAttribute()),
                                    col.getDataType()));
         }
      }

      table.setColumnSelection(cols);
      return table;
   }

   /** An expression column on the table: SQL or script. */
   private static void addFormula(TableAssembly table, boolean sql) {
      ExpressionRef expr = new ExpressionRef(null, "F1");
      expr.setExpression("1 + 1");
      ColumnRef col = new ColumnRef(expr);
      col.setDataType(XSchema.INTEGER);
      col.setSQL(sql);
      ColumnSelection pub = table.getColumnSelection(true);
      pub.addAttribute(col);
      table.setColumnSelection(pub, true);
   }

   private static ConditionList condition(TableAssembly table) {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.addValue("East");
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(column(table, "Region"), cond, 0));
      return list;
   }

   private static int size(ConditionListWrapper wrapper) {
      return wrapper == null || wrapper.getConditionList() == null ?
         0 : wrapper.getConditionList().getSize();
   }

   private static ColumnRef column(TableAssembly table, String name) {
      ColumnRef col = (ColumnRef) table.getColumnSelection(false).getAttribute(name);
      assertNotNull(col, table.getName() + " has no column " + name);
      return col;
   }

   private static ColumnRef typed(AttributeRef ref, String type) {
      ColumnRef col = new ColumnRef(ref);
      col.setDataType(type);
      return col;
   }

   private <T extends TableAssembly> T add(T table) {
      ws.addAssembly(table);
      return table;
   }

   @Autowired
   private MVManager manager;
   private String savedCycle;
   private Worksheet ws;
}
