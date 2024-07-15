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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.Hyperlink.Ref;
import inetsoft.report.StyleConstants;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.PrintWriter;
/**
 * SubmitVSAssemblyInfo stores basic submit assembly information.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public class SubmitVSAssemblyInfo extends ClickableOutputVSAssemblyInfo {
   /**
    * Constructor.
    */
   public SubmitVSAssemblyInfo() {
      popOptionValue = new DynamicValue2(NO_POP_OPTION + "", XSchema.INTEGER);
      popLocationValue.setRValue(PopLocation.MOUSE);
   }

   /**
    * Set the default vsobject format.
    * @param border border color.
    */
   @Override
   protected void setDefaultFormat(boolean border) {
      VSCompositeFormat format = new VSCompositeFormat();
      // avoid text being clipped in default size
      format.getDefaultFormat().setFontValue(getDefaultFont(Font.BOLD, 11));
      format.getDefaultFormat().setAlignmentValue(StyleConstants.H_CENTER |
                                                  StyleConstants.V_CENTER);
      format.getDefaultFormat().setBordersValue(new Insets(1, 1, 1, 1));
      format.getDefaultFormat().setBorderColorsValue(new BorderColors(DEFAULT_BORDER_COLOR,
                                                                      DEFAULT_BORDER_COLOR,
                                                                      DEFAULT_BORDER_COLOR,
                                                                      DEFAULT_BORDER_COLOR));
      format.getDefaultFormat().setRoundCornerValue(3);
      format.getCSSFormat().setCSSType(getObjCSSType());
      setFormat(format);
      setCSSDefaults();
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.SUBMIT;
   }

   /**
    * Get hyperlink and drill info.
    */
   @Override
   public Ref[] getHyperlinks() {
      return new Ref[0];
   }

   /**
    * Set submit label name.
    */
   public void setLabelName(String labelName) {
      this.labelName = labelName;
   }

   /**
    * Get submit label name.
    */
   public String getLabelName() {
      String localizedText = Tool.localizeTextID(
         VSUtil.getTextID(getViewsheet(), getName()));
      labelName = localizedText == null ?
         Tool.getDataString(labelName) : localizedText;

      return labelName;
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public SubmitVSAssemblyInfo clone(boolean shallow) {
      try {
         SubmitVSAssemblyInfo  info = (SubmitVSAssemblyInfo ) super.clone(shallow);

         if(!shallow) {
            if(autoRefresh != null) {
               info.autoRefresh = (DynamicValue) autoRefresh.clone();
            }
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone submitVSAssemblyInfo", ex);
      }

      return null;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" refresh=\"" + isRefresh() + "\"");
      writer.print(" refreshValue=\"" + getRefreshValue() + "\"");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      if(Tool.getAttribute(elem, "refreshValue") != null) {
         setRefreshValue(Tool.getAttribute(elem, "refreshValue"));
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      String label = getLabelName();

      if(label != null) {
         writer.print("<labelName>");
         writer.print("<![CDATA[" + label + "]]>");
         writer.println("</labelName>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element node = Tool.getChildNodeByTagName(elem, "labelName");

      if(node != null) {
         labelName = Tool.getValue(node);
      }
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      SubmitVSAssemblyInfo cinfo = (SubmitVSAssemblyInfo) info;

      if(!Tool.equals(labelName, cinfo.labelName)) {
         labelName = cinfo.labelName;
         result = true;
      }

      if(!Tool.equals(autoRefresh, cinfo.autoRefresh)) {
         autoRefresh.setDValue(cinfo.autoRefresh.getDValue());
         result = true;
      }

      return result;
   }

   /**
    * Check whether submit on change.
    * @return true if submit on change, otherwise false.
    */
   public boolean isRefresh() {
      return Boolean.valueOf(autoRefresh.getRuntimeValue(true) + "");
   }

   /**
    * Set the submit on change.
    * @param refresh true if submit on change, otherwise false.
    */
   public void setRefresh(boolean refresh) {
      autoRefresh.setRValue(refresh);
   }

   /**
    * Get whether submit on change.
    * @return true if submit on change, otherwise false.
    */
   public String getRefreshValue() {
      return autoRefresh.getDValue();
   }

   /**
    * Set the submit on change.
    * @param refresh true if submit on change, otherwise false.
    */
   public void setRefreshValue(String refresh) {
      autoRefresh.setDValue(refresh);
   }

   /**
    * override.
    * Get size scale ratio of this assembly.
    */
   @Override
   public Point2D.Double getSizeScale(Point2D.Double scaleRatio) {
      return new Point2D.Double(scaleRatio.x, 1);
   }

   private String labelName = "Submit";
   private DynamicValue autoRefresh = new DynamicValue("true", XSchema.BOOLEAN);
   private static final Logger LOG = LoggerFactory.getLogger(SubmitVSAssemblyInfo.class);
}
