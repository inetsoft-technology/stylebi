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
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.ArrayList;
import java.util.List;

/**
 * This array provides access to individual legend descriptors.
 */
public class TextFormatArray extends ScriptableObject implements ArrayObject {
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
   public boolean has(String id, Scriptable start) {
      init();
      return ids.contains(id);
   }

   @Override
   public Object get(String id, Scriptable start) {
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
      
      return super.get(id, start);
   }
   
   @Override
   public Object[] getIds() {
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

   @Override
   public String getClassName() {
      return "TextFormat";
   }

   private ChartInfo info;
   private PlotDescriptor plot;
   private List ids;
}

