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
package inetsoft.web.composer.ws;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.asset.delete.*;
import inetsoft.uql.asset.sync.RenameInfo;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSRemoveAssembliesEvent;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@Controller
public class WSRemoveAssembliesController extends WorksheetController {
   @PostMapping("/api/composer/worksheet/remove-assemblies/check-dependency/**")
   @ResponseBody
   public String hasSourceDependency(@RemainingPath String rid,
                                     @RequestParam(value = "all", required = false) boolean all,
                                     @RequestBody WSRemoveAssembliesEvent event,
                                     Principal principal) throws Exception
   {
      WorksheetService engine = getWorksheetEngine();
      RuntimeWorksheet rws = engine.getWorksheet(rid, principal);
      String source = rws.getEntry().toIdentifier();
      List<DeleteInfo> dinfo = new ArrayList<>();
      String[] tables = event.assemblyNames();
      Worksheet ws = rws.getWorksheet();
      String primary = ws.getPrimaryAssemblyName();

      for(int i = 0; i < tables.length; i++) {
         DeleteInfo info = new DeleteInfo(tables[i],
           RenameInfo.ASSET | RenameInfo.TABLE, source, tables[i]);

         if(Tool.equals(primary, tables[i])) {
            info.setPrimary(true);
         }

         dinfo.add(info);
      }

      DeleteDependencyInfo info = DeleteDependencyHandler.createWsDependencyInfo(dinfo, rws);

      if(all) {
         return DeleteDependencyHandler.checkDependencyStatus(info);
      }
      else {
         return DeleteDependencyHandler.hasDependency(info).toString();
      }
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/remove-assemblies")
   public void removeAssemblies(
      @Payload WSRemoveAssembliesEvent event,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String[] names = event.assemblyNames();
      Set<String> nameset = new HashSet<>();

      // build nameset
      for(int i = 0; i < names.length; i++) {
         nameset.add(names[i]);
      }

      for(int i = 0; i < names.length; i++) {
         Assembly assembly = ws.getAssembly(names[i]);

         if(assembly != null) {
            if(AssetEventUtil.hasDependent(assembly, ws, nameset)) {
               MessageCommand command = new MessageCommand();
               command.setMessage(Catalog.getCatalog().
                  getString("assembly.remove.dependency"));
               command.setType(MessageCommand.Type.WARNING);
               command.setAssemblyName(assembly.getName());
               commandDispatcher.sendCommand(command);
               continue;
            }

            WorksheetEventUtil.removeAssembly(rws, assembly, commandDispatcher);
         }
      }
   }
}
