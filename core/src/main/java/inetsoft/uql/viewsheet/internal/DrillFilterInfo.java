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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.util.*;
import org.thymeleaf.util.StringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.sql.Timestamp;
import java.text.Format;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Common class for drill filter related implementations.
 *
 * @version 13.3
 * @author InetSoft Technology Corp
 */
public class DrillFilterInfo implements Cloneable {
   /**
    * Drill Action Condition
    */
   public ConditionList getDrillFilterConditionList(String field) {
      return drillConds.stream().filter(d -> field.equals(d.field)).map(d -> d.conditions)
         .findFirst().orElse(null);
   }

   /**
    * Set Drill Action Condition. This is accumulative. If more than one setDrillFilterConditionList
    * is called on a field, a subsequent call of setDrillFilterConditionList() with null will only
    * remove one drill condition. The later conditions on a field has precendence over conditions
    * set from earlier calls on the same field.
    * @param field the name of the drill down field. For example, if drill from state to city,
    *              the field will be city, and condition will be something like (state = 'NJ').
    * @param drillCondition add a drill condition or remove if condition is null or empty.
    */
   public void setDrillFilterConditionList(String field, ConditionList drillCondition) {
      allConds = null;

      if(drillCondition == null || drillCondition.isEmpty()) {
         for(int i = 0; i < drillConds.size(); i++) {
            if(field.equals(drillConds.get(i).field)) {
               drillConds.remove(i);
               break;
            }
         }
      }
      else {
         drillConds.add(0, new DrillCondition(field, drillCondition));
      }
   }

   public Set<String> getFields() {
      return drillConds.stream().map(d -> d.field).collect(Collectors.toSet());
   }

   /**
    * Get a text description of current drills suitable for using as tooltip.
    */
   public String getDrillDescription() {
      Map<String, Map<String, HashSet<Object>>> nameValues = new LinkedHashMap<>();
      Set<String> added = new HashSet<>();

      for(DrillCondition drill : drillConds) {
         ConditionList conds = drill.conditions;

         if(added.contains(drill.field)) {
            continue;
         }

         added.add(drill.field);
         conds.stream().filter(item -> item instanceof ConditionItem)
            .forEach(a -> {
               ConditionItem item = (ConditionItem) a;
               String dtype = item.getAttribute().getDataType();
               boolean isDate = XSchema.isDateType(dtype);
               String field = item.getAttribute().getAttribute();
               int dateLevel = 0;
               DataRef ref = item.getAttribute();

               if(ref instanceof VSDimensionRef) {
                  ref = ((VSDimensionRef) ref).getDataRef();
               }

               if(ref instanceof ColumnRef) {
                  ref = ((ColumnRef) ref).getDataRef();
               }

               if(ref instanceof DateRangeRef) {
                  DateRangeRef dref = (DateRangeRef) ((ColumnRef) item.getAttribute()).getDataRef();
                  String otype = dref.getOriginalType();
                  dateLevel = dref.getDateOption();

                  // group of time is time. (49956)
                  if(XSchema.TIME.equals(otype)) {
                     dtype = otype;
                  }
               }

               List<Object> values = item.getCondition().getValues();
               List<Object> vals = values.size() == 0 ? new ArrayList() : (List<Object>) Tool.clone(values);

               if(isDate) {
                  for(int i = 0; i < values.size(); i++) {
                     Object v = values.get(i);
                     Format fmt = XUtil.getDefaultDateFormat(dateLevel, dtype);
                     final long DAY = 60000 * 60 * 24;

                     if(v instanceof Timestamp && ((Timestamp) v).getTime() < DAY) {
                        vals.set(i, CoreTool.formatTime((Timestamp) v));
                     }
                     else if(fmt != null) {
                        vals.set(i, fmt.format(v));
                     }
                     else {
                        vals.set(i, CoreTool.toString(v));
                     }
                  }
               }

               Map<String, HashSet<Object>> map = nameValues.computeIfAbsent(field, k -> new HashMap<>());
               XCondition cond = item.getXCondition();

               if(cond.isNegated()) {
                  map.computeIfAbsent("negatedValues", k -> new HashSet<>()).addAll(vals);
               }
               else {
                  map.computeIfAbsent("values", k -> new HashSet<>()).addAll(vals);
               }
            });
      }

      String tip = nameValues.entrySet().stream().map(entry -> {
         String field = entry.getKey();
         Map<String, HashSet<Object>> map = entry.getValue();
         String condValues = null;

         if(map.containsKey("values")) {
            ArrayList values = new ArrayList(map.get("values"));
            condValues = (String) values.stream().filter(a -> a != null)
               .map(a -> a.toString())
               .collect(Collectors.joining(","));
         }

         if(map.containsKey("negatedValues")) {
            ArrayList negatedValues = new ArrayList(map.get("negatedValues"));

            if(!StringUtils.isEmpty(condValues)) {
               condValues += " <i><b>or not</b></i> ";
            }
            else {
               condValues = "<i><b>not</b></i> ";
            }

            condValues += (String) negatedValues.stream().filter(a -> a != null)
               .map(a -> a.toString())
               .collect(Collectors.joining(","));
         }

         if(condValues.length() > 60) {
            condValues = condValues.substring(0, 57) + "...";
         }

         return "<p><b>" + field + "</b>: " + condValues + "</p>";
      }).collect(Collectors.joining(""));

      return tip.isEmpty() ? null :
         "<p><b><u>" + Catalog.getCatalog().getString("Drill Filter") + "</u></b></p><p>" + tip +
         "<p><i>" + Catalog.getCatalog().getString("viewer.viewsheet.drillFilterTip") + "</i><p>";
   }

   /**
    * Get all drill filter conditions merged into one condition list.
    */
   public ConditionList getAllDrillFilterConditions() {
      if(allConds != null) {
         return allConds;
      }

      ConditionList conds = new ConditionList();

      for(String field : getFields()) {
         ConditionList filter = getDrillFilterConditionList(field).clone();

         if(conds.isEmpty()) {
            conds = filter;
         }
         else {
            conds = mergeConditionList(conds, filter);
         }
      }

      return allConds = conds;
   }

   public void writeXML(PrintWriter writer) {
      writer.println("<drill_filters>");

      for(DrillCondition drill : drillConds) {
         ConditionList cond = drill.conditions;

         if(cond != null && !cond.isEmpty()) {
            writer.println("<drill_filter>");
            writer.println("<field><![CDATA[" + drill.field + "]]></field>");
            cond.writeXML(writer);
            writer.println("</drill_filter>");
         }
      }

      writer.println("</drill_filters>");
   }

   public void parseXML(Element node) throws Exception {
      Element filters = Tool.getChildNodeByTagName(node, "drill_filters");
      drillConds.clear();

      if(filters != null) {
         NodeList drills = Tool.getChildNodesByTagName(filters, "drill_filter");

         for(int i = 0; i < drills.getLength(); i++) {
            Element drill = (Element) drills.item(i);
            ConditionList condList = new ConditionList();

            Element conds = Tool.getChildNodeByTagName(drill, "conditions");
            condList.parseXML(conds);
            Element field = Tool.getChildNodeByTagName(drill, "field");

            setDrillFilterConditionList(Tool.getValue(field), condList);
         }
      }
   }

   public DrillFilterInfo clone() {
      try {
         DrillFilterInfo obj = (DrillFilterInfo) super.clone();
         obj.drillConds = Tool.deepCloneSynchronizedList(drillConds, new ArrayList<>());
         obj.allConds = null;
         return obj;
      }
      catch(CloneNotSupportedException e) {
         return this;
      }
   }

   private class DrillCondition {
      String field;
      ConditionList conditions;

      public DrillCondition(String field, ConditionList conditions) {
         this.field = field;
         this.conditions = conditions;
      }
   }

   /**
    * Merge two condition list to single.
    * @param cond1 @{link ConditionList} or <tt>null</tt>
    * @param cond2 @{link ConditionList} or <tt>null</tt>
    */
   private static ConditionList mergeConditionList(ConditionList cond1, ConditionList cond2) {
      List<ConditionList> list = new ArrayList<>();
      list.add(cond1);
      list.add(cond2);

      return ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
   }

   private List<DrillCondition> drillConds = Collections.synchronizedList(new ArrayList<>());
   private transient ConditionList allConds;
}
