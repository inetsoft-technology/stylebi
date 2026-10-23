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
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import inetsoft.web.composer.vs.objects.controller.ComposerVSSelectionListService;
import inetsoft.web.composer.vs.objects.event.VSObjectEvent;
import inetsoft.web.composer.vs.objects.event.VSSetMeasuresEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The Composer selection layout service validates nothing, so the bounds the Angular client
 * enforces are re-implemented in {@link SelectionLayoutService}; these tests pin each one, and
 * that the call is delegated to the Composer service rather than writing the info directly.
 */
@Tag("core")
class SelectionLayoutServiceTest {
   // input validation, before any session is touched

   @Test
   void requiresAtLeastOneSetter() {
      Harness h = list(3, -1, -1, "Sum(Total)", true, true);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setLayout("tok", user(), "Filter1", null, null, null));
      assertTrue(e.getMessage().contains("at least one of"), e.getMessage());
      verifyNoInteractions(h.sessions);
   }

   @Test
   void rejectsColumnCountBelowOneOrFractional() {
      Harness h = list(3, -1, -1, "Sum(Total)", true, true);

      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> h.service.setLayout("tok", user(), "Filter1", 0, null, null))
         .getMessage().contains("'columnCount' must be an integer >= 1"));
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> h.service.setLayout("tok", user(), "Filter1", 2.5, null, null))
         .getMessage().contains("'columnCount' must be an integer"));
      verifyNoInteractions(h.sessions);
   }

   @Test
   void rejectsZeroAndOtherNegativeMeasureWidths() {
      Harness h = list(3, -1, -1, "Sum(Total)", true, true);

      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> h.service.setLayout("tok", user(), "Filter1", null, 0, null))
         .getMessage().contains("'measureTextWidth' must be -1 (auto) or a pixel width >= 1"));
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> h.service.setLayout("tok", user(), "Filter1", null, null, -5))
         .getMessage().contains("'measureBarWidth' must be -1 (auto) or a pixel width >= 1"));
      verifyNoInteractions(h.sessions);
   }

   // type gates

   @Test
   void rejectsColumnCountOnASelectionTree() throws Exception {
      Harness h = tree("Sum(Total)", true, true);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setLayout("tok", user(), "Tree1", 3, null, null));
      assertTrue(e.getMessage().contains("Selection List only"), e.getMessage());
      verifyNoInteractions(h.composer);
   }

   @Test
   void rejectsANonSelectionAssembly() {
      Harness h = harness(mock(TableVSAssembly.class));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setLayout("tok", user(), "Filter1", 2, null, null));
      assertTrue(e.getMessage().contains("not a Selection List or Selection Tree"), e.getMessage());
      verifyNoInteractions(h.composer);
   }

   // embedded

   @Test
   void refusesAWriteOnAnEmbeddedAssemblyBeforeMutating() throws Exception {
      Harness h = list(3, -1, -1, "Sum(Total)", true, true);
      doReturn(embeddedInfo()).when(h.assembly()).getVSAssemblyInfo();

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setLayout("tok", user(), "EV.List1", 2, null, null));
      assertTrue(e.getMessage().contains("EV.List1") && e.getMessage().contains("embedded") &&
                 e.getMessage().contains("viewsheet that owns it"), e.getMessage());
      verify(h.sessions, never()).mutate(anyString(), any(), any());
      verifyNoInteractions(h.composer);
   }

   @Test
   void refusesAColumnCountOnATreeBeforeMutating() throws Exception {
      Harness h = tree("Sum(Total)", true, true);

      assertThrows(IllegalArgumentException.class,
         () -> h.service.setLayout("tok", user(), "Tree1", 3, null, null));
      verify(h.sessions, never()).mutate(anyString(), any(), any());
   }

   // column count

   @Test
   void setsColumnCountThroughTheComposerService() throws Exception {
      Harness h = list(5, -1, -1, "Sum(Total)", true, true);

      Map<String, Object> out = h.service.setLayout("tok", user(), "Filter1", 3, null, null);

      ArgumentCaptor<VSObjectEvent> event = ArgumentCaptor.forClass(VSObjectEvent.class);
      verify(h.composer).changeColCount(eq("rt1"), eq(3), event.capture(), any(), any());
      assertEquals("Filter1", event.getValue().getName());
      verify(h.composer, never()).updateMeasureSize(any(), any(), any(), any());
      assertFalse(out.containsKey("warnings"));
   }

   @Test
   void clampsColumnCountToTheValueCountWithAWarning() throws Exception {
      Harness h = list(4, -1, -1, "Sum(Total)", true, true);

      Map<String, Object> out = h.service.setLayout("tok", user(), "Filter1", 9, null, null);

      verify(h.composer).changeColCount(eq("rt1"), eq(4), any(), any(), any());
      String warning = ((List<?>) out.get("warnings")).get(0).toString();
      assertTrue(warning.contains("9") && warning.contains("4"), warning);
   }

   @Test
   void doesNotClampWhenTheListHasNoValuesYetButSaysSo() throws Exception {
      Harness h = list(0, -1, -1, "Sum(Total)", true, true);

      Map<String, Object> out = h.service.setLayout("tok", user(), "Filter1", 3, null, null);

      verify(h.composer).changeColCount(eq("rt1"), eq(3), any(), any(), any());
      assertTrue(((List<?>) out.get("warnings")).get(0).toString().contains("no values"));
   }

   // measure widths

   @Test
   void mergesTheMissingMeasureWidthWithTheStoredValue() throws Exception {
      Harness h = list(3, 70, 120, "Sum(Total)", true, true);

      h.service.setLayout("tok", user(), "Filter1", null, null, 200);

      ArgumentCaptor<VSSetMeasuresEvent> event = ArgumentCaptor.forClass(VSSetMeasuresEvent.class);
      verify(h.composer).updateMeasureSize(eq("rt1"), event.capture(), any(), any());
      assertEquals(70, event.getValue().getTextWidth());
      assertEquals(200, event.getValue().getBarWidth());
      verify(h.composer, never()).changeColCount(any(), anyInt(), any(), any(), any());
   }

   @Test
   void keepsAutoWhenMergingAStoredAuto() throws Exception {
      Harness h = list(3, -1, -1, "Sum(Total)", true, true);

      h.service.setLayout("tok", user(), "Filter1", null, 80, null);

      ArgumentCaptor<VSSetMeasuresEvent> event = ArgumentCaptor.forClass(VSSetMeasuresEvent.class);
      verify(h.composer).updateMeasureSize(any(), event.capture(), any(), any());
      assertEquals(80, event.getValue().getTextWidth());
      assertEquals(-1, event.getValue().getBarWidth());
   }

   @Test
   void measureWidthsWorkOnATree() throws Exception {
      Harness h = tree("Sum(Total)", true, true);

      Map<String, Object> out = h.service.setLayout("tok", user(), "Tree1", null, 60, 90);

      verify(h.composer).updateMeasureSize(eq("rt1"), any(), any(), any());
      assertEquals("selection_tree", out.get("type"));
      assertFalse(out.containsKey("columnCount"));
   }

   @Test
   void warnsWhenNoMeasureIsBound() throws Exception {
      Harness h = list(3, -1, -1, null, false, false);

      Map<String, Object> out = h.service.setLayout("tok", user(), "Filter1", null, 80, null);

      verify(h.composer).updateMeasureSize(any(), any(), any(), any());
      assertTrue(((List<?>) out.get("warnings")).get(0).toString().contains("No measure"));
   }

   @Test
   void warnsWhenTheMatchingShowFlagIsOff() throws Exception {
      Harness h = list(3, -1, -1, "Sum(Total)", false, true);

      Map<String, Object> out = h.service.setLayout("tok", user(), "Filter1", null, 80, 100);

      List<?> warnings = (List<?>) out.get("warnings");
      assertEquals(1, warnings.size());
      assertTrue(warnings.get(0).toString().contains("showText"));
   }

   // read

   @Test
   void snapshotReportsTheStoredLayout() {
      Harness h = list(7, 70, 120, "Sum(Total)", true, false);

      Map<String, Object> out = h.service.snapshot("Filter1", h.assembly);

      assertEquals("selection_list", out.get("type"));
      assertEquals(2, out.get("columnCount"));
      assertEquals(7, out.get("valueCount"));
      assertEquals(70, out.get("measureTextWidth"));
      assertEquals(120, out.get("measureBarWidth"));
      assertEquals("Sum(Total)", out.get("measure"));
      assertEquals(true, out.get("showText"));
      assertEquals(false, out.get("showBar"));
   }

   // harness

   private Harness list(int valueCount, int text, int bar, String measure, boolean showText,
                        boolean showBar)
   {
      SelectionListVSAssemblyInfo info = mock(SelectionListVSAssemblyInfo.class);
      when(info.getColumnCount()).thenReturn(2);
      when(info.getMeasureSize()).thenReturn(text);
      when(info.getBarSize()).thenReturn(bar);
      when(info.getMeasure()).thenReturn(measure);
      when(info.isShowText()).thenReturn(showText);
      when(info.isShowBar()).thenReturn(showBar);

      SelectionListVSAssembly assembly = mock(SelectionListVSAssembly.class);
      doReturn(info).when(assembly).getVSAssemblyInfo();
      return harness(assembly, valueCount);
   }

   private Harness tree(String measure, boolean showText, boolean showBar) {
      SelectionTreeVSAssemblyInfo info = mock(SelectionTreeVSAssemblyInfo.class);
      when(info.getMeasureSize()).thenReturn(-1);
      when(info.getBarSize()).thenReturn(-1);
      when(info.getMeasure()).thenReturn(measure);
      when(info.isShowText()).thenReturn(showText);
      when(info.isShowBar()).thenReturn(showBar);

      SelectionTreeVSAssembly assembly = mock(SelectionTreeVSAssembly.class);
      doReturn(info).when(assembly).getVSAssemblyInfo();
      return harness(assembly, 0);
   }

   private Harness harness(VSAssembly assembly) {
      return harness(assembly, 0);
   }

   private Harness harness(VSAssembly assembly, int valueCount) {
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

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Read<?> read = invocation.getArgument(2);
            return read.run(rvs, "rt1", null);
         }).when(sessions).read(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      ComposerVSSelectionListService composer = mock(ComposerVSSelectionListService.class);
      return new Harness(new SelectionLayoutService(sessions, composer) {
         @Override
         int valueCount(VSAssembly a) {
            return valueCount;
         }
      }, sessions, composer,
                         assembly);
   }

   private static VSAssemblyInfo embeddedInfo() {
      VSAssemblyInfo info = mock(VSAssemblyInfo.class);
      when(info.isEmbedded()).thenReturn(true);
      return info;
   }

   private static Principal user() {
      return mock(Principal.class);
   }

   private record Harness(SelectionLayoutService service, ViewsheetSessionService sessions,
                          ComposerVSSelectionListService composer, VSAssembly assembly) {}
}
