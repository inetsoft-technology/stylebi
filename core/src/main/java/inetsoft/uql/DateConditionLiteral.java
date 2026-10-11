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
package inetsoft.uql;

import inetsoft.uql.schema.XSchema;

import java.util.Objects;

/**
 * Strict validation of a date, time or timestamp condition literal.
 *
 * <p>Two coercers sit between a literal String and the value a {@link Condition} evaluates:
 * {@link Condition#addValue} converts leniently at write time (any string
 * {@code DateTime.parse} swallows becomes some date, e.g. {@code "12"} becomes December 1st), and
 * {@link AbstractCondition#getObject} converts at read time and falls back to the current
 * date/time when it cannot parse. A literal is only safe to store when both coercers understand
 * it and agree on the result; anything else silently means something other than what was typed.
 * Writers that take user/agent supplied literals (worksheet condition tools, highlight
 * conditions) should call {@link #canonicalize} and refuse a null result.</p>
 */
public final class DateConditionLiteral {
   private DateConditionLiteral() {
   }

   /**
    * @return true for {@link XSchema#DATE}, {@link XSchema#TIME} and {@link XSchema#TIME_INSTANT}.
    */
   public static boolean isDateType(String type) {
      return XSchema.DATE.equals(type) || XSchema.TIME.equals(type) ||
         XSchema.TIME_INSTANT.equals(type);
   }

   /**
    * Checks a literal against both coercers.
    *
    * @param type  a date type, see {@link #isDateType}.
    * @param value the literal text (surrounding whitespace is ignored).
    *
    * @return the trimmed text to store, or null if the literal is null, blank, not understood by
    * {@link AbstractCondition#parseDateConditionValue}, or understood differently by
    * {@link Condition#addValue}. A non-date type is not checked and the value is returned as is.
    */
   public static String canonicalize(String type, String value) {
      if(!isDateType(type)) {
         return value;
      }

      if(value == null || value.trim().isEmpty()) {
         return null;
      }

      String trimmed = value.trim();
      Object parsed = AbstractCondition.parseDateConditionValue(type, trimmed, true);

      if(parsed == null) {
         return null;
      }

      Condition probe = new Condition(type);
      probe.addValue(trimmed);
      Object stored = probe.getValue(0);

      return sameInstant(parsed, stored) ? trimmed : null;
   }

   private static boolean sameInstant(Object a, Object b) {
      if(a instanceof java.util.Date da && b instanceof java.util.Date db) {
         if(a instanceof java.sql.Timestamp || b instanceof java.sql.Timestamp) {
            return da.getTime() == db.getTime();
         }

         return Objects.equals(a.toString(), b.toString());
      }

      return Objects.equals(a, b);
   }
}
