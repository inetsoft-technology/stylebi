/*
 * inetsoft-web - StyleBI is a business intelligence web application.
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
export class ClauseOperationSymbols {
   static get EXISTS(): string {
      return "EXISTS";
   }

   static get IS_NULL(): string {
      return "IS NULL";
   }

   static get UNIQUE(): string {
      return "UNIQUE";
   }

   static get EQUAL_TO(): string {
      return "=";
   }

   static get GREATER_THAN(): string {
      return ">";
   }

   static get LESS_THAN(): string {
      return "<";
   }

   static get GREATER_THAN_OR_EQUAL_TO(): string {
      return ">=";
   }

   static get LESS_THAN_OR_EQUAL_TO(): string {
      return "<=";
   }

   static get NOT_EQUAL_TO(): string {
      return "<>";
   }

   static get LIKE(): string {
      return "LIKE";
   }

   static get MATCH(): string {
      return "MATCH";
   }

   static get IN(): string {
      return "IN";
   }

   static get BETWEEN(): string {
      return "BETWEEN";
   }
}