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

import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.composer.vs.objects.event.FormatVSObjectEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class ViewsheetFormatServiceTest {
   @Test
   void appliesTheFormatToTheNamedAssemblies() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();
      format.setColor("#333333");

      serviceWith(painter).setFormat(
         "tok", principal(),
         new ViewsheetFormatService.FormatRequest(List.of("Gauge1"), format, false), "");

      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                anyString());
      assertArrayEquals(new String[]{ "Gauge1" }, captor.getValue().getObjects());
      assertEquals("#333333", captor.getValue().getFormat().getColor());
   }

   @Test
   void requiresAtLeastOneAssembly() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> serviceWith(mock(FormatPainterService.class)).setFormat(
            "tok", principal(),
            new ViewsheetFormatService.FormatRequest(List.of(), new VSObjectFormatInfoModel(),
                                                     false), ""));
      assertTrue(thrown.getMessage().contains("assemblies"));
   }

   @Test
   void requiresAFormatUnlessResetting() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> serviceWith(mock(FormatPainterService.class)).setFormat(
            "tok", principal(),
            new ViewsheetFormatService.FormatRequest(List.of("Gauge1"), null, false), ""));
      assertTrue(thrown.getMessage().contains("format"));
   }

   @Test
   void resetNeedsNoFormat() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);

      serviceWith(painter).setFormat(
         "tok", principal(),
         new ViewsheetFormatService.FormatRequest(List.of("Gauge1"), null, true), "");

      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                anyString());
      assertTrue(captor.getValue().isReset());
   }

   private static ViewsheetFormatService serviceWith(FormatPainterService painter) {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(null, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new ViewsheetFormatService(sessions, painter);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
