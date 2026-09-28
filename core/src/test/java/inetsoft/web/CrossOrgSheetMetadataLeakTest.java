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
package inetsoft.web;

/*
 * Bug #77261: three more sites built an AssetEntry from a client-supplied (or registry-stored)
 * identifier, whose 5th component is kept as the entry's orgID, and opened it with
 * getSheet(..., permission=false, ...). That skipped the READ check, which is the only place a
 * foreign org is rejected, so another org's viewsheet metadata leaked:
 *   S1 GET /api/em/content/repository/asset-exists (MVController.mvAssetExists) - existence
 *   S2 /app/viewer/view/<id> with a bot User-Agent (HomePageController Open Graph tags)
 *      - title and description; it also passed a null user, which skips every check
 *   S3 DashboardService.getDashboardModelInfo - composedDashboard/scaleToScreen/... flags
 *
 * Sheets are seeded into org A's and org B's storage through a real BlobIndexedStorage and read
 * through the real AbstractAssetEngine.getSheet(). StubAssetEngine inherits
 * AbstractAssetEngine.checkPermission(), which always grants, so the org is the only variable:
 * the same-org controls fail for a fix that simply denies everyone.
 */

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.DataSpace;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.content.repository.MVController;
import inetsoft.web.admin.content.repository.MVService;
import inetsoft.web.admin.content.repository.MVSupportService;
import inetsoft.web.portal.controller.DashboardService;
import inetsoft.web.portal.controller.HomePageController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.servlet.ModelAndView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CrossOrgSheetMetadataLeakTest {
   private static final String ORG_A = "orga_id";
   private static final String ORG_B = "orgb_id";
   private static final String ORG_B_VS = "1^128^__NULL__^SecretVS77261^" + ORG_B;
   private static final String ORG_B_MISSING = "1^128^__NULL__^NoSuchVS77261^" + ORG_B;
   private static final String ORG_A_VS = "1^128^__NULL__^OwnVS77261^" + ORG_A;
   private static final String SECRET_DESC = "SECRET-DESCRIPTION-OF-ORG-B";
   private static final String OWN_DESC = "own-description";
   private static final String BOT_AGENT = "Slackbot-LinkExpanding 1.0";

   private static SecurityTestDataBuilder builder;
   private static SRPrincipal orgAUser;

   @Autowired
   private BlobStorageManager blobStorageManager;

   private AbstractAssetEngine engine;
   private MockedStatic<SUtil> sutil;
   private MockedStatic<AssetUtil> assetUtil;

   @BeforeAll
   static void setUpAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("orga", ORG_A)
         .addOrg("orgb", ORG_B)
         .addUser("orgAUser77261", ORG_A, "password")
         .setup();
      orgAUser = builder.principalOf("orgAUser77261", ORG_A);
   }

   @AfterAll
   static void tearDownAll() {
      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      ThreadContext.setContextPrincipal(null);
      BlobIndexedStorage storage = new BlobIndexedStorage(blobStorageManager);
      StubAssetEngine stub = spy(new StubAssetEngine(storage));
      // the stub storage has no folder metadata; resolve the entry to itself
      doAnswer(inv -> inv.getArgument(0)).when(stub).getAssetEntry(any(AssetEntry.class));
      engine = stub;
      storage.putXMLSerializable(ORG_B_VS, createViewsheet(SECRET_DESC));
      storage.putXMLSerializable(ORG_A_VS, createViewsheet(OWN_DESC));

      sutil = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
      sutil.when(SUtil::isMultiTenant).thenReturn(true);
      assetUtil = Mockito.mockStatic(AssetUtil.class, Mockito.CALLS_REAL_METHODS);
      assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(engine);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);

      if(assetUtil != null) {
         assetUtil.close();
      }

      if(sutil != null) {
         sutil.close();
      }
   }

   // S1
   @Test
   void mvAssetExists_otherOrg_falseAndNoOracle_ownOrgTrue() {
      MVController controller = new MVController(mock(MVService.class),
         mock(MVSupportService.class), mock(SecurityProvider.class), mock(SecurityEngine.class));

      assertFalse(controller.mvAssetExists(ORG_B_VS, orgAUser),
                  "an org A caller must not learn whether an org B asset exists");
      assertFalse(controller.mvAssetExists(ORG_B_MISSING, orgAUser));
      assertTrue(controller.mvAssetExists(ORG_A_VS, orgAUser));
   }

   // S2
   @Test
   void openGraphTags_otherOrg_notDisclosed() {
      ThreadContext.setContextPrincipal(orgAUser);
      ModelAndView model = openGraph(ORG_B_VS);

      assertEquals(defaultProperty("share.opengraph.title"), model.getModel().get("openGraphTitle"));
      assertEquals(defaultProperty("share.opengraph.description"),
                   model.getModel().get("openGraphDescription"));
   }

   @Test
   void openGraphTags_noPrincipal_notDisclosed() {
      ModelAndView model = openGraph(ORG_A_VS);

      assertNotEquals("OwnVS77261", model.getModel().get("openGraphTitle"));
      assertNotEquals(OWN_DESC, model.getModel().get("openGraphDescription"));
   }

   @Test
   void openGraphTags_ownOrg_stillDisclosed() {
      ThreadContext.setContextPrincipal(orgAUser);
      ModelAndView model = openGraph(ORG_A_VS);

      assertEquals("OwnVS77261", model.getModel().get("openGraphTitle"));
      assertEquals(OWN_DESC, model.getModel().get("openGraphDescription"));
   }

   // S3
   @Test
   void dashboardModelInfo_otherOrg_null_ownOrgRead() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getAssetRepository()).thenReturn(engine);
      DashboardService service = new DashboardService(viewsheetService);

      assertNull(service.getDashboardModelInfo(ORG_B_VS, orgAUser),
                 "an org B viewsheet must not be opened for an org A caller");
      assertNull(service.getDashboardModelInfo(ORG_B_MISSING, orgAUser));

      DashboardService.DashboardModelInfo own = service.getDashboardModelInfo(ORG_A_VS, orgAUser);
      assertNotNull(own);
      assertTrue(own.composedDashboard);
      assertTrue(own.scaleToScreen);
   }

   private ModelAndView openGraph(String identifier) {
      DataSpace dataSpace = mock(DataSpace.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class, RETURNS_DEEP_STUBS);
      HomePageController controller = new HomePageController(
         dataSpace, securityEngine, mock(CustomThemesManager.class));
      String path = "/app/viewer/view/" + URLEncoder.encode(identifier, StandardCharsets.UTF_8);
      MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
      request.addHeader("User-Agent", BOT_AGENT);
      return controller.showHomePage(request, new MockHttpServletResponse(),
                                     "http://localhost:8080/");
   }

   private static String defaultProperty(String name) {
      return SreeEnv.getProperty(name).replaceAll("\\s", " ");
   }

   private static Viewsheet createViewsheet(String description) {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setDescription(description);
      vs.getViewsheetInfo().setComposedDashboard(true);
      vs.getViewsheetInfo().setScaleToScreen(true);
      return vs;
   }

   private static class StubAssetEngine extends AbstractAssetEngine {
      StubAssetEngine(IndexedStorage storage) {
         super((LibManagerProvider) null, (Cluster) null);
         istore = storage;
      }

      @Override
      protected boolean checkDataModelFolderPermission(String folder, String source, Principal user) {
         return false;
      }

      @Override
      protected boolean checkQueryFolderPermission(String folder, String source, Principal user) {
         return false;
      }

      @Override
      protected boolean checkQueryPermission(String query, Principal user) {
         return false;
      }

      @Override
      protected boolean checkDataSourcePermission(String dname, Principal user) {
         return false;
      }

      @Override
      protected boolean checkDataSourceFolderPermission(String folder, Principal user) {
         return false;
      }
   }
}
