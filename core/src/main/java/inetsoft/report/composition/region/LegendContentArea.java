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
 * LegendContentArea defines the method of write data to an OutputStream
 *  and parse it from an InputStream.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class LegendContentArea extends DefaultArea implements MenuArea, ContainerArea {
   /**
    * Constructor.
    */
   public LegendContentArea(Legend legend, List<String> targetFields,
                            boolean sharedColor, AffineTransform trans,
                            IndexedSet<String> palette)
   {
      super(legend, trans);
      frame = legend.getVisualFrame();

      this.titleLabel = frame == null ? null : Tool.getDataString(Tool.localize(frame.getTitle()));
      titleLabelIdx = palette.put(titleLabel);
      container = new SplitContainer(getRegions()[0].getBounds());
      bg = legend.getVisualFrame().getLegendSpec().getTextSpec().getBackground();
      field = legend.getVisualFrame().getField();
      this.targetFields = targetFields;
      this.sharedColor = sharedColor;
   }

   public double getBorderWidth() {
      return GTool.getLineWidth(((Legend) vobj).getVisualFrame().getLegendSpec().getBorder());
   }

   public boolean isTitleVisible() {
      return frame.getLegendSpec().isTitleVisible();
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      super.writeData(output);
      output.writeInt(titleLabelIdx);
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
      Rectangle2D bounds = ((Legend) vobj).getContentBounds();
      Rectangle2D.Double rect2d = (Rectangle2D.Double) GTool.transform(bounds, trans);
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
      ChartAreaInfo info = ChartAreaInfo.createLegendContent();
      info.setProperty("aestheticType", aestheticType);
      info.setProperty("isContent", "true");
      info.setProperty("field", field);
      info.setProperty("targetFields", targetFields);
      info.setProperty("sharedColor", sharedColor + "");
      info.setProperty("nodeAesthetic", isNodeAesthetic() + "");
      info.setProperty("title", frame.getTitle());

      return info;
   }

   /**
    * Get all child areas.
    */
   @Override
   public DefaultArea[] getAllAreas() {
      return new DefaultArea[] {this};
   }

   /**
    * Gets the title label of the legend.
    */
   public String getTitleLabel() {
      return titleLabel;
   }

   /**
    * Get the field name.
    */
   public String getField() {
      return field;
   }

   public String[] getTargetFields() {
      return (targetFields != null) ? targetFields.toArray(new String[0]) : new String[0];
   }

   public Color getBackground() {
      return bg;
   }

   public boolean isNodeAesthetic() {
      return GraphUtil.isNodeAestheticFrame(frame, ((Legend) getVisualizable()).getGraphElement());
   }

   private int titleLabelIdx;
   private String titleLabel;
   protected String aestheticType;
   protected List<String> targetFields;
   protected boolean sharedColor;
   private String field;
   private SplitContainer container;
   private Color bg;
   private VisualFrame frame;
}
