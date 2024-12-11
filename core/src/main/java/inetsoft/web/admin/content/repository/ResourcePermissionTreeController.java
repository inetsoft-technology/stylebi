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
package inetsoft.web.admin.content.repository;

import inetsoft.web.admin.security.user.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class ResourcePermissionTreeController {
   @Autowired
   public ResourcePermissionTreeController(SecurityTreeServer userTreeService) {
      this.securityTreeServer = userTreeService;
   }

   @GetMapping("/api/em/settings/content/resource-tree")
   public SecurityTreeRootModel getResourceTree(
      @RequestParam(value = "provider", required = false) String provider,
      @RequestParam(value = "hideOrgAdminRole", required = false) boolean hideOrgAdminRole,
      Principal principal)
   {
      return securityTreeServer.getSecurityTree(provider, principal, true, false, hideOrgAdminRole);
   }

   private final SecurityTreeServer securityTreeServer;
}
