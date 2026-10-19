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
import inetsoft.uql.erm.DataRef;
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
import org.mockito.InOrder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #78868: a leaf select_subtree on a tree with single-select levels deselects what conflicts
 * with it on a single level before the subtree apply, as setSelection's replace does. Peer trees
 * on the same table and columns share one selection set, so the tree's own apply alone can leave a
 * peer's leaf that sorts first winning the single-select refresh. The engine's refresh is not run
 * here: the mocked {@code VSSelectionService} applies the events to a real SelectionList and
 * selects the requested path, so the tests pin which applies are sent and in what order.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionRuntimeServiceSubtreePeerTest {
   private static SelectionValue leaf(String name, boolean selected) {
      SelectionValue value = new SelectionValue(name, name);
      value.setLevel(1);
      value.setSelected(selected);
      return value;
   }

   private static CompositeSelectionValue branch(String name, SelectionValue... children) {
      CompositeSelectionValue composite = new CompositeSelectionValue(name, name);
      SelectionList list = new SelectionList();
      boolean selected = false;

      for(SelectionValue child : children) {
         list.addSelectionValue(child);
         selected |= child.isSelected();
      }

      composite.setSelectionList(list);
      composite.setSelected(selected);
      return composite;
   }

   /** 1{Computers, Electronics, Phones}, 2{Desktops, Laptops}, 3{Feature Phones, Smartphones}. */
   private static SelectionList domain(String... selectedLeaves) {
      List<String> on = Arrays.asList(selectedLeaves);
      SelectionList domain = new SelectionList();
      domain.addSelectionValue(
         branch("1", leaf("Computers", on.contains("1/Computers")),
                leaf("Electronics", on.contains("1/Electronics")),
                leaf("Phones", on.contains("1/Phones"))));
      domain.addSelectionValue(
         branch("2", leaf("Desktops", on.contains("2/Desktops")),
                leaf("Laptops", on.contains("2/Laptops"))));
      domain.addSelectionValue(
         branch("3", leaf("Feature Phones", on.contains("3/Feature Phones")),
                leaf("Smartphones", on.contains("3/Smartphones"))));
      return domain;
   }

   /**
    * A non-ID two-level tree (ParentID, Label). {@code singleLevels} null means single-select off;
    * empty with {@code mixed} false means every level is single.
    */
   private static SelectionRuntimeServiceTest.Harness harness(SelectionList domain,
                                                              List<Integer> singleLevels,
                                                              boolean mixed)
   {
      SelectionTreeVSAssembly assembly =
         SelectionRuntimeServiceTest.tree(0, singleLevels != null);
      SelectionTreeVSAssemblyInfo info = assembly.getSelectionTreeInfo();
      when(info.getStoredSingleSelectionLevels()).thenReturn(singleLevels);
      when(info.isMixedSingleSelection()).thenReturn(mixed);
      when(info.getLevelNames()).thenReturn(List.of("ParentID", "Label"));
      when(assembly.getDataRefs()).thenReturn(new DataRef[]{ mock(DataRef.class),
                                                             mock(DataRef.class) });
      when(assembly.isIDMode()).thenReturn(false);
      when(assembly.getSelectionList()).thenReturn(domain);
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);

      try {
         doAnswer(inv -> {
            ApplySelectionListEvent event = inv.getArgument(2);

            for(ApplySelectionListEvent.Value v : event.getValues()) {
               mark(domain, v.getValue(), v.isSelected());
            }

            return null;
         }).when(h.selections()).applySelection(anyString(), anyString(), any(),
                                                any(Principal.class), any(), anyString());
         doAnswer(inv -> {
            ApplySelectionListEvent event = inv.getArgument(2);
            mark(domain, event.getValues().get(0).getValue(), true);
            return null;
         }).when(h.selections()).selectSubtree(anyString(), anyString(), any(),
                                               any(Principal.class), any(), anyString());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return h;
   }

   /** Marks the leaves at or under {@code path}, and refreshes their parent's flag. */
   private static void mark(SelectionList domain, String[] path, boolean selected) {
      for(int i = 0; i < domain.getSelectionValueCount(); i++) {
         CompositeSelectionValue parent = (CompositeSelectionValue) domain.getSelectionValue(i);

         if(!parent.getValue().equals(path[0])) {
            continue;
         }

         SelectionList children = parent.getSelectionList();
         boolean any = false;

         for(int j = 0; j < children.getSelectionValueCount(); j++) {
            SelectionValue child = children.getSelectionValue(j);

            if(path.length == 1 || child.getValue().equals(path[1])) {
               child.setSelected(selected);
            }

            any |= child.isSelected();
         }

         parent.setSelected(any);
      }
   }

   private static void select(SelectionRuntimeServiceTest.Harness h, String... path)
      throws Exception
   {
      h.service().selectSubtree("tok", SelectionRuntimeServiceTest.principal(), "Tree1",
                                List.of(path), "select", "");
   }

   /** The deselect targets of every apply the service sent. */
   private static List<List<String>> deselected(SelectionRuntimeServiceTest.Harness h)
      throws Exception
   {
      ArgumentCaptor<ApplySelectionListEvent> events =
         ArgumentCaptor.forClass(ApplySelectionListEvent.class);
      verify(h.selections(), atLeast(0)).applySelection(anyString(), anyString(),
                                                        events.capture(), any(Principal.class),
                                                        any(), anyString());
      return events.getAllValues().stream()
         .flatMap(e -> e.getValues().stream())
         .filter(v -> !v.isSelected())
         .map(v -> Arrays.asList(v.getValue()))
         .toList();
   }

   /** Whether some target is {@code path} or one of its ancestors (a deselect clears below it). */
   private static boolean covers(List<List<String>> targets, List<String> path) {
      return targets.stream().anyMatch(t -> t.size() <= path.size() &&
                                            t.equals(path.subList(0, t.size())));
   }

   /** Peer {1/Computers, 1/Electronics}: the tree shows 1/Computers, the request is 1/Phones. */
   @Test
   void singleRootPeerGhostIsDeselectedBeforeTheSubtreeApply() throws Exception {
      SelectionRuntimeServiceTest.Harness h = harness(domain("1/Computers"), List.of(), false);

      select(h, "1", "Phones");

      assertTrue(covers(deselected(h), List.of("1", "Computers")), "ghost: " + deselected(h));
   }

   /** The reporter's tree shows 2/Desktops from the peer; the request is 3/Smartphones. */
   @Test
   void reporterGhostOnAnotherRootIsDeselected() throws Exception {
      SelectionRuntimeServiceTest.Harness h = harness(domain("2/Desktops"), List.of(), false);

      select(h, "3", "Smartphones");

      assertTrue(covers(deselected(h), List.of("2", "Desktops")), "ghost: " + deselected(h));
   }

   @Test
   void requestingTheAlreadySelectedLeafSendsNoDeselect() throws Exception {
      SelectionRuntimeServiceTest.Harness h =
         harness(domain("3/Feature Phones"), List.of(), false);

      select(h, "3", "Feature Phones");

      assertEquals(List.of(), deselected(h));
      verify(h.selections()).selectSubtree(anyString(), anyString(), any(),
                                           any(Principal.class), any(), anyString());
   }

   @Test
   void theDeselectApplyPrecedesTheSubtreeApply() throws Exception {
      SelectionRuntimeServiceTest.Harness h = harness(domain("2/Desktops"), List.of(), false);

      select(h, "3", "Smartphones");

      InOrder order = inOrder(h.selections());
      order.verify(h.selections()).applySelection(anyString(), anyString(), any(),
                                                  any(Principal.class), any(), anyString());
      order.verify(h.selections()).selectSubtree(anyString(), anyString(), any(),
                                                 any(Principal.class), any(), anyString());
   }

   /** A multi-select tree is additive: no prior deselect, whatever else is selected. */
   @Test
   void multiSelectTreeStaysAdditive() throws Exception {
      SelectionRuntimeServiceTest.Harness h =
         harness(domain("2/Desktops", "1/Computers"), null, false);

      select(h, "3", "Smartphones");

      verify(h.selections(), never()).applySelection(anyString(), anyString(), any(),
                                                     any(Principal.class), any(), anyString());
      verify(h.selections()).selectSubtree(anyString(), anyString(), any(),
                                           any(Principal.class), any(), anyString());
   }

   /**
    * Only Label (level 1) is single, ParentID holds several. 3/Feature Phones conflicts with the
    * request on Label; 2/Desktops differs only on ParentID and stays.
    */
   @Test
   void onlyConflictsOnASingleLevelAreDeselectedOnAPartlySingleTree() throws Exception {
      SelectionRuntimeServiceTest.Harness h =
         harness(domain("2/Desktops", "3/Feature Phones"), List.of(1), true);

      select(h, "3", "Smartphones");

      List<List<String>> deselected = deselected(h);
      assertTrue(covers(deselected, List.of("3", "Feature Phones")), "rival: " + deselected);
      assertFalse(covers(deselected, List.of("2", "Desktops")), "kept: " + deselected);
   }

   @Test
   void conflictsAreThePathsThatPartOnASingleLevel() {
      List<List<String>> current = List.of(List.of("2", "Desktops"),
                                           List.of("3", "Feature Phones"),
                                           List.of("3", "Smartphones"));
      List<String> requested = List.of("3", "Smartphones");

      assertEquals(List.of(List.of("3", "Feature Phones")),
                   SelectionRuntimeService.singleLevelConflicts(current, requested, List.of(1)));
      assertEquals(List.of(List.of("2", "Desktops"), List.of("3", "Feature Phones")),
                   SelectionRuntimeService.singleLevelConflicts(current, requested,
                                                                List.of(0, 1)));
      assertEquals(List.of(),
                   SelectionRuntimeService.singleLevelConflicts(current, requested, List.of()));
   }
}
