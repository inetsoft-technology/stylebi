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
package inetsoft.web.viewsheet.service;

import inetsoft.report.composition.graph.GraphTarget;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.adhoc.model.property.MeasureInfo;
import inetsoft.web.adhoc.model.property.TargetInfo;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * A Gantt chart supports targets on its time axis only: the dates of its start, end and milestone
 * fields share that axis, so the Targets pane offers it as the one measure, as a date.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartPropertyServiceGanttTargetTest {
   @Test
   void aGanttSupportsTargets() {
      assertTrue(service().supportsTarget(gantt(), ChartPropertyService.NO_TARGET_STYLES));
   }

   @Test
   void theTimeAxisIsTheOnlyMeasureAndIsADate() throws Exception {
      MeasureInfo[] measures = service().getMeasures(gantt(), false);

      assertEquals(1, measures.length);
      assertEquals(GraphTarget.GANTT_TIME_AXIS, measures[0].getName());
      assertTrue(measures[0].isDateField());
   }

   @Test
   void aNewTargetStartsOnTheTimeAxis() throws Exception {
      TargetInfo info = service().getTargetInfo(gantt(), new GraphTarget(), false);

      assertEquals(GraphTarget.GANTT_TIME_AXIS, info.getMeasure().getName());
      assertTrue(info.getMeasure().isDateField());
   }

   @Test
   void otherChartsStillOfferTheirOwnMeasures() throws Exception {
      VSChartInfo bar = new DefaultVSChartInfo();
      bar.setChartType(GraphTypes.CHART_BAR);
      MeasureInfo[] measures = service().getMeasures(bar, false);

      // the blank "no measure" entry, and nothing of the Gantt's time axis
      assertEquals(1, measures.length);
      assertEquals("", measures[0].getName());
   }

   private static VSChartInfo gantt() {
      VSChartInfo info = new GanttVSChartInfo();
      info.setChartType(GraphTypes.CHART_GANTT);
      info.setRTChartType(GraphTypes.CHART_GANTT);
      return info;
   }

   private static ChartPropertyService service() {
      return new ChartPropertyService(mock(VisualFrameModelFactoryService.class));
   }
}
