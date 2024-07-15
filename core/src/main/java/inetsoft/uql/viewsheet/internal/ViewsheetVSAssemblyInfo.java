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

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;
import java.util.List;
import java.util.*;

/**
 * ViewsheetVSAssemblyInfo, the assembly info of a view sheet.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ViewsheetVSAssemblyInfo extends VSAssemblyInfo {
   /**
    * Constructor.
    */
   public ViewsheetVSAssemblyInfo() {
      super();
      this.setPixelOffset(new Point(0, 0));
      initDefaultFormat();
   }

   /**
    * Check if the VSAssembly is resizable.
    * @return <tt>true</tt> of resizable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isResizable() {
      return false;
   }

   /**
    * Check if this assembly is embedded.
    * @return <tt>true</tt> if embedded, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEmbedded() {
      return vs != null;
   }

   /**
    * Get the absolute name of this assembly.
    * @return the absolute name of this assembly.
    */
   @Override
   public String getAbsoluteName() {
      Viewsheet vs = getViewsheet();
      String pname = vs == null ? null : vs.getAbsoluteName();

      return vs == null ? null :
         (pname == null ? getName() : pname + "." + getName());
   }

   /**
    * Get the primary assembly count.
    * @return primaryCount the primary assembly count.
    */
   public int getPrimaryCount() {
      return primaryCount;
   }

   /**
    * Set the primary assembly count.
    * @parameter primaryCount the primary assembly count.
    */
   public void setPrimaryCount(int primaryCount) {
      this.primaryCount = primaryCount;
   }

   /**
    * Get the number of assemblies, including the non-primary assemblies.
    */
   public int getAssemblyCount() {
      return assemblyCount;
   }

   /**
    * Set the number of assemblies, including the non-primary assemblies.
    */
   public void setAssemblyCount(int assemblyCount) {
      this.assemblyCount = assemblyCount;
   }

   /**
    * Get the mirrored viewsheet entry.
    * @return the mirrored viewsheet entry.
    */
   public AssetEntry getEntry() {
      return entry;
   }

   /**
    * Set the mirrored viewsheet entry.
    * @param entry the specified mirrored viewsheet entry.
    */
   public void setEntry(AssetEntry entry) {
      this.entry = entry;
   }

   /**
    * Set the child assembly of this view.
    * @param list of assembly object.
    */
   public void setChildAssemblies(List list) {
      childAssemblies = list;
   }

   /**
    * Get the child assembly of this view.
    */
   public List getChildAssemblies() {
      return childAssemblies;
   }

   /**
    * Get the bounds (in row/column) of all assemblies in this viewsheet.
    */
   public Rectangle getAssemblyBounds() {
      return compbounds;
   }

   /**
    * Set the bounds (in row/column) of all assemblies in this viewsheet.
    */
   public void setAssemblyBounds(Rectangle box) {
      this.compbounds = box;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print(" primaryCount=\"" + primaryCount + "\"");
      writer.print(" assemblyCount=\"" + assemblyCount + "\"");
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);
      String count = Tool.getAttribute(elem, "primaryCount");

      if(count != null) {
         primaryCount = Integer.parseInt(count);
      }

      count = Tool.getAttribute(elem, "assemblyCount");

      if(count != null) {
         assemblyCount = Integer.parseInt(count);
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(entry != null) {
         writer.print("<viewsheetEntry>");
         entry.writeXML(writer);
         writer.println("</viewsheetEntry>");
      }

      if(childAssemblies != null) {
         Iterator iter = childAssemblies.iterator();
         writer.println("<childAssemblies>");

         while(iter.hasNext()) {
            Assembly child = (Assembly) iter.next();
            writer.print("<childAssembly><![CDATA[" + child.getAbsoluteName() + "]]>");
            writer.println("</childAssembly>");
         }

         writer.println("</childAssemblies>");
      }

      if(compbounds != null) {
         writer.println("<bounds x=\"" + compbounds.x + "\" y=\"" +
                        compbounds.y + "\" width=\"" + compbounds.width +
                        "\" height=\"" + compbounds.height + "\"/>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);
      Element entryNode = Tool.getChildNodeByTagName(elem, "viewsheetEntry");

      if(entryNode != null) {
         entry = new AssetEntry();
         entry.parseXML((Element) entryNode.getFirstChild());
      }
   }

   @Override
   protected void setDefaultFormat(boolean border) {
      super.setDefaultFormat(border);
      getFormat().getDefaultFormat().setBackgroundValue("#f5f5f5");
   }

   private int primaryCount = 0;
   private int assemblyCount = 0;
   private AssetEntry entry;
   // runtime
   private transient List childAssemblies = new ArrayList();
   private Rectangle compbounds; // bounds (row/col) of assemblies
}
