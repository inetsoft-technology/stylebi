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
package inetsoft.report.composition.region;

import inetsoft.graph.Visualizable;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.VDimensionLabel;
import inetsoft.graph.internal.GTool;
import inetsoft.report.Hyperlink;
import inetsoft.report.filter.DCMergeDatesCell;
import inetsoft.report.internal.RectangleRegion;
import inetsoft.uql.viewsheet.internal.DateComparisonFormat;
import inetsoft.util.Tool;

import java.awt.*;
import java.awt.geom.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.text.Format;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DimensionLabelArea class.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class DimensionLabelArea extends DefaultArea
   implements MenuArea, RollOverArea
{
   /**
    * Constructor.
    */
   public DimensionLabelArea(Axis axis, VDimensionLabel dlabel,
                             boolean outer, AffineTransform trans,
                             IndexedSet<String> palette,
                             Hyperlink.Ref[] links, String linkURI)
   {
      super(dlabel, trans);

      Object obj = dlabel.getValue();
      Format fmt = dlabel.getTextSpec().getFormat();
      String vtext = formatItemValue(fmt, obj);
      this.dimensionNameIdx = palette.put(dlabel.getDimensionName());

      String label = dlabel.getText();

      // if only showing last date on axis, should show all dates on tooltip.
      if(fmt instanceof DateComparisonFormat) {
         label = ((DateComparisonFormat) fmt).getFormatWithAllDates().format(obj);
      }
      else if(obj instanceof DCMergeDatesCell) {
         label = ((DCMergeDatesCell) obj).getTooltip();
      }

      this.labelIdx = palette.put(label);
      this.outer = outer;
      this.axisType = dlabel.getAxisType();
      this.objectIdx = palette.put(Tool.getDataString(obj, true, true));
      this.vtextIdx = palette.put(vtext);
      this.palette = palette;
      this.haxis = GTool.isHorizontal(axis.getScreenTransform());
      this.axisBounds = axis.getBounds();
      processHyperlinks(links, linkURI);
      Dimension2D msize = dlabel.getMaxSize();
      Dimension2D size = dlabel.getSize();
      double pwidth = dlabel.getPreferredWidth();
      double pheight = dlabel.getPreferredHeight();

      truncated = size.getWidth() < pwidth || size.getHeight() < pheight ||
         msize != null && (pwidth >= msize.getWidth() || pheight >= msize.getHeight());

      Map<String, Object> pvals = new HashMap<>();

      if(axis.getCoordinate() != null) {
         // @by davyc, for facet chart, only outer chart will paint axis
         // fix bug1367031047813
         pvals.putAll(axis.getCoordinate().getParentValues(haxis));
      }

      for(String name : pvals.keySet()) {
         Object val = pvals.get(name);

         parentValues.add(palette.put(name));
         parentValues.add(palette.put(Tool.getDataString(val, true, true)));
      }
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      super.writeData(output);
      output.writeInt(dimensionNameIdx);
      output.writeInt(labelIdx);
      output.writeInt(objectIdx);
      output.writeInt(vtextIdx);
      output.writeUTF(axisType);
      output.writeBoolean(outer);
      output.writeBoolean(truncated);

      for(int i = 0; i < hlabels.length; i++) {
         output.writeInt(hlabels[i]);
      }

      output.writeBoolean(toolTip == null);

      if(toolTip != null) {
         output.writeUTF(toolTip);
      }

      output.writeBoolean(existsHyperlink);
      output.writeBoolean(haxis);

      output.writeInt(parentValues.size());

      for(int i = 0; i < parentValues.size(); i++) {
         output.writeInt(parentValues.get(i));
      }
   }

   /**
    * Get axis type.
    * @return the type of the axis, x or y.
    */
   public String getAxisType() {
      return axisType;
   }

   public List<String> getParentValues() {
      return parentValues.stream().map(i -> palette.get(i)).collect(Collectors.toList());
   }

   /**
    * Get the dimension name.
    * @return the dimension name.
    */
   public String getDimensionName() {
      if(dimensionNameIdx < 0) {
         return null;
      }

      return palette.get(dimensionNameIdx);
   }

   /**
    * Get the label name.
    * return the label name.
    */
   public String getLabel() {
      if(labelIdx < 0) {
         return null;
      }

      return palette.get(labelIdx);
   }

   /**
    * Get the label value.
    * return the label value.
    */
   public String getValue() {
      if(objectIdx < 0) {
         return null;
      }

      return palette.get(objectIdx);
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
    * Get the data info for this area.
    */
   @Override
   public ChartAreaInfo getChartAreaInfo() {
      ChartAreaInfo info  = ChartAreaInfo.createAxisLabel();
      info.setProperty("axisType", axisType);
      info.setProperty("column", getDimensionName());
      info.setProperty("label", getLabel());
      info.setProperty("isOuter", outer + "");
      info.setProperty("halignmentEnabled", isHAlignmentEnabled() + "");
      info.setProperty("valignmentEnabled", isVAlignmentEnabled() + "");
      info.setProperty("linear", false);

      return info;
   }

   /**
    * Paint the region.
    * @param g the graphics.
    */
   @Override
   public void paintArea(Graphics g, Color color) {
      ((RectangleRegion) getRegion()).fill(g, color);
   }

    /**
    * Get hyperlink.
    */
   public Hyperlink.Ref[] getHyperlinks() {
      return hrefs;
   }

   /**
    * Init hyperlinks.
    */
   private void processHyperlinks(Hyperlink.Ref[] links, String linkURI) {
      links = links == null ? new Hyperlink.Ref[0] : links;
      hrefs = new Hyperlink.Ref[links.length];
      hlabels = new int[links.length];

      for(int i = 0; i < links.length; i++) {
         String tip = (String) links[i].getParameter("tooltip");
         toolTip = toolTip == null ? "".equals(tip) ? null : tip :
            Tool.isEmptyString(tip) ? toolTip : toolTip + "/" + tip;
         hrefs[i] = links[i];

         String lbl = links[i].getName();
         lbl = lbl == null ? "" : lbl;
         hlabels[i] = palette.put(Tool.localize(lbl));
      }
   }

   /**
    * Set contains hyperlink or not.
    */
   public void setHyperlinkExists(boolean exists) {
      this.existsHyperlink = exists;
   }

   /**
    * Text horizontal alignment enabled.
    */
   public boolean isHAlignmentEnabled() {
      boolean radar = "polar".equals(axisType);
      return outer || !haxis && !radar;
   }

   /**
    * Text vertical alignment enabled.
    */
   public boolean isVAlignmentEnabled() {
      boolean radar = "polar".equals(axisType);
      return outer && !haxis && !radar;
   }

   /**
    * Get the bounds of an object.
    */
   @Override
   protected Rectangle2D getBounds(Visualizable vobj) {
      Rectangle2D box = super.getBounds(vobj);
      // make sure the label is not outside of bounds
      return this instanceof RadarDimensionLabelArea || box.isEmpty() ? box :
         box.createIntersection(axisBounds);
   }

   public String getTooltip() {
      return toolTip;
   }

   public boolean isOuter() {
      return outer;
   }

   /**
    * Get the row number of the dimension label.
    */
   public int getRow() {
      return row;
   }

   /**
    * Set the row number of the dimension label.
    */
   public void setRow(int r) {
      this.row = r;
   }

   /**
    * Get the col number of the dimension label.
    */
   public int getCol() {
      return col;
   }

   /**
    * Set the col number of the dimension label.
    */
   public void setCol(int c) {
      col = c;
   }

   private int dimensionNameIdx;
   private int labelIdx;
   private int objectIdx;
   private int vtextIdx;
   private int row;
   private int col;
   private String axisType;
   private boolean outer;
   private boolean truncated;
   private Hyperlink.Ref[] hrefs;
   private int[] hlabels;
   private String toolTip;
   private boolean existsHyperlink;
   private boolean haxis;
   private Rectangle2D axisBounds;
   // parent values (index in palette): name1,value1,name2,value2
   private List<Integer> parentValues = new ArrayList<>();
}
