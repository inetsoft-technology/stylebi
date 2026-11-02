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

import inetsoft.web.admin.content.repository.model.RequiredAssetModel;
import inetsoft.web.admin.content.repository.model.RequiredAssetModelList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class AdminAssetExportServiceTest {
   @Test
   void namesUnresolvedInputs() {
      String m = AdminAssetExportService.noResolvedEntriesMessage(List.of("Nope"), 1);
      assertTrue(m.contains("not found: Nope"), m);
      assertFalse(m.contains("()"), m);
      assertFalse(m.contains("exist but contain no"), m);
   }

   @Test
   void existingEmptyFolderSaysSo() {
      String m = AdminAssetExportService.noResolvedEntriesMessage(List.of(), 1);
      assertTrue(m.contains("exist but contain no viewsheets or worksheets"), m);
      assertFalse(m.contains("()"), m);
   }

   @Test
   void mixedNamesBoth() {
      String m = AdminAssetExportService.noResolvedEntriesMessage(List.of("Nope"), 2);
      assertTrue(m.contains("not found: Nope"), m);
      assertTrue(m.contains("other given folders exist"), m);
   }

   private static RequiredAssetModelList dependents(String... names) {
      RequiredAssetModelList.Builder b = RequiredAssetModelList.builder();

      for(int i = 0; i < names.length; i++) {
         b.addRequiredAssets(RequiredAssetModel.builder().name(names[i]).index(i)
            .lastModifiedTime(0L).build());
      }

      return b.build();
   }

   private static RepositoryExportRequest request(String mode, List<String> names) {
      RepositoryExportRequest req = new RepositoryExportRequest();
      req.setIncludeDependenciesMode(mode);
      req.setIncludeDependencyNames(names);
      return req;
   }

   @Test
   void listModeRefusesUnknownNameNamingItVerbatim() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> AdminAssetExportService.applyDependencyMode(
            request("list", List.of("Examples/NoSuchDep")), dependents("Examples/Real")));
      assertTrue(e.getMessage().contains("Examples/NoSuchDep"), e.getMessage());
      assertTrue(e.getMessage().contains("Examples/Real"), e.getMessage());
   }

   @Test
   void listModeRefusesUnknownEvenWhenMixedWithRealName() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> AdminAssetExportService.applyDependencyMode(
            request("list", List.of("Examples/Real", "Typo")), dependents("Examples/Real")));
      assertTrue(e.getMessage().contains("Typo"), e.getMessage());
   }

   @Test
   void listModeRefusesBlankName() {
      assertThrows(IllegalArgumentException.class,
         () -> AdminAssetExportService.applyDependencyMode(
            request("list", List.of("")), dependents("Examples/Real")));
   }

   @Test
   void listModeKeepsRealNamesAndToleratesDuplicates() {
      List<RequiredAssetModel> out = AdminAssetExportService.applyDependencyMode(
         request("list", List.of("B", "B")), dependents("A", "B"));
      assertEquals(1, out.size());
      assertEquals("B", out.get(0).name());
   }

   @Test
   void listModeEmptyListAndNoneAndAllAreUnchanged() {
      assertTrue(AdminAssetExportService.applyDependencyMode(
         request("list", List.of()), dependents("A")).isEmpty());
      assertTrue(AdminAssetExportService.applyDependencyMode(
         request("none", List.of("Ignored")), dependents("A")).isEmpty());
      assertEquals(1, AdminAssetExportService.applyDependencyMode(
         request("all", List.of("Ignored")), dependents("A")).size());
   }
}
