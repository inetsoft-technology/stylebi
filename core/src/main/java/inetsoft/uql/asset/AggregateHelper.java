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
package inetsoft.uql.asset;

import inetsoft.uql.erm.DataRef;

/**
 * Helper class for generating aggregate functions.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class AggregateHelper {
   /**
    * Get a column string representation for use in SQL query.
    */
   public abstract String getColumnString(DataRef column);

   /**
    * Get the aggregate expression for calculating the specified function.
    * @param func function is one of: stddev, stddevp, var, varp, covar,
    * correl.
    * @return aggregate expression (SQL) or null if the database doesn't
    * support the aggregate function.
    */
   public String getAggregateExpression(String func, String col, String col2) {
      String name = getAggregateFunction(func);

      if(name != null) {
         String expr = name + "(" + col;

         if(col2 != null) {
            expr += ", " + col2;
         }

         return expr + ")";
      }

      return null;
   }

   /**
    * Get the db type.
    */
   public abstract String getDBType();

   /**
    * Get the aggregate function name of the specified function.
    * @param func function is one of: stddev, stddevp, var, varp, covar,
    * correl.
    * @return function name or null if function is not supported in SQL.
    */
   public abstract String getAggregateFunction(String func);
}
