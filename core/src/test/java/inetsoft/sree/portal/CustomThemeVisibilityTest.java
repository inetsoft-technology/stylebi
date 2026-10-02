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
package inetsoft.sree.portal;

/*
 * Test strategy
 *
 * Class type: rule — which themes a theme pointer may resolve to for an organization (Bug #77285).
 *
 * --- isVisibleToOrganization() ---
 *  ├─ global theme                         → visible, multi-tenancy not consulted
 *  ├─ own org's private theme              → visible, multi-tenancy not consulted
 *  ├─ other org's private theme, MT on     → not visible
 *  └─ other org's private theme, MT off    → visible (themes keep their orgID after MT is off)
 *
 * --- isHiddenFromOrganization() ---
 *  ├─ id names no theme                    → not hidden (left to the resource layer)
 *  ├─ id names a visible theme             → not hidden
 *  ├─ id names only invisible themes       → hidden
 *  └─ empty / reserved id / null set       → not hidden
 */

import inetsoft.sree.internal.SUtil;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

@Tag("core")
class CustomThemeVisibilityTest {
   private MockedStatic<SUtil> sUtilStatic;

   @BeforeEach
   void setUp() {
      sUtilStatic = mockStatic(SUtil.class);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
   }

   @AfterEach
   void tearDown() {
      sUtilStatic.close();
   }

   @Test
   void isVisibleToOrganization_globalTheme_visibleWithoutCheckingMultiTenancy() {
      assertTrue(theme("g", null).isVisibleToOrganization("org2"));
      assertTrue(theme("g", "").isVisibleToOrganization("org2"));
      sUtilStatic.verify(SUtil::isMultiTenant, never());
   }

   @Test
   void isVisibleToOrganization_ownOrgTheme_visibleWithoutCheckingMultiTenancy() {
      assertTrue(theme("t", "org1").isVisibleToOrganization("org1"));
      sUtilStatic.verify(SUtil::isMultiTenant, never());
   }

   @Test
   void isVisibleToOrganization_otherOrgTheme_notVisible() {
      assertFalse(theme("t", "org1").isVisibleToOrganization("org2"));
   }

   @Test
   void isVisibleToOrganization_otherOrgThemeSingleTenant_visible() {
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(false);
      assertTrue(theme("t", "org1").isVisibleToOrganization("host-org"));
   }

   @Test
   void isHiddenFromOrganization_unknownId_notHidden() {
      assertFalse(CustomTheme.isHiddenFromOrganization(Set.of(theme("t", "org1")), "x", "org2"));
   }

   @Test
   void isHiddenFromOrganization_visibleTheme_notHidden() {
      Set<CustomTheme> themes = Set.of(theme("t", "org2"), theme("g", null));
      assertFalse(CustomTheme.isHiddenFromOrganization(themes, "t", "org2"));
      assertFalse(CustomTheme.isHiddenFromOrganization(themes, "g", "org2"));
   }

   @Test
   void isHiddenFromOrganization_otherOrgTheme_hidden() {
      assertTrue(CustomTheme.isHiddenFromOrganization(Set.of(theme("t", "org1")), "t", "org2"));
   }

   @Test
   void isHiddenFromOrganization_noThemeSelected_notHidden() {
      Set<CustomTheme> themes = Set.of(theme("t", "org1"));
      assertFalse(CustomTheme.isHiddenFromOrganization(themes, null, "org2"));
      assertFalse(CustomTheme.isHiddenFromOrganization(themes, "", "org2"));
      assertFalse(CustomTheme.isHiddenFromOrganization(themes, CustomTheme.DEFAULT_THEME_ID, "org2"));
      assertFalse(CustomTheme.isHiddenFromOrganization(null, "t", "org2"));
   }

   private static CustomTheme theme(String id, String orgID) {
      CustomTheme theme = new CustomTheme();
      theme.setId(id);
      theme.setName(id);
      theme.setOrgID(orgID);
      return theme;
   }
}
