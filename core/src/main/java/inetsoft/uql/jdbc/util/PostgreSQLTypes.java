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
package inetsoft.uql.jdbc.util;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Time;
import java.sql.Types;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQLTypes specialization that handles PostgreSQL databases.
 * <p>
 * The pgjdbc driver reports both {@code time} and {@code timetz} as {@link Types#TIME} and
 * {@code getTime()} on a {@code timetz} value shifts it by the difference between the JVM zone
 * and the column's stored offset. SQL pushdown (and SQL conditions) work on the stored wall
 * clock, so a timetz column is read here as the stored wall clock to stay consistent.
 */
public final class PostgreSQLTypes extends SQLTypes {
   @Override
   public Object getObject(ResultSet result, int idx, int type) throws Exception {
      if(type == Types.TIME) {
         ResultSetMetaData meta = result.getMetaData();

         if(meta != null && "timetz".equalsIgnoreCase(meta.getColumnTypeName(idx))) {
            String str = result.getString(idx);

            if(str == null) {
               return null;
            }

            Time time = parseWallClock(str);

            if(time != null) {
               return time;
            }

            return result.getTime(idx);
         }
      }

      return super.getObject(result, idx, type);
   }

   /**
    * Build a time from the stored HH:mm:ss[.fraction] part of a timetz string, ignoring the
    * zone offset suffix. The fields are interpreted in the JVM zone so the wall clock is kept.
    *
    * @return the time, or null if the text is not in the expected form.
    */
   static Time parseWallClock(String str) {
      Matcher m = PATTERN.matcher(str.trim());

      if(!m.lookingAt()) {
         return null;
      }

      int hour = Integer.parseInt(m.group(1));
      int min = Integer.parseInt(m.group(2));
      int sec = Integer.parseInt(m.group(3));

      if(hour > 23 || min > 59 || sec > 59) {
         // e.g. 24:00:00, leave to the driver
         return null;
      }

      int millis = 0;

      if(m.group(4) != null) {
         String frac = (m.group(4) + "000").substring(0, 3);
         millis = Integer.parseInt(frac);
      }

      Time time = Time.valueOf(java.time.LocalTime.of(hour, min, sec));
      return millis == 0 ? time : new Time(time.getTime() + millis);
   }

   private static final Pattern PATTERN =
      Pattern.compile("([0-9]{1,2}):([0-9]{2}):([0-9]{2})(?:[.]([0-9]+))?");
}
