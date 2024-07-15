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
package inetsoft.analytic.composition;

import inetsoft.analytic.AnalyticAssistant;
import inetsoft.analytic.composition.command.ExportVSCommand;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.*;
import inetsoft.sree.UserEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.util.SingletonManager;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;

/**
 * The default viewsheet service implementation, implements all the methods
 * of <tt>ViewsheetService</tt> to provide common functions. If necessary,
 * we may chain sub <tt>ViewsheetService</tt>s to serve advanced purposes.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ViewsheetEngine extends WorksheetEngine implements ViewsheetService {
   /**
    * Constructor.
    */
   public ViewsheetEngine() throws RemoteException {
      this((AssetRepository) AnalyticAssistant.getAnalyticAssistant()
         .getAnalyticRepository());
      setServer(true);
   }

   /**
    * Constructor.
    */
   public ViewsheetEngine(AssetRepository engine) throws RemoteException {
      super(engine);
   }

   /**
    * Gets the shared viewsheet service instance.
    *
    * @return the viewsheet engine.
    */
   public static ViewsheetService getViewsheetEngine() {
      return SingletonManager.getInstance(ViewsheetService.class);
   }

   /**
    * Create a runtime sheet.
    * @param entry the specified asset entry.
    * @param sheet the specified sheet.
    * @param user the specified user.
    */
   @Override
   protected RuntimeSheet createRuntimeSheet(AssetEntry entry,
                                             AbstractSheet sheet,
                                             Principal user)
      throws Exception
   {
      if(entry.isViewsheet()) {
         if(sheet instanceof VSSnapshot) {
            VSSnapshot snapshot = (VSSnapshot) sheet;
            sheet = snapshot.createViewsheet(engine, user);
         }

         Viewsheet viewsheet = (Viewsheet) sheet;
         // clear layout position and size of the viewsheet when open it.
         viewsheet.clearLayoutState();
         LayoutInfo layoutInfo = viewsheet.getLayoutInfo();
         String displayWidthProperty =
            entry.getProperty("_device_display_width");
         String pixelDensityProperty =
            entry.getProperty("_device_pixel_density");
         String mobileProperty = entry.getProperty("_device_mobile");
         ViewsheetLayout vsLayout = null;

         if(displayWidthProperty != null) {
            int displayWidth = Integer.parseInt(displayWidthProperty);
            boolean mobile = "true".equals(mobileProperty);
            vsLayout = layoutInfo.matchLayout(displayWidth, mobile);

            if(vsLayout != null) {
               double dpi;

               if(pixelDensityProperty != null) {
                  int pixelDensity = Integer.parseInt(pixelDensityProperty);

                  if(pixelDensity < 200) {
                     dpi = 160D;
                  }
                  else if(pixelDensity <= 280) {
                     dpi = 240D;
                  }
                  else if(pixelDensity <= 400) {
                     dpi = 320D;
                  }
                  else if(pixelDensity <= 560) {
                     dpi = 480D;
                  }
                  else {
                     dpi = 640D;
                  }
               }
               else {
                  // don't scale HTML
                  dpi = 160D;
               }

               vsLayout = layoutInfo.matchDPI(dpi, vsLayout);
               viewsheet = vsLayout.apply(viewsheet);
            }
         }

         if("true".equals(entry.getProperty("meta"))) {
            viewsheet.getViewsheetInfo().setMetadata(true);
         }

         RuntimeViewsheet rvs = new RuntimeViewsheet(entry, viewsheet, user,
                                                     engine, this, null, false);
         rvs.setRuntimeVSLayout(vsLayout);

         return rvs;
      }

      return super.createRuntimeSheet(entry, sheet, user);
   }

   /**
    * Open a preview worksheet.
    * @param id the specified worksheet id or viewsheet id.
    * @param name table assembly name or data vsassembly for viewsheet.
    * @param user the specified user.
    */
   @Override
   public String openPreviewWorksheet(String id, String name, Principal user)
      throws Exception
   {
      RuntimeSheet sheet = getSheet(id, user, false);

      if(sheet instanceof RuntimeWorksheet) {
         return super.openPreviewWorksheet(id, name, user);
      }

      Worksheet ws = new Worksheet();
      DataTableAssembly data = new DataTableAssembly(ws, name);
      AssetEntry entry = new AssetEntry(AssetRepository.TEMPORARY_SCOPE,
                                        AssetEntry.Type.WORKSHEET, "",
                                        user == null ? null : IdentityID.getIdentityIDFromKey(user.getName()));

      ws.addAssembly(data);

      return openPreviewWorksheet(ws, entry, name, user);
   }

   /**
    * Open a temporary viewsheet.
    * @param wentry the specified base worksheet entry.
    * @param user the specified user.
    * @param rid the specified report id.
    * @return the viewsheet id.
    */
   @Override
   public String openTemporaryViewsheet(AssetEntry wentry, Principal user,
                                        String rid)
      throws Exception
   {
      Viewsheet vs = new Viewsheet(wentry);
      AssetEntry entry = getTemporaryAssetEntry(user, AssetEntry.Type.VIEWSHEET);
      vs.update(engine, entry, user);
      RuntimeViewsheet rvs = new RuntimeViewsheet(entry, vs, user, engine, this,
                                                  null, false);
      rvs.setEditable(false);
      return createTemporarySheetId(entry, rvs, user);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String openPreviewViewsheet(String id, Principal user,
                                      AbstractLayout layout) throws Exception {
      return openPreviewViewsheet(id, user, layout, null);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String openPreviewViewsheet(String id, Principal user,
                                      AbstractLayout layout, String previewId) throws Exception
   {
      AssetRepository engine = getAssetRepository();
      RuntimeViewsheet orvs = getViewsheet(id, user);
      AssetEntry oentry = orvs.getEntry();
      Viewsheet vs = orvs.getViewsheet();
      vs = (Viewsheet) vs.clone();
      VSUtil.resetRuntimeValues(vs, true);
      vs.update(engine, oentry, user);
      AssetEntry entry = new AssetEntry(oentry.getScope(), oentry.getType(),
                                        oentry.getPath(), oentry.getUser());
      entry.copyProperties(oentry);

      if(oentry.getAlias() != null && oentry.getAlias().length() > 0) {
         entry.setAlias(oentry.getAlias());
      }

      entry.setProperty("preview", "true");
      vs = layout == null ? vs : layout.apply(vs);
      RuntimeViewsheet rvs = new RuntimeViewsheet(entry, vs, user, engine, this,
                                                  orvs.getVSBookmark(), false);

      if(layout instanceof ViewsheetLayout) {
         entry.setProperty("previewLayoutID", layout.getID());
         rvs.setRuntimeVSLayout((ViewsheetLayout) layout);
      }

      rvs.setEditable(false);
      rvs.setOriginalID(id);

      ViewsheetSandbox ovbox = orvs.getViewsheetSandbox();
      AssetQuerySandbox oBox = ovbox != null ? ovbox.getAssetQuerySandbox() : null;
      AssetQuerySandbox nBox = rvs.getViewsheetSandbox().getAssetQuerySandbox();

      // the new RuntimeViewsheet's VariableTable is based on vs,
      // we should synchronize it with orws
      if(oBox != null && nBox != null) {
         nBox.refreshVariableTable(oBox.getVariableTable());
      }

      synchronized(amap) {
         final String newRvsId;
         final int oldIndex;

         if(previewId == null) {
            newRvsId = getNextID(PREVIEW_VIEWSHEET);
            oldIndex = -1;
         }
         else {
            newRvsId = previewId;
            oldIndex = amap.indexOf(previewId);
         }

         rvs.setID(newRvsId);

         if(oldIndex == -1) {
            amap.put(newRvsId, rvs);
         }
         else {
            closeViewsheet(previewId, user);
            amap.put(oldIndex, newRvsId, rvs);
         }

         return newRvsId;
      }
   }

   /**
    * Refresh preview viewsheet.
    * @param id the viewsheet id which is to be refreshed, the viewsheet is
    *  preview viewsheet.
    * @param user the specified user.
    * @return if refresh successful.
    */
   @Override
   public boolean refreshPreviewViewsheet(String id, Principal user,
                                          AbstractLayout layout) throws Exception
   {
      // get preview runtime viewsheet
      RuntimeViewsheet prvs = getViewsheet(id, user);

      if(prvs == null || !prvs.isRuntime()) {
         return false;
      }

      // get the design runtime viewsheet of the preview runtime viewsheet
      String did = prvs.getOriginalID();
      RuntimeViewsheet drvs = getViewsheet(did, user);

      if(drvs == null || drvs.isRuntime()) {
         return false;
      }

      // create a new preview runtime viewsheet and close old one
      openPreviewViewsheet(did, user, layout, id);
      return true;
   }

   /**
    * Open an existing viewsheet.
    * @param entry the specified asset entry.
    * @param user the specified user.
    * @param viewer <tt>true</tt> if is viewer, <tt>false</tt> otherwise.
    * @return the viewsheet id.
    */
   @Override
   public String openViewsheet(AssetEntry entry, Principal user, boolean viewer)
      throws Exception
   {
      String id = null;

      try {
         // @by yuz, fix bug1246261678219, should use viewer any time
         entry.setProperty("viewer", "" + viewer);
         id = openSheet(entry, user);

         if(user != null) {
            RuntimeViewsheet rvs = getViewsheet(id, user);

            // add user to var table so the query key used for caching would match
            // the subsequent queries where the user is in vars.
            if(rvs != null) {
               rvs.getViewsheetSandbox().getVariableTable().put("__principal__", user);
            }
         }
      }
      finally {
         entry.setProperty("viewer",  null);
         lifecycleMessageService.viewsheetOpened(id);
      }

      return id;
   }

   /**
    * Get the runtime viewsheet.
    * @param id the specified viewsheet id.
    * @param user the specified user.
    * @return the runtime viewsheet if any.
    */
   @Override
   public RuntimeViewsheet getViewsheet(String id, Principal user) throws Exception {
      try {
         return (RuntimeViewsheet) getSheet(id, user, false);
      }
      catch(ClassCastException ex) {
         LOG.error("Incorrect runtime sheet type: " + id);
         throw new RuntimeException("Failed to open viewsheet: " + id);
      }
   }

   /**
    * Save the viewsheet.
    * @param vs the specified viewsheet.
    * @param entry the specified asset entry.
    * @param user the specified user.
    * @param force <tt>true</tt> to set viewsheet forcely without
    * @param updateDependency <tt>true</tt> to update dependency or not
    * checking.
    */
   @Override
   public void setViewsheet(Viewsheet vs, AssetEntry entry, Principal user,
                            boolean force, boolean updateDependency)
      throws Exception
   {
      setSheet(vs, entry, user, force, updateDependency);
   }

   /**
    * Save the snapshop.
    * @param vs the specified snapshot.
    * @param entry the specified asset entry.
    * @param user the specified user.
    * @param force <tt>true</tt> to set viewsheet forcely without
    * checking.
    */
   @Override
   public void setSnapshot(VSSnapshot vs, AssetEntry entry, Principal user,
                           boolean force)
      throws Exception
   {
      setSheet(vs, entry, user, force, true);
   }

   /**
    * Close a viewsheet.
    * @param id the specified viewsheet id.
    */
   @Override
   public void closeViewsheet(String id, Principal user) throws Exception {
      if(id == null) {
         return;
      }

      RuntimeSheet closeSheet;

      // only name available? try finding the runtime viewsheet to be closed
      synchronized(amap) {
         if(!amap.containsKey(id) && user != null) {
            String prefix = id + "-";

            for(Map.Entry<String, RuntimeSheet> e : amap.entrySet()) {
               String vid = e.getKey();
               RuntimeSheet sheet = e.getValue();

               if(!vid.startsWith(prefix)) {
                  continue;
               }

               String suffix = vid.substring(prefix.length());

               if(suffix.matches("^.*\\d.*$")) {
                  continue;
               }

               if(!(sheet instanceof RuntimeViewsheet)) {
                  continue;
               }

               RuntimeViewsheet rvs = (RuntimeViewsheet) sheet;

               // this does not consider global scope and user scope. If required,
               // we can add more logic to support this function per requirement
               if(rvs.matches(user)) {
                  id = vid;
                  break;
               }
            }
         }

         closeSheet = amap.get(id);
      }

      if(closeSheet != null && "true".equals(closeSheet.getProperty("__EXPORTING__"))) {
         closeSheet.setProperty("_CLOSE_AFTER_EXPORT_", "true");
      }
      else {
         closeSheet(id, user);
         lifecycleMessageService.viewsheetClosed(id);
      }
   }

   /**
    * Get the runtime viewsheets.
    * @return the runtime viewsheets.
    */
   @Override
   public RuntimeViewsheet[] getRuntimeViewsheets(Principal user) {
      List<RuntimeSheet> sheets;

      synchronized(amap) {
         sheets = new ArrayList<>(amap.values());
      }

      return sheets.stream()
         .filter(sheet -> sheet instanceof RuntimeViewsheet)
         .map(sheet -> (RuntimeViewsheet) sheet)
         .filter(rvs -> user == null || rvs.matches(user))
         .toArray(RuntimeViewsheet[]::new);
   }

   /**
    * Get the next sheet id.
    * @param entry the specified entry.
    * @return the next sheet id.
    */
   @Override
   protected String getNextID(AssetEntry entry, Principal user) {
      if(entry.getType() == AssetEntry.Type.VIEWSHEET_SNAPSHOT) {
         return getNextID(PREVIEW_VIEWSHEET);
      }

      return super.getNextID(entry, user);
   }

   /**
    * Copy runtime condition to worksheet.
    * @param vid viewsheet id
    * @param rws runtime worksheet.
    */
   @Override
   public void applyRuntimeCondition(String vid, RuntimeWorksheet rws) {
      RuntimeViewsheet rvs = (RuntimeViewsheet) amap.get(vid);

      if(rvs == null) {
         return;
      }

      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      Map<String, List<ConditionList>> conditions = new HashMap<>();
      List<String> etables = new ArrayList<>();
      copyResource(vs, rws, conditions, etables);

      for(Map.Entry<String, List<ConditionList>> e : conditions.entrySet()) {
         String tname = e.getKey();
         List<ConditionList> list = e.getValue();
         TableAssembly table = (TableAssembly) rws.getWorksheet().getAssembly(tname);

         if(table == null) {
            continue;
         }

         ConditionList conds = VSUtil.mergeConditionList(list, JunctionOperator.AND);
         boolean post = !table.isPlain() || !table.getAggregateInfo().isEmpty();
         ColumnSelection columns = table.getColumnSelection(post);
         conds = VSUtil.normalizeConditionList(columns, conds);

         if(post) {
            table.setPostRuntimeConditionList(conds);
         }
         else {
            table.setPreRuntimeConditionList(conds);
         }
      }

      for(String tname : etables) {
         Assembly assembly = vs.getBaseWorksheet().getAssembly(tname);
         Assembly assembly1 = rws.getWorksheet().getAssembly(tname);

         if(assembly instanceof SnapshotEmbeddedTableAssembly &&
            assembly1 instanceof SnapshotEmbeddedTableAssembly)
         {
            SnapshotEmbeddedTableAssembly setable =
               (SnapshotEmbeddedTableAssembly) assembly;
            SnapshotEmbeddedTableAssembly setable1 =
               (SnapshotEmbeddedTableAssembly) assembly1;
            setable1.setTable(setable.getTable());
         }
         else if(assembly instanceof EmbeddedTableAssembly &&
            assembly1 instanceof EmbeddedTableAssembly)
         {
            EmbeddedTableAssembly etable = (EmbeddedTableAssembly) assembly;
            EmbeddedTableAssembly etable1 = (EmbeddedTableAssembly) assembly1;
            etable1.setEmbeddedData(etable.getEmbeddedData());
         }
      }
   }

   public void copyResource(Viewsheet vs, RuntimeWorksheet rws,
                            Map<String, List<ConditionList>> conditions,
                            List<String> etables)
   {
      boolean copy = false;

      if(Tool.equals(vs.getBaseEntry(), rws.getEntry())) {
         copy = true;
      }

      for(Assembly assembly : rws.getWorksheet().getAssemblies()) {
         if(assembly instanceof MirrorAssembly) {
            MirrorAssembly mirror = (MirrorAssembly) assembly;

            if(Tool.equals(vs.getBaseEntry(), mirror.getEntry())) {
               copy = true;
               break;
            }
         }
      }

      if(!copy) {
         return;
      }

      for(Assembly assembly : vs.getAssemblies(true)) {
         if(assembly instanceof Viewsheet) {
            Viewsheet vs1 = (Viewsheet) assembly;
            copyResource(vs1, rws, conditions, etables);
            continue;
         }

         if(assembly instanceof EmbeddedTableVSAssembly) {
            EmbeddedTableVSAssembly et = (EmbeddedTableVSAssembly) assembly;
            String tname = et.getTableName();

            if(!etables.contains(tname)) {
               etables.add(tname);
            }
         }

         if(!(assembly instanceof SelectionVSAssembly)) {
            continue;
         }

         SelectionVSAssembly obj1 = (SelectionVSAssembly) assembly;
         String tname = obj1.getTableName();
         ConditionList condition = obj1.getConditionList();

         if(tname == null || condition == null || condition.isEmpty()) {
            continue;
         }

         List<ConditionList> list = conditions.get(tname);

         if(list == null) {
            list = new ArrayList<>();
            conditions.put(tname, list);
         }

         list.add(condition);
      }
   }

   /**
    * Check the id is a valid executing object which will be add to the
    * execution map. Only the viewsheet id will be count.
    */
   @Override
   protected boolean isValidExecutingObject(String id) {
      return amap.get(id) instanceof RuntimeViewsheet;
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
      return SUtil.isDuplicatedEntry(engine, entry);
   }

   /**
    * Localize the specified asset entry.
    */
   @Override
   public String localizeAssetEntry(String path, Principal principal,
                                    boolean isReplet, AssetEntry entry,
                                    boolean isUserScope)
   {
      return SUtil.localizeAssetEntry(path, principal, isReplet, entry,
         isUserScope);
   }

   /**
    * Get the cached propery by providing the specified key.
    */
   @Override
   public Object getCachedProperty(Principal user, String key) {
      return UserEnv.getProperty(user, key);
   }

   /**
    * Set the cached property by providing the specified key-value pair.
    */
   @Override
   public void setCachedProperty(Principal user, String key, Object val) {
      UserEnv.setProperty(user, key, val);
   }

   @Override
   protected void process0(AssetEvent event, Principal user,
                           AssetCommand command)
      throws Throwable
   {
      try {
         AssetDataCache.monitor(true);
         super.process0(event, user, command);
      }
      finally {
         AssetDataCache.monitor(false);
      }
   }

   /**
    * Get the viewsheet data changed timestamp.
    * @param entry - the viewsheet entry.
    */
   @Override
   public synchronized long getDataChangedTime(AssetEntry entry) {
      Long changeTime = dataChangeMap.get(entry);
      return changeTime == null ? 0L : changeTime;
   }

   @Override
   public void updateBookmarks(AssetEntry viewsheet) {
      for(RuntimeViewsheet runtimeViewsheet : getRuntimeViewsheets(viewsheet)) {
         if(runtimeViewsheet == null) {
            continue;
         }

         runtimeViewsheet.updateVSBookmark();
      }
   }

   /**
    * Save the which viewsheet data changed.
    * @param path - the path of viewsheet.
    * @param scope - the viewsheet scope.
    * @param user - the viewsheet user.
    */
   public synchronized void dataChanged(String path, int scope, IdentityID user)
      throws RemoteException
   {
      AssetEntry entry = new AssetEntry(scope, AssetEntry.Type.VIEWSHEET, path, user);

      if(containsEntry(entry)) {
         dataChangeMap.put(entry, System.currentTimeMillis());
         dataChangeCount++;

         if(dataChangeCount >= NEED_SHRINK_COUNT) {
            dataChangeCount = 0;
            shrink();
         }
      }
   }

   /**
    * Shrink a dataChangeMap map.
    */
   private void shrink() throws RemoteException {
      long current = System.currentTimeMillis();

      for(Iterator<Map.Entry<AssetEntry, Long>> i =
          dataChangeMap.entrySet().iterator(); i.hasNext();)
      {
         Map.Entry<AssetEntry, Long> e = i.next();
         AssetEntry entry = e.getKey();
         long changeTime = e.getValue();

         // check entry is on the registry
         if(!containsEntry(entry)) {
            i.remove();
            continue;
         }

         try {
            AbstractSheet sheet = engine.getSheet(entry, null, false,
                                                  AssetContent.ALL);

            if(sheet instanceof Viewsheet) {
               Viewsheet vsheet = (Viewsheet) sheet;
               ViewsheetInfo vinfo = vsheet.getViewsheetInfo();
               long touchInterval = vinfo.getTouchInterval() * 1000;// s -> ms

               if(!vinfo.isUpdateEnabled() ||
                  current - changeTime > 2 * touchInterval)
               {
                  i.remove();
               }
            }
            else {
               i.remove();
            }
         }
         catch(Exception ex) {
            LOG.warn("Failed to check status of entry: " + entry,
               ex);
            i.remove();
         }
      }
   }

   /**
    * Shrink a dataChangeMap map.
    */
   private boolean containsEntry(AssetEntry entry) throws RemoteException {
      try {
         if(!getAssetRepository().containsEntry(entry)) {
            throw new RemoteException(INVALID_VIEWSHEET);
         }
         else {
            return true;
         }
      }
      catch(Exception e) {
         RemoteException re;

         if(!(e instanceof RemoteException)) {
            LOG.error("Failed to determine if the repository contain entry: " + entry,
               e);
            re = new RemoteException(INVALID_VIEWSHEET);
         }
         else {
            re = (RemoteException) e;
         }

         throw re;
      }
   }

   /**
    * Get the command for exporting to web.
    */
   @Override
   public AssetCommand export(String url) {
      return new ExportVSCommand(url);
   }

   /**
    * Print the current status internally.
    */
   @Override
   protected void print0() {
      super.print0();
      System.err.println(
         "--dataChangeMap size: " + dataChangeMap.size() + "<>" + dataChangeMap.keySet());
   }

   /**
    * Add excution id to map.
    * @param id the specified viewsheet id.
    */
   public void addExecution(String id) {
      synchronized(amap) {
         if(isValidExecutingObject(id)) {
            Vector<ThreadDef> threads = emap.get(id);

            if(threads == null) {
               threads = new Vector<>();
            }

            ThreadDef def = new ThreadDef();
            def.setStartTime(System.currentTimeMillis());
            def.setThread(Thread.currentThread());
            threads.addElement(def);
            emap.put(id, threads);

            executionMap.addObject(id);
         }
      }

      lifecycleMessageService.executionStarted(id);
   }

   /**
    * Delete the excution id from map.
    * @param id the specified viewsheet id.
    */
   public void removeExecution(String id) {
      synchronized(amap) {
         if(isValidExecutingObject(id)) {
            Vector<ThreadDef> threads = emap.get(id);

            if(threads != null) {
               threads.remove(threads.size() -1);

               if(threads.size() == 0) {
                  emap.remove(id);
               }
            }

            executionMap.setCompleted(id);
         }
      }

      lifecycleMessageService.executionCompleted(id);
   }

   public RuntimeViewsheet[] getAllRuntimeViewsheets() {
      synchronized(amap) {
         return amap.values().stream()
            .filter(sheet -> sheet instanceof RuntimeViewsheet)
            .map(sheet -> (RuntimeViewsheet) sheet)
            .toArray(RuntimeViewsheet[]::new);
      }
   }

   private RuntimeViewsheet[] getRuntimeViewsheets(AssetEntry entry) {
      return Arrays.stream(getAllRuntimeViewsheets())
         .filter(vs -> vs != null)
         .filter(vs -> Tool.equals(entry, vs.getEntry()))
         .toArray(RuntimeViewsheet[]::new);
   }

   private static final int NEED_SHRINK_COUNT = 100;
   private static final Logger LOG = LoggerFactory.getLogger(ViewsheetEngine.class);
   private final ViewsheetLifecycleMessageChannel lifecycleMessageService =
      SingletonManager.getInstance(ViewsheetLifecycleMessageChannel.class);
   private int dataChangeCount;
   private Map<AssetEntry, Long> dataChangeMap = new HashMap<>();
}
