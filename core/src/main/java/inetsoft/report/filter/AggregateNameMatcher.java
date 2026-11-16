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
package inetsoft.report.filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Locates aggregate names (e.g. <code>field['Sum([a])']</code>) in a calc field expression.
 * <p>
 * The match relation is the one used by <code>VSUtil.addUsedAggregateRef</code> to decide that
 * an aggregate is used by an expression: a name matches a piece of text when the upper-cased
 * strings are equal ({@link String#toUpperCase(Locale)} with the root locale, so a sharp-s matches "SS"). Unlike a plain
 * upper-case search, the spans are reported in the coordinates of the original expression, so
 * a name whose upper-case form has a different length cannot shift the result.
 * <p>
 * The match is done in a single non-overlapping pass. An occurrence written exactly as the name
 * wins over a case-insensitive one, so two names that differ only by case (<code>a</code> and
 * <code>A</code>) each keep their own occurrences. Case-insensitive occurrences are only
 * assigned to the text left over, longest name first. A name that appears twice in the list is
 * matched by its first index only (the second one finds no free text).
 */
public final class AggregateNameMatcher {
   private AggregateNameMatcher() {
   }

   /**
    * A located name: <code>[start, end)</code> of the original expression belongs to the name
    * at index <code>name</code> of the searched list.
    */
   public static final class Span {
      public Span(int start, int end, int name) {
         this.start = start;
         this.end = end;
         this.name = name;
      }

      public final int start;
      public final int end;
      public final int name;
   }

   /**
    * Find the occurrences of the names in the expression.
    *
    * @param expression the expression.
    * @param names      the names to look for; null and empty names are never matched.
    *
    * @return the non-overlapping spans sorted by start.
    */
   public static List<Span> find(String expression, List<String> names) {
      List<Span> spans = new ArrayList<>();

      if(expression == null || names == null || names.isEmpty()) {
         return spans;
      }

      Integer[] order = new Integer[names.size()];

      for(int i = 0; i < order.length; i++) {
         order[i] = i;
      }

      // longest first, so a name never claims part of a longer name
      Arrays.sort(order, Comparator.comparingInt(
         (Integer i) -> names.get(i) == null ? 0 : names.get(i).length()).reversed());
      boolean[] used = new boolean[expression.length()];

      // 1. exact spelling
      for(int i : order) {
         String name = names.get(i);

         if(name == null || name.isEmpty()) {
            continue;
         }

         int idx = expression.indexOf(name);

         while(idx >= 0) {
            int end = idx + name.length();

            if(free(used, idx, end)) {
               mark(used, idx, end);
               spans.add(new Span(idx, end, i));
               idx = expression.indexOf(name, end);
            }
            else {
               idx = expression.indexOf(name, idx + 1);
            }
         }
      }

      // 2. same relation as VSUtil.addUsedAggregateRef: upper-case strings are equal
      StringBuilder upper = new StringBuilder(expression.length() + 8);
      // upper-case char index -> index of the original char it came from
      int[] origin = new int[expression.length() * 3 + 1];

      for(int i = 0; i < expression.length();) {
         int cp = expression.codePointAt(i);
         int len = Character.charCount(cp);
         String up = expression.substring(i, i + len).toUpperCase(Locale.ROOT);

         for(int k = 0; k < up.length(); k++) {
            if(upper.length() >= origin.length - 1) {
               origin = Arrays.copyOf(origin, origin.length * 2);
            }

            origin[upper.length()] = i;
            upper.append(up.charAt(k));
         }

         i += len;
      }

      origin[upper.length()] = expression.length();
      String uexp = upper.toString();

      for(int i : order) {
         String name = names.get(i);

         if(name == null || name.isEmpty()) {
            continue;
         }

         String uname = name.toUpperCase(Locale.ROOT);
         int idx = uexp.indexOf(uname);

         while(idx >= 0) {
            int uend = idx + uname.length();
            int start = origin[idx];
            int end = origin[uend];
            // the match must begin and end on an original character boundary
            boolean boundary = (idx == 0 || origin[idx - 1] != start) &&
               (uend == uexp.length() || origin[uend - 1] != end);

            if(boundary && free(used, start, end)) {
               mark(used, start, end);
               spans.add(new Span(start, end, i));
               idx = uexp.indexOf(uname, uend);
            }
            else {
               idx = uexp.indexOf(uname, idx + 1);
            }
         }
      }

      spans.sort(Comparator.comparingInt((Span s) -> s.start));
      return spans;
   }

   /**
    * Replace the occurrences of the names in the expression, using {@link #find}.
    *
    * @param replacements the replacement of each name; a null entry leaves that name as is.
    */
   public static String replace(String expression, List<String> names,
                                List<String> replacements)
   {
      StringBuilder result = new StringBuilder();
      int pos = 0;

      for(Span span : find(expression, names)) {
         String replacement = span.name < replacements.size() ? replacements.get(span.name) : null;

         if(replacement != null) {
            result.append(expression, pos, span.start);
            result.append(replacement);
            pos = span.end;
         }
      }

      if(expression != null) {
         result.append(expression, pos, expression.length());
      }

      return expression == null ? null : result.toString();
   }

   private static boolean free(boolean[] used, int start, int end) {
      for(int i = start; i < end; i++) {
         if(used[i]) {
            return false;
         }
      }

      return true;
   }

   private static void mark(boolean[] used, int start, int end) {
      for(int i = start; i < end; i++) {
         used[i] = true;
      }
   }
}
