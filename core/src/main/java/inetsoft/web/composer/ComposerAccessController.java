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
package inetsoft.web.composer;

import inetsoft.report.internal.LicenseException;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.web.SessionLicenseManager;
import inetsoft.sree.web.SessionLicenseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
public class ComposerAccessController {
   @GetMapping("api/composerLicenseCheck")
   public boolean checkComposerLicense(Principal principal) throws Exception {
      SessionLicenseManager manager = SessionLicenseService.getViewerLicenseService();

      if(manager != null) {
         try {
            manager.newSession((SRPrincipal) principal);
         }
         catch(LicenseException le) {
            LOG.error("Invalid viewer license", le);

            return false;
         }
      }

      return true;
   }

   private static final Logger LOG = LoggerFactory.getLogger(ComposerAccessController.class);
}
