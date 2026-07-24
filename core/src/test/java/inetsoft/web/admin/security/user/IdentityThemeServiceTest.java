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
package inetsoft.web.admin.security.user;

/*
 * Issue #75739 (fixed) / matrix row 3d: community/core/src/test/resources/docs/org-lifecycle-resource-matrix.md,
 * section "三、其他机制" / "3.1 主题（Theme）", Delete 场景.
 *
 * removeTheme(orgID) now also strips the deleted org's ID out of every remaining (e.g.
 * globally-shared) theme's `organizations` list, not just themes owned by the deleted org.
 */

import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.portal.CustomThemesManager;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class IdentityThemeServiceTest {

   private CustomThemesManager manager;
   private IdentityThemeService service;

   @BeforeEach
   void setUp() {
      manager = mock(CustomThemesManager.class);
      service = new IdentityThemeService(manager);
   }

   // sanity check for the behavior that IS already correct today -- kept here so this file
   // doesn't consist solely of the disabled regression test below.
   @Test
   void removeTheme_orgOwnedTheme_removedFromSetAndSelectionReset() {
      CustomTheme ownedTheme = new CustomTheme();
      ownedTheme.setId("owned-1");
      ownedTheme.setOrgID("deletedOrg");
      ownedTheme.setOrganizations(new ArrayList<>());

      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(ownedTheme)));

      service.removeTheme("deletedOrg");

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Set<CustomTheme>> captor = ArgumentCaptor.forClass(Set.class);
      verify(manager).setCustomThemes(captor.capture());

      assertTrue(captor.getValue().isEmpty(), "the org-owned theme itself must be removed");
      verify(manager).setOrgSelectedTheme("default", "deletedOrg");
   }

   // Issue #75739 (fixed)
   @Test
   void removeTheme_globalThemeStillListsDeletedOrg_organizationsEntryIsStripped() {
      CustomTheme globalTheme = new CustomTheme();
      globalTheme.setId("global-1");
      globalTheme.setOrgID(null);
      globalTheme.setOrganizations(new ArrayList<>(List.of("deletedOrg", "otherOrg")));

      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(globalTheme)));

      service.removeTheme("deletedOrg");

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Set<CustomTheme>> captor = ArgumentCaptor.forClass(Set.class);
      verify(manager).setCustomThemes(captor.capture());

      CustomTheme saved = captor.getValue().iterator().next();
      assertFalse(saved.getOrganizations().contains("deletedOrg"),
         "deleting an organization must strip its ID from every globally-shared theme's "
         + "organizations list, not just the org-owned themes -- otherwise a future organization "
         + "that reuses this same ID silently inherits the stale selection (Issue #75739)");
      assertTrue(saved.getOrganizations().contains("otherOrg"),
         "an unrelated organization's own membership must survive the cleanup");
   }
}
