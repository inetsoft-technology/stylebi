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
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.CalendarPropertyDialogModel;
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
class CalendarPropertyDialogServiceTest {
   @BeforeEach
   void setup() {
      service = new CalendarPropertyDialogService(
         vsObjectPropertyService,
         vsOutputService,
         dialogService,
         engine,
         trapService,
         assemblyInfoHandler);
   }

   @Test
   void bottomTabsPositionAdjustedOnTitleHeightChange() throws Exception {
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();
      info.setShowTypeValue(CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      info.setTitleHeightValue(20);
      info.setPixelOffset(new Point(50, 400));
      info.setPixelSize(new Dimension(200, 20));

      TabVSAssemblyInfo tabInfo = new TabVSAssemblyInfo();
      tabInfo.setBottomTabsValue(true);
      tabInfo.setPixelOffset(new Point(0, 420));

      TabVSAssembly tabAssembly = Mockito.mock(TabVSAssembly.class);
      when(tabAssembly.getVSAssemblyInfo()).thenReturn(tabInfo);
      when(calendarAssembly.getContainer()).thenReturn(tabAssembly);
      when(calendarAssembly.getVSAssemblyInfo()).thenReturn(info);

      when(runtimeViewsheetRef.getRuntimeId()).thenReturn("Viewsheet1");
      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(calendarAssembly);

      // keep show type as dropdown, but change title height to 30
      given(calendarPropertyDialogModel.getCalendarGeneralPaneModel()
               .getGeneralPropPaneModel().getBasicGeneralPaneModel().getName())
         .willReturn("Calendar1");
      given(calendarPropertyDialogModel.getCalendarGeneralPaneModel()
               .getSizePositionPaneModel().getTitleHeight())
         .willReturn(30);
      given(calendarPropertyDialogModel.getCalendarAdvancedPaneModel()
               .getShowType())
         .willReturn(CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE);

      service.setCalendarPropertyModel("Viewsheet1", "Calendar1",
                                       calendarPropertyDialogModel,
                                       "", null, commandDispatcher);

      ArgumentCaptor<CalendarVSAssemblyInfo> argument =
         ArgumentCaptor.forClass(CalendarVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(any(RuntimeViewsheet.class),
                                                         argument.capture(),
                                                         any(String.class),
                                                         any(String.class),
                                                         any(String.class),
                                                         nullable(Principal.class),
                                                         any(CommandDispatcher.class));

      CalendarVSAssemblyInfo result = argument.getValue();
      // dropdown stays dropdown, title height changed to 30
      // position should be: tabTop(420) - 30 = 390
      assertEquals(390, result.getPixelOffset().y);
   }

   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock VSOutputService vsOutputService;
   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock CommandDispatcher commandDispatcher;
   @Mock RuntimeViewsheet rvs;
   @Mock ViewsheetService engine;
   @Mock VSTrapService trapService;
   @Mock VSDialogService dialogService;
   @Mock VSAssemblyInfoHandler assemblyInfoHandler;
   @Mock CalendarVSAssembly calendarAssembly;
   @Mock(answer = Answers.RETURNS_DEEP_STUBS)
   private Viewsheet viewsheet;
   @Mock(answer = Answers.RETURNS_DEEP_STUBS)
   private CalendarPropertyDialogModel calendarPropertyDialogModel;

   private CalendarPropertyDialogService service;
}
