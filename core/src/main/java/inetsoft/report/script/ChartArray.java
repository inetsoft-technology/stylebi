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
package inetsoft.report.script;

import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;
import org.mozilla.javascript.ScriptRuntime;

/**
 * This represents an array of chart styles, axises, or aesthetic frames in
 * a chart info.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public abstract class ChartArray extends AbstractChartArray {
   /**
    * Constructor.
    * @param property property name, e.g. Object, VisualFrame
    * @param property type, e.g. Object.class, VisualFrame.class.
    */
   public ChartArray(String property, Class pType) {
      super(property, pType);
   }

   /**
    * Initialize method. This needs to be delayed otherwise the chart may
    * be null in the constructor.
    */
   @Override
   protected void init() {
      if(inited) {
         return;
      }

      ChartRef[] refs = new ChartRef[0];
      ChartInfo info = getInfo();

      if("Axis".equals(property)) {
         if(info instanceof CandleChartInfo || info instanceof StockChartInfo) {
            refs = info.getBindingRefs(true);
         }
         else {
            ChartRef[] xfields = info.getXFields();
            ChartRef[] yfields = info.getYFields();

            refs = (ChartRef[]) Tool.mergeArray(xfields, yfields);
         }
      }
      else if(property.indexOf("Frame") != -1) {
         refs = info.getModelRefs(false);
      }

      if(refs != null) {
         if(info instanceof RadarChartInfo) {
           ids = new String[refs.length + 1];
           ids[ids.length - 1] = "Parallel_Label";
         }
         else {
            ids = new String[refs.length];
         }

         for(int i = 0; i < refs.length; i++) {
            ids[i] = refs[i].getFullName();
         }
      }

      super.init();
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "ChartArray";
   }

   /**
    * Get the default value of the object with a given hint.
    */
   @Override
   public Object getDefaultValue(Class hint) {
      if(hint == ScriptRuntime.BooleanClass) {
         return Boolean.TRUE;
      }
      else if(hint == ScriptRuntime.NumberClass) {
         return ScriptRuntime.NaNobj;
      }

      return this;
   }

   /**
    * Get display suffix.
    */
   @Override
   public String getDisplaySuffix() {
      return "[field]";
   }
}
