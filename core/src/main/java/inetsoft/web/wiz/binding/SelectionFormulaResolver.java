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

import inetsoft.uql.asset.AggregateFormula;

/**
 * Resolves a selection list/tree measure {@code formula} the way the property dialog's
 * aggregate dropdown does: Count, Sum, Max or Min ({@code none} is what the service itself writes
 * when there is no measure). Anything else was stored verbatim, and the runtime then silently
 * rendered no measure values for it.
 */
public final class SelectionFormulaResolver {
   private SelectionFormulaResolver() {
   }

   /**
    * Returns the canonical formula name ({@code AggregateFormula.getFormulaName()}), never the
    * caller's raw text, since {@code AggregateFormula.getFormula} also resolves text such as
    * {@code "Sum(Bar)"}. A {@code "$(...)"}/{@code "=..."} value is passed through (trimmed), as
    * the dialog does, except a bare {@code "="} or a blank {@code "$()"}.
    * Average and DistinctCount are refused: the dialog never offers them.
    */
   public static String resolve(String assemblyName, String formula) {
      String trimmed = formula.trim();

      if(trimmed.startsWith("=")) {
         if(trimmed.substring(1).isBlank()) {
            throw new IllegalArgumentException(
               "'formula' is \"" + trimmed + "\" with no expression after it. Give a script "
               + "such as \"=field['Fn']\", an aggregate (" + AVAILABLE + "), or omit it.");
         }

         return trimmed;
      }

      if(trimmed.startsWith("$(") && trimmed.endsWith(")")) {
         if(trimmed.substring(2, trimmed.length() - 1).isBlank()) {
            throw new IllegalArgumentException(
               "'formula' \"" + trimmed + "\" needs a component name between the parentheses, "
               + "e.g. \"$(Combo1)\".");
         }

         return trimmed;
      }

      AggregateFormula resolved = AggregateFormula.getFormula(trimmed);

      if(resolved == AggregateFormula.COUNT_ALL || resolved == AggregateFormula.SUM ||
         resolved == AggregateFormula.MAX || resolved == AggregateFormula.MIN ||
         resolved == AggregateFormula.NONE)
      {
         return resolved.getFormulaName();
      }

      throw new IllegalArgumentException(
         "'" + formula + "' is not an aggregate '" + assemblyName + "' can use. Available: "
         + AVAILABLE + ".");
   }

   private static final String AVAILABLE = "Count, Sum, Max, Min";
}
