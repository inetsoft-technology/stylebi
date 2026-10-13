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
package inetsoft.web.wiz.binding;

import inetsoft.web.composer.model.vs.OutputColumnRefModel;
import inetsoft.web.composer.vs.dialog.DataOutputService;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a selection list/tree {@code measure} the way both write paths
 * ({@code set_selection_source} and {@code set_assembly_properties}) must: against the same list
 * the property dialog's measure dropdown offers ({@code DataOutputService.
 * getOutputSelectionColumns}: the columns the table shares with every additional table).
 */
public final class SelectionMeasureResolver {
   private SelectionMeasureResolver() {
   }

   /**
    * Returns the canonical measure. A {@code "$(...)"}/{@code "=..."} value is passed through
    * (trimmed), exactly as the dialog passes it, except a bare {@code "="} or a blank
    * {@code "$()"}, which are empty references and refused. A bare {@code "$Foo"} is a literal
    * column name, as in {@code VSUtil.isVariableValue}/{@code isScriptValue}.
    */
   public static String resolve(DataOutputService dataOutputService, String runtimeId,
                                Principal user, String assemblyName, String table,
                                List<String> additionalTables, String measure)
      throws Exception
   {
      String trimmed = measure.trim();

      if(trimmed.startsWith("=")) {
         if(trimmed.substring(1).isBlank()) {
            throw new IllegalArgumentException(
               "'measure' is \"" + trimmed + "\" with no expression after it. Give a script "
               + "such as \"=field['Total']\", a column name, or omit it.");
         }

         return trimmed;
      }

      if(trimmed.startsWith("$(") && trimmed.endsWith(")")) {
         if(trimmed.substring(2, trimmed.length() - 1).isBlank()) {
            throw new IllegalArgumentException(
               "'measure' \"" + trimmed + "\" needs a component name between the parentheses, "
               + "e.g. \"$(Combo1)\".");
         }

         return trimmed;
      }

      if(table == null || table.isBlank()) {
         throw new IllegalArgumentException(
            "'measure' '" + measure + "' cannot be checked: '" + assemblyName + "' has no bound "
            + "table. Bind a source first (set_selection_source).");
      }

      List<String> measureTables = measureTables(table, additionalTables);
      OutputColumnRefModel[] candidates =
         dataOutputService.getOutputSelectionColumns(runtimeId, measureTables, user);
      List<String> available = new ArrayList<>();

      for(OutputColumnRefModel candidate : candidates == null ? new OutputColumnRefModel[0]
         : candidates)
      {
         // The dialog's leading "None" entry has a null name.
         if(candidate == null || candidate.getName() == null) {
            continue;
         }

         if(candidate.getName().equalsIgnoreCase(trimmed)) {
            return candidate.getName();
         }

         available.add(candidate.getName());
      }

      throw new IllegalArgumentException(
         "'" + measure + "' is not a measure '" + assemblyName + "' can use from " +
         measureTables + ". Available: " + String.join(", ", available) + ".");
   }

   /**
    * The measure dropdown's candidate matching {@code literal} (case-insensitive) among the
    * columns the table shares with every additional table, or null when it is not offered.
    * Unlike {@link #resolve}, never refuses.
    */
   public static String offered(DataOutputService dataOutputService, String runtimeId,
                                Principal user, String table, List<String> additionalTables,
                                String literal)
      throws Exception
   {
      OutputColumnRefModel[] candidates = dataOutputService.getOutputSelectionColumns(
         runtimeId, measureTables(table, additionalTables), user);
      String trimmed = literal.trim();

      for(OutputColumnRefModel candidate : candidates == null ? new OutputColumnRefModel[0]
         : candidates)
      {
         if(candidate != null && candidate.getName() != null &&
            candidate.getName().equalsIgnoreCase(trimmed))
         {
            return candidate.getName();
         }
      }

      return null;
   }

   private static List<String> measureTables(String table, List<String> additionalTables) {
      List<String> measureTables = new ArrayList<>();
      measureTables.add(table);

      if(additionalTables != null) {
         measureTables.addAll(additionalTables);
      }

      return measureTables;
   }
}
