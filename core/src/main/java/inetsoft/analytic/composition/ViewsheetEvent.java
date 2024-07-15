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
package inetsoft.analytic.composition;

import inetsoft.analytic.composition.command.PendingCommand;
import inetsoft.analytic.composition.command.SetViewsheetInfoCommand;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.*;
import inetsoft.report.composition.command.InitGridCommand;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.GroupedThread;
import inetsoft.util.Tool;
import inetsoft.util.log.LogContext;

import java.awt.*;
import java.util.Date;
import java.util.List;

/**
 * Viewsheet event, the <tt>AssetEvent</tt> requires a viewsheet as the context.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class ViewsheetEvent extends GridEvent {
   /**
    * Constructor.
    */
   public ViewsheetEvent() {
      super();
   }

   /**
    * Get the name of the sheet container.
    * @return the name of the sheet container.
    */
   @Override
   public String getSheetName() {
      String name = null;

      try {
         ViewsheetService engine = getViewsheetEngine();
         RuntimeSheet runtime = engine.getViewsheet(getID(), getUser());
         AssetEntry entry = runtime == null ? null : runtime.getEntry();
         name = entry == null ? null : entry.getSheetName();
      }
      catch(Exception ex) {
      }

      return name;
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      ViewsheetEvent event2 = (ViewsheetEvent) obj;

      // for some event which may be added into comfirm command
      if(!Tool.equals(event2.get("name"), get("name"))) {
         return false;
      }

      String id = getID();
      String id2 = event2.getID();

      return Tool.equals(id, id2);
   }

   /**
    * Get the hash code.
    * @return the hash code of the asset event.
    */
   public int hashCode() {
      int hash = super.hashCode();
      String id = getID();

      if(id != null) {
         hash = hash ^ id.hashCode();
      }

      return hash;
   }

   /**
    * Get the viewsheet engine.
    * @return the viewsheet engine.
    */
   public ViewsheetService getViewsheetEngine() {
      WorksheetService engine = getWorksheetEngine();
      return (ViewsheetService) engine;
   }

   /**
    * Get the asset repository.
    * @return the asset repository.
    */
   @Override
   public AssetRepository getAssetRepository() {
      return getViewsheetEngine().getAssetRepository();
   }

   /**
    * Create a changed assembly list.
    * @param breakable <tt>true</tt> if the assembly list is breakable,
    * <tt>false</tt> otherwise.
    * @param command the specified command container.
    * @param rvs the specified runtime viewsheet.
    * @param uri the specified uri.
    */
   public static final ChangedAssemblyList createList(boolean breakable,
                                                      AssetEvent event,
                                                      AssetCommand command,
                                                      RuntimeViewsheet rvs,
                                                      String uri) {
      ChangedAssemblyList clist = new ChangedAssemblyList(breakable);
      ChangedAssemblyList.ReadyListener rlistener =
         createReadyListener(clist, event, command, rvs, uri);
      clist.setReadyListener(rlistener);

      return clist;
   }

   /**
    * Create a ready listener.
    */
   private static ReadyListener createReadyListener(ChangedAssemblyList clist,
                                                    AssetEvent event,
                                                    AssetCommand cmd,
                                                    RuntimeViewsheet rvs,
                                                    String uri) {
      return new ReadyListener(clist, event, cmd, rvs, uri);
   }

   /**
    * Get the assembly which will load data in this event.
    */
   protected String getAssembly() {
      String name = (String) get("name");

      if(name != null && name.length() > 0) {
         return name;
      }

      try {
         VSAssemblyInfo info = (VSAssemblyInfo) get("info");
         name = info.getAbsoluteName2();

         if(name != null && name.length() > 0) {
            return name;
         }
      }
      catch(Exception ex) {
         // ignore it
      }

      return null;
   }

   /**
    * Process this viewsheet event.
    * @param rvs the specified runtime sheet as the context.
    * @param command the specified command container.
    */
   @Override
   public void process(RuntimeSheet rvs, AssetCommand command)
      throws Exception
   {
      AssetEvent.MAIN.set(this);
      AssetEntry entry = rvs == null ? null : rvs.getEntry();

      if(entry != null && (Thread.currentThread() instanceof GroupedThread)) {
         ((GroupedThread) Thread.currentThread())
            .addRecord(LogContext.DASHBOARD, entry.getPath());
      }

      boolean confirmed = isConfirmed();
      String assembly = getAssembly();

      // maintain time limited here. The sub class could override
      // the method getAssembly() to turn on/off this function
      if(confirmed && assembly != null) {
         RuntimeViewsheet vs = (RuntimeViewsheet) rvs;
         ViewsheetSandbox box = vs.getViewsheetSandbox();

         if(box != null) {
            box.setTimeLimited(assembly, false);

            if(get("associatedSelections") != null) {
               String[] names =
                  ((String) get("associatedSelections")).split(",");

               for(int i = 0; i < names.length; i++) {
                  if(!"".equals(names[i])) {
                     box.setTimeLimited(names[i], false);
                  }
               }
            }
         }
      }

      process((RuntimeViewsheet) rvs, command);

      if("true".equals(get("isConfirmEvent"))) {
         try {
            ViewsheetService engine = getViewsheetEngine();
            RuntimeSheet runtime = engine.getViewsheet(getID(), getUser());
            RuntimeViewsheet runvs = (RuntimeViewsheet) runtime;
            ViewsheetSandbox box = runvs.getViewsheetSandbox();
            ViewsheetScope scope = box.getScope();
            scope.execute("confirmEvent.confirmed = false",
               ViewsheetScope.VIEWSHEET_SCRIPTABLE);
          }
          catch(Exception e) {
          }
      }
   }

   /**
    * Process this viewsheet event.
    * @param rvs the specified runtime viewsheet as the context.
    * @param command the specified command container.
    */
   public abstract void process(RuntimeViewsheet rvs, AssetCommand command)
      throws Exception;

   /**
    * Ready listener.
    */
   private static class ReadyListener
      implements ChangedAssemblyList.ReadyListener
   {
      /**
       * Create a ready listener.
       */
      public ReadyListener(ChangedAssemblyList clist, AssetEvent event,
                           AssetCommand cmd, RuntimeViewsheet rvs, String uri) {
         super();
         this.clist = clist;
         this.event = event;
         this.cmd = cmd;
         this.rvs = rvs;
         this.uri = uri;
      }

      /**
       * Get the changed assembly list.
       * @return the changed assembly list.
       */
      public ChangedAssemblyList getList() {
         return clist;
      }

      /**
       * Get the asset command.
       * @return the asset command.
       */
      public AssetCommand getCommand() {
         return cmd;
      }

      /**
       * Set the runtime sheet.
       * @param rs the specified runtime sheet.
       */
      @Override
      public void setRuntimeSheet(RuntimeSheet rs) {
         this.rvs = (RuntimeViewsheet) rs;
      }

      /**
       * Get the runtime sheet.
       * @return the runtime sheet if any, <tt>null</tt> otherwise.
       */
      @Override
      public RuntimeSheet getRuntimeSheet() {
         return this.rvs;
      }

      /**
       * Set whether to initialize grid.
       * @param grid <tt>true</tt> to initialize grid, <tt>false</tt> otherwise.
       */
      @Override
      public void setInitingGrid(boolean grid) {
         this.grid = grid;
      }

      /**
       * Check if should initing grid.
       * @return <tt>true</tt> if should initing grid, <tt>false</tt> otherwise.
       */
      @Override
      public boolean isInitingGrid() {
         return grid;
      }

      /**
       * Get the viewsheet id.
       * @return the viewsheet id of the list.
       */
      @Override
      public String getID() {
         return id;
      }

      /**
       * Set the viewsheet id to the list.
       * @param id the specified viewsheet id.
       */
      @Override
      public void setID(String id) {
         this.id = id;
      }

      /**
       * Triggered when more assembly gets ready.
       */
      @Override
      public void onReady() throws Exception {
         try {
            onReady0();
         }
         catch(Exception ex) {
            // ignore if the runtime viewsheet has been disposed
            if(rvs != null && rvs.getViewsheet() != null) {
               throw ex;
            }
         }
      }

      private void onReady0() throws Exception {
         if(!inited) {
            Viewsheet sheet = rvs.getViewsheet();

            if(grid && sheet != null) {
               sheet.setLastSize(sheet.getPixelSize());
               // @damianwysocki, Bug #9543
               // Removed grid, so set a default value for now but this is an old command
               // that should eventually be removed
               int[] cols = new int[0];
               int[] rows = new int[0];

               InitGridCommand icmd = new InitGridCommand(id, cols, rows,
                                                          rvs.getEntry(),
                                                          null);
               long modified = sheet.getLastModified();
               Date date = new Date(modified);
               icmd.setIniting(false);
               icmd.setEditable(rvs.isEditable());
               icmd.setLockOwner(rvs.getLockOwner());

               // @by stephenwebster, fix bug1394233595834
               // We should initialize the view size, otherwise this causes a
               // problem when scaleToScreen option is on in the Viewsheet.
               // mimics code in VSEventUtil
               Dimension viewSize = sheet.getInfo().getPixelSize();

               if(event.get("viewWidth") != null && event.get("viewHeight") != null) {
                  viewSize = new Dimension(
                     Integer.parseInt((String) event.get("viewWidth")),
                     Integer.parseInt((String) event.get("viewHeight")));
               }

               icmd.setViewSize(viewSize);
               cmd.addCommand(icmd);
               cmd.addCommand(new SetViewsheetInfoCommand(rvs));
            }

            inited = true;
         }

         List ready = clist.getReadyList();
         List processed = clist.getProcessedList();
         List pending = clist.getPendingList();
         int count = ready.size();

         for(int i = 0; i < count; i++) {
            AssemblyEntry entry = (AssemblyEntry) ready.remove(0);
            process(entry);

            if(!processed.contains(entry)) {
               processed.add(entry);
            }

            pending.remove(entry);
         }

         count = pending.size();

         for(int i = 0; i < count; i++) {
            AssemblyEntry entry = (AssemblyEntry) pending.remove(0);
            String vname = entry.getAbsoluteName();
            Viewsheet vs = rvs.getViewsheet();
            VSAssembly assembly = (VSAssembly) vs.getAssembly(vname);
            // @by: ChrisSpagnoli bug1412261632374 #2 2014-10-10
            // It is possible for client to send VSLayoutEvent with an assembly
            // which has since been deleted.  So, check for null before proceeding.
            if(assembly != null) {
               VSEventUtil.addDeleteVSObject(rvs, event, assembly, uri, cmd);
               AssetCommand pcommand = new PendingCommand(vname);
               cmd.addCommand(pcommand);
            }
         }

         cmd.fireEvent();
      }

      /**
       * Process an assembly.
       * @param entry the specified assembly.
       */
      private void process(AssemblyEntry entry) throws Exception {
         if(rvs == null || rvs.isDisposed()) {
            return;
         }

         Viewsheet vs = rvs.getViewsheet();
         ViewsheetSandbox box = rvs.getViewsheetSandbox();
         String vname = entry.getAbsoluteName();
         VSAssembly assembly = (VSAssembly) vs.getAssembly(vname);
         box.executeView(entry.getAbsoluteName(), false);

         // @by billh, performance optimization, make sure that
         // AddVSObjectCommand and RefreshVSObjectCommand are bundled
         // together, so that RefreshVSObjectCommand could be removed
         // when compacting this AssetCommand to reduce traffic load
         synchronized(cmd) {
            VSEventUtil.refreshData(rvs, cmd, entry, uri);
            VSEventUtil.addDeleteVSObject(rvs, event, assembly, uri, cmd);
            VSEventUtil.loadTableLens(rvs, event, vname, uri, cmd);
         }
      }

      private ChangedAssemblyList clist;
      private AssetCommand cmd;
      private AssetEvent event;
      private RuntimeViewsheet rvs;
      private boolean grid;
      private boolean inited;
      private String id;
      private String uri;
   }
}
