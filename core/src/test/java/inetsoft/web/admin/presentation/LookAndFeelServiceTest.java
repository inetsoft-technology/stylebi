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
package inetsoft.web.admin.presentation;

/*
 * Test strategy
 *
 * PresentationSettingsController.applySettings() passes globalSettings=false for an org admin
 * in a multi-tenant deployment. LookAndFeelService.setModel() used to ignore that flag for the
 * report list type, auto-expand, the user font files under sree.home/portal/font and the
 * dataspace-root userformat.xml, so an org admin's save changed them for every organization
 * (Bug #77054). The EM hides those controls from org admins, but the endpoint did not enforce it.
 *
 * Behavioral guarantees covered:
 *
 * [G1] An org-scoped save does not touch the global report list type, auto-expand, user font
 *      faces/files or font style, even when the payload asks for the default fonts.
 * [G2] An org-scoped save does not write the global userformat.xml.
 * [G3] An org-scoped save still writes the org-scoped repository.tree.sort property.
 * [G4] A global save still writes all of the above.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.FontFaceModel;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.DataSpace;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.util.css.CSSDictionary;
import inetsoft.web.admin.model.FileData;
import inetsoft.web.admin.presentation.model.LookAndFeelSettingsModel;
import inetsoft.web.notifications.NotificationService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class LookAndFeelServiceTest {
   private LookAndFeelService service;
   private PortalThemesManager manager;
   private DataSpace dataSpace;
   private Principal principal;
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<CSSDictionary> cssStatic;
   private MockedStatic<SUtil> sutilStatic;
   private MockedStatic<Audit> auditStatic;

   @BeforeEach
   void setUp() throws Exception {
      manager = mock(PortalThemesManager.class);
      dataSpace = mock(DataSpace.class);
      principal = mock(Principal.class);

      FontFaceModel fontFace = mock(FontFaceModel.class);
      when(fontFace.getFileNamePrefix()).thenReturn("GlobalFont");
      when(manager.getUserFontFaces()).thenReturn(List.of(fontFace));
      when(dataSpace.exists(anyString(), anyString())).thenReturn(true);

      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
      sreeEnvStatic.when(() -> SreeEnv.getProperty("sree.home")).thenReturn("/home");

      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn("orgA");
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      cssStatic = mockStatic(CSSDictionary.class);
      sutilStatic = mockStatic(SUtil.class);
      sutilStatic.when(() -> SUtil.getActionRecord(any(Principal.class), any(), any(), any()))
         .thenReturn(mock(ActionRecord.class));
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));

      service = new LookAndFeelService();
      inject("portalThemesManager", manager);
      inject("dataSpace", dataSpace);
      inject("notificationService", mock(NotificationService.class));
   }

   @AfterEach
   void tearDown() {
      auditStatic.close();
      sutilStatic.close();
      cssStatic.close();
      orgManagerStatic.close();
      sreeEnvStatic.close();
   }

   @Test
   void orgScopedSaveDoesNotChangeGlobalFontsOrPortalThemeSettings() throws Exception {
      service.setModel(model(true), principal, false);

      verify(manager, never()).setReportListType(anyInt());
      verify(manager, never()).setAutoExpand(anyBoolean());
      verify(manager, never()).setUserFontFaces(any());
      verify(manager, never()).setFontStyle(anyBoolean());
      verify(dataSpace, never()).delete(eq("/home/portal/font"), anyString());
   }

   @Test
   void orgScopedSaveWithCustomFontsDoesNotWriteGlobalFontFiles() throws Exception {
      LookAndFeelSettingsModel model = LookAndFeelSettingsModel.builder()
         .from(model(false))
         .userFonts(List.of("OrgFont"))
         .deleteFontFaces(List.of(manager.getUserFontFaces().get(0)))
         .build();

      service.setModel(model, principal, false);

      verify(dataSpace, never()).beginTransaction();
      verify(dataSpace, never()).delete(eq("/home/portal/font"), anyString());
      verify(manager, never()).setUserFontFaces(any());
      verify(manager, never()).setFontStyle(anyBoolean());
   }

   @Test
   void orgScopedSaveDoesNotWriteGlobalUserFormatFile() throws Exception {
      service.setModel(model(true), principal, false);

      verify(dataSpace, never()).withOutputStream(isNull(), eq("userformat.xml"), any());
   }

   @Test
   void orgScopedSaveStillWritesOrgScopedSortProperty() throws Exception {
      service.setModel(model(true), principal, false);

      sreeEnvStatic.verify(() -> SreeEnv.setProperty("repository.tree.sort", "Ascending", true));
   }

   @Test
   void globalSaveStillWritesGlobalSettings() throws Exception {
      service.setModel(model(true), principal, true);

      verify(manager).setReportListType(0);
      verify(manager).setAutoExpand(true);
      verify(manager).setUserFontFaces(Collections.emptyMap());
      verify(manager).setFontStyle(false);
      verify(dataSpace).delete("/home/portal/font", "GlobalFont.ttf");
      verify(dataSpace).withOutputStream(isNull(), eq("userformat.xml"), any());
      sreeEnvStatic.verify(() -> SreeEnv.setProperty("repository.tree.sort", "Ascending", false));
   }

   private LookAndFeelSettingsModel model(boolean defaultFont) {
      String userformat = Base64.getEncoder()
         .encodeToString("<userformat/>".getBytes(StandardCharsets.UTF_8));

      return LookAndFeelSettingsModel.builder()
         .ascending(true)
         .repositoryTree(true)
         .expand(true)
         .defaultLogo(true)
         .defaultFavicon(true)
         .defaultViewsheet(true)
         .defaultFont(defaultFont)
         .userFonts(new ArrayList<>())
         .fontFaces(new ArrayList<>())
         .userformatFile(FileData.builder().name("userformat.xml").content(userformat).build())
         .vsEnabled(true)
         .build();
   }

   private void inject(String name, Object value) throws Exception {
      Field field = LookAndFeelService.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(service, value);
   }
}
