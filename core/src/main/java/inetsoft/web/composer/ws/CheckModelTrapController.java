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
package inetsoft.web.composer.ws;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WSModelTrapContext;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AbstractModelTrapContext;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.ws.CheckModelTrapModel;
import inetsoft.web.composer.ws.event.CheckModelTrapEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Arrays;

import static inetsoft.web.composer.ws.dialog.AggregateDialogController.getAggregateInfo;

@Controller
public class CheckModelTrapController extends WorksheetController {
   @Autowired
   public CheckModelTrapController(DataRefModelFactoryService dataRefModelFactoryService) {
      this.dataRefModelFactoryService = dataRefModelFactoryService;
   }

   /**
    * From {@link inetsoft.report.composition.event.CheckModelTrapEvent}
    */
   @RequestMapping(
      value = "api/composer/worksheet/check-model-trap/{runtimeId}",
      method = RequestMethod.POST)
   @ResponseBody
   public CheckModelTrapModel checkModelTrap(
      @RequestBody CheckModelTrapEvent event, @PathVariable("runtimeId") String runtimeId,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeWorksheet rws =
         super.getWorksheetEngine().getWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      String tname = event.tableName();
      TableAssembly table = (TableAssembly) ws.getAssembly(tname);
      ColumnSelection columnSelection = table.getColumnSelection();
      AggregateInfo agg = getAggregateInfo(event.newAggregateInfo(), columnSelection);
      AggregateInfo oagg = getAggregateInfo(event.oldAggregateInfo(), columnSelection);
      boolean isAgg = !agg.isEmpty();

      WSModelTrapContext mtc = new WSModelTrapContext(
         table, ThreadContext.getContextPrincipal(), isAgg, true);

      if(mtc.isCheckTrap()) {
         TableAssembly otable = (TableAssembly) table.clone();
         otable.setAggregate(true);
         otable.setAggregateInfo(oagg);
         TableAssembly ntable = (TableAssembly) table.clone();
         ntable.setAggregate(true);
         ntable.setAggregateInfo(agg);
         AbstractModelTrapContext.TrapInfo oinfo = mtc.checkTrap(null, otable);
         AbstractModelTrapContext.TrapInfo ninfo = mtc.checkTrap(null, ntable);
         boolean contains = oinfo != null && oinfo.showWarning() ? false :
            ninfo != null && ninfo.showWarning();
         WSModelTrapContext gmtc =
            new WSModelTrapContext(table, ThreadContext.getContextPrincipal(), true, true);
         gmtc.checkTrap(otable, ntable);

         ColumnRefModel[] columnRefs = Arrays.stream(gmtc.getGrayedFields())
            .map(this.dataRefModelFactoryService::createDataRefModel)
            .toArray(ColumnRefModel[]::new);

         return CheckModelTrapModel.builder()
            .trapFields(columnRefs)
            .containsTrap(contains)
            .build();
      }
      else {
         return CheckModelTrapModel.builder().build();
      }
   }

   private final DataRefModelFactoryService dataRefModelFactoryService;
}
