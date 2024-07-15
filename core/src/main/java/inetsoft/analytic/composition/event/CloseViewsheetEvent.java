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

import inetsoft.analytic.composition.ViewsheetEvent;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.command.RemoveVSObjectCommand;
import inetsoft.analytic.composition.command.SetVSEmbedCommand;
import inetsoft.report.composition.AssetCommand;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.AnnotationVSUtil;
import inetsoft.uql.viewsheet.internal.BaseAnnotationVSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;

import java.util.ArrayList;
import java.util.List;

/**
 * Close viewsheet event.
 *
 * @version 8.5, 07/31/2006
 * @author InetSoft Technology Corp
 */
public class CloseViewsheetEvent extends ViewsheetEvent {
   /**
    * Constructor.
    */
   public CloseViewsheetEvent() {
      super();
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Close Viewsheet");
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
      return new String[0];
   }

   /**
    * Close the expired sheet or not.
    */
   @Override
   public boolean isCloseExpired() {
      return true;
   }

   /**
    * Process event.
    */
   @Override
   public void process(RuntimeViewsheet rvs, AssetCommand command)
      throws Exception
   {
      ViewsheetService engine = getViewsheetEngine();
      String eid = rvs == null ? null : rvs.getEmbeddedID();
      List ids = rvs == null ? null : rvs.getViewsheet().getChildrenIds();

      // when close viewsheet, if it has parent viewsheet, refresh it, if
      // is has child viewsheets, refresh all of them, and all the refresh
      // logic move from vs to here, so that we need not mantain the
      // synchronized and delay logic
      // if this is embedded, update the container vs
      if(eid != null) {
         RuntimeViewsheet pvs = engine.getViewsheet(eid, getUser());

         if(pvs != null) {
            refreshParentViewsheet(rvs, pvs, command);
         }
      }

      // if contains child embedded viewsheet, update them
      if(ids != null && ids.size() > 0) {
         refreshChildren(rvs, engine, ids, command);
      }

      engine.closeViewsheet(getID(), getUser());
      AssetEntry entry = rvs.getEntry();
      VSEventUtil.deleteAutoSavedFile(entry, getUser());

      if("true".equals(get("clearCache"))) {
         AbstractAssetEngine assetEngine =
            (AbstractAssetEngine) engine.getAssetRepository();

         if(assetEngine != null && assetEngine.containsEntry(entry)) {
            assetEngine.clearCache(entry);
         }
      }
   }

   /**
    * Refresh parent viewsheet.
    */
   private void refreshParentViewsheet(RuntimeViewsheet rvs,
                                       RuntimeViewsheet pvs,
                                       AssetCommand command)
      throws Exception
   {
      // @by davyc, synchronized the runtime viewsheet, to make sure
      //  VSRefreshEvent and this block working correct, such as:
      // 1: new viewsheet, add a embedded viewsheet
      // 2: click embedded icon to edit the embedded viewsheet
      // 3: add selection list and ragne slider to the embedded viewsheet
      // 4: save and close the embedded viewsheet
      // 5: in the parent viewsheet, the selection list and range slider
      //    may be can not working correct
      // if after synchronized the runtime viewsheet, the problem still
      // exist, two choice:
      // 1: delay the VSRefreshEvent in ExploreViewContext
      // 2: sync the VSRefreshEvent with CloseViewsheetEvent
      // chose 2, so synchronized is useless
      Viewsheet vs = pvs.getViewsheet();
      Assembly[] arr = vs.getAssemblies();
      Viewsheet vs0 = rvs.getViewsheet();
      AssetEntry entry = rvs.getEntry();

      for(int i = 0; i < arr.length; i++) {
         if(!(arr[i] instanceof Viewsheet)) {
            continue;
         }

         Viewsheet evs = (Viewsheet) arr[i];

         if(!entry.equals(evs.getEntry())) {
            continue;
         }

         Assembly[] arr2 = evs.getAssemblies();
         // add from this
         Assembly[] arr0 = vs0.getAssemblies();
         List assemblies = getRemovedAssemblies(arr0, arr2);

         for(int k = 0; k < assemblies.size(); k++) {
            removeObject(rvs, pvs, (VSAssembly) assemblies.get(k),
                         command);
         }

         // copy assemblies
         // only top viewsheet can remove assembly
         for(int k = 0; k < arr2.length; k++) {
            pvs.getViewsheet().removeAssembly((VSAssembly) arr2[k]);
         }

         // during the process to add assembly, if firing event is turned on,
         // sandbox will react accordingly by refreshing assembly or whatever.
         // However, the viewsheet is not a complete viewsheet until all
         // assemblies are added back, which will cause script exceptions and
         // other problems. Considering that there is a refresh process after
         // this process. It's reasonable, safe and faster to turn off event
         // firing, then the following refresh process could update the related
         // sandbox as well
         boolean event = evs.isFireEvent();

         try {
            evs.setFireEvent(false);

            for(int k = 0; k < arr0.length; k++) {
               VSAssembly vass = (VSAssembly) arr0[k].clone();

               if(!AnnotationVSUtil.isAnnotation(vass)) {
                  if(vass.getInfo() instanceof BaseAnnotationVSAssemblyInfo) {
                     ((BaseAnnotationVSAssemblyInfo)
                        vass.getInfo()).clearAnnotations();
                  }

                  evs.addAssembly(vass);
               }
            }
         }
         finally {
            evs.setFireEvent(event);
         }
      }

      vs.removeChildId(getID());
      List nids = vs.getChildrenIds();
      SetVSEmbedCommand scmd = new SetVSEmbedCommand(nids);
      scmd.setID(pvs.getID());
      command.addCommand(scmd);
      // fire VSRefreshEvent here instead of in vs
      refreshViewsheet(command, pvs);
   }

   /**
    * Refresh all child viewsheet.
    */
   private void refreshChildren(RuntimeViewsheet rvs, ViewsheetService engine,
                                List ids, AssetCommand command)
      throws Exception
   {
      Viewsheet pvs = rvs.getViewsheet();

      for(int i = 0; i < ids.size(); i++) {
         String cid = (String) ids.get(i);

         try {
            RuntimeViewsheet ervs = engine.getViewsheet(cid, getUser());

            if(ervs != null) {
               ervs.setEmbeddedID(null);
               Viewsheet cvs = VSEventUtil.getViewsheet(pvs, ervs.getEntry());

               // if parent last modified is before the child last
               // modified, not refresh the child, but when child closed
               // always refresh parent, this will be more reasonable for
               // the embedded viewsheet structure
               if(rvs.getViewsheet().getLastModified(true) <=
                  ervs.getViewsheet().getLastModified(true))
               {
                  continue;
               }

               VSEventUtil.updateViewsheet(cvs, ervs, command);
               refreshViewsheet(command, ervs);
            }
         }
         catch(MessageException ex) {
            // ignore if the vs is already closed
         }
      }
   }

   /**
    * Get removed assemblies.
    */
   private List getRemovedAssemblies(Assembly[] newArr, Assembly[] oldArr) {
      List list = new ArrayList();

      for(int i = 0; i < oldArr.length; i++) {
         String oname = oldArr[i].getName();
         boolean found = false;

         for(int j = 0; j < newArr.length; j++) {
            String nname = newArr[j].getName();

            if(oname.equals(nname)) {
               found = true;
               break;
            }
         }

         if(!found) {
            list.add(oldArr[i]);
         }
      }

      return list;
   }

   /**
    * Remove an object.
    */
   private void removeObject(RuntimeViewsheet rvs, RuntimeViewsheet pvs,
                             VSAssembly assembly, AssetCommand command) {
      if(assembly instanceof Viewsheet) {
         List<Assembly> objs = new ArrayList<>();
         VSEventUtil.listEmbeddedAssemblies((Viewsheet) assembly, objs);

         for(int i = 0; i < objs.size(); i++) {
            String name = objs.get(i).getAbsoluteName();
            RemoveVSObjectCommand rcmd = new RemoveVSObjectCommand(name);
            rcmd.setID(rvs.getEmbeddedID());
            command.addCommand(rcmd);
         }
      }

      String name = assembly.getAbsoluteName();
      RemoveVSObjectCommand rcmd = new RemoveVSObjectCommand(name);
      rcmd.setID(rvs.getEmbeddedID());
      command.addCommand(rcmd);
   }

   /**
    * Fire VSRefreshEvent, to refresh the target viewsheet.
    */
   private void refreshViewsheet(AssetCommand command, RuntimeViewsheet trvs)
      throws Exception
   {
      AssetCommand tcmd = new AssetCommand();
      String trid = trvs.getID();
      tcmd.setID(trid);
      new VSRefreshEvent().process(trvs, tcmd);
      AssetCommand icmd = null;

      // make sure the id for the command is correct
      for(int i = 0; i < tcmd.getCommandCount(); i++) {
         icmd = tcmd.getCommand(i);
         icmd.setID(trid);
         command.addCommand(icmd);
      }
   }
}
