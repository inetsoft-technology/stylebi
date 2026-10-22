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
import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.dialog.*;
import inetsoft.web.viewsheet.service.VSInputService;
import inetsoft.web.viewsheet.service.VSOutputService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bugs #79194 (calendar min/max moves the selected dates) and #79198 (enabled:false stops a
 * filter) through {@code AssemblyPropertyService.set}, with real assemblies and dialog services
 * that apply the write the way the real ones do. The two bugs' tests live in separate nested
 * classes so either detector can be run and judged alone.
 *
 * <p>What this cannot prove: that the dates have already moved when {@code writeModel} returns.
 * The real move happens in {@code VSObjectPropertyService.editObjectProperty -> box.processChange
 * -> processSelections -> CalendarVSAQuery.refreshSelectionValue -> CalendarVSAssembly.setRange},
 * which these stubs replace with a direct {@code setRange(null)} (a simulation). That ordering is
 * inferred from the call chain and is proven only by the live vfl-060 run.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class FilterEffectGuardTest {
   private static final String LIST_ENABLED =
      "selectionGeneralPaneModel.generalPropPaneModel.enabled";
   private static final Principal USER = () -> "tester";

   // ── wording and gates, no context needed ────────────────────────────────────────────────

   @Test
   void literalFalseIsTheOnlyEnabledClaim() {
      assertTrue(FilterEffectGuard.isLiteralFalse("false"));
      assertTrue(FilterEffectGuard.isLiteralFalse("FALSE"));
      assertTrue(FilterEffectGuard.isLiteralFalse(" False "));
      assertTrue(FilterEffectGuard.isLiteralFalse(Boolean.FALSE));
      assertFalse(FilterEffectGuard.isLiteralFalse("true"));
      assertFalse(FilterEffectGuard.isLiteralFalse(Boolean.TRUE));
      assertFalse(FilterEffectGuard.isLiteralFalse("$(Toggle)"));
      assertFalse(FilterEffectGuard.isLiteralFalse("script:false"));
      assertFalse(FilterEffectGuard.isLiteralFalse(null));
   }

   @Test
   void enabledNotesNameTheFiltersAndSayTheSelectionIsKept() {
      String single = FilterEffectGuard.enabledNote("CategoryFilter", List.of("CategoryFilter"),
                                                    false);
      assertTrue(single.startsWith("Filter note: "), single);
      assertTrue(single.contains("'CategoryFilter' no longer filters"), single);
      assertTrue(single.contains("(kept)"), single);
      assertFalse(single.toLowerCase().contains("widen"), single);

      String container = FilterEffectGuard.enabledNote("Cont", List.of("A", "B"), true);
      assertTrue(container.contains("'Cont' is disabled"), container);
      assertTrue(container.contains("child filters A, B no longer filter"), container);
      assertTrue(container.contains("selections are kept"), container);

      String one = FilterEffectGuard.enabledNote("Cont", List.of("A"), true);
      assertTrue(one.contains("child filter A no longer filter"), one);
   }

   @Test
   void calendarNoteIsCauseNeutralUnlessABoundWasTheOnlyThingWritten() {
      String[] before = { "d2023-4-15", "d2023-5-30" };
      String[] after = { "d2023-5-1", "d2023-5-30" };

      String bound = FilterEffectGuard.calendarNote("Cal", before, after, true, false, true);
      assertEquals("Filter note: the selected dates of calendar 'Cal' moved to fit its min/max " +
                   "range: 2023-05-15, 2023-06-30 became 2023-06-01, 2023-06-30. The calendar " +
                   "now filters on the new dates; set_calendar_dates reselects a different range.",
                   bound);

      String mode = FilterEffectGuard.calendarNote("Cal", before, new String[0], true, true, true);
      assertFalse(mode.contains("min/max range"), mode);
      assertTrue(mode.contains("changed: 2023-05-15, 2023-06-30 became (none)"), mode);

      String disabled = FilterEffectGuard.calendarNote("Cal", before, after, true, false, false);
      assertFalse(disabled.contains("now filters"), disabled);
   }

   // ── #79194 ───────────────────────────────────────────────────────────────────────────────

   @Nested
   class Bug79194CalendarDates {
      @Test
      void minOverDatesDiscloseEveryOldDateAndTheNewOnes() throws Exception {
         Stack s = stack();
         CalendarVSAssembly cal = s.calendar("DateFilter", "d2023-4-15", "d2023-5-30");

         List<String> warnings = s.set("DateFilter", Map.of("min", "2023-06-01"));

         assertEquals(1, warnings.size(), warnings.toString());
         assertTrue(warnings.get(0).startsWith("Filter note: "), warnings.get(0));
         assertTrue(warnings.get(0).contains("2023-05-15, 2023-06-30 became 2023-06-01, " +
                                             "2023-06-30"), warnings.get(0));
         assertArrayEquals(new String[] { "d2023-5-1", "d2023-5-30" }, cal.getDates());
      }

      @Test
      void maxOnlyAndBothBoundsAreDisclosed() throws Exception {
         Stack s = stack();
         s.calendar("DateFilter", "d2023-2-1", "d2023-4-30");

         List<String> max = s.set("DateFilter", Map.of("max", "2023-03-31"));
         assertEquals(1, max.size(), max.toString());
         assertTrue(max.get(0).contains("2023-03-01"), max.get(0));

         s = stack();
         s.calendar("DateFilter", "d2023-2-13", "d2023-2-31");
         Map<String, Object> both = new LinkedHashMap<>();
         both.put("min", "2023-04-01");
         both.put("max", "2023-06-30");
         List<String> warnings = s.set("DateFilter", both);
         assertEquals(1, warnings.size(), warnings.toString());
         assertTrue(warnings.get(0).contains("2023-03-13"), warnings.get(0));
      }

      @Test
      void aBoundThatMovesNothingStaysSilent() throws Exception {
         Stack s = stack();
         CalendarVSAssembly cal = s.calendar("DateFilter", "d2023-4-15", "d2023-5-30");

         List<String> warnings = s.set("DateFilter", Map.of("min", "2023-04-01"));

         assertEquals(List.of(), warnings);
         assertArrayEquals(new String[] { "d2023-4-15", "d2023-5-30" }, cal.getDates());
      }

      @Test
      void clearingABoundAndANoSelectionCalendarStaySilent() throws Exception {
         Stack s = stack();
         s.calendar("DateFilter", "d2023-4-15", "d2023-5-30");
         Map<String, Object> clear = new LinkedHashMap<>();
         clear.put("min", "");
         clear.put("max", "");
         assertEquals(List.of(), s.set("DateFilter", clear));

         s = stack();
         s.calendar("DateFilter");
         assertEquals(List.of(), s.set("DateFilter", Map.of("min", "2023-06-01")));
      }

      @Test
      void aViewModeWriteThatClearsTheDatesDoesNotBlameTheBounds() throws Exception {
         Stack s = stack();
         s.calendar("DateFilter", "d2023-4-15", "d2023-5-30");

         List<String> warnings = s.set("DateFilter", Map.of("viewMode", "single"));

         assertEquals(1, warnings.size(), warnings.toString());
         assertFalse(warnings.get(0).contains("min/max"), warnings.get(0));
         assertTrue(warnings.get(0).contains("became (none)"), warnings.get(0));
         // a calendar write never reaches the enabled detector
         assertFalse(warnings.get(0).contains("no longer filter"), warnings.get(0));
      }

      @Test
      void aDynamicBoundThatMovesNothingIsSilent() throws Exception {
         Stack s = stack();
         s.calendar("DateFilter", "d2023-4-15", "d2023-5-30");

         assertEquals(List.of(), s.set("DateFilter", Map.of("min", "$(MinVar)")));
      }

      @Test
      void aNonCalendarWriteReadsNothingAndStaysSilent() throws Exception {
         Stack s = stack();
         s.list("L", true);

         assertEquals(List.of(), s.set("L", Map.of("title", "x")));
      }
   }

   // ── #79198 ───────────────────────────────────────────────────────────────────────────────

   @Nested
   class Bug79198EnabledStopsFiltering {
      @Test
      void disablingASelectedListSaysItNoLongerFilters() throws Exception {
         Stack s = stack();
         SelectionListVSAssembly list = s.list("CategoryFilter", true);

         List<String> warnings = s.set("CategoryFilter", Map.of("enabled", false));

         assertEquals(1, warnings.size(), warnings.toString());
         assertTrue(warnings.get(0).startsWith("Filter note: "), warnings.get(0));
         assertTrue(warnings.get(0).contains("'CategoryFilter' no longer filters"),
                    warnings.get(0));
         // nothing cleared, nothing refused: the selection is still stored
         assertTrue(list.getSelectionList().getSelectionValue(0).isSelected());
      }

      @Test
      void literalFalseInAnyCaseIsDisclosedAndEnabledTrueIsNot() throws Exception {
         Stack s = stack();
         s.list("L", true);
         assertEquals(1, s.set("L", Map.of("enabled", "FALSE")).size());

         // re-enabling and re-disabling an already-disabled filter say nothing
         assertEquals(List.of(), s.set("L", Map.of("enabled", true)));
         s.list("L2", true);
         s.vs.getAssembly("L2").getVSAssemblyInfo().setEnabledValue("false");
         assertEquals(List.of(), s.set("L2", Map.of("enabled", false)));
      }

      @Test
      void noSelectionIsSilent() throws Exception {
         Stack s = stack();
         s.list("L", false);

         assertEquals(List.of(), s.set("L", Map.of("enabled", false)));
      }

      @Test
      void aDynamicEnabledIsNeverClaimedAsAStop() throws Exception {
         Stack s = stack();
         s.list("L", true);

         assertEquals(List.of(), s.set("L", Map.of("enabled", "$(Toggle)")));
      }

      @Test
      void enabledTogetherWithOtherKeysStillDiscloses() throws Exception {
         Stack s = stack();
         s.list("L", true);
         Map<String, Object> patch = new LinkedHashMap<>();
         patch.put("title", "Category");
         patch.put("enabled", false);

         assertEquals(1, s.set("L", patch).size());
      }

      @Test
      void disablingAContainerNamesOnlyTheChildFiltersThatLostASelection() throws Exception {
         Stack s = stack();
         s.list("A", true);
         s.list("B", true);
         s.list("C", true);
         s.list("D", false);
         s.vs.getAssembly("C").getVSAssemblyInfo().setEnabledValue("false");
         s.container("CS", "A", "B", "C", "D");

         List<String> warnings = s.setContainer("CS", Map.of("enabled", false));

         assertEquals(1, warnings.size(), warnings.toString());
         assertTrue(warnings.get(0).contains("'CS' is disabled"), warnings.get(0));
         assertTrue(warnings.get(0).contains("child filters A, B no longer filter"),
                    warnings.get(0));
         assertFalse(warnings.get(0).matches(".*\\bC\\b.*"), warnings.get(0));
      }

      @Test
      void enablingAContainerOrDisablingOneWithNoSelectionsIsSilent() throws Exception {
         Stack s = stack();
         s.list("A", true);
         s.container("CS", "A");
         s.vs.getAssembly("CS").getVSAssemblyInfo().setEnabledValue("false");
         assertEquals(List.of(), s.setContainer("CS", Map.of("enabled", true)));

         s = stack();
         s.list("A", false);
         s.container("CS", "A");
         assertEquals(List.of(), s.setContainer("CS", Map.of("enabled", false)));
      }

      @Test
      void aTabOverANestedContainerReachesTheListUnderIt() throws Exception {
         Stack s = stack();
         s.list("D", true);
         s.container("CS4", "D");
         TabVSAssembly tab = new TabVSAssembly(s.vs, "Tab4");
         s.vs.addAssembly(tab, false);
         tab.setAssemblies(new String[] { "CS4" });

         List<String> warnings = s.setTab("Tab4", Map.of("enabled", false));

         assertEquals(1, warnings.size(), warnings.toString());
         assertTrue(warnings.get(0).contains("'Tab4' is disabled"), warnings.get(0));
         assertTrue(warnings.get(0).contains("child filter D no longer filter"), warnings.get(0));
      }

      @Test
      void aCalendarWithDatesIsAFilterToo() throws Exception {
         Stack s = stack();
         s.calendar("Cal", "d2023-3-1", "d2023-3-5");

         List<String> warnings = s.set("Cal", Map.of("enabled", false));

         assertEquals(1, warnings.size(), warnings.toString());
         assertTrue(warnings.get(0).contains("'Cal' no longer filters"), warnings.get(0));
      }

      @Test
      void aContainerResolvingToItselfDoesNotLoop() {
         Viewsheet vs = mock(Viewsheet.class);
         CurrentSelectionVSAssembly cs = mock(CurrentSelectionVSAssembly.class);
         when(vs.getAssembly(anyString())).thenReturn(cs);
         when(cs.getAssemblies()).thenReturn(new String[] { "CS" });
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(vs);

         assertNull(FilterEffectGuard.snapshot(rvs, "selectioncontainer", "CS",
                                               List.of("a.generalPropPaneModel.enabled")));
      }

      @Test
      void aGuardFailureIsSilenceNeverAnError() {
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenThrow(new IllegalStateException("boom"));

         assertNull(FilterEffectGuard.snapshot(rvs, "calendar", "Cal",
                                               List.of("a.generalPropPaneModel.enabled")));
         assertEquals(List.of(), FilterEffectGuard.apply(
            new FilterEffectGuard.Snapshot(new String[] { "d2023-1-1" }, List.of("A"),
                                           "a.generalPropPaneModel.enabled"),
            rvs, "Cal", new Object(), List.of()));
      }
   }

   // ── harness ──────────────────────────────────────────────────────────────────────────────

   private static Stack stack() {
      return new Stack();
   }

   /**
    * A real viewsheet with real selection assemblies, and dialog services that read and write the
    * assembly's own state: {@code enabled} goes into the assembly info, and the calendar's
    * min/max write is followed by {@code setRange(null)} to stand in for the selection refresh
    * {@code editObjectProperty} runs.
    */
   private static final class Stack {
      final Viewsheet vs = new Viewsheet();
      final SelectionListPropertyDialogService listService =
         mock(SelectionListPropertyDialogService.class);
      final CalendarPropertyDialogService calendarService =
         mock(CalendarPropertyDialogService.class);
      final SelectionContainerPropertyDialogService containerService =
         mock(SelectionContainerPropertyDialogService.class);
      final TabPropertyDialogService tabService = mock(TabPropertyDialogService.class);
      final RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      final AssemblyPropertyService service;

      Stack() {
         when(rvs.getViewsheet()).thenReturn(vs);
         when(rvs.getID()).thenReturn("rt1");
         ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

         try {
            when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
            when(sessions.mutate(anyString(), any(Principal.class), any())).thenAnswer(inv ->
               CapturingCommandDispatcher.withCapturingDispatcher(inv.getArgument(1), d -> {
                  inv.<ViewsheetSessionService.Mutation>getArgument(2).run(rvs, "rt1", d);
                  return d.getWarnings();
               }));
            wireList();
            wireCalendar();
            wireContainer();
            wireTab();
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }

         service = new AssemblyPropertyService(
            sessions, mock(GaugePropertyDialogService.class),
            mock(ImagePropertyDialogService.class), mock(TextPropertyDialogService.class),
            mock(ChartPropertyDialogService.class), mock(TableViewPropertyDialogService.class),
            mock(CrosstabPropertyDialogService.class), listService,
            mock(SelectionTreePropertyDialogService.class), mock(VSInputService.class),
            mock(RangeSliderPropertyDialogService.class), calendarService, tabService,
            mock(CalcTablePropertyDialogService.class),
            mock(GroupContainerPropertyDialogService.class),
            mock(LinePropertyDialogService.class), mock(OvalPropertyDialogService.class),
            mock(RectanglePropertyDialogService.class), containerService,
            mock(SubmitPropertyDialogService.class), mock(VSOutputService.class),
            mock(VSColumnHandler.class), mock(DataOutputService.class));
      }

      List<String> set(String name, Map<String, Object> patch) throws Exception {
         return service.set("tok", USER, name, patch, "");
      }

      List<String> setContainer(String name, Map<String, Object> patch) throws Exception {
         return set(name, patch);
      }

      List<String> setTab(String name, Map<String, Object> patch) throws Exception {
         return set(name, patch);
      }

      SelectionListVSAssembly list(String name, boolean selected) {
         SelectionListVSAssembly list = new SelectionListVSAssembly(vs, name);
         list.setDataRef(new ColumnRef(new AttributeRef("T", "Category")));
         SelectionList values = new SelectionList();
         SelectionValue value = new SelectionValue("Games", "Games");
         value.setSelected(selected);
         values.addSelectionValue(value);
         list.setSelectionList(values);
         list.setStateSelectionList(values);
         vs.addAssembly(list, false);
         return list;
      }

      CalendarVSAssembly calendar(String name, String... dates) {
         CalendarVSAssembly cal = new CalendarVSAssembly(vs, name);
         ((CalendarVSAssemblyInfo) cal.getVSAssemblyInfo()).setViewModeValue(
            CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE);
         ((CalendarVSAssemblyInfo) cal.getVSAssemblyInfo()).setDates(dates);
         vs.addAssembly(cal, false);
         return cal;
      }

      void container(String name, String... children) {
         CurrentSelectionVSAssembly cs = new CurrentSelectionVSAssembly(vs, name);
         vs.addAssembly(cs, false);
         cs.setAssemblies(children);
      }

      private void wireList() throws Exception {
         doAnswer(inv -> new SelectionListPropertyDialogModel()).when(listService)
            .getSelectionListPropertyModel(any(), any(), any());
         doAnswer(inv -> {
            SelectionListPropertyDialogModel model = inv.getArgument(2);
            applyEnabled(inv.getArgument(1),
                         model.getSelectionGeneralPaneModel().getGeneralPropPaneModel());
            return null;
         }).when(listService).setSelectionListPropertyModel(any(), any(), any(), any(), any(),
                                                           any());
      }

      private void wireContainer() throws Exception {
         doAnswer(inv -> new SelectionContainerPropertyDialogModel()).when(containerService)
            .getSelectionContainerPropertyModel(any(), any(), any());
         doAnswer(inv -> {
            SelectionContainerPropertyDialogModel model = inv.getArgument(2);
            applyEnabled(inv.getArgument(1),
                         model.getSelectionContainerGeneralPaneModel().getGeneralPropPaneModel());
            return null;
         }).when(containerService)
            .setSelectionContainerPropertyModel(any(), any(), any(), any(), any(), any());
      }

      private void wireTab() throws Exception {
         doAnswer(inv -> new TabPropertyDialogModel()).when(tabService)
            .getTabPropertyDialogModel(any(), any(), any());
         doAnswer(inv -> {
            TabPropertyDialogModel model = inv.getArgument(2);
            applyEnabled(inv.getArgument(1),
                         model.getTabGeneralPaneModel().getGeneralPropPaneModel());
            return null;
         }).when(tabService).setTabPropertyDialogModel(any(), any(), any(), any(), any(), any());
      }

      private void wireCalendar() throws Exception {
         doAnswer(inv -> {
            String name = inv.getArgument(1);
            CalendarVSAssemblyInfo info =
               (CalendarVSAssemblyInfo) vs.getAssembly(name).getVSAssemblyInfo();
            CalendarPropertyDialogModel model = new CalendarPropertyDialogModel();
            model.getCalendarAdvancedPaneModel().setViewMode(info.getViewModeValue());
            return model;
         }).when(calendarService).getCalendarPropertyModel(any(), any(), any());
         doAnswer(inv -> {
            String name = inv.getArgument(1);
            CalendarPropertyDialogModel model = inv.getArgument(2);
            CalendarVSAssembly cal = (CalendarVSAssembly) vs.getAssembly(name);
            CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) cal.getVSAssemblyInfo();
            CalendarAdvancedPaneModel advanced = model.getCalendarAdvancedPaneModel();
            info.setMinValue(bound(advanced.getMin()));
            info.setMaxValue(bound(advanced.getMax()));

            if(info.getViewModeValue() != advanced.getViewMode()) {
               info.setViewModeValue(advanced.getViewMode());
               info.setDates(new String[0]);
            }

            applyEnabled(name, model.getCalendarGeneralPaneModel().getGeneralPropPaneModel());
            // stands in for processChange -> processSelections -> refreshSelectionValue
            cal.setRange(null);
            return null;
         }).when(calendarService)
            .setCalendarPropertyModel(any(), any(), any(), any(), any(), any());
      }

      private static String bound(DynamicValueModel bound) {
         String value = bound == null ? null : bound.convertToValue();
         return value == null || value.isBlank() ? null : value;
      }

      private void applyEnabled(String name, GeneralPropPaneModel general) {
         if(general != null && general.getEnabled() != null) {
            vs.getAssembly(name).getVSAssemblyInfo().setEnabledValue(general.getEnabled());
         }
      }
   }
}
