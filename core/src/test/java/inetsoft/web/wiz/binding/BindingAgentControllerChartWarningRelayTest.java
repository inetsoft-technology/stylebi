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
package inetsoft.web.wiz.binding;

import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.viewsheet.ViewsheetFormatService;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78439: every chart write endpoint of the binding controller answers
 * {@code {"warnings": [...]}} when its service captured a "chart cannot be generated" warning, and
 * the empty 200 it always was when nothing warned.
 */
@Tag("core")
class BindingAgentControllerChartWarningRelayTest {
   private static final List<String> WARNED = List.of("Chart 'Chart1' cannot be generated: x");
   private static final FieldRef FIELD = new FieldRef("Region", "dimension", null, null, null);

   @Test
   void everyChartWriteRelaysTheWarnings() throws Exception {
      BindingAgentController controller = controller(WARNED);
      Map<String, Object> expected = Map.of("warnings", WARNED);

      assertEquals(expected, controller.setChartShelf(
         "tok", new BindingAgentController.ShelfRequest("Chart1", "x", List.of(FIELD), null), "",
         principal()));
      assertEquals(expected, controller.setChartSource(
         "tok", new BindingAgentController.ChartSourceRequest("Chart1", "T", null), "",
         principal()));
      assertEquals(expected, controller.setChartSingleShelf(
         "tok", new BindingAgentController.SingleShelfRequest("Chart1", "path", FIELD, null), "",
         principal()));
      assertEquals(expected, controller.setChartSort(
         "tok", new BindingAgentController.ChartSortRequest(
            "Chart1", "x", "Region", null, "asc", null, null), "", principal()));
      assertEquals(expected, controller.setChartRanking(
         "tok", new BindingAgentController.ChartRankingRequest(
            "Chart1", "x", "Region", null, "top", 3, "Sales", null), "", principal()));
      assertEquals(expected, controller.setChartType(
         "tok", new BindingAgentController.ChartTypeRequest(
            "Chart1", 3, null, null, null, null), "", principal()));
      assertEquals(expected, controller.swapChartAxes(
         "tok", new BindingAgentController.SwapRequest("Chart1"), "", principal()));
      assertEquals(expected, controller.setChartSeparateStatus(
         "tok", new BindingAgentController.SeparateStatusRequest("Chart1", true), "",
         principal()));
      assertEquals(expected, controller.setAestheticField(
         "tok", new BindingAgentController.AestheticFieldRequest(
            "Chart1", "color", FIELD, null), "", principal()));
      assertEquals(expected, controller.clearAestheticField(
         "tok", new BindingAgentController.AestheticFieldRequest(
            "Chart1", "color", null, null), "", principal()));
      assertEquals(expected, controller.setVisualFrame(
         "tok", new BindingAgentController.AestheticFrameRequest(
            "Chart1", "color", Map.of("type", "static"), null), "", principal()));
      assertEquals(expected, controller.resetVisualFrame(
         "tok", new BindingAgentController.AestheticResetRequest("Chart1", "color", null), "",
         principal()));
   }

   @Test
   void everyChartWriteAnswersAnEmptyBodyWhenNothingWarned() throws Exception {
      BindingAgentController controller = controller(List.of());

      assertNull(controller.setChartShelf(
         "tok", new BindingAgentController.ShelfRequest("Chart1", "x", List.of(FIELD), null), "",
         principal()));
      assertNull(controller.setChartType(
         "tok", new BindingAgentController.ChartTypeRequest(
            "Chart1", 3, null, null, null, null), "", principal()));
      assertNull(controller.swapChartAxes(
         "tok", new BindingAgentController.SwapRequest("Chart1"), "", principal()));
      assertNull(controller.resetVisualFrame(
         "tok", new BindingAgentController.AestheticResetRequest("Chart1", "color", null), "",
         principal()));
   }

   private static Principal principal() {
      return () -> "admin";
   }

   /** A controller whose chart services answer every write with {@code warnings}. */
   private static BindingAgentController controller(List<String> warnings) throws Exception {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      ChartBindingService chart = mock(ChartBindingService.class);
      ChartAestheticAgentService aesthetic = mock(ChartAestheticAgentService.class);
      when(chart.setShelf(anyString(), any(), anyString(), anyString(), any(), any(),
                          anyString())).thenReturn(warnings);
      when(chart.setSource(anyString(), any(), anyString(), anyString(), anyBoolean(),
                           anyString())).thenReturn(warnings);
      when(chart.setSingleShelf(anyString(), any(), anyString(), anyString(), any(), any(),
                                anyString())).thenReturn(warnings);
      when(chart.setSort(anyString(), any(), anyString(), anyString(), anyString(), any(),
                         any(), anyString())).thenReturn(warnings);
      when(chart.setRanking(anyString(), any(), anyString(), anyString(), anyString(), any(),
                            any(), anyString())).thenReturn(warnings);
      when(chart.setChartType(anyString(), any(), anyString(), anyInt(), any(), any(), any(),
                              any(), anyString())).thenReturn(warnings);
      when(chart.swapAxes(anyString(), any(), anyString(), anyString())).thenReturn(warnings);
      when(chart.setSeparateStatus(anyString(), any(), anyString(), anyBoolean(), anyString()))
         .thenReturn(warnings);
      when(aesthetic.setField(anyString(), any(), anyString(), anyString(), any(), any(),
                              anyString())).thenReturn(warnings);
      when(aesthetic.clearField(anyString(), any(), anyString(), anyString(), anyString()))
         .thenReturn(warnings);
      when(aesthetic.setFrame(anyString(), any(), anyString(), anyString(), any(), any(),
                              anyString())).thenReturn(warnings);
      when(aesthetic.resetFrame(anyString(), any(), anyString(), anyString(), any(),
                                anyString())).thenReturn(warnings);

      // The listing fails, so the controller keeps the request's own fields and source
      // (resolveSourceTable's documented fallback) instead of needing a bindable-table fixture.
      BindableFieldsService fields = mock(BindableFieldsService.class);
      when(fields.list(any(), any(), any())).thenThrow(new IllegalStateException("no listing"));

      return new BindingAgentController(
         feature, mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(ViewsheetSessionService.class), fields, mock(BindingReadService.class), chart,
         aesthetic, mock(TableBindingService.class), mock(CalcTableService.class),
         mock(SelectionBindingService.class), mock(CalcFieldAgentService.class),
         mock(ViewsheetFormatService.class), mock(SheetAgentBroadcastService.class));
   }
}
