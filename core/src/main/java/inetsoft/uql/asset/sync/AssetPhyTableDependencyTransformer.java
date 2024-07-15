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
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * AssetLMDependencyTransformer is a class to rename dependenies for ws/vs binding logic model
 *
 * @version 13.2
 * @author InetSoft Technology Corp
 */
public class AssetPhyTableDependencyTransformer extends AssetDependencyTransformer {
   /**
    * Create a transformer to rename dependenies for the asset(ws/vs) binding model.
    */
   public AssetPhyTableDependencyTransformer(AssetEntry asset) {
      super(asset);
   }

   protected void renameAssetEntry(Element elem, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();
      Element pathNode = Tool.getChildNodeByTagName(elem, "path");

      String oname1 = oname.replace(".", "/");
      String nname1 = nname.replace(".", "/");

      String oname2 = oname.replace(".", "^_^");
      String nname2 = nname.replace(".", "^_^");
      String dataSource = Catalog.getCatalog().getString("Data Source");

      if(info.isSource()) {
         replaceAssetAttr(pathNode, oname1, nname1);
         Element descriptionNode = Tool.getChildNodeByTagName(elem, "description");
         replaceAssetAttr(descriptionNode, oname1, nname1);
         Element propertiesNode = Tool.getChildNodeByTagName(elem, "properties");
         NodeList list = Tool.getChildNodesByTagName(propertiesNode, "property");

         for(int i = 0; i < list.getLength(); i++){
            Element prop = (Element) list.item(i);
            Element keyElem = Tool.getChildNodeByTagName(prop, "key");

            if(keyElem == null) {
               keyElem = Tool.getChildNodeByTagName(prop, "name");
            }

            String keyVal = Tool.getValue(keyElem);

            // Only vs binding will call source to transform asset entry. Should not change source
            // name for source name only table name not including catalog + schema
            if(Tool.equals("entry.paths", keyVal)) {
               Element valElem = Tool.getChildNodeByTagName(prop, "value");
               replaceAssetAttr(valElem, oname2, nname2);
            }
            else if(Tool.equals("Tooltip", keyVal)) {
               Element valElem = Tool.getChildNodeByTagName(prop, "value");
               replaceAssetAttr(valElem, oname, nname);
            }
            else if(Tool.equals("source", keyVal)) {
               Element valElem = Tool.getChildNodeByTagName(prop, "value");
               replaceAssetAttr(valElem, oname, nname);
            }
         }
      }
      else if(info.isDataSource()) {
         Element descriptionNode = Tool.getChildNodeByTagName(elem, "description");
         Element propertyNode = Tool.getChildNodeByTagName(elem, "properties");

         replaceDataSource(pathNode, oname, nname);
         replaceDataSource(descriptionNode, dataSource + "/" + oname, dataSource + "/" + nname);
         replacePropertyNode1(propertyNode, "entry.paths", oname, nname, false);
         replacePropertyNode1(propertyNode, "prefix", oname, nname, true);
      }
      else if(info.isDataSourceFolder()) {
         Element descriptionNode = Tool.getChildNodeByTagName(elem, "description");
         Element propertyNode = Tool.getChildNodeByTagName(elem, "properties");

         String prefix = getPropertyValue(propertyNode, "prefix");

         if(getAssetFile() != null && !Tool.equals(prefix, oname)) {
            return;
         }

         replaceDataSource(pathNode, oname, nname);
         replaceDataSource(descriptionNode, oname, nname);
         replacePropertyNode1(propertyNode, "entry.paths", oname, nname, false);
         replacePropertyNode1(propertyNode, "prefix", oname, nname, true);
      }
   }

   protected boolean isSameVSSource0(Element doc, RenameInfo info) {
      return true;
   }

   protected void renameSourceInfos(Element doc, RenameInfo info) {
      // Only vs binding will call source to transform asset entry. Should not change source
      // name for source name only table name not including catalog + schema
      if(info.isSource()) {
         return;
      }

      if(info.isDataSource() || info.isDataSourceFolder()) {
         super.renameSourceInfos(doc, info);
      }
   }

   // Only vs binding will call source to transform asset entry. Should not change source
   // name for source name only table name not including catalog + schema
   protected void renameCalcSource(Element doc, RenameInfo info) {
   }

   @Override
   protected void renameWSSources(Element doc, RenameInfo info) {
      super.renameWSSources(doc, info);

      if(info.isDataSource() || info.isDataSourceFolder()) {
         StringBuilder builder = new StringBuilder();
         builder.append("//assemblies/oneAssembly/assembly/assemblyInfo/datasource |");
         builder.append("//assemblies/oneAssembly/assembly/assemblyInfo/query/query_jdbc/datasource");
         NodeList list = getChildNodes(doc, builder.toString());

         for(int i = 0; i < list.getLength(); i++) {
            Element elem = (Element) list.item(i);
            String value = Tool.getValue(elem);

            if(Tool.equals(value, info.getOldName())) {
               replaceCDATANode(elem, info.getNewName());
            }

            String name = Tool.getAttribute(elem, "name");

            if(Tool.equals(name, info.getOldName())) {
               elem.setAttribute("name", info.getNewName());
            }
         }
      }
   }

   @Override
   protected void renameWSSource(Element elem, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();

      if(info.isSource()) {
         Element pnode = Tool.getChildNodeByTagName(elem, "prefix");
         String opre = Tool.getValue(pnode);

         if(!Tool.equals(opre, info.getPrefix())) {
            return;
         }

         Element snode = Tool.getChildNodeByTagName(elem, "source");

         if(Tool.equals(oname, Tool.getValue(snode))) {
            replaceCDATANode(snode, nname);
         }
      }
      else if(info.isDataSource() || info.isDataSourceFolder()) {
         Element node = Tool.getChildNodeByTagName(elem, "prefix");
         String opre = Tool.getValue(node);

         if(Tool.equals(opre, oname)) {
            replaceCDATANode(node, nname);
         }

         node = Tool.getChildNodeByTagName(elem, "source");
         opre = Tool.getValue(node);

         if(Tool.equals(opre, oname)) {
            replaceCDATANode(node, nname);
         }
      }
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
}