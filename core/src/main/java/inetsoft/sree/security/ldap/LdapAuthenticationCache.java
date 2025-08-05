/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree.security.ldap;

import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.*;
import inetsoft.util.Debouncer;
import inetsoft.util.DefaultDebouncer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.event.*;
import javax.naming.ldap.LdapContext;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class LdapAuthenticationCache implements AutoCloseable, NamespaceChangeListener, ObjectChangeListener {
   public LdapAuthenticationCache(LdapAuthenticationProvider provider) {
      this.provider = provider;
      this.cluster = Cluster.getInstance();
      this.debouncer = new DefaultDebouncer<>();
      prefix = "LdapSecurity:" + provider.getProviderName();
      this.loadCount = cluster.getLong(prefix + ".loadCount");
      this.lastLoad = cluster.getLong(prefix + ".lastLoad");
      this.initialized = cluster.getLong(prefix + ".initialized");
      this.individualUsers = cluster.getReplicatedSet(prefix + ".individualUsers", true);
      this.individualEmails = cluster.getReplicatedSet(prefix + ".individualEmails", true);
      this.userDns = cluster.getReplicatedMap(prefix + ".userDns");
      this.groupDns = cluster.getReplicatedMap(prefix + ".groupDns");
      this.roleDns = cluster.getReplicatedMap(prefix + ".roleDns");
      this.emailsMap = cluster.getReplicatedMap(prefix + ".emailsMap");
      this.usersMap = cluster.getReplicatedMap(prefix + ".usersMap");
      this.groupsMap = cluster.getReplicatedMap(prefix + ".groupsMap");
      this.rolesMap = cluster.getReplicatedMap(prefix + ".rolesMap");
      this.identityMap = cluster.getReplicatedMap(prefix + ".identityMap");
      this.identityRoleMap = cluster.getReplicatedMap(prefix + ".identityRoleMap");
   }

   @Override
   public void close() throws Exception {
      removeListeners();
      executor.shutdown();
      debouncer.close();
   }

   public void reset() {
      removeListeners();
      initialized.compareAndSet(1L, 0L);
      load();
   }

   public void load() {
      refresh(false, true);
   }

   public void refresh() {
      refresh(true, false);
   }

   private void refresh(boolean force, boolean reschedule) {
      long next = -1;

      try {
         next =
            cluster.submit(prefix, new LoadCacheTask(provider.getProviderName(), force)).get();
      }
      catch(Exception e) {
         LOG.warn("Failed to refresh LDAP authentication cache", e);
      }

      if(reschedule && next > 0L) {
         executor.schedule(() -> refresh(false, true), next, TimeUnit.MILLISECONDS);
      }

      listenerLock.lock();

      try {
         if(!listenersAdded) {
            addListeners();
            listenersAdded = true;
         }
      }
      finally {
         listenerLock.unlock();
      }
   }

   Future<Long> load(boolean force) {
      return debouncer.debounce("load", 1L, TimeUnit.SECONDS, new LoadTask(force), this::reduce);
   }

   private long loadInternal(boolean force) {
      if(!force && !isReloadRequired()) {
         return getNextReloadDelay();
      }

      loadCount.incrementAndGet();

      try {
         String[] newIndividualUsers = provider.getClient().getIndividualUsers();
         String[] newIndividualEmails = provider.getClient().getIndividualEmailAddresses();
         Map<String, String> newUserDns = provider.getClient().getUsers();
         Map<String, String> newGroupDns = provider.getClient().getGroups();
         Map<String, String> newRoleDns = provider.getClient().getRoles();

         try(DistributedTransaction tx = cluster.startTx()) {
            individualUsers.clear();
            individualUsers.addAll(Arrays.asList(newIndividualUsers));

            individualEmails.clear();
            individualEmails.addAll(Arrays.asList(newIndividualEmails));

            userDns.clear();
            userDns.putAll(newUserDns);

            groupDns.clear();
            groupDns.putAll(newGroupDns);

            roleDns.clear();
            roleDns.putAll(newRoleDns);

            emailsMap.clear();
            usersMap.clear();
            groupsMap.clear();
            rolesMap.clear();
            identityMap.clear();
            identityRoleMap.clear();

            tx.commit();
            lastLoad.set(System.currentTimeMillis());
            initialized.compareAndSet(0L, 1L);
         }

         return getNextReloadDelay();
      }
      finally {
         loadCount.decrementAndGet();
      }
   }

   public String[] getUsers() {
      validateListeners();
      return userDns.keySet().toArray(new String[0]);
   }

   public String[] getSubIdentities(String group, int type) {
      validateListeners();
      IdentityKey key = new IdentityKey(group, type);
      return identityMap.computeIfAbsent(
         key, k -> provider.getClient().searchSubIdentities(group, getGroupDn(group), type).toArray(new String[0]));
   }

   public String[] getEmails(String user) {
      validateListeners();
      return emailsMap.computeIfAbsent(user, provider.getClient()::getEmails);
   }

   public String[] getIndividualUsers() {
      validateListeners();
      return individualUsers.toArray(new String[0]);
   }

   public String[] getIndividualEmails() {
      validateListeners();
      return individualEmails.toArray(new String[0]);
   }

   public String[] getRoles() {
      validateListeners();
      return roleDns.keySet().toArray(new String[0]);
   }

   public String[] getGroups() {
      validateListeners();
      return groupDns.keySet().toArray(new String[0]);
   }

   public String getUserDn(String user) {
      validateListeners();
      return userDns.get(user);
   }

   public String getGroupDn(String group) {
      validateListeners();
      return groupDns.get(group);
   }

   public String getRoleDn(String role) {
      validateListeners();
      return roleDns.get(role);
   }

   public String[] getRoles(String name, String dn, int type) {
      validateListeners();
      IdentityKey key = new IdentityKey(name, type);
      return identityRoleMap.computeIfAbsent(key, k -> provider.searchRoles(name, dn, type));
   }

   public User getUser(String name) {
      validateListeners();
      return usersMap.computeIfAbsent(
         name, k -> provider.getClient().getUser(new IdentityID(name, Organization.getDefaultOrganizationID())));
   }

   public Group getGroup(String name) {
      validateListeners();
      return groupsMap.computeIfAbsent(name, provider.getClient()::getGroup);
   }

   public Role getRole(String name) {
      validateListeners();
      return rolesMap.computeIfAbsent(name, provider.getClient()::getRole);
   }

   public boolean isLoading() {
      return loadCount.get() > 0;
   }

   public long getAge() {
      long loaded = lastLoad.get();
      return loaded == 0L ? 0L : System.currentTimeMillis() - loaded;
   }

   public boolean isInitialized() {
      return initialized.get() > 0L;
   }

   private Instant getLastLoadTime() {
      long ts = lastLoad.get();

      if(ts == 0L) {
         return null;
      }

      return Instant.ofEpochMilli(ts);
   }

   private boolean isReloadRequired() {
      Instant loaded = getLastLoadTime();
      return loaded == null ||
         Instant.now().isAfter(loaded.plusMillis(provider.getCacheRefreshDelay()));
   }

   private long getNextReloadDelay() {
      Instant loaded = getLastLoadTime();
      long interval = provider.getCacheRefreshDelay();

      if(loaded == null) {
         return interval;
      }

      long delay = interval - Duration.between(loaded, Instant.now()).toMillis();

      if(delay <= 0) {
         return 1L;
      }

      return delay;
   }

   private Callable<Long> reduce(Callable<Long> c1, Callable<Long> c2) {
      if(c1 instanceof LoadTask t1 && c2 instanceof LoadTask t2) {
         t2.force = t2.force || t1.force;
      }

      return c2;
   }

   @Override
   public void objectAdded(NamingEvent evt) {
      refresh(true, true);
   }

   @Override
   public void objectRemoved(NamingEvent evt) {
      refresh(true, true);
   }

   @Override
   public void objectRenamed(NamingEvent evt) {
      refresh(true, true);
   }

   @Override
   public void objectChanged(NamingEvent evt) {
      refresh(true, true);
   }

   @Override
   public void namingExceptionThrown(NamingExceptionEvent evt) {
      LOG.warn("LDAP exception occurred", evt.getException());
   }

   private void validateListeners() {
      validateLock.lock();

      try {
         Instant now = Instant.now();

         if(validateTimestamp == null || now.isAfter(validateTimestamp)) {
            LdapContext context = dirContext;

            if(dirContext != null && !provider.testContext(context)) {
               addListeners();
            }

            validateTimestamp = now.plus(5L, ChronoUnit.MINUTES);
         }
      }
      finally {
         validateLock.unlock();
      }
   }

   private void addListeners() {
      removeListeners();

      try {
         dirContext = provider.createContext();
         eventContext = (EventDirContext) dirContext.lookup("");

         if(provider.supportsNamingListener()) {
            eventContext.addNamingListener("", SearchControls.SUBTREE_SCOPE, this);
         }
      }
      catch(Exception e) {
         LOG.error("Failed to add LDAP listener, automatic cache invalidation disabled", e);

         if(eventContext != null) {
            try {
               eventContext.close();
            }
            catch(NamingException ignore) {
            }
            finally {
               eventContext = null;
            }
         }

         if(dirContext != null) {
            try {
               dirContext.close();
            }
            catch(NamingException ignore) {
            }
            finally {
               dirContext = null;
            }
         }
      }
   }

   private void removeListeners() {
      if(eventContext != null) {
         listenersAdded = false;

         try {
            //noinspection SynchronizeOnNonFinalField
            synchronized(eventContext) {
               eventContext.removeNamingListener(this);
               eventContext.close();
            }
         }
         catch(NamingException e) {
            LOG.warn("Failed to remove LDAP listener", e);
         }
         finally {
            eventContext = null;
         }
      }

      if(dirContext != null) {
         try {
            dirContext.close();
         }
         catch(NamingException e) {
            LOG.warn("Failed to close LDAP context", e);
         }
         finally {
            dirContext = null;
         }
      }
   }

   private final LdapAuthenticationProvider provider;
   private final Cluster cluster;
   private final Debouncer<String> debouncer;
   private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
      r -> new Thread(r, "LdapAuthenticationCache"));
   private final String prefix;
   private final DistributedLong loadCount;
   private final DistributedLong lastLoad;
   private final DistributedLong initialized;
   private final Set<String> individualUsers;
   private final Set<String> individualEmails;
   private final DistributedMap<String, String> userDns;
   private final DistributedMap<String, String> groupDns;
   private final DistributedMap<String, String> roleDns;
   private final DistributedMap<String, String[]> emailsMap;
   private final DistributedMap<String, User> usersMap;
   private final DistributedMap<String, Group> groupsMap;
   private final DistributedMap<String, Role> rolesMap;
   private final DistributedMap<IdentityKey, String[]> identityMap;
   private final DistributedMap<IdentityKey, String[]> identityRoleMap;
   private boolean listenersAdded = false;
   private LdapContext dirContext;
   private EventDirContext eventContext;
   private Instant validateTimestamp;
   private final Lock validateLock = new ReentrantLock();
   private final Lock listenerLock =  new ReentrantLock();
   private static final Logger LOG = LoggerFactory.getLogger(LdapAuthenticationCache.class);

   private final class LoadTask implements Callable<Long> {
      public LoadTask(boolean force) {
         this.force = force;
      }

      @Override
      public Long call() throws Exception {
         return loadInternal(force);
      }

      private boolean force;
   }

   private static final class IdentityKey implements Serializable {
      public IdentityKey(String name, int type) {
         this.name = name;
         this.type = type;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         IdentityKey that = (IdentityKey) o;
         return type == that.type && Objects.equals(name, that.name);
      }

      @Override
      public int hashCode() {
         return Objects.hash(name, type);
      }

      private final String name;
      private final int type;
   }
}
