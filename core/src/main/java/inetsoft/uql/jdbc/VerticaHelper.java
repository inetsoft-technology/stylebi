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
 * for a MySQL database.
 *
 * @author  InetSoft Technology
 * @since   6.0
 */
public class VerticaHelper extends SQLHelper {
   /**
    * Get the sql helper type.
    * @return the sql helper type.
    */
   @Override
   public String getSQLHelperType() {
      return "vertica";
   }

   @Override
   protected String getTableWithLimit(String tbl, int maxrows) {
      return "select * from " +
         quoteTableName(tbl) +
         " limit " +
         maxrows;
   }

   @Override
   protected String generateMaxRowsClause(String sql, int maxrows) {
      return "select * from (" +
         sql +
         ") inner" + System.currentTimeMillis() +
         " limit " +
         maxrows;
   }

   @Override
   public boolean isApplyMaxRowsToTopLevel() {
      return true;
   }

   /**
    * Vertica supports RANGE frames with a numeric or date/time (INTERVAL) value offset, e.g.
    * {@code RANGE BETWEEN INTERVAL '7 days' PRECEDING AND CURRENT ROW}. GROUPS frames are not
    * part of Vertica's documented window-frame grammar; left denied here (falls back to the
    * correct in-memory engine).
    */
   @Override
   public boolean supportsWindowFrame(String mode, boolean hasValueOffset, boolean dateOffset) {
      if(!supportsOperation(WINDOW_FUNCTION)) {
         return false;
      }

      if("RANGE".equals(mode)) {
         return true;   // peer, numeric value, and date INTERVAL offsets all supported
      }

      return super.supportsWindowFrame(mode, hasValueOffset, dateOffset);   // ROWS true, GROUPS false
   }

   /**
    * Vertica's interval literal quotes the quantity and unit together, with a plural,
    * lower-cased unit, e.g. {@code INTERVAL '7 days'}.
    */
   @Override
   public String formatWindowFrameInterval(int offset, String unit) {
      return "INTERVAL '" + offset + " " + unit.toLowerCase() + "s'";
   }
}
