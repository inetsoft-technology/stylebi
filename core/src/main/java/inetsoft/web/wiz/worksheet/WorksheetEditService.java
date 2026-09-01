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
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.TableAssemblyInfo;
import inetsoft.report.internal.Util;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import java.awt.Point;
import java.util.Enumeration;
import inetsoft.web.composer.ws.WorksheetControllerService;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.service.RenderWaitSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Session-resolved edit service for worksheets.
 *
 * <p>Resolves a {@link JoinSession} from a session token, fetches the corresponding
 * {@link RuntimeWorksheet}, applies a caller-supplied mutation via an {@link Editor},
 * then broadcasts a refresh to the owning browser session.</p>
 */
@Service
public class WorksheetEditService {

   @Autowired
   public WorksheetEditService(SheetSessionService sessions,
                               SheetRuntimeAccess runtimeAccess,
                               SheetAgentBroadcastService broadcast,
                               SecurityEngine securityEngine,
                               InnerJoinService innerJoinService)
   {
      this.sessions = sessions;
      this.runtimeAccess = runtimeAccess;
      this.broadcast = broadcast;
      this.securityEngine = securityEngine;
      this.innerJoinService = innerJoinService;
   }

   /**
    * Resolve a session, fetch the runtime worksheet, apply the mutation, and broadcast a refresh.
    *
    * @param sessionToken the session token obtained at join time
    * @param agent        the agent's principal
    * @param mutation     the mutation to apply via the {@link Editor}
    * @throws PairingException if the session is invalid/expired or the runtime is not found
    */
   public void apply(String sessionToken, Principal agent,
                     ThrowingConsumer<Editor> mutation)
      throws Exception
   {
      String agentKey = agentKey(agent);
      JoinSession session = sessions.resolve(sessionToken, agentKey);

      if(session == null) {
         throw new PairingException(PairingException.Kind.SESSION_EXPIRED, "Invalid or expired session: " + sessionToken);
      }

      RuntimeWorksheet rws = (RuntimeWorksheet) runtimeAccess.getSheetForPairing(
         SheetType.WORKSHEET, session.runtimeId(), agent);
      applySocketSession(rws, session);

      Editor editor = new Editor(rws.getWorksheet(), agent, securityEngine, innerJoinService);

      try {
         mutation.accept(editor);
      }
      catch(MessageException me) {
         // Auto-confirm cross-join: the column selection change has already been applied
         // in memory before AbstractJoinTableAssembly.setColumnSelection throws.  In the
         // browser UI this would surface as a confirmation dialog — here we just accept it.
         if(!(me.getCause() instanceof CrossJoinException)) {
            throw me;
         }

         LOG.info("Auto-confirmed cross join in agent edit: {}", me.getMessage());
      }

      // Checkpoint saved after the mutation so redo restores the post-edit state,
      // matching the @Undoable / makeUndoable pattern used by the standard WS controllers.
      rws.addCheckpoint(rws.getWorksheet().prepareCheckpoint());
      refreshAssemblies(rws);

      broadcast.broadcastRefresh(rws, SheetType.WORKSHEET, session.runtimeId(), agent);
   }

   /**
    * Resolve a session, provide the full {@link RuntimeWorksheet} to a mutation, then broadcast
    * a refresh. Use this when the mutation needs more than the {@link Editor} provides (e.g.
    * creating new assemblies that require AssetEventUtil.initColumnSelection).
    */
   public <T> T applyOnRuntime(String sessionToken, Principal agent,
                               ThrowingFunction<RuntimeWorksheet, T> mutation)
      throws Exception
   {
      String agentKey = agentKey(agent);
      JoinSession session = sessions.resolve(sessionToken, agentKey);

      if(session == null) {
         throw new PairingException(PairingException.Kind.SESSION_EXPIRED, "Invalid or expired session: " + sessionToken);
      }

      RuntimeWorksheet rws = (RuntimeWorksheet) runtimeAccess.getSheetForPairing(
         SheetType.WORKSHEET, session.runtimeId(), agent);
      applySocketSession(rws, session);

      T result = mutation.apply(rws);
      // Checkpoint saved after the mutation so redo restores the post-edit state.
      rws.addCheckpoint(rws.getWorksheet().prepareCheckpoint());
      refreshAssemblies(rws);
      broadcast.broadcastRefresh(rws, SheetType.WORKSHEET, session.runtimeId(), agent);
      return result;
   }

   @FunctionalInterface
   public interface ThrowingFunction<A, R> {
      R apply(A a) throws Exception;
   }

   /**
    * Like {@link #applyOnRuntime} but does NOT create an undo checkpoint.
    * Use for operations that manipulate the checkpoint stack themselves (undo/redo).
    */
   public <T> T applyOnRuntimeNoCheckpoint(String sessionToken, Principal agent,
                                            ThrowingFunction<RuntimeWorksheet, T> mutation)
      throws Exception
   {
      String agentKey = agentKey(agent);
      JoinSession session = sessions.resolve(sessionToken, agentKey);

      if(session == null) {
         throw new PairingException(PairingException.Kind.SESSION_EXPIRED, "Invalid or expired session: " + sessionToken);
      }

      RuntimeWorksheet rws = (RuntimeWorksheet) runtimeAccess.getSheetForPairing(
         SheetType.WORKSHEET, session.runtimeId(), agent);
      applySocketSession(rws, session);

      T result = mutation.apply(rws);
      refreshAssemblies(rws);
      broadcast.broadcastRefresh(rws, SheetType.WORKSHEET, session.runtimeId(), agent);
      return result;
   }

   /**
    * Resolve a session and fetch the runtime worksheet without applying any mutation.
    * Useful for read operations that need a live runtime.
    *
    * @param sessionToken the session token obtained at join time
    * @param agent        the agent's principal
    * @return the live {@link RuntimeWorksheet}
    * @throws PairingException if the session is invalid/expired or the runtime is not found
    */
   public RuntimeWorksheet resolve(String sessionToken, Principal agent) throws PairingException {
      String agentKey = agentKey(agent);
      JoinSession session = sessions.resolve(sessionToken, agentKey);

      if(session == null) {
         throw new PairingException(PairingException.Kind.SESSION_EXPIRED, "Invalid or expired session: " + sessionToken);
      }

      return (RuntimeWorksheet) runtimeAccess.getSheetForPairing(
         SheetType.WORKSHEET, session.runtimeId(), agent);
   }

   /**
    * Resolve a session and return both the runtime worksheet and the session's runtime ID.
    */
   public ResolvedSession resolveWithSession(String sessionToken, Principal agent) throws PairingException {
      String agentKey = agentKey(agent);
      JoinSession session = sessions.resolve(sessionToken, agentKey);

      if(session == null) {
         throw new PairingException(PairingException.Kind.SESSION_EXPIRED, "Invalid or expired session: " + sessionToken);
      }

      RuntimeWorksheet rws = (RuntimeWorksheet) runtimeAccess.getSheetForPairing(
         SheetType.WORKSHEET, session.runtimeId(), agent);
      applySocketSession(rws, session);
      return new ResolvedSession(rws, session.runtimeId());
   }

   public record ResolvedSession(RuntimeWorksheet rws, String runtimeId) {}

   // -------------------------------------------------------------------------
   // Identity key helpers
   // -------------------------------------------------------------------------

   /**
    * Refresh column selections and reload table data for all assemblies in the worksheet.
    * Mirrors the UI's post-edit steps (InsertDataService calls refreshColumnSelection +
    * loadTableData after column mutations).
    *
    * <p>{@code refreshColumnSelection} — not {@code loadTableData} — is the call that actually
    * executes a crosstab/grouped table's query ({@code AssetQuerySandbox.refreshColumnSelection}'s
    * {@code query.getTableLens(vars)}); {@code loadTableData} is structural validation and cache
    * invalidation only. Both are bounded together in one {@link RenderWaitSupport#awaitOrRetry}
    * call per table so a table whose query hasn't executed yet in this runtime cannot block this
    * best-effort warm-up past its share of the budget below.</p>
    *
    * <p>Bug #76350 follow-on (item A): before this, neither call had any bound, so a single slow
    * table anywhere in the worksheet — not necessarily the one just edited — made an unrelated,
    * already-committed write op (add_expression_column, set_group_aggregate, etc.) hang until
    * that table's query finished, reported to the caller as a false 30s timeout on a call that had
    * actually already succeeded.</p>
    *
    * <p>All tables share one wall-clock budget ({@link #REFRESH_ASSEMBLIES_BUDGET_MS}) rather than
    * each getting its own independent wait, so the aggregate cost of this warm-up is capped at a
    * flat ~2s regardless of how many tables the worksheet has. Trade-off, accepted deliberately: a
    * persistently slow table that sorts early in {@code ws.getAssemblies()}'s iteration order can
    * consume the whole budget and starve every table after it, on every write op, indefinitely —
    * worse for those specific tables than the old fully-unbounded design, which eventually warmed
    * every table given enough time. This is acceptable because this loop is best-effort
    * cache-warming, not a correctness precondition (unlike {@code ViewsheetEditService}'s
    * {@code ensureTableDataReady}, which guards a mutation that hasn't happened yet): a table
    * skipped here is not warmed by this request, but still executes — and gets cached — on the
    * next real read that needs it.</p>
    */
   private void refreshAssemblies(RuntimeWorksheet rws) {
      Worksheet ws = rws.getWorksheet();

      if(ws == null) {
         return;
      }

      long deadline = System.currentTimeMillis() + REFRESH_ASSEMBLIES_BUDGET_MS;

      for(Assembly a : ws.getAssemblies()) {
         if(a instanceof TableAssembly ta) {
            String name = ta.getName();
            long remaining = deadline - System.currentTimeMillis();

            if(remaining <= 0) {
               LOG.warn("Skipping refresh for assembly {} - refreshAssemblies budget exhausted",
                        name);
               continue;
            }

            try {
               RenderWaitSupport.awaitOrRetry(() -> {
                  WorksheetEventUtil.refreshColumnSelection(rws, name, true);
                  // Drop conditions left pointing at columns the refresh just removed. An op that
                  // changes a table's output columns without touching its conditions leaves an
                  // add_filter or set_ranking referencing a DataRef no longer in the selection --
                  // edit_unpivot is the clearest case, since changing headerColumns renames every
                  // melted column. The Composer validates at exactly this point, right after its
                  // own refreshColumnSelection
                  // (TableUnpivotDialogService#changeUnpivotTableRowHeaders), and
                  // WorksheetMutationSupport already does it for the aggregate path. Doing it in
                  // the shared refresh covers every op rather than requiring each to remember.
                  //
                  // After refreshColumnSelection and before loadTableData, deliberately: a stale
                  // selection would make this delete conditions that are still live, and the
                  // retry wrapper only re-runs the whole block, so ordering inside it holds.
                  AssetUtil.validateConditions(ta.getColumnSelection(), ta);
                  WorksheetEventUtil.loadTableData(rws, name, true, true);
                  return null;
               }, remaining, (int) Math.max(1, remaining / 1000));
               WorksheetEventUtil.fixAssemblyInfo(rws, ta);
            }
            catch(Exception ex) {
               // RenderNotReadyException (a timed-out table) is deliberately swallowed here,
               // same as any other per-table failure: the mutation this method runs after has
               // already succeeded and been checkpointed, so this is a best-effort warm-up, not
               // a precondition the caller needs to know failed.
               LOG.warn("Failed to refresh assembly: {}", name, ex);
            }
         }
      }
   }

   // Mirrors ViewsheetEditService.TABLE_WARM_MAX_ATTEMPTS/TABLE_WARM_RETRY_SLEEP_MS: the same
   // "how long is acceptable to make a caller wait before answering retry-after" ceiling, applied
   // here as one shared budget across the whole refreshAssemblies loop rather than per-table.
   private static final int TABLE_WARM_MAX_ATTEMPTS = 4;
   private static final long TABLE_WARM_RETRY_SLEEP_MS = 500;
   private static final long REFRESH_ASSEMBLIES_BUDGET_MS =
      TABLE_WARM_MAX_ATTEMPTS * TABLE_WARM_RETRY_SLEEP_MS;

   private void applySocketSession(RuntimeWorksheet rws, JoinSession session) {
      if(session.socketSessionId() != null && rws.getSocketSessionId() == null) {
         rws.setSocketSessionId(session.socketSessionId());
      }

      if(rws.getSocketUserName() == null && session.socketUserName() != null) {
         rws.setSocketUserName(session.socketUserName());
      }
   }

   private String agentKey(Principal agent) {
      if(agent instanceof XPrincipal p) {
         IdentityID id = IdentityID.getIdentityIDFromKey(p.getName());
         return id != null ? id.convertToKey() : p.getName();
      }

      return agent != null ? agent.getName() : null;
   }

   // -------------------------------------------------------------------------
   // Dependencies
   // -------------------------------------------------------------------------

   private final SheetSessionService sessions;
   private final SheetRuntimeAccess runtimeAccess;
   private final SheetAgentBroadcastService broadcast;
   private final SecurityEngine securityEngine;
   private final InnerJoinService innerJoinService;
   private static final Logger LOG = LoggerFactory.getLogger(WorksheetEditService.class);

   // =========================================================================
   // Inner class: Editor
   // =========================================================================

   /**
    * Applies column mutations to an in-memory {@link Worksheet}.
    *
    * <p>An {@code Editor} instance is created per {@link #apply} call and
    * operates on the live worksheet object held by the {@link RuntimeWorksheet}.</p>
    */
   public static final class Editor {

      Editor(Worksheet ws, Principal agent, SecurityEngine securityEngine,
             InnerJoinService innerJoinService) {
         this.ws = ws;
         this.agent = agent;
         this.securityEngine = securityEngine;
         this.innerJoinService = innerJoinService;
      }

      /**
       * Removes the named column from the table's public {@link ColumnSelection}.
       * No-ops if the column does not exist.
       *
       * @param table the assembly name
       * @param col   the column attribute name to remove
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists, or if
       *                          a dependent join/composite table still uses this column
       */
      public void removeColumn(String table, String col) throws PairingException {
         TableAssembly t = requireTable(table);
         ColumnSelection cs = t.getColumnSelection();
         DataRef toRemove = cs.getAttribute(col);

         if(toRemove != null) {
            WorksheetMutationSupport.assertSnapshotAllowsColumnRemove(t, table, col, toRemove);

            if(toRemove instanceof ColumnRef cr &&
               !WorksheetControllerService.allowsDeletion(ws, t, cr))
            {
               throw new PairingException(Catalog.getCatalog().getString(
                  "common.columnDependency", col));
            }

            // For embedded tables, also remove the data column from XEmbeddedTable.
            // Snapshots are excluded, and not just as an optimization: the guard above lets
            // only an expression column through, an expression column has no data column, so
            // findColumn could never match. What it WOULD do is call getEmbeddedData(), which
            // on a snapshot goes through getTable() -> initTable() and can fault the whole
            // swapped-out data file back in to answer a question already known to be "no".
            if(t instanceof EmbeddedTableAssembly embedded &&
               !(t instanceof SnapshotEmbeddedTableAssembly))
            {
               XEmbeddedTable data = embedded.getEmbeddedData();
               int index = AssetUtil.findColumn(data, toRemove);

               if(index >= 0) {
                  data.deleteCol(index);
               }
            }

            cs.removeAttribute(toRemove);
            t.setColumnSelection(cs);
         }
      }

      /**
       * Adds a new column to the table's public {@link ColumnSelection}.
       *
       * <p>On an {@link EmbeddedTableAssembly}, {@code name} is optional: a blank
       * name auto-generates the next available {@code "col" + N}, exactly matching
       * the Composer UI's "insert column" behavior ({@code InsertDataService}),
       * since a brand-new spreadsheet-style column has no pre-existing identity to
       * name it after. On any other table type there is no embedded grid to insert
       * into — {@code add_column} there means re-adding an existing-but-hidden
       * column back into the selection, so {@code name} must identify which one and
       * cannot be defaulted.</p>
       *
       * @param table the assembly name
       * @param name  the new column's attribute name, or blank/{@code null} to
       *              auto-generate (embedded tables only)
       * @param type  the data type string (e.g. {@code "string"}, {@code "integer"}), or {@code null}
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists, if
       *                          {@code name} is blank on a non-embedded table, or if a
       *                          column named {@code name} already exists on the table
       */
      public void addColumn(String table, String name, String type) throws PairingException {
         TableAssembly t = requireTable(table);
         WorksheetMutationSupport.assertSnapshotAllowsColumnAdd(t, table, "add_column");
         ColumnSelection cs = t.getColumnSelection();

         if(name == null || name.isBlank()) {
            if(!(t instanceof EmbeddedTableAssembly)) {
               throw new PairingException(
                  "name is required for add_column on a non-embedded table.");
            }

            name = nextEmbeddedColumnName(cs);
         }
         else if(WorksheetMutationSupport.containsColumnNamed(cs, name)) {
            // Fail loud: adding anyway would create a second ColumnRef sharing the same
            // identity, making later lookups by name ambiguous — and a hidden column
            // stays in the selection, so "re-adding" it would silently duplicate it.
            throw new PairingException(
               "A column named '" + name + "' already exists on table '" + table +
               "'. If it is hidden, use set_column_visibility to show it instead.");
         }

         // For embedded tables, also insert the data column into XEmbeddedTable
         // (mirrors InsertDataService.insertData column path).
         if(t instanceof EmbeddedTableAssembly embedded) {
            XEmbeddedTable data = embedded.getEmbeddedData();
            int newColIdx = data.getColCount();
            data.insertCol(newColIdx);
            data.setObject(0, newColIdx, name);
            data.setColumnIdentifier(newColIdx, name);
         }

         AttributeRef attr = new AttributeRef(null, name);
         ColumnRef ref = new ColumnRef(attr);

         if(type != null) {
            ref.setDataType(type);
         }

         String alias = AssetUtil.findAlias(cs, ref);
         ref.setAlias(alias);
         cs.addAttribute(ref);
         t.setColumnSelection(cs);
      }

      /**
       * Finds the next unused {@code "col" + N} identifier, same scheme as
       * {@code InsertDataService.insertData}'s column-insert path.
       */
      private static String nextEmbeddedColumnName(ColumnSelection cs) {
         String colname;
         int i = 1;

         while(true) {
            colname = "col" + i;

            if(cs.getAttribute(colname) == null &&
               AssetUtil.findColumnConflictingWithAlias(cs, null, colname, true) == null)
            {
               return colname;
            }

            i++;
         }
      }

      /**
       * Sets the alias of an existing column, effectively renaming it in the output.
       * No-ops if the column does not exist or is not a {@link ColumnRef}.
       *
       * @param table   the assembly name
       * @param col     the column attribute name to rename
       * @param newName the new alias
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists, or if
       *                          a dependent join/composite table still uses this column
       */
      public void renameColumn(String table, String col, String newName) throws PairingException {
         TableAssembly t = requireTable(table);
         ColumnSelection cs = t.getColumnSelection(false);
         DataRef existing = cs.getAttribute(col);

         if(existing instanceof ColumnRef cr) {
            if(!WorksheetControllerService.allowsDeletion(ws, t, cr)) {
               throw new PairingException(Catalog.getCatalog().getString(
                  "common.columnDependency", col));
            }

            cr.setAlias(newName);
         }
      }

      // -----------------------------------------------------------------------
      // Filter mutators
      // -----------------------------------------------------------------------

      /**
       * Appends a simple pre-condition (AND-joined) to the named table.
       *
       * @param table     the assembly name
       * @param field     the column name to filter on
       * @param operation comparison operator: {@code "="}, {@code "!="}, {@code "<"}, {@code ">"}
       * @param values    one or more literal string values
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists, or if
       *                          {@code table} is an embedded or snapshot-embedded table
       */
      public void addFilter(String table, String field,
                            String operation, String... values) throws PairingException
      {
         TableAssembly t = requireTable(table);
         requireFilterable(t);
         requireColumn(t, field);
         WorksheetMutationSupport.addFilter(t, field, operation, values);
      }

      /**
       * Removes every pre-condition on {@code field} from the named table.
       *
       * @param table the assembly name
       * @param field the column name whose conditions should be removed
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists
       */
      public void removeFilter(String table, String field) throws PairingException {
         WorksheetMutationSupport.removeFilter(requireTable(table), field);
      }

      // -----------------------------------------------------------------------
      // Aggregate mutator
      // -----------------------------------------------------------------------

      /**
       * Builds and sets a new {@link AggregateInfo} on the named table.
       *
       * @param table      the assembly name
       * @param groups     group-by column specs (name, plus optional date grouping level)
       * @param aggregates aggregate measures to apply
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists
       */
      public void setGroupAggregate(String table, List<WorksheetMutationSupport.GroupSpec> groups,
                                    List<WorksheetMutationSupport.AggregateSpec> aggregates)
         throws PairingException
      {
         setGroupAggregate(table, groups, aggregates, false);
      }

      /**
       * Builds and sets a new {@link AggregateInfo} on the named table.
       *
       * @param table      the assembly name
       * @param groups     group-by column specs (name, plus optional date grouping level)
       * @param aggregates aggregate measures to apply
       * @param crosstab   {@code true} to display the result as a crosstab (row/column
       *                   headers) rather than a flat grouped table — the Composer's own
       *                   Group and Aggregate dialog "Switch to Crosstab" toggle. Takes visible
       *                   effect only once {@code groups} has at least 2 entries and
       *                   {@code aggregates} at least 1 — see {@link AggregateInfo#isCrosstab}
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists
       */
      public void setGroupAggregate(String table, List<WorksheetMutationSupport.GroupSpec> groups,
                                    List<WorksheetMutationSupport.AggregateSpec> aggregates,
                                    boolean crosstab)
         throws PairingException
      {
         WorksheetMutationSupport.applyAggregateInfo(requireTable(table), groups, aggregates, crosstab);
      }

      // -----------------------------------------------------------------------
      // Expression column mutator
      // -----------------------------------------------------------------------

      /**
       * Adds an expression column to the named table.
       *
       * @param table      the assembly name
       * @param name       the column name
       * @param expression the expression body
       * @param type       the data type string, or {@code null}
       * @param sql        {@code true} if the expression is SQL rather than script
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists, or
       *                          if a column named {@code name} already exists on the table
       */
      public void addExpressionColumn(String table, String name, String expression,
                                      String type, boolean sql)
         throws PairingException, SecurityException
      {
         requirePermission(ResourceType.WORKSHEET_EXPRESSION_COLUMN);
         TableAssembly t = requireTable(table);

         if(name != null &&
            WorksheetMutationSupport.containsColumnNamed(t.getColumnSelection(false), name))
         {
            // Fail loud: a second column with the same identity makes later lookups by
            // name (set_conditions, set_sort, edit_expression, ...) ambiguous. Use
            // edit_expression to change an existing expression column.
            throw new PairingException(
               "A column named '" + name + "' already exists on table '" + table +
               "'. Use edit_expression to modify an existing expression column.");
         }

         WorksheetMutationSupport.addExpressionColumn(t, name, expression, type, sql);
      }

      // -----------------------------------------------------------------------
      // Sort mutator
      // -----------------------------------------------------------------------

      /**
       * Sets (or replaces) the sort direction on a column of the named table.
       *
       * @param table     the assembly name
       * @param field     the column name to sort on
       * @param direction {@code "ASC"} or {@code "DESC"} (case-insensitive)
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists
       */
      public void setSort(String table, String field, String direction) throws PairingException {
         TableAssembly t = requireTable(table);
         requireColumn(t, field, false);
         WorksheetMutationSupport.setSort(t, field, direction);
      }

      // -----------------------------------------------------------------------
      // Join mutators (low-level TableAssemblyOperator API)
      // -----------------------------------------------------------------------

      /**
       * Creates a new {@link RelationalJoinTableAssembly} joining {@code leftTable} and
       * {@code rightTable} on the given key columns and adds it to the worksheet.
       *
       * <p>Implementation note: {@link inetsoft.web.composer.ws.joins.InnerJoinService}
       * requires a live STOMP/runtime context and is not usable here.  This method uses
       * the lower-level {@link TableAssemblyOperator} API directly instead.</p>
       *
       * @param name      the name for the new join assembly
       * @param leftTable the left source table assembly name
       * @param leftKey   the column name from the left table to join on
       * @param rightTable the right source table assembly name
       * @param rightKey   the column name from the right table to join on
       * @param joinType   one of {@code "INNER"}, {@code "LEFT"}, {@code "RIGHT"},
       *                   {@code "FULL"}, {@code "CROSS"}, {@code "MERGE"}
       *                   (case-insensitive; defaults to {@code "INNER"}).
       *                   {@code "CROSS"} delegates to {@link #addCrossJoin} (no keys).
       *                   {@code "MERGE"} delegates to {@link #addMergeJoin}.
       * @param leftKeys   optional multi-key list of left column names (overrides {@code leftKey})
       * @param rightKeys  optional multi-key list of right column names (overrides {@code rightKey})
       * @throws PairingException if either source assembly is not found
       */
      public void addJoin(String name, String leftTable, String leftKey,
                          String rightTable, String rightKey,
                          String joinType,
                          List<String> leftKeys, List<String> rightKeys)
         throws PairingException, SecurityException
      {
         if(name == null || name.isBlank()) {
            throw new PairingException("Join requires a name.");
         }

         if("CROSS".equalsIgnoreCase(joinType)) {
            addCrossJoin(name, leftTable, rightTable);
            return;
         }

         if("MERGE".equalsIgnoreCase(joinType)) {
            addMergeJoin(name, new String[]{ leftTable, rightTable });
            return;
         }

         TableAssembly left  = requireTable(leftTable);
         TableAssembly right = requireTable(rightTable);

         int operation = parseJoinType(joinType);

         TableAssemblyOperator top = new TableAssemblyOperator();

         // Use multi-key if provided, else fall back to single-key
         if(leftKeys != null && rightKeys != null && !leftKeys.isEmpty()) {
            if(leftKeys.size() != rightKeys.size()) {
               throw new PairingException(
                  "leftKeys and rightKeys must have the same length: " +
                  leftKeys.size() + " vs " + rightKeys.size());
            }

            int count = leftKeys.size();

            for(int i = 0; i < count; i++) {
               TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
               op.setLeftTable(leftTable);
               op.setRightTable(rightTable);
               op.setLeftAttribute(new AttributeRef(null, leftKeys.get(i)));
               op.setRightAttribute(new AttributeRef(null, rightKeys.get(i)));
               op.setOperation(operation);
               top.addOperator(op);
            }
         }
         else {
            // Single-key fallback (backward compat)
            TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
            op.setLeftTable(leftTable);
            op.setRightTable(rightTable);
            op.setLeftAttribute(new AttributeRef(null, leftKey));
            op.setRightAttribute(new AttributeRef(null, rightKey));
            op.setOperation(operation);
            top.addOperator(op);
         }

         RelationalJoinTableAssembly join =
            new RelationalJoinTableAssembly(ws, name,
                                            new TableAssembly[]{ left, right },
                                            new TableAssemblyOperator[]{ top });
         placeAssembly(join);
      }

      /**
       * Creates a new {@link RelationalJoinTableAssembly} spanning three or more tables in a
       * single call, matching the Composer UI's multi-select-then-join capability (as opposed
       * to {@link #addJoin(String, String, String, String, String, String, List, List)}, which
       * joins exactly two).
       *
       * <p>The edges need not form a single left-to-right chain — either side of any edge may
       * name a table introduced by another edge (e.g. a hub table joined to two others) — because
       * this delegates to {@link InnerJoinService#editExistingJoinTable}, the same mechanism
       * behind Composer's own N-ary join (normally reached only via a live STOMP session through
       * {@code WSJoinTablesEvent}), which resolves edges by table name rather than by position.
       * That method takes a plain {@link Worksheet} and no runtime/session context, so it is
       * safe to call here — this mirrors {@code WorksheetTableService.buildJoinTable}, which
       * already calls it the same way for the {@code add_table} "relational join table" type.
       *
       * @param name      the name for the new join assembly
       * @param joinPaths the join edges (at least one); each names its own left/right table and
       *                  key columns, so tables may be introduced across multiple edges
       * @throws PairingException if {@code name}/{@code joinPaths} are empty, a referenced table
       *                    is not found, an edge specifies {@code joinType == "MERGE"}, or a
       *                    {@code "CROSS"} edge is combined with any other edge (a cross join is
       *                    an exclusive operation — {@link TableAssemblyOperator#checkValidity}
       *                    rejects it once the combined operator holds more than one edge — so
       *                    a lone {@code joinPaths} entry may be {@code "CROSS"}, but a 2+-edge
       *                    call may not mix one in)
       */
      public void addJoin(String name, List<WorksheetMutationSupport.JoinPathSpec> joinPaths)
         throws PairingException, SecurityException
      {
         if(name == null || name.isBlank()) {
            throw new PairingException("Join requires a name.");
         }

         // Unlike the two-table addJoin overload, this path cannot go through placeAssembly (see
         // the comment below on ws.addAssembly(join)), so the name has to be checked explicitly
         // here instead of inheriting placeAssembly's requireStorableName call.
         requireStorableName(name, "An assembly name");

         if(joinPaths == null || joinPaths.isEmpty()) {
            throw new PairingException("Multi-table join requires at least one join path.");
         }

         Set<TableAssembly> tableSet = new LinkedHashSet<>();
         TableAssemblyOperator noperator = new TableAssemblyOperator();
         boolean crossJoin = false;

         for(WorksheetMutationSupport.JoinPathSpec path : joinPaths) {
            if("MERGE".equalsIgnoreCase(path.joinType())) {
               throw new PairingException(
                  "Multi-table join does not support MERGE per edge (\"" + path.leftTable() +
                  "\" / \"" + path.rightTable() + "\"); use add_merge_join instead.");
            }

            if("CROSS".equalsIgnoreCase(path.joinType()) && joinPaths.size() > 1) {
               throw new PairingException(
                  "Multi-table join does not support combining CROSS with other edges (\"" +
                  path.leftTable() + "\" / \"" + path.rightTable() + "\" is CROSS, but " +
                  joinPaths.size() + " edges were given); a cross join must be the only edge " +
                  "in the call, or use add_cross_join for a standalone two-table cross join.");
            }

            TableAssembly left = requireTable(path.leftTable());
            TableAssembly right = requireTable(path.rightTable());
            tableSet.add(left);
            tableSet.add(right);

            int operation = parseJoinType(path.joinType());
            crossJoin = crossJoin || operation == TableAssemblyOperator.CROSS_JOIN;

            TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
            op.setLeftTable(path.leftTable());
            op.setRightTable(path.rightTable());

            if(operation != TableAssemblyOperator.CROSS_JOIN) {
               op.setLeftAttribute(new AttributeRef(null, path.leftKey()));
               op.setRightAttribute(new AttributeRef(null, path.rightKey()));
            }

            op.setOperation(operation);
            noperator.addOperator(op);
         }

         if(crossJoin) {
            requirePermission(ResourceType.CROSS_JOIN);
         }

         RelationalJoinTableAssembly join = new RelationalJoinTableAssembly(
            ws, name, tableSet.toArray(new TableAssembly[0]), new TableAssemblyOperator[0]);

         // Position + register before wiring the edges (matching placeAssembly's order for
         // every other join creator here), since editExistingJoinTable needs the assembly
         // already registered in ws to resolve table names against.
         join.setPixelOffset(new Point(25, 25));
         AssetEventUtil.adjustAssemblyPosition(join, ws);
         ws.addAssembly(join);

         try {
            innerJoinService.editExistingJoinTable(ws, join, noperator, true);
         }
         catch(PairingException | SecurityException e) {
            ws.removeAssembly(name);
            throw e;
         }
         catch(Exception e) {
            ws.removeAssembly(name);
            throw new PairingException("Failed to build multi-table join: " + e.getMessage());
         }
      }

      /**
       * Removes an assembly (typically a join assembly) from the worksheet by name.
       *
       * <p>No-ops if no assembly with {@code name} exists.</p>
       *
       * @param name the assembly name to remove
       */
      public void removeJoin(String name) {
         ws.removeAssembly(name);
      }

      // -----------------------------------------------------------------------
      // Add table (embedded)
      // -----------------------------------------------------------------------

      /**
       * Creates an {@link EmbeddedTableAssembly} with the given column names and adds
       * it to the worksheet.
       *
       * @param name    the assembly name
       * @param columns the column names to include in the private column selection
       * @throws PairingException if {@code name} is missing or blank
       */
      public void addTable(String name, String... columns) throws PairingException {
         if(name == null || name.isBlank()) {
            throw new PairingException("Table requires a name.");
         }

         EmbeddedTableAssembly t = new EmbeddedTableAssembly(ws, name);
         ColumnSelection cs = new ColumnSelection();

         for(String col : columns) {
            cs.addAttribute(new ColumnRef(new AttributeRef(null, col)));
         }

         t.setColumnSelection(cs, false);
         placeAssembly(t);
      }

      // -----------------------------------------------------------------------
      // Helper
      // -----------------------------------------------------------------------

      // -----------------------------------------------------------------------
      // Advanced condition mutators
      // -----------------------------------------------------------------------

      /**
       * Replaces the pre-aggregate condition list on a table with a full condition tree.
       *
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists, or if
       *                          {@code table} is an embedded or snapshot-embedded table
       */
      public void setConditions(String table,
                                List<WorksheetMutationSupport.ConditionNode> nodes)
         throws PairingException
      {
         TableAssembly t = requireTable(table);
         requireFilterable(t);
         requireConditionFields(t, nodes, false);
         WorksheetMutationSupport.setConditions(t, nodes, false);
      }

      /**
       * Replaces the post-aggregate condition list (HAVING) on a table.
       *
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists, or if
       *                          {@code table} is an embedded or snapshot-embedded table
       */
      public void setPostConditions(String table,
                                    List<WorksheetMutationSupport.ConditionNode> nodes)
         throws PairingException
      {
         TableAssembly t = requireTable(table);
         requireFilterable(t);
         requireConditionFields(t, nodes, true);
         WorksheetMutationSupport.setConditions(t, nodes, true);
      }

      /**
       * Sets a ranking condition (TOP N / BOTTOM N) on a table.
       */
      public void setRanking(String table, WorksheetMutationSupport.RankingSpec spec)
         throws PairingException
      {
         TableAssembly t = requireTable(table);
         requireFilterable(t);

         if(spec != null) {
            requireRankingField(t, spec.field());
         }

         WorksheetMutationSupport.setRanking(t, spec);
      }

      /**
       * Sets the table's whole ranking condition list from {@code specs}, in order — the
       * plural counterpart to {@link #setRanking}, letting a caller establish more than one
       * independent ranked field in a single call instead of the second call replacing the
       * first (see {@link WorksheetMutationSupport#setRankings}).
       */
      public void setRankings(String table, List<WorksheetMutationSupport.RankingSpec> specs)
         throws PairingException
      {
         TableAssembly t = requireTable(table);
         requireFilterable(t);

         if(specs != null) {
            for(WorksheetMutationSupport.RankingSpec spec : specs) {
               if(spec != null) {
                  requireRankingField(t, spec.field());
               }
            }
         }

         WorksheetMutationSupport.setRankings(t, specs);
      }

      // -----------------------------------------------------------------------
      // Rotate / Unpivot mutators
      // -----------------------------------------------------------------------

      /**
       * Creates a {@link RotatedTableAssembly} (transpose) from an existing table.
       */
      public void addRotate(String name, String source) throws PairingException {
         if(name == null || name.isBlank()) {
            name = AssetUtil.getNextName(ws, AbstractSheet.TABLE_ASSET);
         }

         TableAssembly src = requireTable(source);
         RotatedTableAssembly table = new RotatedTableAssembly(ws, name, src);
         table.setLiveData(true);
         placeAssembly(table);
      }

      /**
       * Creates an {@link UnpivotTableAssembly} from an existing table.
       *
       * @param name          the name for the new unpivot assembly
       * @param source        the source table to unpivot
       * @param headerColumns number of left-most columns to keep as headers
       */
      public void addUnpivot(String name, String source, int headerColumns)
         throws PairingException
      {
         if(name == null || name.isBlank()) {
            name = AssetUtil.getNextName(ws, AbstractSheet.TABLE_ASSET);
         }

         TableAssembly src = requireTable(source);
         int colCount = src.getColumnSelection(false).getAttributeCount();

         if(headerColumns < 0 || headerColumns >= colCount) {
            throw new PairingException(
               "headerColumns (" + headerColumns + ") must be between 0 and " +
               (colCount - 1) + " (table has " + colCount + " columns).");
         }

         UnpivotTableAssembly table = new UnpivotTableAssembly(ws, name, src);
         table.setLiveData(true);
         table.setHeaderColumns(headerColumns);
         placeAssembly(table);
      }

      // -----------------------------------------------------------------------
      // Date/numeric range column mutators
      // -----------------------------------------------------------------------

      /**
       * Adds a date range column to the table's column selection.
       *
       * @param table      the assembly name
       * @param column     the source date column name
       * @param dateOption the grouping option string (e.g. "YEAR", "QUARTER", "MONTH",
       *                   "WEEK", "DAY", "HOUR", "MINUTE", "SECOND")
       */
      public void addDateRangeColumn(String table, String column, String dateOption)
         throws PairingException
      {
         TableAssembly t = requireTable(table);
         ColumnSelection cs = t.getColumnSelection(false);
         DataRef ref = cs.getAttribute(column);

         if(ref == null) {
            throw new PairingException("Column not found: " + column);
         }

         String origType = ref instanceof ColumnRef cr2 ? cr2.getDataType() : null;

         if(!XSchema.isDateType(origType)) {
            throw new PairingException(
               "Column \"" + column + "\" is not a date type (type=" + origType +
               "). date_range_column requires a date, time, or timeInstant column.");
         }

         // DateRangeRef needs the inner AttributeRef, not the ColumnRef wrapper.
         DataRef baseRef = ref instanceof ColumnRef cr ? cr.getDataRef() : ref;

         int option = parseDateOption(dateOption);
         String rangeName = DateRangeRef.getName(column, option);
         DateRangeRef dateRef = new DateRangeRef(rangeName, baseRef, option);
         dateRef.setOriginalType(origType);
         // Not auto-created: this is a deliberate, standalone derived column (matching
         // ValueRangeService's own setAutoCreate(false) for the Composer's equivalent
         // UI action), so WorksheetMutationSupport#applyAggregateInfo's stale-range-
         // column sweep never removes it just because a later set_group_aggregate call
         // groups the same base column at some date level.
         dateRef.setAutoCreate(false);

         requireDerivedColumnFits(cs, rangeName, table);

         ColumnRef colRef = new ColumnRef(dateRef);
         colRef.setDataType(XSchema.STRING);
         cs.addAttribute(colRef);
         t.setColumnSelection(cs, false);
      }

      /**
       * Adds a numeric range column to the table's column selection.
       *
       * @param table      the assembly name
       * @param column     the source numeric column name
       * @param boundaries the bucket boundary values (e.g. [0, 50, 100])
       * @param labels     optional custom bucket labels, one more than {@code boundaries}
       *                   (e.g. 2 boundaries -> 3 labels: below, between, above). {@code null}
       *                   or empty keeps the engine's default auto-generated range text.
       */
      public void addNumericRangeColumn(String table, String column, double[] boundaries,
                                         String[] labels)
         throws PairingException
      {
         if(boundaries == null || boundaries.length == 0) {
            throw new PairingException(
               "boundaries must be a non-empty array of numbers (e.g. [0, 50, 100]).");
         }

         validateLabelCount(boundaries, labels);

         TableAssembly t = requireTable(table);
         ColumnSelection cs = t.getColumnSelection(false);
         DataRef ref = cs.getAttribute(column);

         if(ref == null) {
            throw new PairingException("Column not found: " + column);
         }

         // NumericRangeRef needs the inner AttributeRef, not the ColumnRef wrapper.
         DataRef baseRef = ref instanceof ColumnRef cr ? cr.getDataRef() : ref;
         NumericRangeRef rangeRef = new NumericRangeRef(column + "_range", baseRef);
         ValueRangeInfo info = new ValueRangeInfo();
         info.setValues(boundaries);
         info.setLabels(labels);
         rangeRef.setValueRangeInfo(info);

         requireDerivedColumnFits(cs, column + "_range", table);

         ColumnRef colRef = new ColumnRef(rangeRef);
         colRef.setDataType(XSchema.STRING);
         cs.addAttribute(colRef);
         t.setColumnSelection(cs, false);
      }

      /**
       * Refuses a derived column that would exceed the organization's column cap or collide with a
       * name already on the table.
       *
       * <p>{@code ValueRangeService#createNewColumn} applies both before creating one, at :193 and
       * :204, and sends an ERROR command instead of proceeding. This path applied neither. The
       * collision matters more than it looks: both range columns take their name from the source
       * column and the option rather than from the caller, so issuing the same
       * {@code add_date_range_column} twice generated the same name twice, and a second column with
       * the same identity makes every later lookup by name — set_conditions, set_sort,
       * set_group_aggregate — ambiguous. {@code addExpressionColumn} already refuses its own
       * same-name case for exactly that reason.
       */
      private static void requireDerivedColumnFits(ColumnSelection cs, String columnName,
                                                    String table)
         throws PairingException
      {
         if(cs.getAttributeCount() >= inetsoft.report.internal.Util.getOrganizationMaxColumn()) {
            throw new PairingException(
               "\"" + table + "\" already has " + cs.getAttributeCount() + " columns, the maximum " +
               "this organization allows. Remove a column before deriving another.");
         }

         if(AssetUtil.findColumnConflictingWithAlias(cs, null, columnName, true) != null) {
            throw new PairingException(
               "A column named \"" + columnName + "\" already exists on \"" + table + "\". A " +
               "derived column's name comes from the source column and the option, not from you, " +
               "so deriving the same one twice collides; use the column that is already there.");
         }
      }

      /**
       * Validates that a caller-supplied label array, if present, has exactly one more entry
       * than there are boundaries — the count {@link NumericRangeRef#getExpression} and
       * {@link NumericRangeRef#getScriptExpression} index into (bottom bucket + one per gap +
       * top bucket) given the engine's fixed defaults of showing both the bottom and top
       * buckets. A shorter array is not just cosmetically wrong: those methods index straight
       * into it with no bounds check, so a mismatch throws {@code ArrayIndexOutOfBoundsException}
       * deep inside expression generation instead of failing here with a clear message.
       */
      private static void validateLabelCount(double[] boundaries, String[] labels)
         throws PairingException
      {
         if(labels == null || labels.length == 0) {
            return;
         }

         int expected = boundaries.length + 1;

         if(labels.length != expected) {
            throw new PairingException(
               "labels must have exactly " + expected + " entries for " + boundaries.length +
               " boundaries (one below the first, one between each pair, one above the last) " +
               "— got " + labels.length + ".");
         }
      }

      /**
       * Changes the grouping level of an existing date range column, in place. The column's
       * name encodes its option (see {@link DateRangeRef#getName}), so it is renamed to match
       * the new option — e.g. {@code "QuarterOfYear(Order Date)"} becomes
       * {@code "MonthOfYear(Order Date)"} when {@code dateOption} changes from
       * {@code QUARTER_OF_YEAR} to {@code MONTH_OF_YEAR}. Anything already bound to the old
       * name is left pointing at nothing, the same as a manual {@code rename_column}.
       *
       * @param table      the assembly name
       * @param column     the existing date range column's current name
       * @param dateOption the new grouping option string (e.g. "YEAR", "QUARTER", "MONTH")
       */
      public void editDateRangeColumn(String table, String column, String dateOption)
         throws PairingException
      {
         TableAssembly t = requireTable(table);
         ColumnSelection cs = t.getColumnSelection(false);
         DataRef ref = cs.getAttribute(column);

         if(ref == null) {
            throw new PairingException("Column not found: " + column);
         }

         DataRef unwrapped = ref instanceof ColumnRef cr ? cr.getDataRef() : ref;

         if(!(unwrapped instanceof DateRangeRef dateRef)) {
            throw new PairingException(
               "Column \"" + column + "\" is not a date range column. Use " +
               "add_date_range_column to create one, or pass the range column's own name " +
               "(e.g. \"QuarterOfYear(Order Date)\"), not its source date column.");
         }

         int option = parseDateOption(dateOption);
         DataRef baseRef = dateRef.getDataRef();

         if(baseRef == null) {
            // Not reachable via add_date_range_column, which always constructs one with a
            // non-null base ref — but falling back to dateRef.getAttribute() here (the range
            // column's OWN name) would silently compute a wrong, nested name like
            // "MonthOfYear(QuarterOfYear(orderDate))" instead of failing loud.
            throw new PairingException(
               "Column \"" + column + "\" has no source column reference and cannot be re-leveled.");
         }

         String baseAttr = baseRef.getAttribute();
         String currentName = dateRef.getName();
         String newName = DateRangeRef.getName(baseAttr, option);

         // ColumnSelection enforces name-uniqueness only on addAttribute (a no-op add on
         // collision); renaming an existing entry in place bypasses that check entirely, so a
         // rename onto a name already held by another column (e.g. a second range column
         // already added at the target option) would silently shadow it instead of failing.
         if(!newName.equals(currentName)) {
            DataRef existing = cs.getAttribute(newName);

            if(existing != null && existing != ref) {
               throw new PairingException(
                  "Cannot change \"" + column + "\" to " + dateOption + " — table \"" + table +
                  "\" already has a column named \"" + newName + "\". Remove or rename that " +
                  "column first.");
            }
         }

         dateRef.setDateOption(option);
         dateRef.setName(newName);

         // AbstractDataRef.hashCode() caches its result (chash) from getEntity()+getAttribute()
         // at first use, and ColumnRef.getAttribute() delegates live to the wrapped ref's — so
         // renaming dateRef changes what that hash SHOULD be without invalidating the wrapping
         // ColumnRef's already-cached one. ColumnSelection's backing ListWithFastLookup uses
         // that cached hashCode() for its O(1) addAttribute exclusivity check (rebuilt lazily by
         // iterating current elements' hashCode(), so a per-element stale cache survives even a
         // full rebuild). Left uninvalidated, a later add_date_range_column producing this same
         // new name is not caught as a duplicate — confirmed empirically: without the reset
         // below, two columns end up reporting the identical getName(). setDataRef() re-assigns
         // the same ref purely to invalidate ColumnRef's own cname/chash, the same mechanism
         // renameColumn's setAlias() already relies on for its own (narrower) case.
         if(ref instanceof ColumnRef cr) {
            cr.setDataRef(dateRef);
         }

         t.setColumnSelection(cs, false);
      }

      /**
       * Changes the bucket boundaries (and optionally the custom labels) of an existing
       * numeric range column, in place. Unlike the date range column's name, a numeric range
       * column's name ({@code column + "_range"}) does not encode its boundaries, so it is
       * left unchanged.
       *
       * @param table      the assembly name
       * @param column     the existing numeric range column's current name
       * @param boundaries the new bucket boundary values (e.g. [0, 50, 100])
       * @param labels     optional custom bucket labels, one more than {@code boundaries}. A
       *                   {@code null}/empty array clears any custom labels back to the
       *                   engine's default auto-generated range text — it does not preserve
       *                   whatever labels the column already had, since there is no partial
       *                   "leave labels alone" signal distinguishable from "clear them."
       */
      public void editNumericRangeColumn(String table, String column, double[] boundaries,
                                          String[] labels)
         throws PairingException
      {
         if(boundaries == null || boundaries.length == 0) {
            throw new PairingException(
               "boundaries must be a non-empty array of numbers (e.g. [0, 50, 100]).");
         }

         validateLabelCount(boundaries, labels);

         TableAssembly t = requireTable(table);
         ColumnSelection cs = t.getColumnSelection(false);
         DataRef ref = cs.getAttribute(column);

         if(ref == null) {
            throw new PairingException("Column not found: " + column);
         }

         DataRef unwrapped = ref instanceof ColumnRef cr ? cr.getDataRef() : ref;

         if(!(unwrapped instanceof NumericRangeRef rangeRef)) {
            throw new PairingException(
               "Column \"" + column + "\" is not a numeric range column. Use " +
               "add_numeric_range_column to create one, or pass the range column's own name " +
               "(e.g. \"Amount_range\"), not its source numeric column.");
         }

         // Mutate the existing ValueRangeInfo rather than replacing it wholesale: this tool
         // only ever settles values/labels, but showBottomValue/showTopValue/inclusiveType are
         // also user-settable (via the Composer's own Range Column dialog, or a future richer
         // wiz op) and replacing the object outright would silently reset those three back to
         // ValueRangeInfo's constructor defaults on every edit — an undocumented side effect
         // for a tool whose whole contract is "boundaries and labels, nothing else."
         ValueRangeInfo info = rangeRef.getValueRangeInfo();

         if(info == null) {
            info = new ValueRangeInfo();
         }

         info.setValues(boundaries);
         // Unconditional, even when labels is null/empty: the javadoc above promises omitting
         // labels clears any existing ones, and that contract must hold now that boundaries'
         // sibling settings survive the edit.
         info.setLabels(labels);
         rangeRef.setValueRangeInfo(info);
         t.setColumnSelection(cs, false);
      }

      static int parseDateOption(String dateOption) throws PairingException {
         if(dateOption == null) {
            return DateRangeRef.YEAR_INTERVAL;
         }

         return switch(dateOption.toUpperCase()) {
            case "YEAR"    -> DateRangeRef.YEAR_INTERVAL;
            case "QUARTER" -> DateRangeRef.QUARTER_INTERVAL;
            case "MONTH"   -> DateRangeRef.MONTH_INTERVAL;
            case "WEEK"    -> DateRangeRef.WEEK_INTERVAL;
            case "DAY"     -> DateRangeRef.DAY_INTERVAL;
            case "HOUR"    -> DateRangeRef.HOUR_INTERVAL;
            case "MINUTE"  -> DateRangeRef.MINUTE_INTERVAL;
            case "SECOND"  -> DateRangeRef.SECOND_INTERVAL;
            case "QUARTER_OF_YEAR"   -> DateRangeRef.QUARTER_OF_YEAR_PART;
            case "MONTH_OF_YEAR"     -> DateRangeRef.MONTH_OF_YEAR_PART;
            case "WEEK_OF_YEAR"      -> DateRangeRef.WEEK_OF_YEAR_PART;
            case "DAY_OF_MONTH"      -> DateRangeRef.DAY_OF_MONTH_PART;
            case "DAY_OF_WEEK"       -> DateRangeRef.DAY_OF_WEEK_PART;
            case "HOUR_OF_DAY"       -> DateRangeRef.HOUR_OF_DAY_PART;
            case "MINUTE_OF_HOUR"    -> DateRangeRef.MINUTE_OF_HOUR_PART;
            case "SECOND_OF_MINUTE"  -> DateRangeRef.SECOND_OF_MINUTE_PART;
            default -> throw new PairingException(
               "Unrecognized date level: '" + dateOption + "'. Valid values: YEAR, QUARTER, " +
               "MONTH, WEEK, DAY, HOUR, MINUTE, SECOND, QUARTER_OF_YEAR, MONTH_OF_YEAR, " +
               "WEEK_OF_YEAR, DAY_OF_MONTH, DAY_OF_WEEK, HOUR_OF_DAY, MINUTE_OF_HOUR, " +
               "SECOND_OF_MINUTE.");
         };
      }

      /**
       * Reverse of {@link #parseDateOption}: converts a {@link GroupRef#getDateGroup()} /
       * {@link DateRangeRef} option constant back to the option string accepted by
       * {@code dateOption} / {@code dateLevel}. Returns {@code null} for
       * {@code NONE_DATE_GROUP}. A recognized {@code XConstants} date-group constant that
       * this vocabulary cannot name (reachable from the Composer's Group and Aggregate
       * dialog but not from {@link #parseDateOption}) is reported as
       * {@code "UNKNOWN_DATE_GROUP(<n>)"} rather than {@code null}, so callers can tell
       * "no date group" apart from "grouped at a level this API can't yet name".
       */
      static String dateOptionName(int dateOption) {
         return switch(dateOption) {
            case DateRangeRef.NONE_INTERVAL -> null;
            case DateRangeRef.YEAR_INTERVAL -> "YEAR";
            case DateRangeRef.QUARTER_INTERVAL -> "QUARTER";
            case DateRangeRef.MONTH_INTERVAL -> "MONTH";
            case DateRangeRef.WEEK_INTERVAL -> "WEEK";
            case DateRangeRef.DAY_INTERVAL -> "DAY";
            case DateRangeRef.HOUR_INTERVAL -> "HOUR";
            case DateRangeRef.MINUTE_INTERVAL -> "MINUTE";
            case DateRangeRef.SECOND_INTERVAL -> "SECOND";
            case DateRangeRef.QUARTER_OF_YEAR_PART -> "QUARTER_OF_YEAR";
            case DateRangeRef.MONTH_OF_YEAR_PART -> "MONTH_OF_YEAR";
            case DateRangeRef.WEEK_OF_YEAR_PART -> "WEEK_OF_YEAR";
            case DateRangeRef.DAY_OF_MONTH_PART -> "DAY_OF_MONTH";
            case DateRangeRef.DAY_OF_WEEK_PART -> "DAY_OF_WEEK";
            case DateRangeRef.HOUR_OF_DAY_PART -> "HOUR_OF_DAY";
            case DateRangeRef.MINUTE_OF_HOUR_PART -> "MINUTE_OF_HOUR";
            case DateRangeRef.SECOND_OF_MINUTE_PART -> "SECOND_OF_MINUTE";
            default -> "UNKNOWN_DATE_GROUP(" + dateOption + ")";
         };
      }

      // -----------------------------------------------------------------------
      // Assembly creation mutators
      // -----------------------------------------------------------------------

      /**
       * Creates a {@link ConcatenatedTableAssembly} from two or more existing tables
       * and adds it to the worksheet.
       *
       * @param name      the name for the new concatenated assembly
       * @param tables    the source table assembly names (at least two)
       * @param opType    one of {@code "UNION"}, {@code "INTERSECT"}, {@code "MINUS"}
       *                  (case-insensitive; defaults to {@code "UNION"})
       * @throws PairingException if fewer than two tables are given, a source is not found, or the
       *                          sources do not line up — same number of visible columns, and
       *                          position-by-position mergeable types
       */
      public void addConcatenation(String name, List<String> tables, String opType)
         throws PairingException
      {
         addConcatenation(name, tables, opType, null);
      }

      /**
       * @param distinct whether each pair's operator de-duplicates — the Composer's concatenation
       *                 dialog exposes this per operator, and its write path carries it through
       *                 {@code WorksheetEventUtil#convertOperator}. {@code null} leaves the
       *                 engine's default, which is what this op always used before.
       */
      public void addConcatenation(String name, List<String> tables, String opType,
                                   Boolean distinct)
         throws PairingException
      {
         if(name == null || name.isBlank()) {
            name = AssetUtil.getNextName(ws, AbstractSheet.TABLE_ASSET);
         }

         // Refuse a name already in use. Worksheet#addAssembly replaces a same-named assembly
         // without complaint, so reusing a name never created a second assembly -- it destroyed
         // the first and handed every dependent of that name to the replacement. Reproduced
         // through this op: a concatenation named after one of its own sources became its own
         // source, ended up with zero columns, and left the concatenation downstream of the
         // destroyed original reading from an empty table. The op reported a 500 while all of
         // that stuck.
         //
         // This guard is also what makes the absence of a cycle check on this path correct rather
         // than lucky: a concatenation built under a free name has no dependents yet, so it cannot
         // close a cycle. That is why ConcatenateTablesService does not check for one on creation
         // either, and why only addConcatSubtable needs checkCyclicalDependency. Only an explicit
         // name can collide -- getNextName above is chosen to be free.
         if(ws.getAssembly(name) != null) {
            throw new PairingException(
               "Assembly already exists: " + name + ". Creating a concatenation under an existing " +
               "name replaces that assembly and silently repoints everything built on it at the " +
               "replacement; choose another name, or delete the existing assembly first.");
         }

         if(tables == null || tables.size() < 2) {
            throw new PairingException("Concatenation requires at least two tables.");
         }

         // Refuse a repeated source, as ConcatenateTablesService#checkValidity does with
         // common.table.unionDuplicate. Checked on the names the caller passed rather than after
         // resolution, so the message can name the duplicate the caller actually wrote.
         for(int i = 0; i < tables.size(); i++) {
            for(int j = i + 1; j < tables.size(); j++) {
               if(tables.get(i) != null && tables.get(i).equals(tables.get(j))) {
                  throw new PairingException(
                     "\"" + tables.get(i) + "\" appears twice in the source list. Concatenating a " +
                     "table with itself counts its rows twice; list it once, or add a mirror of it " +
                     "with add_mirror if two independent copies are intended.");
               }
            }
         }

         int operation = parseConcatType(opType);
         TableAssembly[] sources = new TableAssembly[tables.size()];

         for(int i = 0; i < tables.size(); i++) {
            sources[i] = requireTable(tables.get(i));
         }

         // Validate that all tables line up: same column count, and column i of each table
         // mergeable with column i of the first. Sources are concatenated BY POSITION, not by
         // name, so a pair that does not line up produces a column carrying two unrelated kinds
         // of value — which renders as an ordinary column and reports no error anywhere. The
         // Composer runs the same check (ConcatenatedTableAssembly.tableAssembliesAreCompatible,
         // surfaced as concatenationWarning); this path previously checked only the count.
         //
         // Comparing every table against the first rather than against its predecessor (which is
         // what ConcatenatedTableAssembly.areCompatible does) is equivalent, since isMergeable
         // partitions types into disjoint classes — string {string, char}, number {float, double,
         // byte, short, integer, long}, date {date, timeInstant}, identity otherwise — and is
         // therefore transitive. Anchoring on the first also matches getDefaultColumnSelection,
         // which takes the resulting column list from subtables[0].
         //
         // Both counts and positions come from the PUBLIC column selection, so hidden columns are
         // excluded — exactly what the server itself concatenates.
         ColumnSelection firstColumns = sources[0].getColumnSelection(true);
         int colCount = firstColumns.getAttributeCount();

         for(int i = 1; i < sources.length; i++) {
            ColumnSelection otherColumns = sources[i].getColumnSelection(true);
            int otherCount = otherColumns.getAttributeCount();

            if(otherCount != colCount) {
               throw new PairingException(
                  "Data blocks that do not have the same number of columns cannot be " +
                  "concatenated. \"" + sources[0].getName() + "\" has " + colCount +
                  " visible column" + (colCount == 1 ? "" : "s") + " but \"" +
                  sources[i].getName() + "\" has " + otherCount + " visible column" +
                  (otherCount == 1 ? "" : "s") + ".");
            }

            for(int c = 0; c < colCount; c++) {
               // Read the type off DataRef rather than narrowing to ColumnRef: skipping a position
               // whose ref is some other kind would silently leave it unchecked, which is the very
               // failure this validation exists to prevent.
               DataRef first = firstColumns.getAttribute(c);
               DataRef other = otherColumns.getAttribute(c);
               String ftype = first.getDataType();
               String otype = other.getDataType();

               // isMergeable dereferences both arguments; a ref with no type at all is not
               // something this check can speak to, so leave it to the server.
               if(ftype == null || otype == null) {
                  continue;
               }

               if(!AssetUtil.isMergeable(ftype, otype)) {
                  throw new PairingException(
                     "Columns are concatenated by position, and position " + (c + 1) +
                     " does not line up: \"" + sources[0].getName() + "\" has \"" +
                     first.getAttribute() + "\" (" + ftype + ") while \"" + sources[i].getName() +
                     "\" has \"" + other.getAttribute() + "\" (" + otype + "), and those types " +
                     "cannot be merged. Reorder the columns so matching ones share a position, " +
                     "or change one column's type with change_column_type.");
               }
            }
         }

         // Build one operator per adjacent pair.
         TableAssemblyOperator[] operators = new TableAssemblyOperator[sources.length - 1];

         for(int i = 0; i < operators.length; i++) {
            TableAssemblyOperator top = new TableAssemblyOperator();
            TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
            op.setOperation(operation);

            // Only when asked, so omitting it keeps whatever the engine defaults to rather than
            // this op deciding on the caller's behalf.
            if(distinct != null) {
               op.setDistinct(distinct);
            }

            top.addOperator(op);
            operators[i] = top;
         }

         ConcatenatedTableAssembly ctbl =
            new ConcatenatedTableAssembly(ws, name, sources, operators);
         placeAssembly(ctbl);
      }

      /**
       * Creates a {@link MirrorTableAssembly} that references an existing table
       * assembly in the worksheet.
       *
       * @param name   the name for the new mirror assembly
       * @param source the name of the table assembly to mirror
       * @throws PairingException if the source assembly is not found
       */
      public void addMirror(String name, String source) throws PairingException {
         if(name == null || name.isBlank()) {
            name = AssetUtil.getNextName(ws, AbstractSheet.TABLE_ASSET);
         }

         Assembly a = ws.getAssembly(source);

         if(!(a instanceof WSAssembly wsa)) {
            throw new PairingException("Source assembly not found: " + source);
         }

         MirrorTableAssembly mirror = new MirrorTableAssembly(ws, name, wsa);
         placeAssembly(mirror);
      }

      // -----------------------------------------------------------------------
      // Table-level mutators
      // -----------------------------------------------------------------------

      /**
       * Removes a table assembly from the worksheet.
       *
       * @param table the assembly name to delete
       * @throws PairingException if no assembly with {@code table} exists
       */
      public void deleteTable(String table) throws PairingException {
         Assembly a = ws.getAssembly(table);

         if(a == null) {
            throw new PairingException("Table not found in worksheet: " + table);
         }

         // Refuse to delete a table another assembly is built on. ws.removeAssembly does not clean
         // the dependent up: its removeMirrors call returns immediately unless the assembly BEING
         // deleted is itself an outer mirror -- it clears a deleted outer mirror's own imported
         // assemblies, not the mirrors that depend on the deleted table. So the dependent survived
         // referencing a name that was gone, every query against it failed with "not found", and no
         // tool on this surface could repair it.
         //
         // Same guard the Composer's own delete uses on its write path, at
         // WSRemoveAssembliesService#removeAssemblies. It differs there in what it does with the
         // answer: the UI is deleting a batch, so it skips the offending assembly, warns, and
         // carries on with the rest. This op deletes one named table per call -- there is no rest
         // to carry on with, and skipping silently would report success for a deletion that never
         // happened.
         if(AssetEventUtil.hasDependent(a, ws, Set.of(table))) {
            throw new PairingException(
               "\"" + table + "\" cannot be deleted because other assemblies are built on it. " +
               "Deleting it would leave them referencing a table that no longer exists, every " +
               "query against them failing, and nothing able to repair them. Delete the " +
               "assemblies that depend on it first -- read_worksheet_model reports each table's " +
               "`sources`, which is what references what.");
         }

         ws.removeAssembly(table);
      }

      /**
       * Renames a table assembly in the worksheet.  All internal references
       * (joins, mirrors, etc.) are updated automatically by
       * {@link Worksheet#renameAssembly}.
       *
       * @param oldName the current assembly name
       * @param newName the desired new name
       * @throws PairingException if no assembly with {@code oldName} exists or
       *                          the rename fails
       */
      public void renameTable(String oldName, String newName) throws PairingException {
         Assembly a = ws.getAssembly(oldName);

         if(a == null) {
            throw new PairingException("Table not found in worksheet: " + oldName);
         }

         requireStorableName(newName, "A table name");

         if(!ws.renameAssembly(oldName, newName, true)) {
            throw new PairingException(
               "Failed to rename table '" + oldName + "' to '" + newName +
               "' — the name may already be in use.");
         }
      }

      // -----------------------------------------------------------------------
      // Column property mutators
      // -----------------------------------------------------------------------

      /**
       * Sets the visibility of a column in the table's public column selection.
       *
       * @param table   the assembly name
       * @param col     the column attribute name
       * @param visible {@code true} to show, {@code false} to hide
       * @throws PairingException if the table or column is not found, or if hiding would
       *                          break a dependent join/composite table that still uses
       *                          this column
       */
      public void setColumnVisibility(String table, String col, boolean visible)
         throws PairingException
      {
         TableAssembly t = requireTable(table);
         ColumnSelection cs = t.getColumnSelection(false);
         DataRef ref = cs.getAttribute(col);

         if(!(ref instanceof ColumnRef cr)) {
            throw new PairingException("Column not found: " + col);
         }

         if(cr.isVisible() && !visible && !WorksheetControllerService.allowsDeletion(ws, t, cr)) {
            throw new PairingException(Catalog.getCatalog().getString(
               "common.columnDependency", col));
         }

         cr.setVisible(visible);
      }

      /**
       * Changes the data type of a column in the table's public column selection.
       *
       * @param table the assembly name
       * @param col   the column attribute name
       * @param type  the new data type string (e.g. {@code "string"}, {@code "double"},
       *              {@code "integer"}, {@code "date"}, {@code "boolean"})
       * @throws PairingException if the table or column is not found
       */
      public void changeColumnType(String table, String col, String type) throws Exception {
         changeColumnType(table, col, type, true);
      }

      /**
       * @param force what to do with values the target type cannot parse: {@code true} overwrites
       *              them with {@code null}, {@code false} refuses the conversion and leaves the
       *              column as it was. See {@link EditRequest#confirmed()}.
       */
      public void changeColumnType(String table, String col, String type, boolean force)
         throws Exception
      {
         if(!XSchema.isPrimitiveType(type)) {
            throw new PairingException(
               "Invalid column type: \"" + type + "\". Valid types: " +
               "string, boolean, float, double, integer, long, short, byte, " +
               "char, date, time, timeInstant.");
         }

         TableAssembly t = requireTable(table);

         // Use the public column selection (matching the UI's ColumnTypeDialogService
         // approach) so that AssetUtil.findColumn can match against the embedded data.
         ColumnSelection cs = t.getColumnSelection();
         DataRef ref = cs.getAttribute(col);

         if(ref == null) {
            // Fall back to private column selection.
            cs = t.getColumnSelection(false);
            ref = cs.getAttribute(col);
         }

         if(!(ref instanceof ColumnRef cr)) {
            throw new PairingException("Column not found: " + col);
         }

         // Mirrors the UI's WSHeaderCellComponent.supportChangeColumnType(): a type
         // override only survives the post-edit refreshColumnSelection() pass for
         // expression columns, or for embedded/tabular/SQL-bound/unpivot tables that
         // persist it into their own definition — every other table type has its
         // column selection rebuilt from the source schema, discarding the override.
         boolean isRangeRef = cr.getDataRef() instanceof NumericRangeRef ||
            cr.getDataRef() instanceof DateRangeRef;

         // Mirrors the UI's isExpressionAggregate exclusion: an expression column
         // that is also one of the table's aggregate measures is still rejected even
         // though cr.isExpression() would otherwise allow it.
         boolean isExpressionAggregate = cr.isExpression() && t.isAggregate() &&
            t.getAggregateInfo() != null && t.getAggregateInfo().getAggregate(cr) != null;

         boolean supportsTypeChange = !isRangeRef && !isExpressionAggregate && (cr.isExpression() ||
            t instanceof EmbeddedTableAssembly || t instanceof TabularTableAssembly ||
            t instanceof SQLBoundTableAssembly || t instanceof UnpivotTableAssembly);

         if(!supportsTypeChange) {
            throw new PairingException(
               "Column type cannot be changed for \"" + col + "\" on table \"" + table +
               "\". Changing the data type is only supported for expression columns " +
               "and embedded, tabular/REST, SQL-bound, or unpivot tables — physical " +
               "(bound query) tables and joins do not support changing column types.");
         }

         // Also update the matching ref from findAttribute (same approach as
         // ColumnTypeDialogService) to ensure the canonical ref is updated.
         ColumnRef cr2 = (ColumnRef) cs.findAttribute(cr);
         // Captured before the change so the embedded-data conversion below can put it back if
         // the values will not parse and the caller did not ask to force it.
         String oldType = cr2 != null ? cr2.getDataType() : cr.getDataType();

         if(cr2 != null) {
            cr2.setDataType(type);
         }
         else {
            cr.setDataType(type);
         }

         // For embedded tables, also update the underlying XEmbeddedTable data type.
         // Without this, refreshColumnSelection rebuilds the column selection from
         // the data and resets the type back to the original.
         if(t instanceof EmbeddedTableAssembly embedded) {
            XEmbeddedTable data = embedded.getEmbeddedData();

            if(data != null) {
               int index = AssetUtil.findColumn(data, cr2 != null ? cr2 : cr);

               if(index >= 0) {
                  try {
                     data.setDataType(index, type, null, null, force);
                  }
                  catch(Exception ex) {
                     // force=false means setDataType throws rather than nulling the values it
                     // cannot parse. Put the column's declared type back before rethrowing:
                     // it was already changed above, so leaving it would advertise a type the
                     // data does not have. The Composer does the same in
                     // ColumnTypeDialogService#handleChangeTypeParseException, which restores the
                     // old type and then asks the user whether to force it.
                     if(cr2 != null) {
                        cr2.setDataType(oldType);
                     }
                     else {
                        cr.setDataType(oldType);
                     }

                     throw new PairingException(
                        "\"" + col + "\" has values that cannot be converted to " + type +
                        ". Nothing was changed. Pass confirmed=true to convert anyway, which " +
                        "replaces every value that will not parse with null -- that discards " +
                        "data and cannot be undone by changing the type back.");
                  }

                  embedded.setEmbeddedData(data);
               }
            }
         }
      }

      // -----------------------------------------------------------------------
      // Edit-in-place mutators
      // -----------------------------------------------------------------------

      /**
       * Replaces the existing filter condition on {@code field} with a new one.
       *
       * <p>Implemented as remove-then-add so the old condition is fully cleared
       * before the replacement is appended.</p>
       *
       * @param table     the assembly name
       * @param field     the column name whose condition to replace
       * @param operation new comparison operator
       * @param values    new literal values
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists, or if
       *                          {@code table} is an embedded or snapshot-embedded table
       */
      public void editCondition(String table, String field,
                                String operation, String... values)
         throws PairingException
      {
         TableAssembly t = requireTable(table);
         requireFilterable(t);
         WorksheetMutationSupport.removeFilter(t, field);
         WorksheetMutationSupport.addFilter(t, field, operation, values);
      }

      /**
       * Updates the expression body and type of an existing expression column,
       * or adds it if it does not exist yet.
       *
       * @param table      the assembly name
       * @param name       the expression column name to find and update
       * @param expression the new expression body
       * @param type       the new data type string, or {@code null} to leave unchanged
       * @param sql        {@code true} if the expression is SQL rather than script
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists
       */
      public void editExpression(String table, String name, String expression,
                                 String type, boolean sql)
         throws PairingException, SecurityException
      {
         requirePermission(ResourceType.WORKSHEET_EXPRESSION_COLUMN);
         WorksheetMutationSupport.editExpression(requireTable(table), name, expression, type, sql);
      }

      /**
       * Replaces the key columns and join type of an existing two-table join assembly,
       * keeping the same source tables.
       *
       * <p>Supports both single-key ({@code leftKey}/{@code rightKey}) and multi-key
       * ({@code leftKeys}/{@code rightKeys}) joins. When the list parameters are provided
       * they take precedence and fully replace all key pairs on the first operator.</p>
       *
       * @param name      the join assembly name
       * @param leftKey   the new left-side key column (single-key fallback)
       * @param rightKey  the new right-side key column (single-key fallback)
       * @param joinType  new join type — {@code "INNER"}, {@code "LEFT"}, {@code "RIGHT"},
       *                  {@code "FULL"}, {@code "CROSS"} (case-insensitive; defaults to
       *                  {@code "INNER"}). {@code "CROSS"} requires the same CROSS_JOIN
       *                  permission as {@link #addCrossJoin}.
       * @throws PairingException if the assembly is not found or has no operators
       * @throws SecurityException if changing to a cross join and the caller lacks
       *                           CROSS_JOIN permission
       */
      public void editJoin(String name, String leftKey, String rightKey, String joinType,
                           List<String> leftKeys, List<String> rightKeys)
         throws PairingException, SecurityException
      {
         Assembly a = ws.getAssembly(name);

         if(!(a instanceof RelationalJoinTableAssembly join)) {
            throw new PairingException("Join assembly not found: " + name);
         }

         @SuppressWarnings("unchecked")
         Enumeration<TableAssemblyOperator> iter =
            (Enumeration<TableAssemblyOperator>) join.getOperators();

         if(!iter.hasMoreElements()) {
            throw new PairingException("Join assembly has no operators: " + name);
         }

         TableAssemblyOperator top = iter.nextElement();

         if(top.getOperatorCount() == 0) {
            throw new PairingException("Join assembly has no key pairs: " + name);
         }

         // Preserve the existing left/right table names.
         TableAssemblyOperator.Operator existing = top.getOperator(0);
         String leftTable  = existing.getLeftTable();
         String rightTable = existing.getRightTable();
         int operation = parseJoinType(joinType);

         // Changing an existing join into a cross join is as sensitive as creating
         // one, so it must clear the same permission gate as addCrossJoin(); otherwise
         // a caller denied CROSS_JOIN could add an INNER join and then edit it to CROSS.
         if(operation == TableAssemblyOperator.CROSS_JOIN) {
            requirePermission(ResourceType.CROSS_JOIN);
         }

         // Build a replacement operator with updated keys and join type.
         TableAssemblyOperator newTop = new TableAssemblyOperator();

         if(leftKeys != null && rightKeys != null && !leftKeys.isEmpty()) {
            int count = Math.min(leftKeys.size(), rightKeys.size());

            for(int i = 0; i < count; i++) {
               TableAssemblyOperator.Operator newOp = new TableAssemblyOperator.Operator();
               newOp.setLeftTable(leftTable);
               newOp.setRightTable(rightTable);
               newOp.setLeftAttribute(new AttributeRef(null, leftKeys.get(i)));
               newOp.setRightAttribute(new AttributeRef(null, rightKeys.get(i)));
               newOp.setOperation(operation);
               newTop.addOperator(newOp);
            }
         }
         else if(leftKey != null && rightKey != null) {
            TableAssemblyOperator.Operator newOp = new TableAssemblyOperator.Operator();
            newOp.setLeftTable(leftTable);
            newOp.setRightTable(rightTable);
            newOp.setLeftAttribute(new AttributeRef(null, leftKey));
            newOp.setRightAttribute(new AttributeRef(null, rightKey));
            newOp.setOperation(operation);
            newTop.addOperator(newOp);
         }
         else {
            // No new keys provided — preserve existing key pairs, only update join type.
            for(int i = 0; i < top.getOperatorCount(); i++) {
               TableAssemblyOperator.Operator orig = top.getOperator(i);
               TableAssemblyOperator.Operator newOp = new TableAssemblyOperator.Operator();
               newOp.setLeftTable(orig.getLeftTable());
               newOp.setRightTable(orig.getRightTable());
               newOp.setLeftAttribute(orig.getLeftAttribute());
               newOp.setRightAttribute(orig.getRightAttribute());
               newOp.setOperation(operation);
               newTop.addOperator(newOp);
            }
         }

         join.setOperator(leftTable, rightTable, newTop);
      }

      // -----------------------------------------------------------------------
      // Embedded table cell editing
      // -----------------------------------------------------------------------

      /**
       * Resolves a table that the cell/row edit ops are allowed to write to.
       *
       * <p>Two refusals, and the difference between them matters. A non-embedded table simply
       * has no embedded data to edit. A <i>snapshot</i> embedded table does have data -- but
       * every {@link XEmbeddedTable} mutator is copy-and-swap rather than in-place, and
       * {@link SnapshotEmbeddedTableAssembly#getEmbeddedData()} hands out a freshly built
       * wrapper on every call, so the swap lands on a throwaway object and the write is
       * discarded. Before this check the three ops returned success and changed nothing.
       *
       * <p>Refusing is the right answer rather than adding a {@code setEmbeddedData} write-back:
       * the Composer does not offer cell editing on a snapshot either (its
       * {@code snapshot-embedded-table-assembly.ts} overrides {@code isEditable()} to false, so
       * such a table never enters edit mode), and refusing is what makes the agent path agree
       * with the UI. Filtering already refuses a snapshot the same way -- see the
       * {@code composer.ws.filter-snapshopt} catalog string.
       *
       * @param table the assembly name
       * @param op    the op name to quote back to the caller, e.g. {@code "edit_cell"}
       * @return the embedded table assembly, safe to mutate
       * @throws PairingException if the table is not embedded, or is a snapshot
       */
      private EmbeddedTableAssembly requireEditableEmbedded(String table, String op)
         throws PairingException
      {
         TableAssembly t = requireTable(table);

         if(t instanceof SnapshotEmbeddedTableAssembly) {
            throw new PairingException(
               op + " is not supported on a snapshot embedded table: " + table +
               ". Every table imported through the Composer's own file-import wizard is a " +
               "snapshot; its data lives in a swapped-out data file that the cell and row edit " +
               "ops cannot write to. The Composer does not offer cell editing on a snapshot " +
               "either, so there is no workaround in the UI. read_worksheet_model reports these " +
               "tables as type \"EMBEDDED_SNAPSHOT\" - check that before editing. To get an " +
               "editable copy of the data, re-import the source file with import_csv_table or " +
               "import_excel_table: those build a plain embedded table, which does accept " +
               "edit_cell, insert_row and delete_row.");
         }

         if(!(t instanceof EmbeddedTableAssembly embedded)) {
            throw new PairingException(op + " only works on embedded tables: " + table);
         }

         return embedded;
      }

      /**
       * Edits a single cell in an embedded table.
       *
       * @param table the assembly name (must be an embedded table, and not a snapshot)
       * @param row   the 0-based data row index
       * @param col   the 0-based column index
       * @param value the new cell value as a string (parsed according to column type)
       * @throws PairingException if the table is not editable or indices are out of range
       */
      public void editCell(String table, int row, int col, String value)
         throws Exception
      {
         EmbeddedTableAssembly embedded = requireEditableEmbedded(table, "edit_cell");
         XEmbeddedTable data = embedded.getEmbeddedData();

         if(data == null) {
            throw new PairingException("Table has no data: " + table);
         }

         ColumnSelection cs = embedded.getColumnSelection(false);

         if(col < 0 || col >= cs.getAttributeCount()) {
            throw new PairingException("Column index out of range: " + col);
         }

         int dataRow = row + 1; // row 0 in XEmbeddedTable is header

         if(dataRow < 1 || dataRow >= data.getRowCount()) {
            throw new PairingException("Row index out of range: " + row);
         }

         // Find the actual column index in the embedded table
         DataRef attr = cs.getAttribute(col);
         int dataCol = AssetUtil.findColumn(data, attr);

         if(dataCol < 0) {
            throw new PairingException("Column not found in data: " + attr.getName());
         }

         // Matches WSEditTableDataService.java:101-108's cell-size cap. That native path
         // truncates and warns via a MessageCommand -- a channel only a connected UI client
         // can see. An agent caller has no equivalent channel, so a silent truncation would
         // land as a plausible-but-wrong value with no signal anything was cut, which is a
         // worse surprise for an agent than for a human watching the grid. Refusing instead
         // also matches this method's own neighboring guards (column/row bounds above), which
         // all fail loud rather than silently coercing the input.
         if(value != null && value.length() > Util.getOrganizationMaxCellSize()) {
            throw new PairingException(
               "Value is " + value.length() + " characters, exceeding the " +
               Util.getOrganizationMaxCellSize() + "-character limit for a cell in this " +
               "organization. Shorten the value.");
         }

         String dtype = attr instanceof ColumnRef ? ((ColumnRef) attr).getDataType() : null;
         Object parsed;

         try {
            parsed = value != null ? AssetUtil.parse(dtype, value) : null;
         }
         catch(Exception e) {
            // Matches WSEditTableDataService.java:129-141's shape: a value that doesn't parse
            // as the column's type is refused, and the prior cell value is left untouched --
            // it is not applied here either, since this catch runs before data.setObject.
            // Previously this exception propagated uncaught instead of as a clean rejection.
            throw new PairingException(
               Catalog.getCatalog().getString("common.dataFormatErrorParam", value));
         }

         data.setObject(dataRow, dataCol, parsed);
      }

      /**
       * Inserts a new empty row into an embedded table.
       *
       * @param table the assembly name (must be an embedded table, and not a snapshot)
       * @param index the 0-based data row index at which to insert
       * @throws PairingException if the table is not editable
       */
      public void insertRow(String table, int index) throws PairingException {
         EmbeddedTableAssembly embedded = requireEditableEmbedded(table, "insert_row");
         XEmbeddedTable data = embedded.getEmbeddedData();

         if(data == null) {
            throw new PairingException("Table has no data: " + table);
         }

         if(index < 0) {
            throw new PairingException("Row index must be >= 0, got: " + index);
         }

         int dataRow = index + 1; // +1 for header row

         if(dataRow > data.getRowCount()) {
            dataRow = data.getRowCount();
         }

         data.insertRow(dataRow);
      }

      /**
       * Deletes a row from an embedded table.
       *
       * @param table the assembly name (must be an embedded table, and not a snapshot)
       * @param index the 0-based data row index to delete
       * @throws PairingException if the table is not editable or the index is out of range
       */
      public void deleteRow(String table, int index) throws PairingException {
         EmbeddedTableAssembly embedded = requireEditableEmbedded(table, "delete_row");
         XEmbeddedTable data = embedded.getEmbeddedData();

         if(data == null) {
            throw new PairingException("Table has no data: " + table);
         }

         int dataRow = index + 1; // +1 for header row

         if(dataRow < 1 || dataRow >= data.getRowCount()) {
            throw new PairingException("Row index out of range: " + index);
         }

         data.deleteRow(dataRow);
      }

      // -----------------------------------------------------------------------
      // Table-level properties
      // -----------------------------------------------------------------------

      /**
       * Sets optional properties on a table assembly.
       *
       * @param table       the assembly name
       * @param alias       table alias, or {@code null} to leave unchanged
       * @param description table description, or {@code null} to leave unchanged
       * @param maxRows     max rows limit, or {@code null} to leave unchanged
       * @param distinct    distinct flag, or {@code null} to leave unchanged
       * @param mergeable   SQL-mergeable flag, or {@code null} to leave unchanged
       * @param visibleInViewsheet visible-in-viewsheet flag, or {@code null} to leave unchanged
       * @throws PairingException if the table is not found
       */
      /**
       * Applies the table properties, renaming the table when {@code newName} is given.
       *
       * <p>A worksheet table has no display name separate from its name -- the field exists on
       * {@code AssetEntry}, {@code ColumnRef} and {@code WorksheetInfo}, on no {@code TableAssembly}
       * -- so setting one is a rename. That is the shape the Composer's own table-properties dialog
       * already has: {@code TablePropertyDialogModel} carries {@code newName}/{@code oldName} beside
       * description, maxRows and distinct, and its service applies the properties and then renames
       * through {@code refreshAssembly}. This used to accept an {@code alias} argument and drop it
       * behind a comment, returning success while changing nothing.
       *
       * <p><b>The rename runs first, so a failure leaves everything untouched.</b> Renaming can fail
       * on a name already in use, and applying the other properties before finding that out would
       * half-write the patch -- the outcome this service refuses everywhere else. The remaining
       * setters cannot fail, so ordering it this way makes the whole call all-or-nothing.
       */
      public void setTableProperties(String table, String newName, String description,
                                      Integer maxRows, Boolean distinct, Boolean mergeable,
                                      Boolean visibleInViewsheet)
         throws PairingException
      {
         setTableProperties(table, newName, description, maxRows, distinct, mergeable,
                            visibleInViewsheet, null);
      }

      /**
       * @param rowCount for an embedded table, how many data rows it should hold — the dialog's
       *                 "Rows" field ({@code TablePropertyDialogService:113-121}). Ignored for a
       *                 table that is not embedded, as the dialog omits the control there.
       */
      public void setTableProperties(String table, String newName, String description,
                                      Integer maxRows, Boolean distinct, Boolean mergeable,
                                      Boolean visibleInViewsheet, Integer rowCount)
         throws PairingException
      {
         // Resolved before the rename so an unknown table is reported against the name the caller
         // passed, not against a name that does not exist yet.
         requireTable(table);
         String name = table;

         if(newName != null && !newName.equals(table)) {
            // Worksheet.renameAssembly checks only that the old name exists and the new one is
            // free -- a blank name passes both and leaves a table nothing can address afterwards.
            // Guarded here rather than only in the plugin, since this endpoint is reachable
            // without it.
            if(newName.trim().isEmpty()) {
               throw new PairingException(
                  "Cannot rename  + table +  to a blank name. Omit newName to leave the name " +
                  "alone; a blank one would be accepted and leave the table unaddressable.");
            }

            renameTable(table, newName);
            name = newName;
         }

         TableAssembly t = requireTable(name);

         if(description != null) {
            t.setDescription(description);
         }

         if(maxRows != null) {
            // A negative limit is a mistake, not a way to say "unlimited": the Composer's own
            // control refuses it outright (FormValidators.positiveIntegerInRange,
            // form-validators.ts:394-400, which admits 0..Integer.MAX_VALUE), while this path
            // silently folded it into -1 and reported success -- so a caller that sent -100
            // meaning "a hundred rows, roughly" got no limit at all and no indication of it.
            if(maxRows < 0) {
               throw new PairingException(
                  "maxRows cannot be negative (got " + maxRows + "). Pass a positive row limit, " +
                  "or 0 for no limit.");
            }

            t.setMaxRows(maxRows <= 0 ? -1 : maxRows);
         }

         if(distinct != null) {
            t.setDistinct(distinct);
         }

         if(mergeable != null) {
            t.setSQLMergeable(mergeable);
         }

         if(visibleInViewsheet != null) {
            t.setVisibleTable(visibleInViewsheet);
         }

         // Embedded row count. The stored table carries a header row that the dialog's number does
         // not count, which is why TablePropertyDialogService:113-121 compares against
         // getRowCount() - 1 and writes back count + 1; getting that offset wrong silently adds or
         // drops a row. Skipped when the value already matches, matching the dialog, so a
         // set_table_properties call that only changes the description does not rewrite the data.
         if(rowCount != null && t instanceof EmbeddedTableAssembly etable) {
            if(rowCount < 0) {
               throw new PairingException(
                  "rowCount cannot be negative (got " + rowCount + ").");
            }

            XEmbeddedTable data = etable.getEmbeddedData();

            if(data != null) {
               int existing = Math.max(0, data.getRowCount() - 1);

               if(rowCount != existing) {
                  data.setRowCount(rowCount + 1);
                  etable.setEmbeddedData(data);
               }
            }
         }
      }

      // -----------------------------------------------------------------------
      // Cross join
      // -----------------------------------------------------------------------

      /**
       * Creates a cross join (no key columns) between two tables.
       *
       * @param name      the name for the new join assembly
       * @param leftTable the left source table name
       * @param rightTable the right source table name
       * @throws PairingException if either source assembly is not found
       */
      public void addCrossJoin(String name, String leftTable, String rightTable)
         throws PairingException, SecurityException
      {
         if(name == null || name.isBlank()) {
            throw new PairingException("Cross join requires a name.");
         }

         requirePermission(ResourceType.CROSS_JOIN);
         TableAssembly left  = requireTable(leftTable);
         TableAssembly right = requireTable(rightTable);

         TableAssemblyOperator top = new TableAssemblyOperator();
         TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
         op.setOperation(TableAssemblyOperator.CROSS_JOIN);
         top.addOperator(op);

         RelationalJoinTableAssembly join =
            new RelationalJoinTableAssembly(ws, name,
                                            new TableAssembly[]{ left, right },
                                            new TableAssemblyOperator[]{ top });
         placeAssembly(join);
      }

      // -----------------------------------------------------------------------
      // Merge join
      // -----------------------------------------------------------------------

      /**
       * Creates a merge join from two or more tables.
       *
       * @param name       the name for the new merge join assembly
       * @param tableNames the source table names (at least two)
       * @throws PairingException if fewer than two tables are given or a source is not found
       */
      public void addMergeJoin(String name, String[] tableNames) throws PairingException {
         if(name == null || name.isBlank()) {
            throw new PairingException("Merge join requires a name.");
         }

         if(tableNames == null || tableNames.length < 2) {
            throw new PairingException("Merge join requires at least 2 tables.");
         }

         TableAssembly[] tables = new TableAssembly[tableNames.length];

         for(int i = 0; i < tableNames.length; i++) {
            tables[i] = requireTable(tableNames[i]);
         }

         TableAssemblyOperator[] operators = new TableAssemblyOperator[tableNames.length - 1];

         for(int i = 0; i < operators.length; i++) {
            TableAssemblyOperator top = new TableAssemblyOperator();
            TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
            op.setOperation(TableAssemblyOperator.MERGE_JOIN);
            top.addOperator(op);
            operators[i] = top;
         }

         MergeJoinTableAssembly mergeJoin =
            new MergeJoinTableAssembly(ws, name, tables, operators);
         placeAssembly(mergeJoin);
      }

      // -----------------------------------------------------------------------
      // Column reordering
      // -----------------------------------------------------------------------

      /**
       * Reorders the columns in a table's public column selection.
       *
       * @param table       the assembly name
       * @param columnOrder ordered list of column names defining the new order.
       *                    Columns not mentioned are appended at the end.
       * @throws PairingException if the table is not found, or if the table is a crosstab
       *                    (column order there is controlled by the row/column groups, not
       *                    the column selection — this mirrors the Composer UI, which disables
       *                    "Reorder Table Columns" for crosstab tables)
       */
      public void reorderColumns(String table, List<String> columnOrder)
         throws PairingException
      {
         TableAssembly t = requireTable(table);
         AggregateInfo aggInfo = t.getAggregateInfo();

         if(aggInfo != null && aggInfo.isCrosstab()) {
            throw new PairingException("Cannot reorder columns on a crosstab table: " + table);
         }

         ColumnSelection cs = t.getColumnSelection(false);

         // Bare attribute names that occur more than once (e.g. "ID" from both a
         // "Customers" and an "Orders" entity in a join) are ambiguous — keying the
         // lookup map on the bare name for those would collide and silently drop one
         // of the columns. Only use the bare name when it is unique; ambiguous ones
         // fall back to the entity-qualified DataRef.getName().
         java.util.Map<String, Integer> attributeNameCounts = new java.util.HashMap<>();

         for(int i = 0; i < cs.getAttributeCount(); i++) {
            DataRef ref = cs.getAttribute(i);

            if(ref instanceof ColumnRef cr && (cr.getAlias() == null || cr.getAlias().isEmpty())) {
               attributeNameCounts.merge(cr.getAttribute(), 1, Integer::sum);
            }
         }

         // Build a map of name → DataRef for fast lookup
         java.util.LinkedHashMap<String, DataRef> byName = new java.util.LinkedHashMap<>();

         for(int i = 0; i < cs.getAttributeCount(); i++) {
            DataRef ref = cs.getAttribute(i);
            String name;

            // Match on the bare attribute name (what the caller and the UI both use),
            // not DataRef.getName(), which is entity-qualified (e.g. "All Sales.Company")
            // for columns whose entity is non-blank, such as unpivot header columns.
            if(ref instanceof ColumnRef cr) {
               if(cr.getAlias() != null && !cr.getAlias().isEmpty()) {
                  name = cr.getAlias();
               }
               else if(attributeNameCounts.get(cr.getAttribute()) > 1) {
                  name = ref.getName();
               }
               else {
                  name = cr.getAttribute();
               }
            }
            else {
               name = ref.getName();
            }

            byName.put(name, ref);
         }

         ColumnSelection newCs = new ColumnSelection();

         // Add columns in the specified order
         for(String name : columnOrder) {
            DataRef ref = byName.remove(name);

            if(ref != null) {
               newCs.addAttribute(ref);
            }
         }

         // Append any remaining columns not in the specified order
         for(DataRef ref : byName.values()) {
            newCs.addAttribute(ref);
         }

         t.setColumnSelection(newCs, false);
      }

      // -----------------------------------------------------------------------
      // Concatenation sub-table management
      // -----------------------------------------------------------------------

      /**
       * Adds a table to an existing concatenation assembly.
       *
       * @param concatName the concatenation assembly name
       * @param tableName  the table to add
       * @throws PairingException if the concat or source table is not found
       */
      public void addConcatSubtable(String concatName, String tableName)
         throws PairingException
      {
         Assembly a = ws.getAssembly(concatName);

         if(!(a instanceof ConcatenatedTableAssembly ctbl)) {
            throw new PairingException("Concatenation not found: " + concatName);
         }

         TableAssembly newTable = requireTable(tableName);
         TableAssembly[] existing = ctbl.getTableAssemblies();

         // Refuse the concatenation as a source of itself. This is a special case neither check
         // below catches: ctbl is never a member of `existing` (it IS the concatenation, not one
         // of its subtables), so the duplicate-source loop can't see it, and
         // checkCyclicalDependency(ws, ctbl, newTable) can't either when newTable == ctbl --
         // AssetUtil#getDependedAssemblies0 seeds its visited set with the root and passes
         // included=false for it, so asking whether ctbl (as `otherAssembly`) already depends on
         // itself (as `targetAssembly`) finds nothing, because before this attach nothing yet
         // links ctbl back to itself.
         if(tableName.equals(concatName)) {
            throw new PairingException(
               "\"" + concatName + "\" cannot be a source of itself.");
         }

         // Refuse a source that is already present. ConcatenateTablesService#checkValidity refuses
         // it with common.table.unionDuplicate; this path never ported the check, so a repeated
         // source was accepted and counted its rows twice in the UNION with nothing reporting it.
         for(TableAssembly sub : existing) {
            if(tableName.equals(sub.getName())) {
               throw new PairingException(
                  "\"" + tableName + "\" is already a source of \"" + concatName +
                  "\". Concatenating a table with itself counts its rows twice; omit it, or add " +
                  "a mirror of it with add_mirror if two independent copies are intended.");
            }
         }

         // Refuse a cycle BEFORE mutating, and before the column-count check below.
         // checkCyclicalDependency asks whether newTable already depends on this concatenation --
         // reachable whenever newTable mirrors it, directly or through a chain -- and it can answer
         // before the attach, because the dependency that would close the loop is newTable's own
         // and already exists. The Composer calls this identical helper at
         // ConcatenateTablesService:445, alongside its duplicate check and separately from its
         // column compatibility check.
         //
         // Ordered ahead of the column-count check deliberately: that check reads
         // newTable.getColumnSelection(true), and resolving the column selection of an assembly
         // that mirrors this concatenation walks back into the concatenation itself. Rejecting the
         // structural error first keeps that read off a graph that is about to become cyclic, and
         // a cycle is not something the caller can fix by adjusting columns anyway.
         //
         // It has to run here rather than be left to the downstream safety net, for two separate
         // reasons. Worksheet#checkDependencies is reached from
         // AssetQuerySandbox#refreshColumnSelection only after apply() has already committed the
         // mutation and taken an undo checkpoint of it -- so by then the cycle is in the asset and
         // the caller gets a 500 for a change that stuck. And AbstractWSAssembly#checkDependency
         // cannot see a two-node cycle at all: AssetUtil#getDependedAssemblies0 seeds its visited
         // set with the root and passes included=false for it, so a loop back to the root is
         // discarded as already-visited and never reaches the array it matches against.
         if(WorksheetEventUtil.checkCyclicalDependency(ws, ctbl, newTable)) {
            throw new PairingException(
               "Adding \"" + tableName + "\" to \"" + concatName + "\" would create a circular " +
               "dependency: \"" + tableName + "\" already depends on \"" + concatName +
               "\". Concatenate the assemblies \"" + tableName + "\" is built from instead.");
         }

         // Validate column count matches existing subtables.
         int colCount = existing[0].getColumnSelection(true).getAttributeCount();
         int newCount = newTable.getColumnSelection(true).getAttributeCount();

         if(newCount != colCount) {
            throw new PairingException(
               "Data blocks that do not have the same number of columns cannot be " +
               "concatenated. Existing subtables have " + colCount +
               " columns but \"" + tableName + "\" has " + newCount + ".");
         }

         TableAssembly[] updated = new TableAssembly[existing.length + 1];
         System.arraycopy(existing, 0, updated, 0, existing.length);
         updated[existing.length] = newTable;

         // Preserve existing operators and add one for the new pair.
         // Copy the last-pair operator (between existing[n-2] and existing[n-1]) so that
         // the new table inherits the same UNION/INTERSECT/MINUS as the most recent pair
         // rather than always defaulting to whatever operator 0 happens to be.
         TableAssemblyOperator op = ctbl.getOperator(existing.length - 2);
         ctbl.setTableAssemblies(updated);

         // Set operator between last existing and new table
         ctbl.setOperator(existing[existing.length - 1].getName(),
                          tableName, op);
      }

      /**
       * Removes a table from an existing concatenation assembly.
       * If fewer than 2 tables remain, the concatenation is deleted.
       *
       * @param concatName the concatenation assembly name
       * @param tableName  the table to remove
       * @throws PairingException if the concat is not found
       */
      public void removeConcatSubtable(String concatName, String tableName)
         throws PairingException
      {
         Assembly a = ws.getAssembly(concatName);

         if(!(a instanceof ConcatenatedTableAssembly ctbl)) {
            throw new PairingException("Concatenation not found: " + concatName);
         }

         boolean invalid = ctbl.removeTable(tableName);

         if(invalid) {
            ws.removeAssembly(concatName);
         }
      }

      // -----------------------------------------------------------------------
      // Named group assembly
      // -----------------------------------------------------------------------

      /**
       * Creates a {@link DefaultNamedGroupAssembly} with simple value-list mappings. When
       * {@code table} and {@code column} are both provided, the grouping is attached to that
       * column. When both are omitted, a standalone grouping is created instead, matched at
       * runtime by data type rather than by column — mirroring the "Type" option in the
       * Composer's Grouping Properties dialog.
       *
       * @param name       the assembly name
       * @param table      the table containing the column to group, or {@code null} for a
       *                   standalone grouping
       * @param column     the column to attach the grouping to, or {@code null} for a
       *                   standalone grouping
       * @param type       output data type for a standalone grouping (defaults to
       *                   {@link XSchema#STRING} if not specified); ignored when
       *                   {@code table}/{@code column} are provided
       * @param mappings   group name → value list mappings
       * @param groupOthers whether to group unmapped values as "Others"
       * @throws PairingException if the table or column is not found, if only one of
       *                          {@code table}/{@code column} is provided, or if
       *                          {@code type} is not a recognized primitive type
       */
      public void addNamedGroup(String name, String table, String column, String type,
                                List<WorksheetMutationSupport.GroupMapping> mappings,
                                boolean groupOthers) throws PairingException
      {
         if(name == null || name.isBlank()) {
            throw new PairingException("Named group requires a name.");
         }

         if((table == null) != (column == null)) {
            throw new PairingException(
               "table and column must both be specified, or both omitted for a standalone grouping");
         }

         if(type != null && !XSchema.isPrimitiveType(type)) {
            throw new PairingException(
               "Invalid type: \"" + type + "\". Valid types: " +
               "string, boolean, float, double, integer, long, short, byte, " +
               "char, date, time, timeInstant.");
         }

         DataRef ref = null;

         if(table != null) {
            TableAssembly t = requireTable(table);
            ColumnSelection cs = t.getColumnSelection(false);
            ref = cs.getAttribute(column);

            if(ref == null) {
               throw new PairingException("Column not found: " + column);
            }
         }

         String conditionType = ref != null ? ref.getDataType() : type != null ? type : XSchema.STRING;
         DataRef conditionRef = namedGroupConditionRef(ref, conditionType);

         NamedGroupInfo ngi = new NamedGroupInfo();
         ngi.setOthers(groupOthers
            ? XConstants.GROUP_OTHERS
            : XConstants.LEAVE_OTHERS);

         if(mappings != null) {
            for(WorksheetMutationSupport.GroupMapping m : mappings) {
               ngi.setGroupCondition(m.name(),
                  WorksheetMutationSupport.buildGroupConditionList(conditionType, conditionRef, m, ws));
            }
         }

         DefaultNamedGroupAssembly assembly = new DefaultNamedGroupAssembly(ws, name);
         assembly.setNamedGroupInfo(ngi);

         if(ref != null) {
            assembly.setAttachedType(AttachedAssembly.COLUMN_ATTACHED);
            assembly.setAttachedSource(new SourceInfo(SourceInfo.ASSET, null, table));
            assembly.setAttachedAttribute(ref);
         }
         else {
            assembly.setAttachedType(AttachedAssembly.DATA_TYPE_ATTACHED);
            assembly.setAttachedDataType(conditionType);
         }

         placeAssembly(assembly);
      }

      // -----------------------------------------------------------------------
      // Column description
      // -----------------------------------------------------------------------

      /**
       * Sets the description on a column.
       *
       * @param table       the assembly name
       * @param col         the column attribute name
       * @param description the free-text description
       * @throws PairingException if the table or column is not found
       */
      public void setColumnDescription(String table, String col, String description)
         throws PairingException
      {
         TableAssembly t = requireTable(table);
         ColumnSelection cs = t.getColumnSelection(false);
         DataRef ref = cs.getAttribute(col);

         if(!(ref instanceof ColumnRef cr)) {
            throw new PairingException("Column not found: " + col);
         }

         cr.setDescription(description);
      }

      // -----------------------------------------------------------------------
      // Mirror auto-update
      // -----------------------------------------------------------------------

      /**
       * Toggles the auto-update flag on a mirror table assembly.
       *
       * <p>Only an <em>outer</em> mirror — one of an asset in another worksheet — actually carries
       * the flag: {@code MirrorAssemblyImpl.setAutoUpdate} returns early for anything else, and
       * {@code isAutoUpdate} then answers {@code true} regardless. Since
       * {@link #addMirror(String, String)} builds inner mirrors, that is the normal case for an
       * agent, and accepting a write that is silently discarded would leave the caller believing a
       * setting took when it never did — so the write is verified and refused instead.</p>
       *
       * @param table      the mirror assembly name
       * @param autoUpdate {@code true} to enable auto-update, {@code false} to disable
       * @throws PairingException if the assembly is not a mirror table, or is a mirror that cannot
       *                          carry the flag
       */
      public void setMirrorAutoUpdate(String table, boolean autoUpdate)
         throws PairingException
      {
         Assembly a = ws.getAssembly(table);

         if(!(a instanceof MirrorTableAssembly mirror)) {
            throw new PairingException("Not a mirror table: " + table);
         }

         mirror.setAutoUpdate(autoUpdate);

         if(mirror.isAutoUpdate() != autoUpdate) {
            throw new PairingException(
               "autoUpdate cannot be set to " + autoUpdate + " on \"" + table + "\": its source " +
               "is in this worksheet, and such a mirror always tracks its source. Only a mirror " +
               "of an asset in another worksheet carries the flag.");
         }
      }

      // -----------------------------------------------------------------------
      // Assembly positioning
      // -----------------------------------------------------------------------

      /**
       * Sets the pixel position of an assembly on the canvas.
       *
       * @param table the assembly name
       * @param x     pixel X coordinate
       * @param y     pixel Y coordinate
       * @throws PairingException if the assembly is not found
       */
      public void setAssemblyPosition(String table, int x, int y)
         throws PairingException
      {
         Assembly a = ws.getAssembly(table);

         if(!(a instanceof AbstractWSAssembly wsa)) {
            throw new PairingException("Assembly not found: " + table);
         }

         wsa.setPixelOffset(new java.awt.Point(Math.max(0, x), Math.max(0, y)));
      }

      // -----------------------------------------------------------------------
      // Duplicate assembly
      // -----------------------------------------------------------------------

      /**
       * Duplicates an assembly with a new name.
       *
       * @param sourceName the assembly to clone
       * @param newName    the name for the clone
       * @throws PairingException if the source assembly is not found
       */
      public void duplicateAssembly(String sourceName, String newName)
         throws PairingException
      {
         Assembly a = ws.getAssembly(sourceName);

         if(!(a instanceof WSAssembly wsa)) {
            throw new PairingException("Assembly not found: " + sourceName);
         }

         if(ws.getAssembly(newName) != null) {
            throw new PairingException("Assembly already exists: " + newName);
         }

         requireStorableName(newName, "An assembly name");

         try {
            WSAssembly clone = (WSAssembly) wsa.clone();
            clone.getWSAssemblyInfo().setName(newName);

            // Offset the clone position slightly so it doesn't overlap
            java.awt.Point pos = clone.getPixelOffset();

            if(pos != null) {
               clone.setPixelOffset(new java.awt.Point(pos.x + 40, pos.y + 40));
            }

            ws.addAssembly(clone);
         }
         catch(Exception e) {
            throw new PairingException("Cannot duplicate assembly: " + sourceName, e);
         }
      }

      // -----------------------------------------------------------------------
      // Primary assembly
      // -----------------------------------------------------------------------

      /**
       * Sets a table as the worksheet's primary assembly.
       *
       * @param table the assembly name to set as primary
       * @throws PairingException if the assembly is not found
       */
      public void setPrimaryAssembly(String table) throws PairingException {
         Assembly a = ws.getAssembly(table);

         if(a == null) {
            throw new PairingException("Assembly not found: " + table);
         }

         if(!ws.setPrimaryAssembly(table)) {
            throw new PairingException("Failed to set primary assembly: " + table);
         }

         // Making a table primary also exposes it to viewsheets, as WSPrimaryService:84 does.
         // visibleTable defaults to true, so this only matters for a table whose flag was cleared
         // earlier -- but that table would otherwise stay absent from the viewsheet binding tree
         // (VSBindingService:2006, BaseTreeModelBuilder:143, VSOutputService:239) while being the
         // sheet's primary, which is the one assembly a viewsheet binding is most likely to want.
         if(a instanceof TableAssembly ta) {
            ((TableAssemblyInfo) ta.getTableInfo()).setVisibleTable(true);
         }
      }

      // -----------------------------------------------------------------------
      // Edit variable
      // -----------------------------------------------------------------------

      /**
       * Edits an existing variable assembly's properties.
       *
       * @param name         the variable assembly name
       * @param type         new data type, or {@code null} to leave unchanged
       * @param label        new display label, or {@code null} to leave unchanged
       * @param defaultValue new default value, or {@code null} to leave unchanged
       * @param choices      the variable's enumerated "Values" picker (embedded list or query
       *                     source), or {@code null} to leave it unchanged
       * @throws PairingException if the assembly is not found or not a variable
       */
      public void editVariable(String name, String type, String label, String defaultValue,
                               WorksheetMutationSupport.VariableChoicesSpec choices)
         throws PairingException
      {
         Assembly a = ws.getAssembly(name);

         if(!(a instanceof DefaultVariableAssembly va)) {
            throw new PairingException("Variable assembly not found: " + name);
         }

         AssetVariable existing = va.getVariable();

         if(existing == null) {
            throw new PairingException("Variable has no definition: " + name);
         }

         // Every edit below is applied to a scratch copy, published only at the very end.
         // applyOnRuntime mutates the live worksheet with no rollback on a thrown exception, so
         // editing 'existing' in place would let an invalid 'choices' (mismatched labels/values,
         // an unknown displayStyle, a circular query source, ...) discovered partway through
         // leave the already-applied label/type/defaultValue changes committed even though the
         // whole call is reported as a failure.
         AssetVariable var = (AssetVariable) existing.clone();

         if(label != null) {
            var.setAlias(label);
         }

         if(type != null) {
            // L2-Group7: XSchema.createPrimitiveType returns null, with no exception and no
            // log, for a type string the Composer's own (closed, 12-value) Type dropdown could
            // never submit. Left unchecked, the variable silently ends up with no type node.
            var.setTypeNode(requireValidType(type));
         }

         if(defaultValue != null) {
            // Use the variable's type to create the correct value node subclass
            // (e.g. IntegerValue for "integer") so the default value survives
            // serialization round-trips.
            String effectiveType = type != null
               ? type : (var.getTypeNode() != null ? var.getTypeNode().getType() : null);
            inetsoft.uql.schema.XValueNode valueNode =
               inetsoft.uql.schema.XValueNode.createValueNode(name, effectiveType);

            if(valueNode != null) {
               try {
                  valueNode.parse0(defaultValue);
               }
               catch(Exception e) {
                  // L2-Group7: previously fell back to storing the raw, unparsed string
                  // instead of failing loud -- the variable ended up with a default value
                  // inconsistent with its own declared type, silently.
                  throw new PairingException(
                     "Default value '" + defaultValue + "' is not a valid " +
                     (effectiveType != null ? effectiveType : "string") +
                     " value for variable '" + name + "'.");
               }

               var.setValueNode(valueNode);
            }
         }

         WorksheetMutationSupport.applyVariableChoices(ws, var, choices);

         va.setVariable(var);
      }

      /**
       * Renames a variable assembly in the worksheet.  Dependent references
       * (e.g. {@code $(name)} usages in conditions/expressions) are updated
       * automatically by {@link Worksheet#renameAssembly}.
       *
       * @param oldName the current variable assembly name
       * @param newName the desired new name
       * @throws PairingException if no variable assembly with {@code oldName} exists or
       *                          the rename fails
       */
      public void renameVariable(String oldName, String newName) throws PairingException {
         Assembly a = ws.getAssembly(oldName);

         if(!(a instanceof DefaultVariableAssembly)) {
            throw new PairingException("Variable assembly not found: " + oldName);
         }

         requireStorableName(newName, "A variable name");

         if(!ws.renameAssembly(oldName, newName, true)) {
            throw new PairingException(
               "Failed to rename variable '" + oldName + "' to '" + newName +
               "' — the name may already be in use.");
         }
      }

      /**
       * Removes a variable assembly from the worksheet.
       *
       * @param name the variable assembly name to delete
       * @throws PairingException if no variable assembly with {@code name} exists
       */
      public void deleteVariable(String name) throws PairingException {
         Assembly a = ws.getAssembly(name);

         if(!(a instanceof DefaultVariableAssembly)) {
            throw new PairingException("Variable assembly not found: " + name);
         }

         // L2-Group7: mirrors WSRemoveAssembliesService.removeAssemblies, which refuses to
         // delete an assembly AssetEventUtil.hasDependent() reports as still referenced (e.g.
         // a condition using $(name)) instead of deleting it and leaving a dangling reference
         // behind. That guard lives entirely in the UI's own delete service today --
         // Worksheet.removeAssembly(String) itself has no such check -- so this agent path
         // never saw it.
         if(AssetEventUtil.hasDependent(a, ws, java.util.Collections.singleton(name))) {
            throw new PairingException(
               "Variable '" + name + "' is still referenced by another assembly in this " +
               "worksheet (e.g. a condition using $(" + name + ")). Remove those references " +
               "before deleting it.");
         }

         ws.removeAssembly(name);
      }

      // -----------------------------------------------------------------------
      // Edit named group
      // -----------------------------------------------------------------------

      /**
       * Replaces the group mappings on an existing named group assembly.
       *
       * @param name        the named group assembly name
       * @param mappings    new group name → value list mappings
       * @param groupOthers whether to group unmapped values as "Others"
       * @throws PairingException if the assembly is not found
       */
      public void editNamedGroup(String name,
                                 List<WorksheetMutationSupport.GroupMapping> mappings,
                                 boolean groupOthers) throws PairingException
      {
         Assembly a = ws.getAssembly(name);

         if(!(a instanceof DefaultNamedGroupAssembly nga)) {
            throw new PairingException("Named group assembly not found: " + name);
         }

         NamedGroupInfo ngi = new NamedGroupInfo();
         ngi.setOthers(groupOthers
            ? XConstants.GROUP_OTHERS
            : XConstants.LEAVE_OTHERS);

         if(mappings != null) {
            DataRef ref = nga.getAttachedAttribute();
            String conditionType = ref != null ? ref.getDataType() : XSchema.STRING;
            DataRef conditionRef = namedGroupConditionRef(ref, conditionType);

            for(WorksheetMutationSupport.GroupMapping m : mappings) {
               ngi.setGroupCondition(m.name(),
                  WorksheetMutationSupport.buildGroupConditionList(conditionType, conditionRef, m, ws));
            }
         }

         nga.setNamedGroupInfo(ngi);
      }

      // -----------------------------------------------------------------------
      // Set table mode
      // -----------------------------------------------------------------------

      /**
       * Sets the live/metadata/detail/full/edit mode flags on a table assembly.
       *
       * <p>Supported modes:
       * <ul>
       *   <li>{@code "live"} — enables live data preview ({@code liveData=true})</li>
       *   <li>{@code "default"} — metadata view (live for embedded, metadata for bound)</li>
       *   <li>{@code "full"} — all-data view with aggregation disabled</li>
       *   <li>{@code "detail"} — live detail view with aggregation disabled</li>
       *   <li>{@code "edit"} — edit mode (liveData=false, editMode=true)</li>
       * </ul>
       */
      public void setTableMode(String tableName, String mode) throws PairingException {
         Assembly a = ws.getAssembly(tableName);

         if(!(a instanceof TableAssembly table)) {
            throw new PairingException("Table assembly not found: " + tableName);
         }

         switch(mode) {
            case "live" -> {
               table.setLiveData(true);
               table.setRuntime(table.isRuntimeSelected());
               table.setEditMode(false);
               // The aggregate flag follows whether the table actually groups, not which display
               // mode is selected: TableModeService#setLiveTableMode:353 and
               // #setDefaultTableMode:334 both compute it this way, while full/detail/edit force
               // it false (:133, :166, :209) -- which the branches below already match. Leaving it
               // untouched here let a table switched to "full" earlier keep aggregate=false after
               // coming back to "live", and isAggregate() is real query state, not presentation:
               // it selects the public versus private column selection and forms part of the lens
               // cache key (AssetQuerySandbox:900,1010), drives JoinQuery:261's detailView, and is
               // persisted with the assembly.
               ((TableAssemblyInfo) table.getTableInfo()).setAggregate(
                  table.getAggregateInfo() != null && !table.getAggregateInfo().isEmpty());
            }
            case "full" -> {
               table.setLiveData(false);
               table.setRuntime(false);
               table.setEditMode(false);
               ((TableAssemblyInfo) table.getTableInfo()).setAggregate(false);
            }
            case "detail" -> {
               table.setLiveData(true);
               table.setRuntime(false);
               table.setEditMode(false);
               ((TableAssemblyInfo) table.getTableInfo()).setAggregate(false);
            }
            case "edit" -> {
               table.setLiveData(false);
               table.setRuntime(false);
               table.setEditMode(true);
               ((TableAssemblyInfo) table.getTableInfo()).setAggregate(false);
            }
            default -> {
               // "default" — metadata view for bound tables, live for embedded.
               table.setLiveData(table instanceof EmbeddedTableAssembly);
               table.setRuntime(false);
               table.setEditMode(false);
               // Same rule as the "live" branch above, and the same source:
               // TableModeService#setDefaultTableMode:334.
               ((TableAssemblyInfo) table.getTableInfo()).setAggregate(
                  table.getAggregateInfo() != null && !table.getAggregateInfo().isEmpty());
            }
         }
      }

      // -----------------------------------------------------------------------
      // Edit unpivot header columns
      // -----------------------------------------------------------------------

      /**
       * Changes the header column count on an existing unpivot table assembly.
       *
       * @param tableName     the UnpivotTableAssembly name
       * @param headerColumns new number of header columns (≥ 1)
       * @throws PairingException if the assembly is not an UnpivotTableAssembly
       */
      public void editUnpivot(String tableName, int headerColumns) throws PairingException {
         Assembly a = ws.getAssembly(tableName);

         if(!(a instanceof UnpivotTableAssembly table)) {
            throw new PairingException("Not an unpivot table assembly: " + tableName);
         }

         if(table.getHeaderColumns() == headerColumns) {
            return; // no-op
         }

         table.setHeaderColumns(headerColumns);
      }

      // -----------------------------------------------------------------------
      // Helper
      // -----------------------------------------------------------------------

      static int parseConcatType(String opType) {
         if(opType == null) {
            return TableAssemblyOperator.UNION;
         }

         return switch(opType.toUpperCase()) {
            case "INTERSECT" -> TableAssemblyOperator.INTERSECT;
            case "MINUS"     -> TableAssemblyOperator.MINUS;
            default          -> TableAssemblyOperator.UNION;
         };
      }

      static int parseJoinType(String joinType) {
         if(joinType == null) {
            return TableAssemblyOperator.INNER_JOIN;
         }

         return switch(joinType.toUpperCase()) {
            case "LEFT"  -> TableAssemblyOperator.LEFT_JOIN;
            case "RIGHT" -> TableAssemblyOperator.RIGHT_JOIN;
            case "FULL"  -> TableAssemblyOperator.FULL_JOIN;
            case "CROSS" -> TableAssemblyOperator.CROSS_JOIN;
            case "MERGE" -> TableAssemblyOperator.MERGE_JOIN;
            default      -> TableAssemblyOperator.INNER_JOIN;
         };
      }

      /**
       * Places {@code assembly} in an unoccupied area of the canvas and adds it to
       * the worksheet. Uses the same strategy as the UI's
       * {@link AssetEventUtil#adjustAssemblyPosition}: sets a starting position then
       * shifts the assembly below the lowest existing assembly.
       */
      private void placeAssembly(WSAssembly assembly) throws PairingException {
         requireStorableName(assembly.getName(), "An assembly name");
         assembly.setPixelOffset(new Point(25, 25));
         AssetEventUtil.adjustAssemblyPosition(assembly, ws);
         ws.addAssembly(assembly);
      }

      /**
       * Refuses an assembly name that cannot survive being written to storage.
       *
       * <p>{@code AssemblyInfo:254} interpolates the name straight into a CDATA section --
       * {@code writer.print("<name><![CDATA[" + name + "]]></name>")} -- with no escaping. A name
       * containing the literal CDATA terminator closes that section early and leaves malformed XML
       * in indexed storage. The Composer cannot produce one:
       * {@code FormValidators.nameSpecialCharacters} (form-validators.ts:635-648) refuses it,
       * among much else, before the dialog can submit. This path has no such front end, and the
       * save path validates no assembly name at all -- traced from the agent's save endpoint
       * through {@code AbstractAssetEngine.setSheet} to {@code storage.putXMLSerializable}, where
       * the only checks are permissions and a name-blind {@code checkValidity}.
       *
       * <p>Only the terminator is refused, not the Composer's whole whitelist. That whitelist also
       * rejects '/' and '"', but those are legal CDATA content and round-trip intact -- confirmed
       * live -- so enforcing the full rule here would break names that already exist and work
       * while protecting nothing.
       *
       * <p>Package-private (not {@code private}) so {@link WorksheetAgentController} can reuse
       * the same check for the assembly-creation paths it builds directly against
       * {@code Worksheet}/{@code RuntimeWorksheet} rather than through this {@code Editor} --
       * {@code createVariable} and {@code addDatasourceScopedNamedGroup} -- instead of
       * duplicating the rule.
       */
      static void requireStorableName(String name, String what) throws PairingException {
         if(name != null && name.contains("]]>")) {
            throw new PairingException(
               what + " cannot contain \"]]>\". The name is written into a CDATA section " +
               "verbatim, so that sequence closes the section early and leaves malformed XML in " +
               "storage, which no later edit can repair. Choose a name without it.");
         }
      }

      private TableAssembly requireTable(String name) throws PairingException {
         Assembly a = ws.getAssembly(name);

         if(!(a instanceof TableAssembly t)) {
            throw new PairingException("Table not found in worksheet: " + name);
         }

         return t;
      }

      /**
       * L2 repair-review Finding D: this used to be its own, narrower check
       * ({@code cs.getAttribute(column) == null}) than {@link #requireColumn(TableAssembly,
       * String, boolean)} below -- {@link WorksheetMutationSupport#fieldExists} additionally
       * matches a column by its {@code ColumnRef.getAlias()} even when that alias is not the
       * ref's current effective name ({@code ColumnRef.getName()}, which only returns the alias
       * when {@code aalias} is also set) -- a real, if narrow, state a column can be in. The two
       * checks were meant to be the same check (the commit that added the 3-arg overload
       * describes it as "the same check add_filter already had"); delegating closes the drift
       * instead of maintaining two implementations that can diverge further.
       */
      private void requireColumn(TableAssembly t, String column) throws PairingException {
         requireColumn(t, column, false);
      }

      /**
       * L2-Group7: {@code XSchema.createPrimitiveType(type)} returns {@code null}, with no
       * exception and no log, for a type string the Composer's own (closed, 12-value) Type
       * dropdown could never submit -- {@code editVariable} previously stored that {@code null}
       * type node without complaint.
       */
      private inetsoft.uql.schema.XTypeNode requireValidType(String type) throws PairingException {
         inetsoft.uql.schema.XTypeNode typeNode = XSchema.createPrimitiveType(type);

         if(typeNode == null) {
            throw new PairingException(
               "Unrecognized variable type: '" + type + "'. Valid types are: string, " +
               "integer, double, float, character, byte, short, long, date, time, " +
               "timeInstant, boolean.");
         }

         return typeNode;
      }

      /**
       * Same guard as {@link #requireColumn(TableAssembly, String)}, but also matching
       * {@link inetsoft.uql.erm.AggregateInfo} aggregate/group refs when {@code post} is
       * {@code true} -- the field-existence check {@code add_filter} already had via the
       * plain overload above, extended to post-aggregate (HAVING) conditions and ranking,
       * which resolve against a different, wider set of names.
       */
      private void requireColumn(TableAssembly t, String column, boolean post)
         throws PairingException
      {
         if(!WorksheetMutationSupport.fieldExists(t, column, post)) {
            throw new PairingException("Column not found: " + column);
         }
      }

      /**
       * Validates every condition node's field against the table before any of them are
       * applied, matching the native condition dialog's closed field picker: a human cannot
       * submit an unresolvable column, so neither should this path silently fall back to
       * {@code resolveField}'s {@code new AttributeRef(null, field)} placeholder.
       *
       * <p>Also validates each FIELD-typed {@link WorksheetMutationSupport.ConditionValueSpec}
       * in {@code node.condition().valueSpecs()} -- a column-vs-column comparison condition's
       * value side goes through the exact same {@code resolveField} fallback (see
       * {@code WorksheetMutationSupport#conditionValue(TableAssembly, boolean,
       * ConditionValueSpec)}'s FIELD branch) and would otherwise silently accept a typo'd column
       * name there instead of being rejected like every other field reference this method
       * covers.
       */
      private void requireConditionFields(TableAssembly t,
                                          List<WorksheetMutationSupport.ConditionNode> nodes,
                                          boolean post)
         throws PairingException
      {
         if(nodes == null) {
            return;
         }

         for(WorksheetMutationSupport.ConditionNode node : nodes) {
            if(node.condition() != null) {
               requireColumn(t, node.condition().field(), post);

               if(node.condition().valueSpecs() != null) {
                  for(WorksheetMutationSupport.ConditionValueSpec vs : node.condition().valueSpecs()) {
                     // A null/blank field here is a distinct input error ("needs a non-blank
                     // field") that WorksheetMutationSupport#conditionValue already reports
                     // clearly when the condition is applied -- leave that message alone and
                     // only guard against a *named but unresolvable* field, the silent
                     // placeholder-ref failure mode this check exists for.
                     if(vs != null && "field".equalsIgnoreCase(vs.valueType()) &&
                        vs.field() != null && !vs.field().isBlank())
                     {
                        requireColumn(t, vs.field(), post);
                     }
                  }
               }
            }
         }
      }

      /**
       * Validates a ranking condition's field, matching {@link #requireColumn(TableAssembly,
       * String, boolean)} but using ranking's own resolution order (see
       * {@link WorksheetMutationSupport#rankingFieldExists}).
       */
      private void requireRankingField(TableAssembly t, String field) throws PairingException {
         if(!WorksheetMutationSupport.rankingFieldExists(t, field)) {
            throw new PairingException("Column not found: " + field);
         }
      }

      /**
       * Returns the {@link DataRef} to use inside a named group's {@link ConditionItem}s.
       * {@code ConditionItem} requires a non-null attribute — a null one NPEs in
       * {@code ConditionItem.toString()}, which runs whenever the worksheet is cloned (e.g. on
       * touch/save). When the group isn't attached to a real column, the Composer's own Grouping
       * Properties dialog ({@code grouping-condition-dialog.component.ts}) substitutes a "this"
       * placeholder {@link BaseField} instead of leaving the ref null; this mirrors that.
       */
      private DataRef namedGroupConditionRef(DataRef ref, String dataType) {
         if(ref != null) {
            return ref;
         }

         BaseField thisField = new BaseField("this");
         thisField.setDataType(dataType);
         return thisField;
      }

      /**
       * Rejects filter/condition mutations on embedded and snapshot-embedded tables,
       * matching the Composer UI's guard in {@code ws-details-pane.component.ts}
       * ({@code openConditionDialog()}), which never opens the condition dialog for
       * {@code isEmbeddedTable() === true} and shows the {@code composer.ws.filter-snapshopt}
       * message instead.
       */
      private void requireFilterable(TableAssembly t) throws PairingException {
         if(t instanceof EmbeddedTableAssembly) {
            throw new PairingException(Catalog.getCatalog().getString("composer.ws.filter-snapshopt"));
         }
      }

      private void requirePermission(ResourceType type) throws SecurityException {
         if(!securityEngine.checkPermission(agent, type, "*", ResourceAction.ACCESS)) {
            throw new SecurityException(Catalog.getCatalog().getString(
               "composer.authorization.permissionDenied"));
         }
      }

      private final Worksheet ws;
      private final Principal agent;
      private final SecurityEngine securityEngine;
      private final InnerJoinService innerJoinService;
   }
}
