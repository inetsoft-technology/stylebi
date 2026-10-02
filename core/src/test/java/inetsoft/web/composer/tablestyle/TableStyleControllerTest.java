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
package inetsoft.web.composer.tablestyle;

import inetsoft.analytic.composition.SheetLibraryService;
import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.web.composer.tablestyle.service.TableStyleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #76934: saveTableStyle and saveAsTableStyle must enforce WRITE permission themselves
 * instead of relying on the advisory check-save(-as)-permission endpoints.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableStyleControllerTest {
   private static final String STYLE_ID = "style-1";
   private static final String STYLE_NAME = "Shared" + LibManager.SEPARATOR + "Corporate";

   @Mock SheetLibraryService sheetLibraryService;
   @Mock AssetRepository assetRepository;
   @Mock LibManagerProvider libManagerProvider;
   @Mock LibManager libManager;
   @Mock XTableStyle storedStyle;
   @Mock XTableStyle clonedStyle;
   @Mock TableStyleFormatModel styleFormat;

   private TableStyleController controller;
   private SRPrincipal principal;

   @BeforeEach
   void setUp() {
      when(libManagerProvider.getManager()).thenReturn(libManager);
      when(libManagerProvider.getManager(any(java.security.Principal.class))).thenReturn(libManager);
      when(libManager.getTableStyle(STYLE_ID)).thenReturn(storedStyle);
      when(storedStyle.clone()).thenReturn(clonedStyle);
      when(clonedStyle.getName()).thenReturn(STYLE_NAME);
      when(clonedStyle.getID()).thenReturn(STYLE_ID);
      when(libManager.getNextStyleID(anyString())).thenReturn("style-new");

      TableStyleService service =
         spy(new TableStyleService(sheetLibraryService, assetRepository, libManagerProvider));
      // the real sample table needs a swapper bean this lightweight context does not provide
      doReturn(new DefaultTableLens(new Object[][] { { "Header" }, { "Value" } }))
         .when(service).getTableModel();
      controller = new TableStyleController(service, libManagerProvider);
      principal = new SRPrincipal(new IdentityID("user1", Organization.getDefaultOrganizationID()),
         new IdentityID[0], new String[0], Organization.getDefaultOrganizationID(),
         Tool.getSecureRandom().nextLong());
   }

   @Test
   void saveDeniedDoesNotWriteLibrary() throws Exception {
      denyWrite();

      assertThrows(MessageException.class, () -> controller.saveTableStyle(createSaveModel(), principal));

      verify(libManager, never()).setTableStyle(anyString(), any());
      verify(libManager, never()).save();
   }

   @Test
   void saveAllowedChecksStoredStyleAndWrites() throws Exception {
      controller.saveTableStyle(createSaveModel(), principal);

      assertCheckedStyle(STYLE_NAME);
      verify(libManager).setTableStyle(STYLE_ID, clonedStyle);
      verify(libManager).save();
   }

   @Test
   void saveAsDeniedDoesNotWriteLibrary() throws Exception {
      denyWrite();

      assertThrows(MessageException.class,
         () -> controller.saveAsTableStyle(createSaveAsModel("Shared", "Corporate"), principal));

      verify(libManager, never()).setTableStyle(anyString(), any());
      verify(libManager, never()).save();
   }

   @Test
   void saveAsAllowedChecksTargetStyleAndWrites() throws Exception {
      controller.saveAsTableStyle(createSaveAsModel("Shared", "NewStyle"), principal);

      assertCheckedStyle("Shared" + LibManager.SEPARATOR + "NewStyle");
      verify(libManager).setTableStyle(eq("style-new"), any(XTableStyle.class));
      verify(libManager).save();
   }

   private void denyWrite() throws Exception {
      doThrow(new MessageException("denied")).when(assetRepository)
         .checkAssetPermission(eq(principal), any(AssetEntry.class), eq(ResourceAction.WRITE));
   }

   private void assertCheckedStyle(String styleName) throws Exception {
      ArgumentCaptor<AssetEntry> captor = ArgumentCaptor.forClass(AssetEntry.class);
      verify(assetRepository).checkAssetPermission(eq(principal), captor.capture(),
         eq(ResourceAction.WRITE));
      AssetEntry checked = captor.getValue();
      assertEquals(AssetRepository.COMPONENT_SCOPE, checked.getScope());
      assertEquals(AssetEntry.Type.TABLE_STYLE, checked.getType());
      assertEquals(styleName, checked.getProperty("styleName"));
   }

   private TableStyleModel createSaveModel() {
      TableStyleModel model = new TableStyleModel();
      model.setStyleId(STYLE_ID);
      model.setStyleFormat(styleFormat);
      return model;
   }

   private TableStyleRequestModel createSaveAsModel(String folder, String name) {
      SaveTableStyleDialogModel saveModel = new SaveTableStyleDialogModel();
      saveModel.setFolder(folder);
      saveModel.setName(name);

      TableStyleRequestModel request = new TableStyleRequestModel();
      request.setSaveModel(saveModel);
      return request;
   }
}
