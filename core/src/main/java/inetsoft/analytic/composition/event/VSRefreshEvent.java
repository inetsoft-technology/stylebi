/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.analytic.composition.event;

import inetsoft.analytic.composition.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import inetsoft.util.ItemMap;

import java.util.List;

/**
 * Refresh (execute) a viewsheet event.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class VSRefreshEvent extends ViewsheetEvent implements BinaryEvent {
   /**
    * Constructor.
    */
   public VSRefreshEvent() {
      super();
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Refresh");
   }

   /**
    * Check if is undoable/redoable.
    * @return <tt>true</tt> if undoable/redoable.
    */
   @Override
   public boolean isUndoable() {
      return false;
   }

   /**
    * Get the influenced assemblies.
    * @return the influenced assemblies, <tt>null</tt> means all.
    */
   @Override
   public String[] getAssemblies() {
      return null;
   }

   /**
    * Process event.
    */
   @Override
   public void process(RuntimeViewsheet rvs, AssetCommand command)
      throws Exception
   {
      processBookmark(rvs, command);
      ChangedAssemblyList clist =
         createList(true, this, command, rvs, getLinkURI());
      ChangedAssemblyList.ReadyListener rlistener = clist.getReadyListener();
      boolean meta = "true".equals(get("__tableMetaData__"));
      boolean userRefresh = "true".equals(get("userRefresh"));
      ItemMap params = (ItemMap) get("parameters");
      Object initing = get("initing");

      if(rlistener != null && (initing == null || initing.equals("true"))) {
         rlistener.setInitingGrid(true);
         rlistener.setID(getID());
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return;
      }

      if("true".equals(get("checkShareFilter")) && !needRefresh(rvs)) {
         return;
      }

      try {
         AssetQuerySandbox wbox = box.getAssetQuerySandbox();

         if(params != null) {
            VariableTable vars = VSEventUtil.decodeParameters(params);

            if(wbox != null) {
               wbox.refreshVariableTable(vars);
            }
         }

         // @by stephenwebster, For Bug #1432
         // When a refresh occurs, viewsheets based on models get their
         // worksheets created dynamically.  There is a unique case where if
         // the viewsheet is refreshing at the same time it is being opened
         // (Due to scale to screen/window resize), this refresh event can wipe
         // out the worksheet causing other events to fail
         // (like GetChartAreaEvent), and displays an error.
         // The error that occurs due to the refresh action is unnecessary.
         // Here we will hint to the ViewsheetSandbox that the viewsheet is
         // being refreshed so we can prevent these errors from being
         // propogated to the end users.
         box.setRefreshing(true);

         // for table metadata, do not reset runtime
         if(!meta) {
            rvs.resetRuntime();
            rvs.setTouchTimestamp(System.currentTimeMillis());
            box.setTouchTimestamp(rvs.getTouchTimestamp());
         }

         // refresh the current bookmark info when other user changed the current
         // opened bookmark
         rvs.refreshCurrentBookmark(command);
         // refreshViewsheet invokes onLoad, and should be called before reset(),
         // which execute the element scripts
         VSEventUtil.refreshViewsheet(rvs, this, getID(), getLinkURI(), command,
                                      false, true, true, clist);


         // fix bug1256872756925, the VSTable.syncColumnSelection() will changed
         // the correct column selection if the model is not correct, so wait
         // until the model is correct, then to load table lens
         // VSEventUtil.refreshEmbeddedViewsheet(rvs, this, getLinkURI(), command)

         // when some objects' visible property changed, should add/delete those
         // objects
         VSEventUtil.addDeleteEmbeddedViewsheet(rvs, this, getLinkURI(),
                                                command);

         Viewsheet vs = rvs.getViewsheet();

         // replace viewsheet to keep the viewsheet for redo being uptodate
         // if this refresh event is call from other events, don't modify the
         // undo queue since it's managed by the top event
         if(vs != null && userRefresh) {
            rvs.replaceCheckpoint(vs.prepareCheckpoint(), null);
         }
      }
      finally {
         box.setRefreshing(false);
      }
   }

   /**
    * Check if need refresh.
    * @return <tt>true</tt> if Refresh/unRefresh.
    */
   private Boolean needRefresh(RuntimeViewsheet rvs) {
      Assembly[] assemblies = rvs.getViewsheet().getAssemblies();
      ViewsheetService engine = ViewsheetEngine.getViewsheetEngine();
      RuntimeSheet[] rarr = engine.getRuntimeSheets(rvs.getUser());

      if(assemblies == null || rarr == null) {
         return false;
      }

      for(int i = 0; i < assemblies.length; i++) {
         for(int j = 0; j < rarr.length; j++) {
            if(!(rarr[j] instanceof RuntimeViewsheet)) {
               return false;
            }

            RuntimeViewsheet rv = (RuntimeViewsheet) rarr[j];
            ViewsheetSandbox rbox = rv.getViewsheetSandbox();
            ViewsheetSandbox[] boxes = rbox.getSandboxes();

            for(int k = 0; k < boxes.length; k++) {
               Viewsheet vs = boxes[k].getViewsheet();
               VSAssembly assembly = (VSAssembly) assemblies[i];

               List<VSAssembly> list =
                  VSUtil.getSharedVSAssemblies(vs, assembly);

               if(list.size() > 0) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   /**
    * process viewsheet bookmark.
    */
   private void processBookmark(RuntimeViewsheet rvs, AssetCommand command)
      throws Exception
   {
      if(getUser() == null) {
         return;
      }

      IdentityID currUser = IdentityID.getIdentityIDFromKey(getUser().getName());
      VSBookmarkInfo info = null;
      String bookmarkName = (String) get("bookmarkName");
      IdentityID bookmarkUser = IdentityID.getIdentityIDFromKey((String) get("bookmarkUser"));

      if(bookmarkName == null || bookmarkUser == null) {
         return;
      }

      //anonymous should not apply bookmark.
      if(XPrincipal.ANONYMOUS.equals(currUser.name)) {
         return;
      }

      //@temp get type and readonly from bookmark info by name and user.
      for(VSBookmarkInfo bminfo : rvs.getBookmarks(bookmarkUser)) {
         if(bminfo != null && bookmarkName.equals(bminfo.getName())) {
            info = bminfo;
         }
      }

      if(info == null) {
         return;
      }

      int bookmarkType = info.getType();
      boolean readOnly = info.isReadOnly();

      if(!currUser.equals(bookmarkUser)) {
         if(VSBookmarkInfo.PRIVATE == bookmarkType) {
            return;
         }
         else if(VSBookmarkInfo.GROUPSHARE == bookmarkType &&
            !rvs.isSameGroup(bookmarkUser, currUser))
         {
            return;
         }
      }

      EditBookmarkEvent.processBookmark(bookmarkName, bookmarkUser,
         bookmarkType, readOnly, rvs, (XPrincipal) getUser(), this,
         getLinkURI(), getID(), command);
   }
}
