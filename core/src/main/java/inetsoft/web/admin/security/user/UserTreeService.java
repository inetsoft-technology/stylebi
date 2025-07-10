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

import inetsoft.mv.MVManager;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.security.*;
import inetsoft.storage.KeyValueStorage;
import inetsoft.uql.XFactory;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.DependencyTool;
import inetsoft.uql.erm.HiddenColumns;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import inetsoft.util.audit.*;
import inetsoft.web.admin.favorites.FavoriteList;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.general.model.LocalizationModel;
import inetsoft.web.admin.security.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Service;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class UserTreeService {
   @Autowired
   public UserTreeService(AuthenticationProviderService authenticationProviderService,
                          SystemAdminService systemAdminService,
                          IdentityService identityService,
                          LocalizationSettingsService localizationSettingsService,
                          SecurityEngine securityEngine,
                          IdentityThemeService themeService,
                          SimpMessagingTemplate messagingTemplate)
   {
      this.authenticationProviderService = authenticationProviderService;
      this.systemAdminService = systemAdminService;
      this.identityService = identityService;
      this.localizationSettingsService = localizationSettingsService;
      this.securityEngine = securityEngine;
      this.themeService = themeService;
      this.messagingTemplate = messagingTemplate;
      this.editOrganizationListener = new EditOrganizationListener(messagingTemplate);
   }

   public List<String> getOrganizationTree(String providerName, Principal principal) {
      final AuthenticationProvider provider = providerName == null ?
         getSecurityProvider().getAuthenticationProvider() :
         authenticationProviderService.getProviderByName(providerName);

      final String[] organizations = provider.getOrganizationIDs();

      return Arrays.stream(organizations)
      .sorted()
      .collect(Collectors.toList());
   }

   public SecurityTreeNode getUserRoot(AuthenticationProvider provider, Principal principal,
                                boolean isMultiTenant, String curOrgID, String currOrgName)
   {
      String rootOrgUserKey = new IdentityID("Users", curOrgID).convertToKey();
      boolean readOnly = !getSecurityProvider().checkPermission(
         principal, ResourceType.SECURITY_USER, rootOrgUserKey, ResourceAction.ADMIN) &&
         !OrganizationManager.getInstance().isOrgAdmin(principal);

      List <SecurityTreeNode> userChildren = getFilteredUsers(provider.getUsers(), principal);

      //filter multi-tenant users to this organization only
      if(isMultiTenant) {
         userChildren = userChildren.stream()
            .filter(node -> provider.getUser(node.identityID()).getOrganizationID().equals(curOrgID))
            .collect(Collectors.toList());
      }

      String name = "Users";
      IdentityID id = new IdentityID(name, curOrgID);

      return SecurityTreeNode.builder()
         .label(Catalog.getCatalog(principal).getString(name))
         .identityID(id)
         .organization(currOrgName)
         .type(Identity.USER)
         .children(userChildren)
         .root(!isMultiTenant)
         .readOnly(readOnly)
         .build();
   }

   private List<SecurityTreeNode> getFilteredUsers(IdentityID[] users, Principal principal) {
      return Arrays.stream(users)
         .filter(r -> getSecurityProvider().checkPermission(
            principal, ResourceType.SECURITY_USER, r.convertToKey(), ResourceAction.ADMIN))
         .sorted()
         .map(this::createSecurityTreeNode)
         .collect(Collectors.toList());
   }

   public SecurityTreeNode getGroupRoot(AuthenticationProvider provider, Principal principal,
                                 boolean isMultiTenant, String curOrg)
   {
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
            .filter(node -> provider.getGroup(node.identityID()).getOrganizationID().equals(curOrg))
            .collect(Collectors.toList());
      }

      String name = "Groups";
      String rootGroupKey = new IdentityID(name, curOrg).convertToKey();
      boolean readOnly = !getSecurityProvider().checkPermission(
         principal, ResourceType.SECURITY_GROUP, rootGroupKey, ResourceAction.ADMIN) &&
         !OrganizationManager.getInstance().isOrgAdmin(principal);

      IdentityID id = new IdentityID(name, curOrg);

      return SecurityTreeNode.builder()
         .label(Catalog.getCatalog(principal).getString(name))
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

      boolean readOnly = !getSecurityProvider().checkPermission(
         principal, ResourceType.SECURITY_GROUP, groupName.convertToKey(), ResourceAction.ADMIN);

      if(readOnly && groupChildren.isEmpty() && userChildren.isEmpty()) {
         return Optional.empty();
      }

      return Optional.of(SecurityTreeNode.builder()
         .label(groupName.name)
         .organization(groupName.orgID)
         .identityID(groupName)
         .type(Identity.GROUP)
         .addAllChildren(groupChildren)
         .addAllChildren(userChildren)
         .root(false)
         .readOnly(readOnly)
         .build());
   }

   public SecurityTreeNode getRoleTree(AuthenticationProvider provider, Principal principal,
                                        boolean isMultiTenant, String curOrg)
   {
      final boolean allRolesReadOnly = !(getSecurityProvider().checkPermission(
         principal, ResourceType.SECURITY_ROLE, new IdentityID("Roles", curOrg).convertToKey(), ResourceAction.ADMIN) ||
         getSecurityProvider().checkPermission(
            principal, ResourceType.SECURITY_ROLE, new IdentityID("Organization Roles", curOrg).convertToKey(), ResourceAction.ADMIN));

      final Role[] roles = Arrays.stream(provider.getRoles())
         .sorted()
         .map(provider::getRole)
         .filter(Objects::nonNull)
         .toArray(Role[]::new);

      List <SecurityTreeNode> roleChildren = getRoleChildren(roles, allRolesReadOnly, principal);

      if(!isMultiTenant) {
         //filter any org admin roles that are not sys admin from appearing
         roleChildren = roleChildren.stream()
            .filter(node -> provider.getRole(node.identityID()).getOrganizationID() == null
               || provider.getRole(node.identityID()).getOrganizationID().equals(curOrg))
            .filter(node -> !(!provider.isSystemAdministratorRole(node.identityID()) &&
                             (provider.isOrgAdministratorRole(node.identityID()))))
            .collect(Collectors.toList());
      }
      else if(OrganizationManager.getInstance().isSiteAdmin(principal)) {
            //filter global only
            roleChildren = roleChildren.stream()
               .filter(node -> provider.getRole(node.identityID()).getOrganizationID() == null)
               .collect(Collectors.toList());
         }
      else {
         roleChildren = Collections.emptyList();
      }


      final List<SecurityTreeNode> topChildrenNodes = (roleChildren).stream()
         .filter(r -> {
            Role role = provider.getRole(r.identityID());

            if(role == null) {
               return false;
            }

            return role.getRoles() == null || role.getRoles().length == 0;
         })
         .collect(Collectors.toList());

      return SecurityTreeNode.builder()
         .label(Catalog.getCatalog(principal).getString("Roles"))
         .identityID(new IdentityID("Roles", curOrg))
         .type(Identity.ROLE)
         .children(topChildrenNodes)
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
            boolean hasPermission = !allRolesReadOnly || getSecurityProvider().checkPermission(
               principal, ResourceType.SECURITY_ROLE, role.getIdentityID().convertToKey(), ResourceAction.ASSIGN);
            List<SecurityTreeNode> children =
               getRoleChildren(role.getIdentityID(), allRolesReadOnly,
                               !allRolesReadOnly || hasPermission, parentRoleMap, roleMap,
                               principal);

            //filter out other organizations
            children = children.stream().filter(child -> {
                  Role childRole = getSecurityProvider().getRole(child.identityID());

                  return (childRole != null && (
                  (role.getOrganizationID() == null && childRole.getOrganizationID() == null) ||
                  (role.getOrganizationID() != null && (role.getOrganizationID().equals(childRole.getOrganizationID()) ||
                     childRole.getOrganizationID() == null && OrganizationManager.getInstance().getCurrentOrgID().equals(role.getOrganizationID()))) ||
                  (role.getOrganizationID() == null && OrganizationManager.getInstance().getCurrentOrgID().equals(childRole.getOrganizationID()))));
            })
            .collect(Collectors.toList());

            if((role.getRoles().length == 0 || role.getOrganizationID() == null) &&
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
            (hasPermission = getSecurityProvider().checkPermission(
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
      SecurityProvider securityProvider = SecurityEngine.getSecurity().getSecurityProvider();
      String currOrgId = OrganizationManager.getInstance().getCurrentOrgID();

      if(!securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP,
                                           IdentityID.getIdentityRootResorucePath(currOrgId), ResourceAction.ADMIN))
      {
         return null;
      }

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

         String currOrgID =  OrganizationManager.getInstance().getCurrentOrgID();

         if(provider.getOrganization(currOrgID) == null) {
            throw new InvalidOrgException(Catalog.getCatalog().getString("em.security.invalidOrganizationPassed"));
         }

         FSGroup identity = null;

         for(int i = 0; identity == null; i++) {
            String name = "group" + i;
            IdentityID id = new IdentityID(name, currOrgID);

            if(provider.getGroup(id) == null) {
               identity = new FSGroup(id);
               addOrganizationMember(currOrgID, name, (EditableAuthenticationProvider) provider);

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
            .organization(currOrgID)
            .identityNames(Arrays.stream(provider.getGroups()).toList())
            .members(new ArrayList<>())
            .roles(new ArrayList<>())
            .permittedIdentities(new ArrayList<>())
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
      String orgGroupRoot = "Groups";

      final AuthenticationProvider currentProvider =
         authenticationProviderService.getProviderByName(selectedProvider);

      if(currentProvider.getOrganization(OrganizationManager.getInstance().getCurrentOrgID()) == null) {
         throw new InvalidOrgException(Catalog.getCatalog().getString("em.security.invalidOrganizationPassed"));
      }

      if((!SUtil.isMultiTenant() || !securityEngine.isSecurityEnabled()) && isGroupRoot(groupID, principal) &&
         (getSecurityProvider().checkPermission(principal, ResourceType.SECURITY_GROUP,
                   new IdentityID(orgGroupRoot, OrganizationManager.getInstance().getCurrentOrgID()).convertToKey(), ResourceAction.ADMIN) ||
          getSecurityProvider().checkPermission(principal, ResourceType.SECURITY_GROUP, groupID.convertToKey(), ResourceAction.ADMIN)))
      {
         return getRootGroupModel(principal, currentProvider);
      }

      boolean orgAdmin = OrganizationManager.getInstance().isOrgAdmin(principal);

      if(SUtil.isMultiTenant() && securityEngine.isSecurityEnabled() &&
         isGroupRoot(groupID, principal) && (orgAdmin || hasOrgAdminPermission(OrganizationManager.getInstance().getCurrentOrgID(), principal) ||
         getSecurityProvider().checkPermission(principal, ResourceType.SECURITY_GROUP,
                      new IdentityID(orgGroupRoot, OrganizationManager.getInstance().getCurrentOrgID()).convertToKey(), ResourceAction.ADMIN) ||
         getSecurityProvider().checkPermission(principal, ResourceType.SECURITY_GROUP, orgGroupRoot, ResourceAction.ADMIN)))
      {
         return getRootGroupModelForOrg(principal, OrganizationManager.getInstance().getCurrentOrgID(), currentProvider);
      }

      final Group group = currentProvider.getGroup(groupID);

      if(!OrganizationManager.getInstance().isSiteAdmin(principal)) {
         if(Arrays.stream(group.getRoles()).anyMatch(currentProvider::isSystemAdministratorRole)) {
            throw new MessageException(Catalog.getCatalog().getString("em.security.orgAdmin.identityPermissionDenied"));
         }
      }

      IdentityInfo info = identityService
         .getIdentityInfo(groupID, Identity.GROUP, currentProvider);

      String org = group == null ? null : group.getOrganizationID();
      if(org == null || "".equals(org)) {
         org = Organization.getDefaultOrganizationID();
      }

      String orgID = getSecurityProvider().getOrganization(org).getId();

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
         .name("Groups")
         .label(Catalog.getCatalog(principal).getString("Groups"))
         .editable(currentProvider instanceof EditableAuthenticationProvider)
         .permittedIdentities(
            filterOtherOrgs(identityService.getPermission(new IdentityID("Groups", orgID),
                                                          ResourceType.SECURITY_GROUP, orgID, principal)))
         .root(true)
         .build();
   }

   private EditGroupPaneModel getRootGroupModelForOrg(Principal principal, String orgID,
                                                      AuthenticationProvider currentProvider)
   {
      IdentityID rootGroup = new IdentityID("Groups", orgID);

      return EditGroupPaneModel.builder()
         .name("Groups")
         .label(Catalog.getCatalog(principal).getString("Groups"))
         .organization(orgID)
         .editable(currentProvider instanceof EditableAuthenticationProvider)
         .permittedIdentities(
            filterOtherOrgs(identityService.getPermission(rootGroup,
                                                          ResourceType.SECURITY_GROUP, orgID, principal)))
         .root(true)
         .build();
   }

   private boolean isGroupRoot(IdentityID group, Principal principal) {
      String groupRoot = "Groups";
      String orgGroupRoot = groupRoot + IdentityID.KEY_DELIMITER + OrganizationManager.getInstance().getCurrentOrgID();
      String defOrgGroupRoot = groupRoot + IdentityID.KEY_DELIMITER +Organization.getDefaultOrganizationID();

      return Tool.equals(group.name, groupRoot) || Tool.equals(group.name, orgGroupRoot) || Tool.equals(group.name, defOrgGroupRoot);
   }

   void editGroup(String providerName, IdentityID group, EditGroupPaneModel model, Principal principal)
      throws Exception
   {
      IdentityID oldID = new IdentityID(model.oldName(), model.organization());
      IdentityID newID = new IdentityID(model.name(), model.organization());
      IdentityID root = new IdentityID("Groups", model.organization());

      String currOrgID = OrganizationManager.getInstance().getCurrentOrgID();

      if(SecurityEngine.getSecurity().getSecurityProvider().getOrganization(currOrgID) == null) {
         throw new InvalidOrgException(Catalog.getCatalog().getString("em.security.invalidOrganizationPassed"));
      }

      if(isGroupRoot(new IdentityID(model.name(), group.orgID), principal)) {
         if(getSecurityProvider().checkPermission(principal, ResourceType.SECURITY_GROUP, root.convertToKey(), ResourceAction.ADMIN) ||
            getSecurityProvider().checkPermission(principal, ResourceType.SECURITY_GROUP, new IdentityID(model.name(), model.organization()).convertToKey(), ResourceAction.ADMIN))
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

      themeService.updateTheme(model.oldName(), model.name(), CustomTheme::getGroups);

      // if the group has admin permission on themselves, rename the group in the grant
      List<IdentityModel> permittedIdentities = getRenamedPermittedIdentities(
         model.permittedIdentities(), Identity.GROUP, oldID, newID);
      identityService.setIdentity(oldGroup, model, provider, principal);
      identityService.setIdentityPermissions(oldID, newID, ResourceType.SECURITY_GROUP,
                                             principal, permittedIdentities, "");
      IndexedStorage storage = IndexedStorage.getIndexedStorage();
      DataCycleManager cycleManager = DataCycleManager.getDataCycleManager();
      storage.migrateStorageData(oldID.getName(), newID.getName());
      cycleManager.updateCycleInfoNotify(oldID.getName(), newID.getName(), false);
   }

   /**
    * Create a new user
    */
   EditUserPaneModel createUser(String providerName, String parentGroup, Principal principal) {
      SecurityProvider securityProvider = SecurityEngine.getSecurity().getSecurityProvider();
      String currOrgId = OrganizationManager.getInstance().getCurrentOrgID();

      if(!securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                           IdentityID.getIdentityRootResorucePath(currOrgId), ResourceAction.ADMIN))
      {
         return null;
      }

      ActionRecord actionRecord = SUtil.getActionRecord(
         principal, ActionRecord.ACTION_NAME_CREATE, null,
         ActionRecord.OBJECT_TYPE_USERPERMISSION);
      IdentityInfoRecord identityInfoRecord = null;
      final AuthenticationProvider provider =
         authenticationProviderService.getProviderByName(providerName);
      Principal oldPrincipal = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(principal);
      String currOrgID = OrganizationManager.getInstance().getCurrentOrgID();

      if(SecurityEngine.getSecurity().getSecurityProvider().getOrganization(currOrgID) == null) {
         throw new InvalidOrgException(Catalog.getCatalog().getString("em.security.invalidOrganizationPassed"));
      }

      try {
         if(!(provider instanceof EditableAuthenticationProvider)) {
            return null;
         }

         EditableAuthenticationProvider editProvider = (EditableAuthenticationProvider) provider;
         String prefix = "user";
         FSUser identity;

         for(int i = 0; ; i++) {
            String name = prefix + i;
            IdentityID id = new IdentityID(name, currOrgID);

            if(provider.getUser(id) == null) {
               identity = new FSUser(id);
               identity.setOrganization(currOrgID);
               addOrganizationMember(currOrgID, name, editProvider);
               identity.setRoles(getDefaultRoles(editProvider, currOrgID)
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
            .organization(currOrgID)
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

   private void addOrganizationMember(String orgID, String memberName, EditableAuthenticationProvider provider) {
      Organization org = provider.getOrganization(orgID);
      List<String> members = org.getMembers() != null ? new ArrayList<>(Arrays.asList(org.getMembers())) : new ArrayList<>();
      members.add(memberName);
      org.setMembers(members.toArray(new String[0]));
      provider.setOrganization(orgID, org);
   }

   public EditUserPaneModel getUserModel(String provider, IdentityID userName, Principal principal) {
      AuthenticationProvider currentProvider =
         authenticationProviderService.getProviderByName(provider);
      String rootUser = "Users";
      String orgID = OrganizationManager.getInstance().getCurrentOrgID();

      if(SecurityEngine.getSecurity().getSecurityProvider().getOrganization(orgID) == null) {
         throw new InvalidOrgException(Catalog.getCatalog().getString("em.security.invalidOrganizationPassed"));
      }

      if(rootUser.equals(userName.name) &&
         (getSecurityProvider().checkPermission(principal, ResourceType.SECURITY_USER, new IdentityID(rootUser, OrganizationManager.getInstance().getCurrentOrgID()).convertToKey(), ResourceAction.ADMIN) ||
            getSecurityProvider().checkPermission(principal, ResourceType.SECURITY_USER, userName.convertToKey(), ResourceAction.ADMIN)))
      {
         return getRootUserModel(principal, currentProvider);
      }

      User user = currentProvider.getUser(userName);
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());

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

      String org = user.getOrganizationID();
      if(org == null || "".equals(org)) {
         org = Organization.getDefaultOrganizationID();
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
         .supportChangePassword(Tool.isEmptyString(user.getGoogleSSOId()))
         .build();
   }

   /**
    * Create a new user
    */
   public EditOrganizationPaneModel createOrganization(String copyFromOrgID, String providerName,
                                                       String orgName, String orgID, Principal principal)
   {
      ActionRecord actionRecord = SUtil.getActionRecord(
         principal, ActionRecord.ACTION_NAME_CREATE, null,
         ActionRecord.OBJECT_TYPE_USERPERMISSION);
      IdentityInfoRecord identityInfoRecord = null;
      final AuthenticationProvider provider =
         authenticationProviderService.getProviderByName(providerName);
      Principal oldPrincipal = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(principal);
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      String newOrgId = null;

      try {
         if(!(provider instanceof EditableAuthenticationProvider)) {
            return null;
         }

         EditableAuthenticationProvider editProvider = (EditableAuthenticationProvider) provider;
         SecurityProvider securityProvider = SecurityEngine.getSecurity().getSecurityProvider();
         FSOrganization identity;
         IdentityID newOrgKey = new IdentityID(orgName, orgID);

         if(Tool.isEmptyString(orgID)) {
            String prefix = "organization";
            for(int i = 0; ; i++) {
               final String orgKey = prefix + i;
               boolean found = Arrays.stream(getSecurityProvider().getOrganizationIDs()).anyMatch(o -> o.equalsIgnoreCase(orgKey)) ||
                  Arrays.stream(getSecurityProvider().getOrganizationNames()).anyMatch(o -> o.equalsIgnoreCase(orgKey)) ||
                  getSecurityProvider().getOrgNameFromID(orgKey) != null;

               if(!found) {
                  newOrgKey = new IdentityID(orgKey, orgKey);
                  break;
               }
            }
         }
         else if(editProvider.getOrganization(orgID) != null) {
            //provided org id already exists, return error
            throw new MessageException(Catalog.getCatalog().getString("em.duplicateOrganizationID"));
         }
         else if(editProvider.getOrgIdFromName(orgName) != null) {
            //provided org name already exists, return error
            throw new MessageException(Catalog.getCatalog().getString("em.duplicateOrganizationName"));
         }

         newOrgId = newOrgKey.orgID;
         fireCreateOrganizationEvent(EditOrganizationEvent.STARTED, copyFromOrgID, newOrgId, principal);

         if(copyFromOrgID != null && !Tool.isEmptyString(copyFromOrgID)) {
            Organization fromOrg = provider.getOrganization(copyFromOrgID);
            List<IdentityID> userList = Arrays.stream(provider.getUsers()).filter(user ->
               user.getOrgID().equals(copyFromOrgID)).collect(Collectors.toList());
            int namedUserCount = getNamedUserCount();
            int userCount = provider.getUsers().length + userList.size();

            if(namedUserCount > 0 && userCount > namedUserCount) {
               Catalog catalog = Catalog.getCatalog(ThreadContext.getContextPrincipal());
               throw new MessageException(
                  catalog.getString("em.namedUsers.exceeded", userCount, namedUserCount));
            }

            editProvider.copyOrganization(fromOrg, newOrgKey.orgID, identityService, themeService, principal, false);
            identity = (FSOrganization) editProvider.getOrganization(newOrgKey.orgID);
         }
         else {
            FSOrganization newOrganization = new FSOrganization(newOrgKey);
            editProvider.addOrganization(newOrganization);
            identity = newOrganization;
         }

         List<IdentityModel> defMembers = new ArrayList<IdentityModel>();
         IdentityID[] identityNames = provider.getGroups();
         String state = IdentityInfoRecord.STATE_NONE;
         actionRecord.setObjectName(identity.getId());
         identityInfoRecord = SUtil.getIdentityInfoRecord(new IdentityID(identity.getIdentityID().name,
                                                                         pId.getOrgID()),
                                                          identity.getType(),
                                                          IdentityInfoRecord.ACTION_TYPE_CREATE,
                                                          null, state);

         List<String> localesList = localizationSettingsService.getModel().locales()
            .stream().map(LocalizationModel::label).collect(Collectors.toList());

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
         newOrgId = null;
         throw e;
      }
      finally {
         ThreadContext.setContextPrincipal(oldPrincipal);
         Audit.getInstance().auditAction(actionRecord, principal);

         if(identityInfoRecord != null) {
            Audit.getInstance().auditIdentityInfo(identityInfoRecord, principal);
         }

         //update to new organization
         if(newOrgId != null) {
            OrganizationManager.getInstance().setCurrentOrgID(newOrgId);
         }
      }
   }

   private int getNamedUserCount() {
      LicenseManager manager = LicenseManager.getInstance();
      return manager.getNamedUserCount() + manager.getNamedUserViewerSessionCount();
   }

   public EditOrganizationPaneModel getOrganizationModel(String provider, IdentityID orgID, Principal principal) {
      AuthenticationProvider currentProvider =
         authenticationProviderService.getProviderByName(provider);

      if(currentProvider.getOrganization(OrganizationManager.getInstance().getCurrentOrgID()) == null) {
         throw new InvalidOrgException(Catalog.getCatalog().getString("em.security.invalidOrganizationPassed"));
      }

      if(Catalog.getCatalog(principal).getString("Organizations").equals(orgID.name) &&
         getSecurityProvider().checkPermission(
            principal, ResourceType.SECURITY_ORGANIZATION, "*", ResourceAction.ADMIN))
      {
         return getRootOrganizationModel(principal, currentProvider);
      }

      Organization organization = currentProvider.getOrganization(orgID.orgID);
      List<PropertyModel> properties = new ArrayList<>();
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      Set<Object> keyset = SreeEnv.getProperties().keySet();
      String orgPrefix = "inetsoft.org." + orgID.getOrgID().toLowerCase() + ".";

      for(Object key : keyset) {
         String propName = (String) key;

         if(!(propName).startsWith(orgPrefix)) {
            continue;
         }

         propName = propName.substring(orgPrefix.length());

         if(SreeEnv.getProperty(propName, false, true) != null) {
            properties.add(PropertyModel.builder().name(propName).value(SreeEnv.getProperty(propName, false, true)).build());
         }
      }

      List<IdentityModel> grantedOrganizations =
         identityService.getPermission(orgID, ResourceType.SECURITY_ORGANIZATION, orgID.orgID, principal);

      IdentityInfo info = identityService.getIdentityInfo(orgID, Identity.ORGANIZATION, currentProvider);

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

      List<IdentityModel> members = getOrganizationMembers(info.getMembers(), principal);

      return EditOrganizationPaneModel.builder()
         .name(orgID.name)
         .id(orgID.orgID)
         .status(organization.isActive())
         .locale(locale == null ? "" : locale)
         .identityNames(Arrays.stream(currentProvider.getGroups()).toList())
         .members(members)
         .roles(new ArrayList<>())
         .permittedIdentities(filterOtherOrgs(grantedOrganizations))
         .properties(sortProperties(properties))
         .editable(organization.isEditable() && currentProvider instanceof EditableAuthenticationProvider)
         .currentUser(orgID.name.equals(pId.name))
         .localesList(localesList)
         .theme(organization.getTheme())
         .currentUserName(pId.name)
         .build();
   }

   private EditUserPaneModel getRootUserModel(Principal principal, AuthenticationProvider currentProvider) {
      IdentityID rootUser = new IdentityID("Users", OrganizationManager.getInstance().getCurrentOrgID());
      return EditUserPaneModel.builder()
         .name("Users")
         .label(Catalog.getCatalog(principal).getString("Users"))
         .organization(OrganizationManager.getInstance().getCurrentOrgID())
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
      String thisOrg = OrganizationManager.getInstance().getCurrentOrgID();
      return pList.stream()
         //must be valid identity, then must be in this org or global role
         .filter(i -> i.identityID().orgID == null  ||
            i.identityID().orgID.equals(thisOrg))
         .toList();
   }

   /**
    * Edit user model information
    */
   void editUser(EditUserPaneModel model, String providerName,
                 Principal principal) throws Exception
   {
      String rootUser = "Users";
      IdentityID rootID = new IdentityID(rootUser, model.organization());

      if(rootUser.equals(model.name())) {
         if(getSecurityProvider().checkPermission(
            principal, ResourceType.SECURITY_USER, new IdentityID(rootUser, model.organization()).convertToKey(), ResourceAction.ADMIN) ||
            getSecurityProvider().checkPermission(principal, ResourceType.SECURITY_USER, rootID.convertToKey(), ResourceAction.ADMIN))
         {
            identityService.setIdentityPermissions(
              rootID, rootID, ResourceType.SECURITY_USER, principal, model.permittedIdentities(), "");
         }

         return;
      }

      themeService.updateUserTheme(model.oldName(), model.name(), model.theme());

      final AuthenticationProvider provider =
         authenticationProviderService.getProviderByName(providerName);
      IdentityID oldID = new IdentityID(model.oldName(), model.organization());
      IdentityID newID = new IdentityID(model.name(), model.organization());
      final User oldUser = provider.getUser(oldID);

      if(oldUser == null) {
         throw new MessageException(
            Catalog.getCatalog().getString("em.security.editingUser.not.exist", oldID.getName()));
      }

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
      renameUserAsset(newID, oldID);
   }


   /**
    * Edit organization model information
    */
   void editOrganization(EditOrganizationPaneModel model, String providerName,
                 Principal principal) throws Exception
   {
      final AuthenticationProvider provider =
         authenticationProviderService.getProviderByName(providerName);
      final Organization oldOrg = provider.getOrganization(provider.getOrganizationId(model.oldName()));
      IdentityID oldID = new IdentityID( model.oldName(), provider.getOrganizationId(model.oldName())) ;
      IdentityID newID = new IdentityID( model.name(), model.id());

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
            Organization.getSelfOrganizationName().equals(oldOrg.getName())))
      {
         // cannot edit Default Organization name
         throw new MessageException(Catalog.getCatalog().getString("em.security.writeDefaultOrgName"));
      }
      else if(!oldOrg.getId().equalsIgnoreCase(model.id()) &&
              (Organization.getDefaultOrganizationID().equalsIgnoreCase(oldOrg.getId()) ||
               Organization.getSelfOrganizationID().equalsIgnoreCase(oldOrg.getId()))) {
         throw new MessageException(Catalog.getCatalog().getString("em.security.writeDefaultOrgId"));
      }

      if(!Tool.equals(oldID, newID) || !Tool.equals(oldOrg.getId(), model.id())) {
         checkDuplicateOrgIDs(model, oldOrg);
      }

      boolean saveProperties = false;

      for(PropertyModel property: model.properties()) {
         SreeEnv.setProperty(property.name(), property.value(), true);
         saveProperties = true;
      }

      String[] propertyNames = {"max.row.count", "max.col.count", "max.cell.size", "max.user.count"};
      List<String> properties = model.properties().stream().map(p -> p.name()).toList();

      for(String key : propertyNames) {
         if(SreeEnv.getProperty(key, false, true) != null && !properties.contains(key)) {
            SreeEnv.setProperty(key, null, true);
            saveProperties = true;
         }
      }

      if(saveProperties) {
         SreeEnv.save();
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
         permittedIdentities, model.id());
   }

   private void checkDuplicateOrgIDs(EditOrganizationPaneModel model, Organization oldOrg) throws MessageException {
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      String[] organizations = provider.getOrganizationIDs();
      String[] orgNames = provider.getOrganizationNames();

      for(int i=0;i< organizations.length; i++) {
         String orgName = orgNames[i];
         String orgIDName = organizations[i];
         String orgID = provider.getOrganization(orgIDName).getId();

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
            newID : new IdentityID(identity.identityID().name, oldID.orgID);
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

   public SecurityTreeNode createOrgSecurityTreeNode(IdentityID orgID, String currOrgName,
                                                      AuthenticationProvider provider,
                                                      Principal principal, boolean topOnly,
                                                      boolean isPermissions,
                                                      boolean hideOrgAdminRole)
   {

      //readOnly as false if returning permissions
      boolean readOnly = !isPermissions && !getSecurityProvider().checkPermission(
         principal, ResourceType.SECURITY_ORGANIZATION, orgID.convertToKey(), ResourceAction.ADMIN) &&
         !OrganizationManager.getInstance().isOrgAdmin(principal) && !hasOrgAdminPermission(orgID.orgID, principal);


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
            .children(getOrgNodeChildren(orgID.orgID, currOrgName, isPermissions, provider, principal, hideOrgAdminRole))
            .root(false)
            .readOnly(readOnly)
            .build();
      }
   }

   private Iterable<? extends SecurityTreeNode> getOrgNodeChildren(String orgID, String currOrgName,
                                                                   boolean isPermissions,
                                                                   AuthenticationProvider provider,
                                                                   Principal principal,
                                                                   boolean hideOrgAdminRole)
   {
      List<SecurityTreeNode> orgMembersList = new ArrayList<SecurityTreeNode>();
      List<SecurityTreeNode> orgUsersList = new ArrayList<SecurityTreeNode>();
      List<SecurityTreeNode> orgGroupsList = new ArrayList<SecurityTreeNode>();
      List<SecurityTreeNode> orgRolesList = new ArrayList<SecurityTreeNode>();

      IdentityID[] users = Arrays.stream(provider.getUsers()).filter(id -> orgID.equals(id.orgID)).sorted().toArray(IdentityID[]::new);
      IdentityID[] groups = Arrays.stream(provider.getGroups()).filter(id -> orgID.equals(id.orgID)).sorted().toArray(IdentityID[]::new);
      IdentityID[] roles = Arrays.stream(provider.getRoles()).filter(id -> id.orgID == null || orgID.equals(id.orgID)).sorted().toArray(IdentityID[]::new);

      for(int i=0; i < users.length; i++) {
         User user = provider.getUser(users[i]);

         if(!OrganizationManager.getInstance().isSiteAdmin(principal)) {
            IdentityID userID = user.getIdentityID();
            if(OrganizationManager.getInstance().isSiteAdmin(provider, userID) ||
               !getSecurityProvider().checkPermission(principal, ResourceType.SECURITY_USER,
               user.getIdentityID().convertToKey(), ResourceAction.ADMIN))
            {
               continue;
            }
         }

         SecurityTreeNode node = SecurityTreeNode.builder()
            .children(Collections.emptyList())
            .label(user.getName())
            .organization(currOrgName)
            .identityID(user.getIdentityID())
            .type(user.getType())
            .root(false)
            .build();
         orgUsersList.add(node);
      }

      Set<IdentityID> childGroups = Arrays.stream(groups)
         .map(provider::getGroup)
         .filter(Objects::nonNull)
         .filter(g -> g.getOrganizationID().equals(orgID))
         .filter(g -> g.getGroups().length > 0)
         .map(Group::getIdentityID)
         .collect(Collectors.toSet());

      final Map<String, List<Identity>> groupMemberMap = provider.createGroupMemberMap(orgID);

      orgGroupsList = Arrays.stream(groups)
         .filter(g -> orgID.equals(provider.getGroup(g).getOrganizationID()))
         .filter(group -> !childGroups.contains(group))
         .filter(group -> !"".equals(group.name))
         .sorted()
         .map(groupID -> getGroup(provider, groupMemberMap, groupID, principal))
         .filter(Optional::isPresent)
         .map(Optional::get)
         .collect(Collectors.toList());

      boolean orgAdminOnly = OrganizationManager.getInstance().isOrgAdmin(principal) && !OrganizationManager.getInstance().isSiteAdmin(principal);
      boolean allRolesReadOnly = !OrganizationManager.getInstance().isOrgAdmin(principal) && !OrganizationManager.getInstance().isSiteAdmin(principal) &&
         !getSecurityProvider().checkPermission(principal, ResourceType.SECURITY_ROLE,
                   new IdentityID("Organization Roles", OrganizationManager.getInstance().getCurrentOrgID()).convertToKey(), ResourceAction.ADMIN) &&
         !getSecurityProvider().checkPermission(principal, ResourceType.SECURITY_ROLE,
            Organization.getRootOrgRoleName(principal), ResourceAction.ADMIN) &&
         !getSecurityProvider().checkPermission(principal, ResourceType.SECURITY_ROLE,
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
      getOrgRoleList(orgRolesList, topLevenRoles, rolesParentMap, provider, orgID, orgAdminOnly,
         allRolesReadOnly, hideOrgAdminRole, principal);
      String name = "Users";
      String label = Catalog.getCatalog(principal).getString(name);

      //readOnly as false if returning permissions
      boolean readOnly = !isPermissions && !getSecurityProvider().checkPermission(
         principal, ResourceType.SECURITY_USER, new IdentityID(name, orgID).convertToKey(), ResourceAction.ADMIN) &&
         !OrganizationManager.getInstance().isOrgAdmin(principal) && !hasOrgAdminPermission(orgID, principal);

      SecurityTreeNode userRoot = SecurityTreeNode.builder()
         .label(label)
         .identityID(new IdentityID(name, orgID))
         .type(Identity.USER)
         .children(orgUsersList)
         .root(true)
         .organization(currOrgName)
         .readOnly(readOnly)
         .build();

      orgMembersList.add(userRoot);

      String groupName = "Groups";
      readOnly = !isPermissions && !getSecurityProvider().checkPermission(
         principal, ResourceType.SECURITY_GROUP, new IdentityID(groupName, orgID).convertToKey(), ResourceAction.ADMIN) &&
         !OrganizationManager.getInstance().isOrgAdmin(principal) && !hasOrgAdminPermission(orgID, principal);


      SecurityTreeNode groupRoot = SecurityTreeNode.builder()
         .label(Catalog.getCatalog(principal).getString("Groups"))
         .identityID(new IdentityID(groupName, orgID))
         .type(Identity.GROUP)
         .children(orgGroupsList)
         .root(true)
         .organization(currOrgName)
         .readOnly(readOnly)
         .build();

      orgMembersList.add(groupRoot);

      String orgRoleName = "Organization Roles";
      readOnly = !isPermissions && !getSecurityProvider().checkPermission(
         principal, ResourceType.SECURITY_ROLE, new IdentityID(orgRoleName, orgID).convertToKey(), ResourceAction.ADMIN) &&
         !OrganizationManager.getInstance().isOrgAdmin(principal) && !hasOrgAdminPermission(orgID, principal);

      SecurityTreeNode roleRoot = SecurityTreeNode.builder()
         .label(Catalog.getCatalog(principal).getString("Organization Roles"))
         .identityID(new IdentityID(orgRoleName, orgID))
         .type(Identity.ROLE)
         .children(orgRolesList)
         .root(true)
         .readOnly(readOnly)
         .organization(currOrgName)
         .build();

      orgMembersList.add(roleRoot);


      return orgMembersList;
   }

   private void getOrgRoleList(List<SecurityTreeNode> orgRolesList, List<IdentityID> roles,
                               Map<IdentityID, List<IdentityID>> rolesParentMap,
                               AuthenticationProvider provider, String orgID,
                               boolean orgAdminOnly, boolean allRolesReadOnly,
                               boolean hideOrgAdminRole, Principal principal)
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
         boolean permissionExists = getSecurityProvider().checkPermission(principal,
            ResourceType.SECURITY_ROLE, role.getIdentityID().convertToKey(), ResourceAction.ASSIGN);

         if(orgAdminOnly && provider.isOrgAdministratorRole(role.getIdentityID())) {
            if(!currentProvider.isOrgAdministratorRole(role.getIdentityID())) {
               continue;
            }

            hasPermission = true;
         }
         else if(role.getOrganizationID() != null && role.getOrganizationID().equals(orgID)) {
            hasPermission = !allRolesReadOnly || permissionExists;
         }

         boolean isSiteAdmin = OrganizationManager.getInstance().isSiteAdmin(principal);
         boolean isOrgAdmin = OrganizationManager.getInstance().isOrgAdmin(principal);

         boolean assignedPermission = !orgAdminOnly && !isSiteAdmin && permissionExists;
         boolean isOtherOrgRole = role.getOrganizationID() != null &&
            !role.getOrganizationID().equals(OrganizationManager.getInstance().getCurrentOrgID());
         boolean notSiteAdmin = (!isSiteAdmin && provider.isSystemAdministratorRole(role.getIdentityID()));
         boolean notOrgAdmin = (!isOrgAdmin || hideOrgAdminRole) && !isSiteAdmin &&
            provider.isOrgAdministratorRole(role.getIdentityID());

         if(isOtherOrgRole || notSiteAdmin || notOrgAdmin || (!hasPermission && !assignedPermission)) {
            continue;
         }

         List<SecurityTreeNode> children = new ArrayList<>();
         getOrgRoleList(children, rolesParentMap.get(roles.get(i)), rolesParentMap, provider,
            orgID, orgAdminOnly, allRolesReadOnly, hideOrgAdminRole, principal);

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
      String rootName = "Roles";
      String rootOrgName = "Organization Roles";
      IdentityID oldID = new IdentityID(model.oldName(), model.organization());
      IdentityID rootID = new IdentityID(rootName, model.organization());
      IdentityID rootOrgID = new IdentityID(rootOrgName, model.organization());

      if(model.name().equals(rootName)) {
         if(getSecurityProvider().checkPermission(
            principal, ResourceType.SECURITY_ROLE, rootID.convertToKey(), ResourceAction.ADMIN) ||
            getSecurityProvider().checkPermission(
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
         if(getSecurityProvider().checkPermission(
            principal, ResourceType.SECURITY_ROLE, rootOrgID.convertToKey(), ResourceAction.ADMIN) ||
            getSecurityProvider().checkPermission(
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

      // Bug #71826, for edge case, only reproduce when the server responds very slowly + renaming identity +
      // quickly click "Apply" button more than once("Apply" button not quickly change to disabled
      // becauseof the slow server response)
      if(oldRole == null) {
         return;
      }

      final IdentityModification roleChange =
         systemAdminService.getRoleModification(oldRole, model, principal);

      if(!systemAdminService.hasSysAdmin(Collections.singleton(roleChange))) {
         throw new MessageException(Catalog.getCatalog().getString("em.security.noSystemAdmin"));
      }
      else if(!systemAdminService.hasOrgAdmin(Collections.singleton(roleChange))) {
         throw new MessageException(Catalog.getCatalog().getString("em.security.noOrgAdmin"));
      }

      themeService.updateTheme(model.oldName(), model.name(), CustomTheme::getRoles);
      identityService.setIdentity(oldRole, model, provider, principal);
      renameVPMRole(model.oldName(), model.name());
   }

   private List<IdentityID> getDefaultRoles(AuthenticationProvider provider, String org) {
      return Arrays.stream(provider.getRoles())
         .filter(role -> org != null && org.equals(provider.getRole(role).getOrganizationID()))
         .filter(role -> provider.getRole(role).isDefaultRole())
         .collect(Collectors.toList());
   }

   private boolean hasOrgAdminPermission(String organizationID, Principal principal) {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      IdentityID orgID = new IdentityID(getSecurityProvider().getOrgNameFromID(organizationID), organizationID);
      Permission orgPermissions = getSecurityProvider().getPermission(ResourceType.SECURITY_ORGANIZATION, orgID);
      Set<IdentityID> userGrants = orgPermissions == null ? Collections.emptySet() :
                  orgPermissions.getOrgScopedUserGrants(ResourceAction.ADMIN, organizationID);
      return organizationID != null && orgPermissions != null &&
         userGrants != null && userGrants.contains(pId);
   }

   private void checkOrgEditedHasSysAdmin(Organization oldOrg, EditOrganizationPaneModel model, Principal principal) {
      List<IdentityID> newUsersList = model.members().stream()
         .filter(i -> i.type() == Identity.USER)
         .map(IdentityModel::identityID)
         .toList();
      boolean removedUserAdmin = Arrays.stream(getSecurityProvider().getOrganizationMembers(oldOrg.getId()))
         .map(n -> new IdentityID(n, oldOrg.getId()))
         .filter(n -> getSecurityProvider().getUser(n) != null)
         .filter(u -> getSecurityProvider().checkPermission(principal, ResourceType.SECURITY_USER,
                        u.convertToKey(), ResourceAction.ADMIN))
         .filter(u -> !newUsersList.contains(u))
         .anyMatch(userID -> OrganizationManager.getInstance().isSiteAdmin(userID));

      List<IdentityID> newGroupList = model.members().stream()
         .filter(i -> i.type() == Identity.GROUP)
         .map(IdentityModel::identityID)
         .toList();
      List<IdentityID> removedGroups = Arrays.stream(getSecurityProvider().getOrganizationMembers(oldOrg.getId()))
         .map(i -> new IdentityID(i, oldOrg.getId()))
         .filter(i -> getSecurityProvider().getGroup(i) != null)
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
      boolean removedUserAdmin = Arrays.stream(getSecurityProvider().getGroupMembers(oldGroup.getIdentityID()))
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
      List<IdentityID> removedGroups = Arrays.stream(getSecurityProvider().getGroupMembers(oldGroup.getIdentityID()))
         .filter(i -> i.getType() == Identity.GROUP)
         .map(Identity::getIdentityID)
         .filter(g -> !newGroupList.contains(g))
         .toList();

         boolean deletedGroupHasSysAdminChild = checkGroupChildrenSysAdmin(removedGroups);

         if(removedUserAdmin || deletedGroupHasSysAdminChild) {
            throw new MessageException(Catalog.getCatalog().getString("em.security.orgAdmin.identityPermissionDenied"));
         }
   }

   private boolean checkGroupChildrenSysAdmin(List<IdentityID> deletedGroups) {
      boolean sysAdminFound = false;

      for(IdentityID groupName : deletedGroups) {
         Group group = getSecurityProvider().getGroup(groupName);
         List<IdentityID> childUsers = Arrays.stream(group.getGroups())
            .map(u -> new IdentityID(u, group.getOrganizationID()))
            .filter(u -> getSecurityProvider().getUser(u) != null).toList();
         List<IdentityID> childGroups = Arrays.stream(group.getGroups())
            .map(u -> new IdentityID(u, group.getOrganizationID()))
            .filter(u -> getSecurityProvider().getUser(u) != null).toList();

         sysAdminFound = sysAdminFound || childUsers.stream().anyMatch(userID -> OrganizationManager.getInstance().isSiteAdmin(userID)) ||
            Arrays.stream(group.getRoles()).anyMatch(getSecurityProvider()::isSystemAdministratorRole) ||
            checkGroupChildrenSysAdmin(childGroups);

         if(sysAdminFound) {
            return true;
         }
      }
      return false;
   }

   private void renameVPMRole(String oldName, String newName) throws RemoteException {
      XRepository repository = XFactory.getRepository();
      String orgID = OrganizationManager.getInstance().getCurrentOrgID();
      String[] dataSources = repository.getDataSourceFullNames(new IdentityID(orgID, orgID));

      for(String dataSource : dataSources) {
         XDataModel dataModel = repository.getDataModel(dataSource);

         if(dataModel == null) {
            continue;
         }

         String[] vpms = dataModel.getVirtualPrivateModelNames();

         for(String vpm : vpms) {
            VirtualPrivateModel vm = dataModel.getVirtualPrivateModel(vpm);
            HiddenColumns hiddenColumns = vm.getHiddenColumns();

            if(hiddenColumns == null) {
               continue;
            }

            Enumeration<?> roles = hiddenColumns.getRoles();

            while(roles.hasMoreElements()) {
               Object role = roles.nextElement();

               if(Tool.equals(oldName, role)) {
                  hiddenColumns.removeRole(oldName);
                  hiddenColumns.addRole(newName);
                  break;
               }
            }

            dataModel.addVirtualPrivateModel(vm, true);
         }
      }
   }

   private void renameUserAsset(IdentityID newID, IdentityID oldID) throws Exception {
      if(newID.equals(oldID)) {
         return;
      }

      IndexedStorage storage = IndexedStorage.getIndexedStorage();
      MVManager mvManager = MVManager.getManager();
      DataCycleManager cycleManager = DataCycleManager.getDataCycleManager();
      KeyValueStorage<FavoriteList> favorites =
         SingletonManager.getInstance(KeyValueStorage.class, "emFavorites");

      if(favorites != null && oldID != null && newID != null &&
         favorites.contains(oldID.convertToKey()))
      {
         FavoriteList favoriteList = favorites.remove(oldID.convertToKey()).get();
         favorites.put(newID.convertToKey(), favoriteList);
      }

      storage.migrateStorageData(oldID.getName(), newID.getName());
      mvManager.migrateUserAssetsMV(oldID, newID);
      mvManager.updateMVUser(oldID, newID);
      cycleManager.updateCycleInfoNotify(oldID.getName(), newID.getName(), true);
      DependencyStorageService.getInstance().migrateStorageData(oldID, newID);
   }

   private SecurityProvider getSecurityProvider() {
      return securityEngine.getSecurityProvider();
   }

   private List<IdentityModel> getOrganizationMembers(List<IdentityModel> members,
                                                      Principal principal)
   {
      if(members == null || members.isEmpty() ||
         OrganizationManager.getInstance().isSiteAdmin(principal))
      {
         return members;
      }

      ExecutorService executor =
         Executors.newFixedThreadPool(DependencyTool.getThreadNumber(members.size()));

      List<CompletableFuture<IdentityModel>> futures = members.stream()
         .map(identityModel -> CompletableFuture.supplyAsync(() -> {
            if(identityModel.type() != Identity.USER ||
               getSecurityProvider().checkPermission(principal, ResourceType.SECURITY_USER,
                                                     identityModel.identityID().convertToKey(),
                                                     ResourceAction.ADMIN))
            {
               return identityModel;
            }

            return null;
         }, executor))
         .toList();

      List<IdentityModel> orgMembers = futures.stream()
         .map(CompletableFuture::join)
         .filter(Objects::nonNull)
         .collect(Collectors.toList());

      executor.shutdown();

      return orgMembers;
   }

   private List<PropertyModel> sortProperties(List<PropertyModel> properties) {
      if(properties == null || properties.isEmpty()) {
         return properties;
      }

      properties.sort(new Comparator<PropertyModel>() {
         @Override
         public int compare(PropertyModel o1, PropertyModel o2) {
            boolean foundO1 = propertyNames.contains(o1.name());
            boolean foundO2 = propertyNames.contains(o2.name());

            if(foundO1 && !foundO2) {
               return -1;
            }
            else if(!foundO1 && foundO2) {
               return 1;
            }

            return o1.name().compareTo(o2.name());
         }
      });

      return properties;
   }

   @SubscribeMapping("/create-org-status-changed")
   public void subscribeToTopic() {
   }

   public void fireCreateOrganizationEvent(int status, String fromOrgID, String toOrgID, Principal principal) {
      if(fromOrgID == null) {
         return;
      }

      EditOrganizationEvent event = new EditOrganizationEvent(status, fromOrgID, toOrgID);
      String destination = SUtil.getUserDestination(principal);
      editOrganizationListener.statusChanged(destination, event);
   }

   private final AuthenticationProviderService authenticationProviderService;
   private final SystemAdminService systemAdminService;
   private final IdentityService identityService;
   private final LocalizationSettingsService localizationSettingsService;
   private final SecurityEngine securityEngine;
   private final IdentityThemeService themeService;
   private final SimpMessagingTemplate messagingTemplate;
   private final EditOrganizationListener editOrganizationListener;
   private final Set<String> propertyNames = Set.of("max.row.count", "max.col.count", "max.cell.size", "max.user.count");
}
