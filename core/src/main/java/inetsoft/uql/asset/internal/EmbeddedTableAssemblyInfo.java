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
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * EmbeddedTableAssemblyInfo stores embedded table assembly information.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class EmbeddedTableAssemblyInfo extends TableAssemblyInfo {
   /**
    * Constructor.
    */
   public EmbeddedTableAssemblyInfo() {
      super();
   }

   /**
    * Get the row count.
    * @return the row count.
    */
   public int getRowCount() {
      return count;
   }

   /**
    * Set the row count.
    * @param count the specified row count.
    */
   public void setRowCount(int count) {
      this.count = count;
   }

   /**
    * Check is embedded table or not.
    */
   @Override
   public boolean isEmbedded() {
      return true;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" count=\"" + count + "\"");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      String txt = Tool.getAttribute(elem, "count");

      if(txt != null) {
         count = Integer.parseInt(txt);
      }
   }

   private int count;
}
