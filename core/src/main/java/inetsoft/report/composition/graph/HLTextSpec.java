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
package inetsoft.report.composition.graph;

import inetsoft.graph.TextSpec;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.geometry.Geometry;
import inetsoft.graph.geometry.TreemapGeometry;
import inetsoft.graph.internal.TreemapVisualModel;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.filter.TextHighlight;
import inetsoft.report.lens.DataSetTable;

import java.awt.*;
import java.util.*;

/**
 * Apply highlight to text.
 *
 * @version 13.1
 * @author InetSoft Technology
 */
public class HLTextSpec extends TextSpec {
   public HLTextSpec(HighlightGroup group) {
      this.group = group;
   }

   @Override
   public TextSpec evaluate(DataSet data, int row, Geometry gobj, String measure, Object value) {
      if(gobj instanceof TreemapGeometry) {
         int[] childRows = ((TreemapGeometry) gobj).getChildRows();

         if(childRows.length > 0) {
            data = TreemapVisualModel.getGroupTotalDataSet(childRows, data, row);
            row = 0;
         }
      }

      DataSetTable tbl = new DataSetTable(data);

      // label may be showing stacked value, which is an aggregate of values in the
      // data set. set the actual label value to be used for highlight.
      if(measure != null) {
         int col = data.indexOfHeader(measure);

         // network graph highlight defined on dimension. (61258)
         if(col >= 0 && data.isMeasure(measure)) {
            tbl.setObject(row + 1, col, value);
         }
      }

      TextHighlight highlight = (TextHighlight) group.findGroup(tbl, row + 1);

      if(highlight != null) {
         Font font = highlight.getFont();
         Color color = highlight.getForeground();

         return hlspecs.computeIfAbsent(font + ":" + color, f -> {
            TextSpec spec = HLTextSpec.this.clone();

            if(font != null) {
               spec.setFont(font);
            }

            if(color != null) {
               spec.setColor(color);
            }

            return spec;
         });
      }

      return super.evaluate(data, row, gobj, measure, value);
   }

   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      HLTextSpec spec = (HLTextSpec) obj;
      return Objects.equals(group, spec.group);
   }

   private HighlightGroup group;
   private Map<String, TextSpec> hlspecs = new HashMap<>();
}
