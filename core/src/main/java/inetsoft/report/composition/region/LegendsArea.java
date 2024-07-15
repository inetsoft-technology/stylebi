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
package inetsoft.report.composition.region;

import inetsoft.graph.EGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.guide.legend.LegendGroup;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * LegendsArea defines the method of write data to an OutputStream
 *  and parse it from an InputStream.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class LegendsArea extends DefaultArea {
   /**
    * Constructor.
    */
   public LegendsArea(EGraph egraph, LegendGroup legends, 
                      AffineTransform trans, DataSet data,
                      IndexedSet<String> palette) 
   {
      super(legends, trans);

      this.egraph = egraph;
      this.legendAreas = getLegendAreas(legends, data, palette);
      this.minSize = new Dimension((int) legends.getMinWidth(),
                                   (int) legends.getMinHeight());
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      super.writeData(output);
      output.writeDouble(minSize.getHeight());
      output.writeDouble(minSize.getWidth());
      int len = legendAreas.length;
      output.writeInt(len);

      for(int i = 0; i < len; i++) {
         output.writeUTF(legendAreas[i].getClassName());
         legendAreas[i].writeData(output);
      }
   }

   /**
    * Get legend areas.
    */
   private LegendArea[] getLegendAreas(LegendGroup legends, DataSet data,
                                       IndexedSet<String> palette) {
      int count = legends.getLegendCount();
      LegendArea[] areas = new LegendArea[count];

      for(int i = 0; i < count; i++) {
         areas[i] = new LegendArea(legends, i, egraph, trans, data, palette);
      }

      return areas;
   }

   /**
    * Get selected region, this only returns the LegendTitleArea or
    * LegendContentArea.
    * @return area.
    */
   public LegendArea[] getLegendAreas() {
      return legendAreas;
   }

   /**
    * Get the minimal size.
    */
   public Dimension getMinSize() {
      return minSize;
   }

   private LegendArea[] legendAreas;
   private Dimension minSize;
   private EGraph egraph;
}
