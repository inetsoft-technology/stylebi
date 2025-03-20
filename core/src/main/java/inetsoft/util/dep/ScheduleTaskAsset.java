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
package inetsoft.util.dep;

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.security.Principal;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * ScheduleTaskAsset represents a schedule task type asset.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class ScheduleTaskAsset extends AbstractXAsset {
   /**
    * Schedule task type XAsset.
    */
   public static final String SCHEDULETASK = "SCHEDULETASK";

   /**
    * Constructor.
    */
   public ScheduleTaskAsset() {
      super();
   }

   /**
    * Constructor.
    * @param taskId the schedule task asset id.
    * @param user the schedule task asset owner.
    */
   public ScheduleTaskAsset(String taskId, IdentityID user, long lastModifiedTime) {
      this();
      this.task = taskId;
      this.user = user;
      this.lastModifiedTime = lastModifiedTime;
   }

   /**
    * Get all dependencies of this asset.
    * @return an array of XAssetDependency.
    */
   @Override
   public XAssetDependency[] getDependencies() {
      List<XAssetDependency> dependencies = new ArrayList<>();
      ScheduleManager manager = ScheduleManager.getScheduleManager();
      String taskName = task.substring(task.lastIndexOf('/') + 1);
      ScheduleTask stask = manager.getScheduleTask(taskName);

      if(stask == null) {
         return new XAssetDependency[0];
      }

      if(task.startsWith("DataCycle Task: ")) {
         String cycle = task.substring(16);
         String orgId = OrganizationManager.getInstance().getCurrentOrgID();
         String desc = generateDescription(
            catalog.getString("common.xasset.task3", task),
            catalog.getString("common.xasset.cycle", cycle));
         dependencies.add(new XAssetDependency(
            new DataCycleAsset(new DataCycleManager.DataCycleId(cycle, orgId)), this,
            XAssetDependency.SCHEDULETASK_DATACYCLE, desc));
      }
      else {
         for(int i = 0; i < stask.getActionCount(); i++) {
            ScheduleAction action = stask.getAction(i);
            String repName = null;
            String prefix = null;

            if(action instanceof ViewsheetAction) {
               String vsName = ((ViewsheetAction) action).getViewsheetName();
               prefix = catalog.getString("common.xasset.task4", task);
               String desc = generateDescription(prefix,
                                                 catalog.getString("common.xasset.viewsheet", vsName));
               AssetEntry vsEntry = AssetEntry.createAssetEntry(vsName);
               dependencies.add(new XAssetDependency(
                  new ViewsheetAsset(vsEntry), this,
                  XAssetDependency.SCHEDULETASK_REPLET, desc));
            }
            else if(action instanceof BatchAction) {
               BatchAction bAction = (BatchAction) action;
               String tname = bAction.getTaskId();

               if(tname != null) {
                  ScheduleTask stask2 = manager.getScheduleTask(tname);

                  if(stask2 == null) {
                     continue;
                  }

                  String desc = generateDescription(
                     catalog.getString("common.xasset.task5", task),
                     catalog.getString("common.xasset.task0", tname));
                  dependencies.add(new XAssetDependency(
                     new ScheduleTaskAsset(tname, stask2.getOwner(), stask2.getLastModified()), this,
                     XAssetDependency.SCHEDULETASK_SCHEDULETASK, desc));
               }

               AssetEntry entry = bAction.getQueryEntry();

               if(entry != null) {
                  if(entry.isTable()) {
                     entry = new AssetEntry(entry.getScope(),
                                       AssetEntry.Type.WORKSHEET,
                                       entry.getParentPath(),
                                       entry.getUser());
                  }

                  if(entry.isWorksheet()) {
                     String desc = generateDescription(entry.getDescription(), entry.getDescription());
                     dependencies.add(new XAssetDependency(new WorksheetAsset(entry), this,
                                                           XAssetDependency.SCHEDULETASK_WORKSHEET, desc));
                  }
               }
            }
            else if(action instanceof IndividualAssetBackupAction) {
               IndividualAssetBackupAction bAction = (IndividualAssetBackupAction) action;

               for(XAsset asset : bAction.getAssets()) {
                  if(this.equals(asset)) {
                     continue;
                  }

                  String desc = generateDescription(
                     catalog.getString("common.xasset.task6", task),
                     asset.getType() + " " + asset.getPath());
                  dependencies.add(new XAssetDependency(asset, this,
                                      XAssetDependency.SCHEDULETASK_BACKUP, desc));
               }
            }
         }

         for(int i = 0; i < stask.getConditionCount(); i++) {
            ScheduleCondition cond = stask.getCondition(i);

            if(cond instanceof CompletionCondition &&
               ((CompletionCondition) cond).getTaskName() != null)
            {
               String tname = ((CompletionCondition) cond).getTaskName();

               if(tname != null) {
                  ScheduleTask stask2 = manager.getScheduleTask(tname);

                  if(stask2 == null) {
                     continue;
                  }

                  String desc = generateDescription(
                     catalog.getString("common.xasset.task3", task),
                     catalog.getString("common.xasset.task0", tname));
                  dependencies.add(new XAssetDependency(
                     new ScheduleTaskAsset(tname, stask2.getOwner(), stask2.getLastModified()), this,
                     XAssetDependency.SCHEDULETASK_SCHEDULETASK, desc));
               }
            }
         }
      }

      return dependencies.toArray(new XAssetDependency[0]);
   }

   /**
    * Get the path of this asset.
    * @return the path of this asset.
    */
   @Override
   public String getPath() {
      return task;
   }

   /**
    * Get the type of this asset.
    * @return the type of this asset.
    */
   @Override
   public String getType() {
      return SCHEDULETASK;
   }

   /**
    * Get the owner of this asset if any.
    *
    * @return the owner of this asset if any.
    */
   @Override
   public IdentityID getUser() {
      return user;
   }

   public String getTask() {
      return this.task;
   }

   public void setTask(String task) {
      this.task = task;
   }

   /**
    * Parse an identifier to a real asset.
    * @param identifier the specified identifier, usually with the format of
    * ClassName^path^user.
    */
   @Override
   public void parseIdentifier(String identifier) {
      int idx = identifier.indexOf("^");
      String className = identifier.substring(0, idx);

      if(!className.equals(getClass().getName())) {
         return;
      }

      identifier = identifier.substring(idx + 1);
      idx = identifier.indexOf("^");
      task = identifier.substring(0, idx);

      user = IdentityID.getIdentityIDFromKey(identifier.substring(idx + 1));
      user = NULL.equals(user.name) ? null : user;
   }

   /**
    * Create an asset by its path and owner if any.
    *
    * @param path         the specified asset path.
    * @param userIdentity the specified asset owner if any.
    */
   @Override
   public void parseIdentifier(String path, IdentityID userIdentity) {
      this.task = path;
      this.user = userIdentity;
   }

   /**
    * Convert this asset to an identifier.
    * @return an identifier.
    */
   @Override
   public String toIdentifier() {
      return getClass().getName() + "^" + task + "^" + (user == null ? NULL : user.convertToKey());
   }

   @Override
   public synchronized void parseContent(InputStream input, XAssetConfig config, boolean isImport)
      throws Exception
   {
      Element elem = Tool.parseXML(input).getDocumentElement();
      IndexedStorage indexedStorage = IndexedStorage.getIndexedStorage();
      Element folders = Tool.getChildNodeByTagName(elem, "folders");
      Element timeRanges = Tool.getChildNodeByTagName(elem, "timeRanges");

      if(folders != null) {
         NodeList folderNodes = Tool.getChildNodesByTagName(folders, "Folder");

         for(int i = 0; i < folderNodes.getLength(); i ++) {
            importScheduleFolder((Element) folderNodes.item(i), indexedStorage);
         }
      }

      if(timeRanges != null) {
         NodeList rangeNodes = Tool.getChildNodesByTagName(timeRanges, "timeRange");
         List<TimeRange> ranges = new ArrayList<>();
         ranges.addAll(TimeRange.getTimeRanges());

         for(int i = 0; i < rangeNodes.getLength(); i++) {
            if(rangeNodes.item(i) instanceof Element) {
               TimeRange range = new TimeRange();
               range.parseXML((Element) rangeNodes.item(i));

               if(!ranges.contains(range)) {
                  ranges.add(range);
               }
            }
         }

         TimeRange.setTimeRanges(ranges);
      }

      ScheduleTask newTask = new ScheduleTask();
      newTask.parseXML(Tool.getChildNodeByTagName(elem, "Task"));
      String parentPath = newTask.getPath();

      Principal principal = new SRPrincipal(newTask.getOwner());
      ScheduleManager manager = ScheduleManager.getScheduleManager();
      boolean overwriting = config != null && config.isOverwriting();
      ScheduleTask existing = manager.getScheduleTask(newTask.getTaskId());

      if(overwriting || existing == null) {
         if(existing != null) {
            manager.removeScheduleTask(newTask.getTaskId(), principal);
         }

         manager.setScheduleTask(newTask.getTaskId(), newTask, null, principal);

         if(folders != null) {
            moveTask(newTask, parentPath, indexedStorage, manager, principal);
         }

         task = parentPath.equals("/") ? newTask.getTaskId() : parentPath + "/" + newTask.getTaskId();
      }
   }

   private void importScheduleFolder(Element folderElem,
                                     IndexedStorage indexedStorage) throws Exception
   {
      String path = Tool.getAttribute(folderElem, "path");
      IdentityID owner = IdentityID.getIdentityIDFromKey(Tool.getAttribute(folderElem, "owner"));

      AssetEntry folderEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                                              path, null);
      String folderID = folderEntry.toIdentifier();
      AssetFolder folderAsset = (AssetFolder) indexedStorage.getXMLSerializable(folderID, null);

      if(folderAsset == null) {
         folderAsset = new AssetFolder();
         folderAsset.setOwner(owner);
      }

      String parentPath = path.indexOf('/') != -1 ? path.substring(0, path.indexOf('/')) : "/";
      AssetEntry parentEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, parentPath, null);
      AssetFolder parentFolder =
         (AssetFolder) indexedStorage.getXMLSerializable(parentEntry.toIdentifier(), null);

      parentFolder.addEntry(folderEntry);
      indexedStorage.putXMLSerializable(parentEntry.toIdentifier(), parentFolder);
      indexedStorage.putXMLSerializable(folderID, folderAsset);
   }

   private void moveTask(ScheduleTask task, String parentPath, IndexedStorage indexedStorage,
                         ScheduleManager manager, Principal principal) throws Exception
   {
      AssetEntry nEntry
         = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, parentPath, null);
      AssetEntry taskEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK,
                          "/" + task.getTaskId(), null);
      AssetEntry oEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                              AssetEntry.Type.SCHEDULE_TASK_FOLDER, "/", null);

      AssetFolder npfolder =
         (AssetFolder) indexedStorage.getXMLSerializable(nEntry.toIdentifier(), null);
      AssetFolder opfolder =
         (AssetFolder) indexedStorage.getXMLSerializable(oEntry.toIdentifier(), null);

      if(npfolder != null) {
         npfolder.addEntry(taskEntry);
         indexedStorage.putXMLSerializable(nEntry.toIdentifier(), npfolder);
      }

      if(opfolder != null) {
         opfolder.removeEntry(taskEntry);
         indexedStorage.putXMLSerializable(oEntry.toIdentifier(), opfolder);
      }

      manager.setScheduleTask(task.getTaskId(), task, nEntry, principal);
   }

   @Override
   public synchronized boolean writeContent(OutputStream output) throws Exception {
      if(task != null) {
         ScheduleManager manager = ScheduleManager.getScheduleManager();
         String taskName = task.substring(task.lastIndexOf("/") + 1);
         ScheduleTask stask = manager.getScheduleTask(taskName);

         if(stask != null) {
            JarOutputStream out = getJarOutputStream(output);
            ZipEntry zipEntry = new ZipEntry(
               getType() + "_" + replaceFilePath(toIdentifier()));
            out.putNextEntry(zipEntry);

            PrintWriter writer =
               new PrintWriter(new OutputStreamWriter(out, "UTF8"));
            writer.write("<ScheduleTask>");

            if(stask.getPath() != null && !stask.getPath().equals("/") &&
               !stask.getPath().isEmpty())
            {
               writer.write("<folders>");
               writeScheduleFolders(writer, stask.getPath(), IndexedStorage.getIndexedStorage());
               writer.write("</folders>");
            }

            Set<TimeRange> ranges = new HashSet<>();

            for(int i = 0; i < stask.getConditionCount(); i++) {
               if(stask.getCondition(i) instanceof TimeCondition) {
                  TimeCondition cond = (TimeCondition) stask.getCondition(i);

                  if(cond.getTimeRange() != null) {
                     ranges.add(cond.getTimeRange());
                  }
               }
            }

            if(ranges.size() > 0) {
               writer.write("<timeRanges>");
               ranges.forEach(range -> range.writeXML(writer));
               writer.write("</timeRanges>");
            }

            stask.writeXML(writer);
            writer.write("</ScheduleTask>");
            writer.flush();
            return true;
         }
      }

      return false;
   }

   private void writeScheduleFolders(PrintWriter writer, String path,
                                     IndexedStorage indexedStorage) throws Exception
   {
      int idx = path.indexOf('/');

      if(idx != -1) {
         String parent = path.substring(0, idx);
         writeScheduleFolders(writer, parent, indexedStorage);
      }

      AssetEntry folderEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                                              path, null);
      AssetFolder folder = (AssetFolder) indexedStorage.getXMLSerializable(folderEntry.toIdentifier(), null);
      IdentityID owner = folder.getOwner();

      writer.printf("<Folder path=\"%s\"", Tool.escape(path));

      if(owner != null) {
         writer.printf(" owner=\"%s\"", Tool.escape(owner.convertToKey()));
      }

      writer.print(">");
      writer.println("</Folder>");
   }

   @Override
   public boolean exists() {
      return ScheduleManager.getScheduleManager().getScheduleTask(
         task.substring(task.lastIndexOf("/") + 1)) != null;
   }

   @Override
   public Resource getSecurityResource() {
      return new Resource(ResourceType.SCHEDULE_TASK, task);
   }

   @Override
   public String generateDescription(String from, String to) {
      from = SUtil.getTaskNameWithoutOrg(from);
      to = SUtil.getTaskNameWithoutOrg(from);
      return catalog.getString("common.xasset.depends", from, to);
   }

   private String task;
   private IdentityID user;
}