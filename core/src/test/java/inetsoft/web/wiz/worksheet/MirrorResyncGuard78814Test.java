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
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.table.SnapshotEmbeddedTableDataCache;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78814: update_mirror on an outer mirror whose saved source worksheet changed shape is
 * refused before anything is mutated when a dependent would silently lose state, and the
 * columns the guard predicts are the ones a real re-sync produces. Runs against the real asset
 * repository, as {@code OuterCopyIdentityTest} does.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  MirrorResyncGuard78814Test.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // the Spring context with the real asset repository
class MirrorResyncGuard78814Test {
   @Autowired
   private WorksheetService worksheetService;

   private final Principal agent = TestPrincipals.user("alice", "host-org");

   // ------------------------------------------------------------------- dependents' state

   @Test
   void droppingAColumnTheMirrorsOwnAndDependentTablesReadIsRefusedNamingEach() throws Exception {
      AssetEntry source = entry("mrg_drop");
      saveSource(source, "a", "b", "c");
      Worksheet ws = outerWorksheet(source, 1);
      WorksheetEditService svc = service(ws);
      svc.apply("TOK", agent, ed -> {
         ed.addFilter("M", "c", ">", "5");
         ed.addMirror("D", "M");
         ed.setGroupAggregate(
            "D", List.of(new WorksheetMutationSupport.GroupSpec("a")),
            List.of(new WorksheetMutationSupport.AggregateSpec("c", "Sum", null)), false, false);
         ed.addMirror("E", "M");
         ed.addFilter("E", "c", "<", "15");
      });
      saveSource(source, "a", "b");
      List<String> before = state(ws);

      PairingException ex = assertThrows(PairingException.class, () -> resync(ws, false));

      assertTrue(ex.getMessage().contains("'D' (aggregate/group-by on"), ex.getMessage());
      assertTrue(ex.getMessage().contains("'E' (pre-condition"), ex.getMessage());
      assertTrue(ex.getMessage().contains("table 'M' itself"), ex.getMessage());
      assertTrue(ex.getMessage().contains("confirmed:true"), ex.getMessage());
      assertEquals(before, state(ws), "the guard changed the worksheet");
   }

   @Test
   void confirmedProceedsWhenOnlyStateIsAtStakeAndThePredictedColumnsAreTheRealOnes()
      throws Exception
   {
      AssetEntry source = entry("mrg_confirmed");
      saveSource(source, "a", "b", "c");
      Worksheet ws = outerWorksheet(source, 1);
      WorksheetEditService svc = service(ws);
      svc.apply("TOK", agent, ed -> {
         ed.addMirror("E", "M");
         ed.addFilter("E", "c", "<", "15");
      });
      saveSource(source, "a", "b");

      resync(ws, true);

      realResync(ws);
      assertEquals(List.of("a", "b"), attributes((TableAssembly) ws.getAssembly("M")));
   }

   @Test
   void droppingAColumnNoOneReadsIsAccepted() throws Exception {
      AssetEntry source = entry("mrg_unread");
      saveSource(source, "a", "b", "c");
      Worksheet ws = outerWorksheet(source, 1);
      WorksheetEditService svc = service(ws);
      svc.apply("TOK", agent, ed -> {
         ed.addMirror("E", "M");
         ed.addFilter("E", "a", "<", "15");
      });
      saveSource(source, "a", "b");

      resync(ws, false);
   }

   @Test
   void theMirrorsOwnExpressionColumnIsKeptOrNamedByWhatItReads() throws Exception {
      AssetEntry source = entry("mrg_expr");
      saveSource(source, "a", "b", "c");
      Worksheet ws = outerWorksheet(source, 1);
      WorksheetEditService svc = service(ws);
      svc.apply("TOK", agent, ed -> {
         ed.addExpressionColumn("M", "keep", "field['a'] * 2", "integer", false);
         ed.addExpressionColumn("M", "lose", "field['c'] * 2", "integer", false);
      });
      saveSource(source, "a", "b");

      PairingException ex = assertThrows(PairingException.class, () -> resync(ws, false));

      assertTrue(ex.getMessage().contains("table 'M' itself (expression column 'lose')"),
                 ex.getMessage());
      assertFalse(ex.getMessage().contains("keep"), ex.getMessage());

      // the refresh that follows a real re-sync keeps the mirror's own expression columns
      // whatever they read, so the one reading the dropped column is left dangling, silently
      realResync(ws);
      assertEquals(List.of("a", "b", "keep", "lose"),
                   attributes((TableAssembly) ws.getAssembly("M")));
   }

   @Test
   void addingAColumnWithNoConcatenationDownstreamIsAccepted() throws Exception {
      AssetEntry source = entry("mrg_add");
      saveSource(source, "a", "b");
      Worksheet ws = outerWorksheet(source, 1);
      WorksheetEditService svc = service(ws);
      svc.apply("TOK", agent, ed -> {
         ed.addMirror("E", "M");
         ed.addFilter("E", "a", "<", "15");
      });
      saveSource(source, "a", "b", "c");

      resync(ws, false);
   }

   // ------------------------------------------------------------------- the mirror's own aggregate

   @Test
   void droppingTheMirrorsOwnAggregateInputIsRefusedUnlessConfirmed() throws Exception {
      Worksheet ws = ownAggregateWorksheet(
         "mrg_own_sum", "a", new WorksheetMutationSupport.AggregateSpec("c", "Sum", null));
      List<String> before = state(ws);

      PairingException ex = assertThrows(PairingException.class, () -> resync(ws, false));

      assertTrue(ex.getMessage().contains("table 'M' itself (aggregate/group-by on c)"),
                 ex.getMessage());
      assertTrue(ex.getMessage().contains("confirmed:true"), ex.getMessage());
      assertEquals(before, state(ws), "the guard changed the worksheet");

      resync(ws, true);

      // what the refusal protects: the real re-sync drops Sum(c) without a word
      realResync(ws);
      assertEquals(0, ((TableAssembly) ws.getAssembly("M")).getAggregateInfo().getAggregateCount());
   }

   @Test
   void droppingTheMirrorsOwnGroupColumnIsRefused() throws Exception {
      Worksheet ws = ownAggregateWorksheet(
         "mrg_own_group", "c", new WorksheetMutationSupport.AggregateSpec("b", "Sum", null));

      PairingException ex = assertThrows(PairingException.class, () -> resync(ws, false));

      assertTrue(ex.getMessage().contains("table 'M' itself (aggregate/group-by on c)"),
                 ex.getMessage());
      resync(ws, true);
   }

   @Test
   void droppingTheSecondColumnOfTheMirrorsOwnTwoColumnAggregateIsRefused() throws Exception {
      // Correlation(b, c) is dropped by the refresh; First(b, c) is kept but left reading a
      // column that is gone, so it is refused the same way
      for(String formula : new String[] { "Correlation", "First" }) {
         Worksheet ws = ownAggregateWorksheet(
            "mrg_own_2nd_" + formula, "a",
            new WorksheetMutationSupport.AggregateSpec("b", formula, null, null, "c", null));

         PairingException ex = assertThrows(PairingException.class, () -> resync(ws, false),
                                            formula);

         assertTrue(ex.getMessage().contains("table 'M' itself (aggregate/group-by on c)"),
                    ex.getMessage());
      }
   }

   @Test
   void theMirrorsOwnAggregateOnSurvivingColumnsIsAccepted() throws Exception {
      Worksheet ws = ownAggregateWorksheet(
         "mrg_own_ok", "a", new WorksheetMutationSupport.AggregateSpec("b", "Sum", null));

      resync(ws, false);
   }

   @Test
   void theMirrorsOwnAggregateIsAcceptedWhenTheShapeIsUnchanged() throws Exception {
      AssetEntry source = entry("mrg_own_same");
      saveSource(source, "a", "b", "c");
      Worksheet ws = outerWorksheet(source, 1);
      service(ws).apply("TOK", agent, ed -> ed.setGroupAggregate(
         "M", List.of(new WorksheetMutationSupport.GroupSpec("c")),
         List.of(new WorksheetMutationSupport.AggregateSpec("b", "Sum", null)), false, false));
      saveSource(source, 20, "a", "b", "c");

      resync(ws, false);
   }

   /** M mirrors T(a, b, c), grouped by {@code group} with {@code aggregate}; T is then T(a, b). */
   private Worksheet ownAggregateWorksheet(String name, String group,
                                           WorksheetMutationSupport.AggregateSpec aggregate)
      throws Exception
   {
      AssetEntry source = entry(name);
      saveSource(source, "a", "b", "c");
      Worksheet ws = outerWorksheet(source, 1);
      service(ws).apply("TOK", agent, ed -> ed.setGroupAggregate(
         "M", List.of(new WorksheetMutationSupport.GroupSpec(group)), List.of(aggregate),
         false, false));
      assertEquals(1, ((TableAssembly) ws.getAssembly("M")).getAggregateInfo().getAggregateCount(),
                   "the fixture's aggregate was not set");
      saveSource(source, "a", "b");
      return ws;
   }

   // ------------------------------------------------------------------- keys and types

   @Test
   void droppingAJoinKeyIsRefusedNamingTheJoinEvenWhenConfirmed() throws Exception {
      AssetEntry source = entry("mrg_key");
      saveSource(source, "a", "b", "c");
      Worksheet ws = outerWorksheet(source, 1);
      WorksheetEditService svc = service(ws);
      EmbeddedTableAssembly r = TestWorksheets.tableWithColumns(ws, "R", "k");
      ((ColumnRef) r.getColumnSelection().getAttribute(0)).setDataType("integer");
      ws.addAssembly(r);
      svc.apply("TOK", agent, ed -> ed.addJoin("J", "M", "c", "R", "k", "INNER", null, null));
      saveSource(source, "a", "b");

      for(boolean confirmed : new boolean[] { false, true }) {
         PairingException ex = assertThrows(PairingException.class, () -> resync(ws, confirmed));
         assertTrue(ex.getMessage().contains("'J' (join key on"), ex.getMessage());
         assertFalse(ex.getMessage().contains("confirmed:true"), ex.getMessage());
      }
   }

   @Test
   void changingTheTypeOfASurvivingJoinKeyIsRefusedEvenWhenConfirmed() throws Exception {
      AssetEntry source = entry("mrg_keytype");
      saveSource(source, "a", "b", "c");
      Worksheet ws = outerWorksheet(source, 1);
      WorksheetEditService svc = service(ws);
      EmbeddedTableAssembly r = TestWorksheets.tableWithColumns(ws, "R", "k");
      ((ColumnRef) r.getColumnSelection().getAttribute(0)).setDataType("integer");
      ws.addAssembly(r);
      svc.apply("TOK", agent, ed -> ed.addJoin("J", "M", "c", "R", "k", "INNER", null, null));
      saveSource(source, "a", "b", "c:s");

      for(boolean confirmed : new boolean[] { false, true }) {
         PairingException ex = assertThrows(PairingException.class, () -> resync(ws, confirmed));
         assertTrue(ex.getMessage().contains("would change the type of"), ex.getMessage());
         assertTrue(ex.getMessage().contains("join \"J\""), ex.getMessage());
      }
   }

   // ------------------------------------------------------------------- concatenation

   @Test
   void aChangedColumnListIsRefusedWhenAConcatenationIsFedEvenWhenConfirmed() throws Exception {
      for(String[] changed : new String[][] { { "a", "b" }, { "a", "b", "c", "d" },
                                              { "b", "a", "c" }, { "a", "b", "c:s" } })
      {
         AssetEntry source = entry("mrg_concat_" + String.join("", changed).replace(":", ""));
         saveSource(source, "a", "b", "c");
         Worksheet ws = outerWorksheet(source, 1);
         WorksheetEditService svc = service(ws);
         svc.apply("TOK", agent, ed -> {
            ed.addMirror("X", "M");
            ed.addConcatenation("C", List.of("M", "X"), "UNION");
         });
         saveSource(source, changed);

         for(boolean confirmed : new boolean[] { false, true }) {
            PairingException ex =
               assertThrows(PairingException.class, () -> resync(ws, confirmed),
                            Arrays.toString(changed));
            assertTrue(ex.getMessage().contains("concatenation(s) 'C'"), ex.getMessage());
            assertFalse(ex.getMessage().contains("confirmed:true"), ex.getMessage());
         }
      }
   }

   @Test
   void aConcatenationReachedThroughAnotherTableIsFound() throws Exception {
      AssetEntry source = entry("mrg_concat_deep");
      saveSource(source, "a", "b", "c");
      Worksheet ws = outerWorksheet(source, 1);
      WorksheetEditService svc = service(ws);
      svc.apply("TOK", agent, ed -> {
         ed.addMirror("D", "M");
         ed.addMirror("X", "M");
         ed.addConcatenation("C", List.of("D", "X"), "UNION");
      });
      saveSource(source, "a", "b");

      PairingException ex = assertThrows(PairingException.class, () -> resync(ws, true));

      assertTrue(ex.getMessage().contains("concatenation(s) 'C'"), ex.getMessage());
   }

   @Test
   void anUnchangedShapeIsAcceptedWithAConcatenationFed() throws Exception {
      AssetEntry source = entry("mrg_concat_same");
      saveSource(source, "a", "b", "c");
      Worksheet ws = outerWorksheet(source, 1);
      WorksheetEditService svc = service(ws);
      svc.apply("TOK", agent, ed -> {
         ed.addMirror("X", "M");
         ed.addConcatenation("C", List.of("M", "X"), "UNION");
         ed.addFilter("X", "c", ">", "5");
      });
      saveSource(source, 20, "a", "b", "c");

      resync(ws, false);
   }

   // ------------------------------------------------------------------- scope of the re-sync

   @Test
   void everyMirrorSharingTheSourceCopiesIsChecked() throws Exception {
      AssetEntry source = entry("mrg_share");
      saveSource(source, "a", "b", "c");
      Worksheet ws = outerWorksheet(source, 2);
      WorksheetEditService svc = service(ws);
      svc.apply("TOK", agent, ed -> {
         ed.addMirror("D1", "M1");
         ed.addFilter("D1", "c", "<", "15");
      });
      saveSource(source, "a", "b");

      PairingException ex = assertThrows(PairingException.class, () -> resync(ws, "M0", false));

      assertTrue(ex.getMessage().contains("'D1' (pre-condition"), ex.getMessage());
      assertTrue(ex.getMessage().contains("Re-syncing 'M1'"), ex.getMessage());
   }

   @Test
   void aTableReadingAReplacedSourceCopyDirectlyIsRefusedEvenWhenConfirmed() throws Exception {
      AssetEntry source = entry("mrg_direct");
      saveSource(source, "a", "b", "c");
      Worksheet ws = outerWorksheet(source, 1);
      String copy = ((MirrorAssembly) ws.getAssembly("M")).getAssemblyName();
      WorksheetEditService svc = service(ws);
      svc.apply("TOK", agent, ed -> ed.addMirror("U", copy));
      saveSource(source, "a", "b");

      PairingException ex = assertThrows(PairingException.class, () -> resync(ws, true));

      assertTrue(ex.getMessage().contains("'U' reads it directly"), ex.getMessage());
      assertTrue(ex.getMessage().contains(copy), ex.getMessage());
   }

   @Test
   void aTableReadingTheSourceCopyDirectlyIsAcceptedWhenTheColumnsAreTheSame() throws Exception {
      AssetEntry source = entry("mrg_direct_same");
      saveSource(source, "a", "b", "c");
      Worksheet ws = outerWorksheet(source, 1);
      String copy = ((MirrorAssembly) ws.getAssembly("M")).getAssemblyName();
      service(ws).apply("TOK", agent, ed -> ed.addMirror("U", copy));
      saveSource(source, 20, "a", "b", "c");

      resync(ws, false);
      realResync(ws);
      assertEquals(copy, ((MirrorAssembly) ws.getAssembly("U")).getAssemblyName());
      assertNotNull(ws.getAssembly(copy));
   }

   @Test
   void aMirrorOfAssetInTheSameWorksheetIsRefusedLoudly() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      service(ws).apply("TOK", agent, ed -> ed.addMirror("M", "T"));

      PairingException ex = assertThrows(PairingException.class, () -> resync(ws, "M", false));

      assertTrue(ex.getMessage().contains("always tracks its source"), ex.getMessage());
   }

   // ------------------------------------------------------------------- fixtures

   /** What update_mirror does once the guard passed: the re-sync, then the column refresh. */
   private void realResync(Worksheet ws) throws Exception {
      ((MirrorAssembly) ws.getAssembly("M")).updateMirror(repository(), null);
      new AssetQuerySandbox(ws).refreshColumnSelection("M", true);
   }

   private void resync(Worksheet ws, boolean confirmed) throws Exception {
      resync(ws, "M", confirmed);
   }

   private void resync(Worksheet ws, String name, boolean confirmed) throws Exception {
      MirrorResyncGuard.requireResyncKeepsDependents(
         ws, name, (MirrorAssembly) ws.getAssembly(name), repository(), null, confirmed);
   }

   /**
    * A worksheet holding {@code mirrors} outer mirrors M0..: the only one is named M, several
    * share one set of source copies. Auto Update is off, as an update_mirror needs.
    */
   private Worksheet outerWorksheet(AssetEntry source, int mirrors) throws Exception {
      Worksheet ws = new Worksheet();
      WSAssembly[] created = AssetUtil.copyOuterAssemblies(repository(), source, null, ws, null);
      WSAssembly root = created[created.length - 1];

      for(int i = 0; i < mirrors; i++) {
         String name = mirrors == 1 ? "M" : "M" + i;
         MirrorTableAssembly mirror = new MirrorTableAssembly(ws, name, source, true, root);
         mirror.setAutoUpdate(false);
         ws.addAssembly(mirror);
      }

      ws.setPrimaryAssembly(mirrors == 1 ? "M" : "M0");
      return ws;
   }

   /** Saves the source worksheet with a table T of integer columns, a name:s one is text. */
   private void saveSource(AssetEntry entry, String... columns) throws Exception {
      saveSource(entry, 10, columns);
   }

   private void saveSource(AssetEntry entry, int rows, String... columns) throws Exception {
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly table = new SnapshotEmbeddedTableAssembly(ws, "T");
      ws.addAssembly(table);
      ws.setPrimaryAssembly("T");
      XSwappableTable data = new XSwappableTable(columns.length, false);
      Object[] header = new Object[columns.length];

      for(int j = 0; j < columns.length; j++) {
         header[j] = columns[j].replace(":s", "");
      }

      data.addRow(header);

      for(int i = 0; i < rows; i++) {
         Object[] row = new Object[columns.length];

         for(int j = 0; j < columns.length; j++) {
            row[j] = columns[j].endsWith(":s") ? "s" + i : (Object) (i * 10 + j);
         }

         data.addRow(row);
      }

      data.complete();
      table.setEmbeddedData(new XEmbeddedTable(data));
      worksheetService.setWorksheet(ws, entry, null, true, true);
      repository().clearCache(entry);
      SnapshotEmbeddedTableDataCache.getInstance().clear();
   }

   private static List<String> attributes(TableAssembly table) {
      List<String> names = new ArrayList<>();
      ColumnSelection columns = table.getColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         names.add(columns.getAttribute(i).getAttribute());
      }

      return names;
   }

   /** Everything the guard must leave alone: the assemblies, their columns and conditions. */
   private static List<String> state(Worksheet ws) {
      List<String> state = new ArrayList<>();

      for(Assembly assembly : ws.getAssemblies()) {
         String line = assembly.getName();

         if(assembly instanceof TableAssembly table) {
            line += " " + attributes(table) + " " + table.getPreConditionList() + " " +
               Arrays.toString(table.getAggregateInfo().getAggregates());
         }

         state.add(line);
      }

      Collections.sort(state);
      return state;
   }

   private WorksheetEditService service(Worksheet ws) throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      JoinSession s = new JoinSession("TOK", "Worksheet/ws1", "alice~;~host-org",
                                      SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(
         eq(SheetType.WORKSHEET), eq("Worksheet/ws1"), eq(agent))).thenReturn(rws);
      return new WorksheetEditService(sessions, runtimeAccess, broadcast, securityEngine,
                                      new InnerJoinService(null, null));
   }

   private static AbstractAssetEngine repository() {
      return (AbstractAssetEngine) AssetUtil.getAssetRepository(false);
   }

   private static AssetEntry entry(String name) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, name, null);
   }

   @Configuration
   static class Beans {
      // the constructor is package private; saving a sheet updates the dependency storage
      @Bean
      public DependencyStorageService dependencyStorageService(KeyValueStorageManager storage)
         throws Exception
      {
         Constructor<DependencyStorageService> ctor =
            DependencyStorageService.class.getDeclaredConstructor(KeyValueStorageManager.class);
         ctor.setAccessible(true);
         return ctor.newInstance(storage);
      }
   }
}
