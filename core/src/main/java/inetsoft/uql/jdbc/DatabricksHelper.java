/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
 * for a Databricks datasource.
 *
 * @author  InetSoft Technology
 * @since   14.0
 */
public class DatabricksHelper extends SQLHelper {
   /**
    * Get the sql helper type.
    * @return the sql helper type.
    */
   @Override
   public String getSQLHelperType() {
      return "databricks";
   }

   /**
    * Get a sql string for replacing the table name so a row limit is added
    * to restrict the number of rows from the table.
    */
   @Override
   protected String getTableWithLimit(String tbl, int maxrows) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("select * from ");
      buffer.append(getQuotedTableName(tbl));
      buffer.append(" limit ");
      buffer.append(maxrows);
      return buffer.toString();
   }

   /**
    * Get the quote.
    * @return the quote.
    */
   @Override
   public String getQuote() {
      return "`";
   }

   @Override
   public String quotePath(String path, boolean physical, boolean force, boolean selectClause) {
      if(selectClause && uniformSql.isDistinct() && uniformSql.getOrderByFields() != null) {
         int lastIndex = path.lastIndexOf(".");

         if(lastIndex != -1) {
            path = path.substring(lastIndex + 1);
         }
      }

      return super.quotePath(path, physical, force, selectClause);
   }

   /**
    * Get the order by column of a field.
    */
   protected String getOrderByColumn(String field) {
      JDBCSelection xselect = (JDBCSelection) uniformSql.getSelection();
      int index = xselect.indexOfColumn(field);

      if(index >= 0) {
         String alias = xselect.getValidAlias(index, this);

         if(alias != null) {
            return alias;
         }
      }

      return super.getOrderByColumn(field);
   }
}