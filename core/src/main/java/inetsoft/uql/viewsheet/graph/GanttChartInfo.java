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
package inetsoft.uql.viewsheet.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Interface for gantt chart.
 *
 * @version 13.4
 * @author InetSoft Technology Corp
 */
public interface GanttChartInfo extends MergedChartInfo {
   /**
    * Get the start field.
    */
   public ChartRef getStartField();

   /**
    * Get the end field.
    */
   public ChartRef getEndField();

   /**
    * Get the milestone field.
    */
   public ChartRef getMilestoneField();

   /**
    * Get the runtime start field.
    */
   public ChartRef getRTStartField();

   /**
    * Get the runtime end field.
    */
   public ChartRef getRTEndField();

   /**
    * Get the runtime milestone field.
    */
   public ChartRef getRTMilestoneField();

   /**
    * Set the start field.
    */
   public void setStartField(ChartRef ref);

   /**
    * Set the end field.
    */
   public void setEndField(ChartRef ref);

   /**
    * Set the milestone field.
    */
   public void setMilestoneField(ChartRef ref);

   default List<ChartAggregateRef> getGanttFields(boolean runtime) {
      return getGanttFields(runtime, true);
   }

   default List<ChartAggregateRef> getGanttFields(boolean runtime, boolean includeEndField) {
      List<ChartRef> list = new ArrayList<>();

      if(runtime) {
         list.add(getRTStartField());

         if(includeEndField) {
            list.add(getRTEndField());
         }

         list.add(getRTMilestoneField());
      }
      else {
         list.add(getStartField());

         if(includeEndField) {
            list.add(getEndField());
         }

         list.add(getMilestoneField());
      }

      return list.stream().filter(a -> a instanceof ChartAggregateRef)
         .map(a -> (ChartAggregateRef) a)
         .collect(Collectors.toList());
   }

   // set the real chart type of bar and milestone point.
   default void updateChartTypes() {
      if(getStartField() instanceof ChartAggregateRef) {
         ((ChartAggregateRef) getStartField()).setChartType(GraphTypes.CHART_BAR);
         ((ChartAggregateRef) getStartField()).setRTChartType(GraphTypes.CHART_BAR);
      }

      if(getMilestoneField() instanceof ChartAggregateRef) {
         ((ChartAggregateRef) getMilestoneField()).setChartType(GraphTypes.CHART_POINT);
         ((ChartAggregateRef) getMilestoneField()).setRTChartType(GraphTypes.CHART_POINT);
      }
   }
}
