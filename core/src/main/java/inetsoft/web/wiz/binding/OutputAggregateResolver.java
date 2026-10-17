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

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Resolves a gauge/text/image output {@code aggregate} the way the property dialog's aggregate
 * dropdown does: any {@link AggregateFormula}, or a {@code "$(...)"}/{@code "=..."} dynamic
 * value. Anything else was stored verbatim (bug #78756).
 */
public final class OutputAggregateResolver {
   private OutputAggregateResolver() {
   }

   /**
    * Returns the canonical formula name ({@code AggregateFormula.getFormulaName()}), never the
    * caller's raw text, since {@code AggregateFormula.getFormula} also resolves text such as
    * {@code "Sum(Bar)"}. A dynamic value is passed through (trimmed), except a bare {@code "="}
    * or a blank {@code "$()"}.
    */
   public static String resolve(String assemblyName, String aggregate) {
      String trimmed = aggregate.trim();

      if(trimmed.startsWith("=")) {
         if(trimmed.substring(1).isBlank()) {
            throw new IllegalArgumentException(
               "'aggregate' is \"" + trimmed + "\" with no expression after it. Give a script "
               + "such as \"=field['Fn']\", an aggregate (" + available() + "), or omit it.");
         }

         return trimmed;
      }

      if(trimmed.startsWith("$(") && trimmed.endsWith(")")) {
         if(trimmed.substring(2, trimmed.length() - 1).isBlank()) {
            throw new IllegalArgumentException(
               "'aggregate' \"" + trimmed + "\" needs a component name between the "
               + "parentheses, e.g. \"$(Combo1)\".");
         }

         return trimmed;
      }

      AggregateFormula resolved = AggregateFormula.getFormula(trimmed);

      if(resolved == null) {
         throw new IllegalArgumentException(
            "'" + aggregate + "' is not an aggregate '" + assemblyName + "' can use. Available: "
            + available() + ".");
      }

      return resolved.getFormulaName();
   }

   private static String available() {
      return "none, " + Arrays.stream(AggregateFormula.getFormulas())
         .map(AggregateFormula::getFormulaName).collect(Collectors.joining(", "));
   }
}
