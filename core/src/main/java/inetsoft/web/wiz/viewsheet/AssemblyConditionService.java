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

import inetsoft.web.binding.drm.DataRefModel;
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
   @Autowired
   public AssemblyConditionService(ViewsheetSessionService sessions,
                                   VSConditionDialogService conditionService)
   {
      this.sessions = sessions;
      this.conditionService = conditionService;
   }

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
      int[] applied = new int[1];

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         VSConditionDialogModel model = conditionService.getModel(runtimeId, assemblyName, user);

         if(model == null) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' has no condition dialog — it is not an assembly that " +
               "can be filtered. Charts, tables, crosstabs and selection assemblies can.");
         }

         Object[] conditionList = ConditionVocabulary.toConditionList(clauses, model.getFields());
         model.setConditionList(conditionList);
         applied[0] = clauses == null ? 0 : clauses.size();
         conditionService.setModel(runtimeId, assemblyName, model, linkUri, user, dispatcher);
      });

      return applied[0];
   }

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
}
