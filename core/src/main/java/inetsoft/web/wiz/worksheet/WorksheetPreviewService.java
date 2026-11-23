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

import com.fasterxml.jackson.annotation.JsonInclude;
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.filter.ColumnMapFilter;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.XNodeMetaTable;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.util.CoreTool;
import inetsoft.util.TimedQueue;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.util.UserMessage;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.service.RuntimeHeartbeatKeepAlive;
import inetsoft.web.wiz.service.WizVsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Returns a sample of data rows from a live {@link RuntimeWorksheet} table without
 * modifying any state.
 *
 * <p>Data is fetched via {@link AssetQuerySandbox#getTableLens} in
 * {@link AssetQuerySandbox#RUNTIME_MODE}, which executes the full underlying query
 * against the live data source.  LIVE_MODE must NOT be used here: it samples input
 * tables to the sandbox's design-time row cap, so aggregates over large tables are
 * silently computed on truncated data — an agent reading those numbers gets
 * plausible-but-wrong analytics.  The {@code limit} only caps how many result rows
 * are returned, not the rows the query computes over.  Callers should keep
 * {@code limit} small (≤ 200) to avoid large payloads in an agent context.</p>
 */
@Service
public class WorksheetPreviewService {
   private static final Logger LOG = LoggerFactory.getLogger(WorksheetPreviewService.class);

   /**
    * Server-side limit, in seconds, for one whole preview (query execution, row read and row-limit
    * check). Default 110: it must stay below the plugin's 120 s fetch timeout so the server
    * answers first and the abandoned query stops, but above the roughly 105 s a paginated REST
    * tabular table legitimately needs (#79074). 0 or negative disables the limit.
    */
   public static final String TIMEOUT_PROPERTY = "wiz.preview.timeout";
   private static final long DEFAULT_TIMEOUT_MILLIS = 110_000L;
   private static final long MAX_TIMEOUT_MILLIS = 24L * 60 * 60 * 1000;

   // test hook; negative means read TIMEOUT_PROPERTY
   private long timeoutMillis = -1L;

   void setTimeoutMillis(long timeoutMillis) {
      this.timeoutMillis = timeoutMillis;
   }

   private long getTimeoutMillis() {
      if(timeoutMillis >= 0) {
         return timeoutMillis;
      }

      return parseTimeoutMillis(SreeEnv.getProperty(TIMEOUT_PROPERTY));
   }

   /**
    * Converts the {@link #TIMEOUT_PROPERTY} value (seconds) to milliseconds. Blank or invalid
    * values give the default; zero or negative disables the limit (returns 0); huge values are
    * clamped so the scheduled time cannot overflow.
    */
   static long parseTimeoutMillis(String value) {
      if(value == null || value.isBlank()) {
         return DEFAULT_TIMEOUT_MILLIS;
      }

      double seconds;

      try {
         seconds = Double.parseDouble(value.trim());
      }
      catch(NumberFormatException e) {
         seconds = Double.NaN;
      }

      if(Double.isNaN(seconds)) {
         LOG.warn("Invalid value '{}' for {}, using the default", value, TIMEOUT_PROPERTY);
         return DEFAULT_TIMEOUT_MILLIS;
      }

      if(seconds <= 0) {
         return 0L;
      }

      return (long) Math.min(seconds * 1000, MAX_TIMEOUT_MILLIS);
   }

   /**
    * Bounds one preview. On expiry it flags the preview and cancels the table lens (if it
    * exists yet), which releases a reader blocked in {@code moreRows} and stops the summary
    * worker. It bounds the row read: a query still being built inside {@code getTableLens}
    * (a non-lazy join/sort or a tabular fetch) is cancelled as soon as its lens exists, so the
    * request returns only after that build finishes. The cancel runs on the shared
    * {@link TimedQueue} thread, so it must stay non-blocking.
    */
   private static final class Watchdog {
      private final long millis;
      private final AtomicBoolean timedOut = new AtomicBoolean();
      private final AtomicReference<TableLens> lens = new AtomicReference<>();
      private TimedQueue.TimedRunnable task;

      Watchdog(long millis) {
         this.millis = millis;
      }

      void start() {
         if(millis <= 0) {
            return;
         }

         task = TimedQueue.add(new TimedQueue.TimedRunnable(millis) {
            @Override
            public void run() {
               timedOut.set(true);
               cancelLens();
            }
         });
      }

      void stop() {
         if(task != null) {
            TimedQueue.remove(task);
         }
      }

      void setLens(TableLens l) {
         lens.set(l);

         // the limit may have expired while the lens was still being built
         if(timedOut.get()) {
            cancelLens();
         }
      }

      private void cancelLens() {
         TableLens l = lens.get();
         TableLens nested = l == null ? null : Util.getNestedTable(l, CancellableTableLens.class);

         if(nested != null) {
            ((CancellableTableLens) nested).cancel();
         }
      }

      private static String formatSeconds(long millis) {
         return millis % 1000 == 0 ? String.valueOf(millis / 1000) : String.valueOf(millis / 1000.0);
      }

      /**
       * Never let a cut-short read pass as a complete result, whatever cancelled it.
       */
      void check(String tableName) throws PairingException {
         if(timedOut.get()) {
            throw new PairingException("Preview of worksheet table '" + tableName
               + "' timed out after " + millis / 1000.0 + " s and was cancelled. Add a filter or"
               + " an aggregation to reduce the data, or preview a smaller table.");
         }

         // deep check: a cancelled shared base under a non-cancelled outer lens also cuts the
         // rows short while the outer lens completes normally
         TableLens l = lens.get();

         if(l != null && AssetDataCache.isCancelled(l)) {
            throw new PairingException("Preview of worksheet table '" + tableName
               + "' was cancelled before it finished (the query was stopped, e.g. the worksheet"
               + " was closed or loading was cancelled), so the rows are incomplete. Retry.");
         }
      }
   }

   /**
    * Query up to {@code limit} rows from the named table in the live worksheet, starting
    * after the first {@code offset} data rows. Callers can page through a table larger than
    * {@code limit} by repeating the call with increasing {@code offset} values; rows beyond
    * the end of the table are simply not returned (no error).
    *
    * @param rws       the live runtime worksheet
    * @param tableName the table assembly name to query
    * @param offset    number of leading data rows to skip (header row excluded); 0-based.
    *                  Negative values are clamped to 0 (mirrors how {@code limit} is
    *                  clamped, not rejected, by the caller).
    * @param limit     maximum number of data rows to return (header row excluded)
    * @return the preview rows plus any warning raised while producing them (e.g. the
    *         organization's column-count limit silently dropping trailing columns); never
    *         {@code null}
    * @throws PairingException if the sandbox is absent, the table is not found,
    *                          or the query execution fails
    */
   public PreviewResult preview(RuntimeWorksheet rws,
                                 String tableName,
                                 int offset,
                                 int limit)
      throws PairingException
   {
      // The failed-query cause is a thread-local that only a new SQL execution overwrites, so a
      // value left by an earlier request on this pooled thread must not be reported as this
      // call's cause, and this call's own must not leak into the next one.
      XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.remove();

      Watchdog watchdog = new Watchdog(getTimeoutMillis());
      watchdog.start();

      try {
         return preview0(rws, tableName, offset, limit, watchdog);
      }
      finally {
         watchdog.stop();
         XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.remove();
      }
   }

   private PreviewResult preview0(RuntimeWorksheet rws, String tableName, int offset, int limit,
                                  Watchdog watchdog)
      throws PairingException
   {
      AssetQuerySandbox box = rws.getAssetQuerySandbox();

      if(box == null) {
         throw new PairingException(PairingException.Kind.INTERNAL, "Worksheet query sandbox is not available");
      }

      // Discard any pending message left behind by an earlier, unrelated request on this
      // pooled thread (nothing on this path reads/clears it otherwise) so the warning captured
      // below can only be one this call's own execution actually raised. Mirrors
      // WorksheetTableService.probeExecutable's identical guard before its own
      // CoreTool.getUserMessage() read.
      CoreTool.clearUserMessage();

      // RUNTIME_MODE execution calls TableAssembly.replaceVariables, which substitutes
      // variable values ($(name)) into condition lists IN PLACE. Bound tables are cloned
      // per-execution so their originals are safe, but EmbeddedTableAssembly executes
      // un-cloned — the substitution would permanently bake the current variable value
      // into the worksheet state (the composer avoids this by previewing on a cloned
      // runtime sheet). Snapshot the variable-bearing structures and restore them after.
      Map<String, ConditionSnapshot> snapshots = snapshotConditions(rws.getWorksheet());
      TableLens lens;
      // Bug #79595: read right after the execution (variables still substituted), but raised
      // below with the other warnings; independent of any cached execution.
      List<String> upstreamLoss = List.of();
      // See RuntimeHeartbeatKeepAlive's own class comment: keeps the runtime's heartbeat fresh
      // for the duration of the blocking query below, so WorksheetEngine's recycle sweep never
      // disposes it mid-query.
      TimedQueue.TimedRunnable heartbeat = RuntimeHeartbeatKeepAlive.start(rws);

      try {
         lens = box.getTableLens(tableName, AssetQuerySandbox.RUNTIME_MODE);
         watchdog.setLens(lens);

         if(lens != null) {
            upstreamLoss = WorksheetColumnLoss.missingUpstreamInputs(
               rws.getWorksheet(), box, tableName);
         }
      }
      catch(Exception e) {
         watchdog.check(tableName);
         String cause = XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.get();

         if(cause != null && !cause.isBlank()) {
            throw new PairingException("Failed to execute worksheet query for '"
                                       + tableName + "': " + cause);
         }

         if(e instanceof NullPointerException) {
            // Raw NPE text names internal classes and says nothing the caller can act on.
            LOG.error("Unexpected NullPointerException executing worksheet table '{}'",
                      tableName, e);
            throw new PairingException("Failed to execute worksheet query for '" + tableName
                                       + "': internal error while executing the table (logged)");
         }

         throw new PairingException("Failed to execute worksheet query for '"
                                    + tableName + "': " + e.getMessage());
      }
      finally {
         RuntimeHeartbeatKeepAlive.stop(heartbeat);
         restoreConditions(rws.getWorksheet(), snapshots);
      }

      if(lens == null) {
         // RUNTIME_MODE execution of a SQL-bound table whose query fails leaves this thread-local
         // set (AssetQuery.getPreBaseTableLens's catch(SQLExpressionFailedException) branch) rather
         // than throwing, so lens == null here is ambiguous between "genuinely missing table" and
         // "query executed and failed." Prefer the captured cause when present.
         String cause = XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.get();

         if(cause != null && !cause.isBlank()) {
            XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.remove();
            throw new PairingException("Failed to execute worksheet query for '"
                                       + tableName + "': " + cause);
         }

         throw new PairingException("Table not found or produced no data: " + tableName);
      }

      try {
         // lens is non-null here, but a RUNTIME_MODE query that failed (e.g. a JS expression
         // column throwing, or a raw SQL/infra failure with no expression column involved) is
         // not surfaced as null — AssetQuery substitutes a design-time XNodeMetaTable fallback
         // carrying sentinel example data instead. Detect that shape and surface the real cause
         // rather than returning fabricated rows as if they were real query results. Pass false,
         // matching WorksheetTableService.probeExecutable: the expression-specific
         // failedQueryError wrapping would misdirect for a non-expression SQL/infra failure.
         WizVsService.checkFailedQuery(lens, false);
      }
      catch(IllegalArgumentException e) {
         throw new PairingException("Failed to execute worksheet query for '"
                                    + tableName + "': " + e.getMessage(), e);
      }

      try {
         // WBT-012 (#76733): AssetQuerySandbox.executeQuery only applies its own column cap when
         // table.isLiveData() -- a DISPLAY-mode flag, false for every table add_table creates
         // (they read back as mode:"full"). This endpoint executes in RUNTIME_MODE regardless of
         // that flag (see the class comment above), so without re-applying the cap here a plain
         // add_table-built table returns an unbounded row width with nothing in warnings to say
         // so. Idempotent: when the sandbox already capped (mode:"detail"), getColCount() is
         // already within the limit and this is a no-op, so the warning is never raised twice.
         // Inside this try, not before it, so a lens whose getColCount() throws still surfaces as
         // the PairingException the catch below produces rather than a bare crash.
         int maxCols = Util.getOrganizationMaxColumn();
         // The sandbox raises the same localized message on its own cap, so equality with it
         // detects that path without parsing text. getUserMessage() consumes the pending
         // message, hence it is read once here and carried forward.
         List<String> warnings = new ArrayList<>(captureWarnings());

         for(String loss : upstreamLoss) {
            if(warnings.stream().noneMatch(w -> w.contains(loss))) {
               warnings.add(loss);
            }
         }

         String limitMessage = Util.getColumnLimitMessage();
         // pending messages are merged into one newline-joined string, so match by substring
         boolean limited = warnings.stream().anyMatch(w -> w.contains(limitMessage));
         Integer total = null;

         if(limited && lens instanceof ColumnMapFilter filter) {
            total = filter.getTable().getColCount();
         }

         if(lens.getColCount() > maxCols) {
            total = lens.getColCount();
            int[] map = new int[maxCols];

            for(int i = 0; i < maxCols; i++) {
               map[i] = i;
            }

            if(!limited) {
               warnings.add(limitMessage);
               limited = true;
            }

            lens = new ColumnMapFilter(lens, map);
         }

         int colCount = lens.getColCount();

         // Row 0 is the header row in StyleBI's TableLens convention.
         String[] headers = new String[colCount];

         for(int col = 0; col < colCount; col++) {
            Object h = lens.getObject(0, col);
            headers[col] = h != null ? h.toString() : "col" + col;
         }

         List<Map<String, Object>> rows = new ArrayList<>();
         int startRow = 1 + Math.max(0, offset);

         for(int row = startRow; rows.size() < limit && lens.moreRows(row); row++) {
            Map<String, Object> rowMap = new LinkedHashMap<>();

            for(int col = 0; col < colCount; col++) {
               rowMap.put(headers[col], toJsonSafe(lens.getObject(row, col)));
            }

            rows.add(rowMap);
         }

         // a message raised lazily while reading rows (e.g. a sort script error) is still reported
         warnings.addAll(captureWarnings());

         RowLimit rowLimit = detectRowLimit(rws.getWorksheet(), tableName, lens);
         watchdog.check(tableName);

         return new PreviewResult(rows, warnings,
                                  limited ? new ColumnLimit(true, maxCols, total) : null,
                                  rowLimit);
      }
      catch(RuntimeException e) {
         watchdog.check(tableName);
         throw new PairingException("Failed to read result columns for '"
                                    + tableName + "': " + e.getMessage(), e);
      }
   }

   /**
    * Reports the row limit the executed table's result has reached, if any: the table's own max
    * rows or the administrator's runtime query limit. Composer states the same through
    * {@link AssetEventUtil#getExceededMsg}; the runtime-mode variant is used here because this
    * endpoint executes in {@link AssetQuerySandbox#RUNTIME_MODE}, where the design-only preview
    * limit never applies. A table holding exactly as many rows as the limit also reports it, as
    * Composer does, since the rows are indistinguishable from a truncated result.
    * <p>Row 0 of the lens is the header, so "the result has at least {@code n} rows" is
    * {@code lens.moreRows(n)}. The check is bounded by the limit itself.</p>
    */
   private static RowLimit detectRowLimit(Worksheet ws, String tableName, TableLens lens) {
      if(ws == null || !(ws.getAssembly(tableName) instanceof TableAssembly table)) {
         return null;
      }

      try {
         AssetEventUtil.ExceededLimit exceeded =
            AssetEventUtil.getExceededLimit(table, lens::moreRows, true);

         return exceeded == null ? null :
            new RowLimit(true, exceeded.limit(), exceeded.admin() ? "admin" : "table",
                         exceeded.message());
      }
      catch(RuntimeException e) {
         // The rows were already produced; a failure to count them must not fail the preview.
         LOG.warn("Failed to check the row limit of worksheet table '{}'", tableName, e);
         return null;
      }
   }

   /**
    * Reads and clears any {@link CoreTool#addUserMessage} raised while producing this preview
    * — chiefly {@link inetsoft.report.internal.Util#getColumnLimitMessage()}, raised whenever
    * the executed table's column count exceeds the organization's column limit and every column
    * past that limit is silently dropped from the lens this method just read. Two places raise
    * it: {@code AssetQuerySandbox.getColumnLimitTableLens} for a live-data ({@code mode:"detail"})
    * table, and {@link #preview} itself for every other table (WBT-012, #76733 — see its own
    * comment there). The interactive Designer UI has its own request/response cycle that
    * surfaces that same message as a toast; this REST agent endpoint has no equivalent, so
    * without this call the truncation was previously indistinguishable from a genuinely narrow
    * table.
    */
   private static List<String> captureWarnings() {
      UserMessage msg = CoreTool.getUserMessage();

      if(msg == null || msg.getMessage() == null || msg.getMessage().isBlank()) {
         return List.of();
      }

      return List.of(msg.getMessage());
   }

   /**
    * The rows produced by a {@link #preview} call, plus any warning surfaced while producing
    * them (e.g. the organization's column-count limit silently dropping trailing columns).
    * {@code warnings} is empty, never {@code null}, when nothing was raised.
    */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   public record PreviewResult(List<Map<String, Object>> rows, List<String> warnings,
                               ColumnLimit columnLimit, RowLimit rowLimit)
   {
      public PreviewResult(List<Map<String, Object>> rows, List<String> warnings) {
         this(rows, warnings, null, null);
      }

      public PreviewResult(List<Map<String, Object>> rows, List<String> warnings,
                           ColumnLimit columnLimit)
      {
         this(rows, warnings, columnLimit, null);
      }
   }

   /**
    * Structured counterpart of Composer's "Table max rows (N) reached." notice. Present on
    * {@link PreviewResult} only when the table's result reached a row limit, regardless of the
    * preview's own {@code offset}/{@code limit}.
    *
    * @param reached always {@code true} when present
    * @param limit   the number of rows the limit allows
    * @param kind    {@code "table"} for the table's own max rows, {@code "admin"} for the
    *                administrator's runtime query limit
    * @param message the message Composer shows for it
    */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   public record RowLimit(boolean reached, int limit, String kind, String message) {}

   /**
    * Structured counterpart of the column-limit warning. Present on {@link PreviewResult} only
    * when columns were dropped.
    *
    * @param limited always {@code true} when present
    * @param limit   the organization's column limit
    * @param total   the original column count, or {@code null} when not known
    */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   public record ColumnLimit(boolean limited, int limit, Integer total) {}

   /**
    * The structures {@link inetsoft.uql.asset.TableAssembly#replaceVariables} mutates:
    * pre/post/ranking/MV condition lists and the aggregate info.
    */
   private record ConditionSnapshot(
      inetsoft.uql.ConditionListWrapper pre,
      inetsoft.uql.ConditionListWrapper post,
      inetsoft.uql.ConditionListWrapper ranking,
      inetsoft.uql.ConditionListWrapper mvUpdatePre,
      inetsoft.uql.ConditionListWrapper mvUpdatePost,
      inetsoft.uql.ConditionListWrapper mvDeletePre,
      inetsoft.uql.ConditionListWrapper mvDeletePost,
      inetsoft.uql.asset.AggregateInfo aggregateInfo) {}

   private static Map<String, ConditionSnapshot> snapshotConditions(
      inetsoft.uql.asset.Worksheet ws)
   {
      Map<String, ConditionSnapshot> snapshots = new HashMap<>();

      if(ws == null) {
         return snapshots;
      }

      for(inetsoft.uql.asset.Assembly a : ws.getAssemblies()) {
         if(a instanceof inetsoft.uql.asset.TableAssembly t) {
            snapshots.put(t.getName(), new ConditionSnapshot(
               cloneConds(t.getPreConditionList()),
               cloneConds(t.getPostConditionList()),
               cloneConds(t.getRankingConditionList()),
               cloneConds(t.getMVUpdatePreConditionList()),
               cloneConds(t.getMVUpdatePostConditionList()),
               cloneConds(t.getMVDeletePreConditionList()),
               cloneConds(t.getMVDeletePostConditionList()),
               t.getAggregateInfo() != null
                  ? (inetsoft.uql.asset.AggregateInfo) t.getAggregateInfo().clone() : null));
         }
      }

      return snapshots;
   }

   private static void restoreConditions(inetsoft.uql.asset.Worksheet ws,
                                         Map<String, ConditionSnapshot> snapshots)
   {
      if(ws == null) {
         return;
      }

      for(inetsoft.uql.asset.Assembly a : ws.getAssemblies()) {
         if(a instanceof inetsoft.uql.asset.TableAssembly t) {
            ConditionSnapshot s = snapshots.get(t.getName());

            if(s == null) {
               continue;
            }

            t.setPreConditionList(s.pre());
            t.setPostConditionList(s.post());
            t.setRankingConditionList(s.ranking());
            t.setMVUpdatePreConditionList(s.mvUpdatePre());
            t.setMVUpdatePostConditionList(s.mvUpdatePost());
            t.setMVDeletePreConditionList(s.mvDeletePre());
            t.setMVDeletePostConditionList(s.mvDeletePost());

            if(s.aggregateInfo() != null) {
               t.setAggregateInfo(s.aggregateInfo());
            }
         }
      }
   }

   private static inetsoft.uql.ConditionListWrapper cloneConds(
      inetsoft.uql.ConditionListWrapper wrapper)
   {
      return wrapper != null
         ? (inetsoft.uql.ConditionListWrapper) wrapper.clone() : null;
   }

   /**
    * Convert a cell value to a JSON-safe type.  Primitive wrappers, Strings, and
    * null pass through unchanged.  Anything else (e.g. PostgreSQL PGobject for
    * tsvector/enum, byte arrays, custom JDBC types) is converted to its String
    * representation so Jackson can always serialize the response without breaking
    * gzip or JSON encoding.
    */
   private static Object toJsonSafe(Object value) {
      if(value == null) {
         return null;
      }

      if(value instanceof String || value instanceof Number || value instanceof Boolean) {
         return value;
      }

      if(value instanceof java.util.Date || value instanceof java.time.temporal.Temporal) {
         return value.toString();
      }

      if(value instanceof byte[]) {
         return "(binary)";
      }

      // Covers PGobject (tsvector, enum, etc.) and any other non-standard JDBC type
      return value.toString();
   }
}
