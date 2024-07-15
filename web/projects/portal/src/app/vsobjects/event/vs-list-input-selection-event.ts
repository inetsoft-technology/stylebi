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
import { ViewsheetEvent } from "../../common/viewsheet-client/viewsheet-event";

/**
 * Class that encapsulates an event sent to the server to instruct it to update the
 * selection in a list input assembly
 */
export class VSListInputSelectionEvent implements ViewsheetEvent {
   /**
    * The name of the assembly.
    */
   public assemblyName: string;

   /**
    * The selected object.
    */
   public value: any | any[];

   /**
    * Creates a new instance of <tt>VSListInputSelectionEvent</tt>.
    *
    * @param assemblyName the name of the assembly.
    * @param value        the updated selection.
    */
   constructor(assemblyName: string, value: any | any[]) {
      this.assemblyName = assemblyName;
      this.value = value;
   }
}
