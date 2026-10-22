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
package inetsoft.web.admin.ai.viewsheet;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Bug #79016: the projection is the plan-hash input, so caller text must not forge fields. */
@Tag("core")
class ViewsheetProjectionSeparatorTest {
   @Test
   void aliasHoldingASeparatorDoesNotCollideWithAliasPlusDescription() {
      String forged = ViewsheetProjection.projectFolder(
         new GetViewsheetFolderResult(true, "f", "o", "x;description=y", null));
      String genuine = ViewsheetProjection.projectFolder(
         new GetViewsheetFolderResult(true, "f", "o", "x", "y"));

      assertNotEquals(forged, genuine);
   }

   @Test
   void ordinaryFolderValuesProjectUnchanged() {
      assertEquals("type=folder;found=true;path=f;owner=o;alias=a;description=d",
                   ViewsheetProjection.projectFolder(
                      new GetViewsheetFolderResult(true, "f", "o", "a", "d")));
   }

   @Test
   void dependencyPathHoldingACommaDoesNotCollideWithTwoDependencies() {
      AssetEntry forged = entry("a,1:b");
      AssetEntry one = entry("a");
      AssetEntry two = entry("b");

      assertNotEquals(ViewsheetProjection.projectDependencies(new AssetEntry[]{ forged }),
                      ViewsheetProjection.projectDependencies(new AssetEntry[]{ one, two }));
   }

   private static AssetEntry entry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, null);
   }
}
