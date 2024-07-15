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
import inetsoft.uql.jdbc.SelectTable;
import inetsoft.uql.jdbc.UniformSQL;
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
    * Replace spark sql table name reference
    */
   private void replaceSqlString(Element table, RenameInfo info) {
      // update spark sql query data source names
      final Element query = getChildNode(table, "./query[contains(@class, 'inetsoft.uql.spark.sql')]/*/sql");

      if(query != null) {
         final String sqlString = query.getTextContent();

         if(sqlString != null) {
            final UniformSQL sql = new UniformSQL();

            try {
               synchronized(sql) {
                  sql.setSQLString(sqlString, true);

                  if(sql.getParseResult() == UniformSQL.PARSE_INIT) {
                     sql.wait();
                  }
               }

               final SelectTable[] selectTables = sql.getSelectTable();

               for(SelectTable selectTable : selectTables) {
                  final Object selectTableName = selectTable.getName();

                  if(selectTableName instanceof String &&
                     ((String) selectTableName).startsWith(info.getOldName() + "."))
                  {
                     final String tableName = ((String) selectTableName).substring(info.getOldName().length());
                     final String newName = info.getNewName() + tableName;
                     selectTable.setName(newName);
                     selectTable.setAlias(newName);
                     sql.clearSQLString();
                  }
               }

               query.setTextContent(sql.getSQLString());
            }
            catch(InterruptedException ignored) {
               // just ignore
            }
         }
      }
   }

   @Override
   protected boolean renameWSColumn(Element elem, RenameInfo info) {
      return true;
   }
}
