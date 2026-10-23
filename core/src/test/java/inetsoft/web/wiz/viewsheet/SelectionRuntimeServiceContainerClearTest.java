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
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.viewsheet.controller.VSCalendarService;
import inetsoft.web.viewsheet.service.VSSelectionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * clear_selection on a Selection Container delegates to the native
 * {@code VSSelectionService.unselectAll} (the container's own Unselect All) and reports each
 * filter it touched. {@code unselectAll} is simulated: it clears the container's children and,
 * when Show Current Selections is on, the out-of-container filters, the way the native one does.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionRuntimeServiceContainerClearTest {
   /** A bound selection list over the values a, b, c with {@code selected} flagged. */
   private static SelectionListVSAssembly filter(String name, boolean single, String search,
                                                 String... selected)
   {
      SelectionListVSAssembly assembly =
         SelectionRuntimeServiceTest.list(XConstants.SORT_ASC, single, search);
      when(assembly.getName()).thenReturn(name);
      when(assembly.getAbsoluteName()).thenReturn(name);
      SelectionList list = new SelectionList();
      List<String> chosen = Arrays.asList(selected);

      for(String value : List.of("a", "b", "c")) {
         SelectionValue v = new SelectionValue(value, value);
         v.setSelected(chosen.contains(value));
         list.addSelectionValue(v);
      }

      when(assembly.getSelectionList()).thenReturn(list);
      return assembly;
   }

   private static List<String> selected(SelectionVSAssembly assembly) {
      List<String> values = new ArrayList<>();

      for(SelectionValue v : ((SelectionListVSAssembly) assembly).getSelectionList()
         .getSelectionValues())
      {
         if(v.isSelected()) {
            values.add(v.getValue());
         }
      }

      return values;
   }

   private static void unselect(SelectionVSAssembly assembly) {
      for(SelectionValue v : ((SelectionListVSAssembly) assembly).getSelectionList()
         .getSelectionValues())
      {
         v.setSelected(false);
      }
   }

   /** What a test needs to arrange around one container. */
   private static final class Fixture {
      final Map<String, VSAssembly> byName = new LinkedHashMap<>();
      final CurrentSelectionVSAssembly container = mock(CurrentSelectionVSAssembly.class);
      final CurrentSelectionVSAssemblyInfo info = mock(CurrentSelectionVSAssemblyInfo.class);
      final Viewsheet vs = mock(Viewsheet.class);
      final VSSelectionService selections = mock(VSSelectionService.class);
      final VSCalendarService calendars = mock(VSCalendarService.class);
      final SelectionRuntimeService service;
      /** Native effect of unselectAll on one filter; the default clears it. */
      Consumer<SelectionVSAssembly> nativeClear = SelectionRuntimeServiceContainerClearTest::unselect;
      String[] outNames = new String[0];

      Fixture(boolean showCurrent, String... children) throws Exception {
         when(container.getName()).thenReturn("CurrentSelection1");
         when(container.getAbsoluteName()).thenReturn("CurrentSelection1");
         when(container.getViewsheet()).thenReturn(vs);
         when(container.getAssemblies()).thenReturn(children);
         doReturn(info).when(container).getVSAssemblyInfo();
         when(info.isShowCurrentSelection()).thenReturn(showCurrent);
         when(info.getOutSelectionNames()).thenAnswer(inv -> outNames);
         byName.put("CurrentSelection1", container);
         when(vs.getAssembly(anyString())).thenAnswer(inv -> byName.get(inv.<String>getArgument(0)));
         when(vs.getAssemblies(true)).thenAnswer(inv -> byName.values().toArray(new Assembly[0]));

         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(vs);
         ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
         doAnswer(inv -> {
            ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());

         doAnswer(inv -> {
            boolean show = info.isShowCurrentSelection();

            for(String name : container.getAssemblies()) {
               nativeClear.accept((SelectionVSAssembly) byName.get(name));
            }

            if(show) {
               for(String name : info.getOutSelectionNames()) {
                  nativeClear.accept((SelectionVSAssembly) byName.get(name));
               }
            }

            return null;
         }).when(selections).unselectAll(anyString(), anyString(), any(Principal.class), any(),
                                         anyString());
         service = new SelectionRuntimeService(sessions, selections, calendars);
      }

      Fixture add(SelectionVSAssembly assembly) {
         byName.put(assembly.getName(), assembly);
         return this;
      }

      Map<String, Object> clear() throws Exception {
         return service.clearSelection("tok", SelectionRuntimeServiceTest.principal(),
                                       "CurrentSelection1", "");
      }
   }

   @SuppressWarnings("unchecked")
   private static List<Map<String, Object>> entries(Map<String, Object> result) {
      return (List<Map<String, Object>>) result.get("clearedFilters");
   }

   private static Map<String, Object> entry(Map<String, Object> result, String name) {
      return entries(result).stream().filter(e -> name.equals(e.get("assembly"))).findFirst()
         .orElseThrow(() -> new AssertionError(name + " not in " + entries(result)));
   }

   /** Children and an out-of-container filter, Show Current Selections on. */
   @Test
   void clearsChildrenAndOutsideFiltersAndRefreshesTheOutListFirst() throws Exception {
      Fixture f = new Fixture(true, "SL1", "SL2");
      SelectionListVSAssembly sl1 = filter("SL1", false, null, "a");
      SelectionListVSAssembly sl2 = filter("SL2", false, null, "b");
      SelectionListVSAssembly sl3 = filter("SL3", false, null, "c");
      f.add(sl1).add(sl2).add(sl3);
      f.outNames = new String[]{ "SL3" };

      Map<String, Object> result = f.clear();

      assertEquals("a selection container", result.get("type"));
      assertEquals(true, result.get("showCurrentSelection"));
      assertEquals(2, result.get("childCount"));
      assertEquals(1, result.get("outsideCount"));
      assertEquals(3, result.get("clearedCount"));
      assertEquals(List.of(), result.get("stillFiltering"));
      assertEquals(List.of("SL1", "SL2", "SL3"),
                   entries(result).stream().map(e -> e.get("assembly")).toList());
      assertEquals("child", entry(result, "SL1").get("scope"));
      assertEquals("child", entry(result, "SL2").get("scope"));
      assertEquals("outside", entry(result, "SL3").get("scope"));

      for(String name : List.of("SL1", "SL2", "SL3")) {
         assertEquals(1, entry(result, name).get("clearedCount"), name);
         assertEquals(0, entry(result, name).get("selectedTotal"), name);
      }

      assertEquals(List.of(), selected(sl3));
      assertFalse(result.containsKey("otherSelectionsChanged"));

      InOrder order = inOrder(f.container, f.selections);
      order.verify(f.container).updateOutSelection();
      order.verify(f.selections).unselectAll(eq("rt1"), eq("CurrentSelection1"),
                                            any(Principal.class), any(), eq(""));
      verifyNoMoreInteractions(f.selections);
   }

   /** Show Current Selections off: an outside filter keeps its selection and is not listed. */
   @Test
   void leavesOutsideFiltersAloneWhenShowCurrentSelectionIsOff() throws Exception {
      Fixture f = new Fixture(false, "SL1");
      SelectionListVSAssembly sl1 = filter("SL1", false, null, "a");
      SelectionListVSAssembly sl3 = filter("SL3", false, null, "c");
      f.add(sl1).add(sl3);
      f.outNames = new String[]{ "SL3" };

      Map<String, Object> result = f.clear();

      assertEquals(1, result.get("clearedCount"));
      assertEquals(0, result.get("outsideCount"));
      assertEquals(List.of("SL1"), entries(result).stream().map(e -> e.get("assembly")).toList());
      assertEquals(List.of("c"), selected(sl3));
      verify(f.container, never()).updateOutSelection();
   }

   /** A filter added after Show Current Selections was switched on is in the refreshed list. */
   @Test
   void clearsAnOutsideFilterAddedAfterTheOutListWasLastBuilt() throws Exception {
      Fixture f = new Fixture(true, "SL1");
      SelectionListVSAssembly sl1 = filter("SL1", false, null, "a");
      SelectionListVSAssembly late = filter("SL4", false, null, "b");
      f.add(sl1).add(late);
      doAnswer(inv -> {
         f.outNames = new String[]{ "SL4" };
         return null;
      }).when(f.container).updateOutSelection();

      Map<String, Object> result = f.clear();

      assertEquals("outside", entry(result, "SL4").get("scope"));
      assertEquals(List.of(), selected(late));
   }

   /** A search that hides the selection keeps it; the entry says the filter still filters. */
   @Test
   void reportsASearchRetainedSelectionAsStillFiltering() throws Exception {
      Fixture f = new Fixture(false, "SL1", "SL2");
      SelectionListVSAssembly sl1 = filter("SL1", false, "zzz", "a", "b");
      SelectionListVSAssembly sl2 = filter("SL2", false, null, "c");
      f.add(sl1).add(sl2);
      f.nativeClear = a -> {
         if(a != sl1) {
            unselect(a);
         }
      };

      Map<String, Object> result = f.clear();

      Map<String, Object> e1 = entry(result, "SL1");
      assertEquals(0, e1.get("clearedCount"));
      assertEquals(2, e1.get("selectedTotal"));
      assertEquals("zzz", e1.get("scopedBySearch"));
      assertEquals(List.of(List.of("a"), List.of("b")), e1.get("scopedBySearchRetained"));
      assertEquals(1, result.get("clearedCount"));
      assertEquals(List.of("SL1"), result.get("stillFiltering"));
   }

   /** A single-select filter cannot stay empty: the native refresh re-selects a value. */
   @Test
   void reportsASingleSelectFilterThatWasReselected() throws Exception {
      Fixture f = new Fixture(false, "SL1");
      SelectionListVSAssembly sl1 = filter("SL1", true, null, "b");
      f.add(sl1);
      f.nativeClear = a -> {
         unselect(a);
         ((SelectionListVSAssembly) a).getSelectionList().getSelectionValues()[0].setSelected(true);
      };

      Map<String, Object> result = f.clear();

      Map<String, Object> e1 = entry(result, "SL1");
      assertEquals(1, e1.get("clearedCount"));
      assertEquals(1, e1.get("selectedTotal"));
      assertEquals(List.of(List.of("a")), e1.get("reselectedBySingleSelect"));
      assertEquals(List.of("SL1"), result.get("stillFiltering"));
   }

   @Test
   void anEmptyContainerClearsNothing() throws Exception {
      Fixture f = new Fixture(false);

      Map<String, Object> result = f.clear();

      assertEquals(0, result.get("childCount"));
      assertEquals(0, result.get("outsideCount"));
      assertEquals(0, result.get("clearedCount"));
      assertEquals(List.of(), entries(result));
   }

   /** A container with no children still clears its outside filters when they are shown. */
   @Test
   void aChildlessContainerStillClearsItsOutsideFilters() throws Exception {
      Fixture f = new Fixture(true);
      SelectionListVSAssembly sl3 = filter("SL3", false, null, "c");
      f.add(sl3);
      f.outNames = new String[]{ "SL3" };

      Map<String, Object> result = f.clear();

      assertEquals(0, result.get("childCount"));
      assertEquals(1, result.get("outsideCount"));
      assertEquals(1, result.get("clearedCount"));
      assertEquals(List.of(), selected(sl3));
   }

   /** Calendars and range sliders are cleared natively and reported in their own units. */
   @Test
   void reportsACalendarAndARangeSliderChild() throws Exception {
      Fixture f = new Fixture(false, "Cal1", "Slider1");
      CalendarVSAssembly cal = mock(CalendarVSAssembly.class);
      CalendarVSAssemblyInfo calInfo = mock(CalendarVSAssemblyInfo.class);
      String[][] dates = { { "2026-01-01", "2026-01-02" } };
      when(calInfo.getDates()).thenAnswer(inv -> dates[0]);
      when(cal.getName()).thenReturn("Cal1");
      when(cal.getAbsoluteName()).thenReturn("Cal1");
      doReturn(calInfo).when(cal).getVSAssemblyInfo();

      TimeSliderVSAssembly slider = mock(TimeSliderVSAssembly.class);
      SelectionList buckets = new SelectionList();

      for(int i = 0; i < 4; i++) {
         SelectionValue v = new SelectionValue("b" + i, "b" + i);
         v.setSelected(i < 2);
         buckets.addSelectionValue(v);
      }

      when(slider.getName()).thenReturn("Slider1");
      when(slider.getAbsoluteName()).thenReturn("Slider1");
      when(slider.getSelectionList()).thenReturn(buckets);
      f.byName.put("Cal1", cal);
      f.byName.put("Slider1", slider);
      f.nativeClear = a -> {
         if(a == cal) {
            dates[0] = new String[0];
         }
         else {
            for(SelectionValue v : buckets.getSelectionValues()) {
               v.setSelected(true);
            }
         }
      };

      Map<String, Object> result = f.clear();

      assertEquals(2, entry(result, "Cal1").get("clearedCount"));
      assertEquals(0, entry(result, "Cal1").get("selectedTotal"));
      Map<String, Object> s = entry(result, "Slider1");
      assertEquals(2, s.get("clearedCount"));
      assertEquals(0, s.get("selectedTotal"));
      assertEquals(true, s.get("fullRange"));
      assertEquals(4, s.get("selectedBuckets"));
      assertEquals(4, s.get("totalBuckets"));
      assertEquals(List.of(), result.get("stillFiltering"));
   }

   /**
    * An outside filter on the same column as a child, Show Current Selections off: a native clear
    * that moves it through Association shows up as changed instead of being hidden as a cluster
    * neighbor of the cleared child.
    */
   @Test
   void reportsAnUnlistedFilterThatTheClearMovedAsChanged() throws Exception {
      Fixture f = new Fixture(false, "SL1");
      SelectionListVSAssembly sl1 = filter("SL1", false, null, "a");
      SelectionListVSAssembly sibling = filter("SL9", false, null, "a");
      f.add(sl1).add(sibling);
      f.nativeClear = a -> {
         unselect(a);
         unselect(sibling);
      };

      Map<String, Object> result = f.clear();

      assertEquals(List.of("SL1"), entries(result).stream().map(e -> e.get("assembly")).toList());
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> changed =
         (List<Map<String, Object>>) result.get("otherSelectionsChanged");
      assertEquals(1, changed.size());
      assertEquals("SL9", changed.get(0).get("assembly"));
   }

   /** A container in an embedded viewsheet reports and skips by absolute name only. */
   @Test
   void keysTheReportOnAbsoluteNames() throws Exception {
      Fixture f = new Fixture(false, "SL1");
      SelectionListVSAssembly sl1 = filter("SL1", false, null, "a");
      when(sl1.getAbsoluteName()).thenReturn("Emb.SL1");
      f.add(sl1);

      Map<String, Object> result = f.clear();

      assertEquals(List.of("Emb.SL1"),
                   entries(result).stream().map(e -> e.get("assembly")).toList());
      assertFalse(result.containsKey("otherSelectionsChanged"));
   }

   @Test
   void refusesAnUnboundChildNamingItAndClearsNothing() throws Exception {
      Fixture f = new Fixture(false, "SL1", "SL2");
      SelectionListVSAssembly sl1 = filter("SL1", false, null, "a");
      SelectionListVSAssembly sl2 = filter("SL2", false, null, "b");
      when(sl2.getDataRef()).thenReturn(null);
      f.add(sl1).add(sl2);

      Exception e = assertThrows(IllegalArgumentException.class, f::clear);

      assertTrue(e.getMessage().contains("SL2"), e.getMessage());
      assertTrue(e.getMessage().contains("no column bound"), e.getMessage());
      verifyNoInteractions(f.selections);
      assertEquals(List.of("a"), selected(sl1));
   }

   /** The leaf-only endpoints keep refusing a container, and say where its clear lives. */
   @Test
   void otherSelectionToolsStillRefuseAContainerAndPointAtClearSelection() throws Exception {
      Fixture f = new Fixture(false, "SL1");
      f.add(filter("SL1", false, null, "a"));

      Exception e = assertThrows(IllegalArgumentException.class, () -> f.service.setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "CurrentSelection1", null, null,
         "specific", null, null, ""));

      assertTrue(e.getMessage().contains("not a selection assembly"), e.getMessage());
      assertTrue(e.getMessage().contains("clear_selection"), e.getMessage());
   }

   @Test
   void anUnknownNameStillFailsLoudly() throws Exception {
      Fixture f = new Fixture(false);

      Exception e = assertThrows(IllegalArgumentException.class, () -> f.service.clearSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Nope", ""));

      assertTrue(e.getMessage().contains("Unknown assembly 'Nope'"), e.getMessage());
   }
}
