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
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.ListInputVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.ListInputVSAssemblyInfo;
import inetsoft.util.Catalog;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.*;

/**
 * Base class for combobox/checkbox/radiobutton.
 */
public abstract class ListInputModel<T extends ListInputVSAssembly> extends VSInputModel<T> {
   protected ListInputModel(T assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      ListInputVSAssemblyInfo assemblyInfo = (ListInputVSAssemblyInfo) assembly.getVSAssemblyInfo();
      this.assemblyInfo = assemblyInfo;
      Catalog catalog = Catalog.getCatalog(rvs.getUser(), Catalog.REPORT);
      labels = Arrays.stream(assemblyInfo.getLabels())
         .map(l -> catalog.getString(l))
         .toArray(String[]::new);
      values = assemblyInfo.getValues();
      this.formatIndexes = new int[values.length];
      VSCompositeFormat[] formats = assemblyInfo.getFormats();

      if(formats != null) {
         for(int i = 0; i < formats.length; i++) {
            formatIndexes[i] = getFormatIndex(formats[i]);
         }
      }
   }

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

   public String[] getLabels() {
      return labels;
   }

   public Object[] getValues() {
      return values;
   }

   public int[] getFormatIndexes() {
      return formatIndexes;
   }

   // Get the format index to format model map
   public Map<Integer, VSFormatModel> getFormats() {
      Map<Integer, VSFormatModel> map = new HashMap<>();

      for(Map.Entry<VSCompositeFormat, Integer> entry : formats.entrySet()) {
         VSFormatModel formatModel = new VSFormatModel(entry.getKey(), assemblyInfo);
         map.put(entry.getValue(), formatModel);
      }

      return map;
   }

   private String[] labels;
   private Object[] values;
   private Map<VSCompositeFormat, Integer> formats = new Object2IntOpenHashMap<>();
   private ListInputVSAssemblyInfo assemblyInfo;
   private int[] formatIndexes;
   private VSCompositeFormat lastFormat;
   private int lastIndex;
}
