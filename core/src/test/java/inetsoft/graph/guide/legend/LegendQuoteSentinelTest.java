/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.graph.guide.legend;

import inetsoft.graph.EGraph;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82026: Legend.createItems swaps "'" for the placeholder "^_^" around Tool.localize and then
 * turns every "^_^" back into "'", so a label that already contained "^_^" was corrupted.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LegendQuoteSentinelTest {
   @ParameterizedTest
   @ValueSource(strings = { "x^_^y'z", "a/b^_^c'd", "O'Brien", "x^_^y" })
   void legendLabelKeepsDataValue(String value) {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.init(value);

      Legend legend = Legend.createLegend(frame, new EGraph());
      LegendItem[][] items = legend.getItems();

      assertEquals(1, items.length);
      assertEquals(value, items[0][0].getLabel());
   }
}
