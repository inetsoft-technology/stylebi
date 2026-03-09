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
package inetsoft.web.wiz.controller;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.XPrincipal;
import inetsoft.util.Tool;
import inetsoft.web.wiz.OrganizationDomains;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * {@code AppDomainsController} provides the web API endpoints for getting/setting organization application domains.
 */
@RestController
@Tag(
   name = "Application Domains",
   description = "The Application domains APIs allow you to get/set organization application domains.")
@Validated
@SecurityRequirement(name = "api-token")
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
   @GetMapping(value = "/api/public/appDomains", produces = MediaType.APPLICATION_JSON_VALUE)
   @Operation(
      summary = "Get application domains",
      description = "Get organization application domains.",
      extensions = @Extension(properties = @ExtensionProperty(name = "x-since", value = "1.0")))
   @ApiResponses({
      @ApiResponse(
         responseCode = "200",
         description = "Get organization application domains..")
   })
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
   @PostMapping(value = "/api/public/appDomains", produces = MediaType.APPLICATION_JSON_VALUE)
   @Operation(
      summary = "Set application domains",
      description = "Set organization application domains.",
      extensions = @Extension(properties = @ExtensionProperty(name = "x-since", value = "1.0")))
   @ApiResponses({
      @ApiResponse(
         responseCode = "200",
         description = "Get organization application domains.")
   })
   public void setAppDomains(@RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The organization application domains.", required = true) OrganizationDomains appDomains,
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