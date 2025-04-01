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
package inetsoft.sree;

import inetsoft.mv.*;
import inetsoft.report.filter.DefaultComparer;
import inetsoft.report.internal.*;
import inetsoft.report.internal.license.*;
import inetsoft.report.pdf.FontManager;
import inetsoft.sree.internal.*;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.RenameDependencyInfo;
import inetsoft.uql.viewsheet.VSSnapshot;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.RecycleUtils;
import inetsoft.web.admin.deploy.ImportTargetFolderInfo;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the implementation of the RepletRepository. It is the server
 * side report engine for processing replets.
 *
 * @version 7.0
 * @author InetSoft Technology Corp
 */
@SuppressWarnings({
   "SynchronizationOnLocalVariableOrMethodParameter", "SynchronizeOnNonFinalField"
})
public class RepletEngine extends AbstractAssetEngine
   implements RepletRepository, PropertyChangeListener, SessionListener
{
   /**
    * The name for the ServiceRequest parameter. This parameter is
    * only available if the replet is running in pregenerated mode.
    */
   public static final String PARA_PREGENERATED = "pregenerated";

   /**
    * The name for the ServiceRequest parameter. This parameter is
    * only available if the replet is running in live mode.
    */
   public static final String PARA_LIVE = "live";

   /**
    * Main entrance.
    */
   public static void main(String[] args) {
      Catalog.setCatalogGetter(UserEnv.getCatalogGetter());
      RepletEngine engine = new RepletEngine();
      engine.init();
   }

   /**
    * Create a default local replet engine.
    */
   public RepletEngine() {
      cdir = Tool.getCacheDirectory();
      // initialize global viewer actions
      this.scopes = new int[] {GLOBAL_SCOPE, REPORT_SCOPE, USER_SCOPE};
      Arrays.sort(this.scopes);
      getIndexedStorage();

      // initialize id suffix
   }

   /**
    * Get indexed storage.
    */
   private void getIndexedStorage() {
      try {
         istore = IndexedStorage.getIndexedStorage();

         if(istore != null) {
            istore.addTransformListener(new SheetTransformListener());
            LOG.info("Using indexed storage {}", this.istore.getClass());
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to get indexed stoage", ex);
      }
   }

   /**
    * Create a local replet engine.
    * @param id the unique engine ID.
    */
   public RepletEngine(String id) {
      this();

   }

   /**
    * Create a local replet engine and evaluate against a given license key.
    * @param id the unique engine id
    * @param licenseKey the license key for the slave
    */
   @SuppressWarnings("UnusedParameters")
   public RepletEngine(String id, String licenseKey) {
      this();

   }

   /**
    * Checks that there are valid license keys installed.
    */
   private void checkLicenseKeyValidity() {
      LicenseManager licenseManager = LicenseManager.getInstance();
      licenses = licenseManager.getLicenseHash();
      invalidLicenseMessage = null;

      try {
         Set<License> licenses = licenseManager.getClaimedLicenses();

         if(licenses.isEmpty()) {
            Util.showKeyInstruction();
            LOG.error("License key not installed, aborting.");
            throw new LicenseException(
               "License key is not installed " +
               "or duplicate license key in use on the network." +
               "Add a license key on the 'Server' \\>" +
               "'Status' page in Enterprise Manager, or update the " +
               "'license.key' property in the sree.properties file. In a " +
               "cluster environment, each cluster node requires an individual " +
               "key. ");
         }

         if(Tool.isServer()) {
            boolean standalone = false;

            for(License license : licenses) {
               if(license.standalone()) {
                  standalone = true;
               }
            }

            if(!standalone) {
               throw new LicenseException("Standalone key must be present for server to start.");
            }
         }
      }
      catch(LicenseException e) {
         invalidLicenseMessage = e.getMessage();
         throw e;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public RepositoryEntry[] getFolders(Principal principal, EnumSet<ResourceAction> actions) {
      RepositoryEntry[] entries = getFolders0(principal, actions);
      Arrays.sort(entries);
      return entries;
   }

   /**
    * Get accessible folders.
    */
   private RepositoryEntry[] getFolders0(Principal principal, EnumSet<ResourceAction> actions) {
      RepletRegistry registry;
      RepletRegistry userRegistry;
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      try {
         registry = RepletRegistry.getRegistry();
         userRegistry = RepletRegistry.getRegistry(pId);
      }
      catch(Exception e) {
         LOG.warn("Failed to get replet registry for user " + principal, e);
         return new RepositoryEntry[0];
      }

      boolean noMyreports =
         !checkPermission(principal, ResourceType.MY_DASHBOARDS, "*", ResourceAction.READ);

      ArrayList<RepositoryEntry> result = new ArrayList<>();
      addFolders(principal, actions, registry, result, noMyreports);

      if(!noMyreports) {
         addFolders(principal, actions, userRegistry, result, false);
      }

      return result.toArray(new RepositoryEntry[0]);
   }

   /**
    * Add all folders.
    */
   private void addFolders(Principal principal, EnumSet<ResourceAction> actions,
      RepletRegistry registry, ArrayList<RepositoryEntry> result,
      boolean noMyreports)
   {
      if(registry == null || result == null) {
         return;
      }

      Deque<String> stack = new ArrayDeque<>();
      stack.addLast("/");

      while(!stack.isEmpty()) {
         String parent = stack.removeLast();

         if(checkPermission(principal, ResourceType.REPORT, parent, actions)) {
            RepletFolderEntry entry = new RepletFolderEntry(parent);
            entry.setDescription(registry.getFolderDescription(parent));
            entry.setAlias(registry.getFolderAlias(parent));

            if(!result.contains(entry)) {
               result.add(entry);
            }
         }

         String[] folders = noMyreports ?
            registry.getFolders(parent, true) : registry.getFolders(parent);

         for(String folder : folders) {
            if(checkPermission(
               principal, ResourceType.REPORT, folder, EnumSet.of(ResourceAction.READ)))
            {
               stack.addLast(folder);
            }
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean containsFolder(Principal principal, ResourceAction action) {
      RepositoryEntry[] entries =
         getFolders0(principal, action == null ? null : EnumSet.of(action));
      return entries.length > 0;
   }

   /**
    * Get replet registry.
    * @param name the specified folder or replet name.
    * @param principal the specified user.
    * @return replet registry contains the specified folder or replet name.
    */
   protected RepletRegistry getRegistry(String name, Principal principal)
      throws Exception
   {
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      return SUtil.isMyReport(name) ?
         RepletRegistry.getRegistry(pId) :
         RepletRegistry.getRegistry();
   }

   /**
    * Get security engine.
    * @return security engine if any, null otherwise.
    */
   protected SecurityEngine getSecurity() {
      try {
         return SecurityEngine.getSecurity();
      }
      catch(Exception ex) {
         LOG.error("Failed to get security engine", ex);
      }

      return null;
   }

   /**
    * Initialize the engine. This method must be called once before the
    * engine is used.
    */
   public void init() {
      // for init might be called after restart, here we recall
      // initAsset to set up asset runtime environment if any...

      if(threadPool == null) {
         // default 5 threads per cpu for here most runnables are light-weight
         threadPool = new ThreadPool(
            "RepletEngine", 5, "repletEngine.thread.count", 2);
         LOG.debug(
            "Max number of RepletEngine processor: {}, {}",
            threadPool.getSoftLimit(), threadPool.getHardLimit());
      }

      try {
         checkLicenseKeyValidity();
      }
      catch(LicenseException e) {
         LoggerFactory.getLogger("inetsoft.LicenseManager").error(e.getMessage());
      }

      LicenseManager licenseManager = LicenseManager.getInstance();

      if(!licenseManager.isMasterLicense()) {
         licenseManager.startDuplicateLicenseServer();
      }

      if(licenseManager.isElasticLicense()) {
         licenseManager.startElasticPolling();
      }

      if(!scriptEnvInitialized) {
         scriptEnvInitialized = true;
      }

      initLatch = new CountDownLatch(1);

      threadPool.add(new ThreadPool.AbstractPoolRunnable() {
         @Override
         public void run() {
            try {
               DataCycleManager dmgr = DataCycleManager.getDataCycleManager();

               if(dmgr != null) {
                  MVManager mgr = MVManager.getManager();

                  if(mgr != null) {
                     String dcycle = dmgr.getDefaultCycle();

                     if(dcycle != null) {
                        mgr.setDefaultCycle(dcycle);
                     }
                  }
               }

               // pre-load fonts
               if(SreeEnv.getProperty("font.preload").equals("true")) {
                  FontManager.getFontManager();
               }
            }
            finally {
               initLatch.countDown();
            }
         }

         @Override
         public int getPriority() {
            return Thread.MIN_PRIORITY;
         }

         @Override
         public void join(boolean internal) {
         }

         @Override
         public Throwable getError() {
            return null;
         }
      });

      try {
//         resetRepositoryFolder(true, null);  // Reports will be removed
      }
      catch(Throwable ex) {
         LOG.error("Failed to reset repository folder", ex);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String[] getScheduleTasks(Principal principal) throws IOException {
      ScheduleManager manager = ScheduleManager.getScheduleManager();
      String curOrgID = OrganizationManager.getInstance().getCurrentOrgID(principal);
      return getScheduleTasks(principal, manager.getScheduleTasks(curOrgID));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String[] getScheduleTasks(Principal principal,
                                    Collection<ScheduleTask> allTasks)
      throws IOException
   {
      List<String> vec = new ArrayList<>();

      try {
         Map<IdentityID, Boolean> adminUsers = new HashMap<>();
         SecurityProvider provider = getSecurity().getSecurityProvider();

         for(ScheduleTask task : allTasks) {
            if(hasTaskPermission(task, principal)) {
               vec.add(task.getTaskId());
            }
            else if(!ScheduleManager.isInternalTask(task.getTaskId())){
               IdentityID owner = task.getOwner();
               boolean adminUser = adminUsers.computeIfAbsent(owner, u -> provider.checkPermission(
                  principal, ResourceType.SECURITY_USER, owner.convertToKey(), ResourceAction.ADMIN));

               if(adminUser) {
                  vec.add(task.getTaskId());
               }
            }
         }

         String[] tasks = vec.toArray(new String[0]);
         Tool.qsort(tasks, 0, tasks.length - 1, true, new DefaultComparer());
         return tasks;
      }
      catch(Exception ex) {
         throw new IOException("Failed to get scheduled tasks for user: " + principal, ex);
      }
   }

   /**
    * Check if current principal has permission see this task
    * @return whether or not the task can be seen
    */
   public boolean hasTaskPermission(ScheduleTask task, Principal principal) {
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      boolean principalHasPermission =
         checkPermission(principal, ResourceType.SCHEDULE_TASK, task.getTaskId(), ResourceAction.READ) &&
         checkPermission(principal, ResourceType.SCHEDULE_TASK, task.getTaskId(), ResourceAction.WRITE) &&
         checkPermission(principal, ResourceType.SCHEDULE_TASK, task.getTaskId(), ResourceAction.DELETE) &&
         pId != null && pId.orgID.equals(task.getOwner().orgID);
      boolean isOwner = Tool.equals(principal.getName(), task.getOwner().convertToKey());
      boolean internalTaskPermission = ScheduleManager.isInternalTask(task.getTaskId()) &&
         checkPermission(principal, ResourceType.SCHEDULE_TASK, task.getTaskId(), ResourceAction.READ) &&
         XPrincipal.SYSTEM.equals(task.getOwner().name);
      boolean isOwnerAdmin = task.getOwner() != null && checkPermission(
         principal, ResourceType.SECURITY_USER, task.getOwner(), ResourceAction.ADMIN);
      boolean isShareRole = ScheduleManager.getScheduleManager()
         .hasShareGroupPermission(task, principal);

      if(ScheduleManager.isInternalTask(task.getTaskId())) {
         return internalTaskPermission;
      }

      return principalHasPermission || isOwner  || isOwnerAdmin || isShareRole;
   }

   public boolean taskHasShareGroupPermission(IdentityID owner, Principal principal) {
      return ScheduleManager.getScheduleManager().hasShareGroupPermission(owner, principal);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ScheduleTask getScheduleTask(String taskId) {
      return getScheduleTask(taskId, null);
   }

   @Override
   public ScheduleTask getScheduleTask(String taskId, String orgId) {
      if(taskId == null) {
         return null;
      }

      ScheduleManager manager = ScheduleManager.getScheduleManager();
      return manager.getScheduleTask(taskId, orgId);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setScheduleTask(Principal principal, String taskName,
                               ScheduleTask task) throws Exception {
      ScheduleManager manager = ScheduleManager.getScheduleManager();
      manager.setScheduleTask(taskName, task, principal);
   }

   /**
    *{@inheritDoc}
    */
   @Override
   public void removeScheduleTask(Principal principal, String taskName)
      throws Exception
   {
      ScheduleManager manager = ScheduleManager.getScheduleManager();
      manager.removeScheduleTask(taskName, principal);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getProperty(String propname) {
      return SreeEnv.getProperty(propname);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void changePassword(Principal principal, String passwd)
      throws SRSecurityException
   {
      SecurityEngine.getSecurity().changePassword(principal, passwd);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean checkPermission(Principal principal, ResourceType type, String resource,
                                  ResourceAction action)
   {
      // admin has any permission, anyone may acess transient preview report
      if(type == ResourceType.REPORT && SUtil.isPreviewReport(resource) ||
         action == null)
      {
         return true;
      }

      // anyone may access my report if got the my reports permission
      if((type == ResourceType.REPORT || type == ResourceType.ASSET) && SUtil.isMyReport(resource)) {
         return checkPermission(principal, ResourceType.MY_DASHBOARDS, "*", ResourceAction.READ);
      }

      try {
         return SecurityEngine.getSecurity().checkPermission(principal, type, resource, action);
      }
      catch(Exception ex) {
         LOG.error(
            "Failed to check permission ({}) on {} for user {}", action, resource, principal, ex);
         return false;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean checkPermission(Principal principal, ResourceType type, IdentityID identityID,
                                  ResourceAction action)
   {
      // admin has any permission, anyone may acess transient preview report
      if(type == ResourceType.REPORT && SUtil.isPreviewReport(identityID.name) ||
         action == null)
      {
         return true;
      }

      // anyone may access my report if got the my reports permission
      if((type == ResourceType.REPORT || type == ResourceType.ASSET) && SUtil.isMyReport(identityID.name)) {
         return checkPermission(principal, ResourceType.MY_DASHBOARDS, "*", ResourceAction.READ);
      }

      try {
         return SecurityEngine.getSecurity().checkPermission(principal, type, identityID, action);
      }
      catch(Exception ex) {
         LOG.error(
            "Failed to check permission ({}) on {} for user {}", action, identityID, principal, ex);
         return false;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setPermission(Principal principal, ResourceType type, String resource,
                             Permission permission) throws SRSecurityException
   {
      if(permission == null) {
         SecurityEngine.getSecurity().removePermission(type, resource);
      }
      else {
         SecurityEngine.getSecurity().setPermission(type, resource, permission);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean checkPermission(Principal principal, ResourceType type,
                                  String resource, EnumSet<ResourceAction> actions)
   {
      if(actions == null) {
         return true;
      }

      for(ResourceAction action : actions) {
         if(!checkPermission(principal, type, resource, action)) {
            return false;
         }
      }

      return true;
   }

   @Override
   protected void finalize() throws Throwable {
      Enumeration<FileInfo> iter = resmap.elements();

      while(iter.hasMoreElements()) {
         FileInfo info = iter.nextElement();

         info.close();
         info.delete();
      }

      super.finalize();
   }

   /**
    * Check if the access matches license.
    */
   public void checkAccess(Principal principal) throws RepletException {
      // update keys if they have been changed
      if(!LicenseManager.getInstance().getLicenseHash().equals(licenses)) {
         checkLicenseKeyValidity();
      }

      if(invalidLicenseMessage != null) {
         throw new LicenseException(invalidLicenseMessage);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   @SuppressWarnings("unchecked")
   public RepositoryEntry[] getRepositoryEntries(String folder, Principal user,
      ResourceAction action, int selector, boolean isDefaultOrgAsset) throws RemoteException
   {
      return getRepositoryEntries(
         folder, user, action == null ? null : EnumSet.of(action), selector, isDefaultOrgAsset);
   }

   @Override
   public RepositoryEntry[] getRepositoryEntries(String folder, Principal user,
                                                 EnumSet<ResourceAction> actions,
                                                 int selector, boolean isDefaultOrgAsset)
      throws RemoteException
   {
      IdentityID pId = user == null ? null : IdentityID.getIdentityIDFromKey(user.getName());

      if(folder == null) {
         folder = "/";
      }

      // for administrative reports, we needn't show myreport and archive
      boolean myreport = SUtil.isMyReport(folder);
      List<RepositoryEntry> result = new ArrayList<>();
      RepletRegistry registry;

      try {
         registry = getRegistry(folder, user);

         if(rts == -1) {
            rts = registry.getLastModified();
         }

         if(ats == -1) {
            ats = istore.lastModified();
         }
      }
      catch(Exception ex) {
         throw new RemoteException("Failed to reset repository folder", ex);
      }

      String orgId = OrganizationManager.getInstance().getCurrentOrgID();

      if(SUtil.isDefaultVSGloballyVisible(user) && isDefaultOrgAsset) {
         orgId = Organization.getDefaultOrganizationID();
      }

      // get folders
      if((selector & RepositoryEntry.FOLDER) != 0 || isDefaultOrgAsset) {
         boolean noMyreports = user == null || isDefaultOrgAsset ||
            !checkPermission(user, ResourceType.MY_DASHBOARDS, "*", ResourceAction.READ);

         String[] repletFolders = noMyreports ? registry.getFolders(folder, noMyreports, orgId) :
            registry.getFolders(folder, orgId);
         List<String> addedFolders = new ArrayList<>();

         for(String repletFolder : repletFolders) {
            if(isDefaultOrgAsset || checkPermission(user, ResourceType.REPORT, repletFolder, actions)) {
               RepletFolderEntry entry =
                  new RepletFolderEntry(repletFolder, myreport ? pId : null);
               entry.setDescription(registry.getFolderDescription(repletFolder));
               entry.setAlias(registry.getFolderAlias(repletFolder, orgId));

               if(SUtil.isDefaultVSGloballyVisible(user) && !Tool.equals(((XPrincipal)user).getOrgId(), orgId)) {
                  entry.setDefaultOrgAsset(true);
               }

               result.add(entry);
            }
         }
      }

      // get viewsheets
      if((selector & RepositoryEntry.VIEWSHEET) != 0 || isDefaultOrgAsset) {
         AssetRepository assetRepository = AssetUtil.getAssetRepository(false);
         int scope = myreport ? AssetRepository.USER_SCOPE : AssetRepository.GLOBAL_SCOPE;
         String ppath = myreport ? Tool.MY_DASHBOARD.equals(folder) ? "/" :
            folder.substring(Tool.MY_DASHBOARD.length() + 1): folder;
         AssetEntry pentry = new AssetEntry(scope,
            AssetEntry.Type.REPOSITORY_FOLDER, ppath, myreport ? pId : null, orgId);
         AssetEntry[] assets = new AssetEntry[0];

         try {
            assets = assetRepository.getEntries(pentry, user, ResourceAction.READ,
               new AssetEntry.Selector(AssetEntry.Type.VIEWSHEET_SNAPSHOT));
         }
         catch(Exception ignore) {
         }

         for(AssetEntry asset : assets) {
            String path = asset.getPath();

            if(myreport && !path.startsWith(Tool.MY_DASHBOARD)) {
               path = Tool.MY_DASHBOARD + "/" + path;
            }

            ViewsheetEntry ventry = new ViewsheetEntry(path, asset.getUser());
            ventry.setAssetEntry(asset);
            boolean onReport = false;

            if(asset.isVSSnapshot()) {
               try {
                  VSSnapshot snap = (VSSnapshot) assetRepository.getSheet(
                     asset, user, false, AssetContent.ALL);
                  AssetEntry[] entries = snap.getOuterDependents();

                  // always show snapshot even lost the ref to its base
                  onReport = entries[0] == null || entries[0].getProperty("onReport") == null ||
                     entries[0].getProperty("onReport").equals("true");
               }
               catch(Exception ex) {
                  // ignore
               }
            }
            else {
               onReport = "true".equals(asset.getProperty("onReport"));
            }

            if(isDefaultOrgAsset || asset.getType() == AssetEntry.Type.VIEWSHEET &&
               SUtil.isDefaultVSGloballyVisible(user) &&
               Tool.equals(asset.getOrgID(), Organization.getDefaultOrganizationID()) &&
               !Tool.equals(asset.getOrgID(), orgId))
            {
               ventry.setDefaultOrgAsset(true);
            }

            String desc =
               asset.getProperty(AssetEntry.SHEET_DESCRIPTION);
            ventry.setOnReport(onReport);
            ventry.setIdentifier(asset.toIdentifier());
            ventry.setSnapshot(asset.isVSSnapshot());
            ventry.setDescription(desc);
            ventry.setAlias(asset.getAlias());
            ventry.setAssetEntry(asset);

            result.add(ventry);
         }
      }

      // sort entries
      @SuppressWarnings("rawtypes")
      Comparator comp = null;

      String sclass = SreeEnv.getProperty("repository.tree.sorter");

      try {
         if(sclass != null) {
            comp = (Comparator<?>) Class.forName(Tool.convertUserClassName(sclass)).newInstance();
         }
      }
      catch(Throwable exc) {
         LOG.warn("Failed to instantiate tree sort class (repository.tree.sorter): " + sclass, exc);
      }

      if(comp == null) {
         Collections.sort(result);
      }
      else {
         result.sort(comp);
      }

      RepositoryEntry[] entries = new RepositoryEntry[result.size()];
      result.toArray(entries);

      return entries;
   }

   public RepositoryEntry[] getDefaultOrgRepositoryEntries(Principal principal) {
      try {
         return getRepositoryEntries("/", principal, (ResourceAction) null, RepositoryEntry.ALL, true);
      }
      catch(Exception e) {
         LOG.error("Error getting default org's repository assets: "+e);
      }

      return new RepositoryEntry[0];
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean isFolderChanged(String folder, Principal user)
      throws RemoteException
   {
      RepletRegistry registry;
      RepletRegistry myRegistry;

      if(folder == null) {
         folder = "/";
      }

      try {
         registry = getRegistry(folder, user);
         myRegistry = getRegistry(Tool.MY_DASHBOARD, user);
      }
      catch(Exception ex) {
         throw new RemoteException(
            "Failed to get replet registry containing folder " + folder +
            " for user " + user, ex);
      }

      long ts = registry.getLastModified();

      if(rts == -1L) {
         rts = ts;
      }
      else if(rts != ts) {
         rts = ts;
         return true;
      }

      ts = myRegistry.getLastModified();

      if(myrts == -1L) {
         myrts = ts;
      }
      else if(myrts != ts) {
         myrts = ts;
         return true;
      }

      ts = istore.lastModified();

      if(ats == -1L) {
         ats = ts;
      }
      else if(ats != ts) {
         ats = ts;
         return true;
      }

      return false;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void changeFolder(RepositoryEntry entry, String folder,
                            Principal user) throws RemoteException
   {
      writeLock.lock();

      try {
         // does not have write permission of the entry?
         if(!checkPermission(
            user, ResourceType.REPORT, entry.getPath(),
            EnumSet.of(ResourceAction.WRITE, ResourceAction.DELETE)))
         {
            Catalog userCatalog = user == null ? catalog : Catalog.getCatalog(user);
            throw new MessageException(userCatalog.getString(
               "common.deleteAuthority", entry.getPath()));
         }

         // does not have write permission of the folder?
         if(!checkPermission(user, ResourceType.REPORT, folder, EnumSet.of(ResourceAction.WRITE))) {
            Catalog userCatalog = user == null ? catalog : Catalog.getCatalog(user);
            throw new MessageException(userCatalog.getString(
               "common.writeAuthority", folder));
         }

         String name = entry.getName();
         String npath = folder.equals("/") ? "" : folder + "/";
         npath = npath + name;

         // change folder for a folder?
         if(entry.isFolder()) {
            try {
               RepletRegistry registry = getRegistry(entry.getPath(), user);
               String msg = registry.changeFolder(entry.getPath(), npath, user);

               if("true".equals(msg)) {
                  registry.save();
               }
               else {
                  throw new RemoteException(msg);
               }
            }
            catch(Exception ex) {
               throw new RemoteException(
                  "Failed to change registry folder from " + entry.getPath() +
                     " to " + npath, ex);
            }
         }
         else {
            throw new RemoteException("Unsupported entry found: " + entry);
         }
      }
      finally {
         writeLock.unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void removeRepositoryEntry(RepositoryEntry entry, Principal user)
      throws RemoteException
   {
      // does not have delete permission of the entry?
      if(!checkPermission(
         user, ResourceType.REPORT, entry.getPath(), EnumSet.of(ResourceAction.DELETE)))
      {
         throw new RemoteException("Delete access denied: " + entry.getPath());
      }

      // remove a folder?
      if(entry.isFolder()) {
         try {
            RepletRegistry registry = getRegistry(entry.getPath(), user);
            registry.removeFolder(entry.getPath(), true, true);
         }
         catch(Exception ex) {
            throw new RemoteException(
               "Failed to remove folder " + entry.getPath(), ex);
         }
      }
      else {
         throw new RemoteException("Unsupported entry found: " + entry);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void renameRepositoryEntry(RepositoryEntry entry, String nname,
                                     Principal user) throws RemoteException {
      IdentityID pId = user == null ? null : IdentityID.getIdentityIDFromKey(user.getName());
      // does not have write permission of the entry?
      if(!checkPermission(
         user, ResourceType.REPORT, entry.getPath(), EnumSet.of(ResourceAction.WRITE)))
      {
         throw new RemoteException(catalog.getString(
            "common.writeAuthority", entry.getPath()));
      }

      // does not have delete permission of the entry?
      if(!checkPermission(
         user, ResourceType.REPORT, entry.getPath(), EnumSet.of(ResourceAction.DELETE)))
      {
         throw new RemoteException(catalog.getString(
            "common.deleteAuthority", entry.getPath()));
      }

      RepletRegistry registry;

      try {
         registry = getRegistry(entry.getPath(), user);
      }
      catch(Exception ex) {
         throw new RemoteException(
            "Failed to get replet registry containing " + entry.getPath() +
            " for user " + user, ex);
      }

      String oalias = entry.isFolder() ?
         registry.getFolderAlias(entry.getPath()) : null;
      boolean reAlias = oalias != null && !"".equals(oalias);
      String nalias = null;

      if(reAlias) {
         int idx = nname.lastIndexOf('/');
         nalias = idx == -1 ? nname : nname.substring(idx + 1);
      }

      try {
         if(!Tool.equals(nname, entry.getPath()) &&
            SUtil.isDuplicatedViewsheet(this, nname, pId) && !reAlias)
         {
            throw new RuntimeException(
               catalog.getString("em.viewsheet.duplicateName"));
         }
      }
      catch(Exception ex) {
         throw new RemoteException(ex.getMessage(), ex);
      }

      // rename a folder?
      if(entry.isFolder()) {
         try {
            if(reAlias) {
               registry.setFolderAlias(entry.getPath(), nalias, true);
            }
            else {
               String msg = registry.changeFolder(entry.getPath(), nname, user);

               if(!"true".equals(msg)) {
                  throw new RuntimeException(msg);
               }
            }

            registry.save();
         }
         catch(Exception ex) {
            throw new RemoteException(ex.getMessage(), ex);
         }
      }
      else {
         throw new RemoteException("Unsupported entry found: " + entry);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getEntryIdentifier(AssetEntry entry) {
      return super.getEntryIdentifier(entry);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected boolean checkDataModelFolderPermission(String folder, String source,
                                                    Principal user)
   {
      return SUtil.checkDataModelFolderPermission(folder, source, user);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected boolean checkQueryFolderPermission(String folder, String source,
      Principal user)
   {
      return SUtil.checkQueryFolderPermission(folder, source, user);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected boolean checkQueryPermission(String query, Principal user) {
      return SUtil.checkQueryPermission(query, user);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected boolean checkDataSourcePermission(String dname, Principal user) {
      return SUtil.checkDataSourcePermission(dname, user);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected boolean checkDataSourceFolderPermission(String folder,
      Principal user)
   {
      return SUtil.checkDataSourceFolderPermission(folder, user);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected Map<AssetEntry, AssetEntry> changeFolder0(AssetEntry oentry, IndexedStorage ostorage,
                                                       AssetEntry nentry, IndexedStorage nstorage,
                                                       boolean root) throws Exception
   {
      Map<AssetEntry, AssetEntry> changed =
         super.changeFolder0(oentry, ostorage, nentry, nstorage, root);

      if(oentry.getScope() == GLOBAL_SCOPE && !oentry.isRepositoryFolder()) {
         SecurityEngine security = getSecurity();

         // rename permission
         if(nentry.getScope() == GLOBAL_SCOPE) {
            if(security != null) {
               ResourceType ntype = getAssetResourceType(nentry);
               String nname = nentry.getPath();
               ResourceType otype = getAssetResourceType(oentry);
               String oname = oentry.getPath();
               security.setPermission(ntype, nname, security.getPermission(otype, oname));

               if(!Tool.equals(oname, nname)) {
                  security.removePermission(otype, oname);
               }
            }
         }
         // remove permission
         else {
            if(security != null) {
               ResourceType otype = getAssetResourceType(oentry);
               String oname = oentry.getPath();
               security.removePermission(otype, oname);
            }
         }
      }

      return changed;
   }

   /**
    * Imports assets into the repository.
    */
   public void importAssets(boolean overwriting,
                            final List<String> order,
                            DeploymentInfo info,
                            boolean desktop, Principal principal,
                            List<String> ignoreList,
                            List<String> ignoreUserAsset)
      throws Exception
   {
      importAssets(overwriting, order, info, desktop, principal, ignoreList, null, ignoreUserAsset);
   }

   /**
    * Imports assets into the repository.
    */
   public List<String> importAssets(boolean overwriting,
                                    List<String> order,
                                    DeploymentInfo info,
                                    boolean desktop, Principal principal,
                                    List<String> ignoreList,
                                    ActionRecord actionRecord,
                                    List<String> ignoreUserAssets)
      throws Exception
   {
      return importAssets(overwriting, order, info, desktop, principal,
                          ignoreList, null, actionRecord, ignoreUserAssets);
   }

   /**
    * Imports assets into the repository.
    */
   public List<String> importAssets(boolean overwriting,
                                    List<String> order,
                                    DeploymentInfo info,
                                    boolean desktop, Principal principal,
                                    List<String> ignoreList,
                                    ImportTargetFolderInfo targetFolderInfo,
                                    ActionRecord actionRecord,
                                    List<String> ignoreUserAssets)
      throws Exception
   {
      writeLock.lock();

      try {
         List<String> failedList = new ArrayList<>();
         DeployManagerService.importAssets(
            overwriting, order, info, desktop, principal,
            ignoreList, actionRecord, failedList, targetFolderInfo, ignoreUserAssets);
         return failedList;
      }
      finally {
         writeLock.unlock();
      }
   }

   /**
    * Import assets assets into the repository.
    */
   public void importAssets(byte[] data, boolean replace) throws Exception {
      importAssets(data, replace, null);
   }

   /**
    * Import assets assets into the repository.
    */
   public void importAssets(byte[] data, boolean replace,
                            ActionRecord actionRecord) throws Exception
   {
      writeLock.lock();

      try {
         DeployManagerService.getService().importAssets(data, replace, actionRecord);
      }
      finally {
         writeLock.unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void changeSheet(AssetEntry oentry, AssetEntry nentry, Principal user, boolean force)
      throws Exception
   {
      writeLock.lock();

      try {
         // validate the parent repository folder
         super.changeSheet(oentry, nentry, user, force);

         ScheduleManager mgr = ScheduleManager.getScheduleManager();
         mgr.renameSheetInSchedule(oentry, nentry);
      }
      finally {
         writeLock.unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected void changeSheet0(AssetEntry oentry, IndexedStorage ostorage, AssetEntry nentry,
                               IndexedStorage nstorage, boolean root, boolean callFireEvent)
      throws Exception
   {
      changeSheet0(oentry, ostorage, nentry, nstorage, root, callFireEvent, null);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected void changeSheet0(AssetEntry oentry, IndexedStorage ostorage, AssetEntry nentry,
                               IndexedStorage nstorage, boolean root, boolean callFireEvent,
                               List<RenameDependencyInfo> renameDependencyInfos)
      throws Exception
   {
      super.changeSheet0(oentry, ostorage, nentry, nstorage, root, callFireEvent, renameDependencyInfos);

      if(oentry.getScope() == GLOBAL_SCOPE) {
         SecurityEngine security = getSecurity();

         // rename permission
         if(nentry.getScope() == GLOBAL_SCOPE) {
            if(security != null) {
               ResourceType ntype = getAssetResourceType(nentry);
               String nname = nentry.getPath();
               ResourceType otype = getAssetResourceType(oentry);
               String oname = oentry.getPath();
               Permission temp = security.getPermission(otype, oname);
               security.removePermission(otype, oname);
               security.setPermission(ntype, nname, temp);
            }
         }
         // remove permission
         else {
            if(security != null) {
               ResourceType otype = getAssetResourceType(oentry);
               String oname = oentry.getPath();
               security.removePermission(otype, oname);
            }
         }
      }

      if((oentry.getType() == AssetEntry.Type.VIEWSHEET ||
         oentry.getType() == AssetEntry.Type.WORKSHEET) && !oentry.equals(nentry))
      {
         MVManager manager = MVManager.getManager();
         boolean wsMV = oentry.getType() == AssetEntry.Type.WORKSHEET;
         MVDef[] defs = manager.list(true, def -> def.isWSMV() == wsMV);

         for(MVDef def : defs) {
            MVMetaData data = def.getMetaData();
            boolean changed = false;

            if(def.matches(oentry)) {
               def.setEntry(nentry);
               def.setChanged(true);
               changed = true;
            }
            else if(data.isRegistered(oentry.toIdentifier())) {
               data.renameRegistered(oentry.toIdentifier(), nentry.toIdentifier());
               def.setChanged(true);
               changed = true;
            }

            if(changed) {
               manager.add(def);
            }
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected void removeFolder0(AssetEntry entry, IndexedStorage storage, boolean root)
      throws Exception
   {
      super.removeFolder0(entry, storage, root);

      // remove permission
      if(entry.getScope() == GLOBAL_SCOPE && !entry.isRepositoryFolder()) {
         SecurityEngine security = getSecurity();

         if(security != null) {
            ResourceType otype = getAssetResourceType(entry);
            String oname = entry.getPath();
            security.removePermission(otype, oname);
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected void removeSheet0(AssetEntry entry, IndexedStorage storage, boolean isFolder)
      throws Exception
   {
      super.removeSheet0(entry, storage, isFolder);

      // remove permission
      if(entry.getScope() == GLOBAL_SCOPE) {
         SecurityEngine security = getSecurity();

         if(security != null) {
            ResourceType otype = getAssetResourceType(entry);
            String oname = entry.getPath();
            security.removePermission(otype, oname);
         }
      }

      if(entry.getType() == AssetEntry.Type.VIEWSHEET ||
         entry.getType() == AssetEntry.Type.WORKSHEET)
      {
         SharedMVUtil.removeMV(entry);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void addFolder(AssetEntry entry, Principal user)
      throws Exception
   {
      writeLock.lock();

      try {
         super.addFolder(entry, user);

         // set default permission
         if(entry.getScope() == GLOBAL_SCOPE && !entry.isWorksheetFolder() &&
            !entry.isRepositoryFolder() && user != null)
         {
            String name = getAssetResourceType(entry) + entry.getPath();
            Set<String> users = Collections.singleton(user.getName());
            Permission perm = new Permission();
            String orgId = OrganizationManager.getInstance().getCurrentOrgID();
            perm.setUserGrantsForOrg(ResourceAction.READ, users, orgId);
            perm.setUserGrantsForOrg(ResourceAction.WRITE, users, orgId);
            perm.setUserGrantsForOrg(ResourceAction.DELETE, users, orgId);

            setPermission(user, ResourceType.REPORT, name, perm);
         }
      }
      finally {
         writeLock.unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setSheet(AssetEntry entry, AbstractSheet ws, Principal user,
                        boolean force, boolean checkDependency, boolean updateDependency,
                        boolean checkCrossJoins)
      throws Exception
   {
      writeLock.lock();

      try {
         final int scope = entry.getScope();
         super.setSheet(entry, ws, user, force, checkDependency, updateDependency, checkCrossJoins);

         long ts = istore.lastModified();

         if(ats == -1L) {
            ats = ts;
         }
         else if(ats != ts) {
            ats = ts;
         }
      }
      finally {
         writeLock.unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void syncFolders(Principal user) throws Exception {
      // force logic in resetRepositoryFolder
      if(user != null) {
         UserEnv.setProperty(user, "mts", null);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public AssetEntry[] getEntries(AssetEntry entry, Principal user,
                                  ResourceAction action,
                                  AssetEntry.Selector selector)
      throws Exception
   {
      AssetEntry[] entries = super.getEntries(entry, user, action, selector);

      for(AssetEntry aentry : entries) {
         if(aentry.getType() == AssetEntry.Type.VIEWSHEET ||
            aentry.getType() == AssetEntry.Type.VIEWSHEET_SNAPSHOT ||
            aentry.getType() == AssetEntry.Type.VIEWSHEET_BOOKMARK ||
            aentry.getType() == AssetEntry.Type.WORKSHEET)
         {
            String desc = aentry.getDescription();
            desc = desc.substring(0, desc.indexOf('/') + 1);
            String path = aentry.getPath();

            if(aentry.getType() == AssetEntry.Type.WORKSHEET) {
               // WS folders don't have aliases and this will prevent WS folders from
               // using the alias of repository folders of the same name
               desc += AssetUtil.localizeAssetEntry(path, user, aentry,
                  aentry.getScope() == AssetRepository.USER_SCOPE);
            }
            else {
               desc += SUtil.localizeAssetEntry(path, user, true, aentry,
                  aentry.getScope() == AssetRepository.USER_SCOPE);
            }

            aentry.setProperty("_description_", desc);
         }
      }

      return entries;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void changeFolder(AssetEntry oentry, AssetEntry nentry,
                            Principal user, boolean force)
           throws Exception
   {
      changeFolder(oentry, nentry, user, force, true);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void changeFolder(AssetEntry oentry, AssetEntry nentry,
                            Principal user, boolean force, boolean callFireEvent)
      throws Exception
   {
      writeLock.lock();

      try {
         if("false".equals(SreeEnv.getProperty("fs.desktop"))) {
            if(oentry.isRepositoryFolder()) {
               throw new MessageException(catalog.getString(
                       "common.invalidEntry", oentry));
            }
            else if(nentry.isRepositoryFolder()) {
               throw new MessageException(catalog.getString(
                       "common.invalidEntry", nentry));
            }
         }

         super.changeFolder(oentry, nentry, user, force, callFireEvent);
      }
      finally {
         writeLock.unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void removeFolder(AssetEntry entry, Principal user, boolean force)
      throws Exception
   {
      // repository folder? only after the folder is removed from repository
      // could we remove the physical folder from asset repository, and the
      // person should be an admininistrator
      if(entry.isRepositoryFolder()) {
         String path = entry.getPath();
         boolean ismy = entry.getUser() != null;
         path = ismy ? Tool.MY_DASHBOARD + "/" + path : path;
         RepletRegistry registry = null;

         try {
            registry = RepletRegistry.getRegistry(entry.getUser());
         }
         catch(Exception ex) {
            LOG.error("Failed to get replet registry", ex);
         }

         if(registry != null && registry.isFolder(path)) {
            throw new MessageException(catalog.getString(
               "common.deleteRepFolder", entry));
         }
      }

      super.removeFolder(entry, user, force);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void checkFolderRemoveable(AssetEntry entry, Principal user)
      throws Exception
   {
      if(entry.isRepositoryFolder()) {
         throw new MessageException(catalog.getString(
            "common.invalidEntry", entry));
      }

      super.checkFolderRemoveable(entry, user);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void renameUser(IdentityID oname, IdentityID nname) throws Exception {
      if(oname == null || nname == null || Tool.equals(oname, nname)) {
         return;
      }

      super.renameUser(oname, nname);

      AssetEntry oentry = new AssetEntry(AssetRepository.USER_SCOPE,
         AssetEntry.Type.REPOSITORY_FOLDER, "/", oname);
      AssetEntry nentry = new AssetEntry(AssetRepository.USER_SCOPE,
         AssetEntry.Type.REPOSITORY_FOLDER, "/", nname);
      IndexedStorage ostorage = getStorage(oentry);
      IndexedStorage nstorage = getStorage(nentry);

      Map<AssetEntry, AssetEntry> changed = changeFolder0(oentry, ostorage, nentry, nstorage, true);

      for(Map.Entry<AssetEntry, AssetEntry> e : changed.entrySet()) {
         if(e.getKey().isViewsheet()) {
            AssetEntry bentry = getVSBookmarkEntry(e.getKey(), oname, true);
            AssetEntry nbentry = getVSBookmarkEntry(e.getValue(), nname, true);
            renameVSBookmark0(bentry, nbentry);
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void removeUser(IdentityID identityID) throws Exception {
      super.removeUser(identityID);
      AssetEntry entry = new AssetEntry(AssetRepository.USER_SCOPE,
         AssetEntry.Type.REPOSITORY_FOLDER, "/", identityID);
      IndexedStorage storage = getStorage(entry);

      removeFolder0(entry, storage, true);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected void fireEvent(int entryType, int changeType, AssetEntry assetEntry,
                            String oldName, boolean root, AbstractSheet sheet, String reason)
   {
      super.fireEvent(entryType, changeType, assetEntry, oldName, root, sheet, reason);
      ScheduleManager manager = ScheduleManager.getScheduleManager();

      if(changeType == AssetChangeEvent.ASSET_RENAMED) {
         AssetEntry oentry = AssetEntry.createAssetEntry(oldName, assetEntry.getOrgID());
         manager.assetRenamed(oentry, assetEntry, assetEntry.getOrgID());
      }
      else if(changeType == AssetChangeEvent.ASSET_DELETED) {
         manager.assetRemoved(assetEntry, assetEntry.getOrgID());
      }
   }

   @Override
   public void propertyChange(PropertyChangeEvent evt) {
      String name = evt.getPropertyName();
      Object oval = evt.getOldValue();
      Object nval = evt.getNewValue();

      // @by stephenwebster, In parts of RepletRegistry, the source is followed
      // by a flag that indicates whether the change event was caused by a direct
      // change to the asset or whether it was a result of an external change.
      // This is referred to as 'transaction', and is now referred to as
      // 'directChange' in this method. This is important, since many modifications
      // which are triggered by a folder rename may not need to be done or repeated
      // on subsequent replet change events or folder change events.

      // rename registry folder? rename archive folder and permission as well
      if(name.equals(RepletRegistry.RENAME_FOLDER_EVENT)) {
         if(oval == null && nval == null) {
            // set alias, bail out
            return;
         }

         String source = (String) evt.getSource();
         int index = source.indexOf('^');
         IdentityID user = null;

         if(index != -1) {
            user = IdentityID.getIdentityIDFromKey(source.substring(index + 1));
            source = source.substring(0, index);
         }

         boolean directChange = source.endsWith("_true");
         String nname = (String) nval;
         String oname = (String) oval;
         boolean ismy = user != null;

         // change asset folder as well
         if(directChange) {
            String assetNname = ismy ? nname.substring(Tool.MY_DASHBOARD.length() + 1) : nname;
            String assetOname = ismy && oname != null ?
               oname.substring(Tool.MY_DASHBOARD.length() + 1) : oname;
            AssetEntry oentry = new AssetEntry(ismy ? USER_SCOPE : GLOBAL_SCOPE,
                                               AssetEntry.Type.REPOSITORY_FOLDER, assetOname, user);
            AssetEntry nentry = new AssetEntry(ismy ? USER_SCOPE : GLOBAL_SCOPE,
                                               AssetEntry.Type.REPOSITORY_FOLDER, assetNname, user);

            try {
               // If restore folder from recycle bin, it will using change folder to change.
               if(containsEntry(oentry) || nname.contains(RecycleUtils.RECYCLE_BIN_FOLDER)) {
                  super.changeFolder(oentry, nentry, null, true, true);
               }
            }
            catch(Exception ex) {
               LOG.error("Failed to change folder from {}  to {}", oentry, nentry, ex);
            }
         }

         if(!SUtil.isMyReport(oname)) {
            SecurityEngine security = getSecurity();

            if(security != null) {
               security.setPermission(
                  ResourceType.REPORT, nname,
                  security.getPermission(ResourceType.REPORT, oname));

               if(!Tool.equals(nname, oname)) {
                  security.removePermission(ResourceType.REPORT, oname);
               }
            }
         }

         // @by stephenwebster, For bug1408723303556
         // directChange will be true only for the top-level folder which is
         // renamed, therefore renaming nested folders is redundant.
         if(directChange) {
            ScheduleManager manager = ScheduleManager.getScheduleManager();
            String orgID = OrganizationManager.getInstance().getCurrentOrgID();
            manager.folderRenamed(oname, nname, user == null ? null : user.name, orgID);
         }
      }
      else if(name.equals(RepletRegistry.ADD_FOLDER_EVENT)) {
         String folderName = (String) nval;
         String source = (String) evt.getSource();
         int index = source.indexOf('^');
         IdentityID user = null;

         if(index != -1) {
            user = IdentityID.getIdentityIDFromKey(source.substring(index + 1));
         }

         AssetEntry nasset = null;
         boolean isMy = SUtil.isMyReport(folderName);

         if(isMy) {
            nasset = new AssetEntry(AssetRepository.USER_SCOPE,
               AssetEntry.Type.REPOSITORY_FOLDER, folderName.replace("My Dashboards/", ""), user);
         }
         else {
            nasset = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
               AssetEntry.Type.REPOSITORY_FOLDER, folderName, null);
         }

         if(!containsEntry(nasset)) {
            try {
               if(isMy && user != null) {
                  XPrincipal user0 = (XPrincipal) ThreadContext.getContextPrincipal();
                  XPrincipal principal = new XPrincipal(user);

                  // For SSO, it will create user from SRPrincipal, but for others, it will get user
                  // from provider. So SSO should add roles and groups for permission check.
                  if(!"true".equals(user0.getProperty("__internal__"))) {
                     principal = new SRPrincipal(user);
                     principal.setRoles(user0.getRoles());
                     principal.setGroups(user0.getGroups());
                  }

                  addFolder(nasset, isMy ? principal : null);
               }
               else if(!isMy) {
                  addFolder(nasset, null);
               }
            }
            catch(Exception ex) {
               LOG.error("Failed to add folder: " + nval, ex);
            }
         }
      }
      else if(name.equals(RepletRegistry.RENAME_FOLDER_ALIAS_EVENT)) {
         if(oval == null || nval == null) {
            return;
         }

         String source = (String) evt.getSource();
         int index = source.indexOf('^');
         IdentityID user = null;

         if(index != -1) {
            user = IdentityID.getIdentityIDFromKey(source.substring(index + 1));
            source = source.substring(0, index);
         }

         boolean directChange = source.endsWith("_true");
         String nalias = (String) nval;
         String assetName0 = (String) oval;
         boolean ismy = user != null;

         // change asset folder as well
         if(directChange) {
            String assetName =
               ismy ? assetName0.substring(Tool.MY_DASHBOARD.length() + 1) : assetName0;
            AssetEntry oentry = new AssetEntry(ismy ? USER_SCOPE : GLOBAL_SCOPE,
                                               AssetEntry.Type.REPOSITORY_FOLDER, assetName, user);
            AssetEntry nentry = new AssetEntry(ismy ? USER_SCOPE : GLOBAL_SCOPE,
                                               AssetEntry.Type.REPOSITORY_FOLDER, assetName, user);
            nentry.setAlias(nalias);

            try {
               super.changeFolder(oentry, nentry, null, true, true);
            }
            catch(Exception ex) {
               LOG.error("Failed to change folder from " + oentry +
                     " to " + nentry, ex);
            }
         }
      }
      // remove registry folder? remove archive folder and permission as well
      else if(name.equals(RepletRegistry.REMOVE_FOLDER_EVENT)) {
         String source = (String) evt.getSource();
         int index = source.indexOf('^');
         IdentityID user = null;

         if(index != -1) {
            user = IdentityID.getIdentityIDFromKey(source.substring(index + 1));
            source = source.substring(0, index);
         }

         boolean directChange = source.endsWith("_true");
         String path = (String) oval;
         boolean ismy = user != null;

         // remove asset folder as well
         if(directChange) {
            String apath = ismy ?
               path.substring(Tool.MY_DASHBOARD.length() + 1) : path;
            AssetEntry entry = new AssetEntry(ismy ? USER_SCOPE : GLOBAL_SCOPE,
               AssetEntry.Type.REPOSITORY_FOLDER, apath, user);

            try {
               if(containsEntry(entry)) {
                  super.removeFolder(entry, null, true);
               }
            }
            catch(Exception ex) {
               LOG.error("Failed to remove folder " + entry, ex);
            }
         }

         if(!SUtil.isMyReport(path)) {
            SecurityEngine security = getSecurity();

            if(security != null) {
               security.removePermission(ResourceType.REPORT, path);
            }
         }
      }
      // rename registry replet? rename permission as well
      else if(name.equals(RepletRegistry.RENAME_REPLET_EVENT)) {
         String source = (String) evt.getSource();
         int index = source.indexOf('^');
         String user = null;
         boolean directChange = source.endsWith("_true");

         if(index != -1) {
            user = source.substring(index + 1);
         }

         String nname = (String) nval;
         String oname = (String) oval;

         if(!SUtil.isMyReport(oname)) {
            SecurityEngine security = getSecurity();

            if(security != null) {
               security.setPermission(
                  ResourceType.REPORT, nname, security.getPermission(ResourceType.REPORT, oname));

               if(!Tool.equals(nname, oname)) {
                  security.removePermission(ResourceType.REPORT, oname);
               }
            }
         }

         // @by stephenwebster, For bug1408723303556
         // If directChange is true, this means it comes from a direct rename of a replet.
         // Otherwise it is false indicating that the change was triggered by a folder rename.
         // In that case, the ScheduleManager folderRenamed method will handle updating the path
         // to any schedule actions having replets defined in that path.
         if(directChange) {
            ScheduleManager manager = ScheduleManager.getScheduleManager();
            manager.repletRenamed(oname, nname, user);
         }
      }
      // remove registry replet? remove permission as well
      else if(name.equals(RepletRegistry.REMOVE_REPLET_EVENT)) {
         String path = (String) oval;
         String source = (String) evt.getSource();
         int index = source.indexOf('^');
          String user = null;

          if(index != -1) {
             user = source.substring(index + 1);
          }

         if(!SUtil.isMyReport(path)) {
            SecurityEngine security = getSecurity();

            if(security != null) {
               security.removePermission(ResourceType.REPORT, path);
            }
         }

//         ScheduleManager manager = ScheduleManager.getScheduleManager();
//         manager.repletRemoved(path, user);
         //Will be remobe
      }
   }

   @Override
   public synchronized void dispose() {
      if(threadPool != null) {
         threadPool.clear();
         threadPool.interrupt();
         threadPool.dispose();
         threadPool = null;
      }

      if(initLatch != null) {
         try {
            initLatch.await(15L, TimeUnit.SECONDS);
         }
         catch(InterruptedException e) {
            LOG.warn("Interrupted waiting for initialization to complete", e);
         }
      }

      super.dispose();
   }

   protected static final class SessionKey {
      SessionKey(Principal principal) {
         this.principal = principal;
      }

      public Principal getPrincipal() {
         return principal;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         SessionKey that = (SessionKey) o;

         if((principal instanceof SRPrincipal) &&
            ((SRPrincipal) principal).getUser() != null)
         {
            return (that.principal instanceof SRPrincipal) &&
               ((SRPrincipal) that.principal).getUser() != null &&
               ((SRPrincipal) principal).getUser().equals(
                  ((SRPrincipal) that.principal).getUser());

         }

         return principal != null ?
            principal.equals(that.principal) : that.principal == null;

      }

      @Override
      public int hashCode() {
         int hashcode = 0;

         if(principal != null) {
            if((principal instanceof SRPrincipal) &&
               ((SRPrincipal) principal).getUser() != null)
            {
               hashcode = ((SRPrincipal) principal).getUser().hashCode();
            }
            else {
               hashcode = principal.hashCode();
            }
         }

         return hashcode;
      }

      @Override
      public String toString() {
         return "SessionKey[" + principal + "]";
      }

      private final Principal principal;
   }

   /**
    * File info.
    */
   private static class FileInfo {
      public FileInfo(File file, Object id, String rid) {
         this(file, System.currentTimeMillis(), id, rid);
      }

      public FileInfo(File file, long lastModified, Object id, String rid) {
         this.file = file;
         this.lastModified = lastModified;
         this.id = id;
         this.rid = rid;
      }

      public InputStream getInputStream() {
         lastModified = System.currentTimeMillis();
         return input;
      }

      public void setInputStream(InputStream input) {
         this.input = input;
      }

      public void close() {
         IOUtils.closeQuietly(input);
         input = null;
      }

      public void delete() {
         try {
            if(file.exists()) {
               if(!file.delete()) {
                  LOG.warn("Failed to delete file: {}", file);
               }
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to delete file: " + file, ex);
         }
      }

      public File file;
      public long lastModified;
      public Object id; // replet id
      public String rid; // resource id
      private InputStream input;
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public AbstractSheet getSheet(AssetEntry entry, Principal user,
                                 boolean permission, AssetContent ctype)
      throws Exception
   {
      // @by larryl, we call this with null user from many places. Calling
      // checkAccess with null will cause an exception and make server not
      // functional.
      if(user != null) {
         checkAccess(user);
      }

      return super.getSheet(entry, user, permission, ctype);
   }

   /**
    * Get server ip address.
    */
   private static String getIPAddress() {
      long millis = System.currentTimeMillis();

      if(millis - lastUpdateMillis > 5000) {
         try {
            ipAddress = Tool.getIP();
         }
         catch(Throwable ex) {
            // do nothing
         }

         lastUpdateMillis = millis;
      }

      return ipAddress;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void addFolder(RepositoryEntry entry, String name, String alias,
                         String description, Principal user)
         throws RemoteException
   {
      writeLock.lock();

      IdentityID pId = user == null ? null : IdentityID.getIdentityIDFromKey(user.getName());

      try {
         // check if the user has write permissions
         if(!checkPermission(
            user, ResourceType.REPORT, entry.getPath(), EnumSet.of(ResourceAction.WRITE)))
         {
            throw new java.lang.SecurityException(catalog.getString(
               "common.writeAuthority", entry.getPath()));
         }

         AssetEntry assetEntry = entry.getAssetEntry();

         if(assetEntry != null && !containsEntry(assetEntry)) {
            assetEntry.setAlias(alias);
            addFolder(assetEntry, user);
         }

         RepletRegistry registry = SUtil.isMyReport(entry.getPath()) ?
            RepletRegistry.getRegistry(pId) :
               RepletRegistry.getRegistry(null);

         String parentFolder = entry.getPath();
         String folderName = "/".equalsIgnoreCase(parentFolder) ? name :
            parentFolder + "/" + name;

         registry.addFolder(folderName);
         registry.setFolderAlias(folderName, alias);
         registry.setFolderDescription(folderName, description);
         registry.save();
      }
      catch(Exception ex) {
         throw new RemoteException(
            "Failed to add folder " + name + " to " + entry, ex);
      }
      finally {
         writeLock.unlock();
      }
   }

   /**
    * import inserts properties.
    */
   public void setInserts(Properties inserts) {
      this.inserts = inserts;
   }

   /**
    * Check if should log export.
    * @return <tt>true</tt> if should log, <tt>false</tt> otherwise.
    */
   public boolean isLogExport() {
      return logExport;
   }

   /**
    * Obtain a lock on this repository.
    * Used by Analytic Repository to help prevent deadlock
    */
   protected void getLock() {
      writeLock.lock();
   }

   /**
    * Release a lock on this repository.
    * Used by Analytic Repository to help prevent deadlock
    */
   protected void releaseLock() {
      writeLock.unlock();
   }

   @Override
   public void loggedIn(SessionEvent event) {
      // NO-OP
   }

   @Override
   public void loggedOut(SessionEvent event) {
      Principal principal = event.getPrincipal();
      sessionmap.remove(new SessionKey(principal));
   }

    // server ip address
   private static String ipAddress = null;
   private static long lastUpdateMillis = 0;

   // id -> principal
    // fid -> {File, FileInputStream} res
   private Hashtable<String, FileInfo> resmap = new Hashtable<>();
   // id -> FileInfo List
   private Hashtable<Object, List<FileInfo>> resmap2 = new Hashtable<>();
   // principal -> Vector of id
   private Hashtable<SessionKey, Vector<Object>> sessionmap = new Hashtable<>();

   protected String cdir = ".";

   private ThreadPool threadPool = null;
    // pregenerated report which is loading data from disk
   private String licenses;
   private String invalidLicenseMessage = null;
   private long rts = -1L; // the repository.xml file's last modified timestamp
   private long ats = -1L; // the asset.dat file's last modified timestamp
   private long myrts = -1L; // the My Reports/repository.xml last modified timestamp
   private boolean logExport = false;
   private final ReentrantLock writeLock = new ReentrantLock();
   private boolean scriptEnvInitialized = false;
    private CountDownLatch initLatch;

   protected Properties inserts = new Properties(); // html/js inserts

   private static final Logger LOG =
      LoggerFactory.getLogger(RepletEngine.class);
}
