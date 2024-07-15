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
import inetsoft.graph.data.DataSet;
import inetsoft.graph.guide.legend.Legend;
import inetsoft.graph.guide.legend.LegendItem;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.visual.ElementVO;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.internal.RectangleRegion;
import inetsoft.report.internal.Region;
import inetsoft.uql.viewsheet.graph.ChartAggregateRef;
import inetsoft.util.Tool;

import java.awt.*;
import java.awt.geom.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.text.Format;
import java.util.List;

/**
 * LegendItemArea defines the method of write data to an OutputStream
 *  and parse it from an InputStream.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class LegendItemArea extends DefaultArea implements RollOverArea {
   /**
    * Constructor.
    */
   public LegendItemArea(LegendItem item, Legend legend, List<String> targetFields,
                         boolean sharedColor, AffineTransform trans,
                         DataSet data, IndexedSet<String> palette)
   {
      super(item, trans);

      VisualFrame frame = item.getVisualFrame();
      String name = frame == null ? null : Tool.getDataString(Tool.localize(frame.getTitle()));
      this.titleLabel = name;
      this.columnName = name;
      String field = frame == null ? "" : frame.getField();
      Format fmt = frame == null ? null : frame.getLegendSpec().getTextSpec().getFormat();
      Object value = item.getValue();
      value = value == null ? "" : value;
      String vtext = Tool.localize(formatItemValue(fmt, value));
      valueIdx = palette.put(Tool.getDataString(value));
      dnameIdx = palette.put(field);
      vtextIdx = palette.put(vtext);
      labelIdx = palette.put(item.getLabel());
      dimension = !"".equals(field) && field != null &&
         // don't support brushing for discrete measure. (60579)
         !data.isMeasure(ChartAggregateRef.getBaseName(ElementVO.getBaseName(field)));
      this.targetFields = targetFields;
      this.sharedColor = sharedColor;
      this.palette = palette;
      this.nodeAesthetic = GraphUtil.isNodeAestheticFrame(frame, legend.getGraphElement());
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      super.writeData(output);
      output.writeInt(valueIdx);
      output.writeInt(dnameIdx);
      output.writeInt(vtextIdx);
      output.writeInt(labelIdx);
      output.writeBoolean(dimension);

      output.writeInt(targetFields.size());

      for(String field : targetFields) {
         output.writeUTF(field);
      }

      output.writeBoolean(sharedColor);
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
    * Get the binding field name.
    */
   public String getDimensionName() {
      if(dnameIdx < 0) {
         return null;
      }

      return palette.get(dnameIdx);
   }

   /**
    * Get the data path for this area.
    */
   @Override
   public ChartAreaInfo getChartAreaInfo() {
      ChartAreaInfo info = ChartAreaInfo.createLegendContent();
      info.setProperty("aestheticType", aestheticType);
      info.setProperty("isContent", "true");
      info.setProperty("value", getValue());
      info.setProperty("field", getDimensionName());
      info.setProperty("targetFields", targetFields);
      info.setProperty("sharedColor", sharedColor + "");
      info.setProperty("column", columnName);
      boolean band = ((LegendItem) vobj).getVisualFrame() instanceof LinearColorFrame;
      info.setProperty("halignmentEnabled", !band + "");
      info.setProperty("nodeAesthetic", nodeAesthetic);
      info.setProperty("title", titleLabel);

      return info;
   }

   /**
    * Get the value.
    * @return value String
    */
   public String getValue() {
      if(valueIdx < 0) {
         return null;
      }

      return palette.get(valueIdx);
   }

   /**
    * Get the value as a formatted string.
    */
   public String getValueText() {
      if(vtextIdx < 0) {
         return null;
      }

      return palette.get(vtextIdx);
   }

   /**
    * Get the legend label.
    */
   public String getLabel() {
      if(labelIdx < 0) {
         return null;
      }

      return palette.get(labelIdx);
   }

   /**
    * Get regions.
    */
   @Override
   public Region[] getRegions() {
      Rectangle2D bounds = vobj.getBounds();
      Rectangle2D.Double rect2d = (Rectangle2D.Double) GTool.transform(bounds, trans);
      Point2D p = getRelPos();
      rect2d.x = rect2d.x - p.getX();
      rect2d.y = rect2d.y - p.getY();

      return new Region[] {new RectangleRegion(rect2d)};
   }

   /**
    * Method to test whether regions contain the specified point.
    * @param point the specified point.
    */
   @Override
   public boolean contains(Point point) {
      return getRegion().contains(point.x, point.y);
   }

   /**
    * Get targetFields.
    */
   public List<String> getTargetFields() {
      return targetFields;
   }

   /**
    * Gets the title lable.
    */
   public String getTitleLabel() {
      return titleLabel;
   }

   public boolean isSharedColor() {
      return sharedColor;
   }

   public boolean isDimension() {
      return dimension;
   }

   public boolean isNodeAesthetic() {
      return nodeAesthetic;
   }

   private final int valueIdx;
   private final int dnameIdx;
   private final int vtextIdx;
   private final int labelIdx;
   private final boolean dimension;
   private String aestheticType;
   private final List<String> targetFields;
   private final boolean sharedColor;
   private final String columnName;
   private final String titleLabel;
   private final boolean nodeAesthetic;
}
