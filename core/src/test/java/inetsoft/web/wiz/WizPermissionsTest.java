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

package inetsoft.web.wiz;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the exact permission strings. This is the only cross-repo drift guard: the same list is
 * pinned in wiz-services (entitlementService.ts ALL_PERMISSIONS), and the two tests must be
 * changed together.
 */
@Tag("core")
class WizPermissionsTest {
   private static final List<String> EXPECTED_ADMIN = List.of(
      "config.read", "config.write", "config.write.critical",
      "entitlement.read", "entitlement.write",
      "annotation.approve", "annotation.edit", "annotation.exclude", "annotation.delete",
      "observability.read",
      "content.read", "content.delete",
      "audit.read");

   private static final List<String> EXPECTED_END_USER = List.of(
      "plugin.use",
      "portal.tab.chat", "portal.tab.visualizations", "portal.tab.datasources",
      "portal.tab.annotation", "portal.tab.annotationBrowser", "portal.tab.documents",
      "portal.tab.boards",
      "chart.save", "chart.saveToBoard", "chart.export",
      "visualization.delete", "visualization.rename",
      "board.delete",
      "datasource.create", "datasource.edit", "datasource.delete", "datasource.move",
      "document.create", "document.delete");

   @Test
   void adminPermissionsArePinned() {
      assertEquals(EXPECTED_ADMIN, WizPermissions.ADMIN);
      assertEquals(13, WizPermissions.ADMIN.size());
   }

   @Test
   void endUserPermissionsArePinned() {
      assertEquals(EXPECTED_END_USER, WizPermissions.END_USER);
      assertEquals(20, WizPermissions.END_USER.size());
   }

   @Test
   void allIsAdminThenEndUserWithoutDuplicates() {
      assertEquals(33, WizPermissions.ALL.size());
      assertEquals(WizPermissions.ALL.size(), new HashSet<>(WizPermissions.ALL).size());
      assertEquals(WizPermissions.ADMIN, WizPermissions.ALL.subList(0, 13));
      assertEquals(WizPermissions.END_USER, WizPermissions.ALL.subList(13, 33));
   }

   @Test
   void isEndUserDistinguishesTheTwoGroups() {
      WizPermissions.END_USER.forEach(p -> assertTrue(WizPermissions.isEndUser(p), p));
      WizPermissions.ADMIN.forEach(p -> assertFalse(WizPermissions.isEndUser(p), p));
      assertFalse(WizPermissions.isEndUser("unknown.permission"));
      assertFalse(WizPermissions.isEndUser(null));
   }
}
