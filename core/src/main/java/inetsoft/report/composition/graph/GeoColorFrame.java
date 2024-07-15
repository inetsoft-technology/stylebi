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
package inetsoft.report.composition.graph;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.ColorFrame;
import inetsoft.graph.data.AbstractDataSetFilter;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.geo.GeoDataSet;

import java.awt.*;

/**
 * GeoColorFrame, the color frame to apply map brush.
 *
 * @version 10.2
 * @author InetSoft Technology
 */
public class GeoColorFrame extends ColorFrame {
   /**
    * Constructure.
    * @param color brush color.
    */
   public GeoColorFrame(Color color) {
      this(color, CategoricalColorFrame.COLOR_PALETTE[0]);
   }

   /**
    * Constructure.
    * @param color brush color.
    * @param defColor default color.
    */
   public GeoColorFrame(Color color, Color defColor) {
      this.color = color;
      this.defColor = defColor;
   }

   /**
    * Get the color for the specified cell.
    * @param data the specified dataset.
    * @param col the specified column name.
    * @param row the specified row index.
    */
   @Override
   public Color getColor(DataSet data, String col, int row) {
      while(!(data instanceof GeoDataSet) && data instanceof AbstractDataSetFilter) {
         AbstractDataSetFilter filter = (AbstractDataSetFilter) data;
         data = filter.getDataSet();
      }

      if(!(data instanceof GeoDataSet)) {
         return defColor;
      }

      GeoDataSet gdata = (GeoDataSet) data;

      return row < gdata.getRowCount() ? color : defColor;
   }

   /**
    * Get the color for the specified value.
    */
   @Override
   public Color getColor(Object val) {
      return color;
   }

   /**
    * Get the data point color.
    */
   public Color getColor() {
      return color;
   }

   private Color color;
   private Color defColor;
}
