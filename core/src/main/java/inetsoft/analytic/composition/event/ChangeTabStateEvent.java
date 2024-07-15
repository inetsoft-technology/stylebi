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
package inetsoft.analytic.composition.event;

import inetsoft.analytic.composition.ViewsheetEvent;
import inetsoft.report.composition.AssetCommand;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.command.MessageCommand;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.util.Catalog;

import java.util.ArrayList;

/**
 * Change vstab object state event.
 *
 * @version 8.5, 08/01/2006
 * @author InetSoft Technology Corp
 */
public class ChangeTabStateEvent extends ViewsheetEvent {
   /**
    * Constructor.
    */
   public ChangeTabStateEvent() {
      super();
   }

   /**
    * Constructor.
    */
   public ChangeTabStateEvent(String tabName, String compName) {
      this();
      put("tabName", tabName);
      put("compName", compName);
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Change VSTab State");
   }

   /**
    * Check if is undoable/redoable.
    * @return <tt>true</tt> if undoable/redoable.
    */
   @Override
   public boolean isUndoable() {
      return true;
   }

   /**
    * Get the influenced assemblies.
    * @return the influenced assemblies, <tt>null</tt> means all.
    */
   @Override
   public String[] getAssemblies() {
      String name = (String) get("tabName");
      return name != null ? new String[] {name} : new String[0];
   }

   /**
    * Check if requires return.
    * @return <tt>true</tt> if requires, <tt>false</tt> otherwise.
    */
   @Override
   public boolean requiresReturn() {
      return false;
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

      String name = (String) get("tabName");
      String compName = (String) get("compName");
      int dot = compName.lastIndexOf('.');
      String compName0 = (dot > 0) ? compName.substring(dot + 1) : compName;
      VSAssembly tassembly = (VSAssembly) vs.getAssembly(name);
      String uri = getLinkURI();

      if(tassembly != null) {
         ((TabVSAssemblyInfo) tassembly.getVSAssemblyInfo()).
            setSelectedValue(compName0);
         VSEventUtil.execute(rvs, this, name, uri,
            VSAssembly.VIEW_CHANGED, command);
         Viewsheet vs2 = tassembly.getViewsheet();
         VSAssembly assembly = (VSAssembly) vs.getAssembly(compName);

         if(VSEventUtil.isVisibleTabVS(assembly)) {
            VSEventUtil.addDeleteVSObject(rvs, this, assembly, uri, command,
                                          true);
         }
         else {
            if(tassembly.isEmbedded() &&
               (assembly instanceof ContainerVSAssembly))
            {
               ContainerVSAssembly cassembly = (ContainerVSAssembly) assembly;
               String[] names = cassembly.getAssemblies();

               for(int i = 0; i < names.length; i++) {
                  VSAssembly sub = (VSAssembly) vs2.getAssembly(names[i]);

                  if(sub != null) {
                     VSEventUtil.refreshVSAssembly(rvs, sub, command);
                  }
               }

               VSEventUtil.fixSize(assembly, rvs.getViewsheetSandbox(),
				   new ArrayList());
               VSEventUtil.refreshVSAssembly(rvs, assembly, command);
            }

            VSEventUtil.refreshVSAssembly(rvs, compName, command, true);
         }
      }

      command.addCommand(new MessageCommand("", MessageCommand.OK));
   }
}
