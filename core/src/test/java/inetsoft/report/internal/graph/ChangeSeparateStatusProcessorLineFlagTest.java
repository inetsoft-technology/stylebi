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
package inetsoft.report.internal.graph;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.AxisDescriptor;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Separating or combining a chart's axes carries the axis line's author flag with the line.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChangeSeparateStatusProcessorLineFlagTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aSeededLineIsCopiedWithoutTheFlag() {
      AxisDescriptor source = new AxisDescriptor();
      source.setLineVisible(false);
      AxisDescriptor target = new AxisDescriptor();
      target.setAuthorLineVisible(true);

      processor().copyAxisAttributes(target, source);

      assertFalse(target.isLineVisible());
      assertFalse(target.isUserLineVisible());
   }

   @Test
   void anAuthoredLineIsCopiedWithTheFlag() {
      AxisDescriptor source = new AxisDescriptor();
      source.setAuthorLineVisible(false);
      AxisDescriptor target = new AxisDescriptor();

      processor().copyAxisAttributes(target, source);

      assertFalse(target.isLineVisible());
      assertTrue(target.isUserLineVisible());
   }

   private static ChangeSeparateStatusProcessor processor() {
      return new ChangeSeparateStatusProcessor(new DefaultVSChartInfo(), new ChartDescriptor());
   }
}
