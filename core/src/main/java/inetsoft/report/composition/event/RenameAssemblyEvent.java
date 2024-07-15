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
package inetsoft.report.composition.event;

import inetsoft.report.composition.*;
import inetsoft.report.composition.command.MessageCommand;
import inetsoft.report.composition.command.RenameWSObjectCommand;
import inetsoft.uql.asset.*;
import inetsoft.util.Catalog;

/**
 * Rename assembly event.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class RenameAssemblyEvent extends WorksheetEvent {
   /**
    * Constructor.
    */
   public RenameAssemblyEvent() {
      super();
   }

   /**
    * Constructor.
    * @param oname old name.
    * @param nname   new name.
    */
   public RenameAssemblyEvent(String oname, String nname) {
      this();
      put("oname", oname);
      put("nname", nname);
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Rename Assembly");
   }

   /**
    * Get the influenced assemblies.
    * @return the influenced assemblies, <tt>null</tt> means all.
    */
   @Override
   public String[] getAssemblies() {
      String tname = (String) get("oname");
      return tname != null ? new String[] {tname} : new String[0];
   }

   /**
    * Process rename assembly event.
    */
   @Override
   public void process(RuntimeWorksheet rws, AssetCommand command)
      throws Exception
   {
      Worksheet ws = rws.getWorksheet();
      String oname = (String) get("oname");
      String nname = (String) get("nname");

      if(!oname.equals(nname)) {
         if(ws.renameAssembly(oname, nname, true)) {
            command.addCommand(new RenameWSObjectCommand(oname, nname));
            AssetEventUtil.refreshAssembly(rws, nname, false, command);
            Assembly assembly = ws.getAssembly(nname);

            // if we do not refresh the depending assemblies, the infos
            // might be out-of-date, then the editing process might be false
            AssemblyRef[] refs = ws.getDependings(assembly.getAssemblyEntry());

            for(int i = 0; i < refs.length; i++) {
               AssemblyEntry entry = refs[i].getEntry();

               if(entry.isWSAssembly()) {
                  AssetEventUtil.refreshAssembly(rws, entry.getName(), false,
                                                 command);
                  AssetEventUtil.refreshColumnSelection(rws, entry.getName(),
                                                        false, command);
                  AssetEventUtil.loadTableData(rws, entry.getName(), false,
                                               false, command);
               }
            }

            if(ws.getAssembly(nname) instanceof DateRangeAssembly) {
               AssetEventUtil.refreshDateRange(ws, command);
            }

            AssetEventUtil.refreshTableLastModified(ws, nname, true);
         }
         else {
            command.addCommand(new MessageCommand(
               Catalog.getCatalog().getString(
                  "common.renameAssemblyFailed")));
         }
      }
   }
}
