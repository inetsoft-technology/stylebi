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
package inetsoft.web.wiz.binding;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code ChartAestheticAgentService.perMeasureFrameChannels} — the set
 * {@link ChartAestheticMutator} gates its per-measure frame write (and its matching read) on.
 *
 * <p>It has to be the same answer {@code VSFrameVisitor}'s four strategies get from
 * {@code supportsFieldFrame()}. Getting it wrong in either direction puts the frame in a slot the
 * renderer does not read — the defect the per-measure branch exists to fix, mirrored onto a
 * different set of charts.
 *
 * <p>Its own class rather than a section of {@code ChartAestheticAgentServiceTest}, because these
 * build real {@code VSChartInfo} objects and so need the SREE context that test deliberately does
 * without.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PerMeasureFrameChannelsTest {
   @Test
   void anOrdinaryChartRendersEveryFrameChannelPerMeasure() {
      assertEquals(Set.of("color", "shape", "line", "texture", "size"),
                   channelsFor(new DefaultVSChartInfo()));
   }

   /**
    * {@code MergedVSChartInfo} answers false for colour and shape (and therefore for line and
    * texture, whose strategies defer to {@code supportsShapeFieldFrame()}); {@code
    * CandleVSChartInfo} also overrides size. Nothing is per measure, so everything goes to the
    * chart-level slot — which for a MergedChartInfo is exactly what {@code createFrame()} falls
    * back to.
    */
   @Test
   void aCandleChartRendersNothingPerMeasure() {
      assertEquals(Set.of(), channelsFor(new CandleVSChartInfo()));
   }

   /**
    * The case that forced this to be asked of the info rather than derived from the chart type:
    * {@code RadarVSChartInfo.supportsShapeFieldFrame()} consults the plot descriptor, so the same
    * {@code CHART_RADAR} answers differently for a point radar and a line radar. Colour and size
    * are false either way.
    */
   @Test
   void aPointRadarAndALineRadarDisagreeOnTheSameChartType() {
      assertEquals(Set.of("shape", "line", "texture"), channelsFor(radar(true)),
                   "a point radar renders shape/line/texture per measure");
      assertEquals(Set.of(), channelsFor(radar(false)),
                   "a line radar does not, and deriving the set from CHART_RADAR alone would " +
                   "have given one of the two the other's answer");
   }

   /**
    * A contour chart is the {@code AbstractChartInfo} branch: false for all three. (It is also the
    * one chart where the chart-level slot is not a working fallback either — see
    * {@code ChartAestheticMutator.setFrame}'s javadoc — but that is not this method's problem.)
    */
   @Test
   void aScatterContourRendersNothingPerMeasure() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_SCATTER_CONTOUR);
      assertEquals(Set.of(), channelsFor(info));
   }

   /**
    * Gantt overrides all three predicates back to {@code true}, so it renders per measure like an
    * ordinary chart even though it is a {@code MergedVSChartInfo}. Answering otherwise would send
    * the write to the chart-level slot, which a Gantt chart never reads. Where its measures
    * actually live is {@code ChartAestheticMutator.aggregates}' problem, not this method's — see
    * {@code ChartAestheticMutatorTest.setsAFieldLessFrameOnAGanttChartsStartFieldNotItsYShelf}.
    */
   @Test
   void aGanttChartRendersEveryFrameChannelPerMeasure() {
      assertEquals(Set.of("color", "shape", "line", "texture", "size"),
                   channelsFor(new GanttVSChartInfo()));
   }

   @Test
   void aChartWithNoBindingYetRendersNothingPerMeasure() {
      assertEquals(Set.of(), channelsFor(null));
   }

   private static RadarVSChartInfo radar(boolean pointLine) {
      RadarVSChartInfo info = new RadarVSChartInfo();
      info.setChartType(GraphTypes.CHART_RADAR);
      ChartDescriptor descriptor = new ChartDescriptor();
      descriptor.getPlotDescriptor().setPointLine(pointLine);
      info.setChartDescriptor(descriptor);
      return info;
   }

   private static Set<String> channelsFor(VSChartInfo info) {
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      return new LinkedHashSet<>(ChartAestheticAgentService.perMeasureFrameChannels(chart));
   }
}
