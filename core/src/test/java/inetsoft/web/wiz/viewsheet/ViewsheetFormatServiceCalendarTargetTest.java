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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.CalendarVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.composer.vs.objects.event.FormatVSObjectEvent;
import inetsoft.web.wiz.binding.CalcTableService;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.report.TableDataPath;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static inetsoft.web.wiz.viewsheet.ViewsheetFormatServiceTest.principal;
import static inetsoft.web.wiz.viewsheet.ViewsheetFormatServiceTest.sessionsFor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79228: {@code set_format} targets {@code calendarHeader} and {@code calendarGrid} write the
 * Calendar's own format paths (header text, month grid or year grid), which the whole-object and
 * title-bar targets never reach.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetFormatServiceCalendarTargetTest {
   private static CalendarVSAssembly calendar(boolean yearView) {
      CalendarVSAssembly assembly = mock(CalendarVSAssembly.class);
      CalendarVSAssemblyInfo info = mock(CalendarVSAssemblyInfo.class);
      when(info.isYearView()).thenReturn(yearView);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      return assembly;
   }

   private static RuntimeViewsheet rvsWith(String name, VSAssembly assembly) {
      Viewsheet viewsheet = mock(Viewsheet.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(name)).thenReturn(assembly);
      return rvs;
   }

   private static VSObjectFormatInfoModel red() {
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();
      format.setColor("#FF0000");
      return format;
   }

   private static FormatVSObjectEvent write(RuntimeViewsheet rvs, FormatPainterService painter,
                                            String target) throws Exception
   {
      new ViewsheetFormatService(sessionsFor(rvs), painter, mock(CalcTableService.class), null)
         .setFormat("tok", principal(),
                    new ViewsheetFormatService.FormatRequest(List.of("Cal1"), red(), false,
                                                             target, null, null), "");
      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(java.security.Principal.class), any(),
                                anyString());
      return captor.getValue();
   }

   private static TableDataPath onlyPath(FormatVSObjectEvent event) {
      assertNotNull(event.getData(), "a calendar target must populate event.getData()");
      assertEquals(1, event.getData().size());
      assertEquals(1, event.getData().get(0).length);
      return event.getData().get(0)[0];
   }

   @Test
   void calendarHeaderWritesTheCalendarTitlePathInBothViews() throws Exception {
      for(boolean yearView : new boolean[]{ false, true }) {
         FormatVSObjectEvent event = write(rvsWith("Cal1", calendar(yearView)),
                                           mock(FormatPainterService.class), "calendarHeader");
         assertEquals(CalendarVSAssemblyInfo.CALENDAR_TITLE_PATH, onlyPath(event));
      }
   }

   @Test
   void calendarGridWritesTheMonthPathInMonthView() throws Exception {
      FormatVSObjectEvent event = write(rvsWith("Cal1", calendar(false)),
                                        mock(FormatPainterService.class), "calendarGrid");
      assertEquals(CalendarVSAssemblyInfo.CALENDAR_MONTH_PATH, onlyPath(event));
   }

   @Test
   void calendarGridWritesTheYearPathInYearView() throws Exception {
      FormatVSObjectEvent event = write(rvsWith("Cal1", calendar(true)),
                                        mock(FormatPainterService.class), "calendarGrid");
      assertEquals(CalendarVSAssemblyInfo.CALENDAR_YEAR_PATH, onlyPath(event));
   }

   @Test
   void targetsAreCaseInsensitiveLikeTheOthers() throws Exception {
      FormatVSObjectEvent event = write(rvsWith("Cal1", calendar(false)),
                                        mock(FormatPainterService.class), "CalendarGrid");
      assertEquals(CalendarVSAssemblyInfo.CALENDAR_MONTH_PATH, onlyPath(event));
   }

   @Test
   void refusesACalendarTargetOnATableBeforeMutating() throws Exception {
      for(String target : new String[]{ "calendarHeader", "calendarGrid" }) {
         TableVSAssembly table = mock(TableVSAssembly.class);
         when(table.getVSAssemblyInfo()).thenReturn(mock(TableVSAssemblyInfo.class));
         ViewsheetSessionService sessions = sessionsFor(rvsWith("Cal1", table));
         FormatPainterService painter = mock(FormatPainterService.class);

         IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> new ViewsheetFormatService(sessions, painter, mock(CalcTableService.class),
                                             null)
               .setFormat("tok", principal(),
                          new ViewsheetFormatService.FormatRequest(List.of("Cal1"), red(), false,
                                                                   target, null, null), ""));

         assertTrue(e.getMessage().contains("'" + target + "' only applies to a Calendar") &&
                    e.getMessage().contains("'Cal1'") &&
                    e.getMessage().contains("TableVSAssembly"), e.getMessage());
         verify(sessions, never()).mutate(anyString(), any(java.security.Principal.class), any());
         verifyNoInteractions(painter);
      }
   }

   @Test
   void refusesACalendarTargetOnAMissingAssembly() {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> new ViewsheetFormatService(sessionsFor(rvsWith("Cal1", null)),
                                          mock(FormatPainterService.class),
                                          mock(CalcTableService.class), null)
            .setFormat("tok", principal(),
                       new ViewsheetFormatService.FormatRequest(
                          List.of("Cal1"), red(), false, "calendarGrid", null, null), ""));
      assertTrue(e.getMessage().contains("no assembly named 'Cal1'"), e.getMessage());
   }

   @Test
   void unknownTargetErrorListsTheCalendarTargets() {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> new ViewsheetFormatService(sessionsFor(null), mock(FormatPainterService.class),
                                          mock(CalcTableService.class), null)
            .setFormat("tok", principal(),
                       new ViewsheetFormatService.FormatRequest(
                          List.of("Cal1"), red(), false, "bogus", null, null), ""));
      assertTrue(e.getMessage().contains("calendarHeader") &&
                 e.getMessage().contains("calendarGrid"), e.getMessage());
   }
}
