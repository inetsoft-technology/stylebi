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

import inetsoft.graph.*;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.PointElement;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.visual.FormVO;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Builds a real EGraph with a point element and a Percentile target with Above/Below fills,
 * lays it out, and checks the Below fill rectangle ends at the lowest boundary (#79660).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TargetFormChartPathTest {
   private void run(boolean xField, boolean reversed, String tag) {
      Object[][] rows = new Object[101][];
      rows[0] = new Object[] { "x", "y" };

      for(int i = 1; i <= 100; i++) {
         rows[i] = new Object[] { (double) i, (double) i };
      }

      DefaultDataSet data = new DefaultDataSet(rows);
      EGraph graph = new EGraph();
      graph.addElement(new PointElement("x", "y"));
      String field = xField ? "x" : "y";
      LinearScale xs = new LinearScale("x");
      LinearScale ys = new LinearScale("y");
      xs.setMin(0);
      xs.setMax(100);
      ys.setMin(0);
      ys.setMax(100);
      (xField ? xs : ys).setReversed(reversed);
      graph.setScale("x", xs);
      graph.setScale("y", ys);

      TargetForm form = new TargetForm();
      form.setField(field);
      form.setMeasure(field);
      form.setXFieldTarget(xField);
      form.setStrategy(new PercentileStrategy(Arrays.asList(25.0, 50.0, 75.0)));
      form.setFillBelow(Color.RED);
      form.setFillAbove(Color.BLUE);
      form.setAlpha(100);
      form.setChartScope(true);
      graph.addForm(form);

      VGraph vg = Plotter.getPlotter(graph).plotAndLayout(data, 0, 0, 400, 400);
      Rectangle2D below = null;
      Rectangle2D above = null;

      for(int i = 0; i < vg.getVisualCount(); i++) {
         if(vg.getVisual(i) instanceof FormVO vo && vo.getForm() instanceof RectForm) {
            Color c = vo.getForm().getColor();

            if(Color.RED.equals(c)) {
               below = vo.getBounds();
            }
            else if(Color.BLUE.equals(c)) {
               above = vo.getBounds();
            }
         }
      }

      assertNotNull(below, tag);
      assertNotNull(above, tag);
      double bl = xField ? below.getWidth() : below.getHeight();
      double al = xField ? above.getWidth() : above.getHeight();
      // percentiles 25/50/75 of 1..100 split the plot into four bands of about a quarter each;
      // before the fix the below fill reached the highest boundary (about three quarters)
      assertEquals(al, bl, 6.0, tag);
      assertTrue(bl < 110, tag + " below fill must not cover the bands: " + bl);
   }

   @Test
   void belowFillCoversOnlyLowestBandY() {
      run(false, false, "y");
   }

   @Test
   void belowFillCoversOnlyLowestBandYReversed() {
      run(false, true, "y reversed");
   }

   @Test
   void belowFillCoversOnlyLowestBandXField() {
      run(true, false, "x field");
   }
}
