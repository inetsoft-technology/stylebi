/*
 * inetsoft-web - StyleBI is a business intelligence web application.
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
export enum ConditionOperation {
   NONE = 0,
   EQUAL_TO = 1,
   ONE_OF = 2,
   LESS_THAN = 3,
   GREATER_THAN = 4,
   BETWEEN = 5,
   STARTING_WITH = 6,
   CONTAINS = 7,
   NULL = 8,
   TOP_N = 9,
   BOTTOM_N = 10,
   DATE_IN = 11,
   LIKE = 13
}
