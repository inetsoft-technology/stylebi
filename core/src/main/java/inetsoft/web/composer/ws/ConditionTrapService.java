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
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AbstractModelTrapContext;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.condition.ConditionUtil;
import inetsoft.web.composer.ws.assembly.ConditionTrapModel;
import inetsoft.web.composer.ws.assembly.ConditionTrapValidator;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Arrays;

@Service
@ClusterProxy
public class ConditionTrapService extends WorksheetControllerService {
   public ConditionTrapService(ViewsheetService viewsheetService,
                               DataRefModelFactoryService dataRefModelFactoryService) {
      super(viewsheetService);
      this.dataRefModelFactoryService = dataRefModelFactoryService;
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ConditionTrapValidator checkConditionTrap(
      @ClusterProxyKey String runtimeId,
      ConditionTrapModel model,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeWorksheet rws = super.getWorksheetEngine().getWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      TableAssembly table = (TableAssembly) ws.getAssembly(model.tableName());

      if(table != null) {
         SourceInfo sourceInfo = table instanceof BoundTableAssembly ?
            ((BoundTableAssembly) table).getSourceInfo() : null;
         ConditionList oldConditionList = ConditionUtil.fromModelToConditionList(
            model.oldConditionList(), sourceInfo, viewsheetService, principal, rws);
         ConditionList newConditionList = ConditionUtil.fromModelToConditionList(
            model.newConditionList(), sourceInfo, viewsheetService, principal, rws);

         TableAssembly otable = (TableAssembly) table.clone();
         otable.setPreConditionList(oldConditionList);
         WSModelTrapContext mtc =
            new WSModelTrapContext(otable, ThreadContext.getContextPrincipal());

         if(mtc.isCheckTrap()) {
            TableAssembly ntable = (TableAssembly) table.clone();
            ntable.setPreConditionList(newConditionList);
            AbstractModelTrapContext.TrapInfo info = mtc.checkTrap(otable, ntable);
            boolean trap = info.showWarning();
            DataRefModel[] trapFields = Arrays.stream(mtc.getGrayedFields())
               .map(this.dataRefModelFactoryService::createDataRefModel)
               .toArray(DataRefModel[]::new);

            return ConditionTrapValidator.builder()
               .trapFields(trapFields)
               .showTrap(trap)
               .build();
         }
      }

      return null;
   }


   private final DataRefModelFactoryService dataRefModelFactoryService;
   private final ViewsheetService viewsheetService;
}
