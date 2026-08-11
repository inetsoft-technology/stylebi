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
package inetsoft.web.composer;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.web.RecycleBin;
import inetsoft.web.composer.model.RemoveAssetEvent;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.wiz.service.WizVisualizationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for the wiz "save visualization -> new folder" delete bug: a folder created
 * under the wiz-managed "Visualization Components" / "Wiz Chats" roots lives only in the
 * AssetRepository (see {@link WizVisualizationService#ensureFolder}, which calls
 * {@code assetRepository.addFolder} directly) and is never registered in the RepletRegistry the
 * way a folder created through the ordinary composer "New Folder" action is (that action calls
 * {@code repletRepository.addFolder}, see {@code AddFolderController}).
 *
 * <p>Deleting such a folder through the generic composer asset-tree "Remove" action must go
 * through {@code assetRepository.removeFolder}, not the RepletRegistry-based recycle-bin path --
 * the latter silently no-ops ({@code RepletRegistry#changeFolder} finds no folder matching the
 * path in its registry, so nothing is renamed, but the method still returns {@code "true"}) which
 * leaves the folder in place with no error ever shown to the user.
 *
 * <p>Needs the full Sree bootstrap because {@code removeAsset} builds an {@link
 * inetsoft.util.audit.ActionRecord} unconditionally, which reads {@code SreeEnv} for the local
 * host name.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RemoveAssetControllerWizFolderTest {
   private static RemoveAssetController controller(AssetRepository assetRepository) {
      return new RemoveAssetController(
         assetRepository, mock(ViewsheetService.class), mock(SecurityProvider.class),
         mock(LibManagerProvider.class), mock(RecycleBin.class), mock(DependencyHandler.class));
   }

   @Test
   void removesWizVisualizationComponentsFolderDirectlyFromAssetRepository() throws Exception {
      AssetRepository assetRepository = mock(AssetRepository.class);
      AssetEntry folder = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER,
         WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/f2", null);
      RemoveAssetEvent event = new RemoveAssetEvent.Builder()
         .entry(folder)
         .confirmed(true)
         .build();
      Principal principal = mock(Principal.class);

      MessageCommand result = controller(assetRepository).removeAsset(event, principal);

      verify(assetRepository).removeFolder(folder, principal, true);
      assertNull(result);
   }

   @Test
   void removesWizChatFolderDirectlyFromAssetRepository() throws Exception {
      AssetRepository assetRepository = mock(AssetRepository.class);
      AssetEntry folder = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER,
         WizVisualizationService.VISUALIZATION_ROOT_FOLDER_PATH + "/thread-1", null);
      RemoveAssetEvent event = new RemoveAssetEvent.Builder()
         .entry(folder)
         .confirmed(true)
         .build();
      Principal principal = mock(Principal.class);

      controller(assetRepository).removeAsset(event, principal);

      verify(assetRepository).removeFolder(folder, principal, true);
   }

   /** A same-named ordinary repository folder outside the wiz roots keeps using today's
    *  RepletRegistry-based recycle-bin path -- this fix must not touch that behavior. */
   @Test
   void ordinaryRepositoryFolderDoesNotUseAssetRepositoryRemoveFolder() throws Exception {
      AssetRepository assetRepository = mock(AssetRepository.class);
      AssetEntry folder = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, "SomeReportFolder", null);
      RemoveAssetEvent event = new RemoveAssetEvent.Builder()
         .entry(folder)
         .confirmed(true)
         .build();
      Principal principal = mock(Principal.class);

      // RecycleUtils.moveRepositoryToRecycleBin will throw against a fully-mocked
      // RepletRegistry-less environment -- that's fine, this test only asserts the wiz-only
      // removeFolder shortcut was NOT taken for a non-wiz path.
      try {
         controller(assetRepository).removeAsset(event, principal);
      }
      catch(Exception ignore) {
         // expected: no real RepletRegistry/AssetRepository backing in this unit test
      }

      verify(assetRepository, never()).removeFolder(any(), any(), anyBoolean());
   }
}
