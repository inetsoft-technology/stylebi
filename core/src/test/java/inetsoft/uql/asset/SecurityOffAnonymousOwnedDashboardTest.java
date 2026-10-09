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

/*
 * Bug #77357: with security off, DashboardController stores every composed dashboard in
 * USER_SCOPE owned by anonymous, with a null org since Bug #74247 (anonymous~;~__GLOBAL__) and
 * with the default org before it and after this fix. A USER_SCOPE viewsheet's bookmarks and its
 * checkUserAsset owner check matched only the exact owner, so the security-off anonymous user
 * (anonymous@host-org) got "Read access denied" opening a null-org owned dashboard, and neither it
 * nor the virtual admin could list or add bookmarks. With security off, an anonymous-owned
 * viewsheet is now treated as owned by the caller, and its bookmarks stay under the stored owner.
 *
 * [security off][null-org or default-org owner][anonymous or admin]  open (READ/WRITE), list and
 *                                                                    add bookmarks, one shared set
 * [security off][sheet removed]                                      owner-keyed bookmarks removed
 * [security on][null-org owner]                                      still owner-only (control)
 * [security off][non-anonymous owner]                                still owner-only (control)
 *
 * Uses the real asset repository, as ComposedDashboardBookmarkSaveTest does.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SecurityOffAnonymousOwnedDashboardTest {
   private static final String OWNER_BOOKMARK = "Bug77357OwnerBookmark";
   private static final String ADDED_BOOKMARK = "Bug77357AddedBookmark";
   private AssetRepository repository;
   private String orgId;
   private SRPrincipal admin;
   private SRPrincipal anonymous;
   private final List<AssetEntry> cleanup = new ArrayList<>();

   @BeforeEach
   void setUp() {
      assertFalse(SecurityEngine.getSecurity().isSecurityEnabled(), "test requires security off");
      repository = AssetUtil.getAssetRepository(false);
      orgId = Organization.getDefaultOrganizationID();
      admin = new SRPrincipal(new IdentityID("admin", orgId),
                              new IdentityID[] { new IdentityID("Administrator", null) },
                              new String[0], orgId, 1L);
      // a real admin session is in SecurityEngine's logged-in map; admin-of-owner checks on
      // another user's private asset now ask SecurityEngine, which refuses a principal that is
      // not (Bug #78075)
      admin.setIgnoreLogin(true);
      // the security-off anonymous principal has no roles (SecurityEngine.authenticate)
      anonymous = new SRPrincipal(new IdentityID(XPrincipal.ANONYMOUS, orgId), new IdentityID[0],
                                  new String[0], orgId, 1L);
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void anonymousOpensAnonymousOwnedDashboard(boolean nullOrgOwner) throws Exception {
      AssetEntry entry = createDashboard(anonymousOwner(nullOrgOwner));

      assertOpens(entry, anonymous);
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void adminOpensAnonymousOwnedDashboard(boolean nullOrgOwner) throws Exception {
      AssetEntry entry = createDashboard(anonymousOwner(nullOrgOwner));

      assertOpens(entry, admin);
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void anonymousListsAndAddsBookmarks(boolean nullOrgOwner) throws Exception {
      IdentityID owner = anonymousOwner(nullOrgOwner);
      AssetEntry entry = createDashboard(owner);

      assertListsAndAdds(entry, owner, anonymous, admin);
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void adminListsAndAddsBookmarks(boolean nullOrgOwner) throws Exception {
      IdentityID owner = anonymousOwner(nullOrgOwner);
      AssetEntry entry = createDashboard(owner);

      assertListsAndAdds(entry, owner, admin, anonymous);
   }

   @Test
   void removingDashboardRemovesSharedBookmarks() throws Exception {
      IdentityID owner = anonymousOwner(true);
      AssetEntry entry = createDashboard(owner);
      addBookmark(entry, admin);

      repository.removeSheet(entry, null, true);
      cleanup.remove(entry);

      assertFalse(repository.getVSBookmark(entry, new XPrincipal(owner), true)
                     .containsBookmark(ADDED_BOOKMARK),
                  "bookmarks are kept under the owner, which clearVSBookmark removes");
   }

   @Test
   void securityOnKeepsOwnerOnlyBookmarksAndAccess() throws Exception {
      AssetEntry entry = createDashboard(anonymousOwner(true));
      String enabled = SreeEnv.getProperty("security.enabled");
      SreeEnv.setProperty("security.enabled", "true");

      try {
         assertTrue(SecurityEngine.getSecurity().isSecurityEnabled());
         assertNull(repository.getVSBookmark(entry, anonymous, true));
         assertNull(repository.getVSBookmark(entry, admin, true));
         assertThrows(RuntimeException.class, () -> addBookmark(entry, admin));
         assertThrows(Exception.class, () -> repository.checkAssetPermission(
            anonymous, entry, ResourceAction.READ, true));
      }
      finally {
         SreeEnv.setProperty("security.enabled", enabled == null ? "false" : enabled);
      }
   }

   @Test
   void nonAnonymousOwnerKeepsOwnerOnlyBookmarksAndAccess() throws Exception {
      AssetEntry entry = createDashboard(new IdentityID("Bug77357User", orgId));

      assertNull(repository.getVSBookmark(entry, anonymous, true));
      assertNull(repository.getVSBookmark(entry, admin, true));
      assertThrows(RuntimeException.class, () -> addBookmark(entry, admin));
      assertThrows(Exception.class, () -> repository.checkAssetPermission(
         anonymous, entry, ResourceAction.READ, true));
      assertThrows(Exception.class, () -> repository.checkAssetPermission(
         anonymous, entry, ResourceAction.WRITE, true));
   }

   private IdentityID anonymousOwner(boolean nullOrg) {
      // the owner DashboardController.getIdentity() built with security off since Bug #74247
      // (null org), or the default-org owner it builds before that bug and after this one
      IdentityID owner = nullOrg ?
         new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.USER).getIdentityID() :
         new IdentityID(XPrincipal.ANONYMOUS, orgId);
      assertEquals(nullOrg, owner.getOrgID() == null);
      return owner;
   }

   private void assertOpens(AssetEntry entry, SRPrincipal user) {
      // WorksheetEngine.openSheet: getSheet with the READ owner check, then a WRITE owner check
      // that decides whether the sheet is editable
      assertDoesNotThrow(() -> repository.getSheet(entry, user, true, AssetContent.ALL));
      assertDoesNotThrow(() -> repository.checkAssetPermission(
         user, entry, ResourceAction.READ, true));
      assertDoesNotThrow(() -> repository.checkAssetPermission(
         user, entry, ResourceAction.WRITE, true));
   }

   private void assertListsAndAdds(AssetEntry entry, IdentityID owner, SRPrincipal user,
                                   SRPrincipal other) throws Exception
   {
      VSBookmark bookmark = repository.getVSBookmark(entry, user, true);
      assertNotNull(bookmark, "caller must get the dashboard's bookmarks");
      assertTrue(bookmark.containsBookmark(OWNER_BOOKMARK), "owner's bookmark must be listed");

      assertDoesNotThrow(() -> addBookmark(entry, user));

      assertTrue(repository.getVSBookmark(entry, new XPrincipal(owner), true)
                    .containsBookmark(ADDED_BOOKMARK), "bookmark must be stored under the owner");
      assertTrue(repository.getVSBookmark(entry, other, true).containsBookmark(ADDED_BOOKMARK),
                 "security-off users share one bookmark set");
   }

   private void addBookmark(AssetEntry entry, SRPrincipal user) throws Exception {
      Viewsheet vs = (Viewsheet) repository.getSheet(entry, admin, false, AssetContent.ALL);
      VSBookmark bookmark = repository.getVSBookmark(entry, user, true);
      bookmark = bookmark == null ? new VSBookmark() : bookmark;
      bookmark.addBookmark(ADDED_BOOKMARK, vs, VSBookmarkInfo.ALLSHARE, false, false);
      repository.setVSBookmark(entry, bookmark, user);
   }

   private AssetEntry createDashboard(IdentityID owner) throws Exception {
      String name = "Bug77357Dashboard" + System.nanoTime();
      AssetEntry created = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                                          name, owner, orgId);
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setComposedDashboard(true);
      repository.setSheet(created, vs, admin, true);

      // reopen from the stored identifier, as the viewer does
      AssetEntry entry = AssetEntry.createAssetEntry(created.toIdentifier());
      cleanup.add(entry);

      VSBookmark bookmark = new VSBookmark();
      bookmark.setUser(owner);
      bookmark.addBookmark(OWNER_BOOKMARK, vs, VSBookmarkInfo.ALLSHARE, false, false);
      repository.setVSBookmark(entry, bookmark, new XPrincipal(owner));
      return entry;
   }

   @AfterEach
   void tearDown() throws Exception {
      for(AssetEntry entry : cleanup) {
         if(repository.containsEntry(entry)) {
            repository.removeSheet(entry, null, true);
         }
      }

      cleanup.clear();
   }
}
