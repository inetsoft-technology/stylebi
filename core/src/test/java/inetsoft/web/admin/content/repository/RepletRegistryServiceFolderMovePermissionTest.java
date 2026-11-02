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
package inetsoft.web.admin.content.repository;

import inetsoft.sree.*;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78520, a repository folder has a REPORT permission and a worksheet folder has an ASSET
 * permission, and both are keyed by the same path strings. Moving a repository folder through
 * {@link RepletRegistryService#copyFile} must not move or remove the ASSET permission of a
 * worksheet folder at the same path (nor of one of its subfolders); moving a worksheet folder must
 * still move its ASSET permission. Runs against the real registry, asset engine and security
 * engine.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RepletRegistryServiceFolderMovePermissionTest {
   private static final int REPO_FOLDER = RepositoryEntry.REPOSITORY | RepositoryEntry.FOLDER;
   private static final int WS_FOLDER = RepositoryEntry.WORKSHEET_FOLDER;

   private AssetRepository repo;
   private String orgId;
   private SecurityEngine security;
   // the test environment runs without security, so permissions are kept in a map
   private final Map<String, Permission> store = new HashMap<>();

   @BeforeEach
   void setUp() {
      repo = AssetUtil.getAssetRepository(false);
      orgId = Organization.getDefaultOrganizationID();
      store.clear();
      security = permissionEngine();
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void repositoryFolderMoveToRootLeavesWorksheetPermissionsUntouched() throws Exception {
      addReportFolders("F1", "F1/S1", "F1/S1/T1");
      security.setPermission(ResourceType.ASSET, "F1/S1", grant("a1"));
      security.setPermission(ResourceType.ASSET, "F1/S1/T1", grant("n1"));
      security.setPermission(ResourceType.ASSET, "S1", grant("b1"));
      security.setPermission(ResourceType.REPORT, "F1/S1", grant("r1"));

      assertTrue(copy("F1/S1", REPO_FOLDER, "", true));

      assertGrant("a1", ResourceType.ASSET, "F1/S1");
      assertGrant("n1", ResourceType.ASSET, "F1/S1/T1");
      assertGrant("b1", ResourceType.ASSET, "S1");
      assertNull(security.getPermission(ResourceType.ASSET, "S1/T1"));
      // the repository folder permission is still copied to the new path
      assertGrant("r1", ResourceType.REPORT, "S1");
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void repositoryFolderMoveToFolderLeavesWorksheetPermissionsUntouched() throws Exception {
      addReportFolders("F2", "F2/S2", "F2/S2/T2", "G2");
      security.setPermission(ResourceType.ASSET, "F2/S2", grant("a2"));
      security.setPermission(ResourceType.ASSET, "F2/S2/T2", grant("n2"));
      security.setPermission(ResourceType.ASSET, "G2/S2", grant("b2"));

      assertTrue(copy("F2/S2", REPO_FOLDER, "G2", true));

      assertGrant("a2", ResourceType.ASSET, "F2/S2");
      assertGrant("n2", ResourceType.ASSET, "F2/S2/T2");
      assertGrant("b2", ResourceType.ASSET, "G2/S2");
      assertNull(security.getPermission(ResourceType.ASSET, "G2/S2/T2"));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void worksheetFolderMoveStillMovesItsPermission() throws Exception {
      addWsFolders("W3", "W3/S3", "W3/S3/T3");
      security.setPermission(ResourceType.ASSET, "W3/S3", grant("a3"));
      security.setPermission(ResourceType.ASSET, "W3/S3/T3", grant("n3"));

      assertTrue(copy("W3/S3", WS_FOLDER, "", true));

      // the moved folder
      assertGrant("a3", ResourceType.ASSET, "S3");
      assertNull(security.getPermission(ResourceType.ASSET, "W3/S3"));
      // its subfolder
      assertGrant("n3", ResourceType.ASSET, "S3/T3");
      assertNull(security.getPermission(ResourceType.ASSET, "W3/S3/T3"));
   }

   private void assertGrant(String user, ResourceType type, String path) {
      Permission actual = security.getPermission(type, path);
      assertNotNull(actual, type + " " + path + " has no permission");
      Set<String> names = new TreeSet<>();
      actual.getUserGrants(ResourceAction.READ).forEach(g -> names.add(g.getName()));
      assertEquals(Set.of(user), names, type + " " + path);
   }

   private Permission grant(String user) {
      Permission permission = new Permission();
      permission.setUserGrants(ResourceAction.READ,
                               Set.of(new Permission.PermissionIdentity(user, orgId)));
      return permission;
   }

   private boolean copy(String from, int type, String to, boolean move) throws Exception {
      boolean[] result = new boolean[1];
      allowAll(() -> result[0] = service().copyFile(null, from, null, type, to, null, type, move,
                                                   null, admin()));
      return result[0];
   }

   private void addWsFolders(String... paths) throws Exception {
      for(String path : paths) {
         repo.addFolder(new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, path,
                                       null, orgId), null);
      }
   }

   private void addReportFolders(String... paths) throws Exception {
      RepletRegistry reg = RepletRegistryManager.getInstance().getRegistry(orgId);

      for(String path : paths) {
         reg.addFolder(path);
      }

      reg.save();
   }

   private SRPrincipal admin() {
      IdentityID id = new IdentityID("admin", orgId);
      return new SRPrincipal(id, new IdentityID[] { new IdentityID("Administrator", null) },
                             new String[0], orgId, 1L);
   }

   private RepletRegistryService service() {
      return new RepletRegistryService(
         security, null, mock(DependencyHandler.class), mock(RenameTransformHandler.class),
         RepletRegistryManager.getInstance());
   }

   // a security engine that allows every permission check, keeps the permissions in the map, and
   // is otherwise the real one
   private SecurityEngine permissionEngine() {
      SecurityEngine real = SecurityEngine.getSecurity();
      return mock(SecurityEngine.class, withSettings().spiedInstance(real).defaultAnswer(inv -> {
         Object[] args = inv.getArguments();

         switch(inv.getMethod().getName()) {
         case "checkPermission":
            return Boolean.TRUE;
         case "getPermission":
            return args[1] instanceof String ? store.get(key(args[0], args[1])) : null;
         case "setPermission":
            store.put(key(args[0], args[1]), (Permission) args[2]);
            return null;
         case "removePermission":
            store.remove(key(args[0], args[1]));
            return null;
         default:
            return inv.callRealMethod();
         }
      }));
   }

   private static String key(Object type, Object path) {
      return type + ":" + path;
   }

   private interface Body {
      void run() throws Exception;
   }

   private void allowAll(Body body) throws Exception {
      try(MockedStatic<SecurityEngine> st = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS)) {
         st.when(SecurityEngine::getSecurity).thenReturn(security);
         body.run();
      }
   }
}
