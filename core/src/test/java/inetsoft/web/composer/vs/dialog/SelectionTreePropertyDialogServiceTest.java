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
import inetsoft.test.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.vs.SelectionTreePropertyDialogModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith({MockitoExtension.class})
@Tag("core")
class SelectionTreePropertyDialogServiceTest {
   @BeforeEach
   void setup(){
      service = new SelectionTreePropertyDialogService(
         vsObjectPropertyService,
         vsOutputService,
         engine,
         trapService,
         dialogService,
         vsSelectionService,
         selectionDialogService,
         assemblyInfoHandler,
         dataRefService,
         dataSourceRegistry);
   }

   @Test
   void isLabelValueSet() throws Exception{
      given(selectionTreePropertyDialogModel.getSelectionTreePaneModel().getLabel())
         .willReturn("mockLabel");
      given(selectionTreePropertyDialogModel.getSelectionGeneralPaneModel()
               .getGeneralPropPaneModel().getBasicGeneralPaneModel().getName())
         .willReturn("SelectionTree1");
      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(selectionTreeAssembly);
      doReturn(new SelectionTreeVSAssemblyInfo())
         .when(selectionTreeAssembly)
         .getVSAssemblyInfo();

      when(viewsheet.getViewsheet().getPixelSize()).thenReturn(size);
      when(selectionTreePropertyDialogModel.getSelectionTreePaneModel().getMode()).thenReturn(2);

      service.setSelectionTreePropertyModel("Viewsheet1", "SelectionTree1",
                                            selectionTreePropertyDialogModel,
                                            "", null, commandDispatcher);

      ArgumentCaptor<SelectionTreeVSAssemblyInfo> argument =
         ArgumentCaptor.forClass(SelectionTreeVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(any(RuntimeViewsheet.class),
                                                         argument.capture(),
                                                         any(String.class),
                                                         any(String.class),
                                                         any(String.class),
                                                         nullable(Principal.class),
                                                         any(CommandDispatcher.class));

      assertNotNull(argument.getValue().getLabelValue());

   }

   @Test
   void bottomTabsPositionAdjustedOnShowTypeChange() throws Exception {
      SelectionTreeVSAssemblyInfo info = new SelectionTreeVSAssemblyInfo();
      info.setShowTypeValue(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      info.setPixelOffset(new Point(50, 400));
      info.setPixelSize(new Dimension(200, 20));

      TabVSAssemblyInfo tabInfo = new TabVSAssemblyInfo();
      tabInfo.setBottomTabsValue(true);
      tabInfo.setPixelOffset(new Point(0, 420));

      TabVSAssembly tabAssembly = Mockito.mock(TabVSAssembly.class);
      when(tabAssembly.getVSAssemblyInfo()).thenReturn(tabInfo);
      when(selectionTreeAssembly.getContainer()).thenReturn(tabAssembly);
      when(selectionTreeAssembly.getVSAssemblyInfo()).thenReturn(info);

      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(selectionTreeAssembly);
      when(viewsheet.getPixelSize(any())).thenReturn(new Dimension(200, 20));

      given(selectionTreePropertyDialogModel.getSelectionGeneralPaneModel()
               .getGeneralPropPaneModel().getBasicGeneralPaneModel().getName())
         .willReturn("SelectionTree1");
      given(selectionTreePropertyDialogModel.getSelectionGeneralPaneModel()
               .getShowType())
         .willReturn(SelectionVSAssemblyInfo.LIST_SHOW_TYPE);
      given(selectionTreePropertyDialogModel.getSelectionGeneralPaneModel()
               .getListHeight())
         .willReturn(6);
      given(selectionTreePropertyDialogModel.getSelectionGeneralPaneModel()
               .getSizePositionPaneModel().getTitleHeight())
         .willReturn(20);
      given(selectionTreePropertyDialogModel.getSelectionGeneralPaneModel()
               .getSizePositionPaneModel().getCellHeight())
         .willReturn(20);
      given(selectionTreePropertyDialogModel.getSelectionTreePaneModel().getMode())
         .willReturn(2);

      service.setSelectionTreePropertyModel("Viewsheet1", "SelectionTree1",
                                             selectionTreePropertyDialogModel,
                                             "", null, commandDispatcher);

      ArgumentCaptor<SelectionTreeVSAssemblyInfo> argument =
         ArgumentCaptor.forClass(SelectionTreeVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(any(RuntimeViewsheet.class),
                                                         argument.capture(),
                                                         any(String.class),
                                                         any(String.class),
                                                         any(String.class),
                                                         nullable(Principal.class),
                                                         any(CommandDispatcher.class));

      SelectionTreeVSAssemblyInfo result = argument.getValue();
      // switching from dropdown to list: listHeight(6) * cellHeight(20) = 120
      // (titleVisible defaults to false, so no extra cellHeight added)
      // position should be: tabTop(420) - 120 = 300
      assertEquals(300, result.getPixelOffset().y);
   }

   @Test
   void bottomTabsPositionAdjustedOnListToDropdown() throws Exception {
      SelectionTreeVSAssemblyInfo info = new SelectionTreeVSAssemblyInfo();
      info.setShowTypeValue(SelectionVSAssemblyInfo.LIST_SHOW_TYPE);
      info.setPixelOffset(new Point(50, 300));
      info.setPixelSize(new Dimension(200, 120));

      TabVSAssemblyInfo tabInfo = new TabVSAssemblyInfo();
      tabInfo.setBottomTabsValue(true);
      tabInfo.setPixelOffset(new Point(0, 420));

      TabVSAssembly tabAssembly = Mockito.mock(TabVSAssembly.class);
      when(tabAssembly.getVSAssemblyInfo()).thenReturn(tabInfo);
      when(selectionTreeAssembly.getContainer()).thenReturn(tabAssembly);
      when(selectionTreeAssembly.getVSAssemblyInfo()).thenReturn(info);

      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(selectionTreeAssembly);
      when(viewsheet.getPixelSize(any())).thenReturn(new Dimension(200, 120));

      given(selectionTreePropertyDialogModel.getSelectionGeneralPaneModel()
               .getGeneralPropPaneModel().getBasicGeneralPaneModel().getName())
         .willReturn("SelectionTree1");
      given(selectionTreePropertyDialogModel.getSelectionGeneralPaneModel()
               .getShowType())
         .willReturn(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      given(selectionTreePropertyDialogModel.getSelectionGeneralPaneModel()
               .getSizePositionPaneModel().getTitleHeight())
         .willReturn(20);
      given(selectionTreePropertyDialogModel.getSelectionGeneralPaneModel()
               .getSizePositionPaneModel().getCellHeight())
         .willReturn(20);
      given(selectionTreePropertyDialogModel.getSelectionTreePaneModel().getMode())
         .willReturn(2);

      service.setSelectionTreePropertyModel("Viewsheet1", "SelectionTree1",
                                             selectionTreePropertyDialogModel,
                                             "", null, commandDispatcher);

      ArgumentCaptor<SelectionTreeVSAssemblyInfo> argument =
         ArgumentCaptor.forClass(SelectionTreeVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(any(RuntimeViewsheet.class),
                                                         argument.capture(),
                                                         any(String.class),
                                                         any(String.class),
                                                         any(String.class),
                                                         nullable(Principal.class),
                                                         any(CommandDispatcher.class));

      SelectionTreeVSAssemblyInfo result = argument.getValue();
      // switching from list to dropdown: size.height = titleHeight = 20
      // position should be: tabTop(420) - 20 = 400
      assertEquals(400, result.getPixelOffset().y);
   }

   @Test
   void bottomTabsPositionAdjustedOnHeightChange() throws Exception {
      SelectionTreeVSAssemblyInfo info = new SelectionTreeVSAssemblyInfo();
      info.setShowTypeValue(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      info.setTitleHeightValue(20);
      info.setPixelOffset(new Point(50, 400));
      info.setPixelSize(new Dimension(200, 20));

      TabVSAssemblyInfo tabInfo = new TabVSAssemblyInfo();
      tabInfo.setBottomTabsValue(true);
      tabInfo.setPixelOffset(new Point(0, 420));

      TabVSAssembly tabAssembly = Mockito.mock(TabVSAssembly.class);
      when(tabAssembly.getVSAssemblyInfo()).thenReturn(tabInfo);
      when(selectionTreeAssembly.getContainer()).thenReturn(tabAssembly);
      when(selectionTreeAssembly.getVSAssemblyInfo()).thenReturn(info);

      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(selectionTreeAssembly);
      when(viewsheet.getPixelSize(any())).thenReturn(new Dimension(200, 20));

      // keep show type as dropdown, but change title height to 30
      given(selectionTreePropertyDialogModel.getSelectionGeneralPaneModel()
               .getGeneralPropPaneModel().getBasicGeneralPaneModel().getName())
         .willReturn("SelectionTree1");
      given(selectionTreePropertyDialogModel.getSelectionGeneralPaneModel()
               .getShowType())
         .willReturn(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      given(selectionTreePropertyDialogModel.getSelectionGeneralPaneModel()
               .getSizePositionPaneModel().getTitleHeight())
         .willReturn(30);
      given(selectionTreePropertyDialogModel.getSelectionGeneralPaneModel()
               .getSizePositionPaneModel().getCellHeight())
         .willReturn(20);
      given(selectionTreePropertyDialogModel.getSelectionTreePaneModel().getMode())
         .willReturn(2);

      service.setSelectionTreePropertyModel("Viewsheet1", "SelectionTree1",
                                             selectionTreePropertyDialogModel,
                                             "", null, commandDispatcher);

      ArgumentCaptor<SelectionTreeVSAssemblyInfo> argument =
         ArgumentCaptor.forClass(SelectionTreeVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(any(RuntimeViewsheet.class),
                                                         argument.capture(),
                                                         any(String.class),
                                                         any(String.class),
                                                         any(String.class),
                                                         nullable(Principal.class),
                                                         any(CommandDispatcher.class));

      SelectionTreeVSAssemblyInfo result = argument.getValue();
      // dropdown stays dropdown, title height changed to 30
      // size.height = titleHeight = 30
      // position should be: tabTop(420) - 30 = 390
      assertEquals(390, result.getPixelOffset().y);
   }

   @Mock VSOutputService vsOutputService;
   @Mock CommandDispatcher commandDispatcher;
   @Mock RuntimeViewsheet rvs;
   @Mock ViewsheetService engine;
   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock VSTrapService trapService;
   @Mock SelectionTreeVSAssembly selectionTreeAssembly;
   @Mock Dimension size;
   @Mock VSDialogService dialogService;
   @Mock VSSelectionService vsSelectionService;
   @Mock SelectionDialogService selectionDialogService;
   @Mock VSAssemblyInfoHandler assemblyInfoHandler;
   @Mock DataRefModelFactoryService dataRefService;
   @Mock DataSourceRegistry dataSourceRegistry;
   @Mock (answer = Answers.RETURNS_DEEP_STUBS)
   private Viewsheet viewsheet;
   @Mock (answer = Answers.RETURNS_DEEP_STUBS)
   private SelectionTreePropertyDialogModel selectionTreePropertyDialogModel;
   private SelectionTreePropertyDialogService service;
}
