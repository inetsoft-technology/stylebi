/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.viewsheet;

/*
 * Test strategy
 *
 * Bug #76524: right-click a crosstab cell -> Filter (Adhoc Filter) can permanently wedge the
 * viewsheet's loading mask when the STOMP handler thread blocks forever inside business logic
 * (e.g. contending, with no bounded wait, for ViewsheetSandbox's write lock). Because
 * EventAspect.clearLoadingMask's finally only runs when pjp.proceed() returns or throws, a
 * genuine block (not an exception) leaves the mask up forever and the client unrecoverable.
 *
 * This test does not reproduce the crosstab lock contention itself (that needs a live
 * ViewsheetSandbox/lock scenario) -- it verifies the general watchdog added to
 * EventAspect.clearLoadingMask: an aspect target method that simply blocks longer than the
 * configured "loadingmask.watchdog.timeout" must have its loading mask force-cleared and an
 * error/warning surfaced to the client within a bounded time, independent of whether/when the
 * blocked call ever returns.
 *
 * Behavioral guarantees covered:
 *
 * [G1] A @LoadingMask method whose pjp.proceed() blocks past the configured watchdog timeout
 *      causes the aspect to send a ClearLoadingCommand and an INFO MessageCommand to the
 *      client well before pjp.proceed() itself returns. INFO (not WARNING) is required because
 *      WARNING is delivered client-side as a blocking modal dialog, which would contradict the
 *      message's own "you may continue working" text (round-2 review finding #3).
 * [G2] Once pjp.proceed() does return (after being unblocked), postprocess() does not send a
 *      second, redundant ClearLoadingCommand for the same request.
 * [G3] A @LoadingMask method that returns quickly (well under the watchdog timeout) never
 *      triggers the watchdog's ClearLoadingCommand/MessageCommand.
 * [G4] @LoadingMask(watchdogTimeout = 0) disables the watchdog for that endpoint entirely,
 *      even when the global loadingmask.watchdog.timeout would otherwise have fired --
 *      endpoints that execute unbounded runtime queries (e.g. openViewsheet) opt out this way
 *      (round-2 review finding #4).
 * [G5] @LoadingMask(watchdogTimeout = N) with N > 0 overrides the global timeout with N for
 *      that endpoint.
 * [G6] Round-2 review found two more @LoadingMask endpoints whose sole/primary purpose is to
 *      force a fresh ViewsheetSandbox.getVSTableLens() runtime-query re-execution -- the exact
 *      call path implicated as this bug's hang mechanism -- and are therefore in the same
 *      "legitimately unbounded" category as openViewsheet/refreshViewsheet/runQuery:
 *      CrosstabDrillController's /table/drill and /table/drill/cells, and
 *      BaseTableLoadDataController's /table/reload-table-data. This asserts (via reflection on
 *      the real controller methods, not a synthetic fixture) that each still carries
 *      watchdogTimeout = 0, so a future edit that drops the annotation attribute is caught here
 *      rather than only in a live hang.
 */

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.web.viewsheet.command.ClearLoadingCommand;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.web.composer.vs.controller.VSLayoutServiceProxy;
import inetsoft.web.viewsheet.controller.table.BaseTableLoadDataController;
import inetsoft.web.viewsheet.controller.table.CrosstabDrillController;
import inetsoft.web.viewsheet.event.table.DrillCellsEvent;
import inetsoft.web.viewsheet.event.table.DrillEvent;
import inetsoft.web.viewsheet.event.table.LoadTableDataEvent;
import inetsoft.web.vswizard.controller.VSWizardBindingController;
import inetsoft.web.vswizard.controller.VSWizardDialogController;
import inetsoft.web.vswizard.event.OpenVsWizardEvent;
import inetsoft.web.vswizard.event.RefreshBindingFieldsEvent;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({ SpringExtension.class, MockitoExtension.class })
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
public class EventAspectWatchdogTest {
   @Mock private RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock private CoreLifecycleService coreLifecycleService;
   @Mock private ViewsheetService viewsheetService;
   @Mock private VSLayoutServiceProxy vsLayoutService;
   @Mock private EventAspectServiceProxy eventAspectServiceProxy;

   private String savedTimeout;
   private EventAspect aspect;

   @BeforeEach
   void setUp() {
      savedTimeout = SreeEnv.getProperty("loadingmask.watchdog.timeout");
      aspect = new EventAspect(
         runtimeViewsheetRef, coreLifecycleService, viewsheetService, vsLayoutService,
         eventAspectServiceProxy);
   }

   @AfterEach
   void tearDown() {
      SreeEnv.setProperty("loadingmask.watchdog.timeout", savedTimeout);
   }

   // Target class supplying a real, annotated Method object for the mocked
   // ProceedingJoinPoint's MethodSignature -- clearLoadingMask() reads the @LoadingMask
   // annotation via reflection off this Method, so it must be real (not mocked).
   private static class AnnotatedTarget {
      @LoadingMask(true)
      public void forcedMaskMethod() {
      }

      @LoadingMask(value = true, watchdogTimeout = 0)
      public void unboundedMethod() {
      }

      @LoadingMask(value = true, watchdogTimeout = 100)
      public void shortOverrideMethod() {
      }
   }

   private ProceedingJoinPoint mockJoinPoint(CommandDispatcher dispatcher,
                                              String methodName) throws Throwable
   {
      ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
      MethodSignature signature = mock(MethodSignature.class);
      Method method = AnnotatedTarget.class.getMethod(methodName);
      when(signature.getMethod()).thenReturn(method);
      when(pjp.getSignature()).thenReturn(signature);
      when(pjp.getArgs()).thenReturn(new Object[] { dispatcher });
      return pjp;
   }

   private ProceedingJoinPoint mockJoinPoint(CommandDispatcher dispatcher) throws Throwable {
      return mockJoinPoint(dispatcher, "forcedMaskMethod");
   }

   @Test
   void watchdogClearsMaskAndWarnsWhenProceedBlocksPastTimeout() throws Throwable {
      SreeEnv.setProperty("loadingmask.watchdog.timeout", "150");

      CommandDispatcher dispatcher = mock(CommandDispatcher.class);
      CommandDispatcher detached = mock(CommandDispatcher.class);
      when(dispatcher.detach()).thenReturn(detached);

      CountDownLatch proceedStarted = new CountDownLatch(1);
      CountDownLatch releaseProceed = new CountDownLatch(1);
      ProceedingJoinPoint pjp = mockJoinPoint(dispatcher);
      when(pjp.proceed()).thenAnswer(inv -> {
         proceedStarted.countDown();
         // Simulate the real bug: the wrapped call blocks (e.g. on an unbounded
         // ViewsheetSandbox write-lock wait) far longer than the watchdog timeout.
         assertTrue(releaseProceed.await(10, TimeUnit.SECONDS),
                    "test setup error: releaseProceed was never signaled");
         return null;
      });

      Thread worker = new Thread(() -> {
         try {
            aspect.clearLoadingMask(pjp);
         }
         catch(Throwable t) {
            throw new RuntimeException(t);
         }
      }, "test-stomp-handler-thread");
      worker.start();

      assertTrue(proceedStarted.await(2, TimeUnit.SECONDS), "proceed() never started");

      // [G1] Bounded wait: the watchdog must fire well before pjp.proceed() unblocks (10s away).
      // Type must be INFO (non-blocking toast), not WARNING (blocking modal) -- see finding #3.
      verify(detached, timeout(2000)).sendCommand(any(ClearLoadingCommand.class));
      verify(detached, timeout(2000)).sendCommand(argThat(cmd ->
         cmd instanceof MessageCommand &&
         ((MessageCommand) cmd).getType() == MessageCommand.Type.INFO));

      // The wrapped call is left running in the background rather than interrupted.
      assertTrue(worker.isAlive(), "the blocked call should not be killed by the watchdog");

      // Now let the real call complete and confirm the aspect method itself still returns
      // normally, and does not send a second, redundant ClearLoadingCommand [G2].
      releaseProceed.countDown();
      worker.join(5000);
      assertFalse(worker.isAlive(), "aspect method should return once proceed() unblocks");

      verify(detached, times(1)).sendCommand(any(ClearLoadingCommand.class));
   }

   @Test
   void watchdogDoesNotFireForFastMethod() throws Throwable {
      SreeEnv.setProperty("loadingmask.watchdog.timeout", "5000");

      CommandDispatcher dispatcher = mock(CommandDispatcher.class);
      CommandDispatcher detached = mock(CommandDispatcher.class);
      when(dispatcher.detach()).thenReturn(detached);

      ProceedingJoinPoint pjp = mockJoinPoint(dispatcher);
      when(pjp.proceed()).thenReturn(null);

      aspect.clearLoadingMask(pjp);

      // [G3] Fast completion: postprocess() sends the normal clear, but the watchdog itself
      // never gets a chance to fire within the (much larger) configured timeout.
      verify(detached, never()).sendCommand(any(MessageCommand.class));
   }

   @Test
   void watchdogTimeoutZeroOverrideDisablesWatchdog() throws Throwable {
      // Global timeout is short enough that, without the per-endpoint override, the watchdog
      // would fire well within the block below.
      SreeEnv.setProperty("loadingmask.watchdog.timeout", "100");

      CommandDispatcher dispatcher = mock(CommandDispatcher.class);
      CommandDispatcher detached = mock(CommandDispatcher.class);
      when(dispatcher.detach()).thenReturn(detached);

      CountDownLatch proceedStarted = new CountDownLatch(1);
      CountDownLatch releaseProceed = new CountDownLatch(1);
      ProceedingJoinPoint pjp = mockJoinPoint(dispatcher, "unboundedMethod");
      when(pjp.proceed()).thenAnswer(inv -> {
         proceedStarted.countDown();
         assertTrue(releaseProceed.await(10, TimeUnit.SECONDS),
                    "test setup error: releaseProceed was never signaled");
         return null;
      });

      Thread worker = new Thread(() -> {
         try {
            aspect.clearLoadingMask(pjp);
         }
         catch(Throwable t) {
            throw new RuntimeException(t);
         }
      }, "test-stomp-handler-thread-unbounded");
      worker.start();

      assertTrue(proceedStarted.await(2, TimeUnit.SECONDS), "proceed() never started");

      // [G4] Block for far longer than the global 100ms timeout -- with watchdogTimeout = 0,
      // the watchdog must never fire (no MessageCommand, no ClearLoadingCommand, on either
      // dispatcher).
      Thread.sleep(500);
      verify(detached, never()).sendCommand(any(MessageCommand.class));
      verify(dispatcher, never()).sendCommand(any(ClearLoadingCommand.class));
      assertTrue(worker.isAlive(), "unbounded method should still be running, untouched");

      releaseProceed.countDown();
      worker.join(5000);
      assertFalse(worker.isAlive());

      // force=true shows the mask synchronously on the plain dispatcher (not detached), so
      // postprocess()'s normal (non-watchdog) clear -- since the watchdog never fired -- is
      // sent on that same plain dispatcher.
      verify(dispatcher, times(1)).sendCommand(any(ClearLoadingCommand.class));
      verify(detached, never()).sendCommand(any(MessageCommand.class));
   }

   @Test
   void watchdogTimeoutOverrideUsesAnnotationValueInsteadOfGlobal() throws Throwable {
      // Global timeout is long enough that, if the override were ignored, the watchdog would
      // not fire during this test.
      SreeEnv.setProperty("loadingmask.watchdog.timeout", "10000");

      CommandDispatcher dispatcher = mock(CommandDispatcher.class);
      CommandDispatcher detached = mock(CommandDispatcher.class);
      when(dispatcher.detach()).thenReturn(detached);

      CountDownLatch proceedStarted = new CountDownLatch(1);
      CountDownLatch releaseProceed = new CountDownLatch(1);
      ProceedingJoinPoint pjp = mockJoinPoint(dispatcher, "shortOverrideMethod");
      when(pjp.proceed()).thenAnswer(inv -> {
         proceedStarted.countDown();
         assertTrue(releaseProceed.await(10, TimeUnit.SECONDS),
                    "test setup error: releaseProceed was never signaled");
         return null;
      });

      Thread worker = new Thread(() -> {
         try {
            aspect.clearLoadingMask(pjp);
         }
         catch(Throwable t) {
            throw new RuntimeException(t);
         }
      }, "test-stomp-handler-thread-short-override");
      worker.start();

      assertTrue(proceedStarted.await(2, TimeUnit.SECONDS), "proceed() never started");

      // [G5] The 100ms annotation override fires well before the 10s global timeout would.
      verify(detached, timeout(2000)).sendCommand(any(ClearLoadingCommand.class));
      verify(detached, timeout(2000)).sendCommand(argThat(cmd ->
         cmd instanceof MessageCommand &&
         ((MessageCommand) cmd).getType() == MessageCommand.Type.INFO));

      releaseProceed.countDown();
      worker.join(5000);
      assertFalse(worker.isAlive());
   }

   @Test
   void crosstabDrillAndTableReloadEndpointsHaveWatchdogDisabled() throws Throwable {
      // [G6] Reflect on the real, shipped controller methods (not a synthetic fixture) to
      // confirm the round-2-review-requested exemption is actually applied and stays applied.
      Method drill = CrosstabDrillController.class.getMethod(
         "eventHandler", DrillEvent.class, Principal.class, CommandDispatcher.class, String.class);
      Method drillCells = CrosstabDrillController.class.getMethod(
         "drill", DrillCellsEvent.class, Principal.class, CommandDispatcher.class, String.class);
      Method reloadTableData = BaseTableLoadDataController.class.getMethod(
         "eventHandler", LoadTableDataEvent.class, Principal.class, CommandDispatcher.class,
         String.class);

      for(Method method : new Method[] { drill, drillCells, reloadTableData }) {
         LoadingMask mask = method.getAnnotation(LoadingMask.class);
         assertNotNull(mask, method + " is expected to remain @LoadingMask-annotated");
         assertEquals(0, mask.watchdogTimeout(),
                      method + " directly forces a fresh getVSTableLens() runtime-query " +
                      "re-execution and must have the watchdog disabled, like " +
                      "openViewsheet/refreshViewsheet/runQuery");
      }
   }

   @Test
   void wizardBindingAndDialogOpenEndpointsHaveWatchdogDisabled() throws Throwable {
      // [G7] Round 4 systematic sweep: bindingTreeNodeChanged forces a fresh runtime-query
      // re-execution via lockWrite() + resetDataMap(TEMP_CHART_NAME) + CardinalityExecutor/
      // HierarchyExecutor/IntervalExecutor (wizard chart recommendations); createRuntimeSheet
      // opens the wizard dialog via the same CoreLifecycleService.refreshViewsheet(...) family
      // already exempted for openViewsheet/refreshViewsheet. Reflect on the real, shipped
      // controller methods so a future edit that drops the attribute is caught here.
      Method bindingTreeNodeChanged = VSWizardBindingController.class.getMethod(
         "bindingTreeNodeChanged", RefreshBindingFieldsEvent.class, CommandDispatcher.class,
         Principal.class, String.class);
      Method createRuntimeSheet = VSWizardDialogController.class.getMethod(
         "createRuntimeSheet", OpenVsWizardEvent.class, String.class, CommandDispatcher.class,
         Principal.class);

      for(Method method : new Method[] { bindingTreeNodeChanged, createRuntimeSheet }) {
         LoadingMask mask = method.getAnnotation(LoadingMask.class);
         assertNotNull(mask, method + " is expected to remain @LoadingMask-annotated");
         assertEquals(0, mask.watchdogTimeout(),
                      method + " forces a fresh runtime-query re-execution and must have the " +
                      "watchdog disabled, like openViewsheet/refreshViewsheet/runQuery/" +
                      "the crosstab drill and table reload endpoints");
      }
   }
}
