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
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.CalendarPropertyDialogModel;
import inetsoft.web.composer.model.vs.SizePositionPaneModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.Principal;
import java.util.ArrayList;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
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
         assemblyInfoHandler,
         mock(QueryManagerService.class));
      lenient().when(dialogService.getAssemblySize(any(), any()))
         .thenAnswer(inv -> ((VSAssemblyInfo) inv.getArgument(0)).getPixelSize());
   }

   // asserts a bottom-tabs reposition on a title-height-only change, but
   // CalendarPropertyDialogService repositions only inside if(oldType != type). The guard and this
   // test landed together in 1b6ae84fb and the class never ran, lacking @Tag("core"), so this has
   // never passed. Pre-existing and unrelated to the seeded chrome migration
   @Test
   @Disabled("Never passed: CalendarPropertyDialogService repositions bottom tabs only on a " +
             "show-type change, not on a title-height-only change. Pre-existing since 1b6ae84fb, " +
             "hidden until the class was tagged; unrelated to the seeded chrome migration.")
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

   @Test
   void bottomTabsCalendarHeightKeptWhenNothingChanged() throws Exception {
      // a calendar resized to 300 by dragging, then OK without changes
      CalendarVSAssemblyInfo result =
         save(CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, true,
              CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, 20,
              new Dimension(300, 300), new Dimension(300, 300));
      assertEquals(new Dimension(300, 300), result.getPixelSize());
      // bottom stays on the tab strip: tabTop(420) - 300 = 120
      assertEquals(120, result.getPixelOffset().y);
   }

   @Test
   void bottomTabsCalendarKeepsUserTypedHeight() throws Exception {
      // user types height 250 in the size/position pane
      CalendarVSAssemblyInfo result =
         save(CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, true,
              CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, 20,
              new Dimension(300, 300), new Dimension(300, 250));
      assertEquals(new Dimension(300, 250), result.getPixelSize());
      // position should be: tabTop(420) - 250 = 170
      assertEquals(170, result.getPixelOffset().y);
   }

   @Test
   void bottomTabsCalendarHeightKeptOnTitleHeightChange() throws Exception {
      // title height 20 -> 60 does not change the outer calendar height
      CalendarVSAssemblyInfo result =
         save(CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, true,
              CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, 60,
              new Dimension(300, 300), new Dimension(300, 300));
      assertEquals(300, result.getPixelSize().height);
      assertEquals(120, result.getPixelOffset().y);
   }

   @Test
   void resizeAfterSwitchToCalendarNotResetOnWriteXML() throws Exception {
      CalendarVSAssemblyInfo result =
         save(CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE, true,
              CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, 20,
              new Dimension(300, 18), new Dimension(300, 18));
      assertEquals(new Dimension(300, CalendarVSAssemblyInfo.DEFAULT_CALENDAR_HEIGHT),
                   result.getPixelSize());

      // apply the dialog result to the assembly, then resize the calendar by dragging
      CalendarVSAssemblyInfo live = new CalendarVSAssemblyInfo();
      live.setShowTypeValue(CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      live.setPixelOffset(new Point(50, 300));
      live.setPixelSize(new Dimension(300, 18));
      // the mocked data pane leaves the additional table list unset
      result.setAdditionalTableNames(new ArrayList<>());
      live.copyInfo(result);
      live.setPixelSize(new Dimension(300, 280));

      // saving the viewsheet must not reset the resized height
      live.writeXML(new PrintWriter(new StringWriter()));
      assertEquals(280, live.getPixelSize().height);
   }

   @Test
   void reporterCalendarTab1GeometryUnchangedOnOk() throws Exception {
      // calendartab1 (Bug #77371): Calendar1 300x300 at (98,242), title 36, bottom tabs at
      // y=542. OK without changes, then OK again with title height 60.
      for(int titleHeight : new int[] { 36, 60 }) {
         CalendarVSAssemblyInfo result =
            save(CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, 36, new Point(98, 242), 542,
                 CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, titleHeight,
                 new Dimension(300, 300), new Dimension(300, 300));
         assertEquals(new Dimension(300, 300), result.getPixelSize(), "title " + titleHeight);
         assertEquals(new Point(98, 242), result.getPixelOffset(), "title " + titleHeight);
         Mockito.clearInvocations(vsObjectPropertyService);
      }
   }

   @Test
   void resizeAfterDropdownToCalendarKeptOnWriteXMLAndOk() throws Exception {
      // Bug #77373: Dropdown -> Calendar with title height 60 in bottom tabs (y=402)
      CalendarVSAssemblyInfo live = switchDropdownToCalendar();
      assertEquals(new Dimension(210, CalendarVSAssemblyInfo.DEFAULT_CALENDAR_HEIGHT),
                   live.getPixelSize());
      assertEquals(new Point(50, 240), live.getPixelOffset());

      // drag the top-left handle: Top 240 -> 160, height 162 -> 242, width 210 -> 260
      live.setPixelOffset(new Point(50, 160));
      live.setPixelSize(new Dimension(260, 242));

      // cache write-back and save serialize the live info
      live.writeXML(new PrintWriter(new StringWriter()));
      live.writeXML(new PrintWriter(new StringWriter()));
      assertEquals(new Dimension(260, 242), live.getPixelSize());
      assertEquals(new Point(50, 160), live.getPixelOffset());

      // OK in the dialog without changes keeps the resized size (Bug #77371)
      apply(live, CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, 60, new Dimension(260, 242));
      live.writeXML(new PrintWriter(new StringWriter()));
      assertEquals(new Dimension(260, 242), live.getPixelSize());
      assertEquals(new Point(50, 160), live.getPixelOffset());
   }

   @Test
   void dropdownResizeAfterCalendarToDropdownKeptOnWriteXML() throws Exception {
      // Bug #77373: Dropdown -> Calendar, resize, then Calendar -> Dropdown
      CalendarVSAssemblyInfo live = switchDropdownToCalendar();
      live.setPixelOffset(new Point(50, 160));
      live.setPixelSize(new Dimension(260, 242));
      live.writeXML(new PrintWriter(new StringWriter()));

      apply(live, CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE, 60, new Dimension(260, 18));
      assertEquals(new Point(50, 402 - 60), live.getPixelOffset());

      // resize the dropdown, then serialize: must not revert to width x 18
      live.setPixelSize(new Dimension(300, 60));
      live.writeXML(new PrintWriter(new StringWriter()));
      assertEquals(new Dimension(300, 60), live.getPixelSize());
   }

   @Test
   void resizeOfNeverSwitchedCalendarKeptOnWriteXML() throws Exception {
      // control: a calendar that never went through Dropdown -> Calendar
      CalendarVSAssemblyInfo live = liveCalendar(CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE,
                                                 new Point(50, 240), new Dimension(210, 162));
      apply(live, CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, 60, null);
      assertEquals(new Point(50, 240), live.getPixelOffset());

      live.setPixelOffset(new Point(50, 160));
      live.setPixelSize(new Dimension(260, 242));
      live.writeXML(new PrintWriter(new StringWriter()));
      assertEquals(new Dimension(260, 242), live.getPixelSize());
      assertEquals(new Point(50, 160), live.getPixelOffset());
   }

   /**
    * The reporter's steps on one live info: a Calendar in bottom tabs at y=402 is switched to
    * Dropdown, its title height is changed 36 -> 60, then it is switched back to Calendar.
    */
   private CalendarVSAssemblyInfo switchDropdownToCalendar() throws Exception {
      CalendarVSAssemblyInfo live = liveCalendar(CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE,
                                                 new Point(50, 240), new Dimension(210, 162));
      apply(live, CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE, 36, new Dimension(210, 18));
      assertEquals(new Point(50, 402 - 36), live.getPixelOffset());
      apply(live, CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE, 60, new Dimension(210, 18));
      assertEquals(new Point(50, 402 - 60), live.getPixelOffset());
      apply(live, CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, 60, new Dimension(210, 18));
      return live;
   }

   private static CalendarVSAssemblyInfo liveCalendar(int showType, Point pos, Dimension size) {
      CalendarVSAssemblyInfo live = new CalendarVSAssemblyInfo();
      live.setShowTypeValue(showType);
      live.setTitleHeightValue(36);
      live.setPixelOffset(pos);
      live.setPixelSize(size);
      live.setAdditionalTableNames(new ArrayList<>());
      return live;
   }

   /**
    * Apply the dialog to the live info the way AbstractVSAssembly.setVSAssemblyInfo does.
    */
   private void apply(CalendarVSAssemblyInfo live, int newShowType, int newTitleHeight,
                      Dimension dialogSize) throws Exception
   {
      CalendarVSAssemblyInfo result =
         save(live, true, 402, newShowType, newTitleHeight, dialogSize);
      // the mocked data pane leaves the additional table list unset
      result.setAdditionalTableNames(new ArrayList<>());
      live.copyInfo(result);
      Mockito.clearInvocations(vsObjectPropertyService);
   }

   // Bug #77372: calendar show type, title set taller than the calendar height (calendartab1)
   @Test
   void bottomTabsCalendarGrowsWhenTitleTallerThanHeight() throws Exception {
      CalendarVSAssemblyInfo result =
         save(CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, 36, new Point(98, 242), 542,
              CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, 310,
              new Dimension(300, 300), new Dimension(300, 300));
      // height = title(310) + body(144), bottom edge flush with the tab top
      assertEquals(new Dimension(300, 454), result.getPixelSize());
      assertEquals(new Point(98, 88), result.getPixelOffset());
      assertSizeKeptOnWrite(result, new Dimension(300, 454));
   }

   @Test
   void bottomTabsCalendarKeepsHeightOnTitleOnlyChange() throws Exception {
      CalendarVSAssemblyInfo result =
         save(CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, 36, new Point(98, 242), 542,
              CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, 50,
              new Dimension(300, 300), new Dimension(300, 300));
      // show type unchanged, the height must not be reset to the default calendar height
      assertEquals(new Dimension(300, 300), result.getPixelSize());
      assertEquals(new Point(98, 242), result.getPixelOffset());
      assertSizeKeptOnWrite(result, new Dimension(300, 300));
   }

   @Test
   void bottomTabsSwitchToCalendarGrowsWhenTitleTallerThanDefaultHeight() throws Exception {
      CalendarVSAssemblyInfo result =
         save(CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE, 20, new Point(98, 300), 542,
              CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, 310,
              new Dimension(300, 20), new Dimension(300, 20));
      assertEquals(new Dimension(300, 454), result.getPixelSize());
      assertEquals(new Point(98, 88), result.getPixelOffset());
      assertSizeKeptOnWrite(result, new Dimension(300, 454));
   }

   @Test
   void bottomTabsCalendarGrowsWhenTitleEqualsHeight() throws Exception {
      // a title exactly as tall as the calendar leaves no room for the body
      CalendarVSAssemblyInfo result =
         save(CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, 36, new Point(98, 242), 542,
              CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, 300,
              new Dimension(300, 300), new Dimension(300, 300));
      assertEquals(new Dimension(300, 300 + CalendarVSAssemblyInfo.CALENDAR_BODY_HEIGHT),
                   result.getPixelSize());
      assertEquals(new Point(98, 542 - 300 - CalendarVSAssemblyInfo.CALENDAR_BODY_HEIGHT),
                   result.getPixelOffset());
   }

   @Test
   void bottomTabsCalendarKeepsHeightWhenTitleTallerThanDefaultButShorterThanHeight()
      throws Exception
   {
      // the floor compares against the current height, not DEFAULT_CALENDAR_HEIGHT
      CalendarVSAssemblyInfo result =
         save(CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, 36, new Point(98, 242), 542,
              CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE, 250,
              new Dimension(300, 300), new Dimension(300, 300));
      assertEquals(new Dimension(300, 300), result.getPixelSize());
      assertEquals(new Point(98, 242), result.getPixelOffset());
   }

   /**
    * The saved info is copied into the live assembly, and writeXML() of either one runs
    * fixCalendarSize(), which may change the pixel size. Make sure the saved size sticks.
    */
   private void assertSizeKeptOnWrite(CalendarVSAssemblyInfo result, Dimension expected) {
      // the deep-stubbed data pane yields null additional tables, which copyInfo() rejects
      result.setAdditionalTableNames(new ArrayList<>());
      CalendarVSAssemblyInfo live = (CalendarVSAssemblyInfo) originalInfo.clone();
      live.copyInfo(result);
      live.writeXML(new PrintWriter(new StringWriter()));
      assertEquals(expected, live.getPixelSize());

      result.writeXML(new PrintWriter(new StringWriter()));
      assertEquals(expected, result.getPixelSize());
   }

   private CalendarVSAssemblyInfo save(int oldShowType, boolean bottomTabs, int newShowType,
                                       int newTitleHeight) throws Exception
   {
      return save(oldShowType, bottomTabs, newShowType, newTitleHeight,
                  new Dimension(200, 20), null);
   }

   /**
    * @param dialogSize the size in the size/position pane, or null to leave the size unchanged.
    */
   private CalendarVSAssemblyInfo save(int oldShowType, boolean bottomTabs, int newShowType,
                                       int newTitleHeight, Dimension size,
                                       Dimension dialogSize) throws Exception
   {
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();
      info.setShowTypeValue(oldShowType);
      info.setTitleHeightValue(20);
      info.setPixelOffset(new Point(50, 300));
      info.setPixelSize(size);
      return save(info, bottomTabs, 420, newShowType, newTitleHeight, dialogSize);
   }

   /**
    * Save with the real size and position panes applied (dialog shows the current values).
    */
   private CalendarVSAssemblyInfo save(int oldShowType, int oldTitleHeight, Point pos,
                                       int tabTop, int newShowType, int newTitleHeight,
                                       Dimension size, Dimension dialogSize) throws Exception
   {
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();
      info.setShowTypeValue(oldShowType);
      info.setTitleHeightValue(oldTitleHeight);
      info.setPixelOffset(pos);
      info.setPixelSize(size);

      doCallRealMethod().when(dialogService)
         .setAssemblyPosition(any(), any(SizePositionPaneModel.class));
      given(calendarPropertyDialogModel.getCalendarGeneralPaneModel()
               .getSizePositionPaneModel().getLeft())
         .willReturn(pos.x);
      given(calendarPropertyDialogModel.getCalendarGeneralPaneModel()
               .getSizePositionPaneModel().getTop())
         .willReturn(pos.y);

      return save(info, true, tabTop, newShowType, newTitleHeight, dialogSize);
   }

   private CalendarVSAssemblyInfo save(CalendarVSAssemblyInfo info, boolean bottomTabs,
                                       int tabTop, int newShowType, int newTitleHeight,
                                       Dimension dialogSize) throws Exception
   {
      originalInfo = (CalendarVSAssemblyInfo) info.clone();

      if(dialogSize != null) {
         doCallRealMethod().when(dialogService)
            .setAssemblySize(any(), any(SizePositionPaneModel.class));
         doCallRealMethod().when(dialogService).setAssemblySize(any(), anyInt(), anyInt());
         given(calendarPropertyDialogModel.getCalendarGeneralPaneModel()
                  .getSizePositionPaneModel().getWidth())
            .willReturn(dialogSize.width);
         given(calendarPropertyDialogModel.getCalendarGeneralPaneModel()
                  .getSizePositionPaneModel().getHeight())
            .willReturn(dialogSize.height);
      }

      TabVSAssemblyInfo tabInfo = new TabVSAssemblyInfo();
      tabInfo.setBottomTabsValue(bottomTabs);
      tabInfo.setPixelOffset(new Point(0, tabTop));

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
      // the dialog sends the stored view mode back unless the author changes it
      given(calendarPropertyDialogModel.getCalendarAdvancedPaneModel()
               .getViewMode())
         .willReturn(info.getViewModeValue());

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
      return argument.getValue();
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
   // clone of the info passed to the last save(...), before the dialog was applied; it
   // stands in for the live assembly info in assertSizeKeptOnWrite()
   private CalendarVSAssemblyInfo originalInfo;
}
