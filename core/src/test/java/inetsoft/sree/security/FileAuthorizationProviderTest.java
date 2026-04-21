/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

/*
 * FileAuthorizationProvider state transition table
 *
 * [Op: put→get]          empty + put(resource, perm, orgA)     → get(resource, orgA) == perm
 * [Op: put→null→get]     get(k)==perm + put(k, null)           → get(k) == null  (delegates to removePermission)
 * [Op: overwrite]        get(k)==v1 + put(k, v2)               → get(k) == v2
 * [Key: identity]        put(identityID, perm, orgA)           → get(identityID, orgA) == perm  (uses convertToKey())
 * [Key: list-resource]   two resource entries stored           → list() tuples match (type, orgId, path, perm)
 * [Key: list-identity]   identity entry stored                 → list() includes tuple with correct type+org+perm
 * [Bulk: scope-match]    orgA+orgB stored + cleanOrg(orgA)     → orgA entry removed
 * [Bulk: scope-other]    orgA+orgB stored + cleanOrg(orgA)     → orgB entry untouched
 * [Lifecycle: close]     open storage + tearDown()             → storage closed, field nulled
 * [Lifecycle: close×2]   closed storage + tearDown()           → no exception
 * [Op: put→null→get/id]  identity entry + put(identity, null)  → get(identity) == null
 * [Event: rename]        grant with oldId + rename event       → grant rewritten to newId
 * [Event: remove]        grant with oldId + remove event       → grant stripped
 *
 * Intent vs implementation suspects
 *
 * [Suspect 1] setPermission(identity, null) → intent: remove entry
 *             actual: removePermission(IdentityID) not overridden → no-op in base class
 * [Suspect 2] authenticationChanged(rename) → intent: rewrite grants from oldId to newId
 *             actual: Set<PermissionIdentity>.remove(IdentityID) → equals() never matches
 * [Suspect 3] authenticationChanged(remove) → intent: strip grants for deleted identity
 *             actual: same type mismatch as Suspect 2 → remove(oldID) always returns false
 */

import inetsoft.storage.KeyValuePair;
import inetsoft.storage.KeyValueStorage;
import inetsoft.uql.util.Identity;
import inetsoft.util.Tuple4;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class FileAuthorizationProviderTest {
   private static final String ORG_A = "orgA";
   private static final String ORG_B = "orgB";

   // [Op: put→get] resource key round-trip returns the stored permission unchanged
   // Pre: empty storage; Op: put(REPORT, "/reports/sales", perm, orgA); Post: get(REPORT, "/reports/sales", orgA) == perm
   @Test
   void setPermission_resourceWithExplicitOrg_getPermissionReturnsStoredPermission() throws Exception {
      FileAuthorizationProvider provider = newProvider();
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.READ, Set.of("alice"), ORG_A);

      provider.setPermission(ResourceType.REPORT, "/reports/sales", permission, ORG_A);

      assertSame(permission, provider.getPermission(ResourceType.REPORT, "/reports/sales", ORG_A));
   }

   // [Op: put→null→get] null permission delegates to removePermission and clears the stored entry
   // Pre: get(k)==perm; Op: put(k, null, orgA); Post: get(k, orgA) == null
   @Test
   void setPermission_resourceNullPermission_removesExistingPermission() throws Exception {
      FileAuthorizationProvider provider = newProvider();
      Permission permission = new Permission();

      provider.setPermission(ResourceType.REPORT, "/reports/sales", permission, ORG_A);
      provider.setPermission(ResourceType.REPORT, "/reports/sales", null, ORG_A);

      assertNull(provider.getPermission(ResourceType.REPORT, "/reports/sales", ORG_A));
   }

   // [Key: identity] IdentityID.convertToKey() is used as the path segment; round-trip returns stored permission
   // Pre: empty storage; Op: put(SECURITY_USER, identityID, perm, orgA); Post: get(SECURITY_USER, identityID, orgA) == perm
   @Test
   void setPermission_identityWithExplicitOrg_getPermissionReturnsStoredPermission() throws Exception {
      FileAuthorizationProvider provider = newProvider();
      Permission permission = new Permission();
      IdentityID identity = new IdentityID("analyst", ORG_A);

      provider.setPermission(ResourceType.SECURITY_USER, identity, permission, ORG_A);

      assertSame(permission, provider.getPermission(ResourceType.SECURITY_USER, identity, ORG_A));
   }

   // [Op: overwrite] second put on same key replaces first value
   // Pre: get(k)==v1; Op: put(k, v2, orgA); Post: get(k, orgA) == v2
   @Test
   void setPermission_overwritesExistingPermission() throws Exception {
      FileAuthorizationProvider provider = newProvider();
      Permission permV1 = new Permission();
      Permission permV2 = new Permission();

      provider.setPermission(ResourceType.REPORT, "/reports/sales", permV1, ORG_A);
      provider.setPermission(ResourceType.REPORT, "/reports/sales", permV2, ORG_A);

      assertSame(permV2, provider.getPermission(ResourceType.REPORT, "/reports/sales", ORG_A));
   }

   // [Key: list-resource] stored keys are parsed back into (type, orgId, path, perm) tuples by getPermissions()
   // Pre: REPORT+orgA and DASHBOARD+orgB stored; Op: list(); Post: both tuples present with correct fields
   @Test
   void getPermissions_parsesStoredEntriesIntoTuples() throws Exception {
      FileAuthorizationProvider provider = newProvider();
      Permission reportPermission = new Permission();
      Permission dashboardPermission = new Permission();

      provider.setPermission(ResourceType.REPORT, "/reports/sales", reportPermission, ORG_A);
      provider.setPermission(ResourceType.DASHBOARD, "/dashboards/finance", dashboardPermission, ORG_B);

      List<Tuple4<ResourceType, String, String, Permission>> permissions = provider.getPermissions();

      assertTrue(permissions.contains(new Tuple4<>(ResourceType.REPORT, ORG_A, "/reports/sales",
                                                   reportPermission)));
      assertTrue(permissions.contains(new Tuple4<>(ResourceType.DASHBOARD, ORG_B,
                                                   "/dashboards/finance", dashboardPermission)));
   }

   // [Key: list-identity] identity-keyed entries are also exposed by getPermissions() with correct type and org
   // Pre: identity entry stored under SECURITY_USER+orgA; Op: list(); Post: tuple with type==SECURITY_USER, org==orgA, perm==stored
   @Test
   void getPermissions_includesIdentityKeyedEntries() throws Exception {
      FileAuthorizationProvider provider = newProvider();
      Permission identityPermission = new Permission();
      IdentityID identity = new IdentityID("analyst", ORG_A);

      provider.setPermission(ResourceType.SECURITY_USER, identity, identityPermission, ORG_A);

      List<Tuple4<ResourceType, String, String, Permission>> permissions = provider.getPermissions();

      assertTrue(permissions.stream()
         .anyMatch(t -> t.getFirst() == ResourceType.SECURITY_USER
                     && ORG_A.equals(t.getSecond())
                     && t.getForth() == identityPermission));
   }

   // [Bulk: scope-match+scope-other] cleanOrganizationFromPermissions removes only the target org's entries
   // Pre: orgA entry + orgB entry; Op: cleanOrg(orgA); Post: orgA entry gone, orgB entry untouched
   @Test
   void cleanOrganizationFromPermissions_removesOnlyMatchingOrganizationEntries() throws Exception {
      FileAuthorizationProvider provider = newProvider();
      Permission orgAPermission = new Permission();
      Permission orgBPermission = new Permission();

      provider.setPermission(ResourceType.REPORT, "/reports/a", orgAPermission, ORG_A);
      provider.setPermission(ResourceType.REPORT, "/reports/b", orgBPermission, ORG_B);

      provider.cleanOrganizationFromPermissions(ORG_A);

      assertNull(provider.getPermission(ResourceType.REPORT, "/reports/a", ORG_A));
      assertSame(orgBPermission, provider.getPermission(ResourceType.REPORT, "/reports/b", ORG_B));
   }

   // [Lifecycle: close] tearDown closes the underlying storage and nulls the storage field
   // Pre: open storage; Op: tearDown(); Post: storage.isClosed()==true, field==null
   @Test
   void tearDown_closesStorageAndClearsProviderField() throws Exception {
      FileAuthorizationProvider provider = newProvider();
      InMemoryKeyValueStorage<Permission> storage = extractStorage(provider);

      provider.tearDown();

      assertTrue(storage.isClosed());
      assertNull(extractStorageField(provider));
   }

   // [Lifecycle: close×2] tearDown when storage is already null is a safe no-op
   // Pre: field==null (already torn down); Op: tearDown(); Post: no exception thrown
   @Test
   void tearDown_calledTwice_doesNotThrow() throws Exception {
      FileAuthorizationProvider provider = newProvider();
      provider.tearDown();
      assertDoesNotThrow(provider::tearDown);
   }

   // [Op: put→null→get/id] null permission for an identity key should delegate to removePermission(IdentityID)
   // Pre: get(identity, orgA)==perm; Op: put(identity, null, orgA); Post: get(identity, orgA)==null
   // BROKEN: FileAuthorizationProvider does not override removePermission(ResourceType, IdentityID, String);
   //         the call falls through to AbstractAuthorizationProvider's no-op, leaving the entry in storage.
   // Bug #74643
   @Test
   @Disabled("Bug: FileAuthorizationProvider does not override removePermission(ResourceType, IdentityID, String)")
   void setPermission_identityNullPermission_removesExistingPermission() throws Exception {
      FileAuthorizationProvider provider = newProvider();
      Permission permission = new Permission();
      IdentityID identity = new IdentityID("analyst", ORG_A);

      provider.setPermission(ResourceType.SECURITY_USER, identity, permission, ORG_A);
      provider.setPermission(ResourceType.SECURITY_USER, identity, null, ORG_A);

      assertNull(provider.getPermission(ResourceType.SECURITY_USER, identity, ORG_A));
   }

   // [Event: rename] rename event should rewrite grants from oldId to newId across all stored permissions
   // Pre: permission has READ grant for "alice"; Op: authenticationChanged(rename alice→alice-renamed); Post: grant holds "alice-renamed", not "alice"
   // BROKEN: identities.remove(oldID) compares IdentityID against Set<PermissionIdentity>; equals() never matches, so nothing is rewritten.
   // Bug #74643
   @Test
   @Disabled("Bug: authenticationChanged compares IdentityID against PermissionIdentity and mutates only a filtered copy")
   void authenticationChanged_renameEvent_updatesStoredPermissionGrants() throws Exception {
      FileAuthorizationProvider provider = newProvider();
      Permission permission = new Permission();
      IdentityID oldId = new IdentityID("alice", ORG_A);
      IdentityID newId = new IdentityID("alice-renamed", ORG_A);

      permission.setGrants(ResourceAction.READ, Identity.USER,
                           Set.of(new Permission.PermissionIdentity(oldId)));
      provider.setPermission(ResourceType.REPORT, "/reports/sales", permission, ORG_A);

      provider.authenticationChanged(new AuthenticationChangeEvent(this, oldId, newId,
         ORG_A, ORG_A, Identity.USER, false));

      Permission stored = provider.getPermission(ResourceType.REPORT, "/reports/sales", ORG_A);
      assertTrue(stored.getGrants(ResourceAction.READ, Identity.USER, ORG_A).stream()
                       .anyMatch(identity -> "alice-renamed".equals(identity.getName())));
      assertFalse(stored.getGrants(ResourceAction.READ, Identity.USER, ORG_A).stream()
                        .anyMatch(identity -> "alice".equals(identity.getName())));
   }

   // [Event: remove] remove event should strip the deleted identity's grants from all stored permissions
   // Pre: permission has READ grant for "alice"; Op: authenticationChanged(remove alice); Post: grant no longer contains "alice"
   // BROKEN: same IdentityID-vs-PermissionIdentity type mismatch as rename — remove(oldID) always returns false.
   // Bug #74643
   @Test
   @Disabled("Bug: authenticationChanged compares IdentityID against PermissionIdentity — remove(oldID) always returns false")
   void authenticationChanged_removeEvent_stripsOldIdentityFromGrants() throws Exception {
      FileAuthorizationProvider provider = newProvider();
      Permission permission = new Permission();
      IdentityID oldId = new IdentityID("alice", ORG_A);

      permission.setGrants(ResourceAction.READ, Identity.USER,
                           Set.of(new Permission.PermissionIdentity(oldId)));
      provider.setPermission(ResourceType.REPORT, "/reports/sales", permission, ORG_A);

      provider.authenticationChanged(new AuthenticationChangeEvent(this, oldId, null,
         ORG_A, ORG_A, Identity.USER, true));

      Permission stored = provider.getPermission(ResourceType.REPORT, "/reports/sales", ORG_A);
      assertFalse(stored.getGrants(ResourceAction.READ, Identity.USER, ORG_A).stream()
                        .anyMatch(identity -> "alice".equals(identity.getName())));
   }

   private static FileAuthorizationProvider newProvider() throws Exception {
      FileAuthorizationProvider provider = new FileAuthorizationProvider();
      setStorage(provider, new InMemoryKeyValueStorage<>());
      return provider;
   }

   private static void setStorage(FileAuthorizationProvider provider,
                                  InMemoryKeyValueStorage<Permission> storage) throws Exception
   {
      Field field = FileAuthorizationProvider.class.getDeclaredField("storage");
      field.setAccessible(true);
      field.set(provider, storage);
   }

   @SuppressWarnings("unchecked")
   private static InMemoryKeyValueStorage<Permission> extractStorage(FileAuthorizationProvider provider)
      throws Exception
   {
      return (InMemoryKeyValueStorage<Permission>) extractStorageField(provider);
   }

   private static Object extractStorageField(FileAuthorizationProvider provider) throws Exception {
      Field field = FileAuthorizationProvider.class.getDeclaredField("storage");
      field.setAccessible(true);
      return field.get(provider);
   }

   private static final class InMemoryKeyValueStorage<T extends Serializable>
      implements KeyValueStorage<T>
   {
      @Override
      public boolean contains(String key) {
         return values.containsKey(key);
      }

      @Override
      public T get(String key) {
         return values.get(key);
      }

      @Override
      public Future<T> put(String key, T value) {
         return CompletableFuture.completedFuture(values.put(key, value));
      }

      @Override
      public Future<?> putAll(SortedMap<String, T> values) {
         this.values.putAll(values);
         return CompletableFuture.completedFuture(null);
      }

      @Override
      public Future<T> remove(String key) {
         return CompletableFuture.completedFuture(values.remove(key));
      }

      @Override
      public Future<?> removeAll(Set<String> keys) {
         keys.forEach(values::remove);
         return CompletableFuture.completedFuture(null);
      }

      @Override
      public Future<T> rename(String oldKey, String newKey, T value) {
         T renamedValue = value != null ? value : values.remove(oldKey);
         values.remove(oldKey);
         T previous = values.put(newKey, renamedValue);
         return CompletableFuture.completedFuture(previous);
      }

      @Override
      public Future<?> replaceAll(SortedMap<String, T> values) {
         this.values.clear();
         this.values.putAll(values);
         return CompletableFuture.completedFuture(null);
      }

      @Override
      public Future<?> deleteStore() {
         values.clear();
         closed = true;
         return CompletableFuture.completedFuture(null);
      }

      @Override
      public Stream<KeyValuePair<T>> stream() {
         return values.entrySet().stream()
            .map(entry -> new KeyValuePair<>(entry.getKey(), entry.getValue()));
      }

      @Override
      public Stream<String> keys() {
         return values.keySet().stream();
      }

      @Override
      public int size() {
         return values.size();
      }

      @Override
      public void addListener(Listener<T> listener) {
      }

      @Override
      public void removeListener(Listener<T> listener) {
      }

      @Override
      public boolean isClosed() {
         return closed;
      }

      @Override
      public void close() {
         closed = true;
      }

      private final Map<String, T> values = new LinkedHashMap<>();
      private boolean closed;
   }
}
