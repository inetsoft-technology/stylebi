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

import inetsoft.uql.table.XShortColumn;
import inetsoft.uql.table.XTableColumnCreator;

import java.sql.*;
import java.time.LocalTime;

/**
 * SQLTypes specialization that handles Informix databases.
 *
 * @author  InetSoft Technology
 * @since   7.0
 */
public final class InformixSQLTypes extends SQLTypes {
   /**
    * Get the table column creator.
    * @param type the specified sql type.
    * @param tname the specified sql type name.
    * @return the table column creator.
    */
   @Override
   public XTableColumnCreator getXTableColumnCreator(int type, String tname) {
      if(type == Types.SMALLINT) {
         return XShortColumn.getCreator();
      }

      return super.getXTableColumnCreator(type, tname);
   }

   /**
    * Get an object from a resultset. The Informix driver truncates the fraction of a
    * DATETIME HOUR TO FRACTION column (reported as TIME) in getTime(), so it is read through
    * getTimestamp() to keep the milliseconds.
    */
   @Override
   public Object getObject(ResultSet result, int idx, int type) throws Exception {
      if(type == Types.TIME) {
         return toTime(result.getTimestamp(idx));
      }

      return super.getObject(result, idx, type);
   }

   /**
    * Get an object from a CallableStatement. See {@link #getObject(ResultSet, int, int)}.
    */
   @Override
   public Object getObject(CallableStatement cs, int idx, int type) throws Exception {
      if(type == Types.TIME) {
         return toTime(cs.getTimestamp(idx));
      }

      return super.getObject(cs, idx, type);
   }

   /**
    * Convert the timestamp to a time on 1970-01-01 (the date part Informix returns for a
    * time-only value is not meaningful) that keeps the milliseconds.
    */
   static Time toTime(Timestamp ts) {
      if(ts == null) {
         return null;
      }

      LocalTime lt = ts.toLocalDateTime().toLocalTime();
      return new Time(Time.valueOf(lt.withNano(0)).getTime() + lt.getNano() / 1_000_000);
   }
}
