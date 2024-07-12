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
package inetsoft.uql.asset.internal;

import inetsoft.util.Tool;
import inetsoft.util.xml.VersionControlComparators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;

/**
 * ComposedTableAssemblyInfo stores basic composed table assembly information.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class ComposedTableAssemblyInfo extends TableAssemblyInfo {
   /**
    * Constructor.
    */
   public ComposedTableAssemblyInfo() {
      super();

      hierarchical = true;
   }

   /**
    * Check if is composed.
    * @return <tt>true</tt> if composed, <tt>false</tt> otherwise.
    */
   public boolean isComposed() {
      return true;
   }

   /**
    * Check if show in a hierarchical mode.
    * @return <tt>true</tt> to show in a hierarchical mode, <tt>false</tt> to
    * show metadata.
    */
   public boolean isHierarchical() {
      return hierarchical;
   }

   /**
    * Set the hierarchical option.
    * @param hier <tt>true</tt> to show in a hierarchical mode, <tt>false</tt>
    * to show metadata.
    */
   public void setHierarchical(boolean hier) {
      this.hierarchical = hier;
   }

   /**
    * Set whether the child assembly should be iconized.
    */
   public void setIconized(String child, boolean iconized) {
      if(iconized) {
         iconizedChildren.add(child);
      }
      else {
         iconizedChildren.remove(child);
      }
   }

   /**
    * Check whether the child assembly should be iconized.
    */
   public boolean isIconized(String child) {
      return iconizedChildren.contains(child);
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      if(!hierarchical) {
         writer.print(" hierarchical=\"" + hierarchical + "\"");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      this.hierarchical =
         !"false".equals(Tool.getAttribute(elem, "hierarchical"));
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(iconizedChildren.size() > 0) {
         List<String> iconizedChildrenList
            = VersionControlComparators.sortStringSets(iconizedChildren);

         writer.println("<iconized>");

         for(String children : iconizedChildrenList) {
            writer.println("<table><![CDATA[" + children + "]]></table>");
         }

         writer.println("</iconized>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element isnode = Tool.getChildNodeByTagName(elem, "iconized");

      if(isnode != null) {
         NodeList inodes = Tool.getChildNodesByTagName(isnode, "table");

         for(int i = 0; i < inodes.getLength(); i++) {
            Element inode = (Element) inodes.item(i);
            iconizedChildren.add(Tool.getValue(inode));
         }
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      return clone(false);
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone(boolean recursive) {
      try {
         ComposedTableAssemblyInfo info =
            (ComposedTableAssemblyInfo) super.clone();

         info.iconizedChildren = (HashSet<String>) iconizedChildren.clone();

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   private boolean hierarchical;
   private HashSet<String> iconizedChildren = new HashSet<>();

   private static final Logger LOG =
      LoggerFactory.getLogger(ComposedTableAssemblyInfo.class);
}
