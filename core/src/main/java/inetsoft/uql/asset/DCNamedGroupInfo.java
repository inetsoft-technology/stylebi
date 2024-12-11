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
package inetsoft.uql.asset;

import inetsoft.uql.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.internal.DatePeriod;
import inetsoft.util.Tool;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

public class DCNamedGroupInfo extends SNamedGroupInfo {
  @Override
   public void addGroupName(String name) {
      super.addGroupName(name);
   }

   @Override
   public void setGroupValue(String name, List value) {
      if(value == null || value.size() != 2 || !(value.get(0) instanceof Date)) {
         return;
      }

      super.setGroupValue(name, value);
   }

   /**
    * Write to XML.
    *
    * @param writer the stream
    */
   @Override
   public void writeXML(PrintWriter writer) {
      if(values == null || values.isEmpty()) {
         return;
      }

      writer.println("<namedgroups class=\"" + getClass().getName() + "\" type=\"" +
         getType() + "\" strictNull=\"true\">");
      Set<Map.Entry<String, List>> entrySet = values.entrySet();

      for(Map.Entry<String, List> entry : entrySet) {
         writer.print("<namedGroup><![CDATA[" + entry.getKey() + "]]>");
         List value = entry.getValue();

         if(value != null && value.size() > 0) {
            for(int j = 0; j < value.size(); j++) {
               Object val = value.get(j);

               if(Tool.equals("", val)) {
                  continue;
               }

               writer.print("<value>");
               writer.print("<![CDATA[" + Tool.getPersistentDataString(val) + "]]>");
               writer.print("</value>");
            }
         }

         writer.println("</namedGroup>");
      }

      writer.println("</namedgroups>");
   }

   /**
    * Get the condition list of a group.
    *
    * @param name the group name
    * @return the condition list of the group
    */
   @Override
   public ConditionList getGroupCondition(String name) {
      List value = getGroupValue(name);
      ConditionList conds = new ConditionList();

      if(value == null || value.size() != 2) {
         return conds;
      }

      DataRef ref = getDataRef();
      Condition cond = new Condition(ref.getDataType());
      cond.setOperation(XCondition.GREATER_THAN);
      cond.addValue(value.get(0));
      cond.setEqual(true);
      ConditionItem item = new ConditionItem(ref, cond, 0);
      conds.append(item);
      conds.append(new JunctionOperator(JunctionOperator.AND, 0));
      cond = new Condition(ref.getDataType());
      cond.setOperation(XCondition.LESS_THAN);
      cond.addValue(value.get(1));
      cond.setEqual(true);
      item = new ConditionItem(ref, cond, 0);
      conds.append(item);

      return conds;
   }

   @Override
   public String[] getGroups(boolean sort) {
      String[] groups = super.getGroups(sort);

      if(groups == null) {
         return null;
      }

      List<DatePeriod> periods = Arrays.stream(groups).map(group -> {
         List values = getGroupValue(group);
         return values == null ? null : new DatePeriod((Date) values.get(0), (Date) values.get(1));
      }).collect(Collectors.toList());

      return getCustomPeriodLabels(periods).toArray(new String[0]);
   }

   /**
    * Get the labels for custom periods for display on chart. The labels are abbreviated
    * but can uniquely identify a period.
    */
   public static List<String> getCustomPeriodLabels(List<DatePeriod> periods) {
      List<String> labels = periods.stream()
         .map(p -> p == null ? "" : Tool.formatDate(p.getStart()) + ":" + Tool.formatDate(p.getEnd()))
         .collect(Collectors.toList());
      List<String> years = labels.stream().map(s -> s.substring(0, s.indexOf('-')))
         .collect(Collectors.toList());

      // if year part is distinct, use year.
      if(years.size() == years.stream().distinct().count()) {
         // period across multiple years
         boolean spanYears = periods.stream()
            .anyMatch(p -> DateTimeProcessor.at(p.getStart().getTime()).getYear() !=
               DateTimeProcessor.at(p.getEnd().getTime()).getYear());

         if(spanYears) {
            return periods.stream()
               .map(p -> DateTimeProcessor.at(p.getStart().getTime()).getYear() + ":" +
                  DateTimeProcessor.at(p.getEnd().getTime()).getYear())
               .collect(Collectors.toList());
         }

         return years;
      }

      List<String> months = labels.stream().map(s -> s.substring(0, s.indexOf('-', 5)))
         .collect(Collectors.toList());

      // if month part is distinct, use month.
      if(months.size() == months.stream().distinct().count()) {
         // period across multiple month
         boolean spanMonths = periods.stream()
            .anyMatch(p -> DateTimeProcessor.at(p.getStart().getTime()).getMonthOfYear() !=
               DateTimeProcessor.at(p.getEnd().getTime()).getMonthOfYear());

         if(spanMonths) {
            return labels.stream().map(s -> {
               String[] pair = Tool.split(s, ':');
               return pair[0].substring(0, pair[0].indexOf('-', 5)) + ":" +
                  pair[1].substring(5, pair[1].lastIndexOf('-'));
            })
            .collect(Collectors.toList());
         }

         return months;
      }

      // same year-month, show as year-month-day1:day2
      return labels.stream().map(s -> {
         String[] pair = Tool.split(s, ':');
         return pair[0] + ":" + pair[1].substring(pair[1].lastIndexOf('-') + 1);
      }).collect(Collectors.toList());
   }

   public static String SEPARATOR = ":";
}
