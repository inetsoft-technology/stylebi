/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util;

import java.sql.Time;

/**
 * STime extends java.uql.Time by providing a default constructor, then we could
 * use it to save typing time when debugging, e.g.
 * System.err.println("reset report at " + new STime());
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class STime extends Time {
   /**
    * Constructor.
    */
   public STime() {
      this(System.currentTimeMillis());
   }

   /**
    * Constructor.
    */
   public STime(long t) {
      super(t);
   }
}
