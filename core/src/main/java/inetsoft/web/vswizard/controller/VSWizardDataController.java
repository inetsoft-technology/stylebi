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
package inetsoft.web.vswizard.controller;

import inetsoft.web.composer.model.vs.SourceChangeMessage;
import inetsoft.web.vswizard.event.UpdateVsWizardBindingEvent;
import inetsoft.web.vswizard.service.VSWizardDataServiceProxy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class VSWizardDataController {
   @Autowired
   public VSWizardDataController(VSWizardDataServiceProxy wizardDataService) {
      this.wizardDataServiceProxy = wizardDataService;
   }

   @GetMapping("/api/vswizard/binding/sourcechange")
   @ResponseBody
   public SourceChangeMessage checkSourceChanged(@RequestParam("runtimeId") String vsId,
                                                 @RequestParam("tableName") String tableName,
                                                 Principal principal)
      throws Exception
   {
      return wizardDataServiceProxy.checkSourceChanged(vsId, tableName, principal);
   }

   /**
    * Check trap for select second fields in aggregate pane.
    */
   @PostMapping("/api/vswizard/binding/aggregate/checktrap")
   public boolean aggregateCheckTrap(@RequestParam("runtimeId") String id,
                                     @RequestBody UpdateVsWizardBindingEvent event,
                                     Principal principal)
      throws Exception
   {

      return wizardDataServiceProxy.aggregateCheckTrap(id, event.getBindingModel(), principal);
   }

   private final VSWizardDataServiceProxy wizardDataServiceProxy;
}
