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
package inetsoft.report.composition.execution;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.mv.*;
import inetsoft.report.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.graph.*;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.*;
import inetsoft.report.lens.TextSizeLimitTableLens;
import inetsoft.report.script.formula.AssetQueryScope;
import inetsoft.report.script.viewsheet.*;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleInfo;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.script.VariableScriptable;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.log.LogContext;
import inetsoft.util.profile.ProfileUtils;
import inetsoft.util.script.*;
import inetsoft.web.vswizard.model.VSWizardConstants;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Array;
import java.security.Principal;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Viewsheet sandbox, the box contains all data in a viewsheet, and refreshes
 * data accordingly when the contained viewsheet changes.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ViewsheetSandbox implements Cloneable, ActionListener {
   /**
    * Constructor.
    * @param vs the specified viewsheet.
    * @param vmode viewsheet mode (defined in Viewsheet).
    * @param user the specified user.
    */
   public ViewsheetSandbox(Viewsheet vs, int vmode, Principal user, AssetEntry entry) {
      this(null, vs, vmode, user, true, entry);
   }

   /**
    * Constructor.
    * @param vs the specified viewsheet.
    * @param vmode viewsheet mode (defined in Viewsheet).
    * @param user the specified user.
    * @param reset <tt>true</tt> to reset the viewsheet sandbox.
    */
   public ViewsheetSandbox(Viewsheet vs, int vmode, Principal user,
                           boolean reset, AssetEntry entry) {
      this(null, vs, vmode, user, reset, entry);
   }

   /**
    * Constructor.
    * @param root the specified root box.
    * @param vs the specified viewsheet.
    * @param vmode viewsheet mode (defined in Viewsheet).
    * @param user the specified user.
    * @param reset <tt>true</tt> to reset the viewsheet sandbox.
    */
   public ViewsheetSandbox(ViewsheetSandbox root, Viewsheet vs, int vmode,
                           Principal user, boolean reset, AssetEntry entry) {
      super();

      this.root = root == null ? this : root;
      this.vmode = vmode;
      this.entry = entry;
      this.nolimit = new HashSet<>();
      this.qmgrs = new ConcurrentHashMap<>();
      this.dmap = new DataMap();
      this.dKeyMap = new DataMap();
      this.fmap = new HashMap<>();
      this.scriptChangedFormSet = Collections.synchronizedSet(new HashSet<>());
      this.tmap = Collections.synchronizedMap(new HashMap<>());
      this.vset = Collections.synchronizedSet(new HashSet<>());
      this.bmap = new ConcurrentHashMap<>();
      this.images = new HashMap<>();
      this.painters = new HashMap<>();
      this.pairs = new SoftHashMap<>(0);
      this.metarep = new TableMetaDataRepository();
      this.user = user;
      this.rid = XSessionService.createSessionID(XSessionService.VIEWSHEET, null);
      setViewsheet(vs, true);

      if(reset) {
         reset(new ChangedAssemblyList());
      }
   }

   /**
    * Get the name of the viewsheet.
    */
   public String getSheetName() {
      return entry != null ? entry.getSheetName() : "";
   }

   /**
    * Get the id.
    */
   public String getID() {
      return rid;
   }

   /**
    * Get the root id.
    */
   public String getTopBoxID() {
      if(this == root) {
         return getID();
      }

      return root.getID();
   }

   /**
    * Get the asset entry of the viewsheet.
    */
   public AssetEntry getAssetEntry() {
      return entry;
   }

   /**
    * Set the viewsheet.
    * @param vs the specified viewsheet.
    * @param resetRuntime when viewsheet is changed, the runtime should be cleared. This
    * is normally only necessary during design time when the viewsheet is modified. Otherwise
    * if the viewsheet is changed temporarily, the runtime should not be cleared or
    * script variables will be lost.
    */
   public void setViewsheet(Viewsheet vs, boolean resetRuntime) {
      if(vs == this.vs) {
         return;
      }

      if(this.vs != null) {
         this.vs.removeActionListener(this);
         this.vs.removeActionListener(metarep);
         Worksheet ws = getWorksheet();

         if(ws != null) {
            ws.removeActionListener(metarep);
         }
      }

      this.vs = vs;
      this.vs.addActionListener(this);
      this.vs.addActionListener(metarep);
      updateRootSandboxMap(vs.getName(), vs);

      if(resetRuntime) {
         ViewsheetSandbox[] boxes = getSandboxes();

         for(ViewsheetSandbox innnerBox : boxes) {
            innnerBox.resetRuntime();
         }
      }
   }

   /**
    * Create a sandbox for the worksheet.
    */
   private void createAssetQuerySandbox(Worksheet ws) {
      wbox = new AssetQuerySandbox(ws);
      wbox.setViewsheetSandbox(this);
      wbox.setAdditionalVariableProvider(vs);
      wbox.setBaseUser(getUser());
      wbox.setFixingAlias(true);
      wbox.setVPMEnabled(!isMVEnabled() ||
         !vs.getViewsheetInfo().isBypassVPM());

      String val = SreeEnv.getProperty("query.preview.maxrow", "500000");
      wbox.setMaxRows(Integer.parseInt(val));
      AssetEntry entry = vs != null ? vs.getBaseEntry() : null;

      if(entry != null) {
         wbox.setWSName(entry.getSheetName());
         wbox.setWSEntry(entry);
      }

      try {
         wbox.refreshVariableTable(this.vs.getVariableTable());
      }
      catch(Exception ex) {
         LOG.warn("Failed to refresh the variable table", ex);
         List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

         if(exs != null) {
            if(!(ex instanceof ConfirmDataException)) {
               exs.add(ex);
            }
         }
      }

      ws.addActionListener(metarep);
      wbox.setWorksheet(ws);
      wbox.setWSEntry(entry);

      // here refresh the column selection to apply changes
      Assembly[] arr = ws.getAssemblies(true);

      for(int i = 0; i < arr.length; i++) {
         try {
            wbox.refreshColumnSelection(arr[i].getName(), false);
         }
         catch(Exception ex) {
            if(!(ex instanceof ConfirmException)) {
               LOG.warn("Failed to refresh the column selection on assembly: " +
                  arr[i].getName(), ex);
            }

            List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

            if(exs != null) {
               if(!(ex instanceof ConfirmDataException)) {
                  exs.add(ex);
               }
            }
         }
      }
   }

   /**
    * Make sure parent viewsheet hold the same viewsheet with viewsheetsandbox
    * of the embedded viewsheet.
    * @param vname  the embedded viewsheet assembly name.
    * @param vs     the new embedded viewsheet.
    */
   private void updateRootSandboxMap(String vname, Viewsheet vs) {
      if(root == null || root == this) {
         if(root != null && root.getViewsheet() == vs) {
            for(Assembly assembly : vs.getAssemblies()) {
               if(!(assembly instanceof Viewsheet)) {
                  continue;
               }

               ViewsheetSandbox box = bmap.get(assembly.getName());

               if(box != null) {
                  box.setViewsheet((Viewsheet) assembly, false);
               }
            }
         }

         return;
      }

      Viewsheet parent = root.getViewsheet();

      if(parent.containsAssembly(vname) && root.bmap.get(vname) == this) {
         parent.removeAssembly(vname, false, true);
         parent.addAssembly(vs, false, false, false);
      }
   }

   /**
    * Reset the runtime states. This is called if the definition of the
    * viewsheet or worksheet may have changed from external event so the
    * states can be reinitialized to be in sync.
    */
   public void resetRuntime() {
      lockWrite();

      try {
         Worksheet ws = getWorksheet();

         metarep.clear();
         shrink();
         vs.resetWS();
         /*
           scope = null;
           // @by yanie: if scope is set to null, reset lastOnInit so the onInit
           // script can be reexecuted.
           lastOnInit = "";
         */
         // bug1429296619632
         // don't clear out scope because variable set in onInit would be lost.
         // if we also reset lastOnInit and force the onInit to execute again,
         // the code in onInit could override changes in other script
         scope = initScope;

         if(scope != null) {
            // set the correct name (instead of place holder __initViewsheetScope)
            scope.getScriptEnv().put("viewsheet", initScope);

            // fix bug1429769800355, refresh the assembly scriptable. Because
            // initScope only updated when processOnInit, the added assembly
            // scriptables not copied to initScope, and nerver sync the scriptables
            // in next processOnInit.
            scope.refreshScriptable();
         }

         // ws may be changed in resetWS, so wbox should be created after that
         if(ws != null) {
            AssetQuerySandbox wbox = this.wbox;

            if(wbox == null) {
               createAssetQuerySandbox(ws);
            }
            else {
               wbox.setWorksheet(ws);
               wbox.setAdditionalVariableProvider(vs);

               // refresh columns to make sure vpm hidden columns are applied
               for(Assembly assembly : ws.getAssemblies()) {
                  wbox.refreshColumnSelection(assembly.getAbsoluteName(), false, true);
               }
            }
         }
         else {
            this.wbox = null;
         }
      }
      catch(Exception e) {
         LOG.error("Failed to refresh assemblies: " + e, e);
      }
      finally {
         unlockWrite();
      }
   }

   /**
    * Get the viewsheet in this sandbox.
    * @return the viewsheet contained in this sandbox.
    */
   public Viewsheet getViewsheet() {
      return vs;
   }

   /**
    * Get the base worksheet.
    */
   public Worksheet getWorksheet() {
      return vs == null ? null : vs.getBaseWorksheet();
   }

   /**
    * Get the variable table.
    */
   public VariableTable getVariableTable() {
      return wbox == null ? myvars : wbox.getVariableTable();
   }

   /**
    * Get the selections.
    * @return the selections, name -> SelectionVSAssembly.
    */
   public Hashtable<String, SelectionVSAssembly> getSelections() {
      return wbox == null ? new Hashtable<>() : wbox.getSelections();
   }

   /**
    * Get the asset query sandbox.
    */
   public AssetQuerySandbox getAssetQuerySandbox() {
      if(disposed && wbox == null) {
         LOG.debug("Worksheet/viewsheet has expired: {}", getSheetName());
         throw new ExpiredSheetException(getID(), getUser());
      }

      return wbox;
   }

   /**
    * Return the viewsheet mode;
    */
   public int getMode() {
      return vmode;
   }

   /**
    * Set the user of the sandbox.
    * @param user the specified principal.
    */
   public void setUser(Principal user) {
      this.user = user;

      for(ViewsheetSandbox box : bmap.values()) {
         box.setUser(user);
      }

      if(wbox != null) {
         wbox.setBaseUser(user);
      }
   }

   /**
    * Get the user of the sandbox.
    * @return the user of the sandbox.
    */
   public Principal getUser() {
      return user;
   }

   /**
    * Get the specific query manager for tracking the query of an assembly.
    */
   public QueryManager getQueryManager(String name) {
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         ViewsheetSandbox box = getSandbox(name.substring(0, index));
         name = name.substring(index + 1);
         return box.getQueryManager(name);
      }

      QueryManager qmgr = qmgrs.get(name);

      if(qmgr == null) {
         synchronized(qmgrs) {
            boolean cancelable =
               name == null || !name.startsWith(VSWizardConstants.TEMP_CROSSTAB_NAME);
            qmgr = qmgrs.computeIfAbsent(name, k -> new QueryManager(cancelable));
         }
      }

      return qmgr;
   }

   /**
    * Get the general query manager for tracking queries in this sandbox.
    */
   public QueryManager getQueryManager() {
      return queryMgr;
   }

   /**
    * Get all query managers for tracking the query of this viewsheet.
    */
   public Collection<QueryManager> getQueryManagers() {
      List<QueryManager> mgrs = new ArrayList<>(qmgrs.values());
      mgrs.add(queryMgr);
      return mgrs;
   }

   /**
    * Cancels all queries managed by this sandbox.
    */
   public void cancelAllQueries() {
      cancelAllQueries(false);
   }

   /**
    * Cancels all queries managed by this sandbox.
    */
   private void cancelAllQueries(boolean wizard) {
      Iterator it = qmgrs.keySet().iterator();
      QueryManager queryManager = null;

      while(it.hasNext()) {
         Object key = it.next();

         if(key == null || wizard && Tool.equals(key, VSWizardConstants.TEMP_CROSSTAB_NAME)) {
            continue;
         }

         queryManager = qmgrs.get(key + "");

         if(queryManager != null) {
            queryManager.cancel();
         }
      }

      if(queryMgr != null) {
         queryMgr.cancel();
      }

      resetScriptable();
      clearScriptableDataArrays();
      cancelTs = System.currentTimeMillis();

      if(scope != null) {
         scope.resetStartTime();
      }
   }

   /**
    * Get the last execution time.
    */
   public long getLastExecutionTime() {
      return execTS;
   }

   /**
    * Get the touch viewsheet timestamp.
    */
   public long getTouchTimestamp() {
      if(root != null) {
         return root.touchTS;
      }

      return touchTS;
   }

   /**
    * Set the touch viewsheet timestamp.
    */
   public void setTouchTimestamp(long touchTS) {
      this.touchTS = touchTS;
   }

   /**
    * Gets the time at which the queries were last cancelled.
    * @return the cancel timestamp or <tt>-1</tt> if never cancelled.
    */
   private long getCancelTimestamp() {
      if(root != null) {
         return root.cancelTs;
      }

      return cancelTs;
   }

   /**
    * Get the schedule info.
    */
   public ScheduleInfo getScheduleInfo() {
      return (root != null && root != this) ? root.getScheduleInfo()
         : scheduleInfo;
   }

   /**
    * Check if schedule action should be executed.
    */
   public boolean isScheduleAction() {
      return (root != null && root != this) ? root.isScheduleAction()
         : scheduleInfo.isScheduleAction();
   }

   /**
    * Set if schedule action should be executed. Set to false to cancel the
    * scheduled task.
    */
   public void setScheduleAction(ScheduleInfo sinfo) {
      if(root != null && root != this) {
         root.setScheduleAction(sinfo);
      }
      else {
         sinfo.setScheduleAction(
            this.scheduleInfo.isScheduleAction() && sinfo.isScheduleAction());
         this.scheduleInfo = sinfo;
      }
   }

   public void cancel() {
      cancel(false);
   }

   /**
    * Cancel this viewsheet sandbox.
    */
   public void cancel(boolean wizard) {
      Assembly[] arr = vs == null ? new Assembly[0] : vs.getAssemblies(false, false, true);

      // cancel graph pair
      for(int i = 0; i < arr.length; i++) {
         String name = arr[i].getName();
         VGraphPair pair = pairs.remove(name);

         if(pair != null) {
            pair.cancel();

            if(pair.getData() != null) {
               pair.getData().dispose();
            }
         }
      }

      // cancel executing queries
      cancelAllQueries();
      // cancel pending queries
      AssetDataCache.cancel(rid, !wizard);
   }

   /**
    * Dispose this viewsheet sandbox.
    */
   public void dispose() {
      disposed = true;
      getQueryManager().cancel(); // cancel executing queries
      metarep.dispose();

      if(wbox != null) {
         wbox.dispose();
         wbox = null;
      }

      // dispose data
      dmap.dispose();
      dKeyMap.dispose();
      tmap.clear();

      cancel();
      nolimit.clear();

      // dispose view
      vset.clear();
      painters.clear();
      pairs.clear();
      images.clear();

      disposeSandbox();
   }

   // dispose sandbox
   public void disposeSandbox() {
      List<String> list = new ArrayList<>(bmap.keySet());

      for(String name : list) {
         ViewsheetSandbox box = bmap.remove(name);
         box.dispose();
      }

      bmap.clear();
   }

   /**
    * Get the scope for executing formulas. The scope should contain all
    * data tables.
    * @return the scope for executing formulas.
    */
   public ViewsheetScope getScope() {
      ViewsheetScope scope = this.scope;

      // avoid synchronized
      if(scope != null) {
         return scope;
      }

      AssetQuerySandbox wbox = this.wbox;

      if(wbox == null) {
         lockRead();

         try {
            wbox = this.wbox;
         }
         finally {
            unlockRead();
         }
      }

      synchronized(scopeLock) {
         scope = this.scope;

         if(scope == null && initScope != null) {
            this.scope = scope = initScope;
         }

         if(scope == null) {
            scope = new ViewsheetScope(this, wbox != null);
            scope.setMode(vmode == Viewsheet.SHEET_DESIGN_MODE ?
                          AssetQuerySandbox.LIVE_MODE :
                          AssetQuerySandbox.RUNTIME_MODE);

            // chain viewsheet scope with worksheet scope
            if(wbox != null) {
               AssetQueryScope wscope = new AssetQueryScope(wbox);
               wscope.setMode(scope.getMode());
               JavaScriptEngine.addToPrototype(scope, wscope);
               scope.getScriptEnv().put("worksheet", wscope);
            }

            this.scope = scope;
            scope.getScriptEnv().put("pviewsheet", pviewsheet);
         }
      }

      AssetQuerySandbox.addMessageAttributes(getVariableTable());
      return scope;
   }

   /**
    * Set the drill-from viewsheet scope.
    */
   public void setPViewsheet(Object pviewsheet) {
      // always set pviewsheet so a script using pviewsheet won't fail
      this.pviewsheet = pviewsheet != null ? pviewsheet : new PViewsheetScriptable();
      ViewsheetScope scope = this.scope;

      if(scope != null) {
         scope.getScriptEnv().put("pviewsheet", pviewsheet);
      }
   }

   /**
    * Check if is in runtime mode.
    */
   public boolean isRuntime() {
      return vmode == Viewsheet.SHEET_RUNTIME_MODE;
   }

   /**
    * Get the sandbox of a sub viewsheet.
    * @param name the specified viewsheet name.
    * @return the sandbox of the specified sub viewsheet.
    */
   public ViewsheetSandbox getSandbox(String name) {
      if(root != this && root.getViewsheet().getAssembly(name) != null) {
         return root.getSandbox(name);
      }

      Viewsheet svs = (Viewsheet) vs.getAssembly(name);

      if(svs == null) {
         return null;
      }

      ViewsheetSandbox box = bmap.get(name);

      if(box == null) {
         lockWrite();

         try {
            box = bmap.get(name);

            if(box == null) {
               try {
                  AssetEntry entry = svs.getEntry();
                  AssetQuerySandbox wbox = root.getAssetQuerySandbox();
                  VariableTable vars = wbox == null ? null : wbox.getVariableTable();
                  Hashtable selections = wbox == null ? null : wbox.getSelections();
                  box = new ViewsheetSandbox(root, svs, vmode, getUser(), false, entry);

                  if(vars != null) {
                     Enumeration iter = vars.keys();
                     VariableTable vtable = box.getVariableTable();

                     while(vtable != null && iter.hasMoreElements()) {
                        String key = (String) iter.nextElement();
                        Object val = vars.get(key);
                        vtable.put(key, val);
                     }
                  }

                  if(selections != null) {
                     Enumeration iter = selections.keys();
                     Hashtable sel = box.getSelections();

                     while(sel != null && iter.hasMoreElements()) {
                        String key = (String) iter.nextElement();
                        Object val = selections.get(key);
                        sel.put(key, val);
                     }
                  }

                  bmap.put(name, box);
               }
               catch(ExpiredSheetException exSheetException) {
                  LOG.debug("Failed to initialize sandbox for viewsheet: {}", name, exSheetException);
               }
               catch(Exception ex) {
                  if(!(ex instanceof ConfirmException)) {
                     LOG.warn("Failed to initialize sandbox for viewsheet: {}", name, ex);
                  }
               }
            }
         }
         finally {
            unlockWrite();
         }
      }

      return box;
   }

   /**
    * Remove a sandbox, if the assembly is a viewsheet, the inner viewsheet
    * sandbox will also be removed.
    */
   public void removeSandbox(String cmd) {
      ViewsheetSandbox box = bmap.remove(cmd);

      if(box != null) {
         Viewsheet innervs = box.vs;
         Assembly[] assemblies = innervs.getAssemblies();

         for(Assembly assembly : assemblies) {
            removeSandbox(assembly.getAbsoluteName());
         }
      }
   }

   /**
    * Reset the viewsheet sandbox.
    * @param assembly the specified assembly to reset.
    * @param clist the changed assemblies.
    * @param initing <tt>true</tt> if initing the viewsheet.
    */
   private void reset0(Assembly assembly, ChangedAssemblyList clist,
                       boolean initing) {
      if(disposed) {
         return;
      }

      String name = assembly.getAbsoluteName();
      int index = name.lastIndexOf(".");
      String vname = index < 0 ? null : name.substring(0, index);
      reset(vname, new Assembly[] {assembly}, clist, initing, false, null);
   }

   /**
    * Get the bound table assembly.
    * @param assembly the specified base table assembly.
    * @param vassembly the specified viewsheet assembly.
    * @return the bound table assembly.
    */
   public TableAssembly getBoundTable(TableAssembly assembly, String vassembly, boolean detail)
         throws Exception
   {
      if(assembly == null) {
         return null;
      }

      String tname = assembly.getName();
      String nname = getBoundTableName(tname, vassembly);
      boolean vs_flag = tname.startsWith(Assembly.TABLE_VS);
      VSAssembly vobj = !vs.containsAssembly(vassembly) ? null : vs.getAssembly(vassembly);
      Worksheet ws = assembly.getWorksheet();

      if(assembly != null && vobj instanceof DynamicBindableVSAssembly) {
         DynamicBindableVSAssembly dassembly = (DynamicBindableVSAssembly) vobj;
         TableAssembly target = assembly;

         // @by jasonshobe, bug1411032367385. The assembly could be a snapshot
         // embedded table, so we need to check if it is a mirror first.
         if(vs_flag && (assembly instanceof MirrorTableAssembly)) {
            MirrorTableAssembly mirror = (MirrorTableAssembly) assembly;
            String bname = mirror.getAssemblyName();
            Worksheet bws = mirror.getWorksheet();
            target = (TableAssembly) bws.getAssembly(bname);

            if(ws != bws) {
               target = (TableAssembly) target.clone();
               ws.addAssembly(target);
            }
         }

         if(!"true".equals(target.getProperty("vs.cond"))) {
            ConditionList conds = dassembly.getPreConditionList();

            if(conds != null && !conds.isEmpty()) {
               ColumnSelection cols = target.getColumnSelection(false);
               addConditionCalcToColumn(target, dassembly.getTableName(), conds);
               conds = VSUtil.normalizeConditionList(cols, conds);
               conds.replaceVariables(getInputValues());
               ConditionListWrapper pwrapper =
                  target.getPreRuntimeConditionList();

               if(pwrapper != null && !pwrapper.isEmpty()) {
                  List<ConditionList> list = new ArrayList<>();
                  list.add(pwrapper.getConditionList());
                  list.add(conds);
                  conds = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
               }

               target.setPreRuntimeConditionList(conds);
            }

            appendDateComparisonConditions(vassembly, target);
            target.setProperty("vs.cond", "true");
         }
      }

      TableAssembly ntable = (TableAssembly) assembly.copyAssembly(nname);
      ws.addAssembly(ntable);
      MVManager mgr = MVManager.getManager();

      // strip off the prefix VS_
      if(vs_flag) {
         tname = tname.substring(Assembly.TABLE_VS.length());
      }

      // strip off the prefix S_
      if(tname.startsWith(Assembly.SELECTION)) {
         tname = tname.substring(Assembly.SELECTION.length());
      }

      if(isMVEnabled()) {
         try {
            RuntimeMV rmv = mgr.findRuntimeMV(entry, vs, vassembly, tname,
                                              (XPrincipal) user, this, isRuntime());
            ntable.setRuntimeMV(rmv);

            // apply mv for subquery
            if(rmv != null) {
               applySubQueryMV(entry, vs, ntable, ntable, (XPrincipal) user);
            }
         }
         // for on-demand MV
         catch(ConfirmException ex) {
            // ignore for exporting
            if(exportFormat == null) {
               throw ex;
            }
         }
      }

      return ntable;
   }

   /**
    * Append the date comparison condition to target table.
    * @param vassembly viewsheet assembly name.
    * @param target the table to be appended conditions.
    */
   public void appendDateComparisonConditions(String vassembly, TableAssembly target) {
      if(vs == null || Tool.isEmptyString(vassembly) || target == null) {
         return;
      }

      ConditionList dateComparisonConds = getDateComparisonConditions(vassembly);

      if(dateComparisonConds != null && !dateComparisonConds.isEmpty()) {
         List<ConditionList> list = new ArrayList<>();

         if(target.getPreRuntimeConditionList() != null &&
            target.getPreRuntimeConditionList().getConditionList() != null)
         {
            list.add(target.getPreRuntimeConditionList().getConditionList());
            list.add(dateComparisonConds);
            target.setPreRuntimeConditionList(
               ConditionUtil.mergeConditionList(list, JunctionOperator.AND));
         }
         else {
            target.setPreRuntimeConditionList(dateComparisonConds);
         }
      }
   }

   public ConditionList getDateComparisonConditions(String vassembly) {
      if(vs == null || Tool.isEmptyString(vassembly)) {
         return new ConditionList();
      }

      VSAssembly vobj = vs.getAssembly(vassembly);

      if(vobj == null) {
         return new ConditionList();
      }

      return getDateComparisonConditions(vobj.getVSAssemblyInfo());
   }

   private ConditionList getDateComparisonConditions(VSAssemblyInfo vsAssemblyInfo) {
      if(!(vsAssemblyInfo instanceof DateCompareAbleAssemblyInfo)) {
         return null;
      }

      DateCompareAbleAssemblyInfo info = (DateCompareAbleAssemblyInfo) vsAssemblyInfo;
      DateComparisonInfo dateComparisonInfo = DateComparisonUtil.getDateComparison(info, vs);

      if(dateComparisonInfo == null) {
         return null;
      }

      return dateComparisonInfo.getDateComparisonConditions(info.getDateComparisonRef());
   }

   /**
    * Apply mv for sub query.
    */
   private void applySubQueryMV(AssetEntry entry, Viewsheet vs,
                                TableAssembly ptable, TableAssembly table,
                                XPrincipal user)
   {
      if(entry == null || vs == null || table == null) {
         return;
      }

      ConditionListWrapper wrapper = table.getPreConditionList();
      applySubQueryMV(entry, vs, ptable, table, wrapper, user);
      wrapper = table.getPostConditionList();
      applySubQueryMV(entry, vs, ptable, table, wrapper, user);

      if(table instanceof ComposedTableAssembly) {
         TableAssembly[] stables =
            ((ComposedTableAssembly) table).getTableAssemblies(false);

         for(TableAssembly stable : stables) {
            applySubQueryMV(entry, vs, ptable, stable, user);
         }
      }
   }

   private void applySubQueryMV(AssetEntry entry, Viewsheet vs,
                                TableAssembly ptable, TableAssembly table,
                                ConditionListWrapper wrapper, XPrincipal user)
   {
      if(wrapper == null || wrapper.isEmpty()) {
         return;
      }

      ConditionList conds = wrapper.getConditionList();
      Worksheet ws = vs.getBaseWorksheet();

      if(ws == null) {
         return;
      }

      for(int i = 0; i < conds.getSize(); i += 2) {
         ConditionItem citem = conds.getConditionItem(i);
         XCondition cond = citem.getXCondition();

         if(!(cond instanceof AssetCondition)) {
            continue;
         }

         AssetCondition acnd = (AssetCondition) cond;
         SubQueryValue val = acnd.getSubQueryValue();

         if(val == null) {
            continue;
         }

         String query = val.getQuery();
         TableAssembly stable = query == null ? null : (TableAssembly) ws.getAssembly(query);

         if(stable == null || stable.getRuntimeMV() != null) {
            continue;
         }

         MVManager mgr = MVManager.getManager();

         try {
            RuntimeMV mv = mgr.findRuntimeMV(entry, vs, "", query, user, this,
                                             isRuntime(), true);
            stable.setRuntimeMV(mv);
         }
         catch(Exception ex) {
            // ignore it
         }

         applySubQueryMV(entry, vs, ptable, stable, user);
      }
   }

   /**
    * Check if use materialized view.
    * @param detail true if detailed data.
    */
   public boolean isMVEnabled(boolean detail) {
      return isMVEnabled() &&
         (!detail || "true".equals(SreeEnv.getProperty("mv.detail.data")));
   }

   /**
    * Check if use materialized view.
    */
   public boolean isMVEnabled() {
      return needMV() && !isMVDisabled();
   }

   private boolean needMV() {
      ViewsheetInfo vinfo = getViewsheet().getViewsheetInfo();
      return isRuntime() || !vinfo.isMetadata();
   }

   /**
    * Set whether to disable MV processing.
    */
   public void setMVDisabled(boolean flag) {
      synchronized(mvLock) {
         mvDisabled = flag;
      }
   }

   /**
    * Check whether the mv processing is disabled.
    */
   public boolean isMVDisabled() {
      synchronized(mvLock) {
         return mvDisabled;
      }
   }

   /**
    * Get the next table name.
    * @param base the specified base name.
    * @param vassembly the specified viewsheet assembly.
    * @return the next table name.
    */
   private String getBoundTableName(String base, String vassembly) {
      if(base.startsWith(Assembly.TABLE_VS)) {
         base = base.substring(Assembly.TABLE_VS.length());
      }

      int length = vassembly.length();

      if(length > 5) {
         vassembly = vassembly.substring(length - 5);
      }

      String name = Assembly.TABLE_VS + 'M' + base;

      if(!name.endsWith("_" + vassembly)) {
         name += "_" + vassembly;
      }

      return name;
   }

   /**
    * Re-initialize everything.
    */
   public void resetAll(ChangedAssemblyList clist) {
      lastOnInit = "";
      processOnInit();
      reset(null, vs.getAssemblies(), clist, true, true, null);
   }

   /**
    * Force init script to run on next refresh
    */
   public void clearInit() {
      lastOnInit = "";
   }

   /**
    * Initialize the runtime so the same variables for worksheet runtime
    * would be available for MV creation.
    */
   public void prepareMVCreation() {
      lastOnInit = "";
      processOnInit();

      try {
         processOnLoad(new ChangedAssemblyList(), false);
      }
      catch(Exception ex) {
         LOG.warn("Failed to process onLoad script for MV: " + ex, ex);
      }
   }

   /**
    * Reset the viewsheet sandbox.
    * @param clist the changed assemblies.
    */
   public void reset(ChangedAssemblyList clist) {
      reset(null, vs.getAssemblies(), clist, true, false, null);
   }

   /**
    * Reset the viewsheet sandbox.
    * @param arr the specified assemblies to reset.
    * @param clist the changed assemblies.
    * @param initing <tt>true</tt> if initing the viewsheet.
    * @param doOnLoad true to execute onLoad after resetting assemblies
    * but before executing view (scripts).
    * @param scopied the set contains the names of the selection assemblies
    * whose selections are copied from another viewsheet. Here we should start
    * to process selection from these selections.
    */
   public void reset(String vname, Assembly[] arr, ChangedAssemblyList clist,
                     boolean initing, boolean doOnLoad, Set<String> scopied)
   {
      reset(vname, arr, clist, initing, doOnLoad, scopied, false);
   }

   /**
    * Reset the viewsheet sandbox.
    * @param arr the specified assemblies to reset.
    * @param clist the changed assemblies.
    * @param initing <tt>true</tt> if initing the viewsheet.
    * @param doOnLoad true to execute onLoad after resetting assemblies
    * but before executing view (scripts).
    * @param scopied the set contains the names of the selection assemblies
    * whose selections are copied from another viewsheet. Here we should start
    * to process selection from these selections.
    * @param toggleMaxMode the toggle max/normal mode status.
    */
   public void reset(String vname, Assembly[] arr, ChangedAssemblyList clist,
                     boolean initing, boolean doOnLoad, Set<String> scopied, boolean toggleMaxMode)
   {
      long ts = System.currentTimeMillis();

      if(vname != null) {
         ViewsheetSandbox box = getSandbox(vname);

         if(box != null) {
            box.reset(null, arr, clist, initing, doOnLoad, null, toggleMaxMode);
            return;
         }
      }

      if(scopied == null) {
         scopied = new HashSet<>();
      }

      // get the current sheet name for debugging
      if(vname == null) {
         vname = getSheetName();
      }

      Tool.mergeSort(arr, new SelectionComparator(getViewsheet(), scopied));
      List<Assembly> roots = new ArrayList<>();

      for(int i = 0; i < arr.length; i++) {
         AssemblyEntry entry = arr[i].getAssemblyEntry();

         if(arr[i] instanceof EmbeddedTableVSAssembly) {
            try {
               outputDataChanged(entry, clist, false, initing);
            }
            catch(Exception ex) {
               if(!(ex instanceof ConfirmException)) {
                  if(LOG.isDebugEnabled()) {
                     LOG.warn("Failed to process a data changed event on viewsheet: {}, {}",
                              vname, entry.getName(), ex);
                  }
                  else {
                     LOG.warn("Failed to process a data changed event on viewsheet: {}, {} reason: {}",
                              vname, entry.getName(), ex.getMessage());
                  }
               }

               List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

               if(ex instanceof ConfirmDataException && entry != null) {
                  ConfirmDataException cex = (ConfirmDataException) ex;
                  cex.setName(entry.getAbsoluteName());
               }

               if(exs != null) {
                  exs.add(ex);
               }
            }
         }

         boolean root = isRootVSAssembly(arr[i]);

         if(root) {
            roots.add(arr[i]);
         }
      }

      // sort by dependency so value (e.g. input assembly selectedObject) used by
      // another assembly is available. (60791)
      Tool.mergeSort(roots, new DependencyComparator(vs, true));
      Assembly[] rarr = roots.toArray(new Assembly[0]);

      // 1. update root assembly, so that we can shrink worksheet
      // for logical model based Viewsheet properly. Only after update assembly,
      // could we get bound data refs from a VSAssembly
      for(int i = 0; i < rarr.length; i++) {
         AssemblyEntry entry = arr[i].getAssemblyEntry();

         try {
            if(entry.isVSAssembly()) {
               if(Thread.currentThread() instanceof GroupedThread) {
                  ((GroupedThread) Thread.currentThread())
                     .addRecord(LogContext.ASSEMBLY, entry.getAbsoluteName());
               }

               updateAssembly((VSAssembly) rarr[i]);
            }
         }
         catch(ConfirmException | ScriptException ex) {
            // ignore
         }
         catch(Exception ex) {
            if(isCancelled(ts)) {
               LOG.debug("Viewsheet cancelled: {}, {}", vname, entry.getName(), ex);
               return;
            }
            else if(LOG.isDebugEnabled()) {
               LOG.warn("Failed to update assembly on viewsheet: {}, {}",
                        vname, entry.getName(), ex);
            }
            else {
               LOG.warn("Failed to update assembly on viewsheet: {}, {} reason: {}",
                        vname, entry.getName(), ex.getMessage());
            }
         }
      }

      Map<AssemblyEntry, Set<AssemblyRef>> scriptDependencies = vs.getScriptDependings();

      // 2. reset root assembly
      for(int i = 0; i < rarr.length; i++) {
         resetAssembly(rarr[i], clist, initing, true, toggleMaxMode, scriptDependencies);
      }

      // 3. reset the others
      for(int i = 0; i < arr.length; i++) {
         AssemblyEntry entry = arr[i].getAssemblyEntry();
         boolean reset = !clist.contains(entry);

         if(reset) {
            // reset but ignore output query. The output query will be
            // executed after processSelections() so the filter conditions
            // will be set
            boolean execOutput = false;
            resetAssembly(arr[i], clist, initing, execOutput, toggleMaxMode, scriptDependencies);
         }
      }

      // make sure onInit is called. processOnInit ensures it's only run once. (57908)
      processOnInit();

      // process onLoad after reset and before the elements are processed
      if(doOnLoad) {
         VariableTable ovars = getVariableTable().clone();

         try {
            processOnLoad(clist, true);

            // if variable is changed in onLoad, the variable may impact the result of
            // output assembly (62818). since the query is already executed in
            // reset() -> resetAssembly() -> inputDataChanged() -> executeOutput(),
            // so execute them again to make sure the output reflect new parameter.
            if(!ovars.equals(getVariableTable())) {
               for(Assembly assembly : arr) {
                  executeOutput(assembly.getAssemblyEntry());
               }
            }
         }
         catch(Exception ex) {
            if(!(ex instanceof ConfirmException)) {
               if(isCancelled(ts)) {
                  LOG.debug("Viewsheet cancelled: {}, {}", vname, entry.getName(), ex);
                  return;
               }
               else if(LOG.isDebugEnabled()) {
                  LOG.warn("Failed to process onLoad script: {}", vname, ex);
               }
               else {
                  LOG.warn("Failed to process onLoad script: {}, reason: {}",
                           vname, ex.getMessage());
               }

            }

            List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

            if(exs != null) {
               exs.add(ex);
            }
         }
      }

      boolean processSelectionsFailed = false;

      // process selection and associations
      try {
         processSelections(null, clist, false, initing, scopied);
      }
      catch(Exception ex) {
         if(!(ex instanceof ConfirmException)) {
            processSelectionsFailed = true;

            if(isCancelled(ts)) {
               processSelectionsFailed = false;
               LOG.debug("Viewsheet cancelled: {}, {}", vname, entry.getName(), ex);
               return;
            }
            else if(LOG.isDebugEnabled()) {
               LOG.warn("Failed to process selections on viewsheet: {}", vname, ex);
            }
            else {
               LOG.warn("Failed to process selections on viewsheet: {}, reason: {}",
                        vname, ex.getMessage());
            }

            boolean isScheduler = false;

            try {
               isScheduler = (getVariableTable() != null && "true".equals(getVariableTable().get("__is_scheduler__")));
            }
            catch(Exception e) {
               //ignored
            }

            if(isScheduler) {
               throw new RuntimeException("Failure while processing viewsheet",ex);
            }
         }
         // make sure confirm exception (mv on-demand) is passed along (48347)
         else {
            WorksheetService.ASSET_EXCEPTIONS.set(new ArrayList<>());
         }

         List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

         if(exs != null) {
            exs.add(ex);
         }
      }

      // processSelections() above may have changed condition so cached data in dmap should
      // not be reused. could check for dependency to be more precise in the future. (61427)
      for(AssemblyEntry dataEntry : clist.getDataList()) {
         resetDataMap(dataEntry.getName());
      }

      List<AssemblyEntry> processedList = clist.getProcessedList();
      Map<TabVSAssembly, List<AssemblyEntry>> containerMap = new HashMap<>();
      Map<TabVSAssembly, String> tabOldSelected = new HashMap<>();

      if(processedList != null) {
         for(AssemblyEntry entry : processedList) {
            Assembly processedAssebmly = vs.getAssembly(entry);

            if(!(processedAssebmly instanceof VSAssembly)) {
               continue;
            }

            VSAssembly processedVsAssebmly = (VSAssembly) processedAssebmly;
            TabVSAssembly tabContainer = VSUtil.getTabContainer(processedVsAssebmly);

            if(tabContainer == null) {
               continue;
            }

            if(tabOldSelected.get(tabContainer) == null) {
               tabOldSelected.put(tabContainer, tabContainer.getSelected());
            }

            containerMap.computeIfAbsent(tabContainer, k -> new ArrayList<>()).add(entry);
         }
      }

      // execute output assembly, see comments above
      for(int i = 0; i < arr.length; i++) {
         AssemblyEntry entry = arr[i].getAssemblyEntry();
         // bug #62305, if the call to processSelections() failed for some reason, the assembly
         // may not have been executed. Force it to be executed here in that case to ensure that
         // protected data from what was saved at design time in the case of a VPM or additional
         // connections.
         boolean reset = processSelectionsFailed || !clist.contains(entry);

         if(reset) {
            try {
               executeOutput(entry);
            }
            catch(Exception ex) {
               if(!(ex instanceof ConfirmException)) {
                  if(isCancelled(ts)) {
                     LOG.debug("Viewsheet cancelled: {}, {}", vname, entry.getName(), ex);
                     return;
                  }
                  else if(LOG.isDebugEnabled()) {
                     LOG.warn("Failed to execute output query: {}, {}", vname, entry.getName(), ex);
                  }
                  else {
                     LOG.warn("Failed to execute output query: {}, {} reason: {}",
                              vname, entry.getName(), ex.getMessage());
                  }
               }

               List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

               if(exs != null) {
                  exs.add(ex);
               }
            }
         }
      }

      for(int i = 0; i < arr.length; i++) {
         AssemblyEntry entry = arr[i].getAssemblyEntry();

         if(entry.isVSAssembly()) {
            try {
               viewChanged(entry, clist, false, initing, true);
            }
            catch(Exception ex) {
               if(!(ex instanceof ConfirmException)) {
                  if(isCancelled(ts)) {
                     LOG.debug("Viewsheet cancelled: {}, {}", vname, entry.getName(), ex);
                     return;
                  }
                  else if(LOG.isDebugEnabled()) {
                     LOG.warn("Failed to process view changed event on: {}, {}",
                              vname, entry.getName(), ex);
                  }
                  else {
                     LOG.warn("Failed to process view changed event on: {}, {} reason: {}",
                              vname, entry.getName(), ex.getMessage());
                  }
               }

               List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

               if(exs != null) {
                  exs.add(ex);
               }
            }
         }
      }

      List vlist = clist.getViewList();
      List<Assembly> currentSelections = new ArrayList<>();

      // when reset assemblies, execute view here?
      for(int i = 0; i < vlist.size(); i++) {
         AssemblyEntry entry = (AssemblyEntry) vlist.get(i);

         if(entry.isVSAssembly()) {
            try {
               VSAssemblyInfo info = (VSAssemblyInfo) vs.getAssembly(entry.getName()).getInfo();

               //  If the assembly script was executed earlier before the viewsheet's onInit
               //  do not execute it again
               if(!(info instanceof ViewsheetVSAssemblyInfo) || !(info.isScriptEnabled() && info.getScript() != null &&
                  info.getScript().contains("thisParameter")))
               {
                  executeView(entry.getName(), false, initing);
               }

               Assembly assembly = vs.getAssembly(entry);

               if(assembly.getAssemblyType() == Viewsheet.CURRENTSELECTION_ASSET) {
                  currentSelections.add(assembly);
               }

               // refresh the assembly when tab container selected is changed by script.
               if(assembly instanceof TabVSAssembly && processedList != null) {
                  TabVSAssembly tabVSAssembly = (TabVSAssembly) assembly;

                  if(containerMap.get(tabVSAssembly) != null &&
                     !Tool.equals(tabOldSelected.get(assembly), tabVSAssembly.getSelected()))
                  {
                     processedList.removeAll(containerMap.get(tabVSAssembly));
                  }
               }
            }
            catch(Exception ex) {
               if(!(ex instanceof ConfirmException)) {
                  if(isCancelled(ts)) {
                     LOG.debug("Viewsheet cancelled: {}, {}", vname, entry.getName(), ex);
                     return;
                  }
                  else if(LOG.isDebugEnabled()) {
                     LOG.warn("Failed to execute view on: {}, {}", vname, entry.getName(), ex);
                  }
                  else {
                     String msg = ex.getMessage();

                     if(msg == null || msg.isEmpty()) {
                        msg = ex.toString();
                     }

                     LOG.warn("Failed to execute view on: {}, {} reason: {}",
                              vname, entry.getName(), ex.getMessage());
                  }
               }

               List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

               if(ex instanceof ConfirmDataException && entry != null) {
                  ConfirmDataException cex2 = (ConfirmDataException) ex;
                  cex2.setName(entry.getAbsoluteName());
               }

               if(exs != null) {
                  exs.add(ex);
               }
            }
         }
      }

      for(Assembly currentSelection : currentSelections) {
         if(currentSelection instanceof CurrentSelectionVSAssembly) {
            ((CurrentSelectionVSAssembly) currentSelection).updateOutSelection();
         }
      }

      // when reset assemblies, reset sub viewsheets?
      for(int i = 0; i < arr.length; i++) {
         if(arr[i] instanceof Viewsheet) {
            String name = arr[i].getAbsoluteName();
            Viewsheet vs = (Viewsheet) arr[i];
            // processing embedded vs here and track the changes independently since
            // there may be shared asset (e.g. data model) that are not uniquely identified
            // with full path between sub-vs. (61076)
            ChangedAssemblyList clist2 = new ChangedAssemblyList();
            reset(name, vs.getAssemblies(), clist2, initing, doOnLoad, null, toggleMaxMode);

            clist2.getDataList().clear();
            Viewsheet parent = vs.getViewsheet();
            AssemblyEntry parentEntry = parent == null ? null :
               new AssemblyEntry(parent.getName(), parent.getAbsoluteName(), parent.getAssemblyType());

            // don't need to add sub entries when parent entry have already in data list,
            // else will cause refresh duplicated times which will effect performance.
            if(parentEntry == null || !clist.contains(parentEntry)) {
               AssemblyEntry entry = new AssemblyEntry(arr[i].getName(), arr[i].getAbsoluteName(),
                                                       arr[i].getAssemblyType());
               clist2.getDataList().add(entry);
            }

            // merge to main list. this may not be necessary but keep track of all changes
            // like before.
            clist.mergeCore(clist2);
            clist.mergeFilters(clist2);
         }
      }
   }

   /**
    * Check if the viewsheet assembly is the root.
    * @param assembly the specified viewsheet assembly.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   private boolean isRootVSAssembly(Assembly assembly) {
      Assembly[] arr =
         AssetUtil.getDependedAssemblies(vs, assembly, false, false, true);

      for(int i = 0; i < arr.length; i++) {
         if(arr[i] instanceof VSAssembly) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if the specified assembly contains brush selection.
    */
   private boolean containsBrushSelection(VSAssembly assembly) {
      if(!(assembly instanceof ChartVSAssembly)) {
         return false;
      }

      ChartVSAssembly chart = (ChartVSAssembly) assembly;
      VSSelection selection = chart.getBrushSelection();
      return selection != null && !selection.isEmpty();
   }

   /**
    * Reset an assembly.
    * @param assembly the specified assembly to reset.
    * @param clist the specified changed assembly list.
    * @param initing true if initing.
    * @param execOutput true to execute output query.
    */
   private void resetAssembly(Assembly assembly, ChangedAssemblyList clist,
                              boolean initing, boolean execOutput, boolean toggleMaxMode,
                              Map<AssemblyEntry, Set<AssemblyRef>> scriptDependencies)
   {
      AssemblyEntry entry = assembly.getAssemblyEntry();

      // execute onInit and onLoad of embedded sheet on initial load
      if(assembly instanceof Viewsheet) {
         try {
            VSAssemblyInfo info = (VSAssemblyInfo) assembly.getInfo();

            //  Execute the container's assembly scripts that modify the nested viewsheet's parameters
            //  so that those parameters can be used in the nested viewsheet's onInit script
            if(info.isScriptEnabled() && info.getScript() != null &&
               info.getScript().contains("thisParameter"))
            {
               executeView(entry.getName(), false, initing);
            }

            ViewsheetSandbox box = getSandbox(assembly.getAbsoluteName());

            try {
               box.processOnInit();
               box.processOnLoad(new ChangedAssemblyList(), true);

               // if the parent embedded viewsheet is also delayed, need to add its delay to the
               // child assemblies
               int nestedDelay = delayedVisibilityAssemblies.entrySet().stream()
                  .filter(e -> e.getValue().contains(assembly.getAbsoluteName()))
                  .map(Map.Entry::getKey)
                  .max(Integer::compare)
                  .orElse(0);

               if(!box.getDelayedVisibilityAssemblies().isEmpty()) {
                  for(Map.Entry<Integer, Set<String>> e : box.getDelayedVisibilityAssemblies().entrySet()) {
                     addDelayedVisibilityAssemblies(e.getKey() + nestedDelay, e.getValue());
                  }

               }
            }
            finally {
               box.clearDelayedVisibilityAssemblies();
            }
         }
         catch(Exception ex) {
            LOG.warn(
               "Failed to process onInit() and onLoad() scripts when resetting assembly: {}",
               entry, ex);
         }
      }

      try {
         inputDataChanged(
            entry, clist, false, initing, execOutput, toggleMaxMode, scriptDependencies);
      }
      catch(Exception ex) {
         if(!(ex instanceof ConfirmException)) {
            LOG.warn(
               "Failed to process input data changed event when resetting assembly: {}", entry, ex);
         }

         List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

         if(ex instanceof ConfirmDataException && assembly != null) {
            ConfirmDataException cex = (ConfirmDataException) ex;
            cex.setName(assembly.getAbsoluteName());
         }

         if(exs != null) {
            exs.add(ex);
         }
      }

      if((assembly instanceof SelectionVSAssembly) ||
         (assembly instanceof InputVSAssembly) ||
         (assembly instanceof ChartVSAssembly))
      {
         try {
            outputDataChanged(
               entry, clist, false, initing, true, new HashMap<>(), scriptDependencies);
         }
         catch(Exception ex) {
            if(!(ex instanceof ConfirmException)) {
               LOG.warn(
                  "Failed to process output data changed event when resetting assembly: {}",
                  entry, ex);
            }

            List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

            if(ex instanceof ConfirmDataException && assembly != null) {
               ConfirmDataException cex2 = (ConfirmDataException) ex;
               cex2.setName(assembly.getAbsoluteName());
            }

            if(exs != null) {
               exs.add(ex);
            }
         }
      }
   }

   /**
    * Get the viewsheet sandboxes.
    * @return the viewsheet sandboxes.
    */
   public ViewsheetSandbox[] getSandboxes() {
      return Stream.concat(bmap.values().stream(), Stream.of(this))
         .toArray(ViewsheetSandbox[]::new);
   }

   /**
    * Process the change according to hint, which is generated when modify an
    * assembly. The takes care of the propagation of changes on one assembly
    * to the other assemblies in the viewsheet or the base worksheet (embedded).
    *
    * @param name  the specified assembly name.
    * @param hint  the specified change hint.
    * @param clist the specified changed assembly list.
    */
   public void processChange(String name, int hint, ChangedAssemblyList clist) throws Exception {
      processChange(name, hint, clist, 0);
   }

   /**
    * Process the change according to hint, which is generated when modify an
    * assembly. The takes care of the propagation of changes on one assembly
    * to the other assemblies in the viewsheet or the base worksheet (embedded).
    *
    * @param name  the specified assembly name.
    * @param hint  the specified change hint.
    * @param clist the specified changed assembly list.
    * @param type result type defined in DataMap.
    */
   public void processChange(String name, int hint, ChangedAssemblyList clist, int type)
      throws Exception
   {
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         ViewsheetSandbox box = getSandbox(name.substring(0, index));
         String embeddedName = name.substring(index + 1);
         box.processChange(embeddedName, hint, clist, type);
      }

      if((hint & VSAssembly.OUTPUT_DATA_CHANGED) == VSAssembly.OUTPUT_DATA_CHANGED) {
         cancel();
      }

      if((hint & VSAssembly.BINDING_CHANGED) != 0) {
         vs.fireEvent(Viewsheet.BINDING_CHANGED, name);
      }

      resetScriptable(type);
      processOnInit();
      processOnLoad(clist, true);
      processChange0(name, hint, clist);
   }

   /**
    * Reset scope.
    * This causes the scope and initScope to be recreated as well as forces
    * the onInit script to re-executed.  It should be called whenever assemblies
    * are added/removed/modified such that the scope of the assembly scriptables
    * could be affected including the viewsheet scriptable.
    */
   private void resetScope() {
      scope = null;
      initScope = null;
      lastOnInit = "";
      processOnInit();
   }

   /**
    * Reset scriptable.
    */
   public void resetScriptable() {
      resetScriptable(ALL);
   }

   /**
    * Reset scriptable.
    * @param type result type defined in DataMap.
    */
   public void resetScriptable(int type) {
      Assembly[] arr = vs == null ? null : vs.getAssemblies();

      if(arr != null) {
         for(Assembly assembly : arr) {
            VSAScriptable scriptable = getScope().getVSAScriptable(assembly.getName());

            if(scriptable != null) {
               scriptable.clearCache(type);
            }
         }
      }
   }

   private void clearScriptableDataArrays() {
      Assembly[] arr = vs == null ? null : vs.getAssemblies();

      if(arr != null) {
         for(Assembly assembly : arr) {
            VSAScriptable scriptable = getScope().getVSAScriptable(assembly.getName());

            if(scriptable instanceof TableDataVSAScriptable) {
               ((TableDataVSAScriptable) scriptable).clearDataArray();
            }
         }
      }
   }

   /**
    * Process the change according to hint, which is generated when modify an
    * assembly. The takes care of the proporgation of changes on one assembly
    * to the other assemblies in the viewsheet or the base worksheet (embedded).
    */
   private void processChange0(String name, int hint, ChangedAssemblyList clist)
      throws Exception
   {
      Assembly assembly = vs.getAssembly(name);

      if(assembly == null) {
         return;
      }

      AssemblyEntry entry = assembly.getAssemblyEntry();

      try {
         if(assembly instanceof SelectionVSAssembly) {
            schanged.set(Boolean.TRUE);
         }

         if((hint & VSAssembly.INPUT_DATA_CHANGED) != 0) {
            inputDataChanged(entry, clist, true, false, true);
         }

         if((hint & VSAssembly.OUTPUT_DATA_CHANGED) != 0) {
            outputDataChanged(entry, clist, true, false);
         }

         if((hint & VSAssembly.VIEW_CHANGED) != 0) {
            viewChanged(entry, clist, true, false, true);
         }

         if((hint & VSAssembly.DETAIL_INPUT_DATA_CHANGED) != 0) {
            detailInputDataChanged(entry, clist);
         }

         processScriptAssociation(entry, clist, vs.getScriptDependings());
         processSelections(entry, clist, true, false, new HashSet<>());
      }
      catch(DependencyCycleException ex) {
         List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

         if(exs != null) {
            exs.add(ex);
         }
      }
      finally {
         schanged.set(null);
      }
   }

   /**
    * Process the script association.
    * @param entry the specified assembly entry.
    * @param clist the specified changed assembly list.
    */
   private void processScriptAssociation(AssemblyEntry entry, ChangedAssemblyList clist,
                                         Map<AssemblyEntry, Set<AssemblyRef>> scriptDependencies)
   {
      if(entry == null || clist.isScriptDone(entry)) {
         return;
      }

      clist.addScriptDone(entry);
      Assembly assembly = vs.getAssembly(entry);

      if(!clist.getSelectionList().contains(entry) && (assembly instanceof SelectionVSAssembly)) {
         clist.getSelectionList().add(entry);
      }

      addAssemblyEntries(entry, clist);
      List<AssemblyEntry> entryList = new ArrayList<>();
      List<String> processed = new ArrayList<>();
      processScriptAssociation0(clist, entryList, processed, scriptDependencies);
   }

   /**
    * Process the script association.
    * @param clist the specified changed assembly list.
    */
   private void processScriptAssociation0(ChangedAssemblyList clist, List<AssemblyEntry> entryList,
                                          List<String> processed,
                                          Map<AssemblyEntry, Set<AssemblyRef>> scriptDependencies)
   {
      boolean changed = copyChangedEntries(clist.getDataList(), entryList) ||
                        copyChangedEntries(clist.getViewList(), entryList);

      for(AssemblyEntry entry : entryList) {
         if(processed.contains(entry.getAbsoluteName())) {
            continue;
         }

         processed.add(entry.getAbsoluteName());
         Set<AssemblyRef> refs = scriptDependencies.get(entry);

         if(refs != null) {
            for(AssemblyRef ref : refs) {
               addAssemblyEntries(ref.getEntry(), clist);
            }
         }
      }

      if(changed) {
         processScriptAssociation0(clist, entryList, processed, scriptDependencies);
      }
   }

   /**
    * Copy the changed assembly entries.
    */
   private boolean copyChangedEntries(List changedList,
                                      List<AssemblyEntry> entryList)
   {
      if(changedList == null) {
         return false;
      }

      boolean changed = false;
      Iterator iterator = changedList.iterator();

      while(iterator.hasNext()) {
         AssemblyEntry entry = (AssemblyEntry) iterator.next();

         if(entryList.contains(entry)) {
            continue;
         }

         changed = true;
         entryList.add(entry);
      }

      return changed;
   }

   /**
    * Add the assembly entry in changed assembly list.
    */
   private void addAssemblyEntries(AssemblyEntry entry, ChangedAssemblyList clist) {
      if(!clist.getDataList().contains(entry)) {
         clist.getDataList().add(entry);
      }

      if(!clist.getViewList().contains(entry)) {
         clist.getViewList().add(entry);
      }

      vset.remove(entry.getName());

      if(vs.getAssembly(entry) instanceof TableVSAssembly ||
         vs.getAssembly(entry) instanceof CrosstabVSAssembly)
      {
         dmap.remove(entry.getName(), DataMap.VSTABLE);
      }
   }

   /**
    * Check if is selection changed.
    */
   private boolean isSelectionChanged() {
      Object obj = schanged.get();
      return Boolean.TRUE.equals(obj);
   }

   /**
    * Process onInit javascript attached to this viewsheet.
    */
   public void processOnInit() {
      String onInit = vs.getViewsheetInfo().getOnInit();

      // execute for first time or whenever onInit is changed
      if(vs.getViewsheetInfo().isScriptEnabled() && !Tool.equals(onInit, lastOnInit)) {
         Principal oPrincipal = ThreadContext.getContextPrincipal();
         String vsOrgID = vs.getEntry() != null ? vs.getEntry().getOrgID():
                          vs.getRuntimeEntry() != null ? vs.getRuntimeEntry().getOrgID() : null;

         if(SUtil.isDefaultVSGloballyVisible(user) &&
               !Tool.equals(vsOrgID, OrganizationManager.getInstance().getCurrentOrgID()) &&
               Tool.equals(Organization.getDefaultOrganizationID(), vsOrgID)) {
            IdentityID pId = IdentityID.getIdentityIDFromKey(oPrincipal.getName());
            pId.setOrgID(Organization.getDefaultOrganizationID());
            XPrincipal tmpPrincipal = new XPrincipal(pId);
            tmpPrincipal.setOrgId(Organization.getDefaultOrganizationID());
            ThreadContext.setContextPrincipal(tmpPrincipal);
         }

         executeVSScript(lastOnInit = onInit, null);

         boolean scriptSelected = Arrays.stream(vs.getAssemblies())
            .filter(a -> a instanceof AbstractSelectionVSAssembly)
            .anyMatch(a -> ((AbstractSelectionVSAssembly) a).getScriptSelectedValues() != null);

         // if script set selection list states, we should run the associations so it behaves
         // like as if a user has clicked on it. (45130)
         if(scriptSelected) {
            reset(new ChangedAssemblyList());
         }

         final ViewsheetScope scope = this.scope;

         // save the initScope and use it on reset to avoid variables
         // set in init scope being lost on reset
         if(scope != null) {
            ViewsheetScope initScope = (ViewsheetScope) scope.clone();
            this.initScope = initScope;
            // add the initScope to the env, so if the env has not be init'ed,
            // it would be init'ed property with the parent scope set to
            // the script engine
            scope.getScriptEnv().put("__initViewsheetScope", initScope);
         }

         if(!Tool.equals(ThreadContext.getContextPrincipal(),oPrincipal)) {
            ThreadContext.setContextPrincipal(oPrincipal);
         }
      }
   }

   /**
    * After changing for Bug #45263, each thread can only used the variable execute result of itself, so need
    * process onload script for export thread.
    */
   public void prepareForExport() {
      try {
         processOnLoad(new ChangedAssemblyList(), false);
      }
      catch(Exception ex) {
         LOG.warn("Failed to process onLoad script for export: " + ex, ex);
      }
   }

   /**
    * Process onLoad script if onLoad has not been executed.
    */
   public void processOnLoadIf() throws Exception {
      if(!onLoadExeced) {
         processOnLoad(new ChangedAssemblyList(), false);
      }
   }

   /**
    * Process onLoad javascript attached to this viewsheet.
    * @param processDependency true to check if assemblies may have been
    * changed and trigger cascading selection processing
    */
   private void processOnLoad(ChangedAssemblyList clist, boolean processDependency)
         throws Exception
   {
      String onload = vs.getViewsheetInfo().getOnLoad();
      onLoadExeced = true;

      if(!vs.getViewsheetInfo().isScriptEnabled() ||
         onload == null || onload.trim().length() == 0)
      {
         return;
      }

      Set<AssemblyRef> refs = new HashSet<>();

      if(processDependency) {
         VSUtil.getReferencedAssets(onload, refs, vs, null);
      }

      Iterator<AssemblyRef> iterator = refs.iterator();
      List<VSAssembly> assemblies = new ArrayList<>();

      while(iterator.hasNext()) {
         AssemblyRef ref = iterator.next();
         String name = ref.getEntry().getName();
         boolean contained = vs.containsAssembly(name);

         if(contained) {
            VSAssembly assembly = vs.getAssembly(name);
            assemblies.add(assembly);
         }
      }

      int size = assemblies.size();

      if(size > 0) {
         // sort these might-be-changed viewsheet assemblies to make sure that
         // the root ones can process changes first
         Tool.mergeSort(assemblies, new DependencyComparator(vs, true));

         for(int i = 0; i < size; i++) {
            VSAssembly assembly = assemblies.get(i);
            assemblies.set(i, (VSAssembly) assembly.clone());
         }
      }

      VariableTable vars = getVariableTable();
      VariableTable ovars = vars.clone();

      executeVSScript(onload, ViewsheetScope.VIEWSHEET_SCRIPTABLE);

      // variable changed, clear cached data
      if(!vars.equals(ovars)) {
         metarep.clear();
         reset(clist);
      }

      // after executing script, it's time to process change one by one
      for(int i = 0; i < size; i++) {
         VSAssembly assembly = assemblies.get(i);
         String name = assembly.getName();
         VSAssembly assembly2 = vs.getAssembly(name);
         int hint = assembly.setVSAssemblyInfo(
            ((VSAssembly) assembly2.clone()).getVSAssemblyInfo());

         if(assembly instanceof SelectionListVSAssembly) {
            SelectionListVSAssembly sassembly = (SelectionListVSAssembly) assembly;
            SelectionListVSAssembly sassembly2 = (SelectionListVSAssembly) assembly2.clone();
            hint = hint | sassembly.copyStateSelection(sassembly2);
         }

         if(hint != 0) {
            processChange0(name, hint, clist);
         }
      }
   }

   /**
    * Execute viewsheet scope script.
    */
   private void executeVSScript(String cmd, String scriptable) {
      if(cmd == null || cmd.trim().length() == 0) {
         return;
      }

      try {
         ProfileUtils.addExecutionBreakDownRecord(getTopBoxID(),
            ExecutionBreakDownRecord.JAVASCRIPT_PROCESSING_CYCLE, args -> {
               getScope().execute(cmd, scriptable);
         });

         //getScope().execute(cmd, scriptable);
      }
      catch(Exception ex) {
         if(LOG.isDebugEnabled()) {
            LOG.debug("Failed to execute viewsheet script", ex);
         }
         else {
            LOG.warn("Failed to execute viewsheet script: {}", ex.getMessage());
         }

         CoreTool.addUserMessage(ex.getMessage());
      }
   }

   /**
    * Process the detail input data changed event.
    * @param entry the specified assembly entry.
    * @param clist the changed assembly list.
    */
   private void detailInputDataChanged(AssemblyEntry entry, ChangedAssemblyList clist) {
      if(disposed) {
         return;
      }

      DataVSAssembly assembly = (DataVSAssembly) vs.getAssembly(entry);
      String dtname = assembly.getName();
      AssemblyEntry tentry = new AssemblyEntry(dtname, Viewsheet.TABLE_VIEW_ASSET);

      if(!clist.getDataList().contains(tentry)) {
         clist.getDataList().add(tentry);
      }

      for(int type : dmap.getTypes(dtname)) {
         if((type & DataMap.DETAIL) == DataMap.DETAIL) {
            dmap.remove(dtname, type);
         }
      }
   }

   /**
    * Process the input data changed event.
    * @param entry the specified assembly entry.
    * @param clist the changed assembly list.
    * @param rselection <tt>true</tt> to reset selection, <tt>false</tt>
    * otherwise.
    * @param initing <tt>true</tt> if initing the viewsheet.
    * @param execOutput true to execute output query.
    */
   private void inputDataChanged(AssemblyEntry entry, ChangedAssemblyList clist,
                                 boolean rselection, boolean initing,
                                 boolean execOutput) throws Exception
   {
      inputDataChanged(
         entry, clist, rselection, initing, execOutput, false, vs.getScriptDependings());
   }

   /**
    * Process the input data changed event.
    * @param entry the specified assembly entry.
    * @param clist the changed assembly list.
    * @param rselection <tt>true</tt> to reset selection, <tt>false</tt>
    * otherwise.
    * @param initing <tt>true</tt> if initing the viewsheet.
    * @param execOutput true to execute output query.
    * @param toggleMaxMode the toggle max/normal mode status.
    */
   private void inputDataChanged(AssemblyEntry entry, ChangedAssemblyList clist,
                                 boolean rselection, boolean initing,
                                 boolean execOutput, boolean toggleMaxMode,
                                 Map<AssemblyEntry, Set<AssemblyRef>> scriptDependencies)
      throws Exception
   {
      inputDataChanged(
         entry, clist, rselection, initing, execOutput, toggleMaxMode, new HashMap<>(),
         scriptDependencies);
   }

   /**
    * Process the input data changed event.
    * @param entry the specified assembly entry.
    * @param clist the changed assembly list.
    * @param rselection <tt>true</tt> to reset selection, <tt>false</tt>
    * otherwise.
    * @param initing <tt>true</tt> if initing the viewsheet.
    * @param execOutput true to execute output query.
    * @param toggleMaxMode the toggle max/normal mode status.
    */
   private void inputDataChanged(AssemblyEntry entry, ChangedAssemblyList clist,
                                 boolean rselection, boolean initing,
                                 boolean execOutput, boolean toggleMaxMode,
                                 Map<AssemblyEntry, Boolean> inputChangeMap,
                                 Map<AssemblyEntry, Set<AssemblyRef>> scriptDependencies)
      throws Exception
   {
      // assembly name not unique across sub-sheets. (59099)
      AssemblyEntry fullEntry = new AssemblyEntry(
         this.entry.getPath() + "/" + entry.getAbsoluteName(), entry.getType());

      if(disposed || ignoreChange(entry) ||
         clist != null && clist.isInputChangeProcessed(fullEntry))
      {
         return;
      }

      Assembly vsAssembly = entry.isVSAssembly() ? vs.getAssembly(entry) : null;

      if(!(vsAssembly instanceof OutputVSAssembly) || execOutput) {
         clist.addInputChangeProcessed(fullEntry);
      }

      if(inputChangeMap == null) {
         inputChangeMap = new HashMap<>();
      }

      if(entry.isVSAssembly()) {
         if(inputChangeMap.get(entry) != null &&  inputChangeMap.get(entry)) {
            String message = Catalog.getCatalog()
               .getString("viewer.assembly.cycleDependOnWarning",
               entry.getAbsoluteName());
            throw new DependencyCycleException(message);
         }

         inputChangeMap = Tool.deepCloneMap(inputChangeMap);
         inputChangeMap.put(entry, true);
      }

      String vname = null;

      if(vs != null) {
         vname = vs.getName();
      }

      String name = entry.getName();
      boolean ready = clist.getReadyList().contains(entry) ||
         clist.getProcessedList().contains(entry);
      processScriptAssociation(entry, clist, scriptDependencies);

      // worksheet assembly?
      if(entry.isWSAssembly()) {
         if(wbox != null) {
            wbox.resetTableLens(name);

            // only reset metadata if it's not directply caused by a selection
            // change on the same table
            if(name.startsWith(VSAssembly.SELECTION)) {
               metarep.resetTableMetaData(name.substring(VSAssembly.SELECTION.length()));
            }

            Assembly[] arr = vs.getAssemblies();
            String aname = entry.getName();

            for(int i = 0; i < arr.length; i++) {
               if(!(arr[i].getInfo() instanceof BindableVSAssemblyInfo)) {
                  continue;
               }

               BindableVSAssemblyInfo info = (BindableVSAssemblyInfo) arr[i].getInfo();
               BindingInfo binfo = info.getBindingInfo();

               if(binfo == null || !Tool.equals(binfo.getTableName(), aname)) {
                  continue;
               }

               AssemblyEntry inputEntry = arr[i].getAssemblyEntry();
               AssemblyEntry inputFullEntry = new AssemblyEntry(
                  this.entry.getPath() + "/" + inputEntry.getAbsoluteName(), inputEntry.getType());

               if(clist.isInputChangeProcessed(inputFullEntry)) {
                  clist.removeInputChangeProcessed(inputFullEntry);
               }
            }
         }
      }
      // viewsheet assembly?
      else if(entry.isVSAssembly()) {
         if(!ready) {
            boolean schanged = isSelectionChanged();
            dmap.removeAll(name, !schanged);
            dKeyMap.removeAll(name, !schanged);
            clearGraph(name);
            tmap.put(name, System.currentTimeMillis());

            if(!toggleMaxMode && !isExportRefresh()) {
               syncFormData(name);
            }

            VSAssembly assembly = vs.getAssembly(name);

            // fix Bug #48737, should clear metadata if selection list/tree binded any calcfield.
            if((assembly instanceof SelectionListVSAssembly ||
               assembly instanceof SelectionTreeVSAssembly) &&
               ((SelectionVSAssemblyInfo) assembly.getVSAssemblyInfo()).bindedCalcFields())
            {
               String table =
                  ((SelectionVSAssemblyInfo) assembly.getVSAssemblyInfo()).getFirstTableName();
               metarep.resetTableMetaData(table);
            }
         }
      }
      else {
         throw new RuntimeException("Unsupported assembly found: "  + entry);
      }

      // viewsheet assembly?
      if(entry.isVSAssembly()) {
         if(!clist.getDataList().contains(entry)) {
            clist.getDataList().add(entry);
         }

         Assembly[] arr = vs.getAssemblies();
         String aname = entry.getName();

         for(int i = 0; i < arr.length; i++) {
            if(arr[i] instanceof TimeSliderVSAssembly) {
               TimeSliderVSAssembly slider = (TimeSliderVSAssembly) arr[i];

               if(slider.getSourceType() == XSourceInfo.VS_ASSEMBLY) {
                  String source = slider.getTableName();

                  if(aname.equals(source)) {
                     AssemblyEntry e = arr[i].getAssemblyEntry();

                     if(!clist.getDataList().contains(e)) {
                        clist.getDataList().add(e);
                        inputDataChanged(
                           e, clist, rselection, initing, execOutput, toggleMaxMode,
                           scriptDependencies);
                     }
                  }
               }
            }
            else if(arr[i] instanceof DataVSAssembly) {
               SourceInfo sourceInfo = ((DataVSAssembly) arr[i]).getSourceInfo();

               if(sourceInfo != null && sourceInfo.getType() == XSourceInfo.VS_ASSEMBLY) {
                  String source = VSUtil.getVSAssemblyBinding(sourceInfo.getSource());

                  if(aname.equals(source)) {
                     AssemblyEntry e = arr[i].getAssemblyEntry();

                     if(!clist.getDataList().contains(e)) {
                        clist.getDataList().add(e);
                        inputDataChanged(
                           e, clist, rselection, initing, execOutput, toggleMaxMode,
                           scriptDependencies);
                     }
                  }
               }
            }
         }
      }
      // worksheet assembly
      else if(!clist.getTableList().contains(entry)) {
         clist.getTableList().add(entry);
      }

      try {
         Assembly assembly = entry.isVSAssembly() ? vs.getAssembly(entry) : null;

         if(assembly instanceof OutputVSAssembly && execOutput) {
            executeOutput(entry);
         }
         else if(assembly instanceof ListInputVSAssembly) {
            Object data = getData(entry.getName());
            ListInputVSAssembly lassembly = (ListInputVSAssembly) assembly;

            if(data != null) {
               ListData ldata = (ListData) data;
               lassembly.setLabels(ldata.getLabels());
               lassembly.setValues(ldata.getValues());
               lassembly.setFormats(ldata.getFormats());
            }
            else {
               lassembly.setLabels(null);
               lassembly.setValues(null);
               lassembly.setFormats(null);
            }
         }
      }
      catch(Exception ex) {
         if(!(ex instanceof ConfirmException || ex instanceof ScriptException)) {
            if(LOG.isDebugEnabled()) {
               LOG.warn("Failed to update input data for assembly: {}, {}", vname, entry.getName(), ex);
            }
            else {
               LOG.warn("Failed to update input data for assembly: {}, {} reason: {}",
                        vname, entry.getName(), ex.getMessage());
            }
         }

         List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

         if(ex instanceof ConfirmDataException && entry != null) {
            ConfirmDataException cex = (ConfirmDataException) ex;
            cex.setName(entry.getAbsoluteName());
         }

         if(exs != null) {
            exs.add(ex);
         }
      }

      AssemblyRef[] refs = vs.getDependings(entry);

      for(int i = 0; i < refs.length; i++) {
         if(ignoreChange(refs[i].getEntry())) {
            continue;
         }

         if(refs[i].getType() == AssemblyRef.INPUT_DATA &&
            !refs[i].getEntry().equals(entry))
         {
            inputDataChanged(refs[i].getEntry(), clist, rselection, initing,
                             execOutput, toggleMaxMode, inputChangeMap, scriptDependencies);
         }
         else if(refs[i].getType() == AssemblyRef.OUTPUT_DATA) {
            outputDataChanged(
               refs[i].getEntry(), clist, rselection, initing, true, inputChangeMap,
               scriptDependencies);
         }
         else if(refs[i].getType() == AssemblyRef.VIEW) {
            viewChanged(refs[i].getEntry(), clist, rselection, initing,
                        execOutput);
         }
      }

      // viewsheet assembly?
      if(entry.isVSAssembly()) {
         Assembly assembly = vs.getAssembly(entry);

         if(assembly instanceof SelectionVSAssembly) {
            SelectionVSAssembly sassembly = (SelectionVSAssembly) assembly;
            updateAssembly(sassembly, true);
            outputDataChanged(
               entry, clist, rselection, initing, true, inputChangeMap, scriptDependencies);
         }
         else if(assembly instanceof InputVSAssembly) {
            InputVSAssembly iassembly = (InputVSAssembly) assembly;
            new InputVSAQuery(this, iassembly.getName()).resetEmbeddedData(initing);
            updateAssembly(iassembly);
            outputDataChanged(
               entry, clist, rselection, initing, true, inputChangeMap, scriptDependencies);
         }
         else if(containsBrushSelection((VSAssembly) assembly)) {
            outputDataChanged(
               entry, clist, rselection, initing, false, inputChangeMap, scriptDependencies);
         }
      }
   }

   // check if it's doing a refresh for export.
   private static boolean isExportRefresh() {
      return ViewsheetSandbox.exportRefresh.get() != null && ViewsheetSandbox.exportRefresh.get();
   }

   /**
    * Execute output assembly query and set value and related options.
    */
   public void executeOutput(AssemblyEntry entry) throws Exception {
      Assembly assembly = vs.getAssembly(entry);

      if(!(assembly instanceof OutputVSAssembly)) {
         return;
      }

      // @by larryl, don't execute script here, otherwise the element
      // script will be executed before onLoad
      //executeScript((OutputVSAssembly) assembly);

      Object data = getData(entry.getName());
      OutputVSAssemblyInfo outputInfo = (OutputVSAssemblyInfo)
         assembly.getInfo();
      BindingInfo binding = outputInfo.getBindingInfo();
      boolean noBinding = binding == null || binding.isEmpty();
      boolean isText = assembly instanceof TextVSAssembly;

      // binding is not null, need to set data.
      if(data != null || !noBinding && (isText || outputNullToZero)) {
         if(data == null) {
            boolean isDefaultText = assembly instanceof TextVSAssembly &&
               "text".equals(((TextVSAssembly) assembly).getTextValue());

            if(outputNullToZero && !isText) {
               data = 0;
            }
            // if result is null (e.g. aggregate on empty dataset), set the text to empty.
            // don't do this if the text has been changed so a placeholder text can be
            // specified for empty result
            else if(isDefaultText) {
               data = "";
            }
         }

         ((OutputVSAssembly) assembly).setValue(data);
      }
      else if(noBinding) {
         // @by stephenwebster, Originally for bug1407395559911, the output
         // assembly value is set to null here.  However, if the value is set
         // in the onInit script, the value is lost here.
         // For Bug #9617, Move the reset when the binding is modified to
         // EditPropertyOverEvent
         // ((OutputVSAssembly) assembly).setValue(null);
      }

      outputInfo.setOutputEnabled(data != null || !isRuntime() || noBinding ||
                                  outputInfo instanceof TextVSAssemblyInfo);

      if(outputInfo instanceof RangeOutputVSAssemblyInfo) {
         ((RangeOutputVSAssemblyInfo) outputInfo).setDefaultValues(true);
      }
   }

   /**
    * Check if ignore change.
    */
   private boolean ignoreChange(AssemblyEntry entry) {
      if(!isRuntime()) {
         return false;
      }

      return isForm(vs.getAssembly(entry));
   }

   /**
    * Check if form element.
    */
   private boolean isForm(Assembly assembly) {
      if(assembly instanceof ListInputVSAssembly) {
         ListInputVSAssemblyInfo info = (ListInputVSAssemblyInfo)
            assembly.getInfo();
         return info.isForm();
      }
      else if(assembly instanceof TextInputVSAssembly) {
         return true;
      }

      return false;
   }

   /**
    * Synchorize the form table data.
    */
   public void syncFormData(String name) {
      VSAScriptable scriptable = getScope().getVSAScriptable(name);

      if(scriptable instanceof TableDataVSAScriptable && !scriptChangedFormSet.contains(name)) {
         fmap.remove(name);
         scriptable.setAssembly(name);
      }
   }

   public void addScriptChangedForm(String name) {
      scriptChangedFormSet.add(name);
   }

   public void clearScriptChangedFormSet() {
      scriptChangedFormSet.clear();
   }

   /**
    * Get the standalone data assemblies.
    * @param vs the specified viewsheet.
    * @param table the specified table bound to by these data assemblies.
    */
   private Set<AssemblyEntry> getStandaloneDataAssemblies(Viewsheet vs, String table) {
      Set<AssemblyEntry> set = new HashSet<>();
      Assembly[] arr = vs.getAssemblies();

      for(int i = 0; i < arr.length; i++) {
         if(!(arr[i] instanceof BindableVSAssembly)) {
            continue;
         }

         BindableVSAssembly dassembly = (BindableVSAssembly) arr[i];

         if(!Tool.equals(dassembly.getTableName(), table)) {
            continue;
         }

         if(dassembly.isStandalone()) {
            set.add(dassembly.getAssemblyEntry());
         }
      }

      return set;
   }

   /**
    * Process the association of {@code sassembly} and the selection assemblies related to it.
    *
    * @param sassembly the specified selection viewsheet assembly.
    * @param clist     the specified changed assembly list.
    */
   private void processAssociation(AssociatedSelectionVSAssembly sassembly,
                                   ChangedAssemblyList clist, boolean initing,
                                   Set<String> scopied)
      throws Exception
   {
      if(disposed) {
         return;
      }

      /// map of table -> ref name -> selection
      final Map<String, Map<String, Collection<Object>>> appliedSelections = new HashMap<>();
      final Map<String, Map<String, Collection<Object>>> allSelections = new HashMap<>();
      String table = sassembly.getSelectionTableName();

      final SelectionVSAssembly[] sarr =
         SelectionVSUtil.getRelatedSelectionAssemblies(sassembly).toArray(new SelectionVSAssembly[0]);

      selectionTS = System.currentTimeMillis();
      long myTS = selectionTS;
      // fix bug1315540760520, if Measure or Formula is invaild, popup exception
      checkMeasureAndFormula(table);
      boolean singleSelectionReset = false;

      // requires to reset selection? reset the selections of the
      // associated selection assemblies, which are not directly
      // depended on by this selection assembly (cascade selection)
      if(isAssociationEnabled()) {
         final Set<SelectionVSAssembly> assembliesToReset =
            SelectionVSUtil.getNonNeighborAssembliesThatNeedReset(sassembly);

         for(SelectionVSAssembly selectionVSAssembly : assembliesToReset) {
            if(scopied.contains(selectionVSAssembly.getName())) {
               continue;
            }

            boolean changed = selectionVSAssembly.resetSelection();

            if(changed) {
               outputDataChanged(selectionVSAssembly.getAssemblyEntry(), clist, true, initing);
               final AssemblyInfo info = selectionVSAssembly.getInfo();

               if(info instanceof SelectionVSAssemblyInfo && ((SelectionVSAssemblyInfo) info).isSingleSelection()) {
                  singleSelectionReset = true;
               }
            }
         }

         // After resetting non-neighbor assemblies above, there may exist some tree neighbor
         // assemblies that also need to be reset.
         final Set<SelectionTreeLevelTuple> treeNeighborsToReset =
            SelectionVSUtil.getNeighborTreesThatNeedReset(sassembly);

         for(SelectionTreeLevelTuple treeNeighborTuple : treeNeighborsToReset) {
            final SelectionTreeVSAssembly treeAssembly = treeNeighborTuple.getTree();

            if(scopied.contains(treeAssembly.getName())) {
               continue;
            }

            boolean changed = treeAssembly.deselect(treeNeighborTuple.getLevel());

            if(changed) {
               outputDataChanged(treeAssembly.getAssemblyEntry(), clist, true, initing);
               final AssemblyInfo info = treeAssembly.getInfo();

               if(info instanceof SelectionVSAssemblyInfo && ((SelectionVSAssemblyInfo) info).isSingleSelection()) {
                  singleSelectionReset = true;
               }
            }
         }
      }

      /**
       * Bug #8281, If selection list binding hide column, metadata key is null and
       * will return null metadata, if direct return, the selection list value is error,
       * so use the null metadata to create empty selection value for selection list.
      if(metadata == null) {
         return;
      }

      metadata = (TableMetaData) metadata.clone();
      */
      // here we sort selection assemblies to apply dependency properly
      Set<String> set = new HashSet<>();
      set.add(sassembly.getName());
      Tool.mergeSort(sarr, new SelectionComparator(getViewsheet(), set));

      // this was apparently put in to deal with some kind of selection values out of sync
      // with underlying table. but it causes each selection list to be refreshed twice.
      // after experimenting different filter/parameter on subtable of mirror, it's unclear
      // under what circumstance this is needed. we can restore this for more narrow
      // condition in the future if this proves to be needed.
      //refreshSelectionExistence(sarr);

      if(sassembly.isEnabled()) {
         sassembly.getSelection(appliedSelections, true);
         sassembly.getSelection(allSelections, false);
      }

      for(SelectionVSAssembly assembly : sarr) {
         if(assembly.equals(sassembly)) {
            continue;
         }

         // for other assemblies, we should get all selected values regardless
         // of whether they were excluded so the exclusion can be reset from
         // the new selections of this (changed) selection
         if(assembly.isEnabled()) {
            assembly.getSelection(appliedSelections, false);
            assembly.getSelection(allSelections, false);
         }
      }

      Set<AssemblyEntry> dset = getStandaloneDataAssemblies(vs, table);
      int pos = getSelectionReadyPosition(sarr);

      // add pending
      for(int i = 0; i < sarr.length; i++) {
         AssemblyEntry entry = sarr[i].getAssemblyEntry();

         if(!clist.getPendingList().contains(entry) &&
            !clist.getProcessedList().contains(entry))
         {
            clist.getPendingList().add(entry);
         }
      }

      boolean openVS = Boolean.TRUE.equals(VSUtil.OPEN_VIEWSHEET.get());
      int autoSelectFirstMaxIndex = -1;

      for(int i = sarr.length - 1; openVS && i >= 0; i--) {
         final AssemblyInfo info = sarr[i].getInfo();

         if(info instanceof SelectionVSAssemblyInfo && ((SelectionVSAssemblyInfo) info).isSelectFirstItem()) {
            autoSelectFirstMaxIndex = i;
            break;
         }
      }

      for(int i = 0; i < sarr.length; i++) {
         if(isCancelled(myTS)) {
            return;
         }

         final SelectionVSAssembly selectionVSAssembly = sarr[i];
         refreshSelectionValue(selectionVSAssembly, appliedSelections, allSelections, clist);

         // table is ready to execute now
         if(i == pos && clist.isBreakable()) {
            // postpone the refresh process of bindable assemblies after refreshing the
            // selection assembly to apply the selection which changed by script in property dialog.
            if(!clist.isObjectPropertyChanged() ||
               selectionVSAssembly.getVSAssemblyInfo().getScript() == null)
            {
               // apply immature but valid condition list
               refreshRuntimeConditionList(table, isUnsupportedSelection(selectionVSAssembly), new HashSet<>());

               for(AssemblyEntry entry : dset) {
                  dmap.removeAll(entry.getName());
                  dKeyMap.removeAll(entry.getName());
                  tmap.put(entry.getName(), System.currentTimeMillis());
                  vset.remove(entry.getName());
                  images.remove(entry.getName());
                  painters.remove(entry.getName());
                  clearGraph(entry.getName());
                  syncFormData(entry.getName());
                  clist.addReady(entry, false);
               }
            }

            if(dset.size() > 0 && !isSafariOniOS && !singleSelectionReset &&
               autoSelectFirstMaxIndex < 0 && !isCancelled(myTS))
            {
               clist.fireReadyEvent();
            }
         }

         // @by davyc, wrong logic, fix bug1362555902671, the following
         // logic should be moved to SelectionListVSAQuery
         /*
         // fix bug1352718583196, the default value of single selecion value
         // will be set after refreshSelectionValue,
         // so should re-get all_selections and applied_selections here,
         // then the others can apply the selection
         if(sarr[i].equals(sassembly) && sarr[i].isEnabled()) {
            sarr[i].getSelection(applied_selections, false);
            sarr[i].getSelection(all_selections, false);
         }
         */

         // if a new selection is applied during processing a previous
         // selection, we can abandon the processing to free up database
         if(selectionTS != myTS) {
            break;
         }

         // selection is ready to execute now
         if(clist.isBreakable() && i > 0 && !singleSelectionReset && autoSelectFirstMaxIndex < 0) {
            clist.addReady(selectionVSAssembly.getAssemblyEntry(), i >= pos);
         }
      }

      if(selectionTS == myTS && (singleSelectionReset || autoSelectFirstMaxIndex >= 0)) {
         int refreshSelectionUpToIndex = -1;

         if(singleSelectionReset) {
            for(int i = sarr.length - 1; i >= 0; i--) {
               final AssemblyInfo info = sarr[i].getInfo();

               if(info instanceof SelectionVSAssemblyInfo &&
                  singleSelectionReset && ((SelectionVSAssemblyInfo) info).isSingleSelection())
               {
                  refreshSelectionUpToIndex = i;
                  break;
               }
            }
         }

         if(autoSelectFirstMaxIndex >= 0) {
            refreshSelectionUpToIndex = Math.max(refreshSelectionUpToIndex, autoSelectFirstMaxIndex + 1);
         }

         for(int i = 0; i < sarr.length && i < refreshSelectionUpToIndex; i++) {
            final SelectionVSAssembly selectionVSAssembly = sarr[i];
            refreshSelectionValue(selectionVSAssembly, appliedSelections, allSelections, clist);
            refreshRuntimeConditionList(table, isUnsupportedSelection(selectionVSAssembly),
                                        new HashSet<>());

            if(clist.isBreakable()) {
               clist.addReady(selectionVSAssembly.getAssemblyEntry(), i >= pos);
            }
         }

         for(int i = refreshSelectionUpToIndex; i < sarr.length; i++) {
            if(clist.isBreakable()) {
               clist.addReady(sarr[i].getAssemblyEntry(), i >= pos);
            }
         }
      }
      else if(selectionTS == myTS) {
         if(clist.isBreakable()) {
            clist.addReady(sarr[0].getAssemblyEntry(), true);
         }
      }
   }

   /**
    * Process shared filters within this sandbox and sandbox children, based on
    * the state of the provided assembly. If processChange is true, then this
    * method will perform processChange on each shared filter that matches.
    * Otherwise the assemblies that have shared filters applied will be added
    * to the clist SelectionList for future processing. (See
    * the recursive method: processSelections)
    *
    * @param fassembly     the specified source assembly.
    * @param clist         the change list to add modified assemblies to
    * @param processChange <tt>true</tt> to call processChange on each changed
    *                      selections, <tt>false</tt>should be used when already
    *                      in a processChange stack/context.
    * @return <tt>true</tt> if assemblies are changed
    */
   public boolean processSharedFilters(VSAssembly fassembly,
                                       ChangedAssemblyList clist,
                                       boolean processChange)
      throws Exception
   {
      // @davidd, Method moved from RuntimeViewsheet in v11.4b r39236.
      String filterId = vs.getViewsheetInfo().getFilterID(fassembly.getName());

      // Check whether this filter has already been processed
      if(filterId != null && clist != null && clist.isShareFilterProcessed(filterId)) {
         return false;
      }

      clist = clist == null ? new ChangedAssemblyList() : clist;

      if(filterId != null) {
         clist.setShareFilterProcessed(filterId);
      }

      // Always process shared filters from the root box
      ViewsheetSandbox[] boxes = root.getSandboxes();
      boolean result = false;

      for(ViewsheetSandbox box : boxes) {
         result |= box.applySharedFilters(fassembly, clist, processChange);
      }

      return result;
   }

   /**
    * Applies the shared filter on assemblies within this sandbox, based on
    * state and filterID of the provided assembly.
    *
    * @param fassembly     the specified source assembly.
    * @param clist         the change list to add modified assemblies to
    * @param processChange <tt>true</tt> to call processChange on each changed
    *                      selections, <tt>false</tt>should be used when already
    *                      in a processChange stack/context.
    * @return <tt>true</tt> if assemblies are changed
    */
   private boolean applySharedFilters(VSAssembly fassembly,
                                      ChangedAssemblyList clist,
                                      boolean processChange)
      throws Exception
   {
      boolean result = false;

      List<VSAssembly> tassemblies =
         VSUtil.getSharedVSAssemblies(getViewsheet(), fassembly);

      for(VSAssembly tassembly : tassemblies) {
         int hint = VSAssembly.NONE_CHANGED;

         if(tassembly instanceof SelectionVSAssembly) {
            hint = processSharedSelection(tassembly, fassembly);
         }
         else if(tassembly instanceof InputVSAssembly) {
            hint = VSUtil.copySelectedValues((InputVSAssembly) tassembly,
               (InputVSAssembly) fassembly);

            // setting value in 'parameter' so if it's referenced in onload, it would have
            // up-to-date value (48754).
            if(tassembly instanceof SingleInputVSAssembly) {
               getVariableTable().put(tassembly.getName(),
                                      ((SingleInputVSAssembly) tassembly).getSelectedObject());
            }
            else if(tassembly instanceof CompositeInputVSAssembly) {
               getVariableTable().put(tassembly.getName(),
                                      ((CompositeInputVSAssembly) tassembly).getSelectedObjects());
            }
         }

         // @by davidd, If the from and to assemblies come from different
         // viewsheets, then we force processing. Otherwise the
         // clist.selectionList would have entries of other viewsheets.
         if(fassembly.getViewsheet() != tassembly.getViewsheet()) {
            processChange = true;
         }

         // @by davidd, Only bound SelectionVSAssemblies get their view
         // processed. Force VIEW_CHANGED here, until we refactor the
         // Selection-centric approach of processSelections().
         if((hint & VSAssembly.OUTPUT_DATA_CHANGED) ==
            VSAssembly.OUTPUT_DATA_CHANGED)
         {
            hint |= VSAssembly.VIEW_CHANGED;
         }

         if(processChange) {
            // Use a temporary clist, so that processChange doesn't add
            // entries to "flow control" data structures (like selectionList).
            ChangedAssemblyList tempclist = new ChangedAssemblyList(false);

            // Copy the processed filters so they are not re-executed.
            tempclist.mergeFilters(clist);

            // @by davidd, Calling processChange within the tassemblies loop is
            // probably undesirable. For example, onLoad would be called
            // for each shared assembly.
            // I left it here for consistency with how it was done before
            processChange(tassembly.getAbsoluteName(), hint, tempclist);

            // @by davidd, BACKGROUND INFO: The ChangedAssemblyList data
            // structure contains data that controls the behavior of
            // processChange as well as data that controls the behavior of
            // VSEventUtil.execute. It is important that calls to processChange
            // provide a "clean" clist. It is also important that the changes
            // made to the execute-related data is kept.

            // Afterwards, merge back the execute related data from the clist.
            clist.mergeFilters(tempclist);
            clist.mergeCore(tempclist);
         }
         else {
            // Add this updated assembly to the list of changed Selections
            // to get process within the processChange0 recursion.
            if((hint & VSAssembly.OUTPUT_DATA_CHANGED) == VSAssembly.OUTPUT_DATA_CHANGED &&
               tassembly instanceof SelectionVSAssembly)
            {
               clist.getSelectionList().add(tassembly.getAssemblyEntry());
            }

            // @by davidd bug1368187136016, The hint is lost after returning,
            // be sure to process it here.
            AssemblyEntry entry = tassembly.getAssemblyEntry();

            try {
               if(tassembly instanceof SelectionVSAssembly) {
                  schanged.set(Boolean.TRUE);
               }

               if((hint & VSAssembly.INPUT_DATA_CHANGED) != 0) {
                  inputDataChanged(entry, clist, true, false, true);
               }

               if((hint & VSAssembly.OUTPUT_DATA_CHANGED) != 0) {
                  outputDataChanged(entry, clist, true, false);
               }

               if((hint & VSAssembly.VIEW_CHANGED) != 0) {
                  viewChanged(entry, clist, true, false, true);
               }

               if((hint & VSAssembly.DETAIL_INPUT_DATA_CHANGED) != 0) {
                  detailInputDataChanged(entry, clist);
               }
            }
            finally {
               schanged.set(null);
            }
         }

         result = result || (hint & VSAssembly.OUTPUT_DATA_CHANGED) ==
            VSAssembly.OUTPUT_DATA_CHANGED;
      }

      return result;
   }

   /**
    * Process shared selection.
    *
    * @param tassembly the target assembly
    * @param sassembly the source assembly
    * @return change state, see VSAssembly constants
    */
   public static int processSharedSelection(VSAssembly tassembly, VSAssembly sassembly) {
      int hint = 0;

      if(tassembly instanceof TimeSliderVSAssembly) {
         if(sassembly instanceof TimeSliderVSAssembly) {
            final TimeSliderVSAssembly targetSlider = (TimeSliderVSAssembly) tassembly;
            final TimeSliderVSAssembly sourceSlider = (TimeSliderVSAssembly) sassembly;

            final SelectionList intersection = targetSlider.getSelectionIntersection(sourceSlider);

            if(intersection != null) {
               hint = targetSlider.setStateSelectionList(intersection);

               boolean upperInclusive = targetSlider.getUpperInclusiveValue();
               int selectedCount = 0;

               for(SelectionValue val : targetSlider.getSelectionList().getSelectionValues()) {
                  if(val.isSelected()) {
                     selectedCount++;
                  }
               }

               ((TimeSliderVSAssembly) tassembly).setLengthValue(
                  upperInclusive ? Math.max(0, selectedCount - 1) : selectedCount);
            }
         }
      }
      else if(tassembly instanceof StateSelectionListVSAssembly) {
         SelectionList oslist = ((StateSelectionListVSAssembly) sassembly).getStateSelectionList();
         SelectionList slist = oslist == null ? null : (SelectionList) oslist.clone();

         // @by: ChrisSpagnoli bug1397460287828 #4 2014-8-19
         // Cannot simply set the target selections equal to the source assemblies,
         // instead have to to source selections add any selected target assemblies
         // which do not appear in the list of source values.
         SelectionList sourceList = ((StateSelectionListVSAssembly) sassembly).getSelectionList();
         SelectionList targetList = ((StateSelectionListVSAssembly) tassembly).getStateSelectionList();

         if(sourceList != null && targetList != null) {
            final SelectionValue[] sourceAllValuesList = sourceList.getSelectionValues();
            final SelectionValue[] targetSelectedList = targetList.getSelectionValues();

            // traverse list which is selected in target,
            for(SelectionValue t : targetSelectedList) {
               boolean foundFlag = false;

               // then traverse all possible source values,
               for(SelectionValue s : sourceAllValuesList) {
                  // if that target value is already in the source list, do nothing...
                  if(s.getLabel().equalsIgnoreCase(t.getLabel())) {
                     foundFlag = true;
                     break;
                  }
               }

               // but if it is missing, then have to add it
               if(!foundFlag && slist != null) {
                  slist.addSelectionValue(t);
               }
            }
         }

         if(noSelectionValues(sassembly, tassembly)) {
            return hint;
         }

         // before using the source selection list to set the target selection list.
         hint = ((StateSelectionListVSAssembly) tassembly).setStateSelectionList(slist);
      }
      else {
         hint = ((SelectionVSAssembly) tassembly).copyStateSelection(
            (SelectionVSAssembly) sassembly);
      }

      return hint;
   }

   private static boolean noSelectionValues(VSAssembly tassembly, VSAssembly sassembly) {
      String stable = sassembly.getTableName();
      String ttable = tassembly.getTableName();

      if(stable == null || !stable.startsWith("___inetsoft_cube_") ||
         ttable == null || !ttable.startsWith("___inetsoft_cube_"))
      {
         return false;
      }

      SelectionList slist = ((StateSelectionListVSAssembly) sassembly).getStateSelectionList();
      SelectionList tlist = ((StateSelectionListVSAssembly) tassembly).getSelectionList();

      for(int i = 0; i < slist.getSelectionValueCount(); i++) {
         String svalue = slist.getSelectionValue(i).getValue();

         for(int j = 0; j < tlist.getSelectionValueCount(); i++) {
            String tvalue = tlist.getSelectionValue(j).getValue();

            if(Tool.equals(svalue, tvalue)) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Check the measure and the formula is valid,
    * if invalid, throw RuntimeException.
    */
   private void checkMeasureAndFormula(String table) {
      Worksheet ws = getWorksheet();
      AbstractTableAssembly tassembly = (AbstractTableAssembly) ws.getAssembly(table);
      SelectionVSAssembly[] sarr = getSelectionVSAssemblies(table);
      ColumnSelection columns = tassembly.getColumnSelection();

      for(SelectionVSAssembly assembly0 : sarr) {
         if(!(assembly0.getInfo() instanceof SelectionBaseVSAssemblyInfo)) {
            continue;
         }

         SelectionBaseVSAssemblyInfo info =
            (SelectionBaseVSAssemblyInfo) assembly0.getInfo();
         String measureVal = info.getMeasureValue();
         String measure = info.getMeasure();
         DataRef ref = columns.getAttribute(measure);

         if((measureVal != null && measureVal.length() > 0 && measure == null) ||
            (measure != null && measure.length() > 0 && ref == null))
         {
            measure = measure != null ? measure : measureVal;
            throw new ColumnNotFoundException(
               Catalog.getCatalog().getString(
                  "common.viewsheet.measureInvalid", measure));
         }

         String formulaValue = info.getFormulaValue();
         String formula = info.getFormula();

         if(formulaValue != null && formula == null) {
             throw new MessageException(Catalog.getCatalog().getString(
                  "common.viewsheet.aggrInvalid", formulaValue));
         }
      }
   }

   /**
    * Get the position where selection is ready. Then we may start to execute
    * data viewsheet assembly at this position.
    * @param arr the specified selection arr.
    * @return the position where selection is ready.
    */
   private int getSelectionReadyPosition(SelectionVSAssembly[] arr) {
      // special is used to process association for calendar, map and
      // range slider. The special selection do not know whether to reset
      // the other selections or not when its selection state changed, so
      // we delay the ready position for association process to fix selection
      boolean special = false;
      int max = -1;
      String tname = arr.length == 0 ? null : arr[0].getTableName();
      Worksheet ws = getWorksheet();
      Assembly assembly = ws == null || tname == null ? null : ws.getAssembly(tname);
      TableAssembly tassembly = assembly instanceof TableAssembly ? (TableAssembly) assembly : null;
      boolean cube = tassembly != null && AssetUtil.isCubeTable(tassembly);

      // for cube, only mature condition list is supported
      if(cube) {
         return arr.length;
      }

      for(int i = arr.length - 1; i >= 1; i--) {
         if(!(arr[i] instanceof AssociatedSelectionVSAssembly)) {
            if(arr[i].isEnabled() && arr[i].containsSelection()) {
               special = true;
            }

            continue;
         }

         AssociatedSelectionVSAssembly aselection = (AssociatedSelectionVSAssembly) arr[i];
         // if a selection list has selected items, the items may change after selection
         // list is updated. it could be because the items is not in the new data, or
         // if metadata is used (and selected) in design time
         boolean mayChg = aselection.containsSelection();

         if(mayChg) {
            max = i + 1;
            break;
         }
      }

      for(int i = arr.length - 1; special && i >= 1; i--) {
         if(!(arr[i] instanceof AssociatedSelectionVSAssembly)) {
            continue;
         }

         AssociatedSelectionVSAssembly aselection = (AssociatedSelectionVSAssembly) arr[i];

         if(aselection.containsSelection()) {
            max = Math.max(max, i + 1);
            break;
         }
      }

      if(max >= 0) {
         return max;
      }
      else {
         return arr.length > 0 ? 1 : 0;
      }
   }

   /**
    * Refresh the selection value of a selection viewsheet assembly.
    *
    * @param sassembly         the specified selection viewsheet assembly.
    * @param appliedSelections selections that are used in condition.
    * @param allSelections     all selections including ones that are excluded.
    * @param clist             the changed assembly list
    */
   private void refreshSelectionValue(
      SelectionVSAssembly sassembly,
      Map<String, Map<String, Collection<Object>>> appliedSelections,
      Map<String, Map<String, Collection<Object>>> allSelections,
      ChangedAssemblyList clist) throws Exception
   {
      if(disposed) {
         return;
      }

      if(!(sassembly instanceof AssociatedSelectionVSAssembly)) {
         return;
      }

      Object data = getData(sassembly.getName());

      if(data == null) {
         AssociatedSelectionVSAssembly asassembly = (AssociatedSelectionVSAssembly) sassembly;
         asassembly.setSelectionList(null);
         asassembly.setStateSelectionList(null);

         return;
      }

      boolean IDMode = sassembly instanceof SelectionTreeVSAssembly && ((SelectionTreeVSAssembly) sassembly).isIDMode();
      final SelectionMeasureAggregation measureAggregation = new SelectionMeasureAggregation(IDMode);
      final Map<String, Set<Object>> values = new HashMap<>();

      for(String tableName : sassembly.getTableNames()) {
         final TableMetaData metadata = getTableMetaDataFromTable(tableName);

         final Map<String, Collection<Object>> tableAppliedSelections =
            appliedSelections.computeIfAbsent(tableName, (k) -> new HashMap<>());
         final Map<String, Set<Object>> tableValues =
            getAssociatedValues(sassembly, metadata, tableAppliedSelections, measureAggregation);

         for(Map.Entry<String, Set<Object>> entry : tableValues.entrySet()) {
            final Set<Object> vset = values.putIfAbsent(entry.getKey(), entry.getValue());

            if(vset != null) {
               vset.addAll(entry.getValue());
            }
         }
      }

      SelectionVSAQuery query = (SelectionVSAQuery) VSAQuery.createVSAQuery(
         this, sassembly, DataMap.NORMAL);
      // if a value is on the associated list, and it's excluded in the
      // selection, it means it has just become associated with a new selection
      // and should no longer be excluded

      XTable table = !(data instanceof XTable) ? null : (XTable) data;
      // updateBounds is called in refreshSelectionValue->checkAndRunCalcFieldMeasureQuery
      // don't call it here or it will double the values
      //measureAggregation.updateBounds();
      query.refreshSelectionValue(table, allSelections, appliedSelections, values,
                                  measureAggregation);
      viewChanged(sassembly.getAssemblyEntry(), clist, false, false, true);
   }

   /**
    * Get the table meta data for a table assembly.
    *
    * @param tname table assembly name.
    */
   private TableMetaData getTableMetaDataFromTable(String tname) throws Exception {
      return metarep.getTableMetaDataFromTable(tname);
   }

   /**
    * Get the table meta data for a selection assembly.
    * @param sname selection assembly name.
    */
   public TableMetaData getTableMetaData(String sname) throws Exception {
      return metarep.getTableMetaData(sname);
   }

   /**
    * Get the selection viewsheet assemblies have the same table target.
    * @param table the name of the specified table assembly.
    * @return the selection viewsheet assemblies.
    */
   public SelectionVSAssembly[] getSelectionVSAssemblies(String table) {
      return SelectionVSUtil.getDependingSelectionAssemblies(vs, table)
         .toArray(new SelectionVSAssembly[0]);
   }

   /**
    * Get associated values of a selection viewsheet assembly.
    *
    * @param sassembly          the specified selection viewsheet assembly.
    * @param metadata           the specified table metadata.
    * @param selections         the specified selection map.
    * @param measureAggregation selection measure aggregation.
    */
   private Map<String, Set<Object>> getAssociatedValues(
      SelectionVSAssembly sassembly,
      TableMetaData metadata,
      Map<String, Collection<Object>> selections,
      SelectionMeasureAggregation measureAggregation) throws Exception
   {
      DataRef[] refs = sassembly.getDataRefs();
      List<DataRef> datarefs = new ArrayList<>();
      Map<String, Set<Object>> values = new HashMap<>();

      if(metadata != null) {
         Map<String, Collection<Object>> tmap = new HashMap<>(selections);
         SelectionBaseVSAssemblyInfo info = (SelectionBaseVSAssemblyInfo) sassembly.getInfo();
         String measure = info.getMeasure();
         String formula = info.getFormula();
         CalculateRef calcField = vs.getCalcField(info.getTableName(), measure);

         // if aggregate calc, no further aggregation is performed. (53320)
         if(calcField != null && !calcField.isBaseOnDetail()) {
            formula = "Max";
         }

         String aggr = (measure != null && formula != null) ? formula + "(" + measure + ")" : null;
         final String selkey;

         if(!isAssociationEnabled()) {
            // no association, return empty set
            if(measure == null || formula == null) {
               return new HashMap<>();
            }
         }

         if(sassembly instanceof SelectionTreeVSAssembly &&
            ((SelectionTreeVSAssembly) sassembly).isIDMode())
         {
            selkey = ((SelectionTreeVSAssembly) sassembly).getID();
            DataRef tref = ((SelectionTreeVSAssembly) sassembly).getDataRef(selkey);

            if(tref != null) {
               datarefs.add(tref);
            }

            // don't pass tmap (selections) otherwise only the selected items will have
            // measures calculated
            metadata.getAssociatedValues(sassembly.getName(), new HashMap<>(), refs, aggr,
                                         measureAggregation);
         }
         else {
            selkey = VSUtil.getSelectionKey(refs);
            removeNeighborSelections(sassembly, tmap);
         }

         final Set<Object> vset = metadata.getAssociatedValues(
            sassembly.getName(), tmap,
            !datarefs.isEmpty() ? datarefs.toArray(new DataRef[] {}) : refs,
            aggr, measureAggregation);

         values.put(selkey, vset);
      }

      return values;
   }

   /**
    * Remove the selections of neighbors of {@code sassembly} from {@code selections}.
    *
    * @param sassembly  the selection assembly
    * @param selections the map of selections.
    */
   private void removeNeighborSelections(
      SelectionVSAssembly sassembly,
      Map<String, Collection<Object>> selections)
   {
      final DataRef[] refs = sassembly.getDataRefs();
      final String[] refNames = Arrays.stream(refs)
         .map(DataRef::getName)
         .toArray(String[]::new);
      final Iterator<String> iterator = selections.keySet().iterator();

      while(iterator.hasNext()) {
         final String key = iterator.next();
         String suffix = null;

         if(key.startsWith(SelectionVSAssembly.SELECTION_PATH)) {
            suffix = key.substring(SelectionVSAssembly.SELECTION_PATH.length());
         }

         if(suffix != null) {
            final String[] keyRefNames = VSUtil.parseSelectionKey(suffix);
            boolean matches = true;

            for(int i = 0; i < refNames.length && i < keyRefNames.length; i++) {
               if(!refNames[i].equals(keyRefNames[i])) {
                  matches = false;
                  break;
               }
            }

            if(matches) {
               iterator.remove();
            }
         }
      }

      if(refs.length > 0) {
         selections.remove(refs[0].getName());
      }
   }

   /**
    * Process the view changed event.
    * @param entry the specified assembly entry.
    * @param clist the changed assembly list.
    * @param execOutput true to execute output query.
    */
   private void viewChanged(AssemblyEntry entry, ChangedAssemblyList clist,
                            boolean rselection, boolean initing,
                            boolean execOutput)
      throws Exception
   {
      if(disposed) {
         return;
      }

      boolean ready = clist.getReadyList().contains(entry) ||
         clist.getProcessedList().contains(entry);
      Assembly assembly = vs.getAssembly(entry);

      if(assembly != null) {
         AssemblyInfo info = assembly.getInfo();

         if(info instanceof RangeOutputVSAssemblyInfo) {
            ((RangeOutputVSAssemblyInfo) info).setDefaultValues(true);
         }
      }

      if(!ready) {
         vset.remove(entry.getName());
         images.remove(entry.getName());
         painters.remove(entry.getName());
         clearGraph(entry.getName());
         dmap.remove(entry.getName(), DataMap.VSTABLE);
         dKeyMap.remove(entry.getName(), DataMap.VSTABLE);
      }

      if(!clist.getViewList().contains(entry)) {
         clist.getViewList().add(entry);
      }

      // view might depend on view
      AssemblyRef[] refs = vs.getViewDependings(entry);

      for(int i = 0; i < refs.length; i++) {
         if(refs[i].getType() == AssemblyRef.VIEW && !refs[i].getEntry().equals(entry)) {
            viewChanged(refs[i].getEntry(), clist, rselection, initing, execOutput);
         }
         else if(refs[i].getType() == AssemblyRef.INPUT_DATA) {
            inputDataChanged(refs[i].getEntry(), clist, rselection, initing, execOutput);
         }
      }
   }

   /**
    * Process the output data change event.
    * @param entry the specified assembly entry.
    * @param clist the changed assembly list.
    * @param rselection <tt>true</tt> to reset selection, <tt>false</tt>
    * otherwise.
    * @param initing <tt>true</tt> if initing the viewsheet.
    */
   private void outputDataChanged(AssemblyEntry entry,
                                  ChangedAssemblyList clist,
                                  boolean rselection, boolean initing)
      throws Exception
   {
      outputDataChanged(entry, clist, rselection, initing, true);
   }

   /**
    * Process the output data change event.
    * @param entry the specified assembly entry.
    * @param clist the changed assembly list.
    * @param rselection <tt>true</tt> to reset selection, <tt>false</tt>
    * otherwise.
    * @param initing <tt>true</tt> if initing the viewsheet.
    * @param triggerSelf true to trigger self changed.
    */
   private void outputDataChanged(AssemblyEntry entry,
                                  ChangedAssemblyList clist,
                                  boolean rselection, boolean initing,
                                  boolean triggerSelf)
      throws Exception
   {
      outputDataChanged(
         entry, clist, rselection, initing, triggerSelf, null, vs.getScriptDependings());
   }

   /**
    * Process the output data change event.
    * @param entry the specified assembly entry.
    * @param clist the changed assembly list.
    * @param rselection <tt>true</tt> to reset selection, <tt>false</tt>
    * otherwise.
    * @param initing <tt>true</tt> if initing the viewsheet.
    * @param triggerSelf true to trigger self changed.
    */
   private void outputDataChanged(AssemblyEntry entry, ChangedAssemblyList clist,
                                  boolean rselection, boolean initing, boolean triggerSelf,
                                  Map<AssemblyEntry, Boolean> inputChangeMap,
                                  Map<AssemblyEntry, Set<AssemblyRef>> scriptDependencies)
      throws Exception
   {
      if(disposed) {
         return;
      }

      Assembly assembly = vs.getAssembly(entry);
      boolean selection = assembly instanceof SelectionVSAssembly;

      // input viewsheet assembly? modify the value in the cell
      // of the target embedded table
      if(assembly instanceof InputVSAssembly) {
         InputVSAssembly iassembly = (InputVSAssembly) assembly;
         new InputVSAQuery(this, iassembly.getAbsoluteName()).resetEmbeddedData(initing);
         updateAssembly(iassembly);
         refreshVariable(iassembly, initing, clist);
      }
      // selection viewsheet assembly? let's process it later
      else if(selection) {
         if(!clist.getSelectionList().contains(entry)) {
            clist.getSelectionList().add(entry);
         }

         if(wbox != null) {
            Hashtable<String, SelectionVSAssembly> vt = wbox.getSelections();
            vt.put(assembly.getName(), ((SelectionVSAssembly) assembly));
            wbox.refreshSelections(vt);
         }
      }
      // embedded table assembly? set embedded data
      else if(assembly instanceof EmbeddedTableVSAssembly && triggerSelf) {
         EmbeddedTableVSAssembly eassembly = (EmbeddedTableVSAssembly) assembly;
         EmbeddedTableVSAQuery query = new EmbeddedTableVSAQuery(this, eassembly.getName(), false);
         query.setEmbeddedData();
         inputDataChanged(
            eassembly.getAssemblyEntry(), clist, rselection, initing, true, false, inputChangeMap,
            scriptDependencies);
      }
      // @by davyc, for container, update dynamic values of enable
      else if(assembly instanceof ContainerVSAssembly) {
         updateAssembly((VSAssembly) assembly, true);
      }

      if(!selection) {
         AssemblyRef[] refs = vs.getOutputDependings(entry);

         for(int i = 0; i < refs.length; i++) {
            if(refs[i].getType() == AssemblyRef.INPUT_DATA) {
               if(triggerSelf || !refs[i].getEntry().equals(entry)) {
                  if(!clist.isInputChangeProcessed(refs[i].getEntry())) {
                     inputDataChanged(
                        refs[i].getEntry(), clist, rselection, initing, true, false,
                        inputChangeMap, scriptDependencies);
                  }
               }
            }
            else if(refs[i].getType() == AssemblyRef.OUTPUT_DATA &&
               !refs[i].getEntry().equals(entry))
            {
               outputDataChanged(
                  refs[i].getEntry(), clist, rselection, initing, false, inputChangeMap,
                  scriptDependencies);
            }
            else if(refs[i].getType() == AssemblyRef.VIEW) {
               if(triggerSelf || !refs[i].getEntry().equals(entry)) {
                  viewChanged(refs[i].getEntry(), clist, rselection, initing, true);
               }
            }
         }
      }
   }

   /**
    * Refresh variable by target input assembly.
    *
    * @param iassembly the target input assembly.
    * @param initing  <tt>true</tt> if initing the viewsheet.
    * @throws Exception
    */
   private void refreshVariable(InputVSAssembly iassembly, boolean initing) throws Exception {
      refreshVariable(iassembly, initing, null);
   }

   /**
    * Refresh variable by target input assembly.
    *
    * @param iassembly the target input assembly.
    * @param initing  <tt>true</tt> if initing the viewsheet.
    * @throws Exception
    */
   private void refreshVariable(InputVSAssembly iassembly, boolean initing, ChangedAssemblyList clist)
      throws Exception
   {
      Object cdata = null; // single value
      Object mdata = null; // multiple value (null or array)

      if(iassembly instanceof SingleInputVSAssembly) {
         cdata = ((SingleInputVSAssembly) iassembly).getSelectedObject();
      }
      // checkbox should always be array so script don't need to check for different types
      else if(iassembly instanceof CompositeInputVSAssembly) {
         mdata = ((CompositeInputVSAssembly) iassembly).getSelectedObjects();
      }
      else {
         throw new RuntimeException("Unsupported assembly found: " + iassembly);
      }

      String tname = iassembly.getTableName();
      VariableTable vt = wbox == null ? new VariableTable() : wbox.getVariableTable();

      if(iassembly.isVariable() && tname != null && !tname.isEmpty() && tname.startsWith("$(")) {
         tname = tname.substring(2, tname.length() - 1);
      }

      if(wbox != null && tname != null && !tname.isEmpty()) {
         Worksheet ws = getWorksheet();
         Assembly ass = ws.getAssembly(tname);

         if(ass instanceof EmbeddedTableAssembly) {
            EmbeddedTableAssembly eassembly = (EmbeddedTableAssembly) ass;
            DataRef column = iassembly.getColumn();
            int row = iassembly.getRow();

            if(column != null && row > 0) {
               XEmbeddedTable edata = eassembly.getEmbeddedData();
               int col = AssetUtil.findColumn(edata, column);

               if(col >= 0 && row < edata.getRowCount()) {
                  edata.setObject(row, col, cdata);
                  // if the embedded table is changed by input, any cached data
                  // depending on it should be cleared
                  AssetDataCache.removeCacheDependence(eassembly);
                  removeInputProcessed(eassembly.getAssemblyEntry(), clist);
               }
            }

            if(!initing) {
               EmbeddedTableVSAQuery.syncData(eassembly, vs);
            }
         }
         else if(ass instanceof VariableAssembly) {
            vt.put(tname, mdata == null ? cdata : mdata);
            wbox.refreshVariableTable(vt);
         }
         else {
            UserVariable[] uvars = ws.getAllVariables();

            for(UserVariable var : uvars) {
               if(var != null && tname.equals(var.getName())) {
                  vt.put(tname, mdata == null ? cdata : mdata);
                  break;
               }
            }

            wbox.refreshVariableTable(vt);
         }
      }
      else if(wbox != null) {
         vt.put(iassembly.getName(), mdata == null ? cdata : mdata);
         wbox.refreshVariableTable(vt);
      }
   }

   private void removeInputProcessed(AssemblyEntry assemblyEntry, ChangedAssemblyList clist) {
      if(assemblyEntry != null) {
         AssemblyEntry fullEntry = new AssemblyEntry(
            this.entry.getPath() + "/" + assemblyEntry.getAbsoluteName(), assemblyEntry.getType());

         if(clist != null && clist.isInputChangeProcessed(fullEntry)) {
            clist.removeInputChangeProcessed(fullEntry);
            AssemblyRef[] refs = vs.getDependings(assemblyEntry);

            if(refs == null) {
               return;
            }

            for(AssemblyRef ref : refs) {
               removeInputProcessed(ref.getEntry(), clist);
            }
         }
      }
   }

   /**
    * Process the changed selections, until the selection list is empty.
    *
    * @param entry      the specified assembly entry to process, or null to
    *                   process entries in the clist selection list.
    * @param clist      the specified changed assembly list.
    * @param rselection <tt>true</tt> reset selection, <tt>false</tt> otherwise
    * @param initing    <tt>true</tt> if initing the viewsheet.
    */
   private void processSelections(AssemblyEntry entry,
                                  ChangedAssemblyList clist,
                                  boolean rselection,
                                  boolean initing,
                                  Set<String> scopied)
      throws Exception
   {
      if(disposed) {
         return;
      }

      List<AssemblyEntry> list = clist.getSelectionList();

      if(list.isEmpty()) {
         return;
      }

      Tool.mergeSort(list, new SelectionComparator(vs, scopied));
      Assembly assembly = entry == null ? null : vs.getAssembly(entry);

      if(!(assembly instanceof SelectionVSAssembly)) {
         entry = list.get(0);
         assembly = vs.getAssembly(entry);
      }

      // @by: ChrisSpagnoli bug1412261632374 #3 2014-10-16
      // It is possible for client to send VSLayoutEvent with an assembly
      // which has since been deleted.  So, check for null before proceeding.
      if(assembly == null) {
         return;
      }

      processSelection((SelectionVSAssembly) assembly, entry, clist, rselection, initing, scopied);

      // as the selection set might vary in the process,
      // we have to call the method recursively
      clist.removeFromSelectionList(entry); // avoid infinite loop
      processSelections(null, clist, rselection, initing, scopied);
   }

   /**
    * Refresh the runtime condition list.
    * @return the selection assemblies.
    */
   protected List<SelectionVSAssembly> refreshRuntimeConditionList(String table, boolean ignore,
                                                                   Set<String> processed)
      throws Exception
   {
      final List<SelectionVSAssembly> dependedSelections =
         Arrays.asList(getSelectionVSAssemblies(table));

      if(ignore || processed.contains(table)) {
         return dependedSelections;
      }

      Worksheet ws = getWorksheet();
      Assembly assembly0 = ws.getAssembly(table);

      if(!(assembly0 instanceof AbstractTableAssembly)) {
         return dependedSelections;
      }

      AbstractTableAssembly tassembly = (AbstractTableAssembly) assembly0;
      ConditionList conds;
      SourceInfo sourceInfo = tassembly.getSourceInfo();
      String source = sourceInfo == null ? null : sourceInfo.getSource();
      DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      XDataSource ds = null;

      if(source != null) {
         ds = registry.getDataSource(source);

         if(ds == null && sourceInfo.getPrefix() != null) {
            ds = registry.getDataSource(sourceInfo.getPrefix());
         }
      }

      // since model cube finally will execute by jdbc query, so use normal way to merge condition
      // to make sure rangeslider can generate right condition like "greater than min and less
      // than max" which min and max are specific data type insteadof generating one of condition
      // with selected string values for cube which may not match the column type in db.
      if(tassembly instanceof CubeTableAssembly && !(ds instanceof JDBCDataSource)) {
         conds = getMergedCubeConditionList(dependedSelections);
      }
      else {
         conds = getMergedConditionList(dependedSelections);
      }

      MVSession session = getAssetQuerySandbox().getMVSession();

      if(tassembly != null) {
         // mark table as having selection. used by jdbc pushdown for caching
         tassembly.setProperty("vs.selection.bound", "true");

         // condition might require calc column
         if(conds != null) {
            addConditionCalcToColumn(tassembly, table, conds);
         }

         if(session != null) {
            session.clearInitialized(tassembly.getName());
         }

         // if is a crosstab or a rotated table, the condition list has to be
         // post processed, otherwise we should check if aggregate info exists
         boolean post = !tassembly.isPlain();
         ColumnSelection columns = tassembly.getColumnSelection(post);
         conds = VSUtil.normalizeConditionList(columns, conds);

         if(post) {
            tassembly.setPostRuntimeConditionList(conds);
         }
         else {
            tassembly.setPreRuntimeConditionList(conds);

            // @by stephenwebster, For Bug #277
            // When updating the pre runtime conditions we should also check
            // to see if the worksheet assembly is being referenced in another
            // assembly's conditions as a subquery.  If so, the pre runtime
            // conditions should be updated for it as well.  I don't see a
            // usage for post runtime conditions, so I only applied the logic
            // here.
            for(Assembly assembly : ws.getAssemblies()) {
               if(assembly instanceof TableAssembly) {
                  ConditionListWrapper cWrapper = ((TableAssembly) assembly).getPreConditionList();

                  if(cWrapper == null) {
                     continue;
                  }

                  ConditionList condList = cWrapper.getConditionList();

                  for(int i = 0; i < condList.getSize(); i += 2) {
                     ConditionItem item = condList.getConditionItem(i);
                     XCondition xcond = item.getXCondition();

                     if(!(xcond instanceof AssetCondition)) {
                        continue;
                     }

                     AssetCondition cond = (AssetCondition) xcond;
                     SubQueryValue sub = cond.getSubQueryValue();

                     if(sub == null) {
                        continue;
                     }

                     String subTable = sub.getTable().getName();

                     if(subTable.equals(table)) {
                        sub.getTable().setPreRuntimeConditionList(conds);
                     }
                  }
               }
            }
         }
      }

      if(dependedSelections.size() != 0) {
         SelectionVSAssembly selectionVSAssembly = dependedSelections.get(0);

         if(!isRootVSAssembly(selectionVSAssembly)) {
            Assembly[] dependedAssemblies =
               AssetUtil.getDependedAssemblies(vs, selectionVSAssembly, false, false, true);
            processed.add(table);

            for(Assembly assembly : dependedAssemblies) {
               if(assembly instanceof VSAssembly) {
                  String tableName = ((VSAssembly) assembly).getTableName();

                  if(tableName != null) {
                     refreshRuntimeConditionList(((VSAssembly) assembly).getTableName(), ignore,
                                                 processed);
                  }
               }
            }
         }
      }

      return dependedSelections;
   }

   private ConditionList getMergedCubeConditionList0(DataRef column, List<Object> values) {
      ConditionList conds = new ConditionList();
      Condition cond = new Condition();
      cond.setOperation(Condition.ONE_OF);
      cond.setValues(values);
      ConditionItem conditionItem = new ConditionItem();
      conditionItem.setLevel(0);
      conditionItem.setXCondition(cond);
      conditionItem.setAttribute(column);
      conds.append(conditionItem);

      return conds;
   }

   private ConditionList getMergedCubeConditionList(List<SelectionVSAssembly> selectionAssemblies)
      throws Exception
   {
      final List<ConditionList> conditionLists = new ArrayList<>();

      for(SelectionVSAssembly sassembly : selectionAssemblies) {
         updateAssembly(sassembly, true);

         ConditionList conditionList = sassembly.getConditionList();
         List<Object> selectVals = new ArrayList<>();

         if(sassembly instanceof TimeSliderVSAssembly) {
            TimeSliderVSAssemblyInfo assemblyInfo =
               (TimeSliderVSAssemblyInfo) sassembly.getVSAssemblyInfo();
            SelectionList slist = assemblyInfo.getSelectionList();
            TimeSliderVSAssembly timeSliderVSAssembly = (TimeSliderVSAssembly) sassembly;
            TimeInfo timeInfo = timeSliderVSAssembly.getTimeInfo();

            if(timeInfo instanceof SingleTimeInfo) {
               if(slist != null) {
                  for(int i = 0; i < slist.getSelectionValueCount(); i++) {
                     SelectionValue sval = slist.getSelectionValue(i);

                     if(sval.isSelected()) {
                        selectVals.add(sval.getValue());
                     }
                  }
               }

               if(conditionList != null) {
                  ConditionList conds = getMergedCubeConditionList0(
                     conditionList.getConditionItem(0).getAttribute(), selectVals);
                  conditionLists.add(conds);
               }
            }
            else {
               DataRef[] dataRefs = ((CompositeTimeInfo) timeInfo).getDataRefs();
               List<Object>[] refSelectedValues = new List[dataRefs.length];

               if(slist != null) {
                  for(int i = 0; i < slist.getSelectionValueCount(); i++) {
                     SelectionValue sval = slist.getSelectionValue(i);

                     if(!sval.isSelected() || sval == null) {
                        continue;
                     }

                     String[] values = sval.getValue().split("::");

                     if(values.length != dataRefs.length) {
                        break;
                     }

                     for(int j = 0; j < values.length; j++) {
                        List<Object> objects = refSelectedValues[j];

                        if(objects == null) {
                           objects = new ArrayList<>();
                           refSelectedValues[j] = objects;
                        }

                        objects.add(values[j]);
                     }
                  }

                  for(int i = 0; i < dataRefs.length; i++) {
                     if(refSelectedValues[i] != null) {
                        conditionLists.add(getMergedCubeConditionList0(dataRefs[i], refSelectedValues[i]));
                     }
                  }
               }
            }
         }
         else {
            conditionLists.add(conditionList);
         }
      }

      return VSUtil.mergeConditionList(conditionLists, JunctionOperator.AND);
   }

   private ConditionList getMergedConditionList(List<SelectionVSAssembly> selectionAssemblies)
      throws Exception
   {
      final List<ConditionList> conditionLists = new ArrayList<>();

      for(SelectionVSAssembly sassembly : selectionAssemblies) {
         updateAssembly(sassembly, true);
         conditionLists.add(sassembly.getConditionList());
      }

      return VSUtil.mergeConditionList(conditionLists, JunctionOperator.AND);
   }

   /**
    * Add the condition used calc ref to table column selection.
    * @param assembly table assembly.
    * @param table the vsassembly bind table name.
    */
   private void addConditionCalcToColumn(TableAssembly assembly, String table,
                                         ConditionList conds)
   {
      ColumnSelection calcs = new ColumnSelection();
      VSUtil.appendCalcFields(calcs, table, vs, true);
      boolean changed = false;
      ColumnSelection columns = assembly.getColumnSelection();

      for(int i = 0; i < conds.getSize(); i += 2) {
         ConditionItem citem = conds.getConditionItem(i);
         DataRef cref = citem.getAttribute();

         if(cref == null) {
            continue;
         }

         DataRef ref = calcs.getAttribute(cref.getName());

         if(ref != null) {
            columns.addAttribute(ref);
            changed = true;
         }
      }

      if(changed) {
         assembly.resetColumnSelection();
      }
   }

   /**
    * Process selection change.
    *
    * @param assembly   the specified selection viewsheet assembly.
    * @param clist      the specified changed assembly list.
    * @param rselection <tt>true</tt> to reset selection, <tt>false</tt> otherwise.
    * @param initing    <tt>true</tt> if initing the viewsheet.
    */
   private void processSelection(SelectionVSAssembly assembly, AssemblyEntry entry0,
                                 ChangedAssemblyList clist, boolean rselection,
                                 boolean initing, Set<String> scopied)
      throws Exception
   {
      try {
         if(disposed) {
            return;
         }

         AssemblyEntry aentry = assembly.getAssemblyEntry();
         clist.addProcessedTimes(aentry);
         int scount = clist.getProcessedTimes(aentry);

         // @by billh, this is to avoid infinite loop in selecion.
         // For example, S1 and S2 bind to the same table, but S1 depends on
         // S2 in availability (S1.enabled = S2.selectedObjects.length > 0)
         if(scount > 2 && (!clist.isBreakable() || clist.getProcessedList().contains(aentry))) {
            clist.removeFromSelectionList(aentry);
            return;
         }

         if(wbox == null) {
            clist.removeFromSelectionList(aentry);

            if(assembly instanceof AssociatedSelectionVSAssembly) {
               AssociatedSelectionVSAssembly asassembly =
                  (AssociatedSelectionVSAssembly) assembly;
               asassembly.setSelectionList(null);
               asassembly.setStateSelectionList(null);
            }

            return;
         }

         String table = assembly.getTableName();
         List<String> tables = assembly.getTableNames();
         updateAssembly(assembly, true);

         // not yet bound?
         if(table == null || table.length() == 0) {
            clist.removeFromSelectionList(aentry);

            if(assembly instanceof AssociatedSelectionVSAssembly) {
               AssociatedSelectionVSAssembly asassembly =
                  (AssociatedSelectionVSAssembly) assembly;
               asassembly.setSelectionList(null);
               asassembly.setStateSelectionList(null);
            }

            if(assembly instanceof TimeSliderVSAssembly) {
               TimeSliderVSAssembly asassembly = (TimeSliderVSAssembly) assembly;
               asassembly.setSelectionList(null);
               asassembly.setStateSelectionList(null);
            }

         // @by nickgao,this will lead to the not selected dates,and
         // ArrayIndexOutofBoundsException when export a calendar.
         /*
            if(assembly instanceof CalendarVSAssembly) {
               CalendarVSAssembly asassembly =
                  (CalendarVSAssembly) assembly;
               asassembly.setDates(new String[0]);
               asassembly.setRange(new String[0]);
            }
         */

            if(!initing) {
               processSharedFilters(assembly, clist, false);
            }

            return;
         }

         // worksheet
         Worksheet ws = getWorksheet();
         boolean vsAssemblySource = false;

         if(assembly instanceof AbstractSelectionVSAssembly) {
            vsAssemblySource = ((AbstractSelectionVSAssembly) assembly).getSourceType() ==
               XSourceInfo.VS_ASSEMBLY;
         }

         AbstractTableAssembly tassembly = vsAssemblySource ? null :
            (AbstractTableAssembly) ws.getAssembly(table);

         if(tassembly == null && !vsAssemblySource) {
            clist.removeFromSelectionList(aentry);

            if(assembly instanceof AssociatedSelectionVSAssembly) {
               AssociatedSelectionVSAssembly asassembly = (AssociatedSelectionVSAssembly) assembly;
               asassembly.setSelectionList(null);
               asassembly.setStateSelectionList(null);
            }

            if(!initing) {
               processSharedFilters(assembly, clist, false);
            }

            return;
         }

         AssociatedSelectionVSAssembly associated =
            assembly instanceof AssociatedSelectionVSAssembly ?
               (AssociatedSelectionVSAssembly) assembly : null;

         final SelectionVSAssembly[] selectionAssemblies;


         if(tables != null) {
            selectionAssemblies = tables.stream().filter(t -> t != null)
               .map(t -> getSelectionVSAssemblies(t))
               .filter(ts -> ts != null)
               .flatMap(Arrays::stream)
               .distinct()
               .toArray(SelectionVSAssembly[]::new);
         }
         else {
            selectionAssemblies = getSelectionVSAssemblies(table);
         }

         // Iterate over SelectionVSAssemblies that share the same table binding
         // as the processed assembly, and refresh their selection value.
         // Note: Non AssociatedSelectionVSAssembly processing is deferred.
         for(SelectionVSAssembly sassembly : selectionAssemblies) {
            if(sassembly instanceof TimeSliderVSAssembly &&
               clist.getSelectionList().contains(sassembly.getAssemblyEntry()))
            {
               TimeSliderVSAQuery query = (TimeSliderVSAQuery) VSAQuery.
                  createVSAQuery(this, sassembly, DataMap.NORMAL);
               Object obj = getData(sassembly.getName());
               query.refreshSelectionValue(obj);
            }

            if(sassembly instanceof CalendarVSAssembly &&
               clist.getSelectionList().contains(sassembly.getAssemblyEntry()))
            {
               CalendarVSAQuery query = (CalendarVSAQuery) VSAQuery.
                  createVSAQuery(this, sassembly, DataMap.NORMAL);
               Object obj = getData(sassembly.getName());
               query.refreshSelectionValue(obj);
            }

            if(associated == null && sassembly instanceof AssociatedSelectionVSAssembly) {
               associated = (AssociatedSelectionVSAssembly) sassembly;
            }
         }

         // Process the AssociatedSelectionVSAssemblies (like Selection List
         // and Trees) that are based on the same table binding.
         if(associated != null) {
            processAssociation(associated, clist, initing, scopied);
         }
         // if not associated selection, we still need to process viewchange
         // so CurrentSelection container is updated
         else if(entry0 != null) {
            viewChanged(entry0, clist, rselection, initing, true);
         }

         // @by davidd, Shared filters are already processed when opening a vs
         if(!initing) {
            // @davidd bug1364406849572, Process local shared filters
            SelectionVSAssembly[] sarr = getSelectionVSAssemblies(table);

            // Process the assembly filters first
            processSharedFilters(assembly, clist, false);

            // Process other affected assembly filters
            for(SelectionVSAssembly selectionVSAssembly : sarr) {
               if(selectionVSAssembly != assembly) {
                  processSharedFilters(selectionVSAssembly, clist, false);
               }
            }
         }

         final Set<SelectionVSAssembly> selections = new HashSet<>();
         final Set<String> dependentTableNames;

         if(isUnsupportedSelection(assembly)) {
            dependentTableNames = new HashSet<>();
         }
         else if(assembly instanceof AssociatedSelectionVSAssembly) {
            final SelectionVSAssembly[] sarr =
               SelectionVSUtil.getRelatedSelectionAssemblies((AssociatedSelectionVSAssembly) assembly)
                  .toArray(new SelectionVSAssembly[0]);
            dependentTableNames = Arrays.stream(sarr)
               .flatMap((s) -> s.getTableNames().stream())
               .collect(Collectors.toSet());
         }
         else {
            dependentTableNames = new HashSet<>(assembly.getTableNames());
         }

         for(String tname : dependentTableNames) {
            selections.addAll(refreshRuntimeConditionList(tname, false, new HashSet<>()));
         }

         selections.forEach((s) -> clist.getSelectionList().remove(s.getAssemblyEntry()));

         // perform output changed
         for(SelectionVSAssembly sassembly : selections) {
            if(sassembly != null) {
               AssemblyEntry entry = sassembly.getAssemblyEntry();
               AssemblyRef[] refs = sassembly.getViewsheet().getOutputDependings(entry);

               for(AssemblyRef ref : refs) {
                  if(ref.getType() == AssemblyRef.INPUT_DATA) {
                     inputDataChanged(ref.getEntry(), clist, rselection, initing, true);
                  }
                  else if(ref.getType() == AssemblyRef.OUTPUT_DATA &&
                     !ref.getEntry().equals(entry))
                  {
                     outputDataChanged(ref.getEntry(), clist, rselection, initing);
                  }
                  else if(ref.getType() == AssemblyRef.VIEW) {
                     viewChanged(ref.getEntry(), clist, rselection, initing, true);
                  }
               }
            }
         }
      }
      catch(ConfirmDataException ex) {
         if(assembly != null) {
            ex.setName(assembly.getAbsoluteName());
         }

         throw ex;
      }
   }

   private boolean isUnsupportedSelection(VSAssembly assembly) {
      return WizardRecommenderUtil.isWizardTempAssembly(assembly.getAbsoluteName())
         || assembly.isWizardEditing();
   }

   /**
    * Execute the view of an assembly.
    * @param name the name of the specified assembly.
    * @param force <tt>true</tt> to execute the view forcely, <tt>false</tt>
    * otherwise.
    */
   public void executeView(String name, boolean force) throws Exception {
      executeView(name, force, false);
   }

   /**
    * Execute the view of an assembly.
    * @param name the name of the specified assembly.
    * @param force <tt>true</tt> to execute the view forcely, <tt>false</tt>
    * otherwise.
    * @param initing <tt>true</tt> if initing the viewsheet.
    */
   public void executeView(String name, boolean force, boolean initing) throws Exception {
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         ViewsheetSandbox box = getSandbox(name.substring(0, index));

         if(box == null) {
            return;
         }

         name = name.substring(index + 1);
         box.executeView(name, false, initing);
         return;
      }

      if(disposed) {
         return;
      }

      VSAssembly assembly = vs.getAssembly(name);

      if(assembly == null || !force && vset.contains(name)) {
         return;
      }

      try {
         if(Thread.currentThread() instanceof GroupedThread) {
           ((GroupedThread) Thread.currentThread())
               .addRecord(LogContext.ASSEMBLY, assembly.getAbsoluteName());
         }

         VSAssemblyInfo info = assembly.getVSAssemblyInfo();

         // if executing view, the dynamic values of the assembly will be re-executed.
         // however, if the value of a dynamic proeprty is set in script, it would not
         // be cleared and the previous value will be kept. we explicitly clear the
         // dynamic values in this narrow case. (43202, 43235)
         if(info.getScript() != null && !info.getScript().isEmpty() && info.isScriptEnabled()) {
            info.getViewDynamicValues(true).stream()
               // only clear dynamic values so values set in assembly script won't get lost(43202).
               .filter(v -> VSUtil.isDynamic(v))
               .forEach(v -> v.setRValue(null));
         }

         // view might depend on the dynamic values from data
         List<DynamicValue> dvalues = assembly.getDynamicValues();
         executeDynamicValues(name, dvalues);

         List<DynamicValue> vvalues = assembly.getViewDynamicValues(true);
         executeDynamicValues(name, vvalues);

         List<DynamicValue> hvalues = assembly.getHyperlinkDynamicValues();

         try{
            executeDynamicValues(name, hvalues, () -> "");
         }
         catch(Exception e) {
            Tool.addUserMessage(Catalog.getCatalog().getString(
                                   "viewer.viewsheet.hyperlinkScriptFailed", e.getMessage()));
         }

         // try refreshing metadata, for the other vsassemblies might use it
         // in script, e.g. Text1.text = Chart1['xFields'][0]
         refreshMetaData(assembly);

         if(assembly instanceof SelectionVSAssembly) {
            VSAQuery query = VSAQuery.createVSAQuery(this, assembly, DataMap.NORMAL);

            // @by billh, fix customer bug bug1307047842800
            // execute script in time slider to apply changes
            ((SelectionVSAQuery) query).refreshViewSelectionValue();

            if(assembly instanceof TimeSliderVSAssembly &&
               ((TimeSliderVSAssembly) assembly).getTimeInfo() instanceof SingleTimeInfo)
            {
               TimeSliderVSAQuery tquery = (TimeSliderVSAQuery) query;
               tquery.refreshSelectionValue(tquery.getData());
            }
         }
         else if(assembly instanceof ListInputVSAssembly) {
            if(initing && !((ListInputVSAssemblyInfo) assembly.getVSAssemblyInfo()).isForm()) {
               dmap.removeAll(name);
            }

            Object data = getData(name);
            ListInputVSAssembly lassembly = (ListInputVSAssembly) assembly;

            if(data instanceof ListData) {
               VSAQuery query = VSAQuery.createVSAQuery(this, lassembly, DataMap.NORMAL);
               executeScript(assembly);
               ListData ldata = (ListData) ((ListInputVSAssembly) assembly).getRListData().clone();
               ((InputVSAQuery) query).refreshView(ldata);
               Object[] values = ldata.getValues();
               lassembly.setLabels(ldata.getLabels());
               lassembly.setValues(values);
               lassembly.setFormats(ldata.getFormats());

               boolean openVS = Boolean.TRUE.equals(VSUtil.OPEN_VIEWSHEET.get());
               VSAssemblyInfo vsAssemblyInfo = lassembly.getVSAssemblyInfo();

               if(vsAssemblyInfo instanceof CheckBoxVSAssemblyInfo && openVS &&
                  ((CheckBoxVSAssemblyInfo) vsAssemblyInfo).isSelectFirstItem() &&
                  values != null && values.length > 0 &&
                  (((CheckBoxVSAssemblyInfo) vsAssemblyInfo).getSelectedObjects() == null ||
                  ((CheckBoxVSAssemblyInfo) vsAssemblyInfo).getSelectedObjects().length == 0))
               {
                  ((CheckBoxVSAssemblyInfo) vsAssemblyInfo)
                     .setSelectedObjects(new Object[] {values[0]});
                  applyShareFilter(lassembly);
               }

               lassembly.validate();

               refreshVariable(lassembly, initing);
            }
         }
         // by yanie: bug1412619712845
         // first execute script, then updateHighlight, otherwise, if the value
         // is set via script, the highlight will be behind another refresh
         //else if(assembly instanceof OutputVSAssembly) {
         //   OutputVSAssembly oassembly = (OutputVSAssembly) assembly;
         //   oassembly.updateHighlight(getAllVariables(),
         //      getConditionAssetQuerySandbox(assembly.getViewsheet()));
         //}
         else if(assembly.getAssemblyType() == Viewsheet.CURRENTSELECTION_ASSET) {
            ((CurrentSelectionVSAssembly) assembly).updateOutSelection();
         }
         else if(assembly instanceof ChartVSAssembly) {
            ChartVSAssembly chart = (ChartVSAssembly) assembly;
            Dimension size = VSUtil.getContentSize(chart, null);
            ChartDescriptor cdesc = chart.getChartDescriptor();

            // @davidd bug1364406849572, To prevent clearing an already
            // processed chart, moved clearGraph from addAssemblyEntries to here
            // avoid wiping out dynamic values when VGraphPair created repeatedly
            clearGraphIfChanged(name, cdesc);
            LegendsDescriptor ldesc = cdesc == null ? null : cdesc.getLegendsDescriptor();
            GraphUtil.fixLegendsRatio(chart.getVSChartInfo(), ldesc, size.width, size.height);
            GraphUtil.fixChartForeground(chart.getChartInfo());
         }
         else if(assembly instanceof TableVSAssembly) {
            TableVSAssemblyInfo tinfo = (TableVSAssemblyInfo) assembly.getVSAssemblyInfo();

            // execute data for ComboboxColumnOption
            if(isRuntime() && tinfo.isForm()) {
               ColumnSelection columns = tinfo.getColumnSelection();

               for(int i = 0; i < columns.getAttributeCount(); i++) {
                  DataRef ref = columns.getAttribute(i);

                  if(!(ref instanceof FormRef) || !(((FormRef) ref).getOption()
                     instanceof ComboBoxColumnOption))
                  {
                     continue;
                  }

                  ComboBoxColumnOption option =
                     (ComboBoxColumnOption) ((FormRef) ref).getOption();
                  ListData data = (ListData) getData(name + "^" + i + "^" + FORM_OPTION);
                  option.setListData(data);
              }
            }
         }

         executeScript(assembly);

         // by yanie: bug1412619712845
         // updateHighlight after script execution to make sure the value set
         // via script can be used in highlight in time.
         if(assembly instanceof OutputVSAssembly) {
            OutputVSAssembly oassembly = (OutputVSAssembly) assembly;
            oassembly.updateHighlight(getAllVariables(),
               getConditionAssetQuerySandbox(assembly.getViewsheet()));
         }
      }
      catch(ScriptException e) {
         List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

         if(exs != null) {
            exs.add(e);
         }
         else {
            exs = new ArrayList<>();
            exs.add(e);
            WorksheetService.ASSET_EXCEPTIONS.set(exs);
         }
      }
      finally {
         vset.add(name);
      }
   }

   private void applyShareFilter(VSAssembly assembly) throws Exception {
      RuntimeViewsheet[] arr = ViewsheetEngine.getViewsheetEngine().
         getRuntimeViewsheets(getUser());

      for(RuntimeViewsheet rvs : arr) {
         if(rvs == null || rvs.getViewsheet() == getViewsheet()) {
            continue;
         }

         rvs.getViewsheetSandbox().processSharedFilters(
            assembly, null, true);
      }
   }

   /**
    * Reset view.
    * @param name the specified name.
    */
   private void resetView(String name) {
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         ViewsheetSandbox box = getSandbox(name.substring(0, index));

         if(box == null) {
            return;
         }

         name = name.substring(index + 1);
         box.resetView(name);
         return;
      }

      vset.remove(name);
   }

   /**
    * Get the viewsheet table lens.
    * @param name the specified table data assembly name.
    * @param detail <tt>true</tt> if show detail, <tt>false</tt> otherwise.
    * @return the viewsheet table lens.
    */
   public VSTableLens getVSTableLens(String name, boolean detail) throws Exception {
      return getVSTableLens(name, detail, Float.NaN);
   }

   /**
    * Get the viewsheet table lens.
    * @param name the specified table data assembly name.
    * @param detail <tt>true</tt> if show detail, <tt>false</tt> otherwise.
    * @return the viewsheet table lens.
    */
   public VSTableLens getVSTableLens(String name, boolean detail, float rscaleFont)
         throws Exception
   {
      VSTableLens lens  = getBaseVSTableLens(name, detail);

      if(lens != null) {
         lens.setRScaleFont(Float.isNaN(rscaleFont) ? vs.getRScaleFont() : rscaleFont);
      }

      return lens;
   }

   /**
    * Get the viewsheet table lens.
    * @param name the specified table data assembly name.
    * @param detail <tt>true</tt> if show detail, <tt>false</tt> otherwise.
    * @return the viewsheet table lens.
    */
   private VSTableLens getBaseVSTableLens(String name, boolean detail) throws Exception {
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         ViewsheetSandbox box = getSandbox(name.substring(0, index));
         name = name.substring(index + 1);
         return box == null ? null : box.getVSTableLens(name, detail);
      }

      if(disposed) {
         return null;
      }

      VSAssembly assembly = vs.getAssembly(name);

      if(!(assembly instanceof DataVSAssembly)) {
         return null;
      }

      if(detail) {
         TableLens table = (TableLens) getData(name, true, DataMap.DETAIL);

         if(table == null) {
            return null;
         }

         return new VSTableLens(table);
      }
      else if(!(assembly instanceof TableDataVSAssembly)) {
         return null;
      }

      return getVSTableLens0(name, detail, true);
   }

   /**
    * Get the viewsheet table lens.
    *
    * @param name the specified table data assembly name.
    * @param detail <tt>true</tt> if show detail, <tt>false</tt> otherwise.
    * @param cache   <tt>true</tt> if result should be cached
    */
   private VSTableLens getVSTableLens0(String name, boolean detail,
                                       boolean cache) throws Exception
   {
      Object obj = dmap.get(name, DataMap.VSTABLE);
      final VSAssembly assembly = vs.getAssembly(name);

      if(AssetDataCache.isDebugData()) {
         obj = null;
      }

      if(obj == null) {
         lockRead();

         try {
            obj = dmap.get(name, DataMap.VSTABLE);

            if(AssetDataCache.isDebugData()) {
               obj = null;
            }

            if(obj == null) {
               TableDataVSAScriptable scriptable = null;

               try {
                  int type = detail ? DataMap.DETAIL : DataMap.NORMAL;
                  TableLens table = getTableData(name, type);
                  VariableTable variable = null;
                  Hashtable selections = null;

                  // no data available? clearing the data in gui might be better
                  if(table == null) {
                     XEmbeddedTable embedded = new XEmbeddedTable(new String[0], new Object[0][0]);
                     table = new XTableLens(embedded);
                  }
                  else {
                     DataVSAQuery query = (DataVSAQuery)
                        VSAQuery.createVSAQuery(this, assembly, DataMap.NORMAL);
                     VSTableLens vstable = query.getViewTableLens(table);
                     table = vstable.getTable();
                     variable = vstable.getLinkVarTable();
                     selections = vstable.getLinkSelections();
                  }

                  scriptable = (TableDataVSAScriptable) getScope().getVSAScriptable(name);

                  if(scriptable == null) {
                     return null;
                  }

                  scriptable.setTable(table);
                  TableLens table2 = table;

                  while((table2 instanceof TableFilter) &&
                        !(table2 instanceof RuntimeCalcTableLens))
                  {
                     table2 = ((TableFilter) table2).getTable();

                     if(table2 instanceof RuntimeCalcTableLens) {
                        // Bug #64633, need to reset the data array for calc tables if a new table
                        // is set
                        scriptable.clearDataArray();
                     }
                  }

                  int hint = executeScript(assembly);

                  if(hint != VSAssembly.NONE_CHANGED) {
                     scriptable.setDisableForm(true);
                     Object conn = getScope().getConnection();

                     if(conn instanceof DBScriptable) {
                        ((DBScriptable) conn).setScriptOver(true);
                     }

                     // runtime fields maybe cleared when execute script,
                     // so refresh to update runtimes.
                     if(assembly instanceof CrosstabVSAssembly) {
                        refreshMetaData(assembly);
                     }
                  }

                  VSTableLens vstable = new VSTableLens(scriptable.getTable());
                  vstable.setLinkVarTable(variable);
                  // feature1282052384195, set linked selection assemblies for the
                  // vstablelens, and if need to send the selection value as
                  // paramaters, we can use these assemblies to generate the
                  // selection paramaters.
                  vstable.setLinkSelections(selections);
                  obj = vstable;

                  if(assembly instanceof CrosstabVSAssembly &&
                     ((CrosstabVSAssemblyInfo) assembly.getInfo()).isDrillEnabled())
                  {
                     vstable.setCrosstabTree(((CrosstabVSAssembly) assembly).getCrosstabTree());
                  }

                  if(cache && hint != VSAssembly.NONE_CHANGED) {
                     processChange0(name, hint, new ChangedAssemblyList());
                     obj = getVSTableLens0(name, detail, false);
                  }
               }
               catch(ConfirmException cex) {
                  cache = false;
                  throw cex;
               }
               catch(CancelledException cex2) {
                  cache = false;
                  throw cex2;
               }
               finally {
                  if(cache) {
                     if(obj == null && VSUtil.isVSAssemblyBinding(assembly)) {
                        dmap.removeAll(name);
                        dKeyMap.removeAll(name);
                     }
                     else {
                        dmap.put(name, obj == null ? NULL : obj, DataMap.VSTABLE);
                        addDataKey(name, assembly, DataMap.VSTABLE);
                     }
                  }

                  if(scriptable != null) {
                     scriptable.setDisableForm(false);
                  }

                  Object conn = getScope().getConnection();

                  if(conn instanceof DBScriptable) {
                     ((DBScriptable) conn).setScriptOver(false);
                  }
               }
            }
         }
         finally {
            unlockRead();
         }
      }

      return NULL.equals(obj) ? null : (VSTableLens) obj;
   }

   /**
    * Execute assembly script.
    * @param assembly the specified assembly who may contain script.
    * @return changing hint of assembly info.
    */
   public int executeScript(VSAssembly assembly) throws Exception {
      return executeScript(assembly, false);
   }

   /**
    * Execute assembly script.
    * @param assembly the specified assembly who may contain script.
    * @return changing hint of assembly info.
    */
   public int executeScript(VSAssembly assembly, boolean skipForm) throws Exception {
      if(assembly == null) {
         return VSAssembly.NONE_CHANGED;
      }

      VSAssemblyInfo vinfo = assembly.getVSAssemblyInfo();

      if(vinfo == null || !vinfo.isScriptEnabled()) {
         return VSAssembly.NONE_CHANGED;
      }

      String script = vinfo.getScript();

      if(script == null || script.length() == 0) {
         return VSAssembly.NONE_CHANGED;
      }

      // this is ambiguous since we normally execute script before executing the
      // assembly (query). however, if the script access graph/dataset, we must
      // execute the chart first. to solve this we will need a pre and post script.
      if(JSTokenizer.containsTokens(script, "graph", "dataset")) {
         for(Assembly vsobj : vs.getAssemblies()) {
            if(vsobj instanceof ChartVSAssembly) {
               // make sure EGraph is available in script
               try {
                  getVGraphPair(vsobj.getAbsoluteName());
               }
               catch(Exception exc) {
                  LOG.debug("Failed to produce graph object for {}", vsobj.getAbsoluteName(), exc);
               }
            }
         }
      }

      VSAssemblyInfo ovinfo = (VSAssemblyInfo) vinfo.clone();

      Set<String> execAssemblies = ViewsheetSandbox.execAssemblies.get();

      // avoid recursive execution. script may be triggered from a script already
      // executing the script.
      if(execAssemblies.contains(assembly.getAbsoluteName())) {
         return VSAssembly.NONE_CHANGED;
      }

      execAssemblies.add(assembly.getAbsoluteName());

      try {
         // for Feature #26586, add javascript execution time record for current vs.

         ProfileUtils.addExecutionBreakDownRecord(getTopBoxID(),
            ExecutionBreakDownRecord.JAVASCRIPT_PROCESSING_CYCLE, args -> {
               getScope().execute(script, vinfo.getName(), skipForm);
            });

         //getScope().execute(script, vinfo.getName());
      }
      finally {
         execAssemblies.remove(assembly.getAbsoluteName());
      }

      return ovinfo.copyInfo(vinfo);
   }

   /**
    * Mark a table to ignore the time limit check.
    * @param name the specified assembly name.
    * @param limited <tt>true</tt> to check the time limit, <tt>false</tt>
    * otherwise.
    */
   public void setTimeLimited(String name, boolean limited) {
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         ViewsheetSandbox box = getSandbox(name.substring(0, index));
         name = name.substring(index + 1);
         box.setTimeLimited(name, limited);
         return;
      }

      if(limited) {
         nolimit.remove(name);
      }
      else {
         nolimit.add(name);
      }
   }

   /**
    * Check if time limit should be applied to an assembly.
    * @param name the specified assembly name.
    * @return <tt>true</tt> to check the time limit, <tt>false</tt> otherwise.
    */
   public boolean isTimeLimited(String name) {
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         ViewsheetSandbox box = getSandbox(name.substring(0, index));
         name = name.substring(index + 1);
         return box.isTimeLimited(name);
      }

      return !nolimit.contains(name);
   }

   /**
    * Get the data of an assembly.
    * @param name the name of the specified assembly.
    * <tt>false</tt> otherwise.
    * @return the data of the assembly.
    */
   public Object getData(String name) throws Exception {
      return getData(name, true, DataMap.NORMAL);
   }

   /**
    * Get the data of an assembly.
    * @param name the name of the specified assembly.
    * @param initial <tt>true</tt> if initialize data when data not available,
    * <tt>false</tt> otherwise.
    * @return the data of the assembly.
    */
   public Object getData(String name, boolean initial, int type) throws Exception {
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         ViewsheetSandbox box = getSandbox(name.substring(0, index));

         if(box == null) {
            return null;
         }

         name = name.substring(index + 1);
         return box.getData(name, initial, type);
      }

      if(disposed) {
         return null;
      }

      boolean isOptionData = name.contains(FORM_OPTION);
      String ass = isOptionData ? name.split("\\^")[0] : name;
      final VSAssembly assembly = vs.getAssembly(ass);

      if(assembly == null) {
         return null;
      }

      Object obj = dmap.get(name, type);

      if(AssetDataCache.isDebugData() || isDataExpired(name, type)) {
         obj = null;
      }

//      obj = null;

      if(obj == null && initial) {
         boolean cache = true;
         boolean inExec = JavaScriptEngine.getExecScriptable() != null;

         // if called from script, the locking should already be in place. lock it again
         // may cause deadlock if the processing is started in a separate thread. (52463)
         if(!inExec) {
            lockRead();
         }

         try {
            long ts1 = System.currentTimeMillis();

            obj = executeData(name, type);
            Long ts = tmap.get(name);

            // do not cache executing result if query should be discarded
            // when executing the query
            if((type & DataMap.DETAIL) == 0 && ts != null && ts1 < ts) {
               cache = false;
            }
         }
         catch(ConfirmException | CancelledException cex) {
            cache = false;
            throw cex;
         }
         catch(MVExecutionException ex) {
            boolean ondemand = "true".equals(
               SreeEnv.getProperty("mv.ondemand")) &&
               vs.getViewsheetInfo().isMVOnDemand() &&
               // export is not interactive and should not trigger a mv on demand
               exportFormat == null;

            if(ondemand) {
               recreateMVOnDemand();
               LOG.debug("Problem with existing materialized view, recreating: {}", name, ex);
            }
            else {
               LOG.warn("Failed to query materialized view: {}", name, ex);
               CoreTool.addUserMessage(ex.getMessage());
               // error should be shown to user
               throw ex;
            }
         }
         finally {
            // @by larryl, optimization, for selection data, the same table
            // is used again and again. For a large tree, the getObject could
            // get expensive if the table lens is nested very deep.
            if(cache) {
               if(obj == null && VSUtil.isVSAssemblyBinding(assembly)) {
                  dmap.removeAll(name);
                  dKeyMap.removeAll(name);
               }
               else {
                  dmap.put(name, obj == null ? NULL : obj, type);
                  addDataKey(name, assembly, type);
               }
            }

            if(!inExec) {
               unlockRead();
            }
         }
      }

      return NULL.equals(obj) ? null : obj;
   }

   /**
    * Remove and recreate the MV for this viewsheet.
    */
   private void recreateMVOnDemand() {
      // make sure the mapped buffers are collected since it's probably
      // already open when the recreating mv is triggered
      System.gc();

      MVManager mgr = MVManager.getManager();
      String vsId = entry.toIdentifier();
      MVDef[] marr = mgr.list(false, def -> !def.isWSMV());

      // @by Chris Spagnoli, for Bug #7080
      // As MVManager.findRuntimeMV() runs a background thread which invokes
      // MVMetaData.isRegistered(), delay invoking MVMetaData.unregister(),
      // until that background thread finishes.
      while(mgr.isPending(entry, (XPrincipal) user)) {
         try {
            Thread.sleep(100);
         } catch(Exception ex) {}
      }

      for(MVDef mv : marr) {
         if(mv.getMetaData().isRegistered(vsId)) {
            mgr.remove(mv, vsId);
            LOG.debug("Viewsheet {} unregistered from materialized view {} " +
                         "for on-demand recreation.", vsId, mv.getName());
         }
      }

      // mv creation triggered in findRuntimeMV
      entry.setProperty("mv_creation_failed", null);
      mgr.findRuntimeMV(entry, vs, "__anyv__", "__anyt__",
                        (XPrincipal) user, this, isRuntime());
   }

   /**
    * Reset the data map.
    * @param name the specified assembly name.
    */
   public void resetDataMap(String name) {
      resetDataMap(name, ALL);
   }

   /**
    * Reset the data map.
    * @param name the specified assembly name.
    * @param type result type defined in DataMap.
    */
   public void resetDataMap(String name, int type) {
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         ViewsheetSandbox box = getSandbox(name.substring(0, index));

         if(box != null) {
            name = name.substring(index + 1);
            box.resetDataMap(name, type);
         }

         return;
      }

      if(dmap != null && type == ALL) {
         dmap.removeAll(name);
         dKeyMap.removeAll(name);
      }
      else if(dmap != null && type != ALL) {
         dmap.remove(name, type);
         dKeyMap.removeAll(name);
      }
   }

   /**
    * Triggered when viewsheet action performed.
    * @param evt the specified action event.
    */
   @Override
   public void actionPerformed(ActionEvent evt) {
      if(disposed) {
         return;
      }

      int id = evt.getID();
      String cmd = evt.getActionCommand();

      // @by yanie: bug1419286445193
      // We should not reset the scope to null in this case,
      // otherwise, the oninit execution will be lost.
      // scope = null;
      // @by stephenwebster, For bug1431372698031
      // scope AND init scope should be reset, thus causing oninit
      // to re-execute, @see resetScope below.
      // in runtime, adding/removing is either annotation or adhoc filter, should not
      // force onInit to run again. (61106)
      boolean resetScope = !isRuntime();

      if(wbox != null) {
         wbox.resetDefaultColumnSelection();
      }

      if(id == Viewsheet.ADD_ASSEMBLY) {
         VSAssembly assembly = vs.getAssembly(cmd);

         if(assembly != null) {
            reset0(assembly, new ChangedAssemblyList(), assembly instanceof Viewsheet);
         }

         if(AnnotationVSUtil.isAnnotation(assembly)) {
            resetScope = false;
         }
      }
      else if(id == Viewsheet.REMOVE_ASSEMBLY) {
         removeItemOfVariableTable(cmd);
         dmap.removeAll(cmd);
         dKeyMap.removeAll(cmd);
         tmap.remove(cmd);
         vset.remove(cmd);
         images.remove(cmd);
         painters.remove(cmd);
         clearGraph(cmd);
         nolimit.remove(cmd);
         // when remove a viewsheet, remove all the inner viewsheets'
         // sandbox, so that when readd the viewsheet, the inner viewsheets'
         // sandbox will be recreated, fix bug1255517167386
         removeSandbox(cmd);
         QueryManager qmgr = qmgrs.remove(cmd);

         if(qmgr != null) {
            qmgr.cancel();
         }
      }
      else if(id == Viewsheet.RENAME_ASSEMBLY) {
         int index = cmd.indexOf('^');
         String oname = cmd.substring(0, index);
         String nname = cmd.substring(index + 1);

         dmap.rename(oname, nname);
         dKeyMap.rename(oname, nname);

         Object obj = tmap.remove(oname);

         if(obj != null) {
            tmap.put(nname, (Long) obj);
         }

         QueryManager qmgr = qmgrs.remove(oname);

         if(qmgr != null) {
            qmgrs.put(nname, qmgr);
         }

         boolean removed = nolimit.remove(oname);

         if(removed) {
            nolimit.add(nname);
         }

         obj = bmap.remove(oname);

         if(obj != null) {
            bmap.put(nname, (ViewsheetSandbox) obj);
         }

         obj = images.remove(oname);

         if(obj != null) {
            images.put(nname, (Image) obj);
         }

         obj = painters.remove(oname);

         if(obj != null) {
            painters.put(nname, (Painter) obj);
         }

         VGraphPair pair = pairs.remove(oname);
         graphLocks.remove(oname);

         if(pair != null) {
            pairs.put(nname, pair);
         }

         if(vset.contains(oname)) {
            vset.remove(oname);
            vset.add(nname);
         }
      }
      else {
         resetScope = false;
      }

      // @by yanie: bug1419286445193
      // Don't set the scope to null to reinit, but just update the assemblies'
      // VSAScriptable
      // @by stephenwebster, For bug1431372698031
      // scope and init scope should be reset otherwise when add/del/rename
      // assembly, scriptables will not be associated with the correct scope.
      if(resetScope) {
         resetScope();
      }
   }

   /**
    * Clear a cached graph.
    */
   public void clearGraph(String name) {
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         ViewsheetSandbox box = getSandbox(name.substring(0, index));
         name = name.substring(index + 1);
         box.clearGraph(name);
         return;
      }

      VGraphPair pair = pairs.remove(name);

      if(pair != null) {
         pair.cancel();
      }

      graphLocks.remove(name);
   }

   /**
    * Clear a cached graph if chart descriptor changed.
    */
   public void clearGraphIfChanged(String name, ChartDescriptor cdesc) {
      VGraphPair pair = pairs.get(name);

      if(pair != null && !pair.isSameDescriptor(cdesc)) {
         pairs.remove(name);
         pair.cancel();
      }
   }

   /**
    * Save chart info as a flag to check if the data is expired.
    * @param name     the assembly name.
    * @param assembly the target assembly.
    * @param type     the data type.
    */
   private void addDataKey(String name, VSAssembly assembly, int type) {
      if(!(assembly instanceof ChartVSAssembly) || type != DataMap.NORMAL) {
         return;
      }

      VSChartInfo cinfo = ((ChartVSAssembly) assembly).getVSChartInfo();
      dKeyMap.put(name, cinfo, type);
   }

   /**
    * If the chartinfo stored in dKeyMap is not equals to the latest one, which means
    * the data in datamap is not the latest data, then should execute to get the right data.
    * This is added to fix exceptions when change binding many times and quickly which may
    * cause execution order be mess like:
    *   action1 remove data
    *   action2 remove data
    *   action1 get data
    *   action2 get data
    * but normal order is: action1 remove-> get, action2 remove -> get.
    * @param name  the assembly name.
    * @param type  the data type.
    * @return
    */
   private boolean isDataExpired(String name, int type) {
      VSAssembly assembly = getViewsheet().getAssembly(name);

      if(!(assembly instanceof ChartVSAssembly) || dKeyMap.get(name, type) == null) {
         return false;
      }

      VSChartInfo ninfo = ((ChartVSAssembly) assembly).getVSChartInfo();

      return !Tool.equalsContent(ninfo, dKeyMap.get(name, type));
   }

   /**
    * Update the meta data and execute dynamic values in all assemblies in
    * the viewsheet.
    */
   public void updateAssemblies() throws Exception {
      Assembly[] assemblies = vs.getAssemblies();

      for(int i = 0; i < assemblies.length; i++) {
         VSAssembly vsassembly = (VSAssembly) assemblies[i];
         updateAssembly(vsassembly, true);
      }
   }

   /**
    * Update assembly.
    */
   public void updateAssembly(String name) throws Exception {
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         ViewsheetSandbox box = getSandbox(name.substring(0, index));
         name = name.substring(index + 1);
         box.updateAssembly(name);
         return;
      }

      VSAssembly assembly = vs.getAssembly(name);

      if(assembly != null) {
         updateAssembly(assembly);
      }
   }

   /**
    * Execute the data related dynamic values.
    */
   void updateAssembly(VSAssembly assembly) throws Exception {
      updateAssembly(assembly, false);
   }

   /**
    * Execute the data related dynamic values.
    * @param out <tt>true</tt> to include output dynamic values.
    */
   void updateAssembly(VSAssembly assembly, boolean out) throws Exception {
      if(disposed) {
         return;
      }

      ChartVSAssembly chart = assembly instanceof ChartVSAssembly
         ? (ChartVSAssembly) assembly : null;
      boolean dynamicBinding = chart != null && Arrays.stream(chart.getVSChartInfo().getRTFields())
         .filter(f -> f instanceof VSChartAggregateRef)
         .anyMatch(f -> ((VSChartAggregateRef) f).containsDynamic());

      // @by larryl, avoid infinite recursion, the dynamic value expression
      // may reference 'data', which would trigger the getData() and then
      // updateAssembly()
      if(execDValues.get() != Boolean.TRUE) {
         List<DynamicValue> values = assembly.getDynamicValues();
         executeDynamicValues(assembly.getName(), values);

         if(out) {
            values = assembly.getOutputDynamicValues();
            executeDynamicValues(assembly.getName(), values);
         }
      }

      refreshMetaData(assembly);

      // if binding changed by dynamic values, need to set the correct default format (48779).
      if(dynamicBinding) {
         GraphFormatUtil.fixDefaultNumberFormat(chart.getChartDescriptor(), chart.getVSChartInfo());
      }
   }

   /**
    * Remove the corresponding items in VariableTable
    */
   private void removeItemOfVariableTable(String assemblyName) {
      ViewsheetScope scope = getScope();
      VariableScriptable vscriptable = scope.getVariableScriptable();
      VariableTable vtable = (VariableTable) vscriptable.unwrap();

      if(vtable != null && vtable.contains(assemblyName)) {
         vtable.remove(assemblyName);
      }
   }

   /**
    * Shallow clone.
    */
   private ViewsheetSandbox getMVDisabledBox(VSAssembly assembly) {
      if(needMV()) {
         try {
            ViewsheetSandbox cloned = (ViewsheetSandbox) this.clone();
            cloned.mvDisabled = isMVDisabled() ||
               !VSUtil.isVSAssemblyBinding(assembly.getTableName());
            return cloned;
         }
         catch(Throwable ex) {
         }
      }

      return this;
   }

   /**
    * Refresh the column selection from meta data.
    */
   private void refreshMetaData(VSAssembly assembly) {
      if(disposed) {
         return;
      }

      ColumnSelection columns = new ColumnSelection();
      boolean inExec = JavaScriptEngine.getExecScriptable() != null;

      // @by stephenwebster, For Bug #1034
      // It is possible that query.getDefaultColumnSelection will throw a
      // RuntimeException. Though you would not normally handle this error
      // we cannot allow it to prevent an asset from opening.
      if(!inExec) {
         lockRead();
      }

      try {
         if(assembly instanceof DataVSAssembly) {
            // use cloned box, so will not need to lock it
            ViewsheetSandbox cloned = getMVDisabledBox(assembly);
            DataVSAQuery query = (DataVSAQuery) VSAQuery.createVSAQuery(
               cloned, assembly, DataMap.NORMAL);
            query.setShrinkTable(false);
            columns = query.getDefaultColumnSelection();
         }

         assembly.update(columns);
      }
      catch(ExpiredSheetException ex) {
         LOG.info("Viewsheet has expired: " + ex);
      }
      catch(Exception ex) {
         // @by stephenwebster, For Bug #1432
         // Ignore collection of this error while viewsheet is refreshing.
         if(isRefreshing()) {
            return;
         }

         if(ex instanceof ColumnNotFoundException) {
            throw ((ColumnNotFoundException) ex);
         }

         if(!(ex instanceof ConfirmException)) {
            if(ex instanceof BoundTableNotFoundException) {
               LOG.warn("Failed to update assembly when refreshing meta data: {}, {}",
                        assembly.getAssemblyEntry(), ex.getMessage());
            }
            else {
               LOG.warn("Failed to update assembly when refreshing meta data: {}, {}",
                        assembly.getAssemblyEntry(), ex.getMessage(), ex);
            }
         }

         List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

         if(ex instanceof ConfirmDataException && assembly != null) {
            ConfirmDataException cex = (ConfirmDataException) ex;
            cex.setName(assembly.getAbsoluteName());
         }

         if(exs != null) {
            exs.add(ex);
         }
      }
      finally {
         if(!inExec) {
            unlockRead();
         }
      }
   }

   /**
    * Execute the data of an assembly.
    * @param name the name of the specified assembly.
    * @return execution result.
    */
   private Object executeData(String name, int type) throws Exception {
      if(disposed) {
         return null;
      }

      return GroupedThread.runWithRecordContext(
         () -> getLogRecords(name), () -> doExecuteData(name, type));
   }

   private Collection<?> getLogRecords(String name) {
      List<Object> records = new ArrayList<>();
      records.add(LogContext.ASSEMBLY.getRecord(name));

      if(wbox != null && wbox.getWSEntry() != null) {
         records.add(LogContext.WORKSHEET.getRecord(wbox.getWSEntry().getPath()));
      }

      return records;
   }

   private Object doExecuteData(String name, int type) throws Exception {
      // execute combobox column option data
      if(name.contains(FORM_OPTION)) {
         String[] params = name.split("\\^");
         return getColumnOptionData(params[0], Integer.parseInt(params[1]), type);
      }

      VSAQuery query;
      VSAssembly assembly = vs.getAssembly(name);

      if(assembly == null) {
         return null;
      }

      boolean inExec = JavaScriptEngine.getExecScriptable() != null;

      // VSAQuery may modify underlying worksheet (especially chart), treat it as a write
      // to prevent conflicts
      if(!inExec) {
         lockWrite();
      }

      try {
         updateAssembly(assembly);
         query = VSAQuery.createVSAQuery(this, assembly, type);
      }
      finally {
         if(!inExec) {
            unlockWrite();
         }
      }

      if("true".equals(getVariableTable().get("calc_metadata"))) {
         query.setMetadata(true);
      }

      Object result;

      try {
         if(assembly instanceof ListInputVSAssembly ||
            assembly instanceof TableVSAssembly &&
               ((TableVSAssemblyInfo) assembly.getInfo()).isForm())
         {
            getVariableTable().put("_FORM_", "true");
         }

         result = query.getData();

         if(result instanceof TableLens) {
            result = new TextSizeLimitTableLens((TableLens) result, Util.getOrganizationMaxCellSize());
         }
         
         execTS = System.currentTimeMillis();
      }
      finally {
         getVariableTable().remove("_FORM_");
      }

      return result;
   }

   /**
    * Execute the dynamic values.
    * @param name assembly name.
    * @param values dynamic values.
    */
   public void executeDynamicValues(String name, List<DynamicValue> values) throws Exception {
      executeDynamicValues(name, values, null);
   }

   /**
    * Execute the dynamic values.
    * @param name assembly name.
    * @param values dynamic values.
    */
   public void executeDynamicValues(String name, List<DynamicValue> values,
                                    Supplier<Object> supplier) throws Exception
   {
      // @by ankitmathur, Fix bug1430165386906, For a sub-Viewsheet, when the
      // dynamic values are reset, execute them in the correct ViewsheetSandbox
      // to ensure the correct scope is used for running scripts.
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         ViewsheetSandbox childBox = getSandbox(name.substring(0, index));
         name = name.substring(index + 1);
         childBox.executeDynamicValues(name, values, supplier);
         return;
      }

      execDValues.set(Boolean.TRUE);

      try {
         for(DynamicValue dval : values) {
            if(dval != null) {
               executeDynamicValue(dval, name, supplier);
            }
         }
      }
      finally {
         execDValues.set(null);
      }
   }

   /**
    * Execute a dynamic value.
    * @param dval the specified dynamic value.
    * @param name the specified assembly name.
    */
   void executeDynamicValue(DynamicValue dval, String name, Supplier<Object> supplier) throws Exception {
      VSAScriptable scriptable = getScope().getVSAScriptable(name);
      executeDynamicValue(dval, scriptable, name, supplier);
   }

   void executeDynamicValue(DynamicValue dval, VSAScriptable scriptable) throws Exception {
      executeDynamicValue(dval, scriptable, null, null);
   }

   /**
    * Execute a dynamic value.
    * @param dval the specified dynamic value.
    * @param scriptable the specified scriptable.
    */
   void executeDynamicValue(DynamicValue dval, VSAScriptable scriptable, String name,
                            Supplier<Object> supplier) throws Exception
   {
      if(disposed) {
         return;
      }

      Object data = null;
      String dvalue = dval.getDValue();
      // don't execute script if it's a normal column. (55655)
      boolean normalColumn = isNormalColumn(dvalue, scriptable, name) ||
         isSourceName(dvalue, scriptable, name);
      Matcher matcher = null;

      if(!normalColumn && VSUtil.isVariableValue(dvalue)) {
         data = executeVariableValue(dvalue);
      }
      else if(!normalColumn && dvalue != null && !VSUtil.isScriptValue(dvalue) &&
              // optimization, don't run RE match if not necessary.
         VSUtil.containsVariableFormula(dvalue) &&
         (matcher = VSUtil.matchVariableFormula(dvalue)).find())
      {
         Object rColumn = getVariableFormulaCol(dval, executeVariableValue(matcher.group(2)));
         data = matcher.group(1) + rColumn + matcher.group(3);
      }
      else if(!normalColumn && VSUtil.isScriptValue(dvalue)) {
         String script = dvalue.substring(1);

         try {
            data = getScope().execute(script, scriptable, false);
         }
         catch (Exception e) {
            if(supplier != null) {
               dval.setRValue(supplier.get());
            }

            throw e;
         }
      }
      else {
         data = dval.getRValue();
      }

      dval.setRValue(data);
   }

   /**
    * Get runtime value, return the RValue when executed value contains RValue.
    */
   private Object getVariableFormulaCol(DynamicValue dval, Object executed) {
      if(executed != null && executed.getClass().isArray()) {
         if(Array.getLength(executed) == 0) {
            return null;
         }
         else if(Array.getLength(executed) == 1) {
            return Array.get(executed, 0);
         }

         Object rvalue = dval.getRValue();

         if(rvalue == null) {
            return Array.get(executed, 0);
         }

         Object findValue = null;

         for(int i = 0; i < Array.getLength(executed); i++) {
            if(rvalue.equals(Array.get(executed, i))) {
               findValue = Array.get(executed, i);
               break;
            }
         }

         if(findValue != null) {
            return findValue;
         }
         else {
            return Array.get(executed, 0);
         }
      }

      return executed;
   }

   public Object executeVariableValue(String dvalue) {
      Object data = null;
      String param = dvalue.substring(2, dvalue.length() - 1);
      param = param.replaceAll("\\.drillMember$", "");
      Assembly obj = vs.getAssembly(param);

      if (obj instanceof SelectionTreeVSAssembly) {
         SelectionTreeVSAssembly tree = (SelectionTreeVSAssembly) vs.getAssembly(param);

         return SelectionTreeVSAScriptable.getDrillData(tree, "autodrill");
      }
      else {
         if(obj instanceof InputVSAssembly) {
            InputVSAssembly assembly = (InputVSAssembly) obj;

            if(assembly instanceof SingleInputVSAssembly) {
               data = ((SingleInputVSAssembly) assembly).getSelectedObject();
            }
            else if(assembly instanceof CompositeInputVSAssembly) {
               Object[] objs =
                  ((CompositeInputVSAssembly) assembly).getSelectedObjects();

               if(objs == null || objs.length == 0) {
                  data = null;
               }
               else if(objs.length == 1) {
                  data = objs[0];
               }
               else {
                  data = objs;
               }
            }
         }
      }

      return data;
   }

   /**
    * Check if dvalue is a normal column.
    * @param dvalue the target dvalue.
    * @param scriptable the special vsascriptable.
    * @param name the vs assembly name.
    */
   private boolean isNormalColumn(String dvalue, VSAScriptable scriptable, String name) {
      if(dvalue == null || dvalue.isEmpty()) {
         return false;
      }

      String qualifieName = name + ":" + dvalue;

      // optimization
      if(isRuntime()) {
         Boolean rc = normalColumns.get(qualifieName);

         if(rc != null) {
            return rc;
         }
      }

      ColumnSelection cols = getColumnSelection(scriptable, name);
      boolean rc = cols != null && cols.getAttribute(dvalue) != null;
      normalColumns.put(qualifieName, rc);
      return rc;
   }

   private boolean isSourceName(String dvalue, VSAScriptable scriptable, String name) {
      String assemblyName = name;

      if(assemblyName == null && scriptable != null) {
         assemblyName = scriptable.getAssembly();
      }

      if(assemblyName == null) {
         return false;
      }

      Assembly assembly = vs.getAssembly(assemblyName);

      if(!(assembly instanceof BindableVSAssembly)) {
         return false;
      }

      return Tool.equals(((BindableVSAssembly) assembly).getTableName(), dvalue);
   }

   /**
    * @return columnselection of the vsassembly binding source.
    */
   private ColumnSelection getColumnSelection(VSAScriptable scriptable, String name) {
      String assemblyName = name;

      if(assemblyName == null && scriptable != null) {
         assemblyName = scriptable.getAssembly();
      }

      if(assemblyName == null) {
         return null;
      }

      Assembly assembly = vs.getAssembly(assemblyName);

      if(!(assembly instanceof BindableVSAssembly)) {
         return null;
      }

      Worksheet ws = vs.getBaseWorksheet();

      if(ws == null) {
         return null;
      }

      String table = ((BindableVSAssembly) assembly).getTableName();
      Assembly wsassembly = ws.getAssembly(table);

      if(wsassembly instanceof TableAssembly) {
         TableAssembly tassembly = (TableAssembly) wsassembly;
         return tassembly.getColumnSelection();
      }

      return null;
   }

   /**
    * Get the available variables, which could be used using $(aaa) in dynamic
    * value and highlight.
    */
   public VariableTable getInputValues() {
      VariableTable vars = new VariableTable();
      Assembly[] arr = vs.getAssemblies();

      for(int i = 0; i < arr.length; i++) {
         if(!(arr[i] instanceof InputVSAssembly)) {
            continue;
         }

         InputVSAssembly assembly = (InputVSAssembly) arr[i];
         Object data = null;

         if(assembly instanceof SingleInputVSAssembly) {
            data = ((SingleInputVSAssembly) assembly).getSelectedObject();
         }
         else if(assembly instanceof CompositeInputVSAssembly) {
            Object[] objs =
               ((CompositeInputVSAssembly) assembly).getSelectedObjects();

            if(objs == null || objs.length == 0) {
               data = null;
            }
            else if(objs.length == 1) {
               data = objs[0];
            }
            else {
               data = objs;
            }
         }

         vars.put(assembly.getName(), data);
      }

      return vars;
   }

   /**
    * Get all available variables for highlight.
    */
   public VariableTable getAllVariables() {
      VariableTable vars = new VariableTable();
      VariableTable comVars = getVariableTable();
      VariableTable inputVars = getInputValues();

      if(comVars != null) {
         Enumeration<String> e = comVars.keys();

         while(e.hasMoreElements()) {
            String name = e.nextElement();

            try {
               vars.put(name, comVars.get(name));
               vars.setAsIs(name, comVars.isAsIs(name));
            }
            catch(Exception ex) {
               LOG.warn("Failed to copy variable: " + name, ex);
            }
         }
      }

      Enumeration<String> e = inputVars.keys();

      while(e.hasMoreElements()) {
         String name = e.nextElement();

         try {
            vars.put(name, inputVars.get(name));
            vars.setAsIs(name, inputVars.isAsIs(name));
         }
         catch(Exception ex) {
            LOG.warn("Failed to copy input value: " + name, ex);
         }
      }

      return vars;
   }

   /**
    * Remove cached data that is no longer needed by the sheet.
    */
   public void shrink() {
      for(String name : dmap.keys()) {
         if(!vs.containsAssembly(name)) {
            dmap.removeAll(name);
            dKeyMap.removeAll(name);
            tmap.remove(name);
         }
      }

      List<String> list = new ArrayList<>(qmgrs.keySet());

      for(String name : list) {
         if(!vs.containsAssembly(name)) {
            QueryManager qmgr = qmgrs.remove(name);

            if(qmgr != null) {
               qmgr.cancel();
            }
         }
      }

      list = new ArrayList<>(vset);

      for(String name : list) {
         if(!vs.containsAssembly(name)) {
            vset.remove(name);
         }
      }

      list = new ArrayList<>(images.keySet());

      for(String name : list) {
         if(!vs.containsAssembly(name)) {
            images.remove(name);
         }
      }

      list = new ArrayList<>(painters.keySet());

      for(String name : list) {
         if(!vs.containsAssembly(name)) {
            painters.remove(name);
         }
      }

      list = new ArrayList<>(pairs.keySet());

      for(String name : list) {
         if(!vs.containsAssembly(name)) {
            clearGraph(name);
         }
      }

      list = new ArrayList<>(bmap.keySet());

      for(String name : list) {
         Viewsheet embedded = (Viewsheet) vs.getAssembly(name);

         if(embedded == null) {
            ViewsheetSandbox box = bmap.remove(name);
            box.dispose();
         }
         else {
            ViewsheetSandbox box = bmap.get(name);
            box.setViewsheet(embedded, true);
         }
      }

      metarep.shrink();
   }

   /**
    * Get the assembly painter.
    * @param name the specified assembly name.
    * @param width the specified image width.
    * @param height the specified image height.
    * @return the assembly painter if any, <tt>null</tt> otherwise.
    */
   public Painter getPainter(String name, int width, int height)
      throws Exception
   {
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         ViewsheetSandbox box = getSandbox(name.substring(0, index));
         name = name.substring(index + 1);
         return box.getPainter(name, width, height);
      }

      if(disposed) {
         return null;
      }

      synchronized(painters) {
         if(painters.get(name) == null) {
            paintAssembly(name, width, height, 0);
         }

         return painters.get(name);
      }
   }

   /**
    * Get the graph pair for the specified chart.
    * @param name the specified chart name.
    * @return the graph pair if any, <tt>null</tt> otherwise.
    */
   public VGraphPair getVGraphPair(String name) throws Exception {
      return getVGraphPair(name, true, null);
   }

   /**
    * Get the graph pair for the specified chart.
    * @param name the specified chart name.
    * @param ignoreSize don't create new graph pair when only size is differnent with
    *                   the exist graphpair if ignoreSize is true.
    * @return the graph pair if any, <tt>null</tt> otherwise.
    */
   public VGraphPair getVGraphPair(String name, boolean ignoreSize) throws Exception {
      return getVGraphPair(name, true, null, false, 1, false, ignoreSize);
   }

   /**
    * Get the graph pair for the specified chart.
    * @param name the specified chart name.
    * @param init <tt>true</tt> if need to init the pair.
    * @return the graph pair if any, <tt>null</tt> otherwise.
    */
   public VGraphPair getVGraphPair(String name, boolean init, Dimension maxsize) throws Exception {
      return getVGraphPair(name, init, maxsize, false, 1);
   }

   /**
    * Get the graph pair for the specified chart.
    * @param name the specified chart name.
    * @param init <tt>true</tt> if need to init the pair.
    * @param export if is generated to export chart.
    * @return the graph pair if any, <tt>null</tt> otherwise.
    */
   public VGraphPair getVGraphPair(String name, boolean init, Dimension maxsize,
                                   boolean export, double scaleFont)
      throws Exception
   {
      return getVGraphPair(name, init, maxsize, export, scaleFont, false);
   }

   /**
    * Get the graph pair for the specified chart.
    * @param name the specified chart name.
    * @param init <tt>true</tt> if need to init the pair.
    * @param export if is generated to export chart.
    * @param forceExpand whether get graphPair for report chart from vs chart.
    * @return the graph pair if any, <tt>null</tt> otherwise.
    */
   public VGraphPair getVGraphPair(String name, boolean init, Dimension maxsize,
                                   boolean export, double scaleFont, boolean forceExpand)
      throws Exception
   {
      return getVGraphPair(name, init, maxsize, export, scaleFont, forceExpand, false);
   }

   /**
    * Get the graph pair for the specified chart.
    * @param name the specified chart name.
    * @param init <tt>true</tt> if need to init the pair.
    * @param export if is generated to export chart.
    * @param forceExpand whether get graphPair for report chart from vs chart.
    * @return the graph pair if any, <tt>null</tt> otherwise.
    */
   public VGraphPair getVGraphPair(String name, boolean init, Dimension maxsize,
                                   boolean export, double scaleFont, boolean forceExpand, boolean ignoreSize)
      throws Exception
   {
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         ViewsheetSandbox box = getSandbox(name.substring(0, index));
         name = name.substring(index + 1);
         return box.getVGraphPair(name, init, maxsize, export, scaleFont, forceExpand, ignoreSize);
      }

      if(disposed) {
         return null;
      }

      // This is only used to export program.
      if(export) {
         VGraphPair pair = new VGraphPair();
         pair.initGraph(this, name, maxsize, true, scaleFont, forceExpand);

         return pair;
      }

      VGraphPair pair = null;
      VGraphPair opair = null;
      boolean needInit = false;
      ReentrantLock graphLock = graphLocks.computeIfAbsent(name, k -> new ReentrantLock());

      // VGraphPair.init() modifies assembly info and is not thread safe
      try {
         graphLock.lock();
         pair = pairs.get(name);

         // commented out to fix bug1407424376930
         // if(!init) {
         //    return pair;
         // }

         if(pair != null) {
            Dimension nsize = null;
            ChartInfo ninfo = null;

            try {
               ChartVSAssembly chart = (ChartVSAssembly) vs.getAssembly(name);
               nsize = VSUtil.getContentSize(chart, maxsize);
               ninfo = chart.getVSChartInfo();
            }
            // if vs is being reset, pair hsould be recreated
            catch(Exception ex) {
               nsize = null;
            }

            Dimension osize = pair.getContentSize();
            ChartInfo oinfo = pair.getChartInfo();

            if(!ignoreSize && (nsize == null || !nsize.equals(osize)) ||
               oinfo != null && !Tool.equalsContent(oinfo, ninfo))
            {
               opair = pair;
               pairs.remove(name);
               pair = new VGraphPair();

               if(vmode != Viewsheet.SHEET_DESIGN_MODE) {
                  opair.cancel();
               }

               needInit = true;
               pairs.put(name, pair);
            }
         }

         Principal principal = ThreadContext.getContextPrincipal();
         XPrincipal xprincipal = principal == null ? null : (XPrincipal) principal;

         if(pair == null || !pair.isCompleted() && pair.isCancelled() ||
            // init to throw CheckMissingMVEvent to make sure client display progress bar to
            // wait for mv insteadof always loading becauseof an empty graph.
            init && MVManager.getManager().isPending(getAssetEntry(), xprincipal))
         {
            pair = new VGraphPair();
            needInit = true;
            pairs.put(name, pair);
         }
      }
      catch(MessageException | ConfirmException e) {
         throw e;
      }
      // when editing a chart, we should return the previous chart info if
      // the chart fails because of something user changed (e.g. wrong expression
      // dynamic field)
      catch(Exception ex) {
         LOG.error("Failed to generate chart", ex);
         pair = opair;
         opair = null;
      }
      finally {
         // set size before unlocking so the getContentSize() in previous block can get the
         // correct size and avoid initGraph again.
         if(pair != null) {
            pair.initSize(this, name, maxsize, false);
         }

         graphLock.unlock();

         if(pair != null) {
            if(needInit) {
               try {
                  pair.initGraph(this, name, maxsize, false, scaleFont);
               }
               catch(Exception ex) {
                  // ignore exception if cancelled so we can check for cancelled status
                  // instead of causing an error in VSChartAreasController. (51339)
                  if(!pair.isCancelled()) {
                     throw ex;
                  }
               }
            }
            else {
               // wait for pair to finish initialization, otherwise it may not be usable
               // after returned.
               // unlock all locks to prevent deadlock since initGraph() will call getData()
               // which calls lockWrite.
               try {
                  thisLock.unlockAll();
                  pair.waitInit();
               }
               finally {
                  thisLock.restoreLocks();
               }
            }
         }

         if(vmode == Viewsheet.SHEET_DESIGN_MODE && opair != null) {
            opair.cancel();
         }
      }

      if(!pair.isCompleted() && pair.isCancelled()) {
         return getVGraphPair(name, init, maxsize, export, scaleFont);
      }

      return pair;
   }

   /**
    * Get the assembly image.
    * @param name the specified assembly name.
    * @param width the specified image width.
    * @param height the specified image height.
    * @param outer the area outside (top, left, bottom, and right) of the
    * assembly area to draw. This is used for panning image of map.
    * @return the assembly image if any, <tt>null</tt> otherwise.
    */
   public Image getImage(String name, int width, int height, int outer) throws Exception {
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         ViewsheetSandbox box = getSandbox(name.substring(0, index));
         name = name.substring(index + 1);
         return box.getImage(name, width, height, outer);
      }

      if(disposed) {
         return null;
      }

      if(outer > 0) {
         return paintAssembly(name, width, height, outer);
      }

      synchronized(images) {
         Image img = images.get(name);

         if(img == null || img.getWidth(null) != width || img.getHeight(null) != height) {
            paintAssembly(name, width, height, 0);
         }

         return images.get(name);
      }
   }

   /**
    * Paint assembly.
    * @param width the specified image width.
    * @param height the specified image height.
    * @param outer the area outside (top, left, bottom, and right) of the
    * assembly area to draw. This is used for panning image of map.
    * @param name the specified assembly name.
    */
   private Image paintAssembly(String name, int width, int height, int outer) throws Exception {
      VSAssembly assembly = vs.getAssembly(name);
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      Object data = !(assembly instanceof TableDataVSAssembly) ?
         getData(name) : getVSTableLens(name, false);
      int outerw = outer * width / Math.max(width, height);
      int outerh = outer * height / Math.max(width, height);
      // add the outer area on left and right, top and bottom
      Image img = Tool.createImage(width + 2 * outerw, height + 2*outerh, true);
      Painter painter = null;
      VSCompositeFormat fmt = info.getFormat();
      Color bg = fmt == null ? null : fmt.getBackground();
      Color fg = (fmt == null || fmt.getForeground() == null) ? Color.black :
         fmt.getForeground();
      Graphics2D g = (Graphics2D) img.getGraphics();

      if(SreeEnv.getProperty("image.antialias").
         equalsIgnoreCase("true"))
      {
         if(bg != null) {
            RenderingHints hints = new RenderingHints(null);
            hints.put(RenderingHints.KEY_ANTIALIASING,
                      RenderingHints.VALUE_ANTIALIAS_ON);
            hints.put(RenderingHints.KEY_TEXT_ANTIALIASING,
                      RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            hints.put(RenderingHints.KEY_DITHERING,
                      RenderingHints.VALUE_DITHER_ENABLE);
            g.setRenderingHints(hints);
         }
      }

      if(bg != null) {
         g.setColor(bg);
         g.fillRect(0, 0, width + 2 * outerw, height + 2 * outerh);
      }

      g.setColor(fg);

      if(fmt != null && fmt.getFont() != null) {
         g.setFont(fmt.getFont());
      }

      g.dispose();

      if(outer == 0) {
         painters.put(name, painter);
         images.put(name, img);
      }

      return img;
   }

   /**
    * Set whether to ignore filtering.
    */
   public void setIgnoreFiltering(boolean ifiltering) {
      if(this.ifiltering == ifiltering) {
         return;
      }

      this.ifiltering = ifiltering;

      for(ViewsheetSandbox box : bmap.values()) {
         box.setIgnoreFiltering(ifiltering);
      }

      if(wbox != null) {
         wbox.setIgnoreFiltering(ifiltering);
      }
   }

   /**
    * Check if is ignoring filtering.
    */
   public boolean isIgnoreFiltering() {
      return ifiltering;
   }

   /**
    * Get the brushing chart.
    * @param vname the name of the specified viewsheet asssembly to apply brush
    * selection.
    */
   public ChartVSAssembly getBrushingChart(String vname) {
      Assembly assembly = vs.getAssembly(vname);

      if(!(assembly instanceof DataVSAssembly) &&
         !(assembly instanceof OutputVSAssembly))
      {
         return null;
      }

      BindableVSAssembly vassembly = (BindableVSAssembly) assembly;
      String tname = vassembly.getTableName();

      if(tname == null || tname.length() == 0) {
         return null;
      }

      Assembly[] arr = vs.getAssemblies();

      for(int i = 0; i < arr.length; i++) {
         if(!(arr[i] instanceof ChartVSAssembly)) {
            continue;
         }

         ChartVSAssembly chart = (ChartVSAssembly) arr[i];

         if(WizardRecommenderUtil.isTempDataAssembly(chart.getName()) ||
            !tname.equals(chart.getTableName()))
         {
            continue;
         }

         VSSelection selection = chart.getBrushSelection();

         // no selection?
         if(selection == null || selection.isEmpty()) {
            continue;
         }

         // the source chart contains brush selection?
         if(chart.getName().equals(vname)) {
            return null;
         }

         return chart;
      }

      return null;
   }

   /**
    * Check if the user agent is Safari on iOS.
    */
   public void setSafariOniOS(boolean isSafariOniOS) {
      this.isSafariOniOS = isSafariOniOS;
   }

   /**
    * Get table data.
    * @param name the specified table data assembly name.
    */
   public TableLens getTableData(String name) throws Exception {
      return getTableData(name, DataMap.NORMAL);
   }

   /**
    * Get table data.
    * @param name the specified table data assembly name.
    * @param type data type.
    */
   private TableLens getTableData(String name, int type) throws Exception {
      boolean vsAssemblyBinding = VSUtil.isVSAssemblyBinding(name);

      if(vsAssemblyBinding) {
         name = VSUtil.getVSAssemblyBinding(name);
      }

      TableLens lens = getFormTableLens(name);

      if(lens == null) {
         Object data = getData(name, true, type);

         if(data instanceof TableLens) {
            lens = (TableLens) data;
         }
         else if(data instanceof VSDataSet) {
            lens = ((VSDataSet) data).getTable();
         }
      }

      if(vsAssemblyBinding && lens != null) {
         lens = new DataWrapperTableLens(lens);
      }

      return lens;
   }

   /**
    * Get FormTableLens.
    * @param name the specified table data assembly name.
    */
   public FormTableLens getFormTableLens(String name) throws Exception {
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         ViewsheetSandbox box = getSandbox(name.substring(0, index));
         name = name.substring(index + 1);

         if(box == null) {
            return null;
         }

         return box.getFormTableLens(name);
      }

      if(disposed) {
         return null;
      }

      VSAssembly assembly = vs.getAssembly(name);

      if(!isRuntime() || !(assembly instanceof TableVSAssembly)) {
         return null;
      }

      TableVSAssembly tv = (TableVSAssembly) assembly;
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) tv.getInfo();

      if(!info.isForm()) {
         return null;
      }

      FormTableLens flens = fmap.get(name);

      if(flens == null) {
         ColumnSelection cols = info.getColumnSelection();
         ColumnSelection ncols = (ColumnSelection) cols.clone();
         cols.clear();

         for(int i = 0; i < ncols.getAttributeCount(); i++) {
            DataRef ref = ncols.getAttribute(i);

            if(ref != null) {
               cols.addAttribute(i, FormRef.toFormRef(ref));
            }
         }

         flens = new FormTableLens((TableLens) getData(name));
         flens.setEdit(info.isEdit());
         flens.setColumnSelection(cols);
         fmap.put(name, flens);
      }

      return flens;
   }

   /**
    * Get sorted VSTablelens.
    * @param name the specified table data assembly name.
    * @param ref the sort ref.
    */
   public void sortFormTableLens(String name, SortRef ref) throws Exception {
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         ViewsheetSandbox box = getSandbox(name.substring(0, index));
         name = name.substring(index + 1);
         box.sortFormTableLens(name, ref);
         return;
      }

      VSAssembly assembly = vs.getAssembly(name);

      if(!isRuntime() || !(assembly instanceof TableVSAssembly)) {
         return;
      }

      TableVSAssembly tv = (TableVSAssembly) assembly;
      ColumnSelection cols = tv.getVisibleColumns();
      int col = cols.indexOfAttribute(ref);
      int order = ref.getOrder();
      FormTableLens flens = getFormTableLens(name);

      flens.sort(col, order);
   }

   /**
    * Execute data of Combobox ColumnOption.
    * @param name the name of table assembly.
    * @param index the index of the column selection in table info.
    */
   private ListData getColumnOptionData(String name, int index, int type)
      throws Exception
   {
      TableVSAssembly assembly = (TableVSAssembly) vs.getAssembly(name);
      ColumnSelection cols = ((TableVSAssemblyInfo)
         assembly.getVSAssemblyInfo()).getColumnSelection();
      ComboBoxColumnOption option =
         (ComboBoxColumnOption) ((FormRef) cols.getAttribute(index)).getOption();
      TableVSAQuery query = new TableVSAQuery(this, name,
         (type & DataMap.DETAIL) != 0, option);

      return query.getFormListData();
   }

   /**
    * Add Event to ViewsheetScope
    * @param event event to be attached to Viewsheet Scope
    */
   public void attachScriptEvent(ScriptEvent event) {
      getScope().addVariable("event" , event);
   }

   /**
    * Remove Event from ViewsheetScope
    */
   public void detachScriptEvent() {
      getScope().removeVariable("event");
   }

   /**
    * Table metadata repository.
    */
   private class TableMetaDataRepository implements ActionListener {
      /**
       * Create a table metadata repository.
       */
      public TableMetaDataRepository() {
         super();

         this.metamap = new ConcurrentHashMap<>();
      }

      /**
       * Reset the table metadata.
       * @param table the specified table.
       */
      public void resetTableMetaData(String table) {
         TableMetaDataKey key = getTableMetaDataKey(table);

         if(key != null) {
            TableMetaData metadata = metamap.remove(key);

            if(metadata != null) {
               metadata.dispose();
            }
         }
      }

      /**
       * Get the table metadata.
       * @param sname the name of the specified selection list/tree assembly.
       * @return the table metadata.
       */
      public TableMetaData getTableMetaData(String sname) throws Exception {
         Assembly assembly = vs.getAssembly(sname);

         if(!(assembly instanceof SelectionVSAssembly)) {
            return null;
         }

         SelectionVSAssembly sassembly = (SelectionVSAssembly) assembly;
         String tname = sassembly.getTableName();
         return getTableMetaDataFromTable(tname);
      }

      /**
       * @param tname the name of the specified table assembly.
       *
       * @return the table metadata.
       */
      public TableMetaData getTableMetaDataFromTable(String tname) throws Exception {
         if(tname == null) {
            return null;
         }

         lockWrite();

         try {
            TableMetaDataKey okey = getTableMetaDataKey(tname);
            TableMetaDataKey key = TableMetaDataKey.createKey(tname, vs, vmode);

            if(okey != null && !okey.equals(key)) {
               TableMetaData metadata = metamap.remove(okey);

               if(metadata != null) {
                  metadata.dispose();
               }
            }

            if(key == null) {
               return null;
            }

            TableMetaData metadata = metamap.get(key);

            if(metadata == null) {
               metadata = genTableMetaData(key);
               metamap.put(key, metadata);
            }

            return metadata;
         }
         finally {
            unlockWrite();
         }
      }

      /**
       * Clear the metadata repository.
       */
      public void clear() {
         List<TableMetaDataKey> keys = new ArrayList<>(metamap.keySet());

         for(TableMetaDataKey key : keys) {
            TableMetaData metadata = metamap.remove(key);

            if(metadata != null) {
               metadata.dispose();
            }
         }

         metamap.clear();
      }

      /**
       * Dispose this table metadata repository.
       */
      public void dispose() {
         Worksheet ws = getWorksheet();

         if(ws == null) {
            return;
         }

         clear();
      }

      /**
       * Shrink the table metadata.
       */
      public void shrink() {
         Worksheet ws = getWorksheet();

         if(ws == null) {
            return;
         }

         lockRead();

         try {
            List<TableMetaDataKey> keys = new ArrayList<>(metamap.keySet());

            for(TableMetaDataKey key : keys) {
               TableAssembly tassembly = (TableAssembly) ws.getAssembly(key.getTable());

               if(tassembly == null) {
                  TableMetaData metadata = metamap.remove(key);

                  if(metadata != null) {
                     metadata.dispose();
                  }
               }

               TableMetaDataKey key2 = TableMetaDataKey.createKey(
                  key.getTable(), getViewsheet(), vmode);

               if(!Tool.equals(key, key2)) {
                  TableMetaData metadata = metamap.remove(key);

                  if(metadata != null) {
                     metadata.dispose();
                  }
               }
            }
         }
         finally {
            unlockRead();
         }
      }

      /**
       * Triggered when worksheet action performed.
       * @param evt the specified action event.
       */
      @Override
      public void actionPerformed(ActionEvent evt) {
         final DefaultDebouncer<String> debouncer = ConfigurationContext.getContext()
            .computeIfAbsent(DEBOUNCER_KEY, k -> new DefaultDebouncer<>());

         // this is triggered from ws action when ws is modified, at which point the
         // ws is locked. the shrink() method will call lockRead, which assumes it's
         // called before ws is locked. we run it in a separate thread to avoid deadlock
         debouncer.debounce("shrink" + System.identityHashCode(ViewsheetSandbox.this), 1,
                            TimeUnit.SECONDS, () -> ThreadPool.addOnDemand(this::shrink));
      }

      /**
       * Get the table meta data key.
       * @param table the specified table.
       */
      private TableMetaDataKey getTableMetaDataKey(String table) {
         for(TableMetaDataKey key : metamap.keySet()) {
            if(key.getTable().equals(table)) {
               return key;
            }
         }

         return null;
      }

      /**
       * Generate table metadata.
       * @param key the specified table metadata key.
       * @return the generated table metadata.
       */
      private TableMetaData genTableMetaData(TableMetaDataKey key) throws Exception {
         String tname = key.getTable();
         // default to use the top level sandbox
         this.wbox = getAssetQuerySandbox();
         wbox.setQueryManager(getQueryManager());
         MVManager mgr = MVManager.getManager();
         XPrincipal principal = (XPrincipal) user;
         RuntimeMV rmv = mgr.findRuntimeMV(entry, vs, "", tname, principal,
                                           ViewsheetSandbox.this, isRuntime());

         boolean mv = isMVEnabled() && rmv != null &&
            "true".equals(SreeEnv.getProperty("mv.table.metadata", "true"));

         if(mv) {
            MVTableMetaData metadata = new MVTableMetaData(tname, rmv, ViewsheetSandbox.this);

            if("true".equals(SreeEnv.getProperty("mv.debug"))) {
               LOG.debug("use mv table metadata for: {}", key);
            }

            return metadata;
         }

         return genRealtimeTableMetaData(key, rmv);
      }

      /**
       * Create a meta data with a fully loaded data set (without running MV for
       * every query).
       */
      private TableMetaData genRealtimeTableMetaData(TableMetaDataKey key, RuntimeMV rmv)
            throws Exception
      {
         String tname = key.getTable();
         final String[] columns = key.getHeaders();
         final List<AggregateRef> aggrs = key.getAggregates().stream()
            .filter((aggRef) -> (aggRef.getRefType() & DataRef.AGG_CALC) != DataRef.AGG_CALC).collect(Collectors.toList());
         String stname = VSAssembly.SELECTION + tname;
         int mode = isRuntime() ? AssetQuerySandbox.RUNTIME_MODE : AssetQuerySandbox.LIVE_MODE;
         MVManager mgr = MVManager.getManager();

         if("true".equals(SreeEnv.getProperty("mv.debug"))) {
            LOG.debug("Using realtime metadata for: {}", key);
         }

         // do not try mv
         Viewsheet vs = getViewsheet();
         Worksheet ws = vs.getBaseWorksheet();

         if(vs.isDirectSource()) {
            ws = new WorksheetWrapper(ws);
            VSUtil.shrinkTable(vs, ws);
         }

         TableAssembly table = (TableAssembly) ws.getAssembly(stname);
         table = (TableAssembly) table.clone();
         MVDef smv = null;
         boolean hasWSMV = false;

         if(isMVEnabled()) {
            smv = mgr.getSubMV(entry, tname, (XPrincipal) user);

            // @by larryl, not sure why we don't just use the regular MV
            // from findRuntimeMV. shouldn't be necessary
            if(smv != null) {
               RuntimeMV rmv2 = new RuntimeMV(entry, vs, "meta_" + tname, tname,
                                              null, false, smv.getLastUpdateTime());
               table.setRuntimeMV(rmv2);
            }
            // use the regular MV to run the query
            else if(rmv != null) {
               table.setRuntimeMV(rmv);
            }
            else {
               hasWSMV = WSMVTransformer.containsWSRuntimeMV(table);
            }
         }

         table = prepareTable(table, columns, aggrs);
         boolean cubeTable = table instanceof CubeTableAssembly;

         if(cubeTable) {
            table.setProperty("noEmpty", "false");
         }

         try {
            if(smv != null || hasWSMV) {
               ws = table.getWorksheet();
               ws = (Worksheet) ws.clone();
               // we must add this table to its worksheet, otherwise when
               // transforming this table, we are transforming the table with
               // the same name in worksheet, not this table
               ws.addAssembly(table);
            }

            String name = key.getName();
            wbox.setTimeLimited(table.getName(), isTimeLimited(name));
            table.setProperty("assetName", getSheetName());

            if(vs.getViewsheetInfo().isMetadata() && mode == AssetQuerySandbox.LIVE_MODE) {
               mode = AssetQuerySandbox.DESIGN_MODE;
               table.setProperty("metadata", "true");
            }

            TableLens data = AssetDataCache.getData(rid, table, wbox, null, mode,
                                                    true, getTouchTimestamp(), queryMgr);
            final TableMetaData metadata;

            if(cubeTable) {
               metadata = new RealtimeCubeMetaData(tname);
            }
            else {
               metadata = new RealtimeTableMetaData(tname);
            }

            metadata.process(data, columns, aggrs);
            return metadata;
         }
         catch(ConfirmException ex) {
            throw ex;
         }
      }

      /**
       * Change the table so only the columns are visible to avoid loading
       * unnecessary columns.
       */
      private TableAssembly prepareTable(TableAssembly table, String[] columns,
                                         List<AggregateRef> aggrs)
      {
         // only optimize the plain table except crosstab, rotated table, etc.
         if(!table.isPlain()) {
            table.setDistinct(true);
            return table;
         }

         Set<String> colset = new HashSet<>(Arrays.asList(columns));
         ColumnSelection csel = table.getColumnSelection().clone();
         AggregateInfo ainfo = new AggregateInfo();

         if(aggrs.size() == 0) {
            table.setDistinct(true);
         }
         else {
            for(String col : columns) {
               DataRef ref = csel.getAttribute(col, true);

               if(ref != null) {
                  ainfo.addGroup(new GroupRef(ref));
               }
            }
         }

         boolean isNotCube = !table.getName().contains(Assembly.CUBE_VS);

         for(int i = 0; i < csel.getAttributeCount(); i++) {
            ColumnRef column = (ColumnRef) csel.getAttribute(i);

            if(!column.isVisible()) {
               continue;
            }

            String alias = column.getAlias();

            if(alias == null) {
               alias = isNotCube || (column.getRefType() & DataRef.CUBE) == 0 ?
                  column.getAttribute() : column.getName();
            }

            column.setVisible(colset.contains(alias));
         }

         for(AggregateRef aggr : aggrs) {
            DataRef ref = csel.getAttribute(aggr.getAttribute(), true);

            if(ref != null) {
               ExpressionRef exprRef = null;

               // use calc field as selection list measure (47844).
               if(ref instanceof CalculateRef) {
                  exprRef = (ExpressionRef) ((CalculateRef) ref).getDataRef().clone();
                  exprRef.setName(aggr.toString());
               }
               else {
                  exprRef = new AliasDataRef(aggr.toString(), ref);
                  exprRef.setRefType(ref.getRefType());
               }

               ColumnRef column = new ColumnRef(exprRef);

               if(!isNotCube && ref instanceof CalculateRef) {
                  column = new ColumnRef((CalculateRef)ref.clone());
               }

               csel.addAttribute(column);
               ainfo.addAggregate(new AggregateRef(column, aggr.getFormula()));
               table.setProperty("richlist", "true");
            }
         }

         table.setAggregateInfo(ainfo);
         table.setAggregate(!ainfo.isEmpty());
         table.setColumnSelection(csel);
         VSAQuery.normalizeTable(table);

         return table;
      }

      /**
       * Check if a table is the ancestor of another table.
       */
      private boolean isAncestor(Worksheet ws, String ancestor, String child, boolean root) {
         if(!root && ancestor.equals(child)) {
            return true;
         }

         TableAssembly ptable = ws == null ? null :
            (TableAssembly) ws.getAssembly(ancestor);

         if(!(ptable instanceof ComposedTableAssembly)) {
            return false;
         }

         ComposedTableAssembly table = (ComposedTableAssembly) ptable;
         TableAssembly[] arr = table.getTableAssemblies(false);

         for(int i = 0; arr != null && i < arr.length; i++) {
            if(isAncestor(ws, arr[i].getName(), child, false)) {
               return true;
            }
         }

         return false;
      }

      private final Map<TableMetaDataKey, TableMetaData> metamap;
      private AssetQuerySandbox wbox;
   }

   /**
    * Comparator to force changed selection list to be processed first.
    */
   private static final class SelectionComparator extends DependencyComparator {
      public SelectionComparator(Viewsheet viewsheet, Set<String> changed) {
         super(viewsheet, true);
         this.changed = changed == null ? new HashSet<>() : changed;
      }

      @Override
      public int compare(Object obja, Object objb) {
         if(obja == objb) {
            return 0;
         }

         if(!(obja instanceof SelectionVSAssembly) ||
            !(objb instanceof SelectionVSAssembly))
          {
            return super.compare(obja, objb);
         }

         SelectionVSAssembly sa = (SelectionVSAssembly) obja;
         SelectionVSAssembly sb = (SelectionVSAssembly) objb;

         final List<String> tableNamesA = sa.getTableNames();
         final List<String> tableNamesB = sb.getTableNames();

         if(tableNamesA.size() != tableNamesB.size()) {
            return -Integer.compare(tableNamesA.size(), tableNamesB.size());
         }

         if(!tableNamesA.equals(tableNamesB)) {
            return super.compare(obja, objb);
         }

         boolean ca = changed.contains(sa.getName());
         boolean cb = changed.contains(sb.getName());

         if(ca && !cb) {
            return -1;
         }
         else if(cb && !ca) {
            return 1;
         }

         return super.compare(obja, objb);
      }

      private final Set<String> changed;
   }

   /**
    * Get the condition/highlight condition execute query sandbox.
    */
   public AssetQuerySandbox getConditionAssetQuerySandbox(Viewsheet vs) {
      ViewsheetSandbox vbox = this;

      if(vs.isEmbedded()) {
         vbox = getSandbox(vs.getAbsoluteName());
      }

      AssetQuerySandbox box = new AssetQuerySandbox(new Worksheet());
      box.setViewsheetSandbox(this);

      try {
         box.getVariableTable().addAll(vbox.getVariableTable());
      }
      catch(Exception ex) {
         LOG.warn("Failed to copy the variable table", ex);
      }

      return box;
   }

   /**
    * Check to see if a refresh event is currently being executing on the
    * enclosed viewsheet.
    * @return  <true>If the viewsheet is running a refresh event</true>
    * <false>otherwise</false>
    */
   public boolean isRefreshing() {
      return refreshing;
   }

   /**
    * Set whether a refresh event is occuring on the enclosed viewsheet.
    * @param refreshing The status of refreshing the viewsheet.
    */
   public void setRefreshing(boolean refreshing) {
      this.refreshing = refreshing;
   }

   /**
    * Check if selection association should be processed.
    */
   public boolean isAssociationEnabled() {
      ViewsheetInfo vinfo = vs.getViewsheetInfo();
      return vinfo.isAssociationEnabled();
   }

   /**
    * Set the export format, pdf, pptx, xlsx, if this is used for exporting.
    */
   public void setExportFormat(String fmt) {
      this.exportFormat = fmt;
      this.getScope().refreshRuntimeScriptable();
   }

   /**
    * Get the export format, only set during exporting of viewsheet.
    */
   public String getExportFormat() {
      return exportFormat;
   }

   public boolean isCancelled(long executionStart) {
      return executionStart < getCancelTimestamp();
   }

   /**
    * Acquire a write (exclusive) lock.
    */
   public void lockWrite() {
      // script (OutputVSAScriptable) may call getData, which would be triggered from
      // the Processor thread. The write lock may already be acquired by the current
      // thread (CoreLifecycleService.refreshViewsheet). we ignore the thread from the data
      // thread to avoid deadlock.
      if(!AssetDataCache.isProcessorThread()) {
         thisLock.lockWrite();
      }
   }

   /**
    * Release a write (exclusive) lock.
    */
   public void unlockWrite() {
      // see above
      if(!AssetDataCache.isProcessorThread()) {
         thisLock.unlockWrite();
      }
   }

   /**
    * Acquire a read (shared) lock.
    */
   public void lockRead() {
      // see above
      if(!AssetDataCache.isProcessorThread()) {
         thisLock.lockRead();
      }
   }

   /**
    * Release a read (shared) lock.
    */
   public void unlockRead() {
      // see above
      if(!AssetDataCache.isProcessorThread()) {
         thisLock.unlockRead();
      }
   }

   /**
    * Set the original viewsheet id.
    * @param oid the specified original viewsheet id.
    */
   public void setOriginalID(String oid) {
      this.oid = oid;
   }

   /**
    * Get the original viewsheet id.
    * @return the original viewsheet id.
    */
   public String getOriginalID() {
      return oid;
   }

   /**
    * Save the original worksheet
    */
   public void saveWsData(AssetRepository engine, Viewsheet vs) throws Exception {
      AssetEntry wentry = vs.getBaseEntry();

      if(wentry == null || vs.getBaseWorksheet() == null) {
         return;
      }

      Worksheet latestWs =  (Worksheet) engine.getSheet(
         wentry, null, false, AssetContent.ALL, false);
      Worksheet ws = vs.getOriginalWorksheet();

      if(latestWs != null && latestWs.getLastModified() > ws.getLastModified()) {
         throw new RuntimeException(Catalog.getCatalog().getString("write.back.timeout.failed"));
      }

      engine.setSheet(wentry, ws, getUser(), true);

      if(oid != null) {
         ViewsheetService viewsheetService = ViewsheetEngine.getViewsheetEngine();
         RuntimeViewsheet orvs = viewsheetService.getViewsheet(oid, getUser());

         if(orvs != null) {
            orvs.setNeedRefresh(true);
         }
      }
   }

   /**
    * Write back the form data to the worksheet embedded table.
    * note: this function only used for the vs form table which binding source is an embeded
    * worksheet table.
    */
   public void writeBackFormData(TableVSAssembly assembly) {
      Viewsheet vs = assembly.getViewsheet();
      Worksheet ws = vs == null ? null : vs.getOriginalWorksheet();

      if(ws == null) {
         return;
      }

      Catalog catalog = Catalog.getCatalog();
      String name = assembly.getAbsoluteName();
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(!info.isForm() || !info.isWriteBack()) {
         return;
      }

      SourceInfo sinfo = info.getSourceInfo();
      String source = sinfo == null ? null : sinfo.getSource();
      Assembly wsAssembly = source == null ? null : ws.getAssembly(source);

      if(wsAssembly == null || !(wsAssembly instanceof EmbeddedTableAssembly)) {
         throw new RuntimeException(
            catalog.getString("write.back.failed.wrongSource", name));
      }

      EmbeddedTableAssembly wstable = (EmbeddedTableAssembly) wsAssembly;
      XEmbeddedTable embedded = wstable.getEmbeddedData();
      FormTableLens form = null;

      try {
         form = getFormTableLens(name);
      }
      catch(Exception ex) {
         LOG.error(catalog.getString("write.back.failed.noFormData", name), ex);
         return;
      }

      FormTableRow[] rows = form.rows();

      if(rows == null || rows.length == 0) {
         throw new RuntimeException(catalog.getString("write.back.failed.noData", name));
      }

      Map<Integer, Integer> cmap = getColumnMapping(assembly, wstable);

      for(int i = embedded.getHeaderRowCount(); i < rows.length; i++) {
         if(rows[i] == null) {
            continue;
         }

         for(int j = 0; j < rows[i].size(); j++) {
            int c = cmap.get(j);

            if(c != -1) {
               embedded.setObject(i, c, rows[i].get(j));
            }
         }

         rows[i].commit();
      }

      int size = embedded.getRowCount();

      if(size > rows.length) {
         for(int r = size - 1; r >= rows.length; r--) {
            embedded.deleteRow(r);
         }
      }
   }

   /**
    * Create default columnselection for embedded worksheet assembly.
    */
   private ColumnSelection getDefaultColumnSelection(EmbeddedTableAssembly table) {
      if(table instanceof SnapshotEmbeddedTableAssembly) {
         return ((SnapshotEmbeddedTableAssembly) table).getDefaultColumnSelection();
      }

      XEmbeddedTable data = table.getEmbeddedData();
      ColumnSelection columns = new ColumnSelection();

      for(int i = 0; i < data.getColCount(); i++) {
         String header = AssetUtil.format(XUtil.getHeader(data, i));
         String type = data.getDataType(i);
         DataRef attr = new AttributeRef(null, header);
         ColumnRef column = new ColumnRef(attr);

         if(type == null) {
            type = Tool.getDataType(data.getColType(i));
         }

         column.setDataType(type);
         columns.addAttribute(column);
      }

      return columns;
   }

   /**
    * Return column map index for form table.
    * @param vstable  the vs embedded form table.
    * @param wstable  the worksheet embedded table.
    */
   private Map<Integer, Integer> getColumnMapping(TableVSAssembly vstable,
                                                  EmbeddedTableAssembly wstable)
   {
      Map<Integer, Integer> map = new HashMap<>();
      ColumnSelection cols = vstable.getColumnSelection();
      ColumnSelection cols1 = getDefaultColumnSelection(wstable);

      for(int i  = 0; i < cols.getAttributeCount(); i++) {
         int idx = cols1.indexOfAttribute(cols.getAttribute(i));

         if(idx == -1) {
            throw new RuntimeException(Catalog.getCatalog().getString(
               "write.back.failed.notFindColumn", cols.getAttribute(i), wstable.getName()));
         }

         map.put(i, idx);
      }

      return map;
   }

   /**
    * Write back data to ws embedded table directly for form elements.
    * @param engine  the asset repository.
    * @param wsTableName  the write back worksheet table name.
    * @param column       the write back ws table column name.
    * @param row          the write back ws table row.
    * @param value        the write back cell data.
    */
   public void writeBackFormDataDirectly(AssetRepository engine, String wsTableName,
                                         String column, int row, Object value)
      throws Exception
   {
      if(wsTableName == null || column == null) {
         return;
      }

      Worksheet ws = vs.getOriginalWorksheet();

      if(ws == null) {
         return;
      }

      Catalog catalog = Catalog.getCatalog();
      Assembly wsAssembly = ws.getAssembly(wsTableName);

      if(!(wsAssembly instanceof EmbeddedTableAssembly)) {
         throw new RuntimeException(
            catalog.getString("write.back.failed.wrongSource", wsTableName));
      }

      EmbeddedTableAssembly wstable = (EmbeddedTableAssembly) wsAssembly;
      ColumnSelection cols = wstable.getColumnSelection();
      DataRef ref = cols.getAttribute(column);
      int col = cols.indexOfAttribute(ref);

      if(col == -1) {
         throw new RuntimeException(
            catalog.getString("write.back.failed.notFindColumn", column, wsTableName));
      }

      XEmbeddedTable embedded = wstable.getEmbeddedData();
      embedded.setObject(row, col, value);
      saveWsData(engine, vs);
   }

   public void setLimitMessage(String assembly, String message) {
      if(limitMessages == null) {
         limitMessages = new ConcurrentHashMap<>();
      }

      if(message == null) {
         limitMessages.remove(assembly);
      }
      else {
         limitMessages.put(assembly, message);
      }
   }

   public String getLimitMessage(String assembly) {
      return limitMessages == null ? null : limitMessages.get(assembly);
   }

   /**
    * Get the current opened bookmark info.
    */
   public VSBookmarkInfo getOpenedBookmark() {
      return openedBookmark;
   }

   /**
    * Set the current opened bookmark info.
    * @param bookmark the current opened bookmark info.
    */
   public void setOpenedBookmark(VSBookmarkInfo bookmark) {
      openedBookmark = bookmark;
   }

   /**
    * Reset the table metadata.
    * @param table the specified table.
    */
   public void resetTableMetaData(String table) {
      if(metarep != null) {
         metarep.resetTableMetaData(table);
      }
   }

   /**
    * Check if this is a viewsheet used in binding pane.
    */
   public boolean isBinding() {
      return binding;
   }

   public void setBinding(boolean binding) {
      this.binding = binding;
   }

   public Map<Integer, Set<String>> getDelayedVisibilityAssemblies() {
      return Collections.unmodifiableMap(delayedVisibilityAssemblies);
   }

   public void addDelayedVisibilityAssemblies(int delay, Set<String> assemblies) {
      delayedVisibilityAssemblies.computeIfAbsent(delay, k -> new HashSet<>()).addAll(assemblies);
   }

   public void clearDelayedVisibilityAssemblies() {
      delayedVisibilityAssemblies.clear();
   }

   private static final String NULL = "__null__";
   private static final ThreadLocal<Boolean> schanged = new ThreadLocal<>();
   private static final ThreadLocal<Boolean> execDValues = new ThreadLocal<>();
   private static final ThreadLocal<Set<String>> execAssemblies =
           ThreadLocal.withInitial(HashSet::new);
   private static final Logger LOG = LoggerFactory.getLogger(ViewsheetSandbox.class);
   private static final String FORM_OPTION = "__FORM_OPTION__";
   private static final String DEBOUNCER_KEY = "ViewsheetSandbox.debouncer";
   private static final int ALL = 0;

   public AtomicBoolean needRefresh = new AtomicBoolean();
   // run view during export it.
   public static final ThreadLocal<Boolean> exportRefresh =
      ThreadLocal.withInitial(() -> Boolean.FALSE);
   private boolean refreshing = false;
   private final AssetEntry entry; // asset entry
   private Viewsheet vs; // current viewsheet
   private final TableMetaDataRepository metarep; // table metadata repository
   private AssetQuerySandbox wbox; // worksheet sandbox
   private final ViewsheetSandbox root; // root viewsheet sandbox
   private ViewsheetScope scope; // script scope
   private ViewsheetScope initScope; // script scope after onInit
   private final DataMap dmap; // data map
   private final DataMap dKeyMap; // data map
   private final VariableTable myvars = new VariableTable();
   private final Map<String, FormTableLens> fmap; // form table map
   private final Set<String> scriptChangedFormSet; // form table updated by script.
   private final Map<String, Long> tmap; // clear timestamp map
   private final Set<String> vset; // view set
   private final Map<String, ViewsheetSandbox> bmap; // viewsheet sandbox map
   private final Map<String, Image> images; // image map
   private final Map<String, Painter> painters; // painter map
   private final Map<String, VGraphPair> pairs; // graph pairs map
   private Principal user; // current user
   private final int vmode; // viewsheet mode
   private final QueryManager queryMgr = new QueryManager(); // general query manager
   private boolean disposed = false;
   private boolean ifiltering = false;
   private final Object mvLock = new Object(); // mvDisabled lock
   private boolean mvDisabled = false;
   private final boolean outputNullToZero = "true".equals(SreeEnv.getProperty("output.null.to.zero"));
   private final Set<String> nolimit; // tables to ignore time limit
   private final Map<String, QueryManager> qmgrs; // specific query manager for each assembly
   private long selectionTS; // selection timestamp
   private long touchTS = -1; // touch timestamp of data changes
   private long execTS = -1; // last execution time
   private ScheduleInfo scheduleInfo = new ScheduleInfo(true, null);
   private String rid = null; // box id
   private final Object scopeLock = new Object(); // script scope
   private String lastOnInit = "";
   private boolean isSafariOniOS = false;
   private long cancelTs = -1;
   private String exportFormat = null;
   private String oid; // original runtime viewsheet id
   private final Map<String, ReentrantLock> graphLocks = new ConcurrentHashMap<>();
   private final UpgradableReadWriteLock thisLock = new UpgradableReadWriteLock();
   private Object pviewsheet = new PViewsheetScriptable();
   private Map<String, String> limitMessages; //record the asselby limit message.
   private VSBookmarkInfo openedBookmark; // the current opened bookmark
   private boolean onLoadExeced = false;
   private Map<String, Boolean> normalColumns = new ConcurrentHashMap<>();
   private boolean binding; // true if in binding pane
   private final Map<Integer, Set<String>> delayedVisibilityAssemblies = new ConcurrentHashMap<>();
}
