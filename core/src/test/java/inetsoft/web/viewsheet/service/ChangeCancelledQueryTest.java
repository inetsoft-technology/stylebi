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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.*;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.QueryManager;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.CancelledException;
import inetsoft.util.ChangeCancelledException;
import inetsoft.util.CoreTool;
import inetsoft.util.UserMessage;
import inetsoft.util.ThreadContext;
import inetsoft.util.UpgradableReadWriteLock;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.web.composer.ComposerControllerErrorHandler;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.command.LoadTableDataCommand;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.controller.VSCalendarService;
import inetsoft.web.viewsheet.event.ApplySelectionListEvent;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.event.calendar.ImmutableCalendarSelectionEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.invocation.Invocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78024: a newer change of a viewsheet cancels the in-flight queries of an older request.
 * The older request's cancelled query must not show "Query cancelled" as an error, and when the
 * newer change doesn't load the cancelled assembly again, the older request must load it, so it
 * doesn't keep showing old data.
 *
 * Request A applies a calendar and loads a crosstab. It is held where the crosstab's query
 * (created before the cancel) takes the sandbox read lock, after the sandbox released its locks
 * for the fetch. Request B then runs to completion and its real processChange cancels the
 * query. Then A is released.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
class ChangeCancelledQueryTest {
   @BeforeEach
   void setUp() {
      oldPrincipal = ThreadContext.getContextPrincipal();
      AssetDataCache.getCache().clear();
   }

   @AfterEach
   void restore() {
      ThreadContext.setContextPrincipal(oldPrincipal);
   }

   /**
    * B changes the same calendar, so it resets and loads the crosstab itself. A's cancelled
    * query is dropped without an error.
    */
   @Test
   void supersededCalendarChangeShowsNoError() throws Exception {
      Fixture f = fixture();
      VSCalendarService calendarService = calendarService();
      Result r = run(f,
         d -> calendarService.applyCalendar(f.rid, CAL, calendarEvent("m2024-0"), f.principal, d, ""),
         d -> calendarService.applyCalendar(f.rid, CAL, calendarEvent("m2024-1"), f.principal, d, ""));

      assertTrue(r.changeCancelled, "B's change must cancel the crosstab's query");
      assertNull(r.thrownB, "B must complete");
      assertTrue(loadedCrosstab(r.dispatcherB), "B loads the crosstab it reset");
      assertNoError(r);
      // B reset the crosstab before A was released, so A skips it and finishes
      assertNull(r.thrownA, "A must skip the crosstab B loads, but threw " + r.thrownA);
   }

   /**
    * A's calendar on the base table T changes the crosstab on its mirror U and a table X2 on T.
    * B's selection on U resets only the crosstab. A skips the crosstab (B loads it) but must
    * still load X2, which B doesn't. A's loop runs over a hash set, so two names for X2 cover
    * both orders.
    */
   @Test
   void supersededCancelDoesNotStopLoadingOtherTables() throws Exception {
      supersededCancelDoesNotStopLoading("Y78024", false);
   }

   @Test
   void supersededCancelDoesNotStopLoadingOtherTablesInOtherOrder() throws Exception {
      supersededCancelDoesNotStopLoading("Z78024", false);
   }

   /** The same with A a selection on T, the path #77395 caught the cancel on. */
   @Test
   void supersededCancelDoesNotStopLoadingOtherTablesOnSelection() throws Exception {
      supersededCancelDoesNotStopLoading("Y78024", true);
      // the other order of A's loop is covered by the calendar tests, the loop is the same
   }

   /**
    * The user's Cancel during A's crosstab fetch is a plain cancel: it stops A's request, so A
    * loads no other table either, and it shows no error.
    */
   @Test
   void userCancelStopsTheRequest() throws Exception {
      Fixture f = otherTablesFixture("Y78024");
      Result r = run(f,
         d -> calendarService().applyCalendar(f.rid, CAL, calendarEvent("m2024-0"), f.principal,
                                              d, ""),
         d -> f.box.cancelAllQueries());

      assertTrue(r.changeCancelled, "the user's cancel must cancel the crosstab's query");
      assertInstanceOf(CancelledException.class, r.thrownA, "the user's cancel stops A");
      assertFalse(r.thrownA instanceof ChangeCancelledException,
                  "the user's cancel must not be skipped like a newer request's");
      assertFalse(loaded(r.dispatcherA, XT), "A must not load the cancelled crosstab");
      assertFalse(loaded(r.dispatcherA, "Y78024"), "A must not go on loading other tables");
      assertNoError(r);
   }

   /**
    * Changes that keep cancelling the crosstab's query while a calendar change loads it: the
    * request warns (a WARNING message, not an ERROR) and still loads its other tables.
    */
   @Test
   void changesThatKeepCancellingWarnTheRequest() throws Exception {
      Fixture f = otherTablesFixture("Y78024");
      CancellingQueryManager qmgr = new CancellingQueryManager();
      queryManagers(f.box).put(XT, qmgr);
      CommandDispatcher d = dispatcher();
      Exception thrown = null;
      qmgr.thread = Thread.currentThread();

      try {
         calendarService().applyCalendar(f.rid, CAL, calendarEvent("m2024-0"), f.principal, d, "");
      }
      catch(Exception ex) {
         thrown = ex;
      }
      finally {
         qmgr.thread = null;
      }

      assertTrue(qmgr.cancels > 3, "the query must be run again: " + qmgr.cancels);
      assertTrue(thrown == null || thrown instanceof ChangeCancelledException,
                 "only a skipped query may end the request: " + thrown);
      List<MessageCommand> messages = mockingDetails(d).getInvocations().stream()
         .flatMap(inv -> Arrays.stream(inv.getArguments()))
         .filter(MessageCommand.class::isInstance)
         .map(MessageCommand.class::cast)
         .toList();
      assertTrue(messages.stream().anyMatch(m -> m.getType() == MessageCommand.Type.WARNING &&
         m.getMessage().contains(XT)), "the user must be warned: " + messages);
      assertTrue(loaded(d, "Y78024"), "the other tables must still load");
      Result r = new Result();
      r.dispatcherA = d;
      r.dispatcherB = dispatcher();
      r.thrownA = thrown;
      assertNoError(r);
   }

   private void supersededCancelDoesNotStopLoading(String x2, boolean selectionA)
      throws Exception
   {
      Fixture f = otherTablesFixture(x2);
      Viewsheet vs = f.box.getViewsheet();
      String value = firstValue(vs, SEL + "T");

      VSCalendarService calendarService = calendarService();
      ApplySelectionListEvent event = selectionEvent("NAMED");
      ApplySelectionListEvent eventA = selectionEvent(value);
      eventA.setEventSource(SEL + "T");
      Call a = selectionA ?
         d -> selectionService.applySelection(f.rid, SEL + "T", eventA, f.principal, d, "") :
         d -> calendarService.applyCalendar(f.rid, CAL, calendarEvent("m2024-0"), f.principal, d, "");
      Result r = run(f, a,
         d -> selectionService.applySelection(f.rid, SEL, event, f.principal, d, ""));

      assertTrue(r.changeCancelled, x2 + ": B's change must cancel the crosstab's query");
      assertNull(r.thrownB, x2 + ": B must complete");
      assertTrue(loaded(r.dispatcherB, XT), x2 + ": B loads the crosstab it reset");
      assertFalse(loaded(r.dispatcherB, x2), x2 + ": B must not load the table it didn't change");
      assertNull(r.thrownA, x2 + ": A must skip the crosstab B loads, but threw " + r.thrownA);
      assertTrue(loaded(r.dispatcherA, x2), x2 + ": A must load the table B doesn't");
      assertNoError(r);
   }

   /**
    * A's selection on T changes the crosstab. While it loads, a newer query of the crosstab
    * that isn't a change (e.g. an export of the live viewsheet) cancels A's query. Nothing
    * resets the crosstab, and that query doesn't send it to the client, so A must load it.
    */
   @Test
   void newerQueryWithoutResetDoesNotLeaveTableUnloaded() throws Exception {
      Fixture f = otherTablesFixture("Y78024");
      Viewsheet vs = f.box.getViewsheet();
      ApplySelectionListEvent eventA = selectionEvent(firstValue(vs, SEL + "T"));
      eventA.setEventSource(SEL + "T");
      Object[] exported = { null };
      Result r = run(f,
         d -> selectionService.applySelection(f.rid, SEL + "T", eventA, f.principal, d, ""),
         d -> exported[0] = f.box.getData(XT));

      assertTrue(r.changeCancelled, "the newer query must cancel A's query");
      assertNull(r.thrownB, "the newer query must complete");
      assertNotNull(exported[0], "the newer query must get the crosstab's data");
      assertNull(r.thrownA, "A must load the crosstab, but threw " + r.thrownA);
      assertTrue(loadedCrosstab(r.dispatcherA), "A must send the crosstab's data");
      assertNoError(r);
   }

   /**
    * The calendar on the base table T, the crosstab on its mirror U, a table x2 on T, a
    * selection on U and one on T.
    */
   private Fixture otherTablesFixture(String x2) throws Exception {
      Fixture f = fixture();
      Viewsheet vs = f.box.getViewsheet();
      DataVSAssembly xt = (DataVSAssembly) vs.getAssembly(XT);
      String base = xt.getTableName();
      String mirror =
         ((TableVSAssembly) vs.getAssembly("TableView1")).getSourceInfo().getSource();
      assertNotEquals(base, mirror, "TableView1 must be on a mirror of the base table");
      xt.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, mirror));
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly("TableView1").clone();
      table.getVSAssemblyInfo().setName(x2);
      table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, base));
      vs.addAssembly(table);
      SelectionListVSAssembly selection = new SelectionListVSAssembly(vs, SEL);
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) selection.getInfo();
      info.setTableName(mirror);
      info.setDataRef(new ColumnRef(new AttributeRef(null, "type")));
      vs.addAssembly(selection);
      SelectionListVSAssembly baseSelection = new SelectionListVSAssembly(vs, SEL + "T");
      SelectionListVSAssemblyInfo baseInfo =
         (SelectionListVSAssemblyInfo) baseSelection.getInfo();
      baseInfo.setTableName(base);
      baseInfo.setDataRef(new ColumnRef(new AttributeRef(null, "name")));
      vs.addAssembly(baseSelection);
      f.box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
      return f;
   }

   /**
    * B selects on a table the crosstab doesn't use, so it cancels the crosstab's query but
    * doesn't load the crosstab. A must load it, not drop it.
    */
   @Test
   void unrelatedChangeDoesNotLeaveCancelledCrosstabUnloaded() throws Exception {
      Fixture f = fixture();
      Viewsheet vs = f.box.getViewsheet();
      String table = ((TableVSAssembly) vs.getAssembly("TableView1")).getSourceInfo().getSource();
      assertNotEquals(table, ((DataVSAssembly) vs.getAssembly(XT)).getTableName(),
                      "the selection must be on another table than the crosstab's");
      SelectionListVSAssembly selection = new SelectionListVSAssembly(vs, SEL);
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) selection.getInfo();
      info.setTableName(table);
      info.setDataRef(new ColumnRef(new AttributeRef(null, "type")));
      vs.addAssembly(selection);
      f.box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);

      VSCalendarService calendarService = calendarService();
      ApplySelectionListEvent event = selectionEvent("NAMED");
      Result r = run(f,
         d -> calendarService.applyCalendar(f.rid, CAL, calendarEvent("m2024-0"), f.principal, d, ""),
         d -> selectionService.applySelection(f.rid, SEL, event, f.principal, d, ""));

      assertTrue(r.changeCancelled, "B's change must cancel the crosstab's query");
      assertNull(r.thrownB, "B must complete");
      assertFalse(loadedCrosstab(r.dispatcherB), "B must not load the crosstab it didn't change");
      assertNull(r.thrownA, "A must load the crosstab B's change cancelled, but threw " + r.thrownA);
      assertTrue(loadedCrosstab(r.dispatcherA), "A must send the crosstab's data");
      assertNoError(r);
   }

   /** A cancel a cluster proxy wrapped is found in the cause chain. */
   @Test
   void handlerDropsWrappedCancel() throws Exception {
      CommandDispatcher dispatcher = dispatcher();
      Exception wrapped = new RuntimeException(new ExecutionException(
         new CancelledException("Query cancelled")));

      assertDoesNotThrow(() -> new ComposerControllerErrorHandler()
         .handleException(wrapped, dispatcher));
      verify(dispatcher, never()).sendCommand(any());
      verify(dispatcher, never()).sendCommand(any(), any());

      CommandDispatcher dispatcher2 = dispatcher();
      Exception other = new IllegalStateException("boom");
      assertThrows(IllegalStateException.class, () -> new ComposerControllerErrorHandler()
         .handleException(other, dispatcher2));
      verify(dispatcher2).sendCommand(any(MessageCommand.class));
   }

   /** The user's Cancel during the fetch is a plain cancel: the query isn't run again. */
   @Test
   void userCancelIsNotRunAgain() throws Exception {
      Fixture f = fixture();
      Fetch fetch = fetch(f, 2, (attempt, box, qmgr) -> box.cancelAllQueries());

      assertEquals(1, fetch.qmgr.attempts, "a user's cancel must not run the query again");
      assertInstanceOf(CancelledException.class, fetch.thrown);
      assertFalse(fetch.thrown instanceof ChangeCancelledException,
                  "a user's cancel must stop the request, not be skipped");
   }

   /**
    * A change cancels the fetch without a reset, so it runs again, and the user's Cancel lands
    * during the run again: the run again stops like any user cancel, it isn't run once more
    * or skipped.
    */
   @Test
   void userCancelDuringRunAgainStops() throws Exception {
      Fixture f = fixture();
      Fetch fetch = fetch(f, 3, (attempt, box, qmgr) -> {
         if(attempt == 1) {
            qmgr.cancelForChange();
         }
         else {
            box.cancelAllQueries();
         }
      });

      assertEquals(2, fetch.qmgr.attempts, "a user's cancel must not run the query again");
      assertInstanceOf(CancelledException.class, fetch.thrown);
      assertFalse(fetch.thrown instanceof ChangeCancelledException,
                  "a user's cancel must stop the request, not be skipped");
   }

   /**
    * A change cancels the fetch but its reset lands only while the query runs again (its
    * cancel came first). The run's data is dropped, the change loads the assembly.
    */
   @Test
   void runAgainIsDroppedWhenTheChangeResetsDuringIt() throws Exception {
      Fixture f = fixture();
      Fetch fetch = fetch(f, 2, (attempt, box, qmgr) -> {
         if(attempt == 1) {
            qmgr.cancelForChange();
         }
         else {
            long now = System.currentTimeMillis();

            while(System.currentTimeMillis() <= now) {
               Thread.onSpinWait();
            }

            resetTimes(box).put(XT, System.currentTimeMillis());
         }
      });

      assertEquals(2, fetch.qmgr.attempts, "the change-cancelled query must run again");
      assertInstanceOf(ChangeCancelledException.class, fetch.thrown,
                       "the run's data must be dropped once the change reset the assembly");
   }

   /**
    * A newer query of the assembly that cancels the fetch, with no reset, makes it run again,
    * and the run again doesn't cancel that query back.
    */
   @Test
   void newerQueryCancelRunsAgainWithoutCancellingIt() throws Exception {
      Fixture f = fixture();
      long[] after = { -1 };
      Fetch fetch = fetch(f, 2, (attempt, box, qmgr) -> {
         if(attempt == 1) {
            qmgr.cancelForQuery();
            after[0] = qmgr.getCancelCount();
         }
      });

      assertNull(fetch.thrown, "the query must run again, but threw " + fetch.thrown);
      assertNotNull(fetch.data, "the crosstab must get data");
      assertEquals(2, fetch.qmgr.attempts, "the query must run again once");
      assertEquals(after[0], fetch.qmgr.getCancelCount(),
                   "the run again must not cancel the newer query");
   }

   /** Every unrelated change that cancels the fetch makes it run again, more than 3 times. */
   @Test
   void manyUnrelatedChangeCancelsStillLoad() throws Exception {
      Fixture f = fixture();
      Fetch fetch = fetch(f, 6, (attempt, box, qmgr) -> {
         if(attempt <= 5) {
            qmgr.cancelForChange();
         }
      });

      assertNull(fetch.thrown, "the query must run until no change cancels it");
      assertNotNull(fetch.data, "the crosstab must get data");
      assertEquals(6, fetch.qmgr.attempts);
      assertNull(fetch.message, "no warning");
   }

   /** Changes that keep cancelling a fetch end with a warning, not silently. */
   @Test
   void changesThatKeepCancellingEndWithWarning() throws Exception {
      Fixture f = fixture();
      Fetch fetch = fetch(f, 100, (attempt, box, qmgr) -> qmgr.cancelForChange());

      assertInstanceOf(ChangeCancelledException.class, fetch.thrown);
      assertTrue(fetch.qmgr.attempts > 3 && fetch.qmgr.attempts < 100,
                 "bounded, but not by 3: " + fetch.qmgr.attempts);
      assertNotNull(fetch.message, "the user must be warned");
      assertTrue(fetch.message.getMessage().contains(XT), fetch.message.getMessage());
   }

   interface Hold {
      void held(int attempt, ViewsheetSandbox box, HoldingQueryManager qmgr) throws Exception;
   }

   private static final class Fetch {
      HoldingQueryManager qmgr;
      Object data;
      Throwable thrown;
      UserMessage message;
   }

   /**
    * Fetch the crosstab's data on another thread, holding each of its first attempts where its
    * query checks for a cancel, and run the hold on this thread meanwhile.
    */
   private static Fetch fetch(Fixture f, int holds, Hold hold) throws Exception {
      HoldingQueryManager qmgr = new HoldingQueryManager(holds);
      queryManagers(f.box).put(XT, qmgr);
      f.box.resetDataMap(XT);
      Fetch fetch = new Fetch();
      fetch.qmgr = qmgr;
      ExecutorService thread = Executors.newSingleThreadExecutor();

      try {
         Future<?> future = thread.submit(() -> {
            ThreadContext.setContextPrincipal(f.principal);
            qmgr.heldThread = Thread.currentThread();
            CoreTool.getUserMessage();

            try {
               fetch.data = f.box.getData(XT);
            }
            finally {
               fetch.message = CoreTool.getUserMessage();
            }

            return null;
         });

         while(!future.isDone()) {
            if(qmgr.held.tryAcquire(100, TimeUnit.MILLISECONDS)) {
               // a cancel must be later than the query, to the millisecond
               while(System.currentTimeMillis() <= qmgr.heldAt) {
                  Thread.onSpinWait();
               }

               hold.held(qmgr.attempts, f.box, qmgr);
               qmgr.release.release();
            }
         }

         fetch.thrown = outcome(future);
         return fetch;
      }
      finally {
         qmgr.release.release(1000);
         thread.shutdownNow();
         thread.awaitTermination(WAIT, TimeUnit.SECONDS);
      }
   }

   interface Call {
      void call(CommandDispatcher d) throws Exception;
   }

   private static final class Result {
      boolean changeCancelled;
      Throwable thrownA;
      Throwable thrownB;
      CommandDispatcher dispatcherA;
      CommandDispatcher dispatcherB;
   }

   private Result run(Fixture f, Call a, Call b) throws Exception {
      HeldLock lock = installLock(f.box);
      ReportingQueryManager qmgr = new ReportingQueryManager();
      queryManagers(f.box).put(XT, qmgr);
      Result r = new Result();
      r.dispatcherA = dispatcher();
      r.dispatcherB = dispatcher();
      ExecutorService threads = Executors.newFixedThreadPool(2);

      try {
         lock.armed = true;
         Future<?> fa = threads.submit(() -> {
            ThreadContext.setContextPrincipal(f.principal);
            lock.heldThread = Thread.currentThread();
            a.call(r.dispatcherA);
            return null;
         });
         assertTrue(lock.held.await(WAIT, TimeUnit.SECONDS),
                    "A never reached the crosstab's data fetch");

         // B's cancel must be later than A's query, to the millisecond
         while(System.currentTimeMillis() <= lock.heldAt) {
            Thread.onSpinWait();
         }

         Future<?> fb = threads.submit(() -> {
            ThreadContext.setContextPrincipal(f.principal);
            qmgr.bThread = Thread.currentThread();
            b.call(r.dispatcherB);
            return null;
         });
         r.thrownB = outcome(fb);
         r.changeCancelled = qmgr.bCancels > 0;
         lock.release.countDown();
         r.thrownA = outcome(fa);
         return r;
      }
      finally {
         lock.release.countDown();
         threads.shutdownNow();
         threads.awaitTermination(WAIT, TimeUnit.SECONDS);
      }
   }

   private static Throwable outcome(Future<?> future) throws Exception {
      try {
         future.get(WAIT, TimeUnit.SECONDS);
         return null;
      }
      catch(ExecutionException ex) {
         return ex.getCause();
      }
   }

   /** Neither request shows an error, including what the STOMP error handler makes of A's. */
   private static void assertNoError(Result r) throws Exception {
      assertNoErrorMessage(r.dispatcherA, "A");
      assertNoErrorMessage(r.dispatcherB, "B");

      if(r.thrownA instanceof Exception ex) {
         CommandDispatcher handled = dispatcher();
         Exception rethrown = null;

         try {
            new ComposerControllerErrorHandler().handleException(ex, handled);
         }
         catch(Exception e) {
            rethrown = e;
         }

         assertNoErrorMessage(handled, "the error handler for A's " + ex);
         assertNull(rethrown, "the error handler rethrew A's " + ex);
      }
      else {
         assertNull(r.thrownA, "A threw " + r.thrownA);
      }
   }

   private static void assertNoErrorMessage(CommandDispatcher dispatcher, String who) {
      for(Invocation inv : mockingDetails(dispatcher).getInvocations()) {
         for(Object arg : inv.getArguments()) {
            if(arg instanceof MessageCommand m) {
               assertNotEquals(MessageCommand.Type.ERROR, m.getType(),
                               who + " sent the error \"" + m.getMessage() + "\"");
            }
         }
      }
   }

   private static String firstValue(Viewsheet vs, String name) {
      SelectionList list = ((SelectionListVSAssembly) vs.getAssembly(name)).getSelectionList();
      assertNotNull(list, name + " must have values");
      assertTrue(list.getSelectionValueCount() > 0, name + " must have values");
      return list.getSelectionValue(0).getValue();
   }

   private static boolean loadedCrosstab(CommandDispatcher dispatcher) {
      return loaded(dispatcher, XT);
   }

   private static boolean loaded(CommandDispatcher dispatcher, String name) {
      return mockingDetails(dispatcher).getInvocations().stream()
         .filter(inv -> inv.getMethod().getName().equals("sendCommand"))
         .map(Invocation::getArguments)
         .anyMatch(args -> Arrays.asList(args).contains(name) &&
            Arrays.stream(args).anyMatch(LoadTableDataCommand.class::isInstance));
   }

   /**
    * The calendar and the crosstab are on the base embedded table, with a date column added
    * for the calendar.
    */
   private Fixture fixture() throws Exception {
      Fixture f = new Fixture();
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      f.rid = vsResource.getRuntimeId();
      f.principal = ThreadContext.getPrincipal();
      f.box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = f.box.getViewsheet();
      SourceInfo tvSource = ((TableVSAssembly) vs.getAssembly("TableView1")).getSourceInfo();
      Worksheet ws0 = f.box.getWorksheet();
      Assembly base = ws0.getAssembly(tvSource.getSource());

      while(base instanceof MirrorTableAssembly m) {
         base = ws0.getAssembly(m.getAssemblyName());
      }

      SourceInfo source = new SourceInfo(SourceInfo.ASSET, null, base.getName());
      addDateColumn(f.box, vs, source.getSource());

      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, XT);
      crosstab.setSourceInfo(source);
      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      VSDimensionRef dim = new VSDimensionRef(new AttributeRef(null, "type"));
      dim.setGroupColumnValue("type");
      VSAggregateRef sum = new VSAggregateRef();
      sum.setDataRef(new AttributeRef(null, "wind"));
      sum.setColumnValue("wind");
      sum.setFormulaValue("Sum");
      sum.setOriginalDataType(XSchema.INTEGER);
      cinfo.setDesignRowHeaders(new DataRef[] { dim });
      cinfo.setDesignAggregates(new DataRef[] { sum });
      crosstab.setVSCrosstabInfo(cinfo);
      vs.addAssembly(crosstab);

      CalendarVSAssembly cal = new CalendarVSAssembly(vs, CAL);
      CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) cal.getVSAssemblyInfo();
      info.setTableName(source.getSource());
      ColumnRef date = new ColumnRef(new AttributeRef(null, "d"));
      date.setDataType(XSchema.DATE);
      info.setDataRef(date);
      vs.addAssembly(cal);

      f.box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
      assertNotNull(f.box.getData(XT), "the crosstab must have data");
      return f;
   }

   private static void addDateColumn(ViewsheetSandbox box, Viewsheet vs, String tname) {
      Set<Worksheet> sheets = new LinkedHashSet<>();
      sheets.add(box.getWorksheet());

      if(vs.getBaseWorksheet() != null) {
         sheets.add(vs.getBaseWorksheet());
      }

      for(Worksheet ws : sheets) {
         EmbeddedTableAssembly table = (EmbeddedTableAssembly) ws.getAssembly(tname);
         XEmbeddedTable old = table.getEmbeddedData();
         old.moreRows(XTable.EOT);
         int cols = old.getColCount();
         int rows = old.getRowCount();
         String[] types = new String[cols + 1];
         Object[][] data = new Object[rows][cols + 1];

         for(int c = 0; c < cols; c++) {
            types[c] = old.getDataType(c);
         }

         types[cols] = XSchema.DATE;

         for(int row = 0; row < rows; row++) {
            for(int c = 0; c < cols; c++) {
               data[row][c] = old.getObject(row, c);
            }

            data[row][cols] = row == 0 ? "d" :
               java.sql.Date.valueOf(java.time.LocalDate.of(2024, 1, 1).plusDays(row % 90));
         }

         table.setEmbeddedData(new XEmbeddedTable(types, data));
         ColumnRef d = new ColumnRef(new AttributeRef(null, "d"));
         d.setDataType(XSchema.DATE);

         if(table.getColumnSelection(false).getAttribute("d") == null) {
            table.getColumnSelection(false).addAttribute(d);
         }
      }

      vs.resetWS();
   }

   private VSCalendarService calendarService() {
      return new VSCalendarService(coreLifecycleService, mock(VSObjectPropertyService.class),
                                   viewsheetService);
   }

   private static ApplySelectionListEvent selectionEvent(String value) {
      ApplySelectionListEvent event = new ApplySelectionListEvent();
      event.setType(ApplySelectionListEvent.Type.APPLY);
      ApplySelectionListEvent.Value v = new ApplySelectionListEvent.Value();
      v.setValue(new String[] { value });
      v.setSelected(true);
      event.setValues(new ArrayList<>(List.of(v)));
      event.setEventSource(SEL);
      return event;
   }

   private static ImmutableCalendarSelectionEvent calendarEvent(String month) {
      return ImmutableCalendarSelectionEvent.builder()
         .dates(new String[] { month })
         .currentDate1(month.replace("m", ""))
         .eventSource(CAL)
         .build();
   }

   private static CommandDispatcher dispatcher() {
      CommandDispatcher d = mock(CommandDispatcher.class);
      when(d.detach()).thenReturn(d);
      when(d.iterator()).thenAnswer(inv -> Collections.emptyIterator());
      return d;
   }

   private static HeldLock installLock(ViewsheetSandbox box) throws Exception {
      Field field = ViewsheetSandbox.class.getDeclaredField("thisLock");
      field.setAccessible(true);
      HeldLock lock = new HeldLock();
      field.set(box, lock);
      return lock;
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Long> resetTimes(ViewsheetSandbox box) throws Exception {
      Field field = ViewsheetSandbox.class.getDeclaredField("tmap");
      field.setAccessible(true);
      return (Map<String, Long>) field.get(box);
   }

   @SuppressWarnings("unchecked")
   private static Map<String, QueryManager> queryManagers(ViewsheetSandbox box) throws Exception {
      Field field = ViewsheetSandbox.class.getDeclaredField("qmgrs");
      field.setAccessible(true);
      return (Map<String, QueryManager>) field.get(box);
   }

   private static boolean calledFrom(String className, String method) {
      return StackWalker.getInstance().walk(frames -> frames.anyMatch(
         f -> f.getClassName().equals(className) && f.getMethodName().equals(method)));
   }

   /**
    * Holds thread A once, where the crosstab's query takes the sandbox read lock. The sandbox
    * released its locks for the fetch by then.
    */
   private static final class HeldLock extends UpgradableReadWriteLock {
      HeldLock() {
         super(JavaScriptEngine::isScriptThread);
      }

      @Override
      public void lockRead() {
         if(armed && Thread.currentThread() == heldThread &&
            calledFrom(CrosstabVSAQuery.class.getName(), "getTableLens"))
         {
            armed = false;
            heldAt = System.currentTimeMillis();
            held.countDown();

            try {
               release.await(WAIT, TimeUnit.SECONDS);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }

         super.lockRead();
      }

      volatile boolean armed;
      volatile Thread heldThread;
      volatile long heldAt;
      final CountDownLatch held = new CountDownLatch(1);
      final CountDownLatch release = new CountDownLatch(1);
   }

   /** The crosstab's query manager. It counts the cancels made on thread B. */
   private static final class ReportingQueryManager extends QueryManager {
      ReportingQueryManager() {
         super(true);
      }

      @Override
      public int cancel() {
         int count = super.cancel();

         if(Thread.currentThread() == bThread) {
            bCancels++;
         }

         return count;
      }

      volatile Thread bThread;
      volatile int bCancels;
   }

   /**
    * The crosstab's query manager. It holds the fetching thread where each of its first
    * attempts checks for a cancel (the query was created by then), and counts the attempts.
    */
   /**
    * The crosstab's query manager. Each time the crosstab's query checks for a cancel on the
    * thread, a newer change cancels it first, as if changes kept coming.
    */
   private static final class CancellingQueryManager extends QueryManager {
      CancellingQueryManager() {
         super(true);
      }

      @Override
      public long lastCancelled() {
         if(Thread.currentThread() == thread && !inside &&
            HoldingQueryManager.isQueryCancelCheck())
         {
            inside = true;

            try {
               // later than the query, to the millisecond
               long now = System.currentTimeMillis();

               while(System.currentTimeMillis() <= now) {
                  Thread.onSpinWait();
               }

               cancelForChange();
               cancels++;
            }
            finally {
               inside = false;
            }
         }

         return super.lastCancelled();
      }

      volatile Thread thread;
      boolean inside;
      int cancels;
   }

   private static final class HoldingQueryManager extends QueryManager {
      HoldingQueryManager(int holds) {
         super(true);
         this.holds = holds;
      }

      @Override
      public long lastCancelled() {
         if(Thread.currentThread() == heldThread && isQueryCancelCheck()) {
            attempts++;

            if(attempts <= holds) {
               heldAt = System.currentTimeMillis();
               held.release();

               try {
                  release.tryAcquire(WAIT, TimeUnit.SECONDS);
               }
               catch(InterruptedException ex) {
                  Thread.currentThread().interrupt();
               }
            }
         }

         return super.lastCancelled();
      }

      // VSAQuery.isCancelled() called by CrosstabVSAQuery.getTableLens(), once per attempt
      private static boolean isQueryCancelCheck() {
         return StackWalker.getInstance().walk(frames -> {
            List<StackWalker.StackFrame> callers = frames.skip(2).limit(3).toList();
            return callers.size() == 3 && callers.get(0).getMethodName().equals("isCancelled") &&
               callers.get(1).getClassName().equals(CrosstabVSAQuery.class.getName()) &&
               callers.get(1).getMethodName().equals("getTableLens");
         });
      }

      private final int holds;
      volatile Thread heldThread;
      volatile int attempts;
      volatile long heldAt;
      final Semaphore held = new Semaphore(0);
      final Semaphore release = new Semaphore(0);
   }

   private static final class Fixture {
      String rid;
      Principal principal;
      ViewsheetSandbox box;
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   @Autowired
   VSSelectionService selectionService;
   @Autowired
   CoreLifecycleService coreLifecycleService;
   @Autowired
   ViewsheetService viewsheetService;

   private Principal oldPrincipal;
   private static final String XT = "X78024";
   private static final String SEL = "S78024";
   private static final String CAL = "C78024";
   private static final long WAIT = 30;
}
