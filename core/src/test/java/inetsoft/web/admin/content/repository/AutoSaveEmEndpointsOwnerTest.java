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
 * Bug #77947: the EM auto-save endpoints and the repository tree delete act on the auto-save file
 * named by the client. Here the files have production-shaped names (the client IP is part of the
 * name), the tree delete goes through RepositoryObjectController with the real registry and
 * resource permission checks, and a user with ADMIN on the owner is a delegate the owner rule must
 * let through.
 */

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.schedule.ScheduleManager;
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
import inetsoft.web.admin.content.repository.model.DeleteTreeNodesRequest;
import inetsoft.web.admin.content.repository.model.TreeNodeInfo;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  AutoSaveEmEndpointsOwnerTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // over 10 s alone: the Spring context with the real asset repository
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutoSaveEmEndpointsOwnerTest {
   @Configuration
   static class Config {
      @Bean
      @Primary
      PortalThemesManager portalThemesManager() {
         PortalThemesManager manager = mock(PortalThemesManager.class);
         when(manager.getCssEntries()).thenReturn(new HashMap<>());
         return manager;
      }

      // the test has no plugins, the sheet class is loaded directly
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
         .addOrg("v947Org", ORG)
         .addOrgAdminRole("v947OrgAdmin", ORG)
         .addUser("oadm", ORG, "password")
         .addUserToRole("oadm", "v947OrgAdmin", ORG)
         .addUser("victim", ORG, "password")
         .addUser("del", ORG, "password")
         .addUser("delro", ORG, "password")
         .addUser("uadm", ORG, "password");

      // del: the delegated EM grant plus global asset write and repository delete
      // delro: only the delegated EM grant
      // uadm: the delegated EM grant plus ADMIN on the victim
      // victim: the delegated EM grant plus global asset write, to act on its own files
      for(String user : new String[] { "del", "delro", "uadm", "victim" }) {
         builder
            .grantPermission(ResourceType.EM, "*", ResourceAction.ACCESS, user, Identity.USER, ORG)
            .grantPermission(ResourceType.EM_COMPONENT, "settings/content/repository",
                             ResourceAction.ACCESS, user, Identity.USER, ORG);
      }

      for(String user : new String[] { "del", "victim" }) {
         builder
            .grantPermission(ResourceType.ASSET, "/", ResourceAction.READ, user, Identity.USER, ORG)
            .grantPermission(ResourceType.ASSET, "/", ResourceAction.WRITE, user, Identity.USER, ORG)
            .grantPermission(ResourceType.REPORT, "/", ResourceAction.DELETE, user, Identity.USER,
                             ORG);
      }

      builder.grantPermission(ResourceType.SECURITY_USER,
                              new IdentityID("victim", ORG).convertToKey(), ResourceAction.ADMIN,
                              "uadm", Identity.USER, ORG);
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
      oadm = login("oadm");
      victim = login("victim");
      del = login("del");
      delro = login("delro");
      uadm = login("uadm");

      ComponentAuthorizationService components = mock(ComponentAuthorizationService.class);
      when(components.getComponent("settings/content/repository"))
         .thenReturn(ViewComponent.builder().name("repository").label("Repository").build());
      SecuredAspect aspect = new SecuredAspect(mock(DataSourceRegistry.class), components);

      AutoSaveService service = new AutoSaveService(viewsheetService);
      AutoSaveServiceProxy serviceProxy = mock(AutoSaveServiceProxy.class);

      try {
         when(serviceProxy.restoreAutoSaveAssets(any(), any(), anyBoolean(), any()))
            .thenAnswer(inv -> service.restoreAutoSaveAssets(
               inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }

      autoSaveController = proxy(
         new AutoSaveController(serviceProxy, mock(IndexedStorage.class)), aspect);

      SecurityEngine engine = SecurityEngine.getSecurity();
      RepletRegistryService registryService = new RepletRegistryService(
         engine, mock(ScheduleManager.class), mock(DependencyHandler.class),
         mock(RenameTransformHandler.class), mock(RepletRegistryManager.class));
      ResourcePermissionService permissionService = new ResourcePermissionService(
         mock(SecurityProvider.class), engine, mock(LibManagerProvider.class),
         mock(DataSourceRegistry.class));
      RepositoryObjectService objectService = new RepositoryObjectService(
         registryService, mock(ContentRepositoryTreeService.class), mock(SecurityProvider.class),
         permissionService, mock(XRepository.class), mock(RepositoryDashboardService.class),
         mock(DataModelFolderManagerService.class), mock(DataSourceRegistry.class),
         mock(LibManagerProvider.class), mock(RecycleBin.class), mock(DependencyHandler.class),
         mock(RenameTransformHandler.class), mock(RepletRegistryManager.class),
         mock(DashboardRegistryManager.class));
      treeController = proxy(
         new RepositoryObjectController(objectService, permissionService,
                                        mock(ScheduleManager.class)), aspect);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      OrganizationContextHolder.setCurrentOrgId(null);
      sutilStatic.close();
   }

   @Test
   void delegatesCannotDeleteVictimFile() throws Exception {
      String file = recycled(victim, "Untitled-1", false);
      assertTrue(file.endsWith("^" + IP + "~"), file);

      for(SRPrincipal user : new SRPrincipal[] { del, delro }) {
         assertThrows(MessageException.class, () -> act(user, () ->
autoSaveController.deleteAutoSaveAssets(Map.of("ids", file), user)));
      }

      assertTrue(exists(file));
   }

   @Test
   void delegateCannotRestoreVictimFileInMixedBatch() throws Exception {
      String own = recycled(del, "DelOwn", true);
      String other = recycled(victim, "VictimWs", true);

      assertThrows(MessageException.class, () -> act(del, () ->
autoSaveController.restoreAutoSaveAssets(
            Map.of("ids", own + "," + other, "name", "Stolen947v", "folder", "/",
                   "overwrite", "true"), del)));

      assertTrue(exists(own));
      assertTrue(exists(other));
      assertNull(globalSheet("Stolen947v"));
      assertNull(globalSheet("Stolen947v1"));
   }

   @Test
   void delegateGettimeOfVictimFileAnsweredAsMissing() throws Exception {
      String file = recycled(victim, "Untitled-2", false);
      String missing = file.replace("Untitled-2", "Untitled-99");

      assertEquals("", call(del, () -> autoSaveController.getAutoSaveTime(
         Map.of("id", missing, "timezoneid", "UTC"), del)));
      assertEquals("", call(del, () -> autoSaveController.getAutoSaveTime(
         Map.of("id", file, "timezoneid", "UTC"), del)));
      assertNotEquals("", call(victim, () -> autoSaveController.getAutoSaveTime(
         Map.of("id", file, "timezoneid", "UTC"), victim)));
   }

   // del has REPORT DELETE inherited from the root, so the tree's own permission check passes
   @Test
   void delegateCannotDeleteVictimFileFromTree() throws Exception {
      String own = recycled(del, "TreeOwn", false);
      String other = recycled(victim, "TreeOther", false);

      assertThrows(MessageException.class, () -> call(del, () ->
         treeController.deleteRepositoryEntry(deleteRequest(own, other), del)));

      assertTrue(exists(own));
      assertTrue(exists(other));
   }

   @Test
   void ownerCanRestoreAndDeleteOwnFiles() throws Exception {
      String ws = recycled(victim, "VictimOwnWs", true);
      String vs = recycled(victim, "VictimOwnVs", false);

      act(victim, () ->
autoSaveController.restoreAutoSaveAssets(
         Map.of("ids", ws, "name", "VictimRestored947", "folder", "/", "overwrite", "true"),
         victim));
      call(victim, () -> treeController.deleteRepositoryEntry(deleteRequest(vs), victim));

      assertNotNull(globalSheet("VictimRestored947"));
      assertFalse(exists(ws));
      assertFalse(exists(vs));
   }

   @Test
   void adminOfOwnerAndOrgAdminCanActOnVictimFiles() throws Exception {
      String first = recycled(victim, "ForUadm", false);
      String second = recycled(victim, "ForOadm", false);

      assertNotEquals("", call(uadm, () -> autoSaveController.getAutoSaveTime(
         Map.of("id", first, "timezoneid", "UTC"), uadm)));
      act(uadm, () ->
autoSaveController.deleteAutoSaveAssets(Map.of("ids", first), uadm));
      call(oadm, () -> treeController.deleteRepositoryEntry(deleteRequest(second), oadm));

      assertFalse(exists(first));
      assertFalse(exists(second));
   }

   private SRPrincipal login(String name) {
      SRPrincipal base = builder.principalOf(name, ORG);
      SRPrincipal principal = new SRPrincipal(base, new ClientInfo(base.getIdentityID(), IP));
      @SuppressWarnings("unchecked")
      Map<ClientInfo, SRPrincipal> users = (Map<ClientInfo, SRPrincipal>)
         ReflectionTestUtils.getField(SecurityEngine.getSecurity(), "users");
      users.put(principal.getUser().getCacheKey(), principal);
      return principal;
   }

   private static <T> T proxy(T target, SecuredAspect aspect) {
      AspectJProxyFactory factory = new AspectJProxyFactory(target);
      factory.setProxyTargetClass(true);
      factory.addAspect(aspect);
      return factory.getProxy();
   }

   private static DeleteTreeNodesRequest deleteRequest(String... files) {
      TreeNodeInfo[] nodes = Arrays.stream(files)
         .map(f -> TreeNodeInfo.builder().label(f).path(f).type(RepositoryEntry.AUTO_SAVE_VS)
            .build())
         .toArray(TreeNodeInfo[]::new);
      DeleteTreeNodesRequest request = mock(DeleteTreeNodesRequest.class);
      when(request.nodes()).thenReturn(nodes);
      return request;
   }

   // written and recycled as the composer does, returns the name the EM sends back
   private String recycled(SRPrincipal user, String sheet, boolean worksheet) throws Exception {
      AssetEntry entry = new AssetEntry(
         AssetRepository.TEMPORARY_SCOPE,
         worksheet ? AssetEntry.Type.WORKSHEET : AssetEntry.Type.VIEWSHEET, sheet, null);
      byte[] data;

      if(worksheet) {
         Worksheet ws = new Worksheet();
         ws.addAssembly(new EmbeddedTableAssembly(ws, "T_" + sheet));
         data = AbstractIndexedStorage.encodeXMLSerializable(ws, entry.toIdentifier());
      }
      else {
         data = "vs".getBytes(StandardCharsets.UTF_8);
      }

      return call(user, () -> {
         AutoSaveUtils.writeAutoSaveFile(data, entry, user);
         AutoSaveUtils.recycleUserAutoSave(user);
         return AutoSaveUtils.getAutoSavedFiles(user, true).stream()
            .filter(f -> f.contains("^" + sheet + "^")).findFirst().orElseThrow()
            .substring(AutoSaveUtils.RECYCLE_PREFIX.length());
      });
   }

   private boolean exists(String file) throws Exception {
      return call(oadm, () -> AutoSaveUtils.exists(AutoSaveUtils.RECYCLE_PREFIX + file, oadm));
   }

   private AbstractSheet globalSheet(String name) throws Exception {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, name, null);
      entry.setOrgID(ORG);
      return call(oadm, () -> AssetUtil.getAssetRepository(false)
         .getSheet(entry, oadm, false, AssetContent.ALL));
   }

   private static void act(Principal principal, Action action) throws Exception {
      call(principal, () -> {
         action.run();
         return null;
      });
   }

   // SecuredAspect reads the user from the request
   private static <T> T call(Principal principal, Callable<T> call) throws Exception {
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

   private interface Action {
      void run() throws Exception;
   }

   private static final String ORG = "v947org";
   private static final String IP = "10.1.2.3";

   @Autowired
   private ViewsheetService viewsheetService;
   private SecurityTestDataBuilder builder;
   private MockedStatic<SUtil> sutilStatic;
   private SRPrincipal oadm;
   private SRPrincipal victim;
   private SRPrincipal del;
   private SRPrincipal delro;
   private SRPrincipal uadm;
   private AutoSaveController autoSaveController;
   private RepositoryObjectController treeController;
}
