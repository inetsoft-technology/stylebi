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
 * Bug #77345: with security off, DashboardController (since Bug #74247) stores every composed
 * dashboard in USER_SCOPE owned by anonymous with a null org. The branch in
 * AbstractAssetEngine.setSheet meant to copy such a dashboard's bookmarks as its anonymous owner
 * compared a String to an IdentityID and never fired, so overwriteBookmarks ran as the saving
 * virtual admin: a re-save threw "Invalid entry found" and a Save As to GLOBAL threw an NPE, and
 * in both cases the sheet was not written. The owner's bookmarks are read as the owner and written
 * back as the owner only to the owner's own private asset; any other target gets them under the
 * saver, since a null-org owner key on a global asset is unreadable and never cleaned up. Uses the
 * real asset repository with security off.
 */

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
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
class ComposedDashboardBookmarkSaveTest {
   private static final String OWNER_BOOKMARK = "Bug77345OwnerBookmark";
   private static final String FOLDER = "Bug77345";
   private AssetRepository repository;
   private String orgId;
   private SRPrincipal admin;
   private IdentityID owner;
   private AssetEntry dashboardEntry;
   private final List<AssetEntry> cleanup = new ArrayList<>();
   private AssetEntry createdFolder;

   @BeforeEach
   void setUp() throws Exception {
      assertFalse(SecurityEngine.getSecurity().isSecurityEnabled(), "test requires security off");
      repository = AssetUtil.getAssetRepository(false);
      orgId = Organization.getDefaultOrganizationID();
      // the owner DashboardController.getIdentity() builds with security off (null org)
      owner = new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.USER).getIdentityID();
      assertNull(owner.getOrgID());
      admin = principal(new IdentityID("admin", orgId));
      dashboardEntry = createDashboard(owner, true);
   }

   @Test
   void virtualAdminResavesAnonymousOwnedComposedDashboard() throws Exception {
      Viewsheet vs = openAndEdit(dashboardEntry);

      assertDoesNotThrow(() -> repository.setSheet(dashboardEntry, vs, admin, true));

      assertPersisted(dashboardEntry);
      assertTrue(repository.getVSBookmark(dashboardEntry, new XPrincipal(owner), true)
                    .containsBookmark(OWNER_BOOKMARK), "owner's bookmark must be kept");
   }

   @Test
   void virtualAdminSavesAnonymousOwnedComposedDashboardAsGlobal() throws Exception {
      Viewsheet vs = openAndEdit(dashboardEntry);
      AssetEntry target = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                         globalFolder().getPath() + "/" + dashboardEntry.getName(),
                                         null, orgId);
      cleanup.add(target);

      // the test principal is not a logged-in session, so the global folder WRITE check would
      // deny it; the permission check is not under test here, the bookmark copy is
      assertDoesNotThrow(() -> saveIgnoringPermission(target, vs, admin));

      assertPersisted(target);
      // a global asset's bookmarks are looked up per viewer, so the copy must be under the saver
      assertTrue(repository.getVSBookmark(target, admin, true).containsBookmark(OWNER_BOOKMARK),
                 "owner's bookmark must be copied to the saver on the global asset");
      assertFalse(repository.getVSBookmark(target, new XPrincipal(owner), true)
                     .containsBookmark(OWNER_BOOKMARK),
                  "nothing may be written under the unreadable null-org owner key");

      repository.removeSheet(target, null, true);
      cleanup.remove(target);

      assertFalse(repository.getVSBookmark(target, admin, true).containsBookmark(OWNER_BOOKMARK),
                  "removing the global asset must remove the copied bookmark");
      assertFalse(repository.getVSBookmark(target, new XPrincipal(owner), true)
                     .containsBookmark(OWNER_BOOKMARK),
                  "no orphaned owner-keyed bookmark may remain after removal");
   }

   @Test
   void virtualAdminSavesAnonymousOwnedComposedDashboardAsOwnPrivateAsset() throws Exception {
      Viewsheet vs = openAndEdit(dashboardEntry);
      AssetEntry target = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                                         dashboardEntry.getName() + "Mine", admin.getIdentityID(),
                                         orgId);
      cleanup.add(target);

      assertDoesNotThrow(() -> saveIgnoringPermission(target, vs, admin));

      assertPersisted(target);
      assertTrue(repository.getVSBookmark(target, admin, true).containsBookmark(OWNER_BOOKMARK),
                 "owner's bookmark must be copied to admin's own private asset");
   }

   @Test
   void virtualAdminResavesPreBug74247AnonymousOwnedComposedDashboard() throws Exception {
      // before Bug #74247 the security-off owner was the real anonymous user, anonymous@host-org
      AssetEntry entry = createDashboard(new IdentityID(XPrincipal.ANONYMOUS, orgId), true);
      Viewsheet vs = openAndEdit(entry);

      assertDoesNotThrow(() -> repository.setSheet(entry, vs, admin, true));

      assertPersisted(entry);
      assertTrue(repository.getVSBookmark(entry, new XPrincipal(entry.getUser()), true)
                    .containsBookmark(OWNER_BOOKMARK), "owner's bookmark must be kept");
   }

   @Test
   void anonymousOwnerResavesOwnComposedDashboard() throws Exception {
      // saver == owner is excluded from the branch and keeps the ordinary owner path
      IdentityID hostAnonymous = new IdentityID(XPrincipal.ANONYMOUS, orgId);
      AssetEntry entry = createDashboard(hostAnonymous, true);
      SRPrincipal anonymous = principal(hostAnonymous);
      Viewsheet vs = openAndEdit(entry, anonymous);

      assertDoesNotThrow(() -> repository.setSheet(entry, vs, anonymous, true));

      assertPersisted(entry);
      assertTrue(repository.getVSBookmark(entry, anonymous, true).containsBookmark(OWNER_BOOKMARK),
                 "owner's bookmark must be kept");
   }

   @Test
   void virtualAdminResavesAnonymousOwnedNonComposedViewsheet() throws Exception {
      // the setSheet branch is scoped to composed dashboards, but with security off every
      // anonymous-owned viewsheet's bookmarks are readable and writable by the caller under the
      // owner key (Bug #77357), so the ordinary overwriteBookmarks path keeps them too
      AssetEntry entry = createDashboard(owner, false);
      Viewsheet vs = openAndEdit(entry);

      assertDoesNotThrow(() -> repository.setSheet(entry, vs, admin, true));

      assertPersisted(entry);
      assertTrue(repository.getVSBookmark(entry, new XPrincipal(owner), true)
                    .containsBookmark(OWNER_BOOKMARK), "owner's bookmark must be kept");
   }

   @Test
   void adminResavesOtherUsersNonComposedViewsheetKeepsOwnerBookmarks() throws Exception {
      // an ordinary viewsheet owned by another (non-anonymous) user is outside both the
      // composed-dashboard branch and the security-off anonymous-owner rule, so the source
      // bookmarks are read as the saving admin, who cannot see them. The copy is skipped instead
      // of failing the save, and the owner's bookmarks are left untouched (Bug #77363)
      IdentityID user = new IdentityID("Bug77345User", orgId);
      AssetEntry entry = createDashboard(user, false);
      Viewsheet vs = openAndEdit(entry);

      assertDoesNotThrow(() -> repository.setSheet(entry, vs, admin, true));

      assertPersisted(entry);
      assertTrue(repository.getVSBookmark(entry, new XPrincipal(user), true)
                    .containsBookmark(OWNER_BOOKMARK), "owner's bookmark must be kept");
   }

   private AssetEntry createDashboard(IdentityID dashboardOwner, boolean composed)
      throws Exception
   {
      String name = "Bug77345Dashboard" + System.nanoTime();
      AssetEntry created = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                                          name, dashboardOwner, orgId);
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setComposedDashboard(composed);
      repository.setSheet(created, vs, admin, true);

      // reopen from the stored identifier, as the composer does
      AssetEntry entry = AssetEntry.createAssetEntry(created.toIdentifier());
      cleanup.add(entry);

      // the only principal a USER_SCOPE viewsheet's bookmarks can be keyed by is its owner
      VSBookmark bookmark = new VSBookmark();
      bookmark.setUser(dashboardOwner);
      bookmark.addBookmark(OWNER_BOOKMARK, vs, VSBookmarkInfo.ALLSHARE, false, false);
      repository.setVSBookmark(entry, bookmark, new XPrincipal(dashboardOwner));
      return entry;
   }

   private AssetEntry globalFolder() throws Exception {
      AssetEntry folder = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                         AssetEntry.Type.REPOSITORY_FOLDER, FOLDER, null, orgId);

      if(!repository.containsEntry(folder)) {
         repository.addFolder(folder, null);
         createdFolder = folder;
      }

      return folder;
   }

   private void saveIgnoringPermission(AssetEntry target, Viewsheet vs, SRPrincipal user)
      throws Exception
   {
      AssetRepository.IGNORE_PERM.set(true);

      try {
         repository.setSheet(target, vs, user, true);
      }
      finally {
         AssetRepository.IGNORE_PERM.remove();
      }
   }

   private void assertPersisted(AssetEntry entry) throws Exception {
      Viewsheet saved = (Viewsheet) repository.getSheet(entry, admin, false, AssetContent.ALL);
      assertNotNull(saved.getAssembly("Bug77345Text"), "save must be persisted");
   }

   private Viewsheet openAndEdit(AssetEntry entry) throws Exception {
      return openAndEdit(entry, admin);
   }

   private Viewsheet openAndEdit(AssetEntry entry, SRPrincipal user) throws Exception {
      Viewsheet vs = (Viewsheet) repository.getSheet(entry, user, false, AssetContent.ALL);
      // RuntimeViewsheet.setEntry does this for an opened viewsheet
      vs.setRuntimeEntry(entry);
      TextVSAssembly text = new TextVSAssembly(vs, "Bug77345Text");
      vs.addAssembly(text);
      return vs;
   }

   // a real admin session is in SecurityEngine's logged-in map; admin-of-owner checks on another
   // user's private asset now ask SecurityEngine, which refuses a principal that is not (Bug #78075)
   private static SRPrincipal principal(IdentityID id) {
      SRPrincipal principal = new SRPrincipal(
         id, new IdentityID[] { new IdentityID("Administrator", null) }, new String[0],
         id.getOrgID(), 1L);
      principal.setIgnoreLogin(true);
      return principal;
   }

   @AfterEach
   void tearDown() throws Exception {
      for(AssetEntry entry : cleanup) {
         if(repository.containsEntry(entry)) {
            repository.removeSheet(entry, null, true);
         }
      }

      cleanup.clear();

      if(createdFolder != null && repository.containsEntry(createdFolder)) {
         repository.removeFolder(createdFolder, null, true);
      }

      createdFolder = null;
   }
}
