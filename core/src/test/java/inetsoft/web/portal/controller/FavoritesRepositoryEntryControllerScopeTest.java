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
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78491: the favorites endpoints on the real asset engine for a private (My Dashboards)
 * viewsheet, a snapshot, nested folders, a folder with one unreadable child, and repeated or
 * no-op toggles. Results are read back from the indexed storage.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FavoritesRepositoryEntryControllerScopeTest {
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
   void ownPrivateViewsheetCanBeMarkedAndUnmarked() throws Exception {
      AssetEntry mine = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                                       "Mine78491", f.jane.getIdentityID(), f.orgId);
      f.rep.setSheet(mine, new Viewsheet(), null, true);
      // an administrator who can see jane's dashboards had marked it
      String other = new IdentityID("admin78491", f.orgId).convertToKey();
      f.seed(mine, other);

      assertEquals(200, f.send("add", f.jane, f.treeJson(SUtil.MY_REPORT, mine, f.jane))
         .getStatus());
      assertEquals(List.of(other, jane), f.stored(mine).getFavoritesUsers());
      assertTrue(f.rep.containsEntry(mine));

      assertEquals(200, f.send("remove", f.jane, f.treeJson(SUtil.MY_REPORT, mine, f.jane))
         .getStatus());
      assertEquals(List.of(other), f.stored(mine).getFavoritesUsers());
      assertTrue(f.rep.containsEntry(mine));
   }

   @Test
   @Timeout(60)
   void snapshotKeepsTheOtherUsersFavorites() throws Exception {
      f.folder("R78491S");
      AssetEntry snap = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.VIEWSHEET_SNAPSHOT, "R78491S/Snap", null, f.orgId);
      f.rep.setSheet(snap, new Viewsheet(), null, true);
      f.seed(snap, jane);
      String request = f.treeJson("R78491S", snap, f.bob);

      assertEquals(200, f.send("add", f.bob, request).getStatus());
      assertEquals(List.of(jane, bob), f.stored(snap).getFavoritesUsers());
      assertEquals(AssetEntry.Type.VIEWSHEET_SNAPSHOT, f.stored(snap).getType());

      assertEquals(200, f.send("remove", f.bob, request).getStatus());
      assertEquals(List.of(jane), f.stored(snap).getFavoritesUsers());
   }

   @Test
   @Timeout(60)
   void nestedFolderReachesEveryLevel() throws Exception {
      f.folder("R78491T");
      f.folder("R78491T/A");
      f.folder("R78491T/A/B");
      List<AssetEntry> sheets = List.of(f.viewsheet("R78491T/Top", null),
                                        f.viewsheet("R78491T/A/Mid", null),
                                        f.viewsheet("R78491T/A/B/Deep", null));

      for(AssetEntry vs : sheets) {
         f.seed(vs, jane);
      }

      assertEquals(200, f.send("add", f.bob, f.folderJson("R78491T")).getStatus());

      for(AssetEntry vs : sheets) {
         assertEquals(List.of(jane, bob), f.stored(vs).getFavoritesUsers(), vs.getPath());
      }

      assertEquals(200, f.send("remove", f.bob, f.folderJson("R78491T")).getStatus());

      for(AssetEntry vs : sheets) {
         assertEquals(List.of(jane), f.stored(vs).getFavoritesUsers(), vs.getPath());
      }
   }

   @Test
   @Timeout(60)
   void folderSkipsAChildTheUserCannotRead() throws Exception {
      f.folder("R78491U");
      AssetEntry open = f.viewsheet("R78491U/Open", null);
      AssetEntry secret = f.viewsheet("R78491U/Secret", null);
      AssetEntry open2 = f.viewsheet("R78491U/Open2", null);

      for(AssetEntry vs : List.of(open, secret, open2)) {
         f.seed(vs, jane);
      }

      f.deny(f.bob, ResourceType.REPORT, "R78491U/Secret", ResourceAction.READ);

      assertEquals(200, f.send("add", f.bob, f.folderJson("R78491U")).getStatus());
      assertEquals(List.of(jane, bob), f.stored(open).getFavoritesUsers());
      assertEquals(List.of(jane, bob), f.stored(open2).getFavoritesUsers());
      assertEquals(List.of(jane), f.stored(secret).getFavoritesUsers());

      assertEquals(200, f.send("remove", f.bob, f.folderJson("R78491U")).getStatus());
      assertEquals(List.of(jane), f.stored(open).getFavoritesUsers());
      assertEquals(List.of(jane), f.stored(open2).getFavoritesUsers());
      assertEquals(List.of(jane), f.stored(secret).getFavoritesUsers());
   }

   @Test
   @Timeout(60)
   void removeByAUserNotInTheListChangesNothing() throws Exception {
      f.folder("R78491V");
      AssetEntry vs = f.viewsheet("R78491V/VS", null);
      f.seed(vs, jane);

      assertEquals(200, f.send("remove", f.bob, f.treeJson("R78491V", vs, f.bob)).getStatus());
      assertEquals(List.of(jane), f.stored(vs).getFavoritesUsers());
   }

   @Test
   @Timeout(60)
   void addingTwiceDoesNotDuplicate() throws Exception {
      f.folder("R78491W");
      AssetEntry vs = f.viewsheet("R78491W/VS", null);
      f.seed(vs, jane);

      assertEquals(200, f.send("add", f.bob, f.treeJson("R78491W", vs, f.bob)).getStatus());
      assertEquals(200, f.send("add", f.bob, f.treeJson("R78491W", vs, f.bob)).getStatus());
      assertEquals(List.of(jane, bob), f.stored(vs).getFavoritesUsers());
   }

   private FavoritesTestSupport f;
   private String jane, bob;
}
