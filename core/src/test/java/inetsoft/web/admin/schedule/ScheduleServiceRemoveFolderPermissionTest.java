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
package inetsoft.web.admin.schedule;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.schedule.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #78321, deleting a schedule folder removes the SCHEDULE_TASK_FOLDER permissions of its
 * whole subtree. {@code removeScheduleFolders} used to check each selected path right before
 * deleting it, so for {@code [A, A/B]} with DELETE granted on A only, A/B was checked after A's
 * grant was gone, inherited nothing, and was reported as unauthorized although it was deleted.
 * Every path must be checked against the permissions as they were before the request.
 *
 * <p>Runs against the real security stack (FileAuthenticationProvider with the user added,
 * FileAuthorizationProvider, SecurityEngine) so that grants really disappear on delete.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleServiceRemoveFolderPermissionTest {
   private static final String ORG = Organization.getDefaultOrganizationID();
   private static final String USER = "u78321";
   private static final List<String> PATHS = List.of("A", "A/B", "A/B/C", "Q");

   @Autowired
   private BlobStorageManager blobStorageManager;

   private BlobIndexedStorage storage;
   private ScheduleService scheduleService;
   private ScheduleTaskFolderService folderService;
   private SecurityProvider securityProvider;
   private Principal savedPrincipal;
   private SRPrincipal user;

   @BeforeEach
   void setUp() throws Exception {
      savedPrincipal = ThreadContext.getContextPrincipal();

      FileAuthenticationProvider authc = new FileAuthenticationProvider();
      AuthenticationChain authcChain = new AuthenticationChain();
      authcChain.setProviders(List.of(authc));
      authcChain.saveConfiguration();

      FileAuthorizationProvider authz = new FileAuthorizationProvider();
      authz.setProviderName("Primary");
      AuthorizationChain authzChain = new AuthorizationChain();
      authzChain.setProviders(List.of(authz));
      authzChain.saveConfiguration();

      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.save();
      SecurityEngine securityEngine = SecurityEngine.getSecurity();
      securityEngine.init();
      securityProvider = securityEngine.getSecurityProvider();

      IdentityID userId = new IdentityID(USER, ORG);
      // without the user in the authentication provider every grant is ignored
      AuthenticationChain chain =
         (AuthenticationChain) securityProvider.getAuthenticationProvider();
      ((FileAuthenticationProvider) chain.getProviders().get(0)).addUser(new FSUser(userId));

      user = new SRPrincipal(userId, new IdentityID[0], new String[0], ORG, 0L);
      user.setIgnoreLogin(true);
      ThreadContext.setContextPrincipal(user);

      storage = new BlobIndexedStorage(blobStorageManager);
      ScheduleManager scheduleManager = mock(ScheduleManager.class);
      folderService = new ScheduleTaskFolderService(
         scheduleManager, securityEngine, securityProvider, storage,
         mock(RenameTransformHandler.class));
      scheduleService = new ScheduleService(
         null, scheduleManager, null, null, null, null, null, null, securityEngine,
         folderService, storage, null, mock(RenameTransformHandler.class));

      // / -> {A -> A/B -> A/B/C, Q}, all empty; / has no permission
      AssetFolder root = new AssetFolder();
      root.addEntry(folder("A"));
      root.addEntry(folder("Q"));
      storage.putXMLSerializable(folder("/").toIdentifier(), root);
      AssetFolder a = new AssetFolder();
      a.addEntry(folder("A/B"));
      storage.putXMLSerializable(folder("A").toIdentifier(), a);
      AssetFolder b = new AssetFolder();
      b.addEntry(folder("A/B/C"));
      storage.putXMLSerializable(folder("A/B").toIdentifier(), b);
      storage.putXMLSerializable(folder("A/B/C").toIdentifier(), new AssetFolder());
      storage.putXMLSerializable(folder("Q").toIdentifier(), new AssetFolder());

      grant("A", USER);
   }

   @AfterEach
   void tearDown() {
      try {
         for(String path : PATHS) {
            securityProvider.removePermission(ResourceType.SCHEDULE_TASK_FOLDER, path, ORG);
            storage.remove(folder(path).toIdentifier());
         }
      }
      finally {
         ThreadContext.setContextPrincipal(savedPrincipal);
         SreeEnv.remove("security.enabled");
      }
   }

   @Test
   void precondition_deleteOnDescendantsInheritedFromA() throws Exception {
      assertTrue(canDelete("A/B"));
      assertTrue(canDelete("A/B/C"));
      assertFalse(canDelete("/"));
      assertFalse(canDelete("Q"));
   }

   @Test
   void portal_ancestorThenDescendant_noError() throws Exception {
      TaskRemoveResult result = portal("A", "A/B");

      assertNull(result.getErrorMessage());
      assertTrue(result.isRefresh());
      assertGone("A", "A/B", "A/B/C");
   }

   @Test
   void em_ancestorThenDescendant_noError() throws Exception {
      assertDoesNotThrow(() -> em("A", "A/B"));
      assertGone("A", "A/B", "A/B/C");
   }

   @Test
   void portal_descendantThenAncestor_noError() throws Exception {
      TaskRemoveResult result = portal("A/B", "A");

      assertNull(result.getErrorMessage());
      assertGone("A", "A/B", "A/B/C");
   }

   @Test
   void portal_threeLevels_noError() throws Exception {
      TaskRemoveResult result = portal("A", "A/B", "A/B/C");

      assertNull(result.getErrorMessage());
      assertGone("A", "A/B", "A/B/C");
   }

   @Test
   void portal_explicitGrantOnDescendantToo_noError() throws Exception {
      grant("A/B", USER);

      TaskRemoveResult result = portal("A", "A/B");

      assertNull(result.getErrorMessage());
      assertGone("A", "A/B", "A/B/C");
   }

   // a deny on the descendant is still reported in both orders; it is still deleted through A
   @Test
   void portal_denyOnDescendant_reportedInBothOrders() throws Exception {
      grant("A/B", "other");
      assertFalse(canDelete("A/B"));

      TaskRemoveResult result = portal("A", "A/B");

      assertUnauthorized(result.getErrorMessage(), "A/B");
      assertTrue(result.isRefresh());
      assertGone("A", "A/B", "A/B/C");
   }

   @Test
   void portal_denyOnDescendant_descendantFirst_reported() throws Exception {
      grant("A/B", "other");

      TaskRemoveResult result = portal("A/B", "A");

      assertUnauthorized(result.getErrorMessage(), "A/B");
      assertGone("A", "A/B", "A/B/C");
   }

   @Test
   void em_denyOnDescendant_throws() throws Exception {
      grant("A/B", "other");

      SecurityException ex = assertThrows(SecurityException.class, () -> em("A", "A/B"));

      assertUnauthorized(ex.getMessage(), "A/B");
      assertGone("A", "A/B", "A/B/C");
   }

   @Test
   void portal_denyOnDescendantAlone_refusedAndKept() throws Exception {
      grant("A/B", "other");

      TaskRemoveResult result = portal("A/B");

      assertUnauthorized(result.getErrorMessage(), "A/B");
      assertFalse(result.isRefresh());
      assertKept("A", "A/B", "A/B/C");
   }

   // a never-existing child of an allowed folder answers the same as when it is requested alone
   @Test
   void portal_missingDescendant_silentLikeAlone() throws Exception {
      TaskRemoveResult alone = portal("A/X");
      assertNull(alone.getErrorMessage());
      assertKept("A");

      TaskRemoveResult result = portal("A", "A/X");

      assertNull(result.getErrorMessage());
      assertGone("A", "A/B", "A/B/C");
   }

   @Test
   void portal_mixedWithRefusedPaths_onlyRefusedReported() throws Exception {
      TaskRemoveResult result = portal("A", "A/B", "Z", "Q");

      assertUnauthorized(result.getErrorMessage(), "Z, Q");
      assertTrue(result.isRefresh());
      assertGone("A", "A/B", "A/B/C");
      assertKept("Q");
   }

   @Test
   void em_mixedWithRefusedPath_onlyRefusedReported() throws Exception {
      SecurityException ex =
         assertThrows(SecurityException.class, () -> em("A", "A/B", "Z"));

      assertUnauthorized(ex.getMessage(), "Z");
      assertGone("A", "A/B", "A/B/C");
   }

   private TaskRemoveResult portal(String... paths) throws Exception {
      TaskRemoveResult result = new TaskRemoveResult();
      scheduleService.removeScheduleFolders(model(paths), user, result);
      return result;
   }

   private void em(String... paths) throws Exception {
      scheduleService.removeScheduleFolders(model(paths), user);
   }

   private static TaskListModel model(String... paths) {
      return TaskListModel.builder().addTaskNames(paths).build();
   }

   private boolean canDelete(String path) throws Exception {
      return folderService.checkFolderPermission(path, user, ResourceAction.DELETE);
   }

   private void grant(String path, String userName) {
      Permission perm = new Permission();

      for(ResourceAction action :
         List.of(ResourceAction.READ, ResourceAction.WRITE, ResourceAction.DELETE))
      {
         perm.setUserGrantsForOrg(action, Set.of(userName), ORG);
      }

      securityProvider.setPermission(ResourceType.SCHEDULE_TASK_FOLDER, path, perm, ORG);
   }

   private void assertGone(String... paths) throws Exception {
      for(String path : paths) {
         assertNull(storage.getXMLSerializable(folder(path).toIdentifier(), null), path);
      }
   }

   private void assertKept(String... paths) throws Exception {
      for(String path : paths) {
         assertNotNull(storage.getXMLSerializable(folder(path).toIdentifier(), null), path);
      }
   }

   private static void assertUnauthorized(String message, String paths) {
      assertNotNull(message);
      assertTrue(message.startsWith("Unauthorized access to resource(s) \"" + paths + "\""),
                 message);
   }

   private static AssetEntry folder(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            path, null, ORG);
   }
}
