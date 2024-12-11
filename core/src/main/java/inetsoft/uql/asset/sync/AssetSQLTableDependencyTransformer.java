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

import inetsoft.uql.XDataSource;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.XExpression;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.*;

/**
 * AssetSqlTableDependencyTransformer is a class to rename dependenies for ws/vs binding sql table
 *
 * @version 13.2
 * @author InetSoft Technology Corp
 */
public class AssetSQLTableDependencyTransformer extends AssetDependencyTransformer {
   /**
    * Create a transformer to rename dependenies for the asset(ws/vs) binding model.
    */
   public AssetSQLTableDependencyTransformer(AssetEntry asset) {
      super(asset);
   }

   protected void renameAssetEntry(Element elem, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();
      Element pathNode = Tool.getChildNodeByTagName(elem, "path");

      if(info.isDataSource()) {
         Element descriptionNode = Tool.getChildNodeByTagName(elem, "description");
         Element propertyNode = Tool.getChildNodeByTagName(elem, "properties");

         replaceDataSource(pathNode, oname, nname);
         replaceDataSource(descriptionNode, oname, nname);
         replacePropertyNode1(propertyNode, "entry.paths", oname, nname, false);
         replacePropertyNode1(propertyNode, "prefix", oname, nname, true);
      }
      else if(info.isDataSourceFolder()) {
         Element descriptionNode = Tool.getChildNodeByTagName(elem, "description");
         Element propertyNode = Tool.getChildNodeByTagName(elem, "properties");
         replaceDataSource(pathNode, oname, nname);
         replaceDataSource(descriptionNode, oname, nname);
         replacePropertyNode1(propertyNode, "entry.paths", oname, nname, false);
         replacePropertyNode1(propertyNode, "prefix", oname, nname, true);
      }
   }

   @Override
   protected void renameWSSource(Element elem, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();

      //For sql binding, its prefix and source is all datasource's full name.
      if(info.isDataSource()) {
         Element snode = Tool.getChildNodeByTagName(elem, "prefix");

         if(Tool.equals(oname, Tool.getValue(snode))) {
            replaceCDATANode(snode, nname);
         }

         snode = Tool.getChildNodeByTagName(elem, "source");

         if(Tool.equals(oname, Tool.getValue(snode))) {
            replaceCDATANode(snode, nname);
         }
      }
      else if(info.isDataSourceFolder()) {
         Element pnode = Tool.getChildNodeByTagName(elem, "prefix");
         String opre = Tool.getValue(pnode);

         if(Tool.equals(opre, oname)) {
            replaceCDATANode(pnode, nname);
         }

         Element snode = Tool.getChildNodeByTagName(elem, "source");
         String source = Tool.getValue(snode);

         if(Tool.equals(source, oname)) {
            replaceCDATANode(snode, nname);
         }
      }
   }

   protected void renameSQLSources(Element doc, RenameInfo info) {
      String sqlAssembly =
         "//assemblies/oneAssembly/assembly[@class='inetsoft.uql.asset.SQLBoundTableAssembly']";

      // For datasource change or folder change, change source name.
      if(info.isDataSourceFolder() || info.isDataSource())
      {
         NodeList list = getChildNodes(doc,
            sqlAssembly + "/assemblyInfo/datasource |" +
            sqlAssembly + "/assemblyInfo/query/query_jdbc/datasource");

         for(int i = 0; i < list.getLength(); i++) {
            Element elem = (Element) list.item(i);
            renameSQLSource(elem, info);
         }
      }

      if(info.isSource() || info.isTableOption()) {
         NodeList sqls = getChildNodes(doc, sqlAssembly + "/assemblyInfo/query" +
                 "/query_jdbc/uniform_sql");
         List<RenameInfo> rinfos = new ArrayList<>();

         for(int i = 0; i < sqls.getLength(); i++) {
            Element elem = (Element) sqls.item(i);
            renameSQL(elem, info, rinfos);
         }

         NodeList items = getChildNodes(doc, sqlAssembly + "/columns/list/item");

         for(int i = 0; i < items.getLength(); i++) {
            Element elem = (Element) items.item(i);
            renameColumnItem(elem, info);
         }

         NodeList joins = getChildNodes(doc, sqlAssembly + "/joins/XJoin");

         for(int i = 0; i < joins.getLength(); i++) {
            Element elem = (Element) joins.item(i);
            renameJoin(elem, info, rinfos);
         }

         NodeList conditions = getChildNodes(doc, sqlAssembly + "/conditions/conditions/condition");

         for(int i = 0; i < conditions.getLength(); i++) {
            Element elem = (Element) conditions.item(i);
            renameCondition(elem, info, rinfos);
         }
      }
   }

   private void renameSQLSource(Element datasource, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();

      if(Tool.equals(oname, Tool.getAttribute(datasource, "name"))) {
         datasource.setAttribute("name", nname);
      }

      if(Tool.equals(oname, Tool.getValue(datasource))) {
         replaceCDATANode(datasource, nname);
      }
   }

   private void renameSQL(Element sql, RenameInfo info, List<RenameInfo> rinfos) {
      NodeList tables = getChildNodes(sql, "//table");

      for(int i = 0; i < tables.getLength(); i++) {
         Element elem = (Element) tables.item(i);
         renameSQLTable(elem, info, rinfos);
      }

      NodeList columns = getChildNodes(sql, "//column");

      for(int i = 0; i < columns.getLength(); i++) {
         Element elem = (Element) columns.item(i);
         renameSQLColumn(elem, info, rinfos);
      }

      NodeList nodeList = getChildNodes(sql, "//where");

      for(int i = 0; i < nodeList.getLength(); i++) {
         Element elem = (Element) nodeList.item(i);
         renameJoin(elem, info, rinfos);
      }
   }

   private void renameSQLTable(Element table, RenameInfo info, List<RenameInfo> rinfos) {
      Element node = Tool.getChildNodeByTagName(table, "name");

      if(node == null) {
         return;
      }

      if(!info.isTableOption()) {
         return;
      }

      String oname = info.getOldName();
      String otableName = Tool.getValue(node);
      String ntableName = getQualifiedTableName(oname, otableName);
      replaceCDATANode(node, ntableName);
      RenameInfo renameInfo = new RenameInfo(otableName, ntableName, RenameInfo.SQL_TABLE);
      rinfos.add(renameInfo);
      node = Tool.getChildNodeByTagName(table, "alias");
      String alias = Tool.getValue(node);

      if(Tool.equals(otableName, alias)) {
         replaceCDATANode(node, ntableName);
      }
   }

   private void renameSQLEntries(Element entry, RenameInfo info, List<RenameInfo> rinfos) {
      String oname = info.getOldName();
      String nname = info.getNewName();
      Element pathNode = Tool.getChildNodeByTagName(entry, "path");
      String path = Tool.getValue(pathNode);

      if(info.isDataSource() || info.isDataSourceFolder() || info.isTableOption()) {
         if(!path.startsWith(oname + TABLE)) {
            return;
         }

         String table = path.substring(path.lastIndexOf("/") + 1);
         String name = path.substring(path.indexOf(TABLE) + 7);
         String otableName = name.indexOf("/") != -1 ? name.replace("/", ".") : name;
         String ntableName = null;

         if(info.isTableOption()) {
            ntableName = getQualifiedTableName(oname, table);
            oname = oname + TABLE + "/" + otableName;
            nname = nname + TABLE + "/" + ntableName;
         }

         replaceCDATANode(pathNode, path.replace(oname.replace(".", "/"),
            nname.replace(".", "/")));
         Element descriptionNode = Tool.getChildNodeByTagName(entry, "description");
         replaceAssetAttr(descriptionNode, oname.replace(".", "/"),
            nname.replace(".", "/"));
         Element propertiesNode = Tool.getChildNodeByTagName(entry, "properties");
         NodeList list = Tool.getChildNodesByTagName(propertiesNode, "property");

         for(int i = 0; i < list.getLength(); i++) {
            Element prop = (Element) list.item(i);
            Element keyElem = Tool.getChildNodeByTagName(prop, "key");

            if(keyElem == null) {
               keyElem = Tool.getChildNodeByTagName(prop, "name");
            }

            String keyVal = Tool.getValue(keyElem);

            if(Tool.equals("prefix", keyVal) && !info.isTableOption()) {
               Element valElem = Tool.getChildNodeByTagName(prop, "value");
               replaceCDATANode(valElem, nname);
            }
            else if(Tool.equals("entry.paths", keyVal)) {
               Element valElem = Tool.getChildNodeByTagName(prop, "value");
               String nvalue = nname.replace("/", "^_^");
               nvalue = nvalue.replace(".", "^_^");
               replaceCDATANode(valElem, nvalue);
            }
            else if(info.isTableOption() && Tool.equals("source", keyVal)) {
               Element valElem = Tool.getChildNodeByTagName(prop, "value");
               replaceCDATANode(valElem, ntableName);
            }
            else if(info.isTableOption() && Tool.equals("entity", keyVal)) {
               Element valElem = Tool.getChildNodeByTagName(prop, "value");
               String ovalue = Tool.getValue(valElem);
               replaceCDATANode(valElem, ntableName);
               RenameInfo renameInfo = new RenameInfo(ovalue, ntableName, RenameInfo.SQL_TABLE);
               rinfos.add(renameInfo);
            }
         }
      }
      // Fix property->source
      else if(info.isSource()) {
         Element propertiesNode = Tool.getChildNodeByTagName(entry, "properties");
         NodeList list = Tool.getChildNodesByTagName(propertiesNode, "property");

         for(int i = 0; i < list.getLength(); i++) {
            Element prop = (Element) list.item(i);
            Element keyElem = Tool.getChildNodeByTagName(prop, "key");

            if (keyElem == null) {
               keyElem = Tool.getChildNodeByTagName(prop, "name");
            }

            String keyVal = Tool.getValue(keyElem);

            if(Tool.equals("source", keyVal)) {
               Element valElem = Tool.getChildNodeByTagName(prop, "value");
               String value = Tool.getValue(valElem);
               replaceCDATANode(valElem, getNewTableName(value, info, null));
            }
         }
      }
   }

   private void renameSQLColumn(Element column, RenameInfo info, List<RenameInfo> rinfos) {
      Element node = Tool.getChildNodeByTagName(column, "table");
      Element node1 = Tool.getChildNodeByTagName(column, "alias");
      Element node2 = Tool.getChildNodeByTagName(column, "type");
      Element node3 = Tool.getChildNodeByTagName(column, "description");
      String name = Tool.getValue(node);
      String oname = getOldTableName(name, info);
      String nname = getNewTableName(name, info, rinfos);
      String col = Tool.getValue(column);

      if(oname != null || nname != null) {
         replaceCDATANode(column, col.replace(oname, nname));

         if(Tool.equals(name, oname)) {
            replaceCDATANode(node, nname);
         }
      }

      column.appendChild(node1);
      column.appendChild(node2);
      column.appendChild(node3);
   }

   private void renameJoin(Element join, RenameInfo info, List<RenameInfo> rinfos) {
      NodeList nodeList = Tool.getChildNodesByTagName(join, "expression", true);

      for(int i = 0; i < nodeList.getLength(); i++) {
         Element elem = (Element) nodeList.item(i);
         renameExpressionField(elem, info, rinfos);
      }
   }

   private void renameCondition(Element condition, RenameInfo info, List<RenameInfo> rinfos) {
      NodeList dataRefs = Tool.getChildNodesByTagName(condition, "dataRef");

      for(int i = 0; i < dataRefs.getLength(); i++) {
         Element elem = (Element) dataRefs.item(i);
         renameDataRef(elem, info, rinfos);
      }
   }

   private void renameDataRef(Element dataRef, RenameInfo info, List<RenameInfo> rinfos) {
      String className = Tool.getAttribute(dataRef, "class");

      if(Objects.equals(className, "inetsoft.report.internal.binding.BaseField")) {
         String entity = Tool.getAttribute(dataRef, "entity");
         String nentity = getNewTableName(entity, info, rinfos);
         dataRef.setAttribute("entity", nentity);
         Element view = Tool.getChildNodeByTagName(dataRef, "view");
         replaceCDATANode(view, Tool.getValue(view).replace(entity, nentity));
      }
   }

   private void renameExpressionField(Element elem, RenameInfo info, List<RenameInfo> rinfos) {
      String type = Tool.getAttribute(elem, "type");

      if(Objects.equals(type, XExpression.FIELD)) {
         String field = Tool.getValue(elem);
         String otable = field.lastIndexOf(".") != -1 ?
            field.substring(0, field.lastIndexOf(".")) : field;
         String col = field.substring(field.lastIndexOf(".") + 1);
         String ntable = getNewTableName(otable, info, rinfos);
         replaceCDATANode(elem, ntable + "." + col);
      }
   }

   private void renameColumnItem(Element item, RenameInfo info) {
      if(Tool.getValue(item) == null) {
         return;
      }

      String itemValue = Tool.byteDecode(Tool.getValue(item));
      String tableName = itemValue.substring(0, itemValue.lastIndexOf("."));
      String oname = getOldTableName(tableName, info);
      String nname = getOldTableName(tableName, info);
      replaceCDATANode(item, Tool.byteEncode(itemValue.replace(oname, nname)));
   }

   private String getOldTableName(String tableName, RenameInfo info) {
      if(info.isTableOption()) {
         return tableName;
      }

      if(tableName == null) {
         return "";
      }

      int index = tableName.lastIndexOf(".");

      if(index != -1) {
         tableName = tableName.substring(index + 1);
      }

      return info.getOldName() + tableName;
   }

   private String getNewTableName(String tableName, RenameInfo info, List<RenameInfo> rinfos) {
      if(info.isTableOption()) {
         for(RenameInfo rinfo : rinfos) {
            if(Objects.equals(rinfo.getOldName(), tableName)) {
               return rinfo.getNewName();
            }
         }

         return tableName;
      }

      if(tableName == null) {
         return "";
      }

      int index = tableName.lastIndexOf(".");

      if(index != -1) {
         tableName = tableName.substring(index + 1);
      }

      return info.getNewName() + tableName;
   }

   @Override
   protected void renameVSColumn(Element elem, RenameInfo info) {
   }

   @Override
   protected void renameCellBinding(Element binding, RenameInfo info) {
   }

   @Override
   protected void renameTablePath(Element apath, RenameInfo info) {
   }

   @Override
   protected void renameVSTable(Element table, RenameInfo info) {
      renameVSColumns(table, info);
   }

   @Override
   protected void renameVSBinding(Element binding, RenameInfo info) {
   }

   @Override
   protected boolean renameWSColumn(Element elem, RenameInfo info) {
      return true;
   }

   @Override
   protected String getWSColumnName(RenameInfo info, boolean old) {
      return null;
   }

   @Override
   protected void renameSelectionMeasureValue(Element assembly, RenameInfo info) {
   }

   @Override
   protected boolean isSameWSSource(Element elem, RenameInfo info) {
      // For rename column and entity, should check source is the same or not. Others no need.
      if(!info.isColumn() && !info.isEntity()) {
         return true;
      }

      NodeList list = getChildNodes(elem, "./assemblyInfo/source/sourceInfo");

      if(list != null && list.getLength() > 0) {
         Element sourceElem = (Element)list.item(0);
         Element preElem = Tool.getChildNodeByTagName(sourceElem, "prefix");
         Element srcElem = Tool.getChildNodeByTagName(sourceElem, "source");

         if(Tool.equals(Tool.getValue(preElem), info.getPrefix()) &&
            Tool.equals(Tool.getValue(srcElem), info.getSource()))
         {
            return true;
         }
      }

      NodeList list1 = getChildNodes(elem, ".//mirrorAssembly");

      if(list1 != null && list1.getLength() > 0) {
         Element mirror = (Element)list1.item(0);
         String src = Tool.getAttribute(mirror, "source");

         if(Tool.equals(src, info.getSource())) {
            return true;
         }
      }

      return false;
   }

   protected boolean isSameSource(Element elem, RenameInfo info) {
      // For rename column and entity, should check source is the same or not. Others no need.
      if(!info.isColumn() && !info.isEntity()) {
         return true;
      }

      NodeList list = getChildNodes(elem, "./bindingAttr/filter[@class='SourceAttr']");

      if(list == null || list.getLength() == 0) {
         return false;
      }

      Element sourceElem = (Element) list.item(0);
      String source = Tool.getAttribute(sourceElem, "source");
      String prefix = Tool.getAttribute(sourceElem, "prefix");

      return Tool.equals(source, info.getSource()) && Tool.equals(prefix, info.getPrefix());
   }

   private String getQualifiedTableName(String oname, String table) {
      XDataSource ds = getDataSource(oname, null);

      if(ds == null) {
         LOG.error(catalog.getString("notFind.database", oname));
         return null;
      }

      if(!(ds instanceof JDBCDataSource)) {
         return null;
      }

      String ntableName = null;
      JDBCDataSource dataSource = (JDBCDataSource) ds;
      DefaultMetaDataProvider provider = new DefaultMetaDataProvider();
      provider.setDataSource(getDataSource(oname, null));

      try {
         ntableName = DataDependencyTransformer.getQualifiedTableName(table,
            dataSource.getTableNameOption(), provider, null);
      }
      catch(Exception e) {
         LOG.error("Failed to get table: " + table);
      }

      return ntableName;
   }

   private static final String TABLE = "/TABLE";
   private Catalog catalog = Catalog.getCatalog();
   private static final Logger LOG = LoggerFactory.getLogger(AssetSQLTableDependencyTransformer.class);
}
