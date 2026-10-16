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
 * Bug #77363: with security on, a site admin can open another user's private viewsheet in the
 * composer (e.g. by composer ?vsId=) and save it. AbstractAssetEngine.setSheet copies the
 * bookmarks (overwriteBookmarks) before it persists the sheet, reading the source bookmarks as
 * the saver. A USER_SCOPE viewsheet's bookmarks are keyed by its owner only, so the read returned
 * null: a re-save then threw "Invalid entry found" and a Save As threw an NPE, and the save was
 * lost. The copy is now skipped when the saver cannot read the source bookmarks. No bookmark
 * access is broadened: an admin's Save As does not carry the owner's bookmarks. Uses the real
 * asset repository with security enabled through SecurityEngineOverrides.
 */

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class NonOwnerViewsheetBookmarkSaveTest {
   private static final String OWNER_BOOKMARK = "Bug77363OwnerBookmark";
   private static final String TEXT = "Bug77363Text";

   @Autowired
   SecurityEngineOverrides overrides;

   private AssetRepository repository;
   private String orgId;
   private IdentityID bobId;
   private SRPrincipal bob;
   private SRPrincipal admin;
   private AssetEntry bobEntry;
   private final List<AssetEntry> cleanup = new ArrayList<>();

   @BeforeEach
   void setUp() throws Exception {
      SecurityEngineOverrides.assertInstalled(SecurityEngine.getSecurity());
      overrides.setSecurityEnabled(true);
      assertTrue(SecurityEngine.getSecurity().isSecurityEnabled(), "test requires security on");

      repository = AssetUtil.getAssetRepository(false);
      orgId = Organization.getDefaultOrganizationID();
      bobId = new IdentityID("bob", orgId);
      bob = principal("bob");
      admin = principal("admin", "Administrator");
      assertTrue(OrganizationManager.getInstance().isSiteAdmin(admin), "admin must be site admin");
      bobEntry = createBobViewsheet();
   }

   @AfterEach
   void tearDown() throws Exception {
      try {
         runIgnoringPermission(() -> {
            for(AssetEntry entry : cleanup) {
               if(repository.containsEntry(entry)) {
                  repository.removeSheet(entry, null, true);
               }
            }
         });
      }
      finally {
         cleanup.clear();
         overrides.clear();
      }
   }

   @Test
   void siteAdminResavesOtherUsersPrivateViewsheet() throws Exception {
      Viewsheet vs = openAndEdit(bobEntry, admin, true);

      // the real WRITE check, as the composer save does
      assertDoesNotThrow(() -> repository.setSheet(bobEntry, vs, admin, true));

      assertPersisted(bobEntry);
      assertTrue(bobBookmarks(bobEntry).containsBookmark(OWNER_BOOKMARK),
                 "owner's bookmark must be kept");
   }

   @Test
   void siteAdminSavesOtherUsersPrivateViewsheetAsOwnPrivateAsset() throws Exception {
      Viewsheet vs = openAndEdit(bobEntry, admin, true);
      AssetEntry target = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                                         bobEntry.getName() + "Copy", admin.getIdentityID(),
                                         orgId);
      cleanup.add(target);

      assertDoesNotThrow(() -> repository.setSheet(target, vs, admin, true));

      assertPersisted(target);
      assertEquals(0, repository.getVSBookmark(target, admin, true).getBookmarks().length,
                   "the owner's bookmarks must not be copied to the admin");
      assertTrue(bobBookmarks(bobEntry).containsBookmark(OWNER_BOOKMARK),
                 "owner's bookmark must be kept on the source");
   }

   @Test
   void ownerSaveAsStillCopiesBookmarks() throws Exception {
      Viewsheet vs = openAndEdit(bobEntry, bob, false);
      AssetEntry target = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                                         bobEntry.getName() + "OwnerCopy", bobId, orgId);
      cleanup.add(target);

      // the test provider gives the unregistered user bob no My Dashboards permission; the owner
      // path does not depend on the permission check
      assertDoesNotThrow(() -> runIgnoringPermission(
         () -> repository.setSheet(target, vs, bob, true)));

      assertPersisted(target);
      assertTrue(repository.getVSBookmark(target, bob, true).containsBookmark(OWNER_BOOKMARK),
                 "owner's bookmark must follow the owner's Save As");
   }

   @Test
   void nonSiteAdminCannotOpenOtherUsersPrivateViewsheet() {
      SRPrincipal carol = principal("carol");
      assertFalse(OrganizationManager.getInstance().isSiteAdmin(carol));

      // the composer open (WorksheetEngine.openSheet) checks permission on private assets
      assertThrows(MessageException.class,
                   () -> repository.getSheet(bobEntry, carol, true, AssetContent.ALL));
   }

   private AssetEntry createBobViewsheet() throws Exception {
      AssetEntry created = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                                          "Bug77363VS" + System.nanoTime(), bobId, orgId);
      runIgnoringPermission(() -> repository.setSheet(created, new Viewsheet(), bob, true));

      // reopen from the stored identifier, as the composer does
      AssetEntry entry = AssetEntry.createAssetEntry(created.toIdentifier());
      cleanup.add(entry);

      VSBookmark bookmark = new VSBookmark();
      bookmark.setUser(bobId);
      bookmark.addBookmark(OWNER_BOOKMARK, new Viewsheet(), VSBookmarkInfo.PRIVATE, false, false);
      repository.setVSBookmark(entry, bookmark, new XPrincipal(bobId));
      return entry;
   }

   private Viewsheet openAndEdit(AssetEntry entry, SRPrincipal user, boolean permission)
      throws Exception
   {
      Viewsheet vs = (Viewsheet) repository.getSheet(entry, user, permission, AssetContent.ALL);
      // RuntimeViewsheet.setEntry does this for an opened viewsheet
      vs.setRuntimeEntry(entry);
      vs.addAssembly(new TextVSAssembly(vs, TEXT));
      return vs;
   }

   private VSBookmark bobBookmarks(AssetEntry entry) throws Exception {
      return repository.getVSBookmark(entry, new XPrincipal(bobId), true);
   }

   private void assertPersisted(AssetEntry entry) throws Exception {
      Viewsheet[] saved = new Viewsheet[1];
      runIgnoringPermission(
         () -> saved[0] = (Viewsheet) repository.getSheet(entry, admin, false, AssetContent.ALL));
      assertNotNull(saved[0], "sheet must exist");
      assertNotNull(saved[0].getAssembly(TEXT), "save must be persisted");
   }

   private void runIgnoringPermission(ThrowingRunnable runnable) throws Exception {
      AssetRepository.IGNORE_PERM.set(true);

      try {
         runnable.run();
      }
      finally {
         AssetRepository.IGNORE_PERM.remove();
      }
   }

   private SRPrincipal principal(String name, String... roles) {
      IdentityID[] roleIds = new IdentityID[roles.length];

      for(int i = 0; i < roles.length; i++) {
         roleIds[i] = new IdentityID(roles[i], null);
      }

      SRPrincipal principal =
         new SRPrincipal(new IdentityID(name, orgId), roleIds, new String[0], orgId, 1L);
      // a real session is in SecurityEngine's logged-in map; admin-of-owner checks on another
      // user's private asset now ask SecurityEngine, which refuses a principal that is not (Bug #78075)
      principal.setIgnoreLogin(true);
      return principal;
   }

   @FunctionalInterface
   private interface ThrowingRunnable {
      void run() throws Exception;
   }
}
