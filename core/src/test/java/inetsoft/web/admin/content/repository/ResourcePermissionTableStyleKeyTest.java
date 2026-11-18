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

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.security.ResourceType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #80959, the EM permission editor of a table style must read the key the runtime, the
 * composer and the EM write use, the style name, not the style ID, which a rename leaves behind.
 */
@Tag("core")
class ResourcePermissionTableStyleKeyTest {
   private final LibManager manager = mock(LibManager.class);
   private final LibManagerProvider provider = mock(LibManagerProvider.class);
   private final java.util.List<XTableStyle> styles = new java.util.ArrayList<>();

   private XTableStyle style(String id, String name) {
      XTableStyle style = mock(XTableStyle.class);
      when(style.getID()).thenReturn(id);
      when(style.getName()).thenReturn(name);
      styles.add(style);
      return style;
   }

   private String key(String path, boolean folder) {
      // LibManager.getTableStyle finds by ID first, then by name
      when(manager.getTableStyle(org.mockito.ArgumentMatchers.anyString())).thenAnswer(inv -> {
         String n = inv.getArgument(0);
         return styles.stream().filter(x -> n.equals(x.getID())).findFirst()
            .or(() -> styles.stream().filter(x -> n.equals(x.getName())).findFirst())
            .orElse(null);
      });
      when(manager.getTableStyleByName(org.mockito.ArgumentMatchers.anyString()))
         .thenAnswer(inv -> styles.stream().filter(x -> inv.getArgument(0).equals(x.getName()))
            .findFirst().orElse(null));
      when(provider.getManager()).thenReturn(manager);
      return ResourcePermissionService.getPermissionResourcePath(
         path, ResourceType.TABLE_STYLE, folder, provider, null);
   }

   @Test
   void renamedStyleIsKeyedByItsName() {
      style("old", "new");
      assertEquals("new", key("new", false));
   }

   @Test
   void oldNameOfARenamedStyleStaysTheRawPath() {
      // Bug #81051, T1 was renamed to T1x (ID stays T1), the old name must not resolve to T1x
      style("T1", "T1x");
      assertEquals("T1", key("T1", false));
   }

   @Test
   void folderNameIsResolvedByExactName() {
      style("f~s", "f~s");
      assertEquals("f~s", key("f~s", false));
   }

   @Test
   void nonexistentNameStaysTheRawPath() {
      style("T1", "T1x");
      assertEquals("nonexistent", key("nonexistent", false));
   }

   @Test
   void nameWinsOverTheIdOfARenamedStyle() {
      // style X was renamed a -> b (ID stays a), style Y was then created with the name a
      style("a", "b");
      style("a_1", "a");

      assertEquals("a", key("a", false));
   }

   @Test
   void unknownStyleAndFolderKeepThePath() {
      assertEquals("missing", key("missing", false));
      style("old", "new");
      assertEquals("old", key("old", true));
   }
}
