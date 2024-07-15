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
package inetsoft.sree.internal.sync;

import inetsoft.util.dep.*;
import inetsoft.util.gui.ObjectInfo;
import inetsoft.uql.asset.internal.FunctionIterator;
import inetsoft.uql.asset.internal.ScriptIterator;
import inetsoft.uql.asset.sync.UpdateDependencyHandler;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.util.Tool;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.*;

import java.io.File;
import java.util.*;

public class QueryDependenciesFinder {

   /**
    *
    * @param file          the target file to collect query dependencies.
    * @param names         the name map from a ImportJarProperties.
    * @param dependenciesMap  key -> query name, value -> file name.
    * @param queryFolderMap  key -> query name, value -> folder.
    */
   public static void collectDependencies(File file, Map<String, String> names,
                                          Map<String, List<String>> dependenciesMap,
                                          Map<String, String> queryFolderMap)
   {
      String fileName = getFileName(file, names);
      String type = getAssetType(fileName);

      if(type == null) {
         return;
      }

      Document doc = UpdateDependencyHandler.getXmlDocByFile(file);
      Element element = doc.getDocumentElement();

      if(XQueryAsset.XQUERY.equals(type) && !QueryToWsConverter.isIgnoredQuery(file) ||
         XLogicalModelAsset.XLOGICALMODEL.equals(type) || XDataSourceAsset.XDATASOURCE.equals(type))
      {
         String qname = getQueryName(file, names);
         collectDependenciesForQuery(fileName, qname, element, dependenciesMap, queryFolderMap);
      }
      else if(WorksheetAsset.WORKSHEET.equals(type)) {
         collectDependenciesForWorksheet(fileName, element, dependenciesMap);
      }
      else if(ViewsheetAsset.VIEWSHEET.equals(type)) {
         collectDependenciesForViewsheet(fileName, element, dependenciesMap);
      }
   }

   private static String getAssetType(String fileName) {
      if(!Tool.isEmptyString(fileName)) {
         if(fileName.startsWith(XQueryAsset.XQUERY)) {
            return XQueryAsset.XQUERY;
         }
         else if(fileName.startsWith(XLogicalModelAsset.XLOGICALMODEL)) {
            return XLogicalModelAsset.XLOGICALMODEL;
         }
         else if(fileName.startsWith(WorksheetAsset.WORKSHEET)) {
            return WorksheetAsset.WORKSHEET;
         }
         else if(fileName.startsWith(ViewsheetAsset.VIEWSHEET)) {
            return ViewsheetAsset.VIEWSHEET;
         }
         else if(fileName.startsWith(XDataSourceAsset.XDATASOURCE)) {
            return XDataSourceAsset.XDATASOURCE;
         }
      }

      return null;
   }

   public static void collectDependenciesForQuery(String fileName, String qname, Element root,
                                                  Map<String, List<String>> dependenciesMap,
                                                  Map<String, String> queryFolderMap)
   {
      collectDependenciesForAutoDrills(fileName, root, dependenciesMap);
      NodeList list = UpdateDependencyHandler.getChildNodes(root, "//query/query_jdbc/folder");

      for(int i = 0; i < list.getLength(); i++) {
         Element folderElem = (Element) list.item(i);
         String folder = Tool.getValue(folderElem);

         if(!StringUtils.isEmpty(folder)) {
            queryFolderMap.put(qname, folder);
            break;
         }
      }
   }

   public static void collectDependenciesForAutoDrills(String fileName, Element root,
                                                       Map<String, List<String>> dependenciesMap)
   {
      NodeList list = UpdateDependencyHandler.getChildNodes(root, "//XDrillInfo/drillPath/subquery");

      for(int i = 0; i < list.getLength(); i++) {
         Element elem = (Element) list.item(i);
         String qname = elem.getAttribute("qname");

         if(!StringUtils.isEmpty(qname)) {
            dependenciesMap.computeIfAbsent(qname, k -> new ArrayList<>()).add(fileName);
         }
      }
   }

   public static void collectDependenciesForWorksheet(String fileName, Element root,
                                                      Map<String, List<String>> dependenciesMap) {
      StringBuilder builder = new StringBuilder();
      builder.append("//assemblies/oneAssembly/assembly/assemblyInfo/source/sourceInfo |");
      builder.append("//assemblies/oneAssembly/assembly/attachedAssembly/source/sourceInfo");
      NodeList list = UpdateDependencyHandler.getChildNodes(root, builder.toString());

      for(int i = 0; i < list.getLength(); i++) {
         Element sinfo = (Element) list.item(i);
         int type = Integer.parseInt(Tool.getAttribute(sinfo, "type"));

         if(type != XSourceInfo.QUERY) {
            continue;
         }

         Element sourceElem = Tool.getChildNodeByTagName(sinfo, "source");
         String qname = sourceElem == null ? null : Tool.getValue(sourceElem);

         if(!StringUtils.isEmpty(qname)) {
            dependenciesMap.computeIfAbsent(qname, k -> new ArrayList<>()).add(fileName);
         }
      }
   }

   private static void collectDependenciesForViewsheet(String fileName, Element root,
                                                       Map<String, List<String>> dependenciesMap)
   {
      StringBuilder builder = new StringBuilder();
      builder.append("//worksheetEntry/assetEntry | ");
      builder.append("//assemblies/oneAssembly/assembly/worksheetEntry/assetEntry");
      NodeList entryList = UpdateDependencyHandler.getChildNodes(root, builder.toString());

      for(int i = 0; i < entryList.getLength(); i++) {
         Element assetEntry = (Element) entryList.item(i);
         NodeList propertyList = UpdateDependencyHandler.getChildNodes(assetEntry, "./properties/property");
         String type = null;
         String source = null;

         for(int j = 0; j < propertyList.getLength(); j++) {
            Element property = (Element) propertyList.item(j);
            Element keyE = Tool.getChildNodeByTagName(property, "key");
            String key = Tool.getValue(keyE);

            if("mainType".equals(key)) {
               Element valE = Tool.getChildNodeByTagName(property, "value");
               type = Tool.getValue(valE);
            }
            else if("source".equals(key)) {
               Element valE = Tool.getChildNodeByTagName(property, "value");
               source = Tool.getValue(valE);
            }
         }

         if(ObjectInfo.QUERY.equals(type)) {
            dependenciesMap.computeIfAbsent(source, k -> new ArrayList<>()).add(fileName);
         }
      }

      collectDependenciesByVsScript(fileName, root, dependenciesMap);
   }

   private static void collectDependenciesByVsScript(
      String fileName, Element root, Map<String, List<String>> dependenciesMap)
   {
      NodeList list = UpdateDependencyHandler.getChildNodes(root, "//viewsheet/assembly/viewsheetInfo/viewsheetInfo");

      if(list.getLength() == 0) {
         return;
      }

      Element vsInfo = (Element) list.item(0);
      Element initScript = Tool.getChildNodeByTagName(vsInfo, "initScript");
      collectDependenciesByScript(Tool.getValue(initScript), fileName, dependenciesMap);

      Element loadScript = Tool.getChildNodeByTagName(vsInfo, "loadScript");
      collectDependenciesByScript(Tool.getValue(loadScript), fileName, dependenciesMap);
   }

   private static void collectDependenciesByScript(String script, String fileName,
                                                  Map<String, List<String>> dependenciesMap)
   {
      if(StringUtils.isEmpty(script)) {
         return;
      }

      FunctionIterator.ScriptListener listener = (ScriptIterator.Token token, ScriptIterator.Token pref, ScriptIterator.Token cref) -> {
         if(pref != null && Tool.equals(pref.val, "runQuery") && token.isRef()) {
            String qname = token.val;
            dependenciesMap.computeIfAbsent(qname, k -> new ArrayList<>()).add(fileName);
         }
      };

      FunctionIterator iterator = new FunctionIterator(script);
      iterator.addScriptListener(listener);
      iterator.iterate();
   }

   /**
    * Return the name of the file which in the import zip file.
    */
   public static String getFileName(File file, Map<String, String> names) {
      String fileName = file.getName();
      return names.get(fileName);
   }

   /**
    * Return the name of the file which in the import zip file.
    */
   public static String getQueryName(File file, Map<String, String> names) {
      String fileName = file.getName();
      fileName = names.get(fileName);

      if(fileName != null && fileName.startsWith(XQueryAsset.XQUERY)) {
         int idx = fileName.lastIndexOf("^");
         return idx == -1 ? null : fileName.substring(idx + 1);
      }

      return null;
   }
}
