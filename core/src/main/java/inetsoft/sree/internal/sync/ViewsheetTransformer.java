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
package inetsoft.sree.internal.sync;

import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.UpdateDependencyHandler;
import inetsoft.util.Tool;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.*;

import java.io.File;

public class ViewsheetTransformer implements Transformer {
   public ViewsheetTransformer() {
      super();
   }

   public void process(String queryFolder, String queryName, File vsFile) {
      Document doc = UpdateDependencyHandler.getXmlDocByFile(vsFile);
      String queryPath = Tool.createPathString(queryFolder, queryName);
      updateWorksheetEntry(doc, queryPath);
      updateScript(doc.getDocumentElement(), queryName, queryFolder);
      updateFile(doc, vsFile);
   }

   public void processDocument(Document vsDoc, String queryPath) {
      updateWorksheetEntry(vsDoc, queryPath);
      String[] arr = Tool.split(queryPath, '/');
      String queryName = null;
      String folder = null;

      if(arr.length > 1) {
         folder = arr[0];
         queryName = arr[arr.length - 1];
      }
      else {
         queryName = queryPath;
      }

      updateScript(vsDoc.getDocumentElement(), queryName, folder);
   }

   private void updateWorksheetEntry(Document vsDoc, String queryPath) {
      Element root = vsDoc.getDocumentElement();
      NodeList list = UpdateDependencyHandler.getChildNodes(root, "//worksheetEntry/assetEntry | //assemblies/oneAssembly/assembly/worksheetEntry/assetEntry");

      for(int i = 0; i < list.getLength(); i++) {
         Element elem = (Element) list.item(i);

         if(!Tool.equals(AssetEntry.Type.QUERY.id() + "", elem.getAttribute("type"))) {
            continue;
         }

         Element pathElem = Tool.getChildNodeByTagName(elem, "path");

         if(pathElem == null) {
            continue;
         }

         String path = Tool.getValue(pathElem);

         if(path == null || !path.endsWith(queryPath)) {
            continue;
         }

         elem.setAttribute("type", AssetEntry.Type.WORKSHEET.id() + "");
         elem.setAttribute("scope", AssetRepository.GLOBAL_SCOPE + "");
         replaceElementCDATANode(pathElem, queryPath);
         Element properties = Tool.getChildNodeByTagName(elem, "properties");
         updateProperties(properties);
         addProperty(properties, "worksheet_type", Worksheet.TABLE_ASSET + "");
      }
   }

   private void updateProperties(Element properties) {
      NodeList propertyList = UpdateDependencyHandler.getChildNodes(properties, "./property");

      for(int j = 0; j < propertyList.getLength(); j++) {
         Element property = (Element) propertyList.item(j);
         Element keyElem = Tool.getChildNodeByTagName(property, "key");
         String key = Tool.getValue(keyElem);

         if("mainType".equals(key)) {
            Element valElem = Tool.getChildNodeByTagName(property, "value");

            if(valElem != null) {
               replaceElementCDATANode(valElem, "worksheet");
            }
         }
         else if("prefix".equals(key) || "entry.paths".equals(key) ||
            "folder_description".equals(key) || "subType".equals(key) ||
            "folder".equals(key)  || "type".equals(key))
         {
            properties.removeChild(property);
         }
         else if("entry.paths".equals(key)) {
            properties.removeChild(property);
         }
      }
   }

   private void addProperty(Element properties, String key, String value) {
      Document doc = properties.getOwnerDocument();
      Element property = doc.createElement("property");
      properties.appendChild(property);

      Element keyElem = doc.createElement("key");
      Element valElem = doc.createElement("value");
      property.appendChild(keyElem);
      property.appendChild(valElem);
      replaceElementCDATANode(keyElem, key);
      replaceElementCDATANode(valElem, value);
   }

   private void updateScript(Element root, String qname, String qfolder) {
      NodeList list = UpdateDependencyHandler.getChildNodes(root, "//viewsheet/assembly/viewsheetInfo/viewsheetInfo");

      if(list.getLength() == 0) {
         return;
      }

      Element vsInfo = (Element) list.item(0);
      Element initScript = Tool.getChildNodeByTagName(vsInfo, "initScript");
      String script = Tool.getValue(initScript);
      String nscript = replaceScript(script, qname, qfolder);

      if(!Tool.equals(script, nscript, true)) {
         replaceElementCDATANode(initScript, nscript);
      }

      Element loadScript = Tool.getChildNodeByTagName(vsInfo, "loadScript");
      script = Tool.getValue(initScript);
      nscript = replaceScript(script, qname, qfolder);

      if(!Tool.equals(script, nscript, true)) {
         replaceElementCDATANode(loadScript, nscript);
      }
   }

   private static String replaceScript(String script, String qname, String qfolder) {
      if(StringUtils.isEmpty(script) || script.indexOf("runQuery") == -1) {
         return script;
      }

      String path =  Tool.createPathString(qfolder, qname);
      path = "ws:global:" + path;
      script = script.replace("\"" + qname + "\"", "\"" + path + "\"");
      script = script.replace("'" + qname + "'", "'" + path + "'");
      return script;
   }
}
