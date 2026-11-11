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
package inetsoft.web.binding.service;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.util.Tool;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.binding.model.graph.aesthetic.StaticColorModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A measure colour is flagged as changed only when it is off the chart's own palette.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartInfoModelBuilderColorChangedTest {
   private static final VizContext MODERN = VizContext.of(VizMark.MODERN_LIGHT);

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
      SreeEnv.setProperty("map.default.color", null);
      SreeEnv.setProperty("map.empty.color", null);
   }

   @Test
   void aSeededModernColourIsNotChanged() {
      List<StaticColorModel> models = fix(MODERN, "#0490FF", "#FF5A35");
      assertFalse(models.get(0).isChanged());
      assertFalse(models.get(1).isChanged());
   }

   @Test
   void anOffPaletteColourIsChanged() {
      assertTrue(fix(MODERN, "#123456").get(0).isChanged());
   }

   @Test
   void aClassicLegacyColourIsNotChanged() {
      assertFalse(fix(VizContext.LEGACY, "#518DB9").get(0).isChanged());
   }

   private static List<StaticColorModel> fix(VizContext ctx, String... colors) {
      ChartBindingModel model = new ChartBindingModel();
      List<ChartRefModel> yfields = new ArrayList<>();
      List<StaticColorModel> models = new ArrayList<>();

      for(String color : colors) {
         StaticColorModel frame = new StaticColorModel();
         frame.setColor(Tool.toString(Color.decode(color)));
         ChartAggregateRefModel ref = new ChartAggregateRefModel();
         ref.setColorFrame(frame);
         yfields.add(ref);
         models.add(frame);
      }

      model.setYFields(yfields);
      ChartInfoModelBuilder.fixColorChanged(model, ctx);
      return models;
   }
}
