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

import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.internal.XNodeMetaTable;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.wiz.pairing.TestWorksheets;
import inetsoft.web.wiz.request.ExportDatabaseTableToCsvRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("core")
@inetsoft.web.wiz.pairing.WizAgentTestSupport
class RawDataServiceTest {
   private static class Deps {
      final XRepository xrepository = mock(XRepository.class);
      final AssetRepository assetRepository = mock(AssetRepository.class);
      final DataSourceService dataSourceService = mock(DataSourceService.class);

      RawDataService service() {
         return new RawDataService(xrepository, assetRepository, dataSourceService);
      }
   }

   @Test
   void rejectsRequestWithNullTableEntryWithClearMessageAndWithoutTouchingRepository() throws Exception {
      Deps deps = new Deps();
      when(deps.dataSourceService.checkPermission(eq("inventree"), eq(ResourceAction.READ), any()))
         .thenReturn(true);

      ExportDatabaseTableToCsvRequest request = new ExportDatabaseTableToCsvRequest();
      request.setDatasourcePath("inventree");
      request.setTable(null); // the plugin/profiler can omit this — must fail cleanly, not NPE

      ResponseStatusException ex = assertThrows(
         ResponseStatusException.class,
         () -> deps.service().writeDataSourceTableCsvStream(request, null, new ByteArrayOutputStream()));

      // A missing table asset entry must surface as a clean 400 Bad Request (not an opaque 500 NPE).
      assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
      // The reason must name the data source so the failure is diagnosable.
      assertNotNull(ex.getReason());
      assertTrue(ex.getReason().contains("inventree"),
                 "exception reason should name the data source: " + ex.getReason());

      // The guard must run before any repository lookup.
      verifyNoInteractions(deps.xrepository);
   }

   @Test
   void exportThrowsWhenDatasourceReadDenied() throws Exception {
      Deps deps = new Deps();
      when(deps.dataSourceService.checkPermission(eq("secretds"), eq(ResourceAction.READ), any()))
         .thenReturn(false);

      ExportDatabaseTableToCsvRequest request = new ExportDatabaseTableToCsvRequest();
      request.setDatasourcePath("secretds");
      request.setTable(new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.PHYSICAL_TABLE,
                                      "secretds/customers", null));

      ResponseStatusException ex = assertThrows(
         ResponseStatusException.class,
         () -> deps.service().writeDataSourceTableCsvStream(request, null, new ByteArrayOutputStream()));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      assertNotNull(ex.getReason());
      assertTrue(ex.getReason().contains("secretds"),
                 "error should name the denied datasource, got: " + ex.getReason());

      // Denial must short-circuit before any datasource lookup or query execution.
      verifyNoInteractions(deps.xrepository);
   }

   @Test
   void exportProceedsWhenDatasourceReadGranted() throws Exception {
      Deps deps = new Deps();
      when(deps.dataSourceService.checkPermission(eq("myds"), eq(ResourceAction.READ), any()))
         .thenReturn(true);

      ExportDatabaseTableToCsvRequest request = new ExportDatabaseTableToCsvRequest();
      request.setDatasourcePath("myds");
      request.setTable(new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.PHYSICAL_TABLE,
                                      "myds/customers", null));

      // xrepository is an unstubbed mock: getDataSource returns null, so execution fails
      // downstream with "not found" — proof it proceeded past the permission gate into the
      // datasource lookup.
      Exception ex = assertThrows(
         Exception.class,
         () -> deps.service().writeDataSourceTableCsvStream(request, null, new ByteArrayOutputStream()));

      assertTrue(ex.getMessage().contains("myds"),
                 "expected the 'data source not found' error, got: " + ex.getMessage());

      verify(deps.dataSourceService).checkPermission(eq("myds"), eq(ResourceAction.READ), any());
      verify(deps.xrepository).getDataSource(eq("myds"));
   }

   // ---------------------------------------------------------------------------
   // writeLiveWorksheetTableCsvStream (WBS-057) -- exports a worksheet table's CURRENT LIVE
   // data, keyed off an already-resolved RuntimeWorksheet's own AssetQuerySandbox, rather than
   // writeWorksheetTableCsvStream's persisted-asset-by-identifier lookup above.
   // ---------------------------------------------------------------------------

   private static RuntimeWorksheet liveRuntimeWorksheet(Worksheet ws, AssetQuerySandbox box) {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      return rws;
   }

   /** A minimal 1-column, 2-row (header + 1 data row) {@link TableLens}, matching the shape
    *  {@code writeCsvContent} iterates over. */
   private static TableLens simpleLens() {
      TableLens lens = mock(TableLens.class);
      when(lens.getRowCount()).thenReturn(2);
      when(lens.getColCount()).thenReturn(1);
      when(lens.getObject(0, 0)).thenReturn("a");
      when(lens.getObject(1, 0)).thenReturn("1");
      return lens;
   }

   @Test
   void writeLiveWorksheetTableCsvStreamStreamsCsvFromTheLiveSandbox() throws Exception {
      Deps deps = new Deps();
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);

      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      TableLens lens = simpleLens();
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(lens);

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      deps.service().writeLiveWorksheetTableCsvStream(liveRuntimeWorksheet(ws, box), "T", out);

      assertEquals("a\n1\n", out.toString(StandardCharsets.UTF_8));
   }

   /**
    * Bug #79596: the CSV holds the cut text, so the sink must be told before streaming starts
    * and the cut-cell user messages must not be left on the pooled thread.
    */
   @Test
   void writeLiveWorksheetTableCsvStreamReportsCellsCutByTheCapBeforeStreaming() throws Exception {
      Deps deps = new Deps();
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "body");
      ws.addAssembly(t);

      int cap = inetsoft.report.internal.Util.getOrganizationMaxCellSize();
      org.junit.jupiter.api.Assumptions.assumeTrue(cap > 0);
      String big = "x".repeat(cap + 100);
      TableLens lens = new inetsoft.report.lens.TextSizeLimitTableLens(
         new DefaultTableLens(new Object[][] {{"body"}, {big}}), cap);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(lens);

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      java.util.List<java.util.List<String>> seen = new java.util.ArrayList<>();
      int[] bytesWhenReported = {-1};
      deps.service().writeLiveWorksheetTableCsvStream(liveRuntimeWorksheet(ws, box), "T", out,
         w -> {
            bytesWhenReported[0] = out.size();
            seen.add(w);
         });

      assertEquals(0, bytesWhenReported[0]);
      assertEquals(1, seen.size());
      assertEquals(1, seen.get(0).size());
      assertTrue(seen.get(0).get(0).contains("max.cell.size is " + cap));
      assertEquals("body\n" + big.substring(0, cap) + "...\n",
         out.toString(StandardCharsets.UTF_8));
      assertNull(inetsoft.util.CoreTool.getUserMessage());
   }

   @Test
   void writeLiveWorksheetTableCsvStreamThrowsWhenSandboxIsAbsent() {
      Deps deps = new Deps();
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = liveRuntimeWorksheet(ws, null);

      RuntimeException ex = assertThrows(RuntimeException.class,
         () -> deps.service().writeLiveWorksheetTableCsvStream(rws, "T", new ByteArrayOutputStream()));
      assertTrue(ex.getMessage().contains("sandbox"),
         "expected a sandbox-unavailable message, got: " + ex.getMessage());
   }

   /**
    * A table name absent from the live worksheet must fail loudly, naming the table -- mirroring
    * {@code preview_worksheet_data}'s own "Table not found" failure shape -- rather than silently
    * producing an empty or wrong file.
    */
   @Test
   void writeLiveWorksheetTableCsvStreamFailsLoudlyWhenTableNotFound() {
      Deps deps = new Deps();
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);

      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      RuntimeWorksheet rws = liveRuntimeWorksheet(ws, box);

      RuntimeException ex = assertThrows(RuntimeException.class,
         () -> deps.service().writeLiveWorksheetTableCsvStream(rws, "missing", new ByteArrayOutputStream()));
      assertTrue(ex.getMessage().contains("missing"),
         "expected the error to name the missing table, got: " + ex.getMessage());

      // Must fail before ever querying the sandbox for a table that isn't even on the worksheet.
      verifyNoInteractions(box);
   }

   @Test
   void writeLiveWorksheetTableCsvStreamFailsLoudlyWhenLensIsNull() throws Exception {
      Deps deps = new Deps();
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);

      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(null);
      RuntimeWorksheet rws = liveRuntimeWorksheet(ws, box);

      RuntimeException ex = assertThrows(RuntimeException.class,
         () -> deps.service().writeLiveWorksheetTableCsvStream(rws, "T", new ByteArrayOutputStream()));
      assertTrue(ex.getMessage().contains("T"),
         "expected the error to name the table, got: " + ex.getMessage());
   }

   /**
    * A failed JS expression column makes the sandbox hand back a failed-query design lens (sample
    * rows, not null). The export must fail loudly, like preview, and write no bytes (Redmine 78271).
    */
   /**
    * Bug #78972: a CHECKED exception from the sandbox (e.g. the raw
    * {@link ExecutionException} a join/concatenation's first sub-query future used to let
    * escape uncaught before Bug #78972's own {@code JoinQuery}/{@code ConcatenatedQuery} fix)
    * must not escape this method uncaught -- {@code catch(RuntimeException e)} here used to
    * miss it entirely, since {@link ExecutionException} is not a {@link RuntimeException},
    * which produced a bare, body-less HTTP 500 at the controller.
    */
   @Test
   void writeLiveWorksheetTableCsvStreamWrapsACheckedExceptionInsteadOfLettingItEscape()
      throws Exception
   {
      Deps deps = new Deps();
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);

      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt()))
         .thenThrow(new ExecutionException("sub-query failed", new IllegalStateException("root cause")));
      RuntimeWorksheet rws = liveRuntimeWorksheet(ws, box);

      RuntimeException ex = assertThrows(RuntimeException.class,
         () -> deps.service().writeLiveWorksheetTableCsvStream(rws, "T", new ByteArrayOutputStream()));
      assertTrue(ex.getMessage().contains("sub-query failed"),
         "expected the underlying cause, got: " + ex.getMessage());
   }

   @Test
   void writeLiveWorksheetTableCsvStreamFailsLoudlyOnFailedQueryLens() throws Exception {
      Deps deps = new Deps();
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
      ws.addAssembly(t);

      DefaultTableLens failed = new DefaultTableLens(2, 1);
      failed.setObject(0, 0, "a");
      failed.setObject(1, 0, "XXX");
      failed.setProperty(XNodeMetaTable.FAILED_QUERY_PROPERTY, "undefinedFn is not defined");

      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(failed);
      RuntimeWorksheet rws = liveRuntimeWorksheet(ws, box);

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      RuntimeException ex = assertThrows(RuntimeException.class,
         () -> deps.service().writeLiveWorksheetTableCsvStream(rws, "T", out));
      assertTrue(ex.getMessage().contains("undefinedFn is not defined"),
         "expected the underlying cause, got: " + ex.getMessage());
      assertEquals(0, out.size());
   }

   // ---------------------------------------------------------------------------
   // Bug #79074: writeLiveWorksheetTableCsvStream reuses the live session's own, pairing-held,
   // amap-registered AssetQuerySandbox -- the identical exposure WorksheetPreviewService.preview0
   // has -- and this export has no row cap at all (unlike preview's 200-row limit), so it is at
   // least as exposed to WorksheetEngine's recycle sweep disposing the runtime mid-query.
   // ---------------------------------------------------------------------------
   //
   // RuntimeHeartbeatKeepAlive.INTERVAL_MS is shrunk for the duration of these tests so the
   // periodic refresh can be observed without waiting out the real (minutes-long) production
   // interval.

   @Test
   void writeLiveWorksheetTableCsvStreamKeepsHeartbeatAliveWhileQueryIsInFlight() throws Exception {
      long saved = RuntimeHeartbeatKeepAlive.INTERVAL_MS;
      RuntimeHeartbeatKeepAlive.INTERVAL_MS = 20L;

      try {
         Deps deps = new Deps();
         Worksheet ws = new Worksheet();
         EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
         ws.addAssembly(t);

         AssetQuerySandbox box = mock(AssetQuerySandbox.class);
         RuntimeWorksheet rws = liveRuntimeWorksheet(ws, box);

         CountDownLatch heartbeats = new CountDownLatch(3);
         doAnswer(inv -> {
            heartbeats.countDown();
            return null;
         }).when(rws).access(false);

         when(box.getTableLens(eq("T"), anyInt())).thenAnswer(inv -> {
            assertTrue(heartbeats.await(2, TimeUnit.SECONDS),
                       "expected the runtime's heartbeat to be refreshed repeatedly while the " +
                       "export query is still executing");
            return simpleLens();
         });

         deps.service().writeLiveWorksheetTableCsvStream(rws, "T", new ByteArrayOutputStream());

         verify(rws, atLeastOnce()).access(false);
         verify(rws, never()).access(true);
      }
      finally {
         RuntimeHeartbeatKeepAlive.INTERVAL_MS = saved;
      }
   }

   @Test
   void writeLiveWorksheetTableCsvStreamStopsRefreshingHeartbeatOnceQueryCompletes() throws Exception {
      long saved = RuntimeHeartbeatKeepAlive.INTERVAL_MS;
      RuntimeHeartbeatKeepAlive.INTERVAL_MS = 15L;

      try {
         Deps deps = new Deps();
         Worksheet ws = new Worksheet();
         EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a");
         ws.addAssembly(t);

         AssetQuerySandbox box = mock(AssetQuerySandbox.class);
         TableLens lens = simpleLens();
         when(box.getTableLens(eq("T"), anyInt())).thenReturn(lens);
         RuntimeWorksheet rws = liveRuntimeWorksheet(ws, box);

         deps.service().writeLiveWorksheetTableCsvStream(rws, "T", new ByteArrayOutputStream());

         clearInvocations(rws);
         Thread.sleep(150L);
         verify(rws, never()).access(anyBoolean());
      }
      finally {
         RuntimeHeartbeatKeepAlive.INTERVAL_MS = saved;
      }
   }
}
