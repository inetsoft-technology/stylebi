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
package inetsoft.web.wiz.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Derives an enforceable choice list from a REST endpoint parameter's placeholder.
 *
 * <p>The endpoint catalogues write a parameter as {@code {Name?:placeholder}} and
 * {@code RestParameter.placeholder} is a free-text hint field (its javadoc says "format"). A
 * placeholder such as {@code Cash|Accrual} is in practice the closed list of values the vendor
 * API accepts, but the same field also carries open lists ({@code en|es|...}), alternative date
 * formats ({@code yyyy-MM-dd|yyyy-MM-ddTHH:mm:ssZ}), bracket groups
 * ({@code utm-[campaigns|contents]}) and plain hints. Only a CLOSED list is enforced; everything
 * else stays a hint, so a value that builds today is never refused by this class.</p>
 *
 * <p>The rule is mirrored by the composer plugin
 * ({@code plugin/shared/core/src/tabular/restParameterChoices.ts}) and both are pinned by the same
 * golden fixture ({@code rest-parameter-choices-golden.json}); change them together.</p>
 */
final class RestParameterChoices {
   private RestParameterChoices() {
   }

   /** Longest choice list spelled out in a message before the rest is summarised. */
   static final int MAX_LISTED = 30;

   /** A plain enum token: no brackets, commas, quotes, colons or non-ASCII (garbled) bytes. */
   private static final Pattern PLAIN_TOKEN = Pattern.compile("^[A-Za-z0-9_][A-Za-z0-9_\\-. /]*$");

   /** A whole part made only of date-format letters and separators. */
   private static final Pattern DATE_CHARS = Pattern.compile("^[yYMdDHhmsSTZ\\-./: ]+$");

   /** A run of the same format letter, e.g. {@code yy}, {@code MM}, {@code dd}. */
   private static final Pattern DATE_RUN = Pattern.compile("([yYMdDHhmsSTZ])\\1");

   /**
    * The declared choices of a CLOSED placeholder list, trimmed and in catalogue order, or an
    * empty list when the placeholder is not a closed list (then it is a hint only).
    */
   static List<String> closedChoices(String placeholder) {
      if(placeholder == null || placeholder.indexOf('|') < 0) {
         return List.of();
      }

      String[] raw = placeholder.split("\\|", -1);
      List<String> choices = new ArrayList<>(raw.length);

      for(String part : raw) {
         String t = part.trim();

         if(t.isEmpty() || t.endsWith("...") || !PLAIN_TOKEN.matcher(t).matches() ||
            isDateFormat(t))
         {
            return List.of();
         }

         choices.add(t);
      }

      return choices.size() < 2 ? List.of() : List.copyOf(choices);
   }

   /**
    * Whether a WHOLE part is a date format. Anchored on purpose: a substring test for
    * {@code mm}/{@code ss}/{@code dd} wrongly rejects real enum values such as
    * {@code committed}, {@code in-progress} and {@code description}.
    */
   static boolean isDateFormat(String part) {
      return DATE_CHARS.matcher(part).matches() && DATE_RUN.matcher(part).find();
   }

   /**
    * Resolve a supplied value against the placeholder.
    *
    * <p>A blank value, a variable reference ({@code $(name)}) or a placeholder that is not a
    * closed list is accepted unchanged. Otherwise the value is split on {@code ,} (several
    * catalogue parameters take a comma-joined set of the listed values), every element is trimmed
    * and must match a declared choice case-insensitively, and the DECLARED spelling is written
    * back ({@code cash} becomes {@code Cash}). When the list itself has case-colliding choices
    * only an exact match counts.</p>
    *
    * @return the value to use, or {@code null} when some element matches no declared choice.
    */
   static String resolve(String placeholder, String value) {
      if(value == null || value.isBlank() || value.contains("$(")) {
         return value;
      }

      List<String> choices = closedChoices(placeholder);

      if(choices.isEmpty()) {
         return value;
      }

      boolean caseCollision = false;

      for(int i = 0; i < choices.size() && !caseCollision; i++) {
         for(int j = i + 1; j < choices.size(); j++) {
            if(choices.get(i).equalsIgnoreCase(choices.get(j))) {
               caseCollision = true;
               break;
            }
         }
      }

      List<String> out = new ArrayList<>();

      for(String element : value.split(",", -1)) {
         String e = element.trim();
         String match = null;

         for(String choice : choices) {
            if(caseCollision ? choice.equals(e) : choice.equalsIgnoreCase(e)) {
               match = choice;
               break;
            }
         }

         if(match == null) {
            return null;
         }

         out.add(match);
      }

      return String.join(",", out);
   }

   /** The choices spelled for a single-line message, capped at {@link #MAX_LISTED}. */
   static String describeChoices(List<String> choices) {
      if(choices.size() <= MAX_LISTED) {
         return String.join(" | ", choices);
      }

      return String.join(" | ", choices.subList(0, MAX_LISTED)) + " | (+" +
         (choices.size() - MAX_LISTED) + " more)";
   }

   /**
    * One parameter's contribution to the described contract: {@code Name (required) [one of: A | B]}
    * for a closed list, {@code Name [format: hint]} for any other placeholder.
    */
   static String describeParameter(String name, boolean required, String placeholder) {
      StringBuilder s = new StringBuilder(name).append(required ? " (required)" : "");
      List<String> choices = closedChoices(placeholder);

      if(!choices.isEmpty()) {
         s.append(" [one of: ").append(describeChoices(choices)).append(']');
      }
      else if(placeholder != null && !placeholder.isBlank()) {
         s.append(" [format: ").append(oneLine(placeholder, 60)).append(']');
      }

      return s.toString();
   }

   /** Collapse whitespace (a thrown message must be single-line) and cap the length. */
   static String oneLine(String text, int max) {
      String t = text.trim().replaceAll("\\s+", " ");
      return t.length() > max ? t.substring(0, max) + "..." : t;
   }
}
