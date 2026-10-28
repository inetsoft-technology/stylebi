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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.VSAQuery;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTable;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.EmbeddedTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.drm.AggregateRefModel;
import inetsoft.web.binding.drm.CalculateRefModel;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.DateRangeRefModel;
import inetsoft.web.binding.model.ExpressionRefModel;
import inetsoft.web.composer.model.condition.ConditionModel;
import inetsoft.web.composer.model.condition.ConditionValueModel;
import inetsoft.web.composer.model.condition.ExpressionValueModel;
import inetsoft.web.composer.model.vs.VSConditionDialogModel;
import inetsoft.web.composer.vs.dialog.VSConditionDialogService;
import inetsoft.web.viewsheet.controller.VSRefreshService;
import inetsoft.web.viewsheet.controller.table.BaseTableService;
import inetsoft.web.viewsheet.event.RefreshVSAssemblyEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.wiz.service.WizVsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

/**
 * Assembly-level conditions — filtering what an assembly shows.
 *
 * <p>Wraps the Composer's own condition dialog rather than reusing wiz's {@code apply_filter}.
 * That was a deliberate decision: the wiz path is <b>copy-then-apply</b>, duplicating the target
 * so a chat conversation accumulates parallel versions. A Composer user editing their own
 * viewsheet expects the change to land in place, and a parallel copy appearing instead would be
 * a silent wrong result rather than an error.
 *
 * <p>The condition list itself is built by {@link ConditionVocabulary}, which owns the
 * alternating-array hazard. This class only reads the model, swaps the list, and writes it back —
 * preserving {@code tableName} and {@code fields}, neither of which a caller supplies.
 *
 * <p><b>A condition changes what renders</b>, and one that matches nothing produces an empty but
 * structurally valid assembly that returns cleanly. So the summary says to look, and the read
 * side reports how many conditions are active.
 */
@Service
public class AssemblyConditionService {
   /** Evaluates a condition expression the way the viewsheet refresh will. */
   @FunctionalInterface
   interface ExpressionChecker {
      /** @return null when it evaluates, else the script error; throws if it does not compile. */
      String check(String expression, ViewsheetSandbox box) throws Exception;
   }

   @Autowired
   public AssemblyConditionService(ViewsheetSessionService sessions,
                                   VSConditionDialogService conditionService,
                                   VSRefreshService refreshService)
   {
      this(sessions, conditionService, refreshService, VSAQuery::checkConditionExpression);
   }

   AssemblyConditionService(ViewsheetSessionService sessions,
                            VSConditionDialogService conditionService,
                            VSRefreshService refreshService,
                            ExpressionChecker checker)
   {
      this.sessions = sessions;
      this.conditionService = conditionService;
      this.refreshService = refreshService;
      this.checker = checker;
   }

   /** The outcome of a condition write: how many conditions applied, and anything to disclose. */
   public record SetResult(int applied, List<String> warnings) {}

   /** The current conditions, in the flat vocabulary, plus what fields are filterable. */
   public Map<String, Object> read(String sessionToken, Principal user, String assemblyName)
      throws Exception
   {
      VSConditionDialogModel model = conditionService.getModel(
         sessions.resolve(sessionToken, user).getID(), assemblyName, user);

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("assembly", assemblyName);

      if(model == null) {
         out.put("conditions", List.of());
         out.put("fields", List.of());
         return out;
      }

      List<Map<String, Object>> conditions =
         ConditionVocabulary.describe(model.getConditionList(), true);
      out.put("tableName", model.getTableName());
      out.put("conditions", conditions);
      out.put("conditionCount", conditions.size());
      out.put("fields", fieldNames(model.getFields()));
      List<String> warnings = staleConditionWarnings(model);

      if(!warnings.isEmpty()) {
         out.put("warnings", warnings);
      }

      return out;
   }

   /**
    * Bug #79526: a condition on a column the assembly no longer offers (removed from the source
    * worksheet) stays stored and is listed, but is not applied, so the assembly shows unfiltered
    * data without any sign of it. Names each such condition's field.
    *
    * <p>Resolved through {@link ConditionVocabulary#find}, the resolver {@code set_condition}
    * writes use, so a relabelled column and a calc field are not reported. Only a plain column
    * reference is judged: an aggregate, expression, range or calculated reference is not named
    * in {@code fields} the same way. Empty {@code fields} means unknown (an unbound assembly or a
    * failed source query), not "everything is stale", and a label shared by several columns is
    * ambiguous, not stale.</p>
    */
   private static List<String> staleConditionWarnings(VSConditionDialogModel model) {
      DataRefModel[] fields = model.getFields();
      List<String> warnings = new ArrayList<>();

      if(fields == null || fields.length == 0 || model.getConditionList() == null) {
         return warnings;
      }

      Set<String> seen = new HashSet<>();

      for(Object item : model.getConditionList()) {
         if(!(item instanceof ConditionModel condition) || !isPlainColumn(condition.getField())) {
            continue;
         }

         String name = condition.getField().getName();

         if(name == null || name.isBlank() || !seen.add(name.trim().toLowerCase())) {
            continue;
         }

         try {
            if(ConditionVocabulary.find(fields, name) == null) {
               warnings.add("Condition on '" + name + "' is not applied: this assembly's " +
                  "current fields do not include it (column removed from the source " +
                  "worksheet?), so the assembly shows unfiltered data for it. Drop or rewrite " +
                  "the condition with set_condition.");
            }
         }
         catch(IllegalArgumentException ambiguous) {
            // a label shared by several columns resolves to none of them, but it is not stale
         }
      }

      return warnings;
   }

   private static boolean isPlainColumn(DataRefModel field) {
      for(DataRefModel ref = field; ref != null;
          ref = ref instanceof ColumnRefModel column ? column.getDataRefModel() : null)
      {
         if(ref instanceof AggregateRefModel || ref instanceof ExpressionRefModel ||
            ref instanceof DateRangeRefModel || ref instanceof CalculateRefModel)
         {
            return false;
         }
      }

      return field != null;
   }

   /**
    * Replaces the assembly's conditions. One {@code sessions.mutate}, so one undo checkpoint.
    *
    * <p>The clauses are validated against the model's own {@code fields[]}, which means the
    * model has to be read first — a condition naming a column the assembly cannot filter on is
    * the recorded cause of a downstream cast failure.
    */
   public int set(String sessionToken, Principal user, String assemblyName,
                  List<ConditionVocabulary.Clause> clauses, String linkUri) throws Exception
   {
      return setWithWarnings(sessionToken, user, assemblyName, clauses, linkUri).applied();
   }

   /**
    * {@link #set}, plus the warnings a caller should relay (bug #78260).
    *
    * <p>An expression operand that throws leaves the viewsheet table with no data, but the
    * refresh swallows a JavaScript failure, so the write would otherwise read as a plain
    * success. Each expression is therefore evaluated first, before anything is committed:
    * a syntax error is refused (it can never become valid), and a runtime failure is committed
    * with a warning -- it can be transient, e.g. an expression reading a selection list that has
    * no selection yet works once one is made. The expression is evaluated as JavaScript on a
    * viewsheet assembly whatever its declared language, so a {@code sql} expression that does
    * not evaluate is always refused: its failure is not swallowed and would otherwise leave the
    * write half applied.
    */
   public SetResult setWithWarnings(String sessionToken, Principal user, String assemblyName,
                                    List<ConditionVocabulary.Clause> clauses, String linkUri)
      throws Exception
   {
      int[] applied = new int[1];
      List<String> warnings = new ArrayList<>();

      List<String> sessionWarnings = sessions.mutate(sessionToken, user,
                                                     (rvs, runtimeId, dispatcher) -> {
         Viewsheet vs = rvs.getViewsheet();

         // An embedded table never applies a condition list: EmbeddedTableVSAQuery resets the
         // pre-condition list on every run, and the Composer UI hides Conditions for it. Accepting
         // the write would report a narrowing filter that has no effect (bug #79509). Clearing
         // (an empty list) is harmless and still allowed.
         if(clauses != null && !clauses.isEmpty() && vs != null &&
            vs.getAssembly(assemblyName) instanceof EmbeddedTableVSAssembly)
         {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is an Embedded Table, and Embedded Tables do not support " +
               "conditions -- they are ignored on every run, so the filter would never change " +
               "the rows. Nothing was stored. Filter the data with a worksheet condition or a " +
               "selection component instead.");
         }

         VSConditionDialogModel model = conditionService.getModel(runtimeId, assemblyName, user);

         if(model == null) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' has no condition dialog — it is not an assembly that " +
               "can be filtered. Charts, tables, crosstabs and selection assemblies can.");
         }

         Object[] conditionList = ConditionVocabulary.toConditionList(clauses, model.getFields());
         checkExpressions(rvs, conditionList, warnings);
         ViewsheetSandbox box = rvs.getViewsheetSandbox().orElse(null);
         VariableTable variables = box == null ? null : box.getAllVariables();
         VariableOperandCheck.check(conditionList, variables, true, warnings);
         boolean guard = box != null && VariableOperandCheck.hasVariableOperand(conditionList);
         boolean failingBefore = guard && failedQueryCause(box, assemblyName) != null;
         Object[] previous = model.getConditionList();
         model.setConditionList(conditionList);
         applied[0] = clauses == null ? 0 : clauses.size();

         try {
            conditionService.setModel(runtimeId, assemblyName, model, linkUri, user, dispatcher);
            reexecute(rvs, runtimeId, assemblyName, dispatcher, linkUri, user);
         }
         catch(Exception e) {
            // the refresh failed after the new list was committed; put the old one back rather
            // than leave a condition the caller was told failed
            String notRestored =
               restore(rvs, runtimeId, assemblyName, previous, linkUri, user, dispatcher);

            if(notRestored != null) {
               throw new IllegalStateException(
                  e.getMessage() + " The previous conditions could NOT be restored: " +
                  notRestored + " The new condition is still stored on '" + assemblyName +
                  "'; check get_condition.", e);
            }

            throw e;
         }

         // A live-mode query that fails is swallowed into a design-time sample row (e.g. integer
         // 999 and the current timestamp), so setModel does not throw and the table would serve
         // that fabricated row as data. A table that rendered fine before, and fails now, owes
         // that to the variable operand just written: undo it and say why (bug #79535). Read
         // after reexecute() so the data is the re-executed one.
         String cause = guard && !failingBefore ? failedQueryCause(box, assemblyName) : null;

         if(cause != null) {
            String notRestored =
               restore(rvs, runtimeId, assemblyName, previous, linkUri, user, dispatcher);
            throw new IllegalArgumentException(
               "The condition makes the query of '" + assemblyName + "' fail" +
               (cause.isBlank() ? "" : ": " + cause) + ". It uses variable operand(s) " +
               VariableOperandCheck.describeOperands(conditionList, variables) + ". " +
               String.join(" ", warnings) + (warnings.isEmpty() ? "" : " ") +
               (notRestored == null ? "Nothing was changed." :
                  "The previous conditions could NOT be restored: " + notRestored +
                  " The new condition is still stored on '" + assemblyName + "'; fix or clear " +
                  "it with set_condition."));
         }

         ChartRenderCheck.warnIfChartScriptFails(rvs, assemblyName, dispatcher);
      });


      // The pre-check already reported a throwing expression; the refresh then reports the same
      // failure again as a script-failure message on this assembly, so drop that duplicate. On a
      // chart the pre-check text is table-worded ("the table shows no rows") and has no chart
      // name, so the chart's own warning replaces it rather than joining it.
      String ownFailure = BaseTableService.SCRIPT_FAILURE_PREFIX + assemblyName + '\n';
      List<String> relayed = sessionWarnings == null ? List.<String>of() : sessionWarnings;

      if(relayed.stream().anyMatch(w -> ChartRenderCheck.isWarningFor(w, assemblyName))) {
         warnings.clear();
      }

      boolean preCheckWarned = !warnings.isEmpty();

      for(String warning : relayed) {
         if(!preCheckWarned || !warning.startsWith(ownFailure)) {
            warnings.add(warning);
         }
      }

      return new SetResult(applied[0], warnings);
   }

   /**
    * Bug #79543: re-executes the assembly the way {@code edit op:refresh} does. The Composer's
    * condition service skips its own re-execution when any assembly in the viewsheet holds an
    * unresolved prompt variable (it sends a parameter prompt and returns, and nothing answers it
    * here), which left the assembly -- of any type -- serving the previous condition's data until
    * a later read refreshed it. Only the native service's early return is worked around: it is
    * left as is, since the Composer UI relies on it.
    */
   private void reexecute(RuntimeViewsheet rvs, String runtimeId, String assemblyName,
                          CommandDispatcher dispatcher, String linkUri, Principal user)
      throws Exception
   {
      RefreshVSAssemblyEvent event = RefreshVSAssemblyEvent.builder()
         .vsRuntimeId(runtimeId)
         .assemblyName(assemblyName)
         .build();
      Viewsheet vs = rvs.getViewsheet();

      if(vs != null && vs.getAssembly(assemblyName) instanceof ChartVSAssembly) {
         // a chart whose script fails is reported once by the check after this, not thrown
         ChartRenderCheck.swallowScriptError(rvs, assemblyName, () ->
            refreshService.refreshVsAssembly(runtimeId, event, dispatcher, linkUri, user));
      }
      else {
         refreshService.refreshVsAssembly(runtimeId, event, dispatcher, linkUri, user);
      }
   }

   /**
    * Puts the conditions that were stored before the write back, and checks that they are.
    *
    * <p>The write's own model cannot be reused: {@code setModel} refuses a model whose revision
    * predates the last write (it only sends a write-conflict message and returns), and the first
    * {@code setModel} had just bumped it, so a restore through that model silently did nothing
    * (bug #79535). A freshly read model carries the current revision.
    *
    * @return null when the stored list now equals {@code previous}, otherwise why it does not
    */
   private String restore(RuntimeViewsheet rvs, String runtimeId, String assemblyName,
                          Object[] previous, String linkUri, Principal user,
                          CommandDispatcher dispatcher)
   {
      Object[] wanted = previous == null ? new Object[0] : previous;

      try {
         VSConditionDialogModel fresh = conditionService.getModel(runtimeId, assemblyName, user);

         if(fresh == null) {
            return "the assembly no longer has a condition dialog.";
         }

         fresh.setConditionList(wanted);
         conditionService.setModel(runtimeId, assemblyName, fresh, linkUri, user, dispatcher);
         reexecute(rvs, runtimeId, assemblyName, dispatcher, linkUri, user);
         VSConditionDialogModel after = conditionService.getModel(runtimeId, assemblyName, user);

         if(after == null || !ConditionVocabulary.describe(after.getConditionList())
            .equals(ConditionVocabulary.describe(wanted)))
         {
            return "the stored conditions still differ from the previous ones.";
         }

         return null;
      }
      catch(Exception e) {
         return "restoring failed with " + e + ".";
      }
   }

   /**
    * The cause of the failed-query fallback the assembly's table currently renders, or null when
    * it renders real data or the data cannot be read here (a script failure is reported as a
    * warning elsewhere and must not be taken for a failed query).
    */
   private static String failedQueryCause(ViewsheetSandbox box, String assemblyName) {
      try {
         return box.getData(assemblyName) instanceof XTable table ?
            WizVsService.findFailedQueryCause(table) : null;
      }
      catch(Exception e) {
         return null;
      }
   }

   private void checkExpressions(RuntimeViewsheet rvs, Object[] conditionList,
                                 List<String> warnings) throws Exception
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElse(null);

      if(box == null) {
         return;
      }

      for(Object item : conditionList) {
         if(!(item instanceof ConditionModel condition) || condition.getValues() == null) {
            continue;
         }

         for(ConditionValueModel value : condition.getValues()) {
            if(value == null || !(value.getValue() instanceof ExpressionValueModel expression)) {
               continue;
            }

            boolean sql = expression.getType() == ExpressionValueModel.SQL;
            String text = expression.getExpression();
            String error;

            try {
               error = checker.check(text, box);
            }
            catch(Exception e) {
               throw new IllegalArgumentException(
                  "The " + (sql ? "sql" : "js") + " expression '" + text + "' does not compile: " +
                  e.getMessage() + (sql ? SQL_NOTE : "") + " Nothing was changed.");
            }

            if(error == null) {
               continue;
            }

            if(sql) {
               throw new IllegalArgumentException(
                  "The sql expression '" + text + "' failed: " + error + SQL_NOTE +
                  " Nothing was changed.");
            }

            warnings.add("The expression '" + text + "' threw: " + error +
                         "; the table shows no rows until it evaluates.");
         }
      }
   }

   private static final String SQL_NOTE =
      " sql-language expressions are evaluated as JavaScript on viewsheet assemblies.";

   /** Clears every condition. Distinct from setting an empty list only in what it reads like. */
   public void clear(String sessionToken, Principal user, String assemblyName, String linkUri)
      throws Exception
   {
      set(sessionToken, user, assemblyName, List.of(), linkUri);
   }

   /**
    * The values a column actually holds, so a condition is not written against a guess.
    *
    * <p>{@code browseData} wants the column's {@code DataRefModel}, not its name, so the model is
    * read first and the name resolved against its {@code fields[]}. That also means an unknown
    * column fails here with the available list rather than returning an empty browse that reads
    * as "this column has no values".
    */
   public Object browseValues(String sessionToken, Principal user, String assemblyName,
                              String columnName) throws Exception
   {
      if(columnName == null || columnName.isBlank()) {
         throw new IllegalArgumentException(
            "browse_condition_values needs a 'column' — the column whose values to list.");
      }

      String runtimeId = sessions.resolve(sessionToken, user).getID();
      VSConditionDialogModel model = conditionService.getModel(runtimeId, assemblyName, user);
      DataRefModel field = model == null ? null : find(model.getFields(), columnName);

      if(field == null) {
         throw new IllegalArgumentException(
            "'" + columnName + "' is not a field of '" + assemblyName + "'. Available: " +
            (model == null ? "(none)" : String.join(", ", fieldNames(model.getFields()))) +
            ". Browsing an unknown column would return nothing, which reads as an empty column " +
            "rather than a wrong name.");
      }

      return conditionService.browseData(runtimeId, model.getTableName(), assemblyName, false,
                                        field, user);
   }

   private static DataRefModel find(DataRefModel[] fields, String columnName) {
      return ConditionVocabulary.find(fields, columnName);
   }

   /** The built-in date ranges usable with the date_in operator. */
   public Object dateRanges(String sessionToken, Principal user) throws Exception {
      return conditionService.getDateRanges(sessions.resolve(sessionToken, user).getID(), user);
   }

   public Map<String, Object> vocabulary() {
      return ConditionVocabulary.vocabulary();
   }

   private static List<String> fieldNames(DataRefModel[] fields) {
      return ConditionVocabulary.canonicalNames(fields);
   }

   private final ViewsheetSessionService sessions;
   private final VSConditionDialogService conditionService;
   private final VSRefreshService refreshService;
   private final ExpressionChecker checker;
}
