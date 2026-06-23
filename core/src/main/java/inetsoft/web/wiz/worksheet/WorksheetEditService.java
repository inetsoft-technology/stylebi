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
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XEmbeddedTable;
import java.util.Enumeration;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.wiz.pairing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;

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
                               SheetAgentBroadcastService broadcast)
   {
      this.sessions = sessions;
      this.runtimeAccess = runtimeAccess;
      this.broadcast = broadcast;
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
      throws PairingException
   {
      String agentKey = agentKey(agent);
      JoinSession session = sessions.resolve(sessionToken, agentKey);

      if(session == null) {
         throw new PairingException("Invalid or expired session: " + sessionToken);
      }

      RuntimeWorksheet rws = (RuntimeWorksheet) runtimeAccess.getSheetForPairing(
         SheetType.WORKSHEET, session.runtimeId(), agent);
      applySocketSession(rws, session);

      Editor editor = new Editor(rws.getWorksheet());
      mutation.accept(editor);
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
      throws PairingException
   {
      String agentKey = agentKey(agent);
      JoinSession session = sessions.resolve(sessionToken, agentKey);

      if(session == null) {
         throw new PairingException("Invalid or expired session: " + sessionToken);
      }

      RuntimeWorksheet rws = (RuntimeWorksheet) runtimeAccess.getSheetForPairing(
         SheetType.WORKSHEET, session.runtimeId(), agent);
      applySocketSession(rws, session);

      T result = mutation.apply(rws);
      refreshAssemblies(rws);
      broadcast.broadcastRefresh(rws, SheetType.WORKSHEET, session.runtimeId(), agent);
      return result;
   }

   @FunctionalInterface
   public interface ThrowingFunction<A, R> {
      R apply(A a) throws PairingException;
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
         throw new PairingException("Invalid or expired session: " + sessionToken);
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
         throw new PairingException("Invalid or expired session: " + sessionToken);
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
    * Propagates the browser's socket session ID and user name from the pairing session
    * to the RuntimeWorksheet so the broadcast can deliver the refresh command.
    */
   /**
    * Refresh column selections and reload table data for all assemblies in the worksheet.
    * Mirrors the UI's post-edit steps (InsertDataService calls refreshColumnSelection +
    * loadTableData after column mutations).
    */
   private void refreshAssemblies(RuntimeWorksheet rws) {
      Worksheet ws = rws.getWorksheet();

      if(ws == null) {
         return;
      }

      for(Assembly a : ws.getAssemblies()) {
         if(a instanceof TableAssembly) {
            String name = a.getName();
            WorksheetEventUtil.refreshColumnSelection(rws, name, true);
            WorksheetEventUtil.loadTableData(rws, name, true, true);
         }
      }
   }

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

      return agent.getName();
   }

   // -------------------------------------------------------------------------
   // Dependencies
   // -------------------------------------------------------------------------

   private final SheetSessionService sessions;
   private final SheetRuntimeAccess runtimeAccess;
   private final SheetAgentBroadcastService broadcast;
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

      Editor(Worksheet ws) {
         this.ws = ws;
      }

      /**
       * Removes the named column from the table's public {@link ColumnSelection}.
       * No-ops if the column does not exist.
       *
       * @param table the assembly name
       * @param col   the column attribute name to remove
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists
       */
      public void removeColumn(String table, String col) throws PairingException {
         TableAssembly t = requireTable(table);
         ColumnSelection cs = t.getColumnSelection();
         DataRef toRemove = cs.getAttribute(col);

         if(toRemove != null) {
            // For embedded tables, also remove the data column from XEmbeddedTable
            if(t instanceof EmbeddedTableAssembly embedded) {
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
       * @param table the assembly name
       * @param name  the new column's attribute name
       * @param type  the data type string (e.g. {@code "string"}, {@code "integer"}), or {@code null}
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists
       */
      public void addColumn(String table, String name, String type) throws PairingException {
         TableAssembly t = requireTable(table);
         ColumnSelection cs = t.getColumnSelection();

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
       * Sets the alias of an existing column, effectively renaming it in the output.
       * No-ops if the column does not exist or is not a {@link ColumnRef}.
       *
       * @param table   the assembly name
       * @param col     the column attribute name to rename
       * @param newName the new alias
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists
       */
      public void renameColumn(String table, String col, String newName) throws PairingException {
         TableAssembly t = requireTable(table);
         ColumnSelection cs = t.getColumnSelection(false);
         DataRef existing = cs.getAttribute(col);

         if(existing instanceof ColumnRef cr) {
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
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists
       */
      public void addFilter(String table, String field,
                            String operation, String... values) throws PairingException
      {
         WorksheetMutationSupport.addFilter(requireTable(table), field, operation, values);
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
       * @param groups     column names to group by
       * @param aggregates aggregate measures to apply
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists
       */
      public void setGroupAggregate(String table, List<String> groups,
                                    List<WorksheetMutationSupport.AggregateSpec> aggregates)
         throws PairingException
      {
         WorksheetMutationSupport.applyAggregateInfo(requireTable(table), groups, aggregates);
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
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists
       */
      public void addExpressionColumn(String table, String name, String expression,
                                      String type, boolean sql) throws PairingException
      {
         WorksheetMutationSupport.addExpressionColumn(requireTable(table), name,
                                                      expression, type, sql);
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
         WorksheetMutationSupport.setSort(requireTable(table), field, direction);
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
       *                   {@code "FULL"} (case-insensitive; defaults to {@code "INNER"})
       * @throws PairingException if either source assembly is not found
       */
      public void addJoin(String name, String leftTable, String leftKey,
                          String rightTable, String rightKey,
                          String joinType) throws PairingException
      {
         TableAssembly left  = requireTable(leftTable);
         TableAssembly right = requireTable(rightTable);

         int operation = parseJoinType(joinType);

         // Build the operator for the single key-pair join.
         TableAssemblyOperator top = new TableAssemblyOperator();
         TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
         op.setLeftTable(leftTable);
         op.setRightTable(rightTable);
         op.setLeftAttribute(new AttributeRef(null, leftKey));
         op.setRightAttribute(new AttributeRef(null, rightKey));
         op.setOperation(operation);
         top.addOperator(op);

         RelationalJoinTableAssembly join =
            new RelationalJoinTableAssembly(ws, name,
                                            new TableAssembly[]{ left, right },
                                            new TableAssemblyOperator[]{ top });
         ws.addAssembly(join);
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
       */
      public void addTable(String name, String... columns) {
         EmbeddedTableAssembly t = new EmbeddedTableAssembly(ws, name);
         ColumnSelection cs = new ColumnSelection();

         for(String col : columns) {
            cs.addAttribute(new ColumnRef(new AttributeRef(null, col)));
         }

         t.setColumnSelection(cs, false);
         ws.addAssembly(t);
      }

      // -----------------------------------------------------------------------
      // Helper
      // -----------------------------------------------------------------------

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
       * @throws PairingException if fewer than two tables are given or a source is not found
       */
      public void addConcatenation(String name, List<String> tables, String opType)
         throws PairingException
      {
         if(tables == null || tables.size() < 2) {
            throw new PairingException("Concatenation requires at least two tables.");
         }

         int operation = parseConcatType(opType);
         TableAssembly[] sources = new TableAssembly[tables.size()];

         for(int i = 0; i < tables.size(); i++) {
            sources[i] = requireTable(tables.get(i));
         }

         // Build one operator per adjacent pair.
         TableAssemblyOperator[] operators = new TableAssemblyOperator[sources.length - 1];

         for(int i = 0; i < operators.length; i++) {
            TableAssemblyOperator top = new TableAssemblyOperator();
            TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
            op.setOperation(operation);
            top.addOperator(op);
            operators[i] = top;
         }

         ConcatenatedTableAssembly ctbl =
            new ConcatenatedTableAssembly(ws, name, sources, operators);
         ws.addAssembly(ctbl);
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
         Assembly a = ws.getAssembly(source);

         if(!(a instanceof WSAssembly wsa)) {
            throw new PairingException("Source assembly not found: " + source);
         }

         MirrorTableAssembly mirror = new MirrorTableAssembly(ws, name, wsa);
         ws.addAssembly(mirror);
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
       * @throws PairingException if the table or column is not found
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
      public void changeColumnType(String table, String col, String type)
         throws PairingException
      {
         TableAssembly t = requireTable(table);
         ColumnSelection cs = t.getColumnSelection(false);
         DataRef ref = cs.getAttribute(col);

         if(!(ref instanceof ColumnRef cr)) {
            throw new PairingException("Column not found: " + col);
         }

         cr.setDataType(type);
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
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists
       */
      public void editCondition(String table, String field,
                                String operation, String... values)
         throws PairingException
      {
         TableAssembly t = requireTable(table);
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
         throws PairingException
      {
         WorksheetMutationSupport.editExpression(requireTable(table), name, expression, type, sql);
      }

      /**
       * Replaces the key columns and join type of an existing two-table join assembly,
       * keeping the same source tables.
       *
       * <p>Only the first key pair of the first operator is updated; multi-key joins
       * are not supported by this method.</p>
       *
       * @param name      the join assembly name
       * @param leftKey   the new left-side key column
       * @param rightKey  the new right-side key column
       * @param joinType  new join type — {@code "INNER"}, {@code "LEFT"}, {@code "RIGHT"},
       *                  {@code "FULL"} (case-insensitive; defaults to {@code "INNER"})
       * @throws PairingException if the assembly is not found or has no operators
       */
      public void editJoin(String name, String leftKey, String rightKey, String joinType)
         throws PairingException
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

         // Build a replacement operator with updated keys and join type.
         TableAssemblyOperator newTop = new TableAssemblyOperator();
         TableAssemblyOperator.Operator newOp = new TableAssemblyOperator.Operator();
         newOp.setLeftTable(leftTable);
         newOp.setRightTable(rightTable);
         newOp.setLeftAttribute(new AttributeRef(null, leftKey));
         newOp.setRightAttribute(new AttributeRef(null, rightKey));
         newOp.setOperation(parseJoinType(joinType));
         newTop.addOperator(newOp);

         join.setOperator(leftTable, rightTable, newTop);
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
            default      -> TableAssemblyOperator.INNER_JOIN;
         };
      }

      private TableAssembly requireTable(String name) throws PairingException {
         Assembly a = ws.getAssembly(name);

         if(!(a instanceof TableAssembly t)) {
            throw new PairingException("Table not found in worksheet: " + name);
         }

         return t;
      }

      private final Worksheet ws;
   }
}
