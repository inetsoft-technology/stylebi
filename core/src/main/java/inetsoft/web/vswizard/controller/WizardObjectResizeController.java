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
package inetsoft.web.vswizard.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.web.composer.vs.objects.event.ResizeVSObjectEvent;
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
public class WizardObjectResizeController {
   @Autowired
   public WizardObjectResizeController(RuntimeViewsheetRef runtimeViewsheetRef,
                                       ViewsheetService engine,
                                       WizardVSObjectService wizardVSObjectService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.engine = engine;
      this.wizardVSObjectService = wizardVSObjectService;
   }

   @Undoable
   @MessageMapping("/composer/vswizard/object/resize")
   public void resizeObject(@Payload  ResizeVSObjectEvent event, @LinkUri String linkUri,
                            Principal principal, CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = engine.getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);

      if(rvs == null) {
         return;
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockRead();

      try {
         this.wizardVSObjectService.resizeVSObject(rvs, event, linkUri, principal, commandDispatcher);
      }
      finally {
         box.unlockRead();
      }
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ViewsheetService engine;
   private final WizardVSObjectService wizardVSObjectService;
}
