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

import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Drivers;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.types.AccessDatabaseType;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.model.MoveCopyTreeNodesRequest;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.data.*;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.session.IgniteSessionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77673: a data source or a data source folder must not be moved or renamed onto a path
 * that is already used by a data source or a data source folder. The move would overwrite or
 * merge the target, or turn the data sources of a folder into additional connections of a data
 * source with the same path. The move must be refused before anything is changed, while a move to
 * a free path and a move to the current location work as before. The registry and the repository
 * are the real ones.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourceMoveTargetExistsTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceMoveTargetExistsTest {
   private static final String URL = "jdbc:derby:memory:bug77673;create=true";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   private SecurityEngine security;
   private RepositoryObjectService objectService;
   private DataSourceBrowserService browserService;
   private DatabaseDatasourcesService databaseService;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      // the permission checks of the repository throw if denied, a mock lets everything pass
      security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      ResourcePermissionService permissions = mock(ResourcePermissionService.class);
      when(permissions.getRepositoryResourceType(anyInt(), anyString())).thenAnswer(
         inv -> new Resource(ResourceType.DATA_SOURCE, inv.<String>getArgument(1)));
      // a move saves the replet registry of the destination owner at the end
      RepletRegistryManager repletRegistries = mock(RepletRegistryManager.class);
      when(repletRegistries.getRegistry(nullable(IdentityID.class))).thenReturn(mock(RepletRegistry.class));
      objectService = new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class),
         mock(SecurityProvider.class), permissions, repository,
         mock(RepositoryDashboardService.class), mock(DataModelFolderManagerService.class),
         registry, mock(LibManagerProvider.class), mock(RecycleBin.class),
         mock(DependencyHandler.class), mock(RenameTransformHandler.class), repletRegistries,
         mock(DashboardRegistryManager.class));
      browserService = new DataSourceBrowserService(
         security, objectService, repository, mock(DataSourceService.class), registry,
         mock(Config.class), mock(RenameTransformHandler.class));
      databaseService = new DatabaseDatasourcesService(
         new DatabaseTypeService(List.of(new CustomDatabaseType(), new AccessDatabaseType())),
         security, mock(DatabaseSettingsService.class), repository,
         mock(ResourcePermissionService.class), mock(DataSourceStatusService.class),
         mock(IgniteSessionRepository.class), registry, mock(RenameTransformHandler.class));
      principal = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());
   }

   // the helper sees a data source, an additional connection and a folder at the exact path
   @Test
   void pathInUseHelper() throws Exception {
      folder("useF");
      addParent("useF/useP", "useAdd");

      assertTrue(registry.isDataSourcePathInUse("useF"));
      assertTrue(registry.isDataSourcePathInUse("useF/useP"));
      assertTrue(registry.isDataSourcePathInUse("useF/useP/useAdd"));
      assertFalse(registry.isDataSourcePathInUse("useF/useQ"));
      assertFalse(registry.isDataSourcePathInUse("USEF"));
      assertFalse(registry.isDataSourcePathInUse(null));
   }

   // EM: a folder moved onto a folder with the same name is refused, nothing is merged (R3)
   @Test
   void emFolderOntoFolderIsRefused() throws Exception {
      folder("mrgF");
      dataSource("mrgF/mrgX");
      folder("mrgG");
      folder("mrgG/mrgF");
      dataSource("mrgG/mrgF/mrgY");

      MessageException ex = assertThrows(MessageException.class, () -> objectService.moveFiles(
         request(root(), node("mrgG/mrgF", RepositoryEntry.DATA_SOURCE_FOLDER)), true, principal));
      assertTrue(ex.getMessage().contains("mrgF"), ex.getMessage());

      registry.clearCache();
      assertEquals(List.of("mrgF/mrgX"), registry.getSubDataSourceNames("mrgF", false));
      assertEquals(List.of("mrgG/mrgF/mrgY"),
                   registry.getSubDataSourceNames("mrgG/mrgF", false));
      assertNotNull(registry.getDataSourceFolder("mrgG/mrgF"));
      assertNull(registry.getDataSource("mrgF/mrgY"));
   }

   // EM: a data source moved onto the name of a folder is refused, the data sources of the
   // folder don't become its additional connections (R4)
   @Test
   void emDataSourceOntoFolderNameIsRefused() throws Exception {
      folder("dofA");
      dataSource("dofA/dofChild");
      folder("dofF");
      addParent("dofF/dofA", "dofAdd");

      assertThrows(MessageException.class, () -> objectService.moveFiles(
         request(root(), node("dofF/dofA", RepositoryEntry.DATA_SOURCE | RepositoryEntry.FOLDER)),
         true, principal));

      registry.clearCache();
      assertNull(registry.getDataSource("dofA"));
      assertNotNull(registry.getDataSourceFolder("dofA"));
      assertNotNull(registry.getDataSource("dofA/dofChild"));
      assertFalse(registry.isAdditionalConnectionPath("dofA/dofChild"));
      assertChildren("dofF/dofA", "dofAdd");
   }

   // EM: a folder moved onto the name of a data source is refused, the data sources of the
   // folder don't become additional connections of the data source (R11)
   @Test
   void emFolderOntoDataSourceNameIsRefused() throws Exception {
      addParent("fodX", "fodAdd");
      folder("fodG");
      folder("fodG/fodX");
      dataSource("fodG/fodX/fodKid");

      assertThrows(MessageException.class, () -> objectService.moveFiles(
         request(root(), node("fodG/fodX", RepositoryEntry.DATA_SOURCE_FOLDER)), true, principal));

      registry.clearCache();
      assertNull(registry.getDataSourceFolder("fodX"));
      assertChildren("fodX", "fodAdd");
      assertNotNull(registry.getDataSourceFolder("fodG/fodX"));
      assertNotNull(registry.getDataSource("fodG/fodX/fodKid"));
      assertNull(registry.getDataSource("fodX/fodKid"));
   }

   // EM: two folders with the same name moved to the same folder in one batch are refused, and
   // nothing of the batch is moved (R13)
   @Test
   void emInBatchDuplicateIsRefused() throws Exception {
      folder("dupG");
      folder("dupG/dupF");
      dataSource("dupG/dupF/dupA");
      folder("dupH");
      folder("dupH/dupF");
      dataSource("dupH/dupF/dupB");

      assertThrows(MessageException.class, () -> objectService.moveFiles(
         request(root(), node("dupG/dupF", RepositoryEntry.DATA_SOURCE_FOLDER),
                 node("dupH/dupF", RepositoryEntry.DATA_SOURCE_FOLDER)), true, principal));

      registry.clearCache();
      assertNull(registry.getDataSourceFolder("dupF"));
      assertNotNull(registry.getDataSource("dupG/dupF/dupA"));
      assertNotNull(registry.getDataSource("dupH/dupF/dupB"));
      assertNull(registry.getDataSource("dupF/dupA"));
      assertNull(registry.getDataSource("dupF/dupB"));
   }

   // EM: a batch with a free move and a clash moves nothing
   @Test
   void emBatchWithAClashMovesNothing() throws Exception {
      folder("bcDest");
      folder("bcDest/bcTaken");
      folder("bcSrc");
      dataSource("bcSrc/bcFree");
      dataSource("bcSrc/bcTaken");

      assertThrows(MessageException.class, () -> objectService.moveFiles(
         request(dest("bcDest"),
                 node("bcSrc/bcFree", RepositoryEntry.DATA_SOURCE | RepositoryEntry.FOLDER),
                 node("bcSrc/bcTaken", RepositoryEntry.DATA_SOURCE | RepositoryEntry.FOLDER)),
         true, principal));

      registry.clearCache();
      assertNotNull(registry.getDataSource("bcSrc/bcFree"));
      assertNull(registry.getDataSource("bcDest/bcFree"));
      assertNotNull(registry.getDataSource("bcSrc/bcTaken"));
      assertNull(registry.getDataSource("bcDest/bcTaken"));
   }

   // EM: the additional connection message of Bug #77670 is still reported first
   @Test
   void emAdditionalConnectionMessageComesFirst() throws Exception {
      folder("acmDest");
      folder("acmDest/acmTaken");
      folder("acmSrc");
      folder("acmSrc/acmTaken");
      addParent("acmParent", "acmAdd");

      MessageException ex = assertThrows(MessageException.class, () -> objectService.moveFiles(
         request(dest("acmDest"), node("acmSrc/acmTaken", RepositoryEntry.DATA_SOURCE_FOLDER),
                 node("acmParent/acmAdd", RepositoryEntry.DATA_SOURCE)), true, principal));
      assertTrue(ex.getMessage().contains("additional connection"), ex.getMessage());
   }

   // EM: a move to the current folder is a no-op and is not refused (diagnosis #6)
   @Test
   void emSameLocationIsANoOp() throws Exception {
      folder("sameF");
      addParent("sameF/sameA", "sameAdd");
      folder("sameF/sameSub");
      dataSource("sameF/sameSub/sameKid");
      addParent("sameTop", "sameTopAdd");

      objectService.moveFiles(
         request(dest("sameF"),
                 node("sameF/sameA", RepositoryEntry.DATA_SOURCE | RepositoryEntry.FOLDER),
                 node("sameF/sameSub", RepositoryEntry.DATA_SOURCE_FOLDER)), true, principal);
      objectService.moveFiles(
         request(root(), node("sameTop", RepositoryEntry.DATA_SOURCE | RepositoryEntry.FOLDER)),
         true, principal);

      registry.clearCache();
      assertChildren("sameF/sameA", "sameAdd");
      assertNotNull(registry.getDataSource("sameF/sameSub/sameKid"));
      assertChildren("sameTop", "sameTopAdd");
   }

   // EM: a folder holding a data source with additional connections still moves to a free path
   // (R12), to another folder and to the root
   @Test
   void emFolderWithAdditionalConnectionsStillMoves() throws Exception {
      folder("freeF");
      addParent("freeF/freeP", "freeAdd", "freeKeep");
      folder("freeH");

      objectService.moveFiles(
         request(dest("freeH"), node("freeF", RepositoryEntry.DATA_SOURCE_FOLDER)),
         true, principal);

      registry.clearCache();
      assertNull(registry.getDataSource("freeF/freeP"));
      assertChildren("freeH/freeF/freeP", "freeAdd", "freeKeep");
      assertTrue(registry.isAdditionalConnectionPath("freeH/freeF/freeP/freeAdd"));

      objectService.moveFiles(
         request(root(), node("freeH/freeF", RepositoryEntry.DATA_SOURCE_FOLDER)),
         true, principal);

      registry.clearCache();
      assertNull(registry.getDataSource("freeH/freeF/freeP"));
      assertChildren("freeF/freeP", "freeAdd", "freeKeep");
   }

   // portal: a folder moved onto a folder with the same name is refused (R14), whatever the
   // client checked before
   @Test
   void portalFolderOntoFolderIsRefused() throws Exception {
      folder("pmF");
      dataSource("pmF/pmA");
      folder("pmG");
      folder("pmG/pmF");
      dataSource("pmG/pmF/pmB");

      assertThrows(MessageException.class, () -> browserService.moveDataSource(
         new MoveCommand[] { move("pmG/pmF", "pmF", PortalDataType.DATA_SOURCE_FOLDER) },
         principal));

      registry.clearCache();
      assertEquals(List.of("pmF/pmA"), registry.getSubDataSourceNames("pmF", false));
      assertNotNull(registry.getDataSource("pmG/pmF/pmB"));
      assertNull(registry.getDataSource("pmF/pmB"));
   }

   // portal: a hand-made move of a data source onto a folder name is refused, and a batch with a
   // free move and a clash moves nothing
   @Test
   void portalDataSourceOntoFolderNameIsRefused() throws Exception {
      folder("pdA");
      dataSource("pdA/pdChild");
      folder("pdF");
      addParent("pdF/pdA", "pdAdd");
      dataSource("pdF/pdFree");

      assertThrows(MessageException.class, () -> browserService.moveDataSource(
         new MoveCommand[] { move("pdF/pdFree", "pdFree", PortalDataType.DATABASE),
                             move("pdF/pdA", "pdA", PortalDataType.DATABASE) },
         principal));

      registry.clearCache();
      assertNull(registry.getDataSource("pdA"));
      assertNull(registry.getDataSource("pdFree"));
      assertNotNull(registry.getDataSource("pdF/pdFree"));
      assertFalse(registry.isAdditionalConnectionPath("pdA/pdChild"));
      assertChildren("pdF/pdA", "pdAdd");
   }

   // portal: two items with the same target in one batch are refused
   @Test
   void portalInBatchDuplicateIsRefused() throws Exception {
      folder("pbG");
      folder("pbG/pbF");
      folder("pbH");
      folder("pbH/pbF");

      assertThrows(MessageException.class, () -> browserService.moveDataSource(
         new MoveCommand[] { move("pbG/pbF", "pbF", PortalDataType.DATA_SOURCE_FOLDER),
                             move("pbH/pbF", "pbF", PortalDataType.DATA_SOURCE_FOLDER) },
         principal));

      registry.clearCache();
      assertNull(registry.getDataSourceFolder("pbF"));
      assertNotNull(registry.getDataSourceFolder("pbG/pbF"));
      assertNotNull(registry.getDataSourceFolder("pbH/pbF"));
   }

   // portal: a free move and a same-location move still work
   @Test
   void portalFreeAndSameLocationMovesWork() throws Exception {
      folder("pwF");
      addParent("pwF/pwP", "pwAdd");
      folder("pwG");

      browserService.moveDataSource(
         new MoveCommand[] { move("pwF/pwP", "pwF/pwP", PortalDataType.DATABASE) }, principal);
      assertChildren("pwF/pwP", "pwAdd");

      browserService.moveDataSource(
         new MoveCommand[] { move("pwF", "pwG/pwF", PortalDataType.DATA_SOURCE_FOLDER) },
         principal);

      registry.clearCache();
      assertNull(registry.getDataSourceFolder("pwF"));
      assertChildren("pwG/pwF/pwP", "pwAdd");
   }

   // portal folder rename onto a sibling folder or data source name is refused, a free rename
   // and a case-only rename work (R15)
   @Test
   void portalFolderRename() throws Exception {
      folder("prP");
      folder("prP/prA");
      dataSource("prP/prA/prKid");
      folder("prP/prB");
      dataSource("prP/prDs");

      assertThrows(MessageException.class,
                   () -> browserService.renameFolder("prP/prA", "prB", null, null, principal));
      assertThrows(MessageException.class,
                   () -> browserService.renameFolder("prP/prA", "prDs", null, null, principal));

      registry.clearCache();
      assertNotNull(registry.getDataSource("prP/prA/prKid"));
      assertNull(registry.getDataSource("prP/prB/prKid"));
      assertNull(registry.getDataSource("prP/prDs/prKid"));
      assertFalse(registry.isAdditionalConnectionPath("prP/prDs/prKid"));

      assertEquals("prP/prC", browserService.renameFolder("prP/prA", "prC", null, null, principal));
      assertEquals("prP/PRC", browserService.renameFolder("prP/prC", "PRC", null, null, principal));

      registry.clearCache();
      assertNotNull(registry.getDataSource("prP/PRC/prKid"));
      assertNull(registry.getDataSourceFolder("prP/prA"));
   }

   // EM folder rename onto a sibling folder or data source name is refused, a free rename works
   @Test
   void emFolderRename() throws Exception {
      folder("erA");
      dataSource("erA/erKid");
      folder("erB");
      dataSource("erDs");

      assertThrows(MessageException.class,
                   () -> databaseService.setDataSourceFolder("erA", folderModel("erB"), principal));
      assertThrows(MessageException.class,
                   () -> databaseService.setDataSourceFolder("erA", folderModel("erDs"), principal));

      registry.clearCache();
      assertNotNull(registry.getDataSource("erA/erKid"));
      assertNull(registry.getDataSource("erB/erKid"));
      assertNull(registry.getDataSource("erDs/erKid"));

      assertEquals("erC", databaseService.setDataSourceFolder("erA", folderModel("erC"), principal));

      registry.clearCache();
      assertNotNull(registry.getDataSource("erC/erKid"));
      assertNull(registry.getDataSourceFolder("erA"));
   }

   // EM: a data source moved into a folder that already has a data source with the same name is
   // refused, neither data source is overwritten and their additional connections are not merged
   // (the reported case, diagnosis #1)
   @Test
   void emDataSourceOntoDataSourceIsRefused() throws Exception {
      addParent("dodA", "dodOuterAdd");
      folder("dodF");
      addParent("dodF/dodA", "dodInnerAdd");
      JDBCDataSource outer = (JDBCDataSource) registry.getDataSource("dodA");
      outer.setDescription("outer");
      registry.setDataSource(outer, false);
      JDBCDataSource inner = (JDBCDataSource) registry.getDataSource("dodF/dodA");
      inner.setDescription("inner");
      registry.setDataSource(inner, false);

      MessageException ex = assertThrows(MessageException.class, () -> objectService.moveFiles(
         request(dest("dodF"), node("dodA", RepositoryEntry.DATA_SOURCE | RepositoryEntry.FOLDER)),
         true, principal));
      assertTrue(ex.getMessage().contains("dodF/dodA"), ex.getMessage());

      assertChildren("dodA", "dodOuterAdd");
      assertChildren("dodF/dodA", "dodInnerAdd");
      assertEquals("outer", registry.getDataSource("dodA").getDescription());
      assertEquals("inner", registry.getDataSource("dodF/dodA").getDescription());
   }

   // multi-tenant: a path used only by a globally shared data source of the host organization is
   // free in another organization, a move onto it there is not refused and the host organization's
   // data source is not touched
   @Test
   void hostOrgGlobalShareIsNotAClash() throws Exception {
      String hostOrg = Organization.getDefaultOrganizationID();
      String otherOrg = "bug77673org";
      addParent("gsShared", "gsHostAdd");

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS)) {
         sutil.when(SUtil::isDefaultVSGloballyVisible).thenReturn(true);
         sutil.when(() -> SUtil.isDefaultVSGloballyVisible(any())).thenReturn(true);
         OrganizationContextHolder.setCurrentOrgId(otherOrg);

         try {
            // creates the registry root of the other organization
            registry.init();
            registry.clearCache();
            folder("gsF");
            addParent("gsF/gsShared", "gsOtherAdd");

            assertFalse(registry.isDataSourcePathInUse("gsShared"));
            objectService.moveFiles(
               request(root(),
                       node("gsF/gsShared", RepositoryEntry.DATA_SOURCE | RepositoryEntry.FOLDER)),
               true, principal);

            registry.clearCache();
            assertTrue(registry.containObject(new AssetEntry(AssetRepository.QUERY_SCOPE,
               AssetEntry.Type.DATA_SOURCE, "gsShared", null)));
            assertFalse(registry.containObject(new AssetEntry(AssetRepository.QUERY_SCOPE,
               AssetEntry.Type.DATA_SOURCE, "gsF/gsShared", null)));
            assertChildren("gsShared", "gsOtherAdd");
         }
         finally {
            OrganizationContextHolder.clear();
         }
      }

      registry.clearCache();
      assertEquals(hostOrg, OrganizationManager.getInstance().getCurrentOrgID());
      assertChildren("gsShared", "gsHostAdd");
   }

   // the helper finds the data source that a path lies under, at the exact path
   @Test
   void dataSourceAncestorHelper() throws Exception {
      folder("ancF");
      addParent("ancF/ancP", "ancAdd");

      assertEquals("ancF/ancP", registry.getDataSourceAncestor("ancF/ancP/ancX"));
      assertEquals("ancF/ancP", registry.getDataSourceAncestor("ancF/ancP/ancAdd"));
      assertNull(registry.getDataSourceAncestor("ancF/ancP"));
      assertNull(registry.getDataSourceAncestor("ancF/ancX"));
      assertNull(registry.getDataSourceAncestor("ANCF/ANCP/ancX"));
      assertNull(registry.getDataSourceAncestor("ancX"));
      assertNull(registry.getDataSourceAncestor(null));
   }

   // EM: a hand-made move of a data source or a folder under a data source (to a free path) is
   // refused, neither becomes part of the data source (review r1 I-1)
   @Test
   void emMoveUnderADataSourceIsRefused() throws Exception {
      addParent("emuP", "emuAdd");
      folder("emuF");
      dataSource("emuF/emuX");
      folder("emuG");
      dataSource("emuG/emuKid");

      MessageException ex = assertThrows(MessageException.class, () -> objectService.moveFiles(
         request(node("emuP", RepositoryEntry.DATA_SOURCE | RepositoryEntry.FOLDER),
                 node("emuF/emuX", RepositoryEntry.DATA_SOURCE | RepositoryEntry.FOLDER)),
         true, principal));
      assertTrue(ex.getMessage().contains("emuP"), ex.getMessage());
      assertThrows(MessageException.class, () -> objectService.moveFiles(
         request(node("emuP", RepositoryEntry.DATA_SOURCE | RepositoryEntry.FOLDER),
                 node("emuG", RepositoryEntry.DATA_SOURCE_FOLDER)), true, principal));

      registry.clearCache();
      assertNotNull(registry.getDataSource("emuF/emuX"));
      assertNull(registry.getDataSource("emuP/emuX"));
      assertNotNull(registry.getDataSourceFolder("emuG"));
      assertNull(registry.getDataSourceFolder("emuP/emuG"));
      assertNotNull(registry.getDataSource("emuG/emuKid"));
      assertChildren("emuP", "emuAdd");
   }

   // portal: a hand-made move of a data source or a folder under a data source is refused
   @Test
   void portalMoveUnderADataSourceIsRefused() throws Exception {
      addParent("pmuP", "pmuAdd");
      folder("pmuF");
      dataSource("pmuF/pmuX");
      folder("pmuG");

      MessageException ex = assertThrows(MessageException.class,
         () -> browserService.moveDataSource(
            new MoveCommand[] { move("pmuF/pmuX", "pmuP/pmuX", PortalDataType.DATABASE) },
            principal));
      assertTrue(ex.getMessage().contains("pmuP"), ex.getMessage());
      assertThrows(MessageException.class, () -> browserService.moveDataSource(
         new MoveCommand[] { move("pmuG", "pmuP/pmuG", PortalDataType.DATA_SOURCE_FOLDER) },
         principal));

      registry.clearCache();
      assertNotNull(registry.getDataSource("pmuF/pmuX"));
      assertNull(registry.getDataSource("pmuP/pmuX"));
      assertNotNull(registry.getDataSourceFolder("pmuG"));
      assertNull(registry.getDataSourceFolder("pmuP/pmuG"));
      assertChildren("pmuP", "pmuAdd");
   }

   // portal and EM folder renames with a slash in the name can't put the folder under a data
   // source
   @Test
   void folderRenameUnderADataSourceIsRefused() throws Exception {
      folder("fruP");
      folder("fruP/fruA");
      dataSource("fruP/fruA/fruKid");
      dataSource("fruP/fruDs");
      folder("fruB");
      dataSource("fruB/fruKid2");
      dataSource("fruDs2");

      assertThrows(MessageException.class, () -> browserService.renameFolder(
         "fruP/fruA", "fruDs/fruA", null, null, principal));
      assertThrows(MessageException.class, () -> databaseService.setDataSourceFolder(
         "fruB", folderModel("fruDs2/fruB"), principal));

      registry.clearCache();
      assertNotNull(registry.getDataSource("fruP/fruA/fruKid"));
      assertNull(registry.getDataSourceFolder("fruP/fruDs/fruA"));
      assertNotNull(registry.getDataSource("fruB/fruKid2"));
      assertNull(registry.getDataSourceFolder("fruDs2/fruB"));
   }

   // moves into folders whose names only look like a data source path are not refused: a folder
   // whose name starts with a data source's name, and a folder with a data source's name at
   // another path; a data source with additional connections also moves into such a folder
   @Test
   void moveNextToADataSourceStillWorks() throws Exception {
      addParent("nbP", "nbAdd");
      folder("nbPx");
      folder("nbF");
      dataSource("nbF/nbX");
      folder("nbF/nbG");
      dataSource("nbF/nbG/nbKid");
      folder("nbH");
      folder("nbH/nbP");

      assertNull(registry.getDataSourceAncestor("nbPx/nbX"));
      assertNull(registry.getDataSourceAncestor("nbH/nbP/nbG"));

      objectService.moveFiles(
         request(dest("nbPx"), node("nbF/nbX", RepositoryEntry.DATA_SOURCE | RepositoryEntry.FOLDER),
                 node("nbP", RepositoryEntry.DATA_SOURCE | RepositoryEntry.FOLDER)),
         true, principal);
      browserService.moveDataSource(
         new MoveCommand[] { move("nbF/nbG", "nbH/nbP/nbG", PortalDataType.DATA_SOURCE_FOLDER) },
         principal);

      registry.clearCache();
      assertNotNull(registry.getDataSource("nbPx/nbX"));
      assertFalse(registry.isAdditionalConnectionPath("nbPx/nbX"));
      assertNull(registry.getDataSource("nbP"));
      assertChildren("nbPx/nbP", "nbAdd");
      assertNotNull(registry.getDataSourceFolder("nbH/nbP/nbG"));
      assertNotNull(registry.getDataSource("nbH/nbP/nbG/nbKid"));
      assertNull(registry.getDataSourceFolder("nbF/nbG"));
   }

   private void folder(String path) {
      registry.setDataSourceFolder(new DataSourceFolder(path, LocalDateTime.now(), null));
   }

   private void dataSource(String path) throws Exception {
      registry.setDataSource(source(path), false);
   }

   // the names of the additional connections start with a prefix of their own, since every test
   // of the class saves to the same storage
   private void addParent(String path, String... additionals) throws Exception {
      registry.setDataSource(source(path), false);
      JDBCDataSource parent = (JDBCDataSource) registry.getDataSource(path);

      for(String name : additionals) {
         parent.addDatasource(source(name));
      }
   }

   private void assertChildren(String parentPath, String... names) {
      // read from the storage, not from instances cached by the move
      registry.clearCache();
      XDataSource parent = registry.getDataSource(parentPath);
      assertNotNull(parent, parentPath);
      String[] expected = names.clone();
      Arrays.sort(expected);
      String[] children = ((JDBCDataSource) parent).getDataSourceNames();
      Arrays.sort(children);
      assertArrayEquals(expected, children, "additional connections of " + parentPath);
   }

   private static DataSourceFolderSettingsModel folderModel(String name) {
      return DataSourceFolderSettingsModel.builder()
         .name(name)
         .root(false)
         .build();
   }

   private static MoveCommand move(String oldPath, String path, PortalDataType type) {
      MoveCommand move = new MoveCommand();
      move.setOldPath(oldPath);
      move.setPath(path);
      move.setName(path.substring(path.lastIndexOf('/') + 1));
      move.setType(type.name());
      return move;
   }

   private static MoveCopyTreeNodesRequest request(ContentRepositoryTreeNode destination,
                                                   ContentRepositoryTreeNode... source)
   {
      return MoveCopyTreeNodesRequest.builder()
         .source(List.of(source))
         .destination(destination)
         .build();
   }

   private static ContentRepositoryTreeNode root() {
      return node("/", RepositoryEntry.DATA_SOURCE_FOLDER);
   }

   private static ContentRepositoryTreeNode dest(String path) {
      return node(path, RepositoryEntry.DATA_SOURCE_FOLDER);
   }

   private static ContentRepositoryTreeNode node(String path, int type) {
      int index = path.lastIndexOf('/');
      return ContentRepositoryTreeNode.builder()
         .label(index < 0 ? path : path.substring(index + 1))
         .path(path)
         .type(type)
         .build();
   }

   private static JDBCDataSource source(String name) {
      JDBCDataSource dataSource = new JDBCDataSource();
      dataSource.setName(name);
      dataSource.setCustom(true);
      dataSource.setCustomEditMode(true);
      dataSource.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      dataSource.setURL(URL);
      dataSource.setCustomUrl(URL);
      return dataSource;
   }

   @Configuration
   static class Beans {
      @Bean
      public RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }

      // the constructors of these beans are package private
      @Bean
      public DependencyStorageService dependencyStorageService(KeyValueStorageManager storage)
         throws Exception
      {
         Constructor<DependencyStorageService> ctor =
            DependencyStorageService.class.getDeclaredConstructor(KeyValueStorageManager.class);
         ctor.setAccessible(true);
         return ctor.newInstance(storage);
      }

      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      // loads the embedded Derby driver of the test sources
      @Bean
      @Primary
      public Drivers testDrivers() throws Exception {
         Drivers drivers = mock(Drivers.class);
         when(drivers.getDriverClass(anyString()))
            .thenAnswer(inv -> Class.forName(inv.<String>getArgument(0)));
         return drivers;
      }
   }
}
