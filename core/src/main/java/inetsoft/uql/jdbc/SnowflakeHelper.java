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

import inetsoft.uql.util.XUtil;

public class SnowflakeHelper extends SQLHelper {
   @Override
   public boolean isCaseSensitive() {
      return true;
   }

   /**
    * Snowflake folds an unquoted identifier to upper case.
    */
   @Override
   public IdentifierCase getIdentifierCase() {
      return IdentifierCase.UPPER;
   }

   @Override
   public String getSQLHelperType() {
      return "snowflake";
   }

   @Override
   protected String getTableWithLimit(String tbl, int maxrows) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("select * from ");
      buffer.append(quoteTableName(tbl));
      buffer.append(" limit ");
      buffer.append(maxrows);
      return buffer.toString();
   }

   /**
    * Quote every select alias, in the case it is stored. A reference to it ("s"."TransDateTime",
    * order by, group by) is always quoted on a case-sensitive helper, and a plain alias would
    * be folded to upper case by the database and not be found by it (#80212).
    */
   @Override
   protected String quoteColumnAlias(String alias, boolean same, boolean part) {
      return alias.indexOf('"') < 0 ? '"' + alias + '"' :
         super.quoteColumnAlias(alias, same, part);
   }

   /**
    * A column of a physical table (t."col", sum(t.col)) is not a select alias, it is quoted
    * only if needed.
    */
   @Override
   protected String quoteColumnPartAlias(String alias) {
      return XUtil.quoteAlias(alias, this);
   }

   /**
    * A column of a physical table as a function argument is not a select alias either.
    */
   @Override
   protected String quoteCaseSensitiveAggregateColumn(String column) {
      return quoteColumnPartAlias(column);
   }

   /**
    * The alias of a derived table column is quoted as the select alias that defines it.
    */
   @Override
   protected String quoteAliasColumnPart(String alias) {
      return quoteColumnAlias(alias);
   }

   /**
    * Match contains / starting with values literally with {@code LIKE ... ESCAPE '!'}.
    */
   @Override
   public Character getLikeEscapeChar() {
      return '!';
   }
}
