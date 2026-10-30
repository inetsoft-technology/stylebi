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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
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
 * Bug #79736: {@code set_format} target {@code viewsheet} formats the dashboard itself, which
 * FormatPainterService reaches through an empty object list.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetFormatServiceViewsheetTargetTest {
   private static RuntimeViewsheet rvs() {
      Viewsheet viewsheet = mock(Viewsheet.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getVSAssemblyInfo()).thenReturn(mock(VSAssemblyInfo.class));
      return rvs;
   }

   private static ViewsheetFormatService.FormatRequest request(String json, String target,
                                                               List<String> assemblies)
      throws Exception
   {
      ObjectMapper mapper = new ObjectMapper();
      return ViewsheetFormatService.FormatRequest.fromJson(
         assemblies, mapper.readTree(json), false, target, null, null);
   }

   private static FormatVSObjectEvent write(ViewsheetFormatService.FormatRequest request,
                                            FormatPainterService painter) throws Exception
   {
      new ViewsheetFormatService(sessionsFor(rvs()), painter, mock(CalcTableService.class), null)
         .setFormat("tok", principal(), request, "");
      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(java.security.Principal.class),
                                any(), anyString());
      return captor.getValue();
   }

   @Test
   void viewsheetTargetSendsAnEmptyObjectListWithTheBackground() throws Exception {
      FormatVSObjectEvent event = write(
         request("{\"backgroundColor\":\"#112233\"}", "viewsheet", null),
         mock(FormatPainterService.class));

      assertEquals(0, event.getObjects().length, "an empty list is the painter's sheet route");
      assertEquals(0, event.getCharts().length);
      assertEquals("#112233", event.getFormat().getBackgroundColor());
   }

   @Test
   void dashboardIsAnAliasAndEmptyAssembliesAreAccepted() throws Exception {
      FormatVSObjectEvent event = write(
         request("{\"backgroundAlpha\":50}", "Dashboard", List.of()),
         mock(FormatPainterService.class));
      assertEquals(0, event.getObjects().length);
      assertEquals(50, event.getFormat().getBackgroundAlpha());
   }

   @Test
   void refusesAssembliesWithTheViewsheetTarget() {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> write(request("{\"backgroundColor\":\"#112233\"}", "viewsheet", List.of("Chart1")),
                     mock(FormatPainterService.class)));
      assertTrue(e.getMessage().contains("'assemblies'") &&
                 e.getMessage().contains("Chart1"), e.getMessage());
   }

   @Test
   void refusesKeysOtherThanTheBackgroundByName() {
      FormatPainterService painter = mock(FormatPainterService.class);
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> write(request("{\"color\":\"#FF0000\",\"backgroundColor\":\"#112233\"}",
                             "viewsheet", null), painter));
      assertTrue(e.getMessage().contains("[color]") &&
                 e.getMessage().contains("not applied"), e.getMessage());
      verifyNoInteractions(painter);
   }

   @Test
   void otherTargetsStillRequireAssemblies() {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> write(request("{\"backgroundColor\":\"#112233\"}", "object", List.of()),
                     mock(FormatPainterService.class)));
      assertTrue(e.getMessage().contains("at least one assembly name"), e.getMessage());
   }

   @Test
   void unknownTargetErrorListsViewsheet() {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> write(request("{\"backgroundColor\":\"#112233\"}", "bogus", null),
                     mock(FormatPainterService.class)));
      assertTrue(e.getMessage().contains("'viewsheet'"), e.getMessage());
   }
}
