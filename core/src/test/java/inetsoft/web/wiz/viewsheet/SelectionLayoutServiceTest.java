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
import inetsoft.uql.viewsheet.internal.CurrentSelectionVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TimeSliderVSAssemblyInfo;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.service.VSObjectService;
import inetsoft.web.viewsheet.service.VSSelectionContainerService;
import inetsoft.web.composer.vs.objects.controller.ComposerCurrentSelectionService;
import inetsoft.web.composer.vs.objects.controller.ComposerVSSelectionListService;
import inetsoft.web.composer.vs.objects.event.VSObjectEvent;
import inetsoft.web.composer.vs.objects.event.VSSetMeasuresEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.awt.Dimension;
import java.security.Principal;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
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
         () -> set(h, "Filter1", null, null, null));
      assertTrue(e.getMessage().contains("at least one of"), e.getMessage());
      verifyNoInteractions(h.sessions);
   }

   @Test
   void rejectsColumnCountBelowOneOrFractional() {
      Harness h = list(3, -1, -1, "Sum(Total)", true, true);

      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> set(h, "Filter1", 0, null, null))
         .getMessage().contains("'columnCount' must be an integer >= 1"));
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> set(h, "Filter1", 2.5, null, null))
         .getMessage().contains("'columnCount' must be an integer"));
      verifyNoInteractions(h.sessions);
   }

   @Test
   void rejectsZeroAndOtherNegativeMeasureWidths() {
      Harness h = list(3, -1, -1, "Sum(Total)", true, true);

      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> set(h, "Filter1", null, 0, null))
         .getMessage().contains("'measureTextWidth' must be -1 (auto) or a pixel width >= 1"));
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> set(h, "Filter1", null, null, -5))
         .getMessage().contains("'measureBarWidth' must be -1 (auto) or a pixel width >= 1"));
      verifyNoInteractions(h.sessions);
   }

   // type gates

   @Test
   void rejectsColumnCountOnASelectionTree() throws Exception {
      Harness h = tree("Sum(Total)", true, true);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> set(h, "Tree1", 3, null, null));
      assertTrue(e.getMessage().contains("Selection List only"), e.getMessage());
      verifyNoInteractions(h.composer);
   }

   @Test
   void rejectsANonSelectionAssembly() {
      Harness h = harness(mock(TableVSAssembly.class));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> set(h, "Filter1", 2, null, null));
      assertTrue(e.getMessage().contains("not a Selection List, Selection Tree or Range Slider"), e.getMessage());
      verifyNoInteractions(h.composer);
   }

   // embedded

   @Test
   void refusesAWriteOnAnEmbeddedAssemblyBeforeMutating() throws Exception {
      Harness h = list(3, -1, -1, "Sum(Total)", true, true);
      doReturn(embeddedInfo()).when(h.assembly()).getVSAssemblyInfo();

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> set(h, "EV.List1", 2, null, null));
      assertTrue(e.getMessage().contains("EV.List1") && e.getMessage().contains("embedded") &&
                 e.getMessage().contains("viewsheet that owns it"), e.getMessage());
      verify(h.sessions, never()).mutate(anyString(), any(), any());
      verifyNoInteractions(h.composer);
   }

   @Test
   void refusesAColumnCountOnATreeBeforeMutating() throws Exception {
      Harness h = tree("Sum(Total)", true, true);

      assertThrows(IllegalArgumentException.class,
         () -> set(h, "Tree1", 3, null, null));
      verify(h.sessions, never()).mutate(anyString(), any(), any());
   }

   // column count

   @Test
   void setsColumnCountThroughTheComposerService() throws Exception {
      Harness h = list(5, -1, -1, "Sum(Total)", true, true);

      Map<String, Object> out = set(h, "Filter1", 3, null, null);

      ArgumentCaptor<VSObjectEvent> event = ArgumentCaptor.forClass(VSObjectEvent.class);
      verify(h.composer).changeColCount(eq("rt1"), eq(3), event.capture(), any(), any());
      assertEquals("Filter1", event.getValue().getName());
      verify(h.composer, never()).updateMeasureSize(any(), any(), any(), any());
      assertFalse(out.containsKey("warnings"));
   }

   @Test
   void clampsColumnCountToTheValueCountWithAWarning() throws Exception {
      Harness h = list(4, -1, -1, "Sum(Total)", true, true);

      Map<String, Object> out = set(h, "Filter1", 9, null, null);

      verify(h.composer).changeColCount(eq("rt1"), eq(4), any(), any(), any());
      String warning = ((List<?>) out.get("warnings")).get(0).toString();
      assertTrue(warning.contains("9") && warning.contains("4"), warning);
   }

   @Test
   void doesNotClampWhenTheListHasNoValuesYetButSaysSo() throws Exception {
      Harness h = list(0, -1, -1, "Sum(Total)", true, true);

      Map<String, Object> out = set(h, "Filter1", 3, null, null);

      verify(h.composer).changeColCount(eq("rt1"), eq(3), any(), any(), any());
      assertTrue(((List<?>) out.get("warnings")).get(0).toString().contains("no values"));
   }

   // measure widths

   @Test
   void mergesTheMissingMeasureWidthWithTheStoredValue() throws Exception {
      Harness h = list(3, 70, 120, "Sum(Total)", true, true);

      set(h, "Filter1", null, null, 200);

      ArgumentCaptor<VSSetMeasuresEvent> event = ArgumentCaptor.forClass(VSSetMeasuresEvent.class);
      verify(h.composer).updateMeasureSize(eq("rt1"), event.capture(), any(), any());
      assertEquals(70, event.getValue().getTextWidth());
      assertEquals(200, event.getValue().getBarWidth());
      verify(h.composer, never()).changeColCount(any(), anyInt(), any(), any(), any());
   }

   @Test
   void keepsAutoWhenMergingAStoredAuto() throws Exception {
      Harness h = list(3, -1, -1, "Sum(Total)", true, true);

      set(h, "Filter1", null, 80, null);

      ArgumentCaptor<VSSetMeasuresEvent> event = ArgumentCaptor.forClass(VSSetMeasuresEvent.class);
      verify(h.composer).updateMeasureSize(any(), event.capture(), any(), any());
      assertEquals(80, event.getValue().getTextWidth());
      assertEquals(-1, event.getValue().getBarWidth());
   }

   @Test
   void measureWidthsWorkOnATree() throws Exception {
      Harness h = tree("Sum(Total)", true, true);

      Map<String, Object> out = set(h, "Tree1", null, 60, 90);

      verify(h.composer).updateMeasureSize(eq("rt1"), any(), any(), any());
      assertEquals("selection_tree", out.get("type"));
      assertFalse(out.containsKey("columnCount"));
   }

   @Test
   void warnsWhenNoMeasureIsBound() throws Exception {
      Harness h = list(3, -1, -1, null, false, false);

      Map<String, Object> out = set(h, "Filter1", null, 80, null);

      verify(h.composer).updateMeasureSize(any(), any(), any(), any());
      assertTrue(((List<?>) out.get("warnings")).get(0).toString().contains("No measure"));
   }

   @Test
   void warnsWhenTheMatchingShowFlagIsOff() throws Exception {
      Harness h = list(3, -1, -1, "Sum(Total)", false, true);

      Map<String, Object> out = set(h, "Filter1", null, 80, 100);

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

   // collapse / expand a filter inside a Selection Container

   @Test
   void collapsesAListInAContainerThroughTheContainerService() throws Exception {
      Box b = box(false, false);
      stubToggle(b);

      Map<String, Object> out = b.h.service.setLayout("tok", user(), "List1", null, null, null,
                                                      true, null, null, "uri");

      verify(b.h.containers).applySelection(any(RuntimeViewsheet.class), eq("List1"), eq(true),
                                            any(), eq("uri"));
      assertEquals(true, out.get("collapsed"));
      assertEquals(true, out.get("changed"));
      assertEquals("Box1", out.get("inContainer"));
      assertFalse(out.containsKey("alsoCollapsed"));
   }

   @Test
   void expandsACollapsedSliderAndReportsTheSiblingsTheViewerCollapsed() throws Exception {
      Box b = box(true, false);
      // List1 is expanded and the service collapses it to fit, as the Viewer does
      stubToggle(b);

      Map<String, Object> out = b.h.service.setLayout("tok", user(), "Slider1", null, null, null,
                                                      false, null, null, "");

      verify(b.h.containers).applySelection(any(RuntimeViewsheet.class), eq("Slider1"), eq(false),
                                            any(), eq(""));
      assertEquals(false, out.get("collapsed"));
      assertEquals(List.of("List1"), out.get("alsoCollapsed"));
   }

   @Test
   void doesNotCallTheContainerServiceWhenAlreadyAtTheTarget() throws Exception {
      Box b = box(false, false);

      Map<String, Object> collapsed = b.h.service.setLayout("tok", user(), "List2", null, null,
                                                            null, true, null, null, "");

      verifyNoInteractions(b.h.containers);
      assertEquals(false, collapsed.get("changed"));
      assertEquals(true, collapsed.get("collapsed"));

      b.h.service.setLayout("tok", user(), "List1", null, null, null, false, null, null, "");
      verifyNoInteractions(b.h.containers);
   }

   @Test
   void neverCallsTheContainerServiceForAFilterOutsideAContainer() throws Exception {
      SelectionListVSAssemblyInfo info = mock(SelectionListVSAssemblyInfo.class);
      SelectionListVSAssembly assembly = mock(SelectionListVSAssembly.class);
      doReturn(info).when(assembly).getVSAssemblyInfo();
      when(assembly.getContainer()).thenReturn(null);
      Harness h = harness(assembly, 3);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setLayout("tok", user(), "Filter1", null, null, null, true, null, null, ""));
      assertTrue(e.getMessage().contains("Filter1") &&
                 e.getMessage().contains("Selection Container") &&
                 e.getMessage().contains("showType"), e.getMessage());
      verifyNoInteractions(h.containers);
      verify(h.sessions, never()).mutate(anyString(), any(), any());
   }

   @Test
   void refusesCalendarAndTreeChildrenByName() throws Exception {
      Harness tree = tree("Sum(Total)", true, true);
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> tree.service.setLayout("tok", user(), "Tree1", null, null, null, true, null, null, ""));
      assertTrue(e.getMessage().contains("Tree1") && e.getMessage().contains("Selection Tree"),
                 e.getMessage());
      verifyNoInteractions(tree.containers);

      Harness cal = harness(mock(CalendarVSAssembly.class));
      e = assertThrows(IllegalArgumentException.class,
         () -> cal.service.setLayout("tok", user(), "Cal1", null, null, null, true, null, null, ""));
      assertTrue(e.getMessage().contains("Cal1") && e.getMessage().contains("Calendar"),
                 e.getMessage());
      verifyNoInteractions(cal.containers);
   }

   @Test
   void decidesSliderStateFromTheHeightNotTheHiddenFlag() throws Exception {
      // hidden=true but the height is not the title height: the service reads "expanded", so a
      // collapse request must call it
      Box b = box(false, false);
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) b.slider.getVSAssemblyInfo();
      when(info.isHidden()).thenReturn(true);
      sizes.put(info, new Dimension(200, 100));
      stubToggle(b);

      Map<String, Object> out = b.h.service.setLayout("tok", user(), "Slider1", null, null, null,
                                                      true, null, null, "");

      verify(b.h.containers).applySelection(any(RuntimeViewsheet.class), eq("Slider1"), eq(true),
                                            any(), anyString());
      assertEquals(true, out.get("collapsed"));
   }

   @Test
   void warnsWhenTheSliderHiddenFlagDisagreesAfterTheCall() throws Exception {
      Box b = box(false, false);
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) b.slider.getVSAssemblyInfo();
      doAnswer(inv -> {
         sizes.put(info, new Dimension(200, 20));
         return null;
      }).when(b.h.containers).applySelection(any(RuntimeViewsheet.class), eq("Slider1"),
                                             eq(true), any(), anyString());

      Map<String, Object> out = b.h.service.setLayout("tok", user(), "Slider1", null, null, null,
                                                      true, null, null, "");

      assertTrue(((List<?>) out.get("warnings")).get(0).toString().contains("hidden"));
   }

   @Test
   void failsLoudWhenTheStateIsNotTheRequestedOneAfterTheCall() throws Exception {
      Box b = box(false, false);

      Exception e = assertThrows(IllegalStateException.class,
         () -> b.h.service.setLayout("tok", user(), "List1", null, null, null, true, null, null, ""));
      assertTrue(e.getMessage().contains("List1") && e.getMessage().contains("still expanded"),
                 e.getMessage());
   }

   @Test
   void aSliderTakesNoColumnCountOrMeasureWidthOrExpandAll() throws Exception {
      Box b = box(false, false);

      for(Object[] bad : new Object[][] {{2, null, null, null}, {null, 5, null, null},
                                         {null, null, 5, null}, {null, null, null, true}})
      {
         Exception e = assertThrows(IllegalArgumentException.class,
            () -> b.h.service.setLayout("tok", user(), "Slider1", (Number) bad[0], (Number) bad[1],
                                        (Number) bad[2], null, (Boolean) bad[3], null, ""));
         assertTrue(e.getMessage().contains("Slider1"), e.getMessage());
      }

      verifyNoInteractions(b.h.containers);
      verifyNoInteractions(b.h.composer);
   }

   // expandAll on a tree

   @Test
   void setsExpandAllOnATreeThroughAClonedInfoAndPersistsIt() throws Exception {
      Harness h = tree("Sum(Total)", true, true);
      SelectionTreeVSAssemblyInfo clone = mock(SelectionTreeVSAssemblyInfo.class);
      SelectionTreeVSAssemblyInfo cur =
         (SelectionTreeVSAssemblyInfo) h.assembly().getVSAssemblyInfo();
      doReturn(clone).when(cur).clone();
      doAnswer(inv -> {
         when(clone.isExpandAll()).thenReturn(true);
         doReturn(clone).when(h.assembly()).getVSAssemblyInfo();
         return null;
      }).when(h.properties).editObjectProperty(any(), any(), anyString(), anyString(), anyString(),
                                              any(), any());

      Map<String, Object> out = h.service.setLayout("tok", user(), "Tree1", null, null, null,
                                                    null, true, null, "uri");

      verify(clone).setExpandAll(true);
      verify(h.properties).editObjectProperty(any(), eq(clone), eq("Tree1"), eq("Tree1"),
                                              eq("uri"), any(), any());
      assertEquals(true, out.get("expandAll"));
   }

   @Test
   void expandAllAlreadyAtTheTargetDoesNotWrite() throws Exception {
      Harness h = tree("Sum(Total)", true, true);
      when(((SelectionTreeVSAssemblyInfo) h.assembly().getVSAssemblyInfo()).isExpandAll())
         .thenReturn(true);

      Map<String, Object> out = h.service.setLayout("tok", user(), "Tree1", null, null, null,
                                                    null, true, null, "");

      verifyNoInteractions(h.properties);
      assertEquals(true, out.get("expandAll"));
   }

   @Test
   void expandAllFailsLoudWhenTheWriteDidNotStick() throws Exception {
      Harness h = tree("Sum(Total)", true, true);
      SelectionTreeVSAssemblyInfo clone = mock(SelectionTreeVSAssemblyInfo.class);
      SelectionTreeVSAssemblyInfo cur =
         (SelectionTreeVSAssemblyInfo) h.assembly().getVSAssemblyInfo();
      doReturn(clone).when(cur).clone();

      Exception e = assertThrows(IllegalStateException.class,
         () -> h.service.setLayout("tok", user(), "Tree1", null, null, null, null, true, null, ""));
      assertTrue(e.getMessage().contains("expandAll"), e.getMessage());
   }

   @Test
   void expandAllIsRefusedOnAListByName() throws Exception {
      Harness h = list(3, -1, -1, "Sum(Total)", true, true);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setLayout("tok", user(), "Filter1", null, null, null, null, true, null, ""));
      assertTrue(e.getMessage().contains("expandAll") && e.getMessage().contains("Selection Tree"),
                 e.getMessage());
      verifyNoInteractions(h.properties);
   }

   // read: container state and expandAll

   @Test
   void snapshotReportsContainerAndCollapsedForListAndSlider() {
      Box b = box(true, false);

      Map<String, Object> slider = b.h.service.snapshot("Slider1", b.slider);
      assertEquals("range_slider", slider.get("type"));
      assertEquals("Box1", slider.get("inContainer"));
      assertEquals(true, slider.get("collapsed"));
      assertFalse(slider.containsKey("columnCount"));

      Map<String, Object> list = b.h.service.snapshot("List1", b.list1);
      assertEquals("Box1", list.get("inContainer"));
      assertEquals(false, list.get("collapsed"));
   }

   @Test
   void snapshotReportsNullContainerForAStandaloneFilterAndExpandAllForATree() {
      Harness list = list(3, -1, -1, "Sum(Total)", true, true);
      Map<String, Object> out = list.service.snapshot("Filter1", list.assembly);
      assertTrue(out.containsKey("inContainer") && out.get("inContainer") == null);
      assertTrue(out.containsKey("collapsed") && out.get("collapsed") == null);

      Harness tree = tree("Sum(Total)", true, true);
      when(((SelectionTreeVSAssemblyInfo) tree.assembly().getVSAssemblyInfo()).isExpandAll())
         .thenReturn(true);
      Map<String, Object> t = tree.service.snapshot("Tree1", tree.assembly);
      assertEquals(true, t.get("expandAll"));
      assertFalse(t.containsKey("collapsed"));
   }

   // titleRatio: one value on the Selection Container (#80451)

   /** The Composer service stores the ratio, clamped to {@code cap}, as the real one would. */
   private void stubRatioStore(Box b, double cap) throws Exception {
      doAnswer(inv -> {
         double requested = inv.getArgument(1);
         when(b.containerInfo().getTitleRatio()).thenReturn(Math.min(cap, requested));
         return null;
      }).when(b.h.current()).setTitleRatio(anyString(), anyDouble(), any(), any(), any());
   }

   private Map<String, Object> setRatio(Harness h, String name, Number ratio) throws Exception {
      return h.service.setLayout("tok", user(), name, null, null, null, null, null, ratio, "");
   }

   @Test
   void rejectsAnOutOfRangeOrNonFiniteTitleRatioBeforeTouchingTheSession() {
      Box b = box(false, false);

      for(double bad : new double[] {0, 1, -0.2, Double.NaN, Double.POSITIVE_INFINITY}) {
         Exception e = assertThrows(IllegalArgumentException.class,
            () -> setRatio(b.h, "List1", bad));
         assertTrue(e.getMessage().contains("'titleRatio'") &&
                    e.getMessage().contains("between 0 and 1"), e.getMessage());
      }

      Exception percent = assertThrows(IllegalArgumentException.class,
         () -> setRatio(b.h, "List1", 40));
      assertTrue(percent.getMessage().contains("percent") &&
                 percent.getMessage().contains("0.3"), percent.getMessage());
      verifyNoInteractions(b.h.sessions);
   }

   @Test
   void titleRatioIsWrittenOnTheContainerWhenTheTargetIsAChild() throws Exception {
      Box b = box(false, false);
      stubRatioStore(b, 1);

      Map<String, Object> out = setRatio(b.h, "List1", 0.3);

      ArgumentCaptor<VSObjectEvent> event = ArgumentCaptor.forClass(VSObjectEvent.class);
      verify(b.h.current()).setTitleRatio(eq("rt1"), eq(0.3), event.capture(), any(), any());
      assertEquals("Box1", event.getValue().getName());
      assertEquals(0.3, (Double) out.get("titleRatio"), 1e-9);
      assertEquals(true, out.get("titleRatioSet"));
      assertEquals("Box1", out.get("titleRatioContainer"));
      List<?> affects = (List<?>) out.get("titleRatioAffects");
      assertEquals(List.of("List2", "Slider1", "the current-selection rows of 'Box1'"), affects);
      assertFalse(out.containsKey("warnings"));
      verifyNoInteractions(b.h.composer);
   }

   @Test
   void titleRatioOnTheContainerItselfIsAcceptedAndReadBack() throws Exception {
      Box b = box(false, false);
      stubRatioStore(b, 1);

      Map<String, Object> out = setRatio(b.h, "Box1", 0.6);

      assertEquals("selection_container", out.get("type"));
      assertEquals(0.6, (Double) out.get("titleRatio"), 1e-9);
      assertEquals(List.of("List1", "List2", "Slider1"), out.get("children"));
      assertEquals(List.of("List1", "List2", "Slider1",
                           "the current-selection rows of 'Box1'"), out.get("titleRatioAffects"));
   }

   @Test
   void titleRatioReportsAClampAsAWarningWithTheAppliedValue() throws Exception {
      Box b = box(false, false);
      stubRatioStore(b, 0.8);

      Map<String, Object> out = setRatio(b.h, "List1", 0.95);

      assertEquals(0.8, (Double) out.get("titleRatio"), 1e-9);
      String warning = ((List<?>) out.get("warnings")).get(0).toString();
      assertTrue(warning.contains("0.95") && warning.contains("0.8") &&
                 warning.contains("base layout") && warning.contains("50px"), warning);
   }

   @Test
   void titleRatioFailsLoudWhenTheServiceLeftItUnset() throws Exception {
      Box b = box(false, false);

      Exception e = assertThrows(IllegalStateException.class, () -> setRatio(b.h, "List1", 0.3));
      assertTrue(e.getMessage().contains("Box1") && e.getMessage().contains("not applied"),
                 e.getMessage());
   }

   @Test
   void everyTitleRatioRefusalHappensBeforeMutateSoItCostsNoCheckpoint() throws Exception {
      // a filter outside a container
      SelectionListVSAssemblyInfo info = mock(SelectionListVSAssemblyInfo.class);
      SelectionListVSAssembly loose = mock(SelectionListVSAssembly.class);
      doReturn(info).when(loose).getVSAssemblyInfo();
      Harness outside = harness(loose, 3);
      Exception e = assertThrows(IllegalArgumentException.class, () -> setRatio(outside, "F", 0.3));
      assertTrue(e.getMessage().contains("not inside a Selection Container"), e.getMessage());
      verify(outside.sessions, never()).mutate(anyString(), any(), any());

      // an embedded container
      Box embedded = box(false, false);
      when(embedded.containerInfo().isEmbedded()).thenReturn(true);
      e = assertThrows(IllegalArgumentException.class, () -> setRatio(embedded.h, "List1", 0.3));
      assertTrue(e.getMessage().contains("Box1") && e.getMessage().contains("embedded"),
                 e.getMessage());
      verify(embedded.h.sessions, never()).mutate(anyString(), any(), any());

      // the container with another setter
      Box mixed = box(false, false);
      e = assertThrows(IllegalArgumentException.class,
         () -> mixed.h.service.setLayout("tok", user(), "Box1", 2, null, null, null, null, 0.3, ""));
      assertTrue(e.getMessage().contains("only 'titleRatio'"), e.getMessage());
      verify(mixed.h.sessions, never()).mutate(anyString(), any(), any());
      verifyNoInteractions(mixed.h.current());
   }

   @Test
   void titleRatioOnATreeOrCalendarChildWarnsThatItDoesNotRenderTheSplit() throws Exception {
      for(boolean calendar : new boolean[] {false, true}) {
         Box b = box(false, false);
         VSAssembly child = calendar ? mock(CalendarVSAssembly.class)
                                     : mock(SelectionTreeVSAssembly.class);
         doReturn(mock(SelectionTreeVSAssemblyInfo.class)).when(child).getVSAssemblyInfo();
         when(child.getContainer()).thenReturn(b.container());
         b.h.byName().put("Child1", child);
         stubRatioStore(b, 1);

         Map<String, Object> out = setRatio(b.h, "Child1", 0.4);

         String warning = ((List<?>) out.get("warnings")).get(0).toString();
         assertTrue(warning.contains("does not show the title / selected-text split") &&
                    warning.contains(calendar ? "Calendar" : "Selection Tree") &&
                    warning.contains("Box1"), warning);
         assertEquals(calendar ? "calendar" : "selection_tree", out.get("type"));
         assertEquals(0.4, (Double) out.get("titleRatio"), 1e-9);
      }
   }

   @Test
   void aCalendarTakesOnlyTitleRatio() throws Exception {
      Box b = box(false, false);
      CalendarVSAssembly cal = mock(CalendarVSAssembly.class);
      doReturn(mock(SelectionTreeVSAssemblyInfo.class)).when(cal).getVSAssemblyInfo();
      when(cal.getContainer()).thenReturn(b.container());
      b.h.byName().put("Cal1", cal);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> b.h.service.setLayout("tok", user(), "Cal1", 2, null, null, null, null, 0.3, ""));
      assertTrue(e.getMessage().contains("Cal1") && e.getMessage().contains("only 'titleRatio'"),
                 e.getMessage());
      verify(b.h.sessions, never()).mutate(anyString(), any(), any());
   }

   @Test
   void snapshotReportsTheEffectiveRatioAndWhetherItIsStored() {
      Box b = box(false, false);

      Map<String, Object> unset = b.h.service.snapshot("List1", b.list1);
      assertEquals(0.5, (Double) unset.get("titleRatio"), 1e-9);
      assertEquals(false, unset.get("titleRatioSet"));

      when(b.containerInfo().getTitleRatio()).thenReturn(0.25);
      Map<String, Object> box = b.h.service.snapshot("Box1", b.container());
      assertEquals("selection_container", box.get("type"));
      assertEquals(0.25, (Double) box.get("titleRatio"), 1e-9);
      assertEquals(true, box.get("titleRatioSet"));
      assertEquals(0.25, (Double) b.h.service.snapshot("Slider1", b.slider)
                      .get("titleRatio"), 1e-9);

      Harness standalone = list(3, -1, -1, "Sum(Total)", true, true);
      assertFalse(standalone.service.snapshot("Filter1", standalone.assembly)
                     .containsKey("titleRatio"));
   }

   @Test
   void getLayoutAcceptsAContainer() throws Exception {
      Box b = box(false, false);

      Map<String, Object> out = b.h.service.getLayout("tok", user(), "Box1");

      assertEquals("selection_container", out.get("type"));
      assertEquals(0.5, (Double) out.get("titleRatio"), 1e-9);
   }

   // container harness

   private record Box(Harness h, TimeSliderVSAssembly slider, SelectionListVSAssembly list1,
                      SelectionListVSAssembly list2, CurrentSelectionVSAssembly container,
                      CurrentSelectionVSAssemblyInfo containerInfo) {}

   /**
    * Container "Box1" with an expanded List1 (collapsed when list1Collapsed), a collapsed List2
    * and Slider1 (collapsed when sliderCollapsed).
    */
   private Box box(boolean sliderCollapsed, boolean list1Collapsed) {
      CurrentSelectionVSAssembly container = mock(CurrentSelectionVSAssembly.class);
      when(container.getAbsoluteName()).thenReturn("Box1");
      when(container.getAbsoluteAssemblies())
         .thenReturn(new String[] {"List1", "List2", "Slider1"});
      CurrentSelectionVSAssemblyInfo containerInfo = mock(CurrentSelectionVSAssemblyInfo.class);
      when(containerInfo.getTitleRatio()).thenReturn(Double.NaN);
      doReturn(containerInfo).when(container).getVSAssemblyInfo();

      SelectionListVSAssembly list1 = containedList(container, list1Collapsed);
      SelectionListVSAssembly list2 = containedList(container, true);

      TimeSliderVSAssemblyInfo sliderInfo = mock(TimeSliderVSAssemblyInfo.class);
      when(sliderInfo.getTitleHeight()).thenReturn(20);
      when(sliderInfo.isHidden()).thenReturn(sliderCollapsed);
      TimeSliderVSAssembly slider = mock(TimeSliderVSAssembly.class);
      doReturn(sliderInfo).when(slider).getVSAssemblyInfo();
      when(slider.getContainer()).thenReturn(container);
      sizes.put(sliderInfo, new Dimension(200, sliderCollapsed ? 20 : 100));

      Map<String, VSAssembly> byName = new LinkedHashMap<>();
      byName.put("Box1", container);
      byName.put("List1", list1);
      byName.put("List2", list2);
      byName.put("Slider1", slider);
      return new Box(harness(byName, slider, 0), slider, list1, list2, container, containerInfo);
   }

   private SelectionListVSAssembly containedList(CurrentSelectionVSAssembly container,
                                                 boolean collapsed)
   {
      SelectionListVSAssemblyInfo info = mock(SelectionListVSAssemblyInfo.class);
      when(info.getShowTypeValue()).thenReturn(collapsed ? SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE
                                                         : SelectionVSAssemblyInfo.LIST_SHOW_TYPE);
      SelectionListVSAssembly list = mock(SelectionListVSAssembly.class);
      doReturn(info).when(list).getVSAssemblyInfo();
      when(list.getContainer()).thenReturn(container);
      return list;
   }

   /**
    * Makes the mocked container service behave like the real one: toggles the target and, on an
    * expand, collapses the expanded list siblings.
    */
   private void stubToggle(Box b) {
      try {
         doAnswer(inv -> {
            String name = inv.getArgument(1);
            boolean hide = inv.getArgument(2);
            VSAssembly target = b.h.byName().get(name);

            if(target instanceof TimeSliderVSAssembly) {
               TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) target.getVSAssemblyInfo();
               boolean now = sizes.get(info).height == info.getTitleHeight();
               sizes.put(info, new Dimension(200, now ? 100 : 20));
               when(info.isHidden()).thenReturn(!now);
            }
            else {
               SelectionListVSAssemblyInfo info =
                  (SelectionListVSAssemblyInfo) target.getVSAssemblyInfo();
               boolean dropdown =
                  info.getShowTypeValue() == SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE;
               when(info.getShowTypeValue()).thenReturn(
                  dropdown ? SelectionVSAssemblyInfo.LIST_SHOW_TYPE
                           : SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
            }

            if(!hide) {
               for(String sibling : new String[] {"List1", "List2"}) {
                  VSAssembly s = b.h.byName().get(sibling);
                  SelectionListVSAssemblyInfo si =
                     (SelectionListVSAssemblyInfo) s.getVSAssemblyInfo();

                  if(!sibling.equals(name) &&
                     si.getShowTypeValue() == SelectionVSAssemblyInfo.LIST_SHOW_TYPE)
                  {
                     when(si.getShowTypeValue())
                        .thenReturn(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
                  }
               }
            }

            return null;
         }).when(b.h.containers).applySelection(any(RuntimeViewsheet.class), anyString(),
                                                anyBoolean(), any(), anyString());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
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
      return harness(new LinkedHashMap<>(), assembly, valueCount);
   }

   private Harness harness(Map<String, VSAssembly> byName, VSAssembly fallback, int valueCount) {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenAnswer(inv -> {
         VSAssembly named = byName.get(inv.<String>getArgument(0));
         return named != null ? named : fallback;
      });

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
      VSSelectionContainerService containers = mock(VSSelectionContainerService.class);
      VSObjectPropertyService properties = mock(VSObjectPropertyService.class);
      VSObjectService objects = mock(VSObjectService.class);
      when(objects.getSize(any())).thenAnswer(inv -> {
         Dimension d = sizes.get(inv.getArgument(0));
         return d == null ? new Dimension(200, 100) : d;
      });
      ComposerCurrentSelectionService current = mock(ComposerCurrentSelectionService.class);
      return new Harness(new SelectionLayoutService(sessions, composer, containers, properties,
                                                    objects, current)
      {
         @Override
         int valueCount(VSAssembly a) {
            return valueCount;
         }
      }, sessions, composer, fallback, containers, properties, byName, current);
   }

   private final Map<Object, Dimension> sizes = new IdentityHashMap<>();

   private Map<String, Object> set(Harness h, String name, Number cols, Number text, Number bar)
      throws Exception
   {
      return h.service.setLayout("tok", user(), name, cols, text, bar, null, null, null, "");
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
                          ComposerVSSelectionListService composer, VSAssembly assembly,
                          VSSelectionContainerService containers,
                          VSObjectPropertyService properties, Map<String, VSAssembly> byName,
                          ComposerCurrentSelectionService current) {}
}
