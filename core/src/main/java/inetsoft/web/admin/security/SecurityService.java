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
package inetsoft.web.admin.security;

import inetsoft.mv.MVManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.sree.security.db.DatabaseAuthenticationProvider;
import inetsoft.sree.security.ldap.LdapAuthenticationProvider;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.ConnectionProcessor;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import inetsoft.util.audit.*;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.InvalidResourceException;
import inetsoft.web.admin.security.action.ActionPermissionService;
import inetsoft.web.admin.security.action.ActionTreeNode;
import inetsoft.web.admin.security.user.*;
import inetsoft.web.security.auth.MissingResourceException;

import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import inetsoft.web.security.auth.ResourceExistsException;
import inetsoft.web.security.auth.UnauthorizedAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * {@code SecurityService} implements the core identity/permission-grant business logic used by the
 * admin-chat plugin's identities and permissions areas. This is the community-side counterpart of
 * the enterprise Public REST API's {@code SecurityApiService}: the enterprise class delegates its
 * org-agnostic method bodies here, keeping only the multi-org-switch annotations
 * ({@code @SwitchOrg}/{@code @Audited}) and the {@code switchOrganization} entry point itself, since
 * those are meaningful only when multiple organizations exist.
 */
@Service
public class SecurityService {
   @Autowired
   public SecurityService(SecurityEngine securityEngine,
                          IdentityService identityService,
                          ActionPermissionService actionPermissionService,
                          LocalizationSettingsService localizationSettingsService,
                          IdentityThemeService themeService,
                          SystemAdminService systemAdminService,
                          UserTreeService userTreeService,
                          CustomThemesManager customThemesManager)
   {
      this.securityEngine = securityEngine;
      this.identityService = identityService;
      this.actionPermissionService = actionPermissionService;
      this.localizationSettingsService = localizationSettingsService;
      this.themeService = themeService;
      this.systemAdminService = systemAdminService;
      this.userTreeService = userTreeService;
      this.customThemesManager = customThemesManager;
   }

   /**
    * Determines is security is currently enabled.
    *
    * @param user a {@code principal} that identifies the remote user.
    *
    * @return {@code true} if enabled or {@code false} if disabled.
    */
   public boolean isSecurityEnabled(Principal user) throws Exception {
      if(!OrganizationManager.getInstance().isSiteAdmin(user)) {
         throw new UnauthorizedAccessException("Not a site administrator");
      }

      return securityEngine.isSecurityEnabled();
   }

   /**
    * Enables or disables security.
    *
    * @param enabled {@code true} to enable security or {@code false} to disable security.
    * @param user a {@code principal} that identifies the remote user.
    */
   public void setSecurityEnabled(boolean enabled, Principal user) throws Exception{
      if(!OrganizationManager.getInstance().isSiteAdmin(user)) {
         throw new UnauthorizedAccessException("Not a site administrator");
      }

      if(enabled) {
         securityEngine.enableSecurity();
      }
      else {
         securityEngine.disableSecurity();
      }

      SecurityEngine.touch();
   }

   /**
    * Changes the password of a user.
    *
    * @param userID  the name of the user.
    * @param password  the new password of the user.
    * @param principal a principal that identifies the remote user.
    */
   public void changeUserPassword(IdentityID userID, String password, Principal principal)
      throws Exception
   {
      SecurityProvider securityProvider = securityEngine.getSecurityProvider();

      if(!securityProvider.checkPermission(
         principal, ResourceType.SECURITY_USER, userID.convertToKey(), ResourceAction.ADMIN))
      {
         throw new UnauthorizedAccessException("Not an administrator of " + userID);
      }

      EditableAuthenticationProvider provider = getEditableAuthenticationProvider(securityProvider);

      User user = provider.getUser(userID);

      if(user == null) {
         if(securityProvider.getUser(userID) == null) {
            throw new MissingResourceException(userID.name);
         }

         throw new InvalidResourceException();
      }

      IdentityService.validatePasswordStrength(password);

      if(provider instanceof VirtualAuthenticationProvider) {
         SUtil.setPassword((FSUser) user, password);
         provider.addUser(user);
      }
      else {
         provider.changePassword(userID, password);
      }
   }

   public SecurityUserList getUsers(String orgID, Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();

      List<SecurityUser> list = filterPermittedIds(Arrays.asList(securityProvider.getUsers()),
                                                   ResourceType.SECURITY_USER, securityProvider, principal)
         .stream()
         .filter(user -> OrganizationManager.getInstance().isSiteAdmin(principal)
            && (Tool.isEmptyString(orgID) || orgID.equals(user.getOrgID()))
            || !OrganizationManager.getInstance().isSiteAdmin(principal)
            && Tool.equals(user.getOrgID(), OrganizationManager.getInstance().getCurrentOrgID()))
         .map((user) -> getUserModel(user, principal, securityProvider))
         .collect(Collectors.toList());

      SecurityUserList users = new SecurityUserList();
      users.setUsers(list);

      return users;
   }

   public SecurityUser getUser(IdentityID user, Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();

      if(!securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                           user.convertToKey(), ResourceAction.ADMIN))
      {
         throw new UnauthorizedAccessException("Permission denied to get user");
      }

      if(securityProvider.getUser(user) == null) {
         throw new MissingResourceException(user.name);
      }

      return getUserModel(user, principal, securityProvider);
   }

   public void createUser(SecurityUser request, String orgId, Principal principal) throws Exception {
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord record = new ActionRecord(SUtil.getUserName(principal), ActionRecord.ACTION_NAME_CREATE,
         request.getIdentityID().getName(), ActionRecord.OBJECT_TYPE_USERPERMISSION, actionTimestamp,
         ActionRecord.ACTION_STATUS_FAILURE, "");
      IdentityInfoRecord identityInfoRecord = null;

      try {
         SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();
         Set<String> parentGroups = new HashSet<>();

         if(request.getIdentityID() != null && !checkOrganizationExist(request.getIdentityID().getOrgID())) {
            record.setActionError("Not found: " + request.getIdentityID().getOrgID());
            throw new MissingResourceException("Not found: " + request.getIdentityID().getOrgID());
         }

         // Check if api user has permission to create user under any of the groups
         if(request.getGroups() != null) {
            List<IdentityID> groupIds = request.getGroups().stream()
               .map(n -> new IdentityID(n, request.getIdentityID().getOrgID()))
               .collect(Collectors.toList());
            parentGroups = filterPermittedIds(groupIds, ResourceType.SECURITY_GROUP,
                                              securityProvider, principal)
               .stream()
               .map(g -> g.name)
               .collect(Collectors.toSet());
         }

         // Api user must have group permission or root user permission to create user
         if(parentGroups.isEmpty() && !securityProvider.checkPermission(
            principal, ResourceType.SECURITY_USER, getIdentityRootResorucePath(), ResourceAction.ADMIN))
         {
            record.setActionError("Permission denied to create user");
            throw new UnauthorizedAccessException("Permission denied to create user");
         }

         EditableAuthenticationProvider provider = getEditableAuthenticationProvider(securityProvider);

         IdentityID id = setDefaultOrgID(request.getIdentityID());

         if(provider.getUser(id) != null) {
            throw new ResourceExistsException(request.getIdentityID() == null ? null : request.getIdentityID().getName());
         }

         String alias = request.getAlias();
         String locale = validateLocale(request.getLocale());
         boolean active = request.isActive();
         List<String> emails = request.getEmails() == null ?
            new ArrayList<>() : request.getEmails();
         Set<IdentityID> roles = Arrays.stream(provider.getRoles())
            .filter(role -> provider.getRole(role).isDefaultRole())
            .filter(role -> role.getOrgID() == null || role.getOrgID().equalsIgnoreCase(id.orgID))
            .collect(Collectors.toSet());

         // Only add roles if api user has root role permission
         if(securityProvider.checkPermission(
            principal, ResourceType.SECURITY_ROLE, getIdentityRootResorucePath(), ResourceAction.ADMIN))
         {
            if(request.getRoles() != null) {
               roles = filterSystemAdminRoles(request.getRoles(), securityProvider, principal).stream()
                  .filter(role -> provider.getRole(role) != null)
                  .collect(Collectors.toSet());
            }
         }

         IdentityService.validatePasswordStrength(request.getPassword());

         FSUser user = new FSUser(id);
         SUtil.setPassword(user, request.getPassword());
         user.setAlias(alias);
         user.setLocale(locale);
         user.setActive(active);
         user.setEmails(emails.toArray(new String[0]));
         user.setGroups(parentGroups.toArray(new String[0]));
         user.setRoles(roles.toArray(new IdentityID[0]));
         provider.addUser(user);
         String state = IdentityInfoRecord.STATE_ACTIVE;
         identityInfoRecord = SUtil.getIdentityInfoRecord(id, Identity.USER,
            IdentityInfoRecord.ACTION_TYPE_CREATE, null, state);
         user.setOrganization(id.orgID);
         Organization org = provider.getOrganization(id.orgID);
         List<String> members = org.getMembers() != null ? new ArrayList<>(Arrays.asList(org.getMembers())) : new ArrayList<>();
         members.add(id.name);
         org.setMembers(members.toArray(new String[0]));
         provider.setOrganization(id.orgID, org);
         themeService.assignTheme(id.name, id.name, request.getTheme(), CustomTheme::getUsers);
         setIdentityPermissions(id, ResourceType.SECURITY_USER, securityProvider, principal,
            request.getAdminIdentities());
         record.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
         Audit.getInstance().auditIdentityInfo(identityInfoRecord, principal);
      }
      finally {
         Audit.getInstance().auditAction(record, principal);
      }
   }

   public void updateUser(IdentityID id, SecurityUser request, Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();
      EditableAuthenticationProvider provider = getEditableAuthenticationProvider(securityProvider);
      final User oldUser = securityProvider.getUser(id);

      if(!securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                           id.convertToKey(), ResourceAction.ADMIN))
      {
         throw new UnauthorizedAccessException("Permission denied to update user");
      }

      if(provider.getUser(id) == null) {
         if(securityProvider.getUser(id) == null) {
            throw new MissingResourceException(id.name);
         }

         throw new InvalidResourceException();
      }

      // Use the validated org from the path/query, not the request body, so a caller can't
      // smuggle a different org into the body and pull in members from another tenant.
      List<IdentityID> groupIds = request.getGroups() == null ? null : request.getGroups().stream()
         .map(n -> new IdentityID(n, id.orgID)).collect(Collectors.toList());

      List<IdentityModel> parentGroups =
         filterPermittedIds(groupIds, ResourceType.SECURITY_GROUP,
                            securityProvider, principal)
            .stream()
            .map(group -> IdentityModel.builder()
               .identityID(group)
               .type(Identity.GROUP)
               .build())
            .collect(Collectors.toList());

      EditUserPaneModel.Builder builder = EditUserPaneModel.builder()
         .name(request.getIdentityID() == null ? null : request.getIdentityID().getName())
         .oldName(id.name)
         .organization(id.orgID)
         .alias(request.getAlias())
         .locale(toLocaleLabel(request.getLocale()))
         .status(request.isActive() || principal.getName().equals(id.convertToKey()))
         .theme(request.getTheme())
         .members(parentGroups);

      if(request.getEmails() != null) {
         builder.email(String.join(",", request.getEmails()));
      }

      if(request.getRoles() != null) {
         builder = builder.roles(filterSystemAdminRoles(request.getRoles(), securityProvider, principal));
      }

      if(request.getAdminIdentities() != null) {
         builder = builder.permittedIdentities(
            convertAdminIdentitiesModel(request.getAdminIdentities(), securityProvider, principal));
      }

      EditUserPaneModel userModel = builder.build();
      IdentityID oldId = new IdentityID(userModel.oldName(), userModel.organization());
      IdentityID newId = new IdentityID(userModel.name(), userModel.organization());
      identityService.setIdentity(oldUser, userModel, provider, principal);
      themeService.assignTheme(oldUser.getName(), userModel.name(), userModel.theme(), CustomTheme::getUsers);
      identityService.setIdentityPermissions(
         oldId, newId, ResourceType.SECURITY_USER,
         principal, userModel.permittedIdentities(), "");
   }

   public void deleteUser(IdentityID user, Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();
      AuthenticationProvider provider = getEditableAuthenticationProvider(securityProvider);

      if(!securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                           user.convertToKey(), ResourceAction.ADMIN))
      {
         throw new UnauthorizedAccessException("Permission denied to get user");
      }

      if(provider.getUser(user) == null) {
         if(securityProvider.getUser(user) == null) {
            throw new MissingResourceException(user.name);
         }

         throw new InvalidResourceException();
      }

      deleteIdentity(user, Identity.USER, principal, provider);
   }

   private SecurityUser getUserModel(IdentityID userID, Principal principal,
                                     AuthenticationProvider provider)
   {
      if(provider instanceof CompositeSecurityProvider) {
         provider = ((CompositeSecurityProvider) provider).getAuthenticationProvider();
      }

      User user = provider.getUser(userID);
      String locale = user.getLocale();
      boolean active;

      if(provider instanceof AuthenticationChain) {
         boolean activeUser = ((AuthenticationChain) provider).stream()
            .anyMatch(p -> p.getUser(userID) != null && (p instanceof LdapAuthenticationProvider ||
               p instanceof DatabaseAuthenticationProvider));
         active = activeUser || ((AuthenticationChain) provider).stream()
            .map(p -> p.getUser(userID))
            .filter(Objects::nonNull)
            .anyMatch(User::isActive);
      }
      else {
         active = user.isActive();
      }

      SecurityUser userModel = new SecurityUser();
      userModel.setIdentityID(user.getIdentityID());
      userModel.setAlias(user.getAlias());
      userModel.setLocale(locale);
      userModel.setTheme(themeService.getTheme(userID, CustomTheme::getUsers));
      userModel.setActive(active);
      userModel.setEmails(Arrays.asList(user.getEmails()));
      userModel.setGroups(Arrays.asList(user.getGroups()));
      userModel.setRoles(Arrays.asList(user.getRoles()));
      userModel.setAdminIdentities(getIdentityPermissions(userID, ResourceType.SECURITY_USER, principal));

      return userModel;
   }

   public SecurityGroupList getGroups(String orgID, Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();
      List<SecurityGroup> list = filterPermittedIds(Arrays.asList(securityProvider.getGroups()),
                                                    ResourceType.SECURITY_GROUP, securityProvider, principal)
         .stream()
         .filter(group -> OrganizationManager.getInstance().isSiteAdmin(principal)
            && (Tool.isEmptyString(orgID) || orgID.equals(group.getOrgID()))
            || !OrganizationManager.getInstance().isSiteAdmin(principal)
            && Tool.equals(group.getOrgID(), OrganizationManager.getInstance().getCurrentOrgID()))
         .map((group) -> getGroupModel(group, principal, securityProvider))
         .collect(Collectors.toList());

      SecurityGroupList groups = new SecurityGroupList();
      groups.setGroups(list);

      return groups;
   }

   public SecurityGroup getGroup(IdentityID group, Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();
      OrganizationManager orgManager = OrganizationManager.getInstance();
      String callerOrgID = orgManager.getCurrentOrgID(principal);
      boolean isAdminForTargetOrg = orgManager.isSiteAdmin(principal) ||
         (orgManager.isOrgAdmin(principal) && Tool.equals(group.orgID, callerOrgID));

      if(isAdminForTargetOrg && securityProvider.getGroup(group) == null) {
         throw new MissingResourceException(group.name);
      }

      if(!securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP,
                                           group.convertToKey(), ResourceAction.ADMIN))
      {
         throw new UnauthorizedAccessException("Permission denied to get group");
      }

      if(securityProvider.getGroup(group) == null) {
         throw new MissingResourceException(group.name);
      }

      return getGroupModel(group, principal, securityProvider);
   }

   public void createGroup(SecurityGroup request, String orgId, Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();
      Set<String> parentGroups = new HashSet<>();
      String label = "Groups";
      String rootGroupKey = new IdentityID(label, request.getOrgID()).convertToKey();
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord record = new ActionRecord(SUtil.getUserName(principal), ActionRecord.ACTION_NAME_CREATE,
                                             request.getIdentityID().name, ActionRecord.OBJECT_TYPE_USERPERMISSION,
                                             actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE, "");
      IdentityInfoRecord identityInfoRecord = null;

      try {
         if(request.getIdentityID() != null && !checkOrganizationExist(request.getIdentityID().getOrgID())) {
            record.setActionError("Not found: " + request.getIdentityID().getOrgID());
            throw new MissingResourceException("Not found: " + request.getIdentityID().getOrgID());
         }

         // Check if api user has permission to create user under any of the groups
         if(request.getParentGroups() != null) {
            List<IdentityID> parentGroupIds = request.getParentGroups().stream()
               .map(n -> new IdentityID(n, request.getOrgID()))
               .collect(Collectors.toList());
            parentGroups = filterPermittedIds(parentGroupIds, ResourceType.SECURITY_GROUP,
                                              securityProvider, principal)
               .stream()
               .map(g -> g.name)
               .collect(Collectors.toSet());
         }

         // Api user must have permission over the groups or the root to create a group
         if(parentGroups.isEmpty() && !securityProvider.checkPermission(
            principal, ResourceType.SECURITY_GROUP, getIdentityRootResorucePath(), ResourceAction.ADMIN) &&
            !securityProvider.checkPermission(
               principal, ResourceType.SECURITY_GROUP, rootGroupKey, ResourceAction.ADMIN))
         {
            throw new UnauthorizedAccessException("Permission denied to create group");
         }

         EditableAuthenticationProvider provider = getEditableAuthenticationProvider(securityProvider);
         IdentityID identityID = setDefaultOrgID(request.getIdentityID());

         if(provider.getGroup(identityID) != null) {
            throw new ResourceExistsException(request.getIdentityID() == null ? null : request.getIdentityID().getName());
         }

         Set<IdentityID> roles = new HashSet<>();
         List<String> users = request.getMemberUsers();
         List<IdentityID> memberIds = users == null ? new ArrayList<>() :
            users.stream().map(n -> new IdentityID(n, request.getIdentityID().orgID)).collect(Collectors.toList());
         Set<IdentityID> filteredUsers = filterPermittedIds(memberIds,
                                                        ResourceType.SECURITY_USER, securityProvider, principal);
         Set<String> filteredGroups =
            filterMemberGroups(request.getMemberGroups(), parentGroups, securityProvider, principal);

         // Only add roles if api user has root role permission
         if(securityProvider.checkPermission(
            principal, ResourceType.SECURITY_ROLE, getIdentityRootResorucePath(), ResourceAction.ADMIN))
         {
            if(request.getRoles() != null) {
               roles = filterSystemAdminRoles(request.getRoles(), securityProvider, principal).stream()
                  .filter(role -> provider.getRole(role) != null)
                  .collect(Collectors.toSet());
            }
         }

         FSGroup group = new FSGroup(identityID);
         group.setGroups(parentGroups.toArray(new String[0]));
         group.setRoles(roles.toArray(new IdentityID[0]));
         provider.addGroup(group);

         //Assign member users to this group
         for(IdentityID memberUser : filteredUsers) {
            FSUser child = (FSUser) provider.getUser(memberUser);
            List<String> list = new ArrayList<>();
            Collections.addAll(list, child.getGroups());
            list.add(identityID.name);
            child.setGroups(list.toArray(new String[0]));
            provider.setUser(child.getIdentityID(), child);
         }

         //Assign member groups to this group
         for(String memberGroup : filteredGroups) {
            FSGroup child = (FSGroup) provider.getGroup(new IdentityID(memberGroup, setDefaultOrgID(request.getIdentityID()).orgID));
            List<String> list = new ArrayList<>();
            Collections.addAll(list, child.getGroups());
            list.add(identityID.name);
            child.setGroups(list.toArray(new String[0]));
            provider.setGroup(child.getIdentityID(), child);
         }

         String state = IdentityInfoRecord.STATE_ACTIVE;
         identityInfoRecord = SUtil.getIdentityInfoRecord(identityID, Identity.GROUP,
            IdentityInfoRecord.ACTION_TYPE_CREATE, null, state);
         themeService.assignTheme(identityID.name, identityID.name, request.getTheme(), CustomTheme::getGroups);
         setIdentityPermissions(identityID, ResourceType.SECURITY_GROUP, securityProvider, principal,
                                request.getAdminIdentities());
         record.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
         Audit.getInstance().auditIdentityInfo(identityInfoRecord, principal);
      }
      finally {
         Audit.getInstance().auditAction(record, principal);
      }
   }

   public void updateGroup(IdentityID identityID, SecurityGroup request, Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();
      EditableAuthenticationProvider provider = getEditableAuthenticationProvider(securityProvider);
      final Group oldGroup = securityProvider.getGroup(identityID);

      if(!securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP,
                                           identityID.convertToKey(), ResourceAction.ADMIN))
      {
         throw new UnauthorizedAccessException("Permission denied to update group");
      }

      if(provider.getGroup(identityID) == null) {
         if(securityProvider.getGroup(identityID) == null) {
            throw new MissingResourceException(identityID.name);
         }

         throw new InvalidResourceException();
      }

      List<IdentityID> memberIds = null;
      List<IdentityID> memberUserIds = null;

      if(request.getMemberGroups() != null) {
         // Use the validated org from the path/query, not the request body, so a caller can't
         // smuggle a different org into the body and pull in members from another tenant.
         memberIds = request.getMemberGroups().stream()
            .map(n -> new IdentityID(n, identityID.orgID)).collect(Collectors.toList());
      }

      List<IdentityModel> members =
         filterPermittedIds(memberIds, ResourceType.SECURITY_GROUP,
                            securityProvider, principal)
            .stream()
            .map(group -> IdentityModel.builder()
               .identityID(group)
               .type(Identity.GROUP)
               .build())
            .collect(Collectors.toList());

      if(request.getMemberUsers() != null) {
         memberUserIds = request.getMemberUsers().stream()
            .map(n -> new IdentityID(n, identityID.orgID)).collect(Collectors.toList());
      }

      members.addAll(
         filterPermittedIds(memberUserIds, ResourceType.SECURITY_USER,
                            securityProvider, principal)
            .stream()
            .map(user -> IdentityModel.builder()
               .identityID(user)
               .type(Identity.USER)
               .build())
            .collect(Collectors.toList()));

      EditGroupPaneModel.Builder builder = EditGroupPaneModel.builder()
         .name(request.getIdentityID() == null ? null : request.getIdentityID().getName())
         .oldName(identityID.name)
         .theme(request.getTheme())
         .organization(identityID.orgID)
         .members(members);

      if(request.getRoles() != null) {
         builder.roles(filterSystemAdminRoles(request.getRoles(), securityProvider, principal));
      }

      if(request.getAdminIdentities() != null) {
         builder = builder.permittedIdentities(
            convertAdminIdentitiesModel(request.getAdminIdentities(), securityProvider, principal));
      }

      EditGroupPaneModel groupModel = builder.build();
      IdentityID oldId = new IdentityID(groupModel.oldName(), groupModel.organization());
      IdentityID newId = new IdentityID(groupModel.name(), groupModel.organization());

      identityService.setIdentity(oldGroup, groupModel, provider, principal);
      themeService.assignTheme(oldGroup.getName(), groupModel.name(), groupModel.theme(), CustomTheme::getGroups);
      identityService.setIdentityPermissions(
         oldId, newId, ResourceType.SECURITY_GROUP,
         principal, groupModel.permittedIdentities(), identityID.orgID);
      // oldId/newId already carry the validated org (from groupModel.organization()); using the
      // raw request body identity here would let a caller move the group into another org.
      updateParentGroups(oldId, newId, request.getParentGroups(), securityProvider, principal);
   }

   public void deleteGroup(IdentityID group, Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();
      AuthenticationProvider provider = getEditableAuthenticationProvider(securityProvider);

      if(!securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP,
                                           group.convertToKey(), ResourceAction.ADMIN))
      {
         throw new UnauthorizedAccessException("Permission denied to get group");
      }

      if(provider.getGroup(group) == null) {
         if(securityProvider.getGroup(group) == null) {
            throw new MissingResourceException(group.name);
         }

         throw new InvalidResourceException();
      }

      deleteIdentity(group, Identity.GROUP, principal, provider);
   }

   private SecurityGroup getGroupModel(IdentityID identityID, Principal principal,
                                       AuthenticationProvider provider)
   {
      final Group group = provider.getGroup(identityID);
      IdentityInfo info = identityService.getIdentityInfo(identityID, Identity.GROUP, provider);

      List<String> memberUsers = info.getMembers().stream()
         .filter(id -> id.type() == Identity.USER)
         .map(u -> u.identityID().name)
         .collect(Collectors.toList());
      List<String> memberGroups = info.getMembers().stream()
         .filter(id -> id.type() == Identity.GROUP)
         .map(u -> u.identityID().name)
         .collect(Collectors.toList());

      SecurityGroup groupModel = new SecurityGroup();
      groupModel.setIdentityID(identityID);
      groupModel.setTheme(themeService.getTheme(identityID, CustomTheme::getGroups));
      groupModel.setParentGroups(Arrays.asList(group.getGroups()));
      groupModel.setMemberUsers(memberUsers);
      groupModel.setMemberGroups(memberGroups);
      groupModel.setRoles(Arrays.asList(group.getRoles()));
      groupModel.setAdminIdentities(getIdentityPermissions(identityID, ResourceType.SECURITY_GROUP, principal));

      return groupModel;
   }

   private Set<String> filterMemberGroups(List<String> memberGroups, Set<String> parents,
                                          SecurityProvider securityProvider, Principal principal) {
      List<IdentityID> memberGroupIds = memberGroups == null ? new ArrayList<>() : memberGroups.stream()
         .map(u -> new IdentityID(u, IdentityID.getIdentityIDFromKey(principal.getName()).orgID)).collect(Collectors.toList());
      return filterPermittedIds(memberGroupIds,
                                ResourceType.SECURITY_GROUP, securityProvider, principal)
         .stream()
         .filter(member -> parents.stream()
            .allMatch(parent -> checkCircularMembership(member, parent, securityProvider)))
         .map(m -> m.name)
         .collect(Collectors.toSet());
   }

   private void updateParentGroups(IdentityID oldGroupID, IdentityID groupID, List<String> parents,
                                   SecurityProvider securityProvider,
                                   Principal principal) throws Exception
   {
      EditableAuthenticationProvider provider = getEditableAuthenticationProvider(securityProvider);
      List<IdentityID> parentIds = null;

      if(parents != null) {
         parentIds = parents.stream()
            .map(i -> new IdentityID(i,groupID.orgID)).collect(Collectors.toList());
      }

      Set<String> filteredParents =
         filterPermittedIds(parentIds, ResourceType.SECURITY_GROUP, securityProvider, principal)
            .stream()
            .filter(parent -> checkCircularMembership(groupID, parent.name, securityProvider))
            .map(id -> id.name)
            .collect(Collectors.toSet());

      FSGroup group = (FSGroup) provider.getGroup(oldGroupID);

      if(!oldGroupID.equals(groupID)) {
         FSGroup newGroup = new FSGroup(groupID);
         FSGroup newGroup2 = (FSGroup) provider.getGroup(groupID);

         if(newGroup2 != null) {
            provider.removeGroup(oldGroupID);
            newGroup = newGroup2;
         }
         else if(group != null) {
            newGroup.setGroups(group.getGroups());
            newGroup.setRoles(group.getRoles());
            provider.removeGroup(oldGroupID);
         }

         group = newGroup;
      }

      filteredParents.addAll(Arrays.asList(group.getGroups()));
      group.setGroups(filteredParents.toArray(new String[0]));
      provider.setGroup(group.getIdentityID(), group);
   }

   private boolean checkCircularMembership(IdentityID groupId, String currName,
                                           SecurityProvider provider)
   {
      if(currName == null) {
         return true;
      }
      else if(currName.equals(groupId.name)) {
         return false;
      }

      Group group = provider.getGroup(new IdentityID(currName, groupId.orgID));
      String[] parents = group.getGroups();

      return Arrays.stream(parents)
         .allMatch(parent -> checkCircularMembership(groupId, parent, provider));
   }

   public SecurityOrganizationList getOrganizations(Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();
      List<IdentityID> ids = Arrays.stream(securityProvider.getOrganizationNames())
         .map(n -> new IdentityID(n, securityProvider.getOrganizationId(n))).collect(Collectors.toList());
      List<SecurityOrganization> list = filterPermittedIds(ids,
                                                    ResourceType.SECURITY_ORGANIZATION, securityProvider, principal)
         .stream()
         .map((role) -> getOrganizationModel(role, principal, securityProvider))
         .collect(Collectors.toList());

      SecurityOrganizationList organizations = new SecurityOrganizationList();
      organizations.setOrganizations(list);

      return organizations;
   }

   public SecurityOrganization getOrganization(String organizationid, Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();

      if(!securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                           organizationid, ResourceAction.ADMIN))
      {
         throw new UnauthorizedAccessException("Permission denied to get Organization");
      }

      if(securityProvider.getOrganization(organizationid) == null) {
         throw new MissingResourceException(organizationid);
      }

      return getOrganizationModel(new IdentityID(securityProvider.getOrganization(organizationid).getName(), organizationid), principal, securityProvider);
   }

   public void createOrganization(SecurityOrganization request, String copyFromOrgID,
                                  Principal principal)
      throws Exception
   {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();
      EditableAuthenticationProvider provider = getEditableAuthenticationProvider(securityProvider);
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord record = null;
      IdentityInfoRecord identityInfoRecord = null;

      try {
         if(!OrganizationManager.getInstance().isSiteAdmin(principal)) {
            throw new UnauthorizedAccessException("Permission denied to create Organization");
         }

         if(provider.getOrganization(request.getId()) != null ||
            provider.getOrgIdFromName(request.getName()) != null)
         {
            throw new ResourceExistsException(request.getName());
         }

         // Validated ahead of both branches so an invalid code is rejected consistently whether or
         // not copyFrom was supplied, and before the ActionRecord exists -- this method's finally
         // block stamps ACTION_STATUS_SUCCESS unconditionally, so a throw after that point would
         // be audited as a successful create.
         String orgLocale = validateLocale(request.getLocale());

         if(!Tool.isEmptyString(copyFromOrgID)) {
            userTreeService.createOrganization(
               copyFromOrgID, provider.getProviderName(), request.getId(), request.getName(), principal, request.getDefaultPassword());
            return;
         }
         else {
            record = new ActionRecord(SUtil.getUserName(principal), ActionRecord.ACTION_NAME_CREATE,
                                      request.getName(), ActionRecord.OBJECT_TYPE_USERPERMISSION,
                                      actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE, "");
         }

         String name = request.getName();
         String oid = request.getId();
         List<String> requestUsers = request.getMemberUsers() == null ?
            Collections.emptyList() : request.getMemberUsers();
         List<String> requestGroups = request.getMemberGroups() == null ?
            Collections.emptyList() : request.getMemberGroups();
         List<String> requestRoles = request.getRoles() == null ?
            Collections.emptyList() : request.getRoles();
         List<IdentityID> memberUserIds = requestUsers.stream()
            .map(n -> new IdentityID(n, request.getName())).collect(Collectors.toList());
         List<IdentityID> memberGroupIds = requestGroups.stream()
            .map(n -> new IdentityID(n, request.getName())).collect(Collectors.toList());
         List<IdentityID> memberRoleIds = requestRoles.stream()
            .map(n -> new IdentityID(n, request.getName())).collect(Collectors.toList());

         List<String> members = new ArrayList<>();
         members.addAll(memberUserIds.stream().map(id -> id.name).toList());
         members.addAll(memberGroupIds.stream().map(id -> id.name).toList());
         members.addAll(memberRoleIds.stream().map(id -> id.name).toList());

         FSOrganization organization = new FSOrganization(oid);
         organization.setName(name);
         organization.setMembers(members.toArray(new String[0]));
         organization.setLocale(orgLocale);
         Set<CustomTheme> themes = new HashSet<>(customThemesManager.getCustomThemes());
         CustomTheme currentTheme = themes.stream()
            .filter(t -> (t.getId().equals(request.getTheme()) && t.getOrgID() == null) ||
               (t.getOrgID() != null && t.getOrgID().equals(name)))
            .findFirst()
            .orElse(null);
         organization.setTheme(currentTheme == null ? null : currentTheme.getName());
         provider.addOrganization(organization);
         applyOrganizationProperties(oid, request.getProperties());

         for(IdentityID memberUser : memberUserIds) {
            FSUser user = new FSUser(memberUser);
            SUtil.setPassword(user, "success123");
            provider.addUser(user);
         }

         for(IdentityID memberGroup : memberGroupIds) {
            FSGroup group = new FSGroup(memberGroup);
            provider.addGroup(group);
         }

         for(IdentityID memberGroup : memberRoleIds) {
            FSRole role = new FSRole(memberGroup);
            provider.addRole(role);
         }

         String state = IdentityInfoRecord.STATE_ACTIVE;
         identityInfoRecord = SUtil.getIdentityInfoRecord(organization.getIdentityID(), Identity.ORGANIZATION,
                                                          IdentityInfoRecord.ACTION_TYPE_CREATE, null, state);
         themeService.assignTheme(oid, oid, request.getTheme(), CustomTheme::getOrganizations);

         if(request.getTheme() != null) {
            customThemesManager.setOrgSelectedTheme(request.getTheme(), oid);
         }

         setIdentityPermissions(new IdentityID(request.getId(), request.getId()),
                                ResourceType.SECURITY_ORGANIZATION, securityProvider, principal,
                                request.getAdminIdentities());
      }
      finally {
         if(record != null) {
            record.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
            Audit.getInstance().auditAction(record, principal);
         }

         if(identityInfoRecord != null) {
            Audit.getInstance().auditIdentityInfo(identityInfoRecord, principal);
         }
      }
   }

   public void updateOrganization(String id, SecurityOrganization request, Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();
      EditableAuthenticationProvider provider = getEditableAuthenticationProvider(securityProvider);
      final Organization oldOrganization = securityProvider.getOrganization(id);

      if(!securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                           id, ResourceAction.ADMIN))
      {
         throw new UnauthorizedAccessException("Permission denied to update organization");
      }

      if(provider.getOrganization(id) == null) {
         if(securityProvider.getOrganization(id) == null) {
            throw new MissingResourceException(id);
         }

         throw new InvalidResourceException();
      }

      List<String> requestGroups = request.getMemberUsers() == null ?
         Collections.emptyList() : request.getMemberGroups();
      List<IdentityID> memberGroupIds = requestGroups.stream()
         .map(n -> new IdentityID(n, id)).collect(Collectors.toList());

      List<IdentityModel> members =
         filterPermittedIds(memberGroupIds, ResourceType.SECURITY_GROUP,
                            securityProvider, principal, true)
            .stream()
            .map(group -> IdentityModel.builder()
               .identityID(new IdentityID(group.name, request.getId()))
               .type(Identity.GROUP)
               .build())
            .collect(Collectors.toList());

      List<String> requestUsers = request.getMemberUsers() == null ?
         Collections.emptyList() : request.getMemberUsers();
      List<IdentityID> memberUserIds = requestUsers.stream()
         .map(n -> new IdentityID(n, id)).collect(Collectors.toList());

      members.addAll(
         filterPermittedIds(memberUserIds, ResourceType.SECURITY_USER,
                            securityProvider, principal, true)
            .stream()
            .map(user -> IdentityModel.builder()
               .identityID(new IdentityID(user.name, request.getId()))
               .type(Identity.USER)
               .build())
            .collect(Collectors.toList()));

      List<String> requestRoles = request.getRoles() == null ?
         Collections.emptyList() : request.getRoles();
      List<IdentityID> memberRoleIds = requestRoles.stream()
         .map(n -> new IdentityID(n, id)).collect(Collectors.toList());

      members.addAll(
         filterPermittedIds(memberRoleIds, ResourceType.SECURITY_ROLE,
                            securityProvider, principal, true)
            .stream()
            .map(role -> IdentityModel.builder()
               .identityID(new IdentityID(role.name, request.getId()))
               .type(Identity.ROLE)
               .build())
            .collect(Collectors.toList()));

      EditOrganizationPaneModel.Builder builder = EditOrganizationPaneModel.builder()
         .name(request.getName())
         .id(request.getId())
         .oldName(oldOrganization.getName())
         .locale(toLocaleLabel(request.getLocale()))
         .theme(request.getTheme())
         .members(members);

      if(request.getRoles() != null) {
         List<IdentityID> roles = request.getRoles().stream()
            .map(role -> new IdentityID(role, request.getId())).collect(Collectors.toList());
         builder.roles(roles);
      }

      if(request.getAdminIdentities() != null) {
         builder = builder.permittedIdentities(
            convertAdminIdentitiesModel(request.getAdminIdentities(), securityProvider, principal));
      }

      EditOrganizationPaneModel organizationModel = builder.build();
      IdentityID oldId = new IdentityID(organizationModel.oldName(), provider.getOrgIdFromName(organizationModel.oldName()));
      IdentityID newId = new IdentityID(organizationModel.name(), organizationModel.id());

      identityService.setIdentity(oldOrganization, organizationModel, provider, principal);
      applyOrganizationProperties(id, request.getProperties());
      identityService.setIdentityPermissions(
         oldId, newId, ResourceType.SECURITY_ORGANIZATION,
         principal, organizationModel.permittedIdentities(), "");
   }

   public void deleteOrganization(String organizationid, Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();
      AuthenticationProvider provider = getEditableAuthenticationProvider(securityProvider);

      if(!securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                           organizationid, ResourceAction.ADMIN))
      {
         throw new UnauthorizedAccessException("Permission denied to get organization");
      }

      if(provider.getOrganization(organizationid) == null) {
         if(securityProvider.getOrganization(organizationid) == null) {
            throw new MissingResourceException(organizationid);
         }

         throw new InvalidResourceException();
      }

      if(organizationid.equalsIgnoreCase(Organization.getDefaultOrganizationID())) {
         throw new Exception(Catalog.getCatalog().getString("em.security.delDefOrg"));
      }

      if(organizationid.equalsIgnoreCase(Organization.getSelfOrganizationID())) {
         throw new Exception(Catalog.getCatalog().getString("em.security.delSelfOrg"));
      }

      deleteIdentity(new IdentityID(provider.getOrgNameFromID(organizationid), organizationid), Identity.ORGANIZATION, principal, provider);
   }

   private SecurityOrganization getOrganizationModel(IdentityID identityID, Principal principal,
                                       AuthenticationProvider provider)
   {
      final Organization organization = provider.getOrganization(identityID.orgID);
      IdentityInfo info = identityService.getIdentityInfo(identityID, Identity.ORGANIZATION, provider);

      List<String> memberUsers = info.getMembers().stream()
         .filter(id -> id.type() == Identity.USER)
         .map(IdentityModel::identityID)
         .map(id -> id.name)
         .collect(Collectors.toList());
      List<String> memberGroups = info.getMembers().stream()
         .filter(id -> id.type() == Identity.GROUP)
         .map(IdentityModel::identityID)
         .map(id -> id.name)
         .collect(Collectors.toList());
      List<String> memberRoles = info.getMembers().stream()
         .filter(id -> id.type() == Identity.ROLE)
         .map(IdentityModel::identityID)
         .map(id -> id.name)
         .collect(Collectors.toList());

      SecurityOrganization organizationModel = new SecurityOrganization();
      organizationModel.setName(identityID.name);
      organizationModel.setTheme(themeService.getTheme(identityID, CustomTheme::getRoles));
      organizationModel.setId(identityID.orgID);
      organizationModel.setTheme(themeService.getTheme(identityID.orgID, CustomTheme::getOrganizations));
      organizationModel.setLocale(info.getLocale());
      organizationModel.setMemberUsers(memberUsers);
      organizationModel.setMemberGroups(memberGroups);
      organizationModel.setRoles(memberRoles);
      organizationModel.setAdminIdentities(getIdentityPermissions(identityID, ResourceType.SECURITY_ORGANIZATION, principal));
      organizationModel.setProperties(readOrganizationProperties(identityID.orgID));

      return organizationModel;
   }

   /**
    * Reads the org-scoped property overrides written by {@link #applyOrganizationProperties} back
    * out of the shared global property store, mirroring {@code UserTreeService.
    * getOrganizationModel}'s own identical scan (not shared with it: that method belongs to a
    * different, EM-UI-only code path with its own model shape).
    */
   private List<PropertyModel> readOrganizationProperties(String orgId) {
      List<PropertyModel> properties = new ArrayList<>();
      String orgPrefix = "inetsoft.org." + orgId.toLowerCase() + ".";

      try {
         // Mirrors applyOrganizationProperties's own runInOrgScope wrap: SreeEnv.getProperty's
         // org-scoped re-lookup below resolves "current org" from OrganizationManager, not from
         // orgId directly, so without this scope it silently reads back the caller's own org's
         // properties instead of orgId's.
         OrganizationManager.runInOrgScope(orgId, () -> {
            for(Object key : SreeEnv.getProperties().keySet()) {
               String propName = (String) key;

               if(!propName.startsWith(orgPrefix)) {
                  continue;
               }

               propName = propName.substring(orgPrefix.length());
               String value = SreeEnv.getProperty(propName, false, true);

               if(value != null) {
                  properties.add(PropertyModel.builder().name(propName).value(value).build());
               }
            }

            return null;
         });
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }

      return properties;
   }

   /**
    * Writes an organization's property overrides into the shared global property store, mirroring
    * {@code UserTreeService.editOrganization}'s own identical write (same not-shared reasoning as
    * {@link #readOrganizationProperties}). {@code null} means "not specified" (leave every existing
    * property untouched); a non-null list is de-duplicated by last-write-wins per name, then written
    * verbatim, with the same 4 named quota keys (max.row.count/max.col.count/max.cell.size/
    * max.user.count) cleared if currently set but absent from the list -- EM parity for this
    * general-purpose method. (The identities admin-chat create/update path never lets that clearing
    * fire in practice: {@code IdentityMerge.mergeOrganization} always re-includes each quota key's
    * current value into the caller's list first, per this bug's own confirmed human decision to NOT
    * replicate that clear-on-omission behavior for that path.)
    */
   private void applyOrganizationProperties(String orgId, List<PropertyModel> properties)
      throws Exception
   {
      if(properties == null) {
         return;
      }

      Map<String, PropertyModel> deduped = new LinkedHashMap<>();

      for(PropertyModel property : properties) {
         deduped.put(property.name(), property);
      }

      List<PropertyModel> ordered = new ArrayList<>(deduped.values());

      OrganizationManager.runInOrgScope(orgId, () -> {
         boolean saveProperties = false;

         for(PropertyModel property : ordered) {
            SreeEnv.setProperty(property.name(), property.value(), true);
            saveProperties = true;
         }

         String[] quotaKeys =
            { "max.row.count", "max.col.count", "max.cell.size", "max.user.count" };

         for(String key : quotaKeys) {
            if(SreeEnv.getProperty(key, false, true) != null && !deduped.containsKey(key)) {
               SreeEnv.setProperty(key, null, true);
               saveProperties = true;
            }
         }

         if(saveProperties) {
            SreeEnv.save();
         }

         return null;
      });
   }

   public SecurityRoleList getRoles(String orgID, Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();
      List<SecurityRole> list = filterPermittedIds(Arrays.asList(securityProvider.getRoles()),
                                                   ResourceType.SECURITY_ROLE, securityProvider, principal)
         .stream()
         .filter(role -> OrganizationManager.getInstance().isSiteAdmin(principal)
            && (Tool.isEmptyString(orgID) || orgID.equals(role.getOrgID()))
            || !OrganizationManager.getInstance().isSiteAdmin(principal)
            && Tool.equals(role.getOrgID(), OrganizationManager.getInstance().getCurrentOrgID()))
         .map((role) -> getRoleModel(role, principal, securityProvider))
         .collect(Collectors.toList());

      SecurityRoleList roles = new SecurityRoleList();
      roles.setRoles(list);

      return roles;
   }

   public SecurityRole getRole(IdentityID role, Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();

      if(!securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                           role.convertToKey(), ResourceAction.ADMIN))
      {
         throw new UnauthorizedAccessException("Permission denied to get user");
      }

      if(securityProvider.getRole(role) == null) {
         throw new MissingResourceException(role.name);
      }

      return getRoleModel(role, principal, securityProvider);
   }

   public void createRole(SecurityRole request, String orgId, Principal principal)
      throws Exception
   {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();
      IdentityID user = IdentityID.getIdentityIDFromKey(principal.getName());
      String resource = getIdentityRootResorucePath();
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord record = new ActionRecord(SUtil.getUserName(principal), ActionRecord.ACTION_NAME_CREATE,
         request.getIdentityID().getName(), ActionRecord.OBJECT_TYPE_USERPERMISSION, actionTimestamp,
         ActionRecord.ACTION_STATUS_FAILURE, "");
      IdentityInfoRecord identityInfoRecord = null;

      try {
         if(request.getIdentityID() != null && !checkOrganizationExist(request.getIdentityID().getOrgID())) {
            record.setActionError("Not found: " + request.getIdentityID().getOrgID());
            throw new MissingResourceException("Not found: " + request.getIdentityID().getOrgID());
         }

         if(!Tool.equals(user.getOrgID(), Organization.getDefaultOrganizationID())) {
            resource = new IdentityID("Organization Roles", user.getOrgID()).convertToKey();
         }

         // Api user must have permission over the root to create a role
         if(!securityProvider.checkPermission(
            principal, ResourceType.SECURITY_ROLE, resource, ResourceAction.ADMIN))
         {
            record.setActionError("Permission denied to create role");
            throw new UnauthorizedAccessException("Permission denied to create role");
         }

         EditableAuthenticationProvider provider = getEditableAuthenticationProvider(securityProvider);
         IdentityID id = setDefaultOrgID(request.getIdentityID());

         if(provider.getRole(id) != null) {
            throw new ResourceExistsException(request.getIdentityID() == null ? null : request.getIdentityID().getName());
         }

         String description = request.getDescription();
         Set<IdentityID> inheritedRoles = new HashSet<>();

         List<String> assignedUsers = request.getAssignedUsers();
         List<String> assignedGroups = request.getAssignedGroups();
         List<IdentityID> userIds = assignedUsers == null ? new ArrayList<>() : assignedUsers.stream()
            .map(n -> new IdentityID(n, id.orgID)).toList();
         List<IdentityID> groupIds = assignedGroups == null ? new ArrayList<>() : assignedGroups.stream()
            .map(n -> new IdentityID(n, id.orgID)).toList();

         Set<IdentityID> filteredUsers = filterPermittedIds(userIds,
                                                        ResourceType.SECURITY_USER, securityProvider, principal);
         Set<IdentityID> filteredGroups = filterPermittedIds(groupIds,
                                                         ResourceType.SECURITY_GROUP, securityProvider, principal);

         if(request.getInheritedRoles() != null) {
            // No need to check for inheritance loops since this is a new role
            inheritedRoles = filterSystemAdminRoles(request.getInheritedRoles(), securityProvider, principal)
               .stream()
               .filter(role -> provider.getRole(role) != null)
               .collect(Collectors.toSet());
         }

         FSRole role = new FSRole(id, description);
         role.setRoles(inheritedRoles.toArray(new IdentityID[0]));
         role.setDefaultRole(Boolean.TRUE.equals(request.getDefaultRole()));
         role.setSysAdmin(Boolean.TRUE.equals(request.getSysAdmin()));
         role.setOrgAdmin(Boolean.TRUE.equals(request.getOrgAdmin()));
         provider.addRole(role);

         //Assign role to users
         for(IdentityID assignedUser : filteredUsers) {
            FSUser child = (FSUser) provider.getUser(assignedUser);
            List<IdentityID> list = new ArrayList<>();
            Collections.addAll(list, child.getRoles());
            list.add(id);
            child.setRoles(list.toArray(new IdentityID[0]));
            provider.setUser(child.getIdentityID(), child);
         }

         //Assign role to groups
         for(IdentityID assignedGroup : filteredGroups) {
            FSGroup child = (FSGroup) provider.getGroup(assignedGroup);
            List<IdentityID> list = new ArrayList<>();
            Collections.addAll(list, child.getRoles());
            list.add(id);
            child.setRoles(list.toArray(new IdentityID[0]));
            provider.setGroup(child.getIdentityID(), child);
         }

         String state = IdentityInfoRecord.STATE_ACTIVE;
         identityInfoRecord = SUtil.getIdentityInfoRecord(id, Identity.ROLE,
            IdentityInfoRecord.ACTION_TYPE_CREATE, null, state);
         themeService.assignTheme(id.name, id.name, request.getTheme(), CustomTheme::getRoles);
         setIdentityPermissions(id, ResourceType.SECURITY_ROLE, securityProvider, principal,
            request.getAdminIdentities());
         record.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
         Audit.getInstance().auditIdentityInfo(identityInfoRecord, principal);
      }
      finally {
         Audit.getInstance().auditAction(record, principal);
      }
   }

   public void updateRole(IdentityID roleId, SecurityRole request, Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();
      EditableAuthenticationProvider provider = getEditableAuthenticationProvider(securityProvider);
      final Role oldRole = securityProvider.getRole(roleId);

      if(!securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                           roleId.convertToKey(), ResourceAction.ADMIN))
      {
         throw new UnauthorizedAccessException("Permission denied to update role");
      }

      if(provider.getRole(roleId) == null) {
         if(securityProvider.getRole(roleId) == null) {
            throw new MissingResourceException(roleId.name);
         }

         throw new InvalidResourceException();
      }

      // Use the validated org from the path/query, not the request body, to prevent an org
      // admin from smuggling a different org into the body and writing a role cross-tenant.
      String orgID = roleId.orgID;
      List<String> assignedUsers = request.getAssignedUsers();
      List<String> assignedGroups = request.getAssignedGroups();
      List<IdentityID> userIds = assignedUsers == null ? new ArrayList<>() : assignedUsers.stream()
         .map(n -> new IdentityID(n, orgID)).toList();
      List<IdentityID> groupIds = assignedGroups == null ? new ArrayList<>() : assignedGroups.stream()
         .map(n -> new IdentityID(n, orgID)).toList();

      List<IdentityModel> asssignedIDs =
         filterPermittedIds(userIds, ResourceType.SECURITY_USER,
                            securityProvider, principal)
            .stream()
            .map(group -> IdentityModel.builder()
               .identityID(group)
               .type(Identity.USER)
               .build())
            .collect(Collectors.toList());

      asssignedIDs.addAll(
         filterPermittedIds(groupIds, ResourceType.SECURITY_GROUP,
                            securityProvider, principal)
            .stream()
            .map(user -> IdentityModel.builder()
               .identityID(user)
               .type(Identity.GROUP)
               .build())
            .collect(Collectors.toList()));

      EditRolePaneModel.Builder builder = EditRolePaneModel.builder()
         .name(request.getIdentityID() == null ? null : request.getIdentityID().getName())
         .oldName(roleId.name)
         .organization(orgID)
         // request already carries either the caller's override or the current value, preserved
         // by IdentityMerge.mergeRole before this method is called from the identities apply path
         // -- reading info/provider's OWN current state here (as this used to) would make request's
         // value inert. A raw REST caller who omits the field gets Boolean.TRUE.equals(null) ==
         // false, the same blind-overwrite-on-omission contract every other field in this builder
         // already has (description/theme below never fall back to the current value either).
         .defaultRole(Boolean.TRUE.equals(request.getDefaultRole()))
         .isSysAdmin(Boolean.TRUE.equals(request.getSysAdmin()))
         // Deliberately NOT the same blind-overwrite-on-omission contract as defaultRole/sysAdmin
         // above: orgAdmin's caller is IdentityMerge.mergeRole, whose own omit-preserves-current-
         // value semantics (see its comment) depend on this falling back to the role's current
         // server-side state rather than false when request.getOrgAdmin() is null. A raw REST
         // caller who omits the field gets the role's unchanged current orgAdmin status, not a
         // reset to false -- do not "fix" this to match defaultRole/sysAdmin's pattern.
         .isOrgAdmin(request.getOrgAdmin() != null ? request.getOrgAdmin() :
                    provider.isOrgAdministratorRole(roleId))
         .description(request.getDescription())
         .theme(request.getTheme())
         .members(asssignedIDs);

      if(request.getInheritedRoles() != null) {
         builder.roles(filterSystemAdminRoles(request.getInheritedRoles(), securityProvider, principal));
      }

      if(request.getAdminIdentities() != null) {
         builder = builder.permittedIdentities(
            convertAdminIdentitiesModel(request.getAdminIdentities(), securityProvider, principal));
      }

      EditRolePaneModel roleModel = builder.build();
      IdentityID oldId = new IdentityID(roleModel.oldName(), roleModel.organization());
      IdentityID newId = new IdentityID(roleModel.name(), roleModel.organization());

      identityService.setIdentity(oldRole, roleModel, provider, principal);
      themeService.assignTheme(oldRole.getName(), roleModel.name(), roleModel.theme(), CustomTheme::getRoles);
      identityService.setIdentityPermissions(
         oldId, newId, ResourceType.SECURITY_ROLE,
         principal, roleModel.permittedIdentities(), "");
   }

   public void deleteRole(IdentityID role, Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();
      AuthenticationProvider provider = getEditableAuthenticationProvider(securityProvider);

      if(!securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                           role.convertToKey(), ResourceAction.ADMIN))
      {
         throw new UnauthorizedAccessException("Permission denied to get group");
      }

      if(provider.getRole(role) == null) {
         if(securityProvider.getRole(role) == null) {
            throw new MissingResourceException(role.name);
         }

         throw new InvalidResourceException();
      }

      deleteIdentity(role, Identity.ROLE, principal, provider);
   }

   private SecurityRole getRoleModel(IdentityID identityID, Principal principal,
                                     AuthenticationProvider provider)
   {

      IdentityInfo info = identityService.getIdentityInfo(identityID, Identity.ROLE, provider);

      List<String> assignedUsers = info.getMembers().stream()
         .filter(id -> id.type() == Identity.USER)
         .map(IdentityModel::identityID)
         .map(id -> id.name)
         .collect(Collectors.toList());
      List<String> assignedGroups = info.getMembers().stream()
         .filter(id -> id.type() == Identity.GROUP)
         .map(IdentityModel::identityID)
         .map(id -> id.name)
         .collect(Collectors.toList());

      SecurityRole roleModel = new SecurityRole();
      roleModel.setIdentityID(identityID);
      roleModel.setDescription(((Role) info.getIdentity()).getDescription());
      roleModel.setTheme(themeService.getTheme(identityID, CustomTheme::getRoles));
      roleModel.setAssignedUsers(assignedUsers);
      roleModel.setAssignedGroups(assignedGroups);
      roleModel.setInheritedRoles(Arrays.asList(info.getRoles()));
      roleModel.setAdminIdentities(getIdentityPermissions(identityID, ResourceType.SECURITY_ROLE, principal));
      roleModel.setDefaultRole(info.isDefaultRole());
      roleModel.setSysAdmin(provider.isSystemAdministratorRole(identityID));
      roleModel.setOrgAdmin(provider.isOrgAdministratorRole(identityID));

      return roleModel;
   }

   /**
    * Validates that the given value is a locale code known to this deployment (a key of
    * {@link SUtil#loadLocaleProperties()}, e.g. "en_US") and returns it unchanged. The Public API's
    * locale field is documented and consumed as a code, unlike the EM locale dropdown which works
    * in display labels (the values of the same properties).
    */
   private String validateLocale(String locale) throws InvalidResourceException {
      if(Tool.isEmptyString(locale)) {
         return null;
      }

      if(!SUtil.loadLocaleProperties().containsKey(locale)) {
         throw new InvalidResourceException("Invalid locale: " + locale);
      }

      return locale;
   }

   /**
    * Translates a Public API locale code into the display label expected by
    * {@link EditUserPaneModel#locale()}/{@link EditOrganizationPaneModel#locale()} (consumed by
    * IdentityService's label-to-code lookup), so the caller's code round-trips back to itself.
    */
   private String toLocaleLabel(String locale) throws InvalidResourceException {
      String code = validateLocale(locale);
      return code == null ? null : SUtil.loadLocaleProperties().getProperty(code);
   }

   private IdentityID setDefaultOrgID(IdentityID id) {
      if(id.getOrgID() == null) {
         id.setOrgID(OrganizationManager.getInstance().getCurrentOrgID());
      }

      return id;
   }

   /**
    * Drop any system-administrator role from a caller-supplied role list unless the caller is
    * already a site administrator. Prevents an org-scoped admin from granting site-wide
    * "Administrator" privileges to a user, group, or role via the roles/inheritedRoles fields.
    */
   private List<IdentityID> filterSystemAdminRoles(List<IdentityID> roles,
                                                    SecurityProvider securityProvider,
                                                    Principal principal)
   {
      if(roles == null || OrganizationManager.getInstance().isSiteAdmin(principal)) {
         return roles;
      }

      AuthenticationProvider authenticationProvider = securityProvider.getAuthenticationProvider();
      return roles.stream()
         .filter(role -> !isSystemAdminRoleAssignment(authenticationProvider, role))
         .collect(Collectors.toList());
   }

   /**
    * Determines whether a requested role assignment references the system administrator
    * role. System administrator roles are global (orgID == null), so a non-site-admin
    * must not be able to bypass the filter by supplying a spoofed or foreign organization
    * id on the role (e.g. {Administrator, host-org}).
    */
   private boolean isSystemAdminRoleAssignment(AuthenticationProvider provider, IdentityID role) {
      if(role == null) {
         return false;
      }

      if(provider.isSystemAdministratorRole(role)) {
         return true;
      }

      // A non-existent role whose name matches the global system administrator role is a
      // spoofed assignment; reject it regardless of the organization id supplied.
      return role.orgID != null && provider.getRole(role) == null &&
         provider.isSystemAdministratorRole(new IdentityID(role.name, null));
   }

   private Set<IdentityID> filterPermittedIds(List<IdentityID> identities, ResourceType idType,
                                              SecurityProvider securityProvider, Principal principal)
   {
      return filterPermittedIds(identities, idType, securityProvider, principal, false);
   }

   private Set<IdentityID> filterPermittedIds(List<IdentityID> identities, ResourceType idType,
                                          SecurityProvider securityProvider, Principal principal,
                                          boolean allowNull)
   {
      if(identities == null) {
         return new HashSet<>();
      }

      if(allowNull) {
         return identities.stream()
            .filter(id -> securityProvider.checkPermission(principal, idType, id.convertToKey(), ResourceAction.ADMIN))
            .collect(Collectors.toSet());
      }

      Predicate<IdentityID> nullCheck;
      AuthenticationProvider authenticationProvider = securityProvider.getAuthenticationProvider();

      if(ResourceType.SECURITY_USER.equals(idType)) {
         nullCheck = (id) -> authenticationProvider.getUser(id) != null;
      }
      else if(ResourceType.SECURITY_GROUP.equals(idType)) {
         nullCheck = (id) -> authenticationProvider.getGroup(id) != null;
      }
      else if(ResourceType.SECURITY_ROLE.equals(idType)) {
         nullCheck = (id) -> authenticationProvider.getRole(id) != null;
      }
      else {
         nullCheck = (id) -> true;
      }

      return identities.stream()
         .filter(id -> securityProvider.checkPermission(principal, idType, id.convertToKey(), ResourceAction.ADMIN))
         .filter(nullCheck)
         .collect(Collectors.toSet());
   }

   private EditableAuthenticationProvider getEditableAuthenticationProvider(SecurityProvider securityProvider)
      throws Exception
   {
      EditableAuthenticationProvider provider =
         SUtil.getEditableAuthenticationProvider(securityProvider);

      if(provider == null) {
         if(securityProvider.getAuthenticationProvider() instanceof VirtualAuthenticationProvider) {
            provider =
               (EditableAuthenticationProvider) securityProvider.getAuthenticationProvider();
         }
         else {
            throw new Exception("The security provider is not editable");
         }
      }

      return provider;
   }

   private void setIdentityPermissions(IdentityID identityID, ResourceType resourceType,
                                       SecurityProvider securityProvider,
                                       Principal principal,
                                       AdminIdentities ids)
   {
      ResourceAction action;

      if(ids == null) {
         return;
      }

      action = ResourceAction.ADMIN;

      AuthorizationProvider authzProvider = securityProvider.getAuthorizationProvider();
      Permission permission = new Permission();
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      Set<String> userGrants = new HashSet<>();
      Set<String> groupGrants = new HashSet<>();
      Set<String> roleGrants = new HashSet<>();
      Set<String> organizationGrants = new HashSet<>();

      if(ids.getUsers() != null) {
         userGrants = ids.getUsers().stream()
            .filter(user -> securityProvider
               .checkPermission(principal, ResourceType.SECURITY_USER,
                                user.convertToKey(), ResourceAction.ADMIN))
            .map(id -> id.name)
            .collect(Collectors.toSet());
      }

      if(pId != null && Tool.equals(pId.getOrgID(), identityID.getOrgID())) {
         userGrants.add(pId.name);
      }

      if(ids.getGroups() != null) {
         groupGrants = ids.getGroups().stream()
            .filter(group -> securityProvider
               .checkPermission(principal, ResourceType.SECURITY_GROUP,
                                group.convertToKey(), ResourceAction.ADMIN))
            .map(id -> id.name)
            .collect(Collectors.toSet());
      }

      if(ids.getOrganizations() != null) {
         organizationGrants = ids.getOrganizations().stream()
            .filter(organization -> securityProvider
               .checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                organization, ResourceAction.ADMIN))
            .collect(Collectors.toSet());
      }

      if(securityProvider.checkPermission(
         principal, ResourceType.SECURITY_ROLE, getIdentityRootResorucePath(), ResourceAction.ADMIN))
      {
         if(ids.getRoles() != null) {
            roleGrants = ids.getRoles().stream()
               .filter(role -> securityProvider.getRole(role) != null)
               .map(id -> id.name)
               .collect(Collectors.toSet());
         }
      }

      String orgId = identityID.getOrgID();
      permission.setUserGrantsForOrg(action, userGrants, orgId);
      permission.setGroupGrantsForOrg(action, groupGrants, orgId);
      permission.setRoleGrantsForOrg(action, roleGrants, orgId);
      permission.setOrganizationGrantsForOrg(action, organizationGrants, orgId);
      authzProvider.setPermission(resourceType, identityID, permission);
   }

   private AdminIdentities getIdentityPermissions(IdentityID resourceID, ResourceType resourceType,
                                                  Principal principal)
   {
      String orgID = resourceID.orgID;
      AdminIdentities ids = new AdminIdentities();

      List<IdentityModel> permittedIds =
         identityService.getPermission(resourceID, resourceType, orgID, principal);

      List<IdentityID> permittedUsers = permittedIds.stream()
         .filter(id -> id.type() == Identity.Type.USER.code())
         .map(IdentityModel::identityID)
         .collect(Collectors.toList());
      List<IdentityID> permittedGroups = permittedIds.stream()
         .filter(id -> id.type() == Identity.Type.GROUP.code())
         .map(IdentityModel::identityID)
         .collect(Collectors.toList());
      List<String> permittedOrganizations = permittedIds.stream()
         .filter(id -> id.type() == Identity.Type.ORGANIZATION.code())
         .map(IdentityModel::identityID)
         .map(id -> id.name)
         .collect(Collectors.toList());
      List<IdentityID> permittedRoles = permittedIds.stream()
         .filter(id -> id.type() == Identity.Type.ROLE.code())
         .map(IdentityModel::identityID)
         .collect(Collectors.toList());

      ids.setUsers(permittedUsers);
      ids.setGroups(permittedGroups);
      ids.setRoles(permittedRoles);
      ids.setOrganizations(permittedOrganizations);

      return ids;
   }

   private List<IdentityModel> convertAdminIdentitiesModel(AdminIdentities adminIdentities,
                                                           SecurityProvider securityProvider,
                                                           Principal principal)
   {
      if(adminIdentities == null) {
         return null;
      }

      ArrayList<IdentityModel> newModels = new ArrayList<>();

      if(adminIdentities.getUsers() != null) {
         newModels.addAll(
            adminIdentities.getUsers().stream()
               .filter(user -> securityProvider
                  .checkPermission(principal, ResourceType.SECURITY_USER,
                                   user.convertToKey(), ResourceAction.ADMIN))
               .map(user -> IdentityModel.builder()
                  .identityID(user)
                  .type(Identity.USER)
                  .build())
               .collect(Collectors.toSet()));
      }

      if(adminIdentities.getGroups() != null) {
         newModels.addAll(
            adminIdentities.getGroups().stream()
               .filter(group -> securityProvider
                  .checkPermission(principal, ResourceType.SECURITY_GROUP,
                                   group.convertToKey(), ResourceAction.ADMIN))
               .map(group -> IdentityModel.builder()
                  .identityID(group)
                  .type(Identity.GROUP)
                  .build())
               .collect(Collectors.toSet()));
      }

      if(adminIdentities.getRoles() != null) {
         newModels.addAll(
            adminIdentities.getRoles().stream()
               .filter(group -> securityProvider
                  .checkPermission(principal, ResourceType.SECURITY_ROLE,
                                   group.convertToKey(), ResourceAction.ADMIN))
               .map(group -> IdentityModel.builder()
                  .identityID(group)
                  .type(Identity.ROLE)
                  .build())
               .collect(Collectors.toSet()));
      }

      if(adminIdentities.getOrganizations() != null) {
         newModels.addAll(
            adminIdentities.getOrganizations().stream()
               .filter(organizationId -> securityProvider
                  .checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                   organizationId, ResourceAction.ADMIN))
               .map(organizationId -> IdentityModel.builder()
                  .identityID(new IdentityID(organizationId, securityProvider.getOrgNameFromID(organizationId)))
                  .type(Identity.ORGANIZATION)
                  .build())
               .collect(Collectors.toSet()));
      }

      if(securityProvider.checkPermission(
         principal, ResourceType.SECURITY_ROLE, getIdentityRootResorucePath(), ResourceAction.ADMIN))
      {
         if(adminIdentities.getRoles() != null) {
            newModels.addAll(
               adminIdentities.getRoles().stream()
                  .filter(role -> securityProvider.getRole(role) != null)
                  .map(role -> IdentityModel.builder()
                     .identityID(role)
                     .type(Identity.ROLE)
                     .build())
                  .collect(Collectors.toSet()));
         }
      }

      return newModels;
   }

   private void deleteIdentity(IdentityID identityID, int type,
                               Principal principal, AuthenticationProvider provider)
      throws Exception
   {
      Set<Identity> identitiesToDelete = new HashSet<>();
      identitiesToDelete.add(systemAdminService.createIdentity(identityID, type));
      IdentityModel[] models = new IdentityModel[] {IdentityModel.builder()
                                                       .identityID(identityID).type(type).build()};

      if(!systemAdminService.hasOrgAdminAfterDelete(identitiesToDelete)) {
         // Guaranteed pre-mutation: this check runs before deleteIdentities is ever called.
         throw new PreMutationRefusalException(Catalog.getCatalog().getString("em.security.noOrgAdmin"));
      }
      else if(systemAdminService.hasSystemAdminAfterDelete(identitiesToDelete)) {
         //Since we are only deleting one item, we'll get at most one warning
         List<String> warnings =
            identityService.deleteIdentities(models, provider.getProviderName(), principal);

         if(!warnings.isEmpty()) {
            String warning = warnings.get(0);

            // deleteIdentities' self-delete/group-has-members checks both `continue` before
            // ever calling syncIdentity, so a warning matching one of those two exact messages
            // is guaranteed pre-mutation too. Any OTHER warning (notably syncIdentity's own
            // catch-and-report-as-warning fallback, "Failed to delete identity ...") may follow
            // a partially applied mutation (syncIdentity runs dashboard/schedule cleanup before
            // the entity-specific provider call) and must NOT be treated as safe -- callers that
            // need "was anything mutated" must check the exception type, not infer it from
            // re-reading the entity's own fields alone.
            Catalog catalog = Catalog.getCatalog(principal);

            if(warning.equals(catalog.getString("em.security.delgroup")) ||
               warning.equals(catalog.getString("em.security.delself")))
            {
               throw new PreMutationRefusalException(warning);
            }

            throw new Exception(warning);
         }
      }
      else {
         // if no system admin would remain -- also guaranteed pre-mutation, same as above.
         throw new PreMutationRefusalException(Catalog.getCatalog().getString("em.security.noSystemAdmin"));
      }
   }

   /**
    * Thrown by {@link #deleteIdentity} for a refusal that is structurally guaranteed to have
    * happened before any provider-mutating call was attempted for the identity in question --
    * as opposed to a failure that may have occurred partway through
    * {@code IdentityService.syncIdentity}'s multi-step mutation (dashboard/schedule cleanup runs
    * before the entity-specific {@code eprovider.remove*} call, so a later failure in that same
    * method is NOT covered by this type). Callers that need to distinguish "nothing was touched"
    * from "unknown state" (e.g. {@code IdentityChangesetApplyService}) must check for this type
    * specifically rather than inferring safety from the exception's message or from re-reading
    * the identity's own fields alone.
    */
   public static class PreMutationRefusalException extends Exception {
      public PreMutationRefusalException(String message) {
         super(message);
      }
   }

   public ResourcePermissionList listPermissions(Principal principal) throws Exception {
      SecurityProvider securityProvider = securityEngine.getSecurityProvider();
      List<Tuple4<ResourceType, String, String, Permission>> permissions = securityProvider.getPermissions();
      ArrayList<ResourcePermission> permissionModels = new ArrayList<>();

      for(Tuple4<ResourceType, String, String, Permission> tuple : permissions) {
         if(!checkPermissionAccess(tuple.getThird(), tuple.getFirst(), principal)) {
            continue;
         }

         ResourcePermission resourcePermission =
            convertPermission(tuple.getFirst(), tuple.getThird(), tuple.getForth());
         permissionModels.add(resourcePermission);
      }

      ResourcePermissionList permissionList = new ResourcePermissionList();
      permissionList.setPermissions(permissionModels);
      return permissionList;
   }

   public ResourcePermission getPermission(String resourcePath, String resourceType, Principal principal)
      throws Exception
   {
      SecurityProvider securityProvider = securityEngine.getSecurityProvider();
      ResourceType type = convertResourceType(resourceType);

      if(!checkPermissionAccess(resourcePath, type, principal)) {
         throw new UnauthorizedAccessException("Permission denied to access permission");
      }

      Permission permission = securityProvider.getPermission(type, resourcePath);
      return convertPermission(type, resourcePath, permission);
   }

   private ResourcePermission convertPermission(ResourceType type, String path, Permission permission) {
      ArrayList<PermissionGrant> permissionGrants = new ArrayList<>();
      HashMap<IdentityID, ArrayList<String>> userGrants = new HashMap<>();
      HashMap<IdentityID, ArrayList<String>> groupGrants = new HashMap<>();
      HashMap<IdentityID, ArrayList<String>> roleGrants = new HashMap<>();
      HashMap<String, ArrayList<String>> organizationGrants = new HashMap<>();
      String orgId = OrganizationManager.getInstance().getCurrentOrgID();

      if(permission != null) {
         for(ResourceAction action : ResourceAction.values()) {
            Set<IdentityID> idGrants = permission.getOrgScopedUserGrants(action, orgId);

            for(IdentityID user : idGrants) {
               ArrayList<String> actions = userGrants.computeIfAbsent(user, key -> new ArrayList<>());
               actions.add(action.name());
            }

            idGrants = permission.getOrgScopedGroupGrants(action, orgId);

            for(IdentityID group : idGrants) {
               ArrayList<String> actions = groupGrants.computeIfAbsent(group, key -> new ArrayList<>());
               actions.add(action.name());
            }

            idGrants = permission.getOrgScopedOrganizationGrants(action, orgId);

            for(IdentityID organization : idGrants) {
               ArrayList<String> actions = organizationGrants.computeIfAbsent(organization.getOrgID(), key -> new ArrayList<>());
               actions.add(action.name());
            }

            idGrants = permission.getOrgScopedRoleGrants(action, orgId);

            for(IdentityID role : idGrants) {
               ArrayList<String> actions = roleGrants.computeIfAbsent(role, key -> new ArrayList<>());
               actions.add(action.name());
            }
         }
      }

      for(IdentityID user : userGrants.keySet()) {
         PermissionGrant grant = new PermissionGrant();
         grant.setIdentityID(user);
         grant.setType("USER");
         grant.setActions(userGrants.get(user));
         permissionGrants.add(grant);
      }

      for(IdentityID group : groupGrants.keySet()) {
         PermissionGrant grant = new PermissionGrant();
         grant.setIdentityID(group);
         grant.setType("GROUP");
         grant.setActions(groupGrants.get(group));
         permissionGrants.add(grant);
      }

      SecurityProvider securityProvider = securityEngine.getSecurityProvider();

      for(String organizationId : organizationGrants.keySet()) {
         PermissionGrant grant = new PermissionGrant();
         grant.setIdentityID(new IdentityID(securityProvider.getOrgNameFromID(organizationId), organizationId));
         grant.setType("ORGANIZATION");
         grant.setActions(organizationGrants.get(organizationId));
         permissionGrants.add(grant);
      }

      for(IdentityID role : roleGrants.keySet()) {
         PermissionGrant grant = new PermissionGrant();
         grant.setIdentityID(role);
         grant.setType("ROLE");
         grant.setActions(roleGrants.get(role));
         permissionGrants.add(grant);
      }

      ResourcePermission permissionModel = new ResourcePermission();
      permissionModel.setResource(path);
      permissionModel.setResourceType(type.name());
      permissionModel.setPermissionGrants(permissionGrants);

      return permissionModel;
   }

   public void setPermission(String resourcePath, String resourceType, ResourcePermission permissionModel,
                             Principal principal) throws Exception
   {
      SecurityProvider securityProvider = securityEngine.getSecurityProvider();
      ResourceType type = convertResourceType(resourceType);

      if(!checkPermissionAccess(resourcePath, type, principal)) {
         throw new UnauthorizedAccessException("Permission denied to access permission");
      }

      Permission permission = new Permission();

      for(ResourceAction action : ResourceAction.values()) {
         HashSet<String> userGrants = new HashSet<>();
         HashSet<String> groupGrants = new HashSet<>();
         HashSet<String> organizationGrants = new HashSet<>();
         HashSet<String> roleGrants = new HashSet<>();

         for(PermissionGrant grant : permissionModel.grants) {
            if(grant.getActions().contains(action.name())) {
               if(grant.getType().equals("USER")) {
                  userGrants.add(grant.getIdentityID().name);
               }
               else if(grant.getType().equals("GROUP")) {
                  groupGrants.add(grant.getIdentityID().name);
               }
               else if(grant.getType().equals("ORGANIZATION")) {
                  organizationGrants.add(grant.getIdentityID().name);
               }
               else if(grant.getType().equals("ROLE")) {
                  roleGrants.add(grant.getIdentityID().name);
               }
            }

            String orgId = grant.getIdentityID().orgID == null ?
               OrganizationManager.getInstance().getCurrentOrgID() : grant.getIdentityID().orgID ;
            permission.setUserGrantsForOrg(action, userGrants, orgId);
            permission.setGroupGrantsForOrg(action, groupGrants, orgId);
            permission.setRoleGrantsForOrg(action, roleGrants, orgId);
            permission.setOrganizationGrantsForOrg(action, organizationGrants, orgId);
         }
      }

      securityProvider.setPermission(type, resourcePath, permission);
   }

   public void deletePermission(String resourcePath, String resourceType, Principal principal) throws Exception {
      SecurityProvider securityProvider = securityEngine.getSecurityProvider();
      ResourceType type = convertResourceType(resourceType);

      if(!checkPermissionAccess(resourcePath, type, principal)) {
         throw new UnauthorizedAccessException("Permission denied to access permission");
      }

      getPermission(resourcePath, resourceType, principal);
      securityProvider.removePermission(type, resourcePath);
   }

   public PermissionGrant getPermissionGrant(String resourcePath, String resourceType,
                                             String idName, String idType,
                                             Principal principal) throws Exception
   {
      IdentityID id = new IdentityID(idName, IdentityID.getIdentityIDFromKey(principal.getName()).orgID);
      checkIdType(idType, id);
      ResourcePermission permission = this.getPermission(resourcePath, resourceType, principal);
      PermissionGrant grant = permission.grants.stream()
                                 .filter(g -> g.getIdentityID().equals(id) &&
                                    g.getType().equals(idType))
                                 .findFirst()
                                 .orElse(null);

      return grant;
   }

   public void createPermissionGrant(String resourcePath, String resourceType,
                             PermissionGrant grant, Principal principal) throws Exception
   {
      IdentityID idName = grant.getIdentityID();
      String idType = grant.getType();
      checkIdType(idType, idName);
      ResourcePermission permission = this.getPermission(resourcePath, resourceType, principal);

      if(permission.grants.stream()
         .anyMatch(g -> g.getIdentityID().equals(idName) && g.getType().equals(idType)))
      {
         throw new ResourceExistsException(idType + " " + idName + " for " + resourceType + ":" + resourcePath);
      }

      permission.grants.add(grant);
      setPermission(resourcePath, resourceType, permission, principal);
   }

   public void updatePermissionGrant(String resourcePath, String resourceType,
                                   String idName, String idType, PermissionGrant newGrant,
                                   Principal principal) throws Exception
   {
      IdentityID oldId = IdentityID.getIdentityIDFromKey(idName);
      checkIdType(idType, oldId);
      checkIdType(newGrant.getType(), newGrant.getIdentityID());
      ResourcePermission permission = this.getPermission(resourcePath, resourceType, principal);
      PermissionGrant oldGrant = permission.grants.stream()
                                 .filter(g -> g.getIdentityID().convertToKey().equals(idName) && g.getType().equals(idType))
                                 .findFirst()
                                 .orElse(null);

      if(oldGrant == null) {
         throw new MissingResourceException(idType + " " + idName + " for " + resourceType + ":" + resourcePath);
      }

      if(!(newGrant.getIdentityID().convertToKey().equals(idName) && newGrant.getType().equals(idType))) {
         IdentityID newName = newGrant.getIdentityID();
         String newType = newGrant.getType();

         if(permission.grants.stream()
            .anyMatch(g -> g.getIdentityID().equals(newName) && g.getType().equals(newType)))
         {
            throw new ResourceExistsException(newType + " " + newName + " for " + resourceType + ":" + resourcePath);
         }
      }

      permission.grants.remove(oldGrant);
      permission.grants.add(newGrant);
      setPermission(resourcePath, resourceType, permission, principal);
   }

   public void deletePermissionGrant(String resourcePath, String resourceType,
                                     String idName, String idType,
                                     Principal principal) throws Exception
   {
      IdentityID id = IdentityID.getIdentityIDFromKey(idName);
      checkIdType(idType, id);
      ResourcePermission permission = this.getPermission(resourcePath, resourceType, principal);
      PermissionGrant grant = permission.grants.stream()
         .filter(g -> g.getIdentityID().convertToKey().equals(idName) && g.getType().equals(idType))
         .findFirst()
         .orElse(null);

      if(grant == null) {
         throw new MissingResourceException(idType + " " + idName + " for " + resourceType + ":" + resourcePath);
      }

      permission.grants.remove(grant);
      setPermission(resourcePath, resourceType, permission, principal);
   }

   private ResourceType convertResourceType(String type) throws Exception {
      try {
         return ResourceType.valueOf(type);
      }
      catch (Exception e) {
         throw new UnauthorizedAccessException("Invalid resource type");
      }
   }

   private void checkIdType(String type, IdentityID id) throws Exception {
      try {
         Identity.Type.valueOf(type);
      }
      catch (Exception e) {
         throw new UnauthorizedAccessException("Invalid identity type");
      }

      SecurityProvider provider = securityEngine.getSecurityProvider();
      boolean existsAsClaimedType =
         type.equals("USER")         ? provider.getUser(id) != null
       : type.equals("GROUP")        ? provider.getGroup(id) != null
       : type.equals("ROLE")         ? provider.getRole(id) != null
       : type.equals("ORGANIZATION") ? provider.getOrganization(id.name) != null
       : true; // unreachable, valueOf() above already threw

      if(!existsAsClaimedType) {
         String actualKind = actualKindOf(provider, id);

         if(actualKind != null) {
            throw new IllegalArgumentException(
               "identityType: " + id.name + " is not a " + type + " -- it is a " + actualKind +
               ". Did you mean identityType=" + actualKind + "?");
         }
      }
   }

   private String actualKindOf(SecurityProvider provider, IdentityID id) {
      if(provider.getUser(id) != null) {
         return "USER";
      }

      if(provider.getGroup(id) != null) {
         return "GROUP";
      }

      if(provider.getRole(id) != null) {
         return "ROLE";
      }

      if(provider.getOrganization(id.name) != null) {
         return "ORGANIZATION";
      }

      return null;
   }

   private boolean checkPermissionAccess(String resource, ResourceType type, Principal principal) {
      SecurityProvider securityProvider = securityEngine.getSecurityProvider();
      ActionTreeNode root = actionPermissionService.getActionTree(principal);

      boolean isActionTreePermission = checkActionNodeChildren(root, resource, type);

      //Check if resource is accessible from the em security/actions page
      //Otherwise use admin permission on the resource
      if(isActionTreePermission) {
         return securityProvider.checkPermission(principal, ResourceType.EM_COMPONENT,
                                                 "settings/security/actions", ResourceAction.ACCESS);
      }
      else {
         return securityProvider.checkPermission(principal, type,
                                                 resource, ResourceAction.ADMIN);
      }
   }

   private boolean checkActionNodeChildren(ActionTreeNode node, String resource, ResourceType type) {
      if(node.children().isEmpty()) {
         return node.type() == type && node.resource() != null && node.resource().equals(resource);
      }
      else {
         for(ActionTreeNode child : node.children()) {
            if(checkActionNodeChildren(child, resource, type)) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean checkOrganizationExist(String orgID) {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();

      return securityProvider != null && securityProvider.getOrganizationIDs() != null &&
         Arrays.asList(securityProvider.getOrganizationIDs()).contains(orgID);
   }

   private String getIdentityRootResorucePath() {
      return new IdentityID("*", OrganizationManager.getInstance().getCurrentOrgID()).convertToKey();
   }

   private final SecurityEngine securityEngine;
   private final IdentityService identityService;
   private final ActionPermissionService actionPermissionService;
   private final LocalizationSettingsService localizationSettingsService;
   private final IdentityThemeService themeService;
   private final SystemAdminService systemAdminService;
   private final UserTreeService userTreeService;
   private final CustomThemesManager customThemesManager;
}
