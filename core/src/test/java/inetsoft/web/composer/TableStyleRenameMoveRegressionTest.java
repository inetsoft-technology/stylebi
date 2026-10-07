/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.Catalog;
import inetsoft.web.composer.model.ChangeAssetEvent;
import inetsoft.web.composer.model.RenameAssetEvent;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77837, legitimate table style renames and moves (root, folders, nested folders and
 * folder renames) still work after the rename and move take the folder from the stored style,
 * and the refusals come from the intended check. Entries are built with the tree paths that
 * AbstractAssetEngine.getTableStyleEntries gives them, including nested folders.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  TableStyleRenameMoveRegressionTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TableStyleRenameMoveRegressionTest {
   @Configuration
   static class Beans {
      // LibManager.renameTableStyle queues a dependency rewrite through this bean.
      @Bean
      RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }
   }

   private static final String A = "RgA77837";
   private static final String B = A + "~RgB77837";
   private static final String SECRET = "RgSecret77837";

   @BeforeEach
   void setUp() throws Exception {
      repo = AssetUtil.getAssetRepository(false);
      manager = LibManagerProvider.getInstance().getManager(admin());

      for(String folder : new String[] { A, B, SECRET }) {
         if(!manager.containsFolder(folder)) {
            manager.addTableStyleFolder(folder);
         }
      }

      manager.save();
   }

   @Test
   void plainRenameAtRoot() throws Exception {
      addStyle("RgRoot77837", "RgRoot77837");
      MessageCommand r = allow(() -> renameController().renameAsset(
         rename(styleEntry("RgRoot77837"), "RgRoot77837b"), admin()));

      assertNull(r, () -> r.getMessage());
      assertEquals("RgRoot77837b", manager.getTableStyle("RgRoot77837").getName());
   }

   @Test
   void plainRenameInNestedFolder() throws Exception {
      addStyle("RgNest77837", B + "~RgNest77837");
      MessageCommand r = allow(() -> renameController().renameAsset(
         rename(styleEntry("RgNest77837"), "RgNest77837b"), admin()));

      assertNull(r, () -> r.getMessage());
      assertEquals(B + "~RgNest77837b", manager.getTableStyle("RgNest77837").getName());
   }

   @Test
   void renameOntoNameInSameFolderIsRefusedAsDuplicate() throws Exception {
      addStyle("RgDup1x77837", A + "~RgDup77837");
      addStyle("RgDup2x77837", A + "~RgDup2_77837");
      MessageCommand r = allow(() -> renameController().renameAsset(
         rename(styleEntry("RgDup2x77837"), "RgDup77837"), admin()));

      assertNotNull(r);
      assertEquals(Catalog.getCatalog().getString("common.duplicateName"), r.getMessage());
      assertEquals(A + "~RgDup2_77837", manager.getTableStyle("RgDup2x77837").getName());
   }

   @Test
   void renameWithMismatchedIdIsRefusedByPermissionCheckOnStoredName() throws Exception {
      addStyle("RgDecoy77837", "RgDecoy77837");
      addStyle("RgVictim77837", SECRET + "~RgVictim77837");
      AssetEntry entry = styleEntry("RgDecoy77837");
      entry.setProperty("styleID", "RgVictim77837");
      List<String> denied = new ArrayList<>();
      MessageCommand r = denyWriteOn(SECRET, denied, () -> renameController().renameAsset(
         rename(entry, "RgPwned77837"), admin()));

      assertNotNull(r);
      assertEquals(Catalog.getCatalog().getString("composer.nopermission.rename", entry.getName()),
                   r.getMessage());
      assertEquals(List.of(SECRET + "~RgVictim77837"), denied);
      assertEquals(SECRET + "~RgVictim77837", manager.getTableStyle("RgVictim77837").getName());
   }

   @Test
   void moveRootStyleIntoFolder() throws Exception {
      addStyle("RgMv1x77837", "RgMv1_77837");
      MessageCommand r = allow(() -> move(styleEntry("RgMv1x77837"), A));

      assertNull(r, () -> r.getMessage());
      assertEquals(A + "~RgMv1_77837", manager.getTableStyle("RgMv1x77837").getName());
   }

   @Test
   void moveFolderStyleToRoot() throws Exception {
      addStyle("RgMv2x77837", A + "~RgMv2_77837");
      MessageCommand r = allow(() -> move(styleEntry("RgMv2x77837"), null));

      assertNull(r, () -> r.getMessage());
      assertEquals("RgMv2_77837", manager.getTableStyle("RgMv2x77837").getName());
   }

   @Test
   void moveFolderStyleIntoNestedFolder() throws Exception {
      addStyle("RgMv3x77837", A + "~RgMv3_77837");
      MessageCommand r = allow(() -> move(styleEntry("RgMv3x77837"), B));

      assertNull(r, () -> r.getMessage());
      assertEquals(B + "~RgMv3_77837", manager.getTableStyle("RgMv3x77837").getName());
   }

   @Test
   void moveNestedStyleIntoOtherNestedFolder() throws Exception {
      String c = A + "~RgC77837";

      if(!manager.containsFolder(c)) {
         manager.addTableStyleFolder(c);
         manager.save();
      }

      addStyle("RgMv4x77837", B + "~RgMv4_77837");
      MessageCommand r = allow(() -> move(styleEntry("RgMv4x77837"), c));

      assertNull(r, () -> r.getMessage());
      assertEquals(c + "~RgMv4_77837", manager.getTableStyle("RgMv4x77837").getName());
   }

   @Test
   void moveOntoExistingNameInTargetIsRefusedAsDuplicate() throws Exception {
      addStyle("RgMvDA77837", A + "~RgMvD77837");
      addStyle("RgMvDB77837", "RgMvD77837");
      MessageCommand r = allow(() -> move(styleEntry("RgMvDB77837"), A));

      assertNotNull(r);
      assertEquals(Catalog.getCatalog().getString("common.duplicateName"), r.getMessage());
      assertEquals("RgMvD77837", manager.getTableStyle("RgMvDB77837").getName());
   }

   @Test
   void moveWithForgedSourceFolderIsRefusedByTargetFolderWriteCheck() throws Exception {
      addStyle("RgMvF77837", "RgMvF77837");
      AssetEntry entry = styleEntry("RgMvF77837");
      entry.setProperty("folder", SECRET);
      List<String> denied = new ArrayList<>();
      MessageCommand r = denyWriteOn(SECRET, denied, () -> move(entry, SECRET));

      assertNotNull(r);
      assertEquals(List.of(SECRET), denied);
      assertEquals("RgMvF77837", manager.getTableStyle("RgMvF77837").getName());
   }

   @Test
   void renameFolderWithStylesInside() throws Exception {
      String f = "RgF77837";
      String g = "RgG77837";

      if(!manager.containsFolder(f)) {
         manager.addTableStyleFolder(f);
      }

      addStyle("RgIn77837", f + "~RgIn77837");
      AssetEntry folder = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
                                         AssetEntry.Type.TABLE_STYLE_FOLDER,
                                         "Table Style/" + f, null);
      folder.setProperty("folder", f);
      MessageCommand r = allow(() -> renameController().renameAsset(rename(folder, g), admin()));

      assertNull(r, () -> r.getMessage());
      assertTrue(manager.containsFolder(g));
      assertFalse(manager.containsFolder(f));
      assertEquals(g + "~RgIn77837", manager.getTableStyle("RgIn77837").getName());
   }

   // ---- helpers ----

   private void addStyle(String id, String name) throws Exception {
      XTableStyle style = new XTableStyle(new DefaultTableLens(2, 2));
      style.setID(id);
      style.setName(name);
      manager.setTableStyle(id, style);
      manager.save();
   }

   /** Builds the entry the way AbstractAssetEngine.getTableStyleEntries does, nested paths too. */
   private AssetEntry styleEntry(String id) {
      String full = manager.getTableStyle(id).getName();
      int idx = full.lastIndexOf(LibManager.SEPARATOR);
      String leaf = full.substring(idx + 1);
      String folder = idx < 0 ? null : full.substring(0, idx);
      AssetEntry entry = new AssetEntry(AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.TABLE_STYLE,
                                        folderPath(folder) + "/" + leaf, null);
      entry.setProperty("styleName", full);
      entry.setProperty("styleID", id);
      entry.setProperty("folder", folder);
      return entry;
   }

   private static String folderPath(String folder) {
      return folder == null ? "Table Style" : "Table Style/" + folder.replace('~', '/');
   }

   /** The folder entry the tree gives, the "/Table Style" root entry for {@code null}. */
   private static AssetEntry folderEntry(String folder) {
      AssetEntry entry = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
                                        AssetEntry.Type.TABLE_STYLE_FOLDER,
                                        folder == null ? "/Table Style" : folderPath(folder), null);

      if(folder != null) {
         entry.setProperty("folder", folder);
      }

      return entry;
   }

   private static RenameAssetEvent rename(AssetEntry entry, String newName) {
      return new RenameAssetEvent.Builder().entry(entry).newName(newName).confirmed(false).build();
   }

   private MessageCommand move(AssetEntry entry, String folder) throws Exception {
      return new ChangeAssetController(repo, vsService(), LibManagerProvider.getInstance())
         .changeAsset(new ChangeAssetEvent.Builder()
                         .parent(folderEntry(folder))
                         .entries(new ArrayList<>(List.of(entry)))
                         .confirmed(false).build(), admin());
   }

   private RenameAssetController renameController() throws Exception {
      return new RenameAssetController(repo, SUtil.getRepletRepository(), vsService(),
                                       mock(SecurityProvider.class),
                                       LibManagerProvider.getInstance(),
                                       mock(DataSourceRegistry.class));
   }

   private static ViewsheetService vsService() throws Exception {
      ViewsheetService vs = mock(ViewsheetService.class);
      when(vs.isDuplicatedEntry(any(), any())).thenAnswer(
         inv -> SUtil.isDuplicatedEntry(inv.getArgument(0), inv.getArgument(1)));
      when(vs.localizeAssetEntry(any(), any(), anyBoolean(), any(), anyBoolean())).thenAnswer(
         inv -> inv.getArgument(0));
      when(vs.getRuntimeSheets(any())).thenReturn(new RuntimeSheet[0]);
      return vs;
   }

   private static SRPrincipal admin() {
      IdentityID id = new IdentityID("admin", Organization.getDefaultOrganizationID());
      return new SRPrincipal(id, new IdentityID[] { new IdentityID("Administrator", null) },
                             new String[0], Organization.getDefaultOrganizationID(), 1L);
   }

   private interface Body {
      MessageCommand run() throws Exception;
   }

   private static MessageCommand allow(Body body) throws Exception {
      return denyWriteOn(null, new ArrayList<>(), body);
   }

   /**
    * Every permission check passes except WRITE on table style {@code folder} and below; the
    * resources that were refused are added to {@code denied}.
    */
   private static MessageCommand denyWriteOn(String folder, List<String> denied, Body body)
      throws Exception
   {
      SecurityEngine real = SecurityEngine.getSecurity();
      SecurityEngine spy = mock(SecurityEngine.class, withSettings().spiedInstance(real)
         .defaultAnswer(inv -> {
            if(!inv.getMethod().getName().equals("checkPermission")) {
               return inv.callRealMethod();
            }

            Object[] a = inv.getArguments();
            boolean deny = folder != null && a.length == 4 && a[1] == ResourceType.TABLE_STYLE &&
               a[2] instanceof String res && (res.equals(folder) || res.startsWith(folder + "~")) &&
               a[3] == ResourceAction.WRITE;

            if(deny) {
               denied.add((String) a[2]);
            }

            return !deny;
         }));

      try(MockedStatic<SecurityEngine> st = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS)) {
         st.when(SecurityEngine::getSecurity).thenReturn(spy);
         return body.run();
      }
   }

   private AssetRepository repo;
   private LibManager manager;
}
