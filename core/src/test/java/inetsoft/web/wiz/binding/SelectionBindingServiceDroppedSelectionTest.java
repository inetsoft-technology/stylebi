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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.viewsheet.CalendarVSAssembly;
import inetsoft.uql.viewsheet.CompositeSelectionValue;
import inetsoft.uql.viewsheet.SelectionList;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;
import inetsoft.uql.viewsheet.SelectionValue;
import inetsoft.uql.viewsheet.TimeSliderVSAssembly;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import inetsoft.web.composer.model.vs.CalendarPropertyDialogModel;
import inetsoft.web.composer.model.vs.RangeSliderPropertyDialogModel;
import inetsoft.web.composer.model.vs.SelectionListPropertyDialogModel;
import inetsoft.web.composer.model.vs.SelectionTreePropertyDialogModel;
import inetsoft.web.composer.vs.dialog.CalendarPropertyDialogService;
import inetsoft.web.composer.vs.dialog.RangeSliderPropertyDialogService;
import inetsoft.web.composer.vs.dialog.SelectionListPropertyDialogService;
import inetsoft.web.composer.vs.dialog.SelectionTreePropertyDialogService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;

import static inetsoft.web.wiz.binding.SelectionBindingServiceTest.harness;
import static inetsoft.web.wiz.binding.SelectionBindingServiceTest.principal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * bug-79880: a selection list rebind resets the selection upstream; the result must name the
 * selected values that were discarded. A SelectionList needs a Spring context to be constructed,
 * which is why this lives apart from SelectionBindingServiceTest.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionBindingServiceDroppedSelectionTest {
   private static SelectionList list(String... selected) {
      SelectionList list = new SelectionList();

      for(String name : List.of("East", "West", "North")) {
         SelectionValue value = new SelectionValue(name, name);
         value.setSelected(List.of(selected).contains(name));
         list.addSelectionValue(value);
      }

      return list;
   }

   /** A list whose applied state is {@code before} on the first read and {@code after} on later ones. */
   private static SelectionListVSAssembly assembly(SelectionList before, SelectionList after) {
      SelectionListVSAssembly assembly = mock(SelectionListVSAssembly.class);
      when(assembly.getStateSelectionList()).thenReturn(before, after);
      return assembly;
   }

   private static Map<String, Object> rebind(SelectionListVSAssembly assembly, String column)
      throws Exception
   {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      model.getSelectionListPaneModel().setSelectedTable("ORDERS");
      SelectionListPropertyDialogService listService =
         mock(SelectionListPropertyDialogService.class);
      when(listService.getSelectionListPropertyModel(eq("rt1"), eq("List1"), any()))
         .thenReturn(model);

      return harness(assembly, listService, null, null, null)
         .setSource("tok", principal(), "List1", "ORDERS", List.of(column), null, null, null,
                   null, null, false, "");
   }

   @Test
   void disclosesTheSelectedValuesTheRebindReset() throws Exception {
      Map<String, Object> result = rebind(assembly(list("East", "West"), null), "CITY");

      assertEquals(List.of("East", "West"), result.get("droppedSelection"));
   }

   @Test
   void readsTheStateListWhenTheDisplayListIsNotRenderedYet() throws Exception {
      SelectionListVSAssembly assembly = assembly(list("East"), null);
      when(assembly.getSelectionList()).thenReturn(null);

      assertEquals(List.of("East"), rebind(assembly, "CITY").get("droppedSelection"));
   }

   @Test
   void staysSilentWhenNothingWasSelected() throws Exception {
      assertFalse(rebind(assembly(list(), null), "CITY").containsKey("droppedSelection"));
   }

   @Test
   void staysSilentWhenTheSameColumnKeepsTheSelection() throws Exception {
      Map<String, Object> result = rebind(assembly(list("East"), list("East")), "STATE");

      assertFalse(result.containsKey("droppedSelection"));
   }

   @Test
   void disclosesOnlyTheValuesThatNoLongerStaySelected() throws Exception {
      Map<String, Object> result = rebind(assembly(list("East", "West"), list("West")), "CITY");

      assertEquals(List.of("East"), result.get("droppedSelection"));
   }

   // ── selection tree (bug-80132) ──────────────────────────────────────────────

   /** A State > City tree; each state is selected when named, each city when "State/City" is. */
   private static SelectionList tree(String... selected) {
      List<String> picks = List.of(selected);
      SelectionList states = new SelectionList();

      for(String state : List.of("East", "West")) {
         CompositeSelectionValue stateValue = new CompositeSelectionValue(state, state);
         stateValue.setLevel(0);
         stateValue.setSelected(picks.contains(state));
         SelectionList cities = new SelectionList();

         for(String city : List.of("NY", "MA")) {
            SelectionValue cityValue = new SelectionValue(city, city);
            cityValue.setLevel(1);
            cityValue.setSelected(picks.contains(state + "/" + city));
            cities.addSelectionValue(cityValue);
         }

         stateValue.setSelectionList(cities);
         states.addSelectionValue(stateValue);
      }

      return states;
   }

   private static SelectionTreeVSAssembly treeAssembly(SelectionList before, SelectionList after) {
      SelectionTreeVSAssembly assembly = mock(SelectionTreeVSAssembly.class);
      when(assembly.getStateSelectionList()).thenReturn(before, after);
      return assembly;
   }

   private static Map<String, Object> rebindTree(SelectionTreeVSAssembly assembly, boolean idMode)
      throws Exception
   {
      SelectionTreePropertyDialogService treeService =
         mock(SelectionTreePropertyDialogService.class);
      when(treeService.getSelectionTreePropertyModel(eq("rt1"), eq("Tree1"), any()))
         .thenReturn(new SelectionTreePropertyDialogModel());

      return idMode
         ? harness(assembly, null, treeService, null, null)
            .setSource("tok", principal(), "Tree1", "ORDERS", null, null, null, "CITY", "STATE",
                      "ORDER_DATE", false, "")
         : harness(assembly, null, treeService, null, null)
            .setSource("tok", principal(), "Tree1", "ORDERS", List.of("STATE", "CITY"), null,
                      null, null, null, null, false, "");
   }

   @Test
   void disclosesTheTreeNodesAColumnModeRebindReset() throws Exception {
      Map<String, Object> result = rebindTree(
         treeAssembly(tree("East", "East/NY", "West/MA"), null), false);

      assertEquals(List.of("East", "East > NY", "West > MA"), result.get("droppedSelection"));
   }

   @Test
   void disclosesTheTreeNodesAnIdModeRebindReset() throws Exception {
      Map<String, Object> result = rebindTree(treeAssembly(tree("West/NY"), null), true);

      assertEquals(List.of("West > NY"), result.get("droppedSelection"));
   }

   @Test
   void staysSilentWhenNoTreeNodeWasSelected() throws Exception {
      assertFalse(rebindTree(treeAssembly(tree(), null), false).containsKey("droppedSelection"));
      assertFalse(rebindTree(treeAssembly(tree(), null), true).containsKey("droppedSelection"));
   }

   @Test
   void staysSilentWhenTheTreeKeepsItsSelection() throws Exception {
      Map<String, Object> result =
         rebindTree(treeAssembly(tree("East/NY"), tree("East/NY")), false);

      assertFalse(result.containsKey("droppedSelection"));
   }

   @Test
   void disclosesOnlyTheTreeNodesThatNoLongerStaySelected() throws Exception {
      Map<String, Object> result =
         rebindTree(treeAssembly(tree("East/NY", "West/MA"), tree("West/MA")), true);

      assertEquals(List.of("East > NY"), result.get("droppedSelection"));
   }

   /**
    * A realistic ID-hierarchy state tree: node ids (1..4) differ from labels, two leaves under
    * different parents share the label "X", and every selected node is also kept flat at the
    * root, as SelectionTreeStateProcessor.selectIDValue leaves it.
    */
   private static SelectionList idTree(String labelSuffix, String... selectedIds) {
      List<String> picks = List.of(selectedIds);
      SelectionList roots = new SelectionList();
      SelectionList echoes = new SelectionList();

      for(String[] parent : new String[][] {{"1", "East", "2"}, {"3", "West", "4"}}) {
         CompositeSelectionValue parentValue =
            new CompositeSelectionValue(parent[1] + labelSuffix, parent[0]);
         parentValue.setLevel(0);
         SelectionValue leaf = new SelectionValue("X" + labelSuffix, parent[2]);
         leaf.setLevel(1);
         leaf.setSelected(picks.contains(parent[2]));
         SelectionList children = new SelectionList();
         children.addSelectionValue(leaf);
         parentValue.setSelectionList(children);
         roots.addSelectionValue(parentValue);

         if(leaf.isSelected()) {
            SelectionValue echo = new SelectionValue("X" + labelSuffix, parent[2]);
            echo.setSelected(true);
            echoes.addSelectionValue(echo);
         }
      }

      for(SelectionValue echo : echoes.getSelectionValues()) {
         roots.addSelectionValue(echo);
      }

      return roots;
   }

   private static SelectionTreeVSAssembly idTreeAssembly(SelectionList before,
                                                        SelectionList after)
   {
      SelectionTreeVSAssembly assembly = treeAssembly(before, after);
      when(assembly.getMode()).thenReturn(SelectionTreeVSAssemblyInfo.ID);
      return assembly;
   }

   @Test
   void idModeReportsLabelsAndEachSelectedNodeOnce() throws Exception {
      Map<String, Object> result =
         rebindTree(idTreeAssembly(idTree("", "2", "4"), null), true);

      assertEquals(List.of("East > X", "West > X"), result.get("droppedSelection"));
   }

   @Test
   void idModeDiffsByNodeIdNotByLabel() throws Exception {
      Map<String, Object> result = rebindTree(
         idTreeAssembly(idTree("", "2", "4"), idTree("", "2")), true);

      assertEquals(List.of("West > X"), result.get("droppedSelection"));
   }

   @Test
   void idModeStaysSilentWhenOnlyTheLabelsChange() throws Exception {
      Map<String, Object> result = rebindTree(
         idTreeAssembly(idTree("", "2", "4"), idTree("-new", "2", "4")), true);

      assertFalse(result.containsKey("droppedSelection"));
   }

   // ── range slider / calendar (bug-80495) ─────────────────────────────────────

   /** Month buckets 2024-01..2024-05; {@code from..to} (inclusive indexes) are selected. */
   private static SelectionList months(int from, int to) {
      SelectionList list = new SelectionList();
      String[] names = { "2024-01", "2024-02", "2024-03", "2024-04", "2024-05" };

      for(int i = 0; i < names.length; i++) {
         SelectionValue value = new SelectionValue(names[i], names[i]);
         value.setSelected(i >= from && i <= to);
         list.addSelectionValue(value);
      }

      return list;
   }

   /**
    * A real slider holding {@code held}; the mocked dialog service replaces its buckets with
    * {@code afterApply} the way a time-info change does (ORDER_DATE to AMOUNT, a different column type).
    */
   private static Map<String, Object> rebindSlider(SelectionList held, SelectionList afterApply,
                                                   String column) throws Exception
   {
      TimeSliderVSAssembly slider = new TimeSliderVSAssembly();
      slider.setSelectionList(held);
      RangeSliderPropertyDialogService sliderService =
         mock(RangeSliderPropertyDialogService.class);
      when(sliderService.getRangeSliderPropertyModel(eq("rt1"), eq("Slider1"), any()))
         .thenReturn(new RangeSliderPropertyDialogModel());
      doAnswer(invocation -> {
         slider.setSelectionList(afterApply);
         return null;
      }).when(sliderService).setRangeSliderPropertyModel(
         eq("rt1"), eq("Slider1"), any(), any(), any(), any());

      return harness(slider, null, null, sliderService, null)
         .setSource("tok", principal(), "Slider1", "ORDERS", List.of(column), null, null, null,
                   null, null, false, "");
   }

   @Test
   void disclosesTheRangeAColumnChangeResets() throws Exception {
      Map<String, Object> result = rebindSlider(months(0, 3), months(0, 4), "AMOUNT");
      assertEquals(List.of("2024-01 .. 2024-04"), result.get("droppedSelection"));
   }

   @Test
   void staysSilentWhenTheSliderKeepsItsHeldRange() throws Exception {
      Map<String, Object> result = rebindSlider(months(1, 3), months(1, 3), "ORDER_DATE");

      assertFalse(result.containsKey("droppedSelection"));
   }

   @Test
   void staysSilentWhenTheHeldRangeIsTheFullRange() throws Exception {
      Map<String, Object> result = rebindSlider(months(0, 4), months(1, 2), "AMOUNT");

      assertFalse(result.containsKey("droppedSelection"));
   }

   @Test
   void aCalendarRebindThatKeepsItsModeReportsNoDrop() throws Exception {
      CalendarVSAssembly calendar = mock(CalendarVSAssembly.class);
      CalendarPropertyDialogService calendarService = mock(CalendarPropertyDialogService.class);
      when(calendarService.getCalendarPropertyModel(eq("rt1"), eq("Cal1"), any()))
         .thenReturn(new CalendarPropertyDialogModel());

      Map<String, Object> result = harness(calendar, null, null, null, calendarService)
         .setSource("tok", principal(), "Cal1", "ORDERS", List.of("ORDER_DATE"), null, null,
                   null, null, null, false, "");

      assertFalse(result.containsKey("droppedSelection"));
   }
}
