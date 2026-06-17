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
package inetsoft.report.script;

import inetsoft.report.composition.graph.GraphFormatUtil;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.script.ArrayObject;
import inetsoft.util.script.graal.ScriptArrayScope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This array provides access to individual legend descriptors.
 */
public class TextFormatArray implements ArrayObject, ScriptArrayScope {
   /**
    * @param type aesthetic type defined in ChartArea.
    */
   public TextFormatArray(ChartInfo info, PlotDescriptor plot) {
      this.info = info;
      this.plot = plot;
   }

   private void init() {
      if(ids != null) {
         return;
      }

      ChartRef[][] fields = {info.getXFields(), info.getYFields()};
      ids = new ArrayList();

      for(ChartRef[] arr : fields) {
         for(ChartRef ref : arr) {
            if(GraphUtil.isMeasure(ref)) {
               ids.add(ref.getFullName());
            }
         }
      }
   }

   /**
    * Get array element type.
    */
   @Override
   public Class getType() {
      return TextFormatScriptable.class;
   }

   @Override
   public boolean hasMember(String id) {
      init();
      return ids.contains(id);
   }

   @Override
   public Object getMember(String id) {
      VSDataRef ref = info.getRTFieldByFullName(id);
      CompositeTextFormat fmt = null;

      if(!info.isMultiAesthetic()) {
         fmt = GraphFormatUtil.getTextFormat(info, null, plot);
      }
      else if(ref instanceof ChartAggregateRef) {
         ChartAggregateRef aggr = (ChartAggregateRef) ref;
         fmt = GraphFormatUtil.getTextFormat(aggr, aggr, plot);
      }

      if(fmt != null) {
         return new TextFormatScriptable(fmt);
      }

      return members.get(id);
   }

   @Override
   public void putMember(String id, Object value) {
      members.put(id, value);
   }

   @Override
   public Object getArrayElement(long index) {
      return null;
   }

   @Override
   public long getArraySize() {
      return 0;
   }

   @Override
   public Object[] getMemberKeys() {
      init();
      return ids.toArray();
   }

   /**
    * Get display suffix.
    */
   @Override
   public String getDisplaySuffix() {
      return "[index]";
   }

   /**
    * Get suffix.
    */
   @Override
   public String getSuffix() {
      return "[]";
   }

   public String getClassName() {
      return "TextFormat";
   }

   private ChartInfo info;
   private PlotDescriptor plot;
   private List ids;
   private final Map<String, Object> members = new LinkedHashMap<>();
}

