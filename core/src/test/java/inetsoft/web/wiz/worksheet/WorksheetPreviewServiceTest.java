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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.XNodeMetaTable;
import inetsoft.util.CoreTool;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.service.RuntimeHeartbeatKeepAlive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@WizAgentTestSupport
class WorksheetPreviewServiceTest {

   private final WorksheetPreviewService service = new WorksheetPreviewService();

   @AfterEach
   void clearFailedQueryMessage() {
      // Guard against test-order pollution: LAST_FAILED_QUERY_MESSAGE is a real, shared
      // ThreadLocal (production field, not a test double), and JUnit within a class typically
      // reuses the same thread across tests.
      XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.remove();

      // Same guard for CoreTool's pending-user-message queue: a test that calls
      // CoreTool.addUserMessage directly (see the warnings tests below) must not leak into the
      // next test sharing this thread.
      CoreTool.clearUserMessage();
   }

   // ---------------------------------------------------------------------------
   // Helpers
   // ---------------------------------------------------------------------------

   private static RuntimeWorksheet rws(AssetQuerySandbox box) {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      return rws;
   }

   /**
    * Creates a mock TableLens with the given headers and rows.
    * Row 0 = headers, rows 1..n = data.
    */
   private static TableLens lens(String[] headers, Object[][] data) throws Exception {
      TableLens lens = mock(TableLens.class);
      when(lens.getColCount()).thenReturn(headers.length);

      for(int col = 0; col < headers.length; col++) {
         when(lens.getObject(0, col)).thenReturn(headers[col]);
      }

      int totalRows = data.length; // data rows only (row 0 is header)
      for(int row = 0; row < data.length; row++) {
         when(lens.moreRows(row + 1)).thenReturn(true);
         for(int col = 0; col < data[row].length; col++) {
            when(lens.getObject(row + 1, col)).thenReturn(data[row][col]);
         }
      }
      // moreRows returns false beyond the last row
      when(lens.moreRows(totalRows + 1)).thenReturn(false);

      return lens;
   }

   // ---------------------------------------------------------------------------
   // Tests
   // ---------------------------------------------------------------------------

   @Test
   void throwsWhenSandboxIsNull() {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getAssetQuerySandbox()).thenReturn(null);

      assertThrows(PairingException.class,
                   () -> service.preview(rws, "T", 0, 10));
   }

   @Test
   void throwsWhenGetTableLensReturnsNull() throws Exception {
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(null);

      PairingException ex = assertThrows(PairingException.class,
                                          () -> service.preview(rws(box), "T", 0, 10));
      assertEquals("Table not found or produced no data: T", ex.getMessage(),
                   "with no captured query-failure cause, the generic message is unchanged");
   }

   // Bug #76449 (WBS-005): a SQL-bound table whose query fails during RUNTIME_MODE execution
   // (e.g. a JDBC driver rejecting an unsupported SQL feature) is swallowed by
   // AssetQuery.getPreBaseTableLens's catch(SQLExpressionFailedException) into a plain null
   // TableLens - indistinguishable, at this layer, from a genuinely missing table - except that
   // it leaves the real driver message behind in XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE. These
   // tests exercise WorksheetPreviewService.preview's consumer side of that hand-off directly via
   // the real (production) ThreadLocal field, since reproducing the producer side would require a
   // live JDBC datasource whose driver rejects a specific SQL construct.
   @Test
   void surfacesCapturedQueryFailureCauseInsteadOfGenericMessage() throws Exception {
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenAnswer(inv -> {
         XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.set(
            "[Vendor] window functions are not supported by this driver");
         return null;
      });

      PairingException ex = assertThrows(PairingException.class,
                                          () -> service.preview(rws(box), "T", 0, 10));
      assertEquals("Failed to execute worksheet query for 'T': " +
                   "[Vendor] window functions are not supported by this driver",
                   ex.getMessage());
   }

   @Test
   void consumesAndClearsCapturedQueryFailureCauseAfterSurfacingIt() throws Exception {
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenAnswer(inv -> {
         XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.set("driver says no");
         return null;
      });

      assertThrows(PairingException.class, () -> service.preview(rws(box), "T", 0, 10));
      assertNull(XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.get(),
                 "captured cause must be consumed (removed) once surfaced, matching " +
                 "AssetQuery.getDesignTableLens's existing consume-and-clear reader");
   }

   // Bug #78797: a stale cause left on this pooled thread by an earlier request must not be
   // reported as the cause of a call that ran no failing query.
   @Test
   void ignoresStaleCapturedCauseFromAnEarlierRequest() throws Exception {
      XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.set("stale cause of another request");

      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(null);

      PairingException ex = assertThrows(PairingException.class,
                                          () -> service.preview(rws(box), "T", 0, 10));
      assertEquals("Table not found or produced no data: T", ex.getMessage());
      assertNull(XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.get());
   }

   @Test
   void clearsCapturedCauseWhenQueryExecutionThrows() throws Exception {
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenAnswer(inv -> {
         XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.set("driver says no");
         throw new RuntimeException("wrapper");
      });

      PairingException ex = assertThrows(PairingException.class,
                                          () -> service.preview(rws(box), "T", 0, 10));
      assertEquals("Failed to execute worksheet query for 'T': driver says no",
                   ex.getMessage(), "the real cause wins over the wrapper's text");
      assertNull(XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.get(),
                 "the cause must not leak into the next request on this thread");
   }

   @Test
   void nullPointerWithoutCauseNeverForwardsRawNpeText() throws Exception {
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenThrow(new NullPointerException(
         "Cannot invoke \"inetsoft.report.TableLens.getColCount()\" because \"table\" is null"));

      PairingException ex = assertThrows(PairingException.class,
                                          () -> service.preview(rws(box), "T", 0, 10));
      assertFalse(ex.getMessage().contains("Cannot invoke"), ex.getMessage());
      assertFalse(ex.getMessage().contains("is null"), ex.getMessage());
      assertTrue(ex.getMessage().startsWith("Failed to execute worksheet query for 'T': "),
                 ex.getMessage());
      assertTrue(ex.getMessage().contains("internal error"), ex.getMessage());
   }

   @Test
   void nullPointerWithCapturedCauseReportsTheCause() throws Exception {
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenAnswer(inv -> {
         XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.set(
            "Multiple DISTINCT aggregates are not supported at this time.");
         throw new NullPointerException("Cannot invoke \"x\" because \"table\" is null");
      });

      PairingException ex = assertThrows(PairingException.class,
                                          () -> service.preview(rws(box), "T", 0, 10));
      assertEquals("Failed to execute worksheet query for 'T': " +
                   "Multiple DISTINCT aggregates are not supported at this time.",
                   ex.getMessage());
   }

   @Test
   void throwsWhenGetTableLensThrows() throws Exception {
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt()))
         .thenThrow(new RuntimeException("query failed"));

      assertThrows(PairingException.class,
                   () -> service.preview(rws(box), "T", 0, 10));
   }

   @Test
   void returnsEmptyListWhenNoDataRows() throws Exception {
      TableLens emptyLens = mock(TableLens.class);
      when(emptyLens.getColCount()).thenReturn(2);
      when(emptyLens.getObject(0, 0)).thenReturn("a");
      when(emptyLens.getObject(0, 1)).thenReturn("b");
      when(emptyLens.moreRows(1)).thenReturn(false);

      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(emptyLens);

      List<Map<String, Object>> rows = service.preview(rws(box), "T", 0, 10).rows();
      assertTrue(rows.isEmpty());
   }

   @Test
   void returnsRowsWithColumnNamesAsKeys() throws Exception {
      TableLens l = lens(
         new String[]{"name", "age"},
         new Object[][]{{"Alice", 30}, {"Bob", 25}}
      );
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(l);

      List<Map<String, Object>> rows = service.preview(rws(box), "T", 0, 10).rows();

      assertEquals(2, rows.size());
      assertEquals("Alice", rows.get(0).get("name"));
      assertEquals(30, rows.get(0).get("age"));
      assertEquals("Bob", rows.get(1).get("name"));
   }

   @Test
   void respectsLimitAndDoesNotExceedIt() throws Exception {
      TableLens l = lens(
         new String[]{"x"},
         new Object[][]{{"r1"}, {"r2"}, {"r3"}, {"r4"}, {"r5"}}
      );
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(l);

      List<Map<String, Object>> rows = service.preview(rws(box), "T", 0, 3).rows();
      assertEquals(3, rows.size());
   }

   @Test
   void offsetSkipsLeadingRows() throws Exception {
      TableLens l = lens(
         new String[]{"x"},
         new Object[][]{{"r1"}, {"r2"}, {"r3"}, {"r4"}, {"r5"}}
      );
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(l);

      List<Map<String, Object>> rows = service.preview(rws(box), "T", 2, 2).rows();
      assertEquals(2, rows.size());
      assertEquals("r3", rows.get(0).get("x"));
      assertEquals("r4", rows.get(1).get("x"));
   }

   // Review round 1 finding on this same bug (VTB-002): before offset was threaded through the
   // row loop, row 0 (the header) was unreachable since the loop always started at 1. A negative
   // offset (e.g. -1) shifted the start row to 0, silently returning the header row disguised as
   // a data row, with no exception. Clamp to 0 rather than throw, mirroring how the controller
   // already clamps (not rejects) an out-of-range `limit` via Math.min(limit, 200).
   @Test
   void negativeOffsetIsClampedToZero() throws Exception {
      TableLens l = lens(
         new String[]{"x"},
         new Object[][]{{"r1"}, {"r2"}, {"r3"}}
      );
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(l);

      List<Map<String, Object>> rows = service.preview(rws(box), "T", -1, 10).rows();
      assertEquals(3, rows.size());
      assertEquals("r1", rows.get(0).get("x"),
                   "a negative offset must behave like offset=0, not expose the header row");
   }

   @Test
   void offsetBeyondRowCountReturnsEmptyNotError() throws Exception {
      TableLens l = lens(
         new String[]{"x"},
         new Object[][]{{"r1"}, {"r2"}, {"r3"}}
      );
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(l);

      List<Map<String, Object>> rows = service.preview(rws(box), "T", 100, 10).rows();
      assertTrue(rows.isEmpty());
   }

   // Bug #76517 (WBS-038): a non-null TableLens whose getColCount()/header/row extraction
   // throws a RuntimeException (e.g. an internal wrapper TableLens with an unset delegate)
   // previously propagated straight out of preview() as a raw, unactionable exception -
   // everything after the getTableLens() try/catch (lines 102-122 pre-fix) had no exception
   // handling at all. This does not reproduce the reporter's exact NPE (whose root cause is
   // unpinned - see bug-WBS-038/02-refute.md); it proves the narrower, confirmed-safe claim
   // that ANY RuntimeException thrown while reading columns/rows now surfaces as a
   // PairingException naming the table, not a bare crash.
   @Test
   void throwsPairingExceptionWhenGetColCountThrows() throws Exception {
      TableLens l = mock(TableLens.class);
      NullPointerException cause = new NullPointerException(
         "Cannot invoke \"inetsoft.report.TableLens.getColCount()\" because \"table\" is null");
      when(l.getColCount()).thenThrow(cause);

      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(l);

      PairingException ex = assertThrows(PairingException.class,
                                          () -> service.preview(rws(box), "T", 0, 10));
      assertEquals("Failed to read result columns for 'T': " +
                   "Cannot invoke \"inetsoft.report.TableLens.getColCount()\" because \"table\" is null",
                   ex.getMessage());
      assertSame(cause, ex.getCause());
   }

   @Test
   void throwsPairingExceptionWhenRowExtractionThrows() throws Exception {
      TableLens l = mock(TableLens.class);
      when(l.getColCount()).thenReturn(1);
      when(l.getObject(0, 0)).thenReturn("col");
      when(l.moreRows(1)).thenReturn(true);
      RuntimeException cause = new RuntimeException("row read failed");
      when(l.getObject(1, 0)).thenThrow(cause);

      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(l);

      PairingException ex = assertThrows(PairingException.class,
                                          () -> service.preview(rws(box), "T", 0, 10));
      assertEquals("Failed to read result columns for 'T': row read failed", ex.getMessage());
      assertSame(cause, ex.getCause());
   }

   // Bug #76556 (VFO-017): a non-null TableLens can itself be the RUNTIME_MODE failed-query
   // fallback (AssetQuery.doGetTableLens's catch(ExpressionFailedException) substitutes a
   // design-time XNodeMetaTable carrying sentinel example data, e.g. for a JS expression
   // column referencing invalid syntax like "$(DiscountRate)"). Previously preview() had no
   // check for this shape and read the sentinel row as if it were real query output. It must
   // now surface the captured cause instead of returning fabricated rows.
   @Test
   void throwsWhenLensIsFailedQueryFallback() throws Exception {
      XNodeMetaTable failedLens = mock(XNodeMetaTable.class);
      when(failedLens.isFailedQueryDefault()).thenReturn(true);
      when(failedLens.getFailedQueryMessage())
         .thenReturn("ReferenceError: \"$\" is not defined");

      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(failedLens);

      PairingException ex = assertThrows(PairingException.class,
                                          () -> service.preview(rws(box), "T", 0, 10));
      assertTrue(ex.getMessage().contains("T"));
      assertTrue(ex.getMessage().contains("ReferenceError: \"$\" is not defined"));
   }

   // Round-2 review finding on this same bug (#76556 / VFO-017): checkFailedQuery's
   // wrapExpressionError flag decides the wording, not the actual failure cause - the same
   // XNodeMetaTable fallback shape is produced by AssetQuery's sqlFailed branch (a raw broken-SQL
   // worksheet table, no expression column involved) as by an expression-column failure. Passing
   // true here (as the first cut of this fix did) would misdirect an agent into "checking the
   // expression columns" for a failure that has nothing to do with any expression, mirroring the
   // exact pitfall WorksheetTableService.probeExecutable's own comment already calls out for why
   // it passes false. Pin the raw-cause behavior so a regression back to true fails this test.
   @Test
   void surfacesRawCauseWithoutExpressionWrappingForNonExpressionFailure() throws Exception {
      XNodeMetaTable failedLens = mock(XNodeMetaTable.class);
      when(failedLens.isFailedQueryDefault()).thenReturn(true);
      when(failedLens.getFailedQueryMessage())
         .thenReturn("[Vendor] syntax error near 'GROUP'");

      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(failedLens);

      PairingException ex = assertThrows(PairingException.class,
                                          () -> service.preview(rws(box), "T", 0, 10));
      assertTrue(ex.getMessage().contains("[Vendor] syntax error near 'GROUP'"));
      assertFalse(ex.getMessage().toLowerCase().contains("expression columns"),
                  "a non-expression SQL failure must not carry the " +
                  "\"check the worksheet's expression columns\" hint, which would misdirect: " +
                  ex.getMessage());
   }

   @Test
   void usesFallbackHeaderNameWhenObjectIsNull() throws Exception {
      TableLens l = mock(TableLens.class);
      when(l.getColCount()).thenReturn(1);
      when(l.getObject(0, 0)).thenReturn(null); // null header
      when(l.getObject(1, 0)).thenReturn("value");
      when(l.moreRows(1)).thenReturn(true);
      when(l.moreRows(2)).thenReturn(false);

      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(anyString(), anyInt())).thenReturn(l);

      List<Map<String, Object>> rows = service.preview(rws(box), "T", 0, 10).rows();
      assertEquals(1, rows.size());
      assertTrue(rows.get(0).containsKey("col0"), "should use fallback key 'col0'");
   }

   // ---------------------------------------------------------------------------
   // WBT-002 (#76648): surfacing the column-limit warning
   // ---------------------------------------------------------------------------

   // WBT-002 (#76648): AssetQuerySandbox.getColumnLimitTableLens raises
   // Tool.addUserMessage(Util.getColumnLimitMessage()) via the SAME thread-local queue
   // CoreTool.addUserMessage/getUserMessage expose, whenever it wraps a too-wide live-data
   // table's TableLens in a ColumnMapFilter and silently drops every column past the
   // organization's column limit. This test does not re-exercise that cap firing (already
   // confirmed live, see docs/teams/2026-09-14-bugs-76648-tabular/bug-wbt-002/04-live-recheck.md)
   // -- WorksheetPreviewServiceTest mocks AssetQuerySandbox entirely and cannot reach the real
   // cap logic, which lives upstream of that mock boundary (a limitation the refuter's own
   // round-2 write-up already flagged for whoever wrote this test). It instead calls the same
   // production CoreTool.addUserMessage the real cap invokes, directly, to prove preview()'s own
   // new capture logic surfaces whatever is pending in that queue by the time it returns.
   @Test
   void surfacesPendingUserMessageAsWarning() throws Exception {
      TableLens l = lens(new String[]{"x"}, new Object[][]{{"r1"}});
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenAnswer(invocation -> {
         // Simulates AssetQuerySandbox.getColumnLimitTableLens raising its warning as a side
         // effect of the same getTableLens call preview() itself makes.
         CoreTool.addUserMessage("Number of column limited to: 200");
         return l;
      });

      WorksheetPreviewService.PreviewResult result = service.preview(rws(box), "T", 0, 10);

      assertEquals(1, result.rows().size());
      assertEquals(List.of("Number of column limited to: 200"), result.warnings());
   }

   @Test
   void warningsAreEmptyWhenNothingWasRaised() throws Exception {
      TableLens l = lens(new String[]{"x"}, new Object[][]{{"r1"}});
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(l);

      WorksheetPreviewService.PreviewResult result = service.preview(rws(box), "T", 0, 10);

      assertTrue(result.warnings().isEmpty());
   }

   @Test
   void doesNotLeakAnUnrelatedPriorMessageFromTheSamePooledThread() throws Exception {
      // Simulates a message left behind by an earlier, unrelated request that reused this
      // thread from a pool, never consumed by anything downstream of it.
      CoreTool.addUserMessage("stale message from a previous request");

      TableLens l = lens(new String[]{"x"}, new Object[][]{{"r1"}});
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(l);

      WorksheetPreviewService.PreviewResult result = service.preview(rws(box), "T", 0, 10);

      assertTrue(result.warnings().isEmpty(),
                 "a message queued before this call started must not be attributed to it");
   }

   // ---------------------------------------------------------------------------
   // WBT-012 (#76733): capping a table the sandbox itself did not cap
   // ---------------------------------------------------------------------------

   // WBT-012 (#76733): AssetQuerySandbox only applies its own column cap to a live-data
   // (mode:"detail") table, and every table add_table creates is mode:"full" -- so the sandbox
   // hands this endpoint an uncapped lens for the most common way a table gets built. preview()
   // re-applies the cap itself. This is reachable here precisely because the class mocks
   // AssetQuerySandbox: the mock stands in for a "full"-mode execution that raised nothing.
   @Test
   void capsColumnsAndWarnsWhenTableExceedsOrganizationLimit() throws Exception {
      int max = Util.getOrganizationMaxColumn();
      TableLens l = wideLens(max + 5);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(l);

      WorksheetPreviewService.PreviewResult result = service.preview(rws(box), "T", 0, 10);

      assertEquals(1, result.rows().size());
      assertEquals(max, result.rows().get(0).size(),
                   "row should carry exactly the organization's column limit, not every column");
      assertTrue(result.rows().get(0).containsKey("c0"));
      assertTrue(result.rows().get(0).containsKey("c" + (max - 1)));
      assertFalse(result.rows().get(0).containsKey("c" + max),
                  "columns past the limit must be dropped");
      assertEquals(List.of(Util.getColumnLimitMessage()), result.warnings(),
                   "dropping columns silently is the defect -- it has to be disclosed");
   }

   @Test
   void doesNotCapOrWarnWhenTableIsWithinLimit() throws Exception {
      int max = Util.getOrganizationMaxColumn();
      TableLens l = wideLens(max);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(l);

      WorksheetPreviewService.PreviewResult result = service.preview(rws(box), "T", 0, 10);

      assertEquals(max, result.rows().get(0).size(), "a table exactly at the limit is not capped");
      assertTrue(result.warnings().isEmpty(), "nothing was dropped, so nothing to warn about");
   }

   // A mode:"detail" table is capped by AssetQuerySandbox.getColumnLimitTableLens before it ever
   // reaches preview(), which raises the message and hands back an already-narrowed lens. The new
   // cap above must be a no-op on that shape -- neither narrowing it a second time nor queueing a
   // second copy of the same warning.
   @Test
   void doesNotReCapOrDoubleWarnWhenTheSandboxAlreadyCapped() throws Exception {
      int max = Util.getOrganizationMaxColumn();
      TableLens l = wideLens(max);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenAnswer(invocation -> {
         CoreTool.addUserMessage(Util.getColumnLimitMessage());
         return l;
      });

      WorksheetPreviewService.PreviewResult result = service.preview(rws(box), "T", 0, 10);

      assertEquals(max, result.rows().get(0).size());
      assertEquals(List.of(Util.getColumnLimitMessage()), result.warnings());
      assertNull(CoreTool.getUserMessage(),
                 "no second copy of the warning should still be queued after the call");
   }

   // Bug #78350: a structured signal so callers need not match the localized warning text.
   @Test
   void columnLimitCarriesLimitAndTotalWhenPreviewItselfCaps() throws Exception {
      int max = Util.getOrganizationMaxColumn();
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      TableLens wide = wideLens(max + 5);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(wide);

      WorksheetPreviewService.PreviewResult result = service.preview(rws(box), "T", 0, 10);

      assertNotNull(result.columnLimit());
      assertTrue(result.columnLimit().limited());
      assertEquals(max, result.columnLimit().limit());
      assertEquals(max + 5, result.columnLimit().total());
   }

   @Test
   void columnLimitIsFlaggedWithoutTotalWhenTheSandboxAlreadyCapped() throws Exception {
      int max = Util.getOrganizationMaxColumn();
      TableLens capped = wideLens(max);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenAnswer(invocation -> {
         CoreTool.addUserMessage(Util.getColumnLimitMessage());
         return capped;
      });

      WorksheetPreviewService.PreviewResult result = service.preview(rws(box), "T", 0, 10);

      assertNotNull(result.columnLimit());
      assertTrue(result.columnLimit().limited());
      assertEquals(max, result.columnLimit().limit());
      assertNull(result.columnLimit().total(), "the original width is unknown on this path");
   }

   @Test
   void columnLimitIsFlaggedWhenAnotherMessageIsPendingAlongsideTheSandboxCap() throws Exception {
      int max = Util.getOrganizationMaxColumn();
      TableLens capped = wideLens(max);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenAnswer(invocation -> {
         CoreTool.addUserMessage("another pending message");
         CoreTool.addUserMessage(Util.getColumnLimitMessage());
         return capped;
      });

      WorksheetPreviewService.PreviewResult result = service.preview(rws(box), "T", 0, 10);

      assertNotNull(result.columnLimit());
      assertEquals(max, result.columnLimit().limit());
   }

   @Test
   void warningRaisedLazilyWhileReadingRowsIsStillReported() throws Exception {
      TableLens l = lens(new String[]{"x"}, new Object[][]{{"r1"}});
      when(l.getObject(1, 0)).thenAnswer(invocation -> {
         CoreTool.addUserMessage("raised while reading rows");
         return "r1";
      });
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(l);

      WorksheetPreviewService.PreviewResult result = service.preview(rws(box), "T", 0, 10);

      assertEquals(List.of("raised while reading rows"), result.warnings());
   }

   @Test
   void columnLimitIsAbsentWhenNothingWasDropped() throws Exception {
      int max = Util.getOrganizationMaxColumn();
      TableLens atLimit = wideLens(max);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenReturn(atLimit);

      assertNull(service.preview(rws(box), "T", 0, 10).columnLimit());
   }

   @Test
   void columnLimitIsAbsentForAnUnrelatedWarning() throws Exception {
      TableLens narrow = lens(new String[]{"x"}, new Object[][]{{"r1"}});
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getTableLens(eq("T"), anyInt())).thenAnswer(invocation -> {
         CoreTool.addUserMessage("something else happened");
         return narrow;
      });

      WorksheetPreviewService.PreviewResult result = service.preview(rws(box), "T", 0, 10);

      assertEquals(List.of("something else happened"), result.warnings());
      assertNull(result.columnLimit());
   }

   @Test
   void columnLimitSerializesOnlyWhenPresent() throws Exception {
      ObjectMapper mapper = new ObjectMapper();

      JsonNode absent = mapper.valueToTree(
         new WorksheetPreviewService.PreviewResult(List.of(), List.of()));
      assertFalse(absent.has("columnLimit"));

      JsonNode known = mapper.valueToTree(
         new WorksheetPreviewService.PreviewResult(List.of(), List.of(),
            new WorksheetPreviewService.ColumnLimit(true, 200, 250)));
      assertTrue(known.get("columnLimit").get("limited").asBoolean());
      assertEquals(200, known.get("columnLimit").get("limit").asInt());
      assertEquals(250, known.get("columnLimit").get("total").asInt());

      JsonNode unknownTotal = mapper.valueToTree(
         new WorksheetPreviewService.PreviewResult(List.of(), List.of(),
            new WorksheetPreviewService.ColumnLimit(true, 200, null)));
      assertFalse(unknownTotal.get("columnLimit").has("total"));
   }

   /**
    * A single-data-row lens {@code colCount} columns wide, headers {@code c0..cN-1}.
    */
   private static TableLens wideLens(int colCount) throws Exception {
      String[] headers = new String[colCount];
      Object[] row = new Object[colCount];

      for(int col = 0; col < colCount; col++) {
         headers[col] = "c" + col;
         row[col] = "v" + col;
      }

      return lens(headers, new Object[][]{ row });
   }

   // ---------------------------------------------------------------------------
   // Bug #79074: keep the runtime's heartbeat alive while a slow query is in flight
   // ---------------------------------------------------------------------------
   //
   // WorksheetEngine's recycle sweep disposes a runtime sheet once its heartbeat has gone
   // stale for RuntimeSheet.DEFAULT_HEARTBEAT_TIMEOUT (3 minutes) -- with no "currently
   // executing, don't evict" guard for worksheets the way ViewsheetEngine has for viewsheets.
   // preview0() must therefore refresh the heartbeat itself while box.getTableLens(...) blocks,
   // so a legitimately slow-but-finite query (e.g. a multi-round-trip REST pagination fetch)
   // never gets its runtime disposed out from under it mid-query.
   //
   // RuntimeHeartbeatKeepAlive.INTERVAL_MS is shrunk for the duration of these tests so the periodic refresh can
   // be observed without waiting out the real (minutes-long) production interval.

   @Test
   void refreshesHeartbeatPeriodicallyWhileQueryIsInFlight() throws Exception {
      long saved = RuntimeHeartbeatKeepAlive.INTERVAL_MS;
      RuntimeHeartbeatKeepAlive.INTERVAL_MS = 20L;

      try {
         AssetQuerySandbox box = mock(AssetQuerySandbox.class);
         RuntimeWorksheet rws = rws(box);
         CountDownLatch heartbeats = new CountDownLatch(3);
         doAnswer(inv -> {
            heartbeats.countDown();
            return null;
         }).when(rws).access(false);

         when(box.getTableLens(eq("T"), anyInt())).thenAnswer(inv -> {
            // Simulates a query that is still genuinely executing: block long enough for
            // several heartbeat ticks to fire before letting the "query" finish.
            assertTrue(heartbeats.await(2, TimeUnit.SECONDS),
                       "expected the runtime's heartbeat to be refreshed repeatedly while " +
                       "the query is still executing");
            return lens(new String[]{ "A" }, new Object[][]{});
         });

         service.preview(rws, "T", 0, 10);

         verify(rws, atLeastOnce()).access(false);
         // Only the heartbeat clock should move -- the idle/access clock is untouched here;
         // that one call happens earlier, at pairing-access grant time, outside this method.
         verify(rws, never()).access(true);
      }
      finally {
         RuntimeHeartbeatKeepAlive.INTERVAL_MS = saved;
      }
   }

   @Test
   void stopsRefreshingHeartbeatOnceTheQueryCompletes() throws Exception {
      long saved = RuntimeHeartbeatKeepAlive.INTERVAL_MS;
      RuntimeHeartbeatKeepAlive.INTERVAL_MS = 15L;

      try {
         AssetQuerySandbox box = mock(AssetQuerySandbox.class);
         RuntimeWorksheet rws = rws(box);
         TableLens l = lens(new String[]{ "A" }, new Object[][]{});
         when(box.getTableLens(eq("T"), anyInt())).thenReturn(l);

         service.preview(rws, "T", 0, 10);

         // The periodic task must be unregistered once the call returns -- otherwise it would
         // keep firing on this (now very short) interval forever, a background-task leak.
         clearInvocations(rws);
         Thread.sleep(150L);
         verify(rws, never()).access(anyBoolean());
      }
      finally {
         RuntimeHeartbeatKeepAlive.INTERVAL_MS = saved;
      }
   }

   @Test
   void stopsRefreshingHeartbeatWhenTheQueryThrows() throws Exception {
      long saved = RuntimeHeartbeatKeepAlive.INTERVAL_MS;
      RuntimeHeartbeatKeepAlive.INTERVAL_MS = 15L;

      try {
         AssetQuerySandbox box = mock(AssetQuerySandbox.class);
         RuntimeWorksheet rws = rws(box);
         when(box.getTableLens(eq("T"), anyInt()))
            .thenThrow(new RuntimeException("query failed"));

         assertThrows(PairingException.class, () -> service.preview(rws, "T", 0, 10));

         // The failure path's finally block must also unregister the periodic task.
         clearInvocations(rws);
         Thread.sleep(150L);
         verify(rws, never()).access(anyBoolean());
      }
      finally {
         RuntimeHeartbeatKeepAlive.INTERVAL_MS = saved;
      }
   }
}
