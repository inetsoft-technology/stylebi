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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.CompositeSelectionValue;
import inetsoft.uql.viewsheet.SelectionList;
import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;
import inetsoft.uql.viewsheet.SelectionValue;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import inetsoft.web.viewsheet.event.ApplySelectionListEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * bug-78795: set_selection on a Region/State/City tree whose only single-select level is State,
 * end to end through setSelection with a real SelectionList / CompositeSelectionValue domain (the
 * plain mock harness in {@link SelectionRuntimeServiceTest} cannot build one). Covers the wiring
 * the pure-helper tests cannot: the replace-diff runs on a per-level tree, the
 * {@code keepAutoExtended} wrap at its call site, and the {@code prefixPathsCollapsed} result.
 * {@code VSSelectionService} is mocked, so only the events setSelection sends are asserted.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionRuntimeServicePerLevelReplaceTest {
   private static SelectionValue leaf(String name, int level, boolean selected) {
      SelectionValue value = new SelectionValue(name, name);
      value.setLevel(level);
      value.setSelected(selected);
      return value;
   }

   private static CompositeSelectionValue node(String name, int level, boolean selected,
                                               SelectionValue... children)
   {
      CompositeSelectionValue composite = new CompositeSelectionValue(name, name);
      composite.setLevel(level);
      composite.setSelected(selected);
      SelectionList list = new SelectionList();

      for(SelectionValue child : children) {
         list.addSelectionValue(child);
      }

      composite.setSelectionList(list);
      return composite;
   }

   /**
    * USA East{NY{Buffalo}, MA{Boston}} and USA West{CA{LA}}. NY and CA are selected (with their
    * Regions); Buffalo too when {@code buffalo}.
    */
   private static SelectionList geo(boolean buffalo) {
      SelectionList domain = new SelectionList();
      domain.addSelectionValue(node("USA East", 0, true,
                                    node("NY", 1, true, leaf("Buffalo", 2, buffalo)),
                                    node("MA", 1, false, leaf("Boston", 2, false))));
      domain.addSelectionValue(node("USA West", 0, true,
                                    node("CA", 1, true, leaf("LA", 2, false))));
      return domain;
   }

   /** Single-select on, only State (level 1) stored as single, levels edited one by one. */
   private static SelectionRuntimeServiceTest.Harness stateSingle(SelectionList domain) {
      SelectionTreeVSAssembly assembly =
         SelectionRuntimeServiceTest.tree(XConstants.SORT_ASC, true);
      SelectionTreeVSAssemblyInfo info = assembly.getSelectionTreeInfo();
      when(info.getStoredSingleSelectionLevels()).thenReturn(List.of(1));
      when(info.isMixedSingleSelection()).thenReturn(true);
      when(info.getLevelNames()).thenReturn(List.of("Region", "State", "City"));
      when(assembly.isIDMode()).thenReturn(false);
      when(assembly.getSelectionList()).thenReturn(domain);
      return SelectionRuntimeServiceTest.harness(assembly);
   }

   private static Map<String, Object> set(SelectionRuntimeServiceTest.Harness h,
                                          List<List<String>> values) throws Exception
   {
      return h.service().setSelection("tok", SelectionRuntimeServiceTest.principal(), "T", values,
                                      null, null, null, null, "");
   }

   /** Every apply event setSelection sent, in order. */
   private static List<ApplySelectionListEvent> sent(SelectionRuntimeServiceTest.Harness h)
      throws Exception
   {
      ArgumentCaptor<ApplySelectionListEvent> events =
         ArgumentCaptor.forClass(ApplySelectionListEvent.class);
      verify(h.selections(), atLeastOnce()).applySelection(anyString(), anyString(),
                                                           events.capture(), any(Principal.class),
                                                           any(), anyString());
      return events.getAllValues();
   }

   private static List<List<String>> paths(ApplySelectionListEvent event, boolean selected) {
      return event.getValues().stream()
         .filter(v -> v.isSelected() == selected)
         .map(v -> Arrays.asList(v.getValue()))
         .toList();
   }

   /** Whether some target is {@code path} or one of its ancestors (a deselect clears below it). */
   private static boolean covers(List<List<String>> targets, List<String> path) {
      return targets.stream().anyMatch(t -> t.size() <= path.size() &&
                                            t.equals(path.subList(0, t.size())));
   }

   /**
    * (a) A replace on a per-level tree deselects what the request leaves out: West's branch and
    * NY go, not only NY's State sibling (all doApplySelection's per-level clear would do).
    */
   @Test
   void replaceOnAStateSingleTreeDeselectsWestAndNy() throws Exception {
      SelectionRuntimeServiceTest.Harness h = stateSingle(geo(false));

      Map<String, Object> result = set(h, List.of(List.of("USA East", "MA")));

      List<ApplySelectionListEvent> events = sent(h);
      assertEquals(2, events.size(), "a deselect, then the apply");
      List<List<String>> deselected = paths(events.get(0), false);
      assertTrue(covers(deselected, List.of("USA West", "CA")), "West's branch: " + deselected);
      assertTrue(covers(deselected, List.of("USA East", "NY")), "NY: " + deselected);
      assertEquals(List.of(List.of("USA East", "MA")), paths(events.get(1), true));
      assertEquals(1, result.get("valuesSelected"));
   }

   /**
    * (b) [[USA East]] stops above the State level, so the runtime extends it into the selected
    * State: the replace must leave East/NY/Buffalo alone (keepAutoExtended) and only drop West.
    */
   @Test
   void replaceWithARegionAboveTheSingleLevelKeepsItsSelectedBranch() throws Exception {
      SelectionRuntimeServiceTest.Harness h = stateSingle(geo(true));

      set(h, List.of(List.of("USA East")));

      List<ApplySelectionListEvent> events = sent(h);
      assertEquals(2, events.size(), "a deselect, then the apply");
      List<List<String>> deselected = paths(events.get(0), false);
      assertEquals(List.of(List.of("USA West")), deselected);
      assertEquals(List.of(List.of("USA East")), paths(events.get(1), true));
   }

   /** (c) A path that only prefixes another requested one is folded into it and disclosed. */
   @Test
   void prefixPathIsCollapsedAndReported() throws Exception {
      SelectionRuntimeServiceTest.Harness h = stateSingle(geo(false));

      Map<String, Object> result =
         set(h, List.of(List.of("USA East"), List.of("USA East", "NY")));

      assertEquals(List.of(List.of("USA East")), result.get("prefixPathsCollapsed"));
      assertEquals(1, result.get("valuesSelected"));
      List<ApplySelectionListEvent> events = sent(h);
      assertEquals(List.of(List.of("USA East", "NY")),
                   paths(events.get(events.size() - 1), true));
   }
}
