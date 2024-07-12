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
package inetsoft.web.admin.security.user;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.util.Identity;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.audit.*;
import inetsoft.web.admin.security.IdentityModel;
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
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class RoleController {
   @Autowired
   public RoleController(SecurityProvider securityProvider,
                         IdentityService identityService,
                         UserTreeService userTreeService,
                         SystemAdminService systemAdminService,
                         IdentityThemeService themeService)
   {
      this.securityProvider = securityProvider;
      this.identityService = identityService;
      this.userTreeService = userTreeService;
      this.systemAdminService = systemAdminService;
      this.themeService = themeService;
   }

   @GetMapping("/api/em/security/user/get-security-tree-root/{provider}/{providerChanged}")
   public SecurityTreeRootModel getSecurityTreeRoot(@DecodePathVariable("provider") String provider,
                                                    @PathVariable("providerChanged") boolean providerChanged,
                                                    Principal principal)
   {
      return userTreeService.getSecurityTree(provider, principal, false, providerChanged);
   }

   @GetMapping("/api/em/security/user/create-role/{provider}")
   public EditRolePaneModel createRole(HttpServletRequest req, Principal principal,
                                       @DecodePathVariable("provider") String providerName)
   {
      ActionRecord actionRecord =
         SUtil.getActionRecord(req, ActionRecord.ACTION_NAME_CREATE,
                               null, ActionRecord.OBJECT_TYPE_USERPERMISSION);
      IdentityInfoRecord identityInfoRecord = null;

      try {
         AuthenticationProvider authcProvider = getProvider(providerName);

         if(!(authcProvider instanceof EditableAuthenticationProvider)) {
            return null;
         }

         EditableAuthenticationProvider provider = (EditableAuthenticationProvider) authcProvider;
         String prefix = "role";
         FSRole identity;
         String currOrg = OrganizationManager.getCurrentOrgName();

         if(!SUtil.isMultiTenant()) {
            currOrg = Organization.getDefaultOrganizationName();
         }

         for(int i = 0; ; i++) {
            String name = prefix + i;
            IdentityID id = new IdentityID(name, currOrg);
            if(provider.getRole(id) == null) {
               identity = new FSRole(id, new IdentityID[0]);
               provider.addRole(identity);
               break;
            }
         }

         String state = identity.getType() == Identity.USER ?
            IdentityInfoRecord.STATE_ACTIVE : IdentityInfoRecord.STATE_NONE;
         actionRecord.setObjectName(identity.getName());
         identityInfoRecord = SUtil.getIdentityInfoRecord(identity.getIdentityID(),
                                                          identity.getType(),
                                                          IdentityInfoRecord.ACTION_TYPE_CREATE,
                                                          null, state);
         identityService.createIdentityPermissions(identity.getIdentityID(), ResourceType.SECURITY_ROLE,
                                                   principal);

         return EditRolePaneModel.builder()
            .name(identity.getName())
            .organization(currOrg)
            .defaultRole(false)
            .isSysAdmin(false)
            .isOrgAdmin(false)
            .description("")
            .members(new ArrayList<>())
            .roles(new ArrayList<>())
            .permittedIdentities(new ArrayList<>())
            .theme(themeService.getTheme(identity.getIdentityID(), CustomTheme::getRoles))
            .build();
      }
      catch(Exception e) {
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         throw e;
      }
      finally {
         Audit.getInstance().auditAction(actionRecord, principal);

         if(identityInfoRecord != null) {
            Audit.getInstance().auditIdentityInfo(identityInfoRecord, principal);
         }
      }
   }

   @GetMapping("/api/em/security/providers/{provider}/roles/{role}/")
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.SECURITY_ROLE,
         actions = ResourceAction.ADMIN
      )
   })
   public EditRolePaneModel getRole(@DecodePathVariable("provider") String providerName,
                                    @PermissionPath @DecodePathVariable("role") String roleId,
                                    Principal principal)
   {
      IdentityID roleIdentityID= IdentityID.getIdentityIDFromKey(roleId);
      String rootRoleID = new IdentityID(Catalog.getCatalog(principal).getString("Roles"), roleIdentityID.organization).convertToKey();
      String rootOrgRoleID = new IdentityID(Catalog.getCatalog(principal).getString("Organization Roles"), roleIdentityID.organization).convertToKey();

      if(isRoleRoot(roleId, principal) &&
         (securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE, rootRoleID, ResourceAction.ADMIN) ||
         securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE, rootOrgRoleID, ResourceAction.ADMIN) ||
          securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE, roleId, ResourceAction.ADMIN)))
      {
         return roleId.contains(Catalog.getCatalog(principal).getString("Organization Roles")) ?
            getRootOrgRoleModel(principal, providerName) :
            getRootRoleModel(principal, roleIdentityID.organization, providerName);
      }

      AuthenticationProvider provider = getProvider(providerName);
      int type = Identity.ROLE;
      IdentityInfo info = identityService.getIdentityInfo(roleIdentityID, type, provider);
      List<IdentityModel> permissions = identityService.getPermission(roleId, ResourceType.SECURITY_ROLE, principal);
      Role role = provider.getRole(roleIdentityID);

      if(!OrganizationManager.getInstance().isSiteAdmin(principal)) {
         if(Arrays.stream(role.getRoles()).anyMatch(securityProvider::isSystemAdministratorRole)) {
            throw new MessageException(Catalog.getCatalog().getString("em.security.orgAdmin.identityPermissionDenied"));
         }
      }

      String name = role.getName();
      String org = role.getOrganization();

      //disable editing if a global role and not a system administrator
      boolean editableRoles =  (provider instanceof EditableAuthenticationProvider)
                     && ((org != null || OrganizationManager.getInstance().isSiteAdmin(principal)) ||
            SecurityEngine.getSecurity().getSecurityProvider()
               .checkPermission(principal, ResourceType.SECURITY_ROLE, roleIdentityID.convertToKey(), ResourceAction.ASSIGN));

      List<IdentityModel> members = identityService.getRoleMembers(roleIdentityID, provider);
      boolean isSiteAdmin = OrganizationManager.getInstance().isSiteAdmin(principal);

      return EditRolePaneModel.builder()
         .name(name)
         .organization(org)
         .defaultRole(info.isDefaultRole())
         .isSysAdmin(provider.isSystemAdministratorRole(roleIdentityID))
         .isOrgAdmin(provider.isOrgAdministratorRole(roleIdentityID))
         .description(((Role) info.getIdentity()).getDescription())
         .members(org == null && isSiteAdmin ? members : userTreeService.filterOtherOrgs(members))
         .roles(Arrays.asList(info.getRoles()))
         .permittedIdentities(org == null && isSiteAdmin ? members : userTreeService.filterOtherOrgs(permissions))
         .editable(editableRoles)
         .theme(themeService.getTheme(roleIdentityID, CustomTheme::getRoles))
         .enterprise(LicenseManager.getInstance().isEnterprise())
         .build();
   }

   private boolean isRoleRoot(String roleName, Principal principal) {
      String roleRoot = new IdentityID(Catalog.getCatalog(principal).getString("Roles"), OrganizationManager.getCurrentOrgName()).convertToKey();
      String roleOrgRoot = Organization.getRootOrgRoleName(principal);
      return roleRoot.equals(roleName) || roleOrgRoot.equals(roleName) ||
         Catalog.getCatalog(principal).getString("Roles").equals(roleName) ||
         Catalog.getCatalog(principal).getString("Organization Roles").equals(roleName);
   }

   private EditRolePaneModel getRootRoleModel(Principal principal, String org, String providerName) {
      AuthenticationProvider provider = getProvider(providerName);
      String orgID = provider.getOrgId(org);
      return EditRolePaneModel.builder()
         .name(Catalog.getCatalog(principal).getString("Roles"))
         .organization(OrganizationManager.getCurrentOrgName())
         .editable(provider instanceof EditableAuthenticationProvider)
         .permittedIdentities(userTreeService.filterOtherOrgs(identityService.getPermission(
            new IdentityID(Catalog.getCatalog().getString("Roles"), org), ResourceType.SECURITY_ROLE, orgID, principal)))
         .root(true)
         .isSysAdmin(false)
         .isOrgAdmin(false)
         .build();
   }

   private EditRolePaneModel getRootOrgRoleModel(Principal principal, String providerName) {
      String name = Catalog.getCatalog(principal).getString("Organization Roles");
      String permId = Organization.getRootOrgRoleName(principal);
      AuthenticationProvider provider = getProvider(providerName);
      return EditRolePaneModel.builder()
         .name(name)
         .organization(OrganizationManager.getCurrentOrgName())
         .editable(provider instanceof EditableAuthenticationProvider)
         .permittedIdentities(userTreeService.filterOtherOrgs(identityService.getPermission(permId, ResourceType.SECURITY_ROLE, principal)))
         .root(true)
         .isSysAdmin(false)
         .isOrgAdmin(false)
         .build();
   }

   private AuthenticationProvider getProvider(String providerName) {
      AuthenticationProvider authc = securityProvider.getAuthenticationProvider();

      if(!(authc instanceof AuthenticationChain)) {
         return null;
      }

      AuthenticationChain authcChain = (AuthenticationChain) authc;
      return authcChain.getProviders().stream()
         .filter((p) -> Catalog.getCatalog().getString(p.getProviderName()).equals(providerName))
         .findFirst()
         .orElse(null);
   }

   @PostMapping("/api/em/security/user/edit-role/{provider}")
   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectType = ActionRecord.OBJECT_TYPE_USERPERMISSION
   )
   public void editRole(HttpServletRequest req, @AuditUser Principal principal,
                        @RequestBody @AuditObjectName("oldName()") EditRolePaneModel model,
                        @DecodePathVariable("provider") String providerName)
      throws Exception
   {
      IdentityID oldIdentityID = new IdentityID(model.oldName(), model.organization());
      String rootOrgName = Catalog.getCatalog(principal).getString("Organization Roles");
      String rootName = Catalog.getCatalog(principal).getString("Roles");
      String roleSourceName = oldIdentityID.convertToKey();

      if(model.name().equals(rootName) || model.name().equals(rootOrgName)) {
         roleSourceName = new IdentityID(model.name(), model.organization()).convertToKey();
      }

      if(!securityProvider.checkPermission(principal,  ResourceType.SECURITY_ROLE,
                                           roleSourceName, ResourceAction.ADMIN))
      {
         throw new SecurityException("Unauthorized access to edit \"" + model.oldName() +
            "\" role by user " + principal);
      }

      userTreeService.editRole(model, providerName, principal);
      HttpSession session = req.getSession(true);
      Object ticket = session.getAttribute(SUtil.TICKET);
      String warningStr = identityService.getTimeOutWarning(ticket, model.oldName());

      if(warningStr != null) {
         LOG.error(warningStr);
      }
   }

   @PostMapping("/api/em/security/user/delete-identities/{provider}")
   public DeleteIdentitiesResponse deleteIdentities(@RequestBody IdentityModel[] models,
                                                    @DecodePathVariable("provider") String providerName,
                                                    Principal principal)
   {
      List<String> warnings = new ArrayList<>();
      Set<Identity> identitiesToDelete = Arrays.stream(models)
         .map(m -> systemAdminService.createIdentity(m.identityID(), m.type()))
         .collect(Collectors.toSet());

      List<IdentityID> modelIds = Arrays.stream(models)
         .map(m -> m.identityID())
         .collect(Collectors.toList());
      List<String> modelNames = modelIds.stream().map(id -> id.name).toList();

      boolean includesDefaultOrg = false;
      boolean includesTemplateOrg = false;
      boolean includesSelfOrg = false;

      if(modelNames.contains(Organization.getDefaultOrganizationName()) ||
         modelNames.contains(Organization.getTemplateOrganizationName()) ||
         modelNames.contains(Organization.getSelfOrganizationName())) {
         for(IdentityModel model:models) {
            if(model.type() == Identity.ORGANIZATION && Organization.getDefaultOrganizationName().equals(model.identityID().name)) {
               includesDefaultOrg = true;
               break;
            }
            else if(model.type() == Identity.ORGANIZATION && Organization.getTemplateOrganizationName().equals(model.identityID().name)) {
               includesTemplateOrg = true;
               break;
            }
            else if(model.type() == Identity.ORGANIZATION && Organization.getSelfOrganizationName().equals(model.identityID().name)) {
               includesSelfOrg = true;
               break;
            }
         }
      }
      if(includesDefaultOrg) {
         warnings.add(Catalog.getCatalog().getString("em.security.delDefOrg"));
      }
      else if(includesTemplateOrg) {
         warnings.add(Catalog.getCatalog().getString("em.security.delTemplateOrg"));
      }
      else if(includesSelfOrg) {
         warnings.add(Catalog.getCatalog().getString("em.security.delSelfOrg"));
      }
      else if(!systemAdminService.hasOrgAdminAfterDelete(identitiesToDelete)) {
         warnings.add(Catalog.getCatalog().getString("em.security.noOrgAdmin"));
      }
      else if(systemAdminService.hasSystemAdminAfterDelete(identitiesToDelete)) {
         warnings = identityService.deleteIdentities(models, providerName, principal);
      }
      else {
         // if no system admin would remain
         warnings.add(Catalog.getCatalog().getString("em.security.noSystemAdmin"));
      }

      return DeleteIdentitiesResponse.builder()
         .warnings(warnings)
         .build();
   }

   private final SecurityProvider securityProvider;
   private final IdentityService identityService;
   private final UserTreeService userTreeService;
   private final SystemAdminService systemAdminService;
   private final IdentityThemeService themeService;
   private final Logger LOG = LoggerFactory.getLogger(RoleController.class);
}
