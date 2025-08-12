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
package inetsoft.sree.internal;

import inetsoft.mv.MVDef;
import inetsoft.mv.MVManager;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import org.apache.commons.lang3.StringUtils;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;

/**
 * DataCycleManager handles the creation of pregenerated tasks at runtime.
 *
 * @since 7.0
 * @author InetSoft Technology Corp
 */
@SingletonManager.Singleton(DataCycleManager.Reference.class)
public class DataCycleManager
   implements ScheduleExt, PropertyChangeListener, StorageRefreshListener, AutoCloseable
{
   /**
    * Data Cycle task.
    */
   public static final String TASK_PREFIX = "DataCycle Task: ";

   /**
    * Creates a new instance of DataCycleManager.
    */
   public DataCycleManager() {
      ScheduleManager.getScheduleManager().addScheduleExt(this);
      loadOldConfig();
      init();
      IndexedStorage.getIndexedStorage().addStorageRefreshListener(this);
   }

   /**
    * Get the single instance of DataCycleManager that is mapped to the
    * current thread group.
    * @return the DataCycleManager instance.
    */
   public static DataCycleManager getDataCycleManager() {
      return SingletonManager.getInstance(DataCycleManager.class);
   }

   @Override
   public void close() throws Exception {
      IndexedStorage.getIndexedStorage().removeStorageRefreshListener(this);
   }

   /**
    * Initialize the Manager by reading in all the existing data cycles.
    */
   private void init() {
      // read in the properties specified for cycles in the EM
      try {
         generateTasks(false);
      }
      catch(Exception ex) {
         LOG.error("Failed to initialize data cycle manager", ex);
      }
   }

   /**
    * Method listens to Property change event.
    */
   @Override
   public void propertyChange(PropertyChangeEvent evt) {
      String name = evt.getPropertyName();
      String orgId = Util.getOrgIdFromEventSource(evt.getSource());

      if(MVManager.MV_CHANGE_EVENT.equals(name) || RepletRegistry.CHANGE_EVENT.equals(name)) {
         generateTasks(null, orgId != null ? new Organization(new IdentityID(orgId, orgId)) : null,
                       true, false);
      }
   }

   /**
    * Method will be invoked when a replet is removed.
    * @param replet the specified replet.
    * @param owner the specified user.
    */
   @Override
   public boolean repletRemoved(String replet, String owner) {
      return false;
   }

   /**
    * Method will be invoked when a replet is renamed.
    * @param oreplet the specified old replet.
    * @param nreplet the specified new replet.
    * @param owner the specified user.
    */
   @Override
   public boolean repletRenamed(String oreplet, String nreplet, String owner) {
      return false;
   }

   /**
    * Method will be invoked when a viewsheet is renamed.
    * @param oviewsheet the specified old viewsheet.
    * @param nviewsheet the specified new viewsheet.
    * @param owner the specified user.
    */
   @Override
   public boolean viewsheetRenamed(String oviewsheet, String nviewsheet,
                                   String owner)
   {
      return false;
   }

   /**
    * Method will be invoked when an archive is renamed.
    * @param opath the specified old archive path.
    * @param npath the specified new archive path.
    * @param owner the specified user.
    * @return <tt>true</tt> if the extension changed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean archiveRenamed(String opath, String npath, String owner) {
      return false;
   }

   /**
    * Method will be invoked when a folder is renamed.
    * @param opath the specified old folder path.
    * @param npath the specified new folder path.
    * @param owner the specified user.
    * @return <tt>true</tt> if the extension changed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean folderRenamed(String opath, String npath, String owner) {
      return false;
   }

   /**
    * Get the pregenerated tasks for the ScheduleManager.
    * @return Vector of ScheduleTask.
    */
   @Override
   public List<ScheduleTask> getTasks() {
      return getTasks(OrganizationManager.getInstance().getCurrentOrgID());
   }

   /**
    * Get the pregenerated tasks for the ScheduleManager.
    * @return Vector of ScheduleTask.
    */
   @Override
   public List<ScheduleTask> getTasks(String orgID) {
      List<ScheduleTask> tasks = new ArrayList<>();
      Boolean inited = orgPregeneratedTaskLoadedStatus.get(orgID);

      if(inited == null || !inited) {
         Organization org = new Organization(new IdentityID(orgID, orgID));
         generateTasks(null, org, false,  false);
      }

      if(pregeneratedTasksMap.get(orgID) != null) {
         tasks.addAll(pregeneratedTasksMap.get(orgID));
      }

      return tasks;
   }

   public void clearOrgTasks(String orgId) {
      if(pregeneratedTasksMap.containsKey(orgId)) {
         pregeneratedTasksMap.remove(orgId);
         orgPregeneratedTaskLoadedStatus.remove(orgId);
      }
   }

   @Override
   public Iterator<ScheduleTask> iterator() {
      return getTasks().iterator();
   }

   private void generateTasks(boolean reloadExtensions) {
      generateTasks(null, null, reloadExtensions, false);
   }

   /**
    * Internal method used to set pregeneratedTasks.
    */
   private void generateTasks(Organization oorg, Organization norg, boolean reloadExtensions,
                              boolean replace)
   {
      String currOrgID = norg != null ?
         norg.getOrganizationID() : OrganizationManager.getInstance().getCurrentOrgID();

      try {
         // don't load in secondary schedulers
         if(Scheduler.getSchedulerCount() != 1 &&
            "true".equals(System.getProperty("ScheduleServer")))
         {
            return;
         }

      IndexedStorage storage = IndexedStorage.getIndexedStorage();
      List<ScheduleTask> tasks = new ArrayList<>();
      allMVs = oorg == null && norg == null && allMVs;

      for(String orgId : SecurityEngine.getSecurity().getOrganizations()) {
         Set<String> assetIds = storage.getKeys(key -> {
            AssetEntry entry = AssetEntry.createAssetEntry(key);
            return entry != null && entry.getType() == AssetEntry.Type.DATA_CYCLE;
         }, orgId);

         for(String assetId : assetIds) {
            DataCycleAsset asset;

            try {
               asset = (DataCycleAsset) storage.getXMLSerializable(assetId, null, orgId);
            }
            catch(Exception e) {
               LOG.error("Failed to load data cycle {}", assetId, e);
               continue;
            }

            DataCycleId cycle = new DataCycleId(asset.getName(), asset.getOrgId());
            IdentityID identityID = new IdentityID(XPrincipal.SYSTEM, orgId);
            ScheduleTask task = new ScheduleTask(
               TASK_PREFIX + cycle.name, ScheduleTask.Type.CYCLE_TASK);
            task.setEditable(false);
            task.setRemovable(false);
            task.setEnabled(asset.isEnabled());
            task.setOwner(identityID);
            task.setCycleInfo(getCycleInfo(cycle.name, cycle.orgId));

            for(int i = 0; i < getConditionCount(cycle.name, cycle.orgId); i++) {
               task.addCondition(getCondition(cycle.name, cycle.orgId, i));
            }

            generateMVActions(task, cycle.name, tasks, orgId);

            if(task.getActionCount() == 0) {
               continue;
            }

            tasks.add(task);
         }
      }

         synchronized(this) {
            if(replace && oorg != null) {
               pregeneratedTasksMap.remove(oorg.getOrganizationID());
               orgPregeneratedTaskLoadedStatus.remove(oorg.getOrganizationID());
            }
            else {
               String org = norg != null ?
                  norg.getOrganizationID() : OrganizationManager.getInstance().getCurrentOrgID();
               pregeneratedTasksMap.put(org, new Vector<>());
            }

            for(ScheduleTask task : tasks) {
               String orgID = task.getOwner() != null ?
                  task.getOwner().getOrgID() : OrganizationManager.getInstance().getCurrentOrgID();
               pregeneratedTasksMap.computeIfAbsent(orgID, k -> new Vector<>());
               List<ScheduleTask> taskList = pregeneratedTasksMap.get(orgID);

               if(!taskList.contains(task)) {
                  taskList.add(task);
                  taskAdded(task);
               }
            }
         }
      }
      finally {
         orgPregeneratedTaskLoadedStatus.put(currOrgID, true);
      }

      if(reloadExtensions) {
         ScheduleManager.getScheduleManager().reloadExtensions(currOrgID);
      }
   }

   /**
    * Send a message when cycle task is created so it will show up in EM.
    */
   private void taskAdded(ScheduleTask task) {
      ScheduleTaskMessage message = new ScheduleTaskMessage();
      message.setTaskName(task.getTaskId());
      message.setTask(task);
      message.setAction(ScheduleTaskMessage.Action.ADDED);

      try {
         Cluster.getInstance().sendMessage(message);
      }
      catch(Exception ex) {
         LOG.debug("Failed to send task message", ex);
      }
   }

   /**
    * Generate emv actions.
    */
   private void generateMVActions(ScheduleTask task, String cycle, List<ScheduleTask> tasks,
                                  String orgId)
   {
      MVManager manager = MVManager.getManager();
      MVDef[] mvs = null;

      if(orgId == null) {
         mvs = manager.list(false);
      }
      else {
         mvs = manager.list(new String[]{ orgId }).toArray(MVDef[]::new);
      }

      ScheduleTask task2 = new ScheduleTask(
         TASK_PREFIX + cycle + " Stage 2", ScheduleTask.Type.CYCLE_TASK);
      task2.setEditable(false);
      task2.setRemovable(false);
      task2.setEnabled(task.isEnabled());
      task2.setOwner(task.getOwner());
      CycleInfo cycleInfo = task.getCycleInfo();
      task2.setCycleInfo(cycleInfo);
      task2.addCondition(new CompletionCondition(task.getTaskId()));

      for(MVDef def : mvs) {
         if(!cycle.equals(def.getCycle())) {
            continue;
         }

         MVAction action = new MVAction(def);

         // Association MV needs to be created after regular MV so the
         // data in base MV is up-to-date
         if(def.isAssociationMV()) {
            task2.addAction(action);
         }
         else {
            task.addAction(action);
         }
      }

      if(task2.getActionCount() > 0) {
         tasks.add(task2);
      }
   }

   private String[] getNewOrgIds(String oldId, String newId) {
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      List<String> orgIds = new ArrayList<>();

      for(String orgId : provider.getOrganizationIDs()) {
         orgIds.add(Tool.equals(oldId, orgId) ? newId : orgId);
      }

      return orgIds.toArray(new String[0]);
   }

   /**
    * Delete a given task.
    */
   @Override
   public boolean deleteTask(String name) {
      return false;
   }

   /**
    * Enable/Disable a given task.
    */
   @Override
   public boolean setEnable(String name, String orgId, boolean enable) {
      IndexedStorage storage = IndexedStorage.getIndexedStorage();
      String entry = getCycleEntry(name, orgId).toIdentifier();

      if(storage.contains(entry, orgId)) {
         try {
            DataCycleAsset asset = (DataCycleAsset) storage.getXMLSerializable(entry, null, orgId);
            asset.setEnabled(enable);
            storage.putXMLSerializable(entry, asset);
         }
         catch(Exception e) {
            LOG.error("Failed to set enabled state", e);
         }
      }

      return false;
   }

   /**
    * Check if a given task is enabled.
    */
   @Override
   public boolean isEnable(String name, String orgId) {
      IndexedStorage storage = IndexedStorage.getIndexedStorage();
      String entry = getCycleEntry(name, orgId).toIdentifier();

      if(storage.contains(entry, orgId)) {
         try {
            DataCycleAsset asset = (DataCycleAsset) storage.getXMLSerializable(entry, null, orgId);
            return asset.isEnabled();
         }
         catch(Exception e) {
            LOG.error("Failed to check enabled state", e);
         }
      }

      return false;
   }

   /**
    * Check if contains a task.
    */
   @Override
   public boolean containsTask(String name, String orgId) {
      return findTask(name, orgId) != null;
   }

   private void loadOldConfig() {
      Cluster cluster = Cluster.getInstance();
      Lock lock = cluster.getLock(getClass().getName() + ".loadOldConfig");
      lock.lock();

      try {
         if(getDataCycleCount() > 0) {
            return;
         }

         String afile = SreeEnv.getProperty("cycle.file");
         DataSpace space = DataSpace.getDataSpace();

         if(!space.exists(null, afile)) {
            return;
         }

         Document doc;

         try(InputStream fis = space.getInputStream(null, afile)) {
            doc = Tool.parseXML(fis);
         }

         IndexedStorage storage = IndexedStorage.getIndexedStorage();
         space.rename(afile, "cycle.xml.old");

         Element dcycleNode = (Element) doc.getElementsByTagName("dcycle").item(0);
         setDefaultCycle(Tool.getValue(dcycleNode));
         NodeList cycles = doc.getElementsByTagName("DataCycle");

         for(int i = 0; i < cycles.getLength(); i++) {
            Element elem = (Element) cycles.item(i);
            String name = elem.getAttribute("name");
            String orgId = elem.getAttribute("orgId");
            boolean enabled = !"false".equals(elem.getAttribute("enabled"));

            if(StringUtils.isEmpty(name)) {
               throw new IOException("DataCycle Name missing in XML: " + afile);
            }

            if(StringUtils.isEmpty(orgId)) {
               orgId = OrganizationManager.getInstance().getCurrentOrgID();
            }

            NodeList cnodes = Tool.getChildNodesByTagName(elem, "Condition");

            if(cnodes.getLength() == 0) {
               LOG.info("No condition in data cycle, ignored: {}", name);
               continue;
            }

            List<ScheduleCondition> conds = new ArrayList<>();

            for(int j = 0; j < cnodes.getLength(); j++) {
               Element cond = (Element) cnodes.item(j);
               String type = cond.getAttribute("type");

               if(StringUtils.isEmpty(type)) {
                  throw new IOException("Condition type missing in XML: " + afile);
               }

               TimeCondition condition;

               if(type.equals("TimeCondition")) {
                  condition = new TimeCondition();
                  condition.parseXML(cond);
               }
               else {
                  throw new IOException("Unknown condition type: " + type);
               }

               conds.add(condition);
            }

            Element cinfo = Tool.getChildNodeByTagName(elem, "CycleInfo");
            CycleInfo cycleInfo = new CycleInfo(name, orgId);

            if(cinfo != null) {
               cycleInfo.parseXML(cinfo);
               cycleInfo.setName(name);
            }

            DataCycleAsset asset = new DataCycleAsset();
            asset.setName(name);
            asset.setOrgId(orgId);
            asset.setEnabled(enabled);
            asset.setConditions(conds);
            asset.setInfo(cycleInfo);

            IndexedStorage.getIndexedStorage()
               .putXMLSerializable(getCycleEntry(name, orgId).toIdentifier(), asset);
         }
      }
      catch(Exception e) {
         LOG.error("Failed to load old data cycle config", e);
      }
      finally {
         lock.unlock();
      }
   }

   public void save() throws Exception {
      generateTasks(null, null, true, false, true);
   }

   /**
    * Find the task by name.
    */
   private ScheduleTask findTask(String taskId, String orgId) {
      Vector<ScheduleTask> scheduleTasks = pregeneratedTasksMap.get(orgId);

      if(scheduleTasks == null || scheduleTasks.isEmpty()) {
         return null;
      }

      for(ScheduleTask task : scheduleTasks) {
         if(task.getTaskId().equals(taskId)) {
            return task;
         }
      }

      return null;
   }

   /**
    * Add condition to specified data cycle.
    */
   public void addCondition(String name, String orgId, ScheduleCondition sc) {
      IndexedStorage storage = IndexedStorage.getIndexedStorage();
      String entry = getCycleEntry(name, orgId).toIdentifier();
      DataCycleAsset asset;

      try {
         if(storage.contains(entry, orgId)) {
            asset = (DataCycleAsset) storage.getXMLSerializable(entry, null, orgId);
         }
         else {
            asset = new DataCycleAsset();
            asset.setName(name);
            asset.setOrgId(orgId);
            asset.setEnabled(true);
         }

         if(asset.getConditions() == null) {
            asset.setConditions(new ArrayList<>());
         }

         asset.getConditions().add(sc);
         storage.putXMLSerializable(entry, asset);
      }
      catch(Exception e) {
         LOG.error("Failed to add condition", e);
      }
   }

   public void setConditions(String name, String orgId, List<ScheduleCondition> conditions) {
      Vector<ScheduleCondition> conditionVector = new Vector<>(conditions);
      setConditions(name, orgId, conditionVector);
   }
   /**
    * Set conditions to specified data cycle.
    */
   public void setConditions(String name, String orgId, Vector<ScheduleCondition> conds) {
      IndexedStorage storage = IndexedStorage.getIndexedStorage();
      String entry = getCycleEntry(name, orgId).toIdentifier();
      DataCycleAsset asset;

      try {
         if(storage.contains(entry, orgId)) {
            asset = (DataCycleAsset) storage.getXMLSerializable(entry, null, orgId);
         }
         else {
            asset = new DataCycleAsset();
            asset.setName(name);
            asset.setOrgId(orgId);
            asset.setEnabled(true);
         }

         asset.setConditions(new ArrayList<>(conds));
         storage.putXMLSerializable(entry, asset);
      }
      catch(Exception e) {
         LOG.error("Failed to set conditions", e);
      }
   }

   /**
    * Set a condition to specified data cycle.
    */
   public void setCondition(String name, String orgId, ScheduleCondition cond, int index) {
      getConditions(name, orgId).setElementAt(cond, index);
   }

   /**
    * Remove the data cycle with specified name from the data cycle map.
    */
   public void removeDataCycle(String name, String orgId) {
      IndexedStorage storage = IndexedStorage.getIndexedStorage();
      String entry = getCycleEntry(name, orgId).toIdentifier();

      if(storage.contains(entry, orgId)) {
         try {
            storage.remove(entry);
         }
         catch(Exception e) {
            LOG.error("Failed to remove data cycle", e);
         }
      }
   }

   /**
    * Check if the cycle is used.
    */
   public boolean hasPregeneratedDependency(String cycle) {
      if(cycle == null) {
         return false;
      }

      MVManager manager = MVManager.getManager();
      String orgid = OrganizationManager.getInstance().getCurrentOrgID();
      MVDef[] mvs = manager.list(false);

      for(MVDef mv : mvs) {
         if(cycle.equals(mv.getCycle()) && orgid.equals(mv.getEntry().getOrgID())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get all the data cycles.
    */
   public Enumeration<DataCycleId> getDataCycles() {
      return Collections.enumeration(getDataCycleIds());
   }

   /**
    * Get the data cycles in an organization.
    */
   public Enumeration<DataCycleId> getDataCycles(Organization org) {
      String orgId = org != null ? org.getId() : OrganizationManager.getInstance().getCurrentOrgID();
      return Collections.enumeration(getDataCycleIds(orgId));
   }

   /**
    * Get the data cycles in an organization.
    */
   public Enumeration<String> getDataCycles(String orgID) {
      return Collections.enumeration(getDataCycleIds(orgID).stream()
                                        .map(DataCycleId::name)
                                        .toList());
   }

   private Set<DataCycleId> getDataCycleIds() {
      Set<DataCycleId> ids = new HashSet<>();

      for(String orgId : SecurityEngine.getSecurity().getOrganizations()) {
         ids.addAll(getDataCycleIds(orgId));
      }

      return ids;
   }

   private Set<DataCycleId> getDataCycleIds(String orgId) {
      IndexedStorage storage = IndexedStorage.getIndexedStorage();
      Set<DataCycleId> ids = new HashSet<>();
      Set<String> assetIds = storage.getKeys(key -> {
         AssetEntry entry = AssetEntry.createAssetEntry(key);
         return entry != null && entry.getType() == AssetEntry.Type.DATA_CYCLE;
      });

      for(String assetId : assetIds) {
         AssetEntry entry = AssetEntry.createAssetEntry(assetId);
         String name = entry.getName().substring(PREFIX.length() - 1);
         ids.add(new DataCycleId(name, orgId));
      }

      return ids;
   }

   /**
    * Get the time conditions of the specified data cycle.
    */
   public Vector<ScheduleCondition> getConditions(String name, String orgId) {
      IndexedStorage storage = IndexedStorage.getIndexedStorage();
      String entry = getCycleEntry(name, orgId).toIdentifier();

      if(storage.contains(entry, orgId)) {
         try {
            DataCycleAsset asset = (DataCycleAsset) storage.getXMLSerializable(entry, null, orgId);

            if(asset.getConditions() != null) {
               return new Vector<>(asset.getConditions());
            }
         }
         catch(Exception e) {
            LOG.error("Failed to set conditions", e);
         }
      }

      return new Vector<>();
   }

   /**
    * Get the total number of the data cycles.
    */
   public int getDataCycleCount() {
      return getDataCycleIds().size();
   }

   /**
    * Get the specified condition.
    */
   public ScheduleCondition getCondition(String name, String orgId, int index) {
      return getConditions(name, orgId).elementAt(index);
   }

   /**
    * Remove the specified condition.
    */
   public void removeCondition(String name, String orgId, int index) {
      getConditions(name, orgId).removeElementAt(index);
   }

   /**
    * Get condition count of specified data cycle.
    */
   public int getConditionCount(String name, String orgId) {
      Vector<ScheduleCondition> conds = getConditions(name, orgId);
      return conds == null ? 0 : conds.size();
   }

   /**
    * Get the specified cycle info.
    */
   public CycleInfo getCycleInfo(String name, String orgId) {
      IndexedStorage storage = IndexedStorage.getIndexedStorage();
      String entry = getCycleEntry(name, orgId).toIdentifier();

      if(storage.contains(entry, orgId)) {
         try {
            DataCycleAsset asset = (DataCycleAsset) storage.getXMLSerializable(entry, null, orgId);
            return asset.getInfo();
         }
         catch(Exception e) {
            LOG.error("Failed get cycle info", e);
         }
      }

      return null;
   }

   /**
    * Set cycle info for a cycle.
    */
   public void setCycleInfo(String name, String orgId, CycleInfo cycleInfo) {
      IndexedStorage storage = IndexedStorage.getIndexedStorage();
      String entry = getCycleEntry(name, orgId).toIdentifier();

      try {
         DataCycleAsset asset;

         if(storage.contains(entry, orgId)) {
            asset = (DataCycleAsset) storage.getXMLSerializable(entry, null, orgId);
         }
         else {
            asset = new DataCycleAsset();
            asset.setName(name);
            asset.setOrgId(orgId);
            asset.setEnabled(true);
         }

         asset.setInfo(cycleInfo);
         storage.putXMLSerializable(entry, asset);
      }
      catch(Exception e) {
         LOG.error("Failed to set cycle info", e);
      }
   }

   /**
    * Moves data cycles from one organization to another
    */
   public void migrateDataCycles(Organization oorg, Organization norg, boolean replace)
      throws Exception
   {
      IndexedStorage storage = IndexedStorage.getIndexedStorage();
      Set<DataCycleId> oldIds = getDataCycleIds(oorg.getId());
      boolean idChanged = !Tool.equals(oorg.getId(), norg.getId());

      if(idChanged) {
         for(DataCycleId oid : oldIds) {
            String oldEntry = getCycleEntry(oid.name, oid.orgId).toIdentifier();
            String newEntry = getCycleEntry(oid.name, norg.getId()).toIdentifier();
            DataCycleAsset asset = (DataCycleAsset)
               storage.getXMLSerializable(oldEntry, null, oorg.getId());
            asset.setOrgId(norg.getId());
            migrateCycleInfo(asset.getInfo(), oorg, norg);
            storage.putXMLSerializable(newEntry, asset);

            if(replace) {
               storage.remove(oldEntry);
            }
         }
      }

      generateTasks(oorg, norg, true, false, replace);
   }

   public void updateCycleInfoNotify(String oldIdentity , String newIdentity, boolean isUser) throws Exception {
      String orgId = OrganizationManager.getInstance().getCurrentOrgID();
      String suffix = isUser ? Identity.USER_SUFFIX : Identity.GROUP_SUFFIX;

      for(String cycle : Collections.list(getDataCycles(orgId))) {
         CycleInfo cycleInfo = getCycleInfo(cycle, orgId);

         updateEmailField(cycleInfo.endNotify, cycleInfo.endEmail, oldIdentity, newIdentity,
                          suffix, cycleInfo::setEndEmail);
         updateEmailField(cycleInfo.startNotify, cycleInfo.startEmail, oldIdentity, newIdentity,
                          suffix, cycleInfo::setStartEmail);
         updateEmailField(cycleInfo.exceedNotify, cycleInfo.exceedEmail, oldIdentity, newIdentity,
                          suffix, cycleInfo::setExceedEmail);
         updateEmailField(cycleInfo.failureNotify, cycleInfo.failureEmail, oldIdentity, newIdentity,
                          suffix, cycleInfo::setFailureEmail);
      }

      save();
   }

   private void updateEmailField(boolean notify, String emailAddresses, String oldIdentity,
                                 String newIdentity, String suffix, Consumer<String> setter)
   {
      if(notify && emailAddresses != null) {
         List<String> emailList = new ArrayList<>();

         for(String email : emailAddresses.split("[;,]", 0)) {
            if(Tool.matchEmail(email) || (!email.endsWith(Identity.USER_SUFFIX) &&
               !email.endsWith(Identity.GROUP_SUFFIX)) || !email.endsWith(suffix))
            {
               emailList.add(email);
               continue;
            }

            String emailName = email.substring(0, email.lastIndexOf(suffix));

            if(emailName.equals(oldIdentity)) {
               emailList.add(newIdentity + suffix);
            }
            else {
               emailList.add(email);
            }
         }

         setter.accept(String.join(",", emailList));
      }
   }

   private void migrateCycleInfo(CycleInfo cycleInfo, Organization oorg, Organization norg) {
      if(cycleInfo == null) {
         return;
      }

      cycleInfo.setOrgId(norg.getId());
      String createdBy = cycleInfo.getCreatedBy();
      boolean idChanged = !Tool.equals(oorg.getId(), norg.getId());

      if(!Tool.isEmptyString(createdBy) && idChanged) {
         IdentityID identityID = IdentityID.getIdentityIDFromKey(createdBy);
         identityID.setOrgID(norg.getId());
      }

      String lastModifiedBy = cycleInfo.getLastModifiedBy();

      if(!Tool.isEmptyString(lastModifiedBy) && idChanged) {
         IdentityID identityID = IdentityID.getIdentityIDFromKey(lastModifiedBy);
         identityID.setOrgID(norg.getId());
      }
   }

   public void clearDataCycles(String orgId) {
      IndexedStorage storage = IndexedStorage.getIndexedStorage();

      for(DataCycleId id : getDataCycleIds(orgId)) {
         String entry = getCycleEntry(id.name(), id.orgId()).toIdentifier();

         if(storage.contains(entry, orgId)) {
            storage.remove(entry);
         }
      }
   }

   /**
    * Refreshes the cycle data.
    */
   public void refresh() {
      init();
      MVManager.getManager().setDefaultCycle(getDefaultCycle());
   }

   /**
    * Triggered when identity removed.
    * @param identity the specified identity.
    */
   @Override
   public void identityRemoved(Identity identity) {
      // do nothing
   }

   /**
    * Triggered when identity renamed.
    * @param oname the specified original name.
    * @param identity the specified identity.
    */
   @Override
   public void identityRenamed(String oname, Identity identity) {
      // do nothing
   }

   /**
    * Set the default cycle.
    */
   public void setDefaultCycle(String dcycle) {
      SreeEnv.setProperty("default.data.cycle", dcycle);
   }

   /**
    * Get the default cycle.
    */
   public String getDefaultCycle() {
      return SreeEnv.getProperty("default.data.cycle");
   }

   private AssetEntry getCycleEntry(String name, String orgId) {
      return new AssetEntry(
         AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.DATA_CYCLE, PREFIX + name,
         null, orgId);
   }

   @Override
   public void storageRefreshed(StorageRefreshEvent event) {
      boolean changed = false;

      for(TimestampIndexChange change : event.getChanges()) {
         if(change.getKey() != null) {
            AssetEntry entry = AssetEntry.createAssetEntry(change.getKey());

            if(entry != null && entry.getType() == AssetEntry.Type.DATA_CYCLE) {
               changed = true;
            }
         }
      }

      if(changed) {
         refresh();
      }
   }

   private Map<DataCycleId, Vector<ScheduleCondition>> dataCycleMap =
      new LinkedHashMap<>();
   private Map<DataCycleId, Boolean> cycleStatusMap = new LinkedHashMap<>();
   private Map<String, Boolean> orgPregeneratedTaskLoadedStatus = new ConcurrentHashMap<>();
   private Map<String, Vector<ScheduleTask>> pregeneratedTasksMap = new HashMap<>();
   private Map<DataCycleId, CycleInfo> cycleInfoMap = new HashMap<>();
   private String dcycle;   private static final Logger LOG = LoggerFactory.getLogger(DataCycleManager.class);

   private final String PREFIX = "/__DATA_CYCLE__";

   public static class CycleInfo implements Cloneable, XMLSerializable, Serializable {
      public CycleInfo() {
      }

      public CycleInfo(String name, String orgId) {
         this.name = name;
         this.orgId = orgId;
      }

      public boolean isStartNotify() {
         return startNotify;
      }

      public void setStartNotify(boolean startNotify) {
         this.startNotify = startNotify;
      }

      public String getStartEmail() {
         return startEmail;
      }

      public void setStartEmail(String startEmail) {
         this.startEmail = startEmail;
      }

      public boolean isEndNotify() {
         return endNotify;
      }

      public void setEndNotify(boolean endNotify) {
         this.endNotify = endNotify;
      }

      public String getEndEmail() {
         return endEmail;
      }

      public void setEndEmail(String endEmail) {
         this.endEmail = endEmail;
      }

      public boolean isFailureNotify() {
         return failureNotify;
      }

      public void setFailureNotify(boolean failureNotify) {
         this.failureNotify = failureNotify;
      }

      public String getFailureEmail() {
         return failureEmail;
      }

      public void setFailureEmail(String failureEmail) {
         this.failureEmail = failureEmail;
      }

      public boolean isExceedNotify() {
         return exceedNotify;
      }

      public void setExceedNotify(boolean exceedNotify) {
         this.exceedNotify = exceedNotify;
      }

      public int getThreshold() {
         return threshold;
      }

      public void setThreshold(int threshold) {
         this.threshold = threshold;
      }

      public String getExceedEmail() {
         return exceedEmail;
      }

      public void setExceedEmail(String exceedEmail) {
         this.exceedEmail = exceedEmail;
      }

      public String getName() {
         return name;
      }

      public void setName(String name) {
         this.name = name;
      }

      public String getOrgId() {
         return orgId;
      }

      public void setOrgId(String orgId) {
         this.orgId = orgId;
      }

      /**
       * Get created time.
       * @return created time.
       */
      public long getCreated() {
         return created;
      }

      /**
       * Set created time.
       * @param created the specified created time.
       */
      public void setCreated(long created) {
         this.created = created;
      }

      /**
       * Get last modified.
       * @return last modified time.
       */
      public long getLastModified() {
         return modified;
      }

      /**
       * Set last modified time.
       * @param modified the specified last modified time.
       */
      public void setLastModified(long modified) {
         this.modified = modified;
      }

      /**
       * Get the created person.
       * @return the created person.
       */
      public String getCreatedBy() {
         return createdBy;
      }

      /**
       * Set the created person
       * @param createdBy the created person.
       */
      public void setCreatedBy(String createdBy) {
         this.createdBy = createdBy;
      }

      /**
       * Get last modified person.
       * @return last modified person.
       */
      public String getLastModifiedBy() {
         return modifiedBy;
      }

      /**
       * Set last modified person.
       * @param modifiedBy the specified last modified person.
       */
      public void setLastModifiedBy(String modifiedBy) {
         this.modifiedBy = modifiedBy;
      }

      public String toString() {
         return startNotify + ", " + startEmail + ", " +
            endNotify + ", " +  endEmail + ", " +
            failureNotify + ", " + failureEmail + ", " +
            exceedNotify + ", " + threshold + ", " + exceedEmail + "," +
            createdBy + "," + created + "," + modified + "," + modifiedBy;
      }

      @Override
      public void parseXML(Element tag) throws Exception {
         name = Tool.getAttribute(tag, "name");
         orgId = Tool.getAttribute(tag, "orgId");
         startNotify = "true".equals(Tool.getAttribute(tag, "startNotify"));
         startEmail = Tool.getAttribute(tag, "startEmail");
         endNotify = "true".equals(Tool.getAttribute(tag, "endNotify"));
         endEmail = Tool.getAttribute(tag, "endEmail");
         exceedNotify = "true".equals(Tool.getAttribute(tag, "exceedNotify"));
         exceedEmail = Tool.getAttribute(tag, "exceedEmail");
         failureNotify = "true".equals(Tool.getAttribute(tag, "failureNotify"));
         failureEmail = Tool.getAttribute(tag, "failureEmail");
         threshold = Integer.parseInt(Objects.requireNonNull(Tool.getAttribute(tag, "threshold")));
         createdBy = Tool.getAttribute(tag, "createdBy");
         modifiedBy = Tool.getAttribute(tag, "modifiedBy");
         String val = Tool.getAttribute(tag, "created");

         if(val != null) {
            this.created = Long.parseLong(val);
         }

         val = Tool.getAttribute(tag, "modified");

         if(val != null) {
            this.modified = Long.parseLong(val);
         }
      }

      @Override
      public void writeXML(PrintWriter writer) {
         String infos = "name=\"" + Encode.forXmlAttribute(name) + "\" " +
            "orgId=\"" + Encode.forXmlAttribute(orgId) + "\" " +
            "startNotify=\"" + startNotify + "\" " +
            "endNotify=\"" + endNotify + "\" " +
            "failureNotify=\"" + failureNotify + "\" " +
            "exceedNotify=\"" + exceedNotify + "\" " +
            "threshold=\"" + threshold + "\"";

         if(startEmail != null) {
            infos += " startEmail=\"" + startEmail + "\"";
         }

         if(endEmail != null) {
            infos += " endEmail=\"" + endEmail + "\"";
         }

         if(failureEmail != null) {
            infos += " failureEmail=\"" + failureEmail + "\"";
         }

         if(exceedEmail != null) {
            infos += " exceedEmail=\"" + exceedEmail + "\"";
         }

         if(createdBy != null) {
            infos += " createdBy=\"" + createdBy + "\"";
         }

         if(created != 0) {
            infos += " created=\"" + created + "\"";
         }

         if(modifiedBy != null) {
            infos += " modifiedBy=\"" + modifiedBy + "\"";
         }

         if(modified != 0) {
            infos += " modified=\"" + modified + "\"";
         }

         writer.print("<CycleInfo " + infos + "/>");
      }

      @Override
      public Object clone() {
         try {
            return super.clone();
         }
         catch(CloneNotSupportedException e) {
            LOG.error("Clone failed: " + e);
            return null;
         }
      }

      private boolean startNotify;
      private String startEmail;
      private boolean endNotify;
      private String endEmail;
      private boolean failureNotify;
      private String failureEmail;
      private boolean exceedNotify;
      private String exceedEmail;
      private int threshold;
      private String name;
      private String orgId;
      private long created;
      private long modified;
      private String createdBy;
      private String modifiedBy;
   }

   public static final class DataCycleAsset implements Serializable, XMLSerializable {
      public List<ScheduleCondition> getConditions() {
         return conditions;
      }

      public String getName() {
         return name;
      }

      public void setName(String name) {
         this.name = name;
      }

      public String getOrgId() {
         return orgId;
      }

      public void setOrgId(String orgId) {
         this.orgId = orgId;
      }

      public void setConditions(List<ScheduleCondition> conditions) {
         this.conditions = conditions;
      }

      public boolean isEnabled() {
         return enabled;
      }

      public void setEnabled(boolean enabled) {
         this.enabled = enabled;
      }

      public CycleInfo getInfo() {
         return info;
      }

      public void setInfo(CycleInfo info) {
         this.info = info;
      }

      @Override
      public void writeXML(PrintWriter writer) {
         writer.format(
            "<DataCycle name=\"%s\" orgId=\"%s\" enabled=\"%s\">%n",
            Encode.forXmlAttribute(name), Encode.forXmlAttribute(orgId), enabled);
         writer.println("<conditions>");

         if(conditions != null) {
            for(ScheduleCondition condition : conditions) {
               if(condition instanceof TimeCondition tc) {
                  tc.writeXML(writer);
               }
            }
         }

         writer.println("</conditions>");

         if(info != null) {
            info.writeXML(writer);
         }

         writer.println("</DataCycle>");
      }

      @Override
      public void parseXML(Element tag) throws Exception {
         name = Tool.getAttribute(tag, "name");
         orgId = Tool.getAttribute(tag, "orgId");
         enabled = !"false".equals(Tool.getAttribute(tag, "enabled"));
         conditions = new ArrayList<>();
         info = null;

         Element element;

         if((element = Tool.getChildNodeByTagName(tag, "conditions")) != null) {
            NodeList nodes = Tool.getChildNodesByTagName(element, "Condition");

            for(int i = 0; i < nodes.getLength(); i++) {
               TimeCondition condition = new TimeCondition();
               condition.parseXML((Element) nodes.item(i));
               conditions.add(condition);
            }
         }

         if((element = Tool.getChildNodeByTagName(tag, "CycleInfo")) != null) {
            info = new CycleInfo();
            info.parseXML(element);
         }
      }

      private String name;
      private String orgId;
      private boolean enabled;
      private List<ScheduleCondition> conditions = new ArrayList<>();
      private CycleInfo info;
   }

   public record DataCycleId(String name, String orgId) {}

   public static final class Reference
      extends SingletonManager.Reference<DataCycleManager>
   {
      @Override
      public DataCycleManager get(Object... parameters) {
         // prevent deadlock caused by scheduler manager and replet engine initialization
         SingletonManager.getInstance(ScheduleManager.class);

         if(manager == null) {
            Lock lock = Cluster.getInstance().getLock(Scheduler.INIT_LOCK);
            lock.lock();

            try {
               if(manager == null) {
                  manager = new DataCycleManager();
                  ScheduleManager.getScheduleManager().initialize();

                  try {
                     RepletRegistry.getRegistry().addPropertyChangeListener(manager);
                  }
                  catch(Exception ex) {
                     LOG.error("Failed to add property change listener to replet registry", ex);
                  }

                  MVManager.getManager().addPropertyChangeListener(manager);
               }
            }
            finally {
               lock.unlock();
            }
         }

         return manager;
      }

      @Override
      public void dispose() {
         manager = null;
      }

      private DataCycleManager manager;
   }
}
