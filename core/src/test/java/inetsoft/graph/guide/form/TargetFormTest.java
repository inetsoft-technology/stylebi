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
package inetsoft.graph.guide.form;

import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.scale.LinearScale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the fill geometry built by {@link TargetForm}, in particular that the Below fill
 * ends at the lowest boundary and the Above fill starts at the highest boundary.
 */
@Tag("core")
class TargetFormTest {
   private static final double[] BOUNDARIES = { 25.25, 50.5, 75.75 };

   @Test
   void belowFillEndsAtLowestBoundaryYNormal() throws Exception {
      LinearScale y = new LinearScale("m");
      Object[][] rects = fills(false, y, new LinearScale("x"), BOUNDARIES);

      // below: topLeft y is the boundary; above: bottomRight y is the boundary
      assertEquals(25.25, (Double) rects[0][1], 0.0);
      assertEquals(75.75, (Double) rects[1][3], 0.0);
   }

   @Test
   void belowFillEndsAtLowestBoundaryYReversed() throws Exception {
      LinearScale y = new LinearScale("m");
      y.setReversed(true);
      Object[][] rects = fills(false, y, new LinearScale("x"), BOUNDARIES);

      // inverted below: [MIN, MAX] -> [MAX, min]
      assertEquals(25.25, (Double) rects[0][3], 0.0);
      // inverted above: [MIN, max] -> [MAX, MIN]
      assertEquals(75.75, (Double) rects[1][1], 0.0);
   }

   @Test
   void belowFillEndsAtLowestBoundaryXField() throws Exception {
      LinearScale x = new LinearScale("m");
      Object[][] rects = fills(true, new LinearScale("y"), x, BOUNDARIES);

      // x-field below: bottomRight x is the boundary; above: topLeft x is the boundary
      assertEquals(25.25, (Double) rects[0][2], 0.0);
      assertEquals(75.75, (Double) rects[1][0], 0.0);
   }

   @Test
   void singleBoundaryUnchanged() throws Exception {
      Object[][] rects = fills(false, new LinearScale("m"), new LinearScale("x"),
                               new double[] { 42 });

      assertEquals(42.0, (Double) rects[0][1], 0.0);
      assertEquals(42.0, (Double) rects[1][3], 0.0);
   }

   /**
    * Calls the private generateSubForms and returns, for the below fill [0] and the above
    * fill [1], the flattened {topLeft0, topLeft1, bottomRight0, bottomRight1} values.
    */
   private Object[][] fills(boolean xField, LinearScale yscale, LinearScale xscale,
                            double[] boundaries) throws Exception
   {
      TargetForm form = new TargetForm();
      form.setField("m");
      form.setXFieldTarget(xField);
      form.setFillBelow(Color.BLUE);
      form.setFillAbove(Color.RED);
      form.setLine(0); // GraphConstants.NONE, only the fills matter
      form.setBandColorFrame(new inetsoft.graph.aesthetic.CategoricalColorFrame());

      RectCoord coord = new RectCoord(xscale, yscale);
      Method m = TargetForm.class.getDeclaredMethod(
         "generateSubForms", inetsoft.graph.coord.Coordinate.class, double[].class,
         String[].class, MessageFormat[].class);
      m.setAccessible(true);
      Collection<?> forms = (Collection<?>) m.invoke(form, coord, boundaries, null, null);

      Object[][] result = new Object[2][];

      for(Object f : forms) {
         RectForm rect = (RectForm) f;

         if(Color.BLUE.equals(rect.getColor())) {
            result[0] = flatten(rect);
         }
         else if(Color.RED.equals(rect.getColor())) {
            result[1] = flatten(rect);
         }
      }

      assertNotNull(result[0], "below fill missing");
      assertNotNull(result[1], "above fill missing");
      return result;
   }

   private Object[] flatten(RectForm rect) {
      Object[] tl = rect.getTopLeftValues();
      Object[] br = rect.getBottomRightValues();
      return new Object[] { tl[0], tl[1], br[0], br[1] };
   }
}
