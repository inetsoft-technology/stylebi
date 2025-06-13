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
package inetsoft.web.composer;

import inetsoft.report.internal.LicenseException;
import inetsoft.sree.security.*;
import inetsoft.sree.web.SessionLicenseManager;
import inetsoft.sree.web.SessionLicenseService;
import inetsoft.web.composer.model.ComposerAccessModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
public class ComposerAccessController {
   @GetMapping("/api/composerAccessCheck")
   public ComposerAccessModel checkComposerAccess(Principal principal) {
      return ComposerAccessModel.builder()
         .licensed(isLicensed(principal))
         .permitted(isPermitted(principal))
         .build();
   }

   private boolean isLicensed(Principal principal) {
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

   private boolean isPermitted(Principal principal) {
      try {
         SecurityEngine engine = SecurityEngine.getSecurity();
         return engine.checkPermission(
               principal, ResourceType.VIEWSHEET, "*", ResourceAction.ACCESS) ||
            engine.checkPermission(
               principal, ResourceType.WORKSHEET, "*", ResourceAction.ACCESS);
      }
      catch(Exception e) {
         LOG.warn("Failed to check composer permission for {}", principal, e);
         return false;
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(ComposerAccessController.class);
}
