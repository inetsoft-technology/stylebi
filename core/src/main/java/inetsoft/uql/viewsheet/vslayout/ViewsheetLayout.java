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
package inetsoft.uql.viewsheet.vslayout;

import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.Arrays;

/**
 * ViewsheetLayout stores layout information of a viewsheet.
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public class ViewsheetLayout extends AbstractLayout {
   /**
    * Constructor.
    */
   public ViewsheetLayout() {
      super();

      scaleToScreen = true;
      fitToWidth = true;
      mobileOnly = true;
      deviceIds = new String[]{};
   }

   /**
    * Apply layout.
    */
   @Override
   public Viewsheet apply(Viewsheet viewsheet) {
      Viewsheet viewsheet0 = super.apply(viewsheet);
      ViewsheetInfo viewsheetInfo = viewsheet0.getViewsheetInfo();

      if(viewsheetInfo != null) {
         viewsheetInfo.setScaleToScreen(scaleToScreen);
         viewsheetInfo.setFitToWidth(fitToWidth);
      }

      viewsheet0.setOriginalVs(viewsheet);

      return viewsheet0;
   }

   /**
    * Get scale font. Viewsheet layouts have scale font set to 1.
    */
   @Override
   public float getScaleFont() {
      return 1;
   }

   /**
    * Gets the display name of this layout.
    *
    * @return the layout name.
    */
   public String getName() {
      return name;
   }

   /**
    * Sets the display name of this layout.
    *
    * @param name the layout name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Check if scale to screen.
    */
   public boolean isScaleToScreen() {
      return scaleToScreen;
   }

   /**
    * Set whether if scale to screen.
    */
   public void setScaleToScreen(boolean scaleToScreen) {
      this.scaleToScreen = scaleToScreen;
   }

   /**
    * Check if fit to width.
    */
   public boolean isFitToWidth() {
      return fitToWidth;
   }

   /**
    * Set whether if fit to width.
    */
   public void setFitToWidth(boolean fitToWidth) {
      this.fitToWidth = fitToWidth;
   }

   /**
    * Gets the flag that indicates if this layout should only be applied to
    * mobile devices.
    *
    * @return <tt>true</tt> if mobile only; <tt>false</tt> otherwise.
    */
   public boolean isMobileOnly() {
      return mobileOnly;
   }

   /**
    * Sets the flag that indicates if this layout should only be applied to
    * mobile devices.
    *
    * @param mobileOnly <tt>true</tt> if mobile only; <tt>false</tt> otherwise.
    */
   public void setMobileOnly(boolean mobileOnly) {
      this.mobileOnly = mobileOnly;
   }

   /**
    * Get device names in a viewsheet layout.
    */
   public String[] getDeviceIds() {
      return deviceIds;
   }

   /**
    * Set device names to a viewsheet layout.
    */
   public void setDeviceIds(String[] deviceIds) {
      this.deviceIds = deviceIds;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   @SuppressWarnings("Duplicates")
   public void writeXML(PrintWriter writer) {
      writer.print("<viewsheetLayout class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</viewsheetLayout>");
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print(" scaleToScreen=\"" + scaleToScreen + "\"");
      writer.print(" fitToWidth=\"" + fitToWidth + "\"");
      writer.print(" mobileOnly=\"" + mobileOnly + "\"");
   }

   /**
    * Write contents.
    */
   @Override
   @SuppressWarnings("Duplicates")
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(name != null) {
         writer.format("<name><![CDATA[%s]]></name>%n", name);
      }

      if(deviceIds != null && deviceIds.length > 0) {
         writer.print("<deviceIds>");

         for(String deviceId : deviceIds) {
            writer.format("<deviceId><![CDATA[%s]]></deviceId>", deviceId);
         }

         writer.println("</deviceIds>");
      }
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      parseAttributes(elem);
      parseContents(elem);
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      // @by jasonshobe, discussed this with Larry, and we could not come up
      // with a compelling use case not to force scaling for device-based
      // viewsheets, especially since the implementation has been simplified to
      // use only the CSS width of the window like responsive HTML.
//      this.scaleToScreen =
//         !"false".equals(Tool.getAttribute(elem, "scaleToScreen"));
//      this.fitToWidth = !"false".equals(Tool.getAttribute(elem, "fitToWidth"));
      this.scaleToScreen = true;
      this.fitToWidth = true;
      this.mobileOnly = !"false".equals(Tool.getAttribute(elem, "mobileOnly"));
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element nameNode = Tool.getChildNodeByTagName(elem, "name");

      if(nameNode != null) {
         name = Tool.getValue(nameNode);
      }

      NodeList nodes = elem.getElementsByTagName("deviceId");
      deviceIds = new String[nodes.getLength()];

      for(int i = 0; i < nodes.getLength(); i++) {
         Element node = (Element) nodes.item(i);
         deviceIds[i] = Tool.getValue(node);
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public ViewsheetLayout clone() {
      try {
         ViewsheetLayout layout = (ViewsheetLayout) super.clone();

         if(deviceIds != null) {
            layout.deviceIds = deviceIds.clone();
         }

         return layout;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   @Override
   public String toString() {
      return "ViewsheetLayout{" +
         "name='" + name + '\'' +
         ", scaleToScreen=" + scaleToScreen +
         ", fitToWidth=" + fitToWidth +
         ", mobileOnly=" + mobileOnly +
         ", deviceIds=" + Arrays.toString(deviceIds) +
         "} " + super.toString();
   }

   /**
    * Gets the bounds of the devices that match this layout.
    *
    * @return the minimum and maximum widths.
    */
   public int[] getBounds() {
      int min = Integer.MAX_VALUE;
      int max = Integer.MIN_VALUE;

      if(deviceIds != null) {
         for(String id : deviceIds) {
            DeviceInfo device = DeviceRegistry.getRegistry().getDevice(id);

            if(device != null) {
               min = Math.min(min, device.getMinWidth());
               max = Math.max(max, device.getMaxWidth());
            }
         }
      }

      return new int[] { min, max };
   }

   private String name;
   private boolean scaleToScreen;
   private boolean fitToWidth;
   private boolean mobileOnly;
   private String[] deviceIds;

   private static final Logger LOG =
      LoggerFactory.getLogger(ViewsheetLayout.class);
}
