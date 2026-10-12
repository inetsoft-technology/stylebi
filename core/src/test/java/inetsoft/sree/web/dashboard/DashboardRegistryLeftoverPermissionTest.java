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
package inetsoft.sree.web.dashboard;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.ViewsheetEntry;
import inetsoft.sree.security.*;
import inetsoft.storage.KeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.util.*;
import inetsoft.util.dep.DashboardAsset;
import inetsoft.util.dep.XAssetConfig;
import inetsoft.web.admin.content.repository.RepositoryDashboardService;
import inetsoft.web.portal.controller.DashboardController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #78138: deleting a global dashboard left its DASHBOARD permission stored at its name, so a
 * dashboard created later with that name, by an import or a rename, received the grants of the
 * deleted one. Runs the real delete, rename and import paths against a real
 * FileAuthorizationProvider, with a real user in the authentication provider, because
 * checkPermission is false for a user that doesn't exist whatever permission is stored.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DashboardRegistryLeftoverPermissionTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DashboardRegistryLeftoverPermissionTest {
   @Configuration
   static class Config {
      @Bean
      DashboardRegistryManager dashboardRegistryManager(ApplicationEventPublisher publisher,
                                                        SecurityEngine securityEngine,
                                                        DependencyHandler dependencyHandler,
                                                        DataSpace dataSpace)
      {
         return new DashboardRegistryManager(publisher, securityEngine, dependencyHandler,
                                             dataSpace);
      }

      @Bean
      DashboardManager dashboardManager(SecurityEngine securityEngine,
                                        DashboardRegistryManager registryManager,
                                        KeyValueStorageManager storageManager)
      {
         return new DashboardManager(securityEngine, registryManager, storageManager);
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      savedPrincipal = ThreadContext.getContextPrincipal();

      FileAuthenticationProvider authc = new FileAuthenticationProvider();
      AuthenticationChain authcChain = new AuthenticationChain();
      authcChain.setProviders(List.of(authc));
      authcChain.saveConfiguration();

      FileAuthorizationProvider authz = new FileAuthorizationProvider();
      authz.setProviderName("Primary");
      AuthorizationChain authzChain = new AuthorizationChain();
      authzChain.setProviders(List.of(authz));
      authzChain.saveConfiguration();

      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.save();
      SecurityEngine.getSecurity().init();

      SecurityProvider security = SecurityEngine.getSecurity().getSecurityProvider();
      authenticationProvider = (FileAuthenticationProvider)
         ((AuthenticationChain) security.getAuthenticationProvider()).getProviders().get(0);
      authenticationProvider.addUser(new FSUser(GRANTEE));

      provider = (FileAuthorizationProvider)
         ((AuthorizationChain) security.getAuthorizationProvider()).getProviders().get(0);
      // opens the storage
      provider.getPermission(ResourceType.DASHBOARD, "probe", ORG);
      realStorage = storage();

      admin = new SRPrincipal(new IdentityID("admin", ORG),
                              new IdentityID[] { new IdentityID("Administrator", null) },
                              new String[0], ORG, 1L);
      Tool.clearUserMessage();
   }

   @AfterEach
   void tearDown() throws Exception {
      try {
         if(provider != null && realStorage != null) {
            setStorage(realStorage);
         }

         DashboardRegistry registry = globalRegistry();

         for(String name : cleanup) {
            try {
               registry.removeDashboard(name);
               provider.removePermission(ResourceType.DASHBOARD, name);
            }
            catch(Exception ignore) {
               // best-effort cleanup
            }
         }

         if(authenticationProvider != null) {
            authenticationProvider.removeUser(GRANTEE);
         }
      }
      finally {
         cleanup.clear();
         Tool.clearUserMessage();
         ThreadContext.setContextPrincipal(savedPrincipal);

         if(provider != null) {
            provider.tearDown();
         }

         SreeEnv.remove("security.enabled");
      }
   }

   @AfterAll
   static void detachRegistries() {
      DashboardRegistryTestSupport.quiesce(DashboardRegistryManager.getInstance(), null);
   }

   // EM repository delete -> RepositoryDashboardService.delete -> DashboardRegistry.removeDashboard
   @Test
   void emDelete_removesPermission_andImportDoesNotInheritIt() throws Exception {
      String name = "Em78138__GLOBAL";
      createGlobal(name);
      grant(name);
      assertTrue(granteeHasAccess(name), "the test expects the grant to give access");

      emService().delete(name, null, admin);

      assertNull(globalRegistry().getDashboard(name));
      assertNull(permission(name), "the permission of the deleted dashboard is still stored");

      importDashboard(name, null);

      assertNotNull(globalRegistry().getDashboard(name));
      assertNull(permission(name));
      assertFalse(granteeHasAccess(name),
                  "the imported dashboard received the grants of the deleted one");
   }

   // portal DELETE /api/portal/dashboard/deleteDashboard/{name} with a __GLOBAL name
   @Test
   void portalDelete_removesPermission() throws Exception {
      String name = "Portal78138__GLOBAL";
      createGlobal(name);
      grant(name);

      DashboardController controller = new DashboardController(
         null, null, null, SecurityEngine.getSecurity(),
         DashboardRegistryManager.getInstance(), DashboardManager.getManager(),
         mock(DependencyHandler.class), null);

      assertTrue(controller.deleteDashboard(name, admin));
      assertNull(globalRegistry().getDashboard(name));
      assertNull(permission(name), "the permission of the deleted dashboard is still stored");
   }

   // a dashboard without a permission renamed onto the name of a deleted one
   @Test
   void renameOntoLeftover_doesNotInheritIt() throws Exception {
      String deleted = "Deleted78138__GLOBAL";
      String other = "Other78138__GLOBAL";
      createGlobal(deleted);
      createGlobal(other);
      // a permission left behind, e.g. by a delete before the fix
      grant(deleted);
      globalRegistry().removeEntry(deleted);
      assertNull(permission(other));

      globalRegistry().renameDashboard(other, deleted);

      assertNotNull(globalRegistry().getDashboard(deleted));
      assertNull(permission(deleted));
      assertFalse(granteeHasAccess(deleted),
                  "the renamed dashboard received the grants of the deleted one");
   }

   // a dashboard's own permission still moves with it on rename
   @Test
   void rename_movesOwnPermission() throws Exception {
      String oname = "RenameFrom78138__GLOBAL";
      String name = "RenameTo78138__GLOBAL";
      createGlobal(oname);
      grant(oname);

      globalRegistry().renameDashboard(oname, name);
      cleanup.add(name);

      assertNull(permission(oname));
      assertTrue(granteeHasAccess(name));
   }

   // a permission left by a delete before the fix is not received by a new imported dashboard
   @Test
   void importNew_clearsLeftoverFromEarlierDelete() throws Exception {
      String name = "Old78138__GLOBAL";
      cleanup.add(name);
      grant(name);

      importDashboard(name, null);

      assertNotNull(globalRegistry().getDashboard(name));
      assertNull(permission(name));
      assertFalse(granteeHasAccess(name));
   }

   // overwriting an existing dashboard on import keeps its grants
   @Test
   void importOverwrite_keepsGrants() throws Exception {
      String name = "Overwrite78138__GLOBAL";
      createGlobal(name);
      grant(name);

      importDashboard(name, null);

      assertTrue(granteeHasAccess(name), "the grants of the overwritten dashboard were removed");
   }

   // a user dashboard's permission is stored at the bare name, shared by all users' dashboards of
   // that name, so neither deleting nor importing one removes it
   @Test
   void userDashboard_permissionUntouched() throws Exception {
      String name = "Mine78138";
      IdentityID owner = new IdentityID("admin", ORG);
      DashboardRegistry userRegistry = DashboardRegistryManager.getInstance().getRegistry(owner);
      userRegistry.putDashboard(name, board());
      grant(name);

      try {
         emService().delete(name, owner, admin);
         assertNull(userRegistry.getDashboard(name));
         assertNotNull(permission(name));

         importDashboard(name, owner);
         assertNotNull(permission(name));
      }
      finally {
         userRegistry.removeDashboard(name);
         provider.removePermission(ResourceType.DASHBOARD, name);
      }
   }

   // a failed remove doesn't fail the delete and is reported to the user (#77939 pattern)
   @Test
   void delete_removeFails_reportsWarning() throws Exception {
      String name = "Fail78138__GLOBAL";
      createGlobal(name);
      grant(name);
      failRemoves(key(name));

      emService().delete(name, null, admin);

      setStorage(realStorage);
      assertNull(globalRegistry().getDashboard(name));
      assertNotNull(permission(name), "the test expects the permission to be left");
      UserMessage message = Tool.getUserMessage();
      assertNotNull(message, "the permission left at the name was not reported");
      assertEquals(ConfirmException.WARNING, message.getLevel());
      assertEquals(Catalog.getCatalog().getString("em.repository.permissionsMayRemain"),
                   message.getMessage());
   }

   private RepositoryDashboardService emService() {
      return new RepositoryDashboardService(
         null, SecurityEngine.getSecurity().getSecurityProvider(), null,
         DashboardManager.getManager(), SecurityEngine.getSecurity(),
         mock(DependencyHandler.class), DashboardRegistryManager.getInstance(), null);
   }

   private static DashboardRegistry globalRegistry() {
      return DashboardRegistryManager.getInstance().getRegistry();
   }

   private void createGlobal(String name) throws Exception {
      cleanup.add(name);
      globalRegistry().putDashboard(name, board());
   }

   private static VSDashboard board() {
      VSDashboard dashboard = new VSDashboard();
      ViewsheetEntry entry = new ViewsheetEntry("vs78138");
      entry.setIdentifier("1^128^__NULL__^vs78138^" + ORG);
      dashboard.setViewsheet(entry);
      return dashboard;
   }

   private static void importDashboard(String name, IdentityID owner) throws Exception {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?><dashboardAsset>");
      board().writeXML(writer);
      writer.println("</dashboardAsset>");
      writer.flush();
      XAssetConfig config = new XAssetConfig();
      config.setOverwriting(true);
      new DashboardAsset(name, owner).parseContent(
         new ByteArrayInputStream(buffer.toString().getBytes(StandardCharsets.UTF_8)),
         config, true, true);
   }

   private void grant(String name) {
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.ACCESS, Set.of(GRANTEE.getName()), ORG);
      provider.setPermission(ResourceType.DASHBOARD, name, permission);
   }

   private Permission permission(String name) {
      return provider.getPermission(ResourceType.DASHBOARD, name);
   }

   private static boolean granteeHasAccess(String name) {
      SRPrincipal grantee = new SRPrincipal(GRANTEE, new IdentityID[0], new String[0], ORG, 2L);
      return SecurityEngine.getSecurity().getSecurityProvider()
         .checkPermission(grantee, ResourceType.DASHBOARD, name, ResourceAction.ACCESS);
   }

   private static String key(String name) {
      return "DASHBOARD:" + ORG + ":" + name;
   }

   /**
    * Fails every remove of the key. The other writes and the reads go to the real storage.
    */
   @SuppressWarnings("unchecked")
   private void failRemoves(String failKey) throws Exception {
      KeyValueStorage<Permission> failing =
         mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(realStorage));
      doAnswer(inv -> {
         String key = inv.getArgument(0);
         return failKey.equals(key) ?
            CompletableFuture.failedFuture(new IOException("simulated write failure")) :
            realStorage.remove(key);
      }).when(failing).remove(anyString());
      setStorage(failing);
   }

   @SuppressWarnings("unchecked")
   private KeyValueStorage<Permission> storage() throws Exception {
      Field field = FileAuthorizationProvider.class.getDeclaredField("storage");
      field.setAccessible(true);
      return (KeyValueStorage<Permission>) field.get(provider);
   }

   private void setStorage(KeyValueStorage<Permission> storage) throws Exception {
      Field field = FileAuthorizationProvider.class.getDeclaredField("storage");
      field.setAccessible(true);
      field.set(provider, storage);
   }

   private static final String ORG = Organization.getDefaultOrganizationID();
   private static final IdentityID GRANTEE = new IdentityID("grantee78138", ORG);
   private final List<String> cleanup = new ArrayList<>();
   private Principal savedPrincipal;
   private FileAuthenticationProvider authenticationProvider;
   private FileAuthorizationProvider provider;
   private KeyValueStorage<Permission> realStorage;
   private SRPrincipal admin;
}
