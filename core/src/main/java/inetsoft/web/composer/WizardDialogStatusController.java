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
package inetsoft.web.composer;

import inetsoft.sree.UserEnv;
import inetsoft.web.composer.model.WizardDialogStatusModel;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Controller that processes requests to show a new viewsheet or new worksheet dialog.
 */
@RestController
public class WizardDialogStatusController {
   public WizardDialogStatusController() {
   }

   @PostMapping("/api/composer/wizard/status")
   public void setWizardDialogStatus(@RequestBody WizardDialogStatusModel model,
                                     Principal principal)
      throws Exception
   {
      if(model.getViewsheetWizardStatus() != null) {
         UserEnv.setProperty(principal,"vswizard.dialog.status", model.getViewsheetWizardStatus());
      }

      if(model.getWorksheetWizardStatus() != null) {
         UserEnv.setProperty(principal, "wswizard.dialog.status", model.getWorksheetWizardStatus());
      }
   }

   @GetMapping("/api/composer/wizard/status")
   public WizardDialogStatusModel getWizardDialogStatus(Principal principal) {
      return new WizardDialogStatusModel(
         (String) UserEnv.getProperty(principal, "vswizard.dialog.status"),
         (String) UserEnv.getProperty(principal, "wswizard.dialog.status"));
   }
}
