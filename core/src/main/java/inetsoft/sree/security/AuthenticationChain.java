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
package inetsoft.sree.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.db.DatabaseAuthenticationProvider;
import inetsoft.uql.util.Identity;
import inetsoft.util.ShutdownException;
import inetsoft.web.admin.security.AuthenticationProvidersChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@code AuthenticationChain} wraps a list of authentication providers. When authorizing or getting
 * a user, group or role, it tries each provider in sequence, returning the first successful or
 * non-null result. When listing users, groups and roles, it returns the union of those returned by
 * the providers, in no particular order.
 */
public class AuthenticationChain
   extends SecurityChain<AuthenticationProvider> implements AuthenticationProvider
{
   public AuthenticationChain() {
      super();
      initialize();
   }

   AuthenticationChain(boolean readOnly) {
      super(readOnly);
      initialize();
   }

   @Override
   public User getUser(IdentityID userIdentity) {
      RuntimeException failure = null;

      for(AuthenticationProvider p : getProviders()) {
         try {
            User user = p.getUser(userIdentity);

            if(user != null) {
               return user;
            }
         }
         catch(RuntimeException e) {
            if(e instanceof ShutdownException) {
               throw e;
            }

            warnSkippedProvider(p, userIdentity, e);

            if(failure == null) {
               failure = e;
            }
            else if(e != failure) {
               failure.addSuppressed(e);
            }
         }
      }

      // A failure is never reported as "absent" (null): only a positive answer from a later
      // provider can supersede it (Bug #79268). If nothing answered, the failure surfaces as
      // it always did.
      if(failure != null) {
         throw failure;
      }

      return getExternalUser(userIdentity);
   }

   /**
    * Finds the provider that owns a user, together with the user. A provider whose lookup
    * fails is not the owner: it is skipped and the next provider is asked. Use this instead of
    * calling {@code getUser} on each provider when only the owner is wanted.
    * <p>
    * Caveat: if an earlier provider fails and a later provider owns a user with the same
    * identity, the later provider is returned. Authentication against it still needs that
    * account's own credential.
    *
    * @return the owner or <tt>null</tt> if no provider answered with a user.
    */
   public UserOwner findUserOwner(IdentityID userIdentity) {
      for(AuthenticationProvider p : getProviders()) {
         try {
            User user = p.getUser(userIdentity);

            if(user != null) {
               return new UserOwner(p, user);
            }
         }
         catch(RuntimeException e) {
            if(e instanceof ShutdownException) {
               throw e;
            }

            warnSkippedProvider(p, userIdentity, e);
         }
      }

      return null;
   }

   /**
    * Logs, at most once per provider per minute, that a provider failed a user lookup and was
    * skipped. The stack trace is logged at debug level only.
    */
   public static void warnSkippedProvider(AuthenticationProvider provider, IdentityID user,
                                          Exception e)
   {
      String name = String.valueOf(provider.getProviderName());
      long now = System.currentTimeMillis();
      Long last = SKIP_WARNINGS.get(name);

      if(last == null || now - last >= SKIP_WARNING_INTERVAL) {
         SKIP_WARNINGS.put(name, now);
         LOG.warn("Provider {} failed to look up user {}, skipping it (further failures " +
                  "are logged at debug level for {} s): {}", name, user,
                  SKIP_WARNING_INTERVAL / 1000, e.toString());
      }

      LOG.debug("Provider {} failed to look up user {}", name, user, e);
   }

   /**
    * The provider that owns a user and the user it returned.
    */
   public record UserOwner(AuthenticationProvider provider, User user) {
   }

   @Override
   public Organization getOrganization(String id) {
      return stream()
         .map(p -> p.getOrganization(id))
         .filter(Objects::nonNull)
         .findFirst()
         .orElse(null);
   }

   @Override
   public String getOrgIdFromName(String name) {
      for(String oid : getOrganizationIDs()) {
         if(getOrganization(oid).getName().equals(name)) {
            return oid;
         }
      }
      return null;
   }

   @Override
   public String getOrgNameFromID(String id) {
      return getOrganization(id) == null ? null : getOrganization(id).name;

   }

   private User getExternalUser(IdentityID name) {
      UserProvider provider = UserProvider.getInstance();

      if(provider != null) {
         return provider.getUser(name);
      }

      return null;
   }

   @Override
   public IdentityID[] getUsers() {
      return stream()
         .flatMap(p -> Arrays.stream(p.getUsers()))
         .distinct()
         .toArray(IdentityID[]::new);
   }

   @Override
   public String[] getOrganizationIDs() {
      List<AuthenticationProvider> providers = getProviders();

      if(providers == null || providers.isEmpty()) {
         return new String[0];
      }

      return stream()
         .filter(p -> p.getOrganizationIDs() != null)
         .flatMap(p -> Arrays.stream(p.getOrganizationIDs()))
         .distinct()
         .toArray(String[]::new);
   }

   @Override
   public String[] getOrganizationNames() {
      List<AuthenticationProvider> providers = getProviders();

      if(providers == null || providers.isEmpty()) {
         return new String[0];
      }

      return stream()
         .flatMap(p -> Arrays.stream(p.getOrganizationNames()))
         .distinct()
         .toArray(String[]::new);
   }

   @Override
   public IdentityID[] getUsers(IdentityID groupIdentity) {
      return stream()
         .flatMap(p -> Arrays.stream(p.getUsers(groupIdentity)))
         .distinct()
         .toArray(IdentityID[]::new);
   }

   @Override
   @Deprecated
   public String[] getEmails(IdentityID userIdentity) {
      List<AuthenticationProvider> providers = getProviders();

      if(providers.size() == 1) {
         // the only provider, asking it directly is cheaper than building the user (as before)
         String[] emails = providers.get(0).getEmails(userIdentity);
         return emails == null ?
            new String[0] : Arrays.stream(emails).distinct().toArray(String[]::new);
      }

      // Ask only the provider that owns the user (the first one that has it, as in getUser), like
      // SUtil.getEmails does, so the other providers aren't queried for a user they don't have.
      User user = getUser(userIdentity);
      String[] emails = user == null ? null : user.getEmails();
      return emails == null ?
         new String[0] : Arrays.stream(emails).distinct().toArray(String[]::new);
   }

   @Override
   public IdentityID[] getIndividualUsers() {
      return stream()
         .flatMap(p -> Arrays.stream(p.getIndividualUsers()))
         .distinct()
         .toArray(IdentityID[]::new);
   }

   @Override
   public IdentityID[] getRoles() {
      return stream()
         .flatMap(p -> Arrays.stream(p.getRoles()))
         .distinct()
         .toArray(IdentityID[]::new);
   }

   public IdentityID[] getProviderRoles(String providerName) {
      return stream()
         .filter(p -> p.getProviderName().equals(providerName))
         .flatMap(p -> Arrays.stream(p.getRoles()))
         .distinct()
         .toArray(IdentityID[]::new);
   }

   @Override
   public IdentityID[] getRoles(IdentityID roleIdentity) {
      Optional<IdentityID[]> roles = stream()
         .map(p -> p.getRoles(roleIdentity))
         .filter(proleList -> proleList.length > 0)
         .findFirst();

      if(roles.isPresent()) {
         return roles.stream().flatMap(Arrays::stream)
            .distinct()
            .filter(role -> isRoleVisible(role))
            .toArray(IdentityID[]::new);
      }
      else {
         User externalUser = getExternalUser(roleIdentity);
         return externalUser == null ? new IdentityID[0] : externalUser.getRoles();
      }
   }

   private boolean isRoleVisible(IdentityID role) {
      if(!SUtil.isMultiTenant() && "Organization Administrator".equals(role.name)) {
         return false;
      }

      return true;
   }

   @Override
   public Role getRole(IdentityID roleIdentity) {
      return stream()
         .map(p -> p.getRole(roleIdentity))
         .filter(Objects::nonNull)
         .findFirst()
         .orElse(null);
   }

   public Role getProviderRole(IdentityID roleid, String providerName) {
      return stream()
         .filter(p -> p.getProviderName().equals(providerName))
         .map(p -> p.getRole(roleid))
         .filter(Objects::nonNull)
         .findFirst()
         .orElse(null);
   }

   @Override
   public Group getGroup(IdentityID groupIdentity) {
      return stream()
         .map(p -> p.getGroup(groupIdentity))
         .filter(Objects::nonNull)
         .findFirst()
         .orElse(null);
   }

   @Override
   public IdentityID[] getGroups() {
      return stream()
         .flatMap(p -> Arrays.stream(p.getGroups()))
         .distinct()
         .toArray(IdentityID[]::new);
   }

   @Override
   public String[] getUserGroups(IdentityID userId) {
      String[] groups = stream()
         .flatMap(p -> Arrays.stream(p.getUserGroups(userId)))
         .distinct()
         .toArray(String[]::new);

      if(groups.length > 0) {
         return groups;
      }
      else {
         User externalUser = getExternalUser(userId);
         return externalUser == null ? new String[0] : externalUser.getGroups();
      }
   }

   @Override
   public String[] getGroupParentGroups(IdentityID group) {
      return stream()
         .flatMap(p -> Arrays.stream(p.getGroupParentGroups(group)))
         .distinct()
         .toArray(String[]::new);
   }

   @Override
   public boolean authenticate(IdentityID userIdentity, Object credential) {
      // Use the first provider that contains the user to authenticate. This is intentionally
      // different from checking if the authentication is successful with any provider. A
      // provider that fails the lookup is skipped (Bug #79268); see findUserOwner for the
      // same-name caveat. An exception from authenticate() itself still propagates.
      UserOwner owner = findUserOwner(userIdentity);
      return owner != null && owner.provider().authenticate(userIdentity, credential);
   }

   @Override
   public Identity findIdentity(Identity identity) {
      return stream()
         .map(p -> p.findIdentity(identity))
         .filter(Objects::nonNull)
         .findFirst()
         .orElse(null);
   }

   @Override
   public String[] getIndividualEmailAddresses() {
      return stream()
         .flatMap(p -> Arrays.stream(p.getIndividualEmailAddresses()))
         .distinct()
         .toArray(String[]::new);
   }

   @Override
   public boolean isAuthenticationCaseSensitive() {
      // return false if any is not case sensitive.
      for(AuthenticationProvider provider : getProviderList()) {
         if(!provider.isAuthenticationCaseSensitive()) {
            return false;
         }
      }

      return true;
   }

   @Override
   public boolean isVirtual() {
      // return false if any is not virtual
      for(AuthenticationProvider provider : getProviderList()) {
         if(!provider.isVirtual()) {
            return false;
         }
      }

      return true;
   }

   @Override
   public void checkParameters() throws SRSecurityException {
      for(AuthenticationProvider provider : getProviderList()) {
         provider.checkParameters();
      }
   }

   @Override
   public boolean isCacheEnabled() {
      return stream().anyMatch(AuthenticationProvider::isCacheEnabled);
   }

   @Override
   public boolean isLoading() {
      return stream().anyMatch(AuthenticationProvider::isLoading);
   }

   @Override
   public void clearCache() {
      stream().forEach(AuthenticationProvider::clearCache);
   }

   @Override
   public long getCacheAge() {
      return stream().mapToLong(AuthenticationProvider::getCacheAge).max().orElse(0L);
   }

   @Override
   public void tearDown() {
      synchronized(listeners) {
         listeners.clear();
      }

      dispose();
   }

   @Override
   String getConfigFile() {
      return "authc-chain.json";
   }

   @Override
   void dispose(AuthenticationProvider provider) {
      provider.tearDown();
   }

   @Override
   Map<String, String> describeProviders(List<AuthenticationProvider> list) {
      Map<String, String> result = new HashMap<>();

      for(AuthenticationProvider provider : list) {
         if(provider instanceof DatabaseAuthenticationProvider db) {
            result.put(db.getProviderName(), describeCacheConfiguration(db));
         }
      }

      return result;
   }

   /**
    * Refreshes the cluster cache of each Database provider whose configuration the reload
    * added or changed. The cache state of a provider name outlives a removed provider, and on
    * the singleton host this refresh is the first one that runs after the host's chain has the
    * new configuration, so it loads that configuration (Bug #78793).
    * <p>
    * The refreshes connect to the cache service on the singleton host, so they run on their own
    * thread rather than on the storage event thread that calls this. They are submitted after
    * the new providers are in the chain and run in order.
    */
   @Override
   void providersReloaded(Map<String, String> before, List<AuthenticationProvider> list) {
      List<DatabaseAuthenticationProvider> changed = new ArrayList<>();

      for(AuthenticationProvider provider : list) {
         if(provider instanceof DatabaseAuthenticationProvider db && db.isCacheEnabled()) {
            String old = before.get(db.getProviderName());

            if(old == null || !old.equals(describeCacheConfiguration(db))) {
               changed.add(db);
            }
         }
      }

      if(!changed.isEmpty()) {
         RELOAD_EXECUTOR.execute(() -> refreshReloaded(changed));
      }
   }

   private void refreshReloaded(List<DatabaseAuthenticationProvider> providers) {
      for(DatabaseAuthenticationProvider changed : providers) {
         // A later reload may have replaced it: refresh the current provider of that name. A
         // reload in progress has not set the new list yet, so wait for it.
         String waiter = "the cache refresh of security provider " + changed.getProviderName();
         DatabaseAuthenticationProvider db = getProvidersAfterReload(waiter).stream()
            .filter(p -> p instanceof DatabaseAuthenticationProvider &&
                         Objects.equals(p.getProviderName(), changed.getProviderName()))
            .map(DatabaseAuthenticationProvider.class::cast)
            .filter(DatabaseAuthenticationProvider::isCacheEnabled)
            .findFirst().orElse(null);

         if(db == null) {
            continue;
         }

         try {
            db.clearCache();
         }
         catch(Exception e) {
            LOG.warn("Failed to refresh the cache of security provider {}",
                     db.getProviderName(), e);
         }
      }
   }

   /**
    * Describes the configuration that a Database provider loads its cache with, for comparing
    * the same provider name before and after a reload on this node. The encrypted credential is
    * replaced by its non-secret part: it is encrypted with a random IV, so it differs on every
    * write, and a password change doesn't change the data the cache loads. Null values are
    * written as empty strings, as a provider read from the file has them.
    *
    * @return the description, or {@code null} if the configuration could not be written.
    */
   private static String describeCacheConfiguration(DatabaseAuthenticationProvider provider) {
      try {
         ObjectNode config = (ObjectNode) provider.writeConfiguration(new ObjectMapper());
         String login = "";

         if(provider.isRequiresLogin()) {
            login = provider.isUseCredential() ?
               "secret:" + provider.getSecretId() :
               "user:" + Objects.toString(provider.getDbUser(), "");
         }

         config.put("credential", login);
         List<String> nulls = new ArrayList<>();
         config.fields().forEachRemaining(e -> {
            if(e.getValue().isNull()) {
               nulls.add(e.getKey());
            }
         });

         for(String field : nulls) {
            config.put(field, "");
         }

         return provider.getClass().getName() + ":" + config;
      }
      catch(Exception e) {
         LOG.debug("Failed to describe security provider {}", provider.getProviderName(), e);
         return null;
      }
   }

   @Override
   public String getProviderName() {
      return chainName;
   }

   @Override
   public void setProviderName(String providerName) {
      this.chainName = providerName;
   }

   @Override
   void setProviderList(List<AuthenticationProvider> list) {
      // Without the chain's read lock: a reload calls this while it holds the reload lock, and an
      // edit holding the write lock waits for the reload lock when it saves the chain.
      getProviderList().stream()
         .filter(EditableAuthenticationProvider.class::isInstance)
         .map(EditableAuthenticationProvider.class::cast)
         .forEach(l -> l.removeAuthenticationChangeListener(changeDelegate));

      super.setProviderList(list);

      try {
         Cluster.getInstance().sendMessage(new AuthenticationProvidersChanged());
      }
      catch(Exception ex) {
         LOG.debug("Failed to send authentication providers changed message", ex);
      }

      list.stream()
         .filter(EditableAuthenticationProvider.class::isInstance)
         .map(EditableAuthenticationProvider.class::cast)
         .forEach(l -> l.addAuthenticationChangeListener(changeDelegate));
   }

   @Override
   public boolean isSystemAdministratorRole(IdentityID roleId) {
      return stream().filter(p -> p.getRole(roleId) != null).findFirst().stream().anyMatch(p -> p.isSystemAdministratorRole(roleId));
   }

   @Override
   public boolean isOrgAdministratorRole(IdentityID roleId) {
      return stream().filter(p -> p.getRole(roleId) != null).findFirst().stream().anyMatch(p -> p.isOrgAdministratorRole(roleId));
   }

   @Override
   public boolean containsAnonymousUser(String orgId) {
      return stream().anyMatch(p -> p.containsAnonymousUser(orgId));
   }

   /**
    * Adds a listener that is notified whenever an {@link EditableAuthenticationProvider} in this
    * chain has been modified.
    *
    * @param l the listener to add.
    */
   public void addAuthenticationChangeListener(AuthenticationChangeListener l) {
      synchronized(listeners) {
         listeners.add(l);
      }
   }

   /**
    * Removes a listener from the notification list.
    *
    * @param l the listener to remove.
    *
    * @see #addAuthenticationChangeListener(AuthenticationChangeListener)
    */
   public void removeAuthenticationChangeListener(AuthenticationChangeListener l) {
      synchronized(listeners) {
         listeners.remove(l);
      }
   }

   private String chainName = "Authentication Chain";
   private final Set<AuthenticationChangeListener> listeners = new LinkedHashSet<>();

   private final AuthenticationChangeListener changeDelegate = event -> {
      synchronized(listeners) {
         listeners.forEach(l -> l.authenticationChanged(event));
      }
   };

   private static final Logger LOG = LoggerFactory.getLogger(AuthenticationChain.class);
   private static final long SKIP_WARNING_INTERVAL = 60000L;
   private static final Map<String, Long> SKIP_WARNINGS = new java.util.concurrent.ConcurrentHashMap<>();
   // runs the cache refreshes after a chain reload, in order, off the storage event thread
   private static final ExecutorService RELOAD_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
      Thread thread = new Thread(r, "AuthenticationChainReload");
      thread.setDaemon(true);
      return thread;
   });
}