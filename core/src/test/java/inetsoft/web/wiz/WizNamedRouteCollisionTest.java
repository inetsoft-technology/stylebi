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
package inetsoft.web.wiz;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.web.composer.tablestyle.service.TableStyleService;
import inetsoft.web.wiz.controller.WizTableStyleController;
import inetsoft.web.wiz.pairing.SheetAgentFeature;
import inetsoft.web.wiz.service.WizTableStylePreviewRenderer;
import inetsoft.web.wiz.viewsheet.LayoutReadService;
import inetsoft.web.wiz.viewsheet.ViewsheetAssemblyAgentController;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bug #78584: a user-chosen name in a GET path segment that has a literal sibling is shadowed by
 * the sibling (a layout named "list", a table style whose styleId is "defaults"). The name-reading
 * routes take the name as a query parameter instead; the old path routes stay for older plugins.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class WizNamedRouteCollisionTest {
   @BeforeEach
   void setUp() {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      layoutReadService = mock(LayoutReadService.class);
      libManager = mock(LibManager.class);
      LibManagerProvider libManagerProvider = mock(LibManagerProvider.class);
      when(libManagerProvider.getManager(nullable(Principal.class))).thenReturn(libManager);
      when(libManager.getTableStyles(nullable(String.class), anyBoolean()))
         .thenReturn(new XTableStyle[0]);
      when(libManager.getTableStyleFolders(nullable(String.class), anyBoolean()))
         .thenReturn(new String[0]);

      ViewsheetAssemblyAgentController agentController = new ViewsheetAssemblyAgentController(
         feature, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, layoutReadService, null, null, null, null, null, null, null);
      WizTableStyleController tableStyleController = new WizTableStyleController(
         libManagerProvider, mock(TableStyleService.class), mock(SecurityEngine.class),
         mock(WizTableStylePreviewRenderer.class));

      // WebConfig.configurePathMatch sets a UrlPathHelper, which selects AntPathMatcher matching
      mvc = MockMvcBuilders.standaloneSetup(agentController, tableStyleController)
         .setPatternParser(null)
         .build();
   }

   @Test
   void layoutNamedListIsReadByName() throws Exception {
      mvc.perform(get(LAYOUT + "?name=list")).andExpect(status().isOk());

      verify(layoutReadService).get(eq("tok"), any(), eq("list"));
      verify(layoutReadService, never()).list(any(), any());
   }

   @Test
   void oldLayoutRoutesStillWork() throws Exception {
      mvc.perform(get(LAYOUT + "/list")).andExpect(status().isOk());
      mvc.perform(get(LAYOUT + "/Phone")).andExpect(status().isOk());

      verify(layoutReadService).list(eq("tok"), any());
      verify(layoutReadService).get(eq("tok"), any(), eq("Phone"));
   }

   @Test
   void tableStyleNamedDefaultsIsReadById() {
      // no such style in the mocked library, so read() fails after the lookup
      ServletException e = assertThrows(
         ServletException.class,
         () -> mvc.perform(get(TABLE_STYLE).param("styleId", "defaults")));

      assertTrue(e.getCause().getMessage().contains("styleId 'defaults'"),
                 e.getCause().getMessage());
      verify(libManager).getTableStyle("defaults");
   }

   @Test
   void oldTableStyleRoutesStillWork() throws Exception {
      mvc.perform(get(TABLE_STYLE)).andExpect(status().isOk());
      assertThrows(ServletException.class, () -> mvc.perform(get(TABLE_STYLE + "/Bordered")));

      verify(libManager).getTableStyles(null, true);
      verify(libManager).getTableStyle("Bordered");
   }

   private static final String LAYOUT = "/api/wiz/v1/agent/viewsheet/tok/layout";
   private static final String TABLE_STYLE = "/api/wiz/v1/table-style";
   private MockMvc mvc;
   private LayoutReadService layoutReadService;
   private LibManager libManager;
}
