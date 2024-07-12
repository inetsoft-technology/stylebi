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
package inetsoft.graph.aesthetic;

import inetsoft.graph.data.DataSet;
import inetsoft.util.MessageFormat;

import java.text.Format;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This text frame returns the value of the current dimension as defined by certain chart
 * types, such as treemap (CIRCLE, ICICLE, SUNBURST). This allows text labels to be generated
 * based on the current context of a VO.
 *
 * @version 12.3
 * @author InetSoft Technology
 */
public class CurrentTextFrame extends TextFrame {
   public CurrentTextFrame() {
   }

   /**
    * Create a text frame.
    * @param treeDims the tree dimensions in TreemapElement.
    */
   public CurrentTextFrame(List<String> treeDims) {
      this.treeDims = treeDims;
   }

   @Override
   public Object getText(DataSet data, String col, int row) {
      if(!isTextVisible(data, row)) {
         return null;
      }

      String curr = currField.get();

      if(curr != null) {
         if(includeParents) {
            List labels = new ArrayList();
            boolean messageFmt = MessageFormat.isMessageFormat(getFormat(curr));

            for(String dim : treeDims) {
               Object val = super.getText(data, dim, row);
               Format fmt = getFormat(dim);

               // if a format is defined for the dim, and there is no message format for this
               // label, then format the individual value.
               if(!messageFmt && fmt != null) {
                  try {
                     val = fmt.format(val);
                  }
                  catch(IllegalArgumentException ex) {
                     // ignore, e.g. 'Others' for date
                  }
               }

               labels.add(val);

               if(curr.equals(dim)) {
                  break;
               }
            }

            return labels.toArray();
         }

         return super.getText(data, curr, row);
      }

      return super.getText(data, col, row);
   }

   private String getCurrentField() {
      String curr = currField.get();
      return curr != null ? curr : super.getField();
   }

   /**
    * Get the format for the current level.
    */
   public Format getFormat() {
      return getFormat(getCurrentField());
   }

   /**
    * Set the format for a field to format value.
    */
   public void setFormat(String field, Format fmt) {
      if(fmt != null) {
         this.fmtmap.put(field, fmt);
      }
      else {
         this.fmtmap.remove(field);
      }
   }

   /**
    * Get the format for a field to format value.
    */
   public Format getFormat(String field) {
      return field != null ? fmtmap.get(field) : null;
   }

   /**
    * Check if parents labels should be included in the label.
    */
   public boolean isIncludeParents() {
      return includeParents;
   }

   /**
    * Set whether only the current dimension, or all parents labels should be included
    * in the label.
    */
   public void setIncludeParents(boolean includeParents) {
      this.includeParents = includeParents;
   }

   /**
    * Set the current field of a visual object as defined by the chart type, if any.
    */
   public static void setCurrentField(String field) {
      if(field != null) {
         currField.set(field);
      }
      else {
         currField.remove();
      }
   }

   private static ThreadLocal<String> currField = new ThreadLocal<>();
   private Map<String, Format> fmtmap = new ConcurrentHashMap<>();
   private List<String> treeDims;
   private boolean includeParents = false;
   private static final long serialVersionUID = 1L;
}
