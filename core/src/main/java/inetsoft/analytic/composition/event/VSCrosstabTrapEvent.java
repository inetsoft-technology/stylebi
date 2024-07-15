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
import inetsoft.analytic.composition.command.SetVSTreeGrayFieldsCommand;
import inetsoft.analytic.composition.command.VSCrosstabTrapCommand;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.erm.AbstractModelTrapContext.TrapInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import inetsoft.util.Catalog;

/**
 * An event sent to check crosstab model trap when binding is changed.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class VSCrosstabTrapEvent extends ViewsheetEvent {
   /**
    * Contructor.
    */
   public VSCrosstabTrapEvent() {
      super();
   }

   /**
    * Contructor.
    */
   public VSCrosstabTrapEvent(String name, CrosstabVSAssemblyInfo oinfo,
                              CrosstabVSAssemblyInfo ninfo, boolean append)
   {
      this();
      put("name", name);
      put("oinfo", oinfo);
      put("ninfo", ninfo);
      put("append", "" + append);
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Check crosstab model trap");
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
    * Process event.
    */
   @Override
   public void process(RuntimeViewsheet rvs, AssetCommand command)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null) {
         return;
      }

      String name = (String) get("name");
      CrosstabVSAssemblyInfo oinfo = (CrosstabVSAssemblyInfo) get("oinfo");
      CrosstabVSAssemblyInfo ninfo = (CrosstabVSAssemblyInfo) get("ninfo");
      boolean append = "true".equals(get("append"));
      CrosstabVSAssembly assembly = (CrosstabVSAssembly) vs.getAssembly(name);
      CrosstabVSAssemblyInfo info =
         (CrosstabVSAssemblyInfo) assembly.getVSAssemblyInfo().clone();

      // @by larryl, we need the source to be set for the manual order items
      // to be populated
      if(info.getSourceInfo() == null) {
         info.setSourceInfo(ninfo.getSourceInfo());
      }

      info = (CrosstabVSAssemblyInfo) info.clone();

      // update info for runtime fields
      CHECK_FLAG.set(false);
      oinfo = updateAssembly(box, assembly, oinfo);
      CHECK_FLAG.set(true);
      ninfo = updateAssembly(box, assembly, ninfo);
      // roll back info
      updateAssembly(box, assembly, info);
      ninfo = ninfo == null ? info : ninfo;

      if(ninfo == null) {
         command.addCommand(new VSCrosstabTrapCommand(false, new DataRef[0],
                                                      ninfo));
         return;
      }

      VSModelTrapContext mtc = new VSModelTrapContext(rvs, true);
      boolean warning = false;
      boolean check = mtc.isCheckTrap();

      if(check) {
         TrapInfo trapInfo = mtc.checkTrap(oinfo, ninfo);
         warning = trapInfo.showWarning();
      }

      DataRef[] fields = check ? mtc.getGrayedFields() : new DataRef[0];

      if(ninfo != null && ninfo.getVSCrosstabInfo() != null) {
         ninfo.getVSCrosstabInfo().updateRuntimeID(false);
      }

      command.addCommand(new VSCrosstabTrapCommand(warning, fields, ninfo));
      command.addCommand(new SetVSTreeGrayFieldsCommand(fields));
   }

   /*
    * Update assembly info.
    */
   private CrosstabVSAssemblyInfo updateAssembly(ViewsheetSandbox box,
      CrosstabVSAssembly assembly, CrosstabVSAssemblyInfo info)
   {
      if(info == null) {
         return null;
      }

      assembly.setVSAssemblyInfo(info);

      try {
         box.updateAssembly(assembly.getAbsoluteName());
      }
      catch(Exception e) {
         // ignore it
      }

      return (CrosstabVSAssemblyInfo) assembly.getVSAssemblyInfo().clone();
   }

   public static final  ThreadLocal<Boolean> CHECK_FLAG = new ThreadLocal() {
      @Override
      protected Boolean initialValue() {
         return Boolean.TRUE;
      }
   };
}
