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
package inetsoft.uql.viewsheet.internal;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.graph.LegendsDescriptor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartGapResolverTest {
   private static final VizContext MODERN = VizContext.of(VizMark.MODERN_LIGHT);
   private static final VizContext LEGACY = VizContext.LEGACY;

   @Test
   void labelGapTakesTheSpecValueWhenModernAndUnset() {
      assertEquals(8, VSChartChromeDefaults.resolveAxisLabelGap(0, MODERN));
   }

   @Test
   void labelGapIsUnchangedWhenLegacy() {
      assertEquals(0, VSChartChromeDefaults.resolveAxisLabelGap(0, LEGACY));
   }

   @Test
   void labelGapKeepsAnAuthorValue() {
      assertEquals(3, VSChartChromeDefaults.resolveAxisLabelGap(3, MODERN));
   }

   @Test
   void legendGapTakesTheTunableShareOfTheSpecValue() {
      // 16px total minus VGraph.GAP's fixed 2
      assertEquals(14, VSChartChromeDefaults.resolveLegendGap(0, false, MODERN));
   }

   @Test
   void legendGapIsUnchangedWhenLegacy() {
      assertEquals(0, VSChartChromeDefaults.resolveLegendGap(0, false, LEGACY));
   }

   @Test
   void legendGapKeepsAnAuthorValue() {
      assertEquals(20, VSChartChromeDefaults.resolveLegendGap(20, true, MODERN));
   }

   @Test
   void titleGapTakesFourWhenItsLabelsAreDrawn() {
      assertEquals(4, VSChartChromeDefaults.resolveAxisTitleGap(0, true, MODERN));
   }

   @Test
   void titleGapInheritsTheLabelGapWhenItsLabelsAreHidden() {
      // decision 5: the innermost visible band takes the plot-adjacent gap
      assertEquals(8, VSChartChromeDefaults.resolveAxisTitleGap(0, false, MODERN));
   }

   @Test
   void titleGapDoesNotInheritWhenLegacy() {
      // a legacy chart's title must not move when its labels are hidden
      assertEquals(0, VSChartChromeDefaults.resolveAxisTitleGap(0, false, LEGACY));
      assertEquals(0, VSChartChromeDefaults.resolveAxisTitleGap(0, true, LEGACY));
   }

   @Test
   void titleGapKeepsAnAuthorValueInBothStates() {
      assertEquals(5, VSChartChromeDefaults.resolveAxisTitleGap(5, true, MODERN));
      assertEquals(5, VSChartChromeDefaults.resolveAxisTitleGap(5, false, MODERN),
                   "inheritance must not override a value the author chose");
   }

   @Test
   void anUnchangedLegendGapIsNotWrittenBack() {
      // mirrors the dialog's write guard: the pane was shown the resolved 14, the author changed
      // nothing, and the descriptor must keep its 0 so the value goes on resolving
      LegendsDescriptor legends = new LegendsDescriptor();

      int shown = VSChartChromeDefaults.resolveLegendGap(
         legends.getGap(), legends.hasGapValue(), MODERN);
      applyLegendGap(legends, shown, MODERN);

      assertEquals(0, legends.getGap(), "the descriptor still carries no opinion");
      assertEquals(14, VSChartChromeDefaults.resolveLegendGap(
         legends.getGap(), legends.hasGapValue(), MODERN));
   }

   @Test
   void anEditedLegendGapIsStored() {
      LegendsDescriptor legends = new LegendsDescriptor();

      applyLegendGap(legends, 24, MODERN);

      assertEquals(24, legends.getGap());
      assertEquals(24, VSChartChromeDefaults.resolveLegendGap(
                      legends.getGap(), legends.hasGapValue(), MODERN),
                   "an author value is returned unchanged");
   }

   @Test
   void tickingFollowDefaultClearsTheStoredLegendGap() {
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.setGap(24);

      legends.resetGap(CompositeValue.Type.USER);

      assertEquals(0, legends.getGap(), "an unopinionated chart is back at the unset marker");
      assertEquals(14, VSChartChromeDefaults.resolveLegendGap(
                      legends.getGap(), legends.hasGapValue(), MODERN),
                   "clearing the author tier resolves again");
   }

   @Test
   void tickingFollowDefaultLeavesAStylesheetGapStanding() {
      // the defect this guards: pinning a USER 0 instead of clearing the tier would shadow a
      // format.css legend_gap for the life of the chart
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.setGap(20, CompositeValue.Type.CSS);
      legends.setGap(24);

      legends.resetGap(CompositeValue.Type.USER);

      assertEquals(20, legends.getGap(), "the stylesheet's gap comes back");
      assertEquals(20, VSChartChromeDefaults.resolveLegendGap(
                      legends.getGap(), legends.hasGapValue(), MODERN),
                   "and the resolver leaves it alone");
   }

   @Test
   void aDeliberateUserZeroLegendGapIsHonoured() {
      // the defect this fix exists for: typing 0 into the Legend Gap stepper must stay 0, not be
      // read as "unset" and substituted with 14
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.setGap(0);

      assertTrue(legends.hasGapValue(), "a USER-tier zero still carries an opinion");
      assertEquals(0, VSChartChromeDefaults.resolveLegendGap(
         legends.getGap(), legends.hasGapValue(), MODERN));
   }

   @Test
   void aCssLegendGapIsHonouredNotOverridden() {
      // the regression guard for the hasUserValue()-alone trap: CompositeValue tracks cssDefined
      // privately with no accessor, so a CSS-set gap must still be recognized via the value check
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.setGap(20, CompositeValue.Type.CSS);

      assertTrue(legends.hasGapValue(), "a non-zero CSS value implies an opinion");
      assertEquals(20, VSChartChromeDefaults.resolveLegendGap(
         legends.getGap(), legends.hasGapValue(), MODERN),
         "the CSS gap is honoured, not overridden by the resolver's default");
   }

   /** Mirrors LegendFormatDialogModel's write guard for the gap. */
   private static void applyLegendGap(LegendsDescriptor legends, int edited, VizContext ctx) {
      if(edited != VSChartChromeDefaults.resolveLegendGap(
         legends.getGap(), legends.hasGapValue(), ctx))
      {
         legends.setGap(edited);
      }
   }
}
