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
 * Bug #77947: the EM auto-save endpoints (delete, gettime) and the repository tree delete of
 * auto-save nodes are gated only by the EM_COMPONENT settings/content/repository grant, which an
 * administrator can delegate to a plain user. They acted on any auto-save file named by the client.
 *
 * The owner is now checked as stored in the name the endpoint acts on
 * (XAssetExportPermission.isStoredAutoSavePermitted): the owner, a user with ADMIN on the owner, a
 * site or organization administrator; a file without an owner, or with an owner of another
 * organization, only for the administrators. The restore cases, which also need a Drivers mock to
 * read the sheet, are in AutoSaveOwnerPermissionTest.
 *
 * The controller is called through an AspectJ proxy with the real SecuredAspect, so the delegate
 * passes the product's own gate. ComponentAuthorizationService is mocked because
 * view-components.json is not on the test classpath.
 */

import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.service.DataSourceRegistry;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // over 10 s alone: the Spring context with the real asset repository
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutoSaveOwnerCheckTest {
   @BeforeAll
   void setupAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("c947Org", ORG)
         .addOrgAdminRole("c947OrgAdmin", ORG)
         .addUser("oadm", ORG, "password")
         .addUser("del", ORG, "password")
         .addUser("uadm", ORG, "password")
         .addUser("owner", ORG, "password")
         // a plain user of the organization with the name of the site administrator
         .addUser("sadm", ORG, "password")
         .addUserToRole("oadm", "c947OrgAdmin", ORG)
         .addSysAdminRole("c947SiteAdmin", HOST_ORG)
         .addUser("sadm", HOST_ORG, "password")
         .addUserToRole("sadm", "c947SiteAdmin", HOST_ORG)
         .grantPermission(ResourceType.SECURITY_USER, new IdentityID("owner", ORG).convertToKey(),
                          ResourceAction.ADMIN, "uadm", Identity.USER, ORG);

      for(String user : new String[] { "del", "uadm", "sadm" }) {
         builder
            .grantPermission(ResourceType.EM, "*", ResourceAction.ACCESS,
                             user, Identity.USER, ORG)
            .grantPermission(ResourceType.EM_COMPONENT, "settings/content/repository",
                             ResourceAction.ACCESS, user, Identity.USER, ORG)
            .grantPermission(ResourceType.REPORT, "/", ResourceAction.DELETE,
                             user, Identity.USER, ORG);
      }

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
      uadm = builder.principalOf("uadm", ORG);
      owner = builder.principalOf("owner", ORG);
      namesake = builder.principalOf("sadm", ORG);
      // a site admin that switched to the organization in the EM
      sadm = builder.principalOf("sadm", HOST_ORG);
      sadm.setProperty("curr_org_id", ORG);

      ComponentAuthorizationService components = mock(ComponentAuthorizationService.class);
      when(components.getComponent("settings/content/repository"))
         .thenReturn(ViewComponent.builder().name("repository").label("Repository").build());
      AspectJProxyFactory factory = new AspectJProxyFactory(
         new AutoSaveController(mock(AutoSaveServiceProxy.class), mock(IndexedStorage.class)));
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
      String file = recycled(owner, "Del-1");

      assertThrows(MessageException.class, () -> delete(del, file));
      assertTrue(exists(file));
   }

   // every file is checked before any is deleted, so the delegate's own file is kept as well
   @Test
   void delegateBatchWithAnotherUsersAutoSaveDeletesNothing() throws Exception {
      String own = recycled(del, "Own-1");
      String other = recycled(owner, "Other-1");

      assertThrows(MessageException.class, () -> delete(del, own + "," + other));
      assertTrue(exists(own));
      assertTrue(exists(other));
   }

   @Test
   void delegateCanDeleteOwnAutoSave() throws Exception {
      String own = recycled(del, "Own-2");

      delete(del, own);
      assertFalse(exists(own));
   }

   @Test
   void userWithAdminOnOwnerCanDeleteOwnersAutoSave() throws Exception {
      String file = recycled(owner, "Del-2");

      delete(uadm, file);
      assertFalse(exists(file));
   }

   @Test
   void orgAdminCanDeleteAnotherUsersAutoSave() throws Exception {
      String file = recycled(owner, "Del-3");

      delete(oadm, file);
      assertFalse(exists(file));
   }

   @Test
   void siteAdminCanDeleteAnotherUsersAutoSave() throws Exception {
      String file = recycled(owner, "Del-4");

      delete(sadm, file);
      assertFalse(exists(file));
   }

   // the file of a site admin in the organization is stored with the site admin's own key, it is
   // not the file of the organization's user with the same name
   @Test
   void namesakeCannotActOnSiteAdminsAutoSave() throws Exception {
      String file = recycled(sadm, "SaDraft-1");
      assertTrue(file.contains("^sadm" + IdentityID.KEY_DELIMITER + HOST_ORG + "^"), file);

      assertThrows(MessageException.class, () -> delete(namesake, file));
      assertEquals("", gettime(namesake, file));
      assertThrows(MessageException.class, () -> as(namesake, () -> objectService.deleteNodes(
         new TreeNodeInfo[] { node(file) }, namesake, false, false)));
      assertTrue(exists(file));

      // the administrators of the organization list and manage it in the repository tree
      assertNotEquals("", gettime(oadm, file));
      delete(oadm, file);
      assertFalse(exists(file));
   }

   @Test
   void siteAdminCanDeleteOwnAutoSaveInOrganization() throws Exception {
      String file = recycled(sadm, "SaDraft-2");

      delete(sadm, file);
      assertFalse(exists(file));
   }

   // refused as a missing file is answered, so the time is not an existence oracle
   @Test
   void delegateGetsNoTimeForAnotherUsersAutoSave() throws Exception {
      String file = recycled(owner, "Time-1");
      String own = recycled(del, "Time-2");

      assertEquals("", gettime(del, file));
      assertNotEquals("", gettime(del, own));
      assertNotEquals("", gettime(oadm, file));
   }

   @Test
   void delegateCannotDeleteAnotherUsersAutoSaveFromTree() throws Exception {
      String own = recycled(del, "Tree-1");
      String other = recycled(owner, "Tree-2");

      assertThrows(MessageException.class, () -> as(del, () -> objectService.deleteNodes(
         new TreeNodeInfo[] { node(own), node(other) }, del, false, false)));
      assertTrue(exists(own));
      assertTrue(exists(other));
   }

   @Test
   void orgAdminCanDeleteAnotherUsersAutoSaveFromTree() throws Exception {
      String other = recycled(owner, "Tree-3");

      as(oadm, () -> objectService.deleteNodes(
         new TreeNodeInfo[] { node(other) }, oadm, false, false));
      assertFalse(exists(other));
   }

   @Test
   void fileWithoutOwnerOnlyForAdministrators() throws Exception {
      String file = "8^VIEWSHEET^_NULL_^Untitled-1^10.1.2.3~";

      assertFalse(as(del, () -> XAssetExportPermission.isStoredAutoSavePermitted(file, del)));
      assertFalse(as(owner, () -> XAssetExportPermission.isStoredAutoSavePermitted(file, owner)));
      assertTrue(as(oadm, () -> XAssetExportPermission.isStoredAutoSavePermitted(file, oadm)));
      assertTrue(as(sadm, () -> XAssetExportPermission.isStoredAutoSavePermitted(file, sadm)));
   }

   private void delete(SRPrincipal user, String ids) throws Exception {
      as(user, () -> {
         controller.deleteAutoSaveAssets(Map.of("ids", ids), user);
         return null;
      });
   }

   private String gettime(SRPrincipal user, String file) throws Exception {
      return as(user, () -> controller.getAutoSaveTime(
         Map.of("id", file, "timezoneid", "UTC"), user));
   }

   private TreeNodeInfo node(String file) {
      return TreeNodeInfo.builder().label(file).path(file).type(RepositoryEntry.AUTO_SAVE_VS)
         .build();
   }

   private String recycled(SRPrincipal user, String sheet) throws Exception {
      AssetEntry entry = new AssetEntry(
         AssetRepository.TEMPORARY_SCOPE, AssetEntry.Type.VIEWSHEET, sheet, null);

      return as(user, () -> {
         AutoSaveUtils.writeAutoSaveFile(
            "content".getBytes(StandardCharsets.UTF_8), entry, user);
         AutoSaveUtils.recycleUserAutoSave(user);
         return AutoSaveUtils.getAutoSavedFiles(user, true).stream()
            .filter(f -> f.contains("^" + sheet + "^")).findFirst().orElseThrow()
            .substring(AutoSaveUtils.RECYCLE_PREFIX.length());
      });
   }

   private boolean exists(String file) throws Exception {
      return as(owner, () -> AutoSaveUtils.exists(AutoSaveUtils.RECYCLE_PREFIX + file, owner));
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

   private static final String ORG = "c947org";
   private static final String HOST_ORG = Organization.getDefaultOrganizationID();

   private SecurityTestDataBuilder builder;
   private MockedStatic<SUtil> sutilStatic;
   private SRPrincipal oadm;
   private SRPrincipal del;
   private SRPrincipal uadm;
   private SRPrincipal owner;
   private SRPrincipal namesake;
   private SRPrincipal sadm;
   private AutoSaveController controller;
   private RepositoryObjectService objectService;
}
