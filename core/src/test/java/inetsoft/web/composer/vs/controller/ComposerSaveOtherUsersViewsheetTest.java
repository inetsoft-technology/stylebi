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
package inetsoft.web.composer.vs.controller;

/*
 * Bug #77363: the composer save of another user's private viewsheet by a site admin, with
 * security on, goes through the real composer entry point. ComposerViewsheetService.saveViewsheet
 * runs on a RuntimeViewsheet opened in design mode by the real ViewsheetEngine, so the runtime
 * entry, the lock-owner check and the permission checks are the production ones. Before the fix
 * the bookmark copy in AbstractAssetEngine.setSheet threw "Invalid entry found" and the save was
 * lost.
 */

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.mv.MVManager;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.ThreadContext;
import inetsoft.web.composer.ws.event.SaveSheetEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ComposerSaveOtherUsersViewsheetTest {
   private static final String OWNER_BOOKMARK = "Bug77363ComposerOwnerBookmark";
   private static final String TEXT = "Bug77363ComposerText";

   @Autowired
   SecurityEngineOverrides overrides;

   @Autowired
   ViewsheetService viewsheetService;

   private AssetRepository repository;
   private AssetEntry bobEntry;
   private IdentityID bobId;
   private String runtimeId;
   private SRPrincipal admin;
   private Principal savedPrincipal;

   @BeforeEach
   void setUp() throws Exception {
      // opening the viewsheet as admin sets the thread's principal, restored in tearDown()
      savedPrincipal = ThreadContext.getPrincipal();
      SecurityEngineOverrides.assertInstalled(SecurityEngine.getSecurity());
      overrides.setSecurityEnabled(true);
      assertTrue(SecurityEngine.getSecurity().isSecurityEnabled(), "test requires security on");

      repository = AssetUtil.getAssetRepository(false);
      String orgId = Organization.getDefaultOrganizationID();
      bobId = new IdentityID("bob", orgId);
      admin = new SRPrincipal(new IdentityID("admin", orgId),
                              new IdentityID[] { new IdentityID("Administrator", null) },
                              new String[0], orgId, 1L);
      // a real admin session is in SecurityEngine's logged-in map; admin-of-owner checks on
      // another user's private asset now ask SecurityEngine, which refuses a principal that is
      // not (Bug #78075)
      admin.setIgnoreLogin(true);
      assertTrue(OrganizationManager.getInstance().isSiteAdmin(admin), "admin must be site admin");

      AssetEntry created = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                                          "Bug77363ComposerVS" + System.nanoTime(), bobId, orgId);
      runIgnoringPermission(() -> repository.setSheet(
         created, new Viewsheet(), new XPrincipal(bobId), true));
      bobEntry = AssetEntry.createAssetEntry(created.toIdentifier());

      VSBookmark bookmark = new VSBookmark();
      bookmark.setUser(bobId);
      bookmark.addBookmark(OWNER_BOOKMARK, new Viewsheet(), VSBookmarkInfo.PRIVATE, false, false);
      repository.setVSBookmark(bobEntry, bookmark, new XPrincipal(bobId));
   }

   @AfterEach
   void tearDown() throws Exception {
      try {
         if(runtimeId != null) {
            viewsheetService.closeViewsheet(runtimeId, admin);
         }

         runIgnoringPermission(() -> {
            if(repository.containsEntry(bobEntry)) {
               repository.removeSheet(bobEntry, null, true);
            }
         });
      }
      finally {
         runtimeId = null;
         overrides.clear();
         ThreadContext.setPrincipal(savedPrincipal);
      }
   }

   @Test
   void siteAdminComposerSaveOfOtherUsersPrivateViewsheetIsPersisted() throws Exception {
      // composer open (design mode): the real READ check on the private asset
      runtimeId = viewsheetService.openViewsheet(bobEntry, admin, false);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, admin);
      assertEquals(bobEntry, rvs.getViewsheet().getRuntimeEntry(),
                   "composer open must set the runtime entry that drives the bookmark copy");
      rvs.getViewsheet().addAssembly(new TextVSAssembly(rvs.getViewsheet(), TEXT));

      MVManager mvManager = mock(MVManager.class);
      // a mock MVManager reports no MV, so no MV confirmation is raised
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);
      ComposerViewsheetService service = new ComposerViewsheetService(
         null, mock(CoreLifecycleService.class), viewsheetService, null, null, null, null, null,
         mvManager, mock(XSessionService.class));

      Boolean result = assertDoesNotThrow(() -> service.saveViewsheet(
         runtimeId, new SaveSheetEvent(), admin, dispatcher, "http://localhost:8080/sree/"));

      assertEquals(Boolean.TRUE, result);
      Viewsheet[] saved = new Viewsheet[1];
      runIgnoringPermission(() -> saved[0] =
         (Viewsheet) repository.getSheet(bobEntry, admin, false, AssetContent.ALL));
      assertNotNull(saved[0].getAssembly(TEXT), "composer save must be persisted");
      assertTrue(repository.getVSBookmark(bobEntry, new XPrincipal(bobId), true)
                    .containsBookmark(OWNER_BOOKMARK), "owner's bookmark must be kept");
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

   @FunctionalInterface
   private interface ThrowingRunnable {
      void run() throws Exception;
   }
}
