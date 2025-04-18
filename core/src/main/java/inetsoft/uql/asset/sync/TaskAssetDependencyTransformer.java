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

import inetsoft.sree.schedule.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.store.port.TransformerUtil;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.XUtil;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Tool;
import inetsoft.util.dep.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.rmi.RemoteException;
import java.util.List;

/**
 *  process sync the task xml asset information when the dependency assets is change.
 *
 */
public class TaskAssetDependencyTransformer extends DependencyTransformer {
   public TaskAssetDependencyTransformer(AssetEntry task) {
      this.task = task;
   }
   @Override
   public RenameDependencyInfo process(List<RenameInfo> infos) {
      try {
         if(task == null || !task.isScheduleTask()) {
            return null;
         }

         AssetRepository repository = AssetUtil.getAssetRepository(false);
         IndexedStorage storage = repository.getStorage(task);
         renameTask(storage, infos);
      }
      catch(Exception e) {
         LOG.warn("Failed to rename dependency assets: ", e);
      }

      return null;
   }

   private void renameTask(IndexedStorage storage, List<RenameInfo> infos)
      throws Exception
   {
      String identifier = task.toIdentifier();
      Document doc;

      if(getAssetFile() != null) {
         doc = getAssetFileDoc();
      }
      else {
         synchronized(storage) {
            doc = storage.getDocument(identifier, task.getOrgID());
         }
      }

      if(doc != null) {
         if(task.isScheduleTask()) {
            renameTaskActions(doc.getDocumentElement(), infos);
         }

         if(getAssetFile() != null) {
            TransformerUtil.save(getAssetFile().getAbsolutePath(), doc);
         }
         else {
            saveAssets(storage, identifier, doc);
            updateScheduleServerTask();
         }
      }
   }

   private void updateScheduleServerTask() {
      ScheduleManager scheduleManager = ScheduleManager.getScheduleManager();

      if(scheduleManager == null || task == null || !task.isScheduleTask()) {
         return;
      }

      ScheduleTask scheduleTask = scheduleManager.getScheduleTask(task.getName(), task.getOrgID());

      if(scheduleTask == null) {
         return;
      }

      try {
         ScheduleClient.getScheduleClient().taskAdded(scheduleTask);
      }
      catch(RemoteException e) {
         LOG.error("Failed to update scheduler with extension task: " +
            scheduleTask.getTaskId(), e);
      }
   }

   private void renameTaskActions(Element doc, List<RenameInfo> infos) {
      if(infos == null) {
         return;
      }

      for(RenameInfo info : infos) {
         if(info.isReplet()) {
            renameRepletAction(doc, info);
         }
         else if(info.isViewsheet()) {
            renameViewsheetAction(doc, info);
         }

         renameBackUpAction(doc, info);
         renameBatchUpAction(doc, info);
      }
   }

   private void renameBatchUpAction(Element doc, RenameInfo info) {
      if(info.isColumn() && info.isPrimaryTable()) {
         renameBatchUpQueryParameters(doc, info);
      }
      else if(info.isWorksheet()) {
         if(!info.isColumn()) {
            renameBatchUpQuery(doc, info);
         }

         renameBatchUpQueryParameters(doc, info);
      }
   }

   private void renameBatchUpQuery(Element doc, RenameInfo info) {
      if(!info.isWorksheet()) {
         return;
      }

      try {
         String opath, npath = null;
         IdentityID ouser, nuser = null;
         int oscope, nscope = AssetRepository.GLOBAL_SCOPE;
         AssetEntry.Type type = AssetEntry.Type.WORKSHEET;

         if(info.isTable() && !StringUtils.isEmpty(info.getSource())) {
            type = AssetEntry.Type.TABLE;
            AssetEntry wentry = AssetEntry.createAssetEntry(info.getSource());
            oscope = nscope = wentry.getScope();
            opath = wentry.getPath() + "/" + info.getOldName();
            npath = wentry.getPath() + "/" + info.getNewName();
            ouser = nuser = wentry.getUser();
         }
         else {
            if(info.isTable()) {
               type = AssetEntry.Type.TABLE;
            }

            AssetEntry oldEntry = AssetEntry.createAssetEntry(info.getOldName());
            oscope = oldEntry.getScope();
            opath = oldEntry.getPath();
            ouser = oldEntry.getUser();

            AssetEntry newEntry = AssetEntry.createAssetEntry(info.getNewName());
            nscope = newEntry.getScope();
            npath = newEntry.getPath();
            nuser = newEntry.getUser();
         }

         String assetExpression = "//Task/Action/queryEntry/assetEntry[@type='%s' and @scope='%s']";
         assetExpression = String.format(assetExpression, type.id(), oscope);
         NodeList dependings = getChildNodes(doc, assetExpression);

         if(dependings == null || dependings.getLength() == 0) {
            if(info.isWorksheet() && info.getOldPath() != null) {
               String oldPath = info.getOldPath();
               String newPath = info.getNewPath();
               AssetEntry.Type ntype = AssetEntry.Type.TABLE;
               String assetExpression2 = "//Task/Action/queryEntry/assetEntry[@type='%s' and " +
                  "@scope='%s']";
               assetExpression2 = String.format(assetExpression2, ntype.id(), oscope);
               NodeList dependings2 = getChildNodes(doc, assetExpression2);

               if(dependings2 != null) {
                  renameBatchUpEntry(dependings2, oldPath, newPath, ouser, nuser, oscope, nscope);
               }
            }
         }
         else {
            renameBatchUpEntry(dependings, opath, npath, ouser, nuser, oscope, nscope);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to rename batch up query: ", ex);
      }
   }

   private void renameBatchUpEntry(NodeList dependings, String opath, String npath, IdentityID ouser,
                                   IdentityID nuser, int oscope, int nscope) {
      for(int i = 0; i < dependings.getLength(); i++) {
         Element item = (Element) dependings.item(i);

         if(item == null) {
            continue;
         }

         Element path = getChildNode(item, "path");

         if(!Tool.equals(opath, Tool.getValue(path))) {
            continue;
         }

         Element user = getChildNode(item, "user");

         if(!Tool.equals(ouser, Tool.getValue(user))) {
            continue;
         }

         replaceAttribute(item, "scope", oscope + "", nscope + "",
            true);
         replaceCDATANode(path, npath);

         if(user != null) {
            replaceCDATANode(user, nuser.convertToKey());
         }
      }
   }

   private void renameBatchUpQueryParameters(Element doc, RenameInfo info) {
      if(info.isWorksheet() && !info.isTable() && !info.isColumn()) {
         NodeList actions = getChildNodes(doc, "//Task/Action");

         for(int i = 0; i < actions.getLength(); i++) {
            Element action = (Element) actions.item(i);
            NodeList paths = getChildNodes(action, "./queryEntry/assetEntry/path");

            for(int j = 0; j < paths.getLength(); j++) {
               Element path = (Element) paths.item(i);
               String value = Tool.getValue(path);

               if(Tool.equals(value, info.getOldPath())) {
                  DependencyTransformer.replaceElementCDATANode(path, info.getNewPath());
               }

               if(info.isWorksheet()) {
                  String opath = AssetEntry.createAssetEntry(info.getOldName()).getPath();
                  String npath = AssetEntry.createAssetEntry(info.getNewName()).getPath();

                  if(value.startsWith(opath + "/")) {
                     String table = value.substring(opath.length());
                     DependencyTransformer.replaceElementCDATANode(path, npath + table);
                  }
               }
            }
         }

         return;
      }

      String source = info.getSource();
      AssetEntry sourceEntry = source == null ? null : AssetEntry.createAssetEntry(source);

      if(sourceEntry == null) {
         return;
      }

      NodeList actions = getChildNodes(doc, "//Task/Action");
      String path = sourceEntry.getPath();
      String tablePath = path;
      String ofullColName = info.getOldName();
      String nfullColName = info.getNewName();

      if(info.getTable() != null) {
         tablePath = path + "/" + info.getTable();
         ofullColName = info.getTable() + "." + info.getOldName();
         nfullColName = info.getTable() + "." + info.getNewName();
      }

      String oldColName2 = null;
      String newColName2 = null;

      if(info.getEntity() != null) {
         oldColName2 = info.getEntity() + "." + info.getOldName();
      }

      if(info.getEntity() != null) {
         newColName2 = info.isAlias() ? info.getNewName() : info.getEntity() + "." + info.getNewName();
      }

      OUTER:
      for(int i = 0; i < actions.getLength(); i++) {
         Element action = (Element) actions.item(i);
         String expr = ".//queryEntry/assetEntry[@type='%s' and @scope='%s']";
         expr = String.format(expr, AssetEntry.Type.TABLE.id(), sourceEntry.getScope());
         NodeList nodes = getChildNodes(action, expr);

         // not same source.
         if(nodes.getLength() == 0) {
            expr = ".//queryEntry/assetEntry[@type='%s' and @scope='%s']";
            expr = String.format(expr, AssetEntry.Type.WORKSHEET.id(), sourceEntry.getScope());
            nodes = getChildNodes(action, expr);
         }

         if(nodes.getLength() == 0) {
            continue;
         }

         for(int j = 0; j < nodes.getLength(); j++) {
            Element item = (Element) nodes.item(i);
            NodeList list = getChildNodes(item, ".//path");

            if(list.getLength() == 0) {
               continue OUTER;
            }

            Element pathElem = (Element) list.item(0);

            // not same source.
            if(!Tool.equals(Tool.getValue(pathElem), path) &&
               !Tool.equals(Tool.getValue(pathElem), tablePath))
            {
               continue OUTER;
            }
         }

         RenameInfo parentInfo = info.getParentRenameInfo();
         expr = "./queryParameters/map/entry/value";
         nodes = getChildNodes(action, expr);
         String oprefix = null;
         String nprefix = null;

         if(parentInfo != null && parentInfo.isLogicalModel() && parentInfo.isTable()) {
            oprefix = parentInfo.getOldName() + ".";
            nprefix = parentInfo.getNewName() + ".";
         }

         for(int j = 0; j < nodes.getLength(); j++) {
            Element item = (Element) nodes.item(j);
            String value = Tool.getValue(item);

            if(info.isWorksheet() && info.isTable() && value.startsWith(info.getOldName() + ".")){
               replaceCDATANode(item, value.replace(info.getOldName(), info.getNewName()));
            }
            else if(Tool.equals(value, info.getOldName())) {
               replaceCDATANode(item, info.alias ? info.getNewName() : newColName2);
            }
            else if(Tool.equals(value, oldColName2)) {
               replaceCDATANode(item, newColName2);
            }
            else if(Tool.equals(value, ofullColName)) {
               replaceCDATANode(item, nfullColName);
            }
            else if(parentInfo != null && parentInfo.isLogicalModel()) {
               if(parentInfo.isTable() && value != null && value.startsWith(oprefix)) {
                  replaceCDATANode(item, value.replace(oprefix, nprefix));
               }
               else if(parentInfo.isColumn() && Tool.equals(value, parentInfo.getOldName())) {
                  replaceCDATANode(item, parentInfo.getNewName());
               }
            }
         }
      }
   }

   private void renameBackUpAction(Element doc, RenameInfo info) {
      String type = null;
      String path = null;
      IdentityID user = null;

      if(info.isPartition()) {
         type = XPartitionAsset.XPARTITION;
      }
      else if(info.isLogicalModel()) {
         type = XLogicalModelAsset.XLOGICALMODEL;
      }
      else if(info.isVPM()) {
         type = VirtualPrivateModelAsset.VPM;
      }
      else if(info.isQuery()) {
         type = XQueryAsset.XQUERY;
      }
      else if(info.isDataSource()) {
         type = XDataSourceAsset.XDATASOURCE;
      }
      else if(info.isWorksheet()) {
         type = WorksheetAsset.WORKSHEET;
      }
      else if(info.isViewsheet()) {
         type = ViewsheetAsset.VIEWSHEET;
      }
      else if(info.isTableStyle()) {
         type = TableStyleAsset.TABLESTYLE;
      }
      else if(info.isScriptFunction()) {
         type = ScriptAsset.SCRIPT;
      }
      else if(info.isTask()) {
         type = ScheduleTaskAsset.SCHEDULETASK;
      }
      else if(info.isDashboard()) {
         type = DashboardAsset.DASHBOARD;
      }

      String newPath = null;
      IdentityID newUser = null;

      if(info.isVPM()) {
         path = convertVpmPathToAssetPath(info.getOldName());
         newPath = convertVpmPathToAssetPath(info.getNewName());
      }
      else if(info.isReplet() || info.isQuery() ||
         info.isTableStyle() || info.isScriptFunction())
      {
         path = info.getOldName();
         newPath = info.getNewName();
      }
      else if(info.isTask()) {
         String oldTaskPath = info.getOldPath();
         String newTaskPath = info.getNewPath();
         path = Tool.isEmptyString(oldTaskPath) || "/".equals(oldTaskPath) ? info.getOldName() :
            info.getOldPath() + "/" + info.getOldName();
         newPath = Tool.isEmptyString(newTaskPath)  || "/".equals(newTaskPath) ? info.getNewName() :
            info.getNewPath() + "/" + info.getNewName();
      }
      else if((info.isLogicalModel() || info.isPartition()) &&
         (info.isDataSource() || info.isDataSourceFolder()))
      {
         path = getModelPath(info, true);
         newPath = getModelPath(info, false);
      }
      else if(info.isLogicalModel() && info.isSource()) {
         path = getModelPath0(info, true);
         newPath = getModelPath0(info, false);
      }
      else if(info.isLogicalModel() && info.isFolder()) {
         path = info.getOldPath();
         newPath = info.getNewPath();
      }
      else if(info.isPartition() && info.isSource()) {
         path = getPartitionPath(info.getOldName(), info.getModelFolder());
         newPath = getPartitionPath(info.getNewName(), info.getModelFolder());
      }
      else if(info.isPartition() && info.isFolder()) {
         path = info.getOldPath();
         newPath = info.getNewPath();
      }
      else if(info.isDataSource()) {
         path = info.getOldName();
         newPath = info.getNewName();

         if(path.startsWith(Assembly.CUBE_VS)) {
            path = path.substring(Assembly.CUBE_VS.length());
         }

         if(newPath.startsWith(Assembly.CUBE_VS)) {
            newPath = newPath.substring(Assembly.CUBE_VS.length());
         }
      }
      else {
         try {
            AssetEntry oldEntry = AssetEntry.createAssetEntry(info.getOldName());
            AssetEntry newEntry = AssetEntry.createAssetEntry(info.getNewName());
            path = oldEntry.getPath();
            user = oldEntry.getUser();
            newPath = newEntry.getPath();
            newUser = newEntry.getUser();
         }
         catch(Exception ignore){
         }
      }

      String assetExpression = getAssetExpression(path, type, user, info);

      NodeList dependings = getChildNodes(doc, assetExpression);

      if(dependings == null) {
         return;
      }

      //newly created logical model might not contain splitter, try without
      if(dependings.getLength() == 0 && info.isLogicalModel() && path.contains(XUtil.DATAMODEL_FOLDER_SPLITER)) {
         path = path.replace(XUtil.DATAMODEL_FOLDER_SPLITER, "^");

         assetExpression = getAssetExpression(path, type, user, info);
         dependings = getChildNodes(doc, assetExpression);
      }

      for(int i = 0; i < dependings.getLength(); i++) {
         Element item = (Element) dependings.item(i);

         if(item == null) {
            continue;
         }

         item.setAttribute("path", Tool.byteEncode2(newPath));

         if(!info.isTask()) {
            item.setAttribute("user", newUser == null ? "" : newUser.convertToKey());
         }
      }
   }

   private String getAssetExpression(String path, String type, IdentityID user, RenameInfo info) {
      String assetExpression = "//Task/Action/XAsset[@type='%s' and @path='%s' and @user='%s']";
      assetExpression = String.format(assetExpression, type, Tool.byteEncode2(path),
                                      user == null ? "" : user.convertToKey());

      if(info.isTask()) {
         assetExpression = "//Task/Action/XAsset[@type='%s' and @path='%s']";
         assetExpression = String.format(assetExpression, type, Tool.byteEncode2(path));
      }

      return assetExpression;
   }

   private String convertVpmPathToAssetPath(String path) {
      if(path == null) {
         return path;
      }

      int index = path.lastIndexOf('/');

      if(index > 0 && index < path.length() - 1) {
         return path.substring(0, index) + "^" + path.substring(index + 1);
      }

      return path;
   }

   private void renameViewsheetAction(Element doc, RenameInfo info) {
      NodeList childNodes = getChildNodes(doc,
         "//Task/Action[@viewsheet='" + info.getOldName() + "']");

      if(childNodes == null) {
         return;
      }

      for(int i = 0; i < childNodes.getLength(); i++) {
         Element item = (Element) childNodes.item(i);

         if(item == null) {
            continue;
         }

         item.setAttribute("viewsheet", info.getNewName());
      }
   }

   private void renameRepletAction(Element doc, RenameInfo info) {
      NodeList childNodes = getChildNodes(doc,
         "//Task/Action[@replet='" + info.getOldName() + "']");

      if(childNodes == null) {
         return;
      }

      for(int i = 0; i < childNodes.getLength(); i++) {
         Element item = (Element) childNodes.item(i);

         if(item == null) {
            continue;
         }

         item.setAttribute("replet", info.getNewName());
      }
   }

   /**
    * Save assets.
    * @param storage
    * @param identifier  the asset identifier.
    * @param doc         the document after transformation.
    */
   private void saveAssets(IndexedStorage storage, String identifier, Document doc) {
      synchronized(storage) {
         storage.putDocument(identifier, doc, ScheduleTask.class.getName(), task.getOrgID());
      }
   }

   private String getModelPath(RenameInfo info, boolean old) {
      StringBuilder builder = new StringBuilder();
      builder.append(old ? info.getOldName() : info.getNewName());
      String folder = info.getModelFolder();

      if(!StringUtils.isEmpty(folder)) {
         builder.append(XUtil.DATAMODEL_FOLDER_SPLITER);
         builder.append(folder);
      }

      builder.append("^");
      builder.append(info.getSource().replace("/", "^"));
      return builder.toString();
   }

   private String getModelPath0(RenameInfo info, boolean old) {
      StringBuilder builder = new StringBuilder();
      builder.append(info.getPrefix());
      String folder = info.getModelFolder();

      if(!StringUtils.isEmpty(folder)) {
         builder.append(XUtil.DATAMODEL_FOLDER_SPLITER);
         builder.append(folder);
      }

      builder.append("^");
      builder.append(old ? info.getOldName() : info.getNewName());

      return builder.toString();
   }

   private String getPartitionPath(String path, String folder) {
      if(path == null) {
         return path;
      }

      StringBuilder builder = new StringBuilder();
      int index = path.lastIndexOf('/');

      if(Tool.isEmptyString(folder) && index > 0 && index < path.length() - 1) {
         builder.append(path, 0, index);
         builder.append("^");
         builder.append(path.substring(index + 1));
         return builder.toString();
      }
      else if(folder != null && index > 0){
         builder.append(path, 0, index);
         builder.append(XUtil.DATAMODEL_FOLDER_SPLITER);
         builder.append(folder);
         builder.append("^");
         builder.append(path.substring(index + 1));
         return builder.toString();
      }
      else {
         return path;
      }
   }

   private AssetEntry task;

   private static final Logger LOG = LoggerFactory.getLogger(TaskAssetDependencyTransformer.class);
}
