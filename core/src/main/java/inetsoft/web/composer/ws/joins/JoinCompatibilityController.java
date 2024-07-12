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
package inetsoft.web.composer.ws.joins;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.*;
import inetsoft.util.Tool;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;

@Controller
public class JoinCompatibilityController extends WorksheetController {
   @RequestMapping(
      value = "api/composer/worksheet/join/compatible-insertion-tables/{runtimeId}",
      method = RequestMethod.GET)
   @ResponseBody
   public ArrayList<String> getCompatibleInsertionTables(
      @PathVariable("runtimeId") String runtimeId, @RequestParam("joinTable") String joinTableName,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      final RuntimeWorksheet rws = getWorksheetEngine().getWorksheet(runtimeId, principal);
      final Worksheet ws = rws.getWorksheet();
      final AbstractJoinTableAssembly joinTable =
         (AbstractJoinTableAssembly) ws.getAssembly(joinTableName);
      final ArrayList<String> validTables = new ArrayList<>();
      final Assembly[] assemblies = ws.getAssemblies();

      for(Assembly assembly : assemblies) {
         if(joinTable == assembly || !(assembly instanceof TableAssembly)) {
            continue;
         }

         if(joinTable.getTableAssembly(assembly.getAbsoluteName()) != null) {
            continue;
         }

         if(!WorksheetEventUtil.checkCyclicalDependency(
            ws, joinTable, (TableAssembly) assembly))
         {
            validTables.add(assembly.getAbsoluteName());
         }
      }

      return validTables;
   }
}
