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
import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.RenameTransformHandler;
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
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80959, a Composer rename of a script or table style (api/composer/asset-tree/rename-asset)
 * must move the resource permission to the new name, as the EM script rename does, instead of
 * leaving it at the old name and opening the renamed item to everyone with READ on the library.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  RenameLibraryPermissionTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RenameLibraryPermissionTest {
   @Configuration
   static class Config {
      @Bean
      RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }
   }

   private AssetRepository repo;
   private SecurityProvider provider;
   private LibManager manager;
   private String orgId;

   @BeforeEach
   void setUp() throws Exception {
      repo = AssetUtil.getAssetRepository(false);
      provider = CompositeSecurityProvider.create(new VirtualAuthenticationProvider(),
                                                  new FileAuthorizationProvider());
      orgId = Organization.getDefaultOrganizationID();
      manager = LibManagerProvider.getInstance().getManager(admin());
   }

   // The script library of a real LibManager reloads itself from storage on its own cluster
   // message, which undoes the rename before it is saved in a test; the controller's part is the
   // call to renameScript and the permission move, so the manager is a mock here.
   @Test
   @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void scriptRenameMovesPermission() throws Exception {
      LibManager mockManager = mock(LibManager.class);

      run(() -> {
         grant(ResourceType.SCRIPT, "Sc80959a", "alice");
         assertNull(renameScript(mockManager, "Sc80959a", "Sc80959b"));

         verify(mockManager).renameScript("Sc80959a", "Sc80959b");
         assertNull(readers(ResourceType.SCRIPT, "Sc80959a"));
         assertEquals(Set.of("alice"), readers(ResourceType.SCRIPT, "Sc80959b"));
      });
   }

   @Test
   @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void scriptRenameWithoutPermissionClearsStalePermissionAtNewName() throws Exception {
      LibManager mockManager = mock(LibManager.class);

      run(() -> {
         grant(ResourceType.SCRIPT, "Sc80959d", "mallory");
         assertNull(renameScript(mockManager, "Sc80959c", "Sc80959d"));
         assertNull(readers(ResourceType.SCRIPT, "Sc80959d"));
      });
   }

   @Test
   @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void tableStyleRenameMovesPermission() throws Exception {
      addStyle("St80959a", "St80959a");

      run(() -> {
         grant(ResourceType.TABLE_STYLE, "St80959a", "alice");
         assertNull(rename(styleEntry("St80959a"), "St80959b"));

         assertEquals("St80959b", manager.getTableStyle("St80959a").getName());
         assertNull(readers(ResourceType.TABLE_STYLE, "St80959a"));
         assertEquals(Set.of("alice"), readers(ResourceType.TABLE_STYLE, "St80959b"));
      });
   }

   @Test
   @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void tableStyleInFolderRenameMovesPermissionUnderFolderPath() throws Exception {
      String folder = "Fd80959";

      if(!manager.containsFolder(folder)) {
         manager.addTableStyleFolder(folder);
         manager.save();
      }

      addStyle("St80959c", folder + "~St80959c");

      run(() -> {
         grant(ResourceType.TABLE_STYLE, folder + "~St80959c", "alice");
         assertNull(rename(styleEntry("St80959c"), "St80959d"));

         assertNull(readers(ResourceType.TABLE_STYLE, folder + "~St80959c"));
         assertEquals(Set.of("alice"), readers(ResourceType.TABLE_STYLE, folder + "~St80959d"));
      });
   }

   @Test
   @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void tableStyleFolderRenameMovesStylePermissions() throws Exception {
      String f = "Fe80959";
      String g = "Fg80959";

      if(!manager.containsFolder(f)) {
         manager.addTableStyleFolder(f);
         manager.save();
      }

      addStyle("St80959e", f + "~St80959e");
      AssetEntry folder = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
                                         AssetEntry.Type.TABLE_STYLE_FOLDER,
                                         "Table Style/" + f, null);
      folder.setProperty("folder", f);

      run(() -> {
         grant(ResourceType.TABLE_STYLE, f + "~St80959e", "alice");
         grant(ResourceType.TABLE_STYLE, f, "bob");
         assertNull(rename(folder, g));

         assertEquals(g + "~St80959e", manager.getTableStyle("St80959e").getName());
         assertNull(readers(ResourceType.TABLE_STYLE, f + "~St80959e"));
         assertEquals(Set.of("alice"), readers(ResourceType.TABLE_STYLE, g + "~St80959e"));
         assertEquals(Set.of("bob"), readers(ResourceType.TABLE_STYLE, g));
      });
   }

   // ---- helpers ----

   private void addStyle(String id, String name) throws Exception {
      XTableStyle style = new XTableStyle(new DefaultTableLens(2, 2));
      style.setID(id);
      style.setName(name);
      manager.setTableStyle(id, style);
      manager.save();
   }

   private AssetEntry scriptEntry(String name) {
      return new AssetEntry(AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.SCRIPT, name, null);
   }

   private AssetEntry styleEntry(String id) {
      String full = manager.getTableStyle(id).getName();
      int idx = full.lastIndexOf(LibManager.SEPARATOR);
      String leaf = full.substring(idx + 1);
      String folder = idx < 0 ? null : full.substring(0, idx);
      String parent = folder == null ? "Table Style" : "Table Style/" + folder.replace('~', '/');
      AssetEntry entry = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
                                        AssetEntry.Type.TABLE_STYLE, parent + "/" + leaf, null);
      entry.setProperty("styleName", full);
      entry.setProperty("styleID", id);
      entry.setProperty("folder", folder);
      return entry;
   }

   private MessageCommand renameScript(LibManager mockManager, String name, String newName)
      throws Exception
   {
      LibManagerProvider mockProvider = mock(LibManagerProvider.class);
      when(mockProvider.getManager(any(java.security.Principal.class))).thenReturn(mockManager);
      RenameAssetEvent event = new RenameAssetEvent.Builder()
         .entry(scriptEntry(name)).newName(newName).confirmed(false).build();
      return new RenameAssetController(repo, SUtil.getRepletRepository(),
                                       mock(ViewsheetService.class), mockProvider)
         .renameAsset(event, admin());
   }

   private MessageCommand rename(AssetEntry entry, String newName) throws Exception {
      RenameAssetEvent event = new RenameAssetEvent.Builder()
         .entry(entry).newName(newName).confirmed(false).build();
      return new RenameAssetController(repo, SUtil.getRepletRepository(),
                                       mock(ViewsheetService.class),
                                       LibManagerProvider.getInstance())
         .renameAsset(event, admin());
   }

   private void grant(ResourceType type, String path, String user) {
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.READ, Set.of(user), orgId);
      provider.setPermission(type, path, permission);
   }

   private Set<String> readers(ResourceType type, String path) {
      Permission permission = provider.getPermission(type, path);

      return permission == null ? null : permission.getUserGrants(ResourceAction.READ, orgId)
         .stream().map(Permission.PermissionIdentity::getName).collect(Collectors.toSet());
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
    * check and keeps its permissions in the same provider the test reads.
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
