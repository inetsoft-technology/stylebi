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
package inetsoft.report.composition;

import inetsoft.analytic.AnalyticAssistant;
import inetsoft.analytic.composition.SheetLibraryEngine;
import inetsoft.report.composition.execution.BoundTableHelper;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedLong;
import inetsoft.sree.security.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.*;
import inetsoft.util.log.LogLevel;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The default worksheet service implementation, implements all the methods
 * of <tt>WorksheetService</tt> to provide common functions. If necessary,
 * we may chain sub <tt>WorksheetService</tt>s to serve advanced purposes.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class WorksheetEngine extends SheetLibraryEngine implements WorksheetService {
   /**
    * Constructor.
    */
   public WorksheetEngine() throws RemoteException {
      this((AssetRepository) AnalyticAssistant.getAnalyticAssistant()
         .getAnalyticRepository());
      setServer(true);
   }

   /**
    * Constructor.
    * throws RemoteException
    */
   public WorksheetEngine(AssetRepository engine) throws RemoteException {
      Cluster cluster = Cluster.getInstance();
      amap = new RuntimeSheetCache(CACHE_NAME);
      emap = new ConcurrentHashMap<>();
      executionMap = new ExecutionMap();
      renameInfoMap = new HashMap<>();
      nextId = cluster.getLong(NEXT_ID_NAME);

      singlePreviewEnabled = "true".equals(SreeEnv.getProperty("single.preview.enabled"));
      setAssetRepository(engine);
   }

   /**
    * Get the asset repository.
    * @return the associated asset repository.
    */
   @Override
   public AssetRepository getAssetRepository() {
      return engine;
   }

   /**
    * Set the asset repository.
    * @param engine the specified asset repository.
    */
   @Override
   public void setAssetRepository(AssetRepository engine) {
      this.engine = engine;
   }

   /**
    * Get thread definitions of executing event according the id.
    */
   @Override
   public Vector<?> getExecutingThreads(String id) {
      Vector<?> threads = emap == null ? null : emap.get(id);
      return threads != null ? threads : new Vector<>();
   }

   /**
    * Check if the specified entry is duplicated.
    * @param engine the specified engine.
    * @param entry the specified entry.
    * @return <tt>true</tt> if duplicated, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isDuplicatedEntry(AssetRepository engine, AssetEntry entry)
      throws Exception
   {
      return AssetUtil.isDuplicatedEntry(engine, entry);
   }

   /**
    * Localize the specified asset entry.
    */
   @Override
   public String localizeAssetEntry(String path, Principal principal,
                                    boolean isReplet, AssetEntry entry,
                                    boolean isUserScope)
   {
      if(isUserScope) {
         path = Tool.MY_DASHBOARD + "/" + path;
      }

      String newPath = AssetUtil.localize(path, principal, entry);
      return isUserScope ?
         newPath.substring(newPath.indexOf('/') + 1) : newPath;
   }

   /**
    * Get the cached propery by providing the specified key.
    */
   @Override
   public Object getCachedProperty(Principal user, String key) {
      // do not cache property
      return null;
   }

   /**
    * Set the cached property by providing the specified key-value pair.
    */
   @Override
   public void setCachedProperty(Principal user, String key, Object val) {
      // do not cache property
   }

   /**
    * Dispose the worksheet service.
    */
   @Override
   public void dispose() {
      engine.dispose();

      try {
         amap.close();
      }
      catch(Exception e) {
         LOG.warn("Failed to close runtime sheet cache", e);
      }
   }

   /**
    * Open a temporary worksheet.
    * @param user the specified user.
    * @param aentry the specified AssetEntry.
    * @return the worksheet id.
    */
   @Override
   public String openTemporaryWorksheet(Principal user, AssetEntry aentry) {
      AssetEntry entry = aentry != null ? aentry :
         getTemporaryAssetEntry(user, AssetEntry.Type.WORKSHEET);
      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = new RuntimeWorksheet(entry, ws, user, true);
      rws.setEditable(false);
      return createTemporarySheetId(entry, rws, user);
   }

   /**
    * Creates and sets the identifier of a temporary sheet.
    *
    * @param entry the asset entry for the sheet.
    * @param sheet the sheet.
    * @param user  the user creating the sheet.
    *
    * @return the sheet identifier.
    */
   protected String createTemporarySheetId(AssetEntry entry, RuntimeSheet sheet,
                                           Principal user)
   {
      String id = getNextID(entry, user);
      sheet.setID(id);
      amap.put(id, sheet);
      return id;
   }

   /**
    * Open a preview worksheet.
    * @param id the specified worksheet id.
    * @param name the specified table assembly name.
    * @param user the specified user.
    */
   @Override
   public String openPreviewWorksheet(String id, String name, Principal user)
      throws Exception
   {
      RuntimeWorksheet orws = getWorksheet(id, user);

      if(singlePreviewEnabled && orws.getProperty("__preview_target__") != null) {
         throw new RuntimeException("There is already a preview open!");
      }

      AssetEntry entry = orws.getEntry();
      Worksheet ws = orws.getWorksheet();

      String previewID = openPreviewWorksheet(ws, entry, name, user);

      if(singlePreviewEnabled) {
         RuntimeWorksheet previewWS = getWorksheet(previewID, user);

         if (previewWS != null) {
            previewWS.setProperty("__preview_source__", id);
         }

         orws.setProperty("__preview_target__", previewID);
      }

      return previewID;
   }

   /**
    * Create a preview worksheet.
    */
   protected String openPreviewWorksheet(Worksheet ws, AssetEntry oentry,
                                         String name, Principal user) {
      ws = (Worksheet) ws.clone();
      Assembly[] assemblies = ws.getAssemblies();

      for(Assembly assembly : assemblies) {
         if(assembly.getName().equals(name)) {
            TableAssembly table = (TableAssembly) assembly;
            int count = 30;

            table.setVisible(true);
            table.setPixelOffset(new Point(0, 0));
            table.setRuntime(true);
            table.setIconized(false);
            String pageCountStr =
               SreeEnv.getProperty("asset.preview.pageCount");

            try {
               count = Integer.parseInt(pageCountStr);
            }
            catch(Exception ex) {
               LOG.warn("Invalid value for the maximum preview " +
                     "page count (asset.preview.pageCount): " + pageCountStr, ex);
            }

            Dimension size = table.getPixelSize();
            table.setPixelSize(new Dimension(Math.max(size.width, 5 * AssetUtil.defw), (count + 2) * AssetUtil.defh));
         }
         else {
            ((WSAssembly) assembly).setVisible(false);
            assembly.setPixelOffset(new Point(AssetUtil.defw, AssetUtil.defh));
         }
      }

      ws.layout();

      AssetEntry entry = new AssetEntry(oentry.getScope(), oentry.getType(),
                                        oentry.getPath(), oentry.getUser());
      entry.copyProperties(oentry);
      entry.setProperty("preview", "true");

      if(oentry.getAlias() != null && !oentry.getAlias().isEmpty()) {
         entry.setAlias(oentry.getAlias());
      }

      RuntimeWorksheet rws = new RuntimeWorksheet(entry, ws, user, true);
      rws.setEditable(false);

      String id = getNextID(PREVIEW_WORKSHEET);
      rws.setID(id);
      amap.put(id, rws);
      return id;
   }

   /**
    * Create a runtime sheet.
    * @param entry the specified asset entry.
    * @param sheet the specified sheet.
    * @param user the specified user.
    */
   protected RuntimeSheet createRuntimeSheet(AssetEntry entry,
                                             AbstractSheet sheet,
                                             Principal user)
      throws Exception
   {
      if(entry.isWorksheet()) {
         return new RuntimeWorksheet(entry, (Worksheet) sheet, user, true);
      }

      return null;
   }

   /**
    * Open an exsitent worksheet.
    * @param entry the specified asset entry.
    * @param user the specified user.
    * @return the worksheet id.
    */
   @Override
   public String openWorksheet(AssetEntry entry, Principal user)
      throws Exception
   {
      return openSheet(entry, user);
   }

   /**
    * Open an existing sheet. If the sheet is already open for the user,
    * return the id of the existing sheet.
    * @param entry the specified asset entry.
    * @param user the specified user.
    * @return the sheet id.
    */
   @SuppressWarnings("UnnecessaryContinue")
   protected final String openSheet(AssetEntry entry, Principal user)
      throws Exception
   {
      boolean permission = !"true".equals(entry.getProperty("isDashboard"));
      entry.setProperty("isDashboard", null); // clear the temp property
      AbstractSheet sheet = engine.getSheet(entry, user, permission,
                                            AssetContent.ALL);

      if(sheet == null) {
         throw new ViewsheetException(Catalog.getCatalog().getString(
            "common.sheetCannotFount", entry.toString()));
      }

      // @by larryl, runtime should not be persistent, it's set at runtime.
      // bug1328565116718, it appears the preview view is saved as ws so
      // the primary is marked as runtime and others are hidden, not sure
      // how it got into this state but it should be safe to reset it
      // when opening a new ws
      // @by davyc, clear preview status, don't know why the status wrong,
      // need to reproduce and fix
      // fix bug1341215663394
      entry.setProperty("preview", null);

      for(Assembly obj : sheet.getAssemblies()) {
         if(obj instanceof TableAssembly) {
            ((TableAssembly) obj).setRuntime(false);
         }

         if(obj instanceof WSAssembly && (!(obj instanceof BoundTableAssembly) ||
            !"true".equals(((BoundTableAssembly) obj).getProperty(BoundTableHelper.LOGICAL_BOUND_COPY))))
         {
            ((WSAssembly) obj).setVisible(true);
         }

         if(obj instanceof AbstractWSAssembly) {
            ((AbstractWSAssembly) obj).setOldName(obj.getName());

            if(obj instanceof AbstractTableAssembly) {
               DependencyTransformer.initTableColumnOldNames((AbstractTableAssembly) obj, true);
            }
         }
      }

      sheet = (AbstractSheet) sheet.clone();

      String lockedBy = null;
      int mode0 = getEntryMode(entry);
      // try if this is a request to sync a vs with another
      // (from fullscreen or editing)
      boolean sync = "true".equals(entry.getProperty("sync"));

      if(entry.getProperty("drillfrom") == null && sync) {
         for(String id : amap.keySet()) {
            RuntimeSheet rs2 = amap.get(id);

            if(rs2 == null) {
               continue;
            }

            AssetEntry entry2 = rs2.getEntry();
            Principal user2 = rs2.getUser();

            // ignore temporary sheet
            if(entry2.getScope() == AssetRepository.TEMPORARY_SCOPE) {
               continue;
            }

            if(entry2.getProperty("drillfrom") != null) {
               continue;
            }

            // ignore preview sheet
            if(id.startsWith(PREVIEW_PREFIX)) {
               continue;
            }

            // same sheet?
            if(entry2.equals(entry)) {
               // opened by self twice?
               if(Tool.equals(user2, user)) {
                  // only share runtime sheet when same mode, and the sheet has
                  // not been modified since
                  if(mode0 == rs2.getMode() &&
                     sheet.getLastModified(true) ==
                        rs2.getSheet().getLastModified(true)) {
                     return id;
                  }
                  else {
                     continue;
                  }
               }
               // opened by others?
               else if(rs2.isEditable()) {
                  if(!rs2.isRuntime()) {
                     lockedBy = user2 == null ? null :
                        ((XPrincipal) user2).getFullName();
                  }

                  continue;
               }
            }
         }
      }

      RuntimeSheet rs = createRuntimeSheet((AssetEntry) entry.clone(), sheet, user);

      if(lockedBy != null) {
         rs.setEditable(false);
         rs.setLockOwner(lockedBy);

         if(!rs.isRuntime()) {
            List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

            if(exs != null) {
               Catalog catalog = Catalog.getCatalog();
               exs.add(new ConfirmException(
                          catalog.getString("common.AssetLockBy", lockedBy),
                          ConfirmException.WARNING));
            }
         }
      }
      else {
         try {
           engine.checkAssetPermission(user, entry, ResourceAction.WRITE);
         }
         catch(Exception ex) {
            rs.setEditable(false);
         }
      }

      String id = getNextID(entry, user);
      rs.setID(id);
      amap.put(id, rs);
      return id;
   }

   /**
    * Get the mode the entry is for.
    */
   private int getEntryMode(AssetEntry entry) {
      boolean viewer = "true".equals(entry.getProperty("viewer"));
      boolean preview = "true".equals(entry.getProperty("preview"));
      return (viewer || preview || entry.isVSSnapshot())
         ? RuntimeViewsheet.VIEWSHEET_RUNTIME_MODE
         : RuntimeViewsheet.VIEWSHEET_DESIGN_MODE;
   }

   /**
    * Get the runtime worksheet.
    * @param id the specified worksheet id.
    * @param user the specified user.
    * @return the runtime worksheet if any.
    */
   @Override
   public RuntimeWorksheet getWorksheet(String id, Principal user)
      throws Exception
   {
      return (RuntimeWorksheet) getSheet(id, user, false);
   }

   /**
    * Get the runtime sheet.
    * @param id the specified sheet id.
    * @param user the specified user.
    * @return the runtime sheet if any.
    */
   @Override
   public RuntimeSheet getSheet(String id, Principal user) {
      return getSheet(id, user, false);
   }

   /**
    * Get the runtime sheet.
    * @param id the specified sheet id.
    * @param user the specified user.
    * @param touch <code>true</code> to update the access time and heartbeat.
    * @return the runtime sheet if any.
    */
   protected final RuntimeSheet getSheet(String id, Principal user, boolean touch) {
      RuntimeSheet rs = amap.get(id);
      Catalog catalog = Catalog.getCatalog();

      if(rs == null) {
         LOG.debug("Worksheet/viewsheet has expired: " + id);
         throw new ExpiredSheetException(id, user);
      }

      if(rs.getUser() != null && user != null && !rs.matches(user)) {
         if(!(user instanceof SRPrincipal &&
            Boolean.TRUE.toString().equals(((SRPrincipal) user).getProperty("supportLogin"))))
         {
            throw new InvalidUserException(
               catalog.getString("common.invalidUser", user, rs.getUser()),
               LogLevel.INFO, false, rs.getUser());
         }
      }

      accessSheet(id, touch);
      return rs;
   }

   /**
    * Save the worksheet.
    * @param ws the specified worksheet.
    * @param entry the specified asset entry.
    * @param user the specified user.
    * @param force <tt>true</tt> to set worksheet forcely without
    * checking.
    */
   @Override
   public void setWorksheet(Worksheet ws, AssetEntry entry,
                            Principal user, boolean force, boolean updateDependency)
      throws Exception
   {
      setSheet(ws, entry, user, force, updateDependency);
   }

   /**
    * Save the runtime sheet.
    * @param sheet the specified abstract sheet.
    * @param entry the specified asset entry.
    * @param user the specified user.
    * @param force <tt>true</tt> to set worksheet forcely without
    * @param updateDependency <tt>true</tt> to update dependency or not
    * checking.
    */
   protected final void setSheet(AbstractSheet sheet, AssetEntry entry,
                                 Principal user, boolean force, boolean updateDependency)
      throws Exception
   {
      String owner = getLockOwner(entry);
      String uname = user == null ? null : user.getName();

      if(owner != null && !owner.equals(uname)) {
         IdentityID ownerIdentity = IdentityID.getIdentityIDFromKey(owner);
         IdentityID userIdentity = IdentityID.getIdentityIDFromKey(uname);
         String ownerName = ownerIdentity.getName() != null ? ownerIdentity.getName() : "";
         String unameName =
            userIdentity != null && userIdentity.getName() != null ? userIdentity.getName() : "";
         throw new MessageException(Catalog.getCatalog().getString(
                                       "common.worksheetLocked", entry.getPath(),
                                       ownerName, unameName), LogLevel.WARN, false);
      }

      ((AbstractAssetEngine) engine).setSheet(entry, sheet, user, force, true, updateDependency);
   }

   /**
    * Get the runtime sheets.
    * @return the runtime sheets.
    */
   @Override
   public RuntimeSheet[] getRuntimeSheets(Principal user) {
      List<RuntimeSheet> list = new ArrayList<>();

      for(String key : amap.keySet()) {
         RuntimeSheet rvs = amap.get(key);

         if(user == null || rvs != null && rvs.matches(user)) {
            list.add(rvs);
         }
      }

      return list.toArray(new RuntimeSheet[0]);
   }

   /**
    * Get the lock owner.
    * @param entry the specified asset entry.
    */
   @Override
   public String getLockOwner(AssetEntry entry) {
      for(String key : amap.keySet()) {
         RuntimeSheet rs = amap.get(key);

         if(rs == null) {
            continue;
         }

         AssetEntry entry2 = rs.getEntry();

         // when the save viewsheet action is caused by save home bookmark
         // should check lock also
         if(Tool.equals(entry2, entry) && (rs.isEditable() ||
            "true".equals(entry.getProperty("homeBookmarkSaved"))))
         {
            entry.setProperty("homeBookmarkSaved", null);
            Principal user = rs.getUser();
            return user == null ? null : user.getName();
         }
      }

      entry.setProperty("homeBookmarkSaved", null);

      return null;
   }

   /**
    * Access a sheet.
    */
   private void accessSheet(String id, boolean touch) {
      RuntimeSheet rs = amap.get(id);

      if(rs != null) {
         if(touch) {
            rs.access(true);
         }

         amap.put(id, rs);
      }
   }

   public RuntimeWorksheet[] getAllRuntimeWorksheetSheets() {
      return amap.values().stream()
         .filter(sheet -> sheet instanceof RuntimeWorksheet)
         .map(sheet -> (RuntimeWorksheet) sheet)
         .toArray(RuntimeWorksheet[]::new);
   }

   private RuntimeWorksheet getRuntimeWorksheet(String rid) {
      RuntimeSheet runtimeSheet = getRuntimeSheet(rid);

      if(runtimeSheet instanceof RuntimeWorksheet) {
         return (RuntimeWorksheet) runtimeSheet;
      }
      else {
         return null;
      }
   }

   private RuntimeSheet getRuntimeSheet(String rid) {
      return amap.get(rid);
   }

   /**
    * Close a worksheet.
    * @param id the specified worksheet id.
    */
   @Override
   public void closeWorksheet(String id, Principal user) {
      closeSheet(id, user);
   }

   /**
    * Close a sheet.
    * @param id the specified sheet id.
    */
   protected final void closeSheet(String id, Principal user) {
      RuntimeSheet rsheet = amap.get(id);

      if(rsheet != null && user != null &&
         rsheet.getUser() != null && !rsheet.matches(user))
      {
         if(!(user instanceof SRPrincipal &&
            Boolean.TRUE.toString().equals(((SRPrincipal) user).getProperty("supportLogin"))))
         {
            throw new InvalidUserException(Catalog.getCatalog().
               getString("common.invalidUser", user, rsheet.getUser()),
                                           rsheet.getUser());
         }
      }

      if(isValidExecutingObject(id)) {
         executionMap.setCompleted(id);
      }

      amap.remove(id);
      emap.remove(id);
      clearPreviewTarget(rsheet, id);
      renameInfoMap.remove(id);

      if(rsheet != null) {
         rsheet.dispose();
      }
   }

   /**
    * Close sheets according to the specified user.
    * @param user the specified user.
    */
   public void closeSheets(Principal user) {
      List<String> ids = new ArrayList<>();

      for(String key : amap.keySet()) {
         RuntimeSheet rsheet = amap.get(key);

         if(rsheet != null && rsheet.matches(user)) {
            ids.add(key);
         }
      }

      for(String id : ids) {
         closeSheet(id, user);
      }
   }

   /**
    * Get the next sheet id.
    * @param entry the specified entry.
    * @return the next sheet id.
    */
   protected String getNextID(AssetEntry entry, Principal user) {
      return getNextID(entry.getPath());
   }

   /**
    * Get the next sheet id.
    * @param path the specified path.
    * @return the next sheet id.
    */
   protected String getNextID(String path) {
      nextId.compareAndSet(Long.MAX_VALUE, 0L);
      return path + "-" + nextId.getAndIncrement();
   }

   /**
    * Set the server flag.
    * @param server <tt>true</tt> if server, <tt>false</tt> otherwise.
    */
   @Override
   public void setServer(boolean server) {
      this.server = server;
      this.amap.setApplyMaxCount(server);

      if(server) {
         TimedQueue.addSingleton(new RecycleTask(3 * 60000));
      }
      else {
         TimedQueue.remove(RecycleTask.class);
      }
   }

   /**
    * Check if server is turned on.
    * @return <tt>true</tt> if turned on, <tt>false</tt> turned off.
    */
   @Override
   public boolean isServer() {
      return server;
   }

   /**
    * Get the date ranges from root.
    */
   public static List<Assembly> getDateRanges(AssetRepository rep, Principal user) {
      String uname = user == null ? "null" : user.getName();
      List<Assembly> list = dranges.get(uname);

      if(list == null) {
         AssetEntry[] roots = {
            (user != null) ? AssetEntry.createUserRoot(user) : null,
            AssetEntry.createGlobalRoot()
         };

         list = new ArrayList<>();
         Set<AssetEntry> added = new HashSet<>();

         // add all date range assemblies from report, user, global scope
         for(AssetEntry root : roots) {
            if(root == null) {
               continue;
            }

            try {
               AssetEntry[] arr = AssetUtil.getEntries(
                  rep, root, user, AssetEntry.Type.WORKSHEET,
                  AbstractSheet.DATE_RANGE_ASSET, true);

               for(AssetEntry entry : arr) {
                  if(added.contains(entry)) {
                     continue;
                  }

                  Worksheet worksheet = (Worksheet) rep.getSheet(
                     entry, user, false, AssetContent.ALL);
                  added.add(entry);
                  Assembly assembly = worksheet.getPrimaryAssembly();

                  if(assembly instanceof MirrorAssembly) {
                     continue;
                  }

                  boolean contained = false;

                  for(Assembly assembly2 : list) {
                     String name = assembly.getName();

                     if(assembly2.getName().equals(name)) {
                        contained = true;
                        LOG.warn(
                           "Duplicate date range name found: " + name +
                              ", path: " + entry.getDescription(false));
                        break;
                     }
                  }

                  if(!contained) {
                     list.add(assembly);
                  }
               }
            }
            catch(Exception ex) {
               LOG.warn(ex.getMessage(), ex);
            }
         }

         dranges.put(uname, list);
      }

      return list;
   }

   @Override
   public boolean needRenameDep(String rid) {
      List<RenameDependencyInfo> renameDependencyInfos = renameInfoMap.get(rid);
      return renameDependencyInfos != null && !renameDependencyInfos.isEmpty();
   }

   @Override
   public void clearRenameDep(String rid) {
      renameInfoMap.remove(rid);
   }

   @Override
   public void rollbackRenameDep(String rid) {
      List<RenameDependencyInfo> renameDependencyInfos = renameInfoMap.get(rid);

      if(renameDependencyInfos == null) {
         return;
      }

      for(int i = renameDependencyInfos.size() - 1; i >= 0; i--) {
         RenameDependencyInfo info = renameDependencyInfos.get(i);
         RenameDependencyInfo causeRenameDepsInfo = new RenameDependencyInfo();

         AssetObject[] assetObjects = info.getAssetObjects();

         for(AssetObject assetObject : assetObjects) {
            if(!(assetObject instanceof AssetEntry) || !((AssetEntry) assetObject).isWorksheet()) {
               continue;
            }

            List<RenameInfo> infos = info.getRenameInfo(assetObject);
            List<RenameInfo> causeInfos = new ArrayList<>();

            for(int j = infos.size() - 1; j >= 0; j--) {
               RenameInfo renameInfo = infos.get(j);
               causeInfos.addAll(
                  createCauseWSColRenameInfos(renameInfo, (AssetEntry) assetObject, rid));
            }

            List<AssetObject> depAssets =
               DependencyTransformer.getDependencies(((AssetEntry) assetObject).toIdentifier());

            if(depAssets != null) {
               for(AssetObject depAsset : depAssets) {
                  causeRenameDepsInfo.setRenameInfo(depAsset, causeInfos);
               }

               return;
            }
         }

         if(causeRenameDepsInfo.getAssetObjects().length > 0) {
            RenameTransformHandler.getTransformHandler().addTransformTask(causeRenameDepsInfo);
         }
      }
   }

   @Override
   public void fixRenameDepEntry(String rid, AssetObject newEntry) {
      List<RenameDependencyInfo> renameDependencyInfos = renameInfoMap.get(rid);
      RuntimeSheet runtimeSheet = getRuntimeSheet(rid);

      if(renameDependencyInfos == null || runtimeSheet == null) {
         return;
      }

      AssetObject entry = runtimeSheet.getEntry();
      DependencyTransformer.fixRenameDepEntry(rid, entry, newEntry, renameInfoMap);
   }

   private List<RenameInfo> createCauseWSColRenameInfos(RenameInfo renameInfo, AssetEntry entry ,
                                                        String rid)
   {
      RuntimeWorksheet runtimeWorksheet = getRuntimeWorksheet(rid);

      if(runtimeWorksheet == null || !renameInfo.isColumn()) {
         return new ArrayList<>();
      }

      Worksheet ws = runtimeWorksheet.getWorksheet();
      Assembly[] assemblies = ws.getAssemblies();
      List<RenameInfo> causeInfos = new ArrayList<>();

      for(Assembly assembly : assemblies) {
         if(!(assembly instanceof AbstractTableAssembly)) {
            continue;
         }

         createCauseWSColRenameInfosByTable(renameInfo, entry, (AbstractTableAssembly) assembly,
            causeInfos);
      }

      return causeInfos;
   }

   private void createCauseWSColRenameInfosByTable(RenameInfo renameInfo, AssetEntry entry,
                                                   AbstractTableAssembly tableAssembly,
                                                   List<RenameInfo> causeInfos)
   {
      String assemblyName = tableAssembly.getName();
      ColumnSelection cols = tableAssembly.getColumnSelection(false);

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         DataRef ref = cols.getAttribute(i);

         if(ref instanceof ColumnRef col) {
            if(!StringUtils.isEmpty(col.getAlias())) {
               continue;
            }

            String oldName = renameInfo.getOldName();
            String newName = renameInfo.getNewName();

            if(renameInfo.isLogicalModel()) {
               oldName = oldName.substring(oldName.indexOf(".") + 1);
               newName = newName.substring(newName.indexOf(".") + 1);
            }

            if(!StringUtils.equals(col.getOldName(), oldName)) {
               continue;
            }

            causeInfos.add(new RenameInfo(newName, oldName,
               RenameInfo.ASSET | RenameInfo.COLUMN,
               entry.toIdentifier(), assemblyName));
         }
      }
   }

   @Override
   public void renameDep(String rid) {
      List<RenameDependencyInfo> renameDependencyInfos = renameInfoMap.get(rid);

      if(renameDependencyInfos != null) {
         for(RenameDependencyInfo dinfo : renameDependencyInfos) {
            dinfo.setRecursive(false);

            // 1. Should not add task, we only rename dependency for current vs, only one case. And
            // we should refresh viewsheet after rename dependency. So we must do the refresh
            // after rename dependency.
            // 2. The place should only fix current vs, there is no need to update dependency key.
            // For the key is updated when the rename action. The rename action will call update
            // all dependency(no opened) to update and update key. The only action there should do
            // is to update current vs data.
            dinfo.setUpdateStorage(false);

            if(dinfo.getAssetObjects() != null &&
               dinfo.getAssetObjects()[0] instanceof AssetEntry)
            {
               RenameTransformHandler.getTransformHandler().addTransformTask(dinfo, true);
            }
         }

         renameInfoMap.remove(rid);
      }
   }

   public void updateRenameInfos(Object rid, AssetObject assetEntry,
                                    List<RenameInfo> renameInfos)
   {
      DependencyTransformer.updateRenameInfos(rid, assetEntry, renameInfos, renameInfoMap);
   }

   /**
    * Thread definition in emap.
    */
   public static class ThreadDef {
      public void setStartTime(long time) {
         this.time = time;
      }

      public long getStartTime() {
         return time;
      }

      public void setThread(Thread thread) {
         this.thread = thread;
      }

      public Thread getThread() {
         return thread;
      }

      private long time;
      private Thread thread;
   }

   /**
    * Recycle thread.
    */
   private class RecycleTask extends TimedQueue.TimedRunnable {
      public RecycleTask(long time) {
         super(time);
      }

      @Override
      public boolean isRecurring() {
         return true;
      }

      /**
       * Run process.
       */
      @Override
      public void run() {
         try {
            List<String> keys = new ArrayList<>(amap.keySet());

            for(String id : keys) {
               RuntimeSheet rs = amap.get(id);
               boolean timedout;
               boolean scheduler = rs != null && rs.getEntry() != null &&
                  "true".equals(rs.getEntry().getProperty("_scheduler_"));

               timedout = rs == null ||
                  (rs.isTimeout() && !emap.containsKey(id) && !scheduler);

               if(timedout) {
                  if(isValidExecutingObject(id)) {
                     executionMap.setCompleted(id);
                  }

                  amap.remove(id);
                  emap.remove(id);
                  clearPreviewTarget(rs, id);
               }

               if(timedout && rs != null) {
                  rs.dispose();
               }
            }
         }
         catch(Exception ex) {
            LOG.error("An error occurred while cleaning up worksheets", ex);
         }
      }
   }

   private void clearPreviewTarget(RuntimeSheet runtimeSheet, String id) {
      if(singlePreviewEnabled && runtimeSheet != null && id != null && id.startsWith(PREVIEW_WORKSHEET))
      {
         String sourceWorksheetID = (String) runtimeSheet.getProperty("__preview_source__");

         if(sourceWorksheetID != null) {
            RuntimeSheet sourceWorksheet = amap.get(sourceWorksheetID);

            if(sourceWorksheet != null) {
               sourceWorksheet.setProperty("__preview_target__", null);
            }
         }
      }
   }

   /**
    * Get the viewsheet data changed timestamp.
    * @param entry - the viewsheet entry.
    */
   @Override
   public long getDataChangedTime(AssetEntry entry) {
      return 0;
   }

   /**
    * Get the worksheet service.
    */
   public static WorksheetService getWorksheetService() {
      return SingletonManager.getInstance(WorksheetService.class);
   }

   /**
    * Print the current status.
    */
   public void print() {
      System.err.println("---WorksheetEngine: " + this);
      print0();
   }

   /**
    * Print the current status internally.
    */
   protected void print0() {
      System.err.println("--amap size: " + amap.size() + "<>" + amap);
   }

   /**
    * apply runtime condition to the specified worksheet.
    */
   @Override
   public void applyRuntimeCondition(String vid, RuntimeWorksheet rws) {
      // do nothing
   }

   /**
    * Check the id is a valid executing object which will be add to the
    * execution map. Only the viewsheet id will be count.
    */
   protected boolean isValidExecutingObject(String id) {
      return false;
   }

   /**
    * Exception key.
    */
   public static final class ExceptionKey {
      public ExceptionKey(Throwable ex, String id) {
        this.ex = ex;
        this.id = id;
        this.ts = System.currentTimeMillis();
      }

      public int hashCode() {
         return getExceptionString().hashCode();
      }

      public boolean equals(Object obj) {
         if(!(obj instanceof ExceptionKey key2)) {
            return false;
         }

         return key2.getExceptionString().equals(getExceptionString()) &&
            Tool.equals(key2.id, id);
      }

      public boolean isTimeout() {
         return System.currentTimeMillis() - ts > 10000;
      }

      public String toString() {
         return getExceptionString() + "@" + id;
      }

      private String getExceptionString() {
         if(exString == null) {
            exString = ex.toString();
         }

         return exString;
      }

      private final Throwable ex;
      private final String id;
      private final long ts;
      private String exString; // cached string
   }

   private static class ViewsheetException extends MessageException {
      /**
       * Constructor.
       */
      public ViewsheetException(String message) {
         super(message, LogLevel.WARN, false);
      }
   }

   public static final String INVALID_VIEWSHEET =
      "Viewsheet not exist, please check the name.";

   // cached global scope and user scope date ranges: user name->date ranges
   private static final DataCache<String, List<Assembly>> dranges =
      new DataCache<>(50, 1000 * 60 * 60);
   public static final WeakHashMap<Object, ExceptionKey> exceptionMap = new WeakHashMap<>();

   protected AssetRepository engine; // asset repository
   protected final RuntimeSheetCache amap; // runtime asset map
   protected final Map<String,Vector<ThreadDef>> emap; // id -> event threads
   protected ExecutionMap executionMap; // the executing viewsheet
   protected int counter; // counter
   private final DistributedLong nextId;

   private final boolean singlePreviewEnabled;
   private boolean server = false; // server flag
   private final Map<Object, List<RenameDependencyInfo>> renameInfoMap;
   private static final Logger LOG = LoggerFactory.getLogger(WorksheetEngine.class);
   private static final String NEXT_ID_NAME = WorksheetEngine.class.getName() + ".nextId";
   private static final String CACHE_NAME = WorksheetEngine.class.getName() + ".cache";
}
