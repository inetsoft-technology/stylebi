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

import inetsoft.sree.*;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import inetsoft.web.RecycleUtils;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Schedule manager manages schedule tasks. The schedule task might be a normal
 * schedule task or comes from a schedule extension. Scheduler will load and
 * execute the schedule tasks contained in the schedule manager.
 *
 * @version 7.0
 * @author InetSoft Technology Corp
 */
@Service
@Lazy
public class ScheduleManager {
   /**
    * Get the schedule manager.
    */
   public static ScheduleManager getScheduleManager() {
      return ConfigurationContext.getContext().getSpringBean(ScheduleManager.class);
   }

   public static boolean isInternalTask(String taskName) {
      return InternalScheduledTaskService.isInternalTask(taskName);
   }

   public static String getTaskId(String owner, String taskName, String orgId) {
      orgId = orgId == null ? OrganizationManager.getInstance().getCurrentOrgID() : orgId;

      if(owner.contains(IdentityID.KEY_DELIMITER)) {
         return getTaskId(owner, taskName);
      }

      return getTaskId(new IdentityID(owner, orgId).convertToKey(), taskName);
   }

   public static String getTaskId(String ownerID, String taskName) {
      if(taskName.startsWith(ownerID + ":")) {
         return taskName;
      }

      return ownerID + ":" + taskName;
   }

   public static IdentityID getOwner(String taskId) {
      return IdentityID.getIdentityIDFromKey(taskId.split(":")[0]);
   }

   public static ScheduleTaskMetaData getTaskMetaData(String taskId) {
      // Bug #77883, the owner key ends at the first ':', the task name may contain ':'
      int index = taskId.indexOf(':');

      if(index >= 0) {
         return new ScheduleTaskMetaData(taskId.substring(index + 1), taskId.substring(0, index));
      }
      else {
         return new ScheduleTaskMetaData(taskId, null);
      }
   }

   /**
    * @return the names of the internal tasks that have the write permission.
    */
   public static List<String> getWriteableInternalTaskNames() {
      return Arrays.asList(InternalScheduledTaskService.ASSET_FILE_BACKUP);
   }

   /**
    * Spring-injected constructor — enforces SecurityEngine and Cluster startup ordering.
    */
   @Autowired
   public ScheduleManager(SecurityEngine securityEngine, Cluster cluster, ScheduleClient scheduleClient,
                          DependencyHandler dependencyHandler)
   {
      this.securityEngine = securityEngine;
      this.cluster = cluster;
      this.scheduleClient = scheduleClient;
      this.dependencyHandler = dependencyHandler;
      initMap();
   }

   @PostConstruct
   public void initInternalTasks() {
      try {
         new InternalScheduledTaskService(this).initInternalTasks();
      }
      catch(Exception ex) {
         LOG.error("Failed to initialize internal schedule tasks", ex);
      }
   }

   /**
    * populate map of organization scoped scheduleTaskMap
    */
   public void initMap() {
      SecurityProvider provider = securityEngine.getSecurityProvider();
      for(String org : provider.getOrganizationIDs())
      {
         taskMap.put(org, new ScheduleTaskMap(org));
      }
   }

   public List<ScheduleTask> getAllScheduleTasks() {
      List<ScheduleTask> tasks = new ArrayList<>();

      for(ScheduleTaskMap map : taskMap.values()) {
         tasks.addAll(map.values());
      }

      return tasks.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
   }

   /**
    * Gets the schedule tasks of all organizations, including the extension tasks, by the
    * organization they're stored in. Unlike {@link #getScheduleTasks()}, a task stored in more
    * than one organization is in the tasks of each of them.
    */
   Map<String, List<ScheduleTask>> getScheduleTasksByOrganization() {
      Map<String, List<ScheduleTask>> tasks = new LinkedHashMap<>();

      for(Map.Entry<String, ScheduleTaskMap> entry : new ArrayList<>(taskMap.entrySet())) {
         List<ScheduleTask> orgTasks = tasks.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
         entry.getValue().values().stream().filter(Objects::nonNull).forEach(orgTasks::add);
      }

      extensionLock.lock();

      try {
         for(Map.Entry<ExtTaskKey, ScheduleTask> entry : extensionTasks.entrySet()) {
            ScheduleTask task = entry.getValue();
            List<ScheduleTask> orgTasks =
               tasks.computeIfAbsent(entry.getKey().orgId(), k -> new ArrayList<>());

            if(orgTasks.stream().noneMatch(t -> t.getTaskId().equals(task.getTaskId()))) {
               orgTasks.add(task);
            }
         }
      }
      finally {
         extensionLock.unlock();
      }

      return tasks;
   }

   public ScheduleTaskMap getOrgTaskMap(String orgID) {
      ScheduleTaskMap map = taskMap.get(orgID);

      if(map != null) {
         return map;
      }

      //add this org id to task map
      taskMap.put(orgID, new ScheduleTaskMap(orgID));

      return taskMap.get(orgID);
   }

   /**
    * Initialize the schedule manager. This method will be called when
    */
   public void initialize() {
      extensionLock.lock();

      try {
         XPrincipal siteAdminPrincipal = getSiteAdminPrincipal();
         String[] organizations = getSecurityEngine().getOrganizations();
         Principal oldContextPrincipal = ThreadContext.getContextPrincipal();

         try {
            ThreadContext.setContextPrincipal(siteAdminPrincipal);

            for(String orgID : organizations) {
               OrganizationManager.getInstance().setCurrentOrgID(orgID);
               reloadExtensions0(orgID);
            }
         }
         finally {
            ThreadContext.setContextPrincipal(oldContextPrincipal);
         }
      }
      finally {
         extensionLock.unlock();
      }
   }

   /**
    * Reload extensions. The old schedule tasks come from schedule extensions
    * will be discarded and new schedule tasks come from schedule extensions
    * will be loaded.
    */
   public void reloadExtensions(String orgID) {
      extensionLock.lock();

      try {
         reloadExtensions0(orgID);
      }
      finally {
         extensionLock.unlock();
      }
   }

   private XPrincipal getSiteAdminPrincipal() {
      IdentityID[] users = getSecurityEngine().getUsers();

      for(IdentityID user : users) {
         if(OrganizationManager.getInstance().isSiteAdmin(user)) {
            return SUtil.getPrincipal(user, Tool.getIP(), false);
         }
      }

      return null;
   }

   /**
    * Reload extensions. The old schedule tasks come from schedule extensions
    * will be discarded and new schedule tasks come from schedule extensions
    * will be loaded.
    */
   private void reloadExtensions0(String orgID) {
      orgID = orgID != null ? orgID : OrganizationManager.getInstance().getCurrentOrgID();
      boolean scheduler = "true".equals(System.getProperty("ScheduleServer"));

      Map<ExtTaskKey, ScheduleTask> oldExtensionTasks = new HashMap<>(extensionTasks);
      removeExtensionTasksOfOrg(orgID);

      // get ext tasks from the exts
      for(ScheduleExt ext : extensions) {
         List<ScheduleTask> tasks;

         synchronized(ext) {
            tasks = new ArrayList<>(ext.getTasks(orgID));
         }

         for(ScheduleTask task : tasks) {
            ExtTaskKey key = createExtensionTaskKey(task);

            if(!extensionTasks.containsKey(key)) {
               ScheduleTask oldTask = oldExtensionTasks.remove(key);
               extensionTasks.put(key, task);
               extensionTaskOwners.put(key, ext);

               // ScheduleTask.equals() ignores cycleInfo, so compare it explicitly to
               // push notification changes of data cycle tasks to the scheduler
               if(!scheduler && (!task.equals(oldTask) ||
                  !Objects.equals(task.getCycleInfo(), oldTask.getCycleInfo())))
               {
                  try {
                     scheduleClient.taskAdded(task);
                  }
                  catch(RemoteException e) {
                     LOG.error("Failed to update scheduler with extension task: " +
                                  task.getTaskId(), e);
                  }

                  // Send message to notify UI about the new task (after it's in extensionTasks)
                  ScheduleTaskMessage message = new ScheduleTaskMessage();
                  message.setTaskName(task.getTaskId());
                  message.setTask(task);
                  message.setAction(oldTask == null ?
                     ScheduleTaskMessage.Action.ADDED : ScheduleTaskMessage.Action.MODIFIED);

                  try {
                     getCluster().sendMessage(message);
                  }
                  catch(Exception e) {
                     LOG.debug("Failed to send task message", e);
                  }
               }
            }
            else {
               LOG.warn("Duplicate task found, not added: " + task);
            }
         }
      }

      if(!scheduler) {
         for(ExtTaskKey taskKey : oldExtensionTasks.keySet()) {
            if(!Tool.equals(taskKey.orgId, orgID)) {
               continue;
            }

            // task is no longer in the new task list, remove it
            try {
               scheduleClient.taskRemoved(taskKey.name);
            }
            catch(Exception e) {
               LOG.error("Failed to remove extension task: " + taskKey.name, e);
            }
         }
      }

      extensionTasksLoadedOrgs.add(orgID);
   }

   private ExtTaskKey createExtensionTaskKey(ScheduleTask task) {
      String taskId = task.getTaskId();
      String orgId;

      if(task.getCycleInfo() != null) {
         orgId = task.getCycleInfo().getOrgId();
      }
      else {
         orgId = OrganizationManager.getInstance().getCurrentOrgID();
      }

      return new ExtTaskKey(taskId, orgId);
   }

   private String getExtensionTaskOrgId(ScheduleTask task, String defaultOrgId) {
      return task.getCycleInfo() != null ? task.getCycleInfo().getOrgId() : defaultOrgId;
   }

   /**
    * Get the schedule extension that owns a task, or null if the task is not an extension task.
    * Only data cycle tasks are extension tasks, any other task never calls into the extensions
    * (their task lists are not thread safe). The ownership is taken from the extension tasks
    * loaded by reloadExtensions0(). If the task is not found there, e.g. while a reload is in
    * progress or before the org is loaded, the extensions are asked under extensionLock and the
    * extension monitor, so a concurrent reload or task generation is not observed half done.
    * Lock order: ScheduleManager monitor (caller) -> extensionLock -> extension monitor.
    */
   private ScheduleExt getTaskExtension(ScheduleTask task, String orgId) {
      if(task.getType() != ScheduleTask.Type.CYCLE_TASK) {
         return null;
      }

      ExtTaskKey key = new ExtTaskKey(task.getTaskId(), orgId);
      ScheduleExt owner = extensionTaskOwners.get(key);

      if(owner != null) {
         return owner;
      }

      extensionLock.lock();

      try {
         owner = extensionTaskOwners.get(key);

         if(owner != null) {
            return owner;
         }

         for(ScheduleExt ext : extensions) {
            synchronized(ext) {
               if(ext.containsTask(task.getTaskId(), orgId)) {
                  return ext;
               }
            }
         }
      }
      finally {
         extensionLock.unlock();
      }

      return null;
   }

   /**
    * Only the enabled state of an extension task can be changed, log any other change that is
    * dropped (e.g. conditions or actions edited through the task editor or the REST API).
    */
   private void logIgnoredExtensionTaskChanges(ScheduleTask task, String orgId) {
      ScheduleTask current = extensionTasks.get(new ExtTaskKey(task.getTaskId(), orgId));

      if(current != null && current != task) {
         ScheduleTask expected = current.clone();
         expected.setEnabled(task.isEnabled());

         if(!expected.equals(task)) {
            LOG.warn("Only the enabled state of data cycle task {} can be changed, other " +
                        "changes are ignored", task.getTaskId());
         }
      }
   }

   /**
    * For a schedule task in a schedule extension, we should not save it but only change the
    * enable option in the extension.
    *
    * @return <tt>true</tt> if the extension changed, <tt>false</tt> otherwise.
    */
   private boolean updateExtensionEnabled(ScheduleExt ext, ScheduleTask task, String orgId) {
      if(ext.isEnable(task.getTaskId(), orgId) != task.isEnabled()) {
         ext.setEnable(task.getTaskId(), orgId, task.isEnabled());
         return true;
      }

      return false;
   }

   /**
    * A data cycle task is generated from its data cycle and is never stored as an ordinary
    * schedule task. One that no extension owns (e.g. read back from XML, which mangles its id
    * and drops the cycle info) is stale and must be dropped, the data cycle is the source of
    * truth.
    */
   private boolean isUnownedCycleTask(ScheduleTask task) {
      if(task.getType() == ScheduleTask.Type.CYCLE_TASK) {
         LOG.warn("Data cycle task {} does not belong to an existing data cycle, " +
                     "it is not saved as a schedule task", task.getTaskId());
         return true;
      }

      return false;
   }

   /**
    * Save the all the schedule tasks.
    * @param tasks all the schedule tasks which might be changed.
    */
   public synchronized void save(Collection<ScheduleTask> tasks, String orgID) throws Exception {
      boolean extChanged = false;

      try {
         for(ScheduleTask task : tasks) {
            if(save(task, orgID)) {
               extChanged = true;
            }
         }
      }
      catch(Throwable exc) {
         throw new Exception("Failed to save schedule.xml", exc);
      }

      if(extChanged) {
         reloadExtensions(orgID);
      }
   }

   /**
    * Save the all the schedule task.
    */
   private boolean save(ScheduleTask task, String orgID) throws Exception {
      String extOrgId = getExtensionTaskOrgId(
         task, OrganizationManager.getInstance().getCurrentOrgID());
      ScheduleExt ext = getTaskExtension(task, extOrgId);

      if(ext != null) {
         logIgnoredExtensionTaskChanges(task, extOrgId);
         return updateExtensionEnabled(ext, task, extOrgId);
      }

      if(isUnownedCycleTask(task)) {
         return false;
      }

      ScheduleTaskMessage.Action action;

      if(this.getOrgTaskMap(orgID).containsKey(getTaskIdentifier(task.getTaskId(), orgID))) {
         action = ScheduleTaskMessage.Action.MODIFIED;
      }
      else {
         action = ScheduleTaskMessage.Action.ADDED;
      }

      this.getOrgTaskMap(orgID).put(getTaskIdentifier(task.getTaskId(), orgID), task);
      scheduleClient.taskAdded(task);
      ScheduleTaskMessage message = new ScheduleTaskMessage();
      message.setTaskName(task.getTaskId());
      message.setTask(task);
      message.setAction(action);
      getCluster().sendMessage(message);
      return false;
   }

   /**
    * Save the all the schedule tasks.
    * @param tasks all the schedule tasks which might be changed.
    */
   public synchronized void save(Collection<ScheduleTask> tasks) throws Exception {
      this.save(tasks, OrganizationManager.getInstance().getCurrentOrgID());
   }

   /**
    * Get schedule tasks.
    */
   public Vector<ScheduleTask> getScheduleTasks() {
      Vector<ScheduleTask> list = new Vector<>(getAllScheduleTasks());
      extensionLock.lock();

      try {
         for(ScheduleTask task : getExtensionTasks()) {
            if(list.stream().noneMatch(t -> t != null && t.getTaskId().equals(task.getTaskId()))) {
               list.add(task);
            }
         }
      }
      finally {
         extensionLock.unlock();
      }

      for(int i = list.size() - 1; i >= 0; i--) {
         if(list.get(i) == null) {
            list.remove(i);
         }
      }

      return list;
   }

   /**
    * Get schedule tasks.
    */
   public Vector<ScheduleTask> getScheduleTasks(String orgID) {
      if(!extensionTasksLoadedOrgs.contains(orgID)) {
         reloadExtensions(orgID);
      }

      Vector<ScheduleTask> list = new Vector<>(getOrgTaskMap(orgID).values());
      extensionLock.lock();

      try {
         for(ScheduleTask task : getFilteredExtensionTasks(orgID)) {
            if(list.stream().noneMatch(t -> t != null && t.getTaskId().equals(task.getTaskId()))) {
               list.add(task);
            }
         }
      }
      finally {
         extensionLock.unlock();
      }

      for(int i = list.size() - 1; i >= 0; i--) {
         if(list.get(i) == null) {
            list.remove(i);
         }
      }

      return list;
   }

   /**
    * Get schedule tasks.
    */
   public Vector<ScheduleTask> getScheduleTasks(AssetEntry[] taskEntries, boolean loadExtension, boolean loadInternal, String orgID) {
      Set<String> allNames =
         Arrays.stream(taskEntries).map(AssetEntry::getName).collect(Collectors.toSet());
      Vector<ScheduleTask> list = new Vector<>(getOrgTaskMap(orgID).values());

      for(int i = list.size() - 1; i >= 0; i--) {
         if(list.get(i) == null || (!allNames.contains(list.get(i).getTaskId()) &&
            !(loadInternal && isInternalTask(list.get(i).getTaskId()))))
         {
            list.remove(i);
         }
      }

      if(loadExtension) {
         if(!extensionTasksLoadedOrgs.contains(orgID)) {
            reloadExtensions(orgID);
         }

         extensionLock.lock();

         try {
            for(ScheduleTask task : getFilteredExtensionTasks(orgID)) {
               if(list.stream().noneMatch(t -> t != null && t.getTaskId().equals(task.getTaskId())) &&
                  !allNames.contains(task.getTaskId()))
               {
                  list.add(task);
               }
            }
         }
         finally {
            extensionLock.unlock();
         }
      }

      return list;
   }

   /**
    * Get a schedule task with a schedule task id.
    */
   public synchronized ScheduleTask getScheduleTask(String taskId) {
      return getScheduleTask(taskId, null);
   }

   public synchronized ScheduleTask getScheduleTask(String taskId, String orgID) {
      if(orgID == null) {
         orgID = OrganizationManager.getInstance().getCurrentOrgID();
      }

      ScheduleTask task = extensionTasks.get(new ExtTaskKey(taskId, orgID));

      if(InternalScheduledTaskService.isInternalTask(taskId)) {
         orgID = Organization.getDefaultOrganizationID();
      }

      if(task == null) {
         task = getOrgTaskMap(orgID).get(getTaskIdentifier(taskId, orgID));
      }

      // older version (pre 13.1) doesn't have user name as part of the task name (48791).
      if(task == null && taskId != null && taskId.contains(":")) {
         int index = taskId.indexOf(':');
         ScheduleTask legacyTask =
            getOrgTaskMap(orgID).get(getTaskIdentifier(taskId.substring(index + 1), orgID));

         // Bug #77356: don't resolve an owner prefix of another organization to a task of this
         // organization
         if(legacyTask != null &&
            isLegacyTaskIdPrefix(taskId.substring(0, index), orgID, taskId, legacyTask))
         {
            task = legacyTask;
         }
      }

      return task;
   }

   /**
    * Whether the owner prefix stripped by the legacy task id fallback may match a task stored
    * with an owner-less id in the given organization: a prefix without an organization (pre 13.1
    * owner name), a prefix naming that organization, or the found task's own id (e.g. a legacy
    * owner of another organization, the host organization system user).
    */
   private static boolean isLegacyTaskIdPrefix(String prefix, String orgID, String taskId,
                                               ScheduleTask legacyTask)
   {
      if(!prefix.contains(IdentityID.KEY_DELIMITER)) {
         return true;
      }

      String prefixOrgID = IdentityID.getIdentityIDFromKey(prefix).getOrgID();

      if(prefixOrgID != null && prefixOrgID.equalsIgnoreCase(orgID)) {
         return true;
      }

      // a data cycle task without an owner has no id
      return (legacyTask.getType() != ScheduleTask.Type.CYCLE_TASK ||
              legacyTask.getOwner() != null) && taskId.equals(legacyTask.getTaskId());
   }

   /**
    * Get all the schedule task activities.
    */
   public Map<String, TaskActivity> getScheduleActivities() {
      Map<String, TaskActivity> result;

      try {
         result = scheduleClient.getScheduleActivities();
      }
      catch(RemoteException e) {
         LOG.error("Failed to get schedule activities", e);
         result = Collections.emptyMap();
      }

      return result;
   }

   /**
    * Add a schedule extension.
    */
   public void addScheduleExt(ScheduleExt ext) {
      if(!extensions.contains(ext)) {
         extensions.add(ext);
      }
   }

   /**
    * Get all the schedule extension.
    */
   public Vector<ScheduleExt> getExtensions() {
      return extensions;
   }

   /**
    * Gets the schedule extension tasks.
    *
    * @return the extension tasks.
    */
   public Vector<ScheduleTask> getExtensionTasks() {
      return new Vector<>(extensionTasks.values());
   }

   public Set<ScheduleTask> getFilteredExtensionTasks(String orgId) {
      final String orgId0;
      if(orgId == null) {
         orgId0 = OrganizationManager.getInstance().getCurrentOrgID();
      }
      else {
         orgId0 = orgId;
      }
      return extensionTasks.keySet()
         .stream()
         .filter(key -> Tool.equals(key.orgId, orgId0))
         .map(extensionTasks::get)
         .filter(task -> task != null && task.getOwner() != null &&
            Tool.equals(task.getOwner().getOrgID(), orgId0))
         .collect(Collectors.toCollection(HashSet::new));
   }

   /**
    * Get all the tasks depends on the access permission.
    */
   public Vector<ScheduleTask> getScheduleTasks(Principal principal)
      throws Exception
   {
      RepletRepository engine = SUtil.getRepletRepository();
      String[] taskNames = engine.getScheduleTasks(principal);
      Vector<ScheduleTask> vec = new Vector<>();

      for(String taskName : taskNames) {
         ScheduleTask task = engine.getScheduleTask(taskName);

         if(task != null) {
            vec.addElement(task);
         }
      }

      return vec;
   }

   public Vector<ScheduleTask> getScheduleTasks(Principal principal,
                                                Collection<ScheduleTask> allTasks, String orgID)
      throws Exception
   {
      RepletRepository engine = SUtil.getRepletRepository();
      String[] taskNames = engine.getScheduleTasks(principal, allTasks);
      Vector<ScheduleTask> vec = new Vector<>();

      for(String taskName : taskNames) {
         ScheduleTask task = engine.getScheduleTask(taskName, orgID);

         if(task != null) {
            vec.addElement(task);
         }
      }

      return vec;
   }

   public synchronized void setScheduleTask(String taskId, ScheduleTask task,
                                            Principal principal)
           throws Exception
   {
      setScheduleTask(taskId, task, null, principal);
   }

   public synchronized void setScheduleTask(String taskId, ScheduleTask task, AssetEntry parent,
                                            Principal principal)
      throws Exception
   {
      setScheduleTask(taskId, task, parent, ScheduleManager.isInternalTask(taskId), principal);
   }

   /**
    * Save a schedule task.
    */
   public synchronized void setScheduleTask(String taskId, ScheduleTask task, AssetEntry parent,
                                            boolean internal, Principal principal)
      throws Exception
   {
      setScheduleTask(taskId, task, parent, internal, principal, false);
   }

   /**
    * Saves a schedule task in place of a stored task that the caller already removed (e.g. to
    * rename it). Bug #77972, the batch action targets of the task are checked against the targets
    * of that stored task, which can no longer be read when the task is saved, a kept target is
    * not checked again.
    *
    * @param stored the task as it was stored before the caller removed it, or {@code null} if
    *               there was none.
    */
   public synchronized void setScheduleTask(String taskId, ScheduleTask task, AssetEntry parent,
                                            Principal principal, ScheduleTask stored)
      throws Exception
   {
      setScheduleTask(taskId, task, parent, isInternalTask(taskId), principal, false, stored);
   }

   /**
    * Replaces a stored task with a task, i.e. removes the task stored under the old id and saves
    * the task under its own id. Bug #77359, the owner organization of the task is checked before
    * the old task is removed, against the task stored before this change, so a task that is
    * refused isn't lost and a task that already is owned by another organization (stored before
    * the check) can still be modified.
    *
    * @param oldTaskId the id of the stored task.
    * @param task      the task to save under its own id.
    */
   public synchronized void replaceScheduleTask(String oldTaskId, ScheduleTask task,
                                                AssetEntry parent, Principal principal)
      throws Exception
   {
      if(task == null) {
         return;
      }

      String taskId = task.getTaskId();
      boolean ownerChecked = task.getOwner() != null;
      // Bug #77972, the stored task is read before it's removed, the save checks the batch
      // action targets against it
      ScheduleTask stored = getStoredTask(oldTaskId, taskId, principal);
      // Bug #77549, #77863, every refusal of the save is made before the stored task is
      // removed, so it isn't lost
      checkReplaceScheduleTask(oldTaskId, taskId, task, principal);
      removeScheduleTask(oldTaskId, principal);
      setScheduleTask(taskId, task, parent, isInternalTask(taskId), principal, ownerChecked,
                      stored);
   }

   /**
    * Bug #77863, checks, before a stored task is removed or rewritten (e.g. its folder) to save a
    * task with {@link #setScheduleTask(String, ScheduleTask, AssetEntry, Principal)}, that the
    * save will not be refused, so a refused save doesn't lose or change the stored task. Makes
    * every refusal the save makes, with the same rules, and changes nothing.
    *
    * @param taskId    the id the task is saved under.
    * @param task      the task as it will be saved.
    * @param principal the principal the task is saved as.
    *
    * @throws Exception if the save would be refused.
    */
   public synchronized void checkScheduleTaskSave(String taskId, ScheduleTask task,
                                                  Principal principal)
      throws Exception
   {
      checkScheduleTaskSave(taskId, task, principal, false,
                            getStoredTask(taskId, taskId, principal));
   }

   /**
    * Bug #77863, checks, before a stored task is removed to save a task in its place (e.g. to
    * rename it), that the save will not be refused, so a refused save doesn't lose the stored
    * task. The owner organization is checked against the stored task, as
    * {@link #checkReplaceOwnerOrganization}, every other refusal as
    * {@link #checkScheduleTaskSave}.
    *
    * @param oldTaskId the id of the stored task, which is removed.
    * @param taskId    the id the task is saved under.
    * @param task      the task as it will be saved.
    * @param principal the principal the task is saved as.
    *
    * @throws Exception if the save would be refused.
    */
   public synchronized void checkReplaceScheduleTask(String oldTaskId, String taskId,
                                                     ScheduleTask task, Principal principal)
      throws Exception
   {
      if(task == null) {
         return;
      }

      boolean ownerChecked = task.getOwner() != null;

      if(ownerChecked) {
         checkReplaceOwnerOrganization(oldTaskId, taskId, task, principal);
      }

      checkScheduleTaskSave(taskId, task, principal, ownerChecked,
                            getStoredTask(oldTaskId, taskId, principal));
   }

   /**
    * Gets the stored task that a task saved under an id replaces, the task stored under the old
    * id, in the organization the task is saved in.
    */
   private ScheduleTask getStoredTask(String oldTaskId, String taskId, Principal principal) {
      if(oldTaskId == null || taskId == null) {
         return null;
      }

      String orgID = isInternalTask(taskId) ? Organization.getDefaultOrganizationID() :
         OrganizationManager.getInstance().getCurrentOrgID(principal);
      return orgID == null ? null : getScheduleTask(oldTaskId, orgID);
   }

   /**
    * Makes the refusals of a {@link #setScheduleTask(String, ScheduleTask, AssetEntry, Principal)}
    * save without saving: a task with an internal id is saved trusted and a data cycle task is
    * never saved as a schedule task, neither is refused.
    */
   private void checkScheduleTaskSave(String taskId, ScheduleTask task, Principal principal,
                                      boolean ownerChecked, ScheduleTask stored)
      throws Exception
   {
      if(task == null || isInternalTask(taskId) ||
         task.getType() == ScheduleTask.Type.CYCLE_TASK)
      {
         return;
      }

      String orgID = OrganizationManager.getInstance().getCurrentOrgID(principal);
      Principal savePrincipal = getSavePrincipal(principal);
      IdentityID newOwner = task.getOwner() == null ?
         getNewTaskOwner(savePrincipal, orgID, false) : null;
      checkSaveRefusals(taskId, task, newOwner, orgID, savePrincipal, ownerChecked, stored);
   }

   /**
    * Checks, before a stored task is removed to save a task in its place (e.g. to rename it),
    * that the task will not be refused because its owner is in another organization than the
    * one it's stored in.
    *
    * @param oldTaskId the id of the stored task, which is removed.
    * @param taskId    the id the task is saved under.
    *
    * @throws IOException if the task would be refused.
    */
   public synchronized void checkReplaceOwnerOrganization(String oldTaskId, String taskId,
                                                          ScheduleTask task, Principal principal)
      throws IOException
   {
      if(task == null || taskId == null) {
         return;
      }

      boolean internal = isInternalTask(taskId);
      String orgID = internal ? Organization.getDefaultOrganizationID() :
         OrganizationManager.getInstance().getCurrentOrgID(principal);
      ScheduleTask stored = oldTaskId == null || orgID == null ? null :
         getScheduleTask(oldTaskId, orgID);
      checkOwnerOrganization(taskId, task, stored, orgID, internal);
   }

   /**
    * Saves a schedule task, that was read from an organization, back to that organization, e.g.
    * a task changed by the task balancer, which balances the tasks of all organizations. Like an
    * internal save, the scheduler permission and the owner organization aren't checked and the
    * owner isn't granted permissions, the task is saved as it's stored. An internal task is
    * saved in the host organization.
    *
    * @param orgID the organization the task was read from.
    */
   synchronized void setScheduleTask(String taskId, ScheduleTask task, AssetEntry parent,
                                     String orgID, Principal principal)
      throws Exception
   {
      if(isInternalTask(taskId)) {
         setScheduleTask(taskId, task, parent, true, principal);
      }
      else {
         setScheduleTask(taskId, task, parent, orgID, false, true, principal, false, null);
      }
   }

   private synchronized void setScheduleTask(String taskId, ScheduleTask task, AssetEntry parent,
                                             boolean internal, Principal principal,
                                             boolean ownerChecked)
      throws Exception
   {
      if(task == null) {
         return;
      }

      // Bug #77972, the task stored under the id, read before the task is put in its place
      ScheduleTask stored = internal ? null :
         getScheduleTask(taskId, getSaveOrgID(false, principal));
      setScheduleTask(taskId, task, parent, internal, principal, ownerChecked, stored);
   }

   /**
    * @param stored the task as it's stored before this save, the batch action targets are
    *               checked against its targets.
    */
   private synchronized void setScheduleTask(String taskId, ScheduleTask task, AssetEntry parent,
                                             boolean internal, Principal principal,
                                             boolean ownerChecked, ScheduleTask stored)
      throws Exception
   {
      if(task == null) {
         return;
      }

      String orgID = getSaveOrgID(internal, principal);
      setScheduleTask(taskId, task, parent, orgID, internal, internal, principal, ownerChecked,
                      stored);
   }

   /**
    * Gets the organization a task is saved in.
    */
   private static String getSaveOrgID(boolean internal, Principal principal) {
      return internal ? Organization.getDefaultOrganizationID() :
         OrganizationManager.getInstance().getCurrentOrgID(principal);
   }

   /**
    * Saves a schedule task in an organization.
    *
    * @param internal if the task is saved as an internal task, the owner of an owner-less task
    *                 isn't moved to the organization.
    * @param trusted  if the scheduler permission and the owner organization aren't checked and
    *                 the owner isn't granted permissions.
    * @param stored   the task as it's stored before this save, for the checks of an untrusted save.
    */
   private synchronized void setScheduleTask(String taskId, ScheduleTask task, AssetEntry parent,
                                             String orgID, boolean internal, boolean trusted,
                                             Principal principal, boolean ownerChecked,
                                             ScheduleTask stored)
      throws Exception
   {
      if(task == null) {
         return;
      }

      task.setLastModified(System.currentTimeMillis());

      final RepletRepository engine = trusted ? null : SUtil.getRepletRepository();

      // Bug #77213: an extension (data cycle) task is derived from the extension's own asset.
      // Only its enabled state can change and that belongs to the extension, it must never be
      // stored as an ordinary task. Lock order: this monitor -> extensionLock (reload) ->
      // extension monitor.
      String extOrgId = getExtensionTaskOrgId(task, orgID);
      ScheduleExt ext = getTaskExtension(task, extOrgId);

      if(ext != null) {
         logIgnoredExtensionTaskChanges(task, extOrgId);

         if(updateExtensionEnabled(ext, task, extOrgId)) {
            reloadExtensions(extOrgId);
         }

         return;
      }

      if(isUnownedCycleTask(task)) {
         return;
      }

      principal = getSavePrincipal(principal);
      boolean ownerless = task.getOwner() == null;
      IdentityID newOwner = ownerless ? getNewTaskOwner(principal, orgID, internal) : null;

      // Bug #77863, every refusal of a save is made by checkSaveRefusals(), which callers that
      // remove or rewrite the stored task before saving also call first (checkScheduleTaskSave),
      // so the two can't disagree and a refused save never loses the stored task
      if(!trusted) {
         checkSaveRefusals(taskId, task, newOwner, orgID, principal, ownerChecked, stored);
      }

      if(ownerless) {
         task.setOwner(newOwner);
         taskId = task.getTaskId();
      }

      ScheduleTaskMessage.Action action;

      if(getOrgTaskMap(orgID).containsKey(getTaskIdentifier(taskId, orgID), orgID)) {
         action = ScheduleTaskMessage.Action.MODIFIED;
         dependencyHandler.updateTaskDependencies(getOrgTaskMap(orgID).get(getTaskIdentifier(taskId, orgID)), false);
      }
      else {
         action = ScheduleTaskMessage.Action.ADDED;
      }

      // Bug #77379, the parent folder entry may come from the client or be created in another
      // organization than the one the task is stored in (e.g. by a thread without a context
      // principal). The folder is written in the organization of its entry, so keep it in the
      // organization the task is stored in. The organization id of a default entry is lower case.
      if(parent != null && orgID != null && !orgID.equalsIgnoreCase(parent.getOrgID())) {
         parent = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                 AssetEntry.Type.SCHEDULE_TASK_FOLDER, parent.getPath(), null,
                                 orgID);
      }

      getOrgTaskMap(orgID).put(getTaskIdentifier(taskId, orgID), task, parent, orgID);
      dependencyHandler.updateTaskDependencies(task, true);
      scheduleClient.taskAdded(task);
      ScheduleTaskMessage message = new ScheduleTaskMessage();
      message.setTaskName(taskId);
      message.setTask(task);
      message.setAction(action);
      getCluster().sendMessage(message);

      IdentityID owner = task.getOwner();

      try {
         if(!trusted && Tool.equals(owner.orgID, OrganizationManager.getInstance().getCurrentOrgID())) {
            Permission perm = new Permission();
            String orgId = getTaskOrgID(taskId);
            Set<Permission.PermissionIdentity> users = Collections.singleton(new Permission.PermissionIdentity(owner.name, orgId));
            perm.setUserGrants(ResourceAction.READ, users);
            perm.setUserGrants(ResourceAction.WRITE, users);
            perm.setUserGrants(ResourceAction.DELETE, users);
            engine.setPermission(principal, ResourceType.SCHEDULE_TASK, task.getTaskId(), perm);
         }
      }
      catch(SRSecurityException e) {
         LOG.error("Failed to set permission on scheduled task " +
               task.getTaskId() + " for user " + owner.getName(), e);
      }
   }

   /**
    * Gets the principal a task is saved as, an internal anonymous principal for a null one.
    */
   private static Principal getSavePrincipal(Principal principal) {
      if(principal != null) {
         return principal;
      }

      String addr = null;

      try {
         addr = Tool.getIP();
      }
      catch(Exception e) {
         LOG.warn("Failed to get local IP address", e);
      }

      IdentityID clientID = new IdentityID(ClientInfo.ANONYMOUS, Organization.getDefaultOrganizationID());
      SRPrincipal internalPrincipal = new SRPrincipal(new ClientInfo(clientID, addr),
                                                      new IdentityID[0], new String[0], null, 1L);
      internalPrincipal.setProperty("__internal__", "true");
      return internalPrincipal;
   }

   /**
    * Gets the owner a task without an owner gets when it's saved by a principal.
    */
   private static IdentityID getNewTaskOwner(Principal principal, String orgID, boolean internal) {
      // @by mikec, here we shouldn't modify the task's owner.
      // For example if a task was defined by a user A, we should
      // not change it's owner to 'Admin' when admin changed the task
      // in Enterprise Manager.
      // The rule here should be, any role have permisstion on the task
      // (currently it is the admin) should be able to change the task,
      // but the owner should not be changed.
      IdentityID user =
         SUtil.getOwnerForNewTask(IdentityID.getIdentityIDFromKey(principal.getName()));

      // Bug #77359, the owner organization is part of the task id, which is the scheduler
      // (quartz) key of the task in all organizations, keep it the organization the task is
      // stored in (e.g. a site admin working in another organization), the same as a
      // materialized view task (MVSupportService)
      if(!internal && user != null && user.orgID != null && orgID != null &&
         !user.orgID.equalsIgnoreCase(orgID))
      {
         user = SUtil.getOwnerForNewTask(user, orgID);
      }

      return user;
   }

   /**
    * Makes every refusal of a non-trusted {@link #setScheduleTask} save. Nothing is changed, so
    * it's also run before a stored task is removed or rewritten to save a task in its place
    * ({@link #checkScheduleTaskSave}). Bug #77863, a new refusal of a save must be added here,
    * never to setScheduleTask itself, or a caller that removes the stored task first loses it.
    * <p>
    * Bug #77972, the stored task is the one {@link #getScheduleTask} returns, the instance
    * cached by the task map. A caller must change a clone of it, never that instance, or a batch
    * action target it adds is already in the stored task and isn't checked as added.
    *
    * @param newOwner     the owner the task gets if it has no owner.
    * @param ownerChecked if the owner organization was already checked by the caller.
    * @param stored       the task as it's stored before the save, or {@code null} if none.
    */
   private void checkSaveRefusals(String taskId, ScheduleTask task, IdentityID newOwner,
                                  String orgID, Principal principal, boolean ownerChecked,
                                  ScheduleTask stored)
      throws Exception
   {
      IdentityID user = IdentityID.getIdentityIDFromKey(principal.getName());

      // if a task is not removable, it's an internally created task. the schedule permission
      // is used to control whether a user can create schedule tasks (from the gui). internal
      // tasks (such as MV ondemand) are created without user intervention, and the originating
      // task (such as creating MV) is already controlled by a permission, so we shouldn't
      // check the permission here again.
      if(task.isRemovable() && !SUtil.getRepletRepository().checkPermission(
         principal, ResourceType.SCHEDULER, "*", ResourceAction.ACCESS))
      {
         throw new IOException("User '" + user.getName() + "' doesn't have schedule permission.");
      }

      // Bug #77549, a batch action query in another organization is refused
      checkBatchQueryOrganization(task, orgID, principal);
      ScheduleTask checkedTask = task;
      String checkedTaskId = taskId;

      if(task.getOwner() == null) {
         // Bug #77309, the caller becomes the owner of the task, a task whose owner doesn't
         // exist and has the name of a site admin in another organization runs with elevated
         // roles (the org admin roles of its org since Bug #77452)
         if(getSecurityEngine().isSecurityEnabled() &&
            !OrganizationManager.getInstance().isSiteAdmin(principal) &&
            SUtil.getSameNameSiteAdmin(getSecurityEngine().getSecurityProvider(), newOwner) != null)
         {
            throw new inetsoft.sree.security.SecurityException(String.format(
               "Unauthorized creation of a task owned by \"%s\" by %s, the owner doesn't " +
               "exist and has the name of a site admin", newOwner, principal));
         }

         // the owner is checked as the task is saved, with its new owner, on a copy so a check
         // never changes the task
         checkedTask = task.clone();
         checkedTask.setOwner(newOwner);
         checkedTaskId = checkedTask.getTaskId();
      }

      if(!ownerChecked) {
         checkOwnerOrganization(checkedTaskId, checkedTask, orgID, false);
      }

      // Bug #77972, a batch action runs its target task as the target's owner, only a task
      // that the owner of this task and the saving principal may see can be targeted
      checkBatchActionTargets(task, checkedTask.getOwner(), stored, orgID, principal);

      // Bug #77530, every save path converges here (the EM/portal task editor via
      // ScheduleTaskService.saveTask() and the viewer's own "Schedule" dialog via
      // ScheduleDialogService.scheduleVS()), and both let a client-supplied ViewsheetAction
      // sheet identifier through with no check of its own. Reject one that doesn't belong to
      // the saving principal's organization here, the one place every save reaches, the same as
      // the scheduler-permission and owner-organization checks right above.
      checkActionOrgBoundary(task, orgID, principal);
   }

   /**
    * Bug #77972, refuses a batch action target task that the owner of the task, or the saving
    * principal, may not see ({@link #isBatchTargetPermitted}). Only a target that isn't held by a
    * batch action of the stored task is checked against the saving principal, so a stored task
    * whose target is no longer visible can still be changed, renamed or moved; the run-time check
    * in {@link BatchAction#run} still refuses to run it. Every target is checked against the owner
    * when the owner changes. A site admin saving principal isn't checked, the owner always is, so
    * a task isn't saved that would be refused at run time. A target that doesn't exist (e.g. not
    * imported yet) is left to the run-time check. Internal targets are checked by the Bug #77531
    * rule alone, and nothing is checked without security.
    */
   private void checkBatchActionTargets(ScheduleTask task, IdentityID owner, ScheduleTask stored,
                                        String orgID, Principal principal)
      throws inetsoft.sree.security.SecurityException
   {
      if(!getSecurityEngine().isSecurityEnabled()) {
         return;
      }

      Set<String> storedTargets = getBatchActionTargets(stored);
      boolean ownerChanged = stored == null || !Objects.equals(owner, stored.getOwner());
      boolean siteAdmin = OrganizationManager.getInstance().isSiteAdmin(principal);
      Principal ownerPrincipal = null;

      for(String targetId : getBatchActionTargets(task)) {
         boolean added = !storedTargets.contains(targetId);

         if(isInternalTask(targetId) || !added && !ownerChanged) {
            continue;
         }

         ScheduleTask target = getScheduleTask(targetId, orgID);

         if(target == null) {
            continue;
         }

         if(ownerPrincipal == null && owner != null) {
            ownerPrincipal = SUtil.getScheduleTaskOwnerPrincipal(owner, null, false);
         }

         if(!isBatchTargetPermitted(target, ownerPrincipal) ||
            added && !siteAdmin && !isBatchTargetPermitted(target, principal))
         {
            throw new inetsoft.sree.security.SecurityException(String.format(
               "Unauthorized access to resource \"%s\" by %s, the batch action target task " +
               "isn't accessible to the task owner %s or the user", targetId, principal, owner));
         }
      }
   }

   /**
    * Gets the target task ids of the batch actions of a task.
    */
   private static Set<String> getBatchActionTargets(ScheduleTask task) {
      Set<String> targets = new LinkedHashSet<>();

      for(int i = 0; task != null && i < task.getActionCount(); i++) {
         if(task.getAction(i) instanceof BatchAction batch &&
            !StringUtils.isEmpty(batch.getTaskId()))
         {
            targets.add(batch.getTaskId());
         }
      }

      return targets;
   }

   /**
    * Bug #77972, whether a principal may make a batch action run a target task: when it may see
    * the task, by the rule that lists the tasks a batch action may target
    * ({@link RepletEngine#hasTaskPermission}). Fails closed when the rule isn't available.
    *
    * @param target    the target task.
    * @param principal the principal, the owner of the task that holds the batch action or the
    *                  principal that saves it.
    */
   static boolean isBatchTargetPermitted(ScheduleTask target, Principal principal) {
      if(principal == null || target == null || target.getOwner() == null) {
         return false;
      }

      RepletEngine engine = SUtil.getRepletEngine(SUtil.getRepletRepository());

      if(engine == null) {
         LOG.warn("The schedule task permissions can't be checked, the batch action target " +
                  "task \"{}\" is refused", target.getTaskId());
         return false;
      }

      return engine.hasTaskPermission(target, principal);
   }

   /**
    * Bug #77530, rejects a client-supplied {@code ViewsheetAction} whose sheet identifier embeds
    * an organization other than the one the task is being saved into (the same {@code orgID}
    * this save already computed from {@code principal}, {@link OrganizationManager#getCurrentOrgID}
    * so this also respects a legitimate org-context switch), unless {@code principal} is a site
    * admin. Matches the equivalent check the enterprise public REST API already performs for its
    * own save path ({@code ScheduleApiService.checkActionOrgBoundary}/{@code
    * checkAssetOrgBoundary}), so the two save paths behave consistently.
    */
   private static void checkActionOrgBoundary(ScheduleTask task, String orgID, Principal principal)
      throws inetsoft.sree.security.SecurityException
   {
      if(OrganizationManager.getInstance().isSiteAdmin(principal)) {
         return;
      }

      for(int i = 0; i < task.getActionCount(); i++) {
         ScheduleAction action = task.getAction(i);

         if(action instanceof ViewsheetAction viewsheetAction) {
            checkViewsheetOrgBoundary(viewsheetAction, orgID, principal);
         }
      }
   }

   /**
    * The action's raw identifier string is read directly (never through {@link
    * AssetEntry#createAssetEntryForCurrentOrg}, which silently coerces the org to the caller's
    * own and would defeat this check).
    *
    * <p>The comparison is case-insensitive (Bug #77530 review round 1): an org id may be mixed
    * case (see {@code ScheduleTaskIdentityChecker.useCurrentOrgID}'s own Bug #77259 comment), and
    * a restricted (non-site-admin) task import re-cases a task's owner/execute-as identity back to
    * the importer's actual org but, unlike the owner, never re-cases a {@code ViewsheetAction}'s
    * already-embedded org (which {@code ScheduleTask.parseXML}'s own org rewrite leaves
    * lower-cased, via the no-arg, context-principal-based {@code
    * OrganizationManager.getCurrentOrgID()}). A case-sensitive comparison here would then wrongly
    * refuse a legitimate same-org import. Case-insensitive matches how every other org-id
    * comparison in this class already works (e.g. the Bug #77379 parent-folder-org check and
    * {@code checkOwnerOrganization}'s stored-owner-org check, both via {@code equalsIgnoreCase}),
    * and the existing runtime cross-org guard ({@code
    * AbstractAssetEngine.checkAssetPermission0}'s {@code equalsIgnoreCase} check).
    *
    * <p>Exempts a default-org entry when {@code SUtil.isDefaultVSGloballyVisible(principal)}
    * (Bug #77530 external review, PR #6100): with {@code security.exposeDefaultOrgToAll} (or its
    * per-org variant) enabled, the runtime load path ({@code
    * AbstractAssetEngine.checkAssetPermission0}'s matching early {@code return true}) and
    * {@code ViewsheetEngine.doSwitchToHostOrg} both intentionally let a non-site-admin principal
    * from a non-default org open a shared default-org viewsheet -- {@code RepletEngine}, {@code
    * ViewsheetSandbox} and {@code MVManager} all honor this. The viewer's own already-open entry
    * for such a viewsheet keeps the default org, so scheduling it (or picking it from the task
    * editor) legitimately produces exactly this org mismatch, which must not be refused.
    *
    * <p>The exemption is intentionally narrow -- it only fires when the stored entry's org
    * actually is the default org (via the same case-insensitive comparison as the main check),
    * never for any other, genuinely foreign org, so it can't be used to smuggle in a sheet from
    * an unrelated organization. It mirrors {@code checkAssetPermission0}'s own bypass exactly
    * (scope-agnostic, since that bypass itself runs before the {@code USER_SCOPE}/private-asset
    * check and so already covers a default-org private dashboard too, per the diagnosis's own
    * severity note) -- this save-time check is not meant to be stricter than the runtime check it
    * fronts for.
    */
   private static void checkViewsheetOrgBoundary(ViewsheetAction action, String orgID,
                                                  Principal principal)
      throws inetsoft.sree.security.SecurityException
   {
      String sheet = action.getViewsheet();

      if(sheet == null) {
         return;
      }

      AssetEntry entry = AssetEntry.createAssetEntry(sheet);

      // a legacy identifier with no embedded org (entry.getOrgID() == null) is not checked here
      // at all -- the stored value is left to the runtime READ check (AbstractAssetEngine.
      // getSheet/checkAssetPermission0) at load time, the same as it was before this fix.
      if(entry != null && entry.getOrgID() != null &&
         !Tool.equals(entry.getOrgID(), orgID, false) &&
         !(Tool.equals(entry.getOrgID(), Organization.getDefaultOrganizationID(), false) &&
           SUtil.isDefaultVSGloballyVisible(principal)))
      {
         throw new inetsoft.sree.security.SecurityException(String.format(
            "Unauthorized access to viewsheet \"%s\" by %s", sheet, principal));
      }
   }

   /**
    * Bug #77359, the task id embeds the owner and it's the scheduler (quartz) job key of the task
    * in all organizations, so a task whose owner is in another organization than the one it's
    * stored in can have the same id as a task of that organization, and running, stopping or
    * saving one of them affects the other. Refuses to store a task that way, unless the stored
    * task already is (a task saved before this check, e.g. to change its enabled state).
    */
   private void checkOwnerOrganization(String taskId, ScheduleTask task, String orgID,
                                       boolean internal)
      throws IOException
   {
      if(isOwnerOrganizationUnchecked(task, orgID, internal)) {
         return;
      }

      // the stored task, including one stored under its pre 13.1 owner-less id
      checkOwnerOrganization(taskId, task, getScheduleTask(taskId, orgID), orgID, internal);
   }

   /**
    * Checks the owner organization of a task against the task stored before it's saved.
    *
    * @param stored the task stored before the change, if any.
    */
   private void checkOwnerOrganization(String taskId, ScheduleTask task, ScheduleTask stored,
                                       String orgID, boolean internal)
      throws IOException
   {
      if(isOwnerOrganizationUnchecked(task, orgID, internal)) {
         return;
      }

      IdentityID owner = task.getOwner();
      IdentityID storedOwner = stored == null ? null : stored.getOwner();

      // the task and the id it's saved under must both be the stored task's, the id of a task
      // with an owner is its scheduler job key
      if(storedOwner == null || storedOwner.orgID == null ||
         storedOwner.orgID.equalsIgnoreCase(orgID) || !taskId.equals(stored.getTaskId()) ||
         !taskId.equals(task.getTaskId()))
      {
         throw new IOException(
            "Schedule task " + taskId + " is not saved in organization " + orgID +
            ", its owner " + owner.convertToKey() + " is in another organization.");
      }

      LOG.warn("Schedule task {} of organization {} is owned by {} of another organization, " +
               "it has the same scheduler job as a task of that organization with the same id",
               taskId, orgID, owner.convertToKey());
   }

   private static boolean isOwnerOrganizationUnchecked(ScheduleTask task, String orgID,
                                                       boolean internal)
   {
      IdentityID owner = task.getOwner();
      return internal || task.getType() == ScheduleTask.Type.INTERNAL_TASK || owner == null ||
         owner.orgID == null || orgID == null || owner.orgID.equalsIgnoreCase(orgID);
   }

   /**
    * Checks, before a stored task is removed or a task is saved in a batch (e.g. an import),
    * that the task will not be refused by {@link #setScheduleTask} because of the organization of
    * a batch action query, so a refused task neither loses the stored task nor aborts the batch.
    *
    * @throws inetsoft.sree.security.SecurityException if the task would be refused.
    */
   public void checkBatchQueryOrganization(String taskId, ScheduleTask task, Principal principal)
      throws inetsoft.sree.security.SecurityException
   {
      if(task == null || isInternalTask(taskId)) {
         return;
      }

      checkBatchQueryOrganization(
         task, OrganizationManager.getInstance().getCurrentOrgID(principal), principal);
   }

   /**
    * Bug #77549, the query entry of a batch action comes from the client with its organization
    * as sent (e.g. the task editor). Refuses a query in another organization than the one the
    * task is saved in unless the principal is a site admin, the same as the enterprise public
    * API (ScheduleApiService.checkActionOrgBoundary). The worksheet is read with the run
    * principal, which is refused another organization's worksheet when the task runs. The
    * organization id of an imported entry is lower case, so it's compared ignoring case.
    */
   private static void checkBatchQueryOrganization(ScheduleTask task, String orgID,
                                                   Principal principal)
      throws inetsoft.sree.security.SecurityException
   {
      if(orgID == null || OrganizationManager.getInstance().isSiteAdmin(principal)) {
         return;
      }

      for(int i = 0; i < task.getActionCount(); i++) {
         if(task.getAction(i) instanceof BatchAction batchAction) {
            AssetEntry query = batchAction.getQueryEntry();

            if(query != null && query.getOrgID() != null &&
               !query.getOrgID().equalsIgnoreCase(orgID))
            {
               throw new inetsoft.sree.security.SecurityException(String.format(
                  "Unauthorized access to query \"%s\" by %s", query.toIdentifier(), principal));
            }
         }
      }
   }

   /**
    * Logs the tasks of different organizations that have the same id. A task id is the scheduler
    * (quartz) job key of the task in all organizations, only one of them is scheduled.
    *
    * @return the ids of the tasks stored in more than one organization.
    */
   public Set<String> logDuplicateTaskIds() {
      Map<String, String> taskOrgs = new HashMap<>();
      Set<String> duplicates = new TreeSet<>();

      for(Map.Entry<String, ScheduleTaskMap> entry : new ArrayList<>(taskMap.entrySet())) {
         String orgID = entry.getKey();

         for(ScheduleTask task : entry.getValue().values()) {
            if(task == null || task.getType() == ScheduleTask.Type.INTERNAL_TASK) {
               continue;
            }

            String otherOrgID = taskOrgs.putIfAbsent(task.getTaskId(), orgID);

            if(otherOrgID != null && !otherOrgID.equals(orgID)) {
               duplicates.add(task.getTaskId());
               LOG.warn("Schedule task {} is stored in organizations {} and {}, it's one " +
                        "scheduler job and only one of the tasks is scheduled. Its owner " +
                        "should be in the organization it's stored in.",
                        task.getTaskId(), otherOrgID, orgID);
            }
         }
      }

      return duplicates;
   }

   /**
    * Remove a schedule task.
    */
   public synchronized void removeScheduleTask(String taskName,
                                               Principal principal,
                                               boolean checkDependency)
      throws Exception
   {
      if(!checkDependency) {
         removeScheduleTask(taskName, principal);
      }
      else {
         Vector<ScheduleTask> allTasks = getScheduleTasks(principal);
         boolean dependency = hasDependency(allTasks, taskName);

         if(!dependency) {
            removeScheduleTask(taskName, principal);
         }
         else {
            if(taskName == null) {
               throw new Exception(Catalog.getCatalog().getString(
                  "designer.property.emptyNullError"));
            }
            else {
               throw new Exception(Catalog.getCatalog().getString(
                  "em.schedule.task.removeDependency", taskName));
            }
         }
      }
   }

   /**
    * Remove a schedule task.
    */
   public synchronized void removeScheduleTask(String taskName,
                                               Principal principal)
      throws Exception
   {
      String orgID = getTaskOrgID(taskName);
      RepletRepository engine = SUtil.getRepletRepository();

      // Bug #77284: the org of the task is taken from the owner key prefix of the id, which may
      // come from the client. The quartz job key is global, so an id that names another
      // organization would unschedule that organization's task. Only trusted callers may act on
      // a task outside their current organization.
      if(isOtherOrgTaskId(taskName, orgID, principal) && !isCrossOrgRemoveAllowed(principal)) {
         throw new IOException(principal.getName() +
                               " doesn't have delete permission for: " + taskName);
      }

      ScheduleTask task = getScheduleTask(taskName, orgID);

      //possible site admin created in other organization
      if(task == null) {
         task = getScheduleTask(taskName, OrganizationManager.getInstance().getCurrentOrgID(principal));
      }

      // for dynamically created tasks (e.g. mv background creation),
      // the task may not be in schedule manager so we should not
      // throw an exception and can just silently remove it.
      if(task != null) {
         if(!task.isRemovable()) {
            throw new IOException("Task is not removable: " + task.getName());
         }

         boolean isSiteAdminInOtherOrg = isSiteAdminOtherOrg(task.getOwner(), principal);

         boolean adminPermission = getSecurityEngine().checkPermission(
            principal, ResourceType.SECURITY_USER, task.getOwner(), ResourceAction.ADMIN);

         if(!isSiteAdminInOtherOrg && !engine.checkPermission(
            principal, ResourceType.SCHEDULE_TASK, taskName, ResourceAction.DELETE) &&
            principal != null && !Tool.equals(principal.getName(), task.getOwner()) &&
            isDeleteOnlyByOwner(task, principal) && !adminPermission)
         {
            throw new IOException(principal.getName() +
                                  " doesn't have delete permission for: " +
                                  taskName);
         }
      }

      // check if is an ext task
      boolean ext = false;
      Vector<ScheduleExt> extensions = getExtensions();
      boolean extChanged = false;
      orgID = OrganizationManager.getInstance().getCurrentOrgID(principal);

      for(ScheduleExt extension : extensions) {
         if(extension.containsTask(taskName, orgID)) {
            extChanged = extension.deleteTask(taskName);
            ext = true;
            break;
         }
      }

      // act on the id of the resolved task, the raw name may have been matched by the legacy
      // fallback in getScheduleTask() and name a different quartz job
      String taskId = task != null ? task.getTaskId() : taskName;

      // not an ext task? check if a normal task
      if(!ext) {
         if(task != null) {
            String identifier = getTaskIdentifier(task.getTaskId(), orgID);
            String requested = getTaskIdentifier(taskName, orgID);
            getOrgTaskMap(orgID).remove(identifier);

            // Bug #77883, a task stored under the requested id that loads with another id (e.g.
            // its name was changed when it was loaded) is removed by the key it's stored under
            if(!requested.equals(identifier)) {
               ScheduleTask stored = getOrgTaskMap(orgID).get(requested);

               if(stored != null && Tool.equals(stored.getTaskId(), task.getTaskId())) {
                  getOrgTaskMap(orgID).remove(requested);
               }
            }

            dependencyHandler.updateTaskDependencies(task, false);
         }
         else {
            getOrgTaskMap(orgID).remove(getTaskIdentifier(taskName, orgID));
         }

         scheduleClient.taskRemoved(taskId);
         ScheduleTaskMessage message = new ScheduleTaskMessage();
         message.setTaskName(taskId);
         // Bug #74338: include the task in the REMOVED message so that
         // shouldHandleReceivedMessage() can fall back to task.getOwner() when
         // getTaskOwner(taskId) returns null (e.g. for MV tasks whose IDs lack the
         // owner key prefix), allowing the message to reach the correct org's subscribers.
         message.setTask(task);
         message.setAction(ScheduleTaskMessage.Action.REMOVED);
         getCluster().sendMessage(message);
      }

      try {
         engine.setPermission(principal, ResourceType.SCHEDULE_TASK, taskId, null);
      }
      catch(Exception ex) {
         LOG.error("Failed to clear permissions for schedule task {}", taskId, ex);
      }

      if(extChanged) {
         reloadExtensions(orgID);
      }
   }

   /**
    * Whether the task id carries an owner key prefix with an explicit organization that is not
    * the current organization of the caller.
    */
   private boolean isOtherOrgTaskId(String taskId, String taskOrgID, Principal principal) {
      if(taskId == null || taskOrgID == null) {
         return false;
      }

      int index = taskId.indexOf(':');

      // an owner key without an organization is resolved in the caller's organization
      if(index < 0 || !taskId.substring(0, index).contains(IdentityID.KEY_DELIMITER)) {
         return false;
      }

      String callerOrgID = OrganizationManager.getInstance().getCurrentOrgID(principal);
      return !taskOrgID.equalsIgnoreCase(callerOrgID);
   }

   /**
    * Whether the caller may remove a task of another organization: callers without a user
    * (scheduler cleanup), the virtual group/role execute-as principals built by the scheduler,
    * the system principal and site administrators.
    */
   private boolean isCrossOrgRemoveAllowed(Principal principal) {
      // Bug #77284: do not exempt SUtil.isInternalUser() principals, SecurityEngine.authenticate()
      // marks every login principal as __internal__
      if(principal == null ||
         principal instanceof XPrincipal &&
         "true".equals(((XPrincipal) principal).getProperty("virtual")))
      {
         return true;
      }

      IdentityID callerID = IdentityID.getIdentityIDFromKey(principal.getName());

      if(callerID != null && XPrincipal.SYSTEM.equals(callerID.name) &&
         getSecurityEngine().getSecurityProvider().getUser(callerID) == null)
      {
         return true;
      }

      return OrganizationManager.getInstance().isSiteAdmin(principal);
   }

   //return true if user does not actually exist, a site admin of the same name exists and the
   //caller is that site admin (e.g. site admin created the task while in another organization)
   //or the owner identity itself
   private boolean isSiteAdminOtherOrg(IdentityID principalID, Principal caller) {
      if(getSecurityEngine().isSecurityEnabled() &&
         getSecurityEngine().getSecurityProvider().getUser(principalID) == null)
      {
         // Bug #77284: the bypass must depend on the caller, not only on the task owner,
         // otherwise any user could delete a task owned by a site admin in another organization.
         // A caller whose full identity (name and organization) is the owner itself is accepted:
         // the owner is not a user, such a principal is built by the server from the stored
         // owner (the import overwrite in ScheduleTaskAsset uses new SRPrincipal(owner), which
         // carries no site admin roles)
         IdentityID callerID = caller == null ? null :
            IdentityID.getIdentityIDFromKey(caller.getName());

         if(callerID == null || !Tool.equals(principalID.name, callerID.name) ||
            (!principalID.equals(callerID) &&
             !OrganizationManager.getInstance().isSiteAdmin(caller)))
         {
            return false;
         }

         for(IdentityID user : getSecurityEngine().getUsers()) {
            if(Tool.equals(principalID.name,user.name) && OrganizationManager.getInstance().isSiteAdmin(user)) {
               return true;
            }
         }
      }

      return false;
   }

   private String getTaskOrgID(String taskId) {
      int index = taskId.indexOf(':');

      if(index == -1) {
         return null;
      }

      SecurityProvider provider = getSecurityEngine().getSecurityProvider();

      IdentityID taskUser = IdentityID.getIdentityIDFromKey(taskId.substring(0,index));

      User user = provider.getUser(taskUser);

      if(user == null) {
         return taskUser.getOrgID() != null ? taskUser.getOrgID() :
                  OrganizationManager.getInstance().getCurrentOrgID();
      }

      return user.getOrganizationID();
   }

   /**
    * Whether the task just delete by owner.
    * @param task
    * @param principal
    * @return
    */
   public boolean isDeleteOnlyByOwner(ScheduleTask task, Principal principal) {
      if(!isShareInGroup()) {
         return true;
      }

      if(isDeleteByOwner()) {
         return true;
      }
      else {
         return !hasShareGroupPermission(task, principal);
      }
   }

   /**
    * Whether share task in group.
    * @return
    */
   public static boolean isShareInGroup() {
      return SreeEnv.getBooleanProperty("schedule.options.shareTaskInGroup", "true", "CHECKED");
   }

   /**
    * Whether only delete task by owner.
    * @return
    */
   public static boolean isDeleteByOwner() {
      return SreeEnv.getBooleanProperty("schedule.options.deleteTaskOnlyByOwner", "true", "CHECKED");
   }

   public static boolean hasShareGroupPermission(ScheduleTask task, Principal principal) {
      return hasShareGroupPermission(task.getOwner(), principal);
   }

   public static boolean hasShareGroupPermission(IdentityID owner, Principal principal) {
      if(!SecurityEngine.getSecurity().isSecurityEnabled()) {
         return false;
      }

      if(isShareInGroup()) {
         return isSameGroup(owner, IdentityID.getIdentityIDFromKey(principal.getName()));
      }

      return false;
   }

   public static boolean isSameGroup(IdentityID owner, IdentityID user) {
      if(!SecurityEngine.getSecurity().isSecurityEnabled()) {
         return false;
      }

      SecurityProvider securityProvider = SecurityEngine.getSecurity().getSecurityProvider();

      if(securityProvider == null) {
         return false;
      }

      String[] taskOwnerGroups = securityProvider.getUserGroups(owner);
      String[] userGroups = securityProvider.getUserGroups(user);

      if(taskOwnerGroups == null || userGroups == null) {
         return false;
      }

      for(String taskOwnerGroup : taskOwnerGroups) {
         if(ArrayUtils.contains(userGroups, taskOwnerGroup)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check whether user has the specific owner task permission.
    * @param taskOwner task owner.
    * @param principal user.
    * @param action resource action
    * @return
    */
   public static boolean hasTaskPermission(IdentityID taskOwner, Principal principal, ResourceAction action){
      SecurityProvider securityProvider = SecurityEngine.getSecurity().getSecurityProvider();
      boolean adminPermission = securityProvider.checkPermission(
         principal, ResourceType.SECURITY_USER, taskOwner.convertToKey(), ResourceAction.ADMIN);

      if(taskOwner.equals(IdentityID.getIdentityIDFromKey(principal.getName())) || adminPermission) {
         return true;
      }

      if(!isShareInGroup()) {
         return false;
      }

      if(ResourceAction.DELETE.equals(action) && isDeleteByOwner()) {
         return false;
      }

      return hasShareGroupPermission(taskOwner, principal);
   }

   /**
    * Check task dependency for rename &amp; delete action.
    */
   public boolean hasDependency(Vector<ScheduleTask> allTasks, String taskId) {
      boolean dependency = false;

      for(Iterator<ScheduleTask> i = allTasks.iterator(); i.hasNext();) {
         ScheduleTask task = i.next();
         String id = task.getTaskId();

         if(id.equals(taskId)) {
            i.remove();
            break;
         }
      }

      for(ScheduleTask task : allTasks) {
         String id = task.getTaskId();
         task = getScheduleTask(id);
         Enumeration<String> en = task.getDependency();

         while(en.hasMoreElements()) {
            if(taskId.equals(en.nextElement())) {
               dependency = true;
               break;
            }
         }

         if(dependency) {
            break;
         }
      }

      return dependency;
   }

   public List<AssetObject> getDependentTasks(String target, String orgId) {
      String taskName = AssetEntry.createAssetEntry(target).getName();
      return getOrgTaskMap(orgId).entrySet(orgId).stream()
         .filter(entry -> entry.getValue() != null)
         .filter(entry -> isDependent(taskName, entry.getValue()))
         .map(Map.Entry::getKey)
         .map(identifier -> (AssetObject) AssetEntry.createAssetEntry(identifier))
         .toList();
   }

   private boolean isDependent(String target, ScheduleTask task) {
      Enumeration<String> en = task.getDependency();

      while(en.hasMoreElements()) {
         if(target.equals(en.nextElement())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Method will be invoked when a user is removed.
    */
   public synchronized void identityRemoved(Identity identity, EditableAuthenticationProvider eprovider) {
      IdentityID identityID = identity.getIdentityID();
      String orgID;

      switch(identity.getType()) {
         case Identity.USER:
            orgID = eprovider.getUser(identityID).getOrganizationID();
            break;
         case Identity.GROUP:
            orgID = eprovider.getGroup(identityID).getOrganizationID();
            break;
         case Identity.ORGANIZATION:
            orgID = identityID.orgID;
            break;
         default:
            orgID = Organization.getDefaultOrganizationID();
            break;
      }

      identityRemoved(identity, orgID);
   }

   /**
    * Method will be invoked when a user is removed. Unlike
    * {@link #identityRemoved(Identity, EditableAuthenticationProvider)} it does not look the
    * identity up in the provider, so it can run after the identity has been removed.
    *
    * @param orgID the organization of the removed user or group, read before it was removed.
    *              Not used for a role.
    */
   public synchronized void identityRemoved(Identity identity, String orgID) {
      int type = identity.getType();
      IdentityID identityID = identity.getIdentityID();

      if(type == Identity.ROLE) {
         roleRemoved(identityID);

         for(ScheduleExt ext : extensions) {
            ext.identityRemoved(identity);
         }

         return;
      }

      Set<ScheduleTask> changedTasks = new HashSet<>();

      Iterator<ScheduleTask> i = getOrgTaskMap(orgID).values().iterator();

      while(i.hasNext()) {
         ScheduleTask task = i.next();

         if(task == null) {
            continue;
         }

         if(type == Identity.USER && identityID.equals(task.getOwner())) {
            i.remove();
            continue;
         }

         Identity iden = task.getIdentity();

         if(iden != null && type == iden.getType() && identityID.equals(iden.getIdentityID()) &&
            canResetToOwner(task))
         {
            task.setIdentity(null);
            changedTasks.add(task);
         }

         for(int j = 0; j < task.getActionCount(); j++) {
            ScheduleAction action = task.getAction(j);
            updateNotifications(action, identityID.name, null, type, task, changedTasks);
         }
      }

      try {
         save(changedTasks, orgID);
      }
      catch(Exception ex) {
         LOG.error("Failed to save schedule task file after " +
               "identity was removed: " + identityID, ex);
      }

      for(ScheduleExt ext : extensions) {
         ext.identityRemoved(identity);
      }
   }

   /**
    * Clears the "execute as" of tasks that run as the removed role. An org role is only
    * referenced by tasks in its own org; a global role (null org) may be referenced in any org.
    * Notifications are left alone: a role is never a notification recipient, and a bare token
    * with the role's name denotes a user of that name.
    */
   private void roleRemoved(IdentityID identityID) {
      String[] orgIDs = identityID.orgID != null ?
         new String[] { identityID.orgID } : getSecurityEngine().getOrganizations();

      for(String orgID : orgIDs) {
         if(orgID == null) {
            continue;
         }

         Set<ScheduleTask> changedTasks = new HashSet<>();

         for(ScheduleTask task : getOrgTaskMap(orgID).values()) {
            if(task == null) {
               continue;
            }

            Identity iden = task.getIdentity();

            if(iden != null && iden.getType() == Identity.ROLE &&
               identityID.equals(iden.getIdentityID()) && canResetToOwner(task))
            {
               task.setIdentity(null);
               changedTasks.add(task);
            }
         }

         try {
            save(changedTasks, orgID);
         }
         catch(Exception ex) {
            LOG.error("Failed to save schedule task file after " +
                  "identity was removed: " + identityID, ex);
         }
      }
   }

   /**
    * Bug #77332, whether the "execute as" of a task may be cleared when its identity is removed,
    * so that the task runs as its owner. An owner that is not a user and has the name of a site
    * admin runs with elevated roles, the org admin roles of its org since Bug #77452
    * ({@link SUtil#getScheduleTaskOwnerPrincipal}), so for such a
    * task the removed identity is kept instead: it no longer resolves and the task refuses to
    * run until a new "execute as" is selected. The owner is looked up in the whole security
    * provider chain, not only in the provider the identity is removed from.
    */
   private boolean canResetToOwner(ScheduleTask task) {
      IdentityID owner = task.getOwner();

      // same exclusions as the site admin fallback in getScheduleTaskOwnerPrincipal, internal
      // tasks run as the system user and must keep running
      if(owner == null || owner.orgID == null || XPrincipal.ANONYMOUS.equals(owner.name) ||
         XPrincipal.SYSTEM.equals(owner.name) || isInternalTask(task.getTaskId()))
      {
         return true;
      }

      try {
         return !getSecurityEngine().isSecurityEnabled() ||
            getSecurityEngine().getSecurityProvider().getUser(owner) != null;
      }
      catch(Exception ex) {
         LOG.warn("Failed to check the owner {} of schedule task {}, keeping its execute-as",
                  owner, task.getTaskId(), ex);
         return false;
      }
   }

   /**
    * Compute, without modifying anything, which scheduled tasks would be affected if the
    * given identity were removed: tasks owned by a user (which
    * {@link #identityRemoved(Identity, EditableAuthenticationProvider)} deletes) and tasks where
    * the identity is the "execute as" (which it resets, or keeps when the task owner is not a
    * user so that the task refuses to run). The recipient cleanup that
    * identityRemoved also performs (removing the identity's tokens from the notification and the
    * delivery to, cc and bcc lists) is not reported here.
    */
   public synchronized IdentityTaskImpact getIdentityRemovalImpact(Identity identity,
                                                                   EditableAuthenticationProvider eprovider)
   {
      List<String> ownedTasks = new ArrayList<>();
      List<String> executeAsTasks = new ArrayList<>();
      List<String> refusedTasks = new ArrayList<>();
      int type = identity.getType();
      IdentityID identityID = identity.getIdentityID();
      // the identity carries its own org; prefer it so a site/host admin deleting a user in a
      // different org (e.g. the self org) still resolves that org's task map
      String orgID = identityID == null ? null : identityID.orgID;

      if(orgID == null) {
         switch(type) {
            case Identity.USER:
               User user = eprovider.getUser(identityID);
               orgID = user == null ? null : user.getOrganizationID();
               break;
            case Identity.GROUP:
               Group group = eprovider.getGroup(identityID);
               orgID = group == null ? null : group.getOrganizationID();
               break;
            default:
               orgID = Organization.getDefaultOrganizationID();
               break;
         }
      }

      if(orgID == null) {
         return new IdentityTaskImpact(ownedTasks, executeAsTasks, refusedTasks);
      }

      for(ScheduleTask task : getOrgTaskMap(orgID).values()) {
         if(task == null) {
            continue;
         }

         if(type == Identity.USER && identityID.equals(task.getOwner())) {
            ownedTasks.add(task.getName());
            continue;
         }

         Identity iden = task.getIdentity();

         if(iden != null && type == iden.getType() && identityID.equals(iden.getIdentityID())) {
            (canResetToOwner(task) ? executeAsTasks : refusedTasks).add(task.getName());
         }
      }

      return new IdentityTaskImpact(ownedTasks, executeAsTasks, refusedTasks);
   }

   /**
    * The scheduled tasks affected by removing an identity: tasks the identity owns
    * (which are deleted), tasks where the identity is the "execute as" (which is reset), and
    * tasks where the identity is the "execute as" of a task whose owner is not a user (which is
    * kept, so the task refuses to run).
    */
   public record IdentityTaskImpact(List<String> ownedTasks, List<String> executeAsTasks,
                                    List<String> refusedTasks)
   {
   }

   /**
    * Method will be invoked when a user is renamed.
    */
   public synchronized void identityRenamed(IdentityID oname, Identity identity) {
      Set<ScheduleTask> changedTasks = new HashSet<>();
      List<String> removedTaskIds = new ArrayList<>();
      int type = identity.getType();
      String name = identity.getName();
      IdentityID id = identity.getIdentityID();
      SecurityProvider securityProvider = getSecurityEngine().getSecurityProvider();
      String orgID = identity instanceof FSOrganization ? oname.orgID :
               identity.getOrganizationID() == null ? null : identity.getOrganizationID();

      for(ScheduleTask task : getOrgTaskMap(orgID).values()) {
         if(task == null) {
            continue;
         }

         if(type == Identity.USER && oname.equals(task.getOwner())) {
            String oldTaskId = task.getTaskId();
            this.getOrgTaskMap(orgID).remove(getTaskIdentifier(oldTaskId, orgID));
            task.setOwner(id);
            changedTasks.add(task);
            removedTaskIds.add(oldTaskId);
         }

         Identity iden = task.getIdentity();

         if(iden != null && type == iden.getType() && oname.equals(iden.getIdentityID())) {
            this.getOrgTaskMap(orgID).remove(getTaskIdentifier(task.getTaskId(), orgID));
            task.setIdentity(identity);
            changedTasks.add(task);
         }

         // the task ids in the completion conditions, the dependencies and the batch actions,
         // the private viewsheets and the bookmarks are owned by a user, so a group rename must
         // not change the ones of a same-named user
         if(type == Identity.USER) {
            //completion condition relies on user name, change if user changes
            for(int c = 0; c < task.getConditionCount(); c++) {
               ScheduleCondition condition = task.getCondition(c);

               if(condition instanceof CompletionCondition) {
                  CompletionCondition completeCondition = (CompletionCondition) condition;
                  String taskName = completeCondition.getTaskName();
                  int colonIdx = taskName == null ? -1 : taskName.indexOf(":");

                  if(colonIdx < 0) {
                     continue;
                  }

                  String userName = taskName.substring(0, colonIdx);

                  if(Tool.equals(userName, oname.getName()) ||
                     Tool.equals(IdentityID.getIdentityIDFromKey(userName).name, oname.getName()))
                  {
                     completeCondition.setTaskName(taskName.replace(oname.getName(), name));
                     changedTasks.add(task);
                  }
               }
            }

            Enumeration<String> taskDependencies = task.getDependency();

            while(taskDependencies.hasMoreElements()) {
               String taskDep = taskDependencies.nextElement();
               int colonIdx = taskDep == null ? -1 : taskDep.indexOf(":");

               if(colonIdx < 0) {
                  continue;
               }

               String userName = taskDep.substring(0, colonIdx);

               if(Tool.equals(userName, oname.getName()) ||
                  Tool.equals(IdentityID.getIdentityIDFromKey(userName).name, oname.getName()))
               {
                  task.renameDependency(taskDep, taskDep.replace(oname.getName(), name));
                  changedTasks.add(task);
               }
            }
         }

         for(int j = 0; j < task.getActionCount(); j++) {
            ScheduleAction action = task.getAction(j);
            updateNotifications(action, oname.name, name, type, task, changedTasks);

            if(type == Identity.USER) {
               updateScheduleAction(action, oname, id, task, changedTasks);
            }
         }
      }

      try {
         save(changedTasks, orgID);
      }
      catch(Exception ex) {
         LOG.error("Failed to save schedule task file after " +
               "identity was renamed: " + oname + " to " + name, ex);
      }

      // Notify subscribers that the old task IDs have been removed (owner rename changes task ID).
      // The save() above sends ADDED for new task IDs; we must also send REMOVED for old ones so
      // that portal UI removes stale entries without requiring a page refresh.
      for(String oldTaskId : removedTaskIds) {
         try {
            scheduleClient.taskRemoved(oldTaskId);
         }
         catch(Exception e) {
            LOG.error("Failed to notify scheduler of removed task after user rename: {}", oldTaskId, e);
         }

         try {
            ScheduleTaskMessage removeMessage = new ScheduleTaskMessage();
            removeMessage.setTaskName(oldTaskId);
            removeMessage.setAction(ScheduleTaskMessage.Action.REMOVED);
            getCluster().sendMessage(removeMessage);
         }
         catch(Exception e) {
            LOG.error("Failed to send task removed message after user rename: {}", oldTaskId, e);
         }
      }

      for(ScheduleExt ext : extensions) {
         ext.identityRenamed(oname.name, identity);
      }
   }

   public synchronized void removeTaskCacheOfOrg(String orgID) {
      // clear cache
      this.getOrgTaskMap(orgID).clearCache();
      this.taskMap.remove(orgID);
      extensionLock.lock();

      try {
         removeExtensionTasksOfOrg(orgID);
      }
      finally {
         extensionLock.unlock();
      }
   }

   /**
    * Callers must hold extensionLock. Must not be synchronized, see the lock ordering note
    * on extensionLock.
    */
   private void removeExtensionTasksOfOrg(String orgID) {
      for(ScheduleExt ext : extensions) {
         if(!(ext instanceof DataCycleManager)) {
            continue;
         }

         DataCycleManager cycleManager = (DataCycleManager) ext;
         cycleManager.clearOrgTasks(orgID);
      }

      Iterator<ExtTaskKey> it = extensionTasks.keySet().iterator();

      while(it.hasNext()) {
         ExtTaskKey key = it.next();

         if(Tool.equals(key.orgId, orgID)) {
            extensionTasks.remove(key);
         }
      }

      extensionTaskOwners.keySet().removeIf(key -> Tool.equals(key.orgId, orgID));
   }

   private void updateScheduleAction(ScheduleAction action,
                                     IdentityID oid, IdentityID id,
                                     ScheduleTask task,
                                     Set<ScheduleTask> changedTasks)
   {
      if(action instanceof ViewsheetAction) {
         updateViewsheets(action, oid, id, task, changedTasks);
      }
      else if(action instanceof BatchAction) {
         BatchAction batchAction = (BatchAction) action;
         String taskId = batchAction.getTaskId();
         ScheduleTaskMetaData taskMetaData = ScheduleManager.getTaskMetaData(taskId);

         if(taskMetaData.getTaskOwnerId().equals(oid.convertToKey()) &&
            !taskMetaData.getTaskOwnerId().equals(id.convertToKey()))
         {
            batchAction.setTaskId(id.convertToKey() + ":" + taskMetaData.getTaskName());
         }
      }
   }


   /**
    * Removes (nname is null) or renames the recipients that denote the identity in the
    * notification list and in the delivery (to, cc and bcc) lists of the action. A list is only
    * set back, and the task only marked as changed, when a token of it denotes the identity.
    */
   private void updateNotifications(ScheduleAction action, String oname, String nname, int type,
                                    ScheduleTask task, Set<ScheduleTask> changedTasks)
   {
      if(action instanceof AbstractAction) {
         AbstractAction aaction = (AbstractAction) action;
         String notifies = updateNotifications(aaction.getNotifications(), oname, nname, type);

         if(notifies != null) {
            aaction.setNotifications(notifies);
            changedTasks.add(task);
         }

         String emails = updateNotifications(aaction.getEmails(), oname, nname, type);

         if(emails != null) {
            aaction.setEmails(emails);
            changedTasks.add(task);
         }

         String ccAddresses = updateNotifications(aaction.getCCAddresses(), oname, nname, type);

         if(ccAddresses != null) {
            aaction.setCCAddresses(ccAddresses);
            changedTasks.add(task);
         }

         String bccAddresses = updateNotifications(aaction.getBCCAddresses(), oname, nname, type);

         if(bccAddresses != null) {
            aaction.setBCCAddresses(bccAddresses);
            changedTasks.add(task);
         }
      }
   }

   /**
    * Removes (nname is null) or renames the recipients that denote the identity in a recipient
    * list, which is a notification list or a delivery (to, cc or bcc) list. A bare name that is
    * not an email address denotes a user, name(User) a user and name(Group) a group, so a user
    * matches a bare or a (User) token and a group matches only a (Group) token. A renamed token
    * keeps its form. The other tokens, the delimiters and the spacing are kept.
    * <p>
    * This is the shared helper for any stored recipient list (it is also used for the data
    * cycle notification recipients), not to be confused with the private
    * {@code updateNotifications(ScheduleAction, ...)} overload, which applies it to the lists
    * of a schedule action.
    *
    * @param notifies the recipient list, tokens separated by ',' or ';'.
    * @param oname    the old (or removed) name of the identity.
    * @param nname    the new name of the identity, or null to remove its tokens.
    * @param type     the identity type, {@link Identity#USER} or {@link Identity#GROUP}.
    *
    * @return the new list, or null if the list is null or empty or no token denotes the
    *         identity.
    */
   public static String updateNotifications(String notifies, String oname, String nname,
                                            int type)
   {
      if(notifies == null || notifies.isEmpty()) {
         return null;
      }

      List<String> tokens = new ArrayList<>();
      List<String> delimiters = new ArrayList<>();
      Matcher matcher = NOTIFICATION_DELIMITER.matcher(notifies);
      int start = 0;

      while(matcher.find()) {
         tokens.add(notifies.substring(start, matcher.start()));
         delimiters.add(matcher.group());
         start = matcher.end();
      }

      tokens.add(notifies.substring(start));
      boolean matched = false;

      for(int i = 0; i < tokens.size(); i++) {
         String token = tokens.get(i);
         String suffix = getNotificationSuffix(StringUtils.normalizeSpace(token), oname, type);

         if(suffix == null) {
            continue;
         }

         matched = true;

         if(nname == null) {
            tokens.set(i, null);
         }
         else {
            // a bare email address is not a user, so a user renamed to one is written typed
            if(suffix.isEmpty() && Tool.matchEmail(nname)) {
               suffix = Identity.USER_SUFFIX;
            }

            String trimmed = token.trim();
            int lead = token.indexOf(trimmed);
            tokens.set(i, token.substring(0, lead) + nname + suffix +
               token.substring(lead + trimmed.length()));
         }
      }

      if(!matched) {
         return null;
      }

      StringBuilder result = new StringBuilder();
      boolean first = true;

      for(int i = 0; i < tokens.size(); i++) {
         if(tokens.get(i) == null) {
            continue;
         }

         if(!first) {
            result.append(delimiters.get(i - 1));
         }

         result.append(tokens.get(i));
         first = false;
      }

      return result.toString();
   }

   /**
    * Gets the suffix of the notification token if it denotes the identity: an empty string for a
    * bare user name, the user or group suffix for a typed one, or null if it does not denote it.
    */
   private static String getNotificationSuffix(String token, String name, int type) {
      if(name == null || token.isEmpty()) {
         return null;
      }

      if(type == Identity.USER) {
         if(token.equals(name) && !Tool.matchEmail(token)) {
            return "";
         }

         if(token.equals(name + Identity.USER_SUFFIX)) {
            return Identity.USER_SUFFIX;
         }
      }
      else if(type == Identity.GROUP && token.equals(name + Identity.GROUP_SUFFIX)) {
         return Identity.GROUP_SUFFIX;
      }

      return null;
   }

   private void updateViewsheets(ScheduleAction action,
                                     IdentityID oid, IdentityID id,
                                     ScheduleTask task,
                                     Set<ScheduleTask> changedTasks)
   {
      if(action instanceof ViewsheetAction) {
         ViewsheetAction vaction = (ViewsheetAction) action;

         if(vaction.getViewsheet() != null) {
            IdentityID oldUser = vaction.getViewsheetEntry().getUser();

            if(oldUser != null && oldUser.equals(oid) && !oldUser.equals(id)) {
               String newViewsheet = vaction.getViewsheet().replace(oldUser.convertToKey(), id.convertToKey());
               vaction.setViewsheet(newViewsheet);
               changedTasks.add(task);
            }

            IdentityID[] bookmarkUsers = Arrays.stream(vaction.getBookmarkUsers())
               .map((boomarkUser) -> boomarkUser.equals(oid) ? id : boomarkUser)
               .toArray(IdentityID[]::new);
            vaction.setBookmarkUsers(bookmarkUsers);
         }
      }
   }

   /**
    * Method will be invoked when asset is renamed.
    * @param oentry the specified old entry.
    * @param nentry the specified new entry.
    */
   public synchronized void assetRenamed(AssetEntry oentry, AssetEntry nentry, String orgID) {
      if(nentry != null && RecycleUtils.isInRecycleBin(nentry.getPath())) {
         assetRemoved(oentry, orgID);
         return;
      }

      Set<ScheduleTask> changedTasks = new HashSet<>();

      for(ScheduleTask task : getOrgTaskMap(orgID).values()) {
         if(task == null) {
            continue;
         }

         for(int j = task.getActionCount() - 1; j >= 0; j--) {
            if((task.getAction(j) instanceof AssetSupport)) {
               AssetSupport action = (AssetSupport) task.getAction(j);
               AssetEntry entry2 = action.getEntry();

               if(Tool.equals(entry2, oentry)) {
                  action.setEntry(nentry);
                  LOG.debug(
                     "Schedule action in task " + task.getTaskId() +
                        " is changed for asset " + oentry + " is renamed to " +
                        nentry);
                  changedTasks.add(task);
               }
            }
            else if(task.getAction(j) instanceof BatchAction) {
               BatchAction action = (BatchAction) task.getAction(j);
               AssetEntry queryEntry = action.getQueryEntry();

               if(Tool.equals(queryEntry, oentry)) {
                  action.setQueryEntry(nentry);
                  LOG.debug(
                     "Schedule action in task " + task.getTaskId() +
                        " is changed for asset " + oentry + " is renamed to " +
                        nentry);
                  changedTasks.add(task);
               }
            }
         }
      }

      try {
         save(changedTasks, oentry.getOrgID());
      }
      catch(Exception ex) {
         LOG.error("Failed to save schedule task file after " +
               "asset was renamed: " + oentry + " to " + nentry, ex);
      }
   }

   /**
    * Method will be invoked when asset is removed.
    * @param entry the specified asset entry.
    */
   public synchronized void assetRemoved(AssetEntry entry, String orgID) {
      // Bug #65532, optimization, ignore any assets other than ws or vs
      if(entry == null || !entry.isSheet()) {
         return;
      }

      Set<ScheduleTask> changedTasks = new HashSet<>();

      for(ScheduleTask task : getOrgTaskMap(orgID).values()) {
         if(task == null) {
            continue;
         }

         for(int j = task.getActionCount() - 1; j >= 0; j--) {
            if(!(task.getAction(j) instanceof AssetSupport)) {
               continue;
            }

            AssetSupport action = (AssetSupport) task.getAction(j);
            AssetEntry entry2 = action.getEntry();

            if(Tool.equals(entry, entry2)) {
               task.removeAction(j);
               LOG.debug(
                           "Schedule action in task " + task.getTaskId() +
                           " is removed for asset " + entry + " is removed");
               changedTasks.add(task);
            }
         }
      }

      try {
         save(changedTasks, entry.getOrgID());
      }
      catch(Exception ex) {
         LOG.error("Failed to save schedule task file after " +
               "asset was removed: " + entry, ex);
      }
   }

   /**
    * Method will be invoked when a viewsheet is renamed.
    * @param oviewSheet the specified old viewsheet.
    * @param nviewSheet the specified new viewsheet.
    * @param owner the specified user.
    */
   public synchronized void viewSheetRenamed(String oviewSheet,
                                             String nviewSheet, String owner, String orgID)
   {
      AssetEntry nentry = AssetEntry.createAssetEntry(nviewSheet);
      AssetEntry oentry = AssetEntry.createAssetEntry(oviewSheet);

      if(nentry.isViewsheet() && RecycleUtils.isInRecycleBin(nentry.getPath())) {
         viewsheetRemoved(oentry, orgID);
         return;
      }

      // Bug #65532, optimization, ignore any assets other than ws or vs
      if(nentry == null || Tool.equals(oentry, nentry) || !nentry.isSheet()) {
         return;
      }

      Set<ScheduleTask> changedTasks = new HashSet<>();

      for(ScheduleTask task : getOrgTaskMap(orgID).values()) {
         if(task == null) {
            continue;
         }

         for(int j = task.getActionCount() - 1; j >= 0; j--) {
            if(!(task.getAction(j) instanceof ViewsheetSupport)) {
               continue;
            }

            ViewsheetSupport action = (ViewsheetSupport) task.getAction(j);
            String name = action.getViewsheetName();

            if(name.equals(oviewSheet) &&
               (!SUtil.isMyReport(name) || Tool.equals(task.getOwner(), owner)))
            {
               action.setViewsheetName(nviewSheet);
               LOG.debug(
                           "Schedule action in task " + task.getTaskId() +
                              " is changed for viewsheet " + name + " is renamed to " +
                              nviewSheet);
               changedTasks.add(task);
            }
         }
      }

      try {
         save(changedTasks, nentry.getOrgID());
      }
      catch(Exception ex) {
         LOG.error("Failed to save schedule task file after " +
               "viewsheet was renamed: " + oviewSheet + " to " + nviewSheet, ex);
      }

      boolean extchanged = false;

      for(ScheduleExt ext : extensions) {
         extchanged =
            ext.viewsheetRenamed(oviewSheet, nviewSheet, owner) || extchanged;
      }

      if(extchanged) {
         reloadExtensions(orgID);
      }
   }

   public synchronized void viewsheetRemoved(AssetEntry entry, String orgID) {
      Set<ScheduleTask> changedTasks = new HashSet<>();

      for(ScheduleTask task : getOrgTaskMap(orgID).values()) {
         if(task == null) {
            continue;
         }

         for(int j = task.getActionCount() - 1; j >= 0; j--) {
            if(!(task.getAction(j) instanceof ViewsheetSupport)) {
               continue;
            }

            ViewsheetSupport action = (ViewsheetSupport) task.getAction(j);
            String vsId = action.getViewsheetName();

            if(entry.toIdentifier().equals(vsId)) {
               task.removeAction(j);
               LOG.debug(
                  "Schedule action in task " + task.getTaskId() +
                     " is removed after viewsheet " + entry + " is removed");
               changedTasks.add(task);
            }
         }
      }

      try {
         save(changedTasks, entry.getOrgID());
      }
      catch(Exception ex) {
         LOG.error("Failed to save schedule task file after " +
                      "viewsheet was removed: " + entry, ex);
      }
   }

   /**
    * Rename bookmark in schedule actions.
    */
   public void bookmarkRenamed(String oldName, String newName, String viewsheet, IdentityID user) {
      Vector<ScheduleTask> tasks = getScheduleTasks();
      Set<ScheduleTask> changedTasks = new HashSet<>();

      for(ScheduleTask task : tasks) {
         for(int j = 0; j < task.getActionCount(); j++) {
            if(task.getAction(j) instanceof ViewsheetAction) {
               ViewsheetAction vact = (ViewsheetAction) task.getAction(j);
               String[] names = vact.getBookmarks();
               IdentityID[] userNames = vact.getBookmarkUsers();

               for(int i = 0; i < names.length; i ++) {
                  if(Tool.equals(names[i], oldName) && Tool.equals(userNames[i], user) &&
                     Objects.equals(viewsheet, vact.getViewsheet()))
                  {
                     names[i] = newName;
                     vact.setBookmarks(names);
                     changedTasks.add(task);
                     break;
                  }
               }
            }
         }
      }

      try {
         String orgID = viewsheet.substring(viewsheet.lastIndexOf("^") + 1);
         save(changedTasks, orgID);
      }
      catch(Exception ex) {
         LOG.error("Failed to save schedule task file after " +
               "bookmark was renamed: " + oldName + " to " + newName, ex);
      }
   }

   /**
    * Method will be invoked when a folder is renamed.
    * @param opath the specified old folder path.
    * @param npath the specified new folder path.
    * @param owner the specified user.
    */
   public synchronized void folderRenamed(String opath, String npath,
                                          String owner, String orgID)
   {
      Set<ScheduleTask> changedTasks = new HashSet<>();

      for(ScheduleTask task : getOrgTaskMap(orgID).values()) {
         if(task == null) {
            continue;
         }

         for(int j = task.getActionCount() - 1; j >= 0; j--) {
            ScheduleAction action = task.getAction(j);
            String prefix = opath + "/";

            if(action instanceof ViewsheetAction) {
               ViewsheetAction vsAction = (ViewsheetAction) action;
               AssetEntry vsEntry = vsAction.getViewsheetEntry();
               String path = vsEntry.getPath();

               if(path != null && path.startsWith(prefix) &&
                  (Tool.equals(vsEntry.getUser(), owner)))
               {
                  String newName = npath + "/" + path.substring(prefix.length());
                  AssetEntry newVSEntry = new AssetEntry(vsEntry.getScope(), vsEntry.getType(),
                     newName, vsEntry.getUser());
                  vsAction.setViewsheet(newVSEntry.toIdentifier());
                  LOG.info("Schedule action in task " + task.getTaskId() +
                     " is changed for viewsheet " + path + " is renamed to " + newName + "!");
                  changedTasks.add(task);
               }
            }
         }
      }

      try {
         save(changedTasks);
      }
      catch(Exception ex) {
         LOG.error("Failed to save schedule task file after " +
               "folder was renamed: " + opath + " to " + npath, ex);
      }

      boolean extchanged = false;

      for(ScheduleExt ext : extensions) {
         extchanged = ext.folderRenamed(opath, npath, owner) || extchanged;
      }

      if(extchanged) {
         reloadExtensions(orgID);
      }
   }

   /**
    * Rename the viewsheet in schedule tasks.
    */
   public void renameSheetInSchedule(AssetEntry oentry, AssetEntry nentry)
      throws Exception
   {
      if(nentry != null && nentry.isViewsheet() && RecycleUtils.isInRecycleBin(nentry.getPath())) {
         viewsheetRemoved(oentry, nentry.getOrgID());
         return;
      }

      Set<ScheduleTask> changedTasks = new HashSet<>();

      for(ScheduleTask task : getScheduleTasks(nentry.getOrgID())) {
         for(int i = 0; i < task.getActionCount(); i++) {
            ScheduleAction action = task.getAction(i);

            if(action instanceof ViewsheetSupport) {
               ViewsheetSupport vaction = (ViewsheetSupport) action;

               if(nentry.isFolder()) {
                  String opath = oentry.getPath();
                  String npath = nentry.getPath();
                  AssetEntry ventry = AssetEntry.createAssetEntry(vaction.getViewsheetName());
                  assert ventry != null;
                  String vpath = ventry.getPath();

                  if(vpath.startsWith(opath + "/")) {
                     vpath = npath + "/" + vpath.substring(opath.length() + 1);
                     ventry = new AssetEntry(ventry.getScope(), ventry.getType(),
                                             vpath, ventry.getUser(), ventry.getOrgID());
                     vaction.setViewsheetName(ventry.toIdentifier());
                     changedTasks.add(task);
                  }
               }
               else if(Tool.equals(vaction.getViewsheetName(), oentry.toIdentifier())) {
                  vaction.setViewsheetName(nentry.toIdentifier());
                  changedTasks.add(task);
               }
            }
            else if(action instanceof BatchAction) {
               AssetEntry actionEntry = ((BatchAction) action).getQueryEntry();

               if(actionEntry != null &&
                  Tool.equals(actionEntry.toIdentifier(), oentry.toIdentifier()))
               {
                  ((BatchAction) action).setQueryEntry(nentry);
                  changedTasks.add(task);
               }
            }
         }
      }

      save(changedTasks, nentry.getOrgID());
   }

   /**
    * Gets the asset identifier for a schedule task.
    *
    * @param task the schedule task.
    *
    * @return the asset identifier.
    */
   private String getTaskIdentifier(ScheduleTask task) {
      return getTaskIdentifier(task.getTaskId(), null);
   }

   /**
    * Gets the asset identifier for a schedule task.
    *
    * @param taskId the name of the task.
    *
    * @return the asset identifier.
    */
   private String getTaskIdentifier(String taskId, String orgID) {
      return new AssetEntry(
         AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.SCHEDULE_TASK, "/" + taskId, SUtil.getTaskOwner(taskId), orgID)
         .toIdentifier();
   }

   public boolean isArchiveTaskEnabled() {
      //Todo will remove it.
      return false;
   }

   public ScheduleTask getAssetBackupTask() {
      return getScheduleTask(InternalScheduledTaskService.ASSET_FILE_BACKUP, Organization.getDefaultOrganizationID());
   }

   public ScheduleTask getBalanceTask() {
      return getScheduleTask(InternalScheduledTaskService.BALANCE_TASKS, Organization.getDefaultOrganizationID());
   }

   private SecurityEngine getSecurityEngine() {
      return securityEngine;
   }

   private Cluster getCluster() {
      return cluster;
   }

   private final SecurityEngine securityEngine;
   private final Cluster cluster;
   private final ScheduleClient scheduleClient;
   private final DependencyHandler dependencyHandler;
   private final Map<String, ScheduleTaskMap> taskMap = new HashMap<>();
   private final Vector<ScheduleExt> extensions = new Vector<>();
   private final Map<ExtTaskKey, ScheduleTask> extensionTasks = new ConcurrentHashMap<>();
   // the extension that generated each of extensionTasks, guarded like extensionTasks
   private final Map<ExtTaskKey, ScheduleExt> extensionTaskOwners = new ConcurrentHashMap<>();
   private final Set<String> extensionTasksLoadedOrgs = ConcurrentHashMap.newKeySet();
   // Local lock — intentionally NOT a distributed Ignite lock. The state it guards
   // (extensions, extensionTasks, extensionTasksLoadedOrgs) is per-node local data.
   // Using an Ignite distributed lock here caused GridDhtPartitionsExchangeFuture to
   // stall waiting for the lock's volatile-DS-group transaction, blocking TRANSACTIONAL
   // cache writes (including the runtime-sheet cache) and causing ExpiredSheetException
   // under concurrent load when a topology change coincided with a getScheduleTasks() call.
   // Lock ordering (Bug #77195): RepletRegistry monitor -> ScheduleManager monitor ->
   // extensionLock -> DataCycleManager monitor. Holding the ScheduleManager monitor while
   // acquiring extensionLock is allowed (e.g. save() -> reloadExtensions()), but code holding
   // extensionLock must never enter a synchronized ScheduleManager method or take the
   // RepletRegistry monitor.
   private final Lock extensionLock = new ReentrantLock();

   private static final Pattern NOTIFICATION_DELIMITER = Pattern.compile("[;,]");
   private static final Logger LOG = LoggerFactory.getLogger(ScheduleManager.class);

   private record ExtTaskKey(String name, String orgId) { }

}
