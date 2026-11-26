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

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
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
 * else stays a hint for {@link #resolve}.</p>
 *
 * <p>A placeholder whose every part is a year-first ISO date format ({@code YYYY-MM-DD},
 * {@code yyyy-MM-ddTHH:mm:ssZ}, ...) declares what the vendor parses, and a vendor typically
 * answers a value it cannot parse with the UNFILTERED result rather than an error. That is checked
 * by {@link #resolveDate}, which {@link #resolveValue} runs when the placeholder is not a closed
 * list; a placeholder it cannot classify with confidence ({@code dd-mm-YYYY}, {@code yyyy}, a
 * non-date alternative) stays a hint.</p>
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
    * One date-format part, lower-cased and unquoted: a year-first ISO date with an optional time
    * ({@code yyyy-mm-dd}, {@code yyyy-mm-ddthh:mm:ssz}, {@code yyyy-mm-dd hh:mm}, fractional
    * seconds as {@code .sss}).
    */
   private static final Pattern DATE_LAYOUT = Pattern.compile(
      "^y{4}-m{2}-d{2}(?:[t ]h{2}:m{2}(?::s{2}(?:\\.s{1,3})?)?z?)?$");

   private static final Pattern ISO_DATE = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");

   private static final Pattern ISO_DATE_TIME = Pattern.compile(
      "^(\\d{4})-(\\d{2})-(\\d{2})[T ](\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.\\d{1,9})?)?" +
      "(?:Z|[+-]\\d{2}(?::?\\d{2})?)?$");

   /** A year-first date with {@code /} or {@code .} separators: unambiguous, so it is normalized. */
   private static final Pattern YEAR_FIRST =
      Pattern.compile("^(\\d{4})[/.](\\d{2})[/.](\\d{2})$");

   private static final Pattern COMPACT_DATE = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})$");

   private static final Pattern EPOCH = Pattern.compile("^\\d{10,13}(?:\\.\\d+)?$");

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
    * The trimmed parts of a placeholder that is a year-first ISO date layout in EVERY part, or an
    * empty list when any part is something else (then the placeholder stays a hint).
    */
   static List<String> dateFormats(String placeholder) {
      if(placeholder == null || placeholder.isBlank()) {
         return List.of();
      }

      List<String> parts = new ArrayList<>();

      for(String raw : placeholder.split("\\|", -1)) {
         String t = raw.trim();
         String layout = t.toLowerCase().replace("'", "").replace("\u2019", "");

         if(!DATE_LAYOUT.matcher(layout).matches()) {
            return List.of();
         }

         parts.add(t);
      }

      return List.copyOf(parts);
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

   /**
    * Resolve a supplied value against the placeholder: the closed-list rule of {@link #resolve}
    * when the placeholder is a closed list, otherwise the date rule of {@link #resolveDate}.
    *
    * @return the value to use, or {@code null} when the value is refused.
    */
   static String resolveValue(String placeholder, String value) {
      if(value == null || value.isBlank() || value.contains("$(")) {
         return value;
      }

      return closedChoices(placeholder).isEmpty()
         ? resolveDate(placeholder, value) : resolve(placeholder, value);
   }

   /**
    * Check a supplied value against a date-format placeholder.
    *
    * <p>A value is kept when it is a real calendar date ({@code 2020-01-01}) or, when the
    * placeholder declares a time, a date-time in the ISO layout or a Unix epoch of 10-13 digits.
    * A year-first date written with {@code /} or {@code .} (and {@code yyyyMMdd} for a date-only
    * placeholder) is rewritten to {@code yyyy-MM-dd}. Anything else, including a day-first date
    * that cannot be told from a month-first one, is refused. A placeholder that is not a date
    * format, a blank value or a variable reference is accepted unchanged.</p>
    *
    * @return the value to use, or {@code null} when the value is refused.
    */
   static String resolveDate(String placeholder, String value) {
      if(value == null || value.isBlank() || value.contains("$(")) {
         return value;
      }

      List<String> formats = dateFormats(placeholder);

      if(formats.isEmpty()) {
         return value;
      }

      boolean withTime = formats.stream().anyMatch(f -> f.length() > 10);
      String v = value.trim();
      Matcher m = ISO_DATE.matcher(v);

      if(m.matches()) {
         return validDate(m) ? v : null;
      }

      if(withTime) {
         m = ISO_DATE_TIME.matcher(v);

         if(m.matches()) {
            return validDate(m) && validTime(m) ? v : null;
         }

         if(EPOCH.matcher(v).matches()) {
            return v;
         }
      }

      m = YEAR_FIRST.matcher(v);

      if(!m.matches() && !withTime) {
         m = COMPACT_DATE.matcher(v);
      }

      return m.matches() && validDate(m)
         ? m.group(1) + "-" + m.group(2) + "-" + m.group(3) : null;
   }

   private static boolean validDate(Matcher m) {
      try {
         LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                      Integer.parseInt(m.group(3)));
         return true;
      }
      catch(DateTimeException e) {
         return false;
      }
   }

   private static boolean validTime(Matcher m) {
      try {
         LocalTime.of(Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)),
                      m.group(6) == null ? 0 : Integer.parseInt(m.group(6)));
         return true;
      }
      catch(DateTimeException e) {
         return false;
      }
   }

   /** The declared date formats spelled for a single-line message. */
   static String describeDateFormats(List<String> formats) {
      return oneLine(String.join(" | ", formats), 80);
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
