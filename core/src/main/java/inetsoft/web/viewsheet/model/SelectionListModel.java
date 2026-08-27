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
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionBaseVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSObjectChromeDefaults;
import inetsoft.uql.viewsheet.internal.VizContext;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.awt.*;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SelectionListModel {
   public SelectionListModel(SelectionList slist, VSAssemblyInfo assemblyInfo, int limit) {
      this.assemblyInfo = assemblyInfo;
      this.measureMin = slist != null ? slist.getMeasureMin() : 0;
      this.measureMax = slist != null ? slist.getMeasureMax() : 0;
      int cnt = slist == null ? 0 : slist.getSelectionValueCount();
      int maxItems = (limit > 0) ? Math.min(limit, cnt) : cnt;
      values = new SelectionValueModel[maxItems];
      Font font = null;
      boolean wrapping = false;

      for(int i = 0; i < values.length; i++) {
         SelectionValue value = slist.getSelectionValue(i);

         // optimization, avoid large number of calls to getFont/isWrapping. this is
         // safe since all values on the same selection list share the same format.
         if(i == 0) {
            if(value.getFormat() != null) {
               font = value.getFormat().getFont();
               wrapping = value.getFormat().isWrapping();
            }
         }

         if(value instanceof CompositeSelectionValue) {
            values[i] = new CompositeSelectionValueModel(
               (CompositeSelectionValue) value, this, assemblyInfo, font, wrapping);
         }
         else {
            values[i] = new SelectionValueModel(
               value, this, (SelectionBaseVSAssemblyInfo) assemblyInfo, font, wrapping);
         }
      }
   }

   public SelectionValueModel[] getSelectionValues() {
      return values;
   }

   // Get the format index to format model map
   public Map<Integer, VSFormatModel> getFormats() {
      Map<Integer, VSFormatModel> map = new HashMap<>();
      // the detail-cell default foreground is a fixed 0x2b2b2b, set in setDefaultFormat as an
      // unconditional creation default, so seedChromeDefaults never sees it and a dark-marked
      // assembly draws near-black cell text on its dark surface. Substituted on a clone here, so
      // nothing is persisted and a user or format.css colour still wins. Covers the tree as well:
      // CompositeSelectionValueModel builds a nested SelectionListModel per level, and both
      // controllers resolve a cell through this map.
      VizContext ctx = VizContext.of(assemblyInfo);

      for(Map.Entry<VSCompositeFormat, Integer> entry : formats.entrySet()) {
         VSFormatModel formatModel = new VSFormatModel(
            VSObjectChromeDefaults.applyDarkForeground(entry.getKey(), ctx), assemblyInfo);

         if(assemblyInfo instanceof SelectionBaseVSAssemblyInfo) {
            formatModel.setPadding(((SelectionBaseVSAssemblyInfo) assemblyInfo).getCellPadding());
         }

         map.put(entry.getValue(), formatModel);
      }

      return map;
   }

   public double getMeasureMin() {
      return measureMin;
   }

   public double getMeasureMax() {
      return measureMax;
   }

   // Add format to an unique map and return the index in the map
   @JsonIgnore
   int getFormatIndex(VSCompositeFormat format) {
      if(format == null) {
         return -1;
      }
      // optimization
      else if(format == lastFormat) {
         return lastIndex;
      }

      Integer idx = formats.get(format);

      if(idx == null) {
         formats.put(format, idx = formats.size());
         lastIndex = idx;
         lastFormat = format;
      }

      return idx;
   }

   @Override
   public String toString() {
      return
         "{values: " + Arrays.toString(values) + " "+
         "formats:" + formats +"} ";
   }

   private VSAssemblyInfo assemblyInfo;
   private SelectionValueModel[] values;
   private Map<VSCompositeFormat, Integer> formats = new Object2IntOpenHashMap<>();
   private double measureMin;
   private double measureMax;

   private VSCompositeFormat lastFormat;
   private int lastIndex;
}
