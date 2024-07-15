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
import { ViewsheetEvent } from "../../common/viewsheet-client/index";
import { AggregateRef } from "../../common/data/aggregate-ref";

/**
 * Event for common parameters for composer object events.
 */
export class ModifyAggregateFieldEvent implements ViewsheetEvent {
   /**
    * Creates a new instance of <tt>ChangeChartTypeEvent</tt>.
    *
    * @param name the name of the chart.
    * @param tableName the table name.
    * @param newRef the new aggregate ref.
    * @param oldRef the old aggregate ref.
    */
   constructor(public name: string, public tableName: string, public newRef: AggregateRef,
      public oldRef: AggregateRef, public confirmed: boolean)
   {
   }
}
