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
package inetsoft.graph.internal;

import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.*;
import inetsoft.graph.geometry.TreemapGeometry;

import java.awt.*;

/**
 *
 * @version 13.4
 * @author InetSoft Technology
 */
public class TreemapVisualModel extends VisualModel {
   public TreemapVisualModel() {
      super();
   }

   public TreemapVisualModel(DataSet data, ColorFrame colors, SizeFrame sizes,
                             ShapeFrame shapes, TextureFrame textures, LineFrame lines,
                             TextFrame texts) {
      super(data, colors, sizes, shapes, textures, lines, texts);
   }

   private static Object getGroupTotal(DataSet dataset, String field, int row, int[] childRows) {
      if(childRows.length > 0) {
         double total = 0;

         // childRows (rowIndex) is SubDataSet/base row index so should use base of SortedDataSet.
         // see TreeModel.updateCildRows().
         if(dataset instanceof SortedDataSet) {
            dataset = ((DataSetFilter) dataset).getDataSet();
         }

         for(int i = 0; i < childRows.length; i++) {
            Object val = dataset.getData(field, childRows[i]);

            if(val instanceof Number) {
               total += ((Number) val).doubleValue();
            }
         }

         return total;
      }

      return dataset.getData(field, row);
   }

   public Color getColor(TreemapGeometry gobj, int idx) {
      ColorFrame colors = getColorFrame();

      if(colors instanceof LinearColorFrame) {
         return colors.getColor(getGroupTotal(getDataSet(), colors.getField(), idx,
                                              gobj.getChildRows()));
      }
      else if(colors instanceof CompositeColorFrame && !gobj.isLeaf()) {
         // only need to use group total for highlighting (based on measure value).
         // this also eliminate the brushing, which would have wrong color. (50311, 50834)

         if(gobj.getChildRows().length > 0) {
            // create a fake dataset to pass the group total for getting the colors of
            // non-leaf (50274)
            DataSet vdata = getGroupTotalDataSet(gobj.getChildRows(), getDataSet(), idx);
            return colors.getColor(vdata, gobj.getVar(), 0);
         }
      }

      return super.getColor(gobj.getVar(), idx);
   }

   /**
    * Create a dataset for a parent node (row) where value for each measure is the total of the
    * children nodes.
    */
   public static DataSet getGroupTotalDataSet(int[] childRows, DataSet dataset, int row) {
      Object[][] data = new Object[2][dataset.getColCount()];

      for(int i = 0; i < data[0].length; i++) {
         data[0][i] = dataset.getHeader(i);

         if(dataset.isMeasure(dataset.getHeader(i))) {
            data[1][i] = getGroupTotal(dataset, dataset.getHeader(i), row, childRows);
         }
         else {
            data[1][i] = dataset.getData(i, row);
         }
      }

      return new DefaultDataSet(data);
   }

   public GTexture getTexture(TreemapGeometry gobj, int idx) {
      TextureFrame textures = getTextureFrame();

      if(textures instanceof LinearTextureFrame) {
         return textures.getTexture(getGroupTotal(getDataSet(), textures.getField(), idx, gobj.getChildRows()));
      }

      return super.getTexture(gobj.getVar(), idx);
   }

   public GLine getLine(TreemapGeometry gobj, int idx) {
      LineFrame lines = getLineFrame();

      if(lines instanceof LinearLineFrame) {
         return lines.getLine(getGroupTotal(getDataSet(), lines.getField(), idx, gobj.getChildRows()));
      }

      return super.getLine(gobj.getVar(), idx);
   }
}
