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
package inetsoft.web.viewsheet.service;

import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #78584: a selection change in a list is copied into a neighbour tree on the same column
 * ({@code VSSelectionService.applySelectionConditions}). In a column tree each path segment must
 * be looked up on its own level only; in an ID tree an {@code [id]} path may name a node at any
 * depth.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSSelectionServiceNeighborPathTest {
   /**
    * EMPLOYEE_ID > CUSTOMER_ID, where employee 1 has a customer 3: selecting employees 1 and 3
    * must select the top-level nodes 1 and 3, not the customer 1/3.
    */
   @Test
   void columnTreeSelectsTopLevelNodesOnly() throws Exception {
      SelectionTreeVSAssembly tree = createColumnTree();

      applyConditions(tree, Map.of(List.of(EMPLOYEE_ID), paths("1", "3")), true);

      assertEquals(Set.of("1", "3"), selectedPaths(tree.getSelectionList(), ""));
   }

   /**
    * Clearing employees 1 and 3 in the list must clear both top-level nodes in one call.
    */
   @Test
   void columnTreeDeselectsTopLevelNodesInOneCall() throws Exception {
      SelectionTreeVSAssembly tree = createColumnTree();
      SelectionList list = tree.getSelectionList();
      list.findValue("1", false).setSelected(true);
      list.findValue("3", false).setSelected(true);

      applyConditions(tree, Map.of(List.of(EMPLOYEE_ID), paths("1", "3")), false);

      assertEquals(Set.of(), selectedPaths(list, ""));
   }

   /**
    * A tree neighbour on EMPLOYEE_ID > CUSTOMER_ID sends two-segment paths. Each segment is
    * looked up on its own level: [3, 6] must reach employee 3's customer 6, not stop at the
    * customer 1/3 found first by a depth-first lookup of "3".
    */
   @Test
   void columnTreeWalksTwoSegmentPathsLevelByLevel() throws Exception {
      SelectionTreeVSAssembly tree = createColumnTree();
      List<String> refs = List.of(EMPLOYEE_ID, CUSTOMER_ID);

      applyConditions(tree, Map.of(refs, Set.of(List.of("1", "3"), List.of("3", "6"))), true);

      assertEquals(Set.of("1", "1/3", "3", "3/6"), selectedPaths(tree.getSelectionList(), ""));

      applyConditions(tree, Map.of(refs, Set.of(List.of("1", "3"))), false);

      assertEquals(Set.of("3", "3/6"), selectedPaths(tree.getSelectionList(), ""));
   }

   /**
    * An ID tree (1 > 2 > 3, 1 > 4) receives one-segment [id] paths, which must still reach the
    * nodes below the root.
    */
   @Test
   void idTreeSelectsNodesAtAnyDepth() throws Exception {
      Viewsheet vs = new Viewsheet();
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, "IDTree");
      tree.setDataRefs(new DataRef[] { column(EMP_ID), column(MGR_ID) });
      tree.setMode(SelectionTreeVSAssemblyInfo.ID);
      tree.setIDValue(EMP_ID);
      tree.setParentIDValue(MGR_ID);
      tree.setSelectionList(list(
         node("1", list(
            node("2", list(leaf("3"))),
            leaf("4")))));
      vs.addAssembly(tree);

      applyConditions(tree, Map.of(List.of(EMP_ID), paths("3", "4")), true);

      assertEquals(Set.of("1/2/3", "1/4"), selectedPaths(tree.getSelectionList(), ""));
   }

   private static SelectionTreeVSAssembly createColumnTree() {
      Viewsheet vs = new Viewsheet();
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, "ST1");
      tree.setDataRefs(new DataRef[] { column(EMPLOYEE_ID), column(CUSTOMER_ID) });
      // node 1 comes first and has a customer 3, as in the Orders sample data
      tree.setSelectionList(list(
         node("1", list(leaf("3"), leaf("1"))),
         node("2", list(leaf("5"))),
         node("3", list(leaf("6")))));
      vs.addAssembly(tree);
      return tree;
   }

   private static void applyConditions(AssociatedSelectionVSAssembly assembly,
                                       Map<List<String>, Set<List<String>>> conditions,
                                       boolean select)
      throws Exception
   {
      Method apply = VSSelectionService.class.getDeclaredMethod(
         "applySelectionConditions", AssociatedSelectionVSAssembly.class, Map.class,
         boolean.class);
      apply.setAccessible(true);
      apply.invoke(new VSSelectionService(null, null, null, null), assembly, conditions, select);
   }

   private static Set<List<String>> paths(String... values) {
      Set<List<String>> paths = new HashSet<>();

      for(String value : values) {
         paths.add(List.of(value));
      }

      return paths;
   }

   private static Set<String> selectedPaths(SelectionList list, String prefix) {
      Set<String> paths = new TreeSet<>();

      for(SelectionValue value : list.getSelectionValues()) {
         String path = prefix + value.getValue();

         if(value.isSelected()) {
            paths.add(path);
         }

         if(value instanceof CompositeSelectionValue) {
            paths.addAll(selectedPaths(
               ((CompositeSelectionValue) value).getSelectionList(), path + "/"));
         }
      }

      return paths;
   }

   private static SelectionList list(SelectionValue... values) {
      SelectionList list = new SelectionList();
      list.setSelectionValues(values);
      return list;
   }

   private static CompositeSelectionValue node(String value, SelectionList children) {
      CompositeSelectionValue node = new CompositeSelectionValue(value, value);
      node.setSelectionList(children);
      return node;
   }

   private static SelectionValue leaf(String value) {
      return new SelectionValue(value, value);
   }

   private static ColumnRef column(String name) {
      return new ColumnRef(new AttributeRef(null, name));
   }

   private static final String EMPLOYEE_ID = "EMPLOYEE_ID";
   private static final String CUSTOMER_ID = "CUSTOMER_ID";
   private static final String EMP_ID = "EMP_ID";
   private static final String MGR_ID = "MGR_ID";
}
