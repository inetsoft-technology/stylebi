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

package inetsoft.uql.asset;

import inetsoft.sree.security.OrganizationManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Parses, with the real {@code Tool.splitWithQuote} and {@code AssetEntry.createAssetEntry}, the
 * exact {@code runQuery("ws:...")} strings the composer-chat {@code list_worksheets} tool emits
 * (Redmine #78813). The plugin's worksheetListTools.test.ts asserts the same strings, so the two
 * stay in step.
 */
@Tag("core")
class WsRunQueryRefParseTest {
   @Test
   void plainGlobalPath() {
      assertGlobal("ws:global:Examples/Census", "Examples/Census");
   }

   @Test
   void colonInPathNeedsDoubleQuotes() {
      assertGlobal("ws:global:\"a:b/c\"", "a:b/c");
   }

   @Test
   void apostropheInPathNeedsDoubleQuotes() {
      assertGlobal("ws:global:\"Bob's/x\"", "Bob's/x");
      assertGlobal("ws:global:\"Bob's:x\"", "Bob's:x");
   }

   @Test
   void userScopeUsesBareOwner() {
      try(MockedStatic<OrganizationManager> om = mockStatic(OrganizationManager.class)) {
         OrganizationManager manager = mock(OrganizationManager.class);
         om.when(OrganizationManager::getInstance).thenReturn(manager);
         when(manager.getCurrentOrgID()).thenReturn("org1");
         when(manager.getCurrentOrgID(any())).thenReturn("org1");

         assertUser("ws:bob:Mine/Draft", "bob", "Mine/Draft");
         assertUser("ws:\"o'brien\":Mine/Draft", "o'brien", "Mine/Draft");
         assertUser("ws:\"a:b\":Mine/Draft", "a:b", "Mine/Draft");
      }
   }

   private static AssetEntry parse(String ref) {
      return AssetEntry.createAssetEntry(ref, "org1");
   }

   private static void assertGlobal(String ref, String path) {
      AssetEntry entry = parse(ref);

      assertEquals(AssetRepository.GLOBAL_SCOPE, entry.getScope());
      assertEquals(path, entry.getPath());
   }

   private static void assertUser(String ref, String owner, String path) {
      AssetEntry entry = parse(ref);

      assertEquals(AssetRepository.USER_SCOPE, entry.getScope());
      assertEquals(owner, entry.getUser().getName());
      assertEquals(path, entry.getPath());
   }
}
