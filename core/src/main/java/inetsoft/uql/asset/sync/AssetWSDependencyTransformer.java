/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.asset.sync;

import inetsoft.report.Hyperlink;
import inetsoft.report.internal.Util;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.internal.FunctionIterator;
import inetsoft.uql.asset.internal.ScriptIterator;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.util.Tool;
import inetsoft.util.XMLTool;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.*;

/**
 * AssetWSDependencyTransformer is a class to rename dependenies for ws/vs binding worksheet
 *
 * @version 13.2
 * @author InetSoft Technology Corp
 */
public class AssetWSDependencyTransformer extends AssetHyperlinkDependencyTransformer {
   /**
    * Create a transformer to rename dependenies for the asset(ws/vs) binding worksheet.
    */
   public AssetWSDependencyTransformer(AssetEntry asset) {
      super(asset);
   }

   protected void renameAssetEntry(Element elem, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();
      Element pathNode = Tool.getChildNodeByTagName(elem, "path");
      Element userNode = Tool.getChildNodeByTagName(elem, "user");
      String scope = elem.getAttribute("scope");

      if(info.isSource()) {
         String val = Tool.getValue(pathNode);
         String user = Tool.getValue(userNode);
         AssetEntry oentry = AssetEntry.createAssetEntry(oname);
         AssetEntry nentry = AssetEntry.createAssetEntry(nname);

         if(oentry == null || nentry == null) {
            return;
         }

         String opath = oentry.getPath();
         String npath = nentry.getPath();

         if(Tool.equals(val, opath) && Tool.equals(user, oentry.getUser()) &&
            Tool.equals(scope, oentry.getScope() + ""))
         {
            replaceChildValue(elem, "path", opath, npath, true);
            replaceChildValue(elem, "description", oentry.getDescription(), nentry.getDescription(),
               false);
            replacePropertyNode0(elem, "localStr", oentry.getName(), nentry.getName(), true);
            replacePropertyNode0(elem, "_description_", oentry.getDescription(),
               nentry.getDescription(), false);
            syncScope(elem, oentry, nentry);
         }
      }
      else if(info.isDataSource()) {
      }
      else if(info.isDataSourceFolder()) {
      }
      else if(info.isFolder()) {
         replaceChildValue(elem, "path", oname, nname, false);
         replaceChildValue(elem, "description", oname, nname, false);
         replacePropertyNode0(elem, "_description_", oname, nname, false);
      }
   }

   private void syncScope(Element elem, AssetEntry oentry, AssetEntry nentry) {
      if(oentry.getScope() == nentry.getScope()) {
         return;
      }

      replaceAttribute(elem, "scope", oentry.getScope() + "",
         nentry.getScope() + "", true);

      if(oentry.getScope() == AssetRepository.GLOBAL_SCOPE) {
         Element node = Tool.getChildNodeByTagName(elem, "user");

         if(node == null) {
            node = elem.getOwnerDocument().createElement("user");
            Node cdata = elem.getOwnerDocument().createCDATASection(nentry.getUser().convertToKey());
            elem.appendChild(node);
            node.appendChild(cdata);
         }
         else {
            replaceCDATANode(node, nentry.getUser().convertToKey());
         }
      }
      else if(oentry.getScope() == AssetRepository.USER_SCOPE) {
         Element node = Tool.getChildNodeByTagName(elem, "user");

         if(node != null) {
            replaceCDATANode(node, nentry.getUser().convertToKey());
         }
      }
   }

   @Override
   protected void renameVSTable(Element doc, RenameInfo info) {
      renameCalcSource(doc, info);
      renameAggrSource(doc, info);
      renameSourceInfos(doc, info);
      renameSelectionTables(doc, info);
      renameVSBindingInfos(doc, info);
      renameVSAndAssemblyScript(doc, info);
      renameVSCalcTableCellAssetNamedGroup(doc, info);
   }

   private void renameVSCalcTableCellAssetNamedGroup(Element doc, RenameInfo info) {
      if(!isSameWSSource(doc, info)) {
         return;
      }

      NodeList childNodes = getChildNodes(doc, ".//layoutRegion/region/cell/cellBinding");

      if(childNodes == null) {
         return;
      }

      for(int i = 0; i < childNodes.getLength(); i++) {
         Element item = (Element) childNodes.item(i);

         if(item == null) {
            continue;
         }

         Element namedGroupEle = getChildNode(item, "./groupSort/namedgroups");

         if(namedGroupEle == null) {
            continue;
         }

         String type = Tool.getAttribute(namedGroupEle, "type");

         if(!(XNamedGroupInfo.ASSET_NAMEDGROUP_INFO_REF + "").equals(type)) {
            continue;
         }

         String source = Tool.getAttribute(namedGroupEle, "source");

         if(!Tool.equals(source, info.getSource())) {
            continue;
         }

         Element childNode = getChildNode(namedGroupEle, "name");

         if(Tool.equals(Tool.getValue(childNode), info.getOldName())) {
            replaceCDATANode(childNode, info.getNewName());
         }
      }
   }

   protected void renameVSColumns(Element assembly, RenameInfo info, boolean isAllAggs,
                                  boolean isBookMark)
   {
      super.renameVSColumns(assembly, info, isAllAggs, isBookMark);
      renameVSAndAssemblyScript(assembly, info);
   }

   @Override
   protected void renameWSSources(Element doc, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();
      NodeList list = getChildNodes(doc, "//assemblies/oneAssembly/mirrorAssembly |" +
         "//assemblies/oneAssembly/assembly/assemblyInfo/mirrorAssembly/mirrorAssembly");

      for(int i = 0; i < list.getLength(); i++) {
         Element mirror = (Element) list.item(i);

         if(info.isSource()) {
            if(Tool.equals(oname, Tool.getAttribute(mirror, "source"))) {
               replaceAttribute(mirror, "source", oname, nname, true);
               replaceAttribute(mirror, "description", oname, nname, true);
               replaceChildValue(mirror, "assetDependency", oname, nname, true);
            }
         }
         else if(info.isFolder()) {
            replaceAttribute(mirror, "description", oname, nname, false);
            replaceAttribute(mirror, "source", oname, nname, false);
            replaceChildValue(mirror, "assetDependency", oname, nname, false);
         }
      }
   }

   @Override
   protected void renameWSSource(Element doc, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();
      NodeList list = getChildNodes(doc, ".//mirrorAssembly");

      for(int i = 0; i < list.getLength(); i++) {
         Element mirror = (Element) list.item(i);

         if(info.isSource()) {
            replaceAttribute(mirror, "source", oname, nname, true);
            replaceChildValue(mirror, "assetDependency", oname, nname, true);
         }
         else if(info.isFolder()) {
            replaceAttribute(mirror, "description", oname, nname, false);
            replaceAttribute(mirror, "source", oname, nname, false);
            replaceChildValue(mirror, "assetDependency", oname, nname, false);
         }
      }
   }

   @Override
   protected boolean renameWSColumn(Element elem, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();
      String attr = Tool.getAttribute(elem, "attribute");

      if(Tool.equals(oname, attr)) {
         replaceAttribute(elem, "attribute", oname, nname, true);
         replaceChildValue(elem, "view", oname, nname, false);

         return true;
      }

      return false;
   }

   @Override
   protected void renameExpressionRef(Element elem, RenameInfo info, String spliter) {
      String oname = info.getOldName();
      String nname = info.getNewName();

      if(info.isColumn()) {
         super.transformExpression(elem,
                 new RenameInfo(oname, nname, info.getType(), info.getSource()));
      }
   }

   @Override
   protected void renameAutoDrill(Element doc, RenameInfo rinfo) {
      NodeList list = getChildNodes(doc, ".//XDrillInfo/drillPath");

      for(int i = 0; i < list.getLength(); i++) {
         Element elem = (Element) list.item(i);

         String oname = rinfo.getOldName();
         String nname = rinfo.getNewName();
         AssetEntry oentry = AssetEntry.createAssetEntry(oname);
         AssetEntry nentry = AssetEntry.createAssetEntry(nname);

         if((Hyperlink.VIEWSHEET_LINK + "").equals(Tool.getAttribute(elem, "linkType"))) {
            replaceAttribute(elem, "link", oname, nname, true);
         }

         NodeList assets = getChildNodes(elem, "./subquery/worksheetEntry/assetEntry");

         for(int j = 0; j < assets.getLength(); j++) {
            Element assetElem = (Element) assets.item(j);
            Element path = Tool.getChildNodeByTagName(assetElem, "path");
            String pathVal = Tool.getValue(path);

            if(!rinfo .isColumn() && Tool.equals(pathVal, oentry.getPath())) {
               XMLTool.replaceValue(path, nentry.getPath());
            }
         }

         NodeList fieldNodes = getChildNodes(elem, "./parameterField");

         if(fieldNodes == null || fieldNodes.getLength() == 0 && !rinfo .isColumn()) {
            return;
         }

         for(int j = 0; j < fieldNodes.getLength(); j++) {
            Element fieldNode = (Element) fieldNodes.item(j);
            String field = Tool.getAttribute(fieldNode, "field");

            if(Tool.equals(oname, field)) {
               replaceAttribute(fieldNode, "field", oname, nname, true);
            }
         }
      }
   }

   private void renameVSAndAssemblyScript(Element doc, RenameInfo info) {
      NodeList list = getChildNodes(doc,
         "//assemblies/oneAssembly/assembly/assemblyInfo/script");
      renameScript(list, info);
      list = getChildNodes(doc,
         "//viewsheetInfo/viewsheetInfo/initScript");
      renameScript(list, info);
   }

   private void renameScript(NodeList scriptList, RenameInfo info) {
      for(int i = 0; i < scriptList.getLength(); i++) {
         Element scriptEle = (Element) scriptList.item(i);

         if(info.isTable() || info.isColumn()) {
            if(scriptEle != null) {
               String nscript = Util.renameScriptDepended(info.getOldName(),
                  info.getNewName(), Tool.getValue(scriptEle));

               if(nscript != null) {
                  replaceCDATANode(scriptEle, nscript);
               }

               replaceCDATANode(scriptEle, renameRunQueryFunctionScript(Tool.getValue(scriptEle), info));
            }
         }
      }
   }

   private String renameRunQueryFunctionScript(String script, RenameInfo info) {
      if(script == null) {
         return null;
      }

      final StringBuilder oldParas = new StringBuilder();
      final StringBuilder newParas = new StringBuilder();

      ScriptIterator.ScriptListener listener = (ScriptIterator.Token token, ScriptIterator.Token pref,
                                                ScriptIterator.Token cref) ->
      {
         if(!Tool.isEmptyString(oldParas.toString()) && !Tool.isEmptyString(newParas.toString())) {
            return;
         }

         if(pref != null && pref.isRunQuery() && token != null) {
            try {
               AssetEntry entry = AssetEntry.createAssetEntry(token.val);

               if(entry.isWorksheet() && Tool.equals(entry.toIdentifier(), info.getSource()) &&
                  Tool.equals(info.getOldName(), entry.getProperty("table.name")))
               {
                  oldParas.append(token.val);
                  entry.setProperty("table.name", info.getNewName());
                  newParas.append(entry.getScriptRunQueryidentifier());

               }
            }
            catch(Exception ignore) {
            }
         }
      };

      FunctionIterator iterator = new FunctionIterator(script);
      iterator.addScriptListener(listener);
      iterator.iterate();

      if(!Tool.isEmptyString(newParas.toString()) && !Tool.isEmptyString(oldParas.toString())) {
         return script.replace(oldParas, newParas);
      }

      return script;
   }
}
