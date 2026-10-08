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
      // B reset the crosstab before A was released, so A doesn't run the query again
      assertInstanceOf(CancelledException.class, r.thrownA, "A's superseded query is dropped");
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

   private static boolean loadedCrosstab(CommandDispatcher dispatcher) {
      return mockingDetails(dispatcher).getInvocations().stream()
         .filter(inv -> inv.getMethod().getName().equals("sendCommand"))
         .map(Invocation::getArguments)
         .anyMatch(args -> Arrays.asList(args).contains(XT) &&
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
