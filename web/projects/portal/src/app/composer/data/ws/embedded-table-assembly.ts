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
import { AbstractTableAssembly } from "./abstract-table-assembly";
import { WSTableAssembly } from "./ws-table-assembly";

export class EmbeddedTableAssembly extends AbstractTableAssembly {
   constructor(table: WSTableAssembly) {
      super(table);
      this.tableButtons.push("import-data-file");
   }

   public isWSEmbeddedTable(): boolean {
      const aggregate = this.info.aggregate && this.info.hasAggregate;
      return !aggregate;
   }

   public isEmbeddedTable(): boolean {
      return true;
   }

   protected isEditable(): boolean {
      return true;
   }
}
