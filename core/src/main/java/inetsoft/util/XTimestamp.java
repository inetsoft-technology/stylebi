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
package inetsoft.util;

import java.sql.Timestamp;

/**
 * XTimestamp is a value with Timestamp type. It only display the year, month,
 * day, hour, minute and seconds, millisecond is ignored.
 *
 * @version9.5, 4/17/2008
 * @author InetSoft Technology Corp
 */
public class XTimestamp extends Timestamp {
   public XTimestamp(long time) {
      super(time);
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      String str = super.toString();
      int id =  str.lastIndexOf(".");
      String date = str.substring(0, id);

      return date;
   }
}