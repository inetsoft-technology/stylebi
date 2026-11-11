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

import inetsoft.analytic.composition.SheetLibraryService;
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
import inetsoft.web.composer.tablestyle.*;
import inetsoft.web.composer.tablestyle.service.TableStyleService;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81052, save-as matches an existing style by NAME. It used to look the name up
 * ID-first (and fuzzy on folder leaves), so a style renamed or moved away from its ID, or a
 * folder style with the same leaf, was taken for the style being saved and overwritten.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  TableStyleSaveAsRenamedIdTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TableStyleSaveAsRenamedIdTest {
   @Configuration
   static class Beans {
      @Bean
      RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }
   }

   private static final String F = "Sa81052F";

   @BeforeEach
   void setUp() throws Exception {
      repo = AssetUtil.getAssetRepository(false);
      manager = LibManagerProvider.getInstance().getManager(admin());

      if(!manager.containsFolder(F)) {
         manager.addTableStyleFolder(F);
         manager.save();
      }

      TableStyleService service = spy(new TableStyleService(
         mock(SheetLibraryService.class), mock(AssetRepository.class),
         LibManagerProvider.getInstance()));
      doReturn(new DefaultTableLens(new Object[][] { { "H" }, { "V" } })).when(service)
         .getTableModel();
      controller = new TableStyleController(service, LibManagerProvider.getInstance());
   }

   @Test
   void saveAsAfterRenameKeepsRenamedStyle() throws Exception {
      String id1 = saveAs(null, "T1_81052");
      assertEquals("T1_81052", id1);
      manager.renameTableStyle("T1_81052", "T1x_81052", id1);
      manager.save();

      String id2 = saveAs(null, "T1_81052");

      assertNotEquals(id1, id2);
      assertEquals("T1x_81052", manager.getTableStyle(id1).getName());
      assertEquals("T1_81052", manager.getTableStyle(id2).getName());
   }

   @Test
   void saveAsAfterMoveIntoFolderKeepsMovedStyle() throws Exception {
      String id1 = saveAs(null, "T2_81052");
      MessageCommand r = move(id1, F);
      assertNull(r, () -> r.getMessage());
      assertEquals(F + "~T2_81052", manager.getTableStyle(id1).getName());

      String id2 = saveAs(null, "T2_81052");

      assertNotEquals(id1, id2);
      assertEquals(F + "~T2_81052", manager.getTableStyle(id1).getName());
      assertEquals("T2_81052", manager.getTableStyle(id2).getName());
   }

   @Test
   void rootSaveAsDoesNotOverwriteFolderStyleWithSameLeaf() throws Exception {
      String id1 = saveAs(F, "T3_81052");
      assertEquals(F + "~T3_81052", manager.getTableStyle(id1).getName());

      String id2 = saveAs(null, "T3_81052");

      assertNotEquals(id1, id2);
      assertEquals(F + "~T3_81052", manager.getTableStyle(id1).getName());
      assertEquals("T3_81052", manager.getTableStyle(id2).getName());
   }

   @Test
   void saveAsSameNameStillOverwritesWithOwnId() throws Exception {
      String id1 = saveAs(null, "T4_81052");
      assertEquals(id1, saveAs(null, "T4_81052"));

      // the folder style gets a suffixed ID, it is still the one overwritten
      String id2 = saveAs(F, "T4_81052");
      assertNotEquals(id1, id2);
      assertEquals(id2, saveAs(F, "T4_81052"));
      assertEquals("T4_81052", manager.getTableStyle(id1).getName());
   }

   private String saveAs(String folder, String name) throws Exception {
      SaveTableStyleDialogModel saveModel = new SaveTableStyleDialogModel();
      saveModel.setFolder(folder);
      saveModel.setName(name);
      TableStyleRequestModel request = new TableStyleRequestModel();
      request.setSaveModel(saveModel);
      return controller.saveAsTableStyle(request, admin());
   }

   private MessageCommand move(String id, String folder) throws Exception {
      XTableStyle style = manager.getTableStyle(id);
      String full = style.getName();
      int idx = full.lastIndexOf(LibManager.SEPARATOR);
      String leaf = full.substring(idx + 1);
      String from = idx < 0 ? null : full.substring(0, idx);
      AssetEntry entry = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
                                        AssetEntry.Type.TABLE_STYLE,
                                        (from == null ? "Table Style" : "Table Style/" + from) +
                                           "/" + leaf, null);
      entry.setProperty("styleName", full);
      entry.setProperty("styleID", id);
      entry.setProperty("folder", from);
      AssetEntry parent = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
                                         AssetEntry.Type.TABLE_STYLE_FOLDER,
                                         folder == null ? "/Table Style" : "Table Style/" + folder,
                                         null);

      if(folder != null) {
         parent.setProperty("folder", folder);
      }

      ViewsheetService vs = mock(ViewsheetService.class);
      when(vs.isDuplicatedEntry(any(), any())).thenAnswer(
         inv -> SUtil.isDuplicatedEntry(inv.getArgument(0), inv.getArgument(1)));
      when(vs.localizeAssetEntry(any(), any(), anyBoolean(), any(), anyBoolean())).thenAnswer(
         inv -> inv.getArgument(0));
      when(vs.getRuntimeSheets(any())).thenReturn(new RuntimeSheet[0]);
      SecurityEngine real = SecurityEngine.getSecurity();
      SecurityEngine spy = mock(SecurityEngine.class, withSettings().spiedInstance(real)
         .defaultAnswer(inv -> inv.getMethod().getName().equals("checkPermission") ? true :
            inv.callRealMethod()));

      try(MockedStatic<SecurityEngine> st = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS)) {
         st.when(SecurityEngine::getSecurity).thenReturn(spy);
         return new ChangeAssetController(repo, vs, LibManagerProvider.getInstance())
            .changeAsset(new ChangeAssetEvent.Builder().parent(parent)
                            .entries(new ArrayList<>(List.of(entry))).confirmed(false).build(),
                         admin());
      }
   }

   private static SRPrincipal admin() {
      IdentityID id = new IdentityID("admin", Organization.getDefaultOrganizationID());
      return new SRPrincipal(id, new IdentityID[] { new IdentityID("Administrator", null) },
                             new String[0], Organization.getDefaultOrganizationID(), 1L);
   }

   private AssetRepository repo;
   private LibManager manager;
   private TableStyleController controller;
}
