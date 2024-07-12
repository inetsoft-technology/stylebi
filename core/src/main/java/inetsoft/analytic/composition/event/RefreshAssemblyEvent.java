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
package inetsoft.analytic.composition.event;

import inetsoft.analytic.composition.ViewsheetEvent;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Catalog;

/**
 * Refresh (execute) an assembly event.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class RefreshAssemblyEvent extends ViewsheetEvent {
   /**
    * Constructor.
    */
   public RefreshAssemblyEvent() {
      super();
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Refresh Assembly");
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
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      ChangedAssemblyList clist =
         createList(true, this, command, rvs, getLinkURI());
      String name = (String) get("name");
      VSAssembly assembly = (VSAssembly) vs.getAssembly(name);

      VSEventUtil.addDeleteVSObject(rvs, this, assembly, getLinkURI(), command);
      VSEventUtil.loadTableLens(rvs, this, name, getLinkURI(), command);

      // for the tiem slider and calender assembly, it needs to process
      // selections in order to get data for the assembly
      if(assembly instanceof TimeSliderVSAssembly ||
         assembly instanceof CalendarVSAssembly ||
         assembly instanceof OutputVSAssembly)
      {
         ViewsheetSandbox box = rvs.getViewsheetSandbox();

         if(box == null) {
            return;
         }

         box.processChange(name, VSAssembly.INPUT_DATA_CHANGED, clist);
      }

      VSEventUtil.refreshVSAssembly(rvs, name, command);
   }
}
