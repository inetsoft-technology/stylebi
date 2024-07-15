/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.composition.region;

import inetsoft.graph.guide.VLabel;
import inetsoft.report.internal.RectangleRegion;
import inetsoft.report.internal.Region;
import inetsoft.util.Tool;

import java.awt.*;
import java.awt.geom.*;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * TitleArea Class.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class TitleArea extends DefaultArea implements MenuArea, RollOverArea,
                                                      GraphComponentArea {
   /**
    * Constructor.
    */
   public TitleArea(VLabel title, String axisType, int dropType,
                    AffineTransform trans, Rectangle2D bounds)
   {
      super(title, trans);

      this.axisType = axisType;
      this.dropType = dropType;
      this.bounds = bounds;
      container = new SplitContainer(getRegions()[0].getBounds());

      if(title != null) {
         titleName = Tool.getDataString(title.getLabel());
         VLabel title2 = (VLabel) title.clone();
         title2.setMaxSize(null);
         Dimension2D size = title2.getSize();
         double pwidth = title2.getPreferredWidth();
         double pheight = title2.getPreferredHeight();
         
         truncated = size.getWidth() < pwidth || size.getHeight() < pheight;
      }
      else {
         titleName = "";
      }
   }

   /**
    * Get regions.
    */
   @Override
   public Region[] getRegions() {
      // the bounds has been transformed in ChartArea
      return new Region[] {new RectangleRegion(bounds)};
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      super.writeData(output);

      output.writeUTF(axisType);
      output.writeInt(dropType);
      output.writeUTF(titleName);
      output.writeBoolean(truncated);
      container.writeData(output);
   }

   /**
    * Get the title axis type.
    * @return the title axis type.
    */
   public String getAxisType() {
      return axisType;
   }

   /**
    * Paint region.
    * @param g the graphics.
    */
   @Override
   public void paintArea(Graphics g, Color color) {
      (new RectangleRegion(new Rectangle(0, 0, getRegion().getBounds().width,
       getRegion().getBounds().height))).fill(g, color);
   }

    /**
    * Get the data info for this area.
    */
   @Override
   public ChartAreaInfo getChartAreaInfo() {
      ChartAreaInfo info = ChartAreaInfo.createAxisTitle();
      info.setProperty("axisType", axisType);
      info.setProperty("titlename", titleName);

      return info;
   }

   /**
    * Get all the area which this contains.
    */
   @Override
   public DefaultArea[] getAllAreas() {
      return new DefaultArea[] {this};
   }

   private String axisType;
   private int dropType;
   private String titleName;
   private boolean truncated;
   private SplitContainer container;
   private transient Rectangle2D bounds;
}
