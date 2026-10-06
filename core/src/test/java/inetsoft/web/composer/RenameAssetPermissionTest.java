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
package inetsoft.web.composer;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.model.RenameAssetEvent;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77839, a Composer rename (api/composer/asset-tree/rename-asset) must leave the permission
 * of the renamed asset in place when its path does not change (an alias-only or same-name
 * rename), must not touch the worksheet (ASSET) permission of the same path when a viewsheet or
 * viewsheet folder (REPORT) is renamed, and must still move the permission on a real rename.
 * Runs the real controller and asset engine against one real FileAuthorizationProvider, which
 * both the controller and the security engine use, as in production.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  RenameAssetPermissionTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RenameAssetPermissionTest {
   @Configuration
   static class Config {
      @Bean
      RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }
   }

   private AssetRepository repo;
   private SecurityProvider provider;
   private String orgId;

   @BeforeEach
   void setUp() {
      repo = AssetUtil.getAssetRepository(false);
      provider = CompositeSecurityProvider.create(new VirtualAuthenticationProvider(),
                                                  new FileAuthorizationProvider());
      orgId = Organization.getDefaultOrganizationID();
   }

   @Test
   @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void aliasOnlyFolderRenameKeepsPermission() throws Exception {
      String s = "S77839a";
      run(() -> {
         repo.addFolder(wsFolder(s), null);
         grant(ResourceType.ASSET, s, "alice");
         AssetEntry entry = wsFolder(s);
         entry.setAlias("OldAlias");

         assertNull(rename(entry, "NewAlias"));
         assertTrue(repo.containsEntry(wsFolder(s)));
         assertEquals(Set.of("alice"), readers(ResourceType.ASSET, s));
      });
   }

   @Test
   @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void aliasWithSlashOnFolderKeepsPermission() throws Exception {
      String s = "S77839b";
      run(() -> {
         repo.addFolder(wsFolder(s), null);
         grant(ResourceType.ASSET, s, "alice");
         AssetEntry entry = wsFolder(s);
         entry.setAlias("OldAlias");

         rename(entry, "X77839b/" + s);
         assertTrue(repo.containsEntry(wsFolder(s)));
         assertEquals(Set.of("alice"), readers(ResourceType.ASSET, s));
         assertNull(readers(ResourceType.ASSET, "X77839b/" + s));
      });
   }

   @Test
   @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void sameNameFolderRenameKeepsPermission() throws Exception {
      String s = "S77839c";
      run(() -> {
         repo.addFolder(wsFolder(s), null);
         grant(ResourceType.ASSET, s, "alice");

         assertNull(rename(wsFolder(s), s));
         assertEquals(Set.of("alice"), readers(ResourceType.ASSET, s));
      });
   }

   @Test
   @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void aliasOnlyWorksheetRenameKeepsPermission() throws Exception {
      String f = "F77839d";
      String w = f + "/W77839d";
      run(() -> {
         repo.addFolder(wsFolder(f), null);
         AssetEntry ws = worksheet(w);
         ws.setAlias("WsAlias");
         repo.setSheet(ws, new Worksheet(), admin(), true);
         grant(ResourceType.ASSET, w, "alice");

         // the entry as the asset tree gets it, with the stored alias
         AssetEntry entry = Arrays.stream(repo.getEntries(wsFolder(f), admin(),
                                                           ResourceAction.READ))
            .filter(e -> e.getPath().equals(w)).findFirst().orElseThrow();
         assertEquals("WsAlias", entry.getAlias());

         assertNull(rename(entry, "NewWsAlias"));
         assertTrue(repo.containsEntry(worksheet(w)));
         assertEquals(Set.of("alice"), readers(ResourceType.ASSET, w));
      });
   }

   @Test
   @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void viewsheetFolderRenameLeavesWorksheetFolderPermissions() throws Exception {
      String a = "A77839e";
      String b = "B77839e";
      run(() -> {
         RepletRegistry reg = RepletRegistryManager.getInstance().getRegistry(orgId);
         reg.addFolder(a);
         reg.save();
         repo.addFolder(wsFolder(a), null);
         repo.addFolder(wsFolder(b), null);
         grant(ResourceType.ASSET, a, "alice");
         grant(ResourceType.ASSET, b, "bob");
         grant(ResourceType.REPORT, a, "carol");

         assertNull(rename(vsFolder(a), b));
         assertTrue(reg.isFolder(b));
         assertEquals(Set.of("alice"), readers(ResourceType.ASSET, a));
         assertEquals(Set.of("bob"), readers(ResourceType.ASSET, b));
         assertNull(readers(ResourceType.REPORT, a));
         assertEquals(Set.of("carol"), readers(ResourceType.REPORT, b));
      });
   }

   @Test
   @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void viewsheetRenameLeavesWorksheetFolderPermissions() throws Exception {
      String v = "V77839f";
      String vx = v + "x";
      run(() -> {
         repo.setSheet(viewsheet(v), new Viewsheet(), admin(), true);
         repo.addFolder(wsFolder(v), null);
         repo.addFolder(wsFolder(vx), null);
         grant(ResourceType.ASSET, v, "alice");
         grant(ResourceType.ASSET, vx, "bob");
         grant(ResourceType.REPORT, v, "carol");

         assertNull(rename(viewsheet(v), vx));
         assertTrue(repo.containsEntry(viewsheet(vx)));
         assertEquals(Set.of("alice"), readers(ResourceType.ASSET, v));
         assertEquals(Set.of("bob"), readers(ResourceType.ASSET, vx));
         assertNull(readers(ResourceType.REPORT, v));
         assertEquals(Set.of("carol"), readers(ResourceType.REPORT, vx));
      });
   }

   @Test
   @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void folderRenameMovesPermission() throws Exception {
      String s = "S77839g";
      run(() -> {
         repo.addFolder(wsFolder(s), null);
         grant(ResourceType.ASSET, s, "alice");

         assertNull(rename(wsFolder(s), s + "x"));
         assertTrue(repo.containsEntry(wsFolder(s + "x")));
         assertNull(readers(ResourceType.ASSET, s));
         assertEquals(Set.of("alice"), readers(ResourceType.ASSET, s + "x"));
      });
   }

   @Test
   @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void worksheetRenameMovesPermission() throws Exception {
      String w = "W77839h";
      run(() -> {
         repo.setSheet(worksheet(w), new Worksheet(), admin(), true);
         grant(ResourceType.ASSET, w, "alice");

         assertNull(rename(worksheet(w), w + "x"));
         assertTrue(repo.containsEntry(worksheet(w + "x")));
         assertNull(readers(ResourceType.ASSET, w));
         assertEquals(Set.of("alice"), readers(ResourceType.ASSET, w + "x"));
      });
   }

   // ---- helpers ----

   private MessageCommand rename(AssetEntry entry, String newName) throws Exception {
      RenameAssetEvent event = new RenameAssetEvent.Builder()
         .entry(entry).newName(newName).confirmed(false).build();
      return controller().renameAsset(event, admin());
   }

   private RenameAssetController controller() {
      return new RenameAssetController(repo, SUtil.getRepletRepository(),
         mock(ViewsheetService.class), mock(LibManagerProvider.class));
   }

   private void grant(ResourceType type, String path, String user) {
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.READ, Set.of(user), orgId);
      provider.setPermission(type, path, permission);
   }

   /**
    * The users granted READ on the resource, or null if it has no permission.
    */
   private Set<String> readers(ResourceType type, String path) {
      Permission permission = provider.getPermission(type, path);

      return permission == null ? null : permission.getUserGrants(ResourceAction.READ, orgId)
         .stream().map(Permission.PermissionIdentity::getName).collect(Collectors.toSet());
   }

   private AssetEntry wsFolder(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, path, null, orgId);
   }

   private AssetEntry vsFolder(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, path,
                            null, orgId);
   }

   private AssetEntry worksheet(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, path, null,
                            orgId);
   }

   private AssetEntry viewsheet(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, null,
                            orgId);
   }

   private SRPrincipal admin() {
      IdentityID id = new IdentityID("admin", orgId);
      return new SRPrincipal(id, new IdentityID[] { new IdentityID("Administrator", null) },
                             new String[0], orgId, 1L);
   }

   private interface Body {
      void run() throws Exception;
   }

   /**
    * Runs the body with security enabled on a security engine that allows every permission
    * check and keeps its permissions in the same provider as the controller, so the engine's
    * own permission moves and the controller's act on one store.
    */
   private void run(Body body) throws Exception {
      SecurityEngine real = SecurityEngine.getSecurity();
      SecurityEngine spy = mock(SecurityEngine.class, withSettings().spiedInstance(real)
         .defaultAnswer(inv -> {
            String name = inv.getMethod().getName();
            return name.equals("checkPermission") || name.equals("isSecurityEnabled") ?
               Boolean.TRUE : inv.callRealMethod();
         }));
      Field field = SecurityEngine.class.getDeclaredField("provider");
      field.setAccessible(true);
      field.set(spy, provider);

      try(MockedStatic<SecurityEngine> st = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS)) {
         st.when(SecurityEngine::getSecurity).thenReturn(spy);
         body.run();
      }
   }
}
