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
package inetsoft.report.lens.xnode;

import inetsoft.report.TextLens;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Format;

/**
 * Text lens for extracting text value from a data tree. The text value
 * is taken from the first non-null node on the tree.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class XNodeTextLens implements TextLens {
   /**
    * Create a text lens from a data tree.
    */
   public XNodeTextLens(XNode root) {
      // if a table, use the first cell on the table
      if(root instanceof XTableNode) {
         XTableNode table = (XTableNode) root;

         table.rewind();

         if(table.next() && table.getColCount() > 0) {
            Object val = table.getObject(0);
            XMetaInfo info = table.getXMetaInfo(0);
            XFormatInfo formatInfo = info != null ? info.getXFormatInfo() : null;
            format = formatInfo != null ? TableFormat.getFormat(
               formatInfo.getFormat(), formatInfo.getFormatSpec()) : null;

            if(val != null) {
               value = val;
            }
         }

         table.close();
      }
      else {
         format = root.getDefaultFormat();
      }

      if(value == null) {
         // find the none-null node on the tree branch
         for(XNode node = root; node != null;
             node = (node.getChildCount() > 0) ? node.getChild(0) : null)
         {
            Object val = node.getValue();

            if(val != null) {
               value = val;
               break;
            }
         }
      }

      if(root != null) {
         root.removeAllChildren();
      }
   }

   /**
    * Get the text content.
    * @return text string.
    */
   @Override
   public String getText() {
      return (value == null) ? "" : value.toString();
   }

   /**
    * Get the value before converting to text.
    */
   public Object getValue() {
      return value;
   }

   /**
    * Clone the object.
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
    * Get the default format.
    */
   public Format getDefaultFormat() {
      return format;
   }

   Object value;
   Format format;

   private static final Logger LOG =
      LoggerFactory.getLogger(XNodeTextLens.class);
}
