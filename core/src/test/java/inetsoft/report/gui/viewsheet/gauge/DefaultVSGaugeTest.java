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
package inetsoft.report.gui.viewsheet.gauge;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.GaugeVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VBM-006: a trailing blank/NaN {@code rangeValues} boundary (e.g.
 * {@code rangeValues=[60,90,"",""]}) used to collapse the last color band to a zero-width arc
 * that painted nothing, because {@code fillRanges0} treated any NaN boundary as "repeat the
 * previous boundary" with no distinction from a genuine interior gap.
 *
 * <p>Constructing a real {@code GaugeVSAssemblyInfo} runs {@code VSAssemblyInfo}'s constructor,
 * which reads {@code SreeEnv} and therefore needs a Spring context -- hence the harness
 * annotations, which mirror {@code ViewsheetReadServiceTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class DefaultVSGaugeTest {
   @Test
   void trailingNaNBoundaryExtendsTheLastColorBandToMax() {
      RecordingGauge gauge = gaugeWithMinMax(0, 200);

      gauge.fillRanges0(null,
                        new double[]{ 60, 90, Double.NaN, Double.NaN },
                        new Color[]{ Color.RED, Color.YELLOW, Color.GREEN, null },
                        false);

      assertEquals(4, gauge.calls.size());
      // band index 2 (green) must now stretch from the previous boundary (90) to max (200),
      // not collapse to a zero-width arc the way it did before this fix.
      assertNotEquals(0d, gauge.calls.get(2).delta(), 1e-9,
                       "the last populated color band must extend to the gauge's own max");
      // the extra padding band (index 3, no real color pair) still contributes nothing visible.
      assertEquals(0d, gauge.calls.get(3).delta(), 1e-9);
   }

   @Test
   void interiorNaNBoundaryStillCollapsesToZero() {
      RecordingGauge gauge = gaugeWithMinMax(0, 200);

      gauge.fillRanges0(null,
                        new double[]{ 60, Double.NaN, 150 },
                        new Color[]{ Color.RED, Color.YELLOW, Color.GREEN, null },
                        false);

      assertEquals(3, gauge.calls.size());
      // a blank boundary followed by a later, real boundary is a malformed config, not an
      // "extend to max" request -- it must keep collapsing to zero, not be silently resolved.
      assertEquals(0d, gauge.calls.get(1).delta(), 1e-9,
                   "an interior gap must not be silently resolved to a nonzero band");
   }

   @Test
   void allBlankRangeValuesExtendsTheSingleBandToMax() {
      RecordingGauge gauge = gaugeWithMinMax(0, 200);

      gauge.fillRanges0(null,
                        new double[]{ Double.NaN },
                        new Color[]{ Color.RED, null },
                        false);

      assertEquals(1, gauge.calls.size());
      assertNotEquals(0d, gauge.calls.get(0).delta(), 1e-9,
                       "a single all-blank boundary must cover the whole min..max span");
   }

   private static RecordingGauge gaugeWithMinMax(double min, double max) {
      GaugeVSAssemblyInfo info = new GaugeVSAssemblyInfo();
      info.setMin(String.valueOf(min));
      info.setMax(String.valueOf(max));

      RecordingGauge gauge = new RecordingGauge();
      gauge.setAssemblyInfo(info);
      // a real gauge face parses this out of gauge.xml; a nonzero sweep angle is needed so the
      // arithmetic under test (rangeDelta -> nonzero vs. zero angular delta) is observable.
      gauge.angle = Math.toRadians(270);

      return gauge;
   }

   /** Records each band's begin/end radian instead of actually painting, so the resolved
    *  {@code rangeEnd}/{@code rangeDelta} arithmetic can be asserted on directly. */
   private static class RecordingGauge extends DefaultVSGauge {
      final List<Call> calls = new ArrayList<>();

      @Override
      protected void fillRange(Graphics2D g, Point2D center, double beginRadian,
                               double endRadian, Color color1, Color color2, double radius,
                               double rangeWidth, boolean leftRound, boolean rightRound,
                               boolean gradient)
      {
         calls.add(new Call(beginRadian, endRadian));
      }
   }

   private record Call(double beginRadian, double endRadian) {
      double delta() {
         return beginRadian - endRadian;
      }
   }
}
