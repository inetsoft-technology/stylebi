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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.storage.ClusterStorageTransfer;
import inetsoft.storage.KeyValueEngine;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.AdditionalAnswers;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #77965: a global role grant is PermissionIdentity(role, null). Permission's JSON
 * serializer writes the null organization as JSON null, and the deserializer read it back as the
 * string "null". Every JSON key-value engine (database, MongoDB, DynamoDB, Firestore, Cosmos DB)
 * and every storage backup/restore then turned the grant into one that matches no role: holders
 * of the role lost access, under permission.andCondition the role half of the condition was
 * dropped, and deleting the role no longer removed the grant.
 *
 * <p>The reader now keeps the null, and FileAuthorizationProvider repairs the grants that were
 * already stored with "null" when its storage is opened and after a restore into the live
 * storage. A grant is repaired only when the global role exists, and only outside the key of an
 * organization whose id is "null". Otherwise it is kept as it is.
 *
 * <p>Runs against the real SecurityEngine, FileAuthenticationProvider and
 * FileAuthorizationProvider, with the copy-on-read cluster so a stored Permission is not shared
 * with the caller.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  PermissionMatrixOrgLifecycleTest.CopyOnReadClusterConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class GlobalRoleGrantReloadTest {
   private static final String ORG = "o77965";
   private static final String GLOBAL_ROLE = "gr77965";
   private static final String DELETED_ROLE = "gone77965";
   private static final String GROUP = "sales77965";
   private static final String VS = "vs77965";
   private static final String AND_CONDITION = "inetsoft.org." + ORG + ".permission.andCondition";

   private SecurityTestDataBuilder builder;
   private FileAuthorizationProvider authz;
   private final List<String> extraKeys = new ArrayList<>();

   @BeforeEach
   void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG, ORG)
         .addGlobalRole(GLOBAL_ROLE)
         .addGroup(GROUP, ORG)
         .addUser("alice", ORG, "password")
         .addUser("bob", ORG, "password")
         .addUserToGroup("bob", GROUP, ORG)
         .setup();

      AuthenticationChain authcChain = (AuthenticationChain)
         SecurityEngine.getSecurity().getSecurityProvider().getAuthenticationProvider();
      FileAuthenticationProvider authc = (FileAuthenticationProvider) authcChain.getProviders().get(0);
      authz = (FileAuthorizationProvider) SecurityEngine.getSecurity().getAuthorizationChain()
         .orElseThrow().getProviders().get(0);

      // alice holds the global role, as the EM role pane of a site admin assigns it
      IdentityID aliceID = new IdentityID("alice", ORG);
      FSUser alice = (FSUser) authc.getUser(aliceID);
      alice.setRoles(new IdentityID[] { new IdentityID(GLOBAL_ROLE, null) });
      authc.setUser(aliceID, alice);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      SreeEnv.remove(AND_CONDITION);
      PermissionChecker.resetAndConditionCache();

      if(authz != null) {
         authz.removePermission(ResourceType.VIEWSHEET, VS, ORG);

         for(String key : extraKeys) {
            try {
               storage().remove(key).get();
            }
            catch(Exception ignore) {
            }
         }
      }

      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   // ---- the JSON round trip -------------------------------------------------------------------

   @Test
   void jsonRoundTripKeepsTheGlobalRoleGrantGlobal() throws Exception {
      ObjectMapper mapper = KeyValueEngine.createObjectMapper();
      Permission perm = vsPermission();
      perm.setRoleGrantsForOrg(ResourceAction.WRITE, Set.of("orgRole"), ORG);

      Permission reloaded = mapper.readValue(mapper.writeValueAsString(perm), Permission.class);

      assertEquals(Set.of(new Permission.PermissionIdentity(GLOBAL_ROLE, null)),
                   reloaded.getGrants(ResourceAction.READ, Identity.ROLE, null));
      assertEquals(Set.of(new Permission.PermissionIdentity("orgRole", ORG)),
                   reloaded.getGrants(ResourceAction.WRITE, Identity.ROLE, null));
      assertEquals(Set.of(new Permission.PermissionIdentity(GROUP, ORG)),
                   reloaded.getGrants(ResourceAction.READ, Identity.GROUP, null));
   }

   @Test
   void identityWithoutOrganizationFieldReadsAsGlobal() throws Exception {
      ObjectMapper mapper = KeyValueEngine.createObjectMapper();
      String json = "{\"READ\":{\"roles\":[{\"name\":\"" + GLOBAL_ROLE + "\"}]}}";

      Permission reloaded = mapper.readValue(json, Permission.class);

      assertEquals(Set.of(new Permission.PermissionIdentity(GLOBAL_ROLE, null)),
                   reloaded.getGrants(ResourceAction.READ, Identity.ROLE, null));
   }

   // ---- the permission checks after a reload --------------------------------------------------

   @Test
   void globalRoleHolderKeepsAccessAfterJsonReload() throws Exception {
      authz.setPermission(ResourceType.VIEWSHEET, VS, vsPermission(), ORG);
      assertTrue(check("alice"), "precondition: the global role grants alice");

      authz.setPermission(ResourceType.VIEWSHEET, VS, jsonReload(vsPermission()), ORG);

      assertTrue(check("alice"), "the reloaded global role grant no longer grants its holder");
   }

   @Test
   void andConditionStillRequiresTheGlobalRoleAfterJsonReload() throws Exception {
      SreeEnv.setProperty(AND_CONDITION, "true");
      PermissionChecker.resetAndConditionCache();
      authz.setPermission(ResourceType.VIEWSHEET, VS, vsPermission(), ORG);
      assertFalse(check("bob"), "precondition: bob is in the group but doesn't hold the role");

      authz.setPermission(ResourceType.VIEWSHEET, VS, jsonReload(vsPermission()), ORG);

      assertFalse(check("bob"), "the role half of the AND condition was dropped");
   }

   @Test
   void deletingTheGlobalRoleRemovesTheReloadedGrant() throws Exception {
      authz.setPermission(ResourceType.VIEWSHEET, VS, jsonReload(vsPermission()), ORG);

      ((EditableAuthenticationProvider) ((AuthenticationChain) SecurityEngine.getSecurity()
         .getSecurityProvider().getAuthenticationProvider()).getProviders().get(0))
         .removeRole(new IdentityID(GLOBAL_ROLE, null));

      assertEquals(Set.of(), roleGrants(), "the grant of the deleted role was left behind");
   }

   // ---- the repair of grants already stored with "null" ---------------------------------------

   @Test
   void storedStringNullGrantOfExistingGlobalRoleIsRepairedOnOpen() throws Exception {
      authz.setPermission(ResourceType.VIEWSHEET, VS, corrupted(GLOBAL_ROLE), ORG);
      assertFalse(check("alice"), "precondition: the stored grant matches no role");

      reopen();

      assertEquals(Set.of(new Permission.PermissionIdentity(GLOBAL_ROLE, null)), roleGrants());
      assertTrue(check("alice"));
   }

   @Test
   void storedStringNullGrantOfDeletedRoleIsKept() throws Exception {
      authz.setPermission(ResourceType.VIEWSHEET, VS, corrupted(DELETED_ROLE), ORG);

      reopen();

      assertEquals(Set.of(new Permission.PermissionIdentity(DELETED_ROLE, "null")), roleGrants(),
                   "a grant of a role that doesn't exist must be neither revived nor removed");
   }

   @Test
   void storedStringNullGrantIsKeptWhileTheRoleLookupFails() throws Exception {
      authz.setPermission(ResourceType.VIEWSHEET, VS, corrupted(GLOBAL_ROLE), ORG);
      SecurityEngine engine = mock(SecurityEngine.class);
      // only the role lookup fails, the rest of the provider is the real one
      SecurityProvider failing = mock(SecurityProvider.class,
         AdditionalAnswers.delegatesTo(SecurityEngine.getSecurity().getSecurityProvider()));
      when(engine.getSecurityProvider()).thenReturn(failing);
      doThrow(new IllegalStateException("simulated provider outage")).when(failing).getRole(any());

      try(MockedStatic<SecurityEngine> statics = mockStatic(SecurityEngine.class)) {
         statics.when(SecurityEngine::getSecurity).thenReturn(engine);
         assertDoesNotThrow(this::reopen);
      }

      assertEquals(Set.of(new Permission.PermissionIdentity(GLOBAL_ROLE, "null")), roleGrants(),
                   "a failed lookup must keep the grant as it is");

      // the next start repairs it
      reopen();
      assertEquals(Set.of(new Permission.PermissionIdentity(GLOBAL_ROLE, null)), roleGrants());
   }

   @Test
   void storedStringNullGrantUnderOrganizationNamedNullIsKept() throws Exception {
      String key = "VIEWSHEET:null:" + VS;
      extraKeys.add(key);
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.ROLE,
                     Set.of(new Permission.PermissionIdentity(GLOBAL_ROLE, "null")));
      storage().put(key, perm).get();

      reopen();

      assertEquals(Set.of(new Permission.PermissionIdentity(GLOBAL_ROLE, "null")),
                   storage().get(key).getGrants(ResourceAction.READ, Identity.ROLE, null),
                   "the grant may belong to the role of the organization \"null\"");
   }

   // a MapDB backup taken before the fix has the organization "null", and the restore puts the
   // values into the live storage, which is not opened again
   @Test
   void clusterRestoreOfBackupWithStringNullRepairsTheGrant(@TempDir Path dir) throws Exception {
      authz.setPermission(ResourceType.VIEWSHEET, VS, vsPermission(), ORG);
      String value = KeyValueEngine.createObjectMapper().writeValueAsString(vsPermission())
         .replace("\"organization\":null", "\"organization\":\"null\"");
      Path backup = dir.resolve("backup.zip");
      String kv = "{\"defaultSecurityPermissions\":{\"VIEWSHEET:" + ORG + ":" + VS +
         "\":{\"type\":\"" + Permission.class.getName() + "\",\"value\":" + value + "}}}";

      try(OutputStream out = Files.newOutputStream(backup);
          ZipOutputStream zip = new ZipOutputStream(out))
      {
         zip.putNextEntry(new ZipEntry("key-value-index.json"));
         zip.write(kv.getBytes(StandardCharsets.UTF_8));
         zip.putNextEntry(new ZipEntry("blob-index.json"));
         zip.write("{}".getBytes(StandardCharsets.UTF_8));
      }

      new ClusterStorageTransfer().importContents(backup);

      assertEquals(Set.of(new Permission.PermissionIdentity(GLOBAL_ROLE, null)), roleGrants());
      assertTrue(check("alice"));
   }

   // ---- helpers -------------------------------------------------------------------------------

   // READ for the group and the global role, as the EM permission page of a site admin saves it
   private static Permission vsPermission() {
      Permission perm = new Permission();
      perm.setGroupGrantsForOrg(ResourceAction.READ, Set.of(GROUP), ORG);
      perm.setRoleGrantsForOrg(ResourceAction.READ, Set.of(GLOBAL_ROLE), null);
      perm.updateGrantAllByOrg(ORG, true);
      return perm;
   }

   // a permission as the JSON engines stored it before the fix
   private static Permission corrupted(String role) {
      Permission perm = vsPermission();
      perm.setGrants(ResourceAction.READ, Identity.ROLE,
                     Set.of(new Permission.PermissionIdentity(role, "null")));
      return perm;
   }

   // what a JSON key-value engine (database, MongoDB, ...) returns at the next start
   private static Permission jsonReload(Permission perm) throws Exception {
      ObjectMapper mapper = KeyValueEngine.createObjectMapper();
      return mapper.readValue(mapper.writeValueAsString(perm), Permission.class);
   }

   private boolean check(String user) throws Exception {
      SRPrincipal principal = builder.principalOf(user, ORG);
      ThreadContext.setContextPrincipal(principal);
      return SecurityEngine.getSecurity().checkPermission(
         principal, ResourceType.VIEWSHEET, VS, ResourceAction.READ);
   }

   private Set<Permission.PermissionIdentity> roleGrants() {
      Permission perm = authz.getPermission(ResourceType.VIEWSHEET, VS, ORG);
      return perm.getGrants(ResourceAction.READ, Identity.ROLE, null).stream()
         .collect(Collectors.toSet());
   }

   // the next call into the provider opens the storage again, as at the next start
   private void reopen() throws Exception {
      Field f = FileAuthorizationProvider.class.getDeclaredField("storage");
      f.setAccessible(true);
      f.set(authz, null);
      authz.getPermission(ResourceType.VIEWSHEET, VS, ORG);
   }

   @SuppressWarnings("unchecked")
   private inetsoft.storage.KeyValueStorage<Permission> storage() throws Exception {
      authz.getPermission(ResourceType.VIEWSHEET, VS, ORG);
      Field f = FileAuthorizationProvider.class.getDeclaredField("storage");
      f.setAccessible(true);
      return (inetsoft.storage.KeyValueStorage<Permission>) f.get(authz);
   }
}
