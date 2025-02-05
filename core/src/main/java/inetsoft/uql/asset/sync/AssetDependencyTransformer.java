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

import inetsoft.report.CellBinding;
import inetsoft.report.XSessionManager;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.store.port.TransformerUtil;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XQueryWrapper;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.viewsheet.VSBookmark;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.xmla.Domain;
import inetsoft.util.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.*;

/**
 * AssetDependencyTransformer is a class to rename dependenies for vs/ws binding query/lm/ws:
 *    AssetQueryDependencyTransformer(vs/ws binding query)
 *    AssetLMDependencyTransformer(vs/ws binding logic model)
 *    AssetWSDependencyTransformer(vs/ws binding ws)
 *    AssetTabularDependencyTransformer(ws binding tabular)
 *
 * @version 13.2
 * @author InetSoft Technology Corp
 */
public class AssetDependencyTransformer extends DependencyTransformer {
   /**
    * Create a transformer to rename dependenies for the asset(ws/vs).
    */
   public AssetDependencyTransformer(AssetEntry asset) {
      this.asset = asset;
   }

   public static List<RenameDependencyInfo> renameDep(AssetEntry asset, File assetFile,
                                                      List<RenameInfo> infos)
   {
      if(infos == null || infos.size() == 0) {
         return null;
      }

      // For report and ws, it can binding different sources, and when rename its data source name
      // it will get different types rename infos in one asset, such as one ws binding query
      // and model from datasource orders, rename orders, the asset will have two infos:
      // one is query infos, the other is model infos, we should create two transfrms for it.
      // So split the rinfos by info types.
      HashMap<String, List<RenameInfo>> infoMap = new HashMap<>();

      for(int i = 0; i < infos.size(); i++) {
         RenameInfo rinfo = infos.get(i);

         if(rinfo instanceof ChangeTableOptionInfo) {
            List<RenameInfo> list = infoMap.computeIfAbsent("tableOption", k -> new ArrayList<>());
            list.add(rinfo);
         }
         else if(rinfo.isQuery()) {
            List<RenameInfo> list = infoMap.computeIfAbsent("query", k -> new ArrayList<>());
            list.add(rinfo);
         }
         else if(rinfo.isLogicalModel()) {
            List<RenameInfo> list = infoMap.computeIfAbsent("model", k -> new ArrayList<>());
            list.add(rinfo);

            if(rinfo.isCube()) {
               RenameInfo info = createCubeModelRenameInfo(rinfo);
               list = infoMap.computeIfAbsent("cube", k -> new ArrayList<>());
               list.add(info);
            }
         }
         else if(rinfo.isWorksheet() || rinfo.isAutoDrill()) {
            List<RenameInfo> list = infoMap.computeIfAbsent("ws", k -> new ArrayList<>());
            list.add(rinfo);
         }
         else if(rinfo.isTabularSource()) {
            List<RenameInfo> list = infoMap.computeIfAbsent("rest", k -> new ArrayList<>());
            list.add(rinfo);
         }
         else if(rinfo.isPhysicalTable()) {
            List<RenameInfo> list = infoMap.computeIfAbsent("physical", k -> new ArrayList<>());
            list.add(rinfo);
         }
         else if(rinfo.isSqlTable()) {
            List<RenameInfo> list = infoMap.computeIfAbsent("sql", k -> new ArrayList<>());
            list.add(rinfo);
         }
         else if(rinfo.isHyperlink()) {
            List<RenameInfo> list = infoMap.computeIfAbsent("hyperlink", k -> new ArrayList<>());
            list.add(rinfo);
         }
         else if(rinfo.isEmbedViewsheet()) {
            List<RenameInfo> list = infoMap.computeIfAbsent("embedvs", k -> new ArrayList<>());
            list.add(rinfo);
         }
         else if(rinfo.isCube()) {
            List<RenameInfo> list = infoMap.computeIfAbsent("cube", k -> new ArrayList<>());
            list.add(rinfo);
         }
         else if(rinfo.isPartition()) {
            List<RenameInfo> list = infoMap.computeIfAbsent("partition", k -> new ArrayList<>());
            list.add(rinfo);
         }
         else if(rinfo.isDataSource()) {
            List<RenameInfo> list = infoMap.computeIfAbsent("datasource", k -> new ArrayList<>());
            list.add(rinfo);
         }
         else if(rinfo.isScriptFunction()) {
            List<RenameInfo> list = infoMap.computeIfAbsent("script", k -> new ArrayList<>());
            list.add(rinfo);
         }
      }

      Iterator<String> keys = infoMap.keySet().iterator();
      List<RenameDependencyInfo> causeDepInfos = new ArrayList<>();

      while(keys.hasNext()) {
         String name = keys.next();
         List<RenameInfo> rinfos = infoMap.get(name);
         DependencyTransformer dependencyTransformer = null;

         if("tableOption".equals(name)) {
            dependencyTransformer = new DataDependencyTransformer(asset);
         }
         else if("model".equals(name)) {
            dependencyTransformer = new AssetLMDependencyTransformer(asset);
         }
         else if("rest".equals(name)) {
            dependencyTransformer = new AssetTabularDependencyTransformer(asset);
         }
         else if("ws".equals(name)) {
            dependencyTransformer = new AssetWSDependencyTransformer(asset);
         }
         else if("physical".equals(name)) {
            dependencyTransformer = new AssetPhyTableDependencyTransformer(asset);
         }
         else if("sql".equals(name)) {
            dependencyTransformer = new AssetSQLTableDependencyTransformer(asset);
         }
         else if("hyperlink".equals(name)) {
            dependencyTransformer = new AssetHyperlinkDependencyTransformer(asset);
         }
         else if("embedvs".equals(name)) {
            dependencyTransformer = new AssetEmbedDependencyTransformer(asset);
         }
         else if("script".equals(name)) {
            dependencyTransformer = new AssetScriptDependencyTransformer(asset);
         }
         else if("cube".equals(name)) {
            dependencyTransformer = new AssetCubeDependencyTransformer(asset);
         }
         else if("partition".equals(name) && asset.isLogicModel()) {
            //dependencyTransformer = new LogicalModelPartitionDependencyTransformer(asset);
         }
         else if("datasource".equals(name) && asset.isQuery()) {
            dependencyTransformer = new QueryDatasourceDependencyTransformer(asset);
         }

         if(dependencyTransformer != null) {
            dependencyTransformer.setAssetFile(assetFile);
            causeDepInfos.add(dependencyTransformer.process(rinfos));
         }
      }

      return causeDepInfos;
   }

   /**
    * will cause new RenameInfos if rename the columns of worksheet table.
    * @param infos
    * @return
    */
   @Override
   public RenameDependencyInfo process(List<RenameInfo> infos) {
      return renameAssetRepository(infos);
   }

   private RenameDependencyInfo renameAssetRepository(List<RenameInfo> infos) {
      try {
         AssetRepository repository = AssetUtil.getAssetRepository(false);
         IndexedStorage storage = repository.getStorage(asset);

         return renameAsset(storage, infos);
      }
      catch(Exception e) {
         LOG.warn("Failed to rename dependency assets: ", e);
      }

      return null;
   }

   private RenameDependencyInfo renameAsset(IndexedStorage storage, List<RenameInfo> infos)
      throws Exception
   {
      RenameDependencyInfo causeInfos = null;
      String identifier = asset.toIdentifier();
      Document doc;

      if(getAssetFile() != null) {
         doc = getAssetFileDoc();
      }
      else {
         synchronized(storage) {
            doc = storage.getDocument(identifier, asset.getOrgID());
         }
      }

      if(doc != null) {
         Element root;

         if(getAssetFile() != null && asset.isViewsheet()) {
            root = getChildNode(doc.getDocumentElement(), "./assembly");
         }
         else {
            root = doc.getDocumentElement();
         }

         if(asset.isViewsheet() || asset.isWorksheet()) {
            causeInfos = renameSheets(root, infos, asset.isViewsheet());
         }
         else {
            renameAutoDrills(root, infos);
         }

         if(getAssetFile() != null) {
            TransformerUtil.save(getAssetFile().getAbsolutePath(), doc);
         }
         else {
            saveAssets(storage, identifier, doc);
         }
      }

      if(asset.isQuery() || asset.isLogicModel()) {
         AssetDataCache.clearCache();
         XSessionManager.getSessionManager().clearCache();
      }

      return causeInfos;
   }

   protected void renameAutoDrills(Element doc, List<RenameInfo> rinfos) {
   }

   protected void renameAutoDrill(Element doc, RenameInfo rinfo) {
   }

   /**
    * Save assets.
    * @param storage
    * @param identifier  the asset identifier.
    * @param doc         the document after transformation.
    */
   private void saveAssets(IndexedStorage storage, String identifier, Document doc) {
      synchronized(storage) {
         storage.putDocument(identifier, doc, getAssetClassName(), asset.getOrgID());
      }
   }

   /**
    * @return asset sheet class name.
    */
   private String getAssetClassName() {
      if(asset.isWorksheet()) {
         return Worksheet.class.getName();
      }

      if(asset.isViewsheet()) {
         return Viewsheet.class.getName();
      }

      if(asset.isQuery()) {
         return XQueryWrapper.class.getName();
      }

      if(asset.isLogicModel()) {
         return XLogicalModel.class.getName();
      }

      if(asset.isPartition() || asset.getType().isExtendedPartition()) {
         return XPartition.class.getName();
      }

      if(asset.isDomain()) {
         return Domain.class.getName();
      }

      if(asset.isVPM()) {
         return VirtualPrivateModel.class.getName();
      }

      return null;
   }

   private RenameDependencyInfo renameSheets(Element doc, List<RenameInfo> infos, boolean isVS) {
      List<RenameInfo> causeRenameInfos  = new ArrayList<>();
      infos.stream()
         .forEach(info -> causeRenameInfos.addAll(renameSheet(doc, info, isVS)));

      return createRenameDependency(getIdentifier(), causeRenameInfos);
   }

   private String getIdentifier() {
      return asset.toIdentifier();
   }

   private List<RenameInfo> renameSheet(Element doc, RenameInfo info, boolean isVS) {
      // rename depedency for vs
      if(isVS) {
         // rename wentry/calc source for query/lm/ws
         // rename sourceinfo/selectiontable/bindinginfo for query/lm
         if(info.isSource() || info.isDataSource() || info.isDataSourceFolder()) {
            renameVSSource(doc, info);

            return new ArrayList<>();
         }
         else if(info.isFolder()) {
            renameWSEntrys(doc, info);

            return new ArrayList<>();
         }
         else if(info.isTable() && !info.isEntity()) {
            renameVSTable(doc, info);

            return new ArrayList<>();
         }
         else if(info.isHyperlink()) {
            renameLink(doc, info);

            return new ArrayList<>();
         }
         else if(info.isEmbedViewsheet()) {
            renameEmbedVS(doc, info);

            return new ArrayList<>();
         }
         else if(info.isScriptFunction()) {
            renameScript(doc, info);

            return new ArrayList<>();
         }
      }
      // rename depedency for ws
      else {
         // rename sourceinfo for query/lm.
         // rename mirrorAssembly for ws.
         if(info.isSource() || info.isDataSource() || info.isDataSourceFolder() || info.isTableOption() || info.isAsset()) {
            renameWSSources(doc, info);

            if(info.isSqlTable()) {
               renameSQLSources(doc, info);
            }

            if(info.isAsset()) {
               renameAutoDrill(doc, info);
            }

            return new ArrayList<>();
         }
         else if(info.isFolder()) {
            renameWSEntrys(doc, info);
            // fix source property
            renameWSSources(doc, info);

            return new ArrayList<>();
         }
         //rename auto drill
         else if(info.isAutoDrill()) {
            renameAutoDrill(doc, info);

            return new ArrayList<>();
         }
      }

      // rename by assembly/dateref only for column/entity.
      // other
      List<RenameInfo> causeRenameInfos = new ArrayList<>();
      renameSheet(doc, info, isVS, causeRenameInfos);

      if(causeRenameInfos.size() != 0) {
         causeRenameInfos.stream().forEach(rinfo -> rinfo.setParentRenameInfo(info));
      }

      if(isVS && isSameVSSource0(doc, info)) {
         renameBookMarks(info);
      }

      return causeRenameInfos;
   }

   /**
    * Rename the binding source info for vs bookmarks.
    * @param info rename info.
    */
   private void renameBookMarks(RenameInfo info) {
      if(asset != null) {
         AssetRepository repository = AssetUtil.getAssetRepository(false);

         try {
            List<IdentityID> users = repository.getBookmarkUsers(asset);

            if(users != null) {
               for(IdentityID user : users) {
                  VSBookmark vsBookmark = repository.getVSBookmark(asset, new XPrincipal(user));
                  String[] bookMarkNames = vsBookmark.getBookmarks();

                  if(bookMarkNames == null || bookMarkNames.length == 0) {
                     continue;
                  }

                  for(String bookMarkName : bookMarkNames) {
                     renameBookMark(vsBookmark, bookMarkName, info);
                  }

                  repository.setVSBookmark(asset, vsBookmark, new XPrincipal(user));
               }
            }
         }
         catch(Exception e) {
            LOG.debug("Failed to rename binding source info for vs bookmarks", e);
         }
      }
   }

   /**
    * Rename the binding source info for a bookmark.
    * @param vsBookmark vs bookmark.
    * @param bookMarkName rename bookmark name.
    * @param info rename info.
    */
   private void renameBookMark(VSBookmark vsBookmark, String bookMarkName, RenameInfo info)
      throws IOException, ParserConfigurationException
   {
      Object bookmarkData = vsBookmark.getBookmarkData(bookMarkName);
      Document document = null;

      if(bookmarkData instanceof byte[]) {
         byte[] bytes = (byte[]) bookmarkData;

         try(InputStream in = new BufferedInputStream(new ByteArrayInputStream(bytes))) {
            document = Tool.parseXML(in, "UTF-8", false, false);
            renameBookMarkAssemblies(document.getDocumentElement(), info);
         }
      }

      if(document != null) {
         try(ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XMLTool.write(document, out);
            vsBookmark.setBookmarkData(bookMarkName, out.toByteArray());
         }
      }
   }

   protected void renameLink(Element relem, RenameInfo rinfo) {
   }

   protected void renameEmbedVS(Element relem, RenameInfo rinfo) {
   }

   private void renameSheet(Element doc, RenameInfo info, boolean isVS,
                            List<RenameInfo> causeRenameInfos)
   {
      NodeList assemblies = getChildNodes(doc, ASSET_DEPEND_ELEMENTS);

      Map<String, Set<Element>> wsMirrorAssemblyMap = new HashMap<>();

      for(int i = 0; i < assemblies.getLength(); i++) {
         Element assembly = (Element) assemblies.item(i);

         if(assembly == null) {
            continue;
         }

         String assmblyType = getVSAssemblyType(assembly);

         if("Viewsheet".equals(assmblyType) || isVS) {
            continue;
         }

         NodeList mirrors = getChildNodes(assembly, ".//mirrorAssembly");

         for(int j = 0; mirrors != null && j < mirrors.getLength(); j++) {
            Element mirror = (Element) mirrors.item(j);

            if(mirror == null) {
               continue;
            }

            String mirrorTargetName = getString(mirror, "./mirror");

            if(StringUtils.isEmpty(mirrorTargetName)) {
               continue;
            }

            wsMirrorAssemblyMap.compute(mirrorTargetName, (key, oldValue) -> {
               if(oldValue == null) {
                  oldValue = new HashSet<>();
               }

               oldValue.add(assembly);

               return oldValue;
            });
         }
      }

      for(int i = 0; i < assemblies.getLength(); i++) {
         Element assembly = (Element) assemblies.item(i);
         String assmblyType = DependencyTool.getVSAssemblyType(assembly);

         // For Embed Viewsheet
         if("Viewsheet".equals(assmblyType)) {
            renameSheet(assembly, info, true, causeRenameInfos);
         }
         // For viewsheet binding query/lm/ws
         else if(isVS) {
            renameViewsheetAssembly(assembly, info);
         }
         // For worksheet binding query/lm/ws
         else {
            renameWorksheetAssembly(assembly, info, causeRenameInfos, assemblies,
               wsMirrorAssemblyMap);
         }
      }

      if(isVS) {
         renameVSCalcRefs(doc, info);
         renameVSAllAggrs(doc, info);
      }
   }

   /**
    * Rename the binding source for assemblies of bookmark.
    * @param bookMark book mark element.
    * @param info rename info.
    */
   private void renameBookMarkAssemblies(Element bookMark, RenameInfo info) {
      NodeList assemblies = getChildNodes(bookMark, "./assembly");

      for(int i = 0; i < assemblies.getLength(); i++) {
         Element assembly = (Element) assemblies.item(i);
         String assmblyType = DependencyTool.getVSAssemblyType(assembly);

         if("Viewsheet".equals(assmblyType)) {
            renameBookMarkAssemblies(assembly, info);
         }
         else {
            renameViewsheetAssembly(assembly, info, false, true);
         }
      }
   }

   /**
    * Transform vs calcfields expression.
    */
   protected void renameVSCalcRefs(Element doc, RenameInfo info) {
      Element table = getChildNode(doc, "./allcalc/tname");

      if(table != null) {
         String tableName = Tool.getValue(table);

         if(!Tool.equals(info.getSource(), tableName) && !Tool.equals(info.getTable(), tableName)) {
            return;
         }
      }

      NodeList expRefs = getChildNodes(doc, "./allcalc/values/value/dataRef/dataRef");

      for(int i = 0; i < expRefs.getLength(); i++) {
         Element expRef = (Element) expRefs.item(i);
         renameExpressionRef(expRef, info, ":");
      }
   }

   /**
    * Transform datarefs of all aggregates.
    */
   protected void renameVSAllAggrs(Element doc, RenameInfo info) {
      Element elem = getChildNode(doc, "./allaggr");

      if(elem != null) {
         renameViewsheetAssembly(elem, info, true);
      }
   }

   // rename sourceinfo for query/lm.
   // rename mirrorAssembly for ws. to be override by ws
   protected void renameWSSources(Element doc, RenameInfo info) {
      if(info.isQuery() || info.isLogicalModel() || info.isPhysicalTable() ||
         info.isTabularSource() || info.isSqlTable() || info.isDataSource() && info.isCube())
      {
         StringBuilder builder = new StringBuilder();
         builder.append("//assemblies/oneAssembly/assembly/assemblyInfo/source/sourceInfo |");
         builder.append("//assemblies/oneAssembly/assembly/attachedAssembly/source/sourceInfo");

         NodeList list = getChildNodes(doc, builder.toString());

         for(int i = 0; i < list.getLength(); i++) {
            Element elem = (Element) list.item(i);
            renameWSSource(elem, info);
         }
      }
   }

   protected void renameSQLSources(Element doc, RenameInfo info) {
   }

   protected void replaceAssetAttr(Element elem, String oname, String nname) {
      String attr = Tool.getValue(elem);

      if(attr != null) {
         replaceCDATANode(elem, attr.replace(oname, nname));
      }
   }

   protected void renameCalcSource(Element doc, RenameInfo info) {
      // Only support calc for ws table and query/lm source.
      renameNodeValue(doc, info, "./allcalc/tname");
   }

   protected void renameAggrSource(Element doc, RenameInfo info) {
      renameNodeValue(doc, info, "./allaggr/tname");
   }

   private void renameNodeValue(Element doc, RenameInfo info, String path) {
      NodeList list = getChildNodes(doc, path);

      if(list != null && list.getLength() > 0) {
         Element tname = (Element)list.item(0);
         String val = Tool.getValue(tname);

         if(Tool.equals(val, info.getOldName())) {
            replaceCDATANode(tname, info.getNewName());
         }
      }
   }

   private void renameViewsheetAssembly(Element assembly, RenameInfo info) {
      renameViewsheetAssembly(assembly, info, false);
   }

   private void renameViewsheetAssembly(Element assembly, RenameInfo info, boolean isAllAggs) {
      renameViewsheetAssembly(assembly, info, isAllAggs, false);
   }

   private void renameViewsheetAssembly(Element assembly, RenameInfo info, boolean isAllAggs,
                                        boolean isBookMark)
   {
      // when info is table, Only go here when changing entity
      if(info.isTable()) {
         renameVSTable(assembly, info);
      }
      else if(info.isColumn()) {
         renameVSColumns(assembly, info, isAllAggs, isBookMark);
      }
   }

   private void renameWorksheetAssembly(Element assembly, RenameInfo info,
                                        List<RenameInfo> causeRenameInfos, NodeList assemblies)
   {
      renameWorksheetAssembly(assembly, info, causeRenameInfos, assemblies, null);
   }

   private void renameWorksheetAssembly(Element assembly, RenameInfo info,
                                        List<RenameInfo> causeRenameInfos,
                                        NodeList assemblies,
                                        Map<String, Set<Element>> wsMirrorAssemblyMap)
   {
      if(info.isTable()) {
         renameWSTable(assembly, info, causeRenameInfos, assemblies);
      }
      else if(info.isColumn()) {
         renameWSColumns(assembly, info, causeRenameInfos, assemblies, wsMirrorAssemblyMap);
      }
   }

   // To be oerride by children. Different source will override different values.
   // logicmodel, change source info and worksheet entry.
   // ws, only change worksheet entry.
   // query, change source info and worksheet entry.
   // the worksheet entry's ralation structure is different for lm/query and ws. So split them.
   protected void renameVSSource(Element doc, RenameInfo info) {
      if(!isSameVSSource0(doc, info)) {
         return;
      }

      // rename wentry for query/lm/ws
      // rename calcsource/sourceinfo/bindinginfo/seletiontable for query/lm
      renameWSEntrys(doc, info);
      renameCalcSource(doc, info);
      renameSourceInfos(doc, info);
      renameVSBindingInfos(doc, info);
   }

   protected void renameAssetEntry(Element elem, RenameInfo info) {
   }

   protected void renameWSSource(Element doc, RenameInfo info) {
   }

   // To be oerride by children. Different source will override different values.
   // logicmodel, its source will not save table name, but model name. So do nothing.
   // ws, its source name is table name, so change it to new.
   // query, there is no type to change table name, only change two type: source and column.
   protected void renameVSTable(Element table, RenameInfo info) {
   }

   protected void renameScript(Element doc, RenameInfo info) {
   }

   // query, do nothing. LM and ws, fix its table name and entity name.
   protected void renameWSTable(Element table, RenameInfo info,
                                List<RenameInfo> causeRenameInfos, NodeList assemblies)
   {
   }

   // fix column names.
   protected void renameWSColumns(Element assembly, RenameInfo info,
                                  List<RenameInfo> causeRenameInfos,
                                  NodeList assemblies,
                                  Map<String, Set<Element>> wsMirrorAssemblyMap)
   {
      renameWSColumns(assembly, info, causeRenameInfos, assemblies, wsMirrorAssemblyMap, false);
   }

   // fix column names.
   private void renameWSColumns(Element assembly, RenameInfo info,
                                List<RenameInfo> causeRenameInfos, NodeList assemblies,
                                Map<String, Set<Element>> wsMirrorAssemblyMap, boolean mirrorTable)
   {
      if(!isSameWSSource(assembly, info) && !mirrorTable) {
         return;
      }

      NodeList list = Tool.getChildNodesByTagName(assembly, "dataRef", true);
      boolean hasAlias = false;
      boolean renameSuccess = false;

      for(int j = 0; j < list.getLength(); j++){
         Element ref = (Element) list.item(j);

         if("inetsoft.uql.erm.ExpressionRef".equals(Tool.getAttribute(ref, "class"))) {
            renameExpressionRef(ref, info, ".");
         }
         else if(renameWSColumn(ref, info)) {
            renameSuccess = true;
            NodeList alias = getChildNodes(ref, "./alias");

            if(alias != null && alias.getLength() > 0 &&
               !StringUtils.isEmpty(Tool.getValue(alias.item(0))))
            {
               hasAlias = true;
            }
         }
      }

      if(!renameSuccess || hasAlias) {
         return;
      }

      String assemblyName = getAssemblyName(assembly);

      if(!StringUtils.isEmpty(assemblyName)) {
         RenameInfo rinfo = null;

         if(info.isColumn()) {
            rinfo = new RenameInfo(getWSColumnName(info, true),
               getWSColumnName(info, false), RenameInfo.ASSET | RenameInfo.COLUMN,
               getIdentifier(), assemblyName);
         }
         else if(info.isTable()) {
            rinfo = new RenameInfo(info.getOldName(),
               info.getNewName(), RenameInfo.ASSET | RenameInfo.TABLE,
               getIdentifier(), assemblyName);
         }

         causeRenameInfos.add(rinfo);
      }

      // Fix jointable on current table in current worksheet. Then fix its dependences by
      // causeRerenameInfo.Only process when no alias.
      renameJoinTables(assemblyName, info, causeRenameInfos, assemblies);

      // fix other table's expression column using this column
      renameDependExpressionRef(assemblies, info, assemblyName);

      renameVariableColumns(assemblyName, info, causeRenameInfos, assemblies);

      if(!StringUtils.isEmpty(assemblyName) && info.isColumn()) {
         String oldName = info.getOldName();
         String newName = info.getNewName();

         if(oldName == null || !oldName.contains(".") || newName == null ||
            !newName.contains("."))
         {
            return;
         }

         if(wsMirrorAssemblyMap != null && !StringUtils.isEmpty(assemblyName) &&
            wsMirrorAssemblyMap.get(assemblyName) != null)
         {
            String mirrorColumnOldName = assemblyName + oldName.substring(oldName.indexOf("."));
            String mirrorColumnNewName = assemblyName + newName.substring(newName.indexOf("."));
            RenameInfo mirrorRenameInfo = new RenameInfo(mirrorColumnOldName, mirrorColumnNewName,
               RenameInfo.ASSET | RenameInfo.COLUMN, getIdentifier(), assemblyName);
            Set<Element> mirrorAssemblies = wsMirrorAssemblyMap.get(assemblyName);

            for(Element mirrorAssembly : mirrorAssemblies) {
               renameWSColumns(mirrorAssembly, mirrorRenameInfo, causeRenameInfos, assemblies,
                  wsMirrorAssemblyMap, true);
            }
         }
      }
   }

   private void renameVariableColumns(String assemblyName, RenameInfo info,
                                      List<RenameInfo> causeRenameInfos, NodeList assemblies)
   {
      if(assemblies == null) {
         return;
      }

      for(int i = 0; i < assemblies.getLength(); i++) {
         Element assembly = (Element) assemblies.item(i);
         String name = getAssemblyName(assembly);

         if(Tool.equals(assemblyName, name)) {
            continue;
         }

         String className = assembly.getAttribute("class");

         if(className.indexOf("DefaultVariableAssembly") == -1) {
            continue;
         }

         renameVariableColumn(assembly, assemblyName, info, causeRenameInfos);
      }
   }

   private void renameVariableColumn(Element assembly, String assemblyName, RenameInfo info,
                                      List<RenameInfo> causeRenameInfos)
   {
      Element variable = Tool.getChildNodeByTagName(assembly, "variable");
      Element table = Tool.getChildNodeByTagName(variable, "tableAssembly");
      String tname = Tool.getValue(table);

      if(!Tool.equals(assemblyName, tname)) {
         return;
      }

      NodeList list = getChildNodes(variable, ".//dataRef");
      String oname = info.getOldName();
      String nname = info.getNewName();

      for(int i = 0; i < list.getLength(); i++){
         Element ref = (Element) list.item(i);

         renameWSColumn(ref, info);
      }
   }

   /**
    * fix other table's expression column using this column
    * @param assemblies all assemblies in ws
    * @param info rename info
    * @param assemblyName current rename assembly
    */
   protected void renameDependExpressionRef(NodeList assemblies, RenameInfo info,
                                            String assemblyName)
   {
      // no op
   }

   private void renameJoinTables(String assemblyName, RenameInfo info,
                                 List<RenameInfo> causeRenameInfos, NodeList assemblies)
   {
      if(assemblies == null) {
         return;
      }

      for(int i = 0; i < assemblies.getLength(); i++) {
         Element assembly = (Element) assemblies.item(i);
         String name = getAssemblyName(assembly);

         if(Tool.equals(assemblyName, name)) {
            continue;
         }

         String className = assembly.getAttribute("class");

         if(className.indexOf("RelationalJoinTableAssembly") == -1) {
            continue;
         }

         renameJoinTable(assemblyName, assembly, info, causeRenameInfos);
         renameOperators(assembly, info);
      }
   }

   private void renameJoinTable(String assemblyName, Element assembly, RenameInfo info,
                                List<RenameInfo> causeRenameInfos)
   {
      NodeList list = Tool.getChildNodesByTagName(assembly, "dataRef", true);
      boolean renameSuccess = false;
      boolean hasAlias = false;
      String oname = info.getOldName();
      String nname = info.getNewName();

      if(oname.contains(".")) {
         oname = oname.substring(oname.lastIndexOf('.') + 1);
      }

      if(nname.contains(".")) {
         nname = nname.substring(nname.lastIndexOf('.') + 1);
      }

      RenameInfo ninfo = new RenameInfo(oname, nname, info.getType());

      for(int j = 0; j < list.getLength(); j++){
         Element ref = (Element) list.item(j);

         if(renameJoinTableColumn(assemblyName, ref, ninfo) && !hasSqlAlias(ref)) {
            renameSuccess = true;
            NodeList alias = getChildNodes(ref, "./alias");

            if(alias != null && alias.getLength() > 0 &&
               !StringUtils.isEmpty(Tool.getValue(alias.item(0))))
            {
               hasAlias = true;
            }
         }
      }

      if(!renameSuccess || hasAlias) {
         return;
      }

      String joinName = getAssemblyName(assembly);

      if(!StringUtils.isEmpty(assemblyName)) {
         causeRenameInfos.add(new RenameInfo(getWSColumnName(ninfo, true),
            getWSColumnName(ninfo, false), RenameInfo.ASSET | RenameInfo.COLUMN,
            getIdentifier(), joinName));
      }
   }

   private void renameOperators(Element assembly, RenameInfo info) {
      NodeList list = getChildNodes(assembly,  "./assemblyInfo/operators/pairOperator" +
              "/tableAssemblyOperator/operator");

      if(list.getLength() == 0) {
         return;
      }

      for(int j = 0; j < list.getLength(); j++) {
         Element ref = (Element) list.item(j);
         Element leftColumn = getChildNode(ref, "./leftColumn/dataRef/dataRef");

         if(leftColumn != null) {
            renameLeftRightColumn(leftColumn, info);
         }

         Element rightColumn = getChildNode(ref, "./rightColumn/dataRef/dataRef");

         if(rightColumn != null) {
            renameLeftRightColumn(rightColumn, info);
         }
      }
   }

   private void renameLeftRightColumn(Element node, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();
      String oentity = null;

      if(oname.contains(".")) {
         oentity = oname.substring(0, oname.lastIndexOf('.'));
         oname = oname.substring(oname.lastIndexOf('.') + 1);
      }

      if(nname.contains(".")) {
         nname = nname.substring(nname.lastIndexOf('.') + 1);
      }

      String ottr = Tool.getAttribute(node, "attribute");
      String oen = Tool.getAttribute(node, "entity");

      if(Tool.equals(oentity, oen) && Tool.equals(ottr, oname)) {
         node.setAttribute("attribute", nname);

         replaceChildValue(node, "view", oname, nname, false);
      }
   }

   /**
    * Bug #44952. When sql alias exists in the column, rename info should not be passed
    * to join table, because join table uses sqlAlias.
    *
    * <dataRef class="inetsoft.uql.asset.DateRangeRef" ...>
    *    <dataRef class="inetsoft.uql.erm.AttributeRef"
    *       entity="Order Details" attribute="Date2222222222">
    *    ...
    *    </dataRef>
    *    <attribute>
    *       <![CDATA[ Year ]]>
    *    </attribute>
    * </dataRef>
    */
   private boolean hasSqlAlias(Element ref) {
      Node parentNode = ref.getParentNode();
      NodeList sqlAlias = null;

      if(parentNode != null) {
         sqlAlias = Tool.getChildNodesByTagName(parentNode, "attribute");
      }

      return sqlAlias != null && sqlAlias.getLength() == 1;
   }

   private boolean renameJoinTableColumn(String assemblyName, Element elem, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();
      String oattr = Tool.getAttribute(elem, "attribute");
      String oentity = Tool.getAttribute(elem, "entity");

      if(Tool.equals(assemblyName, oentity) && Tool.equals(oname, oattr)) {
         replaceAttribute(elem, "attribute", oname, nname, true);
         replaceChildValue(elem, "view", oname, nname, false);

         return true;
      }

      return false;
   }

   private String getAssemblyName(Element assembly) {
      NodeList assemblyNameEle = getChildNodes(assembly, "./assemblyInfo/name");
      String assemblyName = null;

      if(assemblyNameEle != null && assemblyNameEle.getLength() == 1) {
         assemblyName = Tool.getValue(assemblyNameEle.item(0));
      }

      return assemblyName;
   }

   protected boolean renameWSColumn(Element elem, RenameInfo info) {
      return false;
   }

   protected void renameVSColumns(Element assembly, RenameInfo info) {
      renameVSColumns(assembly, info, false);
   }

   protected void renameVSColumns(Element assembly, RenameInfo info, boolean isAllAggs) {
      renameVSColumns(assembly, info, isAllAggs, false);
   }

   protected void renameVSColumns(Element assembly, RenameInfo info, boolean isAllAggs,
                                  boolean isBookMark)
   {
      if(!isSameVSSource(assembly, info, isAllAggs) && !isBookMark) {
         return;
      }

      renameDataRefs(assembly, info);
      renameBindingInfos(assembly, info);
      renameCellBindings(assembly, info);
      renameTablePaths(assembly, info);
      renameSelectionMeasureValue(assembly, info);
      renameVSFieldValue(assembly, info);
      renameSelectionColumnValue(assembly, info);
      renameChoiceQueryValue(assembly, info);
   }

   private void renameSelectionColumnValue(Element assembly, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();
      String assemblyClass = Tool.getAttribute(assembly, "class");

      if("inetsoft.uql.viewsheet.SliderVSAssembly".equals(assemblyClass) ||
         "inetsoft.uql.viewsheet.SpinnerVSAssembly".equals(assemblyClass) ||
         "inetsoft.uql.viewsheet.RadioButtonVSAssembly".equals(assemblyClass) ||
         "inetsoft.uql.viewsheet.ComboBoxVSAssembly".equals(assemblyClass) ||
         "inetsoft.uql.viewsheet.CheckBoxVSAssembly".equals(assemblyClass) ||
         "inetsoft.uql.viewsheet.TextInputVSAssembly".equals(assemblyClass))
      {
         Element columnValue = getChildNode(assembly, "./assemblyInfo/columnValue");
         String value = Tool.getValue(columnValue);

         if(Tool.equals(value, oname)) {
            replaceCDATANode(columnValue, nname);
         }
      }

      if("inetsoft.uql.viewsheet.SelectionTreeVSAssembly".equals(assemblyClass)) {
         NodeList list = getChildNodes(assembly,
            "./assemblyInfo/parentID | ./assemblyInfo/parentValue | ./assemblyInfo/id | ./assemblyInfo/childValue |" +
               " ./assemblyInfo/label | ./assemblyInfo/labelValue | ./assemblyInfo/measure | " +
               "./assemblyInfo/measureValue");
         String oentityPrefix = null;
         String nentityPrefix = null;

         if(info.isEntity()) {
            oentityPrefix = info.getOldName() + ":";
            nentityPrefix = info.getNewName() + ":";
         }

         if(info.isLogicalModel() && info.isColumn() && !Tool.isEmptyString(info.getTable())) {
            String table = info.getTable();
            String oldPrefix = table + ".";
            String newPrefix = table + ":";

            if(oname.startsWith(oldPrefix)) {
               oname = newPrefix + oname.substring(oldPrefix.length());
            }

            if(nname.startsWith(oldPrefix)) {
               nname = newPrefix + nname.substring(oldPrefix.length());
            }
         }

         for(int i = 0; i < list.getLength(); i++) {
            Element elem = (Element) list.item(i);
            String value = Tool.getValue(elem);

            if(info.isEntity() && value != null && value.startsWith(oentityPrefix)) {
               replaceCDATANode(elem, value.replace(oentityPrefix, nentityPrefix));
            }
            else {
               if(Tool.equals(Tool.getValue(elem), oname)) {
                  replaceCDATANode(elem, nname);
               }
            }
         }
      }
   }

   private void renameVSFieldValue(Element assembly, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();

      NodeList fields = getChildNodes(assembly, ".//VSFieldValue[@fieldName]");

      for(int i = 0; i < fields.getLength(); i++) {
         Element fieldValue = (Element) fields.item(i);
         replaceAttribute(fieldValue, "fieldName", oname, nname, true);
      }
   }

   private void renameChoiceQueryValue(Element assembly, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();
      NodeList fields = getChildNodes(assembly, "./assemblyInfo/conditions/condition/condition_data");

      for(int i = 0; i < fields.getLength(); i++) {
         Element fieldValue = (Element) fields.item(i);

         if(fieldValue != null &&
            Tool.getAttribute(fieldValue, "fieldname") != null &&
            Tool.getAttribute(fieldValue,"fieldname").contains("]:[" + oname))
         {
            replaceAttribute(fieldValue, "fieldname", oname, nname, false);
         }
      }
   }

   protected void renameSelectionMeasureValue(Element assembly, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();
      NodeList measures = getChildNodes(assembly, "./assemblyInfo/measure");

      for(int i = 0; i < measures.getLength(); i++) {
         Element measure = (Element)measures.item(i);
         String cval = Tool.getValue(measure);

         if(Tool.equals(cval, oname)) {
            replaceCDATANode(measure, nname);
         }
      }

      NodeList measureValues = getChildNodes(assembly, "./assemblyInfo/measureValue");

      for(int i = 0; i < measureValues.getLength(); i++) {
         Element measureValue = (Element)measureValues.item(i);
         String cval = Tool.getValue(measureValue);

         if(Tool.equals(cval, oname)) {
            replaceCDATANode(measureValue, nname);
         }
      }
   }

   // check for column and entity.
   private boolean isSameVSSource(Element elem, RenameInfo info, boolean isAllAggs) {
      // For rename column and entity, should check source is the same or not. Others no need.
      if(!info.isColumn() && !info.isEntity()) {
         return true;
      }

      String assmblyType = getVSAssemblyType(elem);
      String source = info.isWorksheet() ?  info.getTable() : info.getSource();

      if("ChartVSAssembly".equals(assmblyType) || "CrosstabVSAssembly".equals(assmblyType) ||
         "CalcTableVSAssembly".equals(assmblyType) || "TableVSAssembly".equals(assmblyType) ||
         "EmbeddedTableVSAssembly".equals(assmblyType))
      {
         return Tool.equals(source, getString(elem, "./assemblyInfo/sourceInfo/source/text()"));
      }
      else if("GaugeVSAssembly".equals(assmblyType) || "ImageVSAssembly".equals(assmblyType)
         || "TextVSAssembly".equals(assmblyType))
      {
         return Tool.equals(source, getString(elem, "./assemblyInfo/bindingInfo/table/text()"));
      }
      else if("SelectionListVSAssembly".equals(assmblyType) || "SliderVSAssembly".equals(assmblyType) ||
         "CalendarVSAssembly".equals(assmblyType) || "SelectionTreeVSAssembly".equals(assmblyType) ||
         "TimeSliderVSAssembly".equals(assmblyType) || "CurrentSelectionVSAssembly".equals(assmblyType) ||
         "SpinnerVSAssembly".equals(assmblyType) || "RadioButtonVSAssembly".equals(assmblyType) ||
         "ComboBoxVSAssembly".equals(assmblyType) || "CheckBoxVSAssembly".equals(assmblyType) ||
         "TextInputVSAssembly".equals(assmblyType))
      {
         return Tool.equals(source, getString(elem, "./assemblyInfo/firstTable/text()")) ||
            Tool.equals(source, getString(elem, "./assemblyInfo/table/text()")) ||
            Tool.equals(source, getString(elem, "./assemblyInfo/bindingInfo/table/text()")) ||
            Tool.equals(source, getString(elem, "./assemblyInfo/additionalTables/table/text()"));
      }
      else if(isAllAggs) {
         return Tool.equals(source, getString(elem, "./tname/text()"));
      }

      return false;
   }

   // Check for data source and source. Only check for query and model, for ws, it will have
   // identity as oname and nname, so it can compare rightly.
   // For query and model, if set the same rename info to one source, it will change wrong:
   // such as:    query1    send two rename info(query1 -> query11), it will change to query111.
   // add to this check to avoid this.
   protected boolean isSameVSSource0(Element doc, RenameInfo info) {
      if(!info.isSource() && !info.isDataSource() || info.isWorksheet() ||
         info.isCube() && info.isLogicalModel())
      {
         return true;
      }

      String source = null;

      if(info.isSource()) {
         source = info.getOldName();
      }
      else if(info.isDataSource()) {
         source = info.getSource();
      }

      NodeList list = getChildNodes(doc, "./worksheetEntry/assetEntry | ./assemblies/oneAssembly/assembly/worksheetEntry/assetEntry");

      for(int i = 0; i < list.getLength(); i++) {
         if(list.item(i) instanceof Element) {
            Element elem = (Element) list.item(i);

            Element pathNode = Tool.getChildNodeByTagName(elem, "path");
            String path = Tool.getValue(pathNode);
            String[] paths = path.split("/");
            final List<String> allTableTypes = new ArrayList<>(Arrays.asList(
               "EXTERNAL TABLE", "TABLE", "VIEW", "SYNONYM", "ALIAS",
               "MATERIALIZED VIEW", "BASE TABLE", "PARTITIONED TABLE"));
            String type = null;
            int tableIdx = -1;

            for(String tableType : allTableTypes) {
               tableIdx = path.indexOf("/" + tableType + "/");

               if(tableIdx >= 0) {
                  type = tableType;
                  break;
               }
            }

            String databaseTable = null;

            if(tableIdx >= 0) {
               databaseTable = path.substring(tableIdx + type.length() + 2).replace("/", ".");
            }

            if(databaseTable != null && Tool.equals(source, databaseTable) || paths.length > 0 &&
               Tool.equals(source, paths[paths.length - 1]))
            {
               return true;
            }
         }
      }

      return false;
   }

   private String getVSAssemblyType(Element elem) {
      if(elem == null) {
         return null;
      }

      String className = elem.getAttribute("class");

      if(className == null) {
         return null;
      }

      int lastIndex = className.lastIndexOf('.');

      if(lastIndex < 0 || lastIndex >= className.length() - 1) {
         return className;
      }
      else {
         return className.substring(lastIndex + 1, className.length());
      }
   }

   protected boolean isSameWSSource(Element elem, RenameInfo info) {
      return DependencyTool.isSameWorkSheetSource(elem, info);
   }

   /**
    * Transform expression.
    */
   protected void renameExpressionRef(Element elem, RenameInfo info, String spliter) {
   }

   /**
    * Transform expression.
    */
   protected void transformExpression(Element elem, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();

      if(oname == null || nname == null) {
         return;
      }

      String content = Tool.getValue(elem);

      if(content == null) {
         return;
      }

      boolean entity = info.isEntity();
      boolean changed = false;

      // for expression:
      // vs: field['formula([Customer:Address], ...)']
      String field = "[" + oname + (entity ? "" : "]");
      String nfield = "[" + nname + (entity ? "" : "]");

      if(content.indexOf(field) != -1) {
         content = Tool.replaceAll(content, field, nfield);
         changed = true;
      }

      // for expression:
      // vs: field['Customer:Region']
      // ws: field['Order.Discount']
      field = "field['" + oname + (entity ? "" : "']");
      nfield = "field['" + nname + (entity ? "" : "']");

      if(content.indexOf(field) != -1) {
         content = Tool.replaceAll(content, field, nfield);
         changed = true;
      }

      if(changed) {
         replaceCDATANode(elem, content);
      }
   }

   // To be override by logic model, query and ws, only change attribute and view data.
   protected void renameVSColumn(Element elem, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();

      if(Tool.equals(oname, Tool.getAttribute(elem, "attribute"))) {
         replaceAttribute(elem, "attribute", oname, nname, true);
      }

      Element attrRef = Tool.getChildNodeByTagName(elem, "attribute");

      if(attrRef != null) {
         replaceChildValue(elem, "attribute", oname, nname, true);
      }

      Element refVal = Tool.getChildNodeByTagName(elem, "refValue");

      if(refVal != null) {
         // fix VSAggregate
         if(Tool.equals(oname, Tool.getValue(refVal))) {
            replaceChildValue(elem, "refValue", oname, nname, true);
            replaceChildValue(elem, "refRValue", oname, nname, true);
            replaceChildValue(elem, "fullName", oname, nname, false);
            replaceChildValue(elem, "oriFullName", oname, nname, false);
            replaceChildValue(elem, "view", oname, nname, false);
         }
      }

      Element secondaryVal = Tool.getChildNodeByTagName(elem, "secondaryValue");

      if(secondaryVal != null) {
         // fix VSAggregate secondaryColumn
         if(Tool.equals(oname, Tool.getValue(secondaryVal))) {
            replaceChildValue(elem, "secondaryValue", oname, nname, true);
            replaceChildValue(elem, "secondaryRValue", oname, nname, true);
            replaceChildValue(elem, "fullName", oname, nname, false);
            replaceChildValue(elem, "oriFullName", oname, nname, false);
         }
      }

      Element viewElem = Tool.getChildNodeByTagName(elem, "view");

      if(viewElem != null) {
         if(Tool.equals(oname, Tool.getValue(viewElem))) {
            replaceChildValue(elem, "view", oname, nname, false);
         }
      }

      Element groupVal = Tool.getChildNodeByTagName(elem, "groupValue");

      if(groupVal != null) {
         // fix VSDimensionRef
         if(Tool.equals(oname, Tool.getValue(groupVal))) {
            replaceChildValue(elem, "groupValue", oname, nname, true);
            replaceChildValue(elem, "groupRValue", oname, nname, true);
            replaceChildValue(elem, "view", oname, nname, false);
         }
      }

      Element rankVal = Tool.getChildNodeByTagName(elem, "rankingColValue");

      if(rankVal != null) {
         String rank = Tool.getValue(rankVal);

         // fix ranking
         if(Tool.equals(oname, rank)) {
            replaceChildValue(elem, "rankingColValue", oname, nname, true);
            replaceChildValue(elem, "rankingColRValue", oname, nname, true);
         }
         else if(rank != null && rank.indexOf("(" + oname + ")") != -1) {
            oname = "(" + oname + ")";
            nname = "(" + nname + ")";
            replaceChildValue(elem, "rankingColValue", oname, nname, false);
            replaceChildValue(elem, "rankingColRValue", oname, nname, false);
         }
         else if(rank != null && rank.indexOf("(" + oname + ",") != -1) {
            oname = "(" + oname + ",";
            nname = "(" + nname + ",";
            replaceChildValue(elem, "rankingColValue", oname, nname, false);
            replaceChildValue(elem, "rankingColRValue", oname, nname, false);
         }
         else if(rank != null && rank.indexOf(", " + oname + ")") != -1) {
            oname = ", " + oname + ")";
            nname = ", " + nname + ")";
            replaceChildValue(elem, "rankingColValue", oname, nname, false);
            replaceChildValue(elem, "rankingColRValue", oname, nname, false);
         }
      }

      Element sortVal = Tool.getChildNodeByTagName(elem, "sortByColValue");

      if(sortVal != null) {
         String sort = Tool.getValue(sortVal);

         // fix sort
         if(Tool.equals(oname, sort)) {
            replaceChildValue(elem, "sortByColValue", oname, nname, true);
            replaceChildValue(elem, "sortByColRValue", oname, nname, true);
         }
         else if(sort != null && sort.indexOf("(" + oname + ")") != -1) {
            replaceChildValue(elem, "sortByColValue", oname, nname, false);
            replaceChildValue(elem, "sortByColRValue", oname, nname, false);
         }
      }
   }

   // To be override by logic model, query and ws, only change column value and view data.
   protected void renameVSBinding(Element binding, RenameInfo info) {
      String oname = info.getOldName().replace(".", ":");
      String nname = info.getNewName().replace(".", ":");

      // for query and model, change source name, for ws, change table name.
      if(info.isSource() || info.isTable()) {
         replaceChildValue(binding, "table", oname, nname, true);
      }
      else if(info.isColumn()) {
         Element col = Tool.getChildNodeByTagName(binding, "columnValue");
         String cval = Tool.getValue(col);

         if(Tool.equals(cval, oname)) {
            replaceCDATANode(col, nname);
            replaceChildValue(binding, "view", oname, nname, true);
         }

         Element col2 = Tool.getChildNodeByTagName(binding, "column2Value");
         String val2 = Tool.getValue(col2);

         if(Tool.equals(cval, oname)) {
            replaceCDATANode(col2, nname);
         }
      }
   }

   protected void renameWSEntrys(Element doc, RenameInfo info) {
      NodeList list = getChildNodes(doc, "//worksheetEntry/assetEntry | //assemblies/oneAssembly/assembly/worksheetEntry/assetEntry");

      for(int i = 0; i < list.getLength(); i++) {
         renameAssetEntry((Element) list.item(i), info);
      }
   }

   protected String getVSSourcePath() {
      return "./assemblyInfo/sourceInfo/source |" +
            "./state_calctable/assemblyInfo/sourceInfo/source |" +
            "./assemblyInfo/firstTable |" +
            "./oneAssembly/assembly/assemblyInfo/firstTable | " +
            "./assemblyInfo/bindingInfo/table | " +
            "./state_table |" +
            "./allcalc/tname |" +
            "./assemblies/oneAssembly/assembly/assemblyInfo/sourceInfo/source |" +
            "./assemblies/oneAssembly/assembly/state_calctable/assemblyInfo/sourceInfo/source |" +
            "./assemblies/oneAssembly/assembly/assemblyInfo/firstTable |" +
            "./assemblies/oneAssembly/assembly/oneAssembly/assembly/assemblyInfo/firstTable | " +
            "./assemblies/oneAssembly/assembly/assemblyInfo/bindingInfo/table | " +
            "./assemblies/oneAssembly/assembly/state_table";
   }

   protected void renameSourceInfos(Element doc, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();

      if(info.isSource() || info.isWorksheet() && info.isTable()) {
         NodeList list = getChildNodes(doc, "./assemblies/oneAssembly/assembly");
         NodeList list2 = getChildNodes(doc, "./allcalc/tname");

         for(int i = 0; i < list.getLength(); i++) {
            Element assembly = (Element) list.item(i);
            NodeList slist = getChildNodes(assembly, getVSSourcePath());

            for(int j = 0; j < slist.getLength(); j++) {
               Element sinfo = (Element) slist.item(j);

               if(Tool.equals(oname, Tool.getValue(sinfo))) {
                  replaceCDATANode(sinfo, nname);
               }
            }

            for(int j = 0; j < list2.getLength(); j++) {
               Element sinfo = (Element) list2.item(j);

               if(Tool.equals(oname, Tool.getValue(sinfo))) {
                  replaceCDATANode(sinfo, nname);
               }
            }
         }

         return;
      }

      NodeList list = getChildNodes(doc,
         "./assemblies/oneAssembly/assembly/assemblyInfo/sourceInfo");

      for(int i = 0; i < list.getLength(); i++) {
         Element sinfo = (Element) list.item(i);

         if(info.isDataSource()) {
            Element pnode = Tool.getChildNodeByTagName(sinfo, "prefix");
            String opre = Tool.getValue(pnode);

            if(opre != null && opre.endsWith(oname)) {
               replaceCDATANode(pnode, opre.replace(oname, nname));
            }
         }
         else if(info.isDataSourceFolder()) {
            replaceChildValue(sinfo, "prefix", oname, nname, false);
         }
      }
   }

   protected void renameSelectionTables(Element doc, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();
      NodeList list = getChildNodes(doc,
         "./assemblies/oneAssembly/assembly/assemblyInfo/firstTable |" +
            " ./assemblies/oneAssembly/assembly/assemblyInfo/table");

      for(int i = 0; i < list.getLength(); i++) {
         Element table = (Element) list.item(i);

         // for query and model, change source name, for ws, change table name.
         if(info.isSource() || info.isTable()) {
            if(Tool.equals(oname, Tool.getValue(table))) {
               replaceCDATANode(table, nname);
            }
         }
      }
   }

   protected void renameDataRefs(Element doc, RenameInfo info) {
      NodeList list = getChildNodes(doc, ".//dataRef");
      String oname = info.getOldName();
      String nname = info.getNewName();

      for(int i = 0; i < list.getLength(); i++){
         Element ref = (Element) list.item(i);

         if("inetsoft.uql.erm.ExpressionRef".equals(Tool.getAttribute(ref, "class"))) {
            renameExpressionRef(ref, info, ":");

            if(Tool.equals(oname, Tool.getAttribute(ref, "name"))) {
               replaceAttribute(ref, "name", oname, nname, true);
            }
         }
         else {
            renameVSColumn(ref, info);
         }
      }
   }

   // rename binding infos for source/table
   protected void renameVSBindingInfos(Element doc, RenameInfo info) {
      NodeList list = getChildNodes(doc, "./assemblies/oneAssembly/assembly/assemblyInfo/bindingInfo");

      for(int i = 0; i < list.getLength(); i++) {
         Element binding = (Element) list.item(i);
         renameVSBinding(binding, info);
      }
   }

   // rename binding info for columns
   protected void renameBindingInfos(Element doc, RenameInfo info) {
      NodeList list = getChildNodes(doc, "./assemblyInfo/bindingInfo");

      for(int i = 0; i < list.getLength(); i++) {
         Element binding = (Element) list.item(i);
         renameVSBinding(binding, info);
      }
   }

   protected void renameCellBindings(Element doc, RenameInfo info) {
      NodeList list = getChildNodes(doc, ".//cellBinding");

      for(int i = 0; i < list.getLength(); i++) {
         Element binding = (Element) list.item(i);
         renameCellBinding(binding, info);
      }
   }

   // override by logic model and ws:
   // model should change oname's . to :
   // ws should check source table name.
   protected void renameCellBinding(Element binding, RenameInfo info) {
      if(!("" + CellBinding.BIND_COLUMN).equals(Tool.getAttribute(binding, "type"))) {
         return;
      }

      String oname = info.getOldName();
      String nname = info.getNewName();
      replaceChildValue(binding, "value", oname, nname, true);
      replaceChildValue(binding, "formula", oname, nname, false);

      // For freehand table, its cell name can be used default cell name, it will create according
      // to column name, so rename column will re-create new default cell name, then bindings such
      // as mergeRowGroup/mergeColGroup/rowGroup/colGroup will not transform for it is not the same
      // as column name but the same as default cell name of changed column.
      // The default name will replace some character to "_", so using default name to transform
      String odname = getDefaultCellName(oname);
      String ndname = getDefaultCellName(nname);
      replaceChildValue(binding, "mergeRowGrp", odname, ndname, true);
      replaceChildValue(binding, "mergeColGrp", odname, ndname, true);
      replaceChildValue(binding, "rowGroup", odname, ndname, true);
      replaceChildValue(binding, "colGroup", odname, ndname, true);
   }

   private String getDefaultCellName(String name) {
      name = name.replaceAll("\\.", "_");
      name = name.replaceAll(" ", "_");
      name = name.replaceAll(":", "_");

      return name;
   }

   protected String getWSColumnName(RenameInfo info, boolean old) {
      return null;
   }

   private static RenameInfo createCubeModelRenameInfo(RenameInfo rinfo) {
      String oname = null;
      String nname = null;

      if(rinfo.isSource()) {
         ArrayList<String> list = new ArrayList<>();
         list.add(Assembly.CUBE_VS);
         list.add(rinfo.getPrefix());
         list.add("/");
         list.add(rinfo.getOldName());
         oname = mergeString(list);

         list.set(3, rinfo.getNewName());
         nname = mergeString(list);
      }
      else if(rinfo.isDataSource() || rinfo.isDataSourceFolder()) {
         ArrayList<String> list = new ArrayList<>();
         list.add(Assembly.CUBE_VS);
         list.add(rinfo.getOldName());
         list.add("/");
         list.add(rinfo.getSource());
         oname = mergeString(list);

         list.set(1, rinfo.getNewName());
         nname = mergeString(list);
      }

      return new RenameInfo(oname, nname, rinfo.getType());
   }

   private static String mergeString(ArrayList<String> list) {
      if(list == null || list.isEmpty()) {
         return "";
      }

      StringBuilder builder = new StringBuilder();
      list.stream().forEach(str -> builder.append(str));
      return builder.toString();
   }

   protected AssetEntry asset;
   public static final String ASSET_DEPEND_ELEMENTS = "./assemblies/oneAssembly/assembly |" +
      " ./assemblies/oneAssembly/assembly/oneAssembly/assembly";

   private static final Logger LOG = LoggerFactory.getLogger(AssetDependencyTransformer.class);
}
