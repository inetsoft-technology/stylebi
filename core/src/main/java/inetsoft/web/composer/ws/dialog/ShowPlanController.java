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
package inetsoft.web.composer.ws.dialog;

import inetsoft.report.composition.QueryTreeModel;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuery;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.util.Tool;
import inetsoft.web.composer.ws.WorksheetController;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class ShowPlanController extends WorksheetController {

   @RequestMapping(value = "api/composer/ws/dialog/show-plan/{runtimeid}", method = RequestMethod.GET)
   public QueryTreeModel.QueryNode showPlan(
      @PathVariable("runtimeid") String runtimeId,
      @RequestParam("table") String tname,
      Principal principal) throws Exception
   {
      RuntimeWorksheet rws = super.getWorksheetEngine()
         .getWorksheet(Tool.byteDecode(runtimeId), principal);
      tname = Tool.byteDecode(tname);
      Worksheet ws = rws.getWorksheet();
      TableAssembly table = (TableAssembly) ws.getAssembly(tname);

      if(table != null) {
         AssetQuerySandbox box = rws.getAssetQuerySandbox();
         int mode = AssetQuerySandbox.RUNTIME_MODE; // always use runtime mode
         table = (TableAssembly) table.clone();
         AssetQuery query = AssetQuery.createAssetQuery(
            table, mode, box, false, -1L, true, true);
         query.setLevel(0);

         return query.getQueryPlan();

      }

      return null;
   }
}
