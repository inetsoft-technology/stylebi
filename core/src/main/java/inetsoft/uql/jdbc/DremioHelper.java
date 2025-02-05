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

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public class DremioHelper extends SQLHelper {
   @Override
   public String getSQLHelperType() {
      return "dremio";
   }

   @Override
   protected String fixTableAlias(Object name, String alias, int maxlen, int index,
                                  Set<String> aliases)
   {
      String nalias = alias;

      if(alias.equals(name)) {
         String table = (String) name;
         int idx = table.lastIndexOf('.');

         if(idx >= 0) {
            String prefix = nalias.substring(idx + 1);
            nalias = prefix;

            for(int i = 1; aliases.contains(nalias); i++) {
               nalias = prefix + i;
            }
         }
      }

      return super.fixTableAlias(name, nalias, maxlen, index, aliases);
   }

   @Override
   protected String getTableWithLimit(String tbl, int maxrows) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("select * from ");
      buffer.append(quoteTableNameWithQuotedSchema(tbl, true));
      buffer.append(" fetch first ");
      buffer.append(maxrows);
      buffer.append(" rows only");
      return buffer.toString();
   }

   @Override
   protected void fixTableName(String namestr, StringBuilder sb, String quote, boolean selectClause) {
      //Dremio requires special handling of table name because schema contains "."
      sb.append(quoteTableNameWithQuotedSchema(namestr, selectClause));
   }

   @Override
   protected String quoteTableNameWithQuotedSchema(String name, boolean selectClause) {
      //table name requires special handling because dremio schema is quoted

      int index = uniformSql.getTableIndex(name);

      // check if is a table alias
      if(index >= 0) {
         SelectTable stable = uniformSql.getSelectTable(index);
         String talias = stable.getAlias();
         Object tname = stable.getName();

         if(tname instanceof String) {
            tname = trimQuote((String) tname);
         }

         // if name is not equal to table but equal to alias, it must
         // be an alias. We should quote it as table alias instead
         if(!trimQuote(name).equals(tname) && name.equals(talias)) {
            if(uniformSql.isSqlQuery()) {
               return talias;
            }

            return quoteTableAlias(name);
         }
      }

      String alias = uniformSql.getTableAlias(name);
      alias = alias == null ? name : alias;
      index = uniformSql.getTableIndex(alias);
      SelectTable stable = (index >= 0) ?
         uniformSql.getSelectTable(index) : null;
      String catalog = stable == null ? null : stable.getCatalog();
      String schema = stable == null ? null : XUtil.quoteAlias(stable.getSchema(), this);
      StringBuilder sb = new StringBuilder();
      boolean tpart = false;  // indicates name only contains the table name
      boolean cexcluded = false; // catalog excluded in table

      // Add catalog to return string, and remove from name
      if(catalog != null) {
         if(name.startsWith(catalog + ".")) {
            processCatalog(sb, catalog, selectClause);
            name = name.substring(catalog.length() + 1);
         }
         else {
            cexcluded = true;
         }
      }

      // Add schema to return string, and remove from name
      // If true, then what is left in name must only be the table name
      if(schema != null) {
         if(name.startsWith(schema + ".")) {
            processSchema(sb, schema, selectClause);
            name = name.substring(schema.length() + 1);
            tpart = true;
         }
         else {
            // neighter catalog nor schema includeded in table, tpart is true
            // if no catalog, it is a table part
            // fix bug1368783884495
            tpart = cexcluded || catalog == null;
         }
      }

      JDBCDataSource src = uniformSql.getDataSource();
      String productName = SQLHelper.getProductName(src);

      if(!tpart) {
         // @by davidd 2008-01-08
         // if table name does not contain catalog or schema,
         // we should quote it as alias to support dot properly
         // DEFAULT may have dropped the schema (when username == schema)
         // in which case we should assume name contains only the table
         if(src != null &&
            src.getTableNameOption() == JDBCDataSource.TABLE_OPTION ||
            //Since SQLite does not support catalog and Schema, table name is equal to tpart
            "sqlite".equals(productName) ||
            "mysql".equals(productName) && catalog != null)
         {
            tpart = true;
         }
      }

      // Dremio needs special handling of quoted name, cannot ignore if already quoted
      Function<String, Boolean> checkSpecial = null;

      // should quote the table name for oracle when the table name has lowercase.
      if("oracle".equals(productName)) {
         checkSpecial = tname -> {
            if(tname == null) {
               return false;
            }

            return !Objects.equals(tname, tname.toUpperCase());
         };
      }

      name = tpart ? XUtil.quoteNameSegment(name, true, this, checkSpecial)
         : XUtil.quoteName(name, this);


      processTableSchema(sb, schema, selectClause);
      sb.append(name);
      return sb.toString();
   }

   @Override
   protected boolean requiresSpecialQuoteHandling() {
      return true;
   }

   @Override
   protected void processSchema(StringBuilder sb, String schema,
                                boolean selectClause) {
      if(!selectClause) {
         super.processSchema(sb, schema, selectClause);
      }
      else {
         //select clause requires schema to be unquoted
            sb.append(trimQuote(schema));
            sb.append(".");
      }
   }

   @Override
   /**
    * Replace table name in expression.
    */
   protected String replaceTable(String expr, String alias, String nalias) {
      int idx = 0;
      int s;

      //quoted schema breaks expr, check and remove, adding quotes around table name with space if needed
      alias = parseAndQuoteTableWithSchema(XUtil.removeQuote(alias));

      while((s = expr.indexOf(alias + ".", idx)) >= 0) {
         // if this is not the start of an identifier, don't replace
         if(s > 0 && Character.isUnicodeIdentifierPart(expr.charAt(s-1))){
            idx = s + 1;
            continue;
         }

         expr = expr.substring(0, s) +
            nalias +
            "." +
            expr.substring(s + alias.length() + 1);
         idx = s + nalias.length() + 1;
      }

      return expr;
   }

   /**
    * Quote table name when provided path with schema
    **/
   private String parseAndQuoteTableWithSchema(String fullName) {
      int tblInd = fullName.lastIndexOf(".");

      if(tblInd > 0) {
         String tblName = fullName.substring(tblInd + 1);
         String quotedName = quoteTableName(tblName);

         return fullName.replace(tblName, quotedName);
      }

      return fullName;
   }

}
