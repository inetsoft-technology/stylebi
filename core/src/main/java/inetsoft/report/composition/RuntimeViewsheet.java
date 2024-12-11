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

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.UserEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.VSBookmark.DefaultBookmark;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.util.*;
import inetsoft.util.audit.AuditRecordUtils;
import inetsoft.util.audit.BookmarkRecord;
import inetsoft.web.embed.EmbedAssemblyInfo;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.ActionListener;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * RuntimeViewsheet represents a runtime viewsheet, contains the viewsheet and
 * other runtime information.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class RuntimeViewsheet extends RuntimeSheet {
   /**
    * Viewsheet design mode.
    */
   public static final int VIEWSHEET_DESIGN_MODE = Viewsheet.SHEET_DESIGN_MODE;
   /**
    * Viewsheet runtime mode.
    */
   public static final int VIEWSHEET_RUNTIME_MODE = Viewsheet.SHEET_RUNTIME_MODE;

   /**
    * Create a runtime viewsheet for a viewsheet.
    */
   public RuntimeViewsheet() {
      super();
   }

   /**
    * Create a runtime viewsheet for a viewsheet.
    * @param entry the viewsheet repository entry.
    * @param vs viewsheet definition.
    * @param user the user who opens this viewsheet.
    * @param rep the specified asset repository.
    * @param bookmarksMap the specified viewsheet bookmark.
    * @param reset <tt>true</tt> to reset the viewsheet sandbox.
    */
   public RuntimeViewsheet(AssetEntry entry, Viewsheet vs, Principal user,
                           AssetRepository rep, WorksheetService engine,
                           Map<String, VSBookmark> bookmarksMap, boolean reset)
   {
      this();
      this.entry = entry;
      this.user = user;
      this.rep = rep;
      this.viewer = "true".equals(entry.getProperty("viewer"));
      this.preview = "true".equals(entry.getProperty("preview"));
      this.mode = viewer || preview || entry.isVSSnapshot() ?
         VIEWSHEET_RUNTIME_MODE : VIEWSHEET_DESIGN_MODE;
      // only design time is editable
      setEditable(mode == VIEWSHEET_DESIGN_MODE);
      this.bookmarksMap = bookmarksMap;

      if(this.bookmarksMap == null) {
         this.bookmarksMap = new HashMap<>();
      }

      Viewsheet ovs = this.vs;
      this.vs = vs;
      this.originalVs = vs;
      resetViewsheet(this.vs, ovs);

      this.dateCreated = System.currentTimeMillis();

      // Get the user environment property if annotations should be shown and set it on
      // the viewsheet so the assemblies can check it without having access to the
      // current user
      final boolean showAnnotation =
         "true".equals(UserEnv.getProperty(user, "annotation", "true"));
      this.vs.setAnnotationsVisible(showAnnotation && mode == VIEWSHEET_RUNTIME_MODE);

      // maintain the inital viewsheet state
      if(isRuntime()) {
         ibookmark = new VSBookmark();
         ibookmark.addBookmark(VSBookmark.INITIAL_STATE, vs,
            VSBookmarkInfo.PRIVATE, false, true);
      }

      setEntry(entry);

      // use user defined default bookmark
      if(isRuntime()) {
         refresh();
      }

      this.engine = engine;
      initViewsheet(this.vs, true);
      this.box = new ViewsheetSandbox(this.vs, mode, user, reset, entry);
      this.box.setOpenedBookmark(getOpenedBookmark()); // set in the call to refresh()
      this.box.getAssetQuerySandbox().setMVProcessor(new MVProcessorImpl(this));
      // the cloned viewsheet is for undo/redo, and we should share the cloned
      // viewsheet in cached query and undo/redo
      addCheckpoint(this.vs.prepareCheckpoint(), null);
   }

   /**
    * Update bookmark.
    */
   public void updateVSBookmark() {
      updateVSBookmark(false);
   }

   /**
    * Update bookmark.
    */
   public void updateVSBookmark(boolean updateHome) {
      if(this.user == null) {
         return;
      }

      if(isAnonymous()) {
         VSBookmark bookmark = getVSBookmark(XPrincipal.ANONYMOUS);

         if(bookmark != null &&
            !bookmark.containsBookmark(VSBookmark.HOME_BOOKMARK))
         {
            bookmark.addHomeBookmark(vs, isRuntime());
         }

         return;
      }

      // open a viewsheet?
      if((entry.getScope() == AssetRepository.GLOBAL_SCOPE ||
          entry.getScope() == AssetRepository.USER_SCOPE) &&
         entry.getType() == AssetEntry.Type.VIEWSHEET && isRuntime())
      {
         if(entry.getScope() != AssetRepository.USER_SCOPE) {
            bookmarksMap.clear();
         }

         if(rep instanceof AbstractAssetEngine) {
            AbstractAssetEngine abstractAssetEngine = (AbstractAssetEngine) rep;
            abstractAssetEngine.clearCache(entry);
         }

         IdentityID pId = user == null ? null : IdentityID.getIdentityIDFromKey(getUserName());
         VSBookmark bookmark = getVSBookmark(getUserName());
         List<VSBookmarkInfo> visibleBookmarks = getUserVisibleBookmarks(pId);

         if(bookmark != null && !containsHomeBookmark(visibleBookmarks)) {
            bookmark.addHomeBookmark(vs, true);
         }
         // fix Bug #24338, should update the home bookmark when preview the viewsheet.
         else if(updateHome) {
            try {
               updateBookmark(VSBookmark.HOME_BOOKMARK, pId, 0);
            }
            catch(Exception ex) {
               LOG.warn("Failed to update home bookmark", ex);
            }
         }
      }
      else if(entry.getScope() == AssetRepository.TEMPORARY_SCOPE &&
         entry.getType() == AssetEntry.Type.VIEWSHEET && isPreview())
      {
         IdentityID pId = IdentityID.getIdentityIDFromKey(this.user.getName());
         VSBookmark bookmark = new VSBookmark();
         bookmark.setUser(pId);
         bookmark.addHomeBookmark(vs, isRuntime());
         bookmarksMap.put(getUserName(), bookmark);
      }
   }

   /**
    * Get VSBookmark for principalKey.
    */
   private VSBookmark getVSBookmark(String principalKey) {
      VSBookmark bookmark = null;

      try {
         bookmark = rep.getVSBookmark(entry, new XPrincipal(IdentityID.getIdentityIDFromKey(principalKey)));
         bookmarksMap.put(principalKey, bookmark);
      }
      catch(Exception ex) {
         LOG.warn("Failed to get bookmark", ex);
      }

      return bookmark;
   }

   /**
    * Set the asset entry.
    * @param entry the specified asset entry.
    */
   @Override
   public void setEntry(AssetEntry entry) {
      super.setEntry(entry);
      updateVSBookmark(isRuntime());

      // go to the default bookmark state for runtime only
      if(isRuntime() && !isAnonymous() && vs != null && user != null) {
         Viewsheet ovs = vs;
         vs = gotoDefaultBookmark(vs);
         resetViewsheet(vs, ovs);
      }

      // set runtime
      if(vs != null) {
         vs.setRuntimeEntry(entry);
      }
   }

   /**
    * Set the Execution Session ID for the RuntimeViewsheet.
    * @param eSessionID the specified sheet id.
    */
   public void setExecSessionID(String eSessionID) {
      execSessionID = eSessionID;
   }

   /**
    * Set the sheet id.
    * @param id the specified sheet id.
    */
   @Override
   public void setID(String id) {
      super.setID(id);

      // when create sheet, will goto the user defined bookmark, but in this
      // time, the sheet id is not set yet, the when set id, lock the user
      // defined bookmar
      if(isViewer()) {
         lockDefaultBookmark();
      }
   }

   /**
    * Goto the user defined bookmark.
    */
   private Viewsheet gotoDefaultBookmark(Viewsheet vs) {
      VSBookmark bookmark = getUserBookmark(user);
      DefaultBookmark defBookmark = bookmark == null ? null :
         bookmark.getDefaultBookmark();
      boolean userDefined = false;

      if(defBookmark != null && isViewer() && SecurityEngine.getSecurity().isSecurityEnabled()) {
         try {
            if(getDefaultContainer(defBookmark) != null) {
               VSBookmark cbookmark = getDefaultContainer(defBookmark);
               vs = VSUtil.vsGotoBookmark(vs, cbookmark, defBookmark.getName(), rep);
               userDefined = true;
               VSBookmarkInfo dInfo = cbookmark.getBookmarkInfo(defBookmark.getName());
               setOpenedBookmark(dInfo);
            }
            else {
               // the default bookmark had been removed
               setDefaultBookmark(null);
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to open the default bookmark", ex);
         }
      }

      if(!userDefined) {
         if(ibookmark != null) {
            vs = ibookmark.getBookmark(VSBookmark.INITIAL_STATE, vs);
         }

         if(bookmark != null) {
            vs = bookmark.getHomeBookmark(vs);
            VSBookmarkInfo hInfo = bookmark.getBookmarkInfo(VSBookmark.HOME_BOOKMARK);

            if(hInfo == null) {
               //The owner of (Home) bookmark is admin
               bookmark = getUserBookmark(new IdentityID("admin", OrganizationManager.getInstance().getCurrentOrgID()));
               vs = bookmark.getHomeBookmark(vs);
               hInfo = bookmark.getBookmarkInfo(VSBookmark.HOME_BOOKMARK);
            }

            setOpenedBookmark(hInfo);
         }
      }

      return vs;
   }

   /**
    * Check if is anonymous user.
    */
   private boolean isAnonymous() {
      return user != null && XPrincipal.ANONYMOUS.equals(getUserName());
   }

   /**
    * Get the viewsheet bookmark.
    * @return the viewsheet bookmark.
    */
   public Map<String, VSBookmark> getVSBookmark() {
      return bookmarksMap;
   }

   /**
    * Get the corresponding repository.
    */
   public AssetRepository getAssetRepository() {
      return rep;
   }

   @Override
   public int addCheckpoint(AbstractSheet sheet, GridEvent event) {
      ((Viewsheet) sheet).prepareCheckpoint();
      return super.addCheckpoint(sheet, event);
   }

   /**
    * Get the asset query sandbox.
    * @return the asset query sandbox.
    */
   public ViewsheetSandbox getViewsheetSandbox() {
      return box;
   }

   /**
    * Reset the runtime states. This is called if the definition of the
    * viewsheet or worksheet may have changed from external event so the
    * states can be reinitialized to be in sync.
    */
   public void resetRuntime() {
      final ViewsheetSandbox box = this.box;
      final Viewsheet vs = this.vs;

      if(vs != null && box != null) {
         box.lockWrite();

         try {
            vs.update(getAssetRepository(), null, getUser());
            box.resetRuntime();
            initViewsheet(vs, true);
            lastReset = System.currentTimeMillis();
         }
         finally {
            box.unlockWrite();
         }
      }
   }

   /**
    * Get the timestamp of the last reset. This runtime is update to date as of
    * that time.
    */
   public long getLastReset() {
      return lastReset;
   }

   /**
    * Refresh the runtime viewsheet to go back to the initialize state.
    */
   public void refresh() {
      gotoDefaultBookmark(vs);
   }

   /**
    * Get the contained sheet.
    * @return the contained sheet.
    */
   @Override
   public AbstractSheet getSheet() {
      return getViewsheet();
   }

   /**
    * Get the viewsheet definition.
    */
   public Viewsheet getViewsheet() {
      return vs;
   }

   @Override
   public boolean isTimeout() {
      if(vs != null && vs.getViewsheetInfo() != null &&
         vs.getViewsheetInfo().isUpdateEnabled() &&
         "true".equals(SreeEnv.getProperty("wallboarding.enabled")))
      {
         return false;
      }

      return super.isTimeout();
   }

   @Override
   protected long getMaxIdleTime0() {
      if(mode == VIEWSHEET_DESIGN_MODE) {
         return super.getMaxIdleTime0() * 10;
      }

      return super.getMaxIdleTime0();
   }

   /**
    * Set the viewsheet object.
    * @param vs the specified viewsheet object.
    */
   public void setViewsheet(Viewsheet vs) {
      if(isDisposed()) {
         return;
      }

      Viewsheet ovs = this.vs;
      this.vs = vs;
      resetViewsheet(this.vs, ovs);

      box.setViewsheet(this.vs, true);
      // vs changed, ignore the previous failed mv attempts and try again
      entry.resetMVOptions();
   }

   /**
    * Check if the specified assmebly is a tip view.
    */
   public boolean isTipView(String fullname) {
      if(vs == null) { // disposed
         return false;
      }

      if(tipviews.containsKey(fullname)) {
         return true;
      }

      Assembly assembly = vs.getAssembly(fullname);

      if(assembly instanceof ContainerVSAssembly) {
         String[] names = ((ContainerVSAssembly) assembly).getAssemblies();

         if(names != null) {
            for(String name : names) {
               assembly = ((VSAssembly) assembly).getViewsheet().getAssembly(name);

               if(assembly != null && isTipView(assembly.getAbsoluteName())) {
                  return true;
               }
            }
         }
      }
      else {
         ContainerVSAssembly containerVSAssembly = getContainerVSAssembly(fullname);

         if(containerVSAssembly != null && tipviews.containsKey(containerVSAssembly.getName())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if the specified assmebly is a pop component.
    */
   public boolean isPopComponent(String fullname) {
      if(vs == null) { // disposed
         return false;
      }

      if(popcomponents.contains(fullname)) {
         return true;
      }

      ContainerVSAssembly containerVSAssembly = getContainerVSAssembly(fullname);

      if(containerVSAssembly != null &&
         Arrays.stream(containerVSAssembly.getAssemblies()).anyMatch(n -> n.equals(fullname)) &&
         popcomponents.contains(containerVSAssembly.getName()))
      {
         return true;
      }

      Assembly assembly = vs.getAssembly(fullname);

      if(!(assembly instanceof ContainerVSAssembly)) {
         return false;
      }

      String[] names = ((ContainerVSAssembly) assembly).getAssemblies();

      if(names == null || names.length == 0) {
         return false;
      }

      for(String name : names) {
         assembly = ((VSAssembly) assembly).getViewsheet().getAssembly(name);

         if(assembly != null && isPopComponent(assembly.getAbsoluteName())) {
            return true;
         }
      }

      return false;
   }

   private ContainerVSAssembly getContainerVSAssembly(String name) {
      Assembly[] assemblies = vs.getAssemblies();

      for(Assembly assembly : assemblies) {
         if(!assembly.getName().equals(name) && assembly instanceof ContainerVSAssembly) {
            ContainerVSAssembly containerVSAssembly = (ContainerVSAssembly) assembly;
            boolean match = Arrays.stream(containerVSAssembly.getAssemblies()).anyMatch((n) -> n.equals(name));

            if(match) {
               return containerVSAssembly;
            }
         }
      }

      return null;
   }

   /**
    * Go to bookmark.
    * @param name the specified bookmark name.
    * @return <tt>true</tt> if go to bookmark successfully, <tt>false</tt>
    * otherwise.
    */
   public boolean gotoBookmark(String name, IdentityID user) throws Exception {
      boolean isHome = VSBookmark.HOME_BOOKMARK.equals(name);
      VSBookmark bookmark = getUserBookmark(user);
      Viewsheet processedViewsheet = null;

      // @by davidd bug1370989280980, If goto Home bookmark but it does not
      // exist in the user's bookmarks, then just set to initial state.
      if(isHome) {
         processedViewsheet = ibookmark.getBookmark(VSBookmark.INITIAL_STATE, originalVs.clone());
      }

      // Process the bookmark that was found
      if(bookmark != null) {
         if(!isHome && !bookmark.containsBookmark(name)) {
            LOG.warn("Bookmark doesn't exist: " + name + " in [" +
                        Arrays.toString(bookmark.getBookmarks()) + "]");
            return false;
         }

         // clear existing locks
         clearLock();
         lockBookmark(name, user);

         if(processedViewsheet == null) {
            processedViewsheet = (Viewsheet) vs.clone();
         }

         processedViewsheet = VSUtil.vsGotoBookmark(processedViewsheet, bookmark, name, rep);
      }

      // Apply the updated viewsheet
      if(processedViewsheet != null) {
         if(rvsLayout != null) {
            processedViewsheet = rvsLayout.apply(processedViewsheet);
         }

         setOpenedBookmark(bookmark == null ? null : bookmark.getBookmarkInfo(name));

         point = -1;
         points = new XSwappableSheetList(this.contextPrincipal);
         events = new ArrayList<>();

         getEntry().setProperty("bookmarkName", name);
         getEntry().setProperty("bookmarkUser", user.convertToKey());
         setViewsheet(processedViewsheet);
         addCheckpoint(processedViewsheet.prepareCheckpoint(), null);

         return true;
      }

      return false;
   }

   /**
    * Refresh the current opened bookmark.
    */
   public void refreshCurrentBookmark(AssetCommand command) throws Exception {
      VSBookmarkInfo cinfo = getOpenedBookmark();

      if(cinfo == null || VSBookmark.HOME_BOOKMARK.equals(cinfo.getName())) {
         return;
      }

      Assembly[] oldArr = vs.getAssemblies(true, false);
      boolean refreshed = refreshBookmark(cinfo.getName(), cinfo.getOwner(),
                                          cinfo.getLastModified());

      if(refreshed) {
         Assembly[] newArr = vs.getAssemblies(true, false);
         AnnotationVSUtil.removeUselessAssemblies(oldArr, newArr, command);
      }
   }

   /**
    * Refresh the current opened bookmark.
    */
   public void refreshCurrentBookmark(CommandDispatcher commandDispatcher, boolean confirmed)
      throws Exception
   {
      VSBookmarkInfo cinfo = getOpenedBookmark();

      if(cinfo == null || VSBookmark.HOME_BOOKMARK.equals(cinfo.getName()) && !confirmed) {
         return;
      }

      Assembly[] oldArr = vs.getAssemblies(true, false);
      boolean refreshed = refreshBookmark(cinfo.getName(), cinfo.getOwner(),
                                          cinfo.getLastModified(), confirmed);

      if(refreshed) {
         Assembly[] newArr = vs.getAssemblies(true, false);
         AnnotationVSUtil.removeUselessAssemblies(oldArr, newArr, commandDispatcher);
      }
   }

   /**
    * Refresh the specified bookmark.
    * @param name the specified bookmark name.
    * @param user the specified bookmark owner.
    * @param lastModified only refresh if the bookmark is newer
    */
   public boolean refreshBookmark(String name, IdentityID user, long lastModified)
      throws Exception
   {
      return refreshBookmark(name, user, lastModified, false);
   }

   /**
    * Refresh the specified bookmark.
    * @param name the specified bookmark name.
    * @param user the specified bookmark owner.
    * @param lastModified only refresh if the bookmark is newer
    */
   public boolean refreshBookmark(String name, IdentityID user, long lastModified, boolean confirmed)
         throws Exception
   {
      if(!updateBookmark(name, user, lastModified, confirmed)) {
         return false;
      }

      if(name.equals(VSBookmark.HOME_BOOKMARK)) {
         Viewsheet originalBookmark = getOriginalBookmark(VSBookmark.HOME_BOOKMARK);
         ibookmark = new VSBookmark();
         ibookmark.addBookmark(VSBookmark.INITIAL_STATE, originalBookmark,
                               VSBookmarkInfo.PRIVATE, false, true);
      }

      // goto the refreshed bookmark
      gotoBookmark(name, user);

      return true;
   }

   /**
    * Update the specified bookmark.
    * @param name the bookmark name.
    * @param owner the bookmark owner.
    */
   private boolean updateBookmark(String name, IdentityID owner, long lastModified)
      throws Exception
   {
      return updateBookmark(name, owner, lastModified, false);
   }

   /**
    * Update the specified bookmark.
    * @param name the bookmark name.
    * @param owner the bookmark owner.
    */
   private boolean updateBookmark(String name, IdentityID owner, long lastModified, boolean confirmed)
         throws Exception
   {
      VSBookmark nbookmark =
         rep.getVSBookmark(entry, new XPrincipal(owner));

      if(nbookmark == null) {
         return false;
      }

      Object data = nbookmark.getBookmarkData(name);
      VSBookmarkInfo ninfo = nbookmark.getBookmarkInfo(name);

      if(data == null || ninfo == null || ninfo.getLastModified() <= lastModified) {
         return false;
      }

      VSBookmark bm = getUserBookmark(owner);

      if(bm == null) {
         return false;
      }

      bm.setBookmarkData(name, data);
      bm.setBookmarkInfo(name, ninfo);

      if(name.equals(VSBookmark.HOME_BOOKMARK) && confirmed) {
         ibookmark.setBookmarkData(VSBookmark.INITIAL_STATE, data);
         ibookmark.setBookmarkInfo(VSBookmark.INITIAL_STATE, ninfo);
      }

      return true;
   }

   /**
    * Check if the specified bookmark exists.
    * @param name the specified bookmark name.
    * @param owner the specified bookmark owner.
    */
   public boolean checkBookmark(String name, IdentityID owner) throws Exception {
      VSBookmark nbookmark =
         rep.getVSBookmark(entry, new XPrincipal(owner));

      if(nbookmark == null) {
         return false;
      }

      if(VSBookmark.HOME_BOOKMARK.equals(name)) {
         return true;
      }

      return nbookmark.containsBookmark(name);
   }

   /**
    * Check if contains a bookmark.
    * @param name the name of the specified bookmark.
    * @return <tt>true</tt> if contains it, <tt>false</tt> otherwise.
    */
   public boolean containsBookmark(String name, IdentityID user) {
      if(getUserBookmark(user) == null) {
         return false;
      }

      VSBookmark bookmark = getUserBookmark(user);

      return bookmark.containsBookmark(name);
   }

   /**
    * Get the bookmark.
    * @param name the specified bookmark name.
    * @return the viewsheet applied this bookmark.
    */
   public Viewsheet getBookmark(String name) {
      return user == null ? null : getBookmark(name, IdentityID.getIdentityIDFromKey(getUserName()));
   }

   /**
    * Get the bookmark.
    * @param name the specified bookmark name.
    * @return the viewsheet applied this bookmark.
    */
   public Viewsheet getBookmark(String name, IdentityID user) {
      VSBookmark bookmark = getUserBookmark(user);

      if(bookmark == null) {
         return null;
      }

      return VSUtil.vsGotoBookmark(vs.clone(), bookmark, name, rep);
   }

   /**
    * Get the original bookmark, the viewsheet's state is same as bookmark.
    * @param name the specified bookmark name.
    */
   public Viewsheet getOriginalBookmark(String name) {
      // fix bug1433297711278, if is shared bookmark, need to get the original
      // bookmark by name and owner.
      if(!VSBookmark.HOME_BOOKMARK.equals(name) && name.contains("(")) {
         int idx0 = name.lastIndexOf("(");
         int idx1 = name.lastIndexOf(")");

         if(idx1 > idx0) {
            String bname = name.substring(0, idx0);
            IdentityID user = IdentityID.getIdentityIDFromKey(name.substring(idx0 + 1, idx1));
            return getBookmark(bname, user);
         }
      }

      if(user == null) {
         return null;
      }

      VSBookmark bookmark = getUserBookmark(user);

      if(bookmark == null) {
         return null;
      }

      Viewsheet processedViewsheet = vs.clone();
      // @by stephenwebster, For bug1428907012145, retrieve the proper
      // viewsheet representing the initial state for the home bookmark.
      if(VSBookmark.HOME_BOOKMARK.equals(name)) {
         processedViewsheet = ibookmark.getBookmark(VSBookmark.INITIAL_STATE,
            processedViewsheet);
      }

      return VSUtil.vsGotoBookmark(processedViewsheet, bookmark, name, rep);
   }

   /**
    * Add the bookmark.
    * @param name the specified bookmark name.
    * @param type the specified bookmark's type.
    */
   public void addBookmark(String name, int type, IdentityID user, boolean readOnly)
      throws Exception
   {
      VSBookmark userBookmark = bookmarksMap.get(user.convertToKey());

      // if current added bookmark is home, we shouldn't add home here.
      if((userBookmark == null ||
         !userBookmark.containsBookmark(VSBookmark.HOME_BOOKMARK))
         && !VSBookmark.HOME_BOOKMARK.equals(name))
      {
         updateVSBookmark();
      }

      VSBookmark bookmark = getUserBookmark(user);

      if(bookmark == null) {
         return;
      }

      if(VSBookmark.HOME_BOOKMARK.equals(name)) {
         ibookmark = new VSBookmark();
         ibookmark.addBookmark(VSBookmark.INITIAL_STATE, vs,
            VSBookmarkInfo.PRIVATE, false, true);
      }

      // @by davidd 2012-12-28, When adding bookmarks commit the adhoc filters.
      // Macquarie use-case initiates add bookmark from our API.
      if(vs.hasAdhocFilters()) {
         Viewsheet vsClone = (Viewsheet) vs.clone();
         // Copy the RVS to not disturb the active adhoc filters
         RuntimeViewsheet rvsClone = new RuntimeViewsheet(entry, vsClone,
            this.user, rep, engine, bookmarksMap, false);
         VSEventUtil.changeAdhocFilterStatus(rvsClone, new AssetCommand());
         vsClone = rvsClone.getViewsheet();
         bookmark.addBookmark(name, vsClone, type, readOnly, true);
         // update this RVS with the new bookmark
         bookmarksMap.put(user.convertToKey(), bookmark);
      }
      else {
         bookmark.addBookmark(name, vs, type, readOnly, true);
      }

      AssetEntry entry = AssetEntry.createAssetEntry(bookmark.getIdentifier());
      rep.setVSBookmark(entry, bookmark, new XPrincipal(user));
      setOpenedBookmark(bookmark.getBookmarkInfo(name));
      // clear the old bookmark lock first
      clearLock();
      lockBookmark(name, user);
   }

   /**
    * Get specified user bookmark.
    */
   private VSBookmark getUserBookmark(IdentityID user) {
      if(bookmarksMap == null) {
         return null;
      }

      if(!bookmarksMap.containsKey(user.convertToKey())) {
         getVSBookmark(user.convertToKey());
      }

      return bookmarksMap.get(user.convertToKey());
   }

   /**
    * Get specified user bookmark.
    */
   private VSBookmark getUserBookmark(Principal user) {
      if(user == null) {
         return null;
      }

      return getUserBookmark(IdentityID.getIdentityIDFromKey(user.getName()));
   }

   /**
    * Remove a bookmark.
    * @param name the specified bookmark name.
    */
   public void removeBookmark(String name, IdentityID user) throws Exception {
      removeBookmark(name, user, true);
   }

   /**
    * Remove a bookmark.
    * @param name the specified bookmark name.
    * @param user the specified bookmark's owner.
    * @param checkLock if true, force to remove the bookmark, if false, when
    *                  the bookmark is locked, then return.
    */
   public void removeBookmark(String name, IdentityID user, boolean checkLock)
      throws Exception
   {
      updateVSBookmark();
      VSBookmark bookmark = getUserBookmark(user);

      if(bookmark == null) {
         return;
      }

      if(checkLock) {
         String lockedUser = getLockedBookmarkUser(name, user);

         if(lockedUser != null) {
            Catalog catalog = Catalog.getCatalog();
            throw new MessageException(
               catalog.getString("viewer.viewsheet.bookmark.otherUserLock",
               name, lockedUser));
         }
      }

      bookmark.removeBookmark(name);
      AssetEntry entry = AssetEntry.createAssetEntry(bookmark.getIdentifier());
      rep.setVSBookmark(entry, bookmark, new XPrincipal(user));
      // delete the all locks of this bookmark
      unLockBookmark(name, user);
   }

   /**
    * Remove a bookmark.
    * @param names the bookmark names.
    * @param user the specified bookmark's owner.
    *
    */
   public void removeBookmarks(List<String> names, IdentityID user)
           throws Exception
   {
      removeBookmarks(names, user, true);
   }

   /**
    * Remove a bookmark.
    * @param names the bookmark names.
    * @param user the specified bookmark's owner.
    * @param checkLock if true, force to remove the bookmark, if false, when
    *                  the bookmark is locked, then return.
    */
   public void removeBookmarks(List<String> names, IdentityID user, boolean checkLock)
           throws Exception
   {
      updateVSBookmark();
      VSBookmark bookmark = getUserBookmark(user);

      if(bookmark == null) {
         return;
      }

      for(String name : names) {
         if(checkLock) {
            String lockedUser = getLockedBookmarkUser(name, user);

            if(lockedUser != null) {
               Catalog catalog = Catalog.getCatalog();
               throw new MessageException(catalog.getString("viewer.viewsheet.bookmark.otherUserLock", name,
                       lockedUser));
            }
         }

         VSBookmarkInfo bookmarkInfo = getBookmarkInfo(name, user);
         bookmark.removeBookmark(name);
         AuditRecordUtils.executeBookmarkRecord(
            getViewsheet(), bookmarkInfo, BookmarkRecord.ACTION_TYPE_DELETE);
      }

      AssetEntry entry = AssetEntry.createAssetEntry(bookmark.getIdentifier());
      rep.setVSBookmark(entry, bookmark, new XPrincipal(user));


      for(String name : names) {
         // delete the all locks of this bookmark
         unLockBookmark(name, user);
      }
   }

   /**
    * Edit a bookmark's properties.
    * @param nname the specified new bookmark name.
    * @param oname the specified old bookmark name.
    * @param type the specified bookmark new type.
    */
   public void editBookmark(String nname, String oname, int type,
      boolean readOnly) throws Exception
   {
      VSBookmark bookmark = getUserBookmark(user);

      if(bookmark == null) {
         return;
      }

      bookmark.editBookmark(nname, oname, type, readOnly);

      if(bookmark.getDefaultBookmark() != null) {
         VSBookmark.DefaultBookmark dbookmark = bookmark.getDefaultBookmark();

         if(oname.equals(dbookmark.getName())) {
            dbookmark.setName(nname);
         }
      }

      AssetEntry entry = AssetEntry.createAssetEntry(bookmark.getIdentifier());
      rep.setVSBookmark(entry, bookmark, user);
      AssetEntry vsEntry = getEntry();

      if(vsEntry != null) {
         List<AssetObject> entries = DependencyTransformer.getDependencies(vsEntry.toIdentifier());

         if(entries == null || entries.size() == 0) {
            return;
         }

         RenameDependencyInfo dependencyInfo = new RenameDependencyInfo();
         RenameInfo rinfo = new RenameInfo(oname, nname, RenameInfo.BOOKMARK);
         rinfo.setBookmarkVS(vsEntry.toIdentifier());
         rinfo.setBookmarkUser(bookmark.getUser());

         for(AssetObject assetObject : entries) {
            dependencyInfo.addRenameInfo(assetObject, rinfo);
         }

         RenameTransformHandler.getTransformHandler().addTransformTask(dependencyInfo);
      }
   }

   /**
    * Check if the specified bookmark is updated by other user.
    * @param name the specified bookmark's name.
    * @param user the specified bookmark's user.
    * return <tt>true</tt> bookmark changed, <tt>false</tt> otherwise.
    */
   public boolean bookmarkUpdated(String name, IdentityID user) throws Exception{
      // temp by charles, look for another way to flag change
      VSBookmark obookmark = getUserBookmark(user);
      VSBookmarkInfo oinfo = obookmark.getBookmarkInfo(name);

      // The Home bookmark may not exist, consider it never modified.
      if(oinfo == null) {
         return false;
      }

      long oldModifyTime = oinfo.getLastModified();

      VSBookmark nbookmark =
         rep.getVSBookmark(entry, new XPrincipal(user));
      VSBookmarkInfo ninfo = nbookmark.getBookmarkInfo(name);

      if(ninfo == null) {
         return false;
      }

      long newModifyTime = ninfo.getLastModified();
      return oldModifyTime < newModifyTime;
   }

   /**
    * Check the specified bookmark if writable.
    * @param name the bookmark name.
    * @param owner the bookmark owner.
    */
   public boolean bookmarkWritable(String name, IdentityID owner) throws Exception {
      if(owner.convertToKey().equals(getUserName())) {
         return true;
      }

      // update the bookmark firstly
      if(!updateBookmark(name, owner, 0)) {
         return false;
      }

      VSBookmark bookmark = getUserBookmark(owner);

      if(bookmark == null) {
         return false;
      }

      VSBookmarkInfo info = bookmark.getBookmarkInfo(name);

      if(info != null) {
         return !info.isReadOnly();
      }

      return false;
   }

   /**
    * Lock the specified bookmark.
    * @param name the specified bookmark name.
    */
   private void lockBookmark(String name, IdentityID owner) throws Exception {
      BookmarkLockManager lockManager = BookmarkLockManager.getManager();
      lockManager.lock(getLockPath(name, owner), getUserName(), getID());
   }

   /**
    * Lock the user defined default bookmark.
    */
   private void lockDefaultBookmark() {
      VSBookmark bookmark = getUserBookmark(user);
      DefaultBookmark defBookmark = bookmark == null ? null :
         bookmark.getDefaultBookmark();

      if(defBookmark == null || isPreview()) {
         return;
      }

      if(getDefaultContainer(defBookmark) != null) {
         try {
            lockBookmark(defBookmark.getName(), defBookmark.getOwner());
         }
         catch(Exception ex) {
            LOG.error("Failed to lock the default bookmark", ex);
         }
      }
   }

   /**
    * Return the VSBookmark which contains the user defined bookmark.
    */
   private VSBookmark getDefaultContainer(DefaultBookmark def) {
      VSBookmark bookmark = getUserBookmark(user);
      IdentityID pId = user == null ? null : IdentityID.getIdentityIDFromKey(user.getName());

      if(bookmark == null) {
         return null;
      }

       // for BC
      if((def.getOwner() == null || "".equals(def.getOwner().name))) {
         def.setOwner(pId);
      }

      String defName = def.getName();
      IdentityID defOwner = def.getOwner();

      if(pId != null && pId.equals(defOwner) && bookmark.containsBookmark(defName))
      {
         return bookmark;
      }
      else {
         VSBookmark bm = getUserBookmark(defOwner);

         if(bm != null && bm.containsBookmark(defName)) {
            return bm;
         }
      }

      return null;
   }

   /**
    * Get the Execution Session ID associated with the RuntimeViewsheet.
    */
   public String getExecSessionID() {
      return execSessionID;
   }

   /**
    * Unlock the specified bookmark.
    * @param name the specified bookmark name.
    * @param owner the specified bookmark owner.
    */
   public void unLockBookmark(String name, IdentityID owner)
      throws Exception
   {
      BookmarkLockManager lockManager = BookmarkLockManager.getManager();
      lockManager.unlock(getLockPath(name, owner), getUserName(), getID());
   }

   /**
    * clear the bookmark locks of this sheet.
    */
   private void clearLock() {
      BookmarkLockManager lockManager = BookmarkLockManager.getManager();
      lockManager.unlockAll(getUserName(), getID());
   }

   private String getLockedBookmarkUser(String name, IdentityID owner) {
      BookmarkLockManager lockManager = BookmarkLockManager.getManager();
      String lockUser = lockManager.getLockedBookmarkUser(getLockPath(name, owner), getUserName());
      return lockUser == null ? null : IdentityID.getIdentityIDFromKey(lockUser).name;
   }

   private String getUserName() {
      return user != null ? user.getName() : null;
   }

   /**
    * Get the lock path with specified bookmark name.
    */
   private String getLockPath(String name, IdentityID owner) {
      String SEPERATOR = "%";
      String all = entry.getPath() + name + owner;
      String hashCode = Integer.toHexString((entry.getPath() + name + owner).hashCode());

      // avoid file name too long
      return endOf(entry.getPath(), 120) + SEPERATOR + endOf(name, 30) +
         "_" + endOf(owner.name, 20) + "_" + all.length() + "_" + hashCode;
   }

   // get the last len substring
   private static String endOf(String str, int len) {
      return str.substring(Math.max(0, str.length() - len));
   }

   /**
    * Get all user's bookmarks.
    * @return the current user visible bookmark.
    */
   public List<VSBookmarkInfo> getBookmarks() {
      if(this.user == null) {
         return new ArrayList<>();
      }

      return getBookmarks(IdentityID.getIdentityIDFromKey(getUserName()));
   }

   /**
    * Get visible bookmarks of the current user.
    * @param vsUser the viewsheet user.
    */
   public List<VSBookmarkInfo> getBookmarks(IdentityID vsUser) {
      List<VSBookmarkInfo> visibleBookmarks = getUserVisibleBookmarks(vsUser);

      // @by ChrisS 2014-5-22: bug1400577032157 fix#2b
      // moved the adding of a (Home) bookmark down to after the user loop
      if(!containsHomeBookmark(visibleBookmarks)) {
         // @by davidd bug1370989280980, Add the HOME bookmark to this user's
         // bookmark list.
         visibleBookmarks.add(
            new VSBookmarkInfo(VSBookmark.HOME_BOOKMARK,
            VSBookmarkInfo.ALLSHARE, vsUser, false,
            new java.util.Date().getTime()));
      }

      return VSUtil.sortBookmark(visibleBookmarks, this.user);
   }

    /**
     * Get visible bookmarks of the current user.
     * @param vsUser the viewsheet user.
     */
   private List<VSBookmarkInfo> getUserVisibleBookmarks(IdentityID vsUser) {
       List<VSBookmarkInfo> visibleBookmarks = new ArrayList<>();

      if(bookmarksMap == null || vsUser == null || vsUser.name.length() == 0) {
         return visibleBookmarks;
      }

      prepareVSBookmarks();
      Set<String> keys = bookmarksMap.keySet();
      Iterator<String> it = keys.iterator();
      boolean isGlobalVS = entry.getScope() == AssetRepository.GLOBAL_SCOPE;
      boolean homeBookmarkExists = false;

      while(it.hasNext()) {
         IdentityID user = IdentityID.getIdentityIDFromKey(it.next());
         VSBookmark bookmark = getUserBookmark(user);

         if(bookmark == null) {
            continue;
         }

         String[] bookmarks = bookmark.getBookmarks();

         for(String bookmarkName : bookmarks) {
            VSBookmarkInfo info = bookmark.getBookmarkInfo(bookmarkName);
            int type = info.getType();

            if(!isGlobalVS || type == VSBookmarkInfo.PRIVATE) {
               if(!vsUser.equals(user)) {
                  continue;
               }
            }
            else if(type == VSBookmarkInfo.GROUPSHARE) {
               if(!isSameGroup(vsUser, user)) {
                  continue;
               }
            }

            info.setOwner(user);

            // @by stephenwebster, Fix bug1421333908729
            // In older versions the home bookmark was stored with the user's
            // bookmarks. As a result, it is possible to return multiple home
            // bookmarks, which does not appear to be the expectation.
            // This will force only the current user's home bookmark to be
            // added to the list if it exists.
            if(bookmarkName.equals(VSBookmark.HOME_BOOKMARK))
            {
               if(!homeBookmarkExists && vsUser.equals(user)) {
                  homeBookmarkExists = true;
                  visibleBookmarks.add(info);
               }
            }
            else {
               visibleBookmarks.add(info);
            }

         }
      }

      return visibleBookmarks;
   }

   /**
    * Check if the target bookmarks contains the home bookmark
    * @param allBookmarks the target bookmarks.
    */
   private boolean containsHomeBookmark(List<VSBookmarkInfo> allBookmarks) {
      // @by ChrisS 2014-5-22: bug1400577032157 fix#2b
      // Moved the adding of a (Home) bookmark down here, so it occurs *after*
      // all the bookmarks from the other users have been added to the list.
      // This was done because we only want to add a (Home) bookmark if there
      // is not already one in the list, from any other user.  Which
      // realistically can only occur if there is no admin user.
      boolean haveHome = false;

      for(VSBookmarkInfo info : allBookmarks) {
         if(info.getName().equals(VSBookmark.HOME_BOOKMARK)) {
            haveHome = true;
            break;
         }
      }

      return haveHome;
   }

   /**
    * Prepare VSBookmark for all users.
    */
   private void prepareVSBookmarks() {
      if(entry.getType() == AssetEntry.Type.VIEWSHEET) {
         IdentityID[] users = getBookmarkUsers();

         for(IdentityID user : users) {
            if(!bookmarksMap.containsKey(user.convertToKey())) {
               getVSBookmark(user.convertToKey());
            }
         }
      }
   }

   /**
     * Check the the two specified users are in same group.
     */
   public boolean isSameGroup(IdentityID user0, IdentityID user1) {
      if(user0.equals(user1)) {
         return true;
      }

      String[] groups0 = SUtil.getGroups(user0);
      List<String> list = Arrays.asList(groups0);
      String[] group1 = SUtil.getGroups(user1);

      for(String group : group1) {
         if(list.contains(group)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get user defined default bookmark.
    * @return user defined default bookmark if any.
    */
   public DefaultBookmark getDefaultBookmark() {
      VSBookmark bookmark = getUserBookmark(user);

      if(bookmark == null) {
         return null;
      }

      return bookmark.getDefaultBookmark();
   }

   /**
    * Set default user defined bookmark.
    * @param defBookmark the specified user defined bookmark.
    */
   public void setDefaultBookmark(DefaultBookmark defBookmark) throws Exception
   {
      VSBookmark bookmark = getUserBookmark(user);

      if(bookmark == null) {
         return;
      }

      bookmark.setDefaultBookmark(defBookmark);
      AssetEntry entry = AssetEntry.createAssetEntry(bookmark.getIdentifier());
      rep.setVSBookmark(entry, bookmark, user);
   }

   /**
    * Get the viewsheet mode.
    * @return the viewsheet mode.
    */
   @Override
   public int getMode() {
      return mode;
   }

   /**
    * Set the viewsheet variables.
    */
   public void setVariableTable(VariableTable vars) {
      this.vars = vars;
   }

   /**
    * Get the viewsheet variables.
    */
   public VariableTable getVariableTable() {
      return vars;
   }

   /**
    * Get the touch viewsheet timestamp.
    * @return touch viewsheet timestamp.
    */
   public long getTouchTimestamp() {
      return touchts;
   }

   /**
    * Set the touch viewsheet timestamp.
    * @param touchts viewsheet timestamp.
    */
   public void setTouchTimestamp(long touchts) {
      this.touchts = touchts;
   }

   /**
    * Reset the influenced assemblies.
    * @param arr the influenced assemblies.
    * @param clist the specified changed assembly list.
    */
   @SuppressWarnings("unused")
   private void reset(String[] arr, ChangedAssemblyList clist) {
      List<Assembly> list = new ArrayList<>();
      String vname = null;

      if(arr == null) {
         Assembly[] assemblies = vs.getAssemblies();
         Collections.addAll(list, assemblies);
      }
      else {
         for(String s : arr) {
            int index = s.lastIndexOf(".");

            if(index >= 0) {
               vname = s.substring(0, index);
            }

            Assembly assembly = vs.getAssembly(s);

            if(assembly == null) {
               continue;
            }

            list.add(assembly);

            if(!(assembly instanceof SelectionVSAssembly)) {
               continue;
            }

            SelectionVSAssembly sassembly = (SelectionVSAssembly) assembly;
            ViewsheetSandbox[] boxes = box.getSandboxes();

            for(ViewsheetSandbox box : boxes) {
               Viewsheet vs = box.getViewsheet();
               List<VSAssembly> tassemblies =
                  VSUtil.getSharedVSAssemblies(vs, sassembly);

               for(VSAssembly tassembly : tassemblies) {
                  box.reset(null, new Assembly[]{ tassembly }, clist,
                            false, false, null);
               }
            }
         }
      }

      Assembly[] assemblies = new Assembly[list.size()];
      list.toArray(assemblies);

      box.reset(vname, assemblies, clist, false, false, null);
   }

   /**
    * Dispose the runtime viewsheet.
    */
   @Override
   public void dispose() {
      clearLock();

      super.dispose();

      resetViewsheet(null, vs);
      vs = null;

      if(vars != null) {
         vars = null;
      }

      if(box != null) {
         box.dispose();
         box = null;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public synchronized boolean undo(ChangedAssemblyList clist) {
      if(point - 1 >= 0 && point < size()) {
         restoreCheckpoint(point - 1);
         point--;
         return true;
      }

      return false;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public synchronized boolean redo(ChangedAssemblyList clist) {
      if(point + 1 >= 0 && point + 1 < size()) {
         restoreCheckpoint(point + 1);
         point += 1;
         return true;
      }

      return false;
   }

   private void restoreCheckpoint(int point) {
      EventInfo event = events.get(point);
      restoreCheckpoint0(point, event.requiresReset());
   }

   private void restoreCheckpoint0(int point, boolean requiresReset) {
      Viewsheet vs = (Viewsheet) points.get(point);
      boolean reloadWS = vs.getBaseWorksheet() == null;

      if(reloadWS) {
         vs.update(getAssetRepository(), null, getUser());
      }

      updateLayoutInfo(vs);
      setViewsheet(vs.clone());

      // if viewsheet is swapped back from disk, need to initialize base worksheet
      if(requiresReset || reloadWS) {
         resetRuntime();
      }
   }

   /**
    * Rollback last changes.
    */
   @Override
   public void rollback() {
      if(points.size() > 0) {
         restoreCheckpoint0(points.size() - 1, false);
      }
   }

   /**
    * Undo in master pane will not influence all other layout pane.
    */
   public void updateLayoutInfo(Viewsheet vs) {
      LayoutInfo layoutInfo = this.vs.getLayoutInfo();
      vs.setLayoutInfo((LayoutInfo) layoutInfo.clone());
   }

   /**
    * Copy shared selection for a selection assembly.
    */
   private void copySharedFilters(RuntimeSheet[] rarr, VSAssembly tassembly) {
      if(!(tassembly instanceof SelectionVSAssembly || tassembly instanceof InputVSAssembly)) {
         return;
      }

      for(int i = 0; rarr != null && i < rarr.length; i++) {
         if(!(rarr[i] instanceof RuntimeViewsheet) || Tool.equals(entry, rarr[i].getEntry())) {
            continue;
         }

         RuntimeViewsheet rvs = (RuntimeViewsheet) rarr[i];
         ViewsheetSandbox rbox = rvs.getViewsheetSandbox();

         if(rbox == null) {
            continue;
         }

         ViewsheetSandbox[] boxes = rbox.getSandboxes();

         for(ViewsheetSandbox box : boxes) {
            Viewsheet vs = box.getViewsheet();
            List<VSAssembly> fassemblies = VSUtil.getSharedVSAssemblies(vs, tassembly);

            // @by: ChrisSpagnoli bug1397460287828 #2 2014-8-19
            // Cannot pick just *one* "best" assembly to share from, as that fails
            // to consider fields which are not in that one assembly.
            // Instead have to share from all, to all.

            if(fassemblies != null && !fassemblies.isEmpty()) {
               for(VSAssembly fassembly : fassemblies) {
                  if(tassembly instanceof SelectionVSAssembly) {
                     ViewsheetSandbox.processSharedSelection(tassembly, fassembly);
                  }
                  else if(tassembly instanceof InputVSAssembly) {
                     VSUtil.copySelectedValues((InputVSAssembly) tassembly,
                                               (InputVSAssembly) fassembly);
                  }
               }
            }
         }
      }
   }

   /**
    * Check if is a runtime viewsheet.
    * @return <tt>true</tt> if a runtime viewsheet, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean isRuntime() {
      return mode == VIEWSHEET_RUNTIME_MODE;
   }

   /**
    * Check if is a preview runtime viewsheet.
    * @return <tt>true</tt> if a preview runtime viewsheet, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean isPreview() {
      return preview;
   }

   /**
    * Check if is viewer runtime viewsheet.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isViewer() {
      return viewer;
   }

   /**
    * Get the original viewsheet id.
    * @return the original viewsheet id.
    */
   public String getOriginalID() {
      return box.getOriginalID();
   }

   /**
    * Set the original viewsheet id.
    * @param oid the specified original viewsheet id.
    */
   public void setOriginalID(String oid) {
      if(box != null) {
         box.setOriginalID(oid);
      }
   }

   /**
    * Check if need to refresh the rvs. like need refresh after form write back action.
    * @return
    */
   public boolean isNeedRefresh() {
      return needRefresh;
   }

   /**
    * Set if need to refresh the rvs.
    * @param refresh   next refresh event will force to refresh the rvs if true, else not.
    */
   public void setNeedRefresh(boolean refresh) {
      this.needRefresh = refresh;
   }

   /**
    * Get the base worksheet.
    */
   public RuntimeWorksheet getRuntimeWorksheet() {
      Worksheet ws = vs.getBaseWorksheet();

      // @by billh, for vs binds to logical model, we need to shrink worksheet,
      // so that table joins are properly maintained. This place might be the
      // best position to perform such a task for WorksheetEvent
      if(vs.isLMSource() && ws != null) {
         ws = new WorksheetWrapper(ws);
         VSUtil.shrinkTable(vs, ws);
      }

      return new RuntimeWorksheet(vs.getBaseEntry(), ws, box.getUser(),
                                  box.getAssetQuerySandbox(), false);
   }

   /**
    * Initialize viewsheet.
    * 1. hide all components used as tip views.
    * 2. modify worksheet to add a mirror to filter data for tip view.
    * 3. apply shared selection.
    */
   public void initViewsheet(Viewsheet vs, boolean root) {
      Assembly[] arr = vs.getAssemblies();
      RuntimeSheet[] rarr = engine.getRuntimeSheets(user);

      for(Assembly assembly : arr) {
         if(assembly instanceof VSAssembly) {
            copySharedFilters(rarr, (VSAssembly) assembly);
         }

         VSAssemblyInfo info0 = (VSAssemblyInfo) assembly.getInfo();

         if((assembly instanceof Viewsheet)) {
            initViewsheet((Viewsheet) assembly, false);
            continue;
         }
         else if((!(info0 instanceof TipVSAssemblyInfo) && !(info0 instanceof PopVSAssemblyInfo)) ||
            root && mode != VIEWSHEET_RUNTIME_MODE)
         {
            continue;
         }

         if(info0 instanceof TipVSAssemblyInfo) {
            refreshTipViewTable((TipVSAssemblyInfo) info0, vs);
         }
         else if(info0 instanceof PopVSAssemblyInfo) {
            int popOption = ((PopVSAssemblyInfo) info0).getPopOption();
            String popComponent = ((PopVSAssemblyInfo) info0).getPopComponent();
            VSAssembly obj = (VSAssembly) vs.getAssembly(popComponent);

            if(popOption != PopVSAssemblyInfo.POP_OPTION || obj == null ||
               obj.getContainer() != null) {
               continue;
            }

            initPopComponentTable(obj, vs);
         }
      }
   }

   /**
    * Refresh the tipviews of the runtime viewsheet by the info.
    * @param info the TipVSAssemblyInfo
    * @return <tt>true</tt> if it can refresh, <tt>false</tt> otherwise.
    */
   private void refreshTipViewTable(TipVSAssemblyInfo info, Viewsheet vs) {
      int tipOption = info.getTipOption();
      String tipview = info.getTipView();
      VSAssembly obj = (VSAssembly) vs.getAssembly(tipview);

      if(tipOption != TipVSAssemblyInfo.VIEWTIP_OPTION || obj == null ||
         obj.getContainer() != null)
      {
         return;
      }

      initTipViewTable(obj, vs);
   }

   /**
    * Refresh the tip views or pop components of the runtime viewsheet.
    */
   public void refreshAllTipViewOrPopComponentTable() {
      Assembly[] arr = vs.getAssemblies();

      if(mode != VIEWSHEET_RUNTIME_MODE) {
         return;
      }

      for(Assembly assembly : arr) {
         if(assembly instanceof VSAssembly) {
            VSAssemblyInfo vsAssemblyInfo = ((VSAssembly) assembly).getVSAssemblyInfo();

            if(vsAssemblyInfo instanceof TipVSAssemblyInfo) {
               refreshTipViewTable((TipVSAssemblyInfo) assembly.getInfo(), vs);
            }
            else if(vsAssemblyInfo instanceof PopVSAssemblyInfo) {
               refreshPopComponentTable((PopVSAssemblyInfo) assembly.getInfo(), vs);
            }
         }
      }
   }

   public void refreshPopComponentTable(PopVSAssemblyInfo info, Viewsheet vs) {
      int popOption = info.getPopOption();
      String componentValue = info.getPopComponentValue();
      VSAssembly obj = (VSAssembly) vs.getAssembly(componentValue);

      if(popOption != PopVSAssemblyInfo.POP_OPTION || obj == null ||
         obj.getContainer() != null)
      {
         return;
      }

      initPopComponentTable(obj, vs);
   }

   /**
    * Restore the tip assemboy binding table.
    */
   public Viewsheet restoreViewsheet() {
      Viewsheet viewsheet = (Viewsheet) vs.clone();
      Assembly[] arr = viewsheet.getAssemblies();

      for(Assembly assembly : arr) {
         if(assembly instanceof Viewsheet) {
            continue;
         }

         VSAssemblyInfo info0 = (VSAssemblyInfo) assembly.getInfo();

         if(!(info0 instanceof TipVSAssemblyInfo) &&
            !(info0 instanceof PopVSAssemblyInfo)) {
            continue;
         }

         if(info0 instanceof TipVSAssemblyInfo) {
            int tipOption = ((TipVSAssemblyInfo) info0).getTipOption();
            String tipview = ((TipVSAssemblyInfo) info0).getTipView();
            VSAssembly obj = (VSAssembly) viewsheet.getAssembly(tipview);

            if(tipOption != TipVSAssemblyInfo.VIEWTIP_OPTION || obj == null ||
               obj.getContainer() instanceof TabVSAssembly) {
               continue;
            }

            restoreTipViewTable(obj);
         }
         else if(info0 instanceof PopVSAssemblyInfo) {
            int popOption = ((PopVSAssemblyInfo) info0).getPopOption();
            String popcomponent = ((PopVSAssemblyInfo) info0).getPopComponent();
            VSAssembly obj = (VSAssembly) viewsheet.getAssembly(popcomponent);

            if(popOption != PopVSAssemblyInfo.POP_OPTION || obj == null ||
               obj.getContainer() instanceof TabVSAssembly) {
               continue;
            }

            restorePopComponentTable(obj);
         }
      }

      return viewsheet;
   }

   /**
    * Create a new ws table for the tip view.
    */
   private void initTipViewTable(VSAssembly obj, Viewsheet vs) {
      VSAssemblyInfo info = obj.getVSAssemblyInfo();

      if(!VSUtil.isFlyOver(info.getAbsoluteName(), vs)) {
         info.setVisible(false);
      }

      if(obj instanceof BindableVSAssembly) {
         BindableVSAssembly bindable = (BindableVSAssembly) obj;
         vs.getBaseWorksheet();
         String tbl = tipviews.get(info.getAbsoluteName());

         if(tbl == null) {
            tbl = bindable.getTableName();
            // this method may be called from refresh, in that case we should
            // use the original binding name instead of the mirror created
            // in the previous call
            tipviews.put(info.getAbsoluteName(), tbl);
         }

         // @by billh, do not know why we need to create another table,
         // it will break several functions such as brush, zoom and mv
         /*
         if(tbl != null && ws != null) {
            TableAssembly tassembly = (TableAssembly) ws.getAssembly(tbl);
            String name = AssetUtil.getNextName(ws, "tipTable");
            MirrorTableAssembly mirror =
               new MirrorTableAssembly(ws, name, null, false, tassembly);

            mirror.setVisible(false);
            ws.addAssembly(mirror);
            bindable.setTableName(name);
         }
         */
      }
      else if(obj instanceof ShapeVSAssembly) {
         tipviews.putIfAbsent(info.getAbsoluteName(), "shape");
      }

      if(obj instanceof ContainerVSAssembly) {
         String[] children = ((ContainerVSAssembly) obj).getAssemblies();

         for(String child : children) {
            VSAssembly cobj = (VSAssembly) vs.getAssembly(child);
            initTipViewTable(cobj, vs);
         }
      }
   }

   /**
    * Create a new ws table for the pop component.
    */
   private void initPopComponentTable(VSAssembly obj, Viewsheet vs) {
      VSAssemblyInfo info = obj.getVSAssemblyInfo();

      info.setVisible(false);

      popcomponents.add(info.getAbsoluteName());

      if(obj instanceof ContainerVSAssembly) {
         String[] children = ((ContainerVSAssembly) obj).getAssemblies();

         for(String child : children) {
            VSAssembly cobj = (VSAssembly) vs.getAssembly(child);
            initPopComponentTable(cobj, vs);
         }
      }
   }

   /**
    * Restore the tip view table.
    */
   private void restoreTipViewTable(VSAssembly obj) {
      VSAssemblyInfo info = obj.getVSAssemblyInfo();
      info.setVisible(true);

      // @by billh, do not know why we need to create another table,
      // it will break several functions such as brush, zoom and mv
      /*
      if(obj instanceof BindableVSAssembly) {
         BindableVSAssembly bindable = (BindableVSAssembly) obj;
         String originaltable = (String) (tipviews.get(info.getAbsoluteName()));

         if(originaltable != null) {
            bindable.setTableName(originaltable);;
         }
      }
      */

      if(obj instanceof ContainerVSAssembly) {
         String[] children = ((ContainerVSAssembly) obj).getAssemblies();

         for(String child : children) {
            VSAssembly cobj = (VSAssembly) vs.getAssembly(child);
            restoreTipViewTable(cobj);
         }
      }
   }

   /**
    * Restore the pop component table.
    */
   private void restorePopComponentTable(VSAssembly obj) {
      VSAssemblyInfo info = obj.getVSAssemblyInfo();
      info.setVisible(true);

      if(obj instanceof ContainerVSAssembly) {
         String[] children = ((ContainerVSAssembly) obj).getAssemblies();

         for(String child : children) {
            VSAssembly cobj = (VSAssembly) vs.getAssembly(child);
            restorePopComponentTable(cobj);
         }
      }
   }

   /**
    * Get the created time of runtime viewsheet.
    */
   public long getDateCreated() {
      return dateCreated;
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
      this.openedBookmark = bookmark;

      if(box != null) {
         box.setOpenedBookmark(bookmark);
      }
   }

   /**
    * Set a property of the sheet.
    * @param key the name of the property.
    * @param value the value of the property, <tt>null</tt> to remove the
    * property.
    */
   @Override
   public void setProperty(String key, Object value) {
      super.setProperty(key, value);

      if("isSafariOniOS".equals(key) && "true".equals(value)) {
         box.setSafariOniOS(true);
      }
   }

   private void resetViewsheet(Viewsheet nvs, Viewsheet ovs) {
      if(nvs != null) {
         nvs.removeActionListener(mvlistener);
         nvs.addActionListener(mvlistener);
      }

      if(ovs != null) {
         ovs.removeActionListener(mvlistener);
      }
   }

   /**
    * Get all users that may have bookmarks for this viewsheet.
    */
   public IdentityID[] getBookmarkUsers() {
      List<IdentityID> list = new ArrayList<>();

      try {
         SecurityEngine engine = SecurityEngine.getSecurity();
         List<IdentityID> userList = rep.getBookmarkUsers(entry);

         for(IdentityID user : userList) {
            XPrincipal principal = SUtil.getPrincipal(user, "localhost", false);

            // @by larryl, optimization, if a user has no permission to
            // create a bookmark, don't check for bookmark
            if(engine.checkPermission(
               principal, ResourceType.VIEWSHEET_ACTION, "Bookmark", ResourceAction.READ))
            {
               list.add(user);
            }
         }
      }
      catch(Exception e) {
         LOG.error("Failed to get bookmark users", e);
      }

      return list.toArray(new IdentityID[0]);
   }

   /**
    * Get bookmark info of the specified bookmark.
    * @param owner the specified bookmark's owner.
    * @param bookmarkName the specified bookmark name.
    * @return the bookmark info of the specified bookmark.
    */
   public VSBookmarkInfo getBookmarkInfo(String bookmarkName, IdentityID owner) {
      if(owner == null || Tool.isEmptyString(owner.name) || Tool.isEmptyString(bookmarkName) ||
         bookmarksMap.get(owner.convertToKey()) == null)
      {
         return null;
      }

      return bookmarksMap.get(owner.convertToKey()).getBookmarkInfo(bookmarkName);
   }

   /**
    * Reset mv options.
    */
   public void resetMVOptions() {
      if(entry != null) {
         entry.resetMVOptions();
      }
   }

   ActionListener mvlistener = e -> {
      int id = e.getID();

      if(id == Viewsheet.ADD_ASSEMBLY ||
         id == Viewsheet.REMOVE_ASSEMBLY ||
         id == Viewsheet.BINDING_CHANGED)
      {
         resetMVOptions();
      }
   };

   /**
    * Get the assemblies which cannot hit mv.
    */
   public Set<String> getNotHitMVAssemblies() {
      return notHitMVs;
   }

   /**
    * Clear not hit mv infomation.
    */
   public void clearNotHitMVInfo() {
      if(notHitMVs != null) {
         notHitMVs.clear();
      }
   }

   /**
    * Get the runtime viewsheet layout.
    */
   public ViewsheetLayout getRuntimeVSLayout() {
      return this.rvsLayout;
   }

   /**
   * Set the runtime viewsheet layout.
   */
   public void setRuntimeVSLayout(ViewsheetLayout layout) {
      this.rvsLayout = layout;
   }

   /**
    * Clear and initialize layout states list.
    */
   public void resetLayoutUndoRedo() {
      layoutPointLock.lock();

      try {
         layoutPoints = new LinkedList<>();
         layoutPoint = -1;
      }
      finally {
         layoutPointLock.unlock();
      }
   }

   /*
    * Undo the current layout.
    */
   public AbstractLayout layoutUndo() {
      layoutPointLock.lock();

      try {
         layoutPoint--;
         return (AbstractLayout) Tool.clone(layoutPoints.get(layoutPoint));
      }
      finally {
         layoutPointLock.unlock();
      }
   }

   /*
    * Redo the current layout.
    */
   public AbstractLayout layoutRedo() {
      layoutPointLock.lock();

      try {
         layoutPoint++;
         return (AbstractLayout) Tool.clone(layoutPoints.get(layoutPoint));
      }
      finally {
         layoutPointLock.unlock();
      }
   }

   /*
    * Add the layout undo redo check point.
    */
   public void addLayoutCheckPoint(AbstractLayout layout) {
      layoutPointLock.lock();

      try {
         if(layoutPoint != layoutPoints.size() - 1) {
            layoutPoints = layoutPoints.subList(0, layoutPoint + 1);
         }

         layoutPoints.add((AbstractLayout) Tool.clone(layout));
         layoutPoint = layoutPoints.size() - 1;
      }
      finally {
         layoutPointLock.unlock();
      }
   }

   /**
    * Current layout state index.
    */
   public int getLayoutPoint() {
      return layoutPoint;
   }

   /**
    * The size of previous layout states.
    */
   public int getLayoutPointsSize() {
      layoutPointLock.lock();

      try {
         return layoutPoints.size();
      }
      finally {
         layoutPointLock.unlock();
      }
   }

   /**
    * set vs wizard of the temporary info
    */
   public void setVSTemporaryInfo(VSTemporaryInfo info) {
      this.temporaryInfo = info;
   }

   /**
    * get vs wizard of the temporary info
    */
   public VSTemporaryInfo getVSTemporaryInfo() {
      return this.temporaryInfo;
   }

   /**
    * weather in viewsheet wizard
    */
   public boolean isWizardViewsheet() {
      return wizardViewsheet;
   }

   /**
    * set in viewshet wizard.
    */
   public void setWizardViewsheet(boolean wizardViewsheet) {
      this.wizardViewsheet = wizardViewsheet;
   }

   public EmbedAssemblyInfo getEmbedAssemblyInfo() {
      return embedAssemblyInfo;
   }

   public void setEmbedAssemblyInfo(EmbedAssemblyInfo embedAssemblyInfo) {
      this.embedAssemblyInfo = embedAssemblyInfo;
   }

   /**
    * MV processor, to handle viewsheet not hit mv.
    */
   private static class MVProcessorImpl implements
      AssetQuerySandbox.MVProcessor
   {
      public MVProcessorImpl(RuntimeViewsheet rvs) {
         this.rvs = rvs;
      }

      @Override
      public boolean needCheck() {
         return rvs != null && !"true".equals(rvs.getProperty("mvconfirmed")) &&
            rvs.getViewsheet() != null &&
            rvs.getViewsheet().getViewsheetInfo() != null &&
            rvs.getViewsheet().getViewsheetInfo().isWarningIfNotHitMV();
      }

      @Override
      public void notHitMV(String target) {
         if(rvs != null && rvs.notHitMVs != null) {
            rvs.notHitMVs.add(target);
         }
      }

      private transient RuntimeViewsheet rvs;
   }

   /**
    * Get the runtime ID of the binding pane opened from this runtime viewsheet
    */
   public String getBindingID() {
      return bindingID;
   }

   public void setBindingID(String bindingID) {
      this.bindingID = bindingID;
   }

   /**
    * Check if this is a viewsheet used in binding pane.
    */
   public boolean isBinding() {
      return box != null && box.isBinding();
   }

   public void setBinding(boolean binding) {
      if(box != null) {
         box.setBinding(binding);
      }
   }

   private String bindingID;
   private Viewsheet vs; // viewsheet
   private Viewsheet originalVs;
   private VariableTable vars; // viewsheet parameter values if any
   private boolean viewer; // viewer flag
   private boolean preview; // preview flag
   private boolean needRefresh = false; // force refresh
   private int mode; // viewsheet mode
   private ViewsheetSandbox box; // query sandbox
   private AssetRepository rep; // asset repository
   private String execSessionID; // Execution Session ID used for Auditing
   private long touchts = -1; // touch timestamp of data changes
   private Map<String, String> tipviews = new HashMap<>(); // full name of tip view -> data table
   private Set<String> popcomponents = new HashSet<>(); // full name of pop component
   private Map<String, VSBookmark> bookmarksMap; // user name -> vs bookmark
   private VSBookmark ibookmark; // inital bookmark
   private VSBookmarkInfo openedBookmark; // the current opened bookmark
   private long lastReset = System.currentTimeMillis();
   private long dateCreated;  // viewsheet created time
   private transient WorksheetService engine = null;
   private transient Set<String> notHitMVs = new HashSet<>();
   private ViewsheetLayout rvsLayout;
   private List<AbstractLayout> layoutPoints = new ArrayList<>();
   private final ReentrantLock layoutPointLock = new ReentrantLock();
   private int layoutPoint = -1;
   private VSTemporaryInfo temporaryInfo;
   private boolean wizardViewsheet = false;
   private EmbedAssemblyInfo embedAssemblyInfo;

   private static final Logger LOG =
      LoggerFactory.getLogger(RuntimeViewsheet.class);
}
