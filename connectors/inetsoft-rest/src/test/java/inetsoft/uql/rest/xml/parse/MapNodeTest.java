/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.xml.parse;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MapNodeTest {
   @Test
   void getLookupEntities() {
      final MapNode root = new MapNode();
      final MapNode grandParent = new MapNode();
      final MapNode parent = new MapNode();
      final MapNode entity = new MapNode();

      root.put("grandParent", grandParent);
      grandParent.put("parent", parent);
      parent.put("entity", entity);

      final List<MapNode> lookupEntities = root
         .getLookupEntities("/grandParent/parent/entity");
      assertEquals(Collections.singletonList(entity), lookupEntities);
   }
}