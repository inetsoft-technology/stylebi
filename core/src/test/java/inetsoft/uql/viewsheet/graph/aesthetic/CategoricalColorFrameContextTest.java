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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("core")
class CategoricalColorFrameContextTest {
   @Test
   void clearDropsStateLeftOnThePooledThread() {
      CategoricalColorFrameContext context = CategoricalColorFrameContext.getContext();
      context.setDimensionColors(Map.of("Games", Color.RED));
      context.addSharedFrame("Category", new CategoricalColorFrame(), null);

      CategoricalColorFrameContext.clear();

      assertTrue(context.getDimensionColors().isEmpty());
      assertEquals(null, context.getSharedFrame("Category", null));
   }

   @Test
   void bindWithoutViewsheetStartsEmpty() {
      CategoricalColorFrameContext context = CategoricalColorFrameContext.getContext();
      context.setDimensionColors(Map.of("Games", Color.RED));

      CategoricalColorFrameContext.bind(null);

      assertTrue(context.getDimensionColors().isEmpty());
   }
}
