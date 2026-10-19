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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Bug #78821: a select_subtree whose own path is not selected after the apply is refused, not
 * reported as selected. {@code firstUnselectedOnPath} judges only the path's own nodes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionRuntimeServiceSubtreePostconditionTest {
   private static SelectionValue leaf(String value, boolean selected) {
      SelectionValue leaf = new SelectionValue(value, value);
      leaf.setSelected(selected);
      return leaf;
   }

   private static CompositeSelectionValue branch(String value, boolean selected,
                                                 SelectionValue... children)
   {
      CompositeSelectionValue composite = new CompositeSelectionValue(value, value);
      SelectionList list = new SelectionList();

      for(SelectionValue child : children) {
         list.addSelectionValue(child);
      }

      composite.setSelectionList(list);
      composite.setSelected(selected);
      return composite;
   }

   private static List<String> firstUnselected(SelectionValue[] domain, String... path) {
      return SelectionRuntimeService.firstUnselectedOnPath(domain, List.of(path));
   }

   @Test
   void aSelectedPathPasses() {
      SelectionValue[] domain = {
         branch("3", true, leaf("Feature Phones", false), leaf("Smartphones", true))
      };

      assertNull(firstUnselected(domain, "3", "Smartphones"));
   }

   /**
    * What the bug left behind: the parent selected, the requested leaf not.
    */
   @Test
   void aParentSelectedWithoutTheRequestedLeafIsReportedAtTheLeaf() {
      SelectionValue[] domain = {
         branch("3", true, leaf("Feature Phones", true), leaf("Smartphones", false))
      };

      assertEquals(List.of("3", "Smartphones"), firstUnselected(domain, "3", "Smartphones"));
   }

   @Test
   void anUnselectedAncestorIsReportedAtTheAncestor() {
      SelectionValue[] domain = {
         branch("2", true, leaf("Desktops", true)),
         branch("3", false, leaf("Smartphones", true))
      };

      assertEquals(List.of("3"), firstUnselected(domain, "3", "Smartphones"));
   }

   @Test
   void aPathThatNoLongerResolvesIsReported() {
      SelectionValue[] domain = { branch("3", true, leaf("Feature Phones", true)) };

      assertEquals(List.of("3", "Smartphones"), firstUnselected(domain, "3", "Smartphones"));
   }

   /**
    * A descendant under the path may stay unselected (association, search); only the path counts.
    */
   @Test
   void anUnselectedDescendantBelowTheSelectedPathPasses() {
      SelectionValue[] domain = {
         branch("3", true, branch("Phones", true, leaf("A", true), leaf("B", false)))
      };

      assertNull(firstUnselected(domain, "3", "Phones"));
   }

   @Test
   void aSelectedPathInAMultiSelectTreeWithOtherSelectionsPasses() {
      SelectionValue[] domain = {
         branch("2", true, leaf("Desktops", true)),
         branch("3", true, leaf("Feature Phones", true), leaf("Smartphones", true))
      };

      assertNull(firstUnselected(domain, "3", "Smartphones"));
   }
}
