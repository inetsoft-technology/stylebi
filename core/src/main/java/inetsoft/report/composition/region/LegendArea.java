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
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.RelationElement;
import inetsoft.graph.guide.legend.Legend;
import inetsoft.graph.guide.legend.LegendGroup;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.TimeScale;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.internal.RectangleRegion;
import inetsoft.report.internal.Region;

import java.awt.*;
import java.awt.geom.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LegendArea defines the method of write data to an OutputStream
 * and parse it from an InputStream.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class LegendArea extends DefaultArea implements GraphComponentArea {
   /**
    * Constructor.
    */
   public LegendArea(LegendGroup legends, int legendIdx, EGraph egraph,
                     AffineTransform trans, DataSet data,
                     IndexedSet<String> palette)
   {
      super(legends.getLegend(legendIdx), trans);

      this.legend = legends.getLegend(legendIdx);
      this.columnNameIdx = palette.put(legend.getVisualFrame().getField());
      this.minSize = new Dimension((int) legend.getMinWidth(), (int) legend.getMinHeight());
      this.aestheticType = GTool.getFrameType(legend.getVisualFrame().getClass());
      this.legendIdx = legendIdx;
      this.frame = legend.getVisualFrame();
      this.targetFields = GraphUtil.getTargetFields(frame, egraph);
      this.sharedColor = isSharedColor(egraph, frame);
      this.categorical = isDimensionFrame(frame);
      this.time = frame.getScale() instanceof TimeScale;
      this.field = frame.getField();
      this.areas = getAreas(legend, data, palette);

      Rectangle2D rect = GTool.transform(getBounds(vobj), trans);
      Point2D p = new Point2D.Double(rect.getX(), rect.getY());

      for(int i = 0; i < areas.length; i++) {
         areas[i].setRelPos(p);
      }
   }

   private boolean isDimensionFrame(VisualFrame frame) {
      if(frame instanceof CategoricalFrame) {
         return true;
      }

      if(frame instanceof StackedMeasuresFrame) {
         VisualFrame vframe = ((StackedMeasuresFrame) frame).getDefaultFrame();

         // categorical or static frame (which would show measure labels). (50347)
         return vframe instanceof CategoricalFrame || vframe.getField() == null;
      }

      return false;
   }

   /**
    * Check if this legend is displaying a shared color legend (so
    * the color legend is not separately displayed). Or if this is a color frame
    * and it's shared with another color frame.
    */
   private boolean isSharedColor(EGraph egraph, VisualFrame frame) {
      // if color legend is displayed, hiding a shape/size legend should not hide color. (58639)
      boolean colorDisplayed = Arrays.stream(egraph.getVisualFrames())
         .anyMatch(f -> f instanceof ColorFrame && Objects.equals(frame.getField(), f.getField()));

      if(colorDisplayed) {
         return false;
      }

      List<VisualFrame> colors = new ArrayList<>();

      for(int i = 0; i < egraph.getElementCount(); i++) {
         GraphElement elem = egraph.getElement(i);
         colors.add(elem.getColorFrame());
         colors.add(elem instanceof RelationElement
                       ? ((RelationElement) elem).getNodeColorFrame() : null);
      }

      Set<VisualFrame> set = colors.stream().filter(a -> a != null).collect(Collectors.toSet());

      if(frame instanceof ColorFrame) {
         return set.size() < 2;
      }

      return colors.stream().anyMatch(color -> color != null && color.getLegendFrame() == frame);
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      super.writeData(output);
      output.writeInt(columnNameIdx);
      output.writeUTF(aestheticType);
      output.writeInt(legendIdx);
      output.writeBoolean(categorical);
      int len = areas.length;
      output.writeInt(len);

      for(int i = 0; i < len; i++) {
         output.writeUTF(areas[i].getClassName());
         areas[i].writeData(output);
      }

      output.writeDouble(minSize.getWidth());
      output.writeDouble(minSize.getHeight());

      output.writeBoolean(field != null);

      if(field != null) {
         output.writeUTF(field);
      }

      output.writeInt(targetFields.size());

      for(String field : targetFields) {
         output.writeUTF(field);
      }

      output.writeBoolean(sharedColor);
   }

   /**
    * Get the index of the legend
    */
   public int getLegendIdx() {
      return legendIdx;
   }

   /**
    * Get aesthetic type.
    */
   public String getAestheticType() {
      return aestheticType;
   }

   /**
    * Get the visual frame corresponding to this legend.
    */
   public VisualFrame getVisualFrame() {
      return frame;
   }

   /**
    * Get the field bound to the visual frame.
    */
   public String getField() {
      return field;
   }

   /**
    * Get the corresponding measure.
    */
   public List<String> getTargetFields() {
      return targetFields;
   }

   /**
    * Get min size of this area.
    */
   public Dimension getMinSize() {
      return minSize;
   }

   /**
    * Get column name.
    * @return the name of column.
    */
   public String getColumnName() {
      if(columnNameIdx < 0) {
         return null;
      }

      return palette.get(columnNameIdx);
   }

   /**
    * Check if this legend is based on a categorical scale.
    */
   public boolean isCategorical() {
      return categorical;
   }

   public boolean isTime() {
      return time;
   }

   /**
    * Paint selected area.
    * @param g the graphic of the area.
    */
   public void paintArea(Graphics g, Color color) {
      Region[] regions = getRegions();

      if(regions != null && regions.length > 0) {
         RectangleRegion rectRegion = (RectangleRegion) regions[0];

         if(rectRegion != null) {
            rectRegion.fill(g, color);
         }
      }
   }

   /**
    * Get legend item areas.
    */
   private DefaultArea[] getAreas(Legend legend, DataSet data, IndexedSet<String> palette) {
      DefaultArea[] lareas;

      if(legend != null && legend.getTitle() == null) {
         lareas = new DefaultArea[1];
         lareas[0] = getContentArea(legend, data, palette);
      }
      else {
         lareas = new DefaultArea[2];

         lareas[0] = new LegendTitleArea(legend, targetFields, sharedColor, trans, palette);
         ((LegendTitleArea) lareas[0]).setAestheticType(aestheticType);
         lareas[1] = getContentArea(legend, data, palette);
      }

      return lareas;
   }

   /**
    * Get legend content area.
    */
   private LegendContentArea getContentArea(Legend legend, DataSet data,
                                            IndexedSet<String> palette) {
      LegendContentArea area = null;

      if(legend.isScalar()) {
         area = new ScalarLegendContentArea(legend, targetFields, sharedColor, trans, palette);
      }
      else {
         area = new ListLegendContentArea(legend, targetFields, sharedColor, trans, data, palette);
      }

      area.setAestheticType(aestheticType);
      return area;
   }

   /**
    * Get all child areas.
    */
   @Override
   public DefaultArea[] getAllAreas() {
      Vector vec = new Vector();

      for(int i = 0; i < areas.length; i++) {
         if(areas[i] instanceof ContainerArea) {
            DefaultArea[] dareas = ((ContainerArea) areas[i]).getAllAreas();

            for(int j = 0; j < dareas.length; j++) {
               vec.add(dareas[j]);
            }
         }
         else {
            vec.add(areas[i]);
         }
      }

      DefaultArea[] rareas = new DefaultArea[vec.size()];

      for(int i = 0; i < vec.size(); i++) {
         rareas[i] = (DefaultArea) vec.get(i);
      }

      return rareas;
   }

   /**
    * Get areas.
    */
   public DefaultArea[] getAreas() {
      return areas;
   }

   /**
    * Get selection type of this area.
    */
   public String getSelectionType() {
      return "LegendArea_" + getColumnName();
   }

   /**
    * Get content area.
    */
   public LegendContentArea getContent() {
      if(areas == null) {
         return null;
      }

      for(int i = 0; i < areas.length; i++) {
         if(areas[i] instanceof LegendContentArea) {
            return (LegendContentArea) areas[i];
         }
      }

      return null;
   }

   public Legend getLegend() {
      return legend;
   }

   public boolean isNodeAesthetic() {
      return GraphUtil.isNodeAestheticFrame(frame, legend.getGraphElement());
   }

   private int columnNameIdx;
   private int legendIdx;
   private DefaultArea[] areas;
   private Dimension minSize;
   private String aestheticType;
   private boolean categorical;
   private boolean time;
   private String field;
   private List<String> targetFields;
   private VisualFrame frame;
   private boolean sharedColor;
   private Legend legend;
}
