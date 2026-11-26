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
package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.event.calendar.ToggleDoubleCalendarEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

/**
 * Bug #81702: switching a calendar to a double calendar in a device layout must grow the group
 * that holds it, and the client must be sent the group again. A group used as a pop component
 * is left alone (Bug #59133).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSCalendarServiceGroupGrowTest {
   @BeforeEach
   void setUp() throws Exception {
      viewsheet = new Viewsheet();
      calendar = new CalendarVSAssembly(viewsheet, "Calendar1");
      group = new GroupContainerVSAssembly(viewsheet, "Group1");
      viewsheet.addAssembly(calendar);
      viewsheet.addAssembly(group);
      group.setAssemblies(new String[] { "Calendar1" });
      group.getVSAssemblyInfo().setLayoutPosition(new Point(9, 9));
      group.getVSAssemblyInfo().setLayoutSize(new Dimension(291, 204));
      calendar.getVSAssemblyInfo().setLayoutPosition(new Point(9, 9));
      calendar.getVSAssemblyInfo().setLayoutSize(new Dimension(195, 156));

      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(eq("rt"), any())).thenReturn(rvs);
      lifecycle = mock(CoreLifecycleService.class);
      VSObjectPropertyService propertyService = mock(VSObjectPropertyService.class);
      // the real service stores the edited info on the assembly
      doAnswer(invocation -> {
         calendar.setVSAssemblyInfo(invocation.getArgument(1));
         return null;
      }).when(propertyService).editObjectProperty(
         any(), any(), any(), any(), any(), any(), any(CommandDispatcher.class));
      service = new VSCalendarService(lifecycle, propertyService, viewsheetService);
   }

   @Test
   void groupGrowsWithTheDoubleCalendarAndIsRefreshed() throws Exception {
      toggle(true);

      assertEquals(new Dimension(390, 156), calendar.getVSAssemblyInfo().getLayoutSize(false));
      assertEquals(new Dimension(390, 204), group.getVSAssemblyInfo().getLayoutSize(false));
      verify(lifecycle).refreshVSAssembly(same(rvs), eq("Group1"), any(CommandDispatcher.class));
   }

   @Test
   void switchingBackDoesNotShrinkTheGroup() throws Exception {
      toggle(true);
      reset(lifecycle);
      toggle(false);

      assertEquals(new Dimension(195, 156), calendar.getVSAssemblyInfo().getLayoutSize(false));
      assertEquals(new Dimension(390, 204), group.getVSAssemblyInfo().getLayoutSize(false));
      verify(lifecycle, never()).refreshVSAssembly(
         any(), eq("Group1"), any(CommandDispatcher.class));
   }

   @Test
   void groupUsedAsAPopComponentIsNotResized() throws Exception {
      TextVSAssembly text = new TextVSAssembly(viewsheet, "Text1");
      viewsheet.addAssembly(text);
      TextVSAssemblyInfo tinfo = (TextVSAssemblyInfo) text.getVSAssemblyInfo();
      tinfo.setPopOptionValue(PopVSAssemblyInfo.POP_OPTION);
      tinfo.setPopComponentValue("Group1");

      toggle(true);

      assertEquals(new Dimension(291, 204), group.getVSAssemblyInfo().getLayoutSize(false));
      verify(lifecycle, never()).refreshVSAssembly(
         any(), eq("Group1"), any(CommandDispatcher.class));
   }

   @Test
   void groupWithoutLayoutSizeIsNotResized() throws Exception {
      group.getVSAssemblyInfo().setLayoutSize(null);

      toggle(true);

      assertEquals(null, group.getVSAssemblyInfo().getLayoutSize(false));
      verify(lifecycle, never()).refreshVSAssembly(
         any(), eq("Group1"), any(CommandDispatcher.class));
   }

   private void toggle(boolean doubleCalendar) throws Exception {
      ToggleDoubleCalendarEvent event = mock(ToggleDoubleCalendarEvent.class);
      when(event.doubleCalendar()).thenReturn(doubleCalendar);
      service.toggleDoubleCalendar("rt", "Calendar1", event, null, null,
                                   mock(CommandDispatcher.class));
   }

   private Viewsheet viewsheet;
   private CalendarVSAssembly calendar;
   private GroupContainerVSAssembly group;
   private RuntimeViewsheet rvs;
   private CoreLifecycleService lifecycle;
   private VSCalendarService service;
}
