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
package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.GraphConstants;
import inetsoft.report.StyleConstants;
import inetsoft.report.TableDataPath;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.PrintWriter;

/**
 * TabVSAssemblyInfo stores basic tab assembly information.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class TabVSAssemblyInfo extends ContainerVSAssemblyInfo {
   /**
    * Constructor.
    */
   public TabVSAssemblyInfo() {
      super();

      labelsValue.setDValue(new String[0]);
      setPixelSize(new Dimension(3 * AssetUtil.defw, 24));
   }

   @Override
   protected void setDefaultFormat(boolean border, boolean setFormat, boolean fill) {
      super.setDefaultFormat(border, setFormat, fill);

      getFormat().getDefaultFormat().setBordersValue(
         new Insets(GraphConstants.NONE, GraphConstants.NONE,
                    GraphConstants.THIN_LINE, GraphConstants.NONE));
      getFormat().getDefaultFormat().setBorderColorsValue(
         new BorderColors(DEFAULT_BORDER_COLOR, DEFAULT_BORDER_COLOR,
                          DEFAULT_BORDER_COLOR, DEFAULT_BORDER_COLOR));
      getFormat().getDefaultFormat().setRoundCornerValue(4);

      VSCompositeFormat activeFormat = new VSCompositeFormat();
      Color primaryColor = new Color(237, 113, 28);
      activeFormat.getCSSFormat().setCSSType(getObjCSSType());
      activeFormat.getCSSFormat().addCSSAttribute("active", "true");
      activeFormat.getDefaultFormat().setBordersValue(
         new Insets(GraphConstants.NONE, GraphConstants.NONE,
                    GraphConstants.THICK_LINE, GraphConstants.NONE));
      activeFormat.getDefaultFormat().setBorderColorsValue(
         new BorderColors(primaryColor, primaryColor,
                          primaryColor, primaryColor));
      activeFormat.getDefaultFormat().setRoundCornerValue(4);
      getFormatInfo().setFormat(ACTIVE_TAB_PATH, activeFormat);
   }

   /**
    * Get the labels.
    */
   public String[] getLabels() {
      return labelsValue.getRValue();
   }

   /**
    * Set the labels.
    */
   public void setLabels(String[] labels) {
      labelsValue.setRValue(labels);
   }

   /**
    * Get the labels.
    */
   public String[] getLabelsValue() {
      return labelsValue.getDValue();
   }

   /**
    * Set the labels.
    */
   public void setLabelsValue(String[] labels) {
      labelsValue.setDValue(labels);
   }

   /**
    * Get the selected object name.
    * @return the name of the selected object.
    */
   public String getSelected() {
      Object selected = selectedValue.getRValue();
      return selected == null ? null : selected + "";
   }

   /**
    * Set the selected object name.
    * @param selected the name of the selected object.
    */
   public void setSelected(String selected) {
      selectedValue.setRValue(selected);
   }

   /**
    * Get the selected object name.
    * @return the name of the selected object.
    */
   public String getSelectedValue() {
      return selectedValue.getDValue();
   }

   /**
    * Set the selected object name.
    * @param selected the name of the selected object.
    */
   public void setSelectedValue(String selected) {
      selectedValue.setDValue(selected);
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public TabVSAssemblyInfo clone(boolean shallow) {
      try {
         TabVSAssemblyInfo info = (TabVSAssemblyInfo) super.clone(shallow);
         info.selectedValue = selectedValue == null ?
            null : (DynamicValue) selectedValue.clone();

         if(!shallow) {
            if(labelsValue != null) {
               info.labelsValue = labelsValue.clone();
            }
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone TabVSAssemblyInfo", ex);
      }

      return null;
   }

   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print(" roundTopCornersOnly=\"" + roundTopCornersOnly + "\"");
   }

   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);
      String str = Tool.getAttribute(elem, "roundTopCornersOnly");

      if(str != null) {
         roundTopCornersOnly = Boolean.parseBoolean(str);
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      String[] labels = labelsValue.getRValue();
      writer.print("<labels>");

      for(int i = 0; labels != null && i < labels.length; i++) {
         writer.print("<label>");

         if(labels[i] != null) {
            // @by stephenwebster, For Bug #643
            // Localize the runtime label values.
            writer.print("<![CDATA[" + Tool.localize(labels[i]) + "]]>");
         }

         writer.println("</label>");
      }

      writer.println("</labels>");

      if(selectedValue.getRValue() != null) {
         writer.print("<selected>");
         writer.print("<![CDATA[" + selectedValue.getRValue() + "]]>");
         writer.println("</selected>");
      }

      labels = labelsValue.getDValue();

      writer.print("<labelsValue>");

      for(int i = 0; labels != null && i < labels.length; i++) {
         writer.print("<label>");

         if(labels[i] != null) {
            writer.print("<![CDATA[" + labels[i] + "]]>");
         }

         writer.println("</label>");
      }

      writer.println("</labelsValue>");

      if(selectedValue.getDValue() != null) {
         writer.print("<selectedValue>");
         writer.print("<![CDATA[" + selectedValue.getDValue() + "]]>");
         writer.println("</selectedValue>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element node = Tool.getChildNodeByTagName(elem, "labelsValue");
      // @by billh, fix customer bug bug1297896608177
      node = node != null ? node : Tool.getChildNodeByTagName(elem, "labels");

      if(node != null) {
         NodeList list = Tool.getChildNodesByTagName(node, "label");
         labelsValue.setDValue(new String[list.getLength()]);

         for(int i = 0; i < list.getLength(); i++) {
            labelsValue.getDValue()[i] = Tool.getValue(list.item(i));
         }
      }
      else {
         labelsValue.setDValue(new String[0]);
      }

      Element selectedNode = Tool.getChildNodeByTagName(elem, "selectedValue");
      selectedNode = selectedNode == null ?
         Tool.getChildNodeByTagName(elem, "selected") : selectedNode;

      if(selectedNode != null) {
         selectedValue.setDValue(Tool.getValue(selectedNode));
      }
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @param deep whether it is simply copy the properties of the parent.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);

      if(deep) {
         TabVSAssemblyInfo tinfo = (TabVSAssemblyInfo) info;

         if(!Tool.equals(labelsValue, tinfo.labelsValue) ||
            !Tool.equals(getLabels(), tinfo.getLabels()))
         {
            labelsValue = tinfo.labelsValue;
            result = true;
         }

         if(!Tool.equals(selectedValue, tinfo.selectedValue) ||
            !Tool.equals(getSelected(), tinfo.getSelected()))
         {
            selectedValue = tinfo.selectedValue;
            result = true;
         }
      }

      return result;
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.TAB;
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      labelsValue.setRValue(null);
      selectedValue.setRValue(null);
   }

   /**
    * override.
    * Get size scale ratio of this assembly.
    */
   @Override
   public Point2D.Double getSizeScale(Point2D.Double scaleRatio) {
      return new Point2D.Double(scaleRatio.x, 1);
   }

   @Override
   protected void setDefaultFormat(boolean border) {
      super.setDefaultFormat(border);
      VSCompositeFormat format = getFormat();
      format.getDefaultFormat().setAlignmentValue(StyleConstants.H_CENTER |
                                                  StyleConstants.V_CENTER);
   }

   public boolean isRoundTopCornersOnly() {
      return roundTopCornersOnly;
   }

   public void setRoundTopCornersOnly(boolean roundTopCornersOnly) {
      this.roundTopCornersOnly = roundTopCornersOnly;
   }

   private ClazzHolder<String[]> labelsValue = new ClazzHolder<>();
   private DynamicValue selectedValue = new DynamicValue();
   private boolean roundTopCornersOnly = true;

   public static final TableDataPath ACTIVE_TAB_PATH =
      new TableDataPath(-1, TableDataPath.DETAIL);

   private static final Logger LOG =
      LoggerFactory.getLogger(TabVSAssemblyInfo.class);
}
