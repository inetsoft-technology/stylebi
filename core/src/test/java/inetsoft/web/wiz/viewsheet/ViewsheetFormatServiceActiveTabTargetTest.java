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

import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.CalendarVSAssembly;
import inetsoft.uql.viewsheet.TabVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.composer.vs.objects.event.FormatVSObjectEvent;
import inetsoft.web.wiz.binding.CalcTableService;
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
 * Bug #79229: {@code set_format} target {@code activeTab} writes the Tab's selected-tab format path
 * ({@link TabVSAssemblyInfo#ACTIVE_TAB_PATH}), which the whole-object target never reaches.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetFormatServiceActiveTabTargetTest {
   private static TabVSAssembly tab() {
      TabVSAssembly assembly = mock(TabVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(mock(TabVSAssemblyInfo.class));
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

   private static ViewsheetFormatService.FormatRequest request(String target) {
      return new ViewsheetFormatService.FormatRequest(List.of("Tab1"), red(), false, target, null,
                                                      null);
   }

   private static FormatVSObjectEvent write(RuntimeViewsheet rvs, FormatPainterService painter,
                                            String target) throws Exception
   {
      new ViewsheetFormatService(sessionsFor(rvs), painter, mock(CalcTableService.class), null)
         .setFormat("tok", principal(), request(target), "");
      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(java.security.Principal.class),
                                any(), anyString());
      return captor.getValue();
   }

   @Test
   void activeTabWritesTheActiveTabPath() throws Exception {
      FormatVSObjectEvent event = write(rvsWith("Tab1", tab()), mock(FormatPainterService.class),
                                        "activeTab");

      assertNotNull(event.getData(), "activeTab must populate event.getData()");
      assertEquals(1, event.getData().size());
      assertArrayEquals(new TableDataPath[]{ TabVSAssemblyInfo.ACTIVE_TAB_PATH },
                        event.getData().get(0));
   }

   @Test
   void objectTargetStillWritesTheWholeObject() throws Exception {
      FormatVSObjectEvent event = write(rvsWith("Tab1", tab()), mock(FormatPainterService.class),
                                        "object");
      assertNull(event.getData(), "object keeps the whole-object write, not the active-tab path");
   }

   @Test
   void targetIsCaseInsensitiveLikeTheOthers() throws Exception {
      FormatVSObjectEvent event = write(rvsWith("Tab1", tab()), mock(FormatPainterService.class),
                                        "ActiveTab");
      assertEquals(TabVSAssemblyInfo.ACTIVE_TAB_PATH, event.getData().get(0)[0]);
   }

   @Test
   void refusesActiveTabOnANonTabBeforeMutating() throws Exception {
      ViewsheetSessionService sessions =
         sessionsFor(rvsWith("Tab1", mock(CalendarVSAssembly.class)));
      FormatPainterService painter = mock(FormatPainterService.class);

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> new ViewsheetFormatService(sessions, painter, mock(CalcTableService.class), null)
            .setFormat("tok", principal(), request("activeTab"), ""));

      assertTrue(e.getMessage().contains("'activeTab' only applies to a Tab") &&
                 e.getMessage().contains("'Tab1'") &&
                 e.getMessage().contains("not applied"), e.getMessage());
      verify(sessions, never()).mutate(anyString(), any(java.security.Principal.class), any());
      verifyNoInteractions(painter);
   }

   @Test
   void refusesActiveTabOnAMissingAssembly() {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> new ViewsheetFormatService(sessionsFor(rvsWith("Tab1", null)),
                                          mock(FormatPainterService.class),
                                          mock(CalcTableService.class), null)
            .setFormat("tok", principal(), request("activeTab"), ""));
      assertTrue(e.getMessage().contains("no assembly named 'Tab1'"), e.getMessage());
   }

   @Test
   void unknownTargetErrorListsActiveTab() {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> new ViewsheetFormatService(sessionsFor(null), mock(FormatPainterService.class),
                                          mock(CalcTableService.class), null)
            .setFormat("tok", principal(), request("bogus"), ""));
      assertTrue(e.getMessage().contains("'activeTab'"), e.getMessage());
   }
}