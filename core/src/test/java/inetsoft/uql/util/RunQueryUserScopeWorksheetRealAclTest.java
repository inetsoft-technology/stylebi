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
 * Bug #77522, companion to RunQueryUserScopeWorksheetTest. That test grants every ACL check in the
 * asset engine and stubs the global ASSET READ walk. Here nothing is stubbed on the permission
 * side: the asset engine's checkPermission hooks delegate to the real SecurityEngine, the way
 * RepletEngine does in production, and the global ACL is a real grant saved through
 * SecurityTestDataBuilder. So the owner's MY_DASHBOARDS READ, the site admin's SECURITY_USER ADMIN,
 * the org admin's ASSET pass and the reader's global grant on the same path all come from the real
 * DefaultCheckPermissionStrategy. It also covers the short identifier form ws:<user>:<path>, whose
 * owner org is taken from the thread principal.
 */

import inetsoft.report.LibManagerProvider;
import inetsoft.report.composition.execution.ReportWorksheetProcessor;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.script.XTableArray;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
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
class RunQueryUserScopeWorksheetRealAclTest {
   private static final String ORG = "org77522acl_id";
   private static final String OWNER = "owner77522acl";
   private static final String READER = "reader77522acl";
   private static final String ORG_ADMIN = "orgAdmin77522acl";
   private static final String SITE_ADMIN = "siteAdmin77522acl";
   private static final String WS_PATH = "privWs77522acl";
   private static final String FULL_KEY_WS =
      "ws:" + OWNER + IdentityID.KEY_DELIMITER + ORG + ":" + WS_PATH;
   private static final String SHORT_WS = "ws:" + OWNER + ":" + WS_PATH;

   private static SecurityTestDataBuilder builder;
   private static SRPrincipal owner;
   private static SRPrincipal reader;
   private static SRPrincipal orgAdmin;
   private static SRPrincipal siteAdmin;

   private MockedStatic<AssetUtil> assetUtil;
   private MockedConstruction<ReportWorksheetProcessor> processors;
   private String oldProvider;

   @BeforeAll
   static void setUpAll() throws Exception {
      // READER holds a real READ grant on the global ASSET resource with the same path as the
      // owner's private worksheet, which is what let the old scope-blind walk through.
      builder = SecurityTestDataBuilder.create()
         .addOrg("org77522acl", ORG)
         .addUser(OWNER, ORG, "password")
         .addUser(READER, ORG, "password")
         .addUser(ORG_ADMIN, ORG, "password")
         .addOrgAdminRole("orgAdmins77522acl", ORG)
         .addUserToRole(ORG_ADMIN, "orgAdmins77522acl", ORG)
         .addUser(SITE_ADMIN, ORG, "password")
         .addSysAdminRole("siteAdmins77522acl", ORG)
         .addUserToRole(SITE_ADMIN, "siteAdmins77522acl", ORG)
         .grantPermission(ResourceType.ASSET, WS_PATH, ResourceAction.READ,
                          READER, Identity.USER, ORG)
         .markPermissionEdited(ResourceType.ASSET, WS_PATH, ORG)
         .setup();
      owner = builder.principalOf(OWNER, ORG);
      reader = builder.principalOf(READER, ORG);
      orgAdmin = builder.principalOf(ORG_ADMIN, ORG);
      siteAdmin = builder.principalOf(SITE_ADMIN, ORG);
   }

   @AfterAll
   static void tearDownAll() {
      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() {
      oldProvider = SreeEnv.getProperty("security.provider");
      SreeEnv.setProperty("security.provider", "file");
      ThreadContext.setContextPrincipal(null);
      ThreadContext.setPrincipal(null);
      AbstractAssetEngine engine = new SecurityEngineAssetEngine();
      assetUtil = Mockito.mockStatic(AssetUtil.class, Mockito.CALLS_REAL_METHODS);
      assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(engine);
      processors = Mockito.mockConstruction(ReportWorksheetProcessor.class, (mock, ctx) ->
         when(mock.execute(any(AssetEntry.class), any(VariableTable.class), any()))
            .thenReturn(new DefaultTableLens(new Object[][] { { "col" }, { "secret" } })));
   }

   @AfterEach
   void tearDown() {
      SreeEnv.setProperty("security.provider", oldProvider == null ? "" : oldProvider);
      ThreadContext.setContextPrincipal(null);
      ThreadContext.setPrincipal(null);

      for(AutoCloseable c : new AutoCloseable[] { processors, assetUtil }) {
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
   void precondition_readerAndOrgAdminPassTheGlobalAssetWalk() throws Exception {
      assertTrue(SecurityEngine.getSecurity().checkPermission(
                    reader, ResourceType.ASSET, WS_PATH, ResourceAction.READ),
                 "reader must pass the global ASSET walk, or the refusal tests prove nothing");
      assertTrue(SecurityEngine.getSecurity().checkPermission(
                    orgAdmin, ResourceType.ASSET, WS_PATH, ResourceAction.READ),
                 "org admin must pass the global ASSET walk, or the refusal tests prove nothing");
   }

   @Test
   void readerWithGlobalGrantOnSamePath_refused() {
      assertNull(XUtil.runQuery(FULL_KEY_WS, null, reader, null));
      assertProcessorNotRun();
   }

   @Test
   void orgAdmin_refused() {
      assertNull(XUtil.runQuery(FULL_KEY_WS, null, orgAdmin, null));
      assertProcessorNotRun();
   }

   @Test
   void shortForm_refusedForReader() {
      ThreadContext.setContextPrincipal(reader);
      assertNull(XUtil.runQuery(SHORT_WS, null, reader, null));
      assertProcessorNotRun();
   }

   @Test
   void owner_allowedThroughRealMyDashboardsCheck() {
      assertInstanceOf(XTableArray.class, XUtil.runQuery(FULL_KEY_WS, null, owner, null));
   }

   @Test
   void shortForm_allowedForOwner() {
      ThreadContext.setContextPrincipal(owner);
      assertInstanceOf(XTableArray.class, XUtil.runQuery(SHORT_WS, null, owner, null));
   }

   @Test
   void siteAdmin_allowedThroughRealSecurityUserAdminCheck() {
      assertInstanceOf(XTableArray.class, XUtil.runQuery(FULL_KEY_WS, null, siteAdmin, null));
   }

   private void assertProcessorNotRun() {
      for(ReportWorksheetProcessor proc : processors.constructed()) {
         try {
            verify(proc, never()).execute(any(), any(), any());
         }
         catch(Exception ex) {
            fail(ex);
         }
      }
   }

   /**
    * An AbstractAssetEngine whose permission hooks go to the real SecurityEngine, as
    * RepletEngine.checkPermission does. checkAssetPermission and checkAssetPermission0 are the
    * real AbstractAssetEngine code.
    */
   private static class SecurityEngineAssetEngine extends AbstractAssetEngine {
      SecurityEngineAssetEngine() {
         super((LibManagerProvider) null, (Cluster) null);
      }

      @Override
      public boolean checkPermission(Principal principal, ResourceType type, String resource,
                                     EnumSet<ResourceAction> actions)
      {
         try {
            for(ResourceAction action : actions) {
               if(!SecurityEngine.getSecurity().checkPermission(principal, type, resource, action)) {
                  return false;
               }
            }

            return true;
         }
         catch(Exception ex) {
            return false;
         }
      }

      @Override
      public boolean checkPermission(Principal principal, ResourceType type,
                                     IdentityID resource, EnumSet<ResourceAction> actions)
      {
         try {
            for(ResourceAction action : actions) {
               if(!SecurityEngine.getSecurity().checkPermission(principal, type, resource, action)) {
                  return false;
               }
            }

            return true;
         }
         catch(Exception ex) {
            return false;
         }
      }
      // The data-source and query hooks are not reached by a worksheet entry.
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
