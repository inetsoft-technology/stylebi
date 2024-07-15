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
package inetsoft.report.internal;

import inetsoft.report.LibManager;
import inetsoft.report.style.TableStyle;
import inetsoft.report.style.XTableStyle;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import java.io.PrintWriter;
import java.util.*;

/**
 * A StyleTreeModel manages all table styles. This class does not really
 * expend the TreeModel in swing, but keeps a tree which can be then used
 * to create a model.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class StyleTreeModel implements XMLSerializable {

   public static final String SEPARATOR = LibManager.SEPARATOR;
   /**
    * The tag of tablestyle.
    */
   public static final String TABLESTYLE = "TableStyle.";

   /**
    * Constructor.
    */
   public StyleTreeModel() {
   }

   /**
    * Get the root of the style tree.
    */
   public DefaultMutableTreeNode getRoot() {
      return top;
   }

   /**
    * Add a folder.
    * @param folder the folder name to be added.
    */
   public static void addFolder(String folder) {
      try {
         LibManager mgr = LibManager.getManager();
         mgr.refresh(false);
         mgr.addTableStyleFolder(folder);
         mgr.save();
      }
      catch(Exception ex) {
         LOG.error("Failed to add folder: " + folder, ex);
      }
   }

   /**
    * Get existing table style id.
    * @param name the full name of the specified table style.
    * @return table style id if any.
    */
   public static String getTableStyleID(String name) {
      Objects.requireNonNull(name);
      LibManager mgr = LibManager.getManager();
      Enumeration<String> styles = mgr.getTableStyles();

      while(styles.hasMoreElements()) {
         XTableStyle tstyle = (XTableStyle) get(styles.nextElement());

         if(name.equals(tstyle.getName()) || name.equals(tstyle.getID())) {
            return tstyle.getID();
         }
      }

      return null;
   }

   /**
    * Get a table style class. The name of the style is either one of the
    * default style class name (without the package name), or the style
    * name of an user defined class.
    * @param name name of the style.
    * @return table style.
    */
   public static TableStyle get(String name) {
      return LibManager.getManager().getTableStyle(name);
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<styleModel>");
      top.writeXML(writer);
      writer.println("</styleModel>");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
   }

   /**
    * Get table style name to display.
    * @param name a table style's full path.
    * @return table style's display name.
    */
   public static String getDisplayName(String name) {
      int idx = name.lastIndexOf(SEPARATOR);
      name = idx < 0 ? name : name.substring(idx + 1);

      return name;
   }

   /**
    * Get parent path.
    * @param name the full path of a folder or a table style.
    * @return parent path of a folder or a table style if any.
    */
   public static String getParentPath(String name) {
      int idx = name.lastIndexOf(SEPARATOR);

      if(idx < 0) {
         return null;
      }

      return name.substring(0, idx);
   }

   /**
    * Style tree node.
    */
   private static class StyleTreeNode
      extends DefaultMutableTreeNode implements XMLSerializable
   {
      public StyleTreeNode(Object userObject) {
         super(userObject);
      }

      @Override
      public void writeXML(PrintWriter writer) {
         writeNode(this, writer);
      }

      @Override
      public void parseXML(Element tag) throws Exception {
      }

      public String toString() {
         if(userObject instanceof XTableStyle) {
            return ((XTableStyle) userObject).getID();
         }

         return super.toString();
      }

      public boolean equals(Object obj) {
         if(!(obj instanceof StyleTreeNode)) {
            return false;
         }

         StyleTreeNode node2 = (StyleTreeNode) obj;

         return Tool.equals(this.userObject, node2.userObject) &&
            Tool.equals(this.par, node2.par);
      }

      @Override
      public void setParent(MutableTreeNode parent) {
         super.setParent(parent);

         if(parent != null) {
            this.par = parent;
         }
      }

      public int hashCode() {
         int hashCode = userObject == null ? 0 : userObject.hashCode();

         if(par != null) {
            hashCode += par.hashCode();
         }

         return hashCode;
      }

      private void writeNode(StyleTreeNode node, PrintWriter writer) {
         if(node.userObject instanceof String) {
            String folder = (String) node.userObject;
            writer.print("<directory label=\"");
            writer.print(getDisplayName(folder));
            writer.print("\" data=\"");
            writer.print(folder);
            writer.println("\">");

            Enumeration nodes = node.children();

            while(nodes.hasMoreElements()) {
               StyleTreeNode node0 = (StyleTreeNode) nodes.nextElement();
               writeNode(node0, writer);
            }

            writer.println("</directory>");
         }
         else {
            XTableStyle tstyle = (XTableStyle) node.userObject;
            String name = tstyle.getName();
            writer.print("<style icon=\"TableStyleIcon\" label=\"");
            writer.print(getDisplayName(name));
            writer.print("\" data=\"");
            writer.print(tstyle.getID());
            writer.println("\" />");
         }
      }

      private Map<String, Object> writeNode(StyleTreeNode node) {
         if(node.userObject instanceof String) {
            Map<String, Object> directory = new HashMap<>();
            String folder = (String) node.userObject;
            directory.put("label", getDisplayName(folder));
            directory.put("data", folder);

            Enumeration nodes = node.children();
            List<Object> directories = new ArrayList<>();

            while(nodes.hasMoreElements()) {
               StyleTreeNode node0 = (StyleTreeNode) nodes.nextElement();
               directories.add(writeNode(node0));
            }

            directory.put("directories", directories);

            return directory;
         }
         else {
            Map<String, Object> style = new HashMap<>();
            XTableStyle tstyle = (XTableStyle) node.userObject;
            String name = tstyle.getName();
            style.put("sIcon", "TableStyleIcon");
            style.put("SLabel", getDisplayName(name));
            style.put("sData", tstyle.getID());

            return style;
         }
      }

      private Object par;
   }

   StyleTreeNode top =
      new StyleTreeNode(Catalog.getCatalog().getString("Styles"));

   private static final Logger LOG =
      LoggerFactory.getLogger(StyleTreeModel.class);
}
