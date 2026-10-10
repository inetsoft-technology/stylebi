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
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.condition.ConditionModel;
import inetsoft.web.composer.model.condition.ConditionValueModel;
import inetsoft.web.composer.model.condition.ExpressionValueModel;
import inetsoft.web.composer.model.vs.VSConditionDialogModel;
import inetsoft.web.composer.vs.dialog.VSConditionDialogService;
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
                                   VSConditionDialogService conditionService)
   {
      this(sessions, conditionService, VSAQuery::checkConditionExpression);
   }

   AssemblyConditionService(ViewsheetSessionService sessions,
                            VSConditionDialogService conditionService,
                            ExpressionChecker checker)
   {
      this.sessions = sessions;
      this.conditionService = conditionService;
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
         ConditionVocabulary.describe(model.getConditionList());
      out.put("tableName", model.getTableName());
      out.put("conditions", conditions);
      out.put("conditionCount", conditions.size());
      out.put("fields", fieldNames(model.getFields()));
      return out;
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
         VSConditionDialogModel model = conditionService.getModel(runtimeId, assemblyName, user);

         if(model == null) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' has no condition dialog — it is not an assembly that " +
               "can be filtered. Charts, tables, crosstabs and selection assemblies can.");
         }

         Object[] conditionList = ConditionVocabulary.toConditionList(clauses, model.getFields());
         checkExpressions(rvs, conditionList, warnings);
         Object[] previous = model.getConditionList();
         model.setConditionList(conditionList);
         applied[0] = clauses == null ? 0 : clauses.size();

         try {
            conditionService.setModel(runtimeId, assemblyName, model, linkUri, user, dispatcher);
         }
         catch(Exception e) {
            // the refresh failed after the new list was committed; put the old one back rather
            // than leave a condition the caller was told failed
            try {
               model.setConditionList(previous);
               conditionService.setModel(runtimeId, assemblyName, model, linkUri, user,
                                         dispatcher);
            }
            catch(Exception restoreFailure) {
               e.addSuppressed(restoreFailure);
            }

            throw e;
         }
      });

      warnings.addAll(sessionWarnings == null ? List.of() : sessionWarnings);
      return new SetResult(applied[0], warnings);
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
      if(fields != null) {
         for(DataRefModel field : fields) {
            if(field != null && field.getName() != null &&
               field.getName().equalsIgnoreCase(columnName))
            {
               return field;
            }
         }
      }

      return null;
   }

   /** The built-in date ranges usable with the date_in operator. */
   public Object dateRanges(String sessionToken, Principal user) throws Exception {
      return conditionService.getDateRanges(sessions.resolve(sessionToken, user).getID(), user);
   }

   public Map<String, Object> vocabulary() {
      return ConditionVocabulary.vocabulary();
   }

   private static List<String> fieldNames(DataRefModel[] fields) {
      List<String> names = new ArrayList<>();

      if(fields != null) {
         for(DataRefModel field : fields) {
            if(field != null && field.getName() != null) {
               names.add(field.getName());
            }
         }
      }

      return names;
   }

   private final ViewsheetSessionService sessions;
   private final VSConditionDialogService conditionService;
   private final ExpressionChecker checker;
}
