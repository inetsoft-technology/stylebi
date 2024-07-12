/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.mv;

import inetsoft.analytic.composition.event.CheckMVEvent;
import inetsoft.mv.fs.internal.ClusterUtil;
import inetsoft.mv.trans.UserInfo;
import inetsoft.mv.util.MVRule;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.*;
import inetsoft.util.log.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * MVManager, manages mvs.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
@SingletonManager.Singleton
public final class MVManager {
   /**
    * Change event, per-transaction event, which should be fired after the
    * change process is over. It's useful for listeners like to be notified
    * but not care how when mv manager changes.
    */
   public static final String MV_CHANGE_EVENT = "mv_change";

   /**
    * Get the mv manager.
    */
   public static MVManager getManager() {
      return SingletonManager.getInstance(MVManager.class);
   }

   /**
    * Create an instance of MVManager.
    */
   public MVManager() {
   }

   /**
    * Get the MVDef by providing mv name.
    */
   public MVDef get(String name) {
      if(name == null) {
         return null;
      }

      if(!mvs.containsKey(name)) {
         return null;
      }

      return mvs.get(name);
   }

   /**
    * Check if MV is required and if throw an exception if it is.
    */
   public RuntimeMV findRuntimeMV(final AssetEntry entry, final Viewsheet vs,
                                  String vassembly, final String tname,
                                  final XPrincipal user,
                                  final ViewsheetSandbox vbox, boolean runtime)
   {
      return findRuntimeMV(entry, vs, vassembly, tname, user, vbox, runtime, false);
   }

   /**
    * Check if MV is required and if throw an exception if it is.
    * Create MV if MV is missing and on-demand is true.
    */
   public RuntimeMV findRuntimeMV(
      final AssetEntry entry, final Viewsheet vs, String vassembly,
      final String tname, final XPrincipal user, final ViewsheetSandbox vbox,
      boolean runtime, boolean findOnly)
   {
      if(entry == null) {
         return null;
      }

      if(entry.isWorksheet()) {
         MVDef mv = findMV(entry, tname, user, null);

         if(mv != null) {
            return new RuntimeMV(entry, null, null, tname, mv.getName(),
                                 false, mv.getLastUpdateTime());
         }
         else {
            return null;
         }
      }

      MVDef mv = findMV(entry, tname, user, null);
      RuntimeMV rmv = null;

      if(mv != null && vs != null && !isCalcFieldsValid(mv, vs)) {
         remove(mv, entry.getPath());
         mv = null;
      }

      MVDef smv = mv != null ? null : getSubMV(entry, tname, user);

      if(mv != null && !mv.isSuccess()) {
         mv = null;
      }

      if(smv != null && !smv.isSuccess()) {
         smv = null;
      }

      if(mv != null) {
         rmv = new RuntimeMV(entry, vs, vassembly, tname, mv.getName(), false,
            mv.getLastUpdateTime());
      }
      // contains mv? mark this table as mv table
      else if(smv != null) {
         // Bug #62332, return a physical mv for association mv
         rmv = new RuntimeMV(entry, vs, vassembly, tname,
                             smv.isAssociationMV() ? smv.getName() : null, false,
                             smv.getLastUpdateTime());
      }

      // fix bug1366967696412, do not create mv for new viewsheet
      if(entry.getScope() == AssetRepository.TEMPORARY_SCOPE) {
         return rmv;
      }

      if("true".equals(entry.getProperty("mv_ignored")) || findOnly) {
         return rmv;
      }

      boolean failed = "true".equals(entry.getProperty("mv_creation_failed"));

      // if this is from a recreateMVOnDemand, and the mv will not be
      // created, we need to trigger a refresh so the data could be
      // fetched from a real-time query
      if(rmv == null && tname.equals("__anyt__") && failed && vs != null) {
         processMissingMV(vs, null, runtime);
         // if no exception is thrown, refresh with realtime query data
         entry.setProperty("mv_ignored", "true");
         throwCheckMV(entry);
      }

      final String key = entry + ":" + user;
      long mvTS = rmv != null ? rmv.getMVLastUpdateTime() : 0;
      final boolean stale = rmv != null && runtime && MVRule.isStale(mvTS) &&
         !pending.containsKey(key);
      final boolean columnMissing = "true".equals(entry.getProperty("mv_column_missing"));
      final boolean mvMissing = rmv == null;
      boolean needsRebuild = columnMissing || stale;

      if((mvMissing || needsRebuild) && vs != null) {
         entry.setProperty("mv_column_missing", "false");

         boolean ondemand = (vs.getViewsheetInfo().isMVOnDemand() &&
            "true".equals(SreeEnv.getProperty("mv.ondemand")) ||
            runtime && mvMissing && MVRule.isAuto() ||
            stale) && !failed;

         if(ondemand) {
            SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();

            // check for permission
            if(provider != null && !provider.checkPermission(user, ResourceType.MATERIALIZATION,
                                                             "*", ResourceAction.ACCESS))
            {
               return null;
            }

            // prevent create too many thread for one key
            // this also not perfect, but can prevent most case
            boolean contains;

            synchronized(pending) {
               contains = pending.containsKey(key);

               if(!contains) {
                  pending.put(key, NULL_CREATOR);
               }
            }

            boolean expired = runtime && MVRule.isExpired(mvTS);

            if(!contains) {
               if(expired) {
                  LOG.info("Recreating MV as it exceeds max age: {} updated at {}",
                           entry, new Date(mvTS));
               }
               else if(stale) {
                  LOG.info("Recreating MV as it exceeds freshness: {} updated at {}",
                           entry, new Date(mvTS));
               }

               (new GroupedThread() {
                  @Override
                  protected void doRun() {
                     try {
                        // don't ues stale cache data if updating stale mv. in this case
                        // the stale mv is used and the update in background should get the
                        // most up-to-date data
                        ThreadContext.setSessionInfo("data.cache.no.stale", stale + "");
                        // make sure runtime refs are populated
                        vbox.setMVDisabled(columnMissing || mvMissing);

                        // the mv eanble status may be changed in other thread,
                        // here simply catch the exception and do nothing
                        try {
                           vbox.updateAssemblies();
                        }
                        catch(Exception ex) {
                           // ignore it
                        }
                        finally {
                           vbox.setMVDisabled(false);
                        }

                        createMV(entry, vs, tname, user, vbox);

                        // if called from recreateMVOnDemand, the mv has
                        // already failed so if it fails again, we shouldn't
                        // try again
                        if(tname.equals("__anyt__")) {
                           entry.setProperty("mv_creation_failed", "true");
                        }
                        // MV not created and there is no error,
                        // maybe embedded table or mv is not possible,
                        // don't try again or it will be an infinite loop
                        else if(findMV(entry, tname, user, null) == null &&
                                !containsSubMV(entry, tname, user))
                        {
                           entry.setProperty("mv_ignored", "true");
                        }
                     }
                     catch(Exception ex) {
                        entry.setProperty("mv_creation_failed", "true");
                        LOG.warn("On-demand creation of " +
                           "materialized view failed: asset=" + entry +
                           ", table=" + tname + ", user=" + user, ex);
                     }
                     finally {
                        if(MVRule.isAuto()) {
                           long now = System.currentTimeMillis();

                           if(now > lastCleanup + 60 * 60000) {
                              lastCleanup = now;
                              MVRule.cleanup();
                           }
                        }

                        pending.remove(key);
                     }
                  }
               }).start();
            }

            // if data is cached, don't wait for MV, proceed and use the cached data.
            // and build the MV in background
            if((mvMissing || expired) && !Drivers.getInstance().isDataCached()) {
               // thread exception if waiting for MV, and the viewer will refresh
               // and wait for MV to complete
               throwCheckMV(entry);
            }
         }
         //bug1397630133880, mv is not ondemand, do not process miss.
//         else {
//            processMissingMV(vs, entry, runtime);
//         }
      }

      return rmv;
   }

   /**
    * Check if the calc fields in the MV contains the same expression
    * as the viewsheet.
    */
   private boolean isCalcFieldsValid(MVDef mv, Viewsheet vs) {
      CalculateRef[] vcalcs = vs.getCalcFields(mv.getBoundTable());

      if(vcalcs == null) {
         return true;
      }

      List<MVColumn> mcalcs = mv.getColumns();

      for(CalculateRef calc1 : vcalcs) {
         if(!calc1.isBaseOnDetail()) {
            continue;
         }

         boolean isValid = false;

         for(MVColumn mcol : mcalcs) {
            ColumnRef col = mcol.getColumn();

            if(col instanceof CalculateRef) {
               CalculateRef calc2 = (CalculateRef) col;
               ExpressionRef expr1 = (ExpressionRef) calc1.getDataRef();
               ExpressionRef expr2 = (ExpressionRef) calc2.getDataRef();

               if(Tool.equals(expr1.getExpression(), expr2.getExpression())) {
                  isValid = true;
                  break;
               }
            }
            // incremental (local) mv would lose the col type. CalculateRef would become
            // ColumnRef after update. we could fix it in MVColumn write/parse. but that
            // poses backward compatibility problem. this maybe an issue if the CalculateRef
            // expression is changed and the old mv would still match. but that doesn't seem
            // much different from regular column where cached data is used. should be ok
            // to require user to recreate mv
            else if(calc1.equals(col)) {
               isValid = true;
               break;
            }
         }

         if(!isValid) {
            return false;
         }
      }

      return true;
   }

   /**
    * Process the case when no MV is created on demand.
    */
   private void processMissingMV(Viewsheet vs, AssetEntry entry, boolean runtime) {
      boolean required = "true".equals(SreeEnv.getProperty("mv.required"));
      boolean metadata = "true".equals(SreeEnv.getProperty("mv.metadata"));

      if(required && runtime) {
         String msg = Catalog.getCatalog().getString("vs.mv.missing");
         throw new MessageException(msg, LogLevel.ERROR);
      }
      else if(metadata && !runtime) {
         vs.getViewsheetInfo().setMetadata(true);
         String msg = Catalog.getCatalog().getString("vs.mv.missing");

         if(entry != null) {
            ConfirmException confirm = new ConfirmException(
               msg, ConfirmException.INFO);
            CheckMVEvent event = new CheckMVEvent(entry);
            event.put("refresh.directly", "true");
            event.put("background", "true");
            confirm.setEvent(event);
            throw confirm;
         }
         else {
            throw new MessageException(msg, LogLevel.ERROR);
         }
      }
   }

   /**
    * Throw the CheckMVEvent.
    */
   private void throwCheckMV(AssetEntry entry) {
      LOG.debug("Check MV event being thrown", new Exception("Stack trace"));
      String msg = Catalog.getCatalog().getString("vs.mv.prepare");
      ConfirmException progress = new ConfirmException(
         msg, ConfirmException.PROGRESS);
      progress.setEvent(new CheckMVEvent(entry));
      throw progress;
   }

   /**
    * Check if MV is being generated.
    */
   public boolean isPending(AssetEntry entry, XPrincipal user) {
      return pending.containsKey(entry + ":" + user);
   }

   /**
    * Cancel the pending MV.
    */
   public synchronized void cancelMV(AssetEntry entry, XPrincipal user) {
      String key = entry + ":" + user;
      MVCreator job = pending.get(key);

      if(job != null) {
         job.cancel();
      }

      pending.remove(key);
   }

   /**
    * Create the on-demand MV for a viewsheet.
    */
   private void createMV(AssetEntry entry, Viewsheet vs, String tname,
                         XPrincipal user, ViewsheetSandbox vbox)
      throws Exception
   {
      Identity id = (user == null || XPrincipal.ANONYMOUS.equals(user.getName()) ||
                     "admin".equals(user.getName()) &&
                     SecurityEngine.getSecurity().getSecurityProvider().isVirtual())
         ? null : new DefaultIdentity(IdentityID.getIdentityIDFromKey(user.getName()), Identity.USER);
      VSMVAnalyzer analyzer = new VSMVAnalyzer(entry.toIdentifier(), vs, id, vbox, false);
      String key = entry + ":" + user;
      boolean full = vs.getViewsheetInfo().isFullData();

      try {
         // always create full data when on-demand
         vs.getViewsheetInfo().setFullData(true);
         MVDef[] defs = analyzer.analyze();

         for(MVDef mv : defs) {
            if(mv.getCycle() == null) {
               mv.setCycle(dcycle);
            }
         }

         List<MVDef> list = Arrays.asList(defs);
         List<UserInfo> hints = new ArrayList<>();
         list = SharedMVUtil.shareAnalyzedMV(list, hints);
         defs = list.toArray(new MVDef[0]);

         // mv is not available for this table? don't recreate all
         if("__anyt__".equals(tname) ||
            findMV0(defs, entry, tname, user, null) != null ||
            containsSubMV0(defs, entry, tname, user))
         {
            for(MVDef mv : defs) {
               // MVDef may be modified but the changes shouldn't persist. This would be
               // consistent with MVAction where MVDef is cloned/serialized.
               MVDef mv0 = (MVDef) mv.clone();
               MVCreator job = MVTool.newMVCreator(mv0);

               // if it's not in pending, it's canceled
               if(!pending.containsKey(key)) {
                  break;
               }

               pending.put(key, job);

               if(job.create()) {
                  synchronized(this) {
                     mv.updateLastUpdateTime(); // set update ts
                     add(mv);
                     SharedMVUtil.shareCreatedMV(mv);
                  }
               }
            }
         }
      }
      finally {
         vs.getViewsheetInfo().setFullData(full);
      }
   }

   /**
    * Get the MVDef by providing viewsheet entry and table name.
    * @param entry the viewsheet entry.
    * @param tname the worksheet data table name.
    * @param ptname the parent table name if this is a sub-table.
    */
   public MVDef findMV(AssetEntry entry, final String tname, XPrincipal user, String ptname) {
      return findMV0(list(false), entry, tname, user, ptname);
   }

   /**
    * List all avaliable mvs which approach the filter.
    */
   private MVDef[] list(MVDef[] defs, MVFilter filter) {
      List<MVDef> list = new ArrayList<>();

      for(MVDef def : defs) {
         if(filter.accept(def)) {
            list.add(def);
         }
      }

      // sorts the mv list so that the VS MVs come first and WS MVs come last
      list.sort((mvDef1, mvDef2) -> {
         if(mvDef1.getVsId() != null && mvDef2.getVsId() == null) {
            return 1;
         }
         else if(mvDef1.getVsId() == null && mvDef2.getVsId() != null) {
            return -1;
         }

         return 0;
      });

      MVDef[] arr = new MVDef[list.size()];
      list.toArray(arr);
      return arr;
   }

   /**
    * Find the specified mv.
    */
   private MVDef findMV0(MVDef[] defs, AssetEntry entry, final String tname,
                         final XPrincipal user, final String ptname)
   {
      if(entry == null) {
         return null;
      }

      final String sheetId = entry.toIdentifier();
      boolean wsMV = entry.isWorksheet();
      MVFilter filter = def -> {
         if(def.isWSMV() != wsMV) {
            return false;
         }

         if(wsMV) {
            if(!def.getMetaData().isRegistered(sheetId, tname)) {
               return false;
            }
         }
         else {
            boolean sub = ptname != null;

            if(def.isSub() != sub) {
               return false;
            }

            if(!def.getMetaData().isRegistered(sheetId)) {
               return false;
            }

            if(!Tool.equals(tname, def.getBoundTable())) {
               return false;
            }

            if(sub && !ptname.equals(def.getMVTable())) {
               return false;
            }
         }

         if(!def.isValidMV()) {
            LOG.warn("Materialized view is not compatible and must be recreated: " +
                        "asset={}, table={}, user={}", sheetId, tname, user);
            return false;
         }

         return true;
      };

      MVDef[] marr = list(defs, filter);

      // 1. try hitting mv for user directly
      Identity id = user == null ? null : new DefaultIdentity(IdentityID.getIdentityIDFromKey(user.getName()), Identity.USER);
      Identity systemAdmin = new DefaultIdentity(IdentityID.getIdentityIDFromKey(XPrincipal.SYSTEM), Identity.USER);

      for(MVDef def : marr) {
         if(def.containsUser(id)) {
            return def;
         }

         if(def.containsUser(systemAdmin)) {
            return def;
         }
      }

      // 2. try hitting mv for group
      String[] groups = user == null ? null : XUtil.getUserGroups(user, true);

      for(int i = 0; groups != null && i < groups.length; i++) {
         id = new DefaultIdentity(groups[i], Identity.GROUP);

         for(MVDef def : marr) {
            if(def.containsUser(id)) {
               return def;
            }
         }
      }

      // 3. try hitting mv for role
      IdentityID[] roles = user == null ? null : XUtil.getUserRoles(user, true);

      for(int i = 0; roles != null && i < roles.length; i++) {
         id = new DefaultIdentity(roles[i], Identity.ROLE);

         for(MVDef def : marr) {
            if(def.containsUser(id)) {
               return def;
            }
         }
      }

      return null;
   }

   /**
    * Check if sub MVDef exists by providing viewsheet entry and table name.
    */
   public boolean containsSubMV(AssetEntry entry, final String tname, XPrincipal user) {
      return containsSubMV0(list(false), entry, tname, user);
   }

   /**
    * Check if contains sub mv for the specified table.
    */
   private boolean containsSubMV0(MVDef[] defs, AssetEntry entry,
                                  final String tname, XPrincipal user)
   {
      return getSubMV0(defs, entry, tname, user) != null;
   }

   public MVDef getSubMV(AssetEntry entry, String tname, XPrincipal user) {
      return getSubMV0(list(false), entry, tname, user);
   }

   private MVDef getSubMV0(MVDef[] defs, AssetEntry entry, final String tname, XPrincipal user) {
      if(entry == null) {
         return null;
      }

      final String vsId = entry.toIdentifier();

      MVFilter filter = def -> {
         // only check sub mv
         if(!def.isSub()) {
            return false;
         }

         // only check vs mv
         if(def.isWSMV()) {
            return false;
         }

         if(!def.getMetaData().isRegistered(vsId)) {
            return false;
         }

         if(!Tool.equals(tname, def.getBoundTable())) {
            return false;
         }

         return true;
      };

      MVDef[] marr = list(defs, filter);

      // 1. try hitting mv for user directly
      Identity id = user == null ? null : new DefaultIdentity(IdentityID.getIdentityIDFromKey(user.getName()), Identity.USER);
      Identity systemAdmin = new DefaultIdentity(IdentityID.getIdentityIDFromKey(XPrincipal.SYSTEM), Identity.USER);

      for(MVDef def : marr) {
         if(def.containsUser(id)) {
            return def;
         }

         if(def.containsUser(systemAdmin)) {
            return def;
         }
      }

      // 2. try hitting mv for group
      String[] groups = user == null ? null : XUtil.getUserGroups(user, true);

      for(int i = 0; groups != null && i < groups.length; i++) {
         id = new DefaultIdentity(groups[i], Identity.GROUP);

         for(MVDef def : marr) {
            if(def.containsUser(id)) {
               return def;
            }
         }
      }

      // 3. try hitting mv for role
      IdentityID[] roles = user == null ? null : XUtil.getUserRoles(user, true);

      for(int i = 0; roles != null && i < roles.length; i++) {
         id = new DefaultIdentity(roles[i], Identity.ROLE);

         for(MVDef def : marr) {
            if(def.containsUser(id)) {
               return def;
            }
         }
      }

      return null;
   }

   /**
    * Add one mv.
    */
   public void add(MVDef mv) {
      add(mv, true);
   }

   /**
    * Add one mv.
    */
   public void add(MVDef mv, boolean event) {
      if(mv == null) {
         return;
      }

      try {
         mvs.put(mv.getName(), mv);

         if(event) {
            fireEvent("mvmanager_" + mv.getName(), MV_CHANGE_EVENT, null, null);
         }
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to save MV definition", e);
      }
   }

   /**
    * Check if contains one mv.
    */
   public boolean containsMV(MVDef mv) {
      return mv != null && mvs.containsKey(mv.getName());
   }

   /**
    * Check if contains mv for the specified mv entry.
    */
   public boolean containsMV(AssetEntry ventry) {
      if(ventry == null) {
         return false;
      }

      final String identifier = ventry.toIdentifier();
      MVDef[] list = list(false, def -> identifier.equals(def.getVsId()));

      return list.length > 0;
   }

   /**
    * Find all sheets based on the worksheet entry.
    */
   public String[] findSheets(AssetEntry wentry, boolean ws) {
      if(wentry == null) {
         return new String[0];
      }

      final String wsId = wentry.toIdentifier();

      MVDef[] list = list(false, def -> {
         if(def.isWSMV() != ws) {
            return false;
         }

         if(ws) {
            for(String sheet : def.getMetaData().getRegisteredSheets()) {
               if(Tool.equals(wsId, sheet)) {
                  return true;
               }
            }
         }
         else {
            return Tool.equals(wsId, def.getWsId());
         }

         return false;
      });

      Set<String> sheetNames = new LinkedHashSet<>();

      for(MVDef mvDef : list) {
         sheetNames.addAll(Arrays.asList(mvDef.getMetaData().getRegisteredSheets()));
      }

      return sheetNames.toArray(new String[0]);
   }

   /**
    * Remove a registered sheet from mv.
    */
   public synchronized boolean remove(MVDef def, String sheetName) {
      boolean changed = def.getMetaData().unregister(sheetName);

      if(def.getMetaData().getRegisteredSheets().length <= 0) {
         changed = remove(def) || changed;

         if(LOG.isDebugEnabled()) {
            LOG.debug("MV is no longer used after deleting " + sheetName +
                         ": " + def.getName());
         }

         ClusterUtil.deleteClusterMV(def.getName());
      }

      return changed;
   }

   /**
    * Remove one mv.
    * @return removed successful.
    */
   public boolean remove(MVDef mv) {
      return remove(mv, true);
   }

   /**
    * Remove one mv.
    * @param event true to fire a change event.
    * @return removed successful.
    */
   public boolean remove(MVDef mv, boolean event) {
      if(mv == null) {
         return false;
      }

      mv.container = mv.getContainer();
      boolean success = mvs.remove(mv.getName()) != null;

      if(success) {
         mv.dispose();
      }

      if(event) {
         fireEvent("mvmanager_" + mv.getName(), MV_CHANGE_EVENT, null, null);
      }

      return success;
   }

   /**
    * List all MVs.
    */
   public MVDef[] list(boolean sort) {
      return list(sort, null);
   }

   public synchronized MVDef[] list(boolean sort, MVFilter filter) {
      return list(sort, filter, null);
   }

   /**
    * List all MVs.
    */
   public synchronized MVDef[] list(boolean sort, MVFilter filter, Principal principal) {
      List<MVDef> defs = new ArrayList<>();
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      String[] orgIDs = null;

      if(principal instanceof SRPrincipal) {
         String orgId = ((SRPrincipal) principal).getOrgId();
         orgId = orgId == null ? Organization.getDefaultOrganizationID() : orgId;
         orgIDs = new String[1];
         orgIDs[0] = orgId;
      }
      else {
         orgIDs = Arrays.stream(provider.getOrganizations())
            .map(name -> provider.getOrganization(name).getId())
            .toArray(String[]::new);
      }

      for(String orgID : orgIDs) {
         for(String key : mvs.keySet(orgID)) {
            try {
               defs.add(mvs.get(key, orgID));
            }
            catch(Exception e) {
               LOG.error("Failed to read MV definition: " + key + " " + orgID, e);
            }
         }
      }

      Stream<MVDef> stream = defs.stream().filter(def -> def != null);

      if(filter != null) {
         stream = stream.filter(filter::accept);
      }

      if(sort) {
         stream = stream.sorted();
      }

      return stream.toArray(MVDef[]::new);
   }

   /**
    * Triggered when viewsheet is renamed.
    */
   public synchronized void renameDependencies(AssetEntry oentry, AssetEntry nentry) {
      // it's not easy to rename mv files, so here we just remove them
      // from file system, then users need to re-create mv
      boolean renamed = !Tool.equals(oentry + "", nentry + "");

      if(renamed) {
         LOG.warn(
            "Materialized view for {} will be removed because the asset is being renamed to {}",
            oentry, nentry);
      }
      else if(nentry != null) {
         AssetEntry pentry = nentry.getParent();
         final String folder = pentry != null ?
            pentry.getDescription(false) : nentry.getParentPath();
         LOG.warn(
            "Materialized view for {} will be removed because the asset is being moved to folder {}",
            oentry, folder);
      }

      removeDependencies(oentry);
   }

   /**
    * Triggered when viewsheet is renamed.
    */
   public synchronized void removeDependencies(AssetEntry entry) {
      MVDef[] arr = list(false);
      boolean changed = false;

      for(final MVDef mvDef : arr) {
         if(mvDef.matches(entry)) {
            changed = remove(mvDef) || changed;

            if(LOG.isDebugEnabled()) {
               LOG.debug("Deleting MV from dependency: " + entry);
            }
         }
      }
   }

   /**
    * Add property change listener.
    */
   public synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
      for(WeakReference<PropertyChangeListener> ref : listeners) {
         PropertyChangeListener obj = ref.get();

         if(listener.equals(obj)) {
            return;
         }
      }

      WeakReference<PropertyChangeListener> ref = new WeakReference<>(listener);
      listeners.add(ref);
   }

   /**
    * Remove property change listener.
    */
   public synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
      for(int i = listeners.size() - 1; i >= 0; i--) {
         WeakReference<PropertyChangeListener> ref = listeners.get(i);
         PropertyChangeListener obj = ref.get();

         if(listener.equals(obj)) {
            listeners.remove(i);
            return;
         }
      }
   }

   /**
    * Fire property change event.
    */
   public void fireEvent(String src, String name, Object oval, Object nval) {
      Vector<WeakReference<PropertyChangeListener>> listenersClone = new Vector<>(this.listeners);
      PropertyChangeEvent evt = new PropertyChangeEvent(src, name, oval, nval);

      for(int i = listenersClone.size() - 1; i >= 0; i--) {
         WeakReference<PropertyChangeListener> ref = listenersClone.get(i);
         PropertyChangeListener listener = ref.get();

         if(listener == null) {
            listenersClone.remove(i);
            continue;
         }

         try {
            listener.propertyChange(evt);
         }
         catch(Exception ex) {
            LOG.warn("Failed to process property change event for property " + name +
               ", old=" + oval + ", new=" + nval, ex);
         }
      }
   }

   /**
    * Refresh mvs.
    */
   public void refresh() {
   }

   public MVDef getShareMV(MVDef mv) {
      return getShareMV(mv, true);
   }

   public synchronized MVDef getShareMV(MVDef mv, boolean recordVS) {
      MVDef[] arr = list(false);

      for(MVDef mv0 : arr) {
         if(mv0.isSharedBy(mv)) {
            if(recordVS && mv0.getMetaData() != null && mv.getMetaData() != null) {
               mv0.shareMV(mv);
            }

            return mv0;
         }
      }

      return null;
   }

   public synchronized MVDef[] getContainsMVs(MVDef mv) {
      MVDef[] arr = list(false);
      List<MVDef> list = new ArrayList<>();

      for(MVDef mv0 : arr) {
         if(mv.isSharedBy(mv0)) {
            list.add(mv0);
         }
      }

      return list.toArray(new MVDef[0]);
   }

   public MVDef[] checkShareBySelf(MVDef[] mvs) {
      return checkShareBySelf(mvs, true);
   }

   public synchronized MVDef[] checkShareBySelf(MVDef[] mvs, boolean recordVS) {
      List<MVDef> mvs0 = new ArrayList<>();

      for(int i = 0; i < mvs.length; i++) {
         boolean contains = false;

         for(int j = i + 1; j < mvs.length; j++) {
            contains = mvs[j].isSharedBy(mvs[i]);

            if(contains) {
               if(recordVS && mvs[j].getMetaData() != null &&
                  mvs[i].getMetaData() != null)
               {
                  mvs[j].shareMV(mvs[i]);
               }

               mvs0.add(mvs[j]);
            }
         }

         if(!contains && !mvs0.contains(mvs[i])) {
            mvs0.add(mvs[i]);
         }
      }

      return mvs0.toArray(new MVDef[0]);
   }

   /**
    * Set the default cycle.
    */
   public void setDefaultCycle(String dcycle) {
      this.dcycle = dcycle;
   }

   /**
    * Get the default cycle.
    */
   public String getDefaultCycle() {
      return dcycle;
   }

   /**
    * Fill MVDef with more data.
    */
   public synchronized void fill(MVDef def) {
      if(def == null) {
         return;
      }

      String name = def.getName();

      // def2 will be gc-ed when exit
      MVDef def2 = mvs.get(name);

      if(def2 != null) {
         def.copyContainer(def2);
      }
      else {
         LOG.warn("Materialized view {} not found", name);
      }
   }

   public boolean isMaterialized(String id, boolean ws) {
      MVDef[] defs = list(true, def -> def.isWSMV() == ws);
      return Arrays.stream(defs).anyMatch(def -> def.isSuccess() &&
         def.getMetaData().isRegistered(id));
   }

   public void initMVDefMap() {
      mvs.initLastModified();
   }

   /**
    * MVFilter filters one MV.
    */
   public interface MVFilter {
      boolean accept(MVDef def);
   }

   private static final MVCreator NULL_CREATOR = new MVCreator() {
      @Override
      public void cancel() {
         // no-op
      }

      @Override
      public boolean create() {
         return true;
      }
   };

   private static final Logger LOG = LoggerFactory.getLogger(MVManager.class);

   private final MVDefMap mvs = new MVDefMap();
   private final Map<Object, MVCreator> pending = new ConcurrentHashMap<>();
   private final Vector<WeakReference<PropertyChangeListener>> listeners = new Vector<>();
   private long lastCleanup;
   private String dcycle;
}
