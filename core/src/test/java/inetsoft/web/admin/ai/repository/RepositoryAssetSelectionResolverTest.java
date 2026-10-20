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
package inetsoft.web.admin.ai.repository;

import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #78906: a nonexistent folderPath must be reported unresolved, not silently dropped. The
 * fake repository models the real one: getAllEntries is a case-sensitive path-prefix filter that
 * returns an empty array (never null) for a missing folder, containsEntry only knows explicit
 * folder records.
 */
@Tag("core")
class RepositoryAssetSelectionResolverTest {
   // explicit folder records (as in the asset tree) and sheet paths (implicit folders)
   private final Set<String> folderRecords = new HashSet<>(Set.of("Examples", "EmptyDir"));
   private final List<String> sheetPaths = List.of("Examples/Census", "Implicit/Sub/Sheet");

   private AssetRepository repository() throws Exception {
      AssetRepository repo = mock(AssetRepository.class);

      when(repo.getAllEntries(any(AssetEntry.class), any(), any(ResourceAction.class),
                              any(AssetEntry.Selector.class)))
         .thenAnswer(inv -> {
            AssetEntry folder = inv.getArgument(0);
            String path = folder.getPath();
            List<AssetEntry> found = new ArrayList<>();

            for(String sheet : sheetPaths) {
               String parent = sheet.substring(0, sheet.lastIndexOf('/'));

               if(path.equals("/") || path.isEmpty() || parent.equals(path) ||
                  parent.startsWith(path + "/"))
               {
                  found.add(new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                           AssetEntry.Type.VIEWSHEET, sheet, null));
               }
            }

            return found.toArray(new AssetEntry[0]);
         });
      when(repo.containsEntry(any(AssetEntry.class)))
         .thenAnswer(inv -> folderRecords.contains(((AssetEntry) inv.getArgument(0)).getPath()));
      return repo;
   }

   private RepositoryAssetSelectionResolver.Resolution resolve(String... folders)
      throws Exception
   {
      AssetRepository repo = repository();

      try(MockedStatic<AssetUtil> util = mockStatic(AssetUtil.class)) {
         util.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(repo);
         return new RepositoryAssetSelectionResolver()
            .resolve(List.of(), Arrays.asList(folders), null);
      }
   }

   @Test
   void existingFolderWithSheetsResolves() throws Exception {
      var r = resolve("Examples");
      assertEquals(1, r.resolved().size());
      assertTrue(r.unresolvedEntities().isEmpty());
   }

   @Test
   void nonexistentFolderIsUnresolved() throws Exception {
      var r = resolve("Examples/Nope");
      assertTrue(r.resolved().isEmpty());
      assertEquals(List.of("Examples/Nope"), r.unresolvedEntities());
   }

   @Test
   void caseMismatchIsUnresolved() throws Exception {
      assertEquals(List.of("examples"), resolve("examples").unresolvedEntities());
   }

   @Test
   void trailingAndLeadingSlashAreNormalized() throws Exception {
      var r = resolve("Examples/", "/Examples");
      assertTrue(r.unresolvedEntities().isEmpty());
      assertEquals(1, r.resolved().size());
   }

   @Test
   void existingEmptyFolderIsResolvedWithZeroAssets() throws Exception {
      var r = resolve("EmptyDir");
      assertTrue(r.resolved().isEmpty());
      assertTrue(r.unresolvedEntities().isEmpty());
   }

   @Test
   void implicitFolderImpliedOnlyBySheetPathsResolves() throws Exception {
      var r = resolve("Implicit", "Implicit/Sub");
      assertTrue(r.unresolvedEntities().isEmpty());
      assertEquals(1, r.resolved().size());
   }

   @Test
   void rootResolvesAndBlankIsUnresolved() throws Exception {
      var root = resolve("/");
      assertTrue(root.unresolvedEntities().isEmpty());
      assertEquals(2, root.resolved().size());

      var blank = resolve("  ", "");
      assertEquals(List.of("  ", ""), blank.unresolvedEntities());
      assertTrue(blank.resolved().isEmpty());
   }

   @Test
   void userScopePathStaysUnresolved() throws Exception {
      assertEquals(List.of("My Dashboards/Mine"), resolve("My Dashboards/Mine").unresolvedEntities());
   }
}
