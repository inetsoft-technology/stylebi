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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.SreeHome;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.vs.SelectionListPropertyDialogModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.*;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SreeHome()
@ExtendWith(MockitoExtension.class)
class SelectionListPropertyDialogServiceTest {
   @BeforeEach
   void setup() {
      service = new SelectionListPropertyDialogService(
         vsObjectPropertyService, vsOutputService, engine, trapService, dialogService,
         selectionDialogService, assemblyInfoHandler, dataRefService, dataSourceRegistry);
   }

   @Test
   void bottomTabsPositionAdjustedOnShowTypeChange() throws Exception {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setShowTypeValue(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      info.setPixelOffset(new Point(50, 400));
      info.setPixelSize(new Dimension(200, 20));

      TabVSAssemblyInfo tabInfo = new TabVSAssemblyInfo();
      tabInfo.setBottomTabsValue(true);
      tabInfo.setPixelOffset(new Point(0, 420));

      TabVSAssembly tabAssembly = Mockito.mock(TabVSAssembly.class);
      when(tabAssembly.getVSAssemblyInfo()).thenReturn(tabInfo);
      when(selectionListAssembly.getContainer()).thenReturn(tabAssembly);
      when(selectionListAssembly.getVSAssemblyInfo()).thenReturn(info);

      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(selectionListAssembly);
      when(viewsheet.getPixelSize(any()))
         .thenReturn(new Dimension(200, 20));

      given(selectionListPropertyDialogModel.getSelectionGeneralPaneModel()
               .getGeneralPropPaneModel().getBasicGeneralPaneModel().getName())
         .willReturn("SelectionList1");
      given(selectionListPropertyDialogModel.getSelectionGeneralPaneModel()
               .getShowType())
         .willReturn(SelectionVSAssemblyInfo.LIST_SHOW_TYPE);
      given(selectionListPropertyDialogModel.getSelectionGeneralPaneModel()
               .getListHeight())
         .willReturn(6);
      given(selectionListPropertyDialogModel.getSelectionGeneralPaneModel()
               .getSizePositionPaneModel().getTitleHeight())
         .willReturn(20);
      given(selectionListPropertyDialogModel.getSelectionGeneralPaneModel()
               .getSizePositionPaneModel().getCellHeight())
         .willReturn(20);

      service.setSelectionListPropertyModel("Viewsheet1", "SelectionList1",
                                            selectionListPropertyDialogModel,
                                            "", null, commandDispatcher);

      ArgumentCaptor<SelectionListVSAssemblyInfo> argument =
         ArgumentCaptor.forClass(SelectionListVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(any(RuntimeViewsheet.class),
                                                         argument.capture(),
                                                         any(String.class),
                                                         any(String.class),
                                                         any(String.class),
                                                         nullable(Principal.class),
                                                         any(CommandDispatcher.class));

      SelectionListVSAssemblyInfo result = argument.getValue();
      // switching from dropdown to list: titleHeight(20) + listHeight(6) * cellHeight(20) = 140
      // position should be: tabTop(420) - 140 = 280
      assertEquals(280, result.getPixelOffset().y);
   }

   @Test
   void bottomTabsPositionAdjustedOnListToDropdown() throws Exception {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setShowTypeValue(SelectionVSAssemblyInfo.LIST_SHOW_TYPE);
      info.setPixelOffset(new Point(50, 280));
      info.setPixelSize(new Dimension(200, 140));

      TabVSAssemblyInfo tabInfo = new TabVSAssemblyInfo();
      tabInfo.setBottomTabsValue(true);
      tabInfo.setPixelOffset(new Point(0, 420));

      TabVSAssembly tabAssembly = Mockito.mock(TabVSAssembly.class);
      when(tabAssembly.getVSAssemblyInfo()).thenReturn(tabInfo);
      when(selectionListAssembly.getContainer()).thenReturn(tabAssembly);
      when(selectionListAssembly.getVSAssemblyInfo()).thenReturn(info);

      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(selectionListAssembly);
      when(viewsheet.getPixelSize(any()))
         .thenReturn(new Dimension(200, 140));

      given(selectionListPropertyDialogModel.getSelectionGeneralPaneModel()
               .getGeneralPropPaneModel().getBasicGeneralPaneModel().getName())
         .willReturn("SelectionList1");
      given(selectionListPropertyDialogModel.getSelectionGeneralPaneModel()
               .getShowType())
         .willReturn(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      given(selectionListPropertyDialogModel.getSelectionGeneralPaneModel()
               .getSizePositionPaneModel().getTitleHeight())
         .willReturn(20);
      given(selectionListPropertyDialogModel.getSelectionGeneralPaneModel()
               .getSizePositionPaneModel().getCellHeight())
         .willReturn(20);

      service.setSelectionListPropertyModel("Viewsheet1", "SelectionList1",
                                            selectionListPropertyDialogModel,
                                            "", null, commandDispatcher);

      ArgumentCaptor<SelectionListVSAssemblyInfo> argument =
         ArgumentCaptor.forClass(SelectionListVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(any(RuntimeViewsheet.class),
                                                         argument.capture(),
                                                         any(String.class),
                                                         any(String.class),
                                                         any(String.class),
                                                         nullable(Principal.class),
                                                         any(CommandDispatcher.class));

      SelectionListVSAssemblyInfo result = argument.getValue();
      // switching from list to dropdown: size.height = titleHeight = 20
      // position should be: tabTop(420) - 20 = 400
      assertEquals(400, result.getPixelOffset().y);
   }

   @Test
   void bottomTabsPositionAdjustedOnHeightChange() throws Exception {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setShowTypeValue(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      info.setTitleHeightValue(20);
      info.setPixelOffset(new Point(50, 400));
      info.setPixelSize(new Dimension(200, 20));

      TabVSAssemblyInfo tabInfo = new TabVSAssemblyInfo();
      tabInfo.setBottomTabsValue(true);
      tabInfo.setPixelOffset(new Point(0, 420));

      TabVSAssembly tabAssembly = Mockito.mock(TabVSAssembly.class);
      when(tabAssembly.getVSAssemblyInfo()).thenReturn(tabInfo);
      when(selectionListAssembly.getContainer()).thenReturn(tabAssembly);
      when(selectionListAssembly.getVSAssemblyInfo()).thenReturn(info);

      when(runtimeViewsheetRef.getRuntimeId()).thenReturn("Viewsheet1");
      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(selectionListAssembly);
      when(viewsheet.getPixelSize(any()))
         .thenReturn(new Dimension(200, 20));

      // keep show type as dropdown, but change title height to 30
      given(selectionListPropertyDialogModel.getSelectionGeneralPaneModel()
               .getGeneralPropPaneModel().getBasicGeneralPaneModel().getName())
         .willReturn("SelectionList1");
      given(selectionListPropertyDialogModel.getSelectionGeneralPaneModel()
               .getShowType())
         .willReturn(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      given(selectionListPropertyDialogModel.getSelectionGeneralPaneModel()
               .getSizePositionPaneModel().getTitleHeight())
         .willReturn(30);
      given(selectionListPropertyDialogModel.getSelectionGeneralPaneModel()
               .getSizePositionPaneModel().getCellHeight())
         .willReturn(20);

      controller.setSelectionListPropertyModel("SelectionList1",
                                              selectionListPropertyDialogModel,
                                              "", null, commandDispatcher);

      ArgumentCaptor<SelectionListVSAssemblyInfo> argument =
         ArgumentCaptor.forClass(SelectionListVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(any(RuntimeViewsheet.class),
                                                         argument.capture(),
                                                         any(String.class),
                                                         any(String.class),
                                                         any(String.class),
                                                         nullable(Principal.class),
                                                         any(CommandDispatcher.class));

      SelectionListVSAssemblyInfo result = argument.getValue();
      // dropdown stays dropdown, title height changed to 30
      // size.height = titleHeight = 30
      // position should be: tabTop(420) - 30 = 390
      assertEquals(390, result.getPixelOffset().y);
   }

   @Mock VSOutputService vsOutputService;
   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock CommandDispatcher commandDispatcher;
   @Mock RuntimeViewsheet rvs;
   @Mock ViewsheetService engine;
   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock VSTrapService trapService;
   @Mock SelectionListVSAssembly selectionListAssembly;
   @Mock VSDialogService dialogService;
   @Mock SelectionDialogService selectionDialogService;
   @Mock VSAssemblyInfoHandler assemblyInfoHandler;
   @Mock DataRefModelFactoryService dataRefService;
   @Mock DataSourceRegistry dataSourceRegistry;
   @Mock(answer = Answers.RETURNS_DEEP_STUBS)
   private Viewsheet viewsheet;
   @Mock(answer = Answers.RETURNS_DEEP_STUBS)
   private SelectionListPropertyDialogModel selectionListPropertyDialogModel;

   private SelectionListPropertyDialogService service;
}
