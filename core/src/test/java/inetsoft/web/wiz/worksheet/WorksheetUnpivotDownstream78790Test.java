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
package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.*;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionList;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.AggregateSpec;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.GroupSpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78790 item 2 (WBS-139): shrinking an unpivot table's {@code headerColumns} melts the
 * departing source columns, so a dependent table's condition (and the unpivot table's own
 * condition or sort) on such a column used to be dropped silently by the next refresh. The shrink
 * is now refused naming the column and every dependent unless {@code confirmed}; a join key is
 * never confirmable. Refusal is checked through the real {@code apply} path, followed by an
 * unrelated op, so a leftover would be published.
 *
 * <p>The {@code AssetDataCache} bean is mandatory: without it a refresh leaves the unpivot
 * table's own column selection empty, the guard is skipped, and every refusal assertion here
 * would be vacuous.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  WorksheetUnpivotDownstream78790Test.CacheConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetUnpivotDownstream78790Test {
   @Configuration
   static class CacheConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }
   }

   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private Worksheet ws;
   private WorksheetEditService svc;
   private int triggers;

   /** S(name,score,q1,q2,q3), UP = unpivot(S, hcol), DEPU = mirror(UP), Z(pid). */
   private void build(int hcol) throws Exception {
      ws = new Worksheet();
      ws.addAssembly(source(ws, "S"));
      ws.addAssembly(pidTable(ws, "Z"));
      svc = service(ws);
      apply(ed -> ed.addUnpivot("UP", "S", hcol));
      apply(ed -> ed.addMirror("DEPU", "UP"));
   }

   // ---- dependents ---------------------------------------------------------------------

   @Test
   void mirrorFilterOnTheMeltedColumnIsRefusedNamingItAndChangesNothing() throws Exception {
      build(2);
      apply(ed -> ed.addFilter("DEPU", "score", ">", "0"));
      trigger();

      String m = refusal(1, false);
      assertTrue(m.contains("\"S.score\""), m);
      assertTrue(m.contains("'DEPU' (pre-condition)"), m);
      assertTrue(m.contains("confirmed:true"), m);
      trigger();

      assertEquals(2, hcol());
      assertEquals(1, pre("DEPU"), "the filter survives the refusal and a refresh");
   }

   @Test
   void mirrorFilterOnTheMeltedColumnIsAcceptedWhenConfirmedAndTheFilterGoes() throws Exception {
      build(2);
      apply(ed -> ed.addFilter("DEPU", "score", ">", "0"));
      trigger();

      apply(ed -> ed.editUnpivot("UP", 1, true));
      trigger();

      assertEquals(1, hcol());
      assertEquals(0, pre("DEPU"), "the named, accepted loss");
   }

   @Test
   void ownFilterOnTheMeltedColumnIsRefusedNamingTheTableItself() throws Exception {
      build(2);
      apply(ed -> ed.addFilter("UP", "score", ">", "0"));
      trigger();

      String m = refusal(1, false);
      assertTrue(m.contains("table 'UP' itself (pre-condition)"), m);
      trigger();

      assertEquals(2, hcol());
      assertEquals(1, pre("UP"));

      apply(ed -> ed.editUnpivot("UP", 1, true));
      assertEquals(1, hcol());
      assertEquals(0, pre("UP"));
   }

   @Test
   void ownSortOnTheMeltedColumnIsRefused() throws Exception {
      build(2);
      apply(ed -> ed.setSort("UP", "score", "ASC"));
      trigger();

      String m = refusal(1, false);
      assertTrue(m.contains("table 'UP' itself (sort)"), m);
      assertEquals(2, hcol());
   }

   @Test
   void dependentAggregateOnTheMeltedColumnIsRefusedAndAcceptedWhenConfirmed() throws Exception {
      build(2);
      apply(ed -> ed.addMirror("DEPA", "UP"));
      apply(ed -> ed.setGroupAggregate("DEPA", List.of(),
         List.of(new AggregateSpec("score", "SUM", null))));
      trigger();

      String m = refusal(1, false);
      assertTrue(m.contains("'DEPA' (aggregate/group-by on UP.score)"), m);
      assertEquals(2, hcol());

      apply(ed -> ed.editUnpivot("UP", 1, true));
      assertEquals(1, hcol());
   }

   @Test
   void dependentThroughAChainOfMirrorsIsNamed() throws Exception {
      build(2);
      apply(ed -> ed.addMirror("DEPU2", "DEPU"));
      apply(ed -> ed.addFilter("DEPU2", "score", ">", "0"));
      trigger();

      String m = refusal(1, false);
      assertTrue(m.contains("'DEPU2'"), m);
      assertEquals(2, hcol());
   }

   @Test
   void renamedMeltedColumnIsFoundUnderItsNewName() throws Exception {
      build(2);
      apply(ed -> ed.renameColumn("UP", "score", "points"));
      apply(ed -> ed.addFilter("DEPU", "points", ">", "0"));
      trigger();

      String m = refusal(1, false);
      assertTrue(m.contains("\"points\""), m);
      assertTrue(m.contains("'DEPU'"), m);
      assertEquals(2, hcol());
   }

   @Test
   void joinSourceIsResolvedThroughItsEntityQualifiedColumns() throws Exception {
      ws = new Worksheet();
      ws.addAssembly(source(ws, "S"));
      ws.addAssembly(pidTable(ws, "Z"));
      svc = service(ws);
      apply(ed -> ed.addJoin("J", "S", "score", "Z", "pid", "INNER", null, null));
      apply(ed -> ed.addUnpivot("UP", "J", 2));
      apply(ed -> ed.addMirror("DEPU", "UP"));
      apply(ed -> ed.addFilter("DEPU", "score", ">", "0"));
      trigger();

      String m = refusal(1, false);
      assertTrue(m.contains("\"J.score\""), m);
      assertTrue(m.contains("'DEPU'"), m);
      assertEquals(2, hcol());
   }

   // ---- what is not refused ------------------------------------------------------------

   @Test
   void growingIsNeverRefused() throws Exception {
      build(1);
      apply(ed -> ed.addFilter("DEPU", "name", "!=", "zzz"));
      trigger();

      apply(ed -> ed.editUnpivot("UP", 2, false));
      trigger();

      assertEquals(2, hcol());
      assertEquals(1, pre("DEPU"));
   }

   @Test
   void shrinkWhoseDependentsOnlyUseKeptColumnsIsAccepted() throws Exception {
      build(2);
      apply(ed -> ed.addFilter("DEPU", "name", "!=", "zzz"));
      trigger();

      apply(ed -> ed.editUnpivot("UP", 1, false));
      trigger();

      assertEquals(1, hcol());
      assertEquals(1, pre("DEPU"));
   }

   @Test
   void shrinkToZeroWithoutReferencesIsAccepted() throws Exception {
      buildNumeric(2);

      apply(ed -> ed.editUnpivot("UP", 0, false));

      assertEquals(0, hcol());
   }

   @Test
   void shrinkToZeroNamesTheReferencedHeaderColumn() throws Exception {
      buildNumeric(2);
      apply(ed -> ed.addFilter("DEPU", "k2", ">", "0"));
      trigger();

      String m = refusal(0, false);
      assertTrue(m.contains("\"S.k2\""), m);
      assertEquals(2, hcol());
   }

   @Test
   void tableNeverRefreshedHasNoColumnsToCheckAndIsAccepted() throws Exception {
      ws = new Worksheet();
      ws.addAssembly(source(ws, "S"));
      ws.addAssembly(pidTable(ws, "Z"));
      UnpivotTableAssembly up =
         new UnpivotTableAssembly(ws, "UP", (TableAssembly) ws.getAssembly("S"));
      up.setHeaderColumns(2);
      ws.addAssembly(up);
      svc = service(ws);
      assertEquals(0, up.getColumnSelection(false).getAttributeCount(), "sanity");

      apply(ed -> ed.editUnpivot("UP", 1, false));

      assertEquals(1, up.getHeaderColumns());
   }

   // ---- the departing column comes from the source, not from UP's own column order ------

   @Test
   void reorderedColumnsDoNotMisnameTheDepartingColumn() throws Exception {
      build(2);
      apply(ed -> ed.reorderColumns("UP", List.of("score", "name", "Dimension", "Measure")));
      apply(ed -> ed.addFilter("DEPU", "score", ">", "0"));
      trigger();

      String m = refusal(1, false);
      assertTrue(m.contains("\"S.score\""), m);
      assertFalse(m.contains("name"), m);
   }

   @Test
   void shrinkGrowShrinkNamesTheColumnThatIsMeltedTheSecondTime() throws Exception {
      build(2);
      apply(ed -> ed.addFilter("UP", "score", ">", "0"));
      refusal(1, false);
      apply(ed -> ed.editUnpivot("UP", 1, true));
      apply(ed -> ed.editUnpivot("UP", 2, false));
      apply(ed -> ed.addFilter("UP", "score", ">", "0"));
      trigger();

      String m = refusal(1, false);
      assertTrue(m.contains("\"S.score\""), m);
      assertFalse(m.contains("Dimension"), m);
   }

   @Test
   void aliasedAndUnaliasedAggregateSourceColumnsAreResolved() throws Exception {
      ws = new Worksheet();
      ws.addAssembly(source(ws, "S"));
      ws.addAssembly(pidTable(ws, "Z"));
      svc = service(ws);
      apply(ed -> ed.addMirror("SA", "S"));
      apply(ed -> ed.setGroupAggregate("SA", List.of(new GroupSpec("name")),
         List.of(new AggregateSpec("score", "SUM", "tot"), new AggregateSpec("q1", "SUM", null),
                 new AggregateSpec("q2", "SUM", "b"))));
      apply(ed -> ed.addUnpivot("UP", "SA", 3));
      apply(ed -> ed.addMirror("DEPU", "UP"));
      apply(ed -> ed.addFilter("DEPU", "tot", ">", "0"));
      trigger();

      // melts the unaliased q1 only, tot is untouched
      apply(ed -> ed.editUnpivot("UP", 2, false));
      assertEquals(2, hcol());

      String m = refusal(1, false);
      assertTrue(m.contains("\"SA.tot\"") && m.contains("'DEPU'"), m);
      assertEquals(2, hcol());
   }

   @Test
   void unaliasedAggregateHeaderIsResolved() throws Exception {
      ws = new Worksheet();
      ws.addAssembly(source(ws, "S"));
      ws.addAssembly(pidTable(ws, "Z"));
      svc = service(ws);
      apply(ed -> ed.addMirror("SA", "S"));
      apply(ed -> ed.setGroupAggregate("SA", List.of(new GroupSpec("name")),
         List.of(new AggregateSpec("score", "SUM", "tot"), new AggregateSpec("q1", "SUM", null),
                 new AggregateSpec("q2", "SUM", "b"))));
      apply(ed -> ed.addUnpivot("UP", "SA", 3));
      apply(ed -> ed.addMirror("DEPU", "UP"));
      apply(ed -> ed.addFilter("DEPU", "q1", ">", "0"));
      trigger();

      String m = refusal(2, false);
      assertTrue(m.contains("\"SA.q1\"") && m.contains("'DEPU'"), m);
   }

   // ---- hidden source columns: the visible selection is the one index space ------------

   @Test
   void hiddenLeadingColumnShiftsTheDepartingRangeToTheVisibleOnes() throws Exception {
      build0(2, true);
      // visible header = score, q1; shrinking to 1 melts q1
      apply(ed -> ed.addFilter("DEPU", "q1", ">", "0"));
      trigger();

      String m = refusal(1, false);
      assertTrue(m.contains("\"S.q1\""), m);
      assertFalse(m.contains("score"), m);

      PairingException ex = assertThrows(PairingException.class,
         () -> apply(ed -> ed.editUnpivot("UP", 4)));
      assertTrue(ex.getMessage().contains("must be between 0 and 3 (table has 4 columns)"),
         ex.getMessage());
   }

   @Test
   void hiddenLeadingColumnDoesNotRefuseAShrinkThatKeepsTheReferencedColumn() throws Exception {
      build0(2, true);
      apply(ed -> ed.addFilter("DEPU", "score", ">", "0"));
      trigger();

      apply(ed -> ed.editUnpivot("UP", 1, false)); // melts q1 only
      trigger();

      assertEquals(1, hcol());
      assertEquals(1, pre("DEPU"));
   }

   @Test
   void hiddenMiddleColumnIsNotTakenForAMeltedOneByTheTypeCheck() throws Exception {
      ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "S", "k", "x", "q1", "q2");
      t.setEmbeddedData(new XEmbeddedTable(new String[]{ "integer", "string", "integer", "integer" },
         new Object[][]{ { "k", "x", "q1", "q2" }, { 1, "a", 3, 4 }, { 5, "b", 7, 8 } }));

      for(String c : List.of("k", "q1", "q2")) {
         ((ColumnRef) t.getColumnSelection(false).getAttribute(c)).setDataType(XSchema.INTEGER);
      }

      ((ColumnRef) t.getColumnSelection(false).getAttribute("x")).setDataType(XSchema.STRING);
      ws.addAssembly(t);
      ws.addAssembly(pidTable(ws, "Z"));
      svc = service(ws);
      apply(ed -> ed.setColumnVisibility("S", "x", false));
      apply(ed -> ed.addUnpivot("UP", "S", 2));

      apply(ed -> ed.editUnpivot("UP", 1, false));

      assertEquals(1, hcol());
   }

   @Test
   void headerCountBeyondTheVisibleColumnsCanStillBeShrunk() throws Exception {
      // addUnpivot bounds against the private selection (5), the melt against the visible (3):
      // the stored 4 means "all but the last visible column" and is clamped to 2.
      build0(4, false);
      apply(ed -> ed.setColumnVisibility("S", "q2", false));
      apply(ed -> ed.setColumnVisibility("S", "q3", false));
      assertEquals(4, hcol(), "sanity: the stored count is beyond the visible columns");
      apply(ed -> ed.addMirror("DEPU", "UP"));
      apply(ed -> ed.addFilter("DEPU", "score", ">", "0"));
      trigger();

      // 2 is the effective count: re-asserting it is accepted and stores 2
      apply(ed -> ed.editUnpivot("UP", 2, false));
      assertEquals(2, hcol());
   }

   @Test
   void shrinkOfAHeaderCountBeyondTheVisibleColumnsNamesTheRightColumn() throws Exception {
      build0(4, false);
      apply(ed -> ed.setColumnVisibility("S", "q2", false));
      apply(ed -> ed.setColumnVisibility("S", "q3", false));
      apply(ed -> ed.addMirror("DEPU", "UP"));
      apply(ed -> ed.addFilter("DEPU", "score", ">", "0"));
      trigger();

      // effective header is name, score; shrinking to 1 melts score
      String m = refusal(1, false);
      assertTrue(m.contains("\"S.score\""), m);
      assertFalse(m.contains("Refresh the table"), m);
      assertEquals(4, hcol());

      apply(ed -> ed.editUnpivot("UP", 1, true));
      assertEquals(1, hcol());
   }

   @Test
   void typeConflictBeyondTheVisibleColumnsIsReportedWithTheRealType() throws Exception {
      ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(
         ws, "S", "a", "b", "c", "h1", "h2");
      t.setEmbeddedData(new XEmbeddedTable(
         new String[]{ "string", "integer", "string", "integer", "integer" },
         new Object[][]{ { "a", "b", "c", "h1", "h2" }, { "x", 1, "y", 2, 3 } }));
      ColumnSelection cs = t.getColumnSelection(false);
      ((ColumnRef) cs.getAttribute("a")).setDataType(XSchema.STRING);
      ((ColumnRef) cs.getAttribute("b")).setDataType(XSchema.INTEGER);
      ((ColumnRef) cs.getAttribute("c")).setDataType(XSchema.STRING);
      ((ColumnRef) cs.getAttribute("h1")).setDataType(XSchema.INTEGER);
      ((ColumnRef) cs.getAttribute("h2")).setDataType(XSchema.INTEGER);
      ws.addAssembly(t);
      ws.addAssembly(pidTable(ws, "Z"));
      svc = service(ws);
      apply(ed -> ed.addUnpivot("UP", "S", 4));
      apply(ed -> ed.setColumnVisibility("S", "h1", false));
      apply(ed -> ed.setColumnVisibility("S", "h2", false));

      String m = refusal(1, false);
      assertTrue(m.contains("b cannot move from header to melted columns"), m);
      assertFalse(m.contains("'null'"), m);
      assertFalse(m.contains("Refresh the table"), m);
   }

   // ---- join keys and unresolvable columns ---------------------------------------------

   @Test
   void joinKeyOnTheMeltedColumnIsRefusedEvenWhenConfirmed() throws Exception {
      build(2);
      apply(ed -> ed.addJoin("J", "UP", "score", "Z", "pid", "INNER", null, null));
      trigger();

      String m = refusal(1, false);
      assertTrue(m.contains("it is a join key of 'J'"), m);
      String confirmed = refusal(1, true);
      assertTrue(confirmed.contains("it is a join key of 'J'"), confirmed);
      assertEquals(2, hcol());
   }

   @Test
   void departingColumnMissingFromTheTableIsRefusedAskingForARefresh() throws Exception {
      build(2);
      UnpivotTableAssembly up = (UnpivotTableAssembly) ws.getAssembly("UP");
      ColumnSelection cs = up.getColumnSelection(false);
      cs.removeAttribute(cs.getAttribute("score"));
      up.setColumnSelection(cs);

      String m = refusal(1, false);
      assertTrue(m.contains("Cannot check what uses column \"score\" of 'UP'"), m);
      assertTrue(m.contains("not in the table's current columns"), m);
      assertTrue(m.contains("Refresh the table and retry"), m);
      assertEquals(2, hcol());
   }

   @Test
   void departingColumnMatchingSeveralTableColumnsIsRefusedWithoutAdviceToRefresh()
      throws Exception
   {
      build(2);
      UnpivotTableAssembly up = (UnpivotTableAssembly) ws.getAssembly("UP");
      ColumnSelection cs = up.getColumnSelection(false);
      cs.removeAttribute(cs.getAttribute("score"));
      cs.addAttribute(new ColumnRef(new AttributeRef("X", "score")));
      cs.addAttribute(new ColumnRef(new AttributeRef("Y", "score")));
      up.setColumnSelection(cs);

      String m = refusal(1, false);
      assertTrue(m.contains("matches more than one of the table's columns"), m);
      assertTrue(m.contains("Nothing was changed"), m);
      assertFalse(m.contains("Refresh the table"), m);
      assertEquals(2, hcol());
   }

   // ---- support ------------------------------------------------------------------------

   /** As {@link #build} on a source whose leading column {@code name} may be hidden first. */
   private void build0(int hcol, boolean hideName) throws Exception {
      ws = new Worksheet();
      ws.addAssembly(source(ws, "S"));
      ws.addAssembly(pidTable(ws, "Z"));
      svc = service(ws);

      if(hideName) {
         apply(ed -> ed.setColumnVisibility("S", "name", false));
      }

      apply(ed -> ed.addUnpivot("UP", "S", hcol));

      if(hideName) {
         apply(ed -> ed.addMirror("DEPU", "UP"));
      }
   }

   private void buildNumeric(int hcol) throws Exception {
      ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "S", "k1", "k2", "q1", "q2");
      t.setEmbeddedData(new XEmbeddedTable(new String[]{ "integer", "integer", "integer", "integer" },
         new Object[][]{ { "k1", "k2", "q1", "q2" }, { 1, 2, 3, 4 }, { 5, 6, 7, 8 } }));

      for(String c : List.of("k1", "k2", "q1", "q2")) {
         ((ColumnRef) t.getColumnSelection(false).getAttribute(c)).setDataType(XSchema.INTEGER);
      }

      ws.addAssembly(t);
      ws.addAssembly(pidTable(ws, "Z"));
      svc = service(ws);
      apply(ed -> ed.addUnpivot("UP", "S", hcol));
      apply(ed -> ed.addMirror("DEPU", "UP"));
   }

   /** Runs the shrink expecting a refusal, and returns its message. */
   private String refusal(int to, boolean confirmed) {
      PairingException ex = assertThrows(PairingException.class,
         () -> apply(ed -> ed.editUnpivot("UP", to, confirmed)));
      return ex.getMessage();
   }

   private int hcol() {
      return ((UnpivotTableAssembly) ws.getAssembly("UP")).getHeaderColumns();
   }

   private int pre(String name) {
      ConditionList l = (ConditionList) ((TableAssembly) ws.getAssembly(name)).getPreConditionList();
      return l == null ? 0 : l.getConditionSize();
   }

   /** An unrelated op, whose refresh would publish any leftover from a refused one. */
   private void trigger() throws Exception {
      String name = "ZM" + (++triggers);
      apply(ed -> ed.addMirror(name, "Z"));
   }

   private void apply(ThrowingConsumer<WorksheetEditService.Editor> m) throws Exception {
      svc.apply("TOK", agent, m);
   }

   static EmbeddedTableAssembly source(Worksheet ws, String name) {
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(
         ws, name, "name", "score", "q1", "q2", "q3");
      t.setEmbeddedData(new XEmbeddedTable(
         new String[]{ "string", "integer", "integer", "integer", "integer" },
         new Object[][]{
            { "name", "score", "q1", "q2", "q3" },
            { "a", 1, 10, 20, 30 },
            { "b", 2, 11, 21, 31 },
            { "c", 3, 12, 22, 32 },
         }));
      ColumnSelection cs = t.getColumnSelection(false);
      ((ColumnRef) cs.getAttribute("name")).setDataType(XSchema.STRING);

      for(String c : List.of("score", "q1", "q2", "q3")) {
         ((ColumnRef) cs.getAttribute(c)).setDataType(XSchema.INTEGER);
      }

      return t;
   }

   private static EmbeddedTableAssembly pidTable(Worksheet ws, String name) {
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, name, "pid");
      t.setEmbeddedData(new XEmbeddedTable(new String[]{ "integer" },
         new Object[][]{ { "pid" }, { 1 }, { 2 } }));
      ((ColumnRef) t.getColumnSelection(false).getAttribute("pid")).setDataType(XSchema.INTEGER);
      return t;
   }

   static WorksheetEditService service(Worksheet ws) throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = new JoinSession("TOK", "Worksheet/ws1", "alice~;~host-org",
                                      SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      box.setActive(true);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq("Worksheet/ws1"), any()))
         .thenReturn(rws);

      return new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), securityEngine, mock(InnerJoinService.class));
   }
}
