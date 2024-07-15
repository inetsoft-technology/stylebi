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

import java.sql.Types;

/**
 * SQLTypes specialization that handles Ingres databases.
 *
 * @author  InetSoft Technology
 * @since   7.0
 */
public final class IngresSQLTypes extends SQLTypes {
   /**
    * Fix sql type according to type name.
    */
   @Override
   public int fixSQLType(int sqltype, String tname) {
      if(Types.REAL == sqltype && "FLOAT".equals(tname)) {
         return Types.FLOAT;
      }

      return super.fixSQLType(sqltype, tname);
   }
}
