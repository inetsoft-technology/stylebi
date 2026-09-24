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

import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * Companion to {@link CrosstabVSAssemblySourceLockOrderingTest} for bug #77030 that runs the
 * real chain end to end: an opened runtime viewsheet with an embedded worksheet table, a
 * table assembly on it, and a crosstab bound to that table assembly
 * ({@link SourceInfo#VS_ASSEMBLY}). Nothing on the query path is stubbed; the sandbox is only
 * spied to observe {@code getTableData()} and {@code restoreLocks()}.
 *
 * <p>The concurrent case does not let the cycle actually form: the writer takes the real write
 * lock inside the reader's release window and only calls {@code VSCrosstabInfo.update()} if the
 * reader does not own the crosstab info monitor, so a regression fails instead of hanging.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class CrosstabVSAssemblySourceRealChainTest {
   /**
    * The crosstab bound to the table assembly fetches its source outside the crosstab info
    * monitor, and returns the same data as a crosstab and charts bound to the worksheet table.
    */
   @Test
   void vsAssemblySourceIsFetchedOutsideTheCrosstabInfoMonitor() throws Exception {
      ViewsheetSandbox box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      SourceInfo wsSource =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      CrosstabVSAssembly ctVs = crosstab(vs, "CrosstabVS", vsSource());
      crosstab(vs, "CrosstabWS", wsSource);
      chart(vs, "ChartVS", vsSource());
      chart(vs, "ChartWS", wsSource);

      // the full path: getData -> doExecuteData -> updateAssembly/refreshMetaData -> query
      assertEquals(EXPECTED, cells(box.getData("CrosstabVS")), "vs assembly bound crosstab");
      assertEquals(EXPECTED, cells(box.getData("CrosstabWS")), "worksheet bound crosstab");
      assertEquals(EXPECTED, cells(box.getData("ChartVS")), "vs assembly bound chart");
      assertEquals(EXPECTED, cells(box.getData("ChartWS")), "worksheet bound chart");

      ViewsheetSandbox spy = Mockito.spy(box);
      AtomicInteger fetches = new AtomicInteger();
      AtomicInteger fetchesUnderMonitor = new AtomicInteger();

      doAnswer(inv -> {
         fetches.incrementAndGet();

         if(Thread.holdsLock(ctVs.getVSCrosstabInfo())) {
            fetchesUnderMonitor.incrementAndGet();
         }

         return inv.callRealMethod();
      }).when(spy).getTableData(anyString());

      box.resetDataMap(TABLE); // force the source to be executed again
      TableLens lens = new CrosstabVSAQuery(spy, "CrosstabVS", false).getTableLens();

      assertEquals(1, fetches.get(), "the source assembly must be fetched exactly once");
      assertEquals(0, fetchesUnderMonitor.get(),
                   "the source assembly was executed while holding the VSCrosstabInfo monitor");
      assertEquals(EXPECTED, cells(lens), "crosstab data");
   }

   /**
    * A writer takes the write lock while the reader has released it to execute the source
    * assembly, then needs the crosstab info monitor, as
    * {@code doExecuteData(crosstab) -> refreshMetaData -> VSCrosstabInfo.update()} does.
    */
   @Test
   void writerInSourceFetchWindowDoesNotNeedAMonitorTheReaderHolds() throws Exception {
      ViewsheetSandbox box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      CrosstabVSAssembly ctVs = crosstab(vs, "CrosstabVS", vsSource());
      box.getData("CrosstabVS"); // fill in the runtime refs
      VSCrosstabInfo cinfo = ctVs.getVSCrosstabInfo();
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(new ColumnRef(new AttributeRef(null, "type")));
      columns.addAttribute(new ColumnRef(new AttributeRef(null, "wind")));

      ViewsheetSandbox spy = Mockito.spy(box);
      AtomicReference<Thread> reader = new AtomicReference<>();
      AtomicReference<Thread> writer = new AtomicReference<>();
      AtomicReference<Boolean> readerOwnedMonitor = new AtomicReference<>();
      AtomicReference<Throwable> writerError = new AtomicReference<>();

      doAnswer(inv -> {
         // the first restore on the reader is inside the source fetch, with every sandbox
         // lock released
         if(Thread.currentThread() == reader.get() && writer.get() == null) {
            Thread readerThread = Thread.currentThread();
            Thread thread = new Thread(() -> {
               try {
                  box.lockWrite();

                  try {
                     boolean owned = ownsMonitor(readerThread, cinfo);
                     readerOwnedMonitor.set(owned);

                     // calling update() here would deadlock; fail instead of hanging
                     if(!owned) {
                        cinfo.update(vs, columns, null, true, null, null);
                     }
                  }
                  finally {
                     box.unlockWrite();
                  }
               }
               catch(Throwable ex) {
                  writerError.set(ex);
               }
            }, "b77030-real-writer");
            thread.setDaemon(true);
            writer.set(thread);
            thread.start();
            thread.join(TimeUnit.SECONDS.toMillis(CAP));
         }

         return inv.callRealMethod();
      }).when(spy).restoreLocks();

      box.resetDataMap(TABLE);
      FutureTask<TableLens> task =
         new FutureTask<>(() -> new CrosstabVSAQuery(spy, "CrosstabVS", false).getTableLens());
      Thread thread = new Thread(task, "b77030-real-reader");
      thread.setDaemon(true);
      reader.set(thread);
      thread.start();

      TableLens lens = task.get(CAP * 2, TimeUnit.SECONDS);
      assertNotNull(writer.get(), "the reader never released the sandbox lock to fetch the source");
      assertFalse(writer.get().isAlive(), "the writer did not finish");
      assertNull(writerError.get(), "the writer failed");
      assertEquals(Boolean.FALSE, readerOwnedMonitor.get(),
                   "the reader held the VSCrosstabInfo monitor while it had released the " +
                   "sandbox lock; a writer needing the monitor would deadlock against it");
      assertEquals(EXPECTED, cells(lens), "crosstab data");
   }

   private static boolean ownsMonitor(Thread thread, Object monitor) {
      ThreadInfo info = ManagementFactory.getThreadMXBean()
         .getThreadInfo(new long[] { thread.getId() }, true, false)[0];

      for(MonitorInfo locked : info.getLockedMonitors()) {
         if(locked.getIdentityHashCode() == System.identityHashCode(monitor) &&
            locked.getClassName().equals(monitor.getClass().getName()))
         {
            return true;
         }
      }

      return false;
   }

   private static SourceInfo vsSource() {
      return new SourceInfo(SourceInfo.VS_ASSEMBLY, null, Assembly.TABLE_VS_BOUND + TABLE);
   }

   private static CrosstabVSAssembly crosstab(Viewsheet vs, String name, SourceInfo source) {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, name);
      crosstab.setSourceInfo(source);
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      VSDimensionRef dim = new VSDimensionRef(new ColumnRef(new AttributeRef(null, "type")));
      dim.setGroupColumnValue("type");
      cinfo.setDesignRowHeaders(new DataRef[] { dim });
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      cinfo.setDesignAggregates(new DataRef[] { agg });
      vs.addAssembly(crosstab);
      return crosstab;
   }

   private static void chart(Viewsheet vs, String name, SourceInfo source) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      chart.setSourceInfo(source);
      VSChartInfo info = chart.getVSChartInfo();
      VSChartDimensionRef dim = new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, "type")));
      dim.setGroupColumnValue("type");
      info.addXField(dim);
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      info.addYField(agg);
      vs.addAssembly(chart);
   }

   private static String cells(Object data) {
      TableLens lens = data instanceof VSDataSet ? ((VSDataSet) data).getTable() : (TableLens) data;
      assertNotNull(lens, "no data");
      StringBuilder sb = new StringBuilder();
      lens.moreRows(Integer.MAX_VALUE);

      for(int r = 0; r < lens.getRowCount(); r++) {
         for(int c = 0; c < lens.getColCount(); c++) {
            sb.append(lens.getObject(r, c)).append(c < lens.getColCount() - 1 ? "|" : "\n");
         }
      }

      return sb.toString();
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   private static final String TABLE = "TableView1";
   private static final String EXPECTED = "type|Sum(wind)\nNAMED|630312.0\nUNNAMED|23420.0\n";
   private static final long CAP = 10;
}
