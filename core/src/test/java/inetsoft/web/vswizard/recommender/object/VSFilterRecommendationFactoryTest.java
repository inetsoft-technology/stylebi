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
package inetsoft.web.vswizard.recommender.object;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.vswizard.model.VSWizardData;
import inetsoft.web.vswizard.model.recommender.VSFilterRecommendation;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for Bug #76497: the Filter recommendation's {@code DataRef[]}
 * used to be built with one slot per X/Y field already bound on the temp
 * chart, positionally matched against the selected tree entries. When a chart
 * already had 2+ bound fields (e.g. an X dimension and a Y measure) but only
 * one entry was selected in the Filter step, unmatched slots came back null,
 * and if the matched field wasn't first in the X+Y order, {@code refs[0]} was
 * null -- causing an NPE downstream in {@code addFilterVSAssembly()} that was
 * silently swallowed, leaving the wizard preview canvas blank.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSFilterRecommendationFactoryTest {
   @Test
   void createDataRefsReturnsOneNonNullRefPerSelectedEntry_evenWithMultipleBoundChartFields() {
      // temp chart already has an X (dimension) and a Y (measure) bound --
      // this is what "bind a measure column to a chart" sets up.
      VSChartDimensionRef xField = new VSChartDimensionRef();
      xField.setGroupColumnValue("category");

      VSChartAggregateRef yField = new VSChartAggregateRef();
      yField.setColumnValue("sales");

      VSChartInfo chartInfo = new VSChartInfo();
      chartInfo.addXField(xField);
      chartInfo.addYField(yField);

      ChartVSAssembly tempChart = new ChartVSAssembly();
      tempChart.setVSChartInfo(chartInfo);

      VSTemporaryInfo temporaryInfo = new VSTemporaryInfo();
      temporaryInfo.setTempChart(tempChart);

      // only one entry selected in the Filter step's tree -- the measure.
      AssetEntry selected = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.COLUMN, "/sales", null);
      selected.setProperty("attribute", "sales");
      selected.setProperty("caption", "Sales");
      selected.setProperty("dtype", XSchema.DOUBLE);

      VSWizardData wizardData = new VSWizardData(new AssetEntry[] { selected }, temporaryInfo);

      VSFilterRecommendationFactory factory = new VSFilterRecommendationFactory();
      VSFilterRecommendation recommendation = factory.recommend(wizardData, null);

      DataRef[] refs = recommendation.getDataRefs();
      assertNotNull(refs);
      assertEquals(1, refs.length);
      assertNotNull(refs[0], "refs[0] must not be null -- addFilterVSAssembly() "
         + "dereferences it unconditionally for Range Slider/Selection List");
   }

   @Test
   void createDataRefsReturnsOneNonNullRefPerSelectedEntry_forMultiEntrySelectionTree() {
      AssetEntry entry1 = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.COLUMN, "/region", null);
      entry1.setProperty("attribute", "region");
      entry1.setProperty("caption", "Region");
      entry1.setProperty("dtype", XSchema.STRING);

      AssetEntry entry2 = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.COLUMN, "/city", null);
      entry2.setProperty("attribute", "city");
      entry2.setProperty("caption", "City");
      entry2.setProperty("dtype", XSchema.STRING);

      VSTemporaryInfo temporaryInfo = new VSTemporaryInfo();
      temporaryInfo.setTempChart(new ChartVSAssembly());

      VSWizardData wizardData =
         new VSWizardData(new AssetEntry[] { entry1, entry2 }, temporaryInfo);

      VSFilterRecommendationFactory factory = new VSFilterRecommendationFactory();
      VSFilterRecommendation recommendation = factory.recommend(wizardData, null);

      DataRef[] refs = recommendation.getDataRefs();
      assertNotNull(refs);
      assertEquals(2, refs.length);
      assertNotNull(refs[0]);
      assertNotNull(refs[1]);
   }
}
