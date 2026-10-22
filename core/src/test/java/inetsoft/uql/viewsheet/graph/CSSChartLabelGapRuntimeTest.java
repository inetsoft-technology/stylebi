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
package inetsoft.uql.viewsheet.graph;

import inetsoft.report.internal.binding.BaseField;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.util.DataSpace;
import inetsoft.util.css.CSSDictionary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A stylesheet axis label_gap must reach the runtime axis descriptors, which render in place of
 * the design ones and may be cloned before the CSS pass. Each stylesheet is a real dataspace file
 * in the portal directory, parsed by CSSDictionary as a chart render's format.css is. Each is
 * written once under its own name, because a rewritten or deleted file re-initializes earlier
 * dictionaries on the blob event thread, which can recreate an empty file under the next test.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CSSChartLabelGapRuntimeTest {
   @AfterEach
   void cleanup() {
      // drop the dictionaries' file listeners before the files go
      CSSDictionary.resetDictionaryCache();
      DataSpace space = DataSpace.getDataSpace();

      for(String file : written) {
         if(space.exists(CSS_DIR, file)) {
            space.delete(CSS_DIR, file);
         }
      }

      written.clear();
   }

   @Test
   void aStylesheetGapReachesTheRuntimeDescriptors() throws Exception {
      VSChartInfo info = chartWithRuntimeDescriptors(GraphTypes.CHART_BAR, true);
      VSChartDimensionRef dim = (VSChartDimensionRef) info.getXField(0);

      applyStylesheet(info, AXIS_GAPS);

      // the design descriptors prove the stylesheet was read
      assertEquals(20, dim.getAxisDescriptor().getLabelGap(), "design x dimension axis");
      assertEquals(16, info.getAxisDescriptor().getLabelGap(), "design measure axis");
      assertEquals(16, info.getAxisDescriptor2().getLabelGap(), "design secondary axis");

      assertEquals(20, dim.getRTAxisDescriptor().getLabelGap(), "runtime x dimension axis");
      assertEquals(16, info.getRTAxisDescriptor().getLabelGap(), "runtime measure axis");
      assertEquals(16, info.getRTAxisDescriptor2().getLabelGap(), "runtime secondary axis");
   }

   @Test
   void droppingTheStylesheetGapClearsTheRuntimeDescriptors() throws Exception {
      VSChartInfo info = chartWithRuntimeDescriptors(GraphTypes.CHART_BAR, true);
      VSChartDimensionRef dim = (VSChartDimensionRef) info.getXField(0);

      applyStylesheet(info, AXIS_GAPS);
      assertEquals(20, dim.getRTAxisDescriptor().getLabelGap(), "the gap reached runtime first");
      assertEquals(16, info.getRTAxisDescriptor().getLabelGap(), "the gap reached runtime first");
      assertEquals(16, info.getRTAxisDescriptor2().getLabelGap(), "the gap reached runtime first");

      applyStylesheet(info, NO_GAPS);

      assertEquals(0, dim.getAxisDescriptor().getLabelGap(), "design x dimension axis");
      assertEquals(0, dim.getRTAxisDescriptor().getLabelGap(), "runtime x dimension axis");
      assertEquals(0, info.getRTAxisDescriptor().getLabelGap(), "runtime measure axis");
      assertEquals(0, info.getRTAxisDescriptor2().getLabelGap(), "runtime secondary axis");
   }

   @Test
   void aMekkoValueAxisFollowsTheYAxisGapAtRuntime() throws Exception {
      VSChartInfo info = chartWithRuntimeDescriptors(GraphTypes.CHART_MEKKO, false);
      // no ref maps to the chart-level axis, so only the chart-wide reset clears it
      info.setSeparatedGraph(true);

      applyStylesheet(info, AXIS_GAPS);
      assertEquals(16, info.getAxisDescriptor().getLabelGap(), "design value axis");
      assertEquals(16, info.getRTAxisDescriptor().getLabelGap(), "runtime value axis");

      applyStylesheet(info, NO_GAPS);
      assertEquals(0, info.getAxisDescriptor().getLabelGap(), "design value axis cleared");
      assertEquals(0, info.getRTAxisDescriptor().getLabelGap(), "runtime value axis cleared");
   }

   /** A chart whose runtime descriptors were cloned before the CSS pass, as a script does. */
   private static VSChartInfo chartWithRuntimeDescriptors(int type, boolean secondary) {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setSeparatedGraph(false);
      info.setMultiStyles(false);
      info.setChartType(type);
      info.setRTChartType(type);

      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField("State"));
      info.addXField(dim);
      info.addYField(measure("Sales", false));

      if(secondary) {
         info.addYField(measure("Profit", true));
      }

      info.setRTAxisDescriptor(info.getAxisDescriptor().clone());
      info.setRTAxisDescriptor2(info.getAxisDescriptor2().clone());
      dim.setRTAxisDescriptor(dim.getAxisDescriptor().clone());

      return info;
   }

   private static VSChartAggregateRef measure(String name, boolean secondary) {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setDataRef(new BaseField(name));
      ref.setSecondaryY(secondary);
      return ref;
   }

   private void applyStylesheet(VSChartInfo info, String css) throws IOException {
      String file = "label-gap-runtime-" + NEXT_FILE.getAndIncrement() + ".css";
      written.add(file);
      DataSpace.getDataSpace().withOutputStream(
         CSS_DIR, file, out -> out.write(css.getBytes(StandardCharsets.UTF_8)));

      CSSDictionary dict = CSSDictionary.getDictionary(CSS_DIR, file, false);
      CSSChartStyles.apply(new ChartDescriptor(), info, dict, null, VizContext.LEGACY);
   }

   private static final String CSS_DIR = "portal";
   private static final AtomicInteger NEXT_FILE = new AtomicInteger();
   private static final String AXIS_GAPS =
      "ChartAxisLabels[axis='x'] { label_gap: 20; }\n" +
      "ChartAxisLabels[axis='y'] { label_gap: 16; }\n";
   private static final String NO_GAPS =
      "ChartAxisLabels[axis='x'] { label_rotation: 0; }\n" +
      "ChartAxisLabels[axis='y'] { label_rotation: 0; }\n";

   private final List<String> written = new ArrayList<>();
}
