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
package inetsoft.web.composer.ws;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WSModelTrapContext;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Arrays;

/**
 * Controller for condition traps
 */
@Controller
public class ConditionTrapController extends WorksheetController {
   @Autowired
   public ConditionTrapController(
      DataRefModelFactoryService dataRefModelFactoryService,
      ViewsheetService viewsheetService) {
      this.dataRefModelFactoryService = dataRefModelFactoryService;
      this.viewsheetService = viewsheetService;
   }

   /**
    * Checks whether a new condition list will cause a trap.
    * Also finds the trap-causing columns for a given condition list.
    *
    * @param model     the model containing the old and new condition lists
    * @param runtimeId the runtime id
    * @param principal the user principal
    *
    * @return the condition trap validator if there is one, null otherwise
    */
   @PostMapping("/api/composer/worksheet/check-condition-trap/{runtimeId}")
   @ResponseBody
   public ConditionTrapValidator checkConditionTrap(
      @RequestBody() ConditionTrapModel model, @PathVariable("runtimeId") String runtimeId,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeWorksheet rws = super.getWorksheetEngine().getWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      TableAssembly table = (TableAssembly) ws.getAssembly(model.tableName());

      if(table != null) {
         SourceInfo sourceInfo = table instanceof BoundTableAssembly ?
            ((BoundTableAssembly) table).getSourceInfo() : null;

         Tool.useDatetimeWithMillisFormat.set(
            Tool.isDatabricks(sourceInfo == null ? null : sourceInfo.getSource()));
         ConditionList oldConditionList = null;
         ConditionList newConditionList = null;

         try {
            oldConditionList = ConditionUtil.fromModelToConditionList(
               model.oldConditionList(), sourceInfo, viewsheetService, principal, rws);
            newConditionList = ConditionUtil.fromModelToConditionList(
               model.newConditionList(), sourceInfo, viewsheetService, principal, rws);
         }
         finally {
            Tool.useDatetimeWithMillisFormat.set(false);
         }

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
