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
package inetsoft.graph.treemap;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class TreeModelTest {
   @Test
   void getItemsAssignsDepthOfChildren() {
      TreeModel root = new TreeModel();
      TreeModel a = new TreeModel();
      TreeModel b = new TreeModel();
      TreeModel grandchild = new TreeModel();
      root.addChild(a);
      root.addChild(b);
      a.addChild(grandchild);

      Mappable[] rootItems = root.getItems();
      assertEquals(2, rootItems.length);
      assertSame(a.getMapItem(), rootItems[0]);
      assertEquals(1, rootItems[0].getDepth());
      assertEquals(1, rootItems[1].getDepth());

      Mappable[] aItems = a.getItems();
      assertEquals(1, aItems.length);
      assertEquals(2, aItems[0].getDepth());
   }

   @Test
   void circularAncestryIsRejected() {
      TreeModel ancestor = new TreeModel();
      TreeModel child = new TreeModel();
      TreeModel descendant = new TreeModel();
      ancestor.addChild(child);
      child.addChild(descendant);

      assertThrows(IllegalArgumentException.class, () -> descendant.addChild(ancestor));
      assertThrows(IllegalArgumentException.class, () -> ancestor.addChild(ancestor));
   }

   @Test
   void addChildInvalidatesTheCachedItems() {
      TreeModel root = new TreeModel();
      root.addChild(new TreeModel());
      assertEquals(1, root.getItems().length);

      TreeModel added = new TreeModel();
      root.addChild(added);

      Mappable[] items = root.getItems();
      assertEquals(2, items.length);
      assertSame(added.getMapItem(), items[1]);
   }

   @Test
   void leafModelsAndTreeItems() {
      TreeModel root = new TreeModel();
      TreeModel b1 = new TreeModel();
      TreeModel b2 = new TreeModel();
      TreeModel x = new TreeModel();
      TreeModel y = new TreeModel();
      TreeModel z = new TreeModel();
      root.addChild(b1);
      root.addChild(b2);
      b1.addChild(x);
      b1.addChild(y);
      b2.addChild(z);

      Set<MapModel> leafModels = new HashSet<>(Arrays.asList(root.getLeafModels()));
      assertEquals(2, root.getLeafModels().length);
      assertEquals(new HashSet<MapModel>(Arrays.asList(b1, b2)), leafModels);

      Set<Mappable> treeItems = new HashSet<>(Arrays.asList(root.getTreeItems()));
      assertEquals(3, root.getTreeItems().length);
      assertEquals(new HashSet<Mappable>(Arrays.asList(x.getMapItem(), y.getMapItem(),
                                                       z.getMapItem())), treeItems);
   }
}
