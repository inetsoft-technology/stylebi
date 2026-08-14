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
import inetsoft.uql.viewsheet.*;
import inetsoft.web.binding.controller.ChangeChartAestheticService;
import inetsoft.web.binding.event.ChangeChartRefEvent;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.aesthetic.BluesColorModel;
import inetsoft.web.binding.model.graph.aesthetic.StaticColorModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class ChartAestheticAgentServiceTest {
   private static Map<String, Object> spec(Object... pairs) {
      Map<String, Object> spec = new LinkedHashMap<>();

      for(int i = 0; i < pairs.length; i += 2) {
         spec.put((String) pairs[i], pairs[i + 1]);
      }

      return spec;
   }

   @Test
   void setFieldPostsTheModelItReadRatherThanAFreshOne() throws Exception {
      ChartBindingModel existing = new ChartBindingModel();
      // A value only the read model carries. If the service constructs a fresh model this is
      // lost — the same failure that would silently discard the user's shelves.
      existing.setChartType(42);
      ChangeChartAestheticService aesthetics = mock(ChangeChartAestheticService.class);

      harness(existing, aesthetics)
         .setField("tok", principal(), "Chart1", "color",
                   new FieldRef("Region", "dimension", null, null, null), "");

      ChangeChartRefEvent event = captureEvent(aesthetics);
      assertEquals(42, event.getModel().getChartType(),
                   "the posted model must be the one read, not a fresh construction");
      assertEquals("Region", event.getModel().getColorField().getFullName());
      assertEquals("Chart1", event.getName());
   }

   /**
    * {@code ChangeChartAestheticService} branches on the field type — colour and shape clear
    * the viewsheet's shared frames. Posting the wrong one leaves stale shared frames behind.
    */
   @Test
   void setFieldTellsTheBackendWhichChannelChanged() throws Exception {
      ChangeChartAestheticService aesthetics = mock(ChangeChartAestheticService.class);

      harness(new ChartBindingModel(), aesthetics)
         .setField("tok", principal(), "Chart1", "color",
                   new FieldRef("Region", "dimension", null, null, null), "");

      assertEquals("color", captureEvent(aesthetics).getFieldType());
   }

   @Test
   void setFieldLeavesTheShelvesUntouched() throws Exception {
      ChartBindingModel existing = new ChartBindingModel();
      ChartBindingMutator.setShelf(
         existing, "x", List.of(new FieldRef("Region", "dimension", null, null, null)));
      Object shelfBefore = existing.getXFields();
      ChangeChartAestheticService aesthetics = mock(ChangeChartAestheticService.class);

      harness(existing, aesthetics)
         .setField("tok", principal(), "Chart1", "color",
                   new FieldRef("Category", "dimension", null, null, null), "");

      assertSame(shelfBefore, captureEvent(aesthetics).getModel().getXFields(),
                 "an aesthetic write must not disturb the shelves spec 2b owns");
   }

   @Test
   void clearFieldUnbindsTheChannel() throws Exception {
      ChartBindingModel existing = new ChartBindingModel();
      ChartAestheticMutator.setField(existing, "color",
                                     new FieldRef("Region", "dimension", null, null, null));
      ChangeChartAestheticService aesthetics = mock(ChangeChartAestheticService.class);

      harness(existing, aesthetics).clearField("tok", principal(), "Chart1", "color", "");

      assertNull(captureEvent(aesthetics).getModel().getColorField());
   }

   @Test
   void setFrameAppliesAStaticColour() throws Exception {
      ChangeChartAestheticService aesthetics = mock(ChangeChartAestheticService.class);

      harness(new ChartBindingModel(), aesthetics)
         .setFrame("tok", principal(), "Chart1", "color",
                   spec("type", "static", "color", "#4e79a7"), "");

      StaticColorModel frame = assertInstanceOf(
         StaticColorModel.class, captureEvent(aesthetics).getModel().getColorFrame());
      assertEquals("#4E79A7", frame.getColor());
   }

   @Test
   void setFrameAppliesANamedPalette() throws Exception {
      ChangeChartAestheticService aesthetics = mock(ChangeChartAestheticService.class);

      harness(new ChartBindingModel(), aesthetics)
         .setFrame("tok", principal(), "Chart1", "color",
                   spec("type", "palette", "palette", "Blues"), "");

      assertInstanceOf(BluesColorModel.class,
                       captureEvent(aesthetics).getModel().getColorFrame());
   }

   @Test
   void eachMutationIsExactlyOneCheckpoint() throws Exception {
      ViewsheetSessionService sessions = sessionsFor(mock(ChartVSAssembly.class));
      ChartAestheticAgentService service = serviceWith(sessions, new ChartBindingModel(),
                                                  mock(ChangeChartAestheticService.class));

      service.setField("tok", principal(), "Chart1", "color",
                       new FieldRef("Region", "dimension", null, null, null), "");

      verify(sessions, times(1)).mutate(anyString(), any(Principal.class), any());
   }

   @Test
   void readDescribesTheChannelsWithoutMutating() throws Exception {
      ChartBindingModel existing = new ChartBindingModel();
      ChartAestheticMutator.setField(existing, "color",
                                     new FieldRef("Region", "dimension", null, null, null));
      ViewsheetSessionService sessions = sessionsFor(mock(ChartVSAssembly.class));
      ChartAestheticAgentService service = serviceWith(sessions, existing,
                                                  mock(ChangeChartAestheticService.class));

      Map<String, Object> read = service.read("tok", principal(), "Chart1");

      @SuppressWarnings("unchecked")
      Map<String, Object> color = (Map<String, Object>) read.get("color");
      assertEquals("Region", color.get("field"));
      verify(sessions, never()).mutate(anyString(), any(Principal.class), any());
   }

   @Test
   void refusesANonChartAssemblyNamingIt() {
      ChartAestheticAgentService service = serviceWith(
         sessionsFor(mock(TextVSAssembly.class)), new ChartBindingModel(),
         mock(ChangeChartAestheticService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.setField("tok", principal(), "Text1", "color",
                                new FieldRef("Region", "dimension", null, null, null), ""));
      assertTrue(thrown.getMessage().contains("Text1"));
   }

   @Test
   void refusesAnUnknownChannelBeforeTouchingTheRuntime() {
      ViewsheetSessionService sessions = sessionsFor(mock(ChartVSAssembly.class));
      ChartAestheticAgentService service = serviceWith(sessions, new ChartBindingModel(),
                                                  mock(ChangeChartAestheticService.class));

      assertThrows(Exception.class,
                   () -> service.setField("tok", principal(), "Chart1", "colour",
                                          new FieldRef("Region", "dimension", null, null, null),
                                          ""));
   }

   // ── harness ───────────────────────────────────────────────────────────────

   private static ChangeChartRefEvent captureEvent(ChangeChartAestheticService aesthetics)
      throws Exception
   {
      ArgumentCaptor<ChangeChartRefEvent> captor =
         ArgumentCaptor.forClass(ChangeChartRefEvent.class);
      verify(aesthetics).changeChartAesthetic(eq("rt1"), captor.capture(), any(Principal.class),
                                              any(), anyString());
      return captor.getValue();
   }

   private static ChartAestheticAgentService harness(ChartBindingModel model,
                                                ChangeChartAestheticService aesthetics)
   {
      return serviceWith(sessionsFor(mock(ChartVSAssembly.class)), model, aesthetics);
   }

   /**
    * A session service whose mutate() runs the mutation immediately against runtime "rt1", so
    * these tests exercise the read-modify-write without a live runtime.
    */
   private static ViewsheetSessionService sessionsFor(VSAssembly assembly) {
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
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return sessions;
   }

   private static ChartAestheticAgentService serviceWith(ViewsheetSessionService sessions,
                                                    ChartBindingModel model,
                                                    ChangeChartAestheticService aesthetics)
   {
      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(model);
      return new ChartAestheticAgentService(sessions, binding, aesthetics);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
