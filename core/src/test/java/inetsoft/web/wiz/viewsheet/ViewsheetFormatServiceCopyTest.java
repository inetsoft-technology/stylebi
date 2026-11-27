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
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.viewsheet.controller.UndoRedoServiceProxy;
import inetsoft.web.wiz.binding.CalcTableService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #82419: {@code format/copy} applies several set_format / set_chart_region_format bodies as
 * ONE undo step. These run the real {@link ViewsheetFormatService} over the real
 * {@link ViewsheetSessionService} (only the runtime, the painter and the broadcaster are mocks),
 * so the checkpoint count is the session service's own.
 */
@WizAgentTestSupport
class ViewsheetFormatServiceCopyTest {
   private RuntimeViewsheet rvs;
   private FormatPainterService painter;
   private ViewsheetFormatService service;
   private final Principal agent = TestPrincipals.user("alice", "host-org");

   @BeforeEach
   void setUp() throws Exception {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("Gauge1")).thenReturn(mock(GaugeVSAssembly.class));
      when(vs.getAssembly("Gauge2")).thenReturn(mock(GaugeVSAssembly.class));
      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("Viewsheet/foo-7");

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = new JoinSession("TOK", "Viewsheet/foo-7", "alice~;~host-org",
                                      SheetType.VIEWSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.VIEWSHEET), eq("Viewsheet/foo-7"),
                                            eq(agent)))
         .thenReturn(rvs);
      ViewsheetSessionService real = new ViewsheetSessionService(
         sessions, runtimeAccess, mock(SheetAgentBroadcastService.class),
         mock(UndoRedoServiceProxy.class));
      painter = mock(FormatPainterService.class);
      service = new ViewsheetFormatService(real, FormatPainterProxies.local(painter),
                                           mock(CalcTableService.class), null);
   }

   private static ViewsheetFormatService.FormatCopyPart object(String assembly, String color) {
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();
      format.setColor(color);
      return new ViewsheetFormatService.FormatCopyPart(
         "format",
         new ViewsheetFormatService.FormatRequest(List.of(assembly), format, false, "object",
                                                  null),
         null);
   }

   @Test
   void severalPartsAreOneCheckpointWhileEachSingleWriteIsStillItsOwn() throws Exception {
      // control: the existing route is unchanged, one checkpoint per call
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();
      format.setColor("#FF0000");
      service.setFormat("TOK", agent,
                        new ViewsheetFormatService.FormatRequest(List.of("Gauge1"), format, false),
                        "");
      service.setFormat("TOK", agent,
                        new ViewsheetFormatService.FormatRequest(List.of("Gauge2"), format, false),
                        "");
      verify(rvs, times(2)).addCheckpoint(any());

      ViewsheetFormatService.FormatCopyResult result = service.copyFormat(
         "TOK", agent,
         new ViewsheetFormatService.FormatCopyRequest(List.of(
            object("Gauge1", "#00FF00"), object("Gauge2", "#00FF00"),
            object("Gauge1", "#0000FF"))),
         "");

      assertEquals(1, result.undoSteps());
      // two before, one for the whole copy
      verify(rvs, times(3)).addCheckpoint(any());
      assertEquals(3, result.parts().size());
      assertTrue(result.parts().stream().allMatch(ViewsheetFormatService.FormatCopyPartResult::ok),
                 result.parts().toString());
      verify(painter, times(5)).setFormat(anyString(), any(), any(), any(), anyString());
   }

   @Test
   void aRefusedPartIsReportedAndTheOthersStillApplyUnderOneCheckpoint() throws Exception {
      ViewsheetFormatService.FormatCopyResult result = service.copyFormat(
         "TOK", agent,
         new ViewsheetFormatService.FormatCopyRequest(List.of(
            object("Gauge1", "#00FF00"), object("NoSuchAssembly", "#00FF00"),
            object("Gauge2", "#00FF00"))),
         "");

      assertEquals(List.of(true, false, true),
                   result.parts().stream()
                      .map(ViewsheetFormatService.FormatCopyPartResult::ok).toList());
      assertTrue(result.parts().get(1).error().contains("'NoSuchAssembly'"),
                 result.parts().get(1).error());
      assertNull(result.parts().get(0).error());
      assertEquals(1, result.undoSteps());
      verify(rvs, times(1)).addCheckpoint(any());
      verify(painter, times(2)).setFormat(anyString(), any(), any(), any(), anyString());
   }

   @Test
   void aCopyWhereEveryPartIsRefusedAddsNoUndoStep() throws Exception {
      ViewsheetFormatService.FormatCopyResult result = service.copyFormat(
         "TOK", agent,
         new ViewsheetFormatService.FormatCopyRequest(List.of(
            object("NoSuchAssembly", "#00FF00"))),
         "");

      assertFalse(result.parts().get(0).ok());
      assertEquals(0, result.undoSteps());
      verify(rvs, never()).addCheckpoint(any());
   }

   @Test
   void malformedRequestsAreRefusedBeforeAnyWrite() throws Exception {
      for(ViewsheetFormatService.FormatCopyRequest request : List.of(
         new ViewsheetFormatService.FormatCopyRequest(null),
         new ViewsheetFormatService.FormatCopyRequest(List.of()),
         new ViewsheetFormatService.FormatCopyRequest(List.of(
            new ViewsheetFormatService.FormatCopyPart(null, null, null))),
         new ViewsheetFormatService.FormatCopyRequest(List.of(
            new ViewsheetFormatService.FormatCopyPart("format", null, null))),
         new ViewsheetFormatService.FormatCopyRequest(List.of(
            new ViewsheetFormatService.FormatCopyPart("region", null, null))),
         new ViewsheetFormatService.FormatCopyRequest(List.of(
            new ViewsheetFormatService.FormatCopyPart("cell", null, null)))))
      {
         assertThrows(IllegalArgumentException.class,
                      () -> service.copyFormat("TOK", agent, request, ""), request.toString());
      }

      verify(rvs, never()).addCheckpoint(any());
      verify(painter, never()).setFormat(anyString(), any(), any(), any(), anyString());
   }

   @Test
   void aBadPartIsNamedByItsPosition() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         service.copyFormat("TOK", agent, new ViewsheetFormatService.FormatCopyRequest(List.of(
            object("Gauge1", "#00FF00"),
            new ViewsheetFormatService.FormatCopyPart("region", null, null))), ""));

      assertTrue(e.getMessage().contains("parts[1]"), e.getMessage());
   }
}
