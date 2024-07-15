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
package inetsoft.web.viewsheet.event.chart;

/**
 * Class that encapsulates the parameters for sorting a chart axis.
 *
 * @since 12.3
 */
public class VSChartSortAxisEvent extends VSChartEvent {
   /**
    * Sets the Sort Operation for axis sorting.
    *
    * @param sortOp the Sort Operation.
    */
   public void setSortOp(String sortOp) {
      this.sortOp = sortOp;
   }

   /**
    * Gets the Sort Operation for axis sorting.
    *
    * @return the Sort Operation.
    */
   public String getSortOp() {
      return sortOp;
   }

   /**
    * Sets the Sort Field for axis sorting.
    *
    * @param sortField the Sort Field.
    */
   public void setSortField(String sortField) {
      this.sortField = sortField;
   }

   /**
    * Gets the Sort Field for axis sorting.
    *
    * @return the Sort Field.
    */
   public String getSortField() {
      return sortField;
   }

   private String sortOp;
   private String sortField;
}
