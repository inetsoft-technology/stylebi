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
package inetsoft.report.composition.region;

import inetsoft.uql.viewsheet.graph.ChartInfo;
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
      if(style == ChartInfo.TooltipStyle.CARD) {
         return renderCard(palette);
      }

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

   // Single-section: 3-tier hierarchy (capped at tier-3).
   // Multi-section (combined): header + uniform list + emphasized stack total.
   private String renderCard(IndexedSet<String> palette) {
      if(isMultiSection()) {
         return renderCombinedCard(palette);
      }

      StringBuilder buffer = new StringBuilder();
      int tier = 1;

      if(customToolTip != null && customToolTip.length() > 0) {
         // Each line of a multi-line custom template becomes its own tier.
         String[] lines = customToolTip.split(ChartToolTip.ENTER + "|\\r|\\n");

         for(String line : lines) {
            if(line.isEmpty()) {
               continue;
            }

            appendTier(buffer, tier, line);
            tier++;
         }
      }

      for(int i = 0; i < tooltips.size(); i += 2) {
         int keyIdx = tooltips.get(i);

         // Skip the (-1, -1) separator markers used between appended tooltips.
         if(keyIdx == -1) {
            continue;
         }

         String label = palette.get(keyIdx);
         String value = (i + 1) < tooltips.size() ? palette.get(tooltips.get(i + 1)) : "";
         appendTier(buffer, tier, label + ChartToolTip.COLON + value);
         tier++;
      }

      return buffer.toString();
   }

   private boolean isMultiSection() {
      for(int i = 0; i < tooltips.size(); i += 2) {
         if(tooltips.get(i) == -1) {
            return true;
         }
      }

      return false;
   }

   // Header (shared X-dim) + per-series .tt-section blocks for CSS spacing
   // + emphasized stack-total row at the bottom.
   private String renderCombinedCard(IndexedSet<String> palette) {
      StringBuilder buffer = new StringBuilder();
      List<int[]> sections = splitSections();

      if(sections.isEmpty()) {
         return "";
      }

      boolean hasStackTotal = stackTotalName != null && !stackTotalName.isEmpty()
         && sections.size() > 1;
      int lastIndex = sections.size() - 1;
      boolean wroteHeader = false;

      for(int s = 0; s < sections.size(); s++) {
         int[] section = sections.get(s);
         boolean isStackTotal = hasStackTotal && s == lastIndex;

         if(isStackTotal) {
            for(int i = 0; i < section.length; i += 2) {
               String label = palette.get(section[i]);
               String value = (i + 1) < section.length ? palette.get(section[i + 1]) : "";
               appendTier(buffer, 1, label + ChartToolTip.COLON + value);
            }

            continue;
         }

         int start = 0;

         // First pair → tier-1 header, outside any section wrapper.
         if(!wroteHeader && section.length >= 2) {
            String label = palette.get(section[0]);
            String value = palette.get(section[1]);
            appendTier(buffer, 1, label + ChartToolTip.COLON + value);
            wroteHeader = true;
            start = 2;
         }

         // Remaining pairs of this section render as one visual group.
         if(start < section.length) {
            buffer.append("<div class=\"tt-section\">");

            for(int i = start; i < section.length; i += 2) {
               String label = palette.get(section[i]);
               String value = (i + 1) < section.length ? palette.get(section[i + 1]) : "";
               appendTier(buffer, 2, label + ChartToolTip.COLON + value);
            }

            buffer.append("</div>");
         }
      }

      return buffer.toString();
   }

   // Split the flat tooltips list into sections at each (-1, -1) marker.
   private List<int[]> splitSections() {
      List<int[]> sections = new ArrayList<>();
      List<Integer> current = new ArrayList<>();

      for(int i = 0; i < tooltips.size(); i += 2) {
         int key = tooltips.get(i);

         if(key == -1) {
            if(!current.isEmpty()) {
               sections.add(toIntArray(current));
               current.clear();
            }
         }
         else {
            current.add(key);
            current.add((i + 1) < tooltips.size() ? tooltips.get(i + 1) : -1);
         }
      }

      if(!current.isEmpty()) {
         sections.add(toIntArray(current));
      }

      return sections;
   }

   private int[] toIntArray(List<Integer> list) {
      int[] arr = new int[list.size()];

      for(int i = 0; i < list.size(); i++) {
         arr[i] = list.get(i);
      }

      return arr;
   }

   private void appendTier(StringBuilder buffer, int tier, String content) {
      int t = Math.min(tier, 3);
      buffer.append("<div class=\"tt-tier-").append(t).append("\">")
            .append(content)
            .append("</div>");
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

   public ChartInfo.TooltipStyle getStyle() {
      return style;
   }

   public void setStyle(ChartInfo.TooltipStyle style) {
      this.style = style == null ? ChartInfo.TooltipStyle.DEFAULT : style;
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
   private ChartInfo.TooltipStyle style = ChartInfo.TooltipStyle.DEFAULT;
}
