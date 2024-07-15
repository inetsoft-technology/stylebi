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

import inetsoft.report.TableDataPath;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * PrintLayout stores print layout information of a viewsheet.
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public class PrintLayout extends AbstractLayout {
   /**
    * Constructor.
    */
   public PrintLayout() {
      super();
   }

   /**
    * Get printInfo.
    */
   public PrintInfo getPrintInfo() {
      return printInfo;
   }

   /**
    * Set printInfo.
    */
   public void setPrintInfo(PrintInfo printInfo) {
      this.printInfo = printInfo;
   }

   /**
    * Get all assembly layouts.
    */
   public List<VSAssemblyLayout> getHeaderLayouts() {
      return headerLayouts;
   }

   /**
    * Set all assembly layouts.
    */
   public void setHeaderLayouts(List<VSAssemblyLayout> layouts) {
      assert layouts != null;
      this.headerLayouts = layouts;
   }

   /**
    * Get all assembly layouts.
    */
   public List<VSAssemblyLayout> getFooterLayouts() {
      return footerLayouts;
   }

   /**
    * Set all assembly layouts.
    */
   public void setFooterLayouts(List<VSAssemblyLayout> layouts) {
      assert layouts != null;
      this.footerLayouts = layouts;
   }

   /**
    * Get one vs assembly layout by name.
    */
   @Override
   public VSAssemblyLayout getVSAssemblyLayout(String name) {
      VSAssemblyLayout layout = super.getVSAssemblyLayout(name);

      if(layout != null) {
         return layout;
      }

      List<VSAssemblyLayout> layouts = new ArrayList<>();
      layouts.addAll(headerLayouts);
      layouts.addAll(footerLayouts);

      for(VSAssemblyLayout assemblyLayout : layouts) {
         if(assemblyLayout.getName().equals(name)) {
            return assemblyLayout;
         }
      }

      return null;
   }

   /**
   * Apply layout.
   */
   @Override
   public Viewsheet apply(Viewsheet vs) {
      Viewsheet vs0 = super.apply(vs);
      List<VSAssemblyLayout> layouts = new ArrayList<>(headerLayouts);
      layouts.addAll(footerLayouts);

      for(VSAssemblyLayout alayout : layouts) {
         if(alayout instanceof VSEditableAssemblyLayout) {
            VSAssemblyInfo info =
               ((VSEditableAssemblyLayout) alayout).getInfo();
            info.setLayoutPosition(alayout.getPosition());
            info.setLayoutSize(alayout.getSize());
            applyScaleFont(info);
         }
      }

      return vs0;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && headerLayouts.isEmpty() && footerLayouts.isEmpty();
   }

   /**
    *  Apply the font scale.
    */
   private void applyScaleFont(VSAssemblyInfo info) {
      FormatInfo formatInfo = info.getFormatInfo();
      TableDataPath[] paths = formatInfo.getPaths();

      for(TableDataPath path : paths) {
         VSCompositeFormat format = formatInfo.getFormat(path);
         format.setRScaleFont(getScaleFont());
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<printLayout class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</printLayout>");
   }

   /**
    * Write contents.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(printInfo != null) {
         printInfo.writeXML(writer);
      }

      if(headerLayouts != null && headerLayouts.size() > 0) {
         writer.print("<headerLayouts>");

         for(VSAssemblyLayout vsAssemblyLayout : headerLayouts) {
            vsAssemblyLayout.writeXML(writer);
         }

         writer.println("</headerLayouts>");
      }

      if(footerLayouts != null && footerLayouts.size() > 0) {
         writer.print("<footerLayouts>");

         for(VSAssemblyLayout vsAssemblyLayout : footerLayouts) {
            vsAssemblyLayout.writeXML(writer);
         }

         writer.println("</footerLayouts>");
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
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      printInfo = new PrintInfo();
      printInfo.parseXML(Tool.getChildNodeByTagName(elem, "printInfo"));
      headerLayouts = parseLayouts(Tool.getChildNodeByTagName(elem, "headerLayouts"));
      footerLayouts = parseLayouts(Tool.getChildNodeByTagName(elem, "footerLayouts"));
   }

   private List<VSAssemblyLayout> parseLayouts(Element element) throws Exception {
      List<VSAssemblyLayout> list = new ArrayList<>();

      if(element != null) {
         NodeList nodes = Tool.getChildNodesByTagName(element, "vsAssemblyLayout");

         for(int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i);
            list.add(AbstractLayout.createAssemblyLayout(node));
         }
      }

      return list;
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public PrintLayout clone() {
      try {
         PrintLayout layout = (PrintLayout) super.clone();
         layout.printInfo = (PrintInfo) printInfo.clone();

         if(headerLayouts != null) {
            layout.headerLayouts = Tool.deepCloneCollection(headerLayouts);
         }

         if(footerLayouts != null) {
            layout.footerLayouts = Tool.deepCloneCollection(footerLayouts);
         }

         return layout;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   private PrintInfo printInfo;
   private List<VSAssemblyLayout> headerLayouts = new ArrayList<>();
   private List<VSAssemblyLayout> footerLayouts = new ArrayList<>();

   private static final Logger LOG = LoggerFactory.getLogger(PrintLayout.class);
}
