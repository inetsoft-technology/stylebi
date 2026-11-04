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

import inetsoft.graph.internal.GDefaults;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.css.CSSDictionary;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Font;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartFormatDefaultsTest {
   @AfterEach
   void reset() {
      CSSDictionary.resetDictionaryCache();
   }

   private ChartVSAssemblyInfo chartInfo() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(null);
      vs.addAssembly(chart);
      return info;
   }

   private CompositeTextFormat addColumnFormat(ChartVSAssemblyInfo info) {
      CompositeTextFormat fmt = new CompositeTextFormat();
      info.getVSChartInfo().getAxisDescriptor().setColumnLabelTextFormat("col", fmt);
      return fmt;
   }

   @Test
   void anAuthorObjectFontBecomesTheAxisLabelDefault() {
      ChartVSAssemblyInfo info = chartInfo();
      Font font = new Font("Arial", Font.BOLD, 14);
      info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH).getUserDefinedFormat()
         .setFontValue(font, true);

      ChartFormatDefaults.fix(info);

      AxisDescriptor axis = info.getVSChartInfo().getAxisDescriptor();
      assertEquals(font, axis.getAxisLabelTextFormat().getDefaultFormat().getFont());
   }

   @Test
   void anUnmarkedChartGetsTheLegacyPerColumnLabelColor() {
      ChartVSAssemblyInfo info = chartInfo();
      CompositeTextFormat fmt = addColumnFormat(info);
      fmt.getDefaultFormat().setColor(
         VSChartChromeDefaults.labelColor(VizContext.of(VizMark.MODERN_LIGHT)));

      ChartFormatDefaults.fix(info);

      assertEquals(GDefaults.DEFAULT_TEXT_COLOR, fmt.getDefaultFormat().getColor());
   }

   @Test
   void aMarkedChartGetsTheModernPerColumnLabelColor() {
      ChartVSAssemblyInfo info = chartInfo();
      CompositeTextFormat fmt = addColumnFormat(info);
      info.setVizMark(VizMark.MODERN_LIGHT);

      ChartFormatDefaults.fix(info);

      assertEquals(VSChartChromeDefaults.labelColor(VizContext.of(info)),
                   fmt.getDefaultFormat().getColor());
   }

   @Test
   void legendTitleDefaultsMatchTheDescriptorsOwnInit() {
      ChartVSAssemblyInfo info = chartInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      ChartFormatDefaults.fix(info);

      LegendsDescriptor expected = new LegendsDescriptor();
      expected.initDefaultFormat(VizContext.of(info));
      TextFormat actual = info.getChartDescriptor().getLegendsDescriptor()
         .getTitleTextFormat().getDefaultFormat();
      TextFormat want = expected.getTitleTextFormat().getDefaultFormat();
      assertEquals(want.getColor(), actual.getColor());
      assertEquals(want.getFont(), actual.getFont());
   }
}
