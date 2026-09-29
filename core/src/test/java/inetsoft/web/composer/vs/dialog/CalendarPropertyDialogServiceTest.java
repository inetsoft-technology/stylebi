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
import inetsoft.web.composer.model.vs.DynamicValueModel;
import inetsoft.web.composer.model.vs.VSAssemblyScriptPaneModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.*;
import java.security.Principal;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith(MockitoExtension.class)
@Tag("core")
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
      // keep show type as dropdown, but change title height from 20 to 30
      CalendarVSAssemblyInfo result =
         save(CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE, true,
              CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE, 30);
      // position should be: tabTop(420) - 30 = 390
      assertEquals(390, result.getPixelOffset().y);
   }

   @Test
   void bottomTabsPositionAdjustedOnShowTypeChange() throws Exception {
      // change show type from calendar to dropdown, title height unchanged
      CalendarVSAssemblyInfo result =
         save(CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, true,
              CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE, 20);
      // position should be: tabTop(420) - 20 = 400 (initial y is 300)
      assertEquals(400, result.getPixelOffset().y);
   }

   @Test
   void bottomTabsPositionAdjustedOnSwitchToCalendar() throws Exception {
      // change show type from dropdown to calendar, fixCalendarSize() resizes the calendar
      CalendarVSAssemblyInfo result =
         save(CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE, true,
              CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, 20);
      assertEquals(CalendarVSAssemblyInfo.DEFAULT_CALENDAR_HEIGHT,
                   result.getPixelSize().height);
      // position should be: tabTop(420) - calendar height
      assertEquals(420 - CalendarVSAssemblyInfo.DEFAULT_CALENDAR_HEIGHT,
                   result.getPixelOffset().y);
      assertEquals(CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, result.getShowType());
   }

   @Test
   void positionUnchangedWhenTabsNotAtBottom() throws Exception {
      CalendarVSAssemblyInfo result =
         save(CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE, false,
              CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE, 30);
      assertEquals(300, result.getPixelOffset().y);
   }

   private CalendarVSAssemblyInfo save(int oldShowType, boolean bottomTabs, int newShowType,
                                       int newTitleHeight) throws Exception
   {
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();
      info.setShowTypeValue(oldShowType);
      info.setTitleHeightValue(20);
      info.setPixelOffset(new Point(50, 300));
      info.setPixelSize(new Dimension(200, 20));

      TabVSAssemblyInfo tabInfo = new TabVSAssemblyInfo();
      tabInfo.setBottomTabsValue(bottomTabs);
      tabInfo.setPixelOffset(new Point(0, 420));

      TabVSAssembly tabAssembly = Mockito.mock(TabVSAssembly.class);
      when(tabAssembly.getVSAssemblyInfo()).thenReturn(tabInfo);
      when(calendarAssembly.getContainer()).thenReturn(tabAssembly);
      when(calendarAssembly.getVSAssemblyInfo()).thenReturn(info);

      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(calendarAssembly);

      given(calendarPropertyDialogModel.getCalendarGeneralPaneModel()
               .getGeneralPropPaneModel().getBasicGeneralPaneModel().getName())
         .willReturn("Calendar1");
      given(calendarPropertyDialogModel.getCalendarGeneralPaneModel()
               .getSizePositionPaneModel().getTitleHeight())
         .willReturn(newTitleHeight);
      given(calendarPropertyDialogModel.getCalendarAdvancedPaneModel()
               .getShowType())
         .willReturn(newShowType);

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
                                                         any(CommandDispatcher.class),
                                                         eq(true),
                                                         nullable(Integer.class));
      return argument.getValue();
   }

   // ── min/max ordering validation (parity audit L7) ──────────────────────────

   @Test
   void refusesAMinThatIsNotBeforeMax() throws Exception {
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();

      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(calendarAssembly);
      when(calendarAssembly.getVSAssemblyInfo()).thenReturn(info);

      CalendarPropertyDialogModel model = minMaxModel("2024-01-10", "2024-01-01");

      Exception thrown = org.junit.jupiter.api.Assertions.assertThrows(
         IllegalArgumentException.class,
         () -> service.setCalendarPropertyModel(
            "Viewsheet1", "Calendar1", model, "", null, commandDispatcher));

      org.junit.jupiter.api.Assertions.assertTrue(thrown.getMessage().contains("min"));
      org.junit.jupiter.api.Assertions.assertTrue(thrown.getMessage().contains("max"));
      org.mockito.Mockito.verifyNoInteractions(vsObjectPropertyService);
   }

   @Test
   void acceptsAMinThatIsBeforeMax() throws Exception {
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();

      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(calendarAssembly);
      when(calendarAssembly.getVSAssemblyInfo()).thenReturn(info);

      CalendarPropertyDialogModel model = minMaxModel("2024-01-01", "2024-01-10");

      service.setCalendarPropertyModel(
         "Viewsheet1", "Calendar1", model, "", null, commandDispatcher);

      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), any(CalendarVSAssemblyInfo.class), any(String.class),
         any(String.class), any(String.class), nullable(Principal.class),
         any(CommandDispatcher.class), eq(true), nullable(Integer.class));
   }

   /**
    * Builds a model like the one the dialog sends: the script pane has no lazy default in
    * the model and the general pane carries the assembly name.
    */
   private CalendarPropertyDialogModel minMaxModel(String min, String max) {
      CalendarPropertyDialogModel model = new CalendarPropertyDialogModel();
      model.setVsAssemblyScriptPaneModel(
         VSAssemblyScriptPaneModel.builder().scriptEnabled(false).expression("").build());
      model.getCalendarGeneralPaneModel().getGeneralPropPaneModel()
         .getBasicGeneralPaneModel().setName("Calendar1");
      model.getCalendarAdvancedPaneModel().setMin(new DynamicValueModel(min));
      model.getCalendarAdvancedPaneModel().setMax(new DynamicValueModel(max));
      return model;
   }

   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock VSOutputService vsOutputService;
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
