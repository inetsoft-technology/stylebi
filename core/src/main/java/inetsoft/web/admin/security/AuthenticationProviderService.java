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
import inetsoft.report.internal.Util;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import inetsoft.sree.security.db.DatabaseAuthenticationProvider;
import inetsoft.sree.security.ldap.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.data.MapModel;
import inetsoft.web.service.BaseSubscribeChangeHandler;
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

import java.security.Principal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class AuthenticationProviderService extends BaseSubscribeChangeHandler {
   @Autowired
   public AuthenticationProviderService(SecurityEngine securityEngine, ObjectMapper objectMapper,
                                        SimpMessagingTemplate messageTemplate,
                                        Cluster cluster)
   {
      super(messageTemplate);
      this.securityEngine = securityEngine;
      this.objectMapper = objectMapper;
      this.cluster = cluster;
   }

   @PostConstruct
   public void init() {
      messageListener = this::messageReceived;
      cluster.addMessageListener(messageListener);
   }

   @PreDestroy
   public void destroy() {
      if(messageListener != null) {
         cluster.removeMessageListener(messageListener);
      }
   }

   @EventListener(SessionDisconnectEvent.class)
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
      final BaseSubscribeChangeHandler.BaseSubscriber subscriber =
         new BaseSubscribeChangeHandler.BaseSubscriber(sessionId, subscriptionId,
                                                       lookupDestination, destination, headerAccessor.getUser());

      return addSubscriber(subscriber);
   }

   public AuthenticationProviderModel getAuthenticationProvider(String name) {
      boolean enterprise = LicenseManager.isEnterprise();
      AuthenticationProvider selectedProvider = getProviderByName(name);
      AuthenticationProviderModel.Builder builder = AuthenticationProviderModel.builder()
         .providerName(name)
         .oldName(name)
         .dbProviderEnabled(enterprise)
         .customProviderEnabled(enterprise)
         .ldapProviderEnabled(!enterprise || !SUtil.isMultiTenant());

      switch(selectedProvider) {
         case FileAuthenticationProvider ignored ->
            builder.providerType(SecurityProviderType.FILE);
         case LdapAuthenticationProvider ldapAuthenticationProvider -> {
            builder.providerType(SecurityProviderType.LDAP);
            builder.ldapProviderModel(ldapAuthenticationProvider);
         }
         case DatabaseAuthenticationProvider databaseAuthenticationProvider -> {
            builder.providerType(SecurityProviderType.DATABASE);
            builder.dbProviderModel(databaseAuthenticationProvider);
         }
         case null, default -> {
            builder.providerType(SecurityProviderType.CUSTOM);
            builder.customProviderModel(selectedProvider, objectMapper);
         }
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

      // checked before getProviderFromModel, which validates the parameters and may connect
      if(model.providerType() == SecurityProviderType.LDAP && multiTenantSupplier.getAsBoolean()) {
         throw new MessageException(
            Catalog.getCatalog().getString("em.security.multitenant.ldapProviderNotAllowed"));
      }

      AuthenticationProvider newProvider = getProviderFromModel(model)
         .orElseThrow(() -> new MessageException("Failed to create new authentication provider"));

      // Read, add and save under the cluster lock, after reloading the file: another node's
      // concurrent edit would otherwise be overwritten by this node's stale list (Bug #79127)
      chain.editProviders(() -> {
         // checked again, another node may have added the name since the check above
         if(getProviderByName(model.providerName()) != null) {
            throw new MessageException(
               Catalog.getCatalog().getString("security.authentication.exists"));
         }

         List<AuthenticationProvider> providerList = chain.getProviders();
         providerList.add(newProvider);
         chain.setProviders(providerList);
      });

      // The cluster cache of a removed Database provider outlives it for a grace period, and a
      // provider added under the same name would serve the removed configuration's data, so
      // load it now (Bug #78793). The other nodes refresh it when they reload the chain.
      if(newProvider instanceof DatabaseAuthenticationProvider && newProvider.isCacheEnabled()) {
         newProvider.clearCache();
      }
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

      // Turning a provider of another type into an LDAP one is refused under multi-tenancy.
      // An existing LDAP provider (legacy state) stays editable, so it can be fixed or replaced.
      if(model.providerType() == SecurityProviderType.LDAP && multiTenantSupplier.getAsBoolean()) {
         AuthenticationProvider existing = getProviderByName(name);

         if(existing != null && !(existing instanceof LdapAuthenticationProvider)) {
            throw new MessageException(
               Catalog.getCatalog().getString("em.security.multitenant.ldapProviderNotAllowed"));
         }
      }

      // The cluster lock and the reload of the file come first, before the list is read and before
      // the replaced provider is torn down (Bug #79127). editProviders() takes the write lock.
      AuthenticationProvider[] refresh = new AuthenticationProvider[1];

      chain.editProviders(() -> {
         // checked again, another node may have added the name since the check above
         if(!model.providerName().equals(name) &&
            getProviderByName(model.providerName()) != null)
         {
            throw new MessageException(
               Catalog.getCatalog().getString("security.authentication.exists"));
         }

         List<AuthenticationProvider> providerList = chain.getProviders();
         boolean found = false;

         for(int i = 0; i < providerList.size(); i++) {
            AuthenticationProvider provider = providerList.get(i);

            if(Objects.equals(name, provider.getProviderName())) {
               found = true;
               AuthenticationProvider newProvider = getProviderFromModel(model).orElseThrow(
                  () -> new MessageException("Failed to edit authentication provider"));
               providerList.set(i, newProvider);

               if(Objects.equals(name, newProvider.getProviderName())) {
                  refresh[0] = newProvider;
               }
               // Note: If provider was renamed, the old service will be cleaned up by lazy cleanup
               // after no clients have connected for the grace period.

               provider.tearDown();
               break;
            }
         }

         if(!found) {
            throw new MessageException(
               "Authentication provider named \"" + name + "\" does not exist");
         }

         chain.setProviders(providerList);
      });

      // outside the locks: loading the cache can be slow
      if(refresh[0] != null) {
         refresh[0].clearCache();
      }
   }

   public SecurityProviderStatusList getProviderListModel() {
      Optional<AuthenticationChain> chain = getAuthenticationChain();
      SecurityProviderStatusList.Builder builder = SecurityProviderStatusList.builder();

      if(chain.isEmpty()) {
         LOG.debug("The authentication chain has not been initialized.");
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

      chain.editProviders(() -> {
         List<AuthenticationProvider> providerList = chain.getProviders();

         if(index >= 0 && index < providerList.size()) {
            AuthenticationProvider removedProvider = providerList.remove(index);
            providerList.stream()
               .filter(this::providerHasSysAdmins)
               .findFirst()
               .orElseThrow(() -> new MessageException(
                  Catalog.getCatalog(principal).getString("em.security.noSystemAdminProvider")));

            // Note: If this is a database provider, its service will be cleaned up by lazy cleanup
            // after no clients have connected for the grace period. Eager teardown is intentionally
            // avoided here: the removal needs time to propagate across cluster nodes, and destroying
            // the service immediately would cause errors on nodes that haven't yet received the update.
            removedProvider.tearDown();
            chain.setProviders(providerList);
         }
         else {
            throw new IndexOutOfBoundsException("Provider index out of bounds");
         }
      });
   }

   private boolean providerHasSysAdmins(AuthenticationProvider provider) {
      return Arrays.stream(provider.getRoles())
         .anyMatch(role -> provider.isSystemAdministratorRole(role) && provider.getRoleMembers(role).length > 0);
   }

   public void reorderAuthenticationProviders(ProviderListReorderModel reorderModel) throws Exception {
      AuthenticationChain providerChain = getAuthenticationChain()
         .orElseThrow(() -> new Exception("The authentication chain has not been initialized."));

      int source = reorderModel.source();
      int destination = reorderModel.destination();

      providerChain.editProviders(() -> {
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
      });
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

   /**
    * Replaces the multi-tenancy check, for tests.
    */
   void setMultiTenantSupplier(BooleanSupplier multiTenantSupplier) {
      this.multiTenantSupplier = multiTenantSupplier;
   }

   public AuthenticationProvider getProviderByName(String name) {
      if(getAuthenticationChain().isEmpty()) {
         LOG.error("em.common.security.noProvider");
         return null;
      }

      return getAuthenticationChain().get().stream()
         .filter((p) -> Catalog.getCatalog().getString(p.getProviderName()).equals(name) || p.getProviderName().equals(name))
         .findAny()
         .orElse(null);
   }

   public String testConnection(AuthenticationProviderModel model) throws Exception {
      try {
         String msg = withProviderFromModel(model, provider ->
            provider.isPresent() ? null :
            Catalog.getCatalog().getString("em.security.testlogin.note4"), true);

         if(msg != null) {
            return msg;
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
      return withProviderFromModel(model, this::getUsers);
   }

   private IdentityListModel getUsers(Optional<AuthenticationProvider> optionProvider) {
      AuthenticationProvider provider = optionProvider.orElse(null);
      IdentityID[] users = previewCall(
         provider, () -> provider == null ? new IdentityID[0] : provider.getUsers());

      //return sorted by organization, then name
      Arrays.sort(users, (o1, o2) -> {
         if(Tool.equals(o1.orgID, o2.orgID)) {
            return Tool.compare(o1.name, o2.name);
         }

         return Tool.compare(o1.orgID, o2.orgID);
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
      return withProviderFromModel(model, (provider) ->
         getUser(provider.orElse(null), userid));
   }

   private MapModel<String, Object> getUser(AuthenticationProvider provider, IdentityID userid) {
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

      Set<String> distinctOrgs = new HashSet<>(Arrays.asList(names));
      return distinctOrgs.toArray(new String[0]);
   }

   public String getOrganizationName(AuthenticationProviderModel model,
                                     String id)
      throws Exception
   {
      return withProviderFromModel(model, provider ->
         getOrganizationName(provider.orElse(null), id));
   }

   private String getOrganizationName(AuthenticationProvider provider, String id) {
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
      return withProviderFromModel(model, provider ->
         getUserEmails(provider.orElse(null), userID));
   }

   private IdentityListModel getUserEmails(AuthenticationProvider provider, IdentityID userID) {
      IdentityID[] emails = previewCall(provider, () -> provider == null ?
         new IdentityID[0] :
         Arrays.stream(provider.getEmails(userID))
            .map(e -> new IdentityID(e,userID.orgID))
            .toArray(IdentityID[]::new));

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
      return withProviderFromModel(model, this::getGroups);
   }

   private IdentityListModel getGroups(Optional<AuthenticationProvider> optionalProvider) {
      AuthenticationProvider provider = optionalProvider.orElse(null);
      IdentityID[] groups = previewCall(
         provider, () -> provider == null ? new IdentityID[0] : provider.getGroups());

      //return sorted by organization, then name
      Arrays.sort(groups, (o1, o2) -> {
         if(Tool.equals(o1.orgID, o2.orgID)) {
            return Tool.compare(o1.name, o2.name);
         }

         return Tool.compare(o1.orgID, o2.orgID);
      });

      return IdentityListModel.builder()
         .ids(groups)
         .type(Identity.GROUP)
         .build();
   }

   public IdentityListModel getOrganizations(AuthenticationProviderModel model)
      throws Exception
   {
      return withProviderFromModel(model, this::getOrganizations);
   }

   private IdentityListModel getOrganizations(Optional<AuthenticationProvider> optionalProvider) {
      AuthenticationProvider provider = optionalProvider.orElse(null);
      IdentityID[] organizations = previewCall(provider, () -> provider == null ?
         new IdentityID[0] :
         Arrays.stream(provider.getOrganizationIDs()).map(o -> new IdentityID(o,o))
            .toArray(IdentityID[]::new));

      return IdentityListModel.builder()
         .ids(organizations)
         .type(Identity.ORGANIZATION)
         .build();
   }

   public IdentityListModel getGroupUsers(AuthenticationProviderModel model,
                                          IdentityID group)
      throws Exception
   {
      return withProviderFromModel(model, provider ->
         getGroupUsers(provider.orElse(null), group));
   }

   private IdentityListModel getGroupUsers(AuthenticationProvider provider, IdentityID group) {
      IdentityID[] users = previewCall(
         provider, () -> provider == null ? new IdentityID[0] : provider.getUsers(group));

      return IdentityListModel.builder()
         .ids(users)
         .type(Identity.USER)
         .build();
   }

   public IdentityListModel getOrganizationMembers(AuthenticationProviderModel model,
                                                   String org)
      throws Exception
   {
      return withProviderFromModel(model, provider ->
         getOrganizationUsers(provider.orElse(null), org));
   }

   private IdentityListModel getOrganizationUsers(AuthenticationProvider provider, String org) {
      IdentityID[] members = previewCall(provider, () -> provider == null ?
         new IdentityID[0] :
         Arrays.stream(provider.getOrganizationMembers(org)).map(n -> new IdentityID(n,org))
            .toArray(IdentityID[]::new));

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
         .map(orgID -> new IdentityID(getStoredOrganizationName(provider, orgID), orgID))
         .collect(Collectors.toList());
   }

   // the org pane looks an organization key up by id and requires its name to match the stored name
   private static String getStoredOrganizationName(AuthenticationProvider provider, String orgID) {
      Organization organization = provider.getOrganization(orgID);
      String name = organization == null ? null : organization.getName();
      return name == null ? orgID : name;
   }

   public IdentityListModel getRoles(AuthenticationProviderModel model)
      throws Exception
   {
      return withProviderFromModel(model, this::getRoles);
   }

   private IdentityListModel getRoles(Optional<AuthenticationProvider> optionalProvider) {
      AuthenticationProvider provider = optionalProvider.orElse(null);
      IdentityID[] roles = previewCall(
         provider, () -> provider == null ? new IdentityID[0] : provider.getRoles());

      //return sorted by organization, then name
      Arrays.sort(roles, (o1, o2) -> {
         if(Tool.equals(o1.orgID, o2.orgID)) {
            return Tool.compare(o1.name, o2.name);
         }

         return Tool.compare(o1.orgID, o2.orgID);
      });

      return IdentityListModel.builder()
         .ids(roles)
         .type(Identity.ROLE)
         .build();
   }

   public IdentityListModel getUserRoles(AuthenticationProviderModel model, IdentityID userName)
      throws Exception
   {
      return withProviderFromModel(model, provider ->
         getUserRoles(provider.orElse(null), userName));
   }

   private IdentityListModel getUserRoles(AuthenticationProvider provider, IdentityID userName) {
      IdentityID[] roles = previewCall(
         provider, () -> provider == null ? new IdentityID[0] : provider.getRoles(userName));

      return IdentityListModel.builder()
         .ids(roles)
         .type(Identity.ROLE)
         .build();
   }

   public UserRoleListModel getAllUserRoles(AuthenticationProviderModel model)
      throws Exception
   {
      UserRoleListModel.Builder builder = UserRoleListModel.builder();

      if(model.providerType() == SecurityProviderType.DATABASE) {
         return withProviderFromModel(model, optProvider -> {
            if(optProvider.orElse(null) instanceof DatabaseAuthenticationProvider dbProvider) {
               Map<IdentityID, IdentityID[]> userRoles =
                  previewCall(dbProvider, dbProvider::getAllUserRoles);

               for(Map.Entry<IdentityID, IdentityID[]> e : userRoles.entrySet()) {
                  builder.addList(UserRolesModel.builder()
                                     .user(e.getKey())
                                     .addRoles(e.getValue())
                                     .build());
               }
            }

            return builder.build();
         });
      }

      return builder.build();
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
         replacePlaceholderWithPassword(provider, model);
         provider.checkParameters();
         break;
      case DATABASE:
         provider = createDatabaseProvider(Objects.requireNonNull(model.dbProviderModel()));
         replacePlaceholderWithPassword(provider, model);

         try {
            ((DatabaseAuthenticationProvider) provider).testConnection();
         }
         catch(Exception e) {
            provider.tearDown();
            throw e;
         }

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

   private <T> T withProviderFromModel(AuthenticationProviderModel model, Function<Optional<AuthenticationProvider>, T> fn) throws Exception {
      return withProviderFromModel(model, fn, false);
   }

   private <T> T withProviderFromModel(AuthenticationProviderModel model, Function<Optional<AuthenticationProvider>, T> fn, boolean rethrowCreationException) throws Exception {
      Optional<AuthenticationProvider> provider;

      try {
         provider = getProviderFromModel(model);
      }
      catch(Exception e) {
         if(rethrowCreationException) {
            throw e;
         }

         // a database provider that can not connect must not look like an empty directory
         if(model.providerType() == SecurityProviderType.DATABASE) {
            LOG.warn("Could not create authentication provider from model", e);
            throw new MessageException(Catalog.getCatalog().getString(
               "em.security.provider.connectionFailed", e.getMessage()), e);
         }

         LOG.warn("Could not create authentication provider from model", e);
         return fn.apply(Optional.empty());
      }

      try {
         return fn.apply(provider);
      }
      finally {
         provider.ifPresent(AuthenticationProvider::tearDown);
      }
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
      dbProvider.setUserRoleListQuery(model.userRoleListQuery());

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

      AuthenticationProvider provider = (AuthenticationProvider) cls.getConstructor().newInstance();

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

   /**
    * Runs a preview getter with the provider cache ignored. A database provider records a
    * failed query instead of throwing (login and the provider chain rely on that), so the
    * failure is turned into an error here; a valid query that returns no rows is not one.
    */
   private <T> T previewCall(AuthenticationProvider provider, Supplier<T> fn) {
      DatabaseAuthenticationProvider dbProvider =
         provider instanceof DatabaseAuthenticationProvider db ? db : null;
      setIgnoreCache(provider, true);

      try {
         if(dbProvider != null) {
            dbProvider.clearQueryFailed();
         }

         T result = fn.get();

         if(dbProvider != null && dbProvider.isQueryFailed()) {
            throw new MessageException(Catalog.getCatalog().getString(
               "em.security.provider.queryFailed"));
         }

         return result;
      }
      finally {
         if(dbProvider != null) {
            dbProvider.clearQueryFailed();
         }

         setIgnoreCache(provider, false);
      }
   }

   private void setIgnoreCache(AuthenticationProvider provider, boolean ignoreCache) {
      if(provider instanceof LdapAuthenticationProvider) {
         ((LdapAuthenticationProvider) provider).setIgnoreCache(ignoreCache);
      }
      else if(provider instanceof DatabaseAuthenticationProvider) {
         ((DatabaseAuthenticationProvider) provider).setIgnoreCache(ignoreCache);
      }
   }

   public void messageReceived(MessageEvent event) {
      if(event.getMessage() instanceof AuthenticationProvidersChanged) {
         getSubscribers().stream()
            .filter(sub -> sub.getUser() instanceof XPrincipal)
            .forEach(sub -> {
               // key on the subscriber, not just the user session: a user with more than one
               // live subscription (two EM tabs, or a reconnected socket whose old subscriber
               // has not been removed yet) would otherwise collapse into a single debounce
               // entry and only one arbitrarily-chosen subscriber would be notified
               String key = ((XPrincipal) sub.getUser()).getSessionID() + ":" +
                  sub.getSessionId() + ":" + sub.getSubscriptionId();
               this.debouncer.debounce(key, 1L, TimeUnit.SECONDS, () -> sendToSubscriber(sub));
            });
      }
   }

   private void replacePlaceholderWithPassword(AuthenticationProvider provider,
                                               AuthenticationProviderModel model)
   {
      String password = model.getPassword();

      // Replace placeholder password with real password
      if(Util.PLACEHOLDER_PASSWORD.equals(password) &&
         model.oldName() != null)
      {
         AuthenticationProvider oldProvider = getProviderByName(model.oldName());

         if(oldProvider instanceof DatabaseAuthenticationProvider dbProvider) {
            String dbPassword = !dbProvider.isUseCredential() ? dbProvider.getDbPassword() : null;
            ((DatabaseAuthenticationProvider) provider).setDbPassword(dbPassword);
         }
         else if(oldProvider instanceof LdapAuthenticationProvider) {
            ((LdapAuthenticationProvider) provider)
               .setPassword(((LdapAuthenticationProvider) oldProvider).getPassword());
         }
      }
   }

   private final SecurityEngine securityEngine;
   private BooleanSupplier multiTenantSupplier = SUtil::isMultiTenant;
   private final ObjectMapper objectMapper;
   private final Cluster cluster;
   private final DefaultDebouncer<String> debouncer = new DefaultDebouncer<>();
   private MessageListener messageListener;
   private static final Logger LOG = LoggerFactory.getLogger(AuthenticationProviderService.class);
}
