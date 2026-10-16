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

import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
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
import java.util.*;

import static inetsoft.web.portal.controller.FavoritesTestSupport.MAPPER;
import static inetsoft.web.portal.controller.FavoritesTestSupport.properties;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #78491: the portal favorites endpoints built the entry to store from the client's entry.
 * That erased every other user's favorites, and since the change skipped the WRITE and DELETE
 * checks, a request could move or rename a viewsheet and set its properties. These tests run
 * the endpoints on the real asset engine and read the result back from the indexed storage.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FavoritesRepositoryEntryControllerStorageTest {
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
   void addAndRemoveKeepTheOtherUsersFavorites() throws Exception {
      f.folder("R78491A");
      AssetEntry vs = f.viewsheet("R78491A/VS", null);
      f.seed(vs, jane);

      assertEquals(200, f.send("add", f.bob, f.treeJson("R78491A", vs, f.bob)).getStatus());
      assertEquals(List.of(jane, bob), f.stored(vs).getFavoritesUsers());

      assertEquals(200, f.send("remove", f.bob, f.treeJson("R78491A", vs, f.bob)).getStatus());
      assertEquals(List.of(jane), f.stored(vs).getFavoritesUsers());
   }

   @Test
   @Timeout(60)
   void aliasedViewsheetIgnoresAStaleRequest() throws Exception {
      f.folder("R78491L");
      AssetEntry vs = f.viewsheet("R78491L/VS", "My Alias");
      f.seed(vs, jane);

      assertEquals(200, f.send("add", f.bob, f.treeJson("R78491L", vs, f.bob)).getStatus());
      assertEquals(List.of(jane, bob), f.stored(vs).getFavoritesUsers());
      assertEquals("My Alias", f.stored(vs).getAlias());

      // bob's tree was read before jane removed hers; it still lists jane
      String stale = f.treeJson("R78491L", vs, f.bob);
      assertEquals(200, f.send("remove", f.jane, f.treeJson("R78491L", vs, f.jane)).getStatus());
      assertEquals(List.of(bob), f.stored(vs).getFavoritesUsers());

      assertEquals(200, f.send("remove", f.bob, stale).getStatus());
      // jane's removal is not undone by the stale list
      assertEquals(List.of(), f.stored(vs).getFavoritesUsers());
      assertEquals("My Alias", f.stored(vs).getAlias());
   }

   @Test
   @Timeout(60)
   void folderKeepsTheOtherUsersFavoritesOnItsViewsheets() throws Exception {
      f.folder("R78491F");
      f.folder("R78491F/Sub");
      AssetEntry c1 = f.viewsheet("R78491F/Sub/C1", null);
      AssetEntry c2 = f.viewsheet("R78491F/Sub/C2", "C2 Alias");
      f.seed(c1, jane);
      f.seed(c2, jane);

      assertEquals(200, f.send("add", f.bob, f.folderJson("R78491F/Sub")).getStatus());
      assertEquals(List.of(jane, bob), f.stored(c1).getFavoritesUsers());
      assertEquals(List.of(jane, bob), f.stored(c2).getFavoritesUsers());

      assertEquals(200, f.send("remove", f.bob, f.folderJson("R78491F/Sub")).getStatus());
      assertEquals(List.of(jane), f.stored(c1).getFavoritesUsers());
      assertEquals(List.of(jane), f.stored(c2).getFavoritesUsers());
   }

   @Test
   @Timeout(60)
   void folderWithoutReadIsRefused() throws Exception {
      f.folder("R78491G");
      AssetEntry vs = f.viewsheet("R78491G/VS", null);
      f.seed(vs, jane);
      f.deny(f.bob, ResourceType.REPORT, "R78491G", ResourceAction.READ);

      for(String action : new String[] { "add", "remove" }) {
         MockHttpServletResponse response = f.send(action, f.bob, f.folderJson("R78491G"));

         assertEquals(500, response.getStatus(), action);
         assertTrue(response.getContentAsString().contains("Read access denied"),
                    action + ": " + response.getContentAsString());
         assertEquals(List.of(jane), f.stored(vs).getFavoritesUsers(), action);
      }
   }

   @Test
   @Timeout(60)
   void forgedRequestChangesOnlyTheUsersOwnFavorite() throws Exception {
      for(String alias : new String[] { null, "My Alias" }) {
         String suffix = alias == null ? "N" : "Y";
         String folder = "R78491M" + suffix;
         f.folder(folder);
         f.folder("R78491O" + suffix);
         AssetEntry vs = f.viewsheet(folder + "/VS", alias);
         f.seed(vs, jane);

         ObjectNode event = (ObjectNode) MAPPER.readTree(f.treeJson(folder, vs, f.bob));
         // listing the tree sets server-side properties on the engine's entry, so compare
         // with the server's entry as the tree left it
         Map<String, String> before = properties(f.stored(vs));
         ObjectNode model = (ObjectNode) event.get("entry");
         ObjectNode asset = (ObjectNode) model.get("entry");
         String stolen = "R78491O" + suffix + "/Stolen";
         model.put("path", stolen);
         asset.put("favoritesUser", "mallory");

         // a forged alias on an unaliased sheet would only take the aliased branch
         if(alias != null) {
            asset.put("alias", "Hacked");
         }

         ObjectNode props = (ObjectNode) asset.get("properties");
         props.put("injected", "evil");
         props.put("onReport", "false");
         props.put("localStr", "Hacked");

         assertEquals(200, f.send("add", f.bob, MAPPER.writeValueAsString(event)).getStatus(),
                      "alias " + alias);

         AssetEntry stolenEntry = new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, stolen, null, f.orgId);
         assertTrue(f.rep.containsEntry(vs), "moved, alias " + alias);
         assertFalse(f.rep.containsEntry(stolenEntry), "moved, alias " + alias);
         AssetEntry after = f.stored(vs);
         assertEquals(List.of(jane, bob), after.getFavoritesUsers(), "alias " + alias);
         assertEquals(alias, after.getAlias());
         assertNull(after.getProperty("injected"));
         assertNotEquals("false", after.getProperty("onReport"));
         assertNotEquals("Hacked", after.getProperty("localStr"));
         assertEquals(before, properties(after), "alias " + alias);
      }
   }

   @Test
   @Timeout(60)
   void forgedEntryOfAnotherUsersPrivateViewsheetIsRefused() throws Exception {
      f.folder("R78491P");
      AssetEntry visible = f.viewsheet("R78491P/VS", null);
      AssetEntry priv = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                                       "Priv78491", f.jane.getIdentityID(), f.orgId);
      f.rep.setSheet(priv, new Viewsheet(), null, true);
      f.seed(priv, jane);
      // bob is not jane and can't administer her
      f.deny(f.bob, ResourceType.SECURITY_USER, jane, ResourceAction.ADMIN);

      ObjectNode event = (ObjectNode) MAPPER.readTree(f.treeJson("R78491P", visible, f.bob));
      ObjectNode model = (ObjectNode) event.get("entry");
      ((ObjectNode) model.get("entry")).put("identifier", priv.toIdentifier());
      model.put("path", SUtil.MY_REPORT + "/Renamed78491");

      MockHttpServletResponse response = f.send("add", f.bob, MAPPER.writeValueAsString(event));

      assertEquals(500, response.getStatus());
      assertTrue(response.getContentAsString().contains("Read access denied"),
                 response.getContentAsString());
      assertTrue(f.rep.containsEntry(priv));
      assertFalse(f.rep.containsEntry(new AssetEntry(
         AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET, "Renamed78491",
         f.jane.getIdentityID(), f.orgId)));
      assertEquals(List.of(jane), f.stored(priv).getFavoritesUsers());
   }

   @Test
   @Timeout(60)
   void viewsheetWithoutReadIsRefused() throws Exception {
      f.folder("R78491R");
      AssetEntry vs = f.viewsheet("R78491R/VS", null);
      f.seed(vs, jane);
      String request = f.treeJson("R78491R", vs, f.bob);
      f.deny(f.bob, ResourceType.REPORT, "R78491R/VS", ResourceAction.READ);

      MockHttpServletResponse response = f.send("add", f.bob, request);

      assertEquals(500, response.getStatus());
      assertTrue(response.getContentAsString().contains("Read access denied"),
                 response.getContentAsString());
      assertEquals(List.of(jane), f.stored(vs).getFavoritesUsers());
   }

   @Test
   @Timeout(60)
   void missingViewsheetIsAnError() throws Exception {
      AssetEntry missing = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "R78491Z/Nope", null, f.orgId);

      for(String action : new String[] { "add", "remove" }) {
         MockHttpServletResponse response = f.send(action, f.bob, f.missingJson(missing));

         assertEquals(500, response.getStatus(), action);
         assertTrue(response.getContentAsString().contains("Sheet not found"),
                    action + ": " + response.getContentAsString());
         assertFalse(f.rep.containsEntry(missing), action);
      }
   }

   @Test
   @Timeout(60)
   void forgedWorksheetIsNotChanged() throws Exception {
      f.folder("R78491K");
      AssetEntry visible = f.viewsheet("R78491K/VS", null);
      AssetEntry ws = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "WS78491", null, f.orgId);
      f.rep.setSheet(ws, new inetsoft.uql.asset.Worksheet(), null, true);

      ObjectNode event = (ObjectNode) MAPPER.readTree(f.treeJson("R78491K", visible, f.bob));
      ((ObjectNode) event.get("entry").get("entry")).put("identifier", ws.toIdentifier());

      MockHttpServletResponse response = f.send("add", f.bob, MAPPER.writeValueAsString(event));

      assertEquals(500, response.getStatus());
      assertTrue(response.getContentAsString().contains("Sheet not found"),
                 response.getContentAsString());
      assertEquals(List.of(), f.rep.getAssetEntry(ws).getFavoritesUsers());
   }

   /**
    * StyleBI: a host-organization viewsheet that is exposed read-only to the other organizations
    * isn't offered for favorites in their portal, and a request from another organization must
    * not write to the host organization's storage.
    */
   @Test
   @Timeout(60)
   void sharedHostViewsheetIsNotChangedFromAnotherOrganization() throws Exception {
      f.folder("R78491H");
      AssetEntry vs = f.viewsheet("R78491H/VS", null);
      f.seed(vs, jane);
      String request = f.treeJson("R78491H", vs, f.bob);
      Principal other = FavoritesTestSupport.user("eve78491", "org78491");

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS)) {
         sutil.when(() -> SUtil.isDefaultVSGloballyVisible(any(Principal.class))).thenReturn(true);
         MockHttpServletResponse response = f.send("add", other, request);

         assertEquals(500, response.getStatus(), response.getContentAsString());
      }

      assertEquals(List.of(jane), f.stored(vs).getFavoritesUsers());
      assertTrue(f.rep.containsEntry(vs));
   }

   private FavoritesTestSupport f;
   private String jane, bob;
}
