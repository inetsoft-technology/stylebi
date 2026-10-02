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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
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
         checkSystemAdminParentGroups(parentGroups, id.orgID, principal);

         if(provider.getUser(id) != null) {
            throw new ResourceExistsException(request.getIdentityID() == null ? null : request.getIdentityID().getName());
         }

         String alias = request.getAlias();
         String locale = validateLocale(request.getLocale());
         // a new user is active unless the request says otherwise
         boolean active = request.getActive() == null || request.getActive();
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
               roles = resolveRoleReferencesOrThrow(
                  filterSystemAdminRoles(request.getRoles(), securityProvider, principal), provider);
               checkAssignableRoles(roles, principal);
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
         themeService.assignTheme(id.name, id.name, id.orgID, request.getTheme(), CustomTheme::getUsers);
         setIdentityPermissions(id, ResourceType.SECURITY_USER, securityProvider, principal,
            request.getAdminIdentities());
         record.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
         Audit.getInstance().auditIdentityInfo(identityInfoRecord, principal);
      }
      finally {
         Audit.getInstance().auditAction(record, principal);
      }
   }

   /**
    * Updates a user. A property or list that the request omits ({@code null}) leaves that
    * property of the user unchanged. A value that is present replaces the current one, and an
    * empty list or string clears it.
    */
   public void updateUser(IdentityID id, SecurityUser request, Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();
      EditableAuthenticationProvider provider = getEditableAuthenticationProvider(securityProvider);
      final User oldUser = securityProvider.getUser(id);
      EditUserPaneModel userModel;

      try {
         if(!securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                              id.convertToKey(), ResourceAction.ADMIN))
         {
            throw new UnauthorizedAccessException("Permission denied to update user");
         }

         final User currentUser = provider.getUser(id);

         if(currentUser == null) {
            if(securityProvider.getUser(id) == null) {
               throw new MissingResourceException(id.name);
            }

            throw new InvalidResourceException();
         }

         // Use the validated org from the path/query, not the request body, so a caller can't
         // smuggle a different org into the body and pull in members from another tenant.
         List<IdentityID> groupIds = request.getGroups() == null ? null : request.getGroups().stream()
            .map(n -> new IdentityID(n, id.orgID)).collect(Collectors.toList());

         // A property that the request omits (null) keeps the user's current value, taken from
         // the editable provider (Bug #77326). An omitted group list keeps the current groups,
         // which are not filtered by the caller's permissions, because they do not change.
         Collection<IdentityID> groups = groupIds == null ?
            Arrays.stream(currentUser.getGroups() == null ? new String[0] : currentUser.getGroups())
               .map(n -> new IdentityID(n, id.orgID)).toList() :
            filterPermittedIds(groupIds, ResourceType.SECURITY_GROUP, securityProvider, principal);
         List<IdentityModel> parentGroups =
            groups
               .stream()
               .map(group -> IdentityModel.builder()
                  .identityID(group)
                  .type(Identity.GROUP)
                  .build())
               .collect(Collectors.toList());

         Boolean active = request.getActive();
         List<String> emails = request.getEmails() != null ? request.getEmails() :
            currentUser.getEmails() == null ? null : Arrays.asList(currentUser.getEmails());

         EditUserPaneModel.Builder builder = EditUserPaneModel.builder()
            .name(request.getIdentityID() == null ? id.name : request.getIdentityID().getName())
            .oldName(id.name)
            .organization(id.orgID)
            .alias(request.getAlias() != null ? request.getAlias() : currentUser.getAlias())
            .locale(getLocaleLabel(request.getLocale(), currentUser.getLocale()))
            .status((active != null ? active : currentUser.isActive()) ||
                       principal.getName().equals(id.convertToKey()))
            .theme(request.getTheme())
            .members(parentGroups);

         if(emails != null) {
            builder.email(String.join(",", emails));
         }

         if(request.getRoles() != null) {
            builder = builder.roles(new ArrayList<>(resolveRoleReferencesOrThrow(
               filterSystemAdminRoles(request.getRoles(), securityProvider, principal), provider)));
         }
         else if(currentUser.getRoles() != null) {
            builder = builder.roles(Arrays.asList(currentUser.getRoles()));
         }

         if(request.getAdminIdentities() != null) {
            builder = builder.permittedIdentities(
               convertAdminIdentitiesModel(request.getAdminIdentities(), securityProvider, principal));
         }

         userModel = builder.build();
      }
      catch(Exception e) {
         throw new PreMutationRefusalException(e.getMessage(), e);
      }

      IdentityID oldId = new IdentityID(userModel.oldName(), userModel.organization());
      IdentityID newId = new IdentityID(userModel.name(), userModel.organization());
      identityService.setIdentity(oldUser, userModel, provider, principal);
      themeService.assignTheme(oldUser.getName(), userModel.name(), userModel.organization(),
                               userModel.theme(), CustomTheme::getUsers);

      if(request.getAdminIdentities() == null) {
         keepIdentityPermissions(oldId, newId, ResourceType.SECURITY_USER, securityProvider);
      }
      else {
         identityService.setIdentityPermissions(
            oldId, newId, ResourceType.SECURITY_USER, principal,
            renameOwnPermittedEntry(userModel.permittedIdentities(), Identity.USER, oldId, newId),
            "");
      }

      // Run the same rename migrations as EM (assets, MV, data cycles, recycle bin, ...), on the
      // stored user's org, and only after the provider rename succeeded.
      String userOrgID = oldUser.getOrganizationID() != null ? oldUser.getOrganizationID() : id.orgID;
      IdentityID oldUserID = new IdentityID(id.name, userOrgID);
      IdentityID newUserID = new IdentityID(userModel.name(), userOrgID);

      if(!oldUserID.equals(newUserID)) {
         userTreeService.migrateUserRename(oldUserID, newUserID);
      }
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
         checkSystemAdminParentGroups(parentGroups, identityID.orgID, principal);

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
               roles = resolveRoleReferencesOrThrow(
                  filterSystemAdminRoles(request.getRoles(), securityProvider, principal), provider);
               checkAssignableRoles(roles, principal);
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
         themeService.updateIdentityTheme(identityID.name, identityID.name, identityID.orgID,
                                          request.getTheme(), CustomTheme::getGroups, principal);
         setIdentityPermissions(identityID, ResourceType.SECURITY_GROUP, securityProvider, principal,
                                request.getAdminIdentities());
         record.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
         Audit.getInstance().auditIdentityInfo(identityInfoRecord, principal);
      }
      finally {
         Audit.getInstance().auditAction(record, principal);
      }
   }

   /**
    * Updates a group. A property or list that the request omits ({@code null}) leaves that
    * property of the group unchanged. A value that is present replaces the current one, and an
    * empty list or string clears it, except for the parent groups: a parent group list is added to
    * the group's current parent groups, and an empty one removes none.
    */
   public void updateGroup(IdentityID identityID, SecurityGroup request, Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();
      EditableAuthenticationProvider provider = getEditableAuthenticationProvider(securityProvider);
      final Group oldGroup = securityProvider.getGroup(identityID);
      EditGroupPaneModel groupModel;

      try {
         if(!securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP,
                                              identityID.convertToKey(), ResourceAction.ADMIN))
         {
            throw new UnauthorizedAccessException("Permission denied to update group");
         }

         final Group currentGroup = provider.getGroup(identityID);

         if(currentGroup == null) {
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

         // a member list that the request omits leaves the group's members of that type unchanged
         Collection<IdentityID> memberGroups = memberIds == null ?
            getGroupMembers(identityID, provider.getGroups(), provider::getGroup) :
            filterPermittedIds(memberIds, ResourceType.SECURITY_GROUP, securityProvider, principal);
         List<IdentityModel> members =
            memberGroups
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

         Collection<IdentityID> memberUsers = memberUserIds == null ?
            getGroupMembers(identityID, provider.getUsers(), provider::getUser) :
            filterPermittedIds(memberUserIds, ResourceType.SECURITY_USER, securityProvider, principal);
         members.addAll(
            memberUsers
               .stream()
               .map(user -> IdentityModel.builder()
                  .identityID(user)
                  .type(Identity.USER)
                  .build())
               .collect(Collectors.toList()));

         // Existing parent groups are not re-checked so that unrelated edits of a group already
         // under an administrator group still succeed (Bug #77073).
         if(request.getParentGroups() != null) {
            Set<String> oldParents = oldGroup.getGroups() == null ?
               new HashSet<>() : new HashSet<>(Arrays.asList(oldGroup.getGroups()));
            checkSystemAdminParentGroups(
               request.getParentGroups().stream().filter(p -> !oldParents.contains(p)).toList(),
               identityID.orgID, principal);
         }

         EditGroupPaneModel.Builder builder = EditGroupPaneModel.builder()
            .name(request.getIdentityID() == null ? identityID.name : request.getIdentityID().getName())
            .oldName(identityID.name)
            .theme(request.getTheme())
            .organization(identityID.orgID)
            .members(members);

         // omitted roles keep the group's current roles, from the editable provider (Bug #77326)
         if(request.getRoles() != null) {
            builder.roles(new ArrayList<>(resolveRoleReferencesOrThrow(
               filterSystemAdminRoles(request.getRoles(), securityProvider, principal), provider)));
         }
         else if(currentGroup.getRoles() != null) {
            builder.roles(Arrays.asList(currentGroup.getRoles()));
         }

         if(request.getAdminIdentities() != null) {
            builder = builder.permittedIdentities(
               convertAdminIdentitiesModel(request.getAdminIdentities(), securityProvider, principal));
         }

         groupModel = builder.build();
      }
      catch(Exception e) {
         throw new PreMutationRefusalException(e.getMessage(), e);
      }

      IdentityID oldId = new IdentityID(groupModel.oldName(), groupModel.organization());
      IdentityID newId = new IdentityID(groupModel.name(), groupModel.organization());

      identityService.setIdentity(oldGroup, groupModel, provider, principal);
      themeService.updateIdentityTheme(oldGroup.getName(), groupModel.name(),
                                       groupModel.organization(), groupModel.theme(),
                                       CustomTheme::getGroups, principal);

      if(request.getAdminIdentities() == null) {
         keepIdentityPermissions(oldId, newId, ResourceType.SECURITY_GROUP, securityProvider);
      }
      else {
         identityService.setIdentityPermissions(
            oldId, newId, ResourceType.SECURITY_GROUP, principal,
            renameOwnPermittedEntry(groupModel.permittedIdentities(), Identity.GROUP, oldId, newId),
            identityID.orgID);
      }

      // oldId/newId already carry the validated org (from groupModel.organization()); using the
      // raw request body identity here would let a caller move the group into another org.
      updateParentGroups(oldId, newId, request.getParentGroups(), securityProvider, principal);

      // Run the same rename migrations as EM (assets, data cycles), on the stored group's org.
      // Keep this last so a migration failure can't skip the provider-level parent-group move.
      String groupOrgID = oldGroup.getOrganizationID() != null ?
         oldGroup.getOrganizationID() : identityID.orgID;
      IdentityID oldGroupID = new IdentityID(identityID.name, groupOrgID);
      IdentityID newGroupID = new IdentityID(groupModel.name(), groupOrgID);

      if(!oldGroupID.equals(newGroupID)) {
         userTreeService.migrateGroupRename(oldGroupID, newGroupID);
      }
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

      if(!isOwnOrganization(organizationid, principal) ||
         !securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                           organizationid, ResourceAction.ADMIN))
      {
         throw new UnauthorizedAccessException("Permission denied to get Organization");
      }

      if(securityProvider.getOrganization(organizationid) == null) {
         throw new MissingResourceException(organizationid);
      }

      return getOrganizationModel(new IdentityID(securityProvider.getOrganization(organizationid).getName(), organizationid), principal, securityProvider);
   }

   // createOrganization and updateOrganization intentionally do not switch the ambient org into
   // the target org (no @SwitchOrg), so the current org is the caller's own (normally host-org).
   // Any permission or storage call here must pass the target org id explicitly (Bug #77206).
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

         if(provider.getOrgIdFromName(request.getName()) != null) {
            throw new ResourceExistsException(request.getName());
         }

         // org names and ids share one case-insensitive namespace
         checkOrganizationIdentityConflict(securityProvider, null, request);

         // Validated ahead of both branches so an invalid code is rejected consistently whether or
         // not copyFrom was supplied, and before the ActionRecord exists -- this method's finally
         // block stamps ACTION_STATUS_SUCCESS unconditionally, so a throw after that point would
         // be audited as a successful create.
         String orgLocale = validateLocale(request.getLocale());

         // runs before both the clone and the plain create below; the clone path's own
         // UserTreeService check is then redundant but harmless
         OrganizationIdRules.checkCreate(request.getId());

         if(!Tool.isEmptyString(copyFromOrgID)) {
            userTreeService.createOrganization(
               copyFromOrgID, provider.getProviderName(), request.getName(), request.getId(), principal, request.getDefaultPassword());
            // the copy branch must apply the requested admins too, in the org's own bucket. The
            // copy does not apply request.getName() (the new org is named by its id) and the
            // copied self grant is keyed by the org as created, so key the requested admins by
            // the created org's actual name. A request list replaces the copied admins, as on
            // update; an omitted list keeps them (setIdentityPermissions returns on null). If the
            // copy did not create the org, write nothing rather than pre-seed a grant for it.
            // The replace does not keep copied grantees the caller cannot administer, as update
            // does: only a site admin reaches this path, and a site admin can administer every
            // grantee.
            Organization createdOrg = provider.getOrganization(request.getId());

            if(createdOrg == null) {
               return;
            }

            // defensive: copyOrganizationInternal() always names the copy (its id when no name
            // is given), so the name is not expected to be null here
            String createdName = createdOrg.getName() == null ?
               request.getId() : createdOrg.getName();
            setIdentityPermissions(new IdentityID(createdName, request.getId()),
                                   ResourceType.SECURITY_ORGANIZATION, securityProvider, principal,
                                   request.getAdminIdentities());
            return;
         }
         else {
            // member users are created with defaultPassword, validate it before any write
            if(request.getMemberUsers() != null && !request.getMemberUsers().isEmpty()) {
               IdentityService.validatePasswordStrength(request.getDefaultPassword());
            }

            checkNoExistingMembers(provider, request);

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
         // members belong to the new organization, so they are keyed by its id, not its name
         List<IdentityID> memberUserIds = requestUsers.stream()
            .map(n -> new IdentityID(n, oid)).collect(Collectors.toList());
         List<IdentityID> memberGroupIds = requestGroups.stream()
            .map(n -> new IdentityID(n, oid)).collect(Collectors.toList());
         List<IdentityID> memberRoleIds = requestRoles.stream()
            .map(n -> new IdentityID(n, oid)).collect(Collectors.toList());

         List<String> members = new ArrayList<>();
         members.addAll(memberUserIds.stream().map(id -> id.name).toList());
         members.addAll(memberGroupIds.stream().map(id -> id.name).toList());
         members.addAll(memberRoleIds.stream().map(id -> id.name).toList());

         FSOrganization organization = new FSOrganization(oid);
         organization.setName(name);
         organization.setMembers(members.toArray(new String[0]));
         organization.setLocale(orgLocale);
         // the requested theme may be given by ID (as returned by getOrganization) or by name,
         // and must be a global theme or one of the new organization's own themes. The
         // organization stores the theme ID, see GlobalStyleController. Update resolves it
         // the same way.
         CustomTheme currentTheme =
            findOrganizationTheme(request.getTheme(), oid).orElse(null);
         organization.setTheme(currentTheme == null ? null : currentTheme.getId());
         provider.addOrganization(organization);
         applyOrganizationProperties(oid, request.getProperties());

         for(IdentityID memberUser : memberUserIds) {
            FSUser user = new FSUser(memberUser);
            SUtil.setPassword(user, request.getDefaultPassword());
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
         if(currentTheme != null) {
            addOrganizationToTheme(currentTheme.getId(), oid);
         }

         // organization.getIdentityID() (name^id), not new IdentityID(oid, oid) (id^id) -- the
         // rename mechanism (IdentityService.updateIdentityPermissions()'s SECURITY_ORGANIZATION
         // path-migration special case, and every real permission read of this grant such as
         // DefaultCheckPermissionStrategy.checkOrgAdminPermission()) expects the org's own self
         // grant to be keyed by (org name, org id), matching Organization.getIdentityID()'s own
         // convention; keying it by (org id, org id) instead left the grant permanently
         // unreadable/unmigratable after a rename (part of bug #76866).
         setIdentityPermissions(organization.getIdentityID(),
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

   /**
    * Updates an organization. A member list, theme or locale that the request omits
    * ({@code null}) leaves that property of the organization unchanged. A value that is present
    * replaces the current one, and an empty string clears the theme or locale.
    */
   public void updateOrganization(String id, SecurityOrganization request, Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();
      EditableAuthenticationProvider provider = getEditableAuthenticationProvider(securityProvider);
      final Organization oldOrganization = securityProvider.getOrganization(id);
      // an update body without an id keeps the org's id, it is not an id rename to null
      final String newOrgId = request.getId() == null ? id : request.getId();
      final Organization currentOrganization = provider.getOrganization(id);
      EditOrganizationPaneModel organizationModel;

      try {
         if(!isOwnOrganization(id, principal) ||
            !securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                              id, ResourceAction.ADMIN))
         {
            throw new UnauthorizedAccessException("Permission denied to update organization");
         }

         if(currentOrganization == null) {
            if(securityProvider.getOrganization(id) == null) {
               throw new MissingResourceException(id);
            }

            throw new InvalidResourceException();
         }

         // org names and ids share one case-insensitive namespace: reject a name or id that equals
         // another org's name or id, ignoring case. Only checked for the fields that actually
         // change so existing orgs stay editable.
         checkOrganizationIdentityConflict(securityProvider, oldOrganization, request);

         // a member that is dropped from an organization is deleted, so a member list that the
         // request omits leaves the organization's members of that type unchanged
         List<IdentityModel> members = getOrganizationMembers(
            request.getMemberGroups(), provider.getGroups(), Identity.GROUP,
            ResourceType.SECURITY_GROUP, id, newOrgId, securityProvider, principal);
         members.addAll(getOrganizationMembers(
            request.getMemberUsers(), provider.getUsers(), Identity.USER,
            ResourceType.SECURITY_USER, id, newOrgId, securityProvider, principal));
         members.addAll(getOrganizationMembers(
            request.getRoles(), provider.getRoles(), Identity.ROLE,
            ResourceType.SECURITY_ROLE, id, newOrgId, securityProvider, principal));

         // setIdentity() applies the theme and locale and clears them when they are null or
         // empty, so a theme or locale that the request omits keeps the organization's current
         // value, and an empty string clears it. See getOrganizationLocale() for the locale.
         // Only a theme that the request supplies is resolved: a theme name is resolved to its
         // id before setIdentity(), whose eligibility checks only match ids, and eligibility uses
         // the current org id, as those checks do. A value that matches no eligible theme, and the
         // reserved "default" id, is passed on unchanged for those checks to handle. The kept
         // current theme is passed through untouched, so an update that omits the theme never
         // rewrites a stored value (Bug #77117).
         final String requestedTheme = request.getTheme();
         final String theme = requestedTheme == null ? currentOrganization.getTheme() :
            findOrganizationTheme(requestedTheme, oldOrganization.getId())
               .map(CustomTheme::getId)
               .orElse(requestedTheme);

         EditOrganizationPaneModel.Builder builder = EditOrganizationPaneModel.builder()
            .name(request.getName())
            .id(newOrgId)
            .oldName(oldOrganization.getName())
            .locale(getOrganizationLocale(request.getLocale(), currentOrganization))
            .theme(theme)
            // the REST organization has no status, keep the stored one (Bug #77147)
            .status(currentOrganization.isActive())
            .members(members);

         if(request.getRoles() != null) {
            List<IdentityID> roles = request.getRoles().stream()
               .map(role -> new IdentityID(role, newOrgId)).collect(Collectors.toList());
            builder.roles(roles);
         }

         if(request.getAdminIdentities() != null) {
            builder = builder.permittedIdentities(
               convertAdminIdentitiesModel(request.getAdminIdentities(), securityProvider, principal));
         }

         organizationModel = builder.build();
      }
      catch(Exception e) {
         throw new PreMutationRefusalException(e.getMessage(), e);
      }

      IdentityID newId = new IdentityID(organizationModel.name(), organizationModel.id());
      // an id change migrates the self grant to (new name, new id) inside setIdentity(), so the
      // existing grant is read from the new key then; otherwise it is still at (old name, id)
      boolean idChanged = !Tool.equals(oldOrganization.getId(), organizationModel.id());
      IdentityID oldId = idChanged ? newId :
         new IdentityID(oldOrganization.getName(), oldOrganization.getId());

      // an organization ID change moves the organization's themes and theme selections in
      // setIdentity() (AbstractEditableAuthenticationProvider.copyThemes())
      identityService.setIdentity(oldOrganization, organizationModel, provider, principal);
      applyOrganizationProperties(id, request.getProperties());
      // a request without adminIdentities keeps the existing admins
      setOrganizationPermissions(
         oldId, newId, securityProvider, principal,
         request.getAdminIdentities() == null ? null : organizationModel.permittedIdentities());
   }

   /**
    * Throws {@link ResourceExistsException} if the requested organization name or id collides
    * with another organization's name or id (see {@link OrganizationIdentityConflict}).
    */
   private static void checkOrganizationIdentityConflict(SecurityProvider securityProvider,
                                                         Organization editedOrg,
                                                         SecurityOrganization request)
      throws ResourceExistsException
   {
      OrganizationIdentityConflict conflict = OrganizationIdentityConflict.find(
         securityProvider, editedOrg, request.getName(), request.getId());

      if(conflict == OrganizationIdentityConflict.ID) {
         throw new ResourceExistsException(request.getId());
      }
      else if(conflict == OrganizationIdentityConflict.NAME) {
         throw new ResourceExistsException(request.getName());
      }
   }

   /**
    * Writes an organization's self grant (who may administer the organization) from the public
    * API. The grant is always stored in the organization's own permission bucket, keyed by
    * (name, id), with the grantees scoped to the organization, which is where the permission
    * check reads it. The org endpoints do not switch into the target organization, so the
    * ambient-org permission calls would use the caller's current org (normally host-org) as the
    * bucket and the grantee scope instead (Bug #77206).
    * <p>
    * The merge is {@code IdentityService.setIdentityPermissions()} with the
    * organization as the explicit bucket org (Bug #77271). Only the parts that differ from EM
    * are kept here: an omitted list keeps the existing admins, and the legacy (id, id) grant is
    * the merge base when there is no grant at {@code oldKey}.
    * <p>
    * As in the permission check and EM (Bug #77185), the legacy (id, id) grant is
    * effective only when there is no grant at {@code oldKey}. When there is one, the legacy grant
    * is stale and its grantees and edited flag are never merged: a rename back to the id
    * overwrites it, and an explicit list update otherwise removes it. When there is no grant at
    * {@code oldKey}, the legacy grant is the base of an explicit list update, so its grantees the
    * caller cannot administer are kept, and an omitted list leaves it in place.
    *
    * @param oldKey              the key the existing grant is stored under.
    * @param newKey              the key to store the grant under, (name, id) of the organization.
    * @param permittedIdentities the requested admins, or {@code null} to keep the existing admins,
    *                            in which case the grant is only re-keyed when the name changed.
    */
   private void setOrganizationPermissions(IdentityID oldKey, IdentityID newKey,
                                           SecurityProvider securityProvider, Principal principal,
                                           List<IdentityModel> permittedIdentities)
   {
      final ResourceType type = ResourceType.SECURITY_ORGANIZATION;
      final String orgId = newKey.getOrgID();

      if(permittedIdentities == null && oldKey.equals(newKey)) {
         return;
      }

      AuthorizationProvider authzProvider = securityProvider.getAuthorizationProvider();
      Permission permission = authzProvider.getPermission(type, oldKey, orgId);

      if(permittedIdentities == null) {
         if(permission != null) {
            // the check ignores a legacy (id, id) grant while a grant exists at oldKey
            // (Bug #77185), so a rename back to the id overwrites it rather than merging it
            authzProvider.setPermission(type, newKey, permission, orgId);
            authzProvider.removePermission(type, oldKey, orgId);
         }

         return;
      }

      // an org that has only the legacy (id, id) grant (no rename since before the (name, id)
      // key) has nothing at oldKey. Merge from the legacy grant then, so its grantees the caller
      // cannot administer survive the legacy remove. When there is a grant at oldKey the check
      // ignores the legacy grant (Bug #77185), so it is stale and is removed or overwritten
      // without merging its grantees.
      IdentityID baseKey = oldKey;
      IdentityID legacyKey = orgId == null ? null : new IdentityID(orgId, orgId);

      if(permission == null && legacyKey != null && !legacyKey.equals(oldKey) &&
         authzProvider.getPermission(type, legacyKey, orgId) != null)
      {
         baseKey = legacyKey;
      }

      identityService.setIdentityPermissions(
         baseKey, newKey, type, principal, permittedIdentities, orgId, orgId);
   }

   /**
    * Throws {@link ResourceExistsException} if a requested member user, group or role already
    * exists in the new organization. Creating the members replaces an existing identity with a
    * blank one, so this must be checked before any write.
    */
   private static void checkNoExistingMembers(EditableAuthenticationProvider provider,
                                              SecurityOrganization request)
      throws ResourceExistsException
   {
      String oid = request.getId();

      if(request.getMemberUsers() != null) {
         for(String name : request.getMemberUsers()) {
            if(provider.getUser(new IdentityID(name, oid)) != null) {
               throw new ResourceExistsException(name);
            }
         }
      }

      if(request.getMemberGroups() != null) {
         for(String name : request.getMemberGroups()) {
            if(provider.getGroup(new IdentityID(name, oid)) != null) {
               throw new ResourceExistsException(name);
            }
         }
      }

      if(request.getRoles() != null) {
         for(String name : request.getRoles()) {
            if(provider.getRole(new IdentityID(name, oid)) != null) {
               throw new ResourceExistsException(name);
            }
         }
      }
   }

   /**
    * Gets the locale label to pass to setIdentity() for an organization update. The Public API
    * takes a locale code (see {@link #validateLocale}), while setIdentity() expects the label of
    * the locale in locale.properties (e.g. "English(America)") and maps it to the locale key that
    * it stores (e.g. "en_US"). An empty string clears the locale. If the request omits the locale
    * ({@code null}), the label of the organization's current locale key is returned, so that the
    * locale is unchanged. This has two limitations, because setIdentity() only accepts labels:
    * <ul>
    *    <li>a current locale key that has no label in locale.properties is cleared;</li>
    *    <li>if locale.properties gives the same label to more than one key, setIdentity() may map
    *       the label to another of those keys.</li>
    * </ul>
    *
    * @param requestLocale the locale code in the request, or {@code null} if omitted.
    * @param organization  the organization in the editable provider.
    */
   private String getOrganizationLocale(String requestLocale, Organization organization)
      throws InvalidResourceException
   {
      return getLocaleLabel(requestLocale, organization.getLocale());
   }

   /**
    * Gets the locale label to pass to setIdentity() for an organization or user update, see
    * {@link #getOrganizationLocale}: the label of the request locale code if present, otherwise
    * the label of the current locale key, so that an omitted locale is unchanged (Bug #77326).
    *
    * @param requestLocale the locale code in the request, or {@code null} if omitted.
    * @param localeKey     the current locale key of the identity in the editable provider.
    */
   private String getLocaleLabel(String requestLocale, String localeKey)
      throws InvalidResourceException
   {
      if(requestLocale != null) {
         return toLocaleLabel(requestLocale);
      }

      return localeKey == null ? null : SUtil.loadLocaleProperties().getProperty(localeKey);
   }

   /**
    * Finds the theme requested for an organization among the themes eligible for it (a global
    * theme or one of the organization's own themes). A value that is the id of an eligible theme
    * selects that theme; otherwise it is matched against the names of the eligible themes. When
    * several eligible themes have that name, the organization's own theme wins over a global one,
    * then the lowest id. Used by both createOrganization() and updateOrganization() (Bug #77117).
    * <p>
    * The reserved id {@link CustomTheme#DEFAULT_THEME_ID} ({@code "default"}, exact case) means
    * "use the default theme", like an empty value (Bug #77304), so it is never resolved, not even
    * to a theme named "default" (such a theme is only reachable by its own id, e.g.
    * {@code default1}). On update it is passed through and the sink clears the theme; on create
    * no theme is stored.
    *
    * @param requested the theme id or name from the request.
    * @param orgID     the id of the organization the theme must be eligible for.
    *
    * @return the matching theme, or empty if {@code requested} is empty or the reserved default
    *         id, or matches no eligible theme.
    */
   private Optional<CustomTheme> findOrganizationTheme(String requested, String orgID) {
      if(Tool.isEmptyString(requested) || CustomTheme.isReservedId(requested)) {
         return Optional.empty();
      }

      // Same eligibility rule as IdentityService.getEligibleOrgTheme(), which compares org ids
      // with Tool.equals(); IdentityService.checkOrganizationTheme() compares them ignoring case,
      // so the two sink checks only differ for org ids that differ in case.
      List<CustomTheme> eligible = customThemesManager.getCustomThemes().stream()
         .filter(t -> Tool.isEmptyString(t.getOrgID()) || Tool.equals(t.getOrgID(), orgID))
         .toList();
      Optional<CustomTheme> byId = eligible.stream()
         .filter(t -> Tool.equals(t.getId(), requested))
         .findFirst();

      if(byId.isPresent()) {
         return byId;
      }

      // an id only wins among eligible themes, so a value equal to the org's current but no
      // longer eligible theme id can still resolve by name to an eligible theme
      return eligible.stream()
         .filter(t -> t.getId() != null && Tool.equals(t.getName(), requested))
         .min(Comparator.comparing((CustomTheme t) -> Tool.isEmptyString(t.getOrgID()))
                 .thenComparing(CustomTheme::getId));
   }

   /**
    * Keeps the grant of who may administer (or assign) a user, group or role, for an update that
    * omits adminIdentities (Bug #77326). IdentityService.setIdentityPermissions() treats an
    * omitted list like an empty one and drops every grantee the caller can administer, so it is
    * not called. setIdentity() renames the identity where it is a grantee, but not the grant keyed
    * by the identity itself, so on a rename that grant is moved to the new key. Nothing is created
    * when there is no grant. The grant is in the current org's permission storage, where the
    * update with a list reads and writes it.
    */
   private static void keepIdentityPermissions(IdentityID oldId, IdentityID newId,
                                               ResourceType type,
                                               SecurityProvider securityProvider)
   {
      if(oldId.equals(newId)) {
         return;
      }

      AuthorizationProvider authzProvider = securityProvider.getAuthorizationProvider();
      Permission permission = authzProvider.getPermission(type, oldId);

      if(permission != null) {
         authzProvider.setPermission(type, newId, permission);
         authzProvider.removePermission(type, oldId);
      }
   }

   /**
    * Gets the members of one identity type for an organization update. If the request omits the
    * list ({@code null}), the organization's current members of that type are kept. They are
    * taken from the editable provider, whose identities setIdentity() compares the members with,
    * so that no identity of another provider is created in it. They are not filtered by the
    * caller's permissions, because they do not change, and setIdentity() would delete a group or
    * role that is missing. A list that is present, even an empty one, replaces the members of
    * that type that the caller can administer.
    *
    * @param requestNames the member names in the request, or {@code null} if omitted.
    * @param currentIds   the identities of that type in the editable provider.
    * @param type         the identity type, one of the {@link Identity} constants.
    * @param idType       the resource type used to check the caller's permission.
    * @param orgId        the current id of the organization.
    * @param newOrgId     the organization id in the request.
    */
   private List<IdentityModel> getOrganizationMembers(List<String> requestNames,
                                                      IdentityID[] currentIds, int type,
                                                      ResourceType idType, String orgId,
                                                      String newOrgId,
                                                      SecurityProvider securityProvider,
                                                      Principal principal)
   {
      Collection<IdentityID> ids;

      if(requestNames == null) {
         ids = currentIds == null ? Collections.emptyList() : Arrays.stream(currentIds)
            .filter(identityID -> Tool.equals(orgId, identityID.getOrgID()))
            .collect(Collectors.toList());
      }
      else {
         List<IdentityID> requestIds = requestNames.stream()
            .map(n -> new IdentityID(n, orgId)).collect(Collectors.toList());
         ids = filterPermittedIds(requestIds, idType, securityProvider, principal, true);
      }

      return ids.stream()
         .map(identityID -> IdentityModel.builder()
            .identityID(new IdentityID(identityID.getName(), newOrgId))
            .type(type)
            .build())
         .collect(Collectors.toList());
   }

   /**
    * Gets the users or groups that are currently direct members of a group, for a group update
    * that omits that member list. They are taken from the editable provider, whose identities
    * setIdentity() compares the members with, and looked up by the group's current id so that a
    * rename keeps them. They are not filtered by the caller's permissions, because they do not
    * change, and setIdentity() would remove the group from any member that is missing.
    *
    * @param groupId     the current id of the group.
    * @param currentIds  the users or groups in the editable provider.
    * @param getIdentity gets a user or group from the editable provider.
    */
   private static List<IdentityID> getGroupMembers(IdentityID groupId, IdentityID[] currentIds,
                                                   Function<IdentityID, ? extends Identity> getIdentity)
   {
      // group membership is stored by group name, within the group's organization
      return currentIds == null ? new ArrayList<>() : Arrays.stream(currentIds)
         .filter(id -> Tool.equals(groupId.getOrgID(), id.getOrgID()) && !groupId.equals(id))
         .filter(id -> {
            Identity identity = getIdentity.apply(id);
            String[] groups = identity == null ? null : identity.getGroups();
            return groups != null && Arrays.asList(groups).contains(groupId.getName());
         })
         .collect(Collectors.toList());
   }

   /**
    * Gets the users or groups that a role is currently assigned to directly, for a role update
    * that omits that list. They are taken from the editable provider, whose identities
    * setIdentity() compares the assignees with, and looked up by the role's current id so that a
    * rename keeps them. They are not filtered by organization, because a global role may be
    * assigned in every organization, nor by the caller's permissions, because they do not change,
    * and setIdentity() would remove the role from any assignee that is missing. The role is
    * matched by its key, so that a global role addressed with the global organization key matches
    * the null organization its assignees store.
    *
    * @param roleId      the current id of the role.
    * @param currentIds  the users or groups in the editable provider.
    * @param getIdentity gets a user or group from the editable provider.
    */
   private static List<IdentityID> getRoleAssignees(IdentityID roleId, IdentityID[] currentIds,
                                                    Function<IdentityID, ? extends Identity> getIdentity)
   {
      String roleKey = roleId.convertToKey();

      return currentIds == null ? new ArrayList<>() : Arrays.stream(currentIds)
         .filter(id -> {
            Identity identity = getIdentity.apply(id);
            IdentityID[] roles = identity == null ? null : identity.getRoles();
            return roles != null && Arrays.stream(roles)
               .anyMatch(role -> role != null && roleKey.equals(role.convertToKey()));
         })
         .collect(Collectors.toList());
   }

   /**
    * Gets the users or groups that a global role is assigned to for a role update that lists
    * them. A listed name is a key ({@code name~;~orgID}) or a bare name of the current
    * organization. The list replaces the role's assignees in the current organization and in
    * the organizations it names by key, and the role's assignees in any other organization are
    * kept, because a global role's assignees store only their names and a bare name must not
    * remove the role from every other organization. A name that is not a user or group of the
    * editable provider is rejected rather than dropped, since a dropped name would remove the
    * role from that assignee. A name that the caller may not administer is left out, as for an
    * org role.
    *
    * @param roleId      the current id of the role.
    * @param names       the user or group names in the request.
    * @param currentIds  the users or groups in the editable provider.
    * @param getIdentity gets a user or group from the editable provider.
    * @param idType      the resource type used to check the caller's permission.
    * @param typeLabel   the identity type named in the error message.
    */
   private Collection<IdentityID> getGlobalRoleAssignees(
      IdentityID roleId, List<String> names, IdentityID[] currentIds,
      Function<IdentityID, ? extends Identity> getIdentity, ResourceType idType, String typeLabel,
      SecurityProvider securityProvider, Principal principal) throws MissingResourceException
   {
      String currentOrgID = OrganizationManager.getInstance().getCurrentOrgID();
      Set<String> listedOrgs = new HashSet<>();
      listedOrgs.add(currentOrgID);
      List<IdentityID> ids = new ArrayList<>();
      List<String> missing = new ArrayList<>();

      for(String name : names) {
         IdentityID id = name == null ? null : name.contains(IdentityID.KEY_DELIMITER) ?
            IdentityID.getIdentityIDFromKey(name) : new IdentityID(name, currentOrgID);

         if(id == null || id.orgID == null) {
            missing.add(name);
         }
         // a name the caller may not administer is left out as for an org role, before the
         // lookup, so that the error does not tell whether it exists
         else if(securityProvider.checkPermission(principal, idType, id.convertToKey(),
                                                  ResourceAction.ADMIN))
         {
            if(getIdentity.apply(id) == null) {
               missing.add(name);
            }
            else {
               ids.add(id);
            }
         }
      }

      if(!missing.isEmpty()) {
         throw new MissingResourceException(
            typeLabel + "(s) not found: " + String.join(", ", missing.stream()
               .map(String::valueOf).toList()) + ". A " + typeLabel.toLowerCase() +
            " of another organization than the current one is given as name" +
            IdentityID.KEY_DELIMITER + "organizationID.");
      }

      Set<IdentityID> result = new LinkedHashSet<>(ids);
      result.forEach(id -> listedOrgs.add(id.orgID));
      getRoleAssignees(roleId, currentIds, getIdentity).stream()
         .filter(id -> !listedOrgs.contains(id.orgID))
         .forEach(result::add);
      return result;
   }

   /**
    * Gets an organization id with the global organization key, the key form of the null
    * organization of a global identity, decoded to that null organization.
    */
   private static String decodeGlobalOrgKey(String orgID) {
      return orgID == null ? null :
         IdentityID.getIdentityIDFromKey(IdentityID.KEY_DELIMITER + orgID).orgID;
   }

   /**
    * Makes a theme the default of a new organization in the two places other than
    * {@code Organization.theme} that hold it: the theme's organizations list and the
    * organization's selected theme. This is what
    * {@code IdentityService.updateCustomThemeOrganization()} does when updateOrganization()
    * changes the theme, so the three copies stay in step (see claude/theme.md).
    */
   private void addOrganizationToTheme(String themeId, String orgID) {
      AtomicBoolean found = new AtomicBoolean();
      customThemesManager.updateCustomThemes(themes -> {
         CustomTheme theme = themes.stream()
            .filter(t -> Tool.equals(t.getId(), themeId))
            .findFirst().orElse(null);

         if(theme == null) {
            return null;
         }

         List<String> themeOrgs = theme.getOrganizations();

         if(!themeOrgs.contains(orgID)) {
            themeOrgs.add(orgID);
         }

         theme.setOrganizations(themeOrgs);
         found.set(true);
         return themes;
      });

      // the theme may have been removed since it was looked up; don't point at a missing theme
      if(found.get()) {
         customThemesManager.setOrgSelectedTheme(themeId, orgID);
      }
   }

   /**
    * Check if the caller may manage the organization. A site admin manages every org, anyone
    * else only their own org, whatever permission check passes for another org (Bug #77216).
    * The caller's home org is used, not the current org, which can be switched. Unlike
    * OrganizationAccess.check(), which skips callers that are not an XPrincipal, this denies
    * them, since the org here is the target of a read, update or delete.
    */
   private static boolean isOwnOrganization(String orgID, Principal principal) {
      if(OrganizationManager.getInstance().isSiteAdmin(principal)) {
         return true;
      }

      return orgID != null && principal instanceof XPrincipal xp && orgID.equals(xp.getOrgId());
   }

   /**
    * Gets the organization's own default theme, {@code Organization.theme}, which is what the
    * PUT compares against and writes back. Like the EM organization pane
    * (UserTreeService.getOrganizationModel()), it is only reported when it is the ID of an
    * existing theme, so a legacy theme name or a deleted theme reads as no theme.
    */
   private String getOrganizationTheme(Organization organization) {
      String themeId = organization == null ? null : organization.getTheme();

      if(themeId == null) {
         return null;
      }

      return customThemesManager.getCustomThemes().stream()
         .anyMatch(theme -> themeId.equals(theme.getId())) ? themeId : null;
   }

   public void deleteOrganization(String organizationid, Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();
      AuthenticationProvider provider = getEditableAuthenticationProvider(securityProvider);

      if(!isOwnOrganization(organizationid, principal) ||
         !securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
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
      organizationModel.setId(identityID.orgID);
      organizationModel.setTheme(getOrganizationTheme(organization));
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
            checkAssignableRoles(inheritedRoles, principal);
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
         themeService.updateIdentityTheme(id.name, id.name, id.orgID, request.getTheme(),
                                          CustomTheme::getRoles, principal);
         setIdentityPermissions(id, ResourceType.SECURITY_ROLE, securityProvider, principal,
            request.getAdminIdentities());
         record.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
         Audit.getInstance().auditIdentityInfo(identityInfoRecord, principal);
      }
      finally {
         Audit.getInstance().auditAction(record, principal);
      }
   }

   /**
    * Updates a role. A property or list that the request omits ({@code null}) leaves that
    * property of the role unchanged. A value that is present replaces the current one, and an
    * empty list or string clears it.
    */
   public void updateRole(IdentityID roleId, SecurityRole request, Principal principal) throws Exception {
      SecurityProvider securityProvider = this.securityEngine.getSecurityProvider();
      EditableAuthenticationProvider provider = getEditableAuthenticationProvider(securityProvider);
      final Role oldRole = securityProvider.getRole(roleId);
      EditRolePaneModel roleModel;
      String permOrgId;

      try {
         if(!securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                              roleId.convertToKey(), ResourceAction.ADMIN))
         {
            throw new UnauthorizedAccessException("Permission denied to update role");
         }

         final Role currentRole = provider.getRole(roleId);

         if(currentRole == null) {
            if(securityProvider.getRole(roleId) == null) {
               throw new MissingResourceException(roleId.name);
            }

            throw new InvalidResourceException();
         }

         // Use the validated org from the path/query, not the request body, to prevent an org
         // admin from smuggling a different org into the body and writing a role cross-tenant.
         // The provider lookups and permission checks above and below use the role's key, which
         // the global org key shares with the null org of a global role, but setIdentity() and
         // the themes compare the org itself, so a global role is passed on with its null org,
         // as EM does (Bug #77327).
         String orgID = decodeGlobalOrgKey(roleId.orgID);
         boolean globalRole = orgID == null;
         // A global (org-less) role has no org of its own, so its admin grants are scoped to the
         // editing org. Pass that explicitly rather than "" -- IdentityService.setIdentityPermissions
         // refuses an all-empty org (bug #76866), and by then setIdentity() has already committed the
         // rename/member change. Resolved here so a failure is still a pre-mutation refusal.
         permOrgId = Tool.isEmptyString(orgID) ?
            OrganizationManager.getInstance().getCurrentOrgID(principal) : orgID;
         List<String> assignedUsers = request.getAssignedUsers();
         List<String> assignedGroups = request.getAssignedGroups();
         // a member list that the request omits leaves the role's assignees of that type unchanged
         Collection<IdentityID> userIds = assignedUsers == null ?
            getRoleAssignees(roleId, provider.getUsers(), provider::getUser) :
            globalRole ?
               getGlobalRoleAssignees(roleId, assignedUsers, provider.getUsers(), provider::getUser,
                                      ResourceType.SECURITY_USER, "User", securityProvider,
                                      principal) :
            filterPermittedIds(assignedUsers.stream().map(n -> new IdentityID(n, orgID)).toList(),
                               ResourceType.SECURITY_USER, securityProvider, principal);
         Collection<IdentityID> groupIds = assignedGroups == null ?
            getRoleAssignees(roleId, provider.getGroups(), provider::getGroup) :
            globalRole ?
               getGlobalRoleAssignees(roleId, assignedGroups, provider.getGroups(),
                                      provider::getGroup, ResourceType.SECURITY_GROUP, "Group",
                                      securityProvider, principal) :
            filterPermittedIds(assignedGroups.stream().map(n -> new IdentityID(n, orgID)).toList(),
                               ResourceType.SECURITY_GROUP, securityProvider, principal);

         List<IdentityModel> asssignedIDs =
            userIds
               .stream()
               .map(group -> IdentityModel.builder()
                  .identityID(group)
                  .type(Identity.USER)
                  .build())
               .collect(Collectors.toList());

         asssignedIDs.addAll(
            groupIds
               .stream()
               .map(user -> IdentityModel.builder()
                  .identityID(user)
                  .type(Identity.GROUP)
                  .build())
               .collect(Collectors.toList()));

         // an omitted description or inherited role list keeps the role's current value, from the
         // editable provider (Bug #77326)
         EditRolePaneModel.Builder builder = EditRolePaneModel.builder()
            .name(request.getIdentityID() == null ? roleId.name : request.getIdentityID().getName())
            .oldName(roleId.name)
            .organization(orgID)
            // request already carries either the caller's override or the current value, preserved
            // by IdentityMerge.mergeRole before this method is called from the identities apply path
            // -- reading info/provider's OWN current state here (as this used to) would make request's
            // value inert. A raw REST caller who omits the field gets Boolean.TRUE.equals(null) ==
            // false, the same blind-overwrite-on-omission contract every other field in this builder
            // already has (theme below never falls back to the current value either).
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
            .description(request.getDescription() != null ?
                            request.getDescription() : currentRole.getDescription())
            .theme(request.getTheme())
            .members(asssignedIDs);

         if(request.getInheritedRoles() != null) {
            builder.roles(filterSystemAdminRoles(request.getInheritedRoles(), securityProvider, principal));
         }
         else if(currentRole.getRoles() != null) {
            builder.roles(Arrays.asList(currentRole.getRoles()));
         }

         if(request.getAdminIdentities() != null) {
            builder = builder.permittedIdentities(
               convertAdminIdentitiesModel(request.getAdminIdentities(), securityProvider, principal));
         }

         roleModel = builder.build();
      }
      catch(Exception e) {
         throw new PreMutationRefusalException(e.getMessage(), e);
      }

      IdentityID oldId = new IdentityID(roleModel.oldName(), roleModel.organization());
      IdentityID newId = new IdentityID(roleModel.name(), roleModel.organization());

      identityService.setIdentity(oldRole, roleModel, provider, principal);
      themeService.updateIdentityTheme(oldRole.getName(), roleModel.name(),
                                       roleModel.organization(), roleModel.theme(),
                                       CustomTheme::getRoles, principal);

      if(request.getAdminIdentities() == null) {
         keepIdentityPermissions(oldId, newId, ResourceType.SECURITY_ROLE, securityProvider);
      }
      else {
         identityService.setIdentityPermissions(
            oldId, newId, ResourceType.SECURITY_ROLE, principal,
            renameOwnPermittedEntry(roleModel.permittedIdentities(), Identity.ROLE, oldId, newId),
            permOrgId);
      }

      // Run the same rename migration as EM (VPM hidden-column roles), on the stored role's name
      // and org, falling back to the path's. A global role has a null org or the global org key,
      // and both update every org's VPMs. Keep this last so a migration failure can't skip the
      // permission update. setIdentityPermissions above uses the path org; the two can only
      // diverge for a hypothetical provider whose stored role org differs from the path org.
      String roleOrgID = oldRole.getOrganizationID() != null ?
         oldRole.getOrganizationID() : roleId.orgID;
      String oldRoleName = oldRole.getName() != null ? oldRole.getName() : roleId.name;
      IdentityID oldRoleID = new IdentityID(oldRoleName, roleOrgID);
      IdentityID newRoleID = new IdentityID(roleModel.name(), roleOrgID);

      if(!oldRoleID.equals(newRoleID)) {
         userTreeService.migrateRoleRename(oldRoleID, newRoleID);
      }
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

      // a global role is deleted with its null org, so that it is also removed from the users
      // and groups of every organization that hold it (Bug #77327)
      deleteIdentity(new IdentityID(role.name, decodeGlobalOrgKey(role.orgID)), Identity.ROLE,
                     principal, provider);
   }

   private SecurityRole getRoleModel(IdentityID identityID, Principal principal,
                                     AuthenticationProvider provider)
   {

      IdentityInfo info = identityService.getIdentityInfo(identityID, Identity.ROLE, provider);
      // a global role addressed with the global org key has the null org that the themes
      // compare (Bug #77327)
      IdentityID roleID = new IdentityID(identityID.name, decodeGlobalOrgKey(identityID.orgID));

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
      roleModel.setIdentityID(roleID);
      roleModel.setDescription(((Role) info.getIdentity()).getDescription());
      roleModel.setTheme(themeService.getTheme(roleID, CustomTheme::getRoles));
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
    * Resolve a caller-supplied role reference to the role it actually refers to. A role
    * requested with the caller's own org id may in fact be a global role (orgID == null, e.g.
    * "Administrator"/"Organization Administrator"), which is stored under a different key than
    * a plain org-scoped lookup would try -- fall back to the global key only when the direct
    * lookup already found nothing, so a genuine org-scoped role of the same name still wins.
    *
    * @return the resolved {@code IdentityID}, or {@code null} if the role does not exist at all.
    */
   private IdentityID resolveRoleReference(AuthenticationProvider provider, IdentityID requested) {
      if(provider.getRole(requested) != null) {
         return requested;
      }

      if(requested.orgID != null) {
         IdentityID globalKey = new IdentityID(requested.name, null);

         if(provider.getRole(globalKey) != null) {
            return globalKey;
         }
      }

      return null;
   }

   /**
    * Resolve every role in {@code roles} via {@link #resolveRoleReference}, throwing a
    * {@link MissingResourceException} naming any role that does not resolve instead of silently
    * dropping it.
    */
   private Set<IdentityID> resolveRoleReferencesOrThrow(List<IdentityID> roles,
                                                         AuthenticationProvider provider)
      throws MissingResourceException
   {
      Set<IdentityID> resolved = new HashSet<>();
      List<String> unresolved = new ArrayList<>();

      for(IdentityID role : roles) {
         IdentityID resolvedRole = resolveRoleReference(provider, role);

         if(resolvedRole == null) {
            unresolved.add(role.name);
         }
         else {
            resolved.add(resolvedRole);
         }
      }

      if(!unresolved.isEmpty()) {
         throw new MissingResourceException("Role(s) not found: " + String.join(", ", unresolved));
      }

      return resolved;
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
    * Rejects the roles requested for a new user, group or role when the caller is not a site
    * administrator and may not assign one of them, or when one of them leads to an organization
    * administrator role and the caller is not an organization administrator (Bug #77381).
    * Identities created through this API are written to the provider directly, bypassing the
    * role assignment check in IdentityService.setIdentity(). Only call this with roles taken from
    * the request, not with default roles the API applies on its own.
    */
   private void checkAssignableRoles(Collection<IdentityID> roles, Principal principal)
      throws UnauthorizedAccessException
   {
      try {
         identityService.checkAssignableRoles(roles, null, principal);
      }
      catch(java.lang.SecurityException e) {
         throw new UnauthorizedAccessException("Permission denied to assign the requested roles");
      }
   }

   /**
    * Rejects placing an identity under any of the given parent groups when the group, or one of
    * its ancestors, grants system administrator and the caller is not a site administrator.
    * Group membership set through this API is written to the provider directly, bypassing the
    * system administrator grant check in IdentityService.setIdentity() (Bug #77073).
    */
   private void checkSystemAdminParentGroups(Collection<String> parentGroups, String orgID,
                                             Principal principal)
      throws UnauthorizedAccessException
   {
      if(parentGroups == null) {
         return;
      }

      for(String parentGroup : parentGroups) {
         try {
            identityService.checkSystemAdminParentGroup(parentGroup, orgID, principal);
         }
         catch(java.lang.SecurityException e) {
            throw new UnauthorizedAccessException(
               "Permission denied to add a member to group " + parentGroup);
         }
      }
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

      // A role that inherits a system administrator role grants the same privileges.
      if(provider.getRole(role) != null &&
         Arrays.stream(provider.getAllRoles(new IdentityID[] { role }))
            .anyMatch(r -> r != null && provider.isSystemAdministratorRole(r)))
      {
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
      // Pass orgId explicitly so the storage key itself is scoped to the org's own id, not
      // whatever org happens to be ambient at write time (bug #76866) -- otherwise a later
      // org rename's migration filter (which matches on storage-key orgId) can never find it.
      authzProvider.setPermission(resourceType, identityID, permission, orgId);
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

   /**
    * Points the renamed identity's own entry in a permitted-identities list at its new name.
    * The list is typically read (GET) before the rename, so it names the renamed identity by its
    * old name. setIdentity() has already renamed the grantee in the stored permission, and
    * setIdentityPermissions() replaces the grantees with this list, so an entry left under the old
    * name would grant the (no longer existing) old identity instead (Bug #77114). EM does the same
    * in UserTreeService.getRenamedPermittedIdentities(), but without re-stamping other entries.
    *
    * Only an entry of the renamed identity's type is rewritten. A user or group entry matches by
    * name only, since setIdentityPermissions() stamps the path org on user and group grantees
    * anyway. A role entry must also match the org, as an org role and a global (null org) role
    * can share a name. Every other entry is kept as is.
    */
   private static List<IdentityModel> renameOwnPermittedEntry(
      List<IdentityModel> permittedIdentities, int type, IdentityID oldId, IdentityID newId)
   {
      if(permittedIdentities == null || oldId.equals(newId)) {
         return permittedIdentities;
      }

      return permittedIdentities.stream()
         .map(identity -> isOwnEntry(identity, type, oldId) ?
            IdentityModel.builder().from(identity).identityID(newId).build() : identity)
         .collect(Collectors.toList());
   }

   /**
    * Checks whether a permitted-identities entry names the renamed identity by its old id, using
    * the matching rules described in renameOwnPermittedEntry(). A role entry must match the org
    * exactly; organization ids that differ only by case cannot be created.
    */
   private static boolean isOwnEntry(IdentityModel identity, int type, IdentityID oldId) {
      return identity.type() == type &&
         Objects.equals(identity.identityID().name, oldId.name) &&
         (type != Identity.ROLE || Objects.equals(identity.identityID().orgID, oldId.orgID));
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
    * <p>
    * Also thrown by {@link #updateUser}/{@link #updateGroup}/{@link #updateRole}/
    * {@link #updateOrganization} for a failure in their own precondition segment -- permission
    * check, existence check, or request-model building -- which is likewise guaranteed to run
    * entirely before {@code IdentityService.setIdentity} is ever called, i.e. before any entity
    * of any kind has been mutated. This is a narrower boundary than delete's own: it does NOT
    * cover a failure inside {@code setIdentity} itself (e.g. the duplicate-rename-name check, or
    * any of the cross-entity mutations {@code setUserInfo}/{@code setGroupInfo}/
    * {@code setRoleInfo}/{@code setOrganizationInfo} perform before their own entity's write) --
    * those remain unwrapped and surface as whatever exception type they naturally throw.
    */
   public static class PreMutationRefusalException extends Exception {
      public PreMutationRefusalException(String message) {
         super(message);
      }

      public PreMutationRefusalException(String message, Throwable cause) {
         super(message, cause);
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
      ActionTreeNode node = getActionTreeNode(resourcePath, type, principal);

      if(!checkPermissionAccess(resourcePath, type, node, principal)) {
         throw new UnauthorizedAccessException("Permission denied to access permission");
      }

      String orgId = OrganizationManager.getInstance().getCurrentOrgID();
      boolean siteAdmin = OrganizationManager.getInstance().isSiteAdmin(principal);
      // the null-org (global) role grants of the request, the other grants are in orgId
      Set<PermissionGrant> globalRoleRequests = new HashSet<>();

      Permission permission = securityProvider.getPermission(type, resourcePath);
      permission = permission == null ? new Permission() : permission;

      // Bug #77274, validate every grant before anything is written. The permission is stored in
      // the current org's bucket, where only the current org's grantees (and global roles) are
      // read, and an action tree node may only be granted the actions it offers (as in EM,
      // Bug #77251).
      for(PermissionGrant grant : permissionModel.grants) {
         String grantOrg = grant.getIdentityID().orgID;

         if(grantOrg != null && !grantOrg.equalsIgnoreCase(orgId)) {
            throw new UnauthorizedAccessException(
               "Invalid organization " + grantOrg + " for " + grant.getType() + " " +
               grant.getIdentityID().name);
         }

         // the bucket the write below stores this grant in: a global role in the null bucket,
         // every other grantee in orgId
         boolean globalRole = grantOrg == null && "ROLE".equals(grant.getType()) &&
            isGlobalRole(grant.getIdentityID().name, orgId, securityProvider);
         String grantBucket = globalRole ? null : orgId;

         if(node != null) {
            for(String action : grant.getActions()) {
               // Bug #77376, an action the node does not offer that the grantee already holds
               // (for example a default grant) is kept by EM, which writes only the node's
               // actions. Here it may only be sent back for that same stored grantee, where the
               // write puts it, but may not be added or moved.
               if(node.actions().stream().noneMatch(a -> a.name().equals(action)) &&
                  !isStoredGrantAction(permission, grant, action, grantBucket))
               {
                  throw new UnauthorizedAccessException(
                     "Unauthorized action " + action + " for action permission " + type + ":" +
                     resourcePath);
               }
            }
         }

         if(globalRole) {
            globalRoleRequests.add(grant);
         }
      }

      for(ResourceAction action : ResourceAction.values()) {
         Set<String> userGrants = new HashSet<>();
         Set<String> groupGrants = new HashSet<>();
         Set<String> organizationGrants = new HashSet<>();
         Set<String> roleGrants = new HashSet<>();
         Set<String> globalRoleGrants = new HashSet<>();

         // grantees the caller cannot administer must not be dropped by the caller's update
         keepGranteesCallerCannotAdminister(
            permission.getUserGrants(action, orgId), ResourceType.SECURITY_USER,
            userGrants, null, securityProvider, principal);
         keepGranteesCallerCannotAdminister(
            permission.getGroupGrants(action, orgId), ResourceType.SECURITY_GROUP,
            groupGrants, null, securityProvider, principal);
         keepGranteesCallerCannotAdminister(
            permission.getRoleGrants(action, orgId), ResourceType.SECURITY_ROLE,
            roleGrants, globalRoleGrants, securityProvider, principal);
         keepGranteesCallerCannotAdminister(
            permission.getOrganizationGrants(action, orgId), ResourceType.SECURITY_ORGANIZATION,
            organizationGrants, null, securityProvider, principal);

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
                  if(globalRoleRequests.contains(grant)) {
                     globalRoleGrants.add(grant.getIdentityID().name);
                  }
                  else {
                     roleGrants.add(grant.getIdentityID().name);
                  }
               }
            }
         }

         permission.setUserGrantsForOrg(action, userGrants, orgId);
         permission.setGroupGrantsForOrg(action, groupGrants, orgId);
         permission.setRoleGrantsForOrg(action, roleGrants, orgId);
         permission.setOrganizationGrantsForOrg(action, organizationGrants, orgId);

         // only a site admin may change global (org-less) role grants (as in EM, Bug #77254).
         // A non site admin keeps the existing ones unchanged and its global role rows are
         // ignored.
         if(siteAdmin) {
            permission.setRoleGrantsForOrg(action, globalRoleGrants, null);
         }
      }

      // as before, a permission written through this API is not marked as edited for the org
      permission.removeGrantAllByOrg(orgId);
      securityProvider.setPermission(type, resourcePath, permission);
   }

   /**
    * Adds to {@code orgGrants} (or {@code globalGrants} for a grantee with no org) the grantees
    * the caller cannot administer. They are not in the requested list, because the caller cannot
    * see them, and must not be dropped by the caller's update.
    */
   private static void keepGranteesCallerCannotAdminister(
      Set<Permission.PermissionIdentity> grants, ResourceType identityType, Set<String> orgGrants,
      Set<String> globalGrants, SecurityProvider securityProvider, Principal principal)
   {
      EnumSet<ResourceAction> adminAction = EnumSet.of(ResourceAction.ADMIN);

      for(Permission.PermissionIdentity grant : grants) {
         if(!securityProvider.checkAnyPermission(
            principal, identityType,
            new IdentityID(grant.getName(), grant.getOrganizationID()).convertToKey(), adminAction))
         {
            if(grant.getOrganizationID() == null && globalGrants != null) {
               globalGrants.add(grant.getName());
            }
            else {
               orgGrants.add(grant.getName());
            }
         }
      }
   }

   /**
    * Checks if the stored permission already grants the action to the grantee of a requested
    * grant, that is to a stored grantee of the same type and name in the bucket (org id, or null
    * for a global role) the write stores the requested grant in.
    */
   private static boolean isStoredGrantAction(Permission permission, PermissionGrant grant,
                                              String action, String bucket)
   {
      int identityType = switch(String.valueOf(grant.getType())) {
         case "USER" -> Identity.USER;
         case "GROUP" -> Identity.GROUP;
         case "ROLE" -> Identity.ROLE;
         case "ORGANIZATION" -> Identity.ORGANIZATION;
         default -> -1;
      };
      ResourceAction resourceAction = Arrays.stream(ResourceAction.values())
         .filter(a -> a.name().equals(action))
         .findFirst()
         .orElse(null);

      if(identityType < 0 || resourceAction == null) {
         return false;
      }

      String name = grant.getIdentityID().name;
      return permission.getGrants(resourceAction, identityType, null).stream()
         .anyMatch(g -> Objects.equals(g.getName(), name) &&
            Objects.equals(g.getOrganizationID(), bucket));
   }

   /**
    * Checks if a role requested without an organization is a global (org-less) role, that is a
    * global role of that name exists and the current organization has no role of that name.
    */
   private static boolean isGlobalRole(String name, String orgId,
                                       SecurityProvider securityProvider)
   {
      return securityProvider.getRole(new IdentityID(name, null)) != null &&
         securityProvider.getRole(new IdentityID(name, orgId)) == null;
   }

   public void deletePermission(String resourcePath, String resourceType, Principal principal) throws Exception {
      SecurityProvider securityProvider = securityEngine.getSecurityProvider();
      ResourceType type = convertResourceType(resourceType);

      if(!checkPermissionAccess(resourcePath, type, principal)) {
         throw new UnauthorizedAccessException("Permission denied to access permission");
      }

      Permission permission = securityProvider.getPermission(type, resourcePath);

      if(permission == null) {
         return;
      }

      // Bug #77274, clear only the current org's grants, as EM does. Only a site admin may clear
      // the global (org-less) role grants.
      String orgId = OrganizationManager.getInstance().getCurrentOrgID();
      boolean siteAdmin = OrganizationManager.getInstance().isSiteAdmin(principal);

      for(ResourceAction action : ResourceAction.values()) {
         for(Identity.Type identityType : Identity.Type.values()) {
            if(identityType == Identity.Type.ROLE && siteAdmin) {
               permission.setGrantsOrgScoped(action, identityType.code(),
                                             Collections.emptySet(), null);
            }

            permission.setGrantsOrgScoped(action, identityType.code(),
                                          Collections.emptySet(), orgId);
         }
      }

      permission.removeGrantAllByOrg(orgId);

      if(permission.isBlank()) {
         securityProvider.removePermission(type, resourcePath);
      }
      else {
         securityProvider.setPermission(type, resourcePath, permission);
      }
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
      return checkPermissionAccess(
         resource, type, getActionTreeNode(resource, type, principal), principal);
   }

   /**
    * @param node the leaf of the caller's action tree for the resource, or {@code null} if the
    *             resource is not in the action tree.
    */
   private boolean checkPermissionAccess(String resource, ResourceType type, ActionTreeNode node,
                                         Principal principal)
   {
      SecurityProvider securityProvider = securityEngine.getSecurityProvider();

      //Check if resource is accessible from the em security/actions page
      //Otherwise use admin permission on the resource
      if(node != null) {
         return securityProvider.checkPermission(principal, ResourceType.EM_COMPONENT,
                                                 "settings/security/actions", ResourceAction.ACCESS);
      }
      else {
         return securityProvider.checkPermission(principal, type,
                                                 resource, ResourceAction.ADMIN);
      }
   }

   private ActionTreeNode getActionTreeNode(String resource, ResourceType type,
                                            Principal principal)
   {
      return findActionNode(actionPermissionService.getActionTree(principal), resource, type);
   }

   private ActionTreeNode findActionNode(ActionTreeNode node, String resource, ResourceType type) {
      if(node.children().isEmpty()) {
         return node.type() == type && node.resource() != null && node.resource().equals(resource) ?
            node : null;
      }
      else {
         for(ActionTreeNode child : node.children()) {
            ActionTreeNode found = findActionNode(child, resource, type);

            if(found != null) {
               return found;
            }
         }
      }

      return null;
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
