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

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.dep.*;
import inetsoft.util.gui.ObjectInfo;
import inetsoft.report.Hyperlink;
import inetsoft.report.internal.binding.SourceAttr;
import inetsoft.sree.internal.DeployHelper;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.store.port.TransformerUtil;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.util.Tool;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.xpath.*;
import java.io.*;
import java.util.*;
import java.util.function.Function;

public final class UpdateDependencyHandler {
   public static void addDashboardDepedency(String id, AssetEntry entry) {
      addDependencyToFile(id, entry);
   }

   // path is query name, not include folder. The same as parse content to create asset entry for
   // query
   public static void addQueryDependencies(String path, Document doc) {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
         AssetEntry.Type.QUERY, path, null);
      Element root = doc.getDocumentElement();
      addDrillDependencies(root, entry);
   }

   public static void addModelDependencies(String path, Document doc) {
      path = Tool.replaceAll(path, "^", "/");
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
         AssetEntry.Type.LOGIC_MODEL, path,null);
      Element root = doc.getDocumentElement();
      addDrillDependencies(root, entry);
   }

   public static void addVPMDependencies(String ds, String path, Document doc) {
      path = Tool.replaceAll(path, "^", "/");
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.VPM,
         path,null);
      Element root = doc.getDocumentElement();

      NodeList list = getChildNodes(root, "//conditions/vpmObject");

      for(int i = 0; i < list.getLength(); i++) {
         Element elem = (Element) list.item(i);
         addViewDependencies(ds, elem, entry);
      }
   }

   private static void addViewDependencies(String ds, Element elem, AssetEntry entry) {
      if(elem == null) {
         return;
      }

      String type = Tool.getAttribute(elem, "type");
      AssetEntry datasource = new AssetEntry(AssetRepository.QUERY_SCOPE,
         AssetEntry.Type.DATA_SOURCE, ds, null);

      if(!"1".equals(type)) {
         addDependencyToFile(datasource.toIdentifier(), entry);
         return;
      }

      Element table = Tool.getChildNodeByTagName(elem, "table");
      AssetEntry view = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.PARTITION,
                                       ds + "/" + Tool.getValue(table), null);
      addDependencyToFile(view.toIdentifier(), entry);
   }

   /**
    * Update drill dependencies after importing query/model.
    * @param root  the root document element of the imported asset.
    * @param entry the target entry of the imported asset.
    */
   private static void addDrillDependencies(Element root, AssetEntry entry) {
      addDrillDependencies(root, entry, null);
   }

   private static void addDrillDependencies(Element root, AssetEntry entry,
                                            Set<AssetObject> dependencies)
   {
      NodeList list = getChildNodes(root, "//XDrillInfo/drillPath");

      for(int i = 0; i < list.getLength(); i++) {
         Element elem = (Element) list.item(i);
         addDrillPathDependencies(elem, entry, dependencies);
      }
   }

   /**
    * Add drill dependencies for target entry.
    */
   private static void addDrillPathDependencies(Element drillPath, AssetEntry entry,
                                                Set<AssetObject> dependencies)
   {
      String linkType = Tool.getAttribute(drillPath, "linkType");
      String link = Tool.getAttribute(drillPath, "link");
      String id = null;

      if((Hyperlink.VIEWSHEET_LINK + "").equals(linkType)) {
         id = AssetEntry.createAssetEntryForCurrentOrg(link).toIdentifier();
      }

      updateDependency(id, entry, dependencies);

      NodeList wsList = getChildNodes(drillPath, "./subquery/worksheetEntry/assetEntry");

      for(int j = 0; j < wsList.getLength(); j++) {
         Element wsEle = (Element) wsList.item(j);

         if(wsEle == null) {
            continue;
         }

         try {
            AssetEntry assetEntry = new AssetEntry();
            assetEntry.parseXML(wsEle);
            updateDependency(assetEntry.toIdentifier(), entry, dependencies);
         }
         catch(Exception ignore) {
         }
      }
   }

   public static void addSheetDependencies(AbstractSheetAsset asset, Document doc) {
      AssetEntry entry = asset.getAssetEntry();
      Element root = doc.getDocumentElement();

      if(asset instanceof ViewsheetAsset) {
         addViewsheetDependencies(root, entry);
      }
      else if(asset instanceof WorksheetAsset) {
         addWorksheetDependencies(root, entry);
      }
   }

   /**
    * Update depencencies after importing the viewsheet.
    */
   private static void addViewsheetDependencies(Element doc, AssetEntry entry) {
      addViewsheetDependencies(doc, entry, null, null);
   }

   private static void addViewsheetDependencies(Element doc, AssetEntry entry,
                                                XAsset asset, Set<AssetObject> dependencies)
   {
      addViewsheetDependencies(doc, entry, asset, dependencies, null);
   }

   private static void addViewsheetDependencies(Element doc, AssetEntry entry,
                                                XAsset asset, Set<AssetObject> dependencies,
                                                List<XAsset> importAssets)
   {
      NodeList entryList = getChildNodes(doc, "//worksheetEntry/assetEntry | //assemblies/oneAssembly/assembly/worksheetEntry/assetEntry");

      for(int i = 0; i < entryList.getLength(); i++) {
         Element assetEntry = (Element) entryList.item(i);
         NodeList propertyList = getChildNodes(assetEntry, "./properties/property");
         String type = null;
         String source = null;
         String prefix = null;
         String entryPaths = null;
         boolean physicalTable = false;

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
            else if("prefix".equals(key)) {
               Element valE = Tool.getChildNodeByTagName(property, "value");
               prefix = Tool.getValue(valE);
            }
            else if(XSourceInfo.TABLE_TYPE.equals(key)) {
               physicalTable = true;
            }
            else if("entry.paths".equals(key)) {
               Element valE = Tool.getChildNodeByTagName(property, "value");
               entryPaths = Tool.getValue(valE);
            }
         }

         if("worksheet".equals(type)) {
            int scope = AssetRepository.GLOBAL_SCOPE;

            try {
               scope = Integer.parseInt(Tool.getAttribute(assetEntry, "scope"));
            }
            catch(Exception ex) {
            }

            Element pathElem = Tool.getChildNodeByTagName(assetEntry, "path");
            String path = pathElem == null ? null : Tool.getValue(pathElem);

            Element UserElem = Tool.getChildNodeByTagName(assetEntry, "user");
            IdentityID user = UserElem == null ? null : IdentityID.getIdentityIDFromKey(Tool.getValue(UserElem));

            if(!StringUtils.isEmpty(path)) {
               AssetEntry wentry = new AssetEntry(scope, AssetEntry.Type.WORKSHEET, path, user);
               source = wentry.toIdentifier();
            }
         }

         if(ObjectInfo.QUERY.equals(type)) {
            addSourceDependency(XSourceInfo.QUERY, prefix, source, entry, dependencies);
         }
         else if(ObjectInfo.LOGICAL_MODEL.equals(type)) {
            addSourceDependency(XSourceInfo.MODEL, prefix, source, entry, dependencies);
         }
         else if(physicalTable) {
            int idx = entryPaths.indexOf("^_^TABLE^_^");

            if(idx < 0) {
               return;
            }

            String tableName = entryPaths.substring(idx + 11);
            tableName = tableName.replace("^_^", ".");
            addSourceDependency(XSourceInfo.PHYSICAL_TABLE, prefix, tableName, entry, dependencies);
         }
         else if(source != null) {
            addSourceDependency(XSourceInfo.ASSET, prefix, source, entry, dependencies);
         }
      }

      addCubeDependency(doc, entry, dependencies, importAssets);
      addEmbeddedVSDependencies(doc, entry, dependencies);
      addVSAssemblyDependencies(doc, entry, dependencies);
      getHyperlinkAssetDependencies(asset, doc, dependencies);
   }

   private static void addEmbeddedVSDependencies(Element doc, AssetEntry entry,
                                                 Set<AssetObject> dependencies)
   {
      NodeList list = getChildNodes(doc, "//assembly/worksheetEntry");

      for(int i = 0; i < list.getLength(); i++) {
         Element wnode = (Element) list.item(i);
         wnode = Tool.getFirstChildNode(wnode);

         if(wnode != null) {
            continue;
         }

         try {
            AssetEntry assetEntry = AssetEntry.createAssetEntry(wnode);

            if(assetEntry != null && assetEntry.isQuery()) {
               AssetEntry queryEntry = new AssetEntry(assetEntry.getScope(),
                  assetEntry.getType(), assetEntry.getName(), assetEntry.getUser());
               queryEntry.copyProperties(queryEntry);
               updateDependency(queryEntry.toIdentifier(), entry, dependencies);
            }
            else if(assetEntry != null) {
               if(entry != null) {
                  addDependencyToFile(assetEntry.toIdentifier(), entry);
               }
               else if(dependencies != null) {
                  dependencies.add(assetEntry);
               }
            }
         }
         catch(Exception ignore) {
         }
      }

      list = getChildNodes(doc,
         "./assembly/assemblies/oneAssembly/assembly/viewsheetEntry/assetEntry");

      for(int i = 0; i < list.getLength(); i++) {
         Element ele = (Element) list.item(i);

         if(ele == null) {
            continue;
         }

         try {
            AssetEntry vs = new AssetEntry();
            vs.parseXML(ele);

            if(entry != null) {
               addDependencyToFile(vs.toIdentifier(), entry);
            }
            else if(dependencies != null) {
               dependencies.add(vs);
            }
         }
         catch(Exception ignore) {
         }
      }
   }

   private static void addVSAssemblyDependencies(Element doc, AssetEntry entry,
                                                 Set<AssetObject> dependencies)
   {
      NodeList list = getChildNodes(doc,
         "//bindingInfo/table/text() | //assemblyInfo/firstTable/text()");

      for(int i = 0; i < list.getLength(); i++) {
         if(!(list.item(i) instanceof CDATASection)) {
            continue;
         }

         CDATASection section = (CDATASection) list.item(i);
         String value = section.getData();

         if(value != null && value.startsWith(Assembly.CUBE_VS)) {
            getSourceAssetEntryDependencies(XSourceInfo.ASSET, null, value, entry, dependencies);
         }
      }
   }

   private static void addCubeDependency(Element doc, AssetEntry entry,
                                         Set<AssetObject> dependencies, List<XAsset> importAssets)
   {
      NodeList list = getChildNodes(doc,
         "//assemblies/oneAssembly/assembly/assemblyInfo/sourceInfo");

      for(int i = 0; i < list.getLength(); i++) {
         Element sinfo = (Element) list.item(i);
         int type = Integer.parseInt(Tool.getAttribute(sinfo, "type"));
         Element src = Tool.getChildNodeByTagName(sinfo, "source");
         String source = Tool.getValue(src);

         if(source != null && source.startsWith(Assembly.CUBE_VS)) {
            addSourceDependency(type, null, source, entry, dependencies, importAssets);
         }
      }
   }

   private static void addWorksheetDependencies(Element doc, AssetEntry entry) {
      addWorksheetDependencies(doc, entry, null);
   }

   private static void collectWorksheetDependencies(Element doc, Set<AssetObject> dependencies) {
      addWorksheetDependencies(doc, null, dependencies);
   }

   private static void addWorksheetDependencies(Element doc, AssetEntry entry,
                                                Set<AssetObject> dependencies)
   {
      StringBuilder builder = new StringBuilder();
      builder.append("//assemblies/oneAssembly/assembly/assemblyInfo/source/sourceInfo |");
      builder.append("//assemblies/oneAssembly/assembly/attachedAssembly/source/sourceInfo");
      NodeList list = getChildNodes(doc, builder.toString());

      for(int i = 0; i < list.getLength(); i++) {
         Element sinfo = (Element) list.item(i);
         int type = Integer.parseInt(Tool.getAttribute(sinfo, "type"));
         Element src = Tool.getChildNodeByTagName(sinfo, "source");
         Element pre = Tool.getChildNodeByTagName(sinfo, "prefix");
         String source = Tool.getValue(src);
         String prefix = Tool.getValue(pre);
         addSourceDependency(type, prefix, source, entry, dependencies);
      }

      // get the embedded ws.
      list = getChildNodes(doc,
         "//assemblies/oneAssembly/assembly/assemblyInfo/mirrorAssembly/mirrorAssembly");

      for(int i = 0; i < list.getLength(); i++) {
         Element item = (Element) list.item(i);

         if(item == null || !"true".equals(item.getAttribute("outer"))) {
            continue;
         }

         String source = item.getAttribute("source");

         if(Tool.isEmptyString(source)) {
            continue;
         }

         if(entry != null) {
            addDependencyToFile(source, entry);
         }
         else if(dependencies != null) {
            dependencies.add(AssetEntry.createAssetEntry(source));
         }
      }
   }

   private static void addSourceDependency(int type, String prefix, String source,
                                           AssetObject entry, Set<AssetObject> dependencies)
   {
      addSourceDependency(type, prefix, source, entry, dependencies, null);

   }

   private static void addSourceDependency(int type, String prefix, String source,
                                           AssetObject entry, Set<AssetObject> dependencies,
                                           List<XAsset> importAssets)
   {
      String id = null;

      if(SourceAttr.QUERY == type) {
         id = DependencyHandler.getAssetId(source, AssetEntry.Type.QUERY);
      }
      else if(SourceAttr.MODEL == type) {
         id = DependencyHandler.getAssetId(DependencyHandler.getUniqueSource(prefix, source),
            AssetEntry.Type.LOGIC_MODEL);
      }
      else if(SourceAttr.ASSET == type) {
         if(source.startsWith(Assembly.CUBE_VS)) {
            id = DependencyHandler.getCubeSourceKey(source, importAssets);

            if(id == null) {
               LOG.error("cannot find cube source: " + source);
               return;
            }
         }
         else {
            id = source;
         }
      }
      else if(SourceAttr.CUBE == (type & SourceAttr.CUBE)) {
         id = DependencyHandler.getAssetId(prefix, AssetEntry.Type.DATA_SOURCE,
            AssetRepository.QUERY_SCOPE);
      }
      else if(SourceAttr.PHYSICAL_TABLE == type) {
         id = DependencyHandler.getAssetId(prefix, AssetEntry.Type.DATA_SOURCE);
      }
      else if(SourceAttr.DATASOURCE == type) {
         id = DependencyHandler.getAssetId(source, AssetEntry.Type.DATA_SOURCE);
      }

      if(id == null) {
         return;
      }

      final boolean isCube = SourceAttr.CUBE == (type & SourceAttr.CUBE) ||
            SourceAttr.ASSET == type && source.startsWith(Assembly.CUBE_VS);

      updateDependency(id, entry, dependencies, (AssetEntry e) -> {
         if(e == null) {
            return null;
         }

         if(e != null && (e.isQuery() || e.isLogicModel() || e.isPartition())) {
            e.setProperty("prefix", prefix);
         }

         if(isCube) {
            e.setProperty("isCube", "true");
         }

         return null;
      });
   }

   /**
    * Update or collect dependency
    * @param id           the identifier of the dependency.
    * @param entry        the current entry to be added to update or collect dependecy.
    * @param dependencies the set to save the collected dependencies.
    */
   private static void updateDependency(String id, AssetObject entry, Set<AssetObject> dependencies) {
      updateDependency(id, entry, dependencies, null);
   }

   /**
    * Update or collect dependency
    * @param id           the identifier of the dependency.
    * @param entry        the current entry to be added to update or collect dependecy.
    * @param dependencies the set to save the collected dependencies.
    */
   private static void updateDependency(String id, AssetObject entry,
                                        Set<AssetObject> dependencies,
                                        Function<AssetEntry, Object> dependencyEntryAction)
   {
      if(id == null) {
         return;
      }

      if(entry != null) {
         addDependencyToFile(id, entry);
      }
      else if(dependencies != null) {
         AssetEntry assetEntry = AssetEntry.createAssetEntry(id);

         if(dependencyEntryAction  != null) {
            dependencyEntryAction.apply(assetEntry);
         }

         dependencies.add(assetEntry);
      }
   }

   private static void addDependencyToFile(String id, AssetObject entry) {
      DependencyStorageService service = DependencyStorageService.getInstance();

      try {
         RenameTransformObject obj = service.get(id);
         DependenciesInfo info = null;
         List<AssetObject> list;

         if(obj instanceof DependenciesInfo) {
            info = (DependenciesInfo) obj;
         }

         if(info == null) {
            info = new DependenciesInfo();
            list = new ArrayList<>();
         }
         else {
            list = info.getDependencies();

            if(list == null) {
               list = new ArrayList<>();
            }

            if(list.contains(entry)) {
               return;
            }
         }

         list.add(entry);
         info.setDependencies(list);
         service.put(id, info);
      }
      catch(Exception e) {
         LOG.warn("Failed to update the dependencies to file.", e);
      }
   }

   public static NodeList getChildNodes(Element doc, String path) {
      try {
         XPathExpression expr = xpath.compile(path);
         return (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
      }
      catch(XPathExpressionException pathException) {
         LOG.debug("Failed to compile xpath: {}", path, pathException);
      }

      return new NodeList() {
         @Override
         public Node item(int index) {
            return null;
         }

         @Override
         public int getLength() {
            return 0;
         }
      };
   }

   public static Document getAssetDocByFile(File file, String assetType) {
      return getXmlDocByFile(file);
   }

   public static Set<AssetObject> getAssetSupportTargetDependencies(File file, XAsset asset,
                                                                    List<XAsset> importAssets)
   {
      Document doc = getAssetDocByFile(file, asset == null ? null : asset.getType());

      if(doc == null) {
         return null;
      }

      Set<AssetObject> dependencies = new HashSet<>();

      if(asset instanceof WorksheetAsset) {
         collectWorksheetDependencies(doc.getDocumentElement(), dependencies);
      }
      else if(asset instanceof ViewsheetAsset) {
         addViewsheetDependencies(doc.getDocumentElement(), null, asset, dependencies, importAssets);
      }
      else if(asset instanceof DashboardAsset) {
         collectDashboardDependencies(doc.getDocumentElement(), dependencies);
      }
      else if(asset instanceof ScheduleTaskAsset) {
         getTaskSupportTargetDependencies(doc.getDocumentElement(), dependencies);
      }
      else if(asset instanceof XPartitionAsset || asset instanceof VirtualPrivateModelAsset) {
         String dataSource;

         if(asset instanceof XPartitionAsset) {
            dataSource = ((XPartitionAsset) asset).getDataSource();
         }
         else {
            dataSource = ((VirtualPrivateModelAsset) asset).getDataSource();
         }

         if(dataSource != null) {
            dependencies.add(new AssetEntry(AssetRepository.QUERY_SCOPE,
               AssetEntry.Type.DATA_SOURCE, dataSource, null));
         }
      }
      else if(asset instanceof XLogicalModelAsset) {
         collectLogicalModelDependencies(((XLogicalModelAsset) asset).getDataSource(),
            doc.getDocumentElement(), dependencies);
      }
      else if(asset instanceof XQueryAsset) {
         collectQueryDependencies(doc.getDocumentElement(), dependencies);
      }
      else if(asset instanceof XDataSourceAsset) {
         collectCubeDomainDependencies(doc.getDocumentElement(), dependencies);
      }

      return dependencies;
   }

   private static void collectQueryDependencies(Element doc, Set<AssetObject> dependencies) {
      String datasource = doc.getAttribute("datasource");

      if(datasource == null) {
         return;
      }

      dependencies.add(new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE,
         datasource, null));
      addDrillDependencies(doc, null, dependencies);
   }

   private static void collectLogicalModelDependencies(String dataSource, Element doc,
                                                       Set<AssetObject> dependencies)
   {
      String partition = doc.getAttribute("partition");

      if(partition == null) {
         return;
      }

      String partitionPath = dataSource + '/' + partition;
      dependencies.add(new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.PARTITION,
         partitionPath, null));
      addDrillDependencies(doc, null, dependencies);
   }

   private static void collectCubeDomainDependencies(Element doc,
                                                     Set<AssetObject> dependencies)
   {
      Element elem = Tool.getChildNodeByTagName(doc, "datasource");
      String type = elem == null ? null : elem.getAttribute("type");

      if(!"xmla".equals(type)) {
         return;
      }

      addDrillDependencies(elem, null, dependencies);
   }

   public static Document getXmlDocByFile(File file) {
      Document doc = null;

      try(InputStream input = new FileInputStream(file)) {
         if(input.available() > 0) {
            doc = Tool.parseXML(input, "UTF-8", false, false);
         }
      }
      catch(Exception ignore) {
      }

      return doc;
   }

   private static void collectDashboardDependencies(Element doc, Set<AssetObject> dependencies) {
      NodeList list = getChildNodes(doc, "//dashboard/entry");

      if(list == null || list.getLength() == 0) {
         return;
      }

      Element item = (Element) list.item(0);

      if(item == null) {
         return;
      }

      String identifier = Tool.getAttribute(item, "identifier");

      if(identifier == null) {
         return;
      }

      identifier = Tool.byteDecode(identifier);
      dependencies.add(AssetEntry.createAssetEntryForCurrentOrg(identifier));
   }

   private static void getTaskSupportTargetDependencies(Element doc,
                                                        Set<AssetObject> dependencies)
   {
      NodeList list = getChildNodes(doc, "//Task");

      if(list == null || list.getLength() == 0) {
         return;
      }

      Element task = (Element) list.item(0);

      if(task == null) {
         return;
      }

      String user = task.getAttribute("owner");
      list = getChildNodes(task, "./Action");

      if(list == null) {
         return;
      }

      for(int i = 0; i < list.getLength(); i++) {
         Element item = (Element) list.item(i);

         if(item == null) {
            continue;
         }

         String type = Tool.getAttribute(item, "type");

         if(type == null) {
            return;
         }

         if(Tool.equals(type, "Viewsheet")) {
            String viewsheet = Tool.getAttribute(item, "viewsheet");

            if(viewsheet != null) {
               dependencies.add(AssetEntry.createAssetEntry(viewsheet));
            }
         }
         else if("Backup".equals(type)) {
            NodeList childNodes = getChildNodes(doc, "//XAsset");

            if(childNodes != null) {
               for(int j = 0; j < childNodes.getLength(); j++) {
                  Element assetEle = (Element) childNodes.item(j);

                  if(assetEle == null) {
                     continue;
                  }

                  String assetType = assetEle.getAttribute("type");
                  String assetPath = Tool.byteDecode(assetEle.getAttribute("path"));
                  IdentityID assetUser = IdentityID.getIdentityIDFromKey(assetEle.getAttribute("user"));
                  XAsset xAsset = SUtil.getXAsset(assetType, assetPath, assetUser);
                  AssetObject assetObject = DeployHelper.getAssetObjectByAsset(xAsset);

                  if(assetObject != null) {
                     dependencies.add(assetObject);
                  }
               }
            }
         }
         else if("Batch".equals(type)) {
            NodeList childNodes = getChildNodes(doc, "//queryEntry/assetEntry");

            if(childNodes == null)  {
               return;
            }

            for(int j = 0; j < childNodes.getLength(); j++) {
               Element assetEle = (Element) childNodes.item(j);

               if(assetEle == null) {
                  continue;
               }

               try {
                  AssetEntry assetEntry = new AssetEntry();
                  assetEntry.parseXML(assetEle);
                  dependencies.add(assetEntry);
               }
               catch(Exception ignore) {
               }
            }

         }
      }
   }

   private static void getSourceAssetEntryDependencies(int type, String prefix, String source,
                                                       AssetEntry entry, Set<AssetObject> dependencies)
   {
      if(source == null) {
         return;
      }

      String id = null;

      if(SourceAttr.QUERY == type) {
         id = DependencyHandler.getAssetId(source, AssetEntry.Type.QUERY,
            AssetRepository.QUERY_SCOPE);
      }
      else if(SourceAttr.MODEL == type) {
         id = DependencyHandler.getAssetId(prefix + "/" + source,
            AssetEntry.Type.LOGIC_MODEL, AssetRepository.QUERY_SCOPE);
      }
      else if(SourceAttr.ASSET == type) {
         if(source.startsWith(Assembly.CUBE_VS)) {
            id = DependencyHandler.getCubeSourceKey(source);
         }
         else {
            id = source;
         }
      }
      else if(SourceAttr.CUBE == type) {
         id = DependencyHandler.getAssetId(prefix, AssetEntry.Type.DATA_SOURCE,
            AssetRepository.QUERY_SCOPE);
      }
      else if(SourceAttr.DATASOURCE == type || SourceAttr.PHYSICAL_TABLE == type) {
         id = DependencyHandler.getAssetId(source, AssetEntry.Type.DATA_SOURCE,
            AssetRepository.QUERY_SCOPE);
      }

      if(Tool.isEmptyString(id)) {
         return;
      }

      if(entry != null) {
         addDependencyToFile(id, entry);
      }
      else if(dependencies != null) {
         AssetEntry assetEntry = AssetEntry.createAssetEntry(id);

         if(assetEntry != null && (assetEntry.isWorksheet() || assetEntry.isViewsheet() ||
            assetEntry.isReplet() || assetEntry.isDataSource() || assetEntry.isLogicModel() ||
            assetEntry.isPartition() || assetEntry.isVPM() || assetEntry.isPhysicalTable() ||
            assetEntry.isQuery()))
         {
            dependencies.add(assetEntry);

            if(source.startsWith(Assembly.CUBE_VS)) {
               assetEntry.setProperty("isCube", "true");
            }

            if((assetEntry.isLogicModel() || assetEntry.isPhysicalTable() || assetEntry.isQuery())
               && assetEntry.getProperty("prefix") == null)
            {
               assetEntry.setProperty("prefix", prefix);
            }
         }
      }
   }

   private static void getHyperlinkAssetDependencies(XAsset asset, Element elem,
                                                     Set<AssetObject> dependencies)
   {
      if(dependencies == null) {
         return;
      }

      NodeList list = getChildNodes(elem, ".//Hyperlink");

      for(int i = 0; i < list.getLength(); i++) {
         Element link = (Element) list.item(i);

         int linkType = Integer.parseInt(Tool.getAttribute(link, "LinkType"));
         String linkName = Tool.getAttribute(link, "Link");

         if(Tool.isEmptyString(linkName)) {
            continue;
         }

         if(linkType == Hyperlink.VIEWSHEET_LINK){
            dependencies.add(AssetEntry.createAssetEntry(linkName));
         }
      }
   }

   public static void replaceDataSourceInfo(File file, XAsset oldAsset, XAsset newAsset)
      throws IOException
   {
      if(file == null || !(oldAsset instanceof XDataSourceAsset) ||
         !(newAsset instanceof XDataSourceAsset))
      {
         return;
      }

      Document doc = getXmlDocByFile(file);

      if(doc == null) {
         return;
      }

      Element documentElement = doc.getDocumentElement();

      if(documentElement == null) {
         return;
      }

      NodeList childNodes = getChildNodes(documentElement, "//registry/datasource");

      if(childNodes != null) {
         for(int i = 0; i < childNodes.getLength(); i++) {
            Element item = (Element) childNodes.item(i);

            if(Tool.equals(oldAsset.getPath(), item.getAttribute("name"))) {
               item.setAttribute("name", newAsset.getPath());
            }
         }
      }

      childNodes = getChildNodes(documentElement, "//registry/DataModel | //registry/Domain");

      if(childNodes != null) {
         for(int i = 0; i < childNodes.getLength(); i++) {
            Element item = (Element) childNodes.item(i);

            if(Tool.equals(oldAsset.getPath(), Tool.byteDecode(item.getAttribute("datasource")))) {
               item.setAttribute("datasource", newAsset.getPath());
            }
         }
      }

      childNodes = getChildNodes(documentElement, "//registry/additional");

      if(childNodes != null) {
         for(int i = 0; i < childNodes.getLength(); i++) {
            Element item = (Element) childNodes.item(i);

            if(Tool.equals(oldAsset.getPath(), item.getAttribute("parent"))) {
               item.setAttribute("parent", newAsset.getPath());
            }
         }
      }

      if(!Tool.equals(newAsset, oldAsset)) {
         TransformerUtil.save(file.getAbsolutePath(), doc);
      }
   }

   public static void replaceQueryInfo(File file, XAsset oldAsset, XAsset newAsset)
      throws IOException
   {
      if(file == null || !(oldAsset instanceof XQueryAsset) ||
         !(newAsset instanceof XQueryAsset))
      {
         return;
      }

      Document doc = getXmlDocByFile(file);

      if(doc == null) {
         return;
      }

      Element documentElement = doc.getDocumentElement();

      if(documentElement == null) {
         return;
      }

      String oldName = getAssetName(oldAsset);
      String newName = getAssetName(newAsset);

      if(Tool.equals(oldName, documentElement.getAttribute("name"))) {
         documentElement.setAttribute("name", newName);
      }

      replaceQueryInfo0(documentElement, oldName, newName);

      if(!Tool.equals(newAsset, oldAsset)) {
         TransformerUtil.save(file.getAbsolutePath(), doc);
      }
   }

   private static void replaceQueryInfo0(Element query, String oldName, String newName) {
      NodeList querys = query.getChildNodes();

      if(querys == null) {
         return;
      }

      for(int j = 0; j < querys.getLength(); j++) {
         Node queryItem = querys.item(j);

         if(queryItem == null) {
            continue;
         }

         if(queryItem.getNodeName().startsWith("query_")) {
            Element name = Tool.getChildNodeByTagName(queryItem, "name");
            String value = Tool.getValue(name);

            if(Tool.equals(oldName, value)) {
               DependencyTransformer.replaceElementCDATANode(name, newName);
            }
         }
      }
   }

   private static String getAssetName(XAsset asset) {
      String path = asset.getPath();
      int idx = path.lastIndexOf('/');

      return idx >= 0 ? path.substring(idx + 1) : path;
   }

   protected final static XPath xpath = XPathFactory.newInstance().newXPath();
   private static final Logger LOG = LoggerFactory.getLogger(UpdateDependencyHandler.class);
}
