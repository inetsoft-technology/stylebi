/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.composer.ws.dialog;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static check of the field['name'] references of a worksheet calculated column expression.
 * Detects references to missing columns, self references and cycles between calculated
 * columns, so the expression dialog can warn when the expression is saved.
 */
final class ExpressionReferenceChecker {
   enum Kind { MISSING, SELF, CYCLE }

   record Problem(Kind kind, String first, String second) {
   }

   private static final Pattern FIELD_REF =
      Pattern.compile("\\bfield\\s*\\[\\s*(['\"])(.*?)\\1\\s*\\]");

   private ExpressionReferenceChecker() {
   }

   /**
    * @param ownName     the name of the column being saved.
    * @param expression  the new script expression of the column.
    * @param columnNames the names of all columns of the table (not including ownName
    *                    necessarily).
    * @param scripts     the script expressions of the other calculated columns, by name.
    *
    * @return the first problem found, or null.
    */
   static Problem check(String ownName, String expression, Set<String> columnNames,
                        Map<String, String> scripts)
   {
      Set<String> refs = references(expression);

      if(refs.contains(ownName)) {
         return new Problem(Kind.SELF, ownName, null);
      }

      for(String ref : refs) {
         if(!columnNames.contains(ref) && !scripts.containsKey(ref)) {
            return new Problem(Kind.MISSING, ref, null);
         }
      }

      for(String ref : refs) {
         if(reaches(ref, ownName, scripts, new HashSet<>())) {
            return new Problem(Kind.CYCLE, ownName, ref);
         }
      }

      return null;
   }

   private static boolean reaches(String from, String target, Map<String, String> scripts,
                                  Set<String> visited)
   {
      if(!visited.add(from)) {
         return false;
      }

      String script = scripts.get(from);

      if(script == null) {
         return false;
      }

      for(String ref : references(script)) {
         if(ref.equals(target) || reaches(ref, target, scripts, visited)) {
            return true;
         }
      }

      return false;
   }

   static Set<String> references(String script) {
      Set<String> result = new LinkedHashSet<>();

      if(script == null) {
         return result;
      }

      Matcher m = FIELD_REF.matcher(script);

      while(m.find()) {
         result.add(m.group(2));
      }

      return result;
   }
}
