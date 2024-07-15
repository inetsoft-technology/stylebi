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
import { ViewsheetEvent } from "../../common/viewsheet-client/index";
import { CalculateRef } from "../data/calculate-ref";
import { VsWizardEditModes } from "../../vs-wizard/model/vs-wizard-edit-modes";

/**
 * Event for common parameters for composer object events.
 */
export class ModifyCalculateFieldEvent implements ViewsheetEvent {
   /**
    * Creates a new instance of <tt>ChangeChartTypeEvent</tt>.
    *
    * @param name the name of the chart.
    * @param calculateRef the calculate ref.
    * @param tableName the table name.
    * @param remove <tt>true</tt> if remove calculate field, <tt>false</tt> otherwise.
    * @param create <tt>true</tt> if create calculate field, <tt>false</tt> otherwise.
    * @param refName the ref name.
    * @param dimType the dimension type.
    * @param checkTrap <tt>true</tt> if check trap field, <tt>false</tt> otherwise.
    */
   constructor(public name: string, public calculateRef: CalculateRef,
      public tableName: string, public remove: boolean, public create: boolean,
      public refName: string, public dimType: string, public checkTrap: boolean,
      public confirmed: boolean, public wizard: boolean,
      public wizardOriginalMode?: VsWizardEditModes)
   {
   }
}
