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
package inetsoft.graph.visual;

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.AreaElement;
import inetsoft.graph.element.LineElement;
import inetsoft.graph.geometry.LineGeometry;
import inetsoft.graph.internal.GTool;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.Random;
import java.util.function.IntToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79663: AreaVO.paint unioned one java.awt.geom.Area per segment, which is super-linear in
 * the point count. The fill is now one path. These tests paint the real AreaVO and compare the
 * pixels with the previous algorithm (kept below as the reference, via reflection on the private
 * members it used).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AreaVOPaintTest {
   private static final int W = 700;
   private static final int H = 400;

   private interface Setup {
      void apply(AreaElement elem);
   }

   private AreaVO createVO(int n, IntToDoubleFunction xf, IntToDoubleFunction yf, Setup setup) {
      Object[][] rows = new Object[n + 1][];
      rows[0] = new Object[] { "x", "y" };

      for(int i = 0; i < n; i++) {
         rows[i + 1] = new Object[] { xf.applyAsDouble(i), yf.applyAsDouble(i) };
      }

      EGraph graph = new EGraph();
      AreaElement elem = new AreaElement("x", "y");

      if(setup != null) {
         setup.apply(elem);
      }

      graph.addElement(elem);
      VGraph vg = Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, W, H);

      for(int i = 0; i < vg.getVisualCount(); i++) {
         if(vg.getVisual(i) instanceof AreaVO vo) {
            return vo;
         }
      }

      fail("no AreaVO");
      return null;
   }

   private static BufferedImage paintNew(AreaVO vo) {
      BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      vo.paint(g);
      g.dispose();
      return img;
   }

   private static Method method(Class<?> cls, String name, Class<?>... types) throws Exception {
      Method m = cls.getDeclaredMethod(name, types);
      m.setAccessible(true);
      return m;
   }

   /** The algorithm before the fix (single shape case): one Area union over all segments. */
   private static BufferedImage paintOld(AreaVO vo) throws Exception {
      Method tp = method(AreaVO.class, "getTransformedBasePoints");
      Method as = method(AreaVO.class, "getAreaShape", Point2D.class, Point2D.class,
                         Point2D.class, Point2D.class, boolean.class);
      Method fa = method(AreaVO.class, "fillArea", Graphics2D.class, Point2D.class,
                         Point2D.class, Shape.class, Color.class, Color.class, GTexture.class,
                         GTexture.class, GLine.class, Color.class);
      Method lo = method(AreaVO.class, "getLowestXPoint", Point2D[].class);
      Method hi = method(AreaVO.class, "getHighestXPoint", Point2D[].class);
      Method pl = method(LineVO.class, "paintLine", Graphics2D.class, double.class);
      Point2D[] basepts = (Point2D[]) tp.invoke(vo);

      LineGeometry gobj = (LineGeometry) vo.getGeometry();
      AreaElement elem = (AreaElement) gobj.getElement();
      Point2D[] pts = vo.getTransformedPoints();
      int[] idxs = gobj.getTupleIndexes();
      Color color = gobj.getColor(idxs[0]);
      GTexture texture = gobj.getTexture(idxs[0]);
      GLine line = gobj.getLine(idxs[0]);
      Color borderColor = elem.getColorFrame() instanceof StaticColorFrame &&
         elem.getLineFrame() instanceof StaticLineFrame ? elem.getBorderColor() : null;

      BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      Graphics2D g2 = (Graphics2D) g.create();

      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                          elem.getType() == LineElement.Type.STEP ?
                             RenderingHints.VALUE_ANTIALIAS_OFF : RenderingHints.VALUE_ANTIALIAS_ON);

      boolean horizontal = GTool.isHorizontal(vo.getScreenTransform());
      Area shapeArea = new Area();

      for(int i = 0; i < pts.length; i++) {
         int next = i + 1;

         if(next == pts.length) {
            if(!vo.isClosed()) {
               break;
            }

            next = 0;
         }

         if(LineVO.isNaN(pts[i]) || LineVO.isNaN(pts[next]) ||
            LineVO.isNaN(basepts[i]) || LineVO.isNaN(basepts[next]))
         {
            continue;
         }

         Shape shape = (Shape) as.invoke(vo, pts[i], pts[next], basepts[i], basepts[next],
                                         horizontal);

         if(shape != null) {
            shapeArea.add(new Area(shape));
         }
      }

      fa.invoke(vo, g2, lo.invoke(vo, (Object) pts), hi.invoke(vo, (Object) pts), shapeArea,
                color, color, texture, texture, line, borderColor);
      g2.dispose();

      if(borderColor == null) {
         pl.invoke(vo, g, Math.min(1, vo.getAlphaHint() * 1.2));
      }

      g.dispose();
      return img;
   }

   private static int diff(BufferedImage a, BufferedImage b) {
      int n = 0;

      for(int y = 0; y < a.getHeight(); y++) {
         for(int x = 0; x < a.getWidth(); x++) {
            if(a.getRGB(x, y) != b.getRGB(x, y)) {
               n++;
            }
         }
      }

      return n;
   }

   private static int painted(BufferedImage a) {
      int n = 0;

      for(int y = 0; y < a.getHeight(); y++) {
         for(int x = 0; x < a.getWidth(); x++) {
            if((a.getRGB(x, y) >>> 24) != 0) {
               n++;
            }
         }
      }

      return n;
   }

   private void check(String tag, int n, IntToDoubleFunction xf, IntToDoubleFunction yf,
                      Setup setup) throws Exception
   {
      AreaVO vo = createVO(n, xf, yf, setup);
      BufferedImage expected = paintOld(vo);
      BufferedImage actual = paintNew(vo);
      assertTrue(painted(expected) > 1000, tag + ": reference image is blank");
      assertEquals(0, diff(expected, actual), tag + ": pixels differ from the union algorithm");
   }

   private static IntToDoubleFunction smooth() {
      return i -> 50 + 30 * Math.sin(i / 20.0);
   }

   @Test
   void plainMatchesUnion() throws Exception {
      check("plain", 300, i -> i, smooth(), null);
   }

   @Test
   void stepMatchesUnion() throws Exception {
      check("step", 300, i -> i, smooth(), e -> e.setType(LineElement.Type.STEP));
   }

   @Test
   void zeroCrossingMatchesUnion() throws Exception {
      check("crossing", 300, i -> i, i -> 40 * Math.sin(i / 15.0), null);
   }

   @Test
   void noisyMatchesUnion() throws Exception {
      Random r = new Random(7);
      double[] y = new double[300];

      for(int i = 0; i < y.length; i++) {
         y[i] = r.nextGaussian() * 40;
      }

      check("noisy", 300, i -> i, i -> y[i], null);
   }

   @Test
   void duplicateXMatchesUnion() throws Exception {
      check("duplicate x", 300, i -> i / 3, smooth(), null);
   }

   @Test
   void unsortedXMatchesUnion() throws Exception {
      Random r = new Random(11);
      double[] x = new double[200];

      for(int i = 0; i < x.length; i++) {
         x[i] = r.nextInt(100);
      }

      check("unsorted x", 200, i -> x[i], smooth(), null);
   }

   @Test
   void textureMatchesUnion() throws Exception {
      check("texture", 300, i -> i, smooth(),
            e -> e.setTextureFrame(new StaticTextureFrame(GTexture.PATTERN_2)));
   }

   @Test
   void borderMatchesUnionOutline() throws Exception {
      check("border", 300, i -> i, smooth(), e -> e.setBorderColor(Color.BLACK));
   }

   @Test
   void borderStepMatchesUnionOutline() throws Exception {
      check("border step", 300, i -> i, smooth(), e -> {
         e.setBorderColor(Color.BLACK);
         e.setType(LineElement.Type.STEP);
      });
   }

   /**
    * The union cost was about n^2.5 (about 3 s at 2,000 points, 90 s at 8,000), so a generous
    * limit at 2,000 points separates the two without being timing sensitive.
    */
   @Test
   void largeAreaPaintsQuickly() {
      AreaVO vo = createVO(2000, i -> i, smooth(), null);
      paintNew(vo); // warm up
      long start = System.nanoTime();
      BufferedImage img = paintNew(vo);
      long ms = (System.nanoTime() - start) / 1_000_000;
      assertTrue(painted(img) > 1000);
      assertTrue(ms < 5000, "painting 2000 points took " + ms + " ms");
   }
}
