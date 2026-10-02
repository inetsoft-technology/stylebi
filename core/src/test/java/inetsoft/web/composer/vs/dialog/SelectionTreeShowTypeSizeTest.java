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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.vs.SelectionTreePropertyDialogModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Switching a selection tree from dropdown to list grows its box to fit the list rows. A marked
 * tree's rows and title lane follow the density and its rows sit inside the card inset, so the
 * box has to make room for all three; an unmarked tree keeps the legacy 20px arithmetic.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@ExtendWith(MockitoExtension.class)
@Tag("core")
class SelectionTreeShowTypeSizeTest {
   @BeforeEach
   void setup() {
      service = new SelectionTreePropertyDialogService(
         vsObjectPropertyService, vsOutputService, engine, trapService, dialogService,
         vsSelectionService, selectionDialogService, assemblyInfoHandler, dataRefService,
         dataSourceRegistry, queryManagerService);
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   // comfortable: six 28px rows, a 30px lane and 16 + 16 of inset
   @Test
   void switchingAMarkedTreeToListMakesRoomForItsRowsAndInset() throws Exception {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SelectionTreeVSAssemblyInfo info = new SelectionTreeVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      VizModernizeUtil.reseedAfterRestore(info);

      assertEquals(6 * 28 + 30 + 32, switchedToList(info).getPixelSize().height);
   }

   // unmarked: six 20px rows and a 20px cell for the title, exactly as before
   @Test
   void switchingAnUnmarkedTreeToListKeepsTheLegacyHeight() throws Exception {
      SelectionTreeVSAssemblyInfo info = new SelectionTreeVSAssemblyInfo();

      assertEquals(6 * 20 + 20, switchedToList(info).getPixelSize().height);
   }

   // a dropdown with six list rows switched to a list. The real getPixelSize hands back the info's
   // own size, which is what the show-type transition resizes in place
   private SelectionTreeVSAssemblyInfo switchedToList(SelectionTreeVSAssemblyInfo info)
      throws Exception
   {
      info.setShowTypeValue(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      info.setListHeight(6);
      info.setPixelSize(new Dimension(info.getPixelSize().width, 30));
      wire(info);
      SelectionTreePropertyDialogModel model =
         service.getSelectionTreePropertyModel("Viewsheet1", "SelectionTree1", null);
      model.getSelectionGeneralPaneModel().setShowType(SelectionVSAssemblyInfo.LIST_SHOW_TYPE);
      model.getSelectionGeneralPaneModel().setListHeight(6);
      model.getSelectionGeneralPaneModel().getGeneralPropPaneModel().getBasicGeneralPaneModel()
         .setName("SelectionTree1");
      service.setSelectionTreePropertyModel("Viewsheet1", "SelectionTree1", model, "", null,
                                            commandDispatcher);
      ArgumentCaptor<SelectionTreeVSAssemblyInfo> captor =
         ArgumentCaptor.forClass(SelectionTreeVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), any(), any(), any(),
         nullable(Principal.class), any(CommandDispatcher.class));
      return captor.getValue();
   }

   private void wire(SelectionTreeVSAssemblyInfo info) throws Exception {
      lenient().when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      lenient().when(rvs.getViewsheet()).thenReturn(viewsheet);
      lenient().when(viewsheet.getAssembly(anyString())).thenReturn(selectionTreeAssembly);
      lenient().when(viewsheet.getAssemblies()).thenReturn(new Assembly[0]);
      lenient().when(viewsheet.getPixelSize(any()))
         .thenAnswer(inv -> ((AssemblyInfo) inv.getArgument(0)).getPixelSize());
      lenient().when(selectionTreeAssembly.getVSAssemblyInfo()).thenReturn(info);
      lenient().when(dialogService.getAssemblyPosition(any(), any())).thenReturn(new Point(0, 0));
      lenient().when(dialogService.getAssemblySize(any(), any())).thenReturn(new Dimension(200, 140));
   }

   @Mock VSOutputService vsOutputService;
   @Mock CommandDispatcher commandDispatcher;
   @Mock RuntimeViewsheet rvs;
   @Mock ViewsheetService engine;
   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock VSTrapService trapService;
   @Mock SelectionTreeVSAssembly selectionTreeAssembly;
   @Mock VSDialogService dialogService;
   @Mock VSSelectionService vsSelectionService;
   @Mock SelectionDialogService selectionDialogService;
   @Mock VSAssemblyInfoHandler assemblyInfoHandler;
   @Mock DataRefModelFactoryService dataRefService;
   @Mock DataSourceRegistry dataSourceRegistry;
   @Mock QueryManagerService queryManagerService;
   @Mock(answer = Answers.RETURNS_DEEP_STUBS)
   private Viewsheet viewsheet;
   private SelectionTreePropertyDialogService service;
}
