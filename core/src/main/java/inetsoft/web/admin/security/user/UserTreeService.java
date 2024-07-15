/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.security.user;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import inetsoft.util.audit.*;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.general.model.LocalizationModel;
import inetsoft.web.admin.security.*;
import inetsoft.web.admin.server.LicenseInfo;
import inetsoft.web.admin.server.ServerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserTreeService {
   @Autowired
   public UserTreeService(AuthenticationProviderService authenticationProviderService,
                          SystemAdminService systemAdminService,
                          IdentityService identityService,
                          LocalizationSettingsService localizationSettingsService,
                          ServerService serverService,
                          SecurityEngine securityEngine, IdentityThemeService themeService)
   {
      this.authenticationProviderService = authenticationProviderService;
      this.systemAdminService = systemAdminService;
      this.identityService = identityService;
      this.localizationSettingsService = localizationSettingsService;
      this.namedUsers = serverService.getLicenseInfos().stream()
         .anyMatch(licenseInfo -> LicenseInfo.NAMED_USER.equals(licenseInfo.getType()));
      this.securityEngine = securityEngine;
      this.themeService = themeService;
   }

   public SecurityTreeRootModel getSecurityTree(String providerName, Principal principal,
                                                boolean isPermissions)
   {
      return getSecurityTree(providerName, principal, isPermissions, false);
   }

   public SecurityTreeRootModel getSecurityTree(String providerName, Principal principal,
                                                boolean isPermissions, boolean providerChanged)
   {
      boolean isMultiTenant = SUtil.isMultiTenant();

      securityProvider = securityEngine.getSecurityProvider();

      final AuthenticationProvider provider = providerName == null ?
         securityProvider.getAuthenticationProvider() :
         authenticationProviderService.getProviderByName(providerName);
      boolean editable = providerName == null || provider instanceof EditableAuthenticationProvider;

      if(providerChanged) {
         ((XPrincipal) principal).setProperty("curr_org_id", provider.getOrganizationId(provider.getOrganizations()[0]));
         ((XPrincipal) principal).setProperty("curr_provider_name", providerName);
      }

      String currOrgName = OrganizationManager.getInstance().getCurrentOrgName(principal);
      currOrgName = currOrgName == null ? Organization.getDefaultOrganizationName() : currOrgName;
      boolean isEnterprise = LicenseManager.getInstance().isEnterprise();
      isMultiTenant = isEnterprise && isMultiTenant;

      if(!isMultiTenant) {
         return SecurityTreeRootModel.builder()
            .users(getUserRoot(provider, principal, isMultiTenant, currOrgName))
            .groups(getGroupRoot(provider, principal, isMultiTenant, currOrgName))
            .roles(getRoleTree(provider, principal, isMultiTenant, currOrgName))
            .editable(editable)
            .isMultiTenant(isMultiTenant)
            .namedUsers(namedUsers)
            .build();
      }
      else {
         return SecurityTreeRootModel.builder()
            .users(getUserRoot(provider, principal, isMultiTenant, currOrgName))
            .groups(getGroupRoot(provider, principal, isMultiTenant, currOrgName))
            .roles(getRoleTree(provider, principal, isMultiTenant, currOrgName))
            .organizations(createOrgSecurityTreeNode(new IdentityID(currOrgName, currOrgName),provider, principal, false, isPermissions))
            .editable(editable)
            .isMultiTenant(isMultiTenant)
            .namedUsers(namedUsers)
            .build();
      }
   }

   public List<String> getOrganizationTree(String providerName, Principal principal) {
      final AuthenticationProvider provider = providerName == null ?
         securityProvider.getAuthenticationProvider() :
         authenticationProviderService.getProviderByName(providerName);

      final String[] organizations = provider.getOrganizations();

      return Arrays.stream(organizations)
      .sorted()
      .collect(Collectors.toList());
   }

   private SecurityTreeNode getUserRoot(AuthenticationProvider provider, Principal principal,
                                        boolean isMultiTenant, String curOrg) {
      String rootOrgUserKey = new IdentityID(Catalog.getCatalog(principal).getString("Users"), curOrg).convertToKey();
      boolean readOnly = !securityProvider.checkPermission(
         principal, ResourceType.SECURITY_USER, rootOrgUserKey, ResourceAction.ADMIN) &&
         !OrganizationManager.getInstance().isOrgAdmin(principal);

      List <SecurityTreeNode> userChildren = getFilteredUsers(provider.getUsers(), principal);

      //filter multi-tenant users to this organization only
      if(isMultiTenant) {
         userChildren = userChildren.stream()
            .filter(node -> provider.getUser(node.identityID()).getOrganization().equals(curOrg))
            .collect(Collectors.toList());
      }
      else {
         userChildren = userChildren.stream()
            .filter(node -> !Organization.getTemplateOrganizationName()
               .equals(provider.getUser(node.identityID()).getOrganization()))
            .collect(Collectors.toList());
      }

      String label = Catalog.getCatalog(principal).getString("Users");
      IdentityID id = new IdentityID(label, curOrg);

      return SecurityTreeNode.builder()
         .label(label)
         .identityID(id)
         .organization(curOrg)
         .type(Identity.USER)
         .children(userChildren)
         .root(!isMultiTenant)
         .readOnly(readOnly)
         .build();
   }

   private List<SecurityTreeNode> getFilteredUsers(IdentityID[] users, Principal principal) {
      return Arrays.stream(users)
         .filter(r -> securityProvider.checkPermission(
            principal, ResourceType.SECURITY_USER, r.convertToKey(), ResourceAction.ADMIN))
         .sorted()
         .map(this::createSecurityTreeNode)
         .collect(Collectors.toList());
   }

   private SecurityTreeNode getGroupRoot(AuthenticationProvider provider, Principal principal,
                                          boolean isMultiTenant, String curOrg) {
      final IdentityID[] groups = provider.getGroups();

      Set<String> childGroups = Arrays.stream(groups)
         .map(provider::getGroup)
         .filter(Objects::nonNull)
         .filter(g -> g.getGroups().length > 0)
         .map(Group::getName)
         .collect(Collectors.toSet());

      final Map<String, List<Identity>> groupMemberMap = provider.createGroupMemberMap(curOrg);

      List <SecurityTreeNode> groupChildren = Arrays.stream(groups)
            .filter(group -> !childGroups.contains(group.name))
            .filter(group -> !"".equals(group.name))
            .sorted()
            .map(groupName -> getGroup(provider, groupMemberMap, groupName, principal))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());

      //filter multi-tenant groups to this organization only
      if(isMultiTenant) {
         groupChildren = groupChildren.stream()
            .filter(node -> provider.getGroup(node.identityID()).getOrganization().equals(curOrg))
            .collect(Collectors.toList());
      }
      else {
         groupChildren = groupChildren.stream()
            .filter(node -> !Organization.getTemplateOrganizationName()
               .equals(provider.getGroup(node.identityID()).getOrganization()))
            .collect(Collectors.toList());

      }

      String label = Catalog.getCatalog(principal).getString("Groups");
      String rootGroupKey = new IdentityID(label, curOrg).convertToKey();
      boolean readOnly = !securityProvider.checkPermission(
         principal, ResourceType.SECURITY_GROUP, rootGroupKey, ResourceAction.ADMIN) &&
         !OrganizationManager.getInstance().isOrgAdmin(principal);

      IdentityID id = new IdentityID(label, curOrg);

      return SecurityTreeNode.builder()
         .label(label)
         .identityID(id)
         .organization(curOrg)
         .type(Identity.GROUP)
         .children(groupChildren)
         .root(!isMultiTenant)
         .readOnly(readOnly)
         .build();
   }

   private Optional<SecurityTreeNode> getGroup(AuthenticationProvider provider,
                                               Map<String, List<Identity>> groupMemberMap,
                                               IdentityID groupName,
                                               Principal principal)
   {
      final Identity[] groupMembers = provider.getGroupMembers(groupName, groupMemberMap);

      List<SecurityTreeNode> groupChildren = Arrays.stream(groupMembers)
         .filter(i -> i.getType() == Identity.GROUP)
         .map(Identity::getIdentityID)
         .sorted()
         .map(g -> getGroup(provider, groupMemberMap, g, principal))
         .filter(Optional::isPresent)
         .map(Optional::get)
         .collect(Collectors.toList());

      IdentityID[] groupUsers = Arrays.stream(groupMembers)
         .filter(identity -> identity.getType() == Identity.USER)
         .map(Identity::getIdentityID)
         .toArray(IdentityID[]::new);
      List<SecurityTreeNode> userChildren = getFilteredUsers(groupUsers, principal);

      boolean readOnly = !securityProvider.checkPermission(
         principal, ResourceType.SECURITY_GROUP, groupName.convertToKey(), ResourceAction.ADMIN);

      if(readOnly && groupChildren.isEmpty() && userChildren.isEmpty()) {
         return Optional.empty();
      }

      return Optional.of(SecurityTreeNode.builder()
         .label(groupName.name)
         .organization(groupName.organization)
         .identityID(groupName)
         .type(Identity.GROUP)
         .addAllChildren(groupChildren)
         .addAllChildren(userChildren)
         .root(false)
         .readOnly(readOnly)
         .build());
   }

   private SecurityTreeNode getRoleTree(AuthenticationProvider provider, Principal principal,
                                        boolean isMultiTenant, String curOrg)
   {
      final boolean allRolesReadOnly = !(securityProvider.checkPermission(
         principal, ResourceType.SECURITY_ROLE, new IdentityID(Catalog.getCatalog(principal).getString("Roles"), curOrg).convertToKey(), ResourceAction.ADMIN) ||
         securityProvider.checkPermission(
            principal, ResourceType.SECURITY_ROLE, new IdentityID(Catalog.getCatalog(principal).getString("Organization Roles"), curOrg).convertToKey(), ResourceAction.ADMIN));

      final Role[] roles = Arrays.stream(provider.getRoles())
         .sorted()
         .map(provider::getRole)
         .filter(Objects::nonNull)
         .toArray(Role[]::new);

      List <SecurityTreeNode> roleChildren = getRoleChildren(roles, allRolesReadOnly, principal);

      if(!isMultiTenant) {
         //filter any org admin roles that are not sys admin from appearing
         roleChildren = roleChildren.stream()
            .filter(node -> provider.getRole(node.identityID()).getOrganization() == null
               || provider.getRole(node.identityID()).getOrganization().equals(curOrg))
            .filter(node -> !(!provider.isSystemAdministratorRole(node.identityID()) &&
                             (provider.isOrgAdministratorRole(node.identityID()))))
            .filter(node -> !Organization.getTemplateOrganizationName()
               .equals(provider.getRole(node.identityID()).getOrganization()))
            .collect(Collectors.toList());
      }
      else if(OrganizationManager.getInstance().isSiteAdmin(principal)) {
            //filter global only
            roleChildren = roleChildren.stream()
               .filter(node -> provider.getRole(node.identityID()).getOrganization() == null)
               .collect(Collectors.toList());
         }
      else {
         roleChildren = Collections.emptyList();
      }

      String roleLabel = Catalog.getCatalog(principal).getString("Roles");
      return SecurityTreeNode.builder()
         .label(roleLabel)
         .identityID(new IdentityID(roleLabel, curOrg))
         .type(Identity.ROLE)
         .children(roleChildren)
         .root(true)
         .readOnly(allRolesReadOnly)
         .build();
   }

   private List<SecurityTreeNode> getRoleChildren(Role[] roles, boolean allRolesReadOnly,
                                                  Principal principal)
   {
      final Map<IdentityID, List<Role>> parentRoleMap = new HashMap<>();

      for(Role role : roles) {
         final IdentityID[] parentRoles = role.getRoles() == null ? new IdentityID[0] : role.getRoles();

         for(IdentityID parentRole : parentRoles) {
            parentRoleMap.compute(parentRole, (key, list) -> {
               if(list == null) {
                  list = new ArrayList<>(1);
               }

               list.add(role);
               return list;
            });
         }
      }

      final List<SecurityTreeNode> topLevelNodes = new ArrayList<>();
      final Map<IdentityID, SecurityTreeNode> roleMap = new HashMap<>();

      for(Role role : roles) {
         if(role != null && !"".equals(role.getName())) {
            boolean hasPermission = !allRolesReadOnly || securityProvider.checkPermission(
               principal, ResourceType.SECURITY_ROLE, role.getIdentityID().convertToKey(), ResourceAction.ASSIGN);
            List<SecurityTreeNode> children =
               getRoleChildren(role.getIdentityID(), allRolesReadOnly,
                               !allRolesReadOnly || hasPermission, parentRoleMap, roleMap,
                               principal);

            //filter out other organizations
            children = children.stream().filter(child -> {
                  Role childRole = securityProvider.getRole(child.identityID());

                  return (childRole != null && (
                  (role.getOrganization() == null && childRole.getOrganization() == null) ||
                  (role.getOrganization() != null && (role.getOrganization().equals(childRole.getOrganization()) ||
                     childRole.getOrganization() == null && OrganizationManager.getCurrentOrgName().equals(role.getOrganization()))) ||
                  (role.getOrganization() == null && OrganizationManager.getCurrentOrgName().equals(childRole.getOrganization()))));
            })
            .collect(Collectors.toList());

            if((role.getRoles().length == 0 || role.getOrganization() == null) &&
               (!allRolesReadOnly || hasPermission ||
               children.size() > 0))
            {
               SecurityTreeNode node = SecurityTreeNode.builder()
                  .children(children)
                  .label(role.getName())
                  .identityID(role.getIdentityID())
                  .type(role.getType())
                  .root(false)
                  .readOnly(allRolesReadOnly)
                  .build();
               topLevelNodes.add(node);
            }
         }
      }

      return topLevelNodes;
   }

   private Map<IdentityID, List<IdentityID>> getRolesParentMap(IdentityID[] roles,
                                                               AuthenticationProvider provider)
   {
      final Map<IdentityID, List<IdentityID>> parentRoleMap = new HashMap<>();

      for(IdentityID roleId : roles) {
         Role role = provider.getRole(roleId);
         final IdentityID[] parentRoles = role.getRoles() == null ? new IdentityID[0] :
            role.getRoles();

         for(IdentityID parentRole : parentRoles) {
            parentRoleMap.compute(parentRole, (key, list) -> {
               if(list == null) {
                  list = new ArrayList<>(1);
               }

               list.add(roleId);
               return list;
            });
         }
      }

      return parentRoleMap;
   }

   private SecurityTreeNode getRoleNode(Role role, boolean allRolesReadOnly,
                                        boolean inheritPermission,
                                        Map<IdentityID, List<Role>> parentRoleMap,
                                        Map<IdentityID, SecurityTreeNode> roleMap,
                                        Principal principal)
   {
      SecurityTreeNode node = roleMap.get(role.getIdentityID());

      if(node == null) {
         node = SecurityTreeNode.builder()
            .children(getRoleChildren(role.getIdentityID(), allRolesReadOnly, inheritPermission,
                                      parentRoleMap, roleMap, principal))
            .label(role.getName())
            .identityID(role.getIdentityID())
            .type(role.getType())
            .root(false)
            .readOnly(allRolesReadOnly)
            .build();
         roleMap.put(role.getIdentityID(), node);
      }

      return node;
   }

   private List<SecurityTreeNode> getRoleChildren(
      IdentityID parentRoleName,
      boolean allRolesReadOnly,
      boolean inheritPermission,
      Map<IdentityID, List<Role>> parentRoleMap,
      Map<IdentityID, SecurityTreeNode> roleMap,
      Principal principal)
   {
      final List<Role> childRoles = parentRoleMap.get(parentRoleName);

      if(childRoles == null) {
         return Collections.emptyList();
      }

      final List<SecurityTreeNode> childNodes = new ArrayList<>(childRoles.size());

      for(Role childRole : childRoles) {
         boolean hasPermission = false;

         if(childRole == null) {
            continue;
         }

         if(!allRolesReadOnly || inheritPermission ||
            (hasPermission = securityProvider.checkPermission(
               principal, ResourceType.SECURITY_ROLE, childRole.getIdentityID().convertToKey(), ResourceAction.ASSIGN)))
         {
            childNodes.add(getRoleNode(childRole, allRolesReadOnly,
                                       inheritPermission || hasPermission, parentRoleMap, roleMap,
                                       principal));
         }
      }

      return childNodes;
   }

   /**
    * Create a new group
    */
   EditGroupPaneModel createGroup(String selectedProvider, String parentGroup, Principal principal)
   {
      ActionRecord actionRecord =
         SUtil.getActionRecord(principal, ActionRecord.ACTION_NAME_CREATE,
                               null, ActionRecord.OBJECT_TYPE_USERPERMISSION);
      IdentityInfoRecord identityInfoRecord = null;

      try {
         final AuthenticationProvider provider =
            authenticationProviderService.getProviderByName(selectedProvider);

         if(!(provider instanceof EditableAuthenticationProvider)) {
            return null;
         }

         String currOrg =  OrganizationManager.getCurrentOrgName();

         FSGroup identity = null;

         for(int i = 0; identity == null; i++) {
            String name = "group" + i;
            IdentityID id = new IdentityID(name, currOrg);

            if(provider.getGroup(id) == null) {
               identity = new FSGroup(id);
               addOrganizationMember(currOrg, name, (EditableAuthenticationProvider) provider);

               if(parentGroup != null) {
                  identity.setGroups(new String[]{ parentGroup });
               }

               ((EditableAuthenticationProvider) provider).addGroup(identity);
               identityService.createIdentityPermissions(id, ResourceType.SECURITY_GROUP,
                                                         principal);
            }
         }

         actionRecord.setObjectName(identity.getName());
         identityInfoRecord = SUtil.getIdentityInfoRecord(identity.getIdentityID(),
                                                          identity.getType(),
                                                          IdentityInfoRecord.ACTION_TYPE_CREATE,
                                                          null,
                                                          IdentityInfoRecord.STATE_NONE);

         return EditGroupPaneModel.builder()
            .name(identity.getName())
            .organization(currOrg)
            .identityNames(Arrays.stream(provider.getGroups()).toList())
            .members(new ArrayList<>())
            .roles(new ArrayList<>())
            .permittedIdentities(new ArrayList<>())
            .theme(themeService.getTheme(identity.getIdentityID(), CustomTheme::getGroups))
            .build();
      }
      catch(Exception e) {
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         actionRecord.setActionError(e.getMessage());
         throw e;
      }
      finally {
         Audit.getInstance().auditAction(actionRecord, principal);

         if(identityInfoRecord != null) {
            Audit.getInstance().auditIdentityInfo(identityInfoRecord, principal);
         }
      }
   }

   EditGroupPaneModel getGroupModel(String selectedProvider, IdentityID groupID, Principal principal)
   {
      String orgGroupRoot = Catalog.getCatalog(principal).getString("Groups");

      final AuthenticationProvider currentProvider =
         authenticationProviderService.getProviderByName(selectedProvider);

      if((!SUtil.isMultiTenant() || !securityEngine.isSecurityEnabled()) && isGroupRoot(groupID, principal) &&
         (securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP,
                   new IdentityID(orgGroupRoot, OrganizationManager.getCurrentOrgName()).convertToKey(), ResourceAction.ADMIN) ||
          securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP, groupID.convertToKey(), ResourceAction.ADMIN)))
      {
         return getRootGroupModel(principal, currentProvider);
      }

      boolean orgAdmin = OrganizationManager.getInstance().isOrgAdmin(principal);

      if(SUtil.isMultiTenant() && securityEngine.isSecurityEnabled() &&
         isGroupRoot(groupID, principal) && (orgAdmin || hasOrgAdminPermission(OrganizationManager.getCurrentOrgName(), principal) ||
         securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP,
                      new IdentityID(orgGroupRoot, OrganizationManager.getCurrentOrgName()).convertToKey(), ResourceAction.ADMIN) ||
         securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP, orgGroupRoot, ResourceAction.ADMIN)))
      {
         return getRootGroupModelForOrg(principal, OrganizationManager.getCurrentOrgName(), currentProvider);
      }

      final Group group = currentProvider.getGroup(groupID);

      if(!OrganizationManager.getInstance().isSiteAdmin(principal)) {
         if(Arrays.stream(group.getRoles()).anyMatch(currentProvider::isSystemAdministratorRole)) {
            throw new MessageException(Catalog.getCatalog().getString("em.security.orgAdmin.identityPermissionDenied"));
         }
      }

      IdentityInfo info = identityService
         .getIdentityInfo(groupID, Identity.GROUP, currentProvider);

      String org = group == null ? null : group.getOrganization();
      if(org == null || "".equals(org)) {
         org = Organization.getDefaultOrganizationName();
      }

      String orgID = securityProvider.getOrganization(org).getId();

      return EditGroupPaneModel.builder()
         .name(groupID.name)
         .members(info.getMembers())
         .roles(Arrays.asList(group.getRoles()))
         .organization(org)
         .permittedIdentities(
            filterOtherOrgs(identityService.getPermission(groupID, ResourceType.SECURITY_GROUP, orgID, principal)))
         .editable(currentProvider instanceof EditableAuthenticationProvider)
         .theme(themeService.getTheme(groupID, CustomTheme::getGroups))
         .build();
   }

   private EditGroupPaneModel getRootGroupModel(Principal principal,
                                                AuthenticationProvider currentProvider)
   {
      String org = OrganizationManager.getInstance().getCurrentOrgName(principal);
      String orgID = OrganizationManager.getInstance().getUserOrgId(principal);

      return EditGroupPaneModel.builder()
         .name(Catalog.getCatalog(principal).getString("Groups"))
         .editable(currentProvider instanceof EditableAuthenticationProvider)
         .permittedIdentities(
            filterOtherOrgs(identityService.getPermission(new IdentityID(Catalog.getCatalog(principal).getString("Groups"), org),
                                                          ResourceType.SECURITY_GROUP, orgID, principal)))
         .root(true)
         .build();
   }

   private EditGroupPaneModel getRootGroupModelForOrg(Principal principal, String orgName,
                                                      AuthenticationProvider currentProvider)
   {
      String groupName = Catalog.getCatalog(principal).getString("Groups");
      IdentityID rootGroup = new IdentityID(groupName, orgName);
      String orgID = currentProvider.getOrgId(orgName);

      return EditGroupPaneModel.builder()
         .name(Catalog.getCatalog(principal).getString("Groups"))
         .organization(orgName)
         .editable(currentProvider instanceof EditableAuthenticationProvider)
         .permittedIdentities(
            filterOtherOrgs(identityService.getPermission(rootGroup,
                                                          ResourceType.SECURITY_GROUP, orgID, principal)))
         .root(true)
         .build();
   }

   private boolean isGroupRoot(IdentityID group, Principal principal) {
      String groupRoot = Catalog.getCatalog(principal).getString("Groups");
      String orgGroupRoot = groupRoot + IdentityID.KEY_DELIMITER + OrganizationManager.getCurrentOrgName();
      String defOrgGroupRoot = groupRoot + IdentityID.KEY_DELIMITER +Organization.getDefaultOrganizationName();

      return Tool.equals(group.name, groupRoot) || Tool.equals(group.name, orgGroupRoot) || Tool.equals(group.name, defOrgGroupRoot);
   }

   void editGroup(String providerName, IdentityID group, EditGroupPaneModel model, Principal principal)
      throws Exception
   {
      IdentityID oldID = new IdentityID(model.oldName(), model.organization());
      IdentityID newID = new IdentityID(model.name(), model.organization());
      IdentityID root = new IdentityID(Catalog.getCatalog().getString("Groups"), model.organization());

      if(isGroupRoot(new IdentityID(model.name(), group.organization), principal)) {
         if(securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP, root.convertToKey(), ResourceAction.ADMIN) ||
            securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP, new IdentityID(model.name(), model.organization()).convertToKey(), ResourceAction.ADMIN))
         {
            identityService.setIdentityPermissions(
               root, root, ResourceType.SECURITY_GROUP, principal, model.permittedIdentities(), model.organization());
         }

         return;
      }

      final AuthenticationProvider provider =
         authenticationProviderService.getProviderByName(providerName);
      final Group oldGroup = provider.getGroup(group);

      if(!OrganizationManager.getInstance().isSiteAdmin(principal)) {
         checkGroupEditedHasSysAdmin(oldGroup, model, principal);
      }

      final IdentityModification groupChange =
         systemAdminService.getGroupModification(oldGroup, model, principal);

      if(!systemAdminService.hasSysAdmin(Collections.singleton(groupChange))) {
         throw new MessageException(Catalog.getCatalog().getString("em.security.noSystemAdmin"));
      }
      else if(!systemAdminService.hasOrgAdmin(Collections.singleton(groupChange))) {
         throw new MessageException(Catalog.getCatalog().getString("em.security.noOrgAdmin"));
      }

      themeService.updateTheme(model.oldName(), model.name(), model.theme(), CustomTheme::getGroups);

      // if the group has admin permission on themselves, rename the group in the grant
      List<IdentityModel> permittedIdentities = getRenamedPermittedIdentities(
         model.permittedIdentities(), Identity.GROUP, oldID, newID);
      identityService.setIdentity(oldGroup, model, provider, principal);
      identityService.setIdentityPermissions(oldID, newID, ResourceType.SECURITY_GROUP,
                                             principal, permittedIdentities, "");
   }

   /**
    * Create a new user
    */
   EditUserPaneModel createUser(String providerName, String parentGroup, Principal principal) {
      ActionRecord actionRecord = SUtil.getActionRecord(
         principal, ActionRecord.ACTION_NAME_CREATE, null,
         ActionRecord.OBJECT_TYPE_USERPERMISSION);
      IdentityInfoRecord identityInfoRecord = null;
      final AuthenticationProvider provider =
         authenticationProviderService.getProviderByName(providerName);
      Principal oldPrincipal = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(principal);

      try {
         if(!(provider instanceof EditableAuthenticationProvider)) {
            return null;
         }

         EditableAuthenticationProvider editProvider = (EditableAuthenticationProvider) provider;
         String currOrg = OrganizationManager.getCurrentOrgName();
         String prefix = "user";
         FSUser identity;

         for(int i = 0; ; i++) {
            String name = prefix + i;
            IdentityID id = new IdentityID(name, currOrg);

            if(provider.getUser(id) == null) {
               identity = new FSUser(id);
               identity.setOrganization(currOrg);
               addOrganizationMember(currOrg, name, editProvider);
               identity.setRoles(getDefaultRoles(editProvider, currOrg)
                                    .toArray(new IdentityID[0]));
               SUtil.setPassword(identity, "success123");

               if(parentGroup != null) {
                  identity.setGroups(new String[] { parentGroup });
               }

               editProvider.addUser(identity);
               break;
            }
         }

         IdentityID[] identityIds = provider.getGroups();
         String state = IdentityInfoRecord.STATE_ACTIVE;
         actionRecord.setObjectName(identity.getName());
         identityInfoRecord = SUtil.getIdentityInfoRecord(identity.getIdentityID(),
                                                          identity.getType(),
                                                          IdentityInfoRecord.ACTION_TYPE_CREATE,
                                                          null, state);

         identityService.createIdentityPermissions(identity.getIdentityID(), ResourceType.SECURITY_USER,
                                                   principal);

         List<String> localesList = localizationSettingsService.getModel().locales()
            .stream().map(LocalizationModel::label).collect(Collectors.toList());

         return EditUserPaneModel.builder()
            .name(identity.getName())
            .password(identity.getPassword())
            .alias("")
            .email("")
            .organization(currOrg)
            .identityNames(Arrays.stream(identityIds).toList())
            .members(new ArrayList<>())
            .roles(new ArrayList<>())
            .permittedIdentities(new ArrayList<>())
            .localesList(localesList)
            .theme(themeService.getTheme(identity.getIdentityID(), CustomTheme::getUsers))
            .build();
      }
      catch(Exception e) {
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         actionRecord.setActionError(e.getMessage());
         throw e;
      }
      finally {
         ThreadContext.setContextPrincipal(oldPrincipal);
         Audit.getInstance().auditAction(actionRecord, principal);

         if(identityInfoRecord != null) {
            Audit.getInstance().auditIdentityInfo(identityInfoRecord, principal);
         }
      }
   }

   private void addOrganizationMember(String orgName, String memberName, EditableAuthenticationProvider provider) {
      Organization org = provider.getOrganization(orgName);
      List<String> members = org.getMembers() != null ? new ArrayList<>(Arrays.asList(org.getMembers())) : new ArrayList<>();
      members.add(memberName);
      org.setMembers(members.toArray(new String[0]));
      provider.setOrganization(orgName, org);
   }

   public EditUserPaneModel getUserModel(String provider, IdentityID userName, Principal principal) {
      AuthenticationProvider currentProvider =
         authenticationProviderService.getProviderByName(provider);
      String rootUser = Catalog.getCatalog(principal).getString("Users");

      if(rootUser.equals(userName.name) &&
         (securityProvider.checkPermission(principal, ResourceType.SECURITY_USER, new IdentityID(rootUser, OrganizationManager.getCurrentOrgName()).convertToKey(), ResourceAction.ADMIN) ||
            securityProvider.checkPermission(principal, ResourceType.SECURITY_USER, userName.convertToKey(), ResourceAction.ADMIN)))
      {
         return getRootUserModel(principal, currentProvider);
      }

      User user = currentProvider.getUser(userName);
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      String orgID = OrganizationManager.getInstance().getCurrentOrgID();

      if(SUtil.isMultiTenant()) {
         if(!OrganizationManager.getInstance().isSiteAdmin(principal)) {
            if(Arrays.stream(user.getRoles()).anyMatch(currentProvider::isSystemAdministratorRole)) {
               throw new MessageException(Catalog.getCatalog().getString("em.security.orgAdmin.identityPermissionDenied"));
            }
         }
      }

      List<IdentityModel> grantedUsers =
         identityService.getPermission(userName, ResourceType.SECURITY_USER, orgID, principal);
      IdentityInfo info = identityService.getIdentityInfo(userName, Identity.USER, currentProvider);
      List<LocalizationModel> locales = localizationSettingsService.getModel().locales();
      List<String> localesList = locales.stream()
         .map(LocalizationModel::label).collect(Collectors.toList());

      String locale = null;

      for(LocalizationModel localeModel : locales) {
         String loc = String.join("_", localeModel.language(), localeModel.country());

         if(loc.equals(user.getLocale())) {
            locale = localeModel.label();
         }
      }

      String[] emails = user.getEmails();

      String org = user.getOrganization();
      if(org == null || "".equals(org)) {
         org = Organization.getDefaultOrganizationName();
      }

      return EditUserPaneModel.builder()
         .name(userName.name)
         .password(null)
         .status(user.isActive())
         .organization(org)
         .alias(user.getAlias())
         .email(emails != null ? String.join(",", emails) : "")
         .locale(locale == null ? "" : locale)
         .identityNames(Arrays.stream(currentProvider.getGroups()).toList())
         .members(info.getMembers())
         .roles(Arrays.asList(info.getRoles()))
         .permittedIdentities(filterOtherOrgs(grantedUsers))
         .editable(user.isEditable() && currentProvider instanceof EditableAuthenticationProvider)
         .currentUser(userName.equals(pId))
         .localesList(localesList)
         .theme(themeService.getTheme(userName, CustomTheme::getUsers))
         .build();
   }
   /**
    * Create a new user
    */
   EditOrganizationPaneModel createOrganization(String providerName, Principal principal) {

      ActionRecord actionRecord = SUtil.getActionRecord(
         principal, ActionRecord.ACTION_NAME_CREATE, null,
         ActionRecord.OBJECT_TYPE_USERPERMISSION);
      IdentityInfoRecord identityInfoRecord = null;
      final AuthenticationProvider provider =
         authenticationProviderService.getProviderByName(providerName);
      Principal oldPrincipal = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(principal);
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());

      try {
         if(!(provider instanceof EditableAuthenticationProvider)) {
            return null;
         }

         EditableAuthenticationProvider editProvider = (EditableAuthenticationProvider) provider;
         SecurityProvider securityProvider = SecurityEngine.getSecurity().getSecurityProvider();
         String prefix = "organization";
         FSOrganization identity;
         List<IdentityModel> defMembers = new ArrayList<IdentityModel>();

         for(int i = 0; ; i++) {
            final String name = prefix + i;
            boolean found = Arrays.stream(securityProvider.getOrganizations()).anyMatch(o -> o.equalsIgnoreCase(name)) ||
                            securityProvider.getOrgNameFromID(name) != null;

            if(!found) {
               Organization template = editProvider.getOrganization(Organization.getTemplateOrganizationName());
               editProvider.copyOrganization(template, name, identityService, themeService, principal);
               identity = (FSOrganization) editProvider.getOrganization(name);
               break;
            }
         }

         IdentityID[] identityNames = provider.getGroups();
         String state = IdentityInfoRecord.STATE_NONE;
         actionRecord.setObjectName(identity.getName());
         identityInfoRecord = SUtil.getIdentityInfoRecord(identity.getIdentityID(),
                                                          identity.getType(),
                                                          IdentityInfoRecord.ACTION_TYPE_CREATE,
                                                          null, state);

         List<String> localesList = localizationSettingsService.getModel().locales()
            .stream().map(LocalizationModel::label).collect(Collectors.toList());

         //update to this organization
         String newOrgID = identity.getId();
         OrganizationManager.getInstance().setCurrentOrgID(newOrgID);

         return EditOrganizationPaneModel.builder()
            .name(identity.getName())
            .id(identity.getId())
            .identityNames(Arrays.stream(identityNames).toList())
            .members(defMembers)
            .roles(new ArrayList<>())
            .permittedIdentities(new ArrayList<>())
            .localesList(localesList)
            .theme(identity.getTheme())
            .currentUserName(pId.name)
            .build();
      }
      catch(Exception e) {
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         actionRecord.setActionError(e.getMessage());
         throw e;
      }
      finally {
         ThreadContext.setContextPrincipal(oldPrincipal);
         Audit.getInstance().auditAction(actionRecord, principal);

         if(identityInfoRecord != null) {
            Audit.getInstance().auditIdentityInfo(identityInfoRecord, principal);
         }
      }
   }

   public void saveOrganizationTemplate(String provider, String fromOrgName, Principal principal) {
      AuthenticationProvider currentProvider =
         authenticationProviderService.getProviderByName(provider);
      if(currentProvider instanceof AbstractEditableAuthenticationProvider) {
         Organization copyFrom = currentProvider.getOrganization(fromOrgName);
         String templateOrgName = Organization.getTemplateOrganizationName();

         if(currentProvider.getOrganization(templateOrgName) == null) {
            Organization newTemplateOrg = new Organization(templateOrgName, Organization.getTemplateOrganizationID(),new String[0], "", true);
            ((AbstractEditableAuthenticationProvider) currentProvider).addOrganization(newTemplateOrg);
         }

         ((AbstractEditableAuthenticationProvider) currentProvider).copyOrganization(copyFrom, templateOrgName, identityService, themeService, principal);
      }
   }

   public void clearOrganizationTemplate(String provider, Principal principal) {
      AuthenticationProvider currentProvider =
         authenticationProviderService.getProviderByName(provider);

      if(currentProvider instanceof AbstractEditableAuthenticationProvider) {
         ((AbstractEditableAuthenticationProvider) currentProvider).cleanTemplateOrganization(identityService, principal);
      }
   }

   public EditOrganizationPaneModel getOrganizationModel(String provider, String orgName, Principal principal) {
      AuthenticationProvider currentProvider =
         authenticationProviderService.getProviderByName(provider);

      if(Catalog.getCatalog(principal).getString("Organizations").equals(orgName) &&
         securityProvider.checkPermission(
            principal, ResourceType.SECURITY_ORGANIZATION, "*", ResourceAction.ADMIN))
      {
         return getRootOrganizationModel(principal, currentProvider);
      }

      Organization organization = currentProvider.getOrganization(orgName);
      String[] propertyNames = {"max.row.count", "max.col.count", "max.cell.size", "max.user.count"};
      List<PropertyModel> properties = new ArrayList<>();
      IdentityID orgId = new IdentityID(orgName, orgName);
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());

      for(String key : propertyNames) {
         if(SreeEnv.getProperty(key, false, true) != null) {
            properties.add(PropertyModel.builder().name(key).value(SreeEnv.getProperty(key, false, true)).build());
         }
      }

      List<IdentityModel> grantedOrganizations =
         identityService.getPermission(orgId, ResourceType.SECURITY_ORGANIZATION, organization.getId(), principal);

      IdentityInfo info = identityService.getIdentityInfo(orgId, Identity.ORGANIZATION, currentProvider);

      List<LocalizationModel> locales = localizationSettingsService.getModel().locales();
      List<String> localesList = locales.stream()
         .map(LocalizationModel::label).collect(Collectors.toList());

      String locale = null;

      for(LocalizationModel localeModel : locales) {
         String loc = String.join("_", localeModel.language(), localeModel.country());

         if(loc.equals(organization.getLocale())) {
            locale = localeModel.label();
         }
      }

      List<IdentityModel> members = info.getMembers().stream()
         .filter(identityModel -> identityModel.type() != Identity.USER ||
            securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                             identityModel.identityID().convertToKey(), ResourceAction.ADMIN)).toList();

      return EditOrganizationPaneModel.builder()
         .name(orgName)
         .id(organization.getId())
         .status(organization.isActive())
         .locale(locale == null ? "" : locale)
         .identityNames(Arrays.stream(currentProvider.getGroups()).toList())
         .members(members)
         .roles(new ArrayList<>())
         .permittedIdentities(filterOtherOrgs(grantedOrganizations))
         .properties(properties)
         .editable(organization.isEditable() && currentProvider instanceof EditableAuthenticationProvider)
         .currentUser(orgName.equals(pId.name))
         .localesList(localesList)
         .theme(organization.getTheme())
         .currentUserName(pId.name)
         .build();
   }

   private EditUserPaneModel getRootUserModel(Principal principal, AuthenticationProvider currentProvider) {
      IdentityID rootUser = new IdentityID(Catalog.getCatalog(principal).getString("Users"), OrganizationManager.getCurrentOrgName());
      return EditUserPaneModel.builder()
         .name(Catalog.getCatalog(principal).getString("Users"))
         .organization(OrganizationManager.getCurrentOrgName())
         .editable(currentProvider instanceof EditableAuthenticationProvider)
         .permittedIdentities(
            filterOtherOrgs(identityService.getPermission(rootUser, ResourceType.SECURITY_USER, OrganizationManager.getInstance().getCurrentOrgID(principal), principal)))
         .root(true)
         .build();
   }

   private EditOrganizationPaneModel getRootOrganizationModel(Principal principal,
                                                              AuthenticationProvider currentProvider)
   {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      return EditOrganizationPaneModel.builder()
         .name(Catalog.getCatalog(principal).getString("Organizations"))
         .editable(currentProvider instanceof EditableAuthenticationProvider)
         .permittedIdentities(
            filterOtherOrgs(identityService.getPermission("*", ResourceType.SECURITY_ORGANIZATION, principal)))
         .root(true)
         .currentUserName(pId.name)
         .build();
   }

   public List<IdentityModel> filterOtherOrgs(List<IdentityModel> pList) {
      String thisOrg = OrganizationManager.getCurrentOrgName();
      return pList.stream()
         //must be valid identity, then must be in this org or global role
         .filter(i -> i.identityID().organization == null  ||
            i.identityID().organization.equals(thisOrg))
         .toList();
   }

   /**
    * Edit user model information
    */
   void editUser(EditUserPaneModel model, String providerName,
                 Principal principal) throws Exception
   {
      String rootUser = Catalog.getCatalog(principal).getString("Users");
      IdentityID rootID = new IdentityID(rootUser, model.organization());

      if(rootUser.equals(model.name())) {
         if(securityProvider.checkPermission(
            principal, ResourceType.SECURITY_USER, new IdentityID(rootUser, model.organization()).convertToKey(), ResourceAction.ADMIN) ||
            securityProvider.checkPermission(principal, ResourceType.SECURITY_USER, rootID.convertToKey(), ResourceAction.ADMIN))
         {
            identityService.setIdentityPermissions(
              rootID, rootID, ResourceType.SECURITY_USER, principal, model.permittedIdentities(), "");
         }

         return;
      }

      themeService.updateTheme(model.oldName(), model.name(), model.theme(), CustomTheme::getUsers);

      final AuthenticationProvider provider =
         authenticationProviderService.getProviderByName(providerName);
      IdentityID oldID = new IdentityID(model.oldName(), model.organization());
      IdentityID newID = new IdentityID(model.name(), model.organization());
      final User oldUser = provider.getUser(oldID);
      final IdentityModification userChange =
         systemAdminService.getUserModification(oldUser, model, principal);

      if(!systemAdminService.hasSysAdmin(Collections.singleton(userChange))) {
         throw new MessageException(Catalog.getCatalog().getString("em.security.noSystemAdmin"));
      }
      else if(!systemAdminService.hasOrgAdmin(Collections.singleton(userChange))) {
         throw new MessageException(Catalog.getCatalog().getString("em.security.noSystemAdmin"));
      }

      if(provider instanceof EditableAuthenticationProvider) {
         identityService.setIdentity(oldUser, model, provider, principal);
      }

      // if the user has admin permission on themselves, rename the user in the grant
      List<IdentityModel> permittedIdentities = getRenamedPermittedIdentities(
         model.permittedIdentities(), Identity.USER, oldID, newID);
      identityService.setIdentityPermissions(
         oldID, newID, ResourceType.SECURITY_USER, principal, permittedIdentities, "");
   }


   /**
    * Edit organization model information
    */
   void editOrganization(EditOrganizationPaneModel model, String providerName,
                 Principal principal) throws Exception
   {
      themeService.updateTheme(model.oldName(), model.name(), model.theme(), CustomTheme::getOrganizations);

      final AuthenticationProvider provider =
         authenticationProviderService.getProviderByName(providerName);
      final Organization oldOrg = provider.getOrganization(model.oldName());
      IdentityID oldID = new IdentityID( model.oldName(), model.oldName());
      IdentityID newID = new IdentityID( model.name(), model.name());

      if(!OrganizationManager.getInstance().isSiteAdmin(principal)) {
         checkOrgEditedHasSysAdmin(oldOrg, model, principal);
      }

      final IdentityModification orgChange =
         systemAdminService.getOrganizationModification(oldOrg, model, principal);

      if(!systemAdminService.hasSysAdmin(Collections.singleton(orgChange))) {
         throw new MessageException(Catalog.getCatalog().getString("em.security.noSystemAdmin"));
      }
      if(!systemAdminService.hasOrgAdmin(Collections.singleton(orgChange))) {
         throw new MessageException(Catalog.getCatalog().getString("em.security.noOrgAdmin"));
      }

      if(!oldOrg.getName().equals(model.name()) &&
         (Organization.getDefaultOrganizationName().equals(oldOrg.getName()) ||
            Organization.getTemplateOrganizationName().equals(oldOrg.getName()) ||
            Organization.getSelfOrganizationName().equals(oldOrg.getName())))
      {
         // cannot edit Default Organization name
         throw new MessageException(Catalog.getCatalog().getString("em.security.writeDefaultOrgName"));
      }
      else if(!oldOrg.getId().equalsIgnoreCase(model.id()) &&
              (Organization.getDefaultOrganizationID().equalsIgnoreCase(oldOrg.getId()) ||
               Organization.getTemplateOrganizationID().equalsIgnoreCase(oldOrg.getId()) ||
               Organization.getSelfOrganizationID().equalsIgnoreCase(oldOrg.getId()))) {
         throw new MessageException(Catalog.getCatalog().getString("em.security.writeDefaultOrgId"));
      }

      if(!Tool.equals(oldID, newID) || !Tool.equals(oldOrg.getId(), model.id())) {
         checkDuplicateOrgIDs(model, oldOrg);
      }

      for(PropertyModel property: model.properties()) {
         SreeEnv.setProperty(property.name(), property.value(), true);
      }

      String[] propertyNames = {"max.row.count", "max.col.count", "max.cell.size", "max.user.count"};
      List<String> properties = model.properties().stream().map(p -> p.name()).toList();

      for(String key : propertyNames) {
         if(SreeEnv.getProperty(key, false, true) != null && !properties.contains(key)) {
            SreeEnv.setProperty(key, null, true);
         }
      }

      if(provider instanceof EditableAuthenticationProvider) {
         identityService.setIdentity(oldOrg, model, provider, principal);
      }

      ///permitted Identities
      // if the organization has admin permission on themselves, rename the Organization in the grant
      List<IdentityModel> permittedIdentities = getRenamedPermittedIdentities(
         model.permittedIdentities(), Identity.ORGANIZATION, oldID, newID);

      if(!model.name().equals(model.oldName())) {
         permittedIdentities = handleOrgNameChangePermittedIdentities(model.oldName(), model.name(),
            permittedIdentities);
      }

      identityService.setIdentityPermissions(
         oldID, newID, ResourceType.SECURITY_ORGANIZATION, principal,
         permittedIdentities, model.name(), model.id());
   }

   private void checkDuplicateOrgIDs(EditOrganizationPaneModel model, Organization oldOrg) throws MessageException {
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      String[] organizations = provider.getOrganizations();
      for(int i=0;i< organizations.length; i++) {
         String orgName = organizations[i];
         String orgID = provider.getOrganization(orgName).getId();

         if(orgName != null && !orgName.equals(oldOrg.getName()) && orgName.equalsIgnoreCase(model.name())) {
            throw new MessageException(Catalog.getCatalog().getString("em.duplicateOrganizationName"));
         }
         else if(orgID != null && !orgID.equals(oldOrg.getId()) && orgID.equalsIgnoreCase(model.id())) {
            throw new MessageException(Catalog.getCatalog().getString("em.duplicateOrganizationID"));
         }
      }
   }

   private List<IdentityModel> handleOrgNameChangePermittedIdentities(String oldName, String newOrgname, List<IdentityModel> ids) {
      List<IdentityModel> updatedIds = new ArrayList<>();
      for (IdentityModel id : ids ) {
         if(id.type() == Identity.ROLE || id.type() == Identity.GROUP) {
            IdentityID newName = new IdentityID(id.identityID().name, newOrgname);
            updatedIds.add(IdentityModel.builder().identityID(newName).type(id.type()).parentNode(id.parentNode()).build());
         }
         else {
            updatedIds.add(id);
         }
      }
      return updatedIds;
   }

   private List<IdentityModel> getRenamedPermittedIdentities(List<IdentityModel> permittedIdentities,
                                                             int type, IdentityID oldID,
                                                             IdentityID newID)
   {
      if(oldID == null || oldID.equals(newID)) {
         return permittedIdentities;
      }

      return permittedIdentities.stream().map(identity -> {
         IdentityID updatedID = identity.type() == type && identity.identityID().equals(oldID) ?
            newID : new IdentityID(identity.identityID().name, oldID.organization);
         return IdentityModel.builder()
            .from(identity)
            .identityID(updatedID)
            .build();
      }).collect(Collectors.toList());
   }

   private SecurityTreeNode createSecurityTreeNode(IdentityID userName) {
      return SecurityTreeNode.builder()
         .identityID(userName)
         .label(userName.name)
         .type(Identity.USER)
         .children(Collections.emptyList())
         .root(false)
         .build();
   }

   private SecurityTreeNode createOrgSecurityTreeNode(IdentityID orgID, AuthenticationProvider provider,
                                                      Principal principal, boolean topOnly, boolean isPermissions) {

      //readOnly as false if returning permissions
      boolean readOnly = !isPermissions && !securityProvider.checkPermission(
         principal, ResourceType.SECURITY_ORGANIZATION, orgID.convertToKey(), ResourceAction.ADMIN) &&
         !OrganizationManager.getInstance().isOrgAdmin(principal) && !hasOrgAdminPermission(orgID.name, principal);


      if(topOnly) {
         return SecurityTreeNode.builder()
            .label(orgID.name)
            .identityID(orgID)
            .type(Identity.ORGANIZATION)
            .children(Collections.emptyList())
            .root(false)
            .readOnly(readOnly)
            .build();
      }
      else {
         return SecurityTreeNode.builder()
            .label(orgID.name)
            .identityID(orgID)
            .type(Identity.ORGANIZATION)
            .children(getOrgNodeChildren(orgID.name, isPermissions, provider, principal))
            .root(false)
            .readOnly(readOnly)
            .build();
      }
   }

   private Iterable<? extends SecurityTreeNode> getOrgNodeChildren(String orgName, boolean isPermissions,
                                                                   AuthenticationProvider provider,
                                                                   Principal principal)
   {
      List<SecurityTreeNode> orgMembersList = new ArrayList<SecurityTreeNode>();

      List<SecurityTreeNode> orgUsersList = new ArrayList<SecurityTreeNode>();
      List<SecurityTreeNode> orgGroupsList = new ArrayList<SecurityTreeNode>();
      List<SecurityTreeNode> orgRolesList = new ArrayList<SecurityTreeNode>();

      IdentityID[] users = Arrays.stream(provider.getUsers()).filter(id -> orgName.equals(id.organization)).sorted().toArray(IdentityID[]::new);
      IdentityID[] groups = Arrays.stream(provider.getGroups()).filter(id -> orgName.equals(id.organization)).sorted().toArray(IdentityID[]::new);
      IdentityID[] roles = Arrays.stream(provider.getRoles()).filter(id -> id.organization == null || orgName.equals(id.organization)).sorted().toArray(IdentityID[]::new);

      for(int i=0; i < users.length; i++) {
         User user = provider.getUser(users[i]);

         if(!OrganizationManager.getInstance().isSiteAdmin(principal)) {
            IdentityID userID = user.getIdentityID();
            if(OrganizationManager.getInstance().isSiteAdmin(provider, userID) ||
               !securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
               user.getIdentityID().convertToKey(), ResourceAction.ADMIN))
            {
               continue;
            }
         }

         SecurityTreeNode node = SecurityTreeNode.builder()
            .children(Collections.emptyList())
            .label(user.getName())
            .organization(user.getOrganization())
            .identityID(user.getIdentityID())
            .type(user.getType())
            .root(false)
            .build();
         orgUsersList.add(node);
      }

      Set<IdentityID> childGroups = Arrays.stream(groups)
         .map(provider::getGroup)
         .filter(Objects::nonNull)
         .filter(g -> g.getOrganization().equals(orgName))
         .filter(g -> g.getGroups().length > 0)
         .map(Group::getIdentityID)
         .collect(Collectors.toSet());

      final Map<String, List<Identity>> groupMemberMap = provider.createGroupMemberMap(orgName);

      orgGroupsList = Arrays.stream(groups)
         .filter(g -> orgName.equals(provider.getGroup(g).getOrganization()))
         .filter(group -> !childGroups.contains(group))
         .filter(group -> !"".equals(group.name))
         .sorted()
         .map(groupID -> getGroup(provider, groupMemberMap, groupID, principal))
         .filter(Optional::isPresent)
         .map(Optional::get)
         .collect(Collectors.toList());

      boolean orgAdminOnly = OrganizationManager.getInstance().isOrgAdmin(principal) && !OrganizationManager.getInstance().isSiteAdmin(principal);
      boolean allRolesReadOnly = !OrganizationManager.getInstance().isOrgAdmin(principal) && !OrganizationManager.getInstance().isSiteAdmin(principal) &&
         !securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                   new IdentityID("Organization Roles", OrganizationManager.getCurrentOrgName()).convertToKey(), ResourceAction.ADMIN) &&
         !securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
            Organization.getRootOrgRoleName(principal), ResourceAction.ADMIN) &&
         !securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
            Organization.getRootRoleName(principal), ResourceAction.ADMIN);

      final List<IdentityID> topLevenRoles = Arrays.stream(roles)
         .sorted()
         .filter(r -> {
            Role role = provider.getRole(r);

               if(role == null) {
               return false;
            }

            return role.getRoles() == null || role.getRoles().length == 0;
         })
         .collect(Collectors.toList());

      Map<IdentityID, List<IdentityID>> rolesParentMap = getRolesParentMap(roles, provider);
      getOrgRoleList(orgRolesList, topLevenRoles, rolesParentMap, provider, orgName, orgAdminOnly,
         allRolesReadOnly, principal);
      String label = Catalog.getCatalog(principal).getString("Users");

      //readOnly as false if returning permissions
      boolean readOnly = !isPermissions && !securityProvider.checkPermission(
         principal, ResourceType.SECURITY_USER, new IdentityID(label, orgName).convertToKey(), ResourceAction.ADMIN) &&
         !OrganizationManager.getInstance().isOrgAdmin(principal) && !hasOrgAdminPermission(orgName, principal);

      SecurityTreeNode userRoot = SecurityTreeNode.builder()
         .label(label)
         .identityID(new IdentityID(label, orgName))
         .type(Identity.USER)
         .children(orgUsersList)
         .root(true)
         .organization(orgName)
         .readOnly(readOnly)
         .build();

      orgMembersList.add(userRoot);

      String groupLabel = Catalog.getCatalog(principal).getString("Groups");
      readOnly = !isPermissions && !securityProvider.checkPermission(
         principal, ResourceType.SECURITY_GROUP, new IdentityID(groupLabel, orgName).convertToKey(), ResourceAction.ADMIN) &&
         !OrganizationManager.getInstance().isOrgAdmin(principal) && !hasOrgAdminPermission(orgName, principal);


      SecurityTreeNode groupRoot = SecurityTreeNode.builder()
         .label(groupLabel)
         .identityID(new IdentityID(groupLabel, orgName))
         .type(Identity.GROUP)
         .children(orgGroupsList)
         .root(true)
         .organization(orgName)
         .readOnly(readOnly)
         .build();

      orgMembersList.add(groupRoot);

      String orgRoleLabel = Catalog.getCatalog(principal).getString("Organization Roles");
      readOnly = !isPermissions && !securityProvider.checkPermission(
         principal, ResourceType.SECURITY_ROLE, new IdentityID(orgRoleLabel, orgName).convertToKey(), ResourceAction.ADMIN) &&
         !OrganizationManager.getInstance().isOrgAdmin(principal) && !hasOrgAdminPermission(orgName, principal);

      SecurityTreeNode roleRoot = SecurityTreeNode.builder()
         .label(orgRoleLabel)
         .identityID(new IdentityID(orgRoleLabel, orgName))
         .type(Identity.ROLE)
         .children(orgRolesList)
         .root(true)
         .readOnly(readOnly)
         .organization(orgName)
         .build();

      orgMembersList.add(roleRoot);


      return orgMembersList;
   }

   private void getOrgRoleList(List<SecurityTreeNode> orgRolesList, List<IdentityID> roles,
                               Map<IdentityID, List<IdentityID>> rolesParentMap,
                               AuthenticationProvider provider, String orgName,
                               boolean orgAdminOnly, boolean allRolesReadOnly, Principal principal)
   {
      if(roles == null) {
         return;
      }

      AuthenticationProvider currentProvider = provider;

      if(provider instanceof AuthenticationChain) {
         for(AuthenticationProvider p : ((AuthenticationChain) provider).getProviders()) {
            IdentityID id = IdentityID.getIdentityIDFromKey(principal.getName());

            if(p.getUser(id) != null) {
               currentProvider = p;
               break;
            }
         }
      }

      for(int i = 0; i < roles.size(); i++) {
         Role role = provider.getRole(roles.get(i));
         boolean hasPermission = false;
         boolean permissionExists = securityProvider.checkPermission(principal,
            ResourceType.SECURITY_ROLE, role.getIdentityID().convertToKey(), ResourceAction.ASSIGN);

         if(orgAdminOnly && provider.isOrgAdministratorRole(role.getIdentityID())) {
            if(!currentProvider.isOrgAdministratorRole(role.getIdentityID())) {
               continue;
            }

            hasPermission = true;
         }
         else if(role.getOrganization() != null && role.getOrganization().equals(orgName)) {
            hasPermission = !allRolesReadOnly || permissionExists;
         }

         boolean assignedPermission = !orgAdminOnly && !OrganizationManager.getInstance().isSiteAdmin(principal)
            && permissionExists;

         boolean isOtherOrgRole = role.getOrganization() != null &&
            !role.getOrganization().equals(OrganizationManager.getCurrentOrgName());

         boolean notSiteAdmin = (!OrganizationManager.getInstance().isSiteAdmin(principal) &&
            provider.isSystemAdministratorRole(role.getIdentityID()));

         boolean notOrgAdmin = !OrganizationManager.getInstance().isOrgAdmin(principal) && !OrganizationManager.getInstance().isSiteAdmin(principal) &&
            provider.isOrgAdministratorRole(role.getIdentityID());

         if(isOtherOrgRole || notSiteAdmin || notOrgAdmin || (!hasPermission && !assignedPermission)) {
            continue;
         }

         List<SecurityTreeNode> children = new ArrayList<>();
         getOrgRoleList(children, rolesParentMap.get(roles.get(i)), rolesParentMap, provider,
            orgName, orgAdminOnly, allRolesReadOnly, principal);

         SecurityTreeNode node = SecurityTreeNode.builder()
            .children(children)
            .label(role.getName())
            .identityID(role.getIdentityID())
            .type(role.getType())
            .root(false)
            .readOnly(allRolesReadOnly && !assignedPermission)
            .build();
         orgRolesList.add(node);
      }
   }

   void editRole(EditRolePaneModel model, String providerName,
                 Principal principal) throws Exception
   {
      String rootName = Catalog.getCatalog(principal).getString("Roles");
      String rootOrgName = Catalog.getCatalog(principal).getString("Organization Roles");
      IdentityID oldID = new IdentityID(model.oldName(), model.organization());
      IdentityID rootID = new IdentityID(rootName, model.organization());
      IdentityID rootOrgID = new IdentityID(rootOrgName, model.organization());

      if(model.name().equals(rootName)) {
         if(securityProvider.checkPermission(
            principal, ResourceType.SECURITY_ROLE, rootID.convertToKey(), ResourceAction.ADMIN) ||
            securityProvider.checkPermission(
               principal, ResourceType.SECURITY_ROLE, new IdentityID(model.name(), model.organization()).convertToKey(), ResourceAction.ADMIN))
         {
            identityService.setIdentityPermissions(
               new IdentityID(model.name(), model.organization()), new IdentityID(model.name(), model.organization()),
               ResourceType.SECURITY_ROLE, principal, model.permittedIdentities(),
               model.organization());
         }

         return;
      }
      else if(model.name().equals(rootOrgName)) {
         if(securityProvider.checkPermission(
            principal, ResourceType.SECURITY_ROLE, rootOrgID.convertToKey(), ResourceAction.ADMIN) ||
            securityProvider.checkPermission(
               principal, ResourceType.SECURITY_ROLE, new IdentityID(model.name(), model.organization()).convertToKey(), ResourceAction.ADMIN))
         {
            identityService.setIdentityPermissions(
               rootOrgID, rootOrgID, ResourceType.SECURITY_ROLE, principal, model.permittedIdentities(),
               model.organization());
         }

         return;
      }

      final AuthenticationProvider provider =
         authenticationProviderService.getProviderByName(providerName);
      final Role oldRole = provider.getRole(oldID);
      final IdentityModification roleChange =
         systemAdminService.getRoleModification(oldRole, model, principal);

      if(!systemAdminService.hasSysAdmin(Collections.singleton(roleChange))) {
         throw new MessageException(Catalog.getCatalog().getString("em.security.noSystemAdmin"));
      }
      else if(!systemAdminService.hasOrgAdmin(Collections.singleton(roleChange))) {
         throw new MessageException(Catalog.getCatalog().getString("em.security.noOrgAdmin"));
      }

      themeService.updateTheme(model.oldName(), model.name(), model.theme(), CustomTheme::getRoles);
      identityService.setIdentity(oldRole, model, provider, principal);
   }

   private List<IdentityID> getDefaultRoles(AuthenticationProvider provider, String org) {
      return Arrays.stream(provider.getRoles())
         .filter(role -> org != null && org.equals(provider.getRole(role).getOrganization()))
         .filter(role -> provider.getRole(role).isDefaultRole())
         .collect(Collectors.toList());
   }

   private boolean hasOrgAdminPermission(String organization, Principal principal) {
      String orgId = securityProvider.getOrgId(organization);
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      IdentityID orgID = new IdentityID(organization, organization);
      Permission orgPermissions = securityProvider.getPermission(ResourceType.SECURITY_ORGANIZATION, orgID);
      Set<IdentityID> userGrants = orgPermissions == null ? Collections.emptySet() :
                  orgPermissions.getOrgScopedUserGrants(ResourceAction.ADMIN, orgId);
      return organization != null && orgPermissions != null &&
         userGrants != null && userGrants.contains(pId);
   }

   private void checkOrgEditedHasSysAdmin(Organization oldOrg, EditOrganizationPaneModel model, Principal principal) {
      List<IdentityID> newUsersList = model.members().stream()
         .filter(i -> i.type() == Identity.USER)
         .map(IdentityModel::identityID)
         .toList();
      boolean removedUserAdmin = Arrays.stream(securityProvider.getOrganizationMembers(oldOrg.getName()))
         .map(n -> new IdentityID(n, oldOrg.getName()))
         .filter(n -> securityProvider.getUser(n) != null)
         .filter(u -> securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                        u.convertToKey(), ResourceAction.ADMIN))
         .filter(u -> !newUsersList.contains(u))
         .anyMatch(userID -> OrganizationManager.getInstance().isSiteAdmin(userID));

      List<IdentityID> newGroupList = model.members().stream()
         .filter(i -> i.type() == Identity.GROUP)
         .map(IdentityModel::identityID)
         .toList();
      List<IdentityID> removedGroups = Arrays.stream(securityProvider.getOrganizationMembers(oldOrg.getName()))
         .map(i -> new IdentityID(i, oldOrg.getName()))
         .filter(i -> securityProvider.getGroup(i) != null)
         .filter(g -> !newGroupList.contains(g))
         .toList();

      boolean deletedGroupHasSysAdminChild = checkGroupChildrenSysAdmin(removedGroups);

      if(removedUserAdmin || deletedGroupHasSysAdminChild)
      {
         throw new MessageException(Catalog.getCatalog().getString("em.security.orgAdmin.identityPermissionDenied"));
      }
   }

   private void checkGroupEditedHasSysAdmin(Group oldGroup, EditGroupPaneModel model, Principal principal)
   {
      List<IdentityID> newUsersList = model.members().stream()
         .filter(i -> i.type() == Identity.USER)
         .map(IdentityModel::identityID)
         .toList();
      boolean removedUserAdmin = Arrays.stream(securityProvider.getGroupMembers(oldGroup.getIdentityID()))
         .filter(i -> i.getType() == Identity.USER)
         .filter(u -> !newUsersList.contains(u))
         .anyMatch(u -> {
            IdentityID userID = u.getIdentityID();
            return OrganizationManager.getInstance().isSiteAdmin(userID);
         });

      List<IdentityID> newGroupList = model.members().stream()
         .filter(i -> i.type() == Identity.GROUP)
         .map(IdentityModel::identityID)
         .toList();
      List<IdentityID> removedGroups = Arrays.stream(securityProvider.getGroupMembers(oldGroup.getIdentityID()))
         .filter(i -> i.getType() == Identity.GROUP)
         .map(Identity::getIdentityID)
         .filter(g -> !newGroupList.contains(g))
         .toList();

         boolean deletedGroupHasSysAdminChild = checkGroupChildrenSysAdmin(removedGroups);

         if(removedUserAdmin || deletedGroupHasSysAdminChild)
             {
                throw new MessageException(Catalog.getCatalog().getString("em.security.orgAdmin.identityPermissionDenied"));
         }
   }

   private boolean checkGroupChildrenSysAdmin(List<IdentityID> deletedGroups) {
      boolean sysAdminFound = false;

      for(IdentityID groupName : deletedGroups) {
         Group group = securityProvider.getGroup(groupName);
         List<IdentityID> childUsers = Arrays.stream(group.getGroups())
            .map(u -> new IdentityID(u, group.getOrganization()))
            .filter(u -> securityProvider.getUser(u) != null).toList();
         List<IdentityID> childGroups = Arrays.stream(group.getGroups())
            .map(u -> new IdentityID(u, group.getOrganization()))
            .filter(u -> securityProvider.getUser(u) != null).toList();

         sysAdminFound = sysAdminFound || childUsers.stream().anyMatch(userID -> OrganizationManager.getInstance().isSiteAdmin(userID)) ||
            Arrays.stream(group.getRoles()).anyMatch(securityProvider::isSystemAdministratorRole) ||
            checkGroupChildrenSysAdmin(childGroups);

         if(sysAdminFound) {
            return true;
         }
      }
      return false;
   }

   private final AuthenticationProviderService authenticationProviderService;
   private final SystemAdminService systemAdminService;
   private final IdentityService identityService;
   private SecurityProvider securityProvider;
   private final LocalizationSettingsService localizationSettingsService;
   private final boolean namedUsers;
   private final SecurityEngine securityEngine;
   private final IdentityThemeService themeService;
}
