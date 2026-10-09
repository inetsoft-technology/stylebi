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
package inetsoft.report.composition.execution.reliability;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The growth gate of {@link RelSoakTest}: a column that only swings on a short tail is not a
 * leak, a column that grows steadily is. Rows are {@code [minutes, value]}.
 */
@Tag("core")
public class RelSoakGateTest {
   /**
    * The shape of the paranoid 20-min soak that failed on "node slots grow" with no leak
    * (Testing #77123): 19 samples 30 s apart, slots swinging between 2 and 8 while other builds
    * saturated the CPU. Its least-squares slope is above the limit, but not by 2 errors.
    */
   @Test
   public void swingingSlotsOnAShortTailAreNotGrowth() {
      double[] values = { 4, 8, 5, 3, 6, 8, 4, 2, 5, 8, 6, 3, 8, 7, 4, 6, 8, 5, 8 };
      List<double[]> samples = new ArrayList<>();

      for(int i = 0; i < values.length; i++) {
         samples.add(new double[] { 4.5 + 0.5 * i, values[i] });
      }

      assertTrue(RelSoakTest.slope(samples, 0, 1) >= 1.0, "the swing fits a slope above 1");
      assertFalse(RelSoakTest.grows(samples, 0, 1, 1.0));
   }

   /** A steady growth of 1.5 per 10 minutes with jitter is a leak. */
   @Test
   public void steadyGrowthIsGrowth() {
      double[] jitter = { 0.5, -0.5, 0.25, -0.25 };
      List<double[]> samples = new ArrayList<>();

      for(int i = 0; i < 40; i++) {
         double minutes = 0.5 * i;
         samples.add(new double[] { minutes, 2 + 0.15 * minutes + jitter[i % 4] });
      }

      assertTrue(RelSoakTest.grows(samples, 0, 1, 1.0));
   }

   /** A slope of 4 times the limit fails however noisy the column is. */
   @Test
   public void steepGrowthFailsWhateverItsError() {
      List<double[]> samples = new ArrayList<>();

      for(int i = 0; i < 12; i++) {
         double minutes = 0.5 * i;
         samples.add(new double[] { minutes, minutes + (i % 2 == 0 ? 4 : -4) });
      }

      double slope = RelSoakTest.slope(samples, 0, 1);
      assertTrue(slope >= 4.0, "slope " + slope);
      assertTrue(slope - 2 * RelSoakTest.se(samples, 0, 1) < 1.0, "the error alone excuses it");
      assertTrue(RelSoakTest.grows(samples, 0, 1, 1.0));
   }

   /** A flat column does not grow. */
   @Test
   public void flatColumnDoesNotGrow() {
      List<double[]> samples = new ArrayList<>();

      for(int i = 0; i < 30; i++) {
         samples.add(new double[] { 0.5 * i, 30 + (i % 3) });
      }

      assertFalse(RelSoakTest.grows(samples, 0, 1, 1.0));
   }
}
