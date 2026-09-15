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
package inetsoft.web.viewsheet.service;

import inetsoft.graph.aesthetic.StaticColorFrame;
import inetsoft.report.composition.graph.GraphTarget;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.web.adhoc.model.property.*;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for seeding a new target's band fill with its measure's companion colour. The Edit
 * Target dialog shows the band fill as an editable swatch, so the value it displays must be the
 * value the chart draws - the companion is a default, never a render-time substitution.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartPropertyServiceCompanionSeedTest {
   @Test
   void aModernChartSeedsTheCompanionRatherThanTheClassicGreen() {
      GraphTarget target = new GraphTarget();
      service().seedCompanionBandFill(measureChart(AZURE), target, modern(), false);

      Color seeded = target.getBandFill().getColor(0);
      assertNotNull(seeded);
      assertNotEquals(CLASSIC_FIRST_BAND, seeded, "the classic green must be replaced");
      assertEquals(AZURE_COMPANION, seeded, "slot 0 is Azure's authored companion");
   }

   @Test
   void allFourBandSlotsAreSeeded() {
      GraphTarget target = new GraphTarget();
      service().seedCompanionBandFill(measureChart(AZURE), target, modern(), false);

      for(int i = 0; i < 4; i++) {
         assertNotNull(target.getBandFill().getColor(i), "band slot " + i);
      }

      // the ladder walks toward the base, so consecutive slots must differ
      assertNotEquals(target.getBandFill().getColor(0), target.getBandFill().getColor(1));
   }

   @Test
   void aClassicChartKeepsTheGreens() {
      GraphTarget target = new GraphTarget();
      service().seedCompanionBandFill(measureChart(AZURE), target, VizContext.LEGACY, false);

      assertEquals(CLASSIC_FIRST_BAND, target.getBandFill().getColor(0));
   }

   @Test
   void colourBoundToADimensionKeepsTheGreens() {
      // the reported failure: the Y field IS an aggregate, so an "is it an aggregate" guard passes
      // and seeds a companion - but the marks are painted by Category and one band spans all of
      // them, so there is no single measure colour and the classic fill must survive
      VSChartInfo info = measureChart(AZURE);
      info.setColorField(dimensionColour("Category"));

      GraphTarget target = new GraphTarget();
      service().seedCompanionBandFill(info, target, modern(), false);

      assertEquals(CLASSIC_FIRST_BAND, target.getBandFill().getColor(0),
                   "a dimension-coloured chart must keep the classic band");
   }

   @Test
   void colourBoundToADimensionOnTheAggregateKeepsTheGreens() {
      // under multi-aesthetic binding the chart-level ref is empty and the real binding hangs off
      // the aggregate, so checking only the chart level would miss it
      VSChartInfo info = measureChart(AZURE);
      ((ChartAggregateRef) info.getYField(0)).setColorField(dimensionColour("Category"));

      GraphTarget target = new GraphTarget();
      service().seedCompanionBandFill(info, target, modern(), false);

      assertEquals(CLASSIC_FIRST_BAND, target.getBandFill().getColor(0));
   }

   @Test
   void aChartWhoseFirstFieldIsNotAnAggregateKeepsTheGreens() {
      // stands in for colour bound to a dimension: one band spans many series colours, so there
      // is no single measure colour to companion and the classic fill must survive
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      info.setRTChartType(GraphTypes.CHART_BAR);
      info.addXField(new VSChartDimensionRef(new BaseField("State")));
      info.updateChartType(false);

      GraphTarget target = new GraphTarget();
      service().seedCompanionBandFill(info, target, modern(), false);

      assertEquals(CLASSIC_FIRST_BAND, target.getBandFill().getColor(0));
   }

   @Test
   void anExistingTargetIsNeverReseeded() {
      // the regression guard for the rule that an author's stored band fill is theirs:
      // updateAllTargets seeds only the targets it creates, so editing one already in the
      // descriptor leaves every slot as the author left it
      GraphTarget existing = new GraphTarget();
      existing.setIndex(0);

      for(int i = 0; i < AUTHOR_PICKED.length; i++) {
         existing.getBandFill().setColor(i, AUTHOR_PICKED[i]);
      }

      ChartDescriptor descriptor = new ChartDescriptor();
      descriptor.addTarget(existing);

      service().updateAllTargets(descriptor, new TargetInfo[]{ bandEditOf(0) }, measureChart(AZURE),
                                 modern(), false);

      for(int i = 0; i < AUTHOR_PICKED.length; i++) {
         assertEquals(AUTHOR_PICKED[i], descriptor.getTarget(0).getBandFill().getColor(i),
                      "band slot " + i + " belongs to its author");
      }
   }

   @Test
   void aTargetBeingCreatedIsSeeded() {
      // the other half of the same guard: index -1 is the Add path, where the companion belongs
      ChartDescriptor descriptor = new ChartDescriptor();
      service().updateAllTargets(descriptor, new TargetInfo[]{ bandEditOf(-1) },
                                 measureChart(AZURE), modern(), false);

      assertEquals(1, descriptor.getTargetCount());
      assertEquals(AZURE_COMPANION, descriptor.getTarget(0).getBandFill().getColor(0));
   }

   /**
    * A band target the dialog reopened and saved without touching its colours - every ColorInfo is
    * left unset so nothing in the update path writes a colour of its own, and any change to the
    * stored band fill can only have come from seeding.
    */
   private static TargetInfo bandEditOf(int index) {
      TargetInfo info = new TargetInfo();
      info.setIndex(index);
      info.setChanged(true);
      info.setTabFlag(TargetInfo.BAND_TARGET);
      info.setMeasure(new MeasureInfo("Total", "Total", false));
      info.setLineColor(new ColorInfo());
      info.setFillAboveColor(new ColorInfo());
      info.setFillBelowColor(new ColorInfo());
      info.setFillBandColor(new ColorInfo());

      return info;
   }

   private static VSChartInfo measureChart(Color measureColor) {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setSeparatedGraph(true);
      info.setMultiStyles(false);
      info.setChartType(GraphTypes.CHART_BAR);
      info.setRTChartType(GraphTypes.CHART_BAR);
      info.addXField(new VSChartDimensionRef(new BaseField("State")));

      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setDataRef(new BaseField("Total"));
      StaticColorFrame frame = new StaticColorFrame();
      frame.setUserColor(measureColor);
      ref.setColorFrame(frame);
      info.addYField(ref);
      info.updateChartType(false);

      return info;
   }

   private static VSAestheticRef dimensionColour(String name) {
      VSAestheticRef aref = new VSAestheticRef();
      aref.setDataRef(new VSChartDimensionRef(new BaseField(name)));
      return aref;
   }

   private static VizContext modern() {
      return VizContext.of(VizMark.MODERN_LIGHT);
   }

   private static ChartPropertyService service() {
      // seedCompanionBandFill never touches the model factories, so an empty registry is enough
      return new ChartPropertyService(
         new VisualFrameModelFactoryService(java.util.Collections.emptyList()));
   }

   private static final Color AZURE = new Color(0x0490FF);
   private static final Color AZURE_COMPANION = new Color(0x97BEEB);
   private static final Color CLASSIC_FIRST_BAND = new Color(0xe4f2e9);
   private static final Color[] AUTHOR_PICKED = {
      new Color(0x123456), new Color(0x234567), new Color(0x345678), new Color(0x456789)
   };
}
