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
package inetsoft.report.script;

import inetsoft.report.internal.AllLegendDescriptor;
import inetsoft.uql.viewsheet.graph.LegendDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class LegendScriptableTest {
   @Test
   void symbolRoundCornersPropertyRoutesToDescriptor() {
      LegendDescriptor legend = new LegendDescriptor();
      LegendScriptable scriptable = new LegendScriptable(legend);

      assertEquals(Boolean.TRUE, scriptable.get("symbolRoundCorners", null));

      scriptable.put("symbolRoundCorners", null, Boolean.FALSE);
      assertFalse(legend.isSymbolRoundCorners());

      legend.setSymbolRoundCorners(true);
      assertEquals(Boolean.TRUE, scriptable.get("symbolRoundCorners", null));
   }

   @Test
   void symbolRoundCornersBroadcastsAcrossAllLegendDescriptor() {
      LegendDescriptor a = new LegendDescriptor();
      LegendDescriptor b = new LegendDescriptor();
      AllLegendDescriptor all = new AllLegendDescriptor(List.of(a, b));
      LegendScriptable scriptable = new LegendScriptable(all);

      scriptable.put("symbolRoundCorners", null, Boolean.FALSE);

      assertFalse(a.isSymbolRoundCorners());
      assertFalse(b.isSymbolRoundCorners());
   }
}
