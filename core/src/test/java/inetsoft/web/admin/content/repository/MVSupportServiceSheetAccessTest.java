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
package inetsoft.web.admin.content.repository;

/*
 * Bug #77280: MVSupportService.getSheet() built an AssetEntry from a client-supplied MV-analysis
 * identifier, whose 5th component is kept as the entry's orgID, and opened it with
 * getSheet(..., permission=false, ...). That skipped the READ check, which is the only place a
 * foreign org is rejected, so an org A MATERIALIZATION holder could analyze org B sheets, and a
 * missing org B sheet failed differently from an existing one.
 *
 * Sheets are seeded into org A's, org B's and the host org's storage through a real
 * BlobIndexedStorage and read through the real AbstractAssetEngine. StubAssetEngine inherits the
 * real checkAssetPermission()/checkAssetPermission0(); only the ACL leaves are stubbed.
 */

import inetsoft.mv.MVDef;
import inetsoft.mv.MVManager;
import inetsoft.mv.MVMetaData;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MVSupportServiceSheetAccessTest {
   private static final String ORG_A = "orga_id";
   private static final String ORG_B = "orgb_id";
   private static final String HOST_ORG = Organization.getDefaultOrganizationID();
   private static final String ORG_B_VS = "1^128^__NULL__^SecretVS77280^" + ORG_B;
   private static final String ORG_B_WS = "1^2^__NULL__^SecretWS77280^" + ORG_B;
   private static final String ORG_B_MISSING = "1^128^__NULL__^SecretVS77280Missing^" + ORG_B;
   private static final String ORG_A_VS = "1^128^__NULL__^OwnVS77280^" + ORG_A;
   private static final String ORG_A_WS = "1^2^__NULL__^OwnWS77280^" + ORG_A;
   private static final String HOST_VS = "1^128^__NULL__^HostVS77280^" + HOST_ORG;
   private static final String OWNER = "owner77280";
   private static final String ORG_ADMIN = "orgAdmin77280";
   private static final String ORG_A_USER = "orgAUser77280";
   private static final String OWNER_VS =
      "4^128^" + OWNER + IdentityID.KEY_DELIMITER + ORG_A + "^MyVS77280^" + ORG_A;
   private static final String NO_OWNER_VS = "4^128^__NULL__^MyVS77280^" + ORG_A;
   private static final String SECRET_DESC = "SECRET-DESCRIPTION-OF-ORG-B";

   private static SecurityTestDataBuilder builder;
   private static SRPrincipal orgAUser;
   private static SRPrincipal orgAdmin;
   private static SRPrincipal owner;
   private static SRPrincipal siteAdmin;

   @Autowired
   private BlobStorageManager blobStorageManager;

   private MockedStatic<SUtil> sutil;
   private MockedStatic<AssetUtil> assetUtil;
   private MVManager mvManager;
   private MVSupportService service;

   @BeforeAll
   static void setUpAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("orga", ORG_A)
         .addOrg("orgb", ORG_B)
         .addUser(ORG_A_USER, ORG_A, "password")
         .addUser(OWNER, ORG_A, "password")
         .addUser(ORG_ADMIN, ORG_A, "password")
         .addOrgAdminRole("orgAdmins77280", ORG_A)
         .addUserToRole(ORG_ADMIN, "orgAdmins77280", ORG_A)
         .addUser("siteAdmin77280", ORG_A, "password")
         .addSysAdminRole("siteAdmins77280", ORG_A)
         .addUserToRole("siteAdmin77280", "siteAdmins77280", ORG_A)
         .setup();
      orgAUser = builder.principalOf(ORG_A_USER, ORG_A);
      orgAdmin = builder.principalOf(ORG_ADMIN, ORG_A);
      owner = builder.principalOf(OWNER, ORG_A);
      siteAdmin = builder.principalOf("siteAdmin77280", ORG_A);
   }

   @AfterAll
   static void tearDownAll() {
      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   @SuppressWarnings("unchecked")
   void setUp() throws Exception {
      ThreadContext.setContextPrincipal(null);
      BlobIndexedStorage storage = new BlobIndexedStorage(blobStorageManager);
      StubAssetEngine stub = spy(new StubAssetEngine(storage));
      // the stub storage has no folder metadata; resolve the entry to itself
      doAnswer(inv -> inv.getArgument(0)).when(stub).getAssetEntry(any(AssetEntry.class));
      storage.putXMLSerializable(ORG_B_VS, createViewsheet(SECRET_DESC));
      storage.putXMLSerializable(ORG_B_WS, new Worksheet());
      storage.putXMLSerializable(ORG_A_VS, createViewsheet("own"));
      storage.putXMLSerializable(ORG_A_WS, new Worksheet());
      storage.putXMLSerializable(HOST_VS, createViewsheet("host"));
      storage.putXMLSerializable(OWNER_VS, createViewsheet("private"));

      sutil = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
      sutil.when(SUtil::isMultiTenant).thenReturn(true);
      assetUtil = Mockito.mockStatic(AssetUtil.class, Mockito.CALLS_REAL_METHODS);
      assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(stub);

      Cluster cluster = mock(Cluster.class);
      DistributedMap<Object, Object> map = mock(DistributedMap.class);
      when(cluster.getMap(anyString())).thenAnswer(inv -> map);
      // no security provider: checkMVPermission() grants; the path-only WRITE check after the
      // load grants too (as it does for an org admin), so the sheet access is the only guard
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(ResourceType.class), anyString(),
                                          any(ResourceAction.class))).thenReturn(true);
      mvManager = mock(MVManager.class);
      service = new MVSupportService(mvManager, securityEngine,
                                     mock(ScheduleManager.class), cluster);
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

   @Test
   void otherOrgViewsheetAndWorksheet_refused() {
      assertRefused(ORG_B_VS, orgAUser);
      assertRefused(ORG_B_WS, orgAUser);
      assertRefused(ORG_B_VS, orgAdmin);
   }

   @Test
   void otherOrgExistingAndMissing_indistinguishable() {
      Exception existing = assertThrows(Exception.class, () -> analyze(ORG_B_VS, orgAUser));
      Exception missing = assertThrows(Exception.class, () -> analyze(ORG_B_MISSING, orgAUser));

      assertEquals(existing.getClass(), missing.getClass());
      assertEquals(MessageException.class, existing.getClass());
      assertEquals(message("SecretVS77280"), existing.getMessage());
      assertEquals(message("SecretVS77280Missing"), missing.getMessage());
      assertFalse(existing.getMessage().contains(SECRET_DESC));
   }

   @Test
   void nullUser_refused() {
      assertRefused(ORG_A_VS, null);
      assertRefused(ORG_B_VS, null);
   }

   @Test
   void globallyVisibleHostViewsheet_refused() throws Exception {
      sutil.when(() -> SUtil.isDefaultVSGloballyVisible(any())).thenReturn(true);
      // control: READ alone admits the host-org viewsheet through the read-only share
      assertDoesNotThrow(() -> AssetUtil.getAssetRepository(false).checkAssetPermission(
         orgAUser, AssetEntry.createAssetEntry(HOST_VS), ResourceAction.READ));

      assertRefused(HOST_VS, orgAUser);
   }

   @Test
   void userScopeWithoutOwner_refusedWithoutNpe() {
      assertRefused(NO_OWNER_VS, orgAdmin);
   }

   @Test
   void sameOrgSheets_allowed() throws Exception {
      assertEquals("own", description(getSheet(ORG_A_VS, orgAUser)));
      assertInstanceOf(Worksheet.class, getSheet(ORG_A_WS, orgAUser));
   }

   @Test
   void userScopeViewsheet_ownerAndOrgAdminAllowed_otherUserRefused() throws Exception {
      assertEquals("private", description(getSheet(OWNER_VS, owner)));
      assertEquals("private", description(getSheet(OWNER_VS, orgAdmin)));
      assertRefused(OWNER_VS, orgAUser);
   }

   @Test
   void siteAdmin_allowedAcrossOrgs() throws Exception {
      assertEquals(SECRET_DESC, description(getSheet(ORG_B_VS, siteAdmin)));
      assertInstanceOf(Worksheet.class, getSheet(ORG_B_WS, siteAdmin));
   }

   @Test
   void reanalyzeRegisteredSheets_ownOrgAllowed_otherOrgRefused() throws Exception {
      // a plain MATERIALIZATION user re-analyzing an own-org MV whose sheet it registered
      registerMV("OwnMV77280", ORG_A_VS);
      assertNotNull(service.analyze(new String[] { "OwnMV77280" }, orgAUser));

      // an own-org MV def pointing at a foreign sheet (registered before the fix)
      registerMV("TaintedMV77280", ORG_B_VS);
      MessageException ex = assertThrows(MessageException.class,
         () -> service.analyze(new String[] { "TaintedMV77280" }, orgAUser));
      assertEquals(message("SecretVS77280"), ex.getMessage());
   }

   @Test
   void analyzeOwnOrgViewsheet_accepted() throws Exception {
      assertNotNull(service.analyze(List.of(ORG_A_VS), false, false, false, orgAUser, false,
                                    false));
   }

   private void registerMV(String name, String sheetId) {
      MVMetaData metaData = mock(MVMetaData.class);
      when(metaData.getRegisteredSheets()).thenReturn(new String[] { sheetId });
      MVDef def = mock(MVDef.class);
      when(def.getMetaData()).thenReturn(metaData);
      when(def.isWSMV()).thenReturn(false);
      when(mvManager.get(name, ORG_A)).thenReturn(def);
   }

   private void assertRefused(String identifier, Principal user) {
      MessageException ex = assertThrows(MessageException.class, () -> getSheet(identifier, user));
      assertEquals(message(AssetEntry.createAssetEntry(identifier).getPath()), ex.getMessage());
      // the public entry point refuses the same way
      assertThrows(MessageException.class, () -> analyze(identifier, user));
   }

   private void analyze(String identifier, Principal user) throws Exception {
      service.analyze(List.of(identifier), false, false, false, user, false, false);
   }

   private static AbstractSheet getSheet(String identifier, Principal user) throws Exception {
      Method method = MVSupportService.class.getDeclaredMethod(
         "getSheet", String.class, AssetEntry.class, Principal.class);
      method.setAccessible(true);

      try {
         return (AbstractSheet) method.invoke(null, identifier, null, user);
      }
      catch(InvocationTargetException ex) {
         throw (Exception) ex.getCause();
      }
   }

   private static String message(String path) {
      return Catalog.getCatalog().getString("em.common.security.no.permission", path);
   }

   private static String description(AbstractSheet sheet) {
      return ((Viewsheet) sheet).getViewsheetInfo().getDescription();
   }

   private static Viewsheet createViewsheet(String description) {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setDescription(description);
      return vs;
   }

   private static class StubAssetEngine extends AbstractAssetEngine {
      StubAssetEngine(IndexedStorage storage) {
         super((LibManagerProvider) null, (Cluster) null);
         istore = storage;
      }

      @Override
      public boolean checkPermission(Principal principal, ResourceType type, String resource,
                                     EnumSet<ResourceAction> action)
      {
         return true;
      }

      // only the org admin administers the owner of the private viewsheet
      @Override
      public boolean checkPermission(Principal principal, ResourceType type,
                                     IdentityID resource, EnumSet<ResourceAction> action)
      {
         return principal != null &&
            ORG_ADMIN.equals(IdentityID.getIdentityIDFromKey(principal.getName()).getName());
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
