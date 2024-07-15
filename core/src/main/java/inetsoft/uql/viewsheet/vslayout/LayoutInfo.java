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
package inetsoft.uql.viewsheet.vslayout;

import inetsoft.uql.asset.AssetObject;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * LayoutInfo stores the all layout and all device.
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public class LayoutInfo implements AssetObject {
   /**
    * Constructor.
    */
   public LayoutInfo() {
      viewsheetLayouts = new ArrayList<>();
   }

   /**
    * Get print layout.
    */
   public PrintLayout getPrintLayout() {
      return printLayout;
   }

   /**
    * Set print layout.
    */
   public void setPrintLayout(PrintLayout printLayout) {
      this.printLayout = printLayout;
   }

   public boolean hasViewsheetLayout() {
      return viewsheetLayouts != null && viewsheetLayouts.size() > 0;
   }

   /**
    * Get viewsheet layouts.
    */
   public List<ViewsheetLayout> getViewsheetLayouts() {
      return viewsheetLayouts;
   }

   /**
    * Set viewsheet layouts.
    */
   public void setViewsheetLayouts(List<ViewsheetLayout> layouts) {
      this.viewsheetLayouts = layouts;
   }

   /**
    * Finds the layout that best matches the specified device parameters.
    *
    * @param width     the display width of the device.
    * @param mobile    <tt>true</tt> if a mobile device.
    *
    * @return the matching layout.
    */
   public ViewsheetLayout matchLayout(int width, boolean mobile) {
      ViewsheetLayout matchedLayout = null;

      for(ViewsheetLayout layout : viewsheetLayouts) {
         if(layout.isEmpty()) {
            LOG.info("Empty layout ignored: " + layout.getName());
         }
         else if(mobile || !layout.isMobileOnly()) {
            int[] bounds = layout.getBounds();

            if(width >= bounds[0] && width <= bounds[1]) {
               matchedLayout = layout;
               break;
            }
         }
      }

      return matchedLayout;
   }

   /**
    * Match the viewsheet layout to the specified dpi.
    * @return the specified viewsheet layout.
    */
   public ViewsheetLayout matchDPI(double pixelDensity, ViewsheetLayout vslayout) {
      double ratio = pixelDensity / PREVIEW_DPI;

      if(ratio == 1) {
         return vslayout;
      }

      ViewsheetLayout vslayout0 = vslayout.clone();
      vslayout0.setScaleFont((float) (vslayout0.getScaleFont() * ratio));

      for(VSAssemblyLayout alayout:vslayout0.getVSAssemblyLayouts()) {
         if(alayout == null) {
            continue;
         }

         Dimension size = alayout.getSize();
         Point pos = alayout.getPosition();
         Dimension nsize = new Dimension((int) Math.floor(size.width * ratio),
                              (int) Math.floor(size.height * ratio));
         Point npos = new Point((int) Math.floor(pos.x * ratio),
                         (int) Math.floor(pos.y * ratio));
         alayout.setSize(nsize);
         alayout.setPosition(npos);
      }

      return vslayout0;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<layoutInfo class=\"" + getClass().getName()+ "\" ");
      writer.println(">");
      writeContents(writer);
      writer.print("</layoutInfo>");
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   private void writeContents(PrintWriter writer) {
      if(viewsheetLayouts != null && viewsheetLayouts.size() > 0) {
         writer.print("<viewsheetLayouts>");

         for(ViewsheetLayout viewsheetLayout : viewsheetLayouts) {
            viewsheetLayout.writeXML(writer);
         }

         writer.println("</viewsheetLayouts>");
      }

      if(printLayout != null) {
         printLayout.writeXML(writer);
      }
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      parseContents(elem);
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   private void parseContents(Element elem) throws Exception {
      Element viewsheetLayoutsNode =
         Tool.getChildNodeByTagName(elem, "viewsheetLayouts");

      if(viewsheetLayoutsNode != null) {
         NodeList viewsheetLayoutsList =
            Tool.getChildNodesByTagName(viewsheetLayoutsNode,
                                        "viewsheetLayout");

         if(viewsheetLayoutsList != null &&
            viewsheetLayoutsList.getLength() > 0)
         {
            for(int i = 0; i < viewsheetLayoutsList.getLength(); i++) {
               Element mNode = (Element) viewsheetLayoutsList.item(i);
               ViewsheetLayout viewsheetLayout = new ViewsheetLayout();
               viewsheetLayout.parseXML(mNode);
               viewsheetLayouts.add(viewsheetLayout);
            }
         }
      }

      Element printLayoutNode = Tool.getChildNodeByTagName(elem, "printLayout");

      if(printLayoutNode != null) {
         printLayout = new PrintLayout();
         printLayout.parseXML(printLayoutNode);
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         LayoutInfo info2 = (LayoutInfo) super.clone();
         info2.viewsheetLayouts = Tool.deepCloneCollection(viewsheetLayouts);

         if(printLayout != null) {
            info2.printLayout = printLayout.clone();
         }

         return info2;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   private List<ViewsheetLayout> viewsheetLayouts;
   private PrintLayout printLayout;
   private static final int PREVIEW_DPI = 160;

   private static final Logger LOG =
      LoggerFactory.getLogger(LayoutInfo.class);
}
