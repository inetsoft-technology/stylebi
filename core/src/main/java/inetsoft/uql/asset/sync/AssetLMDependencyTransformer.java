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
package inetsoft.uql.asset.sync;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.Tool;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.*;

import java.util.List;
import java.util.Objects;

/**
 * AssetLMDependencyTransformer is a class to rename dependenies for ws/vs binding logic model
 *
 * @version 13.2
 * @author InetSoft Technology Corp
 */
public class AssetLMDependencyTransformer extends AssetDependencyTransformer {
   /**
    * Create a transformer to rename dependenies for the asset(ws/vs) binding model.
    */
   public AssetLMDependencyTransformer(AssetEntry asset) {
      super(asset);
   }

   protected void renameAssetEntry(Element elem, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();
      Element pathNode = Tool.getChildNodeByTagName(elem, "path");

      if(info.isSource()) {
         replaceAssetAttr(pathNode, oname, nname);
         Element descriptionNode = Tool.getChildNodeByTagName(elem, "description");
         replaceAssetAttr(descriptionNode, oname, nname);
         Element propertiesNode = Tool.getChildNodeByTagName(elem, "properties");
         NodeList list = Tool.getChildNodesByTagName(propertiesNode, "property");

         for(int i = 0; i < list.getLength(); i++){
            Element prop = (Element) list.item(i);
            Element keyElem = Tool.getChildNodeByTagName(prop, "key");

            if(keyElem == null) {
               keyElem = Tool.getChildNodeByTagName(prop, "name");
            }

            String keyVal = Tool.getValue(keyElem);

            if(Tool.equals("source", keyVal)) {
               Element valElem = Tool.getChildNodeByTagName(prop, "value");
               replaceCDATANode(valElem, nname);
            }
            else if(Tool.equals("entry.paths", keyVal)) {
               Element valElem = Tool.getChildNodeByTagName(prop, "value");
               replaceAssetAttr(valElem, oname, nname);
            }
         }
      }
      else if(info.isDataSource()) {
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
      else if(info.getType() == (RenameInfo.LOGIC_MODEL | RenameInfo.FOLDER)) {
         renameLMFolder(elem, oname, nname);
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
      else if(info.isDataSource()) {
         Element pnode = Tool.getChildNodeByTagName(elem, "prefix");
         String opre = Tool.getValue(pnode);

         if(opre != null && opre.endsWith(oname)) {
            replaceCDATANode(pnode, opre.replace(oname, nname));
         }
      }
      else if(info.isDataSourceFolder()) {
         Element pnode = Tool.getChildNodeByTagName(elem, "prefix");
         String opre = Tool.getValue(pnode);

         if(Tool.equals(opre, oname)) {
            replaceCDATANode(pnode, nname);
         }
      }
      else if(info.getType() == (RenameInfo.LOGIC_MODEL | RenameInfo.FOLDER)) {
         renameLMFolder(elem, oname, nname);
      }
   }

   /**
    * Transform expression.
    * @param spliter  the spliter in entity and attribute.
    *                 ".": default, ws
    *                 ":": vs
    */
   @Override
   protected void renameExpressionRef(Element elem, RenameInfo info, String spliter) {
      String oname = info.getOldName();
      String nname = info.getNewName();

      if(info.isColumn()) {
         if(":".equals(spliter)) {
            oname = Tool.replace(oname, ".", ":");
            nname = Tool.replace(nname, ".", ":");
         }

         super.transformExpression(elem,
            new RenameInfo(oname, nname, info.getType(), info.getSource()));
      }
      else if(info.isEntity()) {
         oname = oname + spliter;
         nname = nname + spliter;

         super.transformExpression(elem,
            new RenameInfo(oname, nname, info.getType(), info.getSource()));
      }
   }

   @Override
   protected void renameVSColumn(Element elem, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();

      if(info.isColumn()) {
         String oname1 = replaceLast(oname, ".", ":");
         String nname1 = replaceLast(nname, ".", ":");

         super.renameVSColumn(elem, new RenameInfo(oname1, nname1, info.getType(),
            info.getSource()));
      }
      else if(info.isEntity()) {
         renamVSColumnEntity(elem, info);
      }
   }

   private String replaceLast(String str, String target, String replacement) {
      if(str == null) {
         return null;
      }

      int index = str.lastIndexOf(target);

      if(index < 0) {
         return str;
      }

      return str.substring(0, index) + str.substring(index).replace(target, replacement);
   }

   private void renamVSColumnEntity(Element elem, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();

      if("inetsoft.uql.erm.ExpressionRef".equals(Tool.getAttribute(elem, "class"))) {
         renameExpressionRef(elem, info, ":");
         return;
      }

      String oattr = Tool.getAttribute(elem, "attribute");

      if(oattr != null && oattr.startsWith(oname + ":")) {
         elem.setAttribute("attribute", oattr.replace(oname, nname));
      }

      Element view = Tool.getChildNodeByTagName(elem, "view");
      String val = Tool.getValue(view);

      if(val != null && val.startsWith(oname + ":")) {
         replaceCDATANode(view, val.replace(oname, nname));
      }

      String oname0 = oname + ":";
      String nname0 = nname + ":";
      Element refVal = Tool.getChildNodeByTagName(elem, "refValue");

      if(refVal != null) {
         String refValue = Tool.getValue(refVal);

         // fix VSAggregate
         if(refValue != null && refValue.startsWith(oname0)) {
            replaceChildValue(elem, "refValue", oname0, nname0, false);
            replaceChildValue(elem, "refRValue", oname0, nname0, false);
            replaceChildValue(elem, "view", oname0, nname0, false);
            replaceChildValue(elem, "fullName", oname0, nname0, false);
            replaceChildValue(elem, "oriFullName", oname0, nname0, false);
         }
      }

      Element secondaryVal = Tool.getChildNodeByTagName(elem, "secondaryValue");

      if(secondaryVal != null) {
         String secondValue = Tool.getValue(secondaryVal);

         // fix VSAggregate secondaryColumn
         if(secondValue != null && secondValue.startsWith(oname0)) {
            replaceChildValue(elem, "secondaryValue", oname0, nname0, false);
            replaceChildValue(elem, "secondaryRValue", oname0, nname0, false);
            replaceChildValue(elem, "fullName", oname0, nname0, false);
            replaceChildValue(elem, "oriFullName", oname0, nname0, false);
         }
      }

      Element groupVal = Tool.getChildNodeByTagName(elem, "groupValue");

      if(groupVal != null) {
         String groupValue = Tool.getValue(groupVal);

         // fix VSDimensionRef
         if(groupValue != null && groupValue.startsWith(oname0)) {
            replaceChildValue(elem, "groupValue", oname0, nname0, false);
            replaceChildValue(elem, "groupRValue", oname0, nname0, false);
            replaceChildValue(elem, "view", oname0, nname0, false);
         }
      }

      Element rankColVal = Tool.getChildNodeByTagName(elem, "rankingColValue");

      if(rankColVal != null) {
         String rankColValue = Tool.getValue(rankColVal);

         // fix VSDimensionRef
         if(rankColValue != null && rankColValue.indexOf(oname0) != -1) {
            replaceChildValue(elem, "rankingColValue", oname0, nname0, false);
            replaceChildValue(elem, "rankingColRValue", oname0, nname0, false);
         }
      }
   }

   @Override
   protected void renameCellBinding(Element binding, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();

      if(info.isColumn()) {
         String oname1 = oname.replace(".", ":");
         String nname1 = nname.replace(".", ":");

         super.renameCellBinding(binding, new RenameInfo(oname1, nname1, info.getType(),
            info.getSource(), info.getTable()));
      }
      else if(info.isEntity()) {
         Element valueNode = Tool.getChildNodeByTagName(binding, "value");
         String val = Tool.getValue(valueNode);

         if(val != null && val.startsWith(oname + ":")) {
            replaceCDATANode(valueNode, val.replace(oname + ":", nname + ":"));
         }

         Element formulaNode = Tool.getChildNodeByTagName(binding, "formula");
         String fval = Tool.getValue(formulaNode);

         // include formula value, not start with column name
         if(fval != null && fval.indexOf(oname + ":") != -1) {
            replaceCDATANode(formulaNode, fval.replace(oname + ":", nname + ":"));
         }
      }
   }

   @Override
   protected void renameTablePath(Element apath, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();

      if(info.isColumn()) {
         String oname1 = oname.replace(".", ":");
         String nname1 = nname.replace(".", ":");

         super.renameTablePath(apath, new RenameInfo(oname1, nname1, info.getType(),
            info.getSource(), info.getTable()));
      }
      else if(info.isEntity()) {
         String val = Tool.getValue(apath);

         if(val != null && val.startsWith(oname + ":")) {
            replaceCDATANode(apath, val.replace(oname, nname));
         }
      }
   }

   @Override
   protected void renameVSTable(Element table, RenameInfo info) {
      renameVSColumns(table, info);
   }

   @Override
   protected void renameVSBinding(Element binding, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();

      if(info.isColumn() || info.isSource()) {
         super.renameVSBinding(binding, info);
      }
      else if(info.isEntity()) {
         Element col = Tool.getChildNodeByTagName(binding, "columnValue");
         String cval = Tool.getValue(col);

         if(cval != null && cval.startsWith(oname + ":")) {
            replaceCDATANode(col, cval.replace(oname, nname));
         }

         Element view = Tool.getChildNodeByTagName(binding, "view");
         String val = Tool.getValue(view);

         if(val != null && val.startsWith(oname + ":")) {
            replaceCDATANode(view, val.replace(oname, nname));
         }

         Element col2 = Tool.getChildNodeByTagName(binding, "column2Value");
         String val2 = Tool.getValue(col2);

         if(val2 != null && val2.startsWith(oname + ":")) {
            replaceCDATANode(col2, val2.replace(oname, nname));
         }
      }
   }

   @Override
   protected void renameWSTable(Element table, RenameInfo info,
                                List<RenameInfo> causeRenameInfos, NodeList assemblies)
   {
      renameWSColumns(table, info, causeRenameInfos, assemblies, null);
   }

   @Override
   protected boolean renameWSColumn(Element elem, RenameInfo info) {
      boolean columnRenamed = false;
      String oname = info.getOldName();
      String nname = info.getNewName();

      if(info.isColumn()) {
         String entity = info.getTable();
         String oentity = info.getOldEntity();
         oentity = oentity == null ? entity : oentity;
         String oattribute = oname.substring(oentity.length() + 1);
         String nattribute = nname.substring(entity.length() + 1);

         if(Tool.equals(Tool.getAttribute(elem, "entity"), oentity) &&
            Tool.equals(Tool.getAttribute(elem, "attribute"), oattribute))
         {
            elem.setAttribute("attribute", nattribute);
            elem.setAttribute("entity", entity);
            columnRenamed = true;
         }

         Element view = Tool.getChildNodeByTagName(elem, "view");
         String val = Tool.getValue(view);

         if(val != null && Tool.equals(val, oname)) {
            replaceCDATANode(view, nname);
         }
      }
      else if(info.isTable()) {
         if(Tool.equals(Tool.getAttribute(elem, "entity"), oname)) {
            elem.setAttribute("entity", nname);
            columnRenamed = true;
         }

         Element view = Tool.getChildNodeByTagName(elem, "view");
         String val = Tool.getValue(view);

         if(val != null && val.contains(oname)) {
            replaceCDATANode(view, val.replace(oname, nname));
         }
      }

      return columnRenamed;
   }

   @Override
   protected void renameDependExpressionRef(NodeList assemblies, RenameInfo info,
                                            String assemblyName)
   {
      if(assemblies.getLength() < 1 || StringUtils.isEmpty(assemblyName) || !info.isColumn()) {
         return;
      }

      String oattribute = getWSColumnName(info, true);
      String nattribute = getWSColumnName(info, false);

      RenameInfo nInfo = new RenameInfo(assemblyName + SPLIT + oattribute,
         assemblyName + SPLIT + nattribute, RenameInfo.COLUMN);
      // ws expression can only using col. e.g. field['Total']
      RenameInfo nInfoOnlyColumnName = new RenameInfo(oattribute, nattribute, RenameInfo.COLUMN);

      for(int i = 0; i < assemblies.getLength(); i++) {
         Element assembly = (Element) assemblies.item(i);
         doRenameDependExpressionRef(assembly, assemblyName, nInfo, nInfoOnlyColumnName);
      }
   }

   /**
    * rename expression if matched.
    * e.g.
    * 1. field['Annual Summary.Total']
    * 2. field['Total']
    */
   private void doRenameDependExpressionRef(Element assembly, String entity,
                                            RenameInfo info, RenameInfo infoOnlyColumnName)
   {
      NodeList exps = getChildNodes(
         assembly, ".//dataRef[@class='inetsoft.uql.erm.ExpressionRef']");
      NodeList cols = getChildNodes(
         assembly, ".//ColumnSelection//dataRef/dataRef[@entity='" + entity + "']");

      for(int i = 0; i < exps.getLength(); i++) {
         Element exp = (Element) exps.item(i);
         renameExpressionRef(exp, info, SPLIT);

         if(matchOnlyColumnExp(cols, info)) {
            renameExpressionRef(exp, infoOnlyColumnName, SPLIT);
         }
      }
   }

   private boolean matchOnlyColumnExp(NodeList cols, RenameInfo info) {
      String oattribute = getWSColumnName(info, true);
      String nattribute = getWSColumnName(info, false);
      String attribute;
      Element col;

      for(int i = 0; i < cols.getLength(); i++) {
         col = (Element) cols.item(i);
         attribute = Tool.getAttribute(col, "attribute");

         // maybe has renamed.
         if(Objects.equals(attribute, oattribute) || Objects.equals(attribute, nattribute)) {
            return true;
         }
      }

      return false;
   }

   @Override
   protected String getWSColumnName(RenameInfo info, boolean old) {
      String columnName = old ? info.getOldName() : info.getNewName();

      if(StringUtils.isEmpty(columnName)) {
         return columnName;
      }

      if(info.isLogicalModel()) {
         String entity = old ? info.getOldEntity() : info.getTable();
         entity = entity == null && old ? info.getTable() : entity;

         if(entity != null) {
            return columnName.substring(entity.length() + 1);
         }
      }

      int index = columnName.indexOf(".");

      if(index < 0) {
         return columnName;
      }

      return columnName.substring(index + 1);
   }

   @Override
   protected void renameSelectionMeasureValue(Element assembly, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();

      if(info.isColumn()) {
         String oname1 = oname.replace(".", ":");
         String nname1 = nname.replace(".", ":");

         super.renameSelectionMeasureValue(assembly,
            new RenameInfo(oname1, nname1, info.getType(), info.getSource()));
      }
      else if(info.isEntity()) {
         NodeList measures = getChildNodes(assembly, "./assemblyInfo/measure");

         for(int i = 0; i < measures.getLength(); i++) {
            Element measure = (Element) measures.item(i);
            String cval = Tool.getValue(measure);

            if(cval != null && cval.startsWith(oname + ":")) {
               replaceCDATANode(measure, cval.replace(oname, nname));
            }
         }

         NodeList measureValues = getChildNodes(assembly, "./assemblyInfo/measureValue");

         for(int i = 0; i < measureValues.getLength(); i++) {
            Element measureValue = (Element) measureValues.item(i);
            String cval = Tool.getValue(measureValue);

            if(cval != null && cval.startsWith(oname + ":")) {
               replaceCDATANode(measureValue, cval.replace(oname, nname));
            }
         }
      }
   }

   @Override
   protected boolean isSameWSSource(Element elem, RenameInfo info) {
      // For rename column and entity, should check source is the same or not. Others no need.
      if(!info.isColumn() && !info.isEntity()) {
         return true;
      }

      if("inetsoft.uql.asset.DefaultVariableAssembly".equals(elem.getAttribute("class"))) {
         NodeList tlist = getChildNodes(elem, "./variable/tableAssembly");

         if(tlist.getLength() == 0) {
            return false;
         }

         Element telem = (Element) tlist.item(0);
         String tableAssembly = Tool.getValue(telem);

         if(StringUtils.isEmpty(tableAssembly)) {
            return false;
         }

         Node parent = elem.getParentNode().getParentNode();
         NodeList tableInfoList = getChildNodes((Element) parent, "./oneAssembly/assembly/assemblyInfo");

         for(int i = 0; i < tableInfoList.getLength(); i++) {
            Element tableInfo = (Element) tableInfoList.item(0);
            Element nameElem = Tool.getChildNodeByTagName(tableInfo, "name");

            if(Tool.equals(tableAssembly, Tool.getValue(nameElem))) {
               NodeList list = getChildNodes((Element) tableInfo, "./source/sourceInfo");

               for(int j = 0; j < list.getLength(); j++) {
                  Element sourceElem = (Element) list.item(0);

                  if(isSameSource0(sourceElem, info)) {
                     return true;
                  }
               }
            }
         }

         return false;
      }

      NodeList list = getChildNodes(elem, "./assemblyInfo/source/sourceInfo | ./attachedAssembly/source/sourceInfo");

      if(list != null && list.getLength() > 0) {
         Element sourceElem = (Element) list.item(0);

         if(isSameSource0(sourceElem, info)) {
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

   private boolean isSameSource0(Element sourceElem, RenameInfo info) {
      Element preElem = Tool.getChildNodeByTagName(sourceElem, "prefix");
      Element srcElem = Tool.getChildNodeByTagName(sourceElem, "source");

      if(Tool.equals(Tool.getValue(preElem), info.getPrefix()) &&
         Tool.equals(Tool.getValue(srcElem), info.getSource()))
      {
         return true;
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

   private void renameLMFolder(Element elem, String oname, String nname) {
      Element pathNode = Tool.getChildNodeByTagName(elem, "path");
      Element descriptionNode = Tool.getChildNodeByTagName(elem, "description");
      Element entryPathPropNode = null;
      NodeList propNodeList = getChildNodes(elem, "./properties/property");
      String prefix = null;
      String source = null;

      for(int i = 0; i < propNodeList.getLength(); i++){
         Element prop = (Element) propNodeList.item(i);
         Element keyElem = Tool.getChildNodeByTagName(prop, "key");
         Element valueElem = Tool.getChildNodeByTagName(prop, "value");
         String key = Tool.getValue(keyElem);
         String value = Tool.getValue(valueElem);

         if("prefix".equals(key)) {
            prefix = value;
         }
         else if("source".equals(key)) {
            source = value;
         }
         else if("entry.paths".equals(key)) {
            entryPathPropNode = valueElem;
         }
         else if("folder".equals(key) && value != null) {
            replaceCDATANode(valueElem, value.replace(oname, nname));
         }
         else if("folder_description".equals(key) && value != null) {
            replaceCDATANode(valueElem, value.replace(oname, nname));
         }
         else if("__query_folder__".equals(key) && value != null) {
            replaceCDATANode(valueElem, value.replace(oname, nname));
         }
      }

      doRenameLMFolder(pathNode, prefix, source, oname, nname, "/", false);
      doRenameLMFolder(descriptionNode, prefix, source, oname, nname, "/", true);
      doRenameLMFolder(entryPathPropNode, prefix, source, oname, nname, "^_^", false);
   }

   private void doRenameLMFolder(Element node, String prefix, String suffix, String oname,
                                 String nname, String separator, boolean description)
   {
      if(node != null && Tool.getValue(node) != null) {
         String path = Tool.getValue(node);
         String oldPath = null;
         String newPath = null;

         if(prefix != null && suffix != null) {
            oldPath = prefix + separator + oname + separator + suffix;
            newPath = prefix + separator + nname + separator + suffix;
         }

         if(description) {
            oldPath = "Data Source/" + oldPath;
            newPath = "Data Source/" + newPath;
         }

         if(path.equals(oldPath)) {
            replaceCDATANode(node, newPath);
         }
      }
   }

   public static final String SPLIT = ".";
}