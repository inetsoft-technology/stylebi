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
package inetsoft.web.portal.model.database;

/**
 * Enumeration of the type of logical model hierarchy columns.
 */

/**
 * The LMHierarchyConstants defines some constants of the logical model hierarchy.
 *
 * @version 13.3
 * @author InetSoft Technology Corp
 */
public class LMHierarchyConstants {

   /**
    * the dimension type of logical model hierarchy columns.
    */
   public static final int DIMENSION = 0;

   /**
    * the member type of logical model hierarchy columns.
    */
   public static final int MEMBER = 1;

   /**
    * the measure type of logical model hierarchy columns.
    */
   public static final int MEASURE = 2;

   /**
    * the year level of date cube member.
    */
   public static final String YEAR_LEVEL = "Year";

   /**
    * the quarter level of date cube member.
    */
   public static final String QUARTER_LEVEL = "Quarter";

   /**
    * the month level of date cube member.
    */
   public static final String MONTH_LEVEL = "Month";

   /**
    * the day level of date cube member.
    */
   public static final String DAY_LEVEL = "Day";
}