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
package inetsoft.uql.util;

/*
 * A variable choice query "[<ws identifier>]^[<col>]" (and the asset-source browse in
 * SreeAssistant) reaches ColumnCache.getColumnData(WorksheetProcessor, AssetEntry, ...), which runs
 * the worksheet through ReportWorksheetProcessor. That processor loads the sheet with
 * getSheet(permission=false) and from the org in the identifier, so the identifier could name
 * another user's private worksheet or another org's worksheet (follow-up to Bug #77522).
 *
 * The asset engine is a stub whose ACL hooks grant everything, so a user-scope refusal can only
 * come from the real owner check in checkAssetPermission0 (checkUserAsset=true). The worksheet
 * processor is a mock, so "allowed" means it ran and its value came back.
 */

import inetsoft.report.LibManagerProvider;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.ThreadContext;
import inetsoft.web.composer.model.BrowseDataModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ColumnCacheWorksheetScopeTest {
   private static final String ORG = "orgCcWs_id";
   private static final String OTHER_ORG = "otherOrgCcWs_id";
   private static final String OWNER = "ownerCcWs";
   private static final String VIEWER = "viewerCcWs";
   private static final String SITE_ADMIN = "siteAdminCcWs";

   private static SecurityTestDataBuilder builder;
   private static SRPrincipal owner;
   private static SRPrincipal viewer;
   private static SRPrincipal siteAdmin;

   private MockedStatic<AssetUtil> assetUtil;
   private MockedStatic<VpmProcessor> vpmProcessor;
   private WorksheetProcessor wsproc;
   private ColumnCache cache;
   private String oldProvider;

   @BeforeAll
   static void setUpAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("orgCcWs", ORG)
         .addUser(OWNER, ORG, "password")
         .addUser(VIEWER, ORG, "password")
         .addUser(SITE_ADMIN, ORG, "password")
         .addSysAdminRole("siteAdminsCcWs", ORG)
         .addUserToRole(SITE_ADMIN, "siteAdminsCcWs", ORG)
         .setup();
      owner = builder.principalOf(OWNER, ORG);
      viewer = builder.principalOf(VIEWER, ORG);
      siteAdmin = builder.principalOf(SITE_ADMIN, ORG);
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
      oldProvider = SreeEnv.getProperty("security.provider");
      SreeEnv.setProperty("security.provider", "file");

      AbstractAssetEngine engine = new GrantingAssetEngine();
      assetUtil = Mockito.mockStatic(AssetUtil.class, Mockito.CALLS_REAL_METHODS);
      assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(engine);

      vpmProcessor = Mockito.mockStatic(VpmProcessor.class, Mockito.CALLS_REAL_METHODS);
      vpmProcessor.when(VpmProcessor::useVpmSecurity).thenReturn(false);

      wsproc = mock(WorksheetProcessor.class);
      when(wsproc.execute(any(AssetEntry.class), any(VariableTable.class), any()))
         .thenAnswer(inv -> new DefaultTableLens(new Object[][] { { "col" }, { "secret" } }));

      cache = new ColumnCache(mock(DataSourceRegistry.class), null);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      SreeEnv.setProperty("security.provider", oldProvider == null ? "" : oldProvider);

      for(AutoCloseable c : new AutoCloseable[] { vpmProcessor, assetUtil }) {
         try {
            if(c != null) {
               c.close();
            }
         }
         catch(Exception ignore) {
         }
      }
   }

   @Test
   void ownPrivateWorksheet_allowed() throws Exception {
      assertBrowsed(privateWs(), owner);
   }

   @Test
   void otherUsersPrivateWorksheet_refused() throws Exception {
      assertRefused(privateWs(), viewer);
   }

   @Test
   void otherUsersPrivateWorksheet_refusedWithContextPrincipalOnly() throws Exception {
      // XSessionManager.executeChoiceQuery passes the thread context principal
      ThreadContext.setContextPrincipal(viewer);
      assertRefused(privateWs(), null);
   }

   @Test
   void otherUsersPrivateWorksheet_allowedForSiteAdmin() throws Exception {
      assertBrowsed(privateWs(), siteAdmin);
   }

   @Test
   void sameOrgGlobalWorksheet_allowedWithoutAclCheck() throws Exception {
      // a viewsheet viewer needn't have READ on the base worksheet, so the asset ACL must not be
      // consulted for a global worksheet in the user's own org
      AssetRepository refusing = mock(AssetRepository.class, inv -> {
         throw new AssertionError("asset ACL consulted: " + inv);
      });
      assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(refusing);
      assertBrowsed(globalWs(ORG), viewer);
   }

   @Test
   void otherOrgGlobalWorksheet_refused() throws Exception {
      assertRefused(globalWs(OTHER_ORG), viewer);
   }

   @Test
   void otherOrgGlobalWorksheet_allowedForSiteAdmin() throws Exception {
      assertBrowsed(globalWs(OTHER_ORG), siteAdmin);
   }

   @Test
   void noUser_unchanged() throws Exception {
      assertBrowsed(privateWs(), null);
   }

   @Test
   void securityOff_unchanged() throws Exception {
      SreeEnv.setProperty("security.provider", "");
      assertBrowsed(privateWs(), viewer);
      assertBrowsed(globalWs(OTHER_ORG), viewer);
   }

   private static AssetEntry privateWs() {
      return new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.WORKSHEET,
                            "privWsCcWs", new IdentityID(OWNER, ORG), ORG);
   }

   private static AssetEntry globalWs(String orgID) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                            "globalWsCcWs", null, orgID);
   }

   private void assertBrowsed(AssetEntry entry, Principal user) throws Exception {
      BrowseDataModel data = cache.getColumnData(wsproc, entry, "col", user, null);
      assertArrayEquals(new Object[] { "secret" }, data.values());
   }

   private void assertRefused(AssetEntry entry, Principal user) throws Exception {
      BrowseDataModel data = cache.getColumnData(wsproc, entry, "col", user, null);
      assertEquals(0, data.values().length);
      verify(wsproc, never()).execute(any(), any(), any());
   }

   /**
    * Grants every ACL check, so a user-scope refusal can only come from the owner check in
    * checkAssetPermission0 with checkUserAsset=true.
    */
   private static class GrantingAssetEngine extends AbstractAssetEngine {
      GrantingAssetEngine() {
         super((LibManagerProvider) null, (Cluster) null);
      }

      @Override
      public boolean checkPermission(Principal principal, ResourceType type, String resource,
                                     EnumSet<ResourceAction> action)
      {
         return true;
      }

      @Override
      public boolean checkPermission(Principal principal, ResourceType type,
                                     IdentityID resource, EnumSet<ResourceAction> action)
      {
         return true;
      }

      @Override
      protected boolean checkDataModelFolderPermission(String folder, String source, Principal user) {
         return true;
      }

      @Override
      protected boolean checkQueryFolderPermission(String folder, String source, Principal user) {
         return true;
      }

      @Override
      protected boolean checkQueryPermission(String query, Principal user) {
         return true;
      }

      @Override
      protected boolean checkDataSourcePermission(String dname, Principal user) {
         return true;
      }

      @Override
      protected boolean checkDataSourceFolderPermission(String folder, Principal user) {
         return true;
      }
   }
}
