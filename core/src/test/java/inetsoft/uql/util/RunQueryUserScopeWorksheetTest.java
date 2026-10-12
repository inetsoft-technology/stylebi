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
 * Bug #77522: runQuery("ws:<user>:<path>") builds a USER_SCOPE entry for any named user, and
 * ReportWorksheetProcessor loads it with getSheet(permission=false). The only gate in
 * XUtil.runQuery was a scope-blind ResourceType.ASSET READ walk on the bare path, which evaluates
 * the *global* worksheet ACL, so any caller with READ on that global path (or an org admin) could
 * run another user's private worksheet.
 *
 * The test runs the real XUtil.runQuery. The asset engine is a stub AbstractAssetEngine whose ACL
 * hooks grant everything, so the real checkAssetPermission0 owner check (checkUserAsset=true) is
 * the only thing that can refuse a user-scope sheet; a call that dropped checkUserAsset (e.g. the
 * AssetRepository default 4-arg method) would let the non-owner through and fail the test. The
 * global ASSET walk goes through the SecurityEngine spy bean, whose ASSET READ check is routed to
 * a hook that grants READ only to READER and the admins. HookConfig stubs the spy once, before it
 * is published; the tests only set the hook and must never stub the shared spy, because another
 * thread calling it (e.g. the debounced ApplicationPropertiesChangedEvent from setUpAll's
 * SreeEnv.save()) can take a pending stub and leave UnfinishedStubbingException (Bug #77783).
 * ReportWorksheetProcessor is replaced by a construction mock, so "allowed" means the processor
 * ran and its table came back.
 */

import inetsoft.report.LibManagerProvider;
import inetsoft.report.composition.execution.ReportWorksheetProcessor;
import inetsoft.report.internal.license.LicenseManager;
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
import inetsoft.uql.script.XTableArray;
import inetsoft.util.ThreadContext;
import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  RunQueryUserScopeWorksheetTest.HookConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RunQueryUserScopeWorksheetTest {
   private static final String ORG = "org77522_id";
   private static final String OWNER = "owner77522";
   private static final String READER = "reader77522";
   private static final String NO_READ = "noRead77522";
   private static final String ORG_ADMIN = "orgAdmin77522";
   private static final String SITE_ADMIN = "siteAdmin77522";
   private static final String PRIVATE_WS =
      "ws:" + OWNER + IdentityID.KEY_DELIMITER + ORG + ":privWs77522";
   private static final String GLOBAL_WS = "ws:global:globalWs77522";

   private static SecurityTestDataBuilder builder;
   private static SRPrincipal owner;
   private static SRPrincipal reader;
   private static SRPrincipal noRead;
   private static SRPrincipal orgAdmin;
   private static SRPrincipal siteAdmin;
   private static final AtomicReference<Predicate<Principal>> CHECK_PERMISSION =
      new AtomicReference<>();

   private MockedStatic<AssetUtil> assetUtil;
   private MockedStatic<VpmProcessor> vpmProcessor;
   private MockedConstruction<ReportWorksheetProcessor> processors;
   private String oldProvider;
   private boolean vpmSecurity;

   @BeforeAll
   static void setUpAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("org77522", ORG)
         .addUser(OWNER, ORG, "password")
         .addUser(READER, ORG, "password")
         .addUser(NO_READ, ORG, "password")
         .addUser(ORG_ADMIN, ORG, "password")
         .addOrgAdminRole("orgAdmins77522", ORG)
         .addUserToRole(ORG_ADMIN, "orgAdmins77522", ORG)
         .addUser(SITE_ADMIN, ORG, "password")
         .addSysAdminRole("siteAdmins77522", ORG)
         .addUserToRole(SITE_ADMIN, "siteAdmins77522", ORG)
         .setup();
      owner = builder.principalOf(OWNER, ORG);
      reader = builder.principalOf(READER, ORG);
      noRead = builder.principalOf(NO_READ, ORG);
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
   void setUp() throws Exception {
      ThreadContext.setContextPrincipal(null);
      oldProvider = SreeEnv.getProperty("security.provider");
      SreeEnv.setProperty("security.provider", "file");
      vpmSecurity = false;

      AbstractAssetEngine engine = new GrantingAssetEngine();
      assetUtil = Mockito.mockStatic(AssetUtil.class, Mockito.CALLS_REAL_METHODS);
      assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(engine);

      // global worksheet ACL: only READER and the admins may read it
      CHECK_PERMISSION.set(p -> {
         String name = IdentityID.getIdentityIDFromKey(p.getName()).getName();
         return READER.equals(name) || ORG_ADMIN.equals(name) || SITE_ADMIN.equals(name);
      });

      vpmProcessor = Mockito.mockStatic(VpmProcessor.class, Mockito.CALLS_REAL_METHODS);
      vpmProcessor.when(VpmProcessor::useVpmSecurity).thenAnswer(inv -> vpmSecurity);

      processors = Mockito.mockConstruction(ReportWorksheetProcessor.class, (mock, ctx) ->
         when(mock.execute(any(AssetEntry.class), any(VariableTable.class), any()))
            .thenReturn(new DefaultTableLens(new Object[][] { { "col" }, { "secret" } })));
   }

   @AfterEach
   void tearDown() throws Exception {
      ThreadContext.setContextPrincipal(null);
      CHECK_PERMISSION.set(null);
      SreeEnv.setProperty("security.provider", oldProvider == null ? "" : oldProvider);

      for(AutoCloseable c : new AutoCloseable[] { processors, vpmProcessor, assetUtil }) {
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
   void otherUsersPrivateWorksheet_refusedForReaderOfSameGlobalPath() {
      assertNull(XUtil.runQuery(PRIVATE_WS, null, reader, null),
                 "a user with READ on the same global path must not run another user's private ws");
      assertProcessorNotRun();
   }

   @Test
   void otherUsersPrivateWorksheet_refusedForOrgAdmin() {
      assertNull(XUtil.runQuery(PRIVATE_WS, null, orgAdmin, null));
      assertProcessorNotRun();
   }

   @Test
   void otherUsersPrivateWorksheet_refusedFromReportScript() {
      assertThrows(ScriptException.class,
                   () -> XUtil.runQuery(PRIVATE_WS, null, reader, null, true, true));
      assertProcessorNotRun();
   }

   @Test
   void ownPrivateWorksheet_allowed() {
      assertInstanceOf(XTableArray.class, XUtil.runQuery(PRIVATE_WS, null, owner, null));
      assertInstanceOf(XTableArray.class,
                       XUtil.runQuery(PRIVATE_WS, null, owner, null, true, true));
   }

   @Test
   void otherUsersPrivateWorksheet_allowedForSiteAdmin() {
      assertInstanceOf(XTableArray.class, XUtil.runQuery(PRIVATE_WS, null, siteAdmin, null));
   }

   @Test
   void globalWorksheet_unchanged() {
      assertInstanceOf(XTableArray.class, XUtil.runQuery(GLOBAL_WS, null, reader, null));
      assertNull(XUtil.runQuery(GLOBAL_WS, null, noRead, null),
                 "control: the global ACL walk still refuses a user without READ");
      // report scripts keep skipping the global ACL walk (bug1400096326732 b.c.)
      assertInstanceOf(XTableArray.class,
                       XUtil.runQuery(GLOBAL_WS, null, noRead, null, true, true));
   }

   @Test
   void securityOff_unchanged() {
      SreeEnv.setProperty("security.provider", "");
      assertInstanceOf(XTableArray.class, XUtil.runQuery(PRIVATE_WS, null, reader, null));
      assertInstanceOf(XTableArray.class, XUtil.runQuery(GLOBAL_WS, null, noRead, null));
   }

   @Test
   void securityProviderOffVpmOn_ownerOnly() {
      SreeEnv.setProperty("security.provider", "");
      vpmSecurity = true;
      assertInstanceOf(XTableArray.class, XUtil.runQuery(PRIVATE_WS, null, owner, null));
      assertNull(XUtil.runQuery(PRIVATE_WS, null, reader, null));
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
    * Replaces the SecurityEngine spy bean with one whose ASSET READ check defers to
    * CHECK_PERMISSION, or to the real method when no hook is set.
    */
   @Configuration
   static class HookConfig {
      @Bean
      @DependsOn("propertiesEngine")
      public SecurityEngine securityEngine(Cluster cluster, LicenseManager licenseManager)
         throws Exception
      {
         SecurityEngine engine = spy(new SecurityEngine(licenseManager, cluster));
         doAnswer(inv -> {
            Predicate<Principal> hook = CHECK_PERMISSION.get();
            return hook == null ? inv.callRealMethod() : hook.test(inv.getArgument(0));
         }).when(engine).checkPermission(any(Principal.class), eq(ResourceType.ASSET),
                                         anyString(), eq(ResourceAction.READ));
         return engine;
      }
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
