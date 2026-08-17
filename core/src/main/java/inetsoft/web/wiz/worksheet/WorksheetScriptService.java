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
import inetsoft.sree.SreeEnv;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.web.wiz.pairing.JoinSession;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.pairing.SheetType;
import inetsoft.web.wiz.script.PaneScopeService;
import inetsoft.web.wiz.script.ScriptTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lands {@link ScriptTarget.Kind#WORKSHEET_EXPRESSION}/{@link ScriptTarget.Kind#WORKSHEET_CONDITION}
 * (G2 Task 8) as a thin front door onto {@link WorksheetAgentController}'s existing
 * {@code edit_expression}/{@code edit_condition} ops.
 *
 * <p><b>A front door, not a second writer.</b> The actual mutation always goes through
 * {@link WorksheetAgentController#edit}, exactly as every other worksheet edit does, so undo/redo
 * and the refresh broadcast stay coherent with the rest of the worksheet. This class only:
 * <ol>
 *   <li>enforces {@link PaneScopeService} so a whole-sheet ("Connect to Claude" toolbar) session
 *       can never reach these two kinds — they have no name or identity at that level, which is
 *       exactly what {@link ScriptTarget.Kind#requiresPaneSession()} encodes;</li>
 *   <li>translates the pane's single free-text edit into the existing op's structured request
 *       (reading the column's current {@code sql}/type flags first, for
 *       {@code worksheetExpression}, so a text-only edit cannot silently flip them); and</li>
 *   <li>refuses — loudly, redirecting to worksheet-chat — any attempt to also change
 *       {@code type}, {@code sql}, or the column's existence, since only the expression/condition
 *       TEXT of the paired location is writable through a pane-scoped session.</li>
 * </ol>
 */
@Service
public class WorksheetScriptService {

   @Autowired
   public WorksheetScriptService(WorksheetEditService editService,
                                 WorksheetAgentController worksheetController)
   {
      this.editService = editService;
      this.worksheetController = worksheetController;
   }

   /** Convenience overload — no other fields were sent alongside {@code text}. */
   public void write(JoinSession session, Principal agent, ScriptTarget target, String text)
      throws Exception
   {
      write(session, agent, target, text, Map.of());
   }

   /**
    * Writes {@code text} at {@code target} through the worksheet's own edit ops.
    *
    * @param extras any OTHER fields the caller also sent (e.g. {@code type}, {@code sql}, a
    *               rename) — always refused. Pass {@link Map#of()}, or use the 4-arg overload,
    *               when there are none.
    * @throws PairingException if {@code session} may not act on {@code target} (see
    *                          {@link PaneScopeService#check}), {@code target} is not one of the
    *                          two kinds this service serves, {@code extras} is non-empty, the
    *                          targeted expression column does not already exist
    *                          ({@code worksheetExpression}), or {@code text} does not parse as a
    *                          condition ({@code worksheetCondition}).
    */
   public void write(JoinSession session, Principal agent, ScriptTarget target, String text,
                     Map<String, Object> extras)
      throws Exception
   {
      requireServedKind(target);
      requireWorksheetSession(session);
      requirePaneScope(session, target);
      rejectExtras(extras);

      EditRequest req = target.kind() == ScriptTarget.Kind.WORKSHEET_EXPRESSION
         ? expressionEditRequest(session, agent, target, text)
         : conditionEditRequest(target, text);

      worksheetController.edit(session.sessionToken(), req, agent);
   }

   private static void requireServedKind(ScriptTarget target) throws PairingException {
      ScriptTarget.Kind kind = target.kind();

      if(kind != ScriptTarget.Kind.WORKSHEET_EXPRESSION &&
         kind != ScriptTarget.Kind.WORKSHEET_CONDITION)
      {
         throw new PairingException(
            "WorksheetScriptService only serves worksheetExpression/worksheetCondition, not '" +
            kind.wireName() + "'.");
      }
   }

   /**
    * A worksheetExpression/worksheetCondition target only makes sense against a worksheet
    * runtime -- their table+field addressing has no viewsheet meaning. Guards against a session
    * minted for the wrong sheet type ever reaching {@link WorksheetAgentController#edit}.
    */
   private static void requireWorksheetSession(JoinSession session) throws PairingException {
      if(session == null || session.sheetType() != SheetType.WORKSHEET) {
         throw new PairingException(
            "worksheetExpression/worksheetCondition require a session joined to a worksheet " +
            "runtime, not " + (session == null ? "no session" : session.sheetType()) + ".");
      }
   }

   /**
    * Reads the strict posture fresh from {@code SreeEnv} on every call (never cached), mirroring
    * {@code ViewsheetAgentController#requirePaneScope} — an administrator flipping
    * {@code wiz.agent.script.require-script-pane} takes effect immediately.
    */
   private void requirePaneScope(JoinSession session, ScriptTarget target) throws PairingException {
      boolean strict = SreeEnv.getBooleanProperty(PaneScopeService.STRICT_FLAG);
      new PaneScopeService(strict).check(session, target);
   }

   private static void rejectExtras(Map<String, Object> extras) throws PairingException {
      if(extras != null && !extras.isEmpty()) {
         throw new PairingException(
            "Only the expression/condition text is writable through this pane-scoped session (" +
            String.join(", ", extras.keySet()) + " may not be changed here). Use " +
            "worksheet-chat's edit_expression/edit_condition tools directly for type, sql, or " +
            "other structural changes.");
      }
   }

   // ---------------------------------------------------------------------------
   // worksheetExpression
   // ---------------------------------------------------------------------------

   private EditRequest expressionEditRequest(JoinSession session, Principal agent,
                                             ScriptTarget target, String text)
      throws PairingException
   {
      String table = target.assemblyName();
      String name = target.name();
      Boolean currentSql = currentSqlFlag(session, agent, table, name);

      if(currentSql == null) {
         throw new PairingException(
            "No expression column '" + name + "' exists on '" + table + "' yet. This " +
            "pane-scoped session may only edit an EXISTING expression's text; use " +
            "worksheet-chat's add_expression_column to create one first.");
      }

      return new EditRequest(
         "edit_expression",   // op
         table,                // table
         null,                 // column
         name,                 // name
         null,                 // type -- null means "leave unchanged" (WorksheetMutationSupport)
         null,                 // newName
         null,                 // field
         null,                 // operation
         null,                 // values
         null,                 // direction
         null,                 // groups
         null,                 // aggregates
         text,                 // expression -- the only field this pane may change
         currentSql,           // sql -- preserved from the existing column, never caller-supplied
         null, null, null, null, null,   // leftTable, leftKey, rightTable, rightKey, joinType
         null, null, null, null,         // visible, tables, source, concatType
         null, null, null, null, null,   // conditions, ranking, headerColumns, dateOption, boundaries
         null, null, null, null,         // datasource, schema, catalog, logicalModel
         null, null,                     // leftKeys, rightKeys
         null, null, null, null,         // row, col, value, index
         null, null, null, null,         // alias, description, maxRows, distinct
         null, null, null, null,         // columnOrder, groupMappings, groupOthers, variableValues
         null, null, null, null,         // x, y, label, defaultValue
         null, null, null                // mode, insert, subtables
      );
   }

   /**
    * The existing column's {@code sql} flag, or {@code null} if no expression column named
    * {@code name} exists on {@code table} yet.
    *
    * <p>A read, not a write — resolves the same {@link RuntimeWorksheet}
    * {@link WorksheetEditService#resolve} already exposes for read-only callers, so this does
    * not touch the worksheet or its undo history.
    */
   private Boolean currentSqlFlag(JoinSession session, Principal agent, String table, String name)
      throws PairingException
   {
      RuntimeWorksheet rws = editService.resolve(session.sessionToken(), agent);
      Worksheet ws = rws == null ? null : rws.getWorksheet();
      Assembly a = ws == null ? null : ws.getAssembly(table);

      if(!(a instanceof TableAssembly t)) {
         throw new PairingException("Table not found in worksheet: " + table);
      }

      ColumnSelection cs = t.getColumnSelection(false);

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         DataRef ref = cs.getAttribute(i);

         if(ref instanceof ColumnRef cr && cr.getDataRef() instanceof ExpressionRef er) {
            if(name.equals(er.getName()) || name.equals(er.getAttribute())) {
               return cr.isSQL();
            }
         }
      }

      return null;
   }

   // ---------------------------------------------------------------------------
   // worksheetCondition
   // ---------------------------------------------------------------------------

   private static EditRequest conditionEditRequest(ScriptTarget target, String text)
      throws PairingException
   {
      ParsedCondition parsed = parseConditionText(text);

      return new EditRequest(
         "edit_condition",     // op
         target.assemblyName(), // table
         null,                  // column
         null,                  // name
         null,                  // type
         null,                  // newName
         target.name(),         // field -- the paired column; never caller-supplied
         parsed.operation(),    // operation
         parsed.values(),       // values
         null, null, null,      // direction, groups, aggregates
         null, false,           // expression, sql
         null, null, null, null, null,   // leftTable, leftKey, rightTable, rightKey, joinType
         null, null, null, null,         // visible, tables, source, concatType
         null, null, null, null, null,   // conditions, ranking, headerColumns, dateOption, boundaries
         null, null, null, null,         // datasource, schema, catalog, logicalModel
         null, null,                     // leftKeys, rightKeys
         null, null, null, null,         // row, col, value, index
         null, null, null, null,         // alias, description, maxRows, distinct
         null, null, null, null,         // columnOrder, groupMappings, groupOthers, variableValues
         null, null, null, null,         // x, y, label, defaultValue
         null, null, null                // mode, insert, subtables
      );
   }

   /**
    * The operator vocabulary {@code editCondition} accepts — exactly
    * {@code WorksheetModel.FilterModel}'s documented set, so a caller inspecting
    * {@code read_worksheet_model}'s existing conditions and one written here see the same
    * grammar.
    */
   private static final Set<String> CONDITION_OPERATORS = Set.of(
      "=", "!=", "<", "<=", ">", ">=", "BETWEEN", "ONE_OF", "NOT_ONE_OF",
      "STARTING_WITH", "CONTAINS", "LIKE", "NULL", "NOT_NULL");

   private record ParsedCondition(String operation, List<String> values) {}

   /**
    * Parses a single free-text condition edit, e.g. {@code "> 0"}, {@code "BETWEEN 1 AND 10"},
    * {@code "ONE_OF a,b,c"}, or {@code "NULL"}, into the operator/values pair
    * {@code editCondition} needs.
    *
    * <p>Deliberately narrow: a compound or multi-field condition tree is out of scope for a
    * single pane's text box and is refused with a redirect to worksheet-chat's
    * {@code set_conditions}, rather than this method growing a junction grammar of its own.
    */
   private static ParsedCondition parseConditionText(String text) throws PairingException {
      if(text == null || text.isBlank()) {
         throw new PairingException(
            "worksheetCondition text is required, e.g. \"> 0\", \"BETWEEN 1 AND 10\", " +
            "\"ONE_OF a,b,c\", or \"NULL\".");
      }

      String trimmed = text.trim();
      int sp = trimmed.indexOf(' ');
      String opToken = (sp < 0 ? trimmed : trimmed.substring(0, sp)).toUpperCase();
      String rest = sp < 0 ? "" : trimmed.substring(sp + 1).trim();

      if(!CONDITION_OPERATORS.contains(opToken)) {
         throw new PairingException(
            "Unrecognized worksheetCondition operator '" + opToken + "' in \"" + text + "\". " +
            "Expected one of " + String.join(", ", CONDITION_OPERATORS.stream().sorted().toList()) +
            "; for compound or multi-field conditions, use worksheet-chat's set_conditions " +
            "instead.");
      }

      if("NULL".equals(opToken) || "NOT_NULL".equals(opToken)) {
         return new ParsedCondition(opToken, List.of());
      }

      if("BETWEEN".equals(opToken)) {
         String[] parts = rest.split("(?i)\\s+AND\\s+", 2);

         if(parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new PairingException(
               "'BETWEEN' requires \"BETWEEN <a> AND <b>\": \"" + text + "\"");
         }

         return new ParsedCondition(opToken, List.of(parts[0].trim(), parts[1].trim()));
      }

      if("ONE_OF".equals(opToken) || "NOT_ONE_OF".equals(opToken)) {
         List<String> values = Arrays.stream(rest.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();

         if(values.isEmpty()) {
            throw new PairingException(
               "'" + opToken + "' requires at least one value: \"" + text + "\"");
         }

         return new ParsedCondition(opToken, values);
      }

      if(rest.isBlank()) {
         throw new PairingException("'" + opToken + "' requires a value: \"" + text + "\"");
      }

      return new ParsedCondition(opToken, List.of(rest));
   }

   private final WorksheetEditService editService;
   private final WorksheetAgentController worksheetController;
}
