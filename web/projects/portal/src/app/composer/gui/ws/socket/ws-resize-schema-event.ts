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
export class WSResizeSchemaTableEvent {
   private joinTableName: string;
   private schemaTableName: string;
   private width: number;
   private offsetLocation: boolean;

   public setJoinTableName(joinTableName: string) {
      this.joinTableName = joinTableName;
   }

   public setSchemaTableName(schemaTableName: string) {
      this.schemaTableName = schemaTableName;
   }

   public setWidth(width: number) {
      this.width = width;
   }

   public setOffsetLocation(offsetLocation: boolean) {
      this.offsetLocation = offsetLocation;
   }
}
