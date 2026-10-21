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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * bug-78787: clear_selection and select_subtree(clear) on a tree with an active search deselect
 * only the selected values the search shows. The native merge-back loses a search-hidden
 * selection that shares a top-level ancestor with a visible match, so a clear that sent every
 * selected value lost it. The reporter's Geo tree: USA East{NY{Buffalo, New York}}, both selected.
 * {@code applySelection} is simulated by unselecting what the captured event names.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionRuntimeServiceSearchClearTest {
   private static SelectionValue leaf(String name, int level) {
      SelectionValue value = new SelectionValue(name, name);
      value.setLevel(level);
      value.setSelected(true);
      return value;
   }

   private static CompositeSelectionValue branch(String name, int level, SelectionValue... kids) {
      CompositeSelectionValue composite = new CompositeSelectionValue(name, name);
      composite.setLevel(level);
      composite.setSelected(true);
      SelectionList list = new SelectionList();

      for(SelectionValue kid : kids) {
         list.addSelectionValue(kid);
      }

      composite.setSelectionList(list);
      return composite;
   }

   private static SelectionList geo() {
      SelectionList domain = new SelectionList();
      domain.addSelectionValue(branch("USA East", 0,
         branch("NY", 1, leaf("Buffalo", 2), leaf("New York", 2))));
      return domain;
   }

   private static SelectionRuntimeServiceTest.Harness harness(SelectionList domain, String search) {
      return harness(domain, search, false);
   }

   private static SelectionRuntimeServiceTest.Harness harness(SelectionList domain, String search,
                                                              boolean idMode)
   {
      SelectionTreeVSAssembly assembly = SelectionRuntimeServiceTest.tree(0, false);
      when(assembly.isIDMode()).thenReturn(idMode);
      when(assembly.getSelectionList()).thenReturn(domain);
      when(((SelectionTreeVSAssemblyInfo) assembly.getInfo())
              .getSearchString()).thenReturn(search);
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);

      try {
         doAnswer(inv -> {
            ApplySelectionListEvent event = inv.getArgument(2);

            for(ApplySelectionListEvent.Value value : event.getValues()) {
               if(idMode) {
                  unselectId(domain.getSelectionValues(), value.getValue()[0]);
               }
               else {
                  unselect(domain.getSelectionValues(), value.getValue(), 0);
               }
            }

            return null;
         }).when(h.selections()).applySelection(anyString(), anyString(), any(),
                                                any(Principal.class), any(), anyString());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return h;
   }

   /** The native deselect: the target and everything below it, but no ancestor flag. */
   private static void unselect(SelectionValue[] level, String[] target, int depth) {
      for(SelectionValue value : level) {
         if(value.getValue().equals(target[depth])) {
            if(depth == target.length - 1) {
               clearFlags(value);
            }
            else if(value instanceof CompositeSelectionValue c) {
               unselect(c.getSelectionList().getSelectionValues(), target, depth + 1);
            }
         }
      }
   }

   /** The ID-mode deselect: the node with that id, wherever it sits. */
   private static void unselectId(SelectionValue[] level, String id) {
      for(SelectionValue value : level) {
         if(value.getValue().equals(id)) {
            value.setSelected(false);
         }

         if(value instanceof CompositeSelectionValue c) {
            unselectId(c.getSelectionList().getSelectionValues(), id);
         }
      }
   }

   private static void clearFlags(SelectionValue value) {
      value.setSelected(false);

      if(value instanceof CompositeSelectionValue c) {
         for(SelectionValue kid : c.getSelectionList().getSelectionValues()) {
            clearFlags(kid);
         }
      }
   }

   private static List<List<String>> sent(SelectionRuntimeServiceTest.Harness h) throws Exception {
      ArgumentCaptor<ApplySelectionListEvent> captor =
         ArgumentCaptor.forClass(ApplySelectionListEvent.class);
      verify(h.selections()).applySelection(anyString(), anyString(), captor.capture(),
                                            any(Principal.class), any(), anyString());
      List<List<String>> paths = new ArrayList<>();

      for(ApplySelectionListEvent.Value value : captor.getValue().getValues()) {
         assertFalse(value.isSelected());
         paths.add(Arrays.asList(value.getValue()));
      }

      return paths;
   }

   private static Map<String, Object> clearAll(SelectionRuntimeServiceTest.Harness h)
      throws Exception
   {
      return h.service().clearSelection("tok", SelectionRuntimeServiceTest.principal(), "Tree1", "");
   }

   private static Map<String, Object> clearSubtree(SelectionRuntimeServiceTest.Harness h,
                                                   String... path) throws Exception
   {
      return h.service().selectSubtree("tok", SelectionRuntimeServiceTest.principal(), "Tree1",
                                       List.of(path), "clear", "");
   }

   @Test
   void clearSelectionUnderBuffSendsOnlyBuffaloAndRetainsNewYork() throws Exception {
      SelectionRuntimeServiceTest.Harness h = harness(geo(), "Buff");

      Map<String, Object> result = clearAll(h);

      assertEquals(List.of(List.of("USA East", "NY", "Buffalo")), sent(h));
      assertEquals(1, result.get("clearedCount"));
      assertEquals(1, result.get("selectedTotal"));
      assertEquals("Buff", result.get("scopedBySearch"));
      assertEquals(List.of(List.of("USA East", "NY", "New York")),
                   result.get("scopedBySearchRetained"));
   }

   @Test
   void clearSelectionUnderYorkMirrorsIt() throws Exception {
      SelectionRuntimeServiceTest.Harness h = harness(geo(), "York");

      Map<String, Object> result = clearAll(h);

      assertEquals(List.of(List.of("USA East", "NY", "New York")), sent(h));
      assertEquals(1, result.get("clearedCount"));
      assertEquals(List.of(List.of("USA East", "NY", "Buffalo")),
                   result.get("scopedBySearchRetained"));
   }

   @Test
   void clearSelectionUnderASearchMatchingAnAncestorClearsTheWholeSubtree() throws Exception {
      SelectionRuntimeServiceTest.Harness h = harness(geo(), "NY");

      Map<String, Object> result = clearAll(h);

      assertEquals(List.of(List.of("USA East")), sent(h));
      assertEquals(2, result.get("clearedCount"));
      assertFalse(result.containsKey("scopedBySearchRetained"));
   }

   /** Control: nothing under USA East matches, so nothing is visible and nothing is cleared. */
   @Test
   void clearSelectionUnderASearchThatShowsNothingSendsNothing() throws Exception {
      SelectionRuntimeServiceTest.Harness h = harness(geo(), "Los");

      Map<String, Object> result = clearAll(h);

      verify(h.selections(), never()).applySelection(anyString(), anyString(), any(),
                                                     any(Principal.class), any(), anyString());
      assertEquals(0, result.get("clearedCount"));
      assertEquals(2, ((List<?>) result.get("scopedBySearchRetained")).size());
   }

   @Test
   void clearSelectionWithoutASearchClearsEverything() throws Exception {
      SelectionRuntimeServiceTest.Harness h = harness(geo(), null);

      Map<String, Object> result = clearAll(h);

      assertEquals(List.of(List.of("USA East")), sent(h));
      assertEquals(2, result.get("clearedCount"));
      assertFalse(result.containsKey("scopedBySearchRetained"));
   }

   @Test
   void subtreeClearUnderBuffAtNyKeepsNewYork() throws Exception {
      SelectionRuntimeServiceTest.Harness h = harness(geo(), "Buff");

      Map<String, Object> result = clearSubtree(h, "USA East", "NY");

      assertEquals(List.of(List.of("USA East", "NY", "Buffalo")), sent(h));
      assertEquals(1, result.get("clearedCount"));
      assertEquals(1, result.get("remainingUnderSubtree"));
      assertEquals(List.of(List.of("USA East", "NY", "New York")),
                   result.get("scopedBySearchRetained"));
   }

   @Test
   void subtreeClearUnderYorkAtTheTopKeepsBuffalo() throws Exception {
      SelectionRuntimeServiceTest.Harness h = harness(geo(), "York");

      Map<String, Object> result = clearSubtree(h, "USA East");

      assertEquals(List.of(List.of("USA East", "NY", "New York")), sent(h));
      assertEquals(1, result.get("clearedCount"));
      assertEquals(List.of(List.of("USA East", "NY", "Buffalo")),
                   result.get("scopedBySearchRetained"));
   }

   @Test
   void subtreeClearWithoutASearchStillUsesTheNativeSubtreeClear() throws Exception {
      SelectionRuntimeServiceTest.Harness h = harness(geo(), null);

      clearSubtree(h, "USA East");

      verify(h.selections()).selectSubtree(anyString(), anyString(), any(),
                                           any(Principal.class), any(), anyString());
      verify(h.selections(), never()).applySelection(anyString(), anyString(), any(),
                                                     any(Principal.class), any(), anyString());
   }

   /** ID tree R{Buffalo, New York}, both selected, root not (select_subtree leaves it unselected). */
   private static SelectionList idTree() {
      SelectionList domain = new SelectionList();
      CompositeSelectionValue root = branch("R", 0, leaf("Buffalo", 1), leaf("New York", 1));
      root.setSelected(false);
      domain.addSelectionValue(root);
      return domain;
   }

   /** bug-78983: an ID-mode subtree clear under a search deselects only the visible node ids. */
   @Test
   void idSubtreeClearUnderBuffSendsOnlyBuffaloAndRetainsNewYork() throws Exception {
      SelectionRuntimeServiceTest.Harness h = harness(idTree(), "Buff", true);

      Map<String, Object> result = clearSubtree(h, "R");

      assertEquals(List.of(List.of("Buffalo")), sent(h));
      assertEquals(1, result.get("clearedCount"));
      assertEquals(1, result.get("remainingUnderSubtree"));
      assertEquals(List.of(List.of("New York")), result.get("scopedBySearchRetained"));
      verify(h.selections(), never()).selectSubtree(anyString(), anyString(), any(),
                                                    any(Principal.class), any(), anyString());
   }

   @Test
   void idSubtreeClearUnderASearchShowingNothingSendsNothing() throws Exception {
      SelectionRuntimeServiceTest.Harness h = harness(idTree(), "Los", true);

      Map<String, Object> result = clearSubtree(h, "R");

      verify(h.selections(), never()).applySelection(anyString(), anyString(), any(),
                                                     any(Principal.class), any(), anyString());
      assertEquals(0, result.get("clearedCount"));
   }

   @Test
   void idSubtreeClearWithoutASearchStillUsesTheNativeSubtreeClear() throws Exception {
      SelectionRuntimeServiceTest.Harness h = harness(idTree(), null, true);

      clearSubtree(h, "R");

      verify(h.selections()).selectSubtree(anyString(), anyString(), any(),
                                           any(Principal.class), any(), anyString());
   }
}
