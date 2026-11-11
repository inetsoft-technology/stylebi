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
package inetsoft.web.binding.service.graph;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.model.graph.aesthetic.StaticColorModel;
import inetsoft.web.binding.service.graph.aesthetic.ColorFrameModelFactory;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactory;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * A binding Apply keeps the waterfall Total's default colour the editor sent.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartAggregateInfoFactorySummaryTest {
   @Test
   void anApplyKeepsTheSeededSummaryColour() {
      assertEquals(new Color(0x767676), apply("#767676"));
   }

   @Test
   void aClassicApplyStillWritesPurple() {
      assertEquals(new Color(0x7030A0), apply("#7030A0"));
   }

   @Test
   void anApplyWithNoDefaultWritesPurple() {
      assertEquals(new Color(0x7030A0), apply(null));
   }

   private static Color apply(String defaultColor) {
      List<VisualFrameModelFactory<?, ?>> factories = new ArrayList<>();
      factories.add(new ColorFrameModelFactory.StaticColorFrameFactory());
      ChartAggregateInfoFactory.VSChartAggregateInfoFactory factory =
         new ChartAggregateInfoFactory.VSChartAggregateInfoFactory(
            new VisualFrameModelFactoryService(factories), null);
      factory.setChartAestheticService(mock(ChartAestheticService.class));

      VSChartAggregateRef ref = new VSChartAggregateRef();
      StaticColorModel summary = new StaticColorModel(new StaticColorFrameWrapper());
      summary.setSummary(true);
      summary.setDefaultColor(defaultColor);
      ChartAggregateRefModel model = new ChartAggregateRefModel();
      model.setSummaryColorFrame(summary);

      factory.pasteChartRef(new DefaultVSChartInfo(), model, ref);

      return ((StaticColorFrameWrapper) ref.getSummaryColorFrameWrapper()).getDefaultColor();
   }
}
