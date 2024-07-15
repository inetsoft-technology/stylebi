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

import inetsoft.graph.aesthetic.LinearColorFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.guide.legend.Legend;
import inetsoft.graph.internal.GTool;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.internal.RectangleRegion;
import inetsoft.report.internal.Region;
import inetsoft.util.Tool;

import java.awt.*;
import java.awt.geom.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * LegendTitleArea defines the method of write data to an OutputStream
 *  and parse it from an InputStream.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class LegendTitleArea extends DefaultArea implements MenuArea, RollOverArea {
   /**
    * Constructor.
    */
   public LegendTitleArea(Legend legend, List<String> targetFields,
                          boolean sharedColor, AffineTransform trans,
                          IndexedSet<String> palette)
   {
      super(legend, trans);
      VisualFrame frame = legend.getVisualFrame();

      String name = frame == null ? null : Tool.getDataString(
         Tool.localize(frame.getTitle()));
      this.titleLabel = name;
      this.columnName = name;
      titleNameIdx = palette.put(name);
      container = new SplitContainer(getRegions()[0].getBounds());
      bg = legend.getVisualFrame().getLegendSpec().getTitleTextSpec().getBackground();
      field = frame.getField();
      this.targetFields = targetFields;
      this.sharedColor = sharedColor;
      this.nodeAesthetic = GraphUtil.isNodeAestheticFrame(frame, legend.getGraphElement());
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      super.writeData(output);
      output.writeInt(titleNameIdx);
      container.writeData(output);

      output.writeBoolean(field != null);

      if(field != null) {
         output.writeUTF(field);
      }

      output.writeInt(targetFields.size());

      for(String field : targetFields) {
         output.writeUTF(field);
      }

      output.writeBoolean(bg != null);

      if(bg != null) {
         output.writeInt(bg.getRGB() & 0xFFFFFF);
         output.writeDouble(bg.getAlpha() / 255.0);
      }

      output.writeBoolean(sharedColor);
   }

   /**
    * Get regions.
    */
   @Override
   public Region[] getRegions() {
      Rectangle2D bounds = ((Legend) vobj).getTitleBounds();
      Rectangle2D.Double rect2d =
         (Rectangle2D.Double) GTool.transform(bounds, trans);
      Point2D p = getRelPos();
      rect2d.x = rect2d.x - p.getX();
      rect2d.y = rect2d.y - p.getY();

      return new Region[] {new RectangleRegion(rect2d)};
   }

   /**
    * Set aesthetic type.
    */
   public void setAestheticType(String aestheticType) {
      this.aestheticType = aestheticType;
   }

   /**
    * Get aesthetic type.
    */
   public String getAestheticType() {
      return aestheticType;
   }

   /**
    * Get the data info for this area.
    */
   @Override
   public ChartAreaInfo getChartAreaInfo() {
      ChartAreaInfo info = ChartAreaInfo.createLegendTitle();
      info.setProperty("aestheticType", aestheticType);
      info.setProperty("isContent", "false");
      info.setProperty("column", columnName);
      boolean band = ((Legend) vobj).getVisualFrame() instanceof LinearColorFrame;
      info.setProperty("halignmentEnabled", !band + "");
      info.setProperty("field", field);
      info.setProperty("targetFields", targetFields);
      info.setProperty("sharedColor", sharedColor + "");
      info.setProperty("nodeAesthetic", nodeAesthetic + "");
      info.setProperty("title", titleLabel);

      return info;
   }

    /**
    * Paint area.
    * @param g the graphic of the area.
    */
   @Override
   public void paintArea(Graphics g, Color color) {
      RectangleRegion region = (RectangleRegion) getRegion();

      if(region != null) {
         region.fill(g, color);
      }
   }

   /**
    * Gets the title label of the legend.
    */
   public String getTitleLabel() {
      return titleLabel;
   }

   public String getField() {
      return field;
   }

   public String[] getTargetFields() {
      return (targetFields != null) ? targetFields.toArray(new String[0]) : new String[0];
   }

   public int getTitleNameIdx() {
      return titleNameIdx;
   }

   public Color getBackground() {
      return bg;
   }

   private int titleNameIdx;
   private SplitContainer container;
   private String aestheticType;
   private Color bg;
   private String columnName;
   private String field;
   private List<String> targetFields;
   private boolean sharedColor;
   private String titleLabel;
   private boolean nodeAesthetic;
}
