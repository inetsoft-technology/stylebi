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
 * in both cases the sheet was not written. Uses the real asset repository with security off.
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

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ComposedDashboardBookmarkSaveTest {
   private static final String OWNER_BOOKMARK = "Bug77345OwnerBookmark";
   private AssetRepository repository;
   private AssetEntry dashboardEntry;
   private SRPrincipal admin;
   private IdentityID owner;

   @BeforeEach
   void setUp() throws Exception {
      assertFalse(SecurityEngine.getSecurity().isSecurityEnabled(), "test requires security off");
      repository = AssetUtil.getAssetRepository(false);
      String orgId = Organization.getDefaultOrganizationID();
      // the owner DashboardController.getIdentity() builds with security off (null org)
      owner = new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.USER).getIdentityID();
      assertNull(owner.getOrgID());
      admin = new SRPrincipal(new IdentityID("admin", orgId),
                              new IdentityID[] { new IdentityID("Administrator", null) },
                              new String[0], orgId, 1L);

      String name = "Bug77345Dashboard" + System.nanoTime();
      AssetEntry created = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                                          name, owner, orgId);
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setComposedDashboard(true);
      repository.setSheet(created, vs, admin, true);

      // reopen from the stored identifier, as the composer does
      dashboardEntry = AssetEntry.createAssetEntry(created.toIdentifier());

      // the only principal a USER_SCOPE viewsheet's bookmarks can be keyed by is its owner
      VSBookmark bookmark = new VSBookmark();
      bookmark.setUser(owner);
      bookmark.addBookmark(OWNER_BOOKMARK, vs, VSBookmarkInfo.ALLSHARE, false, false);
      repository.setVSBookmark(dashboardEntry, bookmark, new XPrincipal(owner));
   }

   @Test
   void virtualAdminResavesAnonymousOwnedComposedDashboard() throws Exception {
      Viewsheet vs = openAndEdit(dashboardEntry);

      assertDoesNotThrow(() -> repository.setSheet(dashboardEntry, vs, admin, true));

      Viewsheet saved = (Viewsheet) repository.getSheet(dashboardEntry, admin, false,
                                                        AssetContent.ALL);
      assertNotNull(saved.getAssembly("Bug77345Text"), "re-save must be persisted");
      assertTrue(repository.getVSBookmark(dashboardEntry, new XPrincipal(owner))
                    .containsBookmark(OWNER_BOOKMARK), "owner's bookmark must be kept");
   }

   @Test
   void virtualAdminSavesAnonymousOwnedComposedDashboardAsGlobal() throws Exception {
      Viewsheet vs = openAndEdit(dashboardEntry);
      String orgId = dashboardEntry.getOrgID();
      AssetEntry folder = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                         AssetEntry.Type.REPOSITORY_FOLDER, "Bug77345", null, orgId);

      if(!repository.containsEntry(folder)) {
         repository.addFolder(folder, null);
      }

      AssetEntry target = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                         "Bug77345/" + dashboardEntry.getName(), null, orgId);

      try {
         // the test principal is not a logged-in session, so the global folder WRITE check would
         // deny it; the permission check is not under test here, the bookmark copy is
         AssetRepository.IGNORE_PERM.set(true);

         try {
            assertDoesNotThrow(() -> repository.setSheet(target, vs, admin, true));
         }
         finally {
            AssetRepository.IGNORE_PERM.remove();
         }

         Viewsheet saved = (Viewsheet) repository.getSheet(target, admin, false,
                                                           AssetContent.ALL);
         assertNotNull(saved.getAssembly("Bug77345Text"), "save as must be persisted");
         assertTrue(repository.getVSBookmark(target, new XPrincipal(owner))
                       .containsBookmark(OWNER_BOOKMARK), "owner's bookmark must be copied");
      }
      finally {
         if(repository.containsEntry(target)) {
            repository.removeSheet(target, null, true);
         }
      }
   }

   @Test
   void virtualAdminSavesAnonymousOwnedComposedDashboardAsOwnPrivateAsset() throws Exception {
      Viewsheet vs = openAndEdit(dashboardEntry);
      AssetEntry target = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                                         dashboardEntry.getName() + "Mine", admin.getIdentityID(),
                                         dashboardEntry.getOrgID());

      try {
         AssetRepository.IGNORE_PERM.set(true);

         try {
            // the anonymous owner's bookmarks cannot be keyed on admin's private asset, and admin
            // cannot read them, so there is nothing to copy; the save itself must go through
            assertDoesNotThrow(() -> repository.setSheet(target, vs, admin, true));
         }
         finally {
            AssetRepository.IGNORE_PERM.remove();
         }

         Viewsheet saved = (Viewsheet) repository.getSheet(target, admin, false,
                                                           AssetContent.ALL);
         assertNotNull(saved.getAssembly("Bug77345Text"), "save as must be persisted");
      }
      finally {
         if(repository.containsEntry(target)) {
            repository.removeSheet(target, null, true);
         }
      }
   }

   private Viewsheet openAndEdit(AssetEntry entry) throws Exception {
      Viewsheet vs = (Viewsheet) repository.getSheet(entry, admin, false, AssetContent.ALL);
      // RuntimeViewsheet.setEntry does this for an opened viewsheet
      vs.setRuntimeEntry(entry);
      TextVSAssembly text = new TextVSAssembly(vs, "Bug77345Text");
      vs.addAssembly(text);
      return vs;
   }

   @AfterEach
   void tearDown() throws Exception {
      if(dashboardEntry != null && repository.containsEntry(dashboardEntry)) {
         repository.removeSheet(dashboardEntry, admin, true);
      }
   }
}
