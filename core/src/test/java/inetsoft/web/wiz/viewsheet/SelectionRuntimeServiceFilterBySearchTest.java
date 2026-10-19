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
import inetsoft.uql.viewsheet.SelectionValue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * bug-76854 / bug-78786: {@code filterBySearch} keeps exactly the paths the tree widget shows for
 * the search string, i.e. the ones that resolve in {@code findAll(search, false)} -- a node whose
 * own label matches shows its whole subtree, so a path under a matching ancestor is kept. Real
 * {@code SelectionValue}s (the old mock-based tests could not see the ancestor rule).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionRuntimeServiceFilterBySearchTest {
   private static SelectionValue leaf(String label, String value) {
      return new SelectionValue(label, value);
   }

   private static CompositeSelectionValue branch(String label, String value,
                                                 SelectionValue... children)
   {
      CompositeSelectionValue composite = new CompositeSelectionValue(label, value);
      SelectionList list = new SelectionList();

      for(SelectionValue child : children) {
         list.addSelectionValue(child);
      }

      composite.setSelectionList(list);
      return composite;
   }

   /** USA East{NY{Buffalo,New York}, MA{Boston}}, USA West{CA{Los Angeles}}; value == label. */
   private static SelectionValue[] geo() {
      return new SelectionValue[]{
         branch("USA East", "USA East",
                branch("NY", "NY", leaf("Buffalo", "Buffalo"), leaf("New York", "New York")),
                branch("MA", "MA", leaf("Boston", "Boston"))),
         branch("USA West", "USA West", branch("CA", "CA", leaf("Los Angeles", "Los Angeles")))
      };
   }

   /** Same shape with ids as values and names as labels. */
   private static SelectionValue[] geoIds() {
      return new SelectionValue[]{
         branch("USA East", "E",
                branch("NY", "NY", leaf("Buffalo", "BUF"), leaf("New York", "NYC")),
                branch("MA", "MA", leaf("Boston", "BOS"))),
         branch("USA West", "W", branch("CA", "CA", leaf("Los Angeles", "LA")))
      };
   }

   private static List<List<String>> filter(SelectionValue[] domain, boolean idMode, String search,
                                            List<String>... paths)
   {
      return SelectionRuntimeService.filterBySearch(domain, List.of(paths), idMode, search);
   }

   private static final List<String> BOSTON = List.of("USA East", "MA", "Boston");
   private static final List<String> NY = List.of("USA East", "NY");
   private static final List<String> BUFFALO = List.of("USA East", "NY", "Buffalo");

   @Test
   @SuppressWarnings("unchecked")
   void keepsAPathUnderAMatchingTopLevelAncestor() {
      assertEquals(List.of(BOSTON, NY, BUFFALO, List.of("USA East")),
                   filter(geo(), false, "East", BOSTON, NY, BUFFALO, List.of("USA East")));
   }

   @Test
   @SuppressWarnings("unchecked")
   void keepsAPathUnderAMatchingMiddleLevelAncestor() {
      assertEquals(List.of(BOSTON), filter(geo(), false, "MA", BOSTON));
   }

   @Test
   @SuppressWarnings("unchecked")
   void dropsAPathWhoseAncestorsAndSelfDoNotMatch() {
      assertEquals(List.of(), filter(geo(), false, "West", BOSTON));
      assertEquals(List.of(), filter(geo(), false, "Bos", BUFFALO));
   }

   @Test
   @SuppressWarnings("unchecked")
   void keepsAnAncestorOfAMatchingDescendantAndTheDescendant() {
      assertEquals(List.of(BOSTON, List.of("USA East")),
                   filter(geo(), false, "Bos", BOSTON, List.of("USA East")));
   }

   @Test
   @SuppressWarnings("unchecked")
   void searchIsCaseInsensitive() {
      assertEquals(List.of(BOSTON), filter(geo(), false, "east", BOSTON));
      assertEquals(List.of(BOSTON), filter(geo(), false, "EAST", BOSTON));
   }

   @Test
   @SuppressWarnings("unchecked")
   void doesNotKeepAFabricatedTailUnderAMatchingAncestor() {
      assertEquals(List.of(), filter(geo(), false, "East", List.of("USA East", "XX")));
      assertEquals(List.of(), filter(geo(), false, "East", List.of("USA East", "NY", "Buffalo", "X")));
   }

   @Test
   @SuppressWarnings("unchecked")
   void idModeKeepsAnIdUnderAMatchingAncestor() {
      assertEquals(List.of(List.of("BOS")), filter(geoIds(), true, "East", List.of("BOS")));
      assertEquals(List.of(List.of("BOS")), filter(geoIds(), true, "east", List.of("BOS")));
   }

   @Test
   @SuppressWarnings("unchecked")
   void idModeDropsAPathContainingAnIdTheSearchHides() {
      assertEquals(List.of(), filter(geoIds(), true, "East", List.of("E", "LA")));
      assertEquals(List.of(), filter(geoIds(), true, "Bos", List.of("BOS", "LA")));
      assertEquals(List.of(), filter(geoIds(), true, "West", List.of("BOS")));
   }

   @Test
   @SuppressWarnings("unchecked")
   void idModeKeepsAPathWhoseEveryIdIsVisible() {
      assertEquals(List.of(List.of("E", "BOS")), filter(geoIds(), true, "East", List.of("E", "BOS")));
   }

   @Test
   @SuppressWarnings("unchecked")
   void flatListKeepsOnlyMatchingValues() {
      SelectionValue[] flat = { leaf("Business", "Business"), leaf("Games", "Games") };
      assertEquals(List.of(List.of("Games")),
                   filter(flat, false, "Ga", List.of("Business"), List.of("Games")));
   }

   @Test
   @SuppressWarnings("unchecked")
   void refusesAnOverLongPathThatBottomsOutAtALeaf() {
      assertEquals(List.of(), filter(new SelectionValue[]{ leaf("East", "East") }, false, "East",
                                     List.of("East", "NY")));
   }
}
