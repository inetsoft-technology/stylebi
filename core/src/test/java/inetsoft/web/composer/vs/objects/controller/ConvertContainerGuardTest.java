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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.vs.objects.event.ConvertToRangeSliderEvent;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The container guard on both selection conversions.
 *
 * <p>Each guard chained its type test and its container test with {@code &&}, while the body below
 * dereferences the container unconditionally. So an assembly of the <i>right</i> type passed the
 * guard with no container at all, and {@code AbstractVSAssembly.getContainer()} returns {@code null}
 * unless the assembly sits inside a Tab, GroupContainer or CurrentSelection container — the cast of
 * {@code null} succeeded and the next line threw {@code NullPointerException}. Inside a Tab or
 * GroupContainer it threw {@code ClassCastException} instead.
 *
 * <p>It was unreachable from the Composer, whose menu item is hidden unless
 * {@code inSelectionContainer} (`selection-list-actions.ts`, `range-slider-actions.ts`), which is why
 * it survived. Any other caller reaches it immediately.
 *
 * <p>These tests assert the standalone case is declined rather than crashing. Restore either
 * {@code &&} and they fail with the original exception, which is what makes them worth having.
 */
@Tag("core")
class ConvertContainerGuardTest {
   /** A selection list on the canvas, not inside a selection container. */
   @Test
   void aStandaloneSelectionListIsDeclinedRatherThanCrashing() throws Exception {
      SelectionListVSAssembly list = mock(SelectionListVSAssembly.class);
      SelectionListVSAssemblyInfo info = mock(SelectionListVSAssemblyInfo.class);
      when(info.isEmbedded()).thenReturn(false);
      doReturn(info).when(list).getInfo();
      when(list.getContainer()).thenReturn(null);

      ComposerVSSelectionListService service =
         new ComposerVSSelectionListService(engineFor(list), mock(CoreLifecycleService.class));

      assertDoesNotThrow(() -> service.convertToRangeSlider(
         "rt1", event("Filter1"), principal(), null, ""));
   }

   /** Inside a Tab the container is non-null but the wrong type — the CCE case. */
   @Test
   void aSelectionListInsideATabIsDeclinedRatherThanCrashing() throws Exception {
      SelectionListVSAssembly list = mock(SelectionListVSAssembly.class);
      SelectionListVSAssemblyInfo info = mock(SelectionListVSAssemblyInfo.class);
      when(info.isEmbedded()).thenReturn(false);
      doReturn(info).when(list).getInfo();
      when(list.getContainer()).thenReturn(mock(TabVSAssembly.class));

      ComposerVSSelectionListService service =
         new ComposerVSSelectionListService(engineFor(list), mock(CoreLifecycleService.class));

      assertDoesNotThrow(() -> service.convertToRangeSlider(
         "rt1", event("Filter1"), principal(), null, ""));
   }

   /** The mirror defect: convertCSComponent chained three tests with &&. */
   @Test
   void aStandaloneRangeSliderIsDeclinedRatherThanCrashing() throws Exception {
      TimeSliderVSAssembly slider = mock(TimeSliderVSAssembly.class);
      TimeSliderVSAssemblyInfo info = mock(TimeSliderVSAssemblyInfo.class);
      when(info.isEmbedded()).thenReturn(false);
      doReturn(info).when(slider).getInfo();
      when(slider.getContainer()).thenReturn(null);

      ComposerRangeSliderService service =
         new ComposerRangeSliderService(engineFor(slider), mock(CoreLifecycleService.class));

      assertDoesNotThrow(() -> service.convertCSComponent(
         "rt1", event("Slider1"), principal(), null, ""));
   }

   @Test
   void aStandaloneSelectionListIsDeclinedByTheRangeSliderConvertToo() throws Exception {
      SelectionListVSAssembly list = mock(SelectionListVSAssembly.class);
      SelectionListVSAssemblyInfo info = mock(SelectionListVSAssemblyInfo.class);
      when(info.isEmbedded()).thenReturn(false);
      doReturn(info).when(list).getInfo();
      when(list.getContainer()).thenReturn(null);

      ComposerRangeSliderService service =
         new ComposerRangeSliderService(engineFor(list), mock(CoreLifecycleService.class));

      assertDoesNotThrow(() -> service.convertCSComponent(
         "rt1", event("Filter1"), principal(), null, ""));
   }

   /**
    * The guard must still decline a wrong type, which is the behaviour the {@code &&} version did get
    * right — so the fix must not have widened what is accepted.
    */
   @Test
   void aChartIsStillDeclined() throws Exception {
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      VSAssemblyInfo info = mock(VSAssemblyInfo.class);
      when(info.isEmbedded()).thenReturn(false);
      doReturn(info).when(chart).getInfo();
      when(chart.getContainer()).thenReturn(mock(CurrentSelectionVSAssembly.class));

      ComposerVSSelectionListService service =
         new ComposerVSSelectionListService(engineFor(chart), mock(CoreLifecycleService.class));

      assertDoesNotThrow(() -> service.convertToRangeSlider(
         "rt1", event("Chart1"), principal(), null, ""));
   }

   private static ViewsheetService engineFor(VSAssembly assembly) throws Exception {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetService engine = mock(ViewsheetService.class);
      when(engine.getViewsheet(anyString(), any(Principal.class))).thenReturn(rvs);
      return engine;
   }

   private static ConvertToRangeSliderEvent event(String name) {
      ConvertToRangeSliderEvent event = new ConvertToRangeSliderEvent();
      event.setName(name);
      return event;
   }

   private static Principal principal() {
      return mock(Principal.class);
   }
}
