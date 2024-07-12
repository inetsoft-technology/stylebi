/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.visual;

import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.ColorFrame;
import inetsoft.graph.geometry.ElementGeometry;
import inetsoft.graph.guide.form.DensityForm;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.internal.MarchingSquaresContour;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Visual object for contour form.
 *
 * @version 13.4
 * @author InetSoft Technology Corp
 */
public class DensityFormVO extends FormVO {
   /**
    * Constructor.
    */
   public DensityFormVO(DensityForm form) {
      super(form);
      setZIndex(form.getZIndex());
   }

   @Override
   public Rectangle2D getBounds() {
      checkCache();
      Rectangle2D bounds = this.bounds;

      if(bounds != null) {
         return bounds;
      }

      VGraph graph = getCoordinate().getVGraph();
      Rectangle2D pbounds = graph.getPlotBounds();
      AffineTransform trans = new AffineTransform();

      trans.translate(pbounds.getX(), pbounds.getY());

      for(GeneralPath path : getContourPaths()) {
         Shape shape = path.createTransformedShape(trans);

         if(bounds == null) {
            bounds = shape.getBounds2D();
         }
         else {
            bounds = bounds.createUnion(shape.getBounds2D());
         }
      }

      return this.bounds = bounds;
   }

   @Override
   public void paint(Graphics2D g) {
      if(levels.length > 0) {
         VGraph graph = getCoordinate().getVGraph();
         GeneralPath[] paths = getContourPaths();
         ColorFrame colors = ((DensityForm) getForm()).getColorFrame();
         int alpha = getForm().getAlpha();
         Color color = getForm().getColor();

         if(colors != null) {
            Scale cscale = colors.getScale();

            if(!(cscale instanceof LinearScale)) {
               colors.setScale(cscale = new LinearScale());
            }

            LinearScale scale = (LinearScale) cscale;
            scale.setMin(0);
            scale.setMax(paths.length);
         }

         Rectangle2D pbounds = graph.getPlotBounds();
         Graphics2D g2 = (Graphics2D) g.create();
         g2.translate(pbounds.getX(), pbounds.getY());

         for(int i = 0; i < paths.length; i++) {
            GeneralPath path = paths[i];
            Color fill = colors != null ? colors.getColor(i + 1) : color;

            if(alpha != 100) {
               fill = GTool.getColor(fill, alpha / 100.0);
            }

            g2.setColor(fill);
            g2.fill(path);
         }

         g2.dispose();
      }
   }

   private synchronized GeneralPath[] getContourPaths() {
      checkCache();

      if(paths != null && paths.length > 0) {
         return paths;
      }

      VGraph graph = getCoordinate().getVGraph();
      Rectangle2D pbounds = graph.getPlotBounds();
      Rectangle2D gbounds = graph.getBounds();
      Rectangle2D bounds = pbounds.isEmpty() ? gbounds : pbounds;
      AffineTransform trans = graph.getScreenTransform();

      if(kde == null || !bounds.equals(calcBounds) || !trans.equals(calcTrans)) {
         synchronized(this) {
            if(kde == null || !bounds.equals(calcBounds) || !trans.equals(calcTrans)) {
               calculateKdeGrid(calcBounds = bounds);
               calcTrans = (AffineTransform) trans.clone();
            }
         }
      }

      MarchingSquaresContour contour = new MarchingSquaresContour(calcBounds);
      ArrayList<DoubleArrayList> expanded = new ArrayList<>();
      double factor = 10;

      for(int i = 0; i < kde.length * factor; i++) {
         double idx = i / factor;
         DoubleArrayList row = new DoubleArrayList();

         for(int j = 0; j < kde[0].length * factor; j++) {
            double y = j / factor;
            double v = interpolate(kde, idx, y);

            if(v < minData) {
               v = minData;
            }
            else if(v > maxData) {
               v = maxData;
            }

            row.add(v);
         }

         expanded.add(row);
      }

      int rows = expanded.size() - (int) (factor - 1);

      if(rows <= 0) {
         return paths = new GeneralPath[0];
      }

      double[][] data = new double[rows][];

      for(int i = 0; i < data.length; i++) {
         data[i] = expanded.get(i).toDoubleArray();
      }

      return paths = contour.createShapes(data, levels);
   }

   private void checkCache() {
      VGraph graph = getCoordinate().getVGraph();
      Rectangle2D pbounds = graph.getPlotBounds();
      Rectangle2D gbounds = graph.getBounds();
      Rectangle2D bounds = pbounds.isEmpty() ? gbounds : pbounds;
      AffineTransform trans = graph.getScreenTransform();

      bounds = trans.createTransformedShape(bounds).getBounds2D();

      if(!bounds.equals(calcBounds) || !trans.equals(calcTrans)) {
         this.paths = null;
         this.bounds = null;
      }
   }

   private void calculateKdeGrid(Rectangle2D bounds) {
      // sanity check
      bounds = new Rectangle2D.Double(bounds.getX(), bounds.getY(), Math.max(1, bounds.getWidth()),
                                      Math.max(1, bounds.getHeight()));

      VGraph graph = getCoordinate().getVGraph();
      DensityForm.PointSelector selector = ((DensityForm) getForm()).getPointSelector();
      int cellSize = ((DensityForm) getForm()).getCellSize();
      int rowSize = (int) Math.max(cellSize, bounds.getHeight() / 1000);
      int colSize = (int) Math.max(cellSize, bounds.getWidth() / 1000);
      int rows = (int) Math.ceil(bounds.getHeight() / rowSize);
      int cols = (int) Math.ceil(bounds.getWidth() / colSize);
      List<WeightedPoint>[][] grid = new List[rows][cols];

      for(int i = 0; i < graph.getVisualCount(); i++) {
         if(graph.getVisual(i) instanceof PointVO) {
            PointVO vo = (PointVO) graph.getVisual(i);

            if(vo.getMeasureName() != null && vo.getMeasureName().startsWith("__all__")) {
               continue;
            }

            if(selector != null && !selector.isIncluded(vo, graph)) {
               continue;
            }

            Point2D pos = vo.getPosition();
            double size = ((ElementGeometry) vo.getGeometry()).getSize(0);
            pos = graph.getScreenTransform().transform(pos, null);
            int r = (int) ((pos.getY() - bounds.getY()) / rowSize);
            int c = (int) ((pos.getX() - bounds.getX()) / colSize);

            // ignore out of bounds points. (51688)
            if(r < 0 || r >= rows || c < 0 || c >= cols) {
               continue;
            }

            if(grid[r][c] == null) {
               grid[r][c] = new ArrayList<>(2);
            }

            grid[r][c].add(new WeightedPoint(pos, size));
         }
      }

      double min = Double.MAX_VALUE;
      double max = 0;

      double[][] kde = new double[rows][cols];

      for(int i = 0; i < kde.length; i++) {
         for(int j = 0; j < kde[i].length; j++) {
            kde[i][j] = calculateKde(grid, bounds, i, j);

            if(kde[i][j] > 0) {
               min = Double.min(kde[i][j], min);
               max = Double.max(kde[i][j], max);
            }
         }
      }

      this.minData = min;
      this.maxData = max;

      int nlevels = ((DensityForm) getForm()).getLevels();
      double range = min > max ? 0 : max - min;
      double incr = Math.max(range / nlevels, 1);
      double[] levels = new double[(int) Math.ceil(range / incr)];

      for(int i = 0; i < levels.length; i++) {
         levels[i] = min + i * incr;
      }

      this.kde = kde;
      this.levels = levels;
   }

   private double calculateKde(List<WeightedPoint>[][] grid, Rectangle2D bounds, int row, int col) {
      if(grid[row][col] == null) {
         return 0;
      }

      int cellSize = ((DensityForm) getForm()).getCellSize();
      int bandwidth = ((DensityForm) getForm()).getBandwidth();
      Point2D refPoint = new Point2D.Double(bounds.getX() + cellSize / 2 + col * cellSize,
                                            bounds.getY() + cellSize / 2 + row * cellSize);
      int bandwidthRegion = (int) Math.ceil(bandwidth / (double) cellSize);
      int row1 = Math.max(row - bandwidthRegion, 0);
      int row2 = Math.min(row + bandwidthRegion + 1, grid.length);
      int col1 = Math.max(col - bandwidthRegion, 0);
      int col2 = Math.min(col + bandwidthRegion + 1, grid[0].length);
      double kde = 0;

      for(int i = row1; i < row2; i++) {
         for(int j = col1; j < col2; j++) {
            List<WeightedPoint> points = grid[i][j];

            if(points != null) {
               for(WeightedPoint point : points) {
                  double distance = point.distance(refPoint);

                  if(distance < bandwidth) {
                     kde += (1 - Math.abs(distance / bandwidth)) * point.getWeight();
                  }
               }
            }
         }
      }

      return kde;
   }

   private double interpolate(double[][] p, double x, double y) {
      int idx = (int) x;
      double r = x - idx;
      double[] arr = new double[4];

      arr[0] = interpolate(p[Math.max(0, idx - 1)], y);
      arr[1] = interpolate(p[idx], y);
      arr[2] = interpolate(p[Math.min(p.length - 1, idx + 1)], y);
      arr[3] = interpolate(p[Math.min(p.length - 1, idx + 2)], y);

      return interpolate(arr, r + 1);
   }

   private double interpolate(double[] vals, double x) {
      int idx = (int) x;
      double r = x - idx;

      double v0 = vals[Math.max(0, idx - 1)];
      double v1 = vals[idx];
      double v2 = vals[Math.min(vals.length - 1, idx + 1)];
      double v3 = vals[Math.min(vals.length - 1, idx + 2)];

      return v1 + 0.5 * r * (v2 - v0 +
         r * (2.0 * v0 - 5.0 * v1 + 4.0 * v2 - v3 + r * (3.0 * (v1 - v2) + v3 - v0)));
   }

   private static class WeightedPoint extends Point2D.Double {
      public WeightedPoint(Point2D pt, double weight) {
         super(pt.getX(), pt.getY());
         this.weight = weight;
      }

      public double getWeight() {
         return weight;
      }

      public void setWeight(double weight) {
         this.weight = weight;
      }

      private double weight = 1;
   }

   private double[][] kde;
   private double[] levels;
   private double minData, maxData;
   private transient Rectangle2D calcBounds;
   private transient AffineTransform calcTrans;
   private transient Rectangle2D bounds = null;
   private transient GeneralPath[] paths;
}
