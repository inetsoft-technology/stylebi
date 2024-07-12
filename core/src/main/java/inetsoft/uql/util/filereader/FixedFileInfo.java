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
package inetsoft.uql.util.filereader;

import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;

/**
 * Descriptor for a fixed-width text file.
 *
 * @author InetSoft Technology
 * @since  11.0
 */
public class FixedFileInfo implements TextFileInfo {
   /**
    * Default length for fixed-width fields.
    */
   public static final int DEF_LENGTH = 10;
   
   /**
    * Creates a new instance of <tt>FixedFileInfo</tt>.
    */
   public FixedFileInfo() {
      // default constructor
   }
   
   /**
    * Gets the total length of a data row.
    * 
    * @return the row length.
    */
   public final int getLength() {
      return length;
   }
   
   /**
    * Sets the total length of a data row.
    * 
    * @param length the row length.
    */
   public final void setLength(int length) {
      this.length = length;
   }
   
   /**
    * Gets the lengths of the fields in the file.
    * 
    * @return the field lengths.
    */
   public final int[] getLengths() {
      return lengths;
   }
   
   /**
    * Sets the lengths of the fields in the file.
    * 
    * @param lengths the field lengths.
    */
   public final void setLengths(int[] lengths) {
      this.lengths = lengths;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      Element node = null;
      
      if((node = Tool.getChildNodeByTagName(tag, "length")) != null) {
         length = Integer.parseInt(Tool.getValue(node));
      }
      
      node = Tool.getChildNodeByTagName(tag, "lengths");
      
      if(node != null) {
         NodeList nodes = Tool.getChildNodesByTagName(node, "length");
         lengths = new int[nodes.getLength()];
         
         for(int i = 0; i < lengths.length; i++) {
            node = (Element) nodes.item(i);
            lengths[i] = Integer.parseInt(Tool.getValue(node));
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<info>");
      writer.format("<length>%d</length>%n", length);
      
      if(lengths != null) {
         writer.println("<lengths>");
         
         for(int l : lengths) {
            writer.format("<length>%d</length>%n", l);
         }
         
         writer.println("</lengths>");
      }
      
      writer.println("</info>");
   }

   private int length = 0;
   private int[] lengths = null;
}
