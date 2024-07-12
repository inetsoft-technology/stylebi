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
package inetsoft.uql;

import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;

import java.io.*;
import java.util.Objects;

/**
 * A Condition Item represents a item of condition in condition group.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class JunctionOperator implements Cloneable, Serializable, HierarchyItem {
   public static final int AND = 0;
   public static final int OR = 1;
   /**
    * Create a Junction Operator.
    */
   public JunctionOperator() {
      this(AND, 0);
   }

   /**
    * Create a Junction Operator.
    */
   public JunctionOperator(int op, int level) {
      this.junction = op;
      this.level = level;
   }

   /**
    * Get the condition item level.
    */
   public int getJunction() {
      return junction;
   }

   /**
    * Get the condition item level.
    */
   @Override
   public int getLevel() {
      return level;
   }

   /**
    * Get the condition item level.
    */
   public void setJunction(int op) {
      this.junction = op;
   }

   /**
    * Get the condition item level.
    */
   @Override
   public void setLevel(int level) {
      this.level = level;
   }

   /**
    * Clone this object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   /**
    * Write this data selection to XML.
    * @param writer the stream to output the XML text to
    */
   public void writeXML(PrintWriter writer) {
      writer.println("<junction junction=\"" + getJunction() + "\" level=\"" +
         getLevel() + "\"/>");
   }

   /**
    * Read in the XML representation of this object.
    * @param tag the XML element representing this object.
    */
   public void parseXML(Element tag) throws IOException, DOMException {
      String str;

      if((str = Tool.getAttribute(tag, "junction")) != null) {
         junction = Integer.parseInt(str);
      }

      if((str = Tool.getAttribute(tag, "level")) != null) {
         level = Integer.parseInt(str);
      }
   }

   /**
    * Get string representation.
    */
   public String toString() {
      return toString(true);
   }

   /**
    * Get string representation.
    */
   public String toString(boolean shlvl) {
      StringBuilder buf = new StringBuilder();

      if(shlvl) {
         for(int i = 0; i < level; i++) {
            buf.append(".........");
         }
      }

      buf.append(junction == AND ?
         "[" + Catalog.getCatalog().getString("and") + "]" :
         "[" + Catalog.getCatalog().getString("or") + "]");

      return buf.toString();
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof JunctionOperator)) {
         return false;
      }

      JunctionOperator jun2 = (JunctionOperator) obj;
      return junction == jun2.junction && level == jun2.level;
   }

   @Override
   public int hashCode() {
      return Objects.hash(junction, level);
   }

   private int junction;
   private int level;

   private static final Logger LOG = LoggerFactory.getLogger(JunctionOperator.class);
}

