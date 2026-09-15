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

import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.QueryManager;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.event.ActionListener;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for {@link ViewsheetSandbox#createTemporaryBox(Viewsheet)}, the mechanism that
 * lets {@code AbstractSelectionVSAQuery.checkAndRunCalcFieldMeasureQuery()} run its crosstab
 * against a throwaway clone of the sheet.
 *
 * <p>That method used to install the clone with {@code box.setViewsheet(clone, false)}, publishing
 * it into the sandbox's single shared {@code vs} field. Nothing serializes two top-level requests
 * on one sandbox -- {@code VSSelectionService.applySelection} holds only a shared *read* lock, and
 * the nested query drops the sandbox lock outright in
 * {@code VSAQuery#getDataWithoutSandboxLock} -- so two overlapping requests could each capture the
 * other's clone as the "original" to restore, permanently stranding the sandbox on a throwaway
 * clone with the real viewsheet stripped of its action listeners. The same idiom in the export
 * paths produced null table lenses and apparent hangs (bug #76576).
 *
 * <p>These tests pin the contract of the replacement rather than executing a real measure query:
 * standing up a viewsheet with a calc-field selection measure and live data needs an imported
 * asset bundle, which is the disproportionate scaffolding this repo also declined for the sibling
 * bug #76614 (see {@link CalcTableVSAQueryTempCrosstabNameTest}). Measure-value equivalence is
 * covered by the end-to-end check, not here.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetSandboxTemporaryBoxTest {
   private static final long TIMEOUT_SECONDS = 20;

   private Viewsheet vs;
   private ViewsheetSandbox sandbox;

   @BeforeEach
   void setUp() throws Exception {
      Worksheet ws = new Worksheet();
      vs = new Viewsheet();

      // Viewsheet.setBaseWorksheet() is private and only reachable through a full update()
      // against an asset repository, so wire the base worksheet directly -- same approach as
      // RefreshVariableTest.
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, ws);

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         "test/ViewsheetSandboxTemporaryBoxTest", null,
         OrganizationManager.getInstance().getCurrentOrgID());
      sandbox = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
   }

   /** The whole point: the shared sandbox never reports the clone, to any thread, ever. */
   @Test
   void temporaryBoxNeverMutatesTheSharedViewsheet() {
      Viewsheet temp = vs.clone();
      ViewsheetSandbox tempBox = sandbox.createTemporaryBox(temp);

      try {
         assertSame(temp, tempBox.getViewsheet(), "temp box must report the clone");
         assertSame(vs, sandbox.getViewsheet(), "shared sandbox must still report the real sheet");
      }
      finally {
         sandbox.releaseTemporaryBox(tempBox);
      }

      assertSame(vs, sandbox.getViewsheet());
   }

   /**
    * Two overlapping invocations must not be able to observe or clobber each other. Under the old
    * {@code setViewsheet()} swap this is exactly what failed: the second thread captured the first
    * thread's clone as its "original" and restored that.
    */
   @Test
   void concurrentTemporaryBoxesAreIndependent() throws Exception {
      CyclicBarrier bothCreated = new CyclicBarrier(2);
      ExecutorService pool = Executors.newFixedThreadPool(2);

      try {
         Callable<Void> task = () -> {
            Viewsheet temp = vs.clone();
            ViewsheetSandbox tempBox = sandbox.createTemporaryBox(temp);

            try {
               assertSame(temp, tempBox.getViewsheet());
               bothCreated.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
               assertSame(temp, tempBox.getViewsheet(),
                          "another thread's clone leaked into this one");
               assertSame(vs, sandbox.getViewsheet(),
                          "a clone became visible through the shared sandbox");
            }
            finally {
               sandbox.releaseTemporaryBox(tempBox);
            }

            return null;
         };

         Future<?> a = pool.submit(task);
         Future<?> b = pool.submit(task);
         a.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
         b.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      }
      finally {
         pool.shutdownNow();
      }

      assertSame(vs, sandbox.getViewsheet());
   }

   /**
    * The temp box must be a shallow view, not a new sandbox: the nested query still locks the real
    * sandbox, executes through the real {@code AssetQuerySandbox} over the original worksheet, and
    * reports under the real id. If any of these stopped being shared, the query would run in a
    * different context than it does today.
    */
   @Test
   void temporaryBoxSharesTheRealSandboxState() {
      Viewsheet temp = vs.clone();
      ViewsheetSandbox tempBox = sandbox.createTemporaryBox(temp);

      try {
         assertEquals(sandbox.getID(), tempBox.getID(), "id must be shared (cache + profiling key)");
         assertSame(sandbox.getAssetQuerySandbox(), tempBox.getAssetQuerySandbox(),
                    "the query must execute through the same AssetQuerySandbox");
         assertSame(sandbox.getAssetEntry(), tempBox.getAssetEntry());
         assertEquals(sandbox.getMode(), tempBox.getMode());
         assertSame(sandbox.getUser(), tempBox.getUser());
         assertSame(lock(sandbox), lock(tempBox), "the sandbox lock must be shared");
      }
      finally {
         sandbox.releaseTemporaryBox(tempBox);
      }
   }

   /**
    * The clone gets the sandbox + metadata listeners for the duration so its edits still reach
    * them, and the real viewsheet's own registration is never disturbed -- the old swap removed
    * the real sheet's listeners and re-added them on restore, which is what made an interleaved
    * restore leave the live sheet unlistened.
    */
   @Test
   void listenersAreAddedToTheCloneAndTheRealSheetIsUntouched() throws Exception {
      int realBefore = listenerCount(vs);
      assertTrue(realBefore > 0, "sandbox should have registered listeners on the real viewsheet");

      Viewsheet temp = vs.clone();
      int tempBaseline = listenerCount(temp);
      ViewsheetSandbox tempBox = sandbox.createTemporaryBox(temp);

      try {
         assertEquals(tempBaseline + 2, listenerCount(temp),
                      "clone should receive the sandbox and metadata listeners");
         assertEquals(realBefore, listenerCount(vs),
                      "real viewsheet lost listeners while the temp box was active");
      }
      finally {
         sandbox.releaseTemporaryBox(tempBox);
      }

      assertEquals(tempBaseline, listenerCount(temp), "clone's listeners were not removed");
      assertEquals(realBefore, listenerCount(vs), "real viewsheet lost listeners");
   }

   /**
    * shrink() prunes qmgrs entries whose assembly is absent from the sandbox's viewsheet, and
    * cancels them. A temp crosstab lives only in the private clone, so without the in-flight
    * registry shrink() -- reachable from resetRuntime(), which only needs the write lock the
    * nested query drops -- would cancel the measure query mid-flight.
    */
   @Test
   void shrinkDoesNotCancelAnInFlightTempAssembly() throws Exception {
      String tempName = CalcTableVSAQuery.TEMP_ASSEMBLY_PREFIX + "SelectionList1_Crosstab";
      QueryManager qmgr = sandbox.getQueryManager(tempName);
      assertNotNull(qmgr);

      sandbox.beginTempAssembly(tempName);

      try {
         sandbox.shrink();
         assertSame(qmgr, sandbox.getQueryManager(tempName),
                    "shrink() cancelled/removed an in-flight temp assembly's QueryManager");
      }
      finally {
         sandbox.endTempAssembly(tempName);
      }
   }

   /**
    * Once the query is done the name must become prunable again, or qmgrs grows without bound --
    * CalcTableVSAQuery's temp names carry a per-invocation id, so a blanket name-based exemption
    * would leak an entry per invocation.
    */
   @Test
   void shrinkPrunesATempAssemblyOnceItIsNoLongerInFlight() throws Exception {
      String tempName = CalcTableVSAQuery.TEMP_ASSEMBLY_PREFIX + "SelectionList1_Crosstab";
      QueryManager qmgr = sandbox.getQueryManager(tempName);
      assertNotNull(qmgr);

      sandbox.beginTempAssembly(tempName);
      sandbox.endTempAssembly(tempName);
      sandbox.shrink();

      assertNotSame(qmgr, sandbox.getQueryManager(tempName),
                    "a finished temp assembly's QueryManager should be pruned by shrink()");
   }

   /**
    * The guard is a reference count, not a flag. If two overlapping invocations ever register the
    * same name, the first to finish must not drop the guard while the second is still running --
    * otherwise shrink() cancels the second one's query, which is the very class of bug this work
    * exists to remove.
    */
   @Test
   void guardSurvivesUntilTheLastRegistrationIsReleased() throws Exception {
      String tempName = CalcTableVSAQuery.TEMP_ASSEMBLY_PREFIX + "Shared_Crosstab";
      QueryManager qmgr = sandbox.getQueryManager(tempName);

      sandbox.beginTempAssembly(tempName);
      sandbox.beginTempAssembly(tempName);
      sandbox.endTempAssembly(tempName);

      sandbox.shrink();
      assertSame(qmgr, sandbox.getQueryManager(tempName),
                 "guard was dropped while a second registration was still in flight");

      sandbox.endTempAssembly(tempName);
      sandbox.shrink();
      assertNotSame(qmgr, sandbox.getQueryManager(tempName),
                    "guard should lift once the last registration is released");
   }

   /**
    * Belt and braces for the same hazard: the temp crosstab name is namespaced per invocation, so
    * two concurrent invocations for the *same* selection assembly cannot collide on a name at all
    * -- which also stops them sharing one QueryManager, where either one's cancel would kill both.
    */
   @Test
   void tempCrosstabNamesAreUniquePerInvocation() {
      String a = CalcTableVSAQuery.getTempCrosstabName(
         "SelectionList1", CalcTableVSAQuery.nextInvocationId(), 0);
      String b = CalcTableVSAQuery.getTempCrosstabName(
         "SelectionList1", CalcTableVSAQuery.nextInvocationId(), 0);

      assertNotEquals(a, b,
                      "two invocations for the same selection assembly must not share a name");
   }

   /** Releasing a null box is a no-op, so the caller's finally never needs a guard. */
   @Test
   void releasingNullIsANoOp() {
      assertDoesNotThrow(() -> sandbox.releaseTemporaryBox(null));
      assertSame(vs, sandbox.getViewsheet());
   }

   private static Object lock(ViewsheetSandbox box) {
      try {
         Field field = ViewsheetSandbox.class.getDeclaredField("thisLock");
         field.setAccessible(true);
         return field.get(box);
      }
      catch(Exception ex) {
         throw new IllegalStateException(ex);
      }
   }

   /** AbstractSheet keeps its listeners in a private weak-reference list with no accessor. */
   @SuppressWarnings("unchecked")
   private static int listenerCount(Viewsheet sheet) throws Exception {
      Field field = AbstractSheet.class.getDeclaredField("listeners");
      field.setAccessible(true);
      List<WeakReference<ActionListener>> listeners =
         (List<WeakReference<ActionListener>>) field.get(sheet);

      if(listeners == null) {
         return 0;
      }

      synchronized(listeners) {
         return (int) listeners.stream().filter(ref -> ref.get() != null).count();
      }
   }
}
