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

import java.util.function.Function;
import java.util.regex.Pattern;

public class GoogleBigQueryHelper extends SQLHelper {
   /**
    * Get the sql helper type.
    *
    * @return the sql helper type.
    */
   @Override
   public String getSQLHelperType() {
      return "google bigquery";
   }

   /**
    * Get the quote.
    *
    * @return the quote.
    */
   @Override
   public String getQuote() {
      return "`";
   }

   @Override
   public String quoteTableName(String name, boolean selectClause) {
      return fixQuote(name, this::quoteFullTableName);
   }

   private String quoteFullTableName(String str) {
      if(containsSpecialChar(str)) {
         return getQuote() + str + getQuote();
      }

      return str;
   }

   private boolean containsSpecialChar(String str) {
      if(special == null) {
         special = Pattern.compile("[^\\w\\d_]+");
      }

      return str != null && !str.startsWith(getQuote()) && special.matcher(str).find();
   }

   @Override
   public String quoteTableAlias(String alias) {
      return fixQuote(alias, s -> super.quoteTableAlias(s));
   }

   // name may be quoted by " before it's fixed in SQLParser. strip it and requote
   // with ` (60928, 61096)
   private String fixQuote(String str, Function<String, String> quoteStr) {
      if(str.startsWith("\"") && str.endsWith("\"")) {
         return getQuote() + str.substring(1, str.length() - 1) + getQuote();
      }

      return quoteStr.apply(str);
   }

   @Override
   public boolean isValidAlias(String alias) {
      return alias == null || super.isValidAlias(alias) && !containsSpecialChar(alias);
   }

   private Pattern special;
}
