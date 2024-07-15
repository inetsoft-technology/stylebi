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
package inetsoft.uql.jdbc;

/**
 * Helper class of UniformSQL. This class generates SQL statements
 * for a Denodo Virtual DataPort database.
 *
 * @author  InetSoft Technology
 * @since   12.0
 */
public class DenodoSQLHelper extends SQLHelper {

   /**
    * Get the sql helper type.
    * @return the sql helper type.
    */
   @Override
   public String getSQLHelperType() {
      return "denodo";
   }

   /**
    * Get the quote.
    * @return the quote.
    */
   @Override
   public String getQuote() {
      return "`";
   }
}
