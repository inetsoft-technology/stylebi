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

import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.wiz.WizPermissions;
import inetsoft.web.wiz.WizUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * wiz-services-facing endpoint reporting which Wiz permissions the calling user really holds. The
 * caller always arrives as a wiz principal, which {@code WizDelegatingCheckPermissionStrategy}
 * approves for every check, so the permissions are evaluated on {@link WizUtil#saveCheckPrincipal}
 * (the same user with the wiz tag removed and the login check skipped).
 */
@RestController
public class WizEntitlementController {
   @Autowired
   public WizEntitlementController(SecurityEngine securityEngine) {
      this.securityEngine = securityEngine;
   }

   @GetMapping("/api/wiz/v1/entitlements/me")
   public EntitlementsResponse getMyEntitlements(Principal principal)
      throws inetsoft.sree.security.SecurityException
   {
      AdminAiCallerGuard.requireBearerAuthenticatedRequest();

      if(principal == null) {
         throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
      }

      Principal real = principal instanceof XPrincipal ?
         WizUtil.saveCheckPrincipal((XPrincipal) principal) : principal;
      List<String> held = new ArrayList<>();

      for(String permission : WizPermissions.ALL) {
         if(securityEngine.checkPermission(
            real, ResourceType.WIZ_PERMISSION, permission, ResourceAction.ACCESS))
         {
            held.add(permission);
         }
      }

      return new EntitlementsResponse(held);
   }

   public record EntitlementsResponse(List<String> permissions) {
   }

   private final SecurityEngine securityEngine;
}
