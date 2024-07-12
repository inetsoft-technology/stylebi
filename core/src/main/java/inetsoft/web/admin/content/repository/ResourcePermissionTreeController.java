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
package inetsoft.web.admin.content.repository;

import inetsoft.web.admin.security.user.SecurityTreeRootModel;
import inetsoft.web.admin.security.user.UserTreeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class ResourcePermissionTreeController {
   @Autowired
   public ResourcePermissionTreeController(UserTreeService userTreeService) {
      this.userTreeService = userTreeService;
   }

   @GetMapping("/api/em/settings/content/resource-tree")
   public SecurityTreeRootModel getResourceTree(
      @RequestParam(value = "provider", required = false) String provider,
      Principal principal)
   {
      return userTreeService.getSecurityTree(provider, principal, true);
   }

   private final UserTreeService userTreeService;
}
