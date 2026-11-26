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

package inetsoft.uql.rest.datasource;

import inetsoft.uql.rest.json.SuffixTemplate;
import inetsoft.uql.rest.json.TemplateComponent;

import java.util.*;

/**
 * The rules an endpoint definition must meet. They work on plain strings, so they can be run
 * against a made-up template as well as a real one; {@link EndpointRulesTest} pins what each
 * rule accepts and rejects, and {@link EndpointDefinitionsTest} applies them to every endpoint.
 */
final class EndpointRules {
   private EndpointRules() {
   }

   /**
    * Returns what is wrong with an endpoint name, or null.
    */
   static String nameProblem(String name) {
      if(name == null || name.isBlank()) {
         return "blank name";
      }

      if(!name.equals(name.strip()) || name.contains("\u00a0")) {
         return "non-breaking or surrounding space in the name";
      }

      return null;
   }

   /**
    * Returns what is wrong with a built suffix (the path and query string once every variable
    * has a value), or null.
    */
   static String malformed(String suffix) {
      if(suffix == null || suffix.isEmpty()) {
         return "empty";
      }

      if(suffix.matches("(?s).*[\\s\u00a0].*")) {
         return "whitespace in " + suffix.replaceAll("\\s", "<ws>");
      }

      int index = suffix.indexOf('?');
      String path = index < 0 ? suffix : suffix.substring(0, index);

      if(path.contains("&") || path.contains("//") || path.contains("{") || path.contains("}")) {
         return "bad path in " + suffix;
      }

      if(index >= 0) {
         String query = suffix.substring(index + 1);

         if(query.contains("?")) {
            return "second '?' in " + suffix;
         }

         if(query.isEmpty()) {
            return "empty query string in " + suffix;
         }

         for(String pair : query.split("&", -1)) {
            if(!pair.matches("[^=&?{}]+=[^=&?{}]*")) {
               return "bad parameter \"" + pair + "\" in " + suffix;
            }
         }
      }

      return null;
   }

   /**
    * Fills in every variable of a suffix template with a simple value and returns what is wrong
    * with the result, or null. A template that cannot be built is reported as well.
    */
   static String buildProblem(String template) {
      try {
         SuffixTemplate parsed = new SuffixTemplate(template);

         for(TemplateComponent variable : variables(parsed)) {
            parsed.setVariable(variable.getValue(), "1");
         }

         return malformed(parsed.build());
      }
      catch(Exception e) {
         return e.toString();
      }
   }

   /**
    * Leaves out each variable of a suffix template in turn. A required variable must make the
    * build fail and an optional one must be omitted. A name that appears more than once is
    * required if any occurrence is.
    */
   static List<String> requiredParameterProblems(String suffix) {
      List<String> problems = new ArrayList<>();
      Map<String, Boolean> requiredByName = new LinkedHashMap<>();

      for(TemplateComponent variable : variables(new SuffixTemplate(suffix))) {
         requiredByName.merge(variable.getValue(), variable.isRequired(), Boolean::logicalOr);
      }

      for(Map.Entry<String, Boolean> variable : requiredByName.entrySet()) {
         SuffixTemplate template = new SuffixTemplate(suffix);

         for(String other : requiredByName.keySet()) {
            if(!other.equals(variable.getKey())) {
               template.setVariable(other, "1");
            }
         }

         try {
            String built = template.build();

            if(variable.getValue()) {
               problems.add("required \"" + variable.getKey() +
                            "\" missing but the request was built: " + built);
            }
         }
         catch(IllegalStateException ex) {
            if(!variable.getValue()) {
               problems.add("optional \"" + variable.getKey() +
                            "\" missing but the request was rejected: " + ex.getMessage());
            }
         }
      }

      return problems;
   }

   /**
    * Returns the lookups that point to an endpoint that does not exist.
    */
   static List<String> unknownLookups(Collection<String> lookups, Set<String> endpointNames) {
      List<String> unknown = new ArrayList<>();

      for(String lookup : lookups) {
         if(!endpointNames.contains(lookup)) {
            unknown.add(lookup);
         }
      }

      return unknown;
   }

   private static List<TemplateComponent> variables(SuffixTemplate template) {
      List<TemplateComponent> variables = new ArrayList<>();

      for(TemplateComponent part : template.getPath()) {
         if(part.isVariable()) {
            variables.add(part);
         }
      }

      for(TemplateComponent part : template.getQuery().values()) {
         if(part.isVariable()) {
            variables.add(part);
         }
      }

      return variables;
   }
}
