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
package inetsoft.web.admin.file;

/*
 * Regression coverage for a NullPointerException in repairRepositoryFolders()'s private
 * helpers repairScheduleTaskFolder() and addNestedFoldersToRegistry(). Both look up an
 * AssetFolder by identifier and dereference folder.getEntries() without a null check, unlike
 * the primary repair loop in repairRepositoryFolders() itself, which already treats a missing
 * folder as an expected "not found" case (IndexedStorage.getXMLSerializable() /
 * MetadataAwareStorage.getAssetFolder() return null when the key is absent). A fresh storage
 * restore hitting the hardcoded schedule task root "1^6^__NULL__^/" before that folder exists
 * reproduced the NPE via repairScheduleTaskFolder(). The fix adds the same null check to both
 * helpers.
 */

import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.IndexedStorage;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeService;
import inetsoft.web.security.auth.MissingResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@Tag("core")
class FileServiceTest {
   @BeforeEach
   void setUp() {
      indexedStorage = mock(IndexedStorage.class, withSettings().lenient());
      registry = mock(RepletRegistry.class, withSettings().lenient());

      service = new FileService(
         mock(ContentRepositoryTreeService.class), mock(SecurityProvider.class),
         indexedStorage, mock(RepletRegistryManager.class));
   }

   @Test
   void repairScheduleTaskFolder_missingFolder_doesNotThrow() throws Exception {
      // simulates a fresh storage restore where the schedule task root folder does not
      // exist yet -- getXMLSerializable() returns null, matching the primary repair loop's
      // own null-is-"not found" precedent
      when(indexedStorage.getXMLSerializable("1^6^__NULL__^/", null)).thenReturn(null);

      Method method = FileService.class.getDeclaredMethod(
         "repairScheduleTaskFolder", IndexedStorage.class, boolean.class, Set.class,
         String.class);
      method.setAccessible(true);

      assertDoesNotThrow(() -> method.invoke(
         service, indexedStorage, false, new HashSet<AssetEntry>(), "1^6^__NULL__^/"));

      verify(indexedStorage, never()).putXMLSerializable(anyString(), any());
   }

   @Test
   void addNestedFoldersToRegistry_missingFolder_doesNotThrow() throws Exception {
      AssetEntry parent = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, "/foo", null, "orgId");

      when(indexedStorage.getXMLSerializable(parent.toIdentifier(), null)).thenReturn(null);

      Method method = FileService.class.getDeclaredMethod(
         "addNestedFoldersToRegistry", AssetEntry.class, IndexedStorage.class,
         RepletRegistry.class);
      method.setAccessible(true);

      assertDoesNotThrow(() -> method.invoke(service, parent, indexedStorage, registry));

      verify(registry).addFolder(parent.getPath(), true);
   }

   /*
    * Regression coverage for cross-caller interference: AdminRepositoryMaintenanceController's
    * self-heal check on an outstanding token it does not own must be able to peek at completion
    * without consuming the real repairTasks entry that the token's actual owner still needs to
    * poll for real. Exercises the real repairTasks map (via reflection, since it's an internal
    * implementation detail with no public seam) rather than a fully-mocked FileService -- a full
    * mock can't surface a bug that lives entirely in the map's own shared, stateful
    * remove-on-first-observation behavior.
    */
   @Test
   void isRepairRepositoryFoldersComplete_doesNotConsumeCompletionRecord() throws Exception {
      String token = "tok-peek";
      putRepairTask(token, CompletableFuture.completedFuture(null));

      // An unrelated caller's peek (e.g. a different session's kickoff self-heal check) must
      // see the task as complete without removing it -- repeatable, not a one-shot observation.
      assertTrue(service.isRepairRepositoryFoldersComplete(token));
      assertTrue(service.isRepairRepositoryFoldersComplete(token));

      // The task's real owner must still be able to poll for the genuine result afterward.
      RepairRepositoryFoldersStatus status = service.getRepairRepositoryFoldersStatus(token);
      assertTrue(status.isComplete());
      assertFalse(status.isFailed());

      // The genuine status poll -- not the peek -- is what actually consumes the entry.
      assertThrows(MissingResourceException.class,
         () -> service.getRepairRepositoryFoldersStatus(token));
   }

   @SuppressWarnings("unchecked")
   private void putRepairTask(String token, CompletableFuture<?> future) throws Exception {
      Field field = FileService.class.getDeclaredField("repairTasks");
      field.setAccessible(true);
      ((ConcurrentMap<String, CompletableFuture<?>>) field.get(service)).put(token, future);
   }

   private IndexedStorage indexedStorage;
   private RepletRegistry registry;
   private FileService service;
}
