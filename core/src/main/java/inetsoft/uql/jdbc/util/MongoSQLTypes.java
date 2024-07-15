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
import java.sql.Types;

public class MongoSQLTypes extends SQLTypes {
   @Override
   public Object getObject(ResultSet result, int idx, int type) throws Exception {
      switch(type) {
      case Types.TINYINT:
      case Types.SMALLINT:
      case Types.INTEGER: {
         int val = result.getInt(idx);
         return result.wasNull() ? null : val;
      }
      // driver may return wrong value when calling getObject(). (56340)
      case Types.DOUBLE: {
         double val = result.getDouble(idx);
         return result.wasNull() ? null : val;
      }
      case Types.FLOAT: {
         double val = result.getFloat(idx);
         return result.wasNull() ? null : val;
      }
      }

      return super.getObject(result, idx, type);
   }
}
