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
 * Bug #77837, a table style rename or move must keep the style in the folder of the stored
 * style and must not take its folder or name from the request.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  TableStyleRenameFolderTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TableStyleRenameFolderTest {
   @Configuration
   static class Beans {
      // LibManager.renameTableStyle queues a dependency rewrite through this bean.
      @Bean
      RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }
   }

   private static final String SECRET = "Secret77837";

   @BeforeEach
   void setUp() throws Exception {
      repo = AssetUtil.getAssetRepository(false);
      manager = LibManagerProvider.getInstance().getManager(admin());

      if(!manager.containsFolder(SECRET)) {
         manager.addTableStyleFolder(SECRET);
      }

      manager.save();
   }

   @AfterEach
   void tearDown() throws Exception {
      for(String id : addedStyles) {
         manager.removeTableStyle(id);
      }

      for(String folder : addedFolders) {
         manager.removeTableStyleFolder(folder);
      }

      addedStyles.clear();
      addedFolders.clear();
      manager.save();
   }

   @Test
   void renameWithTildeIsRefused() throws Exception {
      addStyle("Ren77837", "Ren77837");
      MessageCommand[] r = new MessageCommand[1];
      denyWriteOn(SECRET, () -> r[0] = renameController().renameAsset(
         rename(styleEntry("Ren77837"), SECRET + "~Ren77837"), admin()));

      assertError(r[0]);
      assertEquals("Ren77837", manager.getTableStyle("Ren77837").getName());
   }

   @Test
   void renameWithTildeIntoMissingFolderIsRefused() throws Exception {
      addStyle("Ghost77837", "Ghost77837");
      MessageCommand[] r = new MessageCommand[1];
      allow(() -> r[0] = renameController().renameAsset(
         rename(styleEntry("Ghost77837"), "Nope77837~Ghost77837"), admin()));

      assertError(r[0]);
      assertEquals("Ghost77837", manager.getTableStyle("Ghost77837").getName());
   }

   @Test
   void renameWithTildeOntoExistingNameIsRefused() throws Exception {
      addStyle("DupA77837", SECRET + "~Dup77837");
      addStyle("DupB77837", "DupB77837");
      MessageCommand[] r = new MessageCommand[1];
      allow(() -> r[0] = renameController().renameAsset(
         rename(styleEntry("DupB77837"), SECRET + "~Dup77837"), admin()));

      assertError(r[0]);
      assertEquals("DupB77837", manager.getTableStyle("DupB77837").getName());
   }

   @Test
   void renameWithSlashIsRefused() throws Exception {
      addStyle("Slash77837", "Slash77837");
      MessageCommand[] r = new MessageCommand[1];
      allow(() -> r[0] = renameController().renameAsset(
         rename(styleEntry("Slash77837"), "a/b"), admin()));

      assertError(r[0]);
      assertEquals("Slash77837", manager.getTableStyle("Slash77837").getName());
   }

   @Test
   void renameWithForgedFolderKeepsStoredFolder() throws Exception {
      addStyle("Forge77837", "Forge77837");
      AssetEntry entry = styleEntry("Forge77837");
      entry.setProperty("folder", SECRET);
      MessageCommand[] r = new MessageCommand[1];
      denyWriteOn(SECRET, () -> r[0] = renameController().renameAsset(
         rename(entry, "Forge77837x"), admin()));

      assertNull(r[0]);
      assertEquals("Forge77837x", manager.getTableStyle("Forge77837").getName());
   }

   @Test
   void renameChecksPermissionOnStoredStyle() throws Exception {
      addStyle("Decoy77837", "Decoy77837");
      addStyle("Victim77837", SECRET + "~Victim77837");
      AssetEntry entry = styleEntry("Decoy77837");
      entry.setProperty("styleID", "Victim77837");
      MessageCommand[] r = new MessageCommand[1];
      denyWriteOn(SECRET, () -> r[0] = renameController().renameAsset(
         rename(entry, "Pwned77837"), admin()));

      assertError(r[0]);
      assertEquals(SECRET + "~Victim77837", manager.getTableStyle("Victim77837").getName());
      assertEquals("Decoy77837", manager.getTableStyle("Decoy77837").getName());
   }

   @Test
   void renameOfUnknownStyleIsRefused() throws Exception {
      addStyle("Known77837", "Known77837");
      AssetEntry entry = styleEntry("Known77837");
      entry.setProperty("styleID", "Missing77837");
      MessageCommand[] r = new MessageCommand[1];
      allow(() -> r[0] = renameController().renameAsset(rename(entry, "Other77837"), admin()));

      assertError(r[0]);
      assertEquals("Known77837", manager.getTableStyle("Known77837").getName());
   }

   @Test
   void plainRenameInFolderStillWorks() throws Exception {
      addStyle("Plain77837", SECRET + "~Plain77837");
      MessageCommand[] r = new MessageCommand[1];
      allow(() -> r[0] = renameController().renameAsset(
         rename(styleEntry("Plain77837"), "Plain77837b"), admin()));

      assertNull(r[0]);
      assertEquals(SECRET + "~Plain77837b", manager.getTableStyle("Plain77837").getName());
   }

   @Test
   void moveIntoDeniedFolderIsRefused() throws Exception {
      addStyle("MoveCtl77837", "MoveCtl77837");
      MessageCommand[] r = new MessageCommand[1];
      denyWriteOn(SECRET, () -> r[0] = move(styleEntry("MoveCtl77837"), SECRET));

      assertNotNull(r[0]);
      assertEquals("MoveCtl77837", manager.getTableStyle("MoveCtl77837").getName());
   }

   @Test
   void moveWithForgedSourceFolderIsRefused() throws Exception {
      addStyle("MvForge77837", "MvForge77837");
      AssetEntry entry = styleEntry("MvForge77837");
      entry.setProperty("folder", SECRET);
      MessageCommand[] r = new MessageCommand[1];
      denyWriteOn(SECRET, () -> r[0] = move(entry, SECRET));

      assertNotNull(r[0]);
      assertEquals("MvForge77837", manager.getTableStyle("MvForge77837").getName());
   }

   @Test
   void moveWithForgedSourceFolderRunsDuplicateCheck() throws Exception {
      addStyle("MvDupA77837", SECRET + "~MvDup77837");
      addStyle("MvDupB77837", "MvDup77837");
      AssetEntry entry = styleEntry("MvDupB77837");
      entry.setProperty("folder", SECRET);
      MessageCommand[] r = new MessageCommand[1];
      allow(() -> r[0] = move(entry, SECRET));

      assertError(r[0]);
      assertEquals("MvDup77837", manager.getTableStyle("MvDupB77837").getName());
   }

   @Test
   void renameOntoExistingNameInStoredFolderIsRefused() throws Exception {
      addStyle("RnDupA77837", SECRET + "~RnDupA77837");
      addStyle("RnDupB77837", SECRET + "~RnDupB77837");
      AssetEntry entry = styleEntry("RnDupA77837");
      entry.setProperty("folder", null);
      MessageCommand[] r = new MessageCommand[1];
      allow(() -> r[0] = renameController().renameAsset(rename(entry, "RnDupB77837"), admin()));

      assertError(r[0]);
      assertEquals(SECRET + "~RnDupA77837", manager.getTableStyle("RnDupA77837").getName());
   }

   @Test
   void moveWithForgedLeafKeepsStoredLeaf() throws Exception {
      String sub = SECRET + "~Sub77837";
      addFolder(sub);
      addStyle("MvLeaf77837", "MvLeaf77837");
      AssetEntry entry = styleEntry("MvLeaf77837", "Sub77837~Pwn77837");
      MessageCommand[] r = new MessageCommand[1];
      denyWriteOn(sub, () -> r[0] = move(entry, SECRET));

      String name = manager.getTableStyle("MvLeaf77837").getName();
      assertFalse(name.startsWith(sub + "~"), "style was placed in the denied folder: " + name);
      assertNull(r[0]);
      assertEquals(SECRET + "~MvLeaf77837", name);
   }

   @Test
   void moveWithForgedLeafChecksDuplicateOfStoredLeaf() throws Exception {
      addStyle("MvCollA77837", SECRET + "~MvColl77837");
      addStyle("MvCollB77837", "MvColl77837");
      AssetEntry entry = styleEntry("MvCollB77837", "Other77837");
      MessageCommand[] r = new MessageCommand[1];
      allow(() -> r[0] = move(entry, SECRET));

      assertError(r[0]);
      assertEquals("MvColl77837", manager.getTableStyle("MvCollB77837").getName());
   }

   @Test
   void moveIntoMissingFolderIsRefused() throws Exception {
      addStyle("MvGhost77837", "MvGhost77837");
      MessageCommand[] r = new MessageCommand[1];
      allow(() -> r[0] = move(styleEntry("MvGhost77837"), "Nope77837"));

      assertNotNull(r[0], "expected the request to be refused");
      assertEquals("MvGhost77837", manager.getTableStyle("MvGhost77837").getName());
   }

   // ---- helpers ----

   private static void assertError(MessageCommand command) {
      assertNotNull(command, "expected the request to be refused");
      assertEquals(MessageCommand.Type.ERROR, command.getType(), command.getMessage());
   }

   private void addStyle(String id, String name) throws Exception {
      XTableStyle style = new XTableStyle(new DefaultTableLens(2, 2));
      style.setID(id);
      style.setName(name);
      manager.setTableStyle(id, style);
      manager.save();
      addedStyles.add(id);
   }

   private void addFolder(String folder) throws Exception {
      manager.addTableStyleFolder(folder);
      manager.save();
      addedFolders.add(folder);
   }

   /** Builds the entry the way AbstractAssetEngine.getTableStyleEntries does. */
   private AssetEntry styleEntry(String id) {
      String full = manager.getTableStyle(id).getName();
      return styleEntry(id, full.substring(full.lastIndexOf(LibManager.SEPARATOR) + 1));
   }

   /** Same as {@link #styleEntry(String)}, with the entry path's leaf set to {@code leaf}. */
   private AssetEntry styleEntry(String id, String leaf) {
      String full = manager.getTableStyle(id).getName();
      int idx = full.lastIndexOf(LibManager.SEPARATOR);
      AssetEntry entry = new AssetEntry(AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.TABLE_STYLE,
                                        "Table Style/" + leaf, null);
      entry.setProperty("styleName", full);
      entry.setProperty("styleID", id);
      entry.setProperty("folder", idx < 0 ? null : full.substring(0, idx));
      return entry;
   }

   private AssetEntry folderEntry(String folder) {
      AssetEntry entry = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
                                        AssetEntry.Type.TABLE_STYLE_FOLDER,
                                        "Table Style/" + folder, null);
      entry.setProperty("folder", folder);
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
                                       LibManagerProvider.getInstance());
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
      void run() throws Exception;
   }

   private static void allow(Body body) throws Exception {
      denyWriteOn(null, body);
   }

   /** Every permission check passes except WRITE on table style folder {@code folder} and below. */
   private static void denyWriteOn(String folder, Body body) throws Exception {
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
            return !deny;
         }));

      try(MockedStatic<SecurityEngine> st = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS)) {
         st.when(SecurityEngine::getSecurity).thenReturn(spy);
         body.run();
      }
   }

   private AssetRepository repo;
   private LibManager manager;
   private final List<String> addedStyles = new ArrayList<>();
   private final List<String> addedFolders = new ArrayList<>();
}
