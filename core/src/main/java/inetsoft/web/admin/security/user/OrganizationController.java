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
import inetsoft.util.audit.ActionRecord;
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
                                 IdentityService identityService) {
      this.userTreeService = userTreeService;
      this.identityService = identityService;
   }


   @PostMapping("/api/em/security/users/create-organization/{provider}")
   public EditOrganizationPaneModel createOrganization(Principal principal,
                                       @DecodePathVariable("provider") String provider,
                                       @RequestBody CreateEntityRequest createRequest)
   {
      return userTreeService.createOrganization(provider, principal);
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
      return userTreeService.getOrganizationModel(provider, identityID.getName(), principal);
   }

   @GetMapping("/api/em/security/users/get-all-organizations/")
   public List<String> getAllOrganizations(Principal principal)
   {
      if(!SUtil.isMultiTenant()) {
         return new ArrayList<>();
      }

      return Arrays.stream(SecurityEngine.getSecurity().getSecurityProvider().getOrganizations()).toList();
   }

   @GetMapping("/api/em/security/providers/{provider}/get-is-template/{orgName}")
   public boolean getIsTemplate(@DecodePathVariable("provider") String provider,
                                @DecodePathVariable("orgName") String orgName,
                                           Principal principal)
   {
      String org = IdentityID.getIdentityIDFromKey(orgName).name;
      return org != null && !org.isEmpty() && org.equals(Organization.getTemplateOrganizationName());
   }

   @PostMapping("/api/em/security/users/edit-organization/{provider}")
   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectType = ActionRecord.OBJECT_TYPE_USERPERMISSION
   )
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.SECURITY_ORGANIZATION,
         actions = ResourceAction.ADMIN
      )
   })
   public void editOrganization(HttpServletRequest request,
                                @RequestBody @PermissionPath("oldName()") @AuditObjectName("oldName()") EditOrganizationPaneModel model,
                                @DecodePathVariable("provider") String provider,
                                @AuditUser Principal principal) throws Exception
   {
      userTreeService.editOrganization(model, provider, principal);
      OrganizationManager.getInstance().reset();
   }

   @PostMapping("/api/em/security/users/save-organization-template/{provider}")
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.SECURITY_ORGANIZATION,
         actions = ResourceAction.ADMIN
      )
   })
   public void saveOrganizationTemplate(HttpServletRequest request,
                                @RequestBody @PermissionPath("oldName()") @AuditObjectName("oldName()") EditOrganizationPaneModel model,
                                @DecodePathVariable("provider") String provider,
                                @AuditUser Principal principal) throws Exception
   {
      userTreeService.saveOrganizationTemplate(provider, model.name(), principal);
   }

   @PostMapping("/api/em/security/users/clear-organization-template/{provider}")
   public String clearOrganizationTemplate(HttpServletRequest request, @DecodePathVariable("provider") String provider,
                                         Principal principal) throws Exception
   {
      userTreeService.clearOrganizationTemplate(provider, principal);
      return Organization.getTemplateOrganizationName();
   }

   private final UserTreeService userTreeService;
   private final IdentityService identityService;
   private static final Logger LOG = LoggerFactory.getLogger(OrganizationController.class);
}
