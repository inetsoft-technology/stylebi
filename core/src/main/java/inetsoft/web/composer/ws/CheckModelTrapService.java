/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.composer.ws;


import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AbstractModelTrapContext;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.ws.CheckModelTrapModel;
import inetsoft.web.composer.ws.event.CheckModelTrapEvent;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Arrays;

import static inetsoft.web.composer.ws.dialog.AggregateDialogService.getAggregateInfo;


@Service
@ClusterProxy
public class CheckModelTrapService extends WorksheetControllerService {
   public CheckModelTrapService(ViewsheetService viewsheetService,
                                DataRefModelFactoryService dataRefModelFactoryService)
   {
      super(viewsheetService);
      this.dataRefModelFactoryService = dataRefModelFactoryService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public CheckModelTrapModel checkModelTrap(
      @ClusterProxyKey String runtimeId,
      CheckModelTrapEvent event,
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
