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
package inetsoft.uql.asset.sync;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * AssetTabularDependencyTransformer is a class to rename dependenies for ws binding tabular source
 *
 * @version 13.2
 * @author InetSoft Technology Corp
 */
public class AssetTabularDependencyTransformer extends AssetDependencyTransformer {
   /**
    * Create a transformer to rename dependenies for the ws binding tabular source.
    */
   public AssetTabularDependencyTransformer(AssetEntry asset) {
      super(asset);
   }

   @Override
   protected void renameVSTable(Element doc, RenameInfo info) {
   }

   @Override
   protected void renameWSSource(Element doc, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();
      NodeList list = getChildNodes(doc,
         "//assemblyInfo[@class='inetsoft.uql.asset.internal.TabularTableAssemblyInfo']");

      for(int i = 0; i < list.getLength(); i++) {
         Element tassembly = (Element) list.item(i);
         renameTabularAssembly(tassembly, info);
      }
   }

   private void renameTabularAssembly(Element tabular, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();
      Element src = getFirstChildNode(tabular, ".//source/sourceInfo");

      if(src != null) {
         replaceChildValue(src, "prefix", oname, nname, true);
         replaceChildValue(src, "source", oname, nname, true);
      }

      NodeList dbs = getChildNodes(tabular, "//datasource");

      for(int i = 0; i < dbs.getLength(); i++) {
         Element db = (Element) dbs.item(i);
         replaceAttribute(db, "name", oname, nname, true);
         String val = Tool.getValue(db);

         if(Tool.equals(val, oname)) {
            replaceCDATANode(db, nname);
         }
      }

      replaceSqlString(tabular, info);
   }

   /**
    * Replace spark sql table name reference. The data source qualifier is renamed in the
    * stored text instead of parsing the query and regenerating it, because a regenerated
    * query loses whatever the parse could not hold (LIMIT, QUALIFY, TOP, table aliases,
    * backtick quoting, ...).
    */
   private void replaceSqlString(Element table, RenameInfo info) {
      // update spark sql query data source names
      final Element query = getChildNode(table, "./query[contains(@class, 'inetsoft.uql.spark.sql')]/*/sql");

      if(query != null) {
         final String sqlString = query.getTextContent();
         final String renamed = renameSqlQualifier(sqlString, info.getOldName(), info.getNewName());

         if(!Tool.equals(sqlString, renamed)) {
            query.setTextContent(renamed);
         }
      }
   }

   /**
    * Rename every <code>oname.</code> qualifier in the sql text to <code>nname.</code>. The
    * name must be followed directly by a dot and must not continue an identifier or a dotted
    * path, so <code>rest10.t</code>, <code>xrest1.t</code> and <code>a.rest1.t</code> are not
    * renamed. The backtick quoted form <code>`rest1`.t</code> is renamed too. String literals,
    * double quoted text (a string literal in Spark SQL), variables and comments are skipped.
    * The name is matched exactly (case-sensitive), as data source names are. A table or alias
    * that has the same name as the data source can't be told apart from it, so its column
    * qualifiers (<code>rest1.id</code> for table <code>rest1</code>) are renamed too.
    */
   private static String renameSqlQualifier(String sql, String oname, String nname) {
      if(sql == null || oname == null || oname.isEmpty() || nname == null) {
         return sql;
      }

      // an unquoted all-digit name is a number (1.5), not a qualifier
      final boolean numeric = oname.chars().allMatch(ch -> ch >= '0' && ch <= '9');
      final int n = sql.length();
      final StringBuilder out = new StringBuilder(n);
      int i = 0;

      while(i < n) {
         final char c = sql.charAt(i);
         int end = i + 1;

         if(c == '\'' || c == '"') {
            end = skipQuoted(sql, i, c, true);
         }
         else if(sql.startsWith("--", i)) {
            end = sql.indexOf('\n', i);
            end = end < 0 ? n : end;
         }
         else if(sql.startsWith("/*", i)) {
            end = sql.indexOf("*/", i + 2);
            end = end < 0 ? n : end + 2;
         }
         else if(sql.startsWith("$(", i)) {
            end = sql.indexOf(')', i + 2);
            end = end < 0 ? n : end + 1;
         }
         else if(c == '`') {
            end = skipQuoted(sql, i, c, false);

            if(isQualifierStart(sql, i) && end < n && sql.charAt(end) == '.' &&
               sql.substring(i + 1, end - 1).replace("``", "`").equals(oname))
            {
               out.append('`').append(nname.replace("`", "``")).append('`');
               i = end;
               continue;
            }
         }
         else if(!numeric && isQualifierStart(sql, i) &&
            sql.startsWith(oname, i) && i + oname.length() < n && sql.charAt(i + oname.length()) == '.')
         {
            out.append(isPlainIdentifier(nname) ? nname : "`" + nname.replace("`", "``") + "`");
            i += oname.length();
            continue;
         }

         out.append(sql, i, end);
         i = end;
      }

      return out.toString();
   }

   /**
    * Get the index after the closing quote of the quoted text starting at start. A doubled
    * quote is an escaped quote, and so is a backslash escaped one when backslash is true.
    */
   private static int skipQuoted(String sql, int start, char quote, boolean backslash) {
      final int n = sql.length();
      int i = start + 1;

      while(i < n) {
         final char c = sql.charAt(i);

         if(backslash && c == '\\') {
            i += 2;
         }
         else if(c == quote && i + 1 < n && sql.charAt(i + 1) == quote) {
            i += 2;
         }
         else if(c == quote) {
            return i + 1;
         }
         else {
            i++;
         }
      }

      return n;
   }

   /**
    * Check if a qualifier may start at index i, i.e. it does not continue an identifier or
    * a dotted path.
    */
   private static boolean isQualifierStart(String sql, int i) {
      if(i == 0) {
         return true;
      }

      final char prev = sql.charAt(i - 1);
      return prev != '.' && prev != '`' && !isIdentifierChar(prev);
   }

   /**
    * Check if a name can be written unquoted. Spark SQL's unquoted identifier is ASCII
    * letters, digits and underscore only, so any other name (e.g. a CJK or accented one)
    * must be backtick quoted.
    */
   private static boolean isPlainIdentifier(String name) {
      if(name.isEmpty() || (name.charAt(0) >= '0' && name.charAt(0) <= '9')) {
         return false;
      }

      for(int i = 0; i < name.length(); i++) {
         final char c = name.charAt(i);

         if(!(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_')) {
            return false;
         }
      }

      return true;
   }

   private static boolean isIdentifierChar(char c) {
      return Character.isLetterOrDigit(c) || c == '_';
   }

   @Override
   protected boolean renameWSColumn(Element elem, RenameInfo info) {
      return true;
   }
}
