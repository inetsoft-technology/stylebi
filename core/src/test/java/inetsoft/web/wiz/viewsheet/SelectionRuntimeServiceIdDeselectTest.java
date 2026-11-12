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
import inetsoft.web.viewsheet.event.ApplySelectionListEvent;
import org.junit.jupiter.api.BeforeEach;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #81242: {@code deselect} on an ID-mode selection tree. The viewer unticks one node by sending
 * {@code {value:[id], selected:false}}, which {@code VSSelectionService} applies to exactly the
 * nodes whose id is in the value; set_selection used to refuse it. Runs over a real
 * {@code SelectionList} (see {@link SelectionRuntimeServiceSubtreeDomainTest} for why that needs
 * the Spring context) with an apply that mirrors that per-id rule.
 *
 * <p>Tree: {@code 1 > {2 > {4, 5}, 3}}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionRuntimeServiceIdDeselectTest {
   @BeforeEach
   void setUp() {
      SelectionValue four = new SelectionValue("Laptops", "4");
      SelectionValue five = new SelectionValue("Desktops", "5");
      SelectionList computersKids = new SelectionList();
      computersKids.addSelectionValue(four);
      computersKids.addSelectionValue(five);
      CompositeSelectionValue computers = new CompositeSelectionValue("Computers", "2");
      computers.setSelectionList(computersKids);

      SelectionList rootKids = new SelectionList();
      rootKids.addSelectionValue(computers);
      rootKids.addSelectionValue(new SelectionValue("Phones", "3"));
      CompositeSelectionValue root = new CompositeSelectionValue("Electronics", "1");
      root.setSelectionList(rootKids);

      domain = new SelectionList();
      domain.addSelectionValue(root);

      tree = SelectionRuntimeServiceTest.tree(0, false);
      when(tree.isIDMode()).thenReturn(true);
      when(tree.getSelectionList()).thenReturn(domain);
      harness = SelectionRuntimeServiceTest.harness(tree);

      // VSSelectionService.updateIDSelectionTree: every node whose id is in a value takes that
      // value's selected flag; nothing else changes.
      try {
         doAnswer(invocation -> {
            ApplySelectionListEvent event = invocation.getArgument(2);

            for(ApplySelectionListEvent.Value value : event.getValues()) {
               apply(domain.getSelectionValues(), List.<String[]>of(value.getValue()),
                     value.isSelected());
            }

            return null;
         }).when(harness.selections()).applySelection(anyString(), anyString(), any(),
                                                      any(Principal.class), any(), anyString());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
   }

   private static void apply(SelectionValue[] values, List<String[]> ids, boolean selected) {
      for(SelectionValue value : values) {
         for(String[] path : ids) {
            if(Arrays.asList(path).contains(value.getValue())) {
               value.setSelected(selected);
            }
         }

         if(value instanceof CompositeSelectionValue composite &&
            composite.getSelectionList() != null)
         {
            apply(composite.getSelectionList().getSelectionValues(), ids, selected);
         }
      }
   }

   private void select(String... ids) {
      List<String[]> paths = new ArrayList<>();

      for(String id : ids) {
         paths.add(new String[]{ id });
      }

      apply(domain.getSelectionValues(), paths, true);
   }

   private List<String> selectedIds() {
      return SelectionRuntimeService.selectedNodeIds(domain.getSelectionValues());
   }

   private Map<String, Object> deselect(List<List<String>> deselect) throws Exception {
      return harness.service().setSelection("tok", SelectionRuntimeServiceTest.principal(), "Tree1",
                                            null, deselect, null, null, null, "");
   }

   private List<ApplySelectionListEvent> sentEvents() throws Exception {
      ArgumentCaptor<ApplySelectionListEvent> sent =
         ArgumentCaptor.forClass(ApplySelectionListEvent.class);
      verify(harness.selections(), atLeast(0)).applySelection(
         anyString(), anyString(), sent.capture(), any(Principal.class), any(), anyString());
      return sent.getAllValues();
   }

   /** The reported case: Computers stays selected with Desktops while Laptops is removed. */
   @Test
   void deselectsOneLeafAndLeavesItsParentAndSiblingSelected() throws Exception {
      select("2", "4", "5");

      Map<String, Object> result = deselect(List.of(List.of("4")));

      assertEquals(List.of("2", "5"), selectedIds());
      assertEquals(1, result.get("deselected"));
      assertEquals(2, result.get("selectedTotal"));
   }

   /** The id chain spelling names its last id only: it must not drop 1 or 2 on the way. */
   @Test
   void anIdChainRemovesItsLastIdOnly() throws Exception {
      select("1", "2", "4", "5");

      deselect(List.of(List.of("1", "2", "4")));

      assertEquals(List.of("1", "2", "5"), selectedIds());
      List<ApplySelectionListEvent> events = sentEvents();
      assertEquals(1, events.size());
      assertEquals(1, events.get(0).getValues().size());
      assertArrayEquals(new String[]{ "4" }, events.get(0).getValues().get(0).getValue());
      assertFalse(events.get(0).getValues().get(0).isSelected());
   }

   /** The backend deselects the named node only; unticking a parent's subtree is the caller's call. */
   @Test
   void deselectingAParentLeavesItsChildrenSelected() throws Exception {
      select("2", "4", "5");

      deselect(List.of(List.of("2")));

      assertEquals(List.of("4", "5"), selectedIds());
   }

   @Test
   void severalIdsAreRemovedInOneApply() throws Exception {
      select("2", "4", "5");

      Map<String, Object> result = deselect(List.of(List.of("4"), List.of("5")));

      assertEquals(List.of("2"), selectedIds());
      assertEquals(2, result.get("deselected"));
      assertEquals(1, sentEvents().size());
   }

   /** A real node that is not selected stays a legal no-op: nothing is applied or reported. */
   @Test
   void deselectingAnUnselectedNodeAppliesNothing() throws Exception {
      select("2");

      Map<String, Object> result = deselect(List.of(List.of("3")));

      assertEquals(List.of("2"), selectedIds());
      assertEquals(List.of(), sentEvents());
      assertFalse(result.containsKey("deselected"));
   }

   @Test
   void refusesAnUnknownIdByNameBeforeApplyingAnything() throws Exception {
      select("2", "4");

      Exception e = assertThrows(IllegalArgumentException.class,
                                 () -> deselect(List.of(List.of("4"), List.of("99"))));

      assertTrue(e.getMessage().contains("99"), e.getMessage());
      assertEquals(List.of("2", "4"), selectedIds());
      assertEquals(List.of(), sentEvents());
   }

   /** A chain is validated whole: a fabricated id on the way is refused, not silently ignored. */
   @Test
   void refusesAChainThatPassesThroughAnUnknownId() throws Exception {
      select("2", "4");

      Exception e = assertThrows(IllegalArgumentException.class,
                                 () -> deselect(List.of(List.of("1", "99", "4"))));

      assertTrue(e.getMessage().contains("99"), e.getMessage());
      assertEquals(List.of(), sentEvents());
   }

   @Test
   void refusesAnEmptyEntryByName() {
      Exception e = assertThrows(IllegalArgumentException.class,
                                 () -> deselect(List.of(List.of())));

      assertTrue(e.getMessage().contains("names no node"), e.getMessage());
   }

   @Test
   void idLeafPathsReducesEachChainToItsLastIdAndDropsDuplicates() {
      assertEquals(List.of(List.of("4"), List.of("5")),
                   SelectionRuntimeService.idLeafPaths(
                      "Tree1", List.of(List.of("1", "2", "4"), List.of("4"), List.of("5"))));
      assertNull(SelectionRuntimeService.idLeafPaths("Tree1", null));
   }

   /** The replace-diff for {@code values} still keys off the path-diffable gate, which is unchanged. */
   @Test
   void anIdTreeIsStillNotPathDiffable() {
      assertFalse(SelectionRuntimeService.isPathDiffable(tree));
   }

   private SelectionList domain;
   private SelectionTreeVSAssembly tree;
   private SelectionRuntimeServiceTest.Harness harness;
}
