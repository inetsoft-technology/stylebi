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
import { ViewsheetEvent } from "../viewsheet-client/index";
import { VariableInfo } from "../data/variable-info";

/**
 * Event used to collect filled in parameter values.
 */
export class CollectParametersOverEvent implements ViewsheetEvent {
   /**
    * Updated variables.
    */
   public variables: VariableInfo[];

   public disableAudit: boolean;
   public openVS: boolean;

   /**
    * Creates a new instance of <tt>CollectParametersOverEvent</tt>.
    *
    * @param variables the list of variables.
    */
   constructor(variables: VariableInfo[], disableAudit = false, openVS: boolean = false) {
      this.variables = variables;
      this.disableAudit = disableAudit;
      this.openVS = openVS;
   }
}
