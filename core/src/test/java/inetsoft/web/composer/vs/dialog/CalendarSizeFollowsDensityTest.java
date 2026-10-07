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
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.CalendarPropertyDialogModel;
import inetsoft.web.composer.model.vs.SizePositionPaneModel;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * The size checkbox is offered for a marked single calendar only, and the dialog's save applies it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@ExtendWith(MockitoExtension.class)
@Tag("core")
class CalendarSizeFollowsDensityTest {
   @BeforeEach
   void setup() throws Exception {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      service = new CalendarPropertyDialogService(
         vsObjectPropertyService, vsOutputService, dialogService, engine, trapService,
         assemblyInfoHandler, mock(QueryManagerService.class));
      lenient().when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      lenient().when(rvs.getViewsheet()).thenReturn(viewsheet);
      lenient().when(viewsheet.getAssembly(anyString())).thenReturn(calendarAssembly);
      lenient().when(dialogService.getAssemblyPosition(any(), any())).thenReturn(new Point(0, 0));
      lenient().when(dialogService.getAssemblySize(any(), any()))
         .thenAnswer(inv -> ((VSAssemblyInfo) inv.getArgument(0)).getPixelSize());
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedSingleCalendarIsOfferedTicked() throws Exception {
      assertEquals(Boolean.TRUE, read(calendar(VizMark.MODERN_LIGHT, new Dimension(300, 332))));
   }

   @Test
   void aMarkedCalendarAtAnAuthorSizeIsOfferedUnticked() throws Exception {
      assertEquals(Boolean.FALSE, read(calendar(VizMark.MODERN_LIGHT, new Dimension(400, 300))));
   }

   @Test
   void noCheckboxForAnUnmarkedCalendarADropdownOrADoubleCalendar() throws Exception {
      assertNull(read(calendar(null, new Dimension(300, 300))));

      CalendarVSAssemblyInfo dropdown = calendar(VizMark.MODERN_LIGHT, new Dimension(300, 332));
      dropdown.setShowTypeValue(CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      assertNull(read(dropdown));

      CalendarVSAssemblyInfo twoMonths = calendar(VizMark.MODERN_LIGHT, new Dimension(600, 332));
      twoMonths.setViewModeValue(CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE);
      assertNull(read(twoMonths));
   }

   @Test
   void tickingWritesTheTierSizeAndClearsTheFlag() throws Exception {
      CalendarVSAssemblyInfo info = calendar(VizMark.MODERN_LIGHT, new Dimension(400, 500));
      info.setUserSize(true);

      CalendarVSAssemblyInfo result = save(info, Boolean.TRUE, new Dimension(400, 500));

      assertEquals(new Dimension(300, 332), result.getPixelSize());
      assertFalse(result.isUserSize());
   }

   @Test
   void untickingFlagsTheSize() throws Exception {
      CalendarVSAssemblyInfo result =
         save(calendar(VizMark.MODERN_LIGHT, new Dimension(300, 332)), Boolean.FALSE,
              new Dimension(300, 332));

      assertEquals(new Dimension(300, 332), result.getPixelSize());
      assertTrue(result.isUserSize());
   }

   @Test
   void aTypedSizeFlagsTheSize() throws Exception {
      CalendarVSAssemblyInfo result =
         save(calendar(VizMark.MODERN_LIGHT, new Dimension(300, 332)), null,
              new Dimension(300, 250));

      assertEquals(new Dimension(300, 250), result.getPixelSize());
      assertTrue(result.isUserSize());
   }

   // OK for an unrelated change leaves the box following
   @Test
   void anUnchangedSizeKeepsFollowing() throws Exception {
      CalendarVSAssemblyInfo result =
         save(calendar(VizMark.MODERN_LIGHT, new Dimension(300, 332)), null,
              new Dimension(300, 332));

      assertFalse(result.isUserSize());
      assertTrue(result.followsDensitySize());
   }

   @Test
   void tickingInBottomTabsKeepsTheBottomOnTheTabStrip() throws Exception {
      TabVSAssemblyInfo tabInfo = new TabVSAssemblyInfo();
      tabInfo.setBottomTabsValue(true);
      tabInfo.setPixelOffset(new Point(0, 420));
      TabVSAssembly tab = mock(TabVSAssembly.class);
      when(tab.getVSAssemblyInfo()).thenReturn(tabInfo);
      when(calendarAssembly.getContainer()).thenReturn(tab);

      CalendarVSAssemblyInfo info = calendar(VizMark.MODERN_LIGHT, new Dimension(300, 400));
      info.setPixelOffset(new Point(50, 20));
      info.setUserSize(true);

      CalendarVSAssemblyInfo result = save(info, Boolean.TRUE, new Dimension(300, 400));

      assertEquals(new Dimension(300, 332), result.getPixelSize());
      assertEquals(new Point(50, 420 - 332), result.getPixelOffset());
   }

   // the dialog doubles the width on a switch to double; the checkbox must not undo that
   @Test
   void aTickedSingleSwitchedToDoubleKeepsTheDoubledWidth() throws Exception {
      CalendarVSAssemblyInfo result =
         save(calendar(VizMark.MODERN_LIGHT, new Dimension(300, 332)), Boolean.TRUE,
              new Dimension(600, 332), CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE,
              CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE);

      assertEquals(new Dimension(600, 332), result.getPixelSize());
      assertFalse(result.isUserSize());
   }

   // the dialog's own resize on a mode switch is not the author's size
   @Test
   void aDoubleSwitchedBackToSingleFollowsAgain() throws Exception {
      CalendarVSAssemblyInfo info = calendar(VizMark.MODERN_LIGHT, new Dimension(600, 332));
      info.setViewModeValue(CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE);

      CalendarVSAssemblyInfo result =
         save(info, null, new Dimension(300, 332), CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE,
              CalendarVSAssemblyInfo.SINGLE_CALENDAR_MODE);

      assertEquals(new Dimension(300, 332), result.getPixelSize());
      assertFalse(result.isUserSize());
      assertTrue(result.followsDensitySize());
   }

   private static CalendarVSAssemblyInfo calendar(VizMark mark, Dimension size) {
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();
      info.setVizMark(mark);
      info.setTitleHeightValue(36);
      info.setPixelOffset(new Point(0, 0));
      info.setPixelSize(size);
      return info;
   }

   private Boolean read(CalendarVSAssemblyInfo info) throws Exception {
      lenient().when(calendarAssembly.getVSAssemblyInfo()).thenReturn(info);
      CalendarPropertyDialogModel result =
         service.getCalendarPropertyModel("Viewsheet1", "Calendar1", null);
      return result.getCalendarGeneralPaneModel().getSizePositionPaneModel()
         .getSizeFollowsDensity();
   }

   /**
    * Save through the dialog. The size pane is a real model: the checkbox writes the tier size into
    * it, and a deep stub would drop that write.
    */
   private CalendarVSAssemblyInfo save(CalendarVSAssemblyInfo info, Boolean follows,
                                       Dimension typed) throws Exception
   {
      return save(info, follows, typed, info.getShowTypeValue(), info.getViewModeValue());
   }

   private CalendarVSAssemblyInfo save(CalendarVSAssemblyInfo info, Boolean follows,
                                       Dimension typed, int showType, int viewMode)
      throws Exception
   {
      when(calendarAssembly.getVSAssemblyInfo()).thenReturn(info);
      doCallRealMethod().when(dialogService)
         .setAssemblySize(any(), any(SizePositionPaneModel.class));
      doCallRealMethod().when(dialogService).setAssemblySize(any(), anyInt(), anyInt());

      SizePositionPaneModel size = new SizePositionPaneModel();
      size.setSizeFollowsDensity(follows);
      size.setWidth(typed.width);
      size.setHeight(typed.height);
      size.setTitleHeight(info.getTitleHeightValue());
      when(model.getCalendarGeneralPaneModel().getSizePositionPaneModel()).thenReturn(size);
      given(model.getCalendarGeneralPaneModel().getGeneralPropPaneModel()
               .getBasicGeneralPaneModel().getName())
         .willReturn("Calendar1");
      given(model.getCalendarAdvancedPaneModel().getShowType()).willReturn(showType);
      given(model.getCalendarAdvancedPaneModel().getViewMode()).willReturn(viewMode);

      service.setCalendarPropertyModel("Viewsheet1", "Calendar1", model, "", null,
                                       commandDispatcher);

      ArgumentCaptor<CalendarVSAssemblyInfo> captor =
         ArgumentCaptor.forClass(CalendarVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), any(String.class), any(String.class),
         any(String.class), nullable(Principal.class), any(CommandDispatcher.class));
      return captor.getValue();
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
   private CalendarPropertyDialogModel model;
   private CalendarPropertyDialogService service;
}
