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
package inetsoft.web.composer.model.ws;

public class FreeFormSQLPaneModel {
   public FreeFormSQLPaneModel() {
   }

   public boolean isHasSqlString() {
      return hasSqlString;
   }

   public void setHasSqlString(boolean hasSqlString) {
      this.hasSqlString = hasSqlString;
   }

   public String getGeneratedSqlString() {
      return generatedSqlString;
   }

   public void setGeneratedSqlString(String generatedSqlString) {
      this.generatedSqlString = generatedSqlString;
   }

   public String getSqlString() {
      return sqlString;
   }

   public void setSqlString(String sqlString) {
      this.sqlString = sqlString;
   }

   public boolean isParseSql() {
      return parseSql;
   }

   public void setParseSql(boolean parseSql) {
      this.parseSql = parseSql;
   }

   public boolean isHasColumnInfo() {
      return hasColumnInfo;
   }

   public void setHasColumnInfo(boolean hasColumnInfo) {
      this.hasColumnInfo = hasColumnInfo;
   }

   public int getParseResult() {
      return parseResult;
   }

   public void setParseResult(int parseResult) {
      this.parseResult = parseResult;
   }

   private boolean hasSqlString;
   private String sqlString;
   private String generatedSqlString;
   private boolean parseSql;
   private boolean hasColumnInfo;
   private int parseResult;
}
