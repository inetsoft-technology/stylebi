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
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.composition.region.ChartArea;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.vs.objects.command.SetCurrentFormatCommand;
import inetsoft.web.composer.vs.objects.event.GetVSObjectFormatEvent;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.viewsheet.command.ViewsheetCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.PlaceholderService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.*;
import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SreeHome()
@ExtendWith(MockitoExtension.class)
class FormatPainterControllerTest {

   @BeforeEach
   void setup() throws Exception {
      controller = spy(new FormatPainterController(runtimeViewsheetRef, placeholderService,
                                                   chartRegionHandler, viewsheetEngine,
                                                   objectModelService, bindingService,
                                                   vsLayoutService));

      when(runtimeViewsheetRef.getRuntimeId()).thenReturn("Viewsheet1");
      when(viewsheetEngine.getViewsheet(anyString(), nullable(Principal.class)))
         .thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getViewsheetSandbox()).thenReturn(viewsheetSandbox);
      doNothing().when(graphPair).waitInit();
      when(viewsheetSandbox.getVGraphPair(anyString(), anyBoolean(), nullable(Dimension.class)))
         .thenReturn(graphPair);

      chart = new ChartVSAssembly(viewsheet, "Chart1");
      chart.setVSChartInfo(new VSChartInfo());
      chart.setXCube(new VSCube());

      ChartDescriptor chartDescriptor = new ChartDescriptor();
      chart.setChartDescriptor(chartDescriptor);

      when(viewsheet.getAssembly("Chart1")).thenReturn(chart);
   }

   @Test
   void getChartFormatWorks() throws Exception {
      TitlesDescriptor titlesDescriptor = new TitlesDescriptor();
      TitleDescriptor titleDescriptor = spy(new TitleDescriptor());
      titleDescriptor.setTextFormat(null);
      titlesDescriptor.setXTitleDescriptor(titleDescriptor);
      chart.getChartDescriptor().setTitlesDescriptor(titlesDescriptor);

      GetVSObjectFormatEvent event = new GetVSObjectFormatEvent();
      event.setName("Chart1");
      event.setRegion("x_title");

      controller.getFormat(event, null, dispatcher);

      assertNotNull(titlesDescriptor.getXTitleDescriptor().getTextFormat());
      verify(titleDescriptor, atLeast(2)).setTextFormat(any());
   }

   @Test
   @Disabled
   void legendAlignIsEnabled() throws Exception {
      LegendDescriptor legendDescriptor = new LegendDescriptor();

      doReturn(legendDescriptor).when(controller)
         .getLegendDescriptor(nullable(ChartDescriptor.class), nullable(ChartInfo.class),
                              nullable(ChartArea.class), anyInt());

      GetVSObjectFormatEvent event = new GetVSObjectFormatEvent();
      event.setName("Chart1");
      event.setRegion("legend_content");
      event.setDimensionColumn(true);
      event.setIndex(0);

      controller.getFormat(event, null, dispatcher);

      verify(dispatcher).sendCommand(any(ViewsheetCommand.class));
      verify(dispatcher).sendCommand(argCaptor.capture());

      List<SetCurrentFormatCommand> commands = argCaptor.getAllValues();
      assertTrue(commands.get(0).getModel().isHAlignmentEnabled());
      assertFalse(commands.get(0).getModel().isVAlignmentEnabled());
   }

   // Bug #16423 when selecting axis label, ensure correct alignment options are enabled.
   @Test
   void yAxisAlignEnabled() throws Exception {
      AxisDescriptor axisDescriptor = new AxisDescriptor();

      doReturn(axisDescriptor).when(chartRegionHandler)
         .getAxisDescriptor(nullable(VSChartInfo.class), nullable(ChartArea.class),
                            nullable(String.class), anyInt(), nullable(String.class));

      GetVSObjectFormatEvent event = new GetVSObjectFormatEvent();
      event.setName("Chart1");
      event.setRegion("left_y_axis");
      event.setDimensionColumn(true);
      event.setIndex(0);
      event.setColumnName("");

      controller.getFormat(event, null, dispatcher);

      event.setDimensionColumn(false);

      controller.getFormat(event, null, dispatcher);

      verify(dispatcher, times(2)).sendCommand(any(ViewsheetCommand.class));
      verify(dispatcher, times(2)).sendCommand(argCaptor.capture());

      List<SetCurrentFormatCommand> commands = argCaptor.getAllValues();
      assertTrue(commands.get(0).getModel().isHAlignmentEnabled());
      assertFalse(commands.get(0).getModel().isVAlignmentEnabled());
      assertFalse(commands.get(1).getModel().isHAlignmentEnabled());
      assertFalse(commands.get(1).getModel().isVAlignmentEnabled());
   }

   @Captor ArgumentCaptor<SetCurrentFormatCommand> argCaptor;
   @Mock ViewsheetEngine viewsheetEngine;
   @Mock PlaceholderService placeholderService;
   @Mock ChartRegionHandler chartRegionHandler;
   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock VSObjectModelFactoryService objectModelService;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock ViewsheetSandbox viewsheetSandbox;
   @Mock CommandDispatcher dispatcher;
   @Mock VSBindingService bindingService;
   @Mock VSLayoutService vsLayoutService;
   @Mock VGraphPair graphPair;
   private ChartVSAssembly chart;

   private FormatPainterController controller;
}
