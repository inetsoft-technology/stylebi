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
package inetsoft.web.admin.security;

import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Drivers;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tuple4;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #77771: deleting a self-organization user removes the user's data sources and data source
 * folders, and the registry removes their grants, including those of nested resources and
 * additional connections. The grants that the user delete writes back from its permission
 * snapshot must not re-create the keys of the removed resources, or a resource created again at
 * the path gets the leftover grant.
 * <p>
 * IdentityService, the registry and the security engine with its file authorization provider are
 * the real ones. A self organization exists only with the enterprise OrganizationManager, whose
 * {@code getCurrentOrgID()} doesn't lower-case the id like the community one, so the test
 * installs a manager with the enterprise behavior. Grants are written the way a self user's
 * portal create writes them, in the self organization's scope.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  IdentityServiceSelfResourceGrantTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class IdentityServiceSelfResourceGrantTest {
   private static final String URL = "jdbc:derby:memory:bug77771;create=true";
   private static final String SELF = Organization.getSelfOrganizationID();
   private static final String PREFIX = "b71";

   @Autowired
   private DataSourceRegistry registry;
   private SecurityTestDataBuilder builder;
   private OrganizationManager oldManager;

   @BeforeEach
   void setUp() {
      oldManager = (OrganizationManager)
         ReflectionTestUtils.getField(OrganizationManager.class, "instance");
      ReflectionTestUtils.setField(OrganizationManager.class, "instance",
                                   new CaseKeepingOrganizationManager());
   }

   @AfterEach
   void tearDown() throws Exception {
      try {
         if(builder != null) {
            for(Tuple4<ResourceType, String, String, Permission> t : chain().getPermissions()) {
               if(t.getThird() != null && t.getThird().startsWith(PREFIX)) {
                  chain().removePermission(t.getFirst(), t.getThird(), t.getSecond());
               }
            }

            inSelf(() -> {
               for(String name : registry.getDataSourceFullNames()) {
                  if(name.startsWith(PREFIX)) {
                     removeQuietly(() -> registry.removeDataSource(name));
                  }
               }

               for(String name : registry.getDataSourceFolderFullNames()) {
                  if(name.startsWith(PREFIX)) {
                     removeQuietly(() -> registry.removeDataSourceFolder(name));
                  }
               }
            });
         }
      }
      finally {
         registry.clearCache();

         if(builder != null) {
            builder.teardown();
         }

         ReflectionTestUtils.setField(OrganizationManager.class, "instance", oldManager);
      }
   }

   // own data source with an additional connection, a folder with a subfolder and nested data
   // sources (one with an additional connection), and several folders whose data source may be
   // listed before or after the folder in the permission snapshot: all removed, no key left
   @Test
   void removedResourcesKeepNoGrant() throws Exception {
      builder = base().setup();
      inSelf(() -> {
         registry.init();
         registry.setDataSource(source("b71a"), false);
         addAdditional("b71a", "add");
         registry.setDataSourceFolder(folder("b71F"));
         registry.setDataSourceFolder(folder("b71F/sub"));
         registry.setDataSource(source("b71F/own"), false);
         addAdditional("b71F/own", "add2");

         for(int i = 0; i < 8; i++) {
            registry.setDataSourceFolder(folder("b71F" + i));
            registry.setDataSource(source("b71F" + i + "/d"), false);
         }
      });

      List<String[]> grants = new ArrayList<>(List.of(
         new String[] { "DATA_SOURCE", "b71a" }, new String[] { "DATA_SOURCE", "b71a::add" },
         new String[] { "DATA_SOURCE_FOLDER", "b71F" },
         new String[] { "DATA_SOURCE_FOLDER", "b71F/sub" },
         new String[] { "DATA_SOURCE", "b71F/own" },
         new String[] { "DATA_SOURCE", "b71F/own::add2" }));

      for(int i = 0; i < 8; i++) {
         grants.add(new String[] { "DATA_SOURCE_FOLDER", "b71F" + i });
         grants.add(new String[] { "DATA_SOURCE", "b71F" + i + "/d" });
      }

      for(String[] grant : grants) {
         ownerGrant(ResourceType.valueOf(grant[0]), grant[1], "alice");
      }

      assertEquals(grants.size(), keys().size(), () -> "keys before the delete: " + keys());

      deleteUser("alice");

      inSelf(() -> {
         registry.clearCache();
         assertTrue(Arrays.stream(registry.getDataSourceFullNames())
                       .noneMatch(name -> name.startsWith(PREFIX)), "data sources left");
         assertTrue(Arrays.stream(registry.getDataSourceFolderFullNames())
                       .noneMatch(name -> name.startsWith(PREFIX)), "folders left");
      });
      assertEquals(List.of(), keys(), "grants of removed resources were written back");
   }

   // a nested data source also granted to a role: the role grant must not stay on the path, or a
   // data source created there again gives the role members access that a new one doesn't
   @Test
   void recreatedNestedDataSourceDoesNotGetTheRoleGrant() throws Exception {
      SecurityTestDataBuilder b = base();
      b.grantPermission(ResourceType.DATA_SOURCE_FOLDER, "/", ResourceAction.READ, "b71unused",
                        Identity.ROLE, SELF);
      b.markPermissionEdited(ResourceType.DATA_SOURCE_FOLDER, "/", SELF);
      builder = b.setup();
      inSelf(() -> {
         registry.init();
         registry.setDataSourceFolder(folder("b71H"));
         registry.setDataSource(source("b71H/x"), false);
      });
      ownerGrant(ResourceType.DATA_SOURCE_FOLDER, "b71H", "alice");
      ownerGrant(ResourceType.DATA_SOURCE, "b71H/x", "alice");
      inSelf(() -> {
         Permission p = SecurityEngine.getSecurity().getPermission(ResourceType.DATA_SOURCE, "b71H/x");
         p.setRoleGrantsForOrg(ResourceAction.READ, Collections.singleton("b71viewer"), SELF);
         SecurityEngine.getSecurity().setPermission(ResourceType.DATA_SOURCE, "b71H/x", p);
      });
      SRPrincipal bob = builder.principalOf("bob", SELF);
      assertTrue(canRead(bob, "b71H/x"));

      deleteUser("alice");

      assertFalse(exists("b71H/x"));
      assertEquals(List.of(), keys());

      inSelf(() -> {
         registry.setDataSourceFolder(folder("b71H"));
         registry.setDataSource(source("b71H/x"), false);
         registry.setDataSourceFolder(folder("b71J"));
         registry.setDataSource(source("b71J/x"), false);
      });
      assertFalse(canRead(bob, "b71J/x"), "control: a new data source isn't readable");
      assertFalse(canRead(bob, "b71H/x"), "the re-created data source got the old role grant");
   }

   // the removed data source's own key must not stay as a blank "edited" grant, which denies
   // the access a data source created there again would inherit
   @Test
   void recreatedDataSourceInheritsLikeANewOne() throws Exception {
      builder = base().setup();
      inSelf(() -> {
         registry.init();
         registry.setDataSource(source("b71L"), false);
      });
      ownerGrant(ResourceType.DATA_SOURCE, "b71L", "alice");

      deleteUser("alice");

      assertFalse(exists("b71L"));
      assertEquals(List.of(), keys());

      inSelf(() -> {
         registry.setDataSource(source("b71L"), false);
         registry.setDataSource(source("b71M"), false);
      });
      SRPrincipal bob = builder.principalOf("bob", SELF);
      assertTrue(canRead(bob, "b71M"), "control: a new data source is readable");
      assertTrue(canRead(bob, "b71L"), "the re-created data source got the old blank grant");
   }

   // Bug #77725, a data source that shares its path with a folder is kept: its grant must still
   // be written without the deleted user
   @Test
   void keptDataSourceLosesTheDeletedUsersGrant() throws Exception {
      builder = base().setup();
      inSelf(() -> {
         registry.init();
         registry.setDataSourceFolder(folder("b71K"));
         registry.setDataSource(source("b71K/b71KX"), false);
         registry.setDataSource(source("b71K"), false);
      });
      ownerGrant(ResourceType.DATA_SOURCE, "b71K", "alice");

      deleteUser("alice");

      assertTrue(exists("b71K"));
      Permission permission = chain().getPermission(ResourceType.DATA_SOURCE, "b71K", SELF);
      assertNotNull(permission);
      assertTrue(permission.getAllUserGrants(ResourceAction.READ).stream()
                    .noneMatch(id -> "alice".equals(id.getName())),
                 "the kept data source still names the deleted user");
   }

   private SecurityTestDataBuilder base() {
      return SecurityTestDataBuilder.create()
         .withSecurity("true")
         .withMultiTenant("true")
         .addOrg("Self Organization", SELF)
         .addUser("alice", SELF, "password")
         .addUser("bob", SELF, "password")
         .addRole("b71viewer", SELF)
         .addRole("b71unused", SELF)
         .addUserToRole("bob", "b71viewer", SELF);
   }

   private AuthorizationChain chain() {
      return SecurityEngine.getSecurity().getAuthorizationChain().orElseThrow();
   }

   private List<String> keys() {
      List<String> keys = new ArrayList<>();

      for(Tuple4<ResourceType, String, String, Permission> t : chain().getPermissions()) {
         if(t.getThird() != null && t.getThird().startsWith(PREFIX)) {
            keys.add(t.getFirst() + ":" + t.getSecond() + ":" + t.getThird());
         }
      }

      return keys;
   }

   private IdentityService service() {
      return new IdentityService(
         SecurityEngine.getSecurity(), null, null, null, null, null, null, null, null, registry,
         null, null, null, null, Optional.empty(), null, null, null, null, null, null, null, null,
         null, null, null, null, null, Optional.empty());
   }

   // the permission part of a user delete (IdentityService.syncIdentity)
   private void deleteUser(String user) throws Exception {
      inSelf(() -> service().updateIdentityPermissions(
         Identity.USER, new IdentityID(user, SELF), null, SELF, SELF, true));
   }

   // the grant a self user's portal create writes (DatasourcesBaseService.createDataSource)
   private void ownerGrant(ResourceType type, String path, String user) throws Exception {
      inSelf(() -> {
         String orgId = OrganizationManager.getInstance().getCurrentOrgID();
         assertEquals(SELF, orgId);
         Permission permission = new Permission();
         Set<String> users = Collections.singleton(user);
         permission.setUserGrantsForOrg(ResourceAction.READ, users, orgId);
         permission.setUserGrantsForOrg(ResourceAction.WRITE, users, orgId);
         permission.setUserGrantsForOrg(ResourceAction.DELETE, users, orgId);
         permission.updateGrantAllByOrg(orgId, true);
         SecurityEngine.getSecurity().setPermission(type, path, permission);
      });
   }

   private boolean exists(String path) throws Exception {
      boolean[] result = new boolean[1];
      inSelf(() -> {
         registry.clearCache();
         result[0] = registry.getDataSource(path) != null;
      });
      return result[0];
   }

   private boolean canRead(SRPrincipal principal, String path) throws Exception {
      boolean[] result = new boolean[1];
      inSelf(() -> result[0] = SecurityEngine.getSecurity().checkPermission(
         principal, ResourceType.DATA_SOURCE, path, ResourceAction.READ));
      return result[0];
   }

   private void addAdditional(String path, String name) {
      ((JDBCDataSource) registry.getDataSource(path)).addDatasource(source(name));
   }

   private static DataSourceFolder folder(String path) {
      return new DataSourceFolder(path, LocalDateTime.now(), null);
   }

   private static JDBCDataSource source(String name) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName(name);
      ds.setCustom(true);
      ds.setCustomEditMode(true);
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL(URL);
      ds.setCustomUrl(URL);
      return ds;
   }

   private static void inSelf(Body body) throws Exception {
      OrganizationManager.runInOrgScope(SELF, () -> {
         body.run();
         return null;
      });
   }

   private static void removeQuietly(Body body) {
      try {
         body.run();
      }
      catch(Exception ignore) {
         // already removed with its folder, or kept by a clash
      }
   }

   private interface Body {
      void run() throws Exception;
   }

   /**
    * The current organization id as the enterprise OrganizationManager returns it, without the
    * lower-casing of the community one, so that it is "SELF" in the self organization.
    */
   static class CaseKeepingOrganizationManager extends OrganizationManager {
      @Override
      public String getCurrentOrgID() {
         return getCurrentOrgID((XPrincipal) ThreadContext.getContextPrincipal());
      }

      @Override
      public String getCurrentOrgID(Principal principal) {
         return super.getCurrentOrgID(principal);
      }
   }

   @Configuration
   static class Beans {
      @Bean
      public RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }

      @Bean
      public DependencyStorageService dependencyStorageService(KeyValueStorageManager storage)
         throws Exception
      {
         Constructor<DependencyStorageService> ctor =
            DependencyStorageService.class.getDeclaredConstructor(KeyValueStorageManager.class);
         ctor.setAccessible(true);
         return ctor.newInstance(storage);
      }

      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      @Bean
      @Primary
      public Drivers testDrivers() throws Exception {
         Drivers drivers = mock(Drivers.class);
         when(drivers.getDriverClass(anyString()))
            .thenAnswer(inv -> Class.forName(inv.<String>getArgument(0)));
         return drivers;
      }
   }
}
