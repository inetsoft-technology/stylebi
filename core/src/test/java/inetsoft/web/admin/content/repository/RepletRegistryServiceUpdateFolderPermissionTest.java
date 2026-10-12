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

/*
 * Bug #78374: RepletRegistryService.updateFolderPermission(pathFrom, pathTo) computed
 * newPath = pathTo + "/" + entryName unconditionally. For a root-target folder move
 * (pathTo = "" or "/", both of which the diagnosis/refutation traced as reachable from the EM
 * drag-and-drop "move to root" flow), this produced a bogus key ("/Sub1" or "//Sub1") that does not
 * match the folder's real registered path ("Sub1", per copyFolder's own root-aware join at
 * RepletRegistryService:565-568 and checkFolderMoved's independent computation in
 * RepositoryObjectService). AbstractAssetEngine.movePermissionBestEffort then wrote the explicit
 * permission to that orphan key and removed it from the real old key, leaving the moved folder with
 * no explicit permission of its own (silently falling back to inherited permissions from root).
 *
 * Fix mirrors copyFolder's existing guard:
 *   newPath = ("".equals(pathTo) || "/".equals(pathTo) ? "" : pathTo + "/") + entryName
 *
 * updateFolderPermission is private and is reached in production only through
 * copyFile()/copyFolder(), which also perform registry I/O (reading/writing the folder tree,
 * recursing into children). Those are orthogonal to the path-arithmetic defect this bug is about, so
 * this test isolates updateFolderPermission directly via reflection -- the narrowest point that
 * exercises the real fixed code against a mocked SecurityEngine, asserting exactly which resource key
 * setPermission()/removePermission() are called with. This matches the existing style in this module
 * (e.g. GraphTypeUtilCheckChartStylePermissionTest mocks SecurityEngine directly rather than wiring a
 * full Spring context / real AuthorizationProvider).
 */

import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.Permission;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("core")
class RepletRegistryServiceUpdateFolderPermissionTest {
   @Test
   void moveToRoot_emptyStringTarget_preservesPermissionAtRealPath() throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      Permission permission = new Permission();
      when(securityEngine.getPermission(ResourceType.ASSET, "Folder1/Sub1")).thenReturn(permission);

      invokeUpdateFolderPermission(newService(securityEngine), "Folder1/Sub1", "");

      // real registered path after copyFolder's own root-aware join is "Sub1", not "/Sub1"
      verify(securityEngine).setPermission(ResourceType.ASSET, "Sub1", permission);
      verify(securityEngine).removePermission(ResourceType.ASSET, "Folder1/Sub1");
      verify(securityEngine, never()).setPermission(eq(ResourceType.ASSET), eq("/Sub1"), any());
   }

   @Test
   void moveToRoot_slashTarget_preservesPermissionAtRealPath() throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      Permission permission = new Permission();
      when(securityEngine.getPermission(ResourceType.ASSET, "Folder1/Sub1")).thenReturn(permission);

      invokeUpdateFolderPermission(newService(securityEngine), "Folder1/Sub1", "/");

      // real registered path after copyFolder's own root-aware join is "Sub1", not "//Sub1"
      verify(securityEngine).setPermission(ResourceType.ASSET, "Sub1", permission);
      verify(securityEngine).removePermission(ResourceType.ASSET, "Folder1/Sub1");
      verify(securityEngine, never()).setPermission(eq(ResourceType.ASSET), eq("//Sub1"), any());
   }

   /*
    * Control case: moving into a non-root folder was never affected by this bug (both the buggy and
    * fixed arithmetic agree here). Kept to prove the fix's else-branch reproduces the old
    * unconditional concatenation exactly, with no change in non-root behavior.
    */
   @Test
   void moveToNonRootFolder_behaviorUnchanged() throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      Permission permission = new Permission();
      when(securityEngine.getPermission(ResourceType.ASSET, "Folder1/Sub1")).thenReturn(permission);

      invokeUpdateFolderPermission(newService(securityEngine), "Folder1/Sub1", "Folder2");

      verify(securityEngine).setPermission(ResourceType.ASSET, "Folder2/Sub1", permission);
      verify(securityEngine).removePermission(ResourceType.ASSET, "Folder1/Sub1");
   }

   private RepletRegistryService newService(SecurityEngine securityEngine) {
      return new RepletRegistryService(
         securityEngine, mock(ScheduleManager.class), mock(DependencyHandler.class),
         mock(RenameTransformHandler.class), mock(RepletRegistryManager.class));
   }

   private void invokeUpdateFolderPermission(RepletRegistryService service, String pathFrom,
                                              String pathTo)
      throws Exception
   {
      Method method = RepletRegistryService.class.getDeclaredMethod(
         "updateFolderPermission", String.class, String.class);
      method.setAccessible(true);
      method.invoke(service, pathFrom, pathTo);
   }
}
