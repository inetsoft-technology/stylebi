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
package inetsoft.web.admin.deploy;

import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.util.*;
import inetsoft.util.dep.*;
import inetsoft.web.admin.content.repository.*;
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.service.BinaryTransferService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #77862: export/create, get-dependent-assets and the schedule backup action save build the
 * exported assets from a client-supplied owner, and the dashboard, viewsheet, worksheet and
 * snapshot assets are read from the storage of the owner's organization. Only the advisory
 * export/check-permission preflight checked the owner, so a caller could export another user's
 * or another organization's private assets. The export must refuse them the way the preflight
 * drops them, a site admin may still export across organizations.
 *
 * Uses the real SecurityEngine / DefaultCheckPermissionStrategy (SecurityTestDataBuilder).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DeployServiceExportOwnerTest {
   private static final String ORG_A = "dxoorga";
   private static final String ORG_B = "dxoorgb";
   private static final IdentityID ALICE = new IdentityID("alice", ORG_A);
   private static final IdentityID CAROL = new IdentityID("carol", ORG_A);
   private static final IdentityID DAN = new IdentityID("dan", ORG_A);
   private static final IdentityID BOB = new IdentityID("bob", ORG_B);

   private static SecurityTestDataBuilder builder;

   private MockedStatic<SUtil> sutilStatic;
   private DeployService deployService;
   private ExportAssetService exportService;
   private FileSystemService fileSystemService;
   private SRPrincipal alice;   // org admin of org A
   private SRPrincipal carol;   // plain user of org A
   private SRPrincipal sadm;    // site admin (org A)

   /**
    * The owner-keyed asset types of the repository tree, each with the path the tree sends.
    */
   enum OwnedType {
      DASHBOARD(RepositoryEntry.DASHBOARD, SUtil.MY_DASHBOARD + "/secretDash", "DASHBOARD"),
      VIEWSHEET(RepositoryEntry.VIEWSHEET, "secretVs", "VIEWSHEET"),
      WORKSHEET(RepositoryEntry.WORKSHEET, "secretWs", "WORKSHEET");

      OwnedType(int type, String path, String assetType) {
         this.type = type;
         this.path = path;
         this.assetType = assetType;
      }

      final int type;
      final String path;
      final String assetType;
   }

   @BeforeAll
   static void setupAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("dxoOrgA", ORG_A)
         .addOrg("dxoOrgB", ORG_B)
         .addOrgAdminRole("dxoOrgAdminA", ORG_A)
         .addSysAdminRole("dxoSiteAdmin", ORG_A)
         .addUser("alice", ORG_A, "password")
         .addUser("carol", ORG_A, "password")
         .addUser("dan", ORG_A, "password")
         .addUser("sadm", ORG_A, "password")
         .addUser("bob", ORG_B, "password")
         .addUserToRole("alice", "dxoOrgAdminA", ORG_A)
         .addUserToRole("sadm", "dxoSiteAdmin", ORG_A);
      builder.setup();
   }

   @AfterAll
   static void teardownAll() {
      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() {
      sutilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutilStatic.when(SUtil::isMultiTenant).thenReturn(true);

      alice = loginPrincipalOf("alice", ORG_A);
      carol = loginPrincipalOf("carol", ORG_A);
      sadm = loginPrincipalOf("sadm", ORG_A);

      ContentRepositoryTreeService treeService = mock(ContentRepositoryTreeService.class);
      when(treeService.getUnscopedPath(anyString()))
         .thenAnswer(inv -> SUtil.getUnscopedPath(inv.getArgument(0)));
      // no registry entry, a viewsheet stays a viewsheet (not a snapshot)
      RepletRegistryService registryService = mock(RepletRegistryService.class);
      fileSystemService = mock(FileSystemService.class);
      deployService = new DeployService(treeService, SecurityEngine.getSecurity(), null, null,
                                        null, null, null, fileSystemService, registryService);
      exportService = new ExportAssetService(deployService, mock(BinaryTransferService.class),
                                             mock(Cluster.class), fileSystemService);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      sutilStatic.close();
   }

   // ── selected entities: export/create, get-dependent-assets ──────────────

   @ParameterizedTest
   @EnumSource(OwnedType.class)
   void exportCreate_otherOrgOwner_isRefused(OwnedType type) {
      ExportedAssetsModel model = exportModel(List.of(selected(type, BOB)), List.of());

      assertThrows(MessageException.class,
                   () -> as(alice, () -> exportService.createExport("id", model, alice)));
      assertThrows(MessageException.class,
                   () -> as(carol, () -> exportService.createExport("id", model, carol)));
      verifyNothingWritten();
   }

   @ParameterizedTest
   @EnumSource(OwnedType.class)
   void getDependentAssets_otherOrgOwner_isRefused(OwnedType type) {
      assertThrows(MessageException.class, () -> as(alice, () ->
         deployService.getDependentAssetsList(List.of(selected(type, BOB)), alice)));
   }

   @ParameterizedTest
   @EnumSource(OwnedType.class)
   void getEntryAssets_sameOrgOtherUser_isRefusedForPlainUser(OwnedType type) {
      assertThrows(MessageException.class, () -> as(carol, () ->
         deployService.getEntryAssets(List.of(selected(type, DAN)), carol)));
   }

   @Test
   void getEntryAssets_oneForeignEntity_refusesTheWholeRequest() {
      List<SelectedAssetModel> entities =
         List.of(selected(OwnedType.WORKSHEET, CAROL), selected(OwnedType.WORKSHEET, BOB));

      assertThrows(MessageException.class,
                   () -> as(carol, () -> deployService.getEntryAssets(entities, carol)));
   }

   @Test
   void getEntryAssets_parentSegmentInOwner_isRefused() {
      IdentityID owner = new IdentityID("..", ORG_A);

      assertThrows(MessageException.class, () -> as(sadm, () ->
         deployService.getEntryAssets(List.of(selected(OwnedType.WORKSHEET, owner)), sadm)));
   }

   @ParameterizedTest
   @EnumSource(OwnedType.class)
   void getEntryAssets_owner_isAllowed(OwnedType type) throws Exception {
      List<XAsset> assets = as(carol, () ->
         deployService.getEntryAssets(List.of(selected(type, CAROL)), carol));

      assertOwnedAsset(type, CAROL, assets);
   }

   @ParameterizedTest
   @EnumSource(OwnedType.class)
   void getEntryAssets_orgAdminOnOwnOrgUser_isAllowed(OwnedType type) throws Exception {
      List<XAsset> assets = as(alice, () ->
         deployService.getEntryAssets(List.of(selected(type, CAROL)), alice));

      assertOwnedAsset(type, CAROL, assets);
   }

   @ParameterizedTest
   @EnumSource(OwnedType.class)
   void getEntryAssets_siteAdminAcrossOrgs_isAllowed(OwnedType type) throws Exception {
      List<XAsset> assets = as(sadm, () ->
         deployService.getEntryAssets(List.of(selected(type, BOB)), sadm));

      assertOwnedAsset(type, BOB, assets);
   }

   @ParameterizedTest
   @EnumSource(OwnedType.class)
   void getEntryAssets_keptAsset_isNotChecked(OwnedType type) throws Exception {
      // the schedule backup action keeps the assets it already stores
      SelectedAssetModel entity = selected(type, BOB);
      String identifier = as(sadm, () -> deployService.getEntryAssets(List.of(entity), sadm))
         .get(0).toIdentifier();

      List<XAsset> assets = as(alice, () ->
         deployService.getEntryAssets(List.of(entity), Set.of(identifier), alice));

      assertOwnedAsset(type, BOB, assets);
   }

   @Test
   void getEntryAssets_autoSaveAsset_hasNoOwner() throws Exception {
      // an auto-save asset reports the owner __NULL__ (round-tripped by the backup action model)
      SelectedAssetModel entity = SelectedAssetModel.builder()
         .path("4^VIEWSHEET^carol~;~" + ORG_A + "^vs1^127.0.0.1")
         .type(RepositoryEntry.AUTO_SAVE_VS)
         .typeName(VSAutoSaveAsset.AUTOSAVEVS)
         .typeLabel("")
         .user(new IdentityID(XAsset.NULL, ORG_A))
         .build();

      List<XAsset> assets =
         as(carol, () -> deployService.getEntryAssets(List.of(entity), carol));

      assertEquals(1, assets.size());
      assertInstanceOf(VSAutoSaveAsset.class, assets.get(0));
   }

   // the server-side check follows the export/check-permission preflight
   @ParameterizedTest
   @EnumSource(OwnedType.class)
   void getEntryAssets_matchesThePreflight(OwnedType type) throws Exception {
      for(SRPrincipal caller : List.of(alice, carol, sadm)) {
         for(IdentityID owner : List.of(CAROL, DAN, BOB)) {
            SelectedAssetModel entity = selected(type, owner);
            boolean kept = !as(caller, () -> deployService.filterEntities(
               SelectedAssetModelList.builder().selectedAssets(List.of(entity)).build(),
               caller)).selectedAssets().isEmpty();
            boolean exported;

            try {
               as(caller, () -> deployService.getEntryAssets(List.of(entity), caller));
               exported = true;
            }
            catch(MessageException e) {
               exported = false;
            }

            assertEquals(kept, exported, caller.getName() + " exports " + owner);
         }
      }
   }

   // ── dependent assets: export/create ─────────────────────────────────────

   @ParameterizedTest
   @EnumSource(OwnedType.class)
   void exportCreate_dependentOnlyOtherOrgOwner_isRefused(OwnedType type) {
      ExportedAssetsModel model = exportModel(List.of(), List.of(required(type, BOB)));

      assertThrows(MessageException.class,
                   () -> as(alice, () -> exportService.createExport("id", model, alice)));
      verifyNothingWritten();
   }

   @ParameterizedTest
   @EnumSource(OwnedType.class)
   void exportCreate_dependentSameOrgOtherUser_isRefusedForPlainUser(OwnedType type) {
      ExportedAssetsModel model = exportModel(List.of(), List.of(required(type, DAN)));

      assertThrows(MessageException.class,
                   () -> as(carol, () -> exportService.createExport("id", model, carol)));
      verifyNothingWritten();
   }

   @Test
   void checkAssetOwner_permittedOwners_areAllowed() {
      assertDoesNotThrow(() -> deployService.checkAssetOwner(null, "ds", carol));
      assertDoesNotThrow(() -> deployService.checkAssetOwner(CAROL, "ws", carol));
      assertDoesNotThrow(() -> deployService.checkAssetOwner(CAROL, "ws", alice));
      assertDoesNotThrow(() -> deployService.checkAssetOwner(BOB, "ws", sadm));
   }

   @Test
   void checkAssetOwner_nullOwner_isOnlyCheckedForItsOrganization() {
      // the auto-save owner, the asset is built with the owner's organization
      assertDoesNotThrow(() -> deployService.checkAssetOwner(
         new IdentityID(XAsset.NULL, ORG_A), "autosave", carol));
      assertThrows(MessageException.class, () -> deployService.checkAssetOwner(
         new IdentityID(XAsset.NULL, ORG_B), "autosave", carol));
   }

   private void assertOwnedAsset(OwnedType type, IdentityID owner, List<XAsset> assets) {
      assertEquals(1, assets.size());
      assertEquals(type.assetType, assets.get(0).getType());
      assertEquals(owner, assets.get(0).getUser());
   }

   private void verifyNothingWritten() {
      verify(fileSystemService, never()).getCacheFile(anyString());
   }

   private static SelectedAssetModel selected(OwnedType type, IdentityID owner) {
      return SelectedAssetModel.builder()
         .path(type.path)
         .type(type.type)
         .typeName(type.assetType)
         .typeLabel("")
         .user(owner)
         .build();
   }

   private static RequiredAssetModel required(OwnedType type, IdentityID owner) {
      return RequiredAssetModel.builder()
         .name(SUtil.getUnscopedPath(type.path))
         .type(type.assetType)
         .user(owner)
         .lastModifiedTime(0)
         .build();
   }

   private static ExportedAssetsModel exportModel(List<SelectedAssetModel> selected,
                                                  List<RequiredAssetModel> dependents)
   {
      return ExportedAssetsModel.builder()
         .name("export77862")
         .selectedEntities(selected)
         .dependentAssets(dependents)
         .build();
   }

   private static <T> T as(Principal principal, Callable<T> call) throws Exception {
      Principal old = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(principal);

      try {
         return call.call();
      }
      finally {
         ThreadContext.setContextPrincipal(old);
      }
   }

   private static SRPrincipal loginPrincipalOf(String name, String orgID) {
      SRPrincipal principal = builder.principalOf(name, orgID);
      principal.setProperty("__internal__", "true");
      return principal;
   }
}
