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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.controller.ChangeChartRefService;
import inetsoft.web.binding.controller.ChangeChartTypeService;
import inetsoft.web.binding.controller.SwapXYBindingService;
import inetsoft.web.binding.event.ChangeChartRefEvent;
import inetsoft.web.binding.event.ChangeChartTypeEvent;
import inetsoft.web.binding.event.ChangeSeparateStatusEvent;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class ChartBindingServiceTest {
   @Test
   void setShelfPostsTheModelItReadRatherThanAFreshOne() throws Exception {
      ChartBindingModel existing = new ChartBindingModel();
      // A value only the read model carries. If the service constructs a fresh model, this
      // is lost — which is exactly how the thirteen aesthetic fields would be lost too.
      existing.setChartType(42);
      ChangeChartRefService refs = mock(ChangeChartRefService.class);

      harness(existing, refs, mock(ChangeChartTypeService.class), mock(SwapXYBindingService.class))
         .setShelf("tok", principal(), "Chart1", "x",
                   List.of(new FieldRef("Region", "dimension", null, null, null)), "");

      ArgumentCaptor<ChangeChartRefEvent> captor =
         ArgumentCaptor.forClass(ChangeChartRefEvent.class);
      verify(refs).changeChartRef(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                  anyString());
      assertEquals(42, captor.getValue().getModel().getChartType(),
                   "the posted model must be the one read, not a fresh construction");
      assertEquals(1, captor.getValue().getModel().getXFields().size());
      assertEquals("Chart1", captor.getValue().getName());
   }

   @Test
   void setShelfLeavesTheAestheticFieldsUntouched() throws Exception {
      ChartBindingModel existing = new ChartBindingModel();
      Map<String, Object> before = ChartBindingFields.snapshotAesthetics(existing);
      ChangeChartRefService refs = mock(ChangeChartRefService.class);

      harness(existing, refs, mock(ChangeChartTypeService.class), mock(SwapXYBindingService.class))
         .setShelf("tok", principal(), "Chart1", "y",
                   List.of(new FieldRef("Sales", "measure", "Sum", null, null)), "");

      ArgumentCaptor<ChangeChartRefEvent> captor =
         ArgumentCaptor.forClass(ChangeChartRefEvent.class);
      verify(refs).changeChartRef(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                  anyString());
      assertEquals(before, ChartBindingFields.snapshotAesthetics(captor.getValue().getModel()),
                   "a shelf write must not disturb the aesthetic fields spec 2c owns");
   }

   @Test
   void setChartTypeDelegatesTheType() throws Exception {
      ChangeChartTypeService types = mock(ChangeChartTypeService.class);

      harness(new ChartBindingModel(), mock(ChangeChartRefService.class), types,
              mock(SwapXYBindingService.class))
         .setChartType("tok", principal(), "Chart1", 3, null, null, null, "");

      ArgumentCaptor<ChangeChartTypeEvent> captor =
         ArgumentCaptor.forClass(ChangeChartTypeEvent.class);
      verify(types).changeChartType(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                    anyString());
      assertEquals(3, captor.getValue().getType());
      assertEquals("Chart1", captor.getValue().getName());
   }

   @Test
   void swapAxesDelegatesTheAssemblyName() throws Exception {
      SwapXYBindingService swap = mock(SwapXYBindingService.class);

      harness(new ChartBindingModel(), mock(ChangeChartRefService.class),
              mock(ChangeChartTypeService.class), swap)
         .swapAxes("tok", principal(), "Chart1", "");

      ArgumentCaptor<ChangeSeparateStatusEvent> captor =
         ArgumentCaptor.forClass(ChangeSeparateStatusEvent.class);
      verify(swap).swapXYBinding(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                 anyString());
      assertEquals("Chart1", captor.getValue().getName());
   }

   @Test
   void refusesANonChartAssemblyNamingIt() {
      ChartBindingService service = harnessWithAssembly(
         mock(TextVSAssembly.class), new ChartBindingModel(),
         mock(ChangeChartRefService.class), mock(ChangeChartTypeService.class),
         mock(SwapXYBindingService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.swapAxes("tok", principal(), "Text1", ""));
      assertTrue(thrown.getMessage().contains("Text1"));
   }

   private static ChartBindingService harness(ChartBindingModel model,
                                              ChangeChartRefService refs,
                                              ChangeChartTypeService types,
                                              SwapXYBindingService swap)
   {
      return harnessWithAssembly(mock(ChartVSAssembly.class), model, refs, types, swap);
   }

   /**
    * A session service whose mutate() runs the mutation immediately against runtime "rt1",
    * so these tests exercise the read-modify-write without a live runtime.
    */
   private static ChartBindingService harnessWithAssembly(VSAssembly assembly,
                                                          ChartBindingModel model,
                                                          ChangeChartRefService refs,
                                                          ChangeChartTypeService types,
                                                          SwapXYBindingService swap)
   {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(model);

      return new ChartBindingService(sessions, binding, refs, types, swap);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
