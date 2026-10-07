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
 * Bug #77947: the EM auto-save endpoints (delete, restore, gettime) and the repository tree delete
 * of auto-save nodes are gated only by the EM_COMPONENT settings/content/repository grant, which an
 * administrator can delegate to a plain user. They acted on any auto-save file named by the client,
 * so the delegate could delete another user's auto-save, restore it into a global sheet of its own
 * choosing, and probe its existence and time.
 *
 * The owner rule of the auto-save export (XAssetExportPermission.isAutoSavePermitted, #77924) now
 * applies: the owner from the file name, a user with ADMIN on the owner, a site or organization
 * administrator; a file without an owner only for the administrators.
 *
 * The controller is called through an AspectJ proxy with the real SecuredAspect, so the delegate
 * passes the product's own gate. ComponentAuthorizationService is mocked because
 * view-components.json is not on the test classpath, AutoSaveServiceProxy delegates to a real
 * AutoSaveService (only the cluster hop is skipped), and Drivers is mocked to load the sheet class
 * because the test has no plugins.
 */

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Drivers;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import inetsoft.web.*;
import inetsoft.web.admin.authz.ComponentAuthorizationService;
import inetsoft.web.admin.authz.ViewComponent;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.model.TreeNodeInfo;
import inetsoft.web.admin.deploy.XAssetExportPermission;
import inetsoft.web.security.SecuredAspect;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  AutoSaveOwnerPermissionTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // over 10 s alone: the Spring context with the real asset repository
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutoSaveOwnerPermissionTest {
   @Configuration
   static class Config {
      @Bean
      @Primary
      PortalThemesManager portalThemesManager() {
         PortalThemesManager manager = mock(PortalThemesManager.class);
         when(manager.getCssEntries()).thenReturn(new HashMap<>());
         return manager;
      }

      @Bean
      @Primary
      Drivers drivers() throws Exception {
         Drivers drivers = mock(Drivers.class);
         when(drivers.getDriverClass(anyString(), any()))
            .thenAnswer(inv -> Class.forName(inv.getArgument(0)));
         return drivers;
      }
   }

   @BeforeAll
   void setupAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("a947Org", ORG)
         .addOrgAdminRole("a947OrgAdmin", ORG)
         .addUser("oadm", ORG, "password")
         .addUser("del", ORG, "password")
         .addUser("owner", ORG, "password")
         .addSysAdminRole("a947SiteAdmin", HOST_ORG)
         .addUser("sadm", HOST_ORG, "password")
         .addUserToRole("sadm", "a947SiteAdmin", HOST_ORG)
         .addUserToRole("oadm", "a947OrgAdmin", ORG)
         .grantPermission(ResourceType.EM, "*", ResourceAction.ACCESS,
                          "del", Identity.USER, ORG)
         .grantPermission(ResourceType.EM_COMPONENT, "settings/content/repository",
                          ResourceAction.ACCESS, "del", Identity.USER, ORG)
         .grantPermission(ResourceType.ASSET, "/", ResourceAction.READ,
                          "del", Identity.USER, ORG)
         .grantPermission(ResourceType.ASSET, "/", ResourceAction.WRITE,
                          "del", Identity.USER, ORG)
         .grantPermission(ResourceType.REPORT, "/", ResourceAction.DELETE,
                          "del", Identity.USER, ORG);
      builder.setup();
   }

   @AfterAll
   void teardownAll() {
      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() {
      sutilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      oadm = builder.principalOf("oadm", ORG);
      del = builder.principalOf("del", ORG);
      owner = builder.principalOf("owner", ORG);
      // a site admin that switched to the organization in the EM
      sadm = builder.principalOf("sadm", HOST_ORG);
      sadm.setProperty("curr_org_id", ORG);

      AutoSaveService service = new AutoSaveService(viewsheetService);
      AutoSaveServiceProxy serviceProxy = mock(AutoSaveServiceProxy.class, inv ->
         "restoreAutoSaveAssets".equals(inv.getMethod().getName()) ?
            service.restoreAutoSaveAssets(inv.getArgument(0), inv.getArgument(1),
                                          inv.getArgument(2), inv.getArgument(3)) : null);
      ComponentAuthorizationService components = mock(ComponentAuthorizationService.class);
      when(components.getComponent("settings/content/repository"))
         .thenReturn(ViewComponent.builder().name("repository").label("Repository").build());
      AspectJProxyFactory factory = new AspectJProxyFactory(
         new AutoSaveController(serviceProxy, mock(IndexedStorage.class)));
      factory.setProxyTargetClass(true);
      factory.addAspect(new SecuredAspect(mock(DataSourceRegistry.class), components));
      controller = factory.getProxy();

      ResourcePermissionService resourcePermissionService = mock(ResourcePermissionService.class);
      when(resourcePermissionService.getRepositoryResourceType(anyInt(), anyString()))
         .thenAnswer(inv -> new Resource(ResourceType.REPORT, inv.getArgument(1)));
      objectService = new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class),
         mock(SecurityProvider.class), resourcePermissionService, mock(XRepository.class),
         mock(RepositoryDashboardService.class), mock(DataModelFolderManagerService.class),
         mock(DataSourceRegistry.class), mock(LibManagerProvider.class), mock(RecycleBin.class),
         mock(DependencyHandler.class), mock(RenameTransformHandler.class),
         mock(RepletRegistryManager.class), mock(DashboardRegistryManager.class));
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      OrganizationContextHolder.setCurrentOrgId(null);
      sutilStatic.close();
   }

   @Test
   void delegateCannotDeleteAnotherUsersAutoSave() throws Exception {
      String file = recycledViewsheet(owner, "Del-1");

      assertThrows(MessageException.class, () -> as(del, () -> {
         controller.deleteAutoSaveAssets(Map.of("ids", file), del);
         return null;
      }));

      assertTrue(exists(file));
   }

   // every file is checked before any is deleted, so the delegate's own file is kept as well
   @Test
   void delegateBatchWithAnotherUsersAutoSaveDeletesNothing() throws Exception {
      String own = recycledViewsheet(del, "Own-1");
      String other = recycledViewsheet(owner, "Other-1");

      assertThrows(MessageException.class, () -> as(del, () -> {
         controller.deleteAutoSaveAssets(Map.of("ids", own + "," + other), del);
         return null;
      }));

      assertTrue(exists(own));
      assertTrue(exists(other));
   }

   @Test
   void delegateCanDeleteOwnAutoSave() throws Exception {
      String own = recycledViewsheet(del, "Own-2");

      as(del, () -> {
         controller.deleteAutoSaveAssets(Map.of("ids", own), del);
         return null;
      });

      assertFalse(exists(own));
   }

   @Test
   void orgAdminCanDeleteAnotherUsersAutoSave() throws Exception {
      String file = recycledViewsheet(owner, "Del-2");

      as(oadm, () -> {
         controller.deleteAutoSaveAssets(Map.of("ids", file), oadm);
         return null;
      });

      assertFalse(exists(file));
   }

   @Test
   void siteAdminCanDeleteAnotherUsersAutoSave() throws Exception {
      String file = recycledViewsheet(owner, "Del-3");

      as(sadm, () -> {
         controller.deleteAutoSaveAssets(Map.of("ids", file), sadm);
         return null;
      });

      assertFalse(exists(file));
   }

   @Test
   void delegateCannotRestoreAnotherUsersAutoSave() throws Exception {
      String file = recycledWorksheet(owner, "Ws-1");

      assertThrows(MessageException.class, () -> as(del, () -> {
         controller.restoreAutoSaveAssets(
            Map.of("ids", file, "name", "Stolen947", "folder", "/", "overwrite", "true"), del);
         return null;
      }));

      assertTrue(exists(file));
      assertNull(restoredSheet("Stolen947"));
   }

   @Test
   void orgAdminCanRestoreAnotherUsersAutoSave() throws Exception {
      String file = recycledWorksheet(owner, "Ws-2");

      as(oadm, () -> {
         controller.restoreAutoSaveAssets(
            Map.of("ids", file, "name", "Restored947", "folder", "/", "overwrite", "true"), oadm);
         return null;
      });

      AbstractSheet sheet = restoredSheet("Restored947");
      assertNotNull(sheet);
      assertNotNull(((Worksheet) sheet).getAssembly("OWNER_TABLE"));
      assertFalse(exists(file));
   }

   // refused as a missing file is answered, so the time is not an existence oracle
   @Test
   void delegateGetsNoTimeForAnotherUsersAutoSave() throws Exception {
      String file = recycledViewsheet(owner, "Time-1");
      String own = recycledViewsheet(del, "Time-2");

      assertEquals("", as(del, () -> controller.getAutoSaveTime(
         Map.of("id", file, "timezoneid", "UTC"), del)));
      assertNotEquals("", as(del, () -> controller.getAutoSaveTime(
         Map.of("id", own, "timezoneid", "UTC"), del)));
      assertNotEquals("", as(oadm, () -> controller.getAutoSaveTime(
         Map.of("id", file, "timezoneid", "UTC"), oadm)));
   }

   @Test
   void delegateCannotDeleteAnotherUsersAutoSaveFromTree() throws Exception {
      String own = recycledViewsheet(del, "Tree-1");
      String other = recycledViewsheet(owner, "Tree-2");

      assertThrows(MessageException.class, () -> as(del, () ->
         objectService.deleteNodes(new TreeNodeInfo[] { node(own), node(other) }, del, false,
                                   false)));

      assertTrue(exists(own));
      assertTrue(exists(other));
   }

   @Test
   void orgAdminCanDeleteAnotherUsersAutoSaveFromTree() throws Exception {
      String other = recycledViewsheet(owner, "Tree-3");

      as(oadm, () ->
         objectService.deleteNodes(new TreeNodeInfo[] { node(other) }, oadm, false, false));

      assertFalse(exists(other));
   }

   @Test
   void fileWithoutOwnerOnlyForAdministrators() throws Exception {
      String file = "8^VIEWSHEET^_NULL_^Untitled-1^10.1.2.3~";

      assertFalse(as(del, () -> XAssetExportPermission.isAutoSavePermitted(file, del)));
      assertFalse(as(owner, () -> XAssetExportPermission.isAutoSavePermitted(file, owner)));
      assertTrue(as(oadm, () -> XAssetExportPermission.isAutoSavePermitted(file, oadm)));
      assertTrue(as(sadm, () -> XAssetExportPermission.isAutoSavePermitted(file, sadm)));
   }

   private TreeNodeInfo node(String file) {
      return TreeNodeInfo.builder().label(file).path(file).type(RepositoryEntry.AUTO_SAVE_VS)
         .build();
   }

   private String recycledViewsheet(SRPrincipal user, String sheet) throws Exception {
      AssetEntry entry = new AssetEntry(
         AssetRepository.TEMPORARY_SCOPE, AssetEntry.Type.VIEWSHEET, sheet, null);
      return recycled(user, entry, "content".getBytes(StandardCharsets.UTF_8));
   }

   private String recycledWorksheet(SRPrincipal user, String sheet) throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new EmbeddedTableAssembly(ws, "OWNER_TABLE"));
      AssetEntry entry = new AssetEntry(
         AssetRepository.TEMPORARY_SCOPE, AssetEntry.Type.WORKSHEET, sheet, null);
      return recycled(user, entry,
                      AbstractIndexedStorage.encodeXMLSerializable(ws, entry.toIdentifier()));
   }

   private String recycled(SRPrincipal user, AssetEntry entry, byte[] data) throws Exception {
      return as(user, () -> {
         AutoSaveUtils.writeAutoSaveFile(data, entry, user);
         AutoSaveUtils.recycleUserAutoSave(user);
         return AutoSaveUtils.getAutoSavedFiles(user, true).stream()
            .filter(f -> f.contains("^" + entry.getName() + "^")).findFirst().orElseThrow()
            .substring(AutoSaveUtils.RECYCLE_PREFIX.length());
      });
   }

   private boolean exists(String file) throws Exception {
      return as(owner, () -> AutoSaveUtils.exists(AutoSaveUtils.RECYCLE_PREFIX + file, owner));
   }

   private AbstractSheet restoredSheet(String name) throws Exception {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, name, null);
      entry.setOrgID(ORG);
      return as(oadm, () -> AssetUtil.getAssetRepository(false)
         .getSheet(entry, oadm, false, AssetContent.ALL));
   }

   // SecuredAspect reads the user from the request
   private static <T> T as(Principal principal, Callable<T> call) throws Exception {
      Principal old = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(principal);
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.setUserPrincipal(principal);
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

      try {
         return call.call();
      }
      finally {
         RequestContextHolder.resetRequestAttributes();
         ThreadContext.setContextPrincipal(old);
      }
   }

   private static final String ORG = "a947org";
   private static final String HOST_ORG = Organization.getDefaultOrganizationID();

   @Autowired
   private ViewsheetService viewsheetService;
   private SecurityTestDataBuilder builder;
   private MockedStatic<SUtil> sutilStatic;
   private SRPrincipal oadm;
   private SRPrincipal del;
   private SRPrincipal owner;
   private SRPrincipal sadm;
   private AutoSaveController controller;
   private RepositoryObjectService objectService;
}
