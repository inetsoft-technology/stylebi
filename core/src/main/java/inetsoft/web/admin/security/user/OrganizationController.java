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
import inetsoft.util.Tool;
import inetsoft.util.UserMessage;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.factory.DecodePathVariable;
import inetsoft.web.security.*;
import inetsoft.web.viewsheet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
public class OrganizationController {
   @Autowired
   public OrganizationController(UserTreeService userTreeService,
                                 IdentityService identityService,
                                 AuthenticationProviderService authenticationProviderService)
   {
      this.userTreeService = userTreeService;
      this.identityService = identityService;
      this.authenticationProviderService = authenticationProviderService;
   }


   @PostMapping("/api/em/security/users/create-organization/{provider}")
   public EditOrganizationPaneModel createOrganization(Principal principal,
                                       @DecodePathVariable("provider") String provider,
                                       @RequestBody CreateEntityRequest createRequest)
   {
      String copyFromOrgID = createRequest.parentGroup();

      if(copyFromOrgID != null && !Tool.isEmptyString(copyFromOrgID)) {
         return userTreeService.createOrganization(copyFromOrgID, provider, null, null, principal);
      }
      else {
         return userTreeService.createOrganization(null, provider, null, null, principal);
      }
   }

   @GetMapping("/api/em/security/providers/{provider}/organization/{organization}/")
   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.SECURITY_ORGANIZATION,
         actions = ResourceAction.ADMIN)
   )
   public EditOrganizationPaneModel getOrganization(@DecodePathVariable("provider") String provider,
                                    @PermissionPath @DecodePathVariable("organization") String organizationId,
                                    Principal principal)
   {
      IdentityID identityID = IdentityID.getIdentityIDFromKey(organizationId);
      return userTreeService.getOrganizationModel(provider, identityID, principal);
   }

   @GetMapping("/api/em/security/users/get-all-organization-names/")
   public List<String> getAllOrganizationNames(Principal principal)
   {
      if(!SUtil.isMultiTenant()) {
         return new ArrayList<>();
      }

      return Arrays.stream(SecurityEngine.getSecurity().getSecurityProvider().getOrganizationNames()).toList();
   }

   @GetMapping("/api/em/security/users/get-all-organization-ids/")
   public List<String> getAllOrganizationIDs(Principal principal)
   {
      if(!SUtil.isMultiTenant()) {
         return new ArrayList<>();
      }

      return Arrays.stream(SecurityEngine.getSecurity().getSecurityProvider().getOrganizationIDs()).toList();
   }

   @GetMapping("/api/em/security/users/get-all-organizations")
   public List<IdentityID> getAllOrganizationIdentityIDs(@RequestParam("name") String name,
                                                         Principal principal)
   {
      if(!SUtil.isMultiTenant()) {
         return new ArrayList<>();
      }

      AuthenticationProvider provider = authenticationProviderService.getProviderByName(name);

      return Arrays.stream(provider.getOrganizationIDs())
         .map(id -> new IdentityID(provider.getOrgNameFromID(id), id)).toList();
   }

   @GetMapping("/api/em/security/users/get-organization-detail-string/{orgID}")
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.SECURITY_ORGANIZATION,
         actions = ResourceAction.ADMIN
      )
   })
   public String getOrganizationDetailString(Principal principal,
                                             @PermissionPath @DecodePathVariable("orgID") String orgKey)
   {
     return identityService.getOrganizationDetailString(orgKey, principal);
   }

   @PostMapping("/api/em/security/users/edit-organization/{provider}")
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.SECURITY_ORGANIZATION,
         actions = ResourceAction.ADMIN
      )
   })
   public String editOrganization(HttpServletRequest request,
                                @RequestBody @PermissionPath("oldName()") @AuditObjectName("oldName()") EditOrganizationPaneModel model,
                                @DecodePathVariable("provider") String provider,
                                @AuditUser Principal principal) throws Exception
   {
      userTreeService.editOrganization(model, provider, principal);
      OrganizationManager.getInstance().reset();
      UserMessage message = Tool.getUserMessage();

      if(message != null) {
         String msg = message.getMessage();
         Tool.clearUserMessage();

         return msg;
      }

      return "";
   }

   private final UserTreeService userTreeService;
   private final IdentityService identityService;
   private final AuthenticationProviderService authenticationProviderService;
   private static final Logger LOG = LoggerFactory.getLogger(OrganizationController.class);
}
