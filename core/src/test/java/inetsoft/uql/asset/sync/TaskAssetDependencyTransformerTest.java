/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.uql.asset.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskAssetDependencyTransformerTest {
   private TaskAssetDependencyTransformer transformer;

   @BeforeEach
   void setUp() {
      transformer = new TaskAssetDependencyTransformer(null);
   }

   @Test
   void toModelAssetPath_null_returnsNull() {
      assertNull(transformer.toModelAssetPath(null, null));
   }

   @Test
   void toModelAssetPath_alreadyIdFormat_returnedUnchanged() {
      String idPath = "Examples/Orders^__^F1^Order Model";
      assertEquals(idPath, transformer.toModelAssetPath(idPath, null));
   }

   @Test
   void toModelAssetPath_rootModel_noFolder() {
      // "database/modelName" → "database^modelName"
      assertEquals("Examples/Orders^Order Model",
         transformer.toModelAssetPath("Examples/Orders/Order Model", null));
   }

   @Test
   void toModelAssetPath_folderedModel() {
      // "database/folder/modelName" → "database^__^folder^modelName"
      assertEquals("Examples/Orders^__^F1^Order Model",
         transformer.toModelAssetPath("Examples/Orders/F1/Order Model", "F1"));
   }

   @Test
   void toModelAssetPath_malformedPath_returnedUnchanged() {
      // Path with no '/' is malformed — return as-is rather than throw
      assertEquals("NoSlashPath", transformer.toModelAssetPath("NoSlashPath", null));
   }

   @Test
   void toModelAssetPath_emptyFolder_treatedAsNoFolder() {
      assertEquals("MyDB^MyModel",
         transformer.toModelAssetPath("MyDB/MyModel", ""));
   }

   // Portal-format paths: DatabaseModelBrowserService always prepends DATAMODEL_FOLDER_SPLITER
   // even for root-level models, producing "database^__^name" instead of "database^name".

   @Test
   void toModelAssetPath_portalRootPath_normalizedToCorrectFormat() {
      // "database^__^name" (portal root-model path) → "database^name"
      assertEquals("Examples/Orders^Order Model",
         transformer.toModelAssetPath("Examples/Orders^__^Order Model", null));
   }

   @Test
   void toModelAssetPath_portalFolderPath_returnedUnchanged() {
      // "database^__^folder^name" is already correct — must not be modified
      String idPath = "Examples/Orders^__^F1^Order Model";
      assertEquals(idPath, transformer.toModelAssetPath(idPath, "F1"));
   }
}
