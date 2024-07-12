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
package inetsoft.graph.guide.form;

import inetsoft.graph.VGraph;
import inetsoft.graph.Visualizable;
import inetsoft.graph.aesthetic.BluesColorFrame;
import inetsoft.graph.aesthetic.ColorFrame;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.visual.DensityFormVO;
import inetsoft.graph.visual.PointVO;

/**
 * Form for drawing contour of points.
 *
 * @version 13.4
 * @author InetSoft Technology Corp
 */
public class DensityForm extends GraphForm {
   public DensityForm() {
      colorFrame.setScale(new LinearScale(0, 1));
   }

   @Override
   public Visualizable createVisual(Coordinate coord) {
      return new DensityFormVO(this);
   }

   /**
    * Get the contour bandwidth.
    */
   public int getBandwidth() {
      return bandwidth;
   }

   /**
    * Set the contour bandwidth, which controls how wide an area is used to calculate
    * each contour point.
    */
   public void setBandwidth(int bandwidth) {
      this.bandwidth = bandwidth;
   }

   /**
    * Get the contour cell size.
    */
   public int getCellSize() {
      return cellSize;
   }

   /**
    * Set the contour cell size, which controls how fine grained the contour will be.
    */
   public void setCellSize(int cellSize) {
      this.cellSize = cellSize;
   }

   /**
    * Get the color frame for contour.
    */
   public ColorFrame getColorFrame() {
      return colorFrame;
   }

   /**
    * Set the color frame for contour, which is used to color the contour based on density.
    */
   public void setColorFrame(ColorFrame colorFrame) {
      this.colorFrame = colorFrame;
   }

   /**
    * Get the number of levels for contours.
    */
   public int getLevels() {
      return levels;
   }

   /**
    * Set the number of levels for contours, which controls the number of contours lines
    * for each density area.
    */
   public void setLevels(int levels) {
      this.levels = levels;
   }

   /**
    * Get the selector to check if a point should be included in the contour.
    */
   public PointSelector getPointSelector() {
      return pointSelector;
   }

   /**
    * Set the selector to check if a point should be included in the contour.
    */
   public void setPointSelector(PointSelector pointSelector) {
      this.pointSelector = pointSelector;
   }

   /**
    * Interface for selecting points in contour calculation.
    */
   public interface PointSelector {
      boolean isIncluded(PointVO vo, VGraph graph);
   }

   private int cellSize = 10;
   private int bandwidth = 20;
   private int levels = 10;
   private ColorFrame colorFrame = new BluesColorFrame();
   private PointSelector pointSelector;
   private static final long serialVersionUID = 1L;
}
