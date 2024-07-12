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
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import inetsoft.web.vswizard.event.RefreshVSWizardEvent;
import inetsoft.web.vswizard.service.WizardViewsheetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class WizardPaneController {
   @Autowired
   public WizardPaneController(ViewsheetService viewsheetService,
                               WizardViewsheetService wizardViewsheetService)
   {
      this.viewsheetService = viewsheetService;
      this.wizardViewsheetService = wizardViewsheetService;
   }

   @MessageMapping("/composer/vswizard/wizard-pane/refresh")
   public void refreshWizardViewsheet(@Payload RefreshVSWizardEvent event, Principal principal,
                                      @LinkUri String linkUri,
                                      CommandDispatcher commandDispatcher)
      throws Exception
   {
      String vsId = event.getRuntimeId();

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      box.lockRead();

      try {
         this.wizardViewsheetService.refreshWizardViewsheet(vsId, linkUri, principal,
                                                            commandDispatcher);

         if(!event.isFormGridPane()) {
            return;
         }

         this.wizardViewsheetService.updateGridRowsAndNewBlock(vs.getAssemblies(),
                                                               commandDispatcher);
      }
      finally {
         box.unlockRead();
      }
   }

   private final ViewsheetService viewsheetService;
   private final WizardViewsheetService wizardViewsheetService;
}