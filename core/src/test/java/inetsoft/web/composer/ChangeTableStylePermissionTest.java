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
import inetsoft.report.composition.RuntimeSheet;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.web.composer.model.ChangeAssetEvent;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81263, a Composer move of a table style into a folder (api/composer/asset-tree/change-asset)
 * renames the style to Folder~Style, so its permission must move to the new name instead of staying
 * on the old name and leaving the moved style open to everyone with READ on the library.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  ChangeTableStylePermissionTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChangeTableStylePermissionTest {
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

   @Test
   @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void moveIntoFolderMovesPermission() throws Exception {
      String folder = "Fd81263a";
      addFolder(folder);
      addStyle("St81263a", "St81263a");

      run(() -> {
         grant(ResourceType.TABLE_STYLE, "St81263a", "alice");
         assertNull(move("St81263a", folder));

         assertEquals(folder + "~St81263a", manager.getTableStyle("St81263a").getName());
         assertNull(readers(ResourceType.TABLE_STYLE, "St81263a"));
         assertEquals(Set.of("alice"), readers(ResourceType.TABLE_STYLE, folder + "~St81263a"));
      });
   }

   @Test
   @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void moveOutOfFolderMovesPermission() throws Exception {
      String folder = "Fd81263b";
      addFolder(folder);
      addStyle("St81263b", folder + "~St81263b");

      run(() -> {
         grant(ResourceType.TABLE_STYLE, folder + "~St81263b", "alice");
         assertNull(move("St81263b", null));

         assertEquals("St81263b", manager.getTableStyle("St81263b").getName());
         assertNull(readers(ResourceType.TABLE_STYLE, folder + "~St81263b"));
         assertEquals(Set.of("alice"), readers(ResourceType.TABLE_STYLE, "St81263b"));
      });
   }

   @Test
   @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void moveWithoutPermissionClearsStalePermissionAtNewName() throws Exception {
      String folder = "Fd81263c";
      addFolder(folder);
      addStyle("St81263c", "St81263c");

      run(() -> {
         grant(ResourceType.TABLE_STYLE, folder + "~St81263c", "mallory");
         assertNull(move("St81263c", folder));
         assertNull(readers(ResourceType.TABLE_STYLE, folder + "~St81263c"));
      });
   }

   // ---- helpers ----

   private void addFolder(String folder) throws Exception {
      if(!manager.containsFolder(folder)) {
         manager.addTableStyleFolder(folder);
         manager.save();
      }
   }

   private void addStyle(String id, String name) throws Exception {
      XTableStyle style = new XTableStyle(new DefaultTableLens(2, 2));
      style.setID(id);
      style.setName(name);
      manager.setTableStyle(id, style);
      manager.save();
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

   private AssetEntry folderEntry(String folder) {
      AssetEntry entry;

      if(folder == null) {
         entry = new AssetEntry(AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.TABLE_STYLE_FOLDER,
                                "Table Style", null);
      }
      else {
         entry = new AssetEntry(AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.TABLE_STYLE_FOLDER,
                                "Table Style/" + folder, null);
         entry.setProperty("folder", folder);
      }

      return entry;
   }

   private MessageCommand move(String styleId, String folder) throws Exception {
      ArrayList<AssetEntry> entries = new ArrayList<>();
      entries.add(styleEntry(styleId));
      ChangeAssetEvent event = new ChangeAssetEvent.Builder()
         .parent(folderEntry(folder)).entries(entries).confirmed(true).build();
      ViewsheetService vsService = mock(ViewsheetService.class);
      when(vsService.getRuntimeSheets(any())).thenReturn(new RuntimeSheet[0]);
      return new ChangeAssetController(repo, vsService,
                                       LibManagerProvider.getInstance())
         .changeAsset(event, admin());
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
