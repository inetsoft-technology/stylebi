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
package inetsoft.web.portal.controller;

import inetsoft.sree.internal.SUtil;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #78491: the favorites endpoints with host organization viewsheets shared with every
 * organization. The host organization's own users still toggle their favorites, and a user of
 * another organization, who sees those sheets read-only and is not offered Add or Remove
 * Favorites on them, changes neither a shared viewsheet nor a shared snapshot.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FavoritesRepositoryEntryControllerSharedOrgTest {
   @BeforeEach
   void setUp() throws Exception {
      f = new FavoritesTestSupport();
      jane = f.jane.getName();
      bob = f.bob.getName();
   }

   @AfterEach
   void tearDown() {
      f.close();
   }

   @Test
   @Timeout(60)
   void hostOrganizationUserTogglesASharedViewsheet() throws Exception {
      f.folder("R78491G");
      AssetEntry vs = f.viewsheet("R78491G/VS", null);
      f.seed(vs, jane);

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS)) {
         sutil.when(() -> SUtil.isDefaultVSGloballyVisible(any(Principal.class))).thenReturn(true);
         sutil.when(SUtil::isDefaultVSGloballyVisible).thenReturn(true);
         String request = f.treeJson("R78491G", vs, f.bob);

         MockHttpServletResponse added = f.send("add", f.bob, request);
         assertEquals(200, added.getStatus(), added.getContentAsString());
         assertEquals(List.of(jane, bob), f.stored(vs).getFavoritesUsers());

         MockHttpServletResponse removed = f.send("remove", f.bob, request);
         assertEquals(200, removed.getStatus(), removed.getContentAsString());
         assertEquals(List.of(jane), f.stored(vs).getFavoritesUsers());
      }
   }

   @Test
   @Timeout(60)
   void anotherOrganizationChangesNeitherASharedSnapshotNorAStaleFavorite() throws Exception {
      f.folder("R78491K");
      AssetEntry vs = f.viewsheet("R78491K/VS", null);
      AssetEntry snap = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.VIEWSHEET_SNAPSHOT, "R78491K/Snap", null, f.orgId);
      f.rep.setSheet(snap, new Viewsheet(), null, true);
      Principal eve = FavoritesTestSupport.user("eve78491", "org78491");
      // a name the host organization's list already holds for eve, e.g. from before the fix
      f.seed(vs, jane, eve.getName());
      f.seed(snap, jane);
      String vsRequest = f.treeJson("R78491K", vs, f.bob);
      String snapRequest = f.treeJson("R78491K", snap, f.bob);

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS)) {
         sutil.when(() -> SUtil.isDefaultVSGloballyVisible(any(Principal.class))).thenReturn(true);
         sutil.when(SUtil::isDefaultVSGloballyVisible).thenReturn(true);

         MockHttpServletResponse removed = f.send("remove", eve, vsRequest);
         assertEquals(500, removed.getStatus(), removed.getContentAsString());
         MockHttpServletResponse added = f.send("add", eve, snapRequest);
         assertEquals(500, added.getStatus(), added.getContentAsString());
      }

      assertEquals(List.of(jane, eve.getName()), f.stored(vs).getFavoritesUsers());
      assertEquals(List.of(jane), f.stored(snap).getFavoritesUsers());
      assertTrue(f.rep.containsEntry(vs));
      assertTrue(f.rep.containsEntry(snap));
   }

   private FavoritesTestSupport f;
   private String jane, bob;
}
