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
package inetsoft.web.admin.deploy;

import inetsoft.sree.internal.DeployManagerService;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.dep.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80093: the import preview classifies willOverwrite/willCreate from the changed asset
 * ({@code getChangeRootFolderAsset}).{@code getPath()/getUser()}. This pins what that real asset
 * yields for a worksheet and a viewsheet in global and user scope, so the mapping is not only
 * checked through prefilled model fields.
 */
@Tag("core")
class ChangeRootFolderAssetTargetTest {
   private final DeployManagerService service = new DeployManagerService(
      null, null, null, null, null, null, null, null, null, null, null);

   private XAsset change(XAsset asset, AssetEntry target) {
      AssetEntry commonPrefix = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                               AssetEntry.Type.FOLDER, "A", null);
      return service.getChangeRootFolderAsset(asset, target, null, commonPrefix, true,
                                              null, null, true);
   }

   private static AssetEntry folder(int scope, String path, IdentityID user) {
      return new AssetEntry(scope, AssetEntry.Type.FOLDER, path, user);
   }

   @Test
   void globalTargetForAGlobalWorksheet() {
      WorksheetAsset ws = new WorksheetAsset(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "A/ws", null));

      XAsset changed = change(ws, folder(AssetRepository.GLOBAL_SCOPE, "Target", null));

      assertEquals("Target/ws", changed.getPath());
      assertTrue(changed.getUser() == null || changed.getUser().name == null ||
                 changed.getUser().name.isEmpty() || "__NULL__".equals(changed.getUser().name),
                 String.valueOf(changed.getUser()));
   }

   @Test
   void userTargetForAWorksheetCarriesTheOwner() {
      IdentityID bob = new IdentityID("bob", "host-org");
      WorksheetAsset ws = new WorksheetAsset(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "A/ws", null));

      XAsset changed = change(ws, folder(AssetRepository.USER_SCOPE, "Target", bob));

      assertEquals("Target/ws", changed.getPath());
      assertEquals(bob, changed.getUser());
   }

   @Test
   void userTargetForAViewsheetCarriesTheOwner() {
      IdentityID bob = new IdentityID("bob", "host-org");
      ViewsheetAsset vs = new ViewsheetAsset(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "A/vs", null));

      XAsset changed = change(vs, folder(AssetRepository.USER_SCOPE, "Target", bob));

      assertEquals("Target/vs", changed.getPath());
      assertEquals(bob, changed.getUser());
   }
}
