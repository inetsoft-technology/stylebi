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

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78218, {@code removeScheduledTasks0} fetched the stored {@code AssetFolder} for a folder
 * path and immediately called {@code getEntries()} on it with no null check. A path can resolve
 * to no stored folder (so {@code getTaskFolder} returns null) for two distinct reasons, and both
 * must be treated as "already gone, so skip" rather than fail the whole request:
 *
 * <ul>
 *   <li>(a) An earlier path in the <em>same</em> request's loop already deleted it, as a side
 *       effect of recursively deleting its own ancestor folder (e.g. {@code taskNames=["A","A/B"]}
 *       deletes {@code A/B} while processing {@code A}, then NPEs processing {@code A/B} itself).
 *   <li>(b) It was already gone before the request started - a single-path request with no
 *       ancestor involved at all (e.g. {@code taskNames=["NoSuchFolder"]}).
 * </ul>
 *
 * Before the fix, both NPE'd on {@code AssetFolder.getEntries()}, were wrapped as
 * {@code MessageException}, and surfaced as HTTP 500 even though the folder(s) actually named
 * earlier in the request had already been deleted from storage. The fix is a null-guard-and-skip
 * in {@code removeScheduledTasks0}, mirroring the guard {@code checkScheduledTaskDependency0}
 * already has for the identical hazard (added for bug #77906).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleServiceRemoveFolderTest {
   // the ActionRecord built for each path (ScheduleService.java:2311) resolves the caller's
   // organization through OrganizationManager, which only resolves without extra wiring for the
   // default organization under the Spring context @SreeHome/BaseTestConfiguration provide.
   private static final String ORG = Organization.getDefaultOrganizationID();

   @Autowired
   private BlobStorageManager blobStorageManager;

   private BlobIndexedStorage storage;
   private ScheduleService scheduleService;
   private Principal savedPrincipal;
   private SRPrincipal user;

   @BeforeEach
   void setUp() throws Exception {
      savedPrincipal = ThreadContext.getContextPrincipal();
      storage = new BlobIndexedStorage(blobStorageManager);
      user = new SRPrincipal(new IdentityID("user78218", ORG), new IdentityID[0],
                             new String[0], ORG, 0L);
      ThreadContext.setContextPrincipal(user);

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), eq(ResourceType.SCHEDULE_TASK_FOLDER),
                                          anyString(), any(ResourceAction.class)))
         .thenReturn(true);

      ScheduleManager scheduleManager = mock(ScheduleManager.class);

      ScheduleTaskFolderService folderService = new ScheduleTaskFolderService(
         scheduleManager, securityEngine, mock(SecurityProvider.class), storage,
         mock(RenameTransformHandler.class));
      scheduleService = new ScheduleService(
         null, scheduleManager, null, null, null, null, null, null, securityEngine,
         folderService, storage, null, mock(RenameTransformHandler.class));

      // / -> A -> A/B (both subfolders are empty, no tasks needed for this scenario)
      AssetFolder root = new AssetFolder();
      root.addEntry(folder("A"));
      storage.putXMLSerializable(folder("/").toIdentifier(), root);
      AssetFolder a = new AssetFolder();
      a.addEntry(folder("A/B"));
      storage.putXMLSerializable(folder("A").toIdentifier(), a);
      storage.putXMLSerializable(folder("A/B").toIdentifier(), new AssetFolder());
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   // Case (a): the explicit second path ("A/B") was already deleted as a side effect of the
   // first path's ("A") recursive self-delete - must not NPE/500, and both folders end up gone.
   @Test
   void multiSelect_ancestorThenDescendant_deletesBothWithoutError() throws Exception {
      TaskListModel model = TaskListModel.builder().addTaskNames("A", "A/B").build();
      TaskRemoveResult result = new TaskRemoveResult();

      assertDoesNotThrow(() -> scheduleService.removeScheduleFolders(model, user, result));

      assertTrue(result.isRefresh());
      assertNull(storage.getXMLSerializable(folder("A").toIdentifier(), null));
      assertNull(storage.getXMLSerializable(folder("A/B").toIdentifier(), null));
      AssetFolder root =
         (AssetFolder) storage.getXMLSerializable(folder("/").toIdentifier(), null);
      assertEquals(0, root.getEntries().length, "A removed from the root's own entries");
   }

   // Case (b): a single path that was never there / already gone before the request started -
   // no ancestor/descendant relationship involved at all, same NPE site, same fix.
   @Test
   void singlePath_alreadyGone_isTreatedAsAlreadyDeleted() {
      TaskListModel model = TaskListModel.builder().addTaskNames("NoSuchFolder").build();
      TaskRemoveResult result = new TaskRemoveResult();

      assertDoesNotThrow(() -> scheduleService.removeScheduleFolders(model, user, result));

      assertTrue(result.isRefresh());
   }

   // Positive control: descendant named before its ancestor never hits the null path at all
   // (deleting A/B first unlinks it from A, so processing A next finds no stale recursion) -
   // confirms the fix doesn't change this already-working ordering.
   @Test
   void multiSelect_descendantThenAncestor_deletesBothWithoutError() throws Exception {
      TaskListModel model = TaskListModel.builder().addTaskNames("A/B", "A").build();
      TaskRemoveResult result = new TaskRemoveResult();

      assertDoesNotThrow(() -> scheduleService.removeScheduleFolders(model, user, result));

      assertTrue(result.isRefresh());
      assertNull(storage.getXMLSerializable(folder("A").toIdentifier(), null));
      assertNull(storage.getXMLSerializable(folder("A/B").toIdentifier(), null));
   }

   private static AssetEntry folder(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            path, null, ORG);
   }
}
