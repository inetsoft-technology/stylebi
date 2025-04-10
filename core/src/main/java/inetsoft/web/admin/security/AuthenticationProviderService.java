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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.mv.fs.FSService;
import inetsoft.mv.mr.XJobPool;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.*;
import inetsoft.sree.security.db.DatabaseAuthenticationProvider;
import inetsoft.sree.security.ldap.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.data.MapModel;
import inetsoft.web.admin.security.user.IdentityThemeService;
import inetsoft.web.service.BaseSubscribeChangHandler;
import inetsoft.web.viewsheet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.DestinationPatternsMessageCondition;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AuthenticationProviderService extends BaseSubscribeChangHandler implements MessageListener {
   @Autowired
   public AuthenticationProviderService(SecurityEngine securityEngine, ObjectMapper objectMapper,
                                        SimpMessagingTemplate messageTemplate,
                                        IdentityService identityService, IdentityThemeService themeService)
   {
      super(messageTemplate);
      this.securityEngine = securityEngine;
      this.objectMapper = objectMapper;
      this.identityService = identityService;
      this.themeService = themeService;
      Cluster.getInstance().addMessageListener(this);
   }

   @EventListener
   public void handleUnsubscribe(SessionUnsubscribeEvent event) {
      super.handleUnsubscribe(event);
   }

   @EventListener
   public void handleDisconnect(SessionDisconnectEvent event) {
      super.handleDisconnect(event);
   }

   @Override
   public Object getData(BaseSubscriber subscriber) {
      return "";
   }

   public Object addSubscriber(StompHeaderAccessor headerAccessor) {
      final String sessionId = headerAccessor.getSessionId();
      final MessageHeaders messageHeaders = headerAccessor.getMessageHeaders();
      final String destination = (String) messageHeaders
         .get(SimpMessageHeaderAccessor.DESTINATION_HEADER);
      final String lookupDestination = (String) messageHeaders
         .get(DestinationPatternsMessageCondition.LOOKUP_DESTINATION_HEADER);
      final String subscriptionId =
         (String) messageHeaders.get(SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER);
      final BaseSubscribeChangHandler.BaseSubscriber subscriber =
         new BaseSubscribeChangHandler.BaseSubscriber(sessionId, subscriptionId,
                                                      lookupDestination, destination, headerAccessor.getUser());

      return addSubscriber(subscriber);
   }

   public AuthenticationProviderModel getAuthenticationProvider(String name) {
      boolean enterprise = LicenseManager.getInstance().isEnterprise();
      AuthenticationProvider selectedProvider = getProviderByName(name);
      AuthenticationProviderModel.Builder builder = AuthenticationProviderModel.builder()
         .providerName(name)
         .dbProviderEnabled(enterprise)
         .customProviderEnabled(enterprise)
         .ldapProviderEnabled(!enterprise || !SUtil.isMultiTenant());

      if(selectedProvider instanceof FileAuthenticationProvider) {
         builder.providerType(SecurityProviderType.FILE);
      }
      else if(selectedProvider instanceof LdapAuthenticationProvider) {
         builder.providerType(SecurityProviderType.LDAP);
         builder.ldapProviderModel((LdapAuthenticationProvider) selectedProvider);
      }
      else if(selectedProvider instanceof DatabaseAuthenticationProvider) {
         builder.providerType(SecurityProviderType.DATABASE);
         builder.dbProviderModel((DatabaseAuthenticationProvider) selectedProvider);
      }
      else {
         builder.providerType(SecurityProviderType.CUSTOM);
         builder.customProviderModel(selectedProvider, objectMapper);
      }

      return builder.build();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_CREATE,
      objectType = ActionRecord.OBJECT_TYPE_SECURITY_PROVIDER
   )
   public void addAuthenticationProvider(AuthenticationProviderModel model,
                                         @AuditObjectName String name,
                                         @AuditUser Principal principal) throws Exception
   {
      AuthenticationChain chain = getAuthenticationChain()
         .orElseGet(() -> {
            securityEngine.newChain();
            return getAuthenticationChain().orElseThrow(() -> new MessageException("Could not initialize security."));
         });

      if(getProviderByName(model.providerName()) != null) {
         throw new MessageException(Catalog.getCatalog().getString("security.authentication.exists"));
      }

      AuthenticationProvider newProvider = getProviderFromModel(model)
         .orElseThrow(() -> new MessageException("Failed to create new authentication provider"));

      List<AuthenticationProvider> providerList = chain.getProviders();
      providerList.add(newProvider);
      chain.setProviders(providerList);
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectType = ActionRecord.OBJECT_TYPE_SECURITY_PROVIDER
   )
   public void editAuthenticationProvider(@AuditObjectName String name,
                                          AuthenticationProviderModel model,
                                          @AuditUser Principal principal) throws Exception {
      AuthenticationChain chain = getAuthenticationChain()
         .orElseThrow(() -> new IllegalStateException("The authentication chain has not been initialized."));

      if(!model.providerName().equals(name) &&
         getProviderByName(model.providerName()) != null) {
         throw new MessageException(Catalog.getCatalog().getString("security.authentication.exists"));
      }

      try {
         chain.writeLock();
         List<AuthenticationProvider> providerList = chain.getProviders();
         boolean found = false;

         for(int i = 0; i < providerList.size(); i++) {
            AuthenticationProvider provider = providerList.get(i);

            if(Objects.equals(name, provider.getProviderName())) {
               found = true;
               AuthenticationProvider newProvider = getProviderFromModel(model).orElseThrow(
                  () -> new MessageException("Failed to edit authentication provider"));
               providerList.set(i, newProvider);
               provider.tearDown();
               break;
            }
         }

         if(!found) {
            throw new MessageException(
               "Authentication provider named \"" + name + "\" does not exist");
         }

         chain.setProviders(providerList);
      }
      finally {
         chain.writeUnlock();
      }
   }

   public SecurityProviderStatusList getProviderListModel() {
      Optional<AuthenticationChain> chain = getAuthenticationChain();
      SecurityProviderStatusList.Builder builder = SecurityProviderStatusList.builder();

      if(!chain.isPresent()) {
         LOG.warn("The authentication chain has not been initialized.");
      }
      else {
         chain.get().stream()
            .map(p -> SecurityProviderStatus.builder().from(p).build())
            .forEach(builder::addProviders);
      }

      return builder.build();
   }

   public SecurityProviderStatus getCurrentProvider(Principal principal) {
      Optional<AuthenticationChain> chain = getAuthenticationChain();

      if(chain.isPresent()) {
         Optional<AuthenticationProvider> provider = chain.get().getProviders()
            .stream()
            .filter(p -> p.getUser(IdentityID.getIdentityIDFromKey(principal.getName())) != null)
            .findFirst();

         if(provider.isPresent()) {
            return SecurityProviderStatus.builder().from(provider.get()).build();
         }
      }

      return null;
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_DELETE,
      objectType = ActionRecord.OBJECT_TYPE_SECURITY_PROVIDER
   )
   public void removeAuthenticationProvider(int index,
                                            @AuditObjectName String providerName,
                                            @AuditUser Principal principal) throws Exception
   {
      AuthenticationChain chain = getAuthenticationChain()
         .orElseThrow(() -> new Exception("The authentication chain has not been initialized."));

      List<AuthenticationProvider> providerList = chain.getProviders();

      if(index >= 0 && index < providerList.size()) {
         AuthenticationProvider removedProvider = providerList.remove(index);
         providerList.stream()
            .filter(this::providerHasSysAdmins)
            .findFirst()
            .orElseThrow(() -> new MessageException(
               Catalog.getCatalog(principal).getString("em.security.noSystemAdminProvider")));

         List<String> removedOrgIds = Arrays.stream(removedProvider.getOrganizationIDs())
            .filter((orgId) -> providerList.stream()
               .noneMatch(provider -> Arrays.asList(provider.getOrganizationIDs()).contains(orgId)))
            .toList();
         removeOrganizations(removedOrgIds, removedProvider);

         removedProvider.tearDown();
         chain.setProviders(providerList);
      }
      else {
         throw new IndexOutOfBoundsException("Provider index out of bounds");
      }
   }

   private boolean providerHasSysAdmins(AuthenticationProvider provider) {
      return Arrays.stream(provider.getRoles())
         .anyMatch(role -> provider.isSystemAdministratorRole(role) && provider.getRoleMembers(role).length > 0);
   }

   private void removeOrganizations(List<String> removedOrgIds, AuthenticationProvider provider) {
      PortalThemesManager manager = PortalThemesManager.getManager();

      for(String orgId : removedOrgIds) {
         identityService.removeOrgProperties(orgId);
         identityService.removeOrgScopedDataSpaceElements(provider.getOrganization(orgId));
         themeService.removeTheme(orgId);
         FSService.clearServerNodeCache(orgId);
         XJobPool.resetOrgCache(orgId);
         manager.removeCSSEntry(orgId);
         manager.save();

         try{
            identityService.updateRepletRegistry(orgId, null);
            identityService.removeStorages(orgId);
         }
         catch(Exception e) {
            LOG.warn("Unable to remove old organization storage: "+e);
         }
      }
   }

   public void reorderAuthenticationProviders(ProviderListReorderModel reorderModel) throws Exception {
      AuthenticationChain providerChain = getAuthenticationChain()
         .orElseThrow(() -> new Exception("The authentication chain has not been initialized."));

      int source = reorderModel.source();
      int destination = reorderModel.destination();
      List<AuthenticationProvider> providers = providerChain.getProviders();

      //Check if the destination index is in bounds
      if(source >= 0 && source < providers.size() &&
         destination >= 0 && destination < providers.size()) {

         //Rotate is used to preserve the order of everything but the source
         if(source > destination) { //Moving "up" the list
            Collections.rotate(providers.subList(destination, source + 1), +1);
            providerChain.setProviders(providers);
         }
         else { //Moving "down" the list
            Collections.rotate(providers.subList(source, destination + 1), -1);
            providerChain.setProviders(providers);
         }
      }
      else {
         throw new IndexOutOfBoundsException("Source and/or Destination index out of bounds");
      }
   }

   public SecurityProviderStatus clearAuthenticationProviderCache(int index) throws Exception {
      AuthenticationProvider provider = getAuthenticationChain()
         .orElseThrow(() -> new Exception("The authentication chain has not been initialized."))
         .getProviders().get(index);
      provider.clearCache();
      return SecurityProviderStatus.builder()
         .from(provider)
         .build();
   }

   public AuthenticationProvider getProviderByName(String name) {
      if(!getAuthenticationChain().isPresent()) {
         LOG.error("em.common.security.noProvider");
         return null;
      }

      return getAuthenticationChain().get().stream()
         .filter((p) -> Catalog.getCatalog().getString(p.getProviderName()).equals(name))
         .findAny()
         .orElse(null);
   }

   public String testConnection(AuthenticationProviderModel model) throws Exception {
      try {
         Optional<AuthenticationProvider> provider = getProviderFromModel(model);

         if(!provider.isPresent()) {
            return Catalog.getCatalog().getString("em.security.testlogin.note4");
         }
      }
      catch(SRSecurityException secException) {
         return secException.getMessage();
      }

      // Connection OK
      return Catalog.getCatalog().getString("em.security.testlogin.note2");
   }

   public IdentityListModel getUsers(AuthenticationProviderModel model)
      throws Exception
   {
      AuthenticationProvider provider = getProviderFromModel(model).orElse(null);
      IdentityID[] users = new IdentityID[0];
      setIgnoreCache(provider, true);

      if(provider != null) {
         users = provider.getUsers();
      }

      setIgnoreCache(provider, false);

      //return sorted by organization, then name
      Arrays.sort(users, new Comparator<IdentityID>() {
         @Override
         public int compare(IdentityID o1, IdentityID o2) {
            if(Tool.equals(o1.orgID, o2.orgID)) {
               return Tool.compare(o1.name, o2.name);
            }

            return Tool.compare(o1.orgID, o2.orgID);
         }
      });

      return IdentityListModel.builder()
         .ids(users)
         .type(Identity.USER)
         .build();
   }

   public MapModel<String, Object> getUser(AuthenticationProviderModel model,
                                           IdentityID userid)
      throws Exception
   {
      AuthenticationProvider provider = getProviderFromModel(model).orElse(null);
      setIgnoreCache(provider, true);
      Map<String, Object> result = provider == null ? null
         : ((DatabaseAuthenticationProvider) provider).queryUser(userid);
      setIgnoreCache(provider, false);

      return new MapModel<>(result);
   }

   private String[] getDistinctValues(String[] names) {
      if(names == null || names.length == 1) {
         return names;
      }

      Set<String> distinctOrgs = new HashSet<>();
      distinctOrgs.addAll(Arrays.asList(names));
      return distinctOrgs.toArray(new String[distinctOrgs.size()]);
   }

   public String getOrganizationName(AuthenticationProviderModel model,
                                     String id)
      throws Exception
   {
      AuthenticationProvider provider = getProviderFromModel(model).orElse(null);
      setIgnoreCache(provider, true);
      String orgName = provider == null ? null :
         ((DatabaseAuthenticationProvider) provider).getOrganizationName(id);
      setIgnoreCache(provider, false);

      return orgName;
   }

   public IdentityListModel getUserEmails(AuthenticationProviderModel model,
                                          IdentityID userID)
      throws Exception
   {
      AuthenticationProvider provider = getProviderFromModel(model).orElse(null);
      setIgnoreCache(provider, true);
      IdentityID[] emails = provider == null ? new IdentityID[0] :
         Arrays.stream(provider.getEmails(userID)).map(e -> new IdentityID(e,userID.orgID)).toArray(IdentityID[]::new);
      setIgnoreCache(provider, false);

      return IdentityListModel.builder()
         .ids(emails)
         .type(Identity.USER)
         .build();
   }

   public List<IdentityID> getFilteredUsers(String providerName, Principal principal) {
      AuthenticationProvider provider = getProviderByName(providerName);

      return Arrays.stream(provider.getUsers())
         .filter(userName -> securityEngine.getSecurityProvider().checkPermission(
            principal, ResourceType.SECURITY_USER, userName.convertToKey(), ResourceAction.ADMIN))
         .sorted()
         .collect(Collectors.toList());
   }

   public IdentityListModel getGroups(AuthenticationProviderModel model)
      throws Exception
   {
      AuthenticationProvider provider = getProviderFromModel(model).orElse(null);
      IdentityID[] groups = new IdentityID[0];

      setIgnoreCache(provider, true);

      if(provider != null) {
         groups = provider.getGroups();
      }

      setIgnoreCache(provider, false);

      //return sorted by organization, then name
      Arrays.sort(groups, new Comparator<IdentityID>() {
         @Override
         public int compare(IdentityID o1, IdentityID o2) {
            if(Tool.equals(o1.orgID, o2.orgID)) {
               return Tool.compare(o1.name, o2.name);
            }

            return Tool.compare(o1.orgID, o2.orgID);
         }
      });

      return IdentityListModel.builder()
         .ids(groups)
         .type(Identity.GROUP)
         .build();
   }

   public IdentityListModel getOrganizations(AuthenticationProviderModel model)
      throws Exception
   {
      AuthenticationProvider provider = getProviderFromModel(model).orElse(null);
      IdentityID[] organizations = new IdentityID[0];

      setIgnoreCache(provider, true);

      if(provider != null) {
         organizations = Arrays.stream(provider.getOrganizationIDs()).map(o -> new IdentityID(o,o)).toArray(IdentityID[]::new);
      }

      setIgnoreCache(provider, false);

      return IdentityListModel.builder()
         .ids(organizations)
         .type(Identity.ORGANIZATION)
         .build();
   }

   public IdentityListModel getGroupUsers(AuthenticationProviderModel model,
                                          IdentityID group)
      throws Exception
   {
      AuthenticationProvider provider = getProviderFromModel(model).orElse(null);
      setIgnoreCache(provider, true);
      IdentityID[] users = provider == null ? new IdentityID[0] : provider.getUsers(group);
      setIgnoreCache(provider, false);

      return IdentityListModel.builder()
         .ids(users)
         .type(Identity.USER)
         .build();
   }

   public IdentityListModel getOrganizationMembers(AuthenticationProviderModel model,
                                                   String org)
      throws Exception
   {
      AuthenticationProvider provider = getProviderFromModel(model).orElse(null);
      setIgnoreCache(provider, true);
      IdentityID[] members = provider == null ? new IdentityID[0] :
         Arrays.stream(provider.getOrganizationMembers(org)).map(n -> new IdentityID(n,org)).toArray(IdentityID[]::new);
      setIgnoreCache(provider, false);

      return IdentityListModel.builder()
         .ids(members)
         .type(Identity.USER)
         .build();
   }

   public List<IdentityID> getFilteredGroups(String providerName, Principal principal) {
      AuthenticationProvider provider = getProviderByName(providerName);

      return Arrays.stream(provider.getGroups())
         .filter(groupName -> securityEngine.getSecurityProvider().checkPermission(
            principal, ResourceType.SECURITY_GROUP, groupName.convertToKey(), ResourceAction.ADMIN))
         .sorted()
         .collect(Collectors.toList());
   }

   public List<IdentityID> getFilteredOrganizations(String providerName, Principal principal) {
      AuthenticationProvider provider = getProviderByName(providerName);

      return Arrays.stream(provider.getOrganizationIDs())
         .filter(orgName -> securityEngine.getSecurityProvider().checkPermission(
            principal, ResourceType.SECURITY_ORGANIZATION, orgName, ResourceAction.ADMIN))
         .sorted()
         .map(name -> new IdentityID(name,name))
         .collect(Collectors.toList());
   }

   public IdentityListModel getRoles(AuthenticationProviderModel model)
      throws Exception
   {
      AuthenticationProvider provider = getProviderFromModel(model).orElse(null);
      IdentityID[] roles = new IdentityID[0];

      setIgnoreCache(provider, true);

      if(provider != null) {
         roles = provider.getRoles();
      }

      setIgnoreCache(provider, false);

      //return sorted by organization, then name
      Arrays.sort(roles, new Comparator<IdentityID>() {
         @Override
         public int compare(IdentityID o1, IdentityID o2) {
            if(Tool.equals(o1.orgID, o2.orgID)) {
               return Tool.compare(o1.name, o2.name);
            }

            return Tool.compare(o1.orgID, o2.orgID);
         }
      });

      return IdentityListModel.builder()
         .ids(roles)
         .type(Identity.ROLE)
         .build();
   }

   public IdentityListModel getUserRoles(AuthenticationProviderModel model, IdentityID userName)
      throws Exception
   {
      AuthenticationProvider provider = getProviderFromModel(model).orElse(null);
      setIgnoreCache(provider, true);
      IdentityID[] roles = provider == null ? new IdentityID[0] : provider.getRoles(userName);
      setIgnoreCache(provider, false);

      return IdentityListModel.builder()
         .ids(roles)
         .type(Identity.ROLE)
         .build();
   }

   public List<IdentityID> getFilteredRoles(String providerName, Principal principal) {
      AuthenticationProvider provider = getProviderByName(providerName);

      return Arrays.stream(provider.getRoles())
         .filter(roleName -> securityEngine.getSecurityProvider().checkPermission(
            principal, ResourceType.SECURITY_ROLE, roleName.convertToKey(), ResourceAction.ASSIGN))
         .sorted()
         .collect(Collectors.toList());
   }

   private Optional<AuthenticationProvider> getProviderFromModel(AuthenticationProviderModel model)
      throws Exception {
      AuthenticationProvider provider;

      switch(model.providerType()) {
      case FILE:
         provider = new FileAuthenticationProvider();
         break;
      case LDAP:
         provider = createLDAPProvider(model.ldapProviderModel());
         provider.checkParameters();
         break;
      case DATABASE:
         provider = createDatabaseProvider(Objects.requireNonNull(model.dbProviderModel()));
         ((DatabaseAuthenticationProvider) provider).testConnection();
         break;
      case CUSTOM:
         provider = createCustomProvider(Objects.requireNonNull(model.customProviderModel()));
         break;
      default:
         return Optional.empty();
      }

      provider.setProviderName(model.providerName());
      return Optional.of(provider);
   }

   private AuthenticationProvider createLDAPProvider(LdapAuthenticationProviderModel model) {
      if(model == null) {
         return null;
      }

      LdapAuthenticationProvider ldapProvider;

      switch(model.ldapServer()) {
      case ACTIVE_DIRECTORY:
         ldapProvider = new ADAuthenticationProvider();
         break;
      case GENERIC:
         ldapProvider = new GenericLdapAuthenticationProvider();
         GenericLdapAuthenticationProvider genericProvider =
            (GenericLdapAuthenticationProvider) ldapProvider;
         genericProvider.setUserAttribute(model.userAttr());
         genericProvider.setMailAttribute(model.mailAttr());
         genericProvider.setGroupSearch(model.groupFilter());
         genericProvider.setGroupAttribute(model.groupAttr());
         genericProvider.setRoleSearch(model.roleFilter());
         genericProvider.setRoleAttribute(model.roleAttr());
         genericProvider.setRoleRolesSearch(model.roleRoleFilter());
         genericProvider.setGroupRolesSearch(model.groupRoleFilter());
         genericProvider.setStartTls(Boolean.TRUE.equals(model.startTls()));
         break;
      default:
         return null;
      }

      ldapProvider.setProtocol(model.protocol());
      ldapProvider.setHost(model.hostName());
      ldapProvider.setPort(model.hostPort());
      ldapProvider.setRootDn(model.rootDN());
      ldapProvider.setUseCredential(model.useCredential());
      ldapProvider.setUserSearch(model.userFilter());
      ldapProvider.setUserRolesSearch(model.userRoleFilter());
      ldapProvider.setUserBase(model.userBase());
      ldapProvider.setGroupBase(model.groupBase());
      ldapProvider.setRoleBase(model.roleBase());
      ldapProvider.setSearchSubtree(model.searchTree());

      if(model.useCredential()) {
         ldapProvider.setSecretId(model.secretId());
      }
      else {
         ldapProvider.setLdapAdministrator(model.adminID());
         ldapProvider.setPassword(model.password());
      }

      if(model.sysAdminRoles() == null) {
         ldapProvider.setSystemAdministratorRoles(new String[0]);
      }
      else {
         ldapProvider.setSystemAdministratorRoles(model.sysAdminRoles());
      }

      return ldapProvider;
   }

   private AuthenticationProvider createDatabaseProvider(DatabaseAuthenticationProviderModel model) {
      // do not using cache when design, fix the query result is empty when the first query fails.
      DatabaseAuthenticationProvider dbProvider = new DatabaseAuthenticationProvider();
      dbProvider.setDriver(model.driver());
      dbProvider.setUrl(model.url());
      dbProvider.setRequiresLogin(model.requiresLogin());
      dbProvider.setUseCredential(model.useCredential());
      dbProvider.setHashAlgorithm(model.hashAlgorithm());
      dbProvider.setAppendSalt(model.appendSalt());
      dbProvider.setUserQuery(model.userQuery());
      dbProvider.setUserEmailsQuery(model.userEmailsQuery());
      dbProvider.setGroupListQuery(model.groupListQuery());
      dbProvider.setUserListQuery(model.userListQuery());
      dbProvider.setRoleListQuery(model.roleListQuery());
      dbProvider.setOrganizationListQuery(model.organizationListQuery());
      dbProvider.setGroupUsersQuery(model.groupUsersQuery());
      dbProvider.setOrganizationNameQuery(model.organizationNameQuery());
      dbProvider.setOrganizationMembersQuery(model.organizationMembersQuery());
      dbProvider.setOrganizationRolesQuery(model.organizationRolesQuery());
      dbProvider.setUserRolesQuery(model.userRolesQuery());

      if(model.useCredential()) {
         dbProvider.setSecretId(model.secretId());
      }
      else {
         dbProvider.setDbUser(model.user());
         dbProvider.setDbPassword(model.password());
      }

      if(model.sysAdminRoles() == null) {
         dbProvider.setSystemAdministratorRoles(new String[0]);
      }
      else {
         dbProvider.setSystemAdministratorRoles(model.sysAdminRoles().split(", "));
      }

      if(model.orgAdminRoles() == null) {
         dbProvider.setOrgAdministratorRoles(new String[0]);
      }
      else {
         dbProvider.setOrgAdministratorRoles(model.orgAdminRoles().split(", "));
      }

      return dbProvider;
   }

   private AuthenticationProvider createCustomProvider(CustomProviderModel model) throws Exception {
      Class<?> cls;

      try {
         cls = Class.forName(model.className());
      }
      catch(ClassNotFoundException exc) {
         String msg = Catalog.getCatalog().getString(
            "em.securityProvider.invalidClassName", exc.getMessage());
         throw new MessageException(msg);
      }

      if(FileAuthenticationProvider.class.isAssignableFrom(cls) ||
         LdapAuthenticationProvider.class.isAssignableFrom(cls) ||
         DatabaseAuthenticationProvider.class.isAssignableFrom(cls)) {
         String msg = Catalog.getCatalog().getString(
            "em.securityProvider.invalidClassName",
            "Built-in providers cannot be used as custom providers");
         throw new MessageException(msg);
      }

      AuthenticationProvider provider = (AuthenticationProvider) cls.newInstance();

      if(!model.jsonConfiguration().isEmpty()) {
         try {
            JsonNode config = objectMapper.readTree(model.jsonConfiguration());
            provider.readConfiguration(config);
         }
         catch(Exception e) {
            LOG.error("Invalid authentication provider configuration", e);
            throw new MessageException(Catalog.getCatalog().getString(
               "em.securityProvider.invalidJsonConfiguration"));
         }
      }

      return provider;
   }

   public Optional<AuthenticationChain> getAuthenticationChain() {
      return securityEngine.getAuthenticationChain();
   }

   private void setIgnoreCache(AuthenticationProvider provider, boolean ignoreCache) {
      if(provider instanceof LdapAuthenticationProvider) {
         ((LdapAuthenticationProvider) provider).setIgnoreCache(ignoreCache);
      }
      else if(provider instanceof DatabaseAuthenticationProvider) {
         ((DatabaseAuthenticationProvider) provider).setIgnoreCache(ignoreCache);
      }
   }

   @Override
   public void messageReceived(MessageEvent event) {
      if(event.getMessage() instanceof AuthenticationProvidersChanged) {
         getSubscribers().stream()
            .filter(sub -> sub.getUser() instanceof XPrincipal)
            .forEach(sub -> {
               this.debouncer.debounce(((XPrincipal) sub.getUser()).getCurrentOrgId(), 1L, TimeUnit.SECONDS,
                                       () -> sendToSubscriber(sub));
            });
      }
   }

   private final SecurityEngine securityEngine;
   private final ObjectMapper objectMapper;
   private final IdentityService identityService;
   private final IdentityThemeService themeService;
   private final DefaultDebouncer<String> debouncer = new DefaultDebouncer<>();
   private final Logger LOG = LoggerFactory.getLogger(AuthenticationProviderService.class);
}