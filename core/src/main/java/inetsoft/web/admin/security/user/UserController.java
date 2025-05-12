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
package inetsoft.web.admin.security.user;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.util.InvalidOrgException;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.factory.DecodePathVariable;
import inetsoft.web.security.*;
import inetsoft.web.viewsheet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class UserController {
   @Autowired
   public UserController(UserTreeService userTreeService,
                         IdentityService identityService)
   {
      this.userTreeService = userTreeService;
      this.identityService = identityService;
   }

   @PostMapping("/api/em/security/users/create-user/{provider}")
   public EditUserPaneModel createUser(Principal principal,
                                       @DecodePathVariable("provider") String provider,
                                       @RequestBody CreateEntityRequest createRequest)
   {
      return userTreeService.createUser(provider, createRequest.parentGroup(), principal);
   }

   @GetMapping("/api/em/security/providers/{provider}/users/{user}/")
   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.SECURITY_USER,
         actions = ResourceAction.ADMIN)
   )
   public EditUserPaneModel getUser(@DecodePathVariable("provider") String provider,
                                    @PermissionPath @DecodePathVariable(value = "user") String user,
                                    Principal principal)
   {
      IdentityID userID = IdentityID.getIdentityIDFromKey(user);
      return userTreeService.getUserModel(provider, userID, principal);
   }

   @PostMapping("/api/em/security/users/edit-user/{provider}")
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.SECURITY_USER,
         actions = ResourceAction.ADMIN
      )
   })
   public void editUser(HttpServletRequest request,
                        @RequestBody @PermissionPath("oldIdentityKey()")
                        @AuditObjectName("oldName()") EditUserPaneModel model,
                        @DecodePathVariable("provider") String provider,
                        @AuditUser Principal principal) throws Exception
   {
      String currOrgID = OrganizationManager.getInstance().getCurrentOrgID();

      if(SecurityEngine.getSecurity().getSecurityProvider().getOrganization(currOrgID) == null) {
         throw new InvalidOrgException(Catalog.getCatalog().getString("em.security.invalidOrganizationPassed"));
      }

      userTreeService.editUser(model, provider, principal);
      HttpSession session = request.getSession(true);
      Object ticket = session.getAttribute(SUtil.TICKET);
      String warning = identityService.getTimeOutWarning(ticket, model.oldName());

      if(warning != null) {
         LOG.error(warning);
      }
   }

   private final UserTreeService userTreeService;
   private final IdentityService identityService;
   private static final Logger LOG = LoggerFactory.getLogger(UserController.class);
}
