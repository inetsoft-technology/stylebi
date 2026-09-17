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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.graph.GraphTarget;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.report.composition.graph.StandardDeviationWrapper;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.adhoc.model.property.ColorInfo;
import inetsoft.web.adhoc.model.property.MeasureInfo;
import inetsoft.web.adhoc.model.property.TargetInfo;
import inetsoft.web.viewsheet.service.ChartPropertyService;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Characterizes the {@code TargetInfo} round trip that every chart property write goes through,
 * because two of its behaviours are load-bearing for {@link ChartTargetLineService} and
 * {@link AssemblyPropertyService} and neither is obvious from reading either caller.
 *
 * <p>These are deliberately written as characterization tests of the <em>existing</em>
 * {@code ChartPropertyService} rather than as assertions about the new service, so that anyone
 * who later decides the {@code changed = false} bookkeeping looks redundant finds out here what
 * it is protecting.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartPropertyServiceTargetRoundTripTest {
   /**
    * <b>Why untouched targets are written back unchanged.</b> A statistics target's labels are
    * joined with commas on read and re-escaped as one string on write, so re-deriving a target
    * nobody edited silently collapses two labels into one.
    */
   @Test
   void rederivingAStatisticsTargetCollapsesItsLabels() throws Exception {
      ChartPropertyService service = service();
      GraphTarget target = statisticsTarget("Low", "High");

      TargetInfo info = service.getTargetInfo(chartInfo(), target, false);
      assertEquals("Low,High", info.getLabelFormats(),
                   "read joins the labels with a comma -- the lossy step is on the way back");

      GraphTarget rewritten = statisticsTarget("Low", "High");
      service.updateStatLabel(info, rewritten);

      assertEquals(1, rewritten.getLabelFormats().length,
                   "two labels came back as one: the comma is escaped along with the rest, so " +
                   "the separator stops separating");
      assertEquals("Low,High", rewritten.getLabelFormats()[0].getDValue());
   }

   /** The same target, left unchanged, is skipped outright — nothing is re-derived. */
   @Test
   void anUnchangedTargetIsNotRederived() throws Exception {
      ChartPropertyService service = service();
      ChartDescriptor descriptor = new ChartDescriptor();
      GraphTarget target = statisticsTarget("Low", "High");
      target.setIndex(0);
      descriptor.addTarget(target);

      TargetInfo info = service.getTargetInfo(chartInfo(), target, false);
      info.setChanged(false);

      service.updateAllTargets(descriptor, new TargetInfo[]{ info });

      assertEquals(1, descriptor.getTargetCount());
      assertSame(target, descriptor.getTarget(0), "the descriptor's own object is left alone");
      assertEquals(2, target.getLabelFormats().length, "both labels survive");
   }

   /** A new target is appended, and only an {@code index} of -1 reaches {@code addTarget}. */
   @Test
   void onlyIndexMinusOneIsAppended() {
      ChartPropertyService service = service();
      ChartDescriptor descriptor = new ChartDescriptor();

      service.updateAllTargets(descriptor, new TargetInfo[]{ lineTarget(-1, "100") });

      assertEquals(1, descriptor.getTargetCount());
      assertEquals(0, descriptor.getTarget(0).getIndex());
   }

   /**
    * <b>The silent drop.</b> With no targets on the chart, an {@code index >= 0} builds a
    * {@code GraphTarget} and never adds it: {@code addTarget} is guarded on {@code index == -1}
    * alone. Characterized here so the service's "always append with -1" rule has a reason on
    * record rather than looking like defensive noise.
    */
   @Test
   void aStaleIndexOnAnEmptyChartIsSilentlyDropped() {
      ChartPropertyService service = service();
      ChartDescriptor descriptor = new ChartDescriptor();

      service.updateAllTargets(descriptor, new TargetInfo[]{ lineTarget(0, "100") });

      assertEquals(0, descriptor.getTargetCount(),
                   "the target was built and thrown away, and the caller was told nothing");
   }

   /** Appending leaves the existing descriptor objects, and their order, alone. */
   @Test
   void appendingDoesNotDisturbExistingTargets() {
      ChartPropertyService service = service();
      ChartDescriptor descriptor = new ChartDescriptor();
      GraphTarget first = new GraphTarget();
      first.setIndex(0);
      GraphTarget second = new GraphTarget();
      second.setIndex(1);
      descriptor.addTarget(first);
      descriptor.addTarget(second);

      TargetInfo unchangedFirst = lineTarget(0, "10");
      unchangedFirst.setChanged(false);
      TargetInfo unchangedSecond = lineTarget(1, "20");
      unchangedSecond.setChanged(false);

      service.updateAllTargets(
         descriptor, new TargetInfo[]{ unchangedFirst, unchangedSecond, lineTarget(-1, "30") });

      assertEquals(3, descriptor.getTargetCount());
      assertSame(first, descriptor.getTarget(0));
      assertSame(second, descriptor.getTarget(1));
      assertEquals(2, descriptor.getTarget(2).getIndex());
   }

   /** A tabFlag the dispatch does not know is not an error — it is nothing at all. */
   @Test
   void anUnknownTabFlagDoesNothing() {
      ChartPropertyService service = service();
      ChartDescriptor descriptor = new ChartDescriptor();
      TargetInfo info = lineTarget(-1, "100");
      info.setTabFlag(7);

      service.updateAllTargets(descriptor, new TargetInfo[]{ info });

      assertEquals(1, descriptor.getTargetCount());
      // A fresh GraphTarget already carries a default strategy, so "nothing happened" shows up
      // as the target's own fields never being set: updateTargetCommonInfo, which every one of
      // the three real branches runs first, would have copied the measure onto it.
      assertNull(descriptor.getTarget(0).getField(),
                 "updateTarget dispatched on nothing, so the appended target was never filled in");
   }

   /**
    * The four unguarded dereferences, pinned. The service builds every one of these from the
    * pane's {@code newTargetInfo} template precisely because a hand-built bean NPEs here.
    */
   @ParameterizedTest
   @ValueSource(strings = { "measure", "lineColor", "fillAboveColor", "fillBelowColor" })
   void aMissingBeanFieldNpesInsideTheCommit(String missing) {
      ChartPropertyService service = service();
      TargetInfo info = lineTarget(-1, "100");

      switch(missing) {
      case "measure" -> info.setMeasure(null);
      case "lineColor" -> info.setLineColor(null);
      case "fillAboveColor" -> info.setFillAboveColor(null);
      default -> info.setFillBelowColor(null);
      }

      assertThrows(NullPointerException.class,
                   () -> service.updateAllTargets(new ChartDescriptor(), new TargetInfo[]{ info }));
   }

   /** Removal is index-addressed, and the survivors are renumbered. */
   @Test
   void removeDropsOnlyTheNamedTargetAndReindexes() {
      ChartPropertyService service = service();
      ChartDescriptor descriptor = new ChartDescriptor();
      GraphTarget first = new GraphTarget();
      first.setIndex(0);
      GraphTarget second = new GraphTarget();
      second.setIndex(1);
      GraphTarget third = new GraphTarget();
      third.setIndex(2);
      descriptor.addTarget(first);
      descriptor.addTarget(second);
      descriptor.addTarget(third);

      service.removeDeletedTargets(descriptor, new Integer[]{ 1 });

      assertEquals(2, descriptor.getTargetCount());
      assertSame(first, descriptor.getTarget(0));
      assertSame(third, descriptor.getTarget(1));
      assertEquals(0, descriptor.getTarget(0).getIndex());
      assertEquals(1, descriptor.getTarget(1).getIndex());
   }

   private static ChartPropertyService service() {
      return new ChartPropertyService(mock(VisualFrameModelFactoryService.class));
   }

   /** A statistics target carrying two distinct labels. */
   private static GraphTarget statisticsTarget(String first, String second) {
      GraphTarget target = new GraphTarget();
      target.setStrategy(new StandardDeviationWrapper(true, "1", "2"));
      target.setLabelFormats(new DynamicValue[]{ new DynamicValue(first),
                                                 new DynamicValue(second) });
      return target;
   }

   /** A line target filled the way the commit path needs it: nothing null, alpha numeric. */
   private static TargetInfo lineTarget(int index, String value) {
      TargetInfo info = new TargetInfo();
      info.setTabFlag(TargetInfo.LINE_TARGET);
      info.setIndex(index);
      info.setChanged(true);
      info.setValue(value);
      info.setLabel("{0}");
      info.setAlpha("100");
      info.setMeasure(new MeasureInfo("Sum(Total)", "Sum(Total)", false));
      info.setLineColor(new ColorInfo("", ChartPropertyService.COLOR_PALETTE));
      info.setFillAboveColor(new ColorInfo("", ChartPropertyService.COLOR_PALETTE));
      info.setFillBelowColor(new ColorInfo("", ChartPropertyService.COLOR_PALETTE));
      return info;
   }

   /** Enough of a chart for {@code getMeasures} to run; the measure list itself is not the point. */
   private static VSChartInfo chartInfo() {
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.getModelRefsX(anyBoolean())).thenReturn(new ChartRef[0]);
      when(info.getModelRefsY(anyBoolean())).thenReturn(new ChartRef[0]);
      when(info.getXFields()).thenReturn(new ChartRef[0]);
      when(info.getYFields()).thenReturn(new ChartRef[0]);
      when(info.getRTXFields()).thenReturn(new ChartRef[0]);
      when(info.getRTYFields()).thenReturn(new ChartRef[0]);
      return info;
   }
}
