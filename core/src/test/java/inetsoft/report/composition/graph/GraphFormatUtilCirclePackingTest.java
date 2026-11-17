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
package inetsoft.report.composition.graph;

import inetsoft.uql.erm.AttributeRef;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Bug #81414: the circle packing container format must be read and written through the same
 * place, whether or not a text field is bound.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class GraphFormatUtilCirclePackingTest {
   @Test
   void containerFormatRoundTripsWhenTextFieldBound() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_CIRCLE_PACKING);
      info.setRTChartType(GraphTypes.CHART_CIRCLE_PACKING);
      VSChartDimensionRef state = dim("state");
      VSChartDimensionRef city = dim("city");
      info.addGroupField(state);
      info.addGroupField(city);

      VSAestheticRef text = new VSAestheticRef();
      text.setDataRef(city);
      info.setTextField(text);

      PlotDescriptor plot = new PlotDescriptor();
      CompositeTextFormat fmt = new CompositeTextFormat();
      GraphFormatUtil.setBindingTextFormat(info, state, plot, fmt, false);

      assertSame(fmt, GraphFormatUtil.getBindingTextFormat(info, state, plot, false));
      assertSame(fmt, plot.getCircleFormat(1));
   }

   private static VSChartDimensionRef dim(String name) {
      return new VSChartDimensionRef(new AttributeRef(null, name));
   }
}
