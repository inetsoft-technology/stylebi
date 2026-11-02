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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class RepositoryImportChangePlanServiceTest {
   private static RequiredAssetModel asset(String name, int index) {
      return RequiredAssetModel.builder().name(name).index(index).lastModifiedTime(0L).build();
   }

   @Test
   void unknownIgnoreNameIsRefusedNamingItVerbatim() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> RepositoryImportChangePlanService.ignoreListIndices(
            List.of(asset("Examples/Real", 0)), List.of("Examples/NoSuchDep")));
      assertTrue(e.getMessage().contains("Examples/NoSuchDep"), e.getMessage());
      assertTrue(e.getMessage().contains("Examples/Real"), e.getMessage());
   }

   @Test
   void unknownNameMixedWithRealNameIsRefused() {
      assertThrows(IllegalArgumentException.class,
         () -> RepositoryImportChangePlanService.ignoreListIndices(
            List.of(asset("A", 0)), List.of("A", "Typo")));
   }

   @Test
   void blankNameDoesNotHideATypo() {
      assertThrows(IllegalArgumentException.class,
         () -> RepositoryImportChangePlanService.ignoreListIndices(
            List.of(asset("A", 0)), List.of("")));
   }

   @Test
   void unknownNameRefusedWhenPackageHasNoDependents() {
      assertThrows(IllegalArgumentException.class,
         () -> RepositoryImportChangePlanService.ignoreListIndices(null, List.of("X")));
   }

   @Test
   void realNamesMapToIndicesAndDuplicatesAreHarmless() {
      assertEquals(List.of("3"), RepositoryImportChangePlanService.ignoreListIndices(
         List.of(asset("A", 1), asset("B", 3)), List.of("B", "B")));
   }

   @Test
   void emptyOrNullIgnoreListIgnoresNothing() {
      assertTrue(RepositoryImportChangePlanService.ignoreListIndices(
         List.of(asset("A", 1)), List.of()).isEmpty());
      assertTrue(RepositoryImportChangePlanService.ignoreListIndices(
         List.of(asset("A", 1)), null).isEmpty());
   }
}
