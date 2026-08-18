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
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.ConditionListWrapper;
import inetsoft.uql.HierarchyItem;
import inetsoft.uql.XCondition;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.web.wiz.pairing.EditorContext;
import inetsoft.web.wiz.pairing.JoinSession;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.pairing.SheetType;
import inetsoft.web.wiz.script.PaneScopeService;
import inetsoft.web.wiz.script.ScriptTarget;
import inetsoft.web.wiz.script.model.ScriptInfo;
import inetsoft.web.wiz.script.model.ScriptTargetInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
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
 *
 * <p><b>G2 Task 8b</b> wires this service to {@link WorksheetScriptController}'s real HTTP
 * endpoints and adds its read side: {@link #list} enumerates worksheetExpression/
 * worksheetCondition targets (mirroring {@code ScriptReadService#list(RuntimeViewsheet,
 * JoinSession)}'s whole-sheet-sees-everything / pane-scoped-sees-its-own-grant split), and
 * {@link #read} reads one target's current text, enforcing the exact same three gates {@link
 * #write} does. Before Task 8b, this class had no caller in {@code core/src/main/java} outside
 * itself, so the "front door, not second writer" guarantee above was proven only against a
 * Mockito stub of {@link WorksheetAgentController}.
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

   // ---------------------------------------------------------------------------
   // Discovery (G2 Task 8b) -- targets/read, the companions write() already had
   // ---------------------------------------------------------------------------

   /**
    * Enumerates every worksheetExpression/worksheetCondition target on {@code rws}, scoped to
    * {@code session}'s own grant.
    *
    * <p>Mirrors {@code ScriptReadService.list(RuntimeViewsheet, JoinSession)}: a whole-sheet
    * session ({@code session} is {@code null} or carries no {@link JoinSession#editorContext()})
    * sees the full enumeration -- discovery only, since {@link #write}/{@link #read} still refuse
    * it per-target via {@link PaneScopeService}, exactly as a whole-sheet viewsheet session can
    * see every {@code calcField} in {@code list_script_targets} without being able to write one.
    * A pane-scoped session sees only the target(s) {@link PaneScopeService#matchesGrant} lets it
    * act on.
    *
    * <p>One target per expression column found (any table), and one target per FIELD carrying an
    * existing pre-condition (any filterable table) -- not one per possible column, since
    * {@code editCondition} adding a condition to a field with none is legitimate but a "every
    * column on every table is a target" enumeration would explode into a list nobody asked for.
    */
   public List<ScriptTargetInfo> list(RuntimeWorksheet rws, JoinSession session) {
      List<ScriptTargetInfo> all = new ArrayList<>();
      Worksheet ws = rws == null ? null : rws.getWorksheet();

      if(ws != null) {
         for(Assembly a : ws.getAssemblies()) {
            if(a instanceof TableAssembly t) {
               collectExpressionTargets(t, all);
               collectConditionTargets(t, all);
            }
         }
      }

      if(session == null || session.editorContext() == null) {
         return all;
      }

      EditorContext grant = session.editorContext();
      return all.stream()
         .filter(info -> matchesGrant(grant, info))
         .toList();
   }

   /** Decodes {@code info}'s own id back into a {@link ScriptTarget} to test it against grant. */
   private static boolean matchesGrant(EditorContext grant, ScriptTargetInfo info) {
      try {
         return PaneScopeService.matchesGrant(grant, ScriptTarget.fromId(info.id()));
      }
      catch(PairingException ex) {
         // A target whose own id fails to decode cannot be addressed anyway; exclude it from a
         // scoped listing rather than let a malformed id leak past the filter.
         return false;
      }
   }

   private static void collectExpressionTargets(TableAssembly t, List<ScriptTargetInfo> out) {
      ColumnSelection cs = t.getColumnSelection(false);

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         DataRef ref = cs.getAttribute(i);

         if(!(ref instanceof ColumnRef cr) || !(cr.getDataRef() instanceof ExpressionRef er)) {
            continue;
         }

         String name = er.getName() != null ? er.getName() : er.getAttribute();
         ScriptTarget target;

         try {
            target = ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_EXPRESSION, t.getName(), name);
         }
         catch(PairingException ex) {
            // Unreachable: t.getName() and name are both non-blank here.
            continue;
         }

         out.add(new ScriptTargetInfo(
            target.id(), ScriptTarget.Kind.WORKSHEET_EXPRESSION.wireName(), t.getName(), name,
            cr.isSQL(), null,
            "Worksheet expression column '" + name + "' on " + t.getName(),
            "each time " + t.getName() + " is queried",
            true,                            // an expression column always has a body (may be "")
            true,                            // no per-column enable flag exists
            "worksheetExpression:" + t.getName() + ":" + name,
            "worksheet", null));             // no legacy delimited form
      }
   }

   private static void collectConditionTargets(TableAssembly t, List<ScriptTargetInfo> out) {
      ConditionListWrapper wrapper = t.getPreConditionList();

      if(wrapper == null || wrapper.isEmpty()) {
         return;
      }

      ConditionList cl = wrapper.getConditionList();
      Set<String> seen = new LinkedHashSet<>();

      for(int i = 0; i < cl.getSize(); i++) {
         HierarchyItem hi = cl.getItem(i);

         if(!(hi instanceof ConditionItem ci)) {
            continue;
         }

         DataRef attr = ci.getAttribute();
         String name = attr.getAttribute() != null && !attr.getAttribute().isEmpty()
            ? attr.getAttribute() : attr.getName();

         if(name == null || !seen.add(name)) {
            continue; // one target per field, even when several ANDed conditions share it
         }

         ScriptTarget target;

         try {
            target = ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_CONDITION, t.getName(), name);
         }
         catch(PairingException ex) {
            continue; // unreachable: t.getName() and name are both non-blank here
         }

         out.add(new ScriptTargetInfo(
            target.id(), ScriptTarget.Kind.WORKSHEET_CONDITION.wireName(), t.getName(), name,
            null, null,
            "Worksheet condition on '" + t.getName() + "." + name + "'",
            "each time " + t.getName() + " is queried",
            true, true,
            "worksheetCondition:" + t.getName() + ":" + name,
            "worksheet", null));
      }
   }

   /**
    * Reads the current text of {@code target} -- the read-side companion to {@link #write},
    * enforcing the same three gates (served kind, worksheet session, pane scope) since a read of
    * an expression-level location leaks the same identity a write would.
    */
   public ScriptInfo read(JoinSession session, Principal agent, ScriptTarget target)
      throws PairingException
   {
      requireServedKind(target);
      requireWorksheetSession(session);
      requirePaneScope(session, target);

      RuntimeWorksheet rws = editService.resolve(session.sessionToken(), agent);
      Worksheet ws = rws == null ? null : rws.getWorksheet();
      Assembly a = ws == null ? null : ws.getAssembly(target.assemblyName());

      if(!(a instanceof TableAssembly t)) {
         throw new PairingException("Table not found in worksheet: " + target.assemblyName());
      }

      return target.kind() == ScriptTarget.Kind.WORKSHEET_EXPRESSION
         ? readExpression(t, target)
         : readCondition(t, target);
   }

   private static ScriptInfo readExpression(TableAssembly t, ScriptTarget target)
      throws PairingException
   {
      ColumnSelection cs = t.getColumnSelection(false);

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         DataRef ref = cs.getAttribute(i);

         if(ref instanceof ColumnRef cr && cr.getDataRef() instanceof ExpressionRef er) {
            if(target.name().equals(er.getName()) || target.name().equals(er.getAttribute())) {
               String text = er.getExpression();
               return new ScriptInfo(target.toString(), text == null ? "" : text, true);
            }
         }
      }

      throw new PairingException(
         "No expression column '" + target.name() + "' exists on '" + target.assemblyName() +
         "'.");
   }

   private static ScriptInfo readCondition(TableAssembly t, ScriptTarget target) {
      ConditionListWrapper wrapper = t.getPreConditionList();

      if(wrapper != null && !wrapper.isEmpty()) {
         ConditionList cl = wrapper.getConditionList();

         for(int i = 0; i < cl.getSize(); i++) {
            HierarchyItem hi = cl.getItem(i);

            if(hi instanceof ConditionItem ci) {
               DataRef attr = ci.getAttribute();

               if(target.name().equals(attr.getAttribute()) || target.name().equals(attr.getName())) {
                  return new ScriptInfo(target.toString(),
                     formatCondition((Condition) ci.getXCondition()), true);
               }
            }
         }
      }

      // No condition set on this field yet -- not an error; editCondition() can add one.
      return new ScriptInfo(target.toString(), "", true);
   }

   /**
    * The inverse of {@link #parseConditionText}: renders a {@link Condition} back into the same
    * free-text vocabulary a caller would write, e.g. {@code "> 0"}, {@code "BETWEEN 1 AND 10"},
    * {@code "ONE_OF a,b,c"}, or {@code "NULL"}, so a round-trip read/write sees the same grammar.
    */
   private static String formatCondition(Condition c) {
      boolean negated = c.isNegated();
      List<String> values = new ArrayList<>();

      for(int i = 0; i < c.getValueCount(); i++) {
         Object v = c.getValue(i);
         values.add(v == null ? "" : String.valueOf(v));
      }

      String first = values.isEmpty() ? "" : values.get(0);

      return switch(c.getOperation()) {
         case XCondition.NULL -> negated ? "NOT_NULL" : "NULL";
         case XCondition.BETWEEN ->
            "BETWEEN " + first + " AND " + (values.size() > 1 ? values.get(1) : "");
         case XCondition.ONE_OF -> (negated ? "NOT_ONE_OF " : "ONE_OF ") + String.join(",", values);
         case XCondition.STARTING_WITH -> "STARTING_WITH " + first;
         case XCondition.CONTAINS -> "CONTAINS " + first;
         case XCondition.LIKE -> "LIKE " + first;
         case XCondition.LESS_THAN -> (c.isEqual() ? "<= " : "< ") + first;
         case XCondition.GREATER_THAN -> (c.isEqual() ? ">= " : "> ") + first;
         default -> (negated ? "!= " : "= ") + first; // XCondition.EQUAL_TO and any other fallback
      };
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
