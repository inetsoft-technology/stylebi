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

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * bug-79882: a tree leaf given under the wrong parent was refused with the same "confirm the exact
 * spelling" hint as a misspelled value. When the failing segment exists elsewhere in the tree the
 * refusal must name its real full path instead.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionRuntimeServiceWrongParentHintTest {
   private static CompositeSelectionValue node(String name, int level, SelectionValue... children) {
      CompositeSelectionValue node = new CompositeSelectionValue(name, name);
      node.setLevel(level);
      SelectionList list = new SelectionList();

      for(SelectionValue child : children) {
         list.addSelectionValue(child);
      }

      node.setSelectionList(list);
      return node;
   }

   private static SelectionValue leaf(String name, int level) {
      SelectionValue value = new SelectionValue(name, name);
      value.setLevel(level);
      return value;
   }

   /** USA East{CT{Hartford}, MA{Boston}}, USA West{CA{LA}}. */
   private static SelectionValue[] domain() {
      SelectionList list = new SelectionList();
      list.addSelectionValue(node("USA East", 0,
         node("CT", 1, leaf("Hartford", 2)), node("MA", 1, leaf("Boston", 2))));
      list.addSelectionValue(node("USA West", 0, node("CA", 1, leaf("LA", 2))));
      return list.getSelectionValues();
   }

   private static String refusal(List<String> path, String action) {
      return assertThrows(IllegalArgumentException.class,
         () -> SelectionRuntimeService.requireResolvable("Tree1", domain(),
            List.of(path), false, action)).getMessage();
   }

   @Test
   void aLeafUnderTheWrongParentNamesItsRealFullPath() {
      String message = refusal(Arrays.asList("USA East", "MA", "Hartford"), "deselecting");

      assertTrue(message.contains("'Hartford' exists at [USA East, CT, Hartford]"), message);
      assertTrue(message.contains("before deselecting it."), message);
      assertFalse(message.contains("exact spelling"), message);
   }

   @Test
   void aNonexistentValueKeepsTheSpellingHint() {
      String message = refusal(Arrays.asList("USA East", "MA", "Nowhere"), "deselecting");

      assertTrue(message.contains("(not found: 'Nowhere')"), message);
      assertTrue(message.contains(
         "confirm the exact spelling via browse_condition_values before deselecting it."), message);
      assertFalse(message.contains("exists at"), message);
   }

   @Test
   void aMisspelledParentKeepsTheSpellingHint() {
      String message = refusal(Arrays.asList("USA Eest", "CT", "Hartford"), "selecting");

      assertTrue(message.contains("(not found: 'USA Eest')"), message);
      assertTrue(message.contains("exact spelling"), message);
   }

   @Test
   void aResolvablePathIsAccepted() {
      SelectionRuntimeService.requireResolvable("Tree1", domain(),
         List.of(Arrays.asList("USA East", "CT", "Hartford")), false, "selecting");
   }
}
