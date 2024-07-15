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

import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.script.ArrayObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.*;

/**
 * This array provides access to individual legend descriptors.
 */
public class LegendArray extends ScriptableObject implements ArrayObject {
   /**
    * @param type aesthetic type defined in ChartArea.
    */
   public LegendArray(ChartInfo info, LegendsDescriptor desc, String type) {
      this.info = info;
      this.desc = desc;
      this.type = type;
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
      return LegendScriptable.class;
   }

   @Override
   public boolean has(String id, Scriptable start) {
      init();
      return ids.contains(id);
   }

   @Override
   public Object get(String id, Scriptable start) {
      if(agg2desc.containsKey(id)) {
         return agg2desc.get(id);
      }

      LegendDescriptor legend = GraphUtil.getLegendDescriptor(info, desc, "", id, type);

      // if no legend descriptor per aggr, return the global one
      if(legend == null) {
         legend = GraphUtil.getLegendDescriptor(info, desc, null, (String) null, type);
      }

      if(legend != null) {
         LegendScriptable scriptable = new LegendScriptable(legend);
         agg2desc.put(id, scriptable);
         return scriptable;
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
      return "Legend";
   }

   private ChartInfo info;
   private LegendsDescriptor desc;
   private String type;
   private List ids;
   private Map<String, LegendScriptable> agg2desc = new HashMap<>();
}
