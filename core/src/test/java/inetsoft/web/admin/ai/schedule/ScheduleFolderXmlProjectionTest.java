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
package inetsoft.web.admin.ai.schedule;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.sree.security.IdentityID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

// Bug #80838: AssetEntry created/modified stamps must not reach the plan-hash projection.
@Tag("core")
class ScheduleFolderXmlProjectionTest {
   @Test void nullFolderProjectsToNull() {
      assertNull(ScheduleFolderXmlProjection.project("A", null));
   }

   @Test void childrenDifferingOnlyInStampsProjectEqual() {
      AssetFolder a = folder(child("A/t1", 1_000L, "alice"));
      AssetFolder b = folder(child("A/t1", 9_999_000L, "bob"));

      assertEquals(ScheduleFolderXmlProjection.project("A", a),
                   ScheduleFolderXmlProjection.project("A", b));
   }

   @Test void stampElementsAreAbsentFromTheProjection() {
      String projection = ScheduleFolderXmlProjection.project(
         "A", folder(child("A/t1", 1_000L, "alice")));

      assertFalse(projection.contains("createdDate"));
      assertFalse(projection.contains("modifiedDate"));
      assertFalse(projection.contains("createdUsername"));
      assertFalse(projection.contains("modifiedUsername"));
      assertTrue(projection.contains("A/t1"));
   }

   @Test void childPathStillChangesTheProjection() {
      assertNotEquals(
         ScheduleFolderXmlProjection.project("A", folder(child("A/t1", 1L, "alice"))),
         ScheduleFolderXmlProjection.project("A", folder(child("A/t2", 1L, "alice"))));
   }

   @Test void childTypeStillChangesTheProjection() {
      AssetEntry task = child("A/x", 1L, "alice");
      AssetEntry sub = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, "A/x", null);

      assertNotEquals(ScheduleFolderXmlProjection.project("A", folder(task)),
                      ScheduleFolderXmlProjection.project("A", folder(sub)));
   }

   @Test void extraChildStillChangesTheProjection() {
      assertNotEquals(
         ScheduleFolderXmlProjection.project("A", folder(child("A/t1", 1L, "a"))),
         ScheduleFolderXmlProjection.project(
            "A", folder(child("A/t1", 1L, "a"), child("A/t2", 1L, "a"))));
   }

   @Test void folderPathStillChangesTheProjection() {
      AssetFolder f = folder(child("A/t1", 1L, "alice"));

      assertNotEquals(ScheduleFolderXmlProjection.project("A", f),
                      ScheduleFolderXmlProjection.project("B", f));
   }

   private static AssetFolder folder(AssetEntry... children) {
      AssetFolder folder = new AssetFolder();
      folder.setOwner(new IdentityID("admin", "host-org"));

      for(AssetEntry child : children) {
         folder.addEntry(child);
      }

      return folder;
   }

   private static AssetEntry child(String path, long stamp, String user) {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK, path, null);
      entry.setCreatedDate(new Date(stamp));
      entry.setModifiedDate(new Date(stamp + 5));
      entry.setCreatedUsername(user);
      entry.setModifiedUsername(user);
      return entry;
   }
}
