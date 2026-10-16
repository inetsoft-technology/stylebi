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
package inetsoft.graph.guide.form;

import inetsoft.graph.*;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.visual.FormVO;
import inetsoft.report.composition.graph.DynamicLineWrapper;
import inetsoft.report.composition.graph.GraphTarget;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78583: form alpha is a percentage (0-100). An out-of-range value used to be accepted by
 * the setter and then fail the chart render in FormVO.paint.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class GraphFormAlphaTest {
   @ParameterizedTest
   @ValueSource(ints = { -1, 101, 255 })
   void setAlphaRejectsOutOfRange(int alpha) {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                 () -> new RectForm().setAlpha(alpha));
      assertTrue(ex.getMessage().contains("0-100"), ex.getMessage());

      // TargetForm overrides setAlpha without calling super
      assertThrows(IllegalArgumentException.class, () -> new TargetForm().setAlpha(alpha));
   }

   @ParameterizedTest
   @ValueSource(ints = { 0, 50, 100 })
   void setAlphaAcceptsPercent(int alpha) {
      RectForm form = new RectForm();
      form.setAlpha(alpha);
      assertEquals(alpha, form.getAlpha());

      TargetForm target = new TargetForm();
      target.setAlpha(alpha);
      assertEquals(alpha, target.getAlpha());
   }

   @Test
   void paintClampsAnOutOfRangeAlpha() {
      RectForm form = new RectForm() {
         @Override
         public int getAlpha() {
            return 255;
         }
      };
      form.setFill(true);
      FormVO vo = new FormVO(form);
      vo.setShape(new Rectangle2D.Double(0, 0, 10, 10));

      assertDoesNotThrow(() -> paint(vo));
   }

   /**
    * A stored GraphTarget alpha is not range-checked when it is loaded; it is clamped when the
    * target form is built, so the chart still renders its fills.
    */
   @Test
   void storedTargetAlphaOutOfRangeStillRenders() {
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "x", "y" }, { "a", 1 }, { "b", 2 }, { "c", 3 } });
      GraphTarget target = new GraphTarget();
      target.setField("y");
      target.setStrategy(new DynamicLineWrapper("2"));
      target.setFillAbove(Color.RED);
      target.setFillBelow(Color.BLUE);
      target.setAlphaValue(255);

      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("x", "y"));
      TargetForm form = new TargetForm();
      assertDoesNotThrow(() -> target.initializeForm(form, data));
      assertEquals(100, form.getAlpha());
      graph.addForm(form);

      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(data, 0, 0, 400, 300);
      List<FormVO> forms = new ArrayList<>();
      collectForms(vgraph, forms);

      assertFalse(forms.isEmpty());
      forms.forEach(vo -> assertDoesNotThrow(() -> paint(vo)));
   }

   private static void paint(FormVO vo) {
      BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();

      try {
         vo.paint(g);
      }
      finally {
         g.dispose();
      }
   }

   private static void collectForms(Visualizable visual, List<FormVO> forms) {
      // the target's fills; its line and label need SVGSupport, which the test env lacks
      if(visual instanceof FormVO && ((FormVO) visual).getForm() instanceof RectForm) {
         forms.add((FormVO) visual);
      }

      if(visual instanceof VContainer) {
         VContainer container = (VContainer) visual;

         for(int i = 0; i < container.getVisualCount(); i++) {
            collectForms(container.getVisual(i), forms);
         }
      }
   }
}
