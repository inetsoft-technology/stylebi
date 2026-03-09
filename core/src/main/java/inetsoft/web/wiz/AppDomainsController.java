/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.XPrincipal;
import inetsoft.util.Tool;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * {@code AppDomainsController} provides the web API endpoints for getting/setting organization application domains.
 */
@RestController
@Validated
public class AppDomainsController {
   /**
    * Get application domains
    *
    * @param user a principal that identifies the remote user.
    *
    * @return the organization application domains.
    *
    * @since 2025
    */
   @GetMapping(value = "/api/appDomains", produces = MediaType.APPLICATION_JSON_VALUE)
   public OrganizationDomains getAppDomains(Principal user) throws Exception {
      String value = SreeEnv.getProperty(getOrgAppDomainPropKey(user), false, true);

      if(Tool.isEmptyString(value)) {
         return null;
      }

      OrganizationDomains domains = new OrganizationDomains();
      domains.parseFromProperty(value);
      return domains;
   }

   /**
    * Set application domains
    *
    * @param user a principal that identifies the remote user.
    *
    * @since 2025
    */
   @PostMapping(value = "/api/appDomains", produces = MediaType.APPLICATION_JSON_VALUE)
   public void setAppDomains(@RequestBody OrganizationDomains appDomains,
                             Principal user) throws Exception
   {
      String value = appDomains.toString();
      SreeEnv.setProperty(getOrgAppDomainPropKey(user), value, true);
   }

   private String getOrgAppDomainPropKey(Principal user) {
      if(user != null && ((XPrincipal) user).getOrgId() != null) {
         return Tool.buildString(APP_DOMAIN, ".", ((XPrincipal) user).getOrgId());
      }

      return APP_DOMAIN;
   }

   private final static String APP_DOMAIN = "app.domains";
}