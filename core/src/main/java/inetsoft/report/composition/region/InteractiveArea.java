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

import inetsoft.graph.Visualizable;
import inetsoft.graph.data.HRef;
import inetsoft.report.Hyperlink;

import java.awt.geom.AffineTransform;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * InteractiveArea represents areas with hyperlink and could do brushing.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class InteractiveArea extends DefaultArea {
   /**
    * Create an instanceof interactive area.
    */
   public InteractiveArea(Visualizable vobj, AffineTransform trans,
                          IndexedSet<String> palette)
   {
      super(vobj, trans);

      this.palette = palette;
   }

   /**
    * Get measure name.
    * @return measure name.
    */
   public String getMeasureName() {
      if(measureIndex < 0) {
         return null;
      }

      return palette.get(measureIndex);
   }

   /**
    * Set measure name.
    * @param measureName the specified measure name.
    */
   public void setMeasureName(String measureName) {
      measureIndex = palette.put(measureName);
   }

   /**
    * Get the row index.
    * @return the row indexs.
    */
   public int getRowIndex() {
      return rowIndexes != null && rowIndexes.length > 0 ? rowIndexes[0] : -1;
   }

   /**
    * Get the col index.
    * @return col index.
    */
   public int getColIndex() {
      return cidx;
   }

   /**
    * Set the col index.
    */
   public void setColIndex(int cidx) {
      this.cidx = cidx;
   }

   /**
    * Get row indexes.
    * @return row indexes.
    */
   public int[] getRowIndexes() {
      return rowIndexes;
   }

  /**
    * Set row indexes.
    * @param rowIndexes the specified row indexes.
    */
   public void setRowIndexes(int[] rowIndexes) {
      this.rowIndexes = rowIndexes;
   }

   /**
    * Get hyperlink.
    * @return hyper link.
    */
   public Hyperlink.Ref getHyperlink() {
      return href;
   }

   /**
    * Set hyperlink.
    * @param href the specified hyper link ref.
    */
   public void setHyperlink(Hyperlink.Ref href) {
      this.href = href;
   }

   /**
    * Get hyperlink label.
    */
   public String getHyperlinkLabel() {
      return hyperlinkLabel;
   }

   /**
    * Set hyperlink label.
    */
   public void setHyperlinkLabel(String hyperlinkLabel) {
      this.hyperlinkLabel = hyperlinkLabel;
   }

   /**
    * Get the tooltip string of this object.
    * @return the tooltip string.
    */
   public String getToolTipString() {
      return toolTip.getTooltip(palette);
   }

   /**
    * Set tooltip.
    * @param toolTip the specified tooltip.
    */
   public void setToolTip(ChartToolTip toolTip) {
      this.toolTip = toolTip;
   }

   /**
    * Get tooltip.
    * @return tooltip.
    */
   public ChartToolTip getToolTip() {
      return toolTip;
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      super.writeData(output);

      output.writeInt(measureIndex);
      output.writeInt(rowIndexes.length);

      for(int i = 0; i < rowIndexes.length; i++) {
         output.writeInt(rowIndexes[i]);
      }

      output.writeBoolean(href == null);
      output.writeBoolean(hyperlinkLabel == null);

      if(hyperlinkLabel != null) {
         output.writeUTF(hyperlinkLabel);
      }

      output.writeInt(drillHrefs.length);

      output.writeInt(dlinkLabels.length);

      for(int i = 0; i < dlinkLabels.length; i++) {
         output.writeUTF(dlinkLabels[i]);
      }

      output.writeBoolean(toolTip == null);

      if(toolTip != null) {
         toolTip.writeData(output);
      }
   }

   /**
    * Set drill hyperlink.
    */
   public void setDrillHyperlinks(HRef[] drillHrefs) {
      this.drillHrefs = drillHrefs;
   }

   /**
    * Get drill hyperlink.
    */
   public HRef[] getDrillHyperlinks() {
      return drillHrefs;
   }

   /**
    * Set drill hyperlink label.
    */
   public void setDrillHyperlinkLabels(String[] dlinkLabels) {
      this.dlinkLabels = dlinkLabels;
   }

   /**
    * Get drill hyperlink label.
    */
   public String[] getDrillHyperlinkLabels() {
      return dlinkLabels;
   }

   private Hyperlink.Ref href;
   private String hyperlinkLabel;
   private HRef[] drillHrefs = new Hyperlink.Ref[0];
   private String[] dlinkLabels = new String[0];
   private int measureIndex = -1;
   private ChartToolTip toolTip;
   private int[] rowIndexes;
   private transient IndexedSet<String> palette;
   private int cidx;
}
