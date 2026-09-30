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
package inetsoft.util;

import java.util.*;

/**
 * Invariants of typed value parsing and serialization ({@link CoreTool#getData} and
 * {@link CoreTool#getDataString}), which is how condition values, parameters and embedded
 * data are saved and reloaded. Shared by {@code DataStringSeedTest} and the enterprise fuzzer
 * ({@code test/fuzzer}).
 * <p>
 * An input is the data type ({@code integer}, {@code date}, ...) on the first line and the
 * value text on the rest. {@link #check(String)} verifies, for both the regular and the
 * persistent (strict null) string forms, that:
 * <ol>
 *    <li>parsing never throws;</li>
 *    <li>a parsed value survives a save and reload unchanged: once the text has been
 *        normalized by the first parse, {@code getData(type, getDataString(v, type))}
 *        equals {@code v} (for times, has the same time of day).</li>
 * </ol>
 */
public final class DataStringProperties {
   private DataStringProperties() {
   }

   /**
    * @return false if the input does not name a known type and was skipped.
    */
   public static boolean check(String input) {
      int newline = input.indexOf('\n');

      if(input.length() > MAX_INPUT || newline < 0) {
         return false;
      }

      String type = input.substring(0, newline).strip();
      String text = input.substring(newline + 1);

      if(!TYPES.contains(type)) {
         return false;
      }

      roundTrip(type, text, false);
      roundTrip(type, text, true);
      return true;
   }

   private static void roundTrip(String type, String text, boolean persistent) {
      String where = type + " \"" + text + "\"" + (persistent ? " (persistent)" : "");
      Object value = parse(where, type, text, persistent);

      if(value == null) {
         return;
      }

      String saved = persistent ?
         CoreTool.getPersistentDataString(value, type) : CoreTool.getDataString(value, type);
      Object reloaded = parse(where, type, saved, persistent);

      if(!same(value, reloaded)) {
         throw new AssertionError("Value changed on save and reload: " + where +
                                  "\nparsed:   " + describe(value) +
                                  "\nsaved as: \"" + saved + "\"" +
                                  "\nreloaded: " + describe(reloaded));
      }
   }

   private static Object parse(String where, String type, String text, boolean persistent) {
      try {
         return CoreTool.getData(type, text, persistent);
      }
      catch(Throwable ex) {
         throw new AssertionError("Parsing " + where + " threw " + ex, ex);
      }
   }

   private static boolean same(Object a, Object b) {
      if(a instanceof Object[] && b instanceof Object[]) {
         return Arrays.deepEquals((Object[]) a, (Object[]) b);
      }

      // a parsed time may keep a date part that is not saved, only the time of day matters
      if(a instanceof java.sql.Time && b instanceof java.sql.Time) {
         return a.toString().equals(b.toString());
      }

      return Objects.equals(a, b);
   }

   private static String describe(Object value) {
      if(value == null) {
         return "null";
      }

      String text = value instanceof Object[] ?
         Arrays.deepToString((Object[]) value) : value.toString();
      return value.getClass().getName() + " " + text;
   }

   public static final int MAX_INPUT = 500;

   private static final Set<String> TYPES = Set.of(
      CoreTool.STRING, CoreTool.BOOLEAN, CoreTool.FLOAT, CoreTool.DOUBLE, CoreTool.CHAR,
      CoreTool.CHARACTER, CoreTool.BYTE, CoreTool.SHORT, CoreTool.INTEGER, CoreTool.LONG,
      CoreTool.TIME_INSTANT, CoreTool.DATE, CoreTool.TIME, CoreTool.COLOR, CoreTool.ARRAY);
}
