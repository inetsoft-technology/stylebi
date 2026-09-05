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
package inetsoft.web.binding.handler;

import inetsoft.report.composition.AssetTreeModel;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AssetContent;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.PhysicalBoundTableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.IndexedStorage;
import inetsoft.web.wiz.pairing.TestPrincipals;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Settles bug #76471's "branch 1 vs. branch 2" question (see
 * {@code docs/teams/2026-09-04-bugs-composer-ranking-bindable-fields/bug-76471/01-diagnosis.md}):
 * does a {@code save_worksheet} between {@code set_group_aggregate} and {@code list_bindable_fields}
 * (i.e. a persisted change to the base worksheet, picked up by
 * {@link inetsoft.analytic.composition.event.CubeTreeModelBuilder#getCubeTreeModel}'s
 * storage-timestamp staleness check, which fires {@link RuntimeViewsheet#resetRuntime()}) actually
 * make {@link VSTreeHandler#getChartTreeModel} return the post-aggregation column shape.
 *
 * <p>Answer: NEITHER branch cleanly held before the fix below. {@code resetRuntime()} (via
 * {@code Viewsheet#update}) DOES correctly reload the live {@code Viewsheet}'s base worksheet with
 * the fresh, already-persisted post-aggregation table -- so a save genuinely fixed the underlying
 * state. But {@code CubeTreeModelBuilder.getBuilder} snapshots {@code vs.getBaseWorksheet()} into
 * its own {@code baseWS} field BEFORE {@code getCubeTreeModel} runs the staleness check that
 * triggers the reset (see {@code CubeTreeModelBuilder.java:96-97} vs. {@code :119-131}), and every
 * downstream read ({@code getModel}/{@code getBaseModel}/{@code appendColumnNodes}) read that same
 * stale, already-captured field, never re-reading {@code vs.getBaseWorksheet()} after the reset it
 * had just triggered -- so the FIRST {@code list_bindable_fields} call after a save still returned
 * the raw pre-aggregation columns, one call late, while a SECOND call (a brand-new builder against
 * the by-then-already-reset viewsheet) returned the correct shape. Fixed in
 * {@code CubeTreeModelBuilder.getCubeTreeModel} by re-reading {@code baseWS}/{@code baseEntry} from
 * {@code vs} immediately after {@code processor.baseWorksheetChanged()} runs, so the SAME call that
 * triggers the reset also sees its result -- verified by {@link #firstCallAfterSaveReturnsFreshColumns},
 * with {@link #secondCallAfterSaveReturnsFreshColumns} guarding the already-correct no-redundant-reset
 * case.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSTreeHandlerCrossRuntimeStalenessTest {
   private static final String TABLE_NAME = "BASE";
   private static final long T_STALE = 1_000L;
   private static final long T_FRESH = 2_000L;

   private static ColumnRef rawColumn(String name) {
      AttributeRef ref = new AttributeRef(null, name);
      ref.setDataType(XSchema.STRING);
      ColumnRef col = new ColumnRef(ref);
      col.setDataType(XSchema.STRING);
      return col;
   }

   /** Pre-aggregation shape: every raw join column, not aggregated. */
   private static Worksheet staleWorksheet() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, TABLE_NAME);
      table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "postgres", "public.sales"));
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(rawColumn("RESELLER"));
      cs.addAttribute(rawColumn("SALES_AMOUNT"));
      cs.addAttribute(rawColumn("REGION"));
      table.setColumnSelection(cs, false);
      table.setColumnSelection(cs, true);
      ws.addAssembly(table);
      ws.setLastModified(T_STALE);
      return ws;
   }

   /**
    * Post-aggregation shape, as it would look immediately after {@code set_group_aggregate} +
    * {@code WorksheetEditService.apply}'s {@code refreshAssemblies} regenerated the public column
    * selection, then {@code save_worksheet} persisted it: group-by dimension + one aggregate
    * alias only.
    */
   private static Worksheet freshWorksheet() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, TABLE_NAME);
      table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "postgres", "public.sales"));
      ColumnSelection raw = new ColumnSelection();
      raw.addAttribute(rawColumn("RESELLER"));
      raw.addAttribute(rawColumn("SALES_AMOUNT"));
      raw.addAttribute(rawColumn("REGION"));
      table.setColumnSelection(raw, false);
      ColumnSelection publicSel = new ColumnSelection();
      publicSel.addAttribute(rawColumn("RESELLER"));
      publicSel.addAttribute(rawColumn("TOTAL_SALES"));
      table.setColumnSelection(publicSel, true);
      table.setAggregate(true);
      ws.addAssembly(table);
      ws.setLastModified(T_FRESH);
      return ws;
   }

   /** All leaf column-node names under a chart tree's root, across every folder. */
   private static List<String> leafColumnNames(AssetTreeModel model) {
      AssetTreeModel.Node root = (AssetTreeModel.Node) model.getRoot();
      return leafColumnNames(root);
   }

   private static List<String> leafColumnNames(AssetTreeModel.Node node) {
      List<String> names = new java.util.ArrayList<>();

      for(AssetTreeModel.Node child : node.getNodes()) {
         if(child.getNodeCount() > 0) {
            names.addAll(leafColumnNames(child));
         }
         else {
            names.add(child.getEntry().getPath());
         }
      }

      return names;
   }

   private static final class Fixture {
      final AssetEntry wsEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws1", null);
      final Viewsheet vs = new Viewsheet(wsEntry);
      final ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      final AssetRepository engine = mock(AssetRepository.class);
      final IndexedStorage store = mock(IndexedStorage.class);
      final RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      final Principal principal = TestPrincipals.user("alice", "host-org");
      final AtomicLong lastReset = new AtomicLong();
      final VSTreeHandler handler = new VSTreeHandler(
         mock(VSChartHandler.class), mock(inetsoft.sree.security.SecurityEngine.class));

      Fixture() throws Exception {
         chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, TABLE_NAME));
         vs.addAssembly(chart);

         // Connect: load the STALE (pre-aggregation) worksheet, as of before set_group_aggregate.
         when(engine.getSheet(any(AssetEntry.class), any(), anyBoolean(), any(AssetContent.class)))
            .thenReturn(staleWorksheet());
         vs.reloadBaseWorksheet(engine, principal);
         lastReset.set(500L); // "connected" before either worksheet snapshot's own timestamp matters

         // Now simulate set_group_aggregate + refreshAssemblies + save_worksheet: the repository
         // itself has moved on to the fresh, post-aggregation worksheet (T_FRESH), independent of
         // this already-connected viewsheet session, which nobody has told to refresh yet.
         when(engine.getSheet(any(AssetEntry.class), any(), anyBoolean(), any(AssetContent.class)))
            .thenReturn(freshWorksheet());
         when(engine.getStorage(any(AssetEntry.class))).thenReturn(store);
         when(store.lastModified()).thenReturn(T_FRESH);

         when(rvs.getViewsheet()).thenReturn(vs);
         when(rvs.isRuntime()).thenReturn(true);
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());
         when(rvs.getLastReset()).thenAnswer(inv -> lastReset.get());
         // The real RuntimeViewsheet#resetRuntime() calls Viewsheet#update(rep, null, user), whose
         // essential effect on the base worksheet is identical to reloadBaseWorksheet (both call
         // AssetRepository#getSheet and swap in the result) -- reproduced directly here so this
         // test exercises the real cross-runtime reload mechanism, not a stub that just "passes".
         doAnswer(inv -> {
            vs.reloadBaseWorksheet(engine, principal);
            lastReset.set(System.currentTimeMillis());
            return null;
         }).when(rvs).resetRuntime();
      }

      AssetTreeModel getChartTree() throws Exception {
         ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
         return handler.getChartTreeModel(engine, rvs, info, false, principal);
      }
   }

   /**
    * Fixture for the "no {@code save_worksheet} ever happens" scenario (bug PWA-010, see
    * {@code docs/teams/2026-09-05-bug-pwa-010/} in the stylebi-wiz repo): the worksheet-editing
    * session's {@code set_group_aggregate} is applied only to that session's own in-memory
    * {@code RuntimeWorksheet} and never persisted, so the {@code AssetRepository}/
    * {@code IndexedStorage} this viewsheet's engine reads from never changes and
    * {@code store.lastModified()} never exceeds {@code rvs.getLastReset()} -- the exact inverse of
    * {@link Fixture}'s one load-bearing precondition (there, {@code store.lastModified()} is bumped
    * past {@code lastReset} to simulate a save that landed). Everything else (assembly wiring,
    * engine/store/rvs mocks, principal) mirrors {@link Fixture} exactly.
    */
   private static final class NoSaveFixture {
      final AssetEntry wsEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws2", null);
      final Viewsheet vs = new Viewsheet(wsEntry);
      final ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      final AssetRepository engine = mock(AssetRepository.class);
      final IndexedStorage store = mock(IndexedStorage.class);
      final RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      final Principal principal = TestPrincipals.user("alice", "host-org");
      final VSTreeHandler handler = new VSTreeHandler(
         mock(VSChartHandler.class), mock(inetsoft.sree.security.SecurityEngine.class));

      NoSaveFixture() throws Exception {
         chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, TABLE_NAME));
         vs.addAssembly(chart);

         // Connect: load the STALE (pre-aggregation) worksheet. Unlike Fixture, the repository
         // never advances beyond this -- set_group_aggregate is applied only in the separate
         // worksheet-editing session's own in-memory RuntimeWorksheet, with no save_worksheet ever
         // persisting it here, so every subsequent engine.getSheet call keeps returning the same
         // pre-aggregation snapshot.
         when(engine.getSheet(any(AssetEntry.class), any(), anyBoolean(), any(AssetContent.class)))
            .thenReturn(staleWorksheet());
         vs.reloadBaseWorksheet(engine, principal);

         when(engine.getStorage(any(AssetEntry.class))).thenReturn(store);
         // The inverse of Fixture's precondition: store.lastModified() never exceeds lastReset,
         // because nothing was ever persisted after the viewsheet connected.
         when(store.lastModified()).thenReturn(T_STALE);
         when(rvs.getLastReset()).thenReturn(T_FRESH);

         when(rvs.getViewsheet()).thenReturn(vs);
         when(rvs.isRuntime()).thenReturn(true);
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());
      }

      AssetTreeModel getChartTree() throws Exception {
         ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
         return handler.getChartTreeModel(engine, rvs, info, false, principal);
      }
   }

   /**
    * Pins CURRENT, BY-DESIGN behavior of the worksheet/viewsheet runtime-separation boundary --
    * this is not an invariant a future cross-runtime live-bridge feature (e.g. one built on
    * {@code WorksheetEngine#getAllRuntimeWorksheetSheets()}, see
    * {@code docs/teams/2026-09-05-bug-pwa-010/PWA-010/01-diagnosis.md}'s "Revised proposed fix")
    * would be "breaking" if it deliberately changed this assertion -- it documents what happens
    * today, absent any such bridge, so a future change in this area is a deliberate design decision
    * rather than an accidental regression. Confirmed against the live product (not just this mock
    * fixture) in {@code docs/teams/2026-09-05-bug-pwa-010/PWA-010/02b-live-check-lead.md}'s "Check 1":
    * {@code set_group_aggregate} -> {@code list_bindable_fields} -> {@code list_bindable_fields}
    * again, no {@code save_worksheet} anywhere in between, returned the identical raw
    * pre-aggregation column list on both calls.
    */
   @Test
   void noSaveEverLeavesColumnsStaleOnEveryCall() throws Exception {
      NoSaveFixture fx = new NoSaveFixture();

      List<String> firstCall = leafColumnNames(fx.getChartTree());
      List<String> secondCall = leafColumnNames(fx.getChartTree());

      // The staleness check must never fire resetRuntime() -- store.lastModified() never exceeds
      // rvs.getLastReset() because nothing was ever persisted.
      verify(fx.rvs, org.mockito.Mockito.never()).resetRuntime();

      for(List<String> columns : List.of(firstCall, secondCall)) {
         assertTrue(columns.stream().anyMatch(c -> c.contains("REGION")),
            "absent a save, the viewsheet's base worksheet must keep reporting the raw, " +
            "pre-aggregation column shape on every call -- this is the two-runtimes-don't-" +
            "share-state boundary, not a bug in the CubeTreeModelBuilder read path itself");
         assertTrue(columns.stream().noneMatch(c -> c.contains("TOTAL_SALES")),
            "the post-aggregation TOTAL_SALES alias must never appear -- it exists only in the " +
            "separate worksheet-editing session's own in-memory state, which this viewsheet " +
            "session has no live (non-persisted) way to observe");
      }
   }

   @Test
   void firstCallAfterSaveReturnsFreshColumns() throws Exception {
      Fixture fx = new Fixture();

      List<String> columns = leafColumnNames(fx.getChartTree());

      // Confirms the staleness check actually fired resetRuntime() during THIS call (not that it
      // silently never triggered, which would also -- for an uninteresting, different reason --
      // leave stale data lying around undetected).
      verify(fx.rvs, times(1)).resetRuntime();

      assertTrue(columns.stream().anyMatch(c -> c.contains("TOTAL_SALES")),
         "the post-aggregation TOTAL_SALES alias must be visible on the very same call that " +
         "detects the base worksheet changed and triggers resetRuntime() -- the builder must " +
         "re-read the just-reset viewsheet's base worksheet before building the tree");
      assertTrue(columns.stream().noneMatch(c -> c.contains("REGION")),
         "the raw, pre-aggregation REGION column must not leak through from the builder's " +
         "pre-reset snapshot");
   }

   @Test
   void secondCallAfterSaveReturnsFreshColumns() throws Exception {
      Fixture fx = new Fixture();

      fx.getChartTree(); // first call: triggers the reset, still returns stale (see above)
      List<String> columns = leafColumnNames(fx.getChartTree()); // second call: fresh builder

      // The staleness check must NOT fire a second time -- lastReset was already advanced past
      // store.lastModified() by the first call's reset, so the second call's builder should see
      // an already-up-to-date viewsheet and skip resetRuntime() entirely.
      verify(fx.rvs, times(1)).resetRuntime();

      assertTrue(columns.stream().anyMatch(c -> c.contains("TOTAL_SALES")),
         "a second list_bindable_fields call, after the first already triggered resetRuntime(), " +
         "must see the post-aggregation shape -- proving the underlying reload mechanism itself " +
         "is correct and the defect is specifically the first call's stale builder snapshot");
      assertTrue(columns.stream().noneMatch(c -> c.contains("REGION")),
         "the raw REGION column must be gone once the builder is reconstructed against the " +
         "already-reset viewsheet");
   }
}
