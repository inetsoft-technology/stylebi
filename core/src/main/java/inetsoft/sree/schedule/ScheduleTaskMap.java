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
package inetsoft.sree.schedule;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.w3c.dom.*;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

/**
 * Map of schedule tasks that is backed by indexed storage.
 *
 * @since 12.2
 */
class ScheduleTaskMap extends AbstractMap<String, ScheduleTask> {
   /**
    * Creates a new instance of <tt>ScheduleTaskMap</tt>.
    */
   ScheduleTaskMap(String orgID) {
      this.orgID = orgID;
      indexedStorage = IndexedStorage.getIndexedStorage();
   }

   @Override
   public boolean containsKey(Object key) {
      return containsKey(key, null);
   }

   public boolean containsKey(Object key, String orgID) {
      String identifier = (String) key;
      return indexedStorage.contains(identifier, orgID);
   }

   @Override
   public ScheduleTask get(Object key) {
      ScheduleTask task = null;
      String identifier = (String) key;
      String curOrg = this.orgID;

      int startIndex = org.apache.commons.lang.StringUtils.ordinalIndexOf((String) key, "^", 3);
      int endIndex = org.apache.commons.lang.StringUtils.ordinalIndexOf((String) key, "^", 4);
      String taskName = ((String) key).substring(startIndex + 2, endIndex);

      if(InternalScheduledTaskService.isInternalTask(taskName)) {
         curOrg = Organization.getDefaultOrganizationID();
      }

      long ts = indexedStorage.lastModified(identifier, curOrg);
      TaskWrapper wrapper = cache.get(identifier);

      if(wrapper != null && ts == wrapper.ts) {
         task = wrapper.task;
      }

      try {
         if(task == null) {
            task = (ScheduleTask) indexedStorage
               .getXMLSerializable(identifier, new ScheduleTransformListener(), curOrg);
            cache.put(identifier, new TaskWrapper(task, ts));
         }
      }
      catch(Exception e) {
         throw new RuntimeException(
            "Failed to load schedule task: " + identifier, e);
      }

      return task;
   }

   public void clearCache() {
      cache.clear();
      rootFolders.clear();
      rootTS.clear();
   }

   @Override
   public ScheduleTask put(String key, ScheduleTask value) {
      try {
         AssetEntry pentry = getRootEntry();

         if(value.getPath() != null && !value.getPath().isEmpty()) {
            pentry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                    AssetEntry.Type.SCHEDULE_TASK_FOLDER, value.getPath(), null);
         }

         return put(key, value, pentry, this.orgID);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to store schedule task: " + key, e);
      }
   }
   public ScheduleTask put(String key, ScheduleTask value, String orgID) {
      try {
         AssetEntry pentry = getRootEntry();

         if(value.getPath() != null && !value.getPath().isEmpty()) {
            pentry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                    AssetEntry.Type.SCHEDULE_TASK_FOLDER, value.getPath(), null, orgID);
         }

         return put(key, value, pentry, orgID);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to store schedule task: " + key, e);
      }
   }

   public ScheduleTask put(String key, ScheduleTask value, AssetEntry parent, String orgID) {
      ScheduleTask oldValue = null;

      if(parent == null) {
         parent = getRootEntry();
      }

      try {
         AssetFolder parentFolder = (AssetFolder)
            indexedStorage.getXMLSerializable(parent.toIdentifier(), null, parent.getOrgID());

         if(parentFolder == null) {
            // if repository is corrupt (orphaned schedule asset), recreate the parent folder
            parentFolder = new AssetFolder();
         }

         AssetEntry entry = AssetEntry.createAssetEntry(key, orgID);

         if(parentFolder.containsEntry(entry)) {
            entry = parentFolder.getEntry(entry);
            parentFolder.removeEntry(entry);
            oldValue = get(key);
         }

         AssetUtil.updateMetaData(
            entry, ThreadContext.getContextPrincipal(), System.currentTimeMillis());
         parentFolder.addEntry(entry);
         value.setPath(parent.getPath());
         indexedStorage.putXMLSerializable(parent.toIdentifier(), parentFolder);
         indexedStorage.putXMLSerializable(key, value);
         AssetFolder rootFolder =
            (AssetFolder) indexedStorage.getXMLSerializable(getRootIdentifier(), null, orgID);

         if(rootFolder == null) {
            rootFolder = new AssetFolder();
            indexedStorage.putXMLSerializable(getRootIdentifier(), rootFolder);
         }

         rootFolders.put(orgID, rootFolder);
         rootTS.put(orgID, indexedStorage.lastModified(getRootIdentifier(), orgID));
      }
      catch(Exception e) {
         throw new RuntimeException(
            "Failed to store schedule task: " + key, e);
      }
      finally {
         indexedStorage.close();
      }

      return oldValue;
   }

   @Override
   public ScheduleTask remove(Object key) {
      ScheduleTask task = get(key);

      if(task != null) {
         String identifier = (String) key;

         try {
            AssetFolder root = getRoot();
            AssetEntry entry = AssetEntry.createAssetEntry(identifier);
            root.removeEntry(entry);
            indexedStorage.putXMLSerializable(getRootIdentifier(orgID), root);
            indexedStorage.remove(identifier);
            cache.remove(identifier);

            // should remove the task from folder when delete a task.
            if(task != null && !StringUtils.isEmpty(task.getPath())) {
               AssetEntry folderEntry = new AssetEntry( AssetRepository.GLOBAL_SCOPE,
                                                        AssetEntry.Type.SCHEDULE_TASK_FOLDER, task.getPath(), null, orgID);
               XMLSerializable folder = indexedStorage
                  .getXMLSerializable(folderEntry.toIdentifier(), null, orgID);

               if(folder instanceof AssetFolder) {
                  ((AssetFolder) folder).removeEntry(entry);
                  indexedStorage.putXMLSerializable(folderEntry.toIdentifier(), folder);
               }
            }
         }
         catch(Exception e) {
            throw new RuntimeException(
               "Failed to delete schedule task: " + identifier, e);
         }
         finally {
            indexedStorage.close();
         }
      }

      return task;
   }

   @Override
   public void clear() {
      try {
         AssetFolder root = getRoot();

         for(AssetEntry entry : root.getEntries()) {
            indexedStorage.remove(entry.toIdentifier());
            root.removeEntry(entry);
         }

         indexedStorage.putXMLSerializable(getRootIdentifier(this.orgID), root);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to clear schedule tasks", e);
      }
      finally {
         indexedStorage.close();
      }
   }

   @SuppressWarnings("NullableProblems")
   @Override
   public Set<String> keySet() {
      Set<String> keys = new HashSet<>();

      try {
         AssetFolder root = getRoot();
         AssetEntry[] entries = getAllChildren(root).toArray(new AssetEntry[0]);

         for(AssetEntry entry : entries) {
            if(entry.getType() == AssetEntry.Type.SCHEDULE_TASK) {
               keys.add(entry.getName());
            }
         }
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to list schedule tasks", e);
      }

      // Add internal tasks
      keys.add(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.SCHEDULE_TASK, "/" + InternalScheduledTaskService.ASSET_FILE_BACKUP,
         null, Organization.getDefaultOrganizationID())
                  .toIdentifier());
      keys.add(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.SCHEDULE_TASK, "/" + InternalScheduledTaskService.BALANCE_TASKS,
         null, Organization.getDefaultOrganizationID())
                  .toIdentifier());

      return keys;
   }

   @SuppressWarnings("NullableProblems")
   @Override
   public Set<Entry<String, ScheduleTask>> entrySet() {
      return new ScheduleTaskEntrySet(this.orgID);
   }

   public Set<Entry<String, ScheduleTask>> entrySet(String orgID) {
      return new ScheduleTaskEntrySet(orgID);
   }

   @Override
   public int size() {
      try {
         return getRoot().size();
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to list schedule tasks", e);
      }
   }

   /**
    * Gets the root folder where all the asset entries are stored
    */
   private AssetFolder getRoot() throws Exception {
      String rootId = getRootIdentifier(orgID);
      long ts = indexedStorage.lastModified(rootId);
      Long ots = rootTS.containsKey(orgID) ? rootTS.get(orgID) : 0;
      AssetFolder rootFolder = rootFolders.get(orgID);

      if(ts > ots || rootFolder == null) {
         rootFolder = (AssetFolder) indexedStorage.getXMLSerializable(rootId, null, orgID);

         if(rootFolder == null) {
            // bug #58866, handle corruption where asset entry is in the index, but the asset data
            // file is missing
            rootFolder = new AssetFolder();
            indexedStorage.putXMLSerializable(rootId, rootFolder);
            ts = indexedStorage.lastModified(rootId);
         }

         rootTS.put(orgID, ots);
      }

      return rootFolder;
   }

   /**
    * Gets the root asset entry.
    *
    * @return the root entry.
    */
   private AssetEntry getRootEntry() {
      return new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
         "/", null, orgID);
   }

   /**
    * Gets the identifier of the root asset entry.
    *
    * @return the root identifier.
    */
   private String getRootIdentifier() {
      return getRootEntry().toIdentifier();
   }

   private String getRootIdentifier(String orgID) {
      return getRootEntry().toIdentifier();
   }

   /**
    * Initializes and stores the AssetFolder that serves as the root of the
    * schedule tasks.
    */
   private void initRoot() throws Exception {
      try {
         if(!indexedStorage.contains(getRootIdentifier(this.orgID))) {
            indexedStorage.putXMLSerializable(
               getRootIdentifier(this.orgID), new AssetFolder());
         }
      }
      finally {
         indexedStorage.close();
      }
   }

   private List<AssetEntry> getAllChildren(AssetFolder root) {
      return this.getAllChildren(root, null);
   }

   private List<AssetEntry> getAllChildren(AssetFolder root, String orgID) {
      List<AssetEntry> res = new ArrayList<>();

      if(root == null) {
         return res;
      }

      AssetEntry[] children = root.getEntries();

      Arrays.stream(children).forEach((child) -> {
         // if built-in task is saved into schedule.xml by mistake, ignore it on
         // load otherwise user can't delete them. (45028)
         if(child.getName().contains(DataCycleManager.TASK_PREFIX)) {
            ScheduleTask scheduleTask = get(child.toIdentifier());

            if(scheduleTask != null &&
               scheduleTask.getName().startsWith(DataCycleManager.TASK_PREFIX))
            {
               indexedStorage.remove(child.toIdentifier());
               return;
            }
         }

         if(child.isScheduleTaskFolder()) {
            try {
               AssetFolder folder = (AssetFolder)
                  indexedStorage.getXMLSerializable(child.toIdentifier(), null, orgID);

               res.addAll(getAllChildren(folder, orgID));
            }
            catch(Exception e) {
               throw new RuntimeException("Failed to get schedule task folder", e);
            }
         }
         else {
            res.add(child);
         }
      });

      indexedStorage.close();

      return res;
   }

   private final class ScheduleTaskEntry implements Map.Entry<String, ScheduleTask> {
      ScheduleTaskEntry(String key) {
         this.key = key;
         this.orgID = OrganizationManager.getInstance().getCurrentOrgID();
      }

      ScheduleTaskEntry(String key, String orgID) {
         this.key = key;
         this.orgID = orgID;
      }

      @Override
      public String getKey() {
         return key;
      }

      @Override
      public ScheduleTask getValue() {
         return ScheduleTaskMap.this.get(key);
      }

      @Override
      public ScheduleTask setValue(ScheduleTask value) {
         return ScheduleTaskMap.this.put(key, value, orgID);
      }

      private final String key;
      private final String orgID;
   }

   private final class ScheduleTaskEntryIterator
      implements Iterator<Map.Entry<String, ScheduleTask>>
   {
      ScheduleTaskEntryIterator(String orgID) {
         try {

            root = getRoot();
            entries = getAllChildren(root, orgID).toArray(new AssetEntry[0]);

            if(!orgID.equals(Organization.getDefaultOrganizationID())) {
               internalTasks = new AssetEntry[3];

               internalTasks[0] = new AssetEntry(
                  AssetRepository.GLOBAL_SCOPE,
                  AssetEntry.Type.SCHEDULE_TASK, "/" + InternalScheduledTaskService.ASSET_FILE_BACKUP,
                  null, Organization.getDefaultOrganizationID());

               internalTasks[1] = new AssetEntry(
                  AssetRepository.GLOBAL_SCOPE,
                  AssetEntry.Type.SCHEDULE_TASK, "/" + InternalScheduledTaskService.BALANCE_TASKS,
                  null, Organization.getDefaultOrganizationID());
               internalTasks[2] = new AssetEntry(
                  AssetRepository.GLOBAL_SCOPE,
                  AssetEntry.Type.SCHEDULE_TASK,
                  "/" + InternalScheduledTaskService.UPDATE_ASSETS_DEPENDENCIES,
                  null, Organization.getDefaultOrganizationID());
            }
            else {
               internalTasks = new AssetEntry[0];
            }
         }
         catch(Exception e) {
            throw new RuntimeException("Failed to get schedule task folder", e);
         }
      }

      @Override
      public boolean hasNext() {
         boolean result = false;

         for(int i = index + 1; i < entries.length; i++) {
            if(indexedStorage.contains(entries[i].toIdentifier(), orgID)) {
               result = true;
               break;
            }
         }

         if(!result) {
            for(int i = globalIndex + 1; i < internalTasks.length; i++) {
               if(indexedStorage.contains(internalTasks[i].toIdentifier(),
                                          Organization.getDefaultOrganizationID()))
               {
                  result = true;
                  break;
               }
            }
         }

         return result;
      }

      @Override
      public Entry<String, ScheduleTask> next() {
         ScheduleTaskEntry entry = null;

         do {
            ++index;

            if(index < entries.length &&
               indexedStorage.contains(entries[index].toIdentifier(), orgID))
            {
               if(entries[index].isScheduleTask()) {
                  entry = new ScheduleTaskEntry(entries[index].toIdentifier(), orgID);
                  removed = false;
               }
               else {}

            }
         }
         while(index < entries.length && entry == null);

         if(index >= entries.length) {
            do {
               ++globalIndex;

               if(globalIndex < internalTasks.length &&
                  indexedStorage.contains(internalTasks[globalIndex].toIdentifier(),
                                          Organization.getDefaultOrganizationID()))
               {
                  if(internalTasks[globalIndex].isScheduleTask()) {
                     entry = new ScheduleTaskEntry(internalTasks[globalIndex].toIdentifier(), orgID);
                     removed = false;
                  }
                  else {}

               }
            }
            while(globalIndex < internalTasks.length && entry == null);

            if(globalIndex >= internalTasks.length) {
               throw new NoSuchElementException();
            }
         }

         return entry;
      }

      @Override
      public void remove() {
         if(index < 0) {
            throw new IllegalStateException("next has not been called");
         }

         if(removed) {
            throw new IllegalStateException(
               "remove has already been called on the current element");
         }

         if(globalIndex >= 0) {
            throw new IllegalStateException(
               "cannot remove internal task");
         }

         removed = true;

         AssetEntry entry = entries[index];
         ScheduleTaskMap.this.remove(entry.toIdentifier());
         root.removeEntry(entry);
      }

      private final AssetFolder root;
      private final AssetEntry[] entries;
      private final AssetEntry[] internalTasks;
      private int index = -1;
      private int globalIndex = -1;
      private boolean removed = false;
   }

   private final class ScheduleTaskEntrySet
      extends AbstractSet<Map.Entry<String, ScheduleTask>>
   {
      private ScheduleTaskEntrySet() {
         super();
         this.orgID = OrganizationManager.getInstance().getCurrentOrgID();
      }

      private ScheduleTaskEntrySet(String orgID) {
         super();
         this.orgID = orgID;
      }

      @SuppressWarnings("NullableProblems")
      @Override
      public Iterator<Map.Entry<String, ScheduleTask>> iterator() {
         return new ScheduleTaskEntryIterator(orgID);
      }

      @Override
      public int size() {
         return ScheduleTaskMap.this.size();
      }

      String orgID = null;
   }

   private static class TaskWrapper {
      public TaskWrapper(ScheduleTask task, long ts) {
         this.task = task;
         this.ts = ts;
      }


      public final ScheduleTask task;
      public final long ts;
   }

   private final IndexedStorage indexedStorage;
   private final String orgID;
   private final Map<String, TaskWrapper> cache = new ConcurrentHashMap<>();
   private final Map<String, Long> rootTS = new ConcurrentHashMap<>();
   private Map<String, AssetFolder> rootFolders = new ConcurrentHashMap<>();
   private static final Logger LOG =
      LoggerFactory.getLogger(ScheduleTaskMap.class);
}
