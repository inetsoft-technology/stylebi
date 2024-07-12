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
package inetsoft.report.composition.region;

import inetsoft.util.DataSerializable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ChartToolTip defines the values and index mapping to avoid redundancy.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class ChartToolTip implements DataSerializable {
   /**
    * Colon character.
    */
   public static final String COLON = ":&nbsp;";
   /**
    * Enter character.
    */
   public static final String ENTER = "&#13;";

   /**
    * Constructor.
    */
   public ChartToolTip() {
      tooltips = new ArrayList<>();
   }

   /**
    * Generator tooltip with special palette.
    */
   public String getTooltip(IndexedSet<String> palette) {
      StringBuilder buffer = new StringBuilder();

      if(customToolTip != null && customToolTip.length() > 0) {
         buffer.append(customToolTip);
      }

      if(tooltips.size() > 0 && customToolTip != null && customToolTip.length() > 0) {
         buffer.append(ChartToolTip.ENTER);
      }

      for(int i = 0; i < tooltips.size(); i++) {
         if(i != 0 && (i % 2) == 0) {
            buffer.append(ChartToolTip.ENTER);

            if(tooltips.get(i) == -1) {
               i ++;
               continue;
            }
         }
         else if((i % 2) == 1) {
            buffer.append(ChartToolTip.COLON);
         }

         buffer.append(palette.get(tooltips.get(i)));
      }

      return buffer.toString();
   }

   public List<Integer> getTooltipList() {
      return tooltips;
   }

   /**
    * Add an chart tooltip.
    * @param key the specified tooltip key.
    * @param value the specified tooltip value.
    */
   public void addTooltip(int key, int value) {
      for(int i = 0; i < tooltips.size(); i += 2) {
         if(key == tooltips.get(i) && value == tooltips.get(i + 1)) {
            return;
         }
      }

      tooltips.add(key);
      tooltips.add(value);
   }

   public boolean containsTooltip(int key) {
      return tooltips.contains(key);
   }

   public int getTooltipValue(int key) {
      for(int i = 0; i < tooltips.size(); i += 2) {
         if(key == tooltips.get(i) && i + 1 < tooltips.size()) {
            return tooltips.get(i + 1);
         }
      }

      return -1;
   }

   /**
    * Removes a chart tooltip by key
    * @param key the specified tooltip key.
    */
   public void removeTooltip(int key) {
      removeTooltip(key, false);
   }

   public void removeTooltip(int key, boolean removeSplitSpace) {
      for(int i = 0; i < tooltips.size(); i += 2) {
         if(key == tooltips.get(i)) {
            int idx = i;
            if(removeSplitSpace && i > 1) {
               if((idx - 1) < tooltips.size() && tooltips.get(idx - 1) == -1) {
                  tooltips.remove(idx - 1);
                  idx--;
               }

               if((idx - 1) < tooltips.size() && tooltips.get(idx - 1) == -1) {
                  tooltips.remove(idx - 1);
                  idx--;
               }
            }

            tooltips.remove(idx);
            tooltips.remove(idx);
         }
      }
   }

   public void appendTooltips(ChartToolTip newTooltip) {
      appendTooltips(newTooltip, true);
   }

   public void appendTooltips(ChartToolTip newTooltip, boolean withSplitSpace) {
      if(newTooltip == null || newTooltip.isEmpty()) {
         return;
      }

      if(tooltips.size() > 0 && this.tooltips.get(tooltips.size() - 1) != -1 || withSplitSpace) {
         this.tooltips.add(-1);
         this.tooltips.add(-1);
      }

      this.tooltips.addAll(newTooltip.getTooltipList());
   }

   /**
    * Set custom tool tip.
    * @param customToolTip the specified custom tool tip.
    */
   public void setCustomToolTip(String customToolTip) {
      this.customToolTip = customToolTip;
   }

   /**
    * Get custom tool tip.
    * @return custom tool tip if any.
    */
   public String getCustomToolTip() {
      return customToolTip;
   }

   /**
    * Check is empty.
    */
   public boolean isEmpty() {
      return tooltips.isEmpty();
   }

   public String getStackTotalName() {
      return stackTotalName;
   }

   public void setStackTotalName(String stackTotalName) {
      this.stackTotalName = stackTotalName;
   }

   /**
    * Clear chart tooltip.
    */
   public void clear() {
      tooltips.clear();
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      boolean custom = customToolTip != null;
      output.writeBoolean(custom);

      if(custom) {
         output.writeUTF(customToolTip);
      }

      int len = tooltips.size();
      output.writeInt(len);

      for(Integer tooltip : tooltips) {
         output.writeInt(tooltip);
      }
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @retrun <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean parseData(DataInputStream input) {
      return true;
   }

   @Override
   public String toString() {
      return super.toString() + "[" + customToolTip + ": " + tooltips + "]";
   }

   private final List<Integer> tooltips;
   private String stackTotalName = null;
   private String customToolTip;
}
