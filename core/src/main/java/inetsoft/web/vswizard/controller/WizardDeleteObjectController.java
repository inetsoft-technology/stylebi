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
package inetsoft.web.vswizard.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.web.composer.vs.objects.event.RemoveVSObjectsEvent;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import inetsoft.web.vswizard.service.WizardVSObjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class WizardDeleteObjectController {
   @Autowired
   public WizardDeleteObjectController(RuntimeViewsheetRef runtimeViewsheetRef,
                                       ViewsheetService engine,
                                       WizardVSObjectService wizardVsObjectService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.engine = engine;
      this.wizardVsObjectService = wizardVsObjectService;
   }

   /**
    * Add new object to the composer.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param commandDispatcher the command dispatcher.
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @Undoable
   @MessageMapping("/composer/vswizard/object/remove")
   public void removeObject(@Payload RemoveVSObjectsEvent event, @LinkUri String linkUri,
                            Principal principal, CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = engine.getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);

      if(rvs == null) {
         return;
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockWrite();

      try {
         this.wizardVsObjectService.deleteVSObject(rvs, event, linkUri, principal, commandDispatcher);
      }
      finally {
         box.unlockWrite();
      }
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ViewsheetService engine;
   private final WizardVSObjectService wizardVsObjectService;
}
