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
package inetsoft.web.admin.content.repository;

import inetsoft.mv.*;
import inetsoft.mv.fs.internal.ClusterUtil;
import inetsoft.mv.trans.UserInfo;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.util.*;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.lang.SecurityException;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Stateless class that provides support for managing materialized views.
 */
@Service
public class MVSupportService {
   public static MVSupportService getInstance() {
      return SingletonManager.getInstance(MVSupportService.class);
   }

   /**
    * Re-analyze a set of materialized views.
    *
    * @param mvs       the materialized view names.
    * @param principal the principal that identifies the current user.
    *
    * @return the results of the analysis.
    *
    * @throws Exception if the analysis failed.
    */
   public AnalysisResult analyze(String[] mvs, Principal principal) throws Exception {
      if(!checkMVPermission(principal)) {
         throw new inetsoft.sree.security.SecurityException(Catalog.getCatalog()
            .getString("mv.permission.missing.message"));
      }

      MVManager mgr = MVManager.getManager();
      Map<String, MVDef> vs2def = new HashMap<>();
      Map<String, MVDef> ws2def = new HashMap<>();

      for(String mv : mvs) {
         MVDef def = mgr.get(mv);

         if(def != null) {
            String[] sheetIds = def.getMetaData().getRegisteredSheets();

            if(def.isWSMV()) {
               for(String id : sheetIds) {
                  ws2def.put(id, def);
               }
            }
            else {
               for(String id : sheetIds) {
                  vs2def.put(id, def);
               }
            }
         }
      }

      List<String> identifiers = new ArrayList<>();
      boolean[] expanded = new boolean[vs2def.size() + ws2def.size()];
      boolean[] bypass = new boolean[vs2def.size() + ws2def.size()];
      boolean[] full = new boolean[vs2def.size() + ws2def.size()];
      int index = 0;

      for(String id : vs2def.keySet()) {
         identifiers.add(id);
         MVDef def = vs2def.get(id);
         expanded[index] = def.getMetaData().isGroupExpanded();
         bypass[index] = def.getMetaData().isBypassVPM();
         full[index] = def.getMetaData().isFullData();
         index++;
      }

      for(String id : ws2def.keySet()) {
         identifiers.add(id);
         MVDef def = ws2def.get(id);
         expanded[index] = def.getMetaData().isGroupExpanded();
         bypass[index] = def.getMetaData().isBypassVPM();
         full[index] = def.getMetaData().isFullData();
         index++;
      }

      List<UserInfo> exceptions = new ArrayList<>();
      Map<String, StringBuffer> plans = new HashMap<>();

      AnalysisTask task = new AnalysisTask(
         identifiers, exceptions, plans, expanded, bypass, full, true, principal, false);

      return new AnalysisResult(identifiers, null, exceptions, plans, analyzePool.submit(task));
   }

   /**
    * Perform the initial materialized view analysis on a viewsheet.
    *
    * @param identifiers  the viewsheet identifiers.
    * @param paths        the viewsheet paths.
    * @param expandGroups <tt>true</tt> to expand groups to the member users.
    * @param bypassVpm    <tt>true</tt> to bypass any VPM conditions.
    * @param fullData     <tt>true</tt> to include all data.
    * @param principal    the principal that identifies the current user.
    *
    * @param portal
    * @return the results of the analysis.
    *
    * @throws Exception if the analysis failed.
    */
   public AnalysisResult analyze(List<String> identifiers, List<String> paths,
                                 boolean expandGroups, boolean bypassVpm,
                                 boolean fullData, Principal principal, boolean portal)
      throws Exception
   {
      if(!checkMVPermission(principal)) {
         throw new inetsoft.sree.security.SecurityException(Catalog.getCatalog()
            .getString("mv.permission.missing.message"));
      }

      List<UserInfo> exceptions = new ArrayList<>();
      Map<String, StringBuffer> plans = new HashMap<>();

      // find nested viewsheets and add them to the lists
      List<String> newIdentifiers = new ArrayList<>(identifiers);
      List<String> newPaths = new ArrayList<>(paths);

      for(String id : identifiers) {
         AssetEntry entry = getEntry(id);
         AbstractSheet sheet = getSheet(id, entry, principal);

         if(sheet instanceof Viewsheet) {
            for(AssetEntry childEntry : sheet.getOuterDependents()) {
               if(childEntry.getType() == AssetEntry.Type.VIEWSHEET &&
                  !newIdentifiers.contains(childEntry.toIdentifier()))
               {
                  if(getSheet(childEntry.toIdentifier(), childEntry, principal) == null) {
                     continue;
                  }

                  newIdentifiers.add(childEntry.toIdentifier());
                  newPaths.add(childEntry.getPath());
               }
            }
         }
      }

      identifiers = newIdentifiers;
      paths = newPaths;

      AnalysisTask task = new AnalysisTask(
         identifiers, exceptions, plans, new boolean[] { expandGroups },
         new boolean[] { bypassVpm }, new boolean[] { fullData }, false,
         principal, portal);

      return new AnalysisResult(identifiers, paths, exceptions, plans, analyzePool.submit(task));
   }

   /**
    * Re-creates a set of existing materialized views.
    *
    * @param names      the names of the materialized views.
    * @param background <tt>true</tt> to run the materialization in a background
    *                   thread.
    * @param principal  the principal that identifies the current user.
    *
    * @return any warning messages generated during the creation process.
    *
    * @throws Throwable if the materialized views could not be created.
    */
   public String recreateMV(String[] names, boolean background, Principal principal)
      throws Throwable
   {
      MVManager manager = MVManager.getManager();
      ArrayList<MVStatus> views = new ArrayList<>();

      for(String name : names) {
         MVDef def = manager.get(name);

         if(def != null) {
            MVStatus status = new MVStatus(def, "");
            views.add(status);
         }
      }

      return createMV0(views, background, false, principal);
   }

   /**
    * Creates a set of new materialized views.
    *
    * @param names      the names of the materialized views to generate.
    * @param views      the definitions of the materialized views.
    * @param background a tag to check if schedule the mv creation. create mv
    *                   in back ground means schedule the mv creation. manager
    *                   mv back ground means run the mv creation in a
    *                   background thread
    * @param noData     <tt>true</tt> if no data should be generated for the
    *                   materialized views at this time.
    * @param principal  the principal that identifies the current user.
    *
    * @return any warning messages generated during the creation process.
    *
    * @throws Throwable if the materialized views could not be created.
    */
   public String createMV(List<String> names, List<MVStatus> views,
                          boolean background, boolean noData,
                          Principal principal)
      throws Throwable
   {
      List<MVStatus> createList = views.stream()
         .filter(mv -> names.contains(mv.getDefinition().getName()))
         .collect(Collectors.toList());
      return createMV0(createList, background, noData, principal);
   }

   /**
    * Creates a set of materialized views.
    *
    * @param views      the definitions of the materialized views.
    * @param background <tt>true</tt> to run the materialization in a background
    *                   thread.
    * @param noData     <tt>true</tt> if no data should be generated for the
    *                   materialized views at this time.
    * @param principal  the principal that identifies the current user.
    *
    * @return any warning messages generated during the creation process.
    *
    * @throws Throwable if the materialized views could not be created.
    */
   private String createMV0(final List<MVStatus> views, boolean background,
                            final boolean noData, final Principal principal)
      throws Throwable
   {
      if(!checkMVPermission(principal)) {
         throw new inetsoft.sree.security.SecurityException(Catalog.getCatalog()
            .getString("mv.permission.missing.message"));
      }

      String exception = null;
      // sort association MV after regular MV, since association MV uses
      // base MV to generate
      views.sort(new MVComparator());

      Map<String, String> statusMap = Cluster.getInstance().getMap("inetsoft.mv.status.map");

      for(MVStatus status : views) {
         statusMap.put(status.getDefinition().getName(), "Pending");
      }

      if(background) {
         if(Cluster.getInstance().isSchedulerRunning()) {
            createMVBackground(views, principal);
         }
         else {
            remoteCreatePool.submit(() -> {
               try {
                  createMVForeground(views, noData, principal);
               }
               catch(Throwable ex) {
                  LOG.error("Failed to create MVs: " + views, ex);
               }
            });
         }
      }
      else {
         exception = createMVForeground(views, noData, principal);
      }

      return exception;
   }

   /**
    * Creates a set of materialized views.
    *
    * @param views     the materialized views being created.
    * @param noData    <tt>true</tt> if no data should be generated for the
    *                  materialized views at this time.</tt>
    * @param principal the principal that identifies the current user.
    *
    * @return any warning messages generated during the creation process.
    *
    */
   private String createMVForeground(List<MVStatus> views, boolean noData, Principal principal) {
      Principal oldPrincipal = ThreadContext.getContextPrincipal();

      try {
         ThreadContext.setContextPrincipal(principal);
         return createMVForeground0(views, noData, principal);
      }
      finally {
         ThreadContext.setContextPrincipal(oldPrincipal);
      }
   }
   /**
    * Creates a set of materialized views.
    *
    * @param views     the materialized views being created.
    * @param noData    <tt>true</tt> if no data should be generated for the
    *                  materialized views at this time.</tt>
    * @param principal the principal that identifies the current user.
    *
    * @return any warning messages generated during the creation process.
    *
    */
   private String createMVForeground0(List<MVStatus> views, boolean noData, Principal principal) {
      MVManager manager = MVManager.getManager();
      final String prefix = principal.getName() + ":";
      ScheduleTask task = new ScheduleTask(
         prefix + MV_TASK_PREFIX + UUID.randomUUID().toString(), ScheduleTask.Type.MV_TASK);

      for(MVStatus status : views) {
         if(noData) {
            manager.add(status.getDefinition(), false);
         }
         else {
            MVAction action = new MVAction(status.getDefinition());
            task.addAction(action);
            status.getDefinition().setSuccess(false);
            manager.add(status.getDefinition(), false);
         }
      }

      try {
         task.run(principal);
         return null;
      }
      catch(Throwable ex) {
         LOG.warn("Failed to process materialized view status: " + ex, ex);
         return ex.getMessage();
      }
      finally {
         manager.fireEvent("mvmanager_", MVManager.MV_CHANGE_EVENT, null, null);
      }
   }

   /**
    * Deletes the specified materialized views.
    *
    * @param mvs the names of the materialized views to delete.
    */
   public void dispose(List<String> mvs) {
      MVManager manager = MVManager.getManager();
      String orgID = OrganizationManager.getInstance().getCurrentOrgID();

      for(String mv : mvs) {
         ClusterUtil.deleteClusterMV(mv);
         MVDef def = manager.get(mv, orgID);

         if(def != null) {
            manager.remove(def, false, orgID);
         }
      }

      ClusterUtil.clearRemovedMVFiles();
      manager.fireEvent("mvmanager_", MVManager.MV_CHANGE_EVENT, null, null);
   }

   /**
    * Sets the data cycle for the specified materialized views.
    *
    * @param mvs       the names of the materialized views.
    * @param dataCycle the name of the data cycle.
    */
   public void setDataCycle(String[] mvs, String dataCycle) {
      MVManager manager = MVManager.getManager();
      boolean event = mvs.length > 0;

      for(String mv : mvs) {
         MVDef def = manager.get(mv);
         def.setCycle(getCycle(dataCycle));
         def.setChanged(true);
         manager.add(def, false);
      }

      if(event) {
         manager.fireEvent("mvmanager_", MVManager.MV_CHANGE_EVENT, null, null);
      }
   }

   /**
    * Sets the data cycle for materialized views that have not been created yet.
    *
    * @param mvs       the names of the materialized views.
    * @param statuses  the pending materialized view definitions.
    * @param dataCycle the name of the data cycle.
    */
   public void setDataCycle(List<String> mvs, List<MVStatus> statuses, String dataCycle) {
      MVManager manager = MVManager.getManager();

      for(String name : mvs) {
         for(MVStatus status : statuses) {
            if(status.getDefinition().getName().equals(name)) {
               status.getDefinition().setCycle(getCycle(dataCycle));
               status.getDefinition().setChanged(true);

               MVDef odef = manager.get(name);

               if(odef != null) {
                  status.getDefinition().setSuccess(odef.isSuccess());
               }

               // this is called when analyzing MV, don't trigger event
               // until it's actually created later to avoid creating
               // schedule task
               manager.add(status.getDefinition(), false);
            }
         }
      }

      manager.fireEvent("mvmanager_", MVManager.MV_CHANGE_EVENT, null, null);
   }

   /**
    * Schedules the data for a materialized view to be generated.
    *
    * @param statuses  the statuses of the materialized views.
    * @param principal the principal the principal that identifies the current user.
    *
    * @throws Exception if the data generation could not be scheduled.
    */
   private void createMVBackground(List<MVStatus> statuses, Principal principal) throws Exception {
      long time = System.currentTimeMillis() + 6000 * 2;
      MVManager mvManager = MVManager.getManager();
      TimeCondition condition = TimeCondition.at(new Date(time));
      ScheduleTask task = new ScheduleTask(MV_TASK_PREFIX + UUID.randomUUID(), ScheduleTask.Type.MV_TASK);
      IdentityID user = IdentityID.getIdentityIDFromKey(principal.getName());
      IdentityID owner = SUtil.getOwnerForNewTask(user);
      task.setOwner(SUtil.getOwnerForNewTask(user));
      ScheduleTask task2 = new ScheduleTask(MV_TASK_STAGE_PREFIX + UUID.randomUUID(), ScheduleTask.Type.MV_TASK);
      task2.setOwner(owner);
      task.setDeleteIfNoMoreRun(true);
      task.setEditable(false);
      task.setDurable(true);
      task.addCondition(condition);
      task2.setDeleteIfNoMoreRun(true);
      task2.setEditable(false);
      task2.setDurable(true);
      task2.addCondition(new CompletionCondition(task.getTaskId()));

      for(MVStatus status : statuses) {
         if(status.getDefinition().isAssociationMV()) {
            task2.addAction(new MVAction(status.getDefinition()));
         }
         else {
            task.addAction(new MVAction(status.getDefinition()));
         }

         status.setScheduled(true);
         status.setExists(true);
         mvManager.add(status.getDefinition(), false);
      }

      ScheduleManager manager = ScheduleManager.getScheduleManager();
      manager.setScheduleTask(task.getTaskId(), task, principal);

      if(task2.getActionCount() > 0) {
         manager.setScheduleTask(task2.getTaskId(), task2, principal);
      }

      mvManager.fireEvent("mvmanager_", MVManager.MV_CHANGE_EVENT, null, null);
   }

   /**
    * Gets the status of all materialized views associated with a viewsheet.
    *
    * @param identifier the viewsheet identifier.
    *
    * @return the materialized view statuses.
    */
   public List<MVStatus> getMVStatus(String identifier) {
      MVManager manager = MVManager.getManager();
      ScheduleManager schedule = ScheduleManager.getScheduleManager();
      List<MVStatus> list = new ArrayList<>();
      AssetEntry entry = (identifier != null) ? getEntry(identifier) : null;

      for(MVDef def : manager.list(false)) {
         if(entry == null || def.matches(entry) ||
            (def.getMetaData() != null &&
               def.getMetaData().isRegistered(entry.toIdentifier())))
         {
            MVStatus mvstatus = new MVStatus(def, "");
            mvstatus.setExists(true);
            mvstatus.setDataPresent(def.hasData());
            list.add(mvstatus);
         }
      }

      // status to be removed
      for(ScheduleTask task : schedule.getScheduleTasks()) {
         if(task.getTaskId().startsWith(DataCycleManager.TASK_PREFIX)) {
            continue;
         }

         for(int i = 0; i < task.getActionCount(); i++) {
            ScheduleAction scheduleAction = task.getAction(i);

            if(scheduleAction instanceof MVAction) {
               MVAction mvaction = (MVAction) scheduleAction;
               MVDef def = mvaction.getMV();

               if(def.getEntry().toIdentifier().equals(identifier)) {
                  MVStatus status = new MVStatus(def, "");
                  status.setScheduled(true);

                  if(!list.contains(status)) {
                     list.add(status);
                  }
               }
            }
         }
      }

      return list;
   }

   /**
    * Shuts down the thread pools that serve background processing of
    * materialization tasks.
    */
   @PreDestroy
   public void shutdown() {
      analyzePool.shutdownNow();
      localCreatePool.shutdownNow();
      remoteCreatePool.shutdownNow();
   }

   /**
    * Check MV permission for specific user.
    * @param principal the user will be checked.
    *
    * @return true if user have MV permission else false.
    */
   private boolean checkMVPermission(Principal principal) {
      SecurityProvider provider;

      try {
         provider = SecurityEngine.getSecurity().getSecurityProvider();
      }
      catch(Exception ex) {
         LOG.error("Failed to get security provider", ex);

         return true;
      }

      if(provider == null) {
         return true;
      }

      return provider.checkPermission(
         principal, ResourceType.MATERIALIZATION, "*", ResourceAction.ACCESS);
   }

   /**
    * Do not apply "" cycle.
    */
   private static String getCycle(String cycle) {
      if("".equals(cycle)) {
         return null;
      }

      return cycle;
   }

   /**
    * Get an asset entry for the identifier.
    */
   static AssetEntry getEntry(String identifier) {
      return AssetEntry.createAssetEntry(identifier);
   }

   /**
    * Get the sheet.
    * @param identifier the specified identifier.
    * @param entry the specified entry.
    */
   private static AbstractSheet getSheet(String identifier,
                                         AssetEntry entry, Principal user) throws Exception
   {
      entry = entry == null ? getEntry(identifier) : entry;

      if(entry == null) {
         return null;
      }

      AssetRepository repository = AssetUtil.getAssetRepository(false);
      return repository.getSheet(entry, user, false, AssetContent.ALL);
   }

   private static void saveViewsheet(String identifier, Principal user) {
      try {
         AssetEntry entry = getEntry(identifier);

         if(!entry.isViewsheet()) {
            return;
         }

         Viewsheet vs = (Viewsheet) getSheet(identifier, null, user);

         if(vs == null) {
            return;
         }

         ViewsheetInfo vinfo = vs.getViewsheetInfo();

         if(!vinfo.isWarningIfNotHitMV()) {
            return;
         }

         vinfo.setWarningIfNotHitMV(false);
         AssetRepository repository = AssetUtil.getAssetRepository(false);
         repository.setSheet(entry, vs, user, true);
      }
      catch(Exception ex) {
         LOG.warn("Failed to save viewsheet " + identifier + " for user " + user, ex);
      }
   }

   private static Permission findPermission(String orgId, ResourceType type, String path) {
      if(orgId == null) {
         orgId = OrganizationManager.getInstance().getCurrentOrgID();
      }

      Permission perm = SecurityEngine.getSecurity().getPermission(type, path);

      if((perm == null || perm.isBlank() && !perm.hasOrgEditedGrantAll(orgId)) &&
         type.isHierarchical())
      {
         Resource resource = new Resource(type, path);
         Resource parent = type.getParent(path);

         if(parent != null && !Objects.equals(resource, parent)) {
            return findPermission(orgId, parent.getType(), parent.getPath());
         }
      }

      return perm;
   }

   /**
    * Class that encapsulates the status of a materialized view.
    */
   public static final class MVStatus implements Comparable<MVStatus>, Serializable {
      /**
       * Creates a new instance of <tt>MVStatus</tt>.
       *
       * @param mvDef    the materialized view.
       * @param assembly the assembly name.
       */
      private MVStatus(MVDef mvDef, String assembly) {
         this.mvDef = mvDef;
         this.assembly = assembly;
      }

      /**
       * Gets the materialized view definition.
       *
       * @return the definition.
       */
      public MVDef getDefinition() {
         return mvDef;
      }

      /**
       * Sets the materialized view definition.
       *
       * @param definition the definition.
       */
      private void setDefinition(MVDef definition) {
         this.mvDef = definition;
      }

      /**
       * Gets the viewsheet assembly to which the materialized view applies.
       *
       * @return the assembly name.
       */
      public String getAssembly() {
         return assembly;
      }

      /**
       * Gets the flag that indicates if the materialized view has been created.
       *
       * @return <tt>true</tt> if the MV exists; <tt>false</tt> otherwise.
       */
      public boolean isExists() {
         return exists;
      }

      /**
       * Sets the flag that indicates if the materialized view has been created.
       *
       * @param exists <tt>true</tt> if the MV exists; <tt>false</tt> otherwise.
       */
      private void setExists(boolean exists) {
         this.exists = exists;
      }

      /**
       * Gets the flag that indicates if the data for the materialized view has
       * been generated.
       *
       * @return <tt>true</tt> if the data exists; <tt>false</tt> otherwise.
       */
      public boolean isDataPresent() {
         return dataPresent;
      }

      /**
       * Sets the flag that indicates if the data for the materialized view has
       * been generated.
       *
       * @param dataPresent <tt>true</tt> if the data exists; <tt>false</tt>
       *                    otherwise.
       */
      private void setDataPresent(boolean dataPresent) {
         this.dataPresent = dataPresent;
      }

      /**
       * Gets the flag that indicates if the data for the materialized view is
       * scheduled for generation.
       *
       * @return <tt>true</tt> if scheduled; <tt>false</tt> otherwise.
       */
      public boolean isScheduled() {
         return scheduled;
      }

      /**
       * Gets the flag that indicates if the data for the materialized view is
       * scheduled for generation.
       *
       * @param scheduled <tt>true</tt> if scheduled; <tt>false</tt> otherwise.
       */
      private void setScheduled(boolean scheduled) {
         this.scheduled = scheduled;
      }

      /**
       * Get the viewsheet that is sharing this MV.
       */
      public String[] getRegisteredSheets() {
         return mvDef.getMetaData().getRegisteredSheets();
      }

      /**
       * Update the exist/hasData status.
       */
      public void updateStatus() {
         MVManager manager = MVManager.getManager();
         setExists(manager.get(mvDef.getName()) != null);
         setDataPresent(mvDef.hasData());
      }

      @Override
      public boolean equals(Object obj) {
         if(!(obj instanceof MVStatus)) {
            return false;
         }

         MVStatus mvStatus = (MVStatus) obj;
         return mvDef.getName().equals(mvStatus.mvDef.getName());
      }

      @Override
      public int hashCode() {
         return mvDef.getName().hashCode();
      }

      @Override
      public int compareTo(MVStatus status2) {
         return mvDef.getName().compareTo(status2.mvDef.getName());
      }

      @Override
      public String toString() {
         return "MVStatus{" +
            "mvDef=" + (mvDef == null ? null : mvDef.getName()) +
            ", assembly='" + assembly + '\'' +
            ", exists=" + exists +
            ", dataPresent=" + dataPresent +
            ", scheduled=" + scheduled +
            ", sharedBy=" + Arrays.toString(getRegisteredSheets()) +
            '}';
      }

      private MVDef mvDef;
      private String assembly;
      private boolean exists;
      private boolean dataPresent;
      private boolean scheduled;
   }

   /**
    * Class that encapsulates the asynchronous result of an MV analysis.
    */
   public static final class AnalysisResult {
      /**
       * Creates a new instance of <tt>AnalysisResult</tt>.
       *
       * @param identifiers the identifiers of the viewsheets being analyzed.
       * @param paths       the paths to the viewsheets being analyzed.
       * @param exceptions  the users for whom the viewsheet cannot be
       *                    materialized.
       * @param plans       the materialization plans.
       * @param future      the asynchronous response for the analysis task.
       */
      private AnalysisResult(List<String> identifiers, List<String> paths,
                             List<UserInfo> exceptions,
                             Map<String, StringBuffer> plans,
                             Future<List<MVStatus>> future)
      {
         this.identifiers = identifiers;
         this.paths = paths;
         this.exceptions = exceptions;
         this.plans = plans;
         this.future = future;
      }

      /**
       * Gets the identifiers of the viewsheets being analyzed.
       *
       * @return the identifiers.
       */
      public List<String> getIdentifiers() {
         return identifiers;
      }

      /**
       * Gets the paths to the viewsheets being analyzed.
       *
       * @return the paths.
       */
      public List<String> getPaths() {
         return paths;
      }

      /**
       * Gets the users for whom the viewsheets could not be materialized.
       *
       * @return the exception users.
       */
      public List<UserInfo> getExceptions() {
         return exceptions;
      }

      /**
       * Gets the result of the materialized view analysis. This value is not
       * available until {@link #isCompleted()} returns <tt>true</tt>.
       *
       * @return the result status.
       */
      public List<MVStatus> getStatus() {
         return status;
      }

      /**
       * Gets the plans for the materialized views.
       *
       * @return the plans.
       */
      public Map<String, StringBuffer> getPlans() {
         return plans;
      }

      /**
       * Determines if the background analysis task has been completed.
       *
       * @return <tt>true</tt> if completed; <tt>false</tt> otherwise.
       */
      public synchronized boolean isCompleted() {
         boolean result = true;

         if(future != null) {
            if(future.isDone() || future.isCancelled()) {
               try {
                  status = future.get();
               }
               catch(Exception ex) {
                  LOG.error("Failed to generate MV: " + ex, ex);
               }

               future = null;
            }
            else {
               result = false;
            }
         }

         return result;
      }

      /**
       * Waits for the background analysis task to complete.
       */
      public synchronized void waitFor() {
         if(future != null) {
            try {
               status = future.get();
            }
            catch(Exception e) {
               LOG.error("Failed to perform MV analysis", e);
            }

            future = null;
         }
      }

      private final List<String> identifiers;
      private final List<String> paths;
      private final List<UserInfo> exceptions;
      private final Map<String, StringBuffer> plans;
      private List<MVStatus> status;
      private Future<List<MVStatus>> future;
   }

   private static final class AnalysisTask implements Callable<List<MVStatus>> {
      public AnalysisTask(List<String> identifiers, List<UserInfo> exceptions,
                          Map<String, StringBuffer> plans, boolean[] expanded,
                          boolean[] bypass, boolean[] full, boolean reanalyze,
                          Principal principal, boolean portal) throws Exception
      {
         this.exceptions = exceptions;
         this.plans = plans;
         this.reanalyze = reanalyze;
         this.portal = portal;
         this.principal = principal;

         jobs = new AnalysisJob[identifiers.size()];

         for(int i = 0; i < identifiers.size(); i++) {
            int mod = i % expanded.length;
            jobs[i] = new AnalysisJob(identifiers.get(i), expanded[mod],
                                      bypass[mod], full[mod], principal);
         }
      }

      @Override
      public List<MVStatus> call() {
         Principal oldPrincipal = ThreadContext.getContextPrincipal();
         ThreadContext.setContextPrincipal(principal);

         try {
            MVDef.REJECT_VPM.set(portal);
            return _call();
         }
         catch(Exception | Error e) {
            LOG.warn("Analysis job failed: " + e, e);
            throw e;
         }
         finally {
            MVDef.REJECT_VPM.remove();
            ThreadContext.setContextPrincipal(oldPrincipal);
         }
      }

      private List<MVStatus> _call() {
         for(AnalysisJob job : jobs) {
            job.run();
            List<UserInfo> infos = job.exceptions;

            for(UserInfo info : infos) {
               if(!exceptions.contains(info)) {
                  exceptions.add(info);
               }
            }

            List<String> descs = job.descs;
            StringBuffer plan = new StringBuffer();

            for(String desc : descs) {
               plan.append(desc);
            }

            plans.put(job.identifier, plan);
         }

         List<MVDef> mvs = new ArrayList<>();

         for(AnalysisJob job : jobs) {
            mvs.addAll(job.defs);
         }

         List<UserInfo> hints = new ArrayList<>();
         mvs = reanalyze
            ? SharedMVUtil.checkMVValid(mvs)
            : SharedMVUtil.shareAnalyzedMV(mvs, hints);

         for(UserInfo info : hints) {
            if(!exceptions.contains(info)) {
               exceptions.add(info);
            }
         }

         List<MVStatus> result = new ArrayList<>();

         for(MVDef def : mvs) {
            MVStatus status = new MVStatus(def, "");
            status.exists = def.exist;
            status.dataPresent = def.hasData();
            result.add(status);
         }

         return result;
      }

      private final boolean reanalyze;
      private final AnalysisJob[] jobs;
      private final List<UserInfo> exceptions;
      private final Map<String, StringBuffer> plans;
      private final boolean portal;
      private final Principal principal;
   }

   private static final class AnalysisJob {
      public AnalysisJob(String identifier, boolean expanded, boolean bypass,
                         boolean full, Principal principal)
         throws Exception
      {
         this.identifier = identifier;
         this.entry = getEntry(identifier);
         this.sheet = getSheet(identifier, entry, principal);
         Objects.requireNonNull(sheet, "Sheet " + identifier + " is null");
         this.expanded = expanded;
         this.bypass = bypass;
         this.full = full;
         this.principal = principal;

         if(sheet instanceof Viewsheet) {
            ViewsheetInfo vinfo = ((Viewsheet) sheet).getViewsheetInfo();
            vinfo.setGroupExpanded(expanded);
            vinfo.setBypassVPM(bypass);
            vinfo.setFullData(full);
         }
      }

      public void run() {
         try {
            createIdentities();
            createMVDef();
         }
         catch(Throwable ex) {
            LOG.error("Failed to analyze materialized view", ex);
            UserInfo info = new UserInfo(entry.getPath(), "", ex.toString());
            exceptions.add(info);
         }
      }

      /**
       * Get identities.
       */
      private void createIdentities() {
         SecurityEngine engine = SecurityEngine.getSecurity();
         SecurityProvider securityProvider = engine.getSecurityProvider();

         if(entry.getScope() == AssetRepository.USER_SCOPE) {
            identities.add(new DefaultIdentity(entry.getUser().name, entry.getOrgID(), Identity.USER));
         }
         else if(!bypass) {
            Resource resource = AssetUtil.getSecurityResource(entry);
            String orgID = Optional.ofNullable(entry.getOrgID()).orElse(Organization.getDefaultOrganizationID());
            Permission permission = findPermission(orgID, resource.getType(), resource.getPath());

            if(permission != null) {
               for(Permission.PermissionIdentity pident : permission.getGroupGrants(ResourceAction.READ)) {
                  if(expanded) {
                     SRIdentityFinder finder = (SRIdentityFinder) XUtil.getXIdentityFinder();

                     for(IdentityID usr : engine.getOrgUsers(pident.getOrganizationID())) {
                        Identity user = new DefaultIdentity(usr.name, pident.getOrganizationID(), Identity.USER);

                        if(finder.isParentGroup(user, pident.getName())) {
                           identities.add(user);
                        }
                     }
                  }
                  else {
                     identities.add(new DefaultIdentity(pident.getName(), pident.getOrganizationID(), Identity.GROUP));
                  }
               }

               if(!expanded) {
                  for(Permission.PermissionIdentity pident : permission.getRoleGrants(ResourceAction.READ)) {
                     identities.add(new DefaultIdentity(
                        pident.getName(), pident.getOrganizationID(), Identity.ROLE));
                  }
               }

               for(Permission.PermissionIdentity pident : permission.getUserGrants(ResourceAction.READ)) {
                  identities.add(new DefaultIdentity(
                     pident.getName(), pident.getOrganizationID(), Identity.USER));
               }
            }

            // user enable security, but without any permission, default to administrator
            if(!SecurityEngine.getSecurity().getSecurityProvider().isVirtual() &&
               identities.isEmpty())
            {
               List<IdentityID> orgAdminUsers = OrganizationManager.getInstance().orgAdminUsers(orgID);

               if(!orgID.equals(Organization.getDefaultOrganizationID()) &&
                  !orgAdminUsers.isEmpty())
               {
                  //assign org admin role instead of printing all org admins
                  if(orgAdminUsers.size() > 1) {
                     SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();

                     for(IdentityID roleID : provider.getRoles()) {
                        if(roleID != null && (roleID.orgID == null || Tool.equals(roleID.orgID, orgID)) && provider.isOrgAdministratorRole(roleID)) {
                           identities.add(new DefaultIdentity(roleID.name, orgID, Identity.ROLE));
                        }
                     }
                  }
                  else {
                     identities.add(new DefaultIdentity(orgAdminUsers.get(0).name, orgID, Identity.USER));
                  }
               }
               else {
                  identities.add(new DefaultIdentity(XPrincipal.SYSTEM, orgID, Identity.USER));
               }
            }

            if(this.shouldAddCurrentUser()) {
               identities.add(new DefaultIdentity(((XPrincipal) this.principal).getIdentityID().getName(), orgID, Identity.USER));
            }
         }

         if(identities.isEmpty()) {
            identities.add(null);
         }
      }

      private boolean containsIdentity(List<Identity> identities, String name) {
         if(identities == null || name == null) {
            return false;
         }

         for(Identity identity : identities) {
            if(identity != null && name.equals(identity.getName())) {
               return true;
            }
         }

         return false;
      }

      private boolean shouldAddCurrentUser() {
         if(this.principal == null || !MVDef.REJECT_VPM.get()) {
            return false;
         }

         if(!identities.isEmpty()) {
            boolean isOrgAdmin = OrganizationManager.getInstance().isOrgAdmin(this.principal);
            boolean isSiteAdmin = OrganizationManager.getInstance().isSiteAdmin(this.principal);

            if(!isOrgAdmin && !isSiteAdmin) {
               return false;
            }

            if(containsIdentity(identities, ((XPrincipal)this.principal).getIdentityID().getName()) ||
               containsIdentity(identities, XPrincipal.SYSTEM)) {
               return false;
            }
         }

         String[] groups = ((XPrincipal)this.principal).getGroups();
         IdentityID[] roles = ((XPrincipal)this.principal).getRoles();

         if(groups != null && groups.length > 0) {
            boolean exists =  Arrays.stream(groups)
               .anyMatch(group -> containsIdentity(identities, group));

            if(exists) {
               return false;
            }
         }

         if(roles != null && roles.length > 0) {
            boolean exists = Arrays.stream(roles)
               .anyMatch(role -> containsIdentity(identities, role.getName()));

            if(exists) {
               return false;
            }
         }

         return true;
      }

      /**
       * Create mv defs.
       */
      private void createMVDef() throws Exception {
         boolean isWarned = false;

         // Bug #4705 - Store ColumnSelection of all TableAssembly in Worksheet,
         // as the ColumnSelections in Worksheet can be modified (by user),
         // in AssetQuerySandbox.refreshColumnSelection().
         Assembly[] wsAssemblies = sheet instanceof Viewsheet ?
            ((Viewsheet) sheet).getBaseWorksheet().getAssemblies(true) :
            ((Worksheet) sheet).getAssemblies(true);
         ColumnSelection[] columnsPub = new ColumnSelection[wsAssemblies.length];
         ColumnSelection[] columnsPrivate = new ColumnSelection[wsAssemblies.length];

         for(int i = 0; i < wsAssemblies.length; i++) {
            if(wsAssemblies[i] instanceof TableAssembly) {
               TableAssembly table = (TableAssembly) wsAssemblies[i];
               columnsPub[i] = table.getColumnSelection(true);
               columnsPrivate[i] = table.getColumnSelection(false);
            }
         }

         // from last user start create
         for(Identity identity : identities) {
            SRPrincipal user = SUtil.getPrincipal(identity, null, true);

            if(sheet instanceof Worksheet) {
               analyzer = new WSMVAnalyzer(entry.toIdentifier(), (Worksheet) sheet, identity,
                                           bypass);
            }
            else {
               ViewsheetSandbox box = new ViewsheetSandbox(
                  (Viewsheet) sheet, Viewsheet.SHEET_DESIGN_MODE, user, false, entry);

               // @by ChrisSpagnoli, for Bug #6297
               // prepareMVCreation() needed to set up any script-set vars,
               // so they can be referenced during MV creation
               box.prepareMVCreation();
               box.setMVDisabled(true);
               box.updateAssemblies();
               analyzer = new VSMVAnalyzer(entry.toIdentifier(), (Viewsheet) sheet, identity,
                                           box, bypass);
            }

            MVDef[] mvs;

            try {
               mvs = analyzer.analyze();

               for(MVDef def : mvs) {
                  def.getMetaData().setGroupExpanded(expanded);
                  def.getMetaData().setFullData(full);
               }

               isWarned = isWarned || analyzer.isNotHitMVWarned();
            }
            // thrown for vpm, should terminate immediately
            catch(SecurityException ex) {
               throw ex;
            }
            // for one identity included in permission, it may not be suitable
            // for creating mv, and exception might occur (e.g.multitenant) when
            // preparing mv. For this case, we should ignore the identity and
            // continue to prepare mv for the other identities
            catch(Exception ex) {
               LOG.warn("Failed to analyze materialized views", ex);
               UserInfo info = new UserInfo(entry.getPath(), "", ex.toString());
               exceptions.add(info);
               continue;
            }

            // the optimize plan for each user are the same, so only show once
            if(descs.isEmpty()) {
               descs.add(analyzer.getInfo(mvs));
               exceptions.addAll(analyzer.getDescriptor().getUserInfo());
            }

            LOOP:
            for(MVDef def : mvs) {
               for(MVDef def2 : defs) {
                  if(def.equalsContent(def2)) {
                     Identity[] src = def2.getUsers();
                     src = src == null ? new Identity[0] : src;
                     Identity[] src2 = def.getUsers();
                     src2 = src2 == null ? new Identity[0] : src2;

                     if(src2.length > 0 &&
                        !Arrays.asList(src).contains(src2[0])) {
                        Identity[] dest = new Identity[src.length + 1];
                        System.arraycopy(src, 0, dest, 0, src.length);
                        dest[src.length] = src2[0];
                        def2.setUsers(dest);
                     }

                     continue LOOP;
                  }
               }

               def.sortUsers();
               defs.add(def);
            }

            // Bug #4705 - Restore ColumnSelection to all TableAssembly in Worksheet
            for(int i = 0; i < wsAssemblies.length; i++) {
               if(wsAssemblies[i] instanceof TableAssembly) {
                  TableAssembly table = (TableAssembly) wsAssemblies[i];

                  for(MVDef def : mvs) {
                     if(Tool.equals(table.getName(), def.getBoundTable())) {
                        ColumnSelection origColumns = columnsPub[i];
                        ColumnSelection columns = table.getColumnSelection(true);

                        if(origColumns.getAttributeCount() == columns.getAttributeCount()) {
                           break;
                        }

                        for(int c = 0; c < origColumns.getAttributeCount(); c++) {
                           DataRef ref = origColumns.getAttribute(c);

                           if(ref instanceof ColumnRef && columns.indexOfAttribute(ref) < 0) {
                              def.addRemovedColumn((ColumnRef) ref);
                           }
                        }
                     }
                  }

                  table.setColumnSelection(columnsPub[i], true);
                  table.setColumnSelection(columnsPrivate[i], false);
               }
            }
         }

         if(isWarned && sheet instanceof Viewsheet) {
            saveViewsheet(identifier, principal);
         }
      }

      private Principal principal;
      private String identifier;
      private AssetEntry entry;
      private AbstractSheet sheet;
      private boolean expanded;
      private boolean bypass;
      private boolean full;

      private MVAnalyzer analyzer;
      private List<MVDef> defs = new ArrayList<>();
      private List<Identity> identities = new ArrayList<>();
      private List<UserInfo> exceptions = new ArrayList<>();
      private List<String> descs = new ArrayList<>();
   }

   // sort MV to place association MV after non-association MV
   private static class MVComparator implements Comparator<MVStatus> {
      @Override
      public int compare(MVStatus mv1, MVStatus mv2) {
         return Boolean.compare(mv1.getDefinition().isAssociationMV(),
                                mv2.getDefinition().isAssociationMV());
      }
   }

   private final ExecutorService analyzePool =
      Executors.newFixedThreadPool(4, new GroupedThreadFactory());
   private final ExecutorService localCreatePool =
      Executors.newFixedThreadPool(2, new GroupedThreadFactory());
   private final ExecutorService remoteCreatePool =
      Executors.newCachedThreadPool(new GroupedThreadFactory());
   public static final String MV_TASK_PREFIX = "MV Task: ";
   public static final String MV_TASK_STAGE_PREFIX = "MV Task Stage 2: ";

   private static final Logger LOG = LoggerFactory.getLogger(MVSupportService.class);

   private static final class GroupedThreadFactory implements ThreadFactory {
      @Override
      public Thread newThread(Runnable r) {
         GroupedThread thread = new GroupedThread(r);
         thread.setDaemon(true);
         return thread;
      }
   }
}
