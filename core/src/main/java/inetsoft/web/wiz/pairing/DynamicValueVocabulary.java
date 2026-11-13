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
package inetsoft.web.wiz.pairing;

import inetsoft.web.wiz.viewsheet.PropertyAliases;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whether a well-formed {@link DynamicValueName} is in the vocabulary of one CONCRETE assembly
 * type, checked at mint time (Bug #80459).
 *
 * <p>A vocabulary check, not a state check: nothing is written by a {@code dynamicValue} draft, so
 * it proves only that the host's literal is spelled the way the plugin's own tool would accept it
 * for this type. A typo in one of the many browser hosts therefore fails at the one chokepoint
 * every host passes through, with the near-match list {@link PropertyAliases#resolve} builds,
 * instead of as a dead button. Static and pure, like {@link DynamicValueName}.
 */
public final class DynamicValueVocabulary {
   private DynamicValueVocabulary() {
   }

   /**
    * Why {@code name} is not valid for the assembly type {@code assemblyTypeKey} (a
    * {@link PropertyAliases#typeKeyOf} key), or {@code null} when it is.
    */
   public static String problem(String assemblyTypeKey, DynamicValueName name) {
      if(name == null) {
         return "name is required";
      }

      if(assemblyTypeKey == null || assemblyTypeKey.isBlank()) {
         return "the type of the assembly is unknown, so '" + name.wire() + "' cannot be checked";
      }

      if(DynamicValueName.PROP.equals(name.namespace())) {
         return propProblem(assemblyTypeKey, name.key());
      }

      Set<String> types = typesOf(name);

      if(!types.contains(assemblyTypeKey)) {
         return "'" + name.wire() + "' is not available on a " + assemblyTypeKey + "; it applies " +
            "to " + String.join(", ", new TreeSet<>(types)) + " only";
      }

      return null;
   }

   private static Set<String> typesOf(DynamicValueName name) {
      return switch(name.namespace()) {
         case DynamicValueName.SOURCE -> name.key().equals("measure")
            ? Set.of("selectionlist", "selectiontree") : Set.of("selectiontree");
         case DynamicValueName.BINDING -> Set.of("chart", "crosstab", "table");
         case DynamicValueName.TABLE_OPTION -> Set.of("crosstab");
         case DynamicValueName.DATE_COMPARISON -> Set.of("chart", "crosstab");
         case DynamicValueName.CHART_TARGET, DynamicValueName.CHART_REGION -> Set.of("chart");
         default -> Set.of();
      };
   }

   private static final Pattern LIST_ELEMENT = Pattern.compile("(rangeValues)\\[[0-9]+\\]");

   private static String propProblem(String type, String key) {
      if(!PropertyAliases.covers(type)) {
         return "'" + type + "' is a type whose properties are not covered yet. Covered types: " +
            String.join(", ", new TreeSet<>(PropertyAliases.coveredTypes())) + ".";
      }

      // Only a list alias has elements; an element is checked as its list.
      Matcher element = LIST_ELEMENT.matcher(key);
      boolean listElement = element.matches();
      String lookup = listElement ? element.group(1) : key;

      try {
         // The write path of set_assembly_properties, minus the write: the same alias lookup,
         // the same proof that a raw dotted key exists on the type's dialog model, and the same
         // read-only / dead-field refusals, so a name the tool would refuse never mints.
         PropertyAliases.resolveForWrite(type, lookup, PropertyAliases.forType(type).modelClass(),
                                         null);
         return null;
      }
      catch(IllegalArgumentException ex) {
         return listElement ? "'" + key + "' needs the list alias '" + lookup + "': " +
            ex.getMessage() : ex.getMessage();
      }
   }
}
