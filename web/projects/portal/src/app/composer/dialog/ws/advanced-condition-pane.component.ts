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
import { HttpClient } from "@angular/common/http";
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import { SubqueryTable } from "../../../common/data/condition/subquery-table";
import { DataRef } from "../../../common/data/data-ref";
import { PrePostConditionItemPaneProvider } from "./pre-post-condition-item-pane-provider";
import { RankingConditionItemPaneProvider } from "./ranking-condition-item-pane-provider";

@Component({
   selector: "advanced-condition-pane",
   templateUrl: "advanced-condition-pane.component.html",
})
export class AdvancedConditionPane implements OnInit, OnChanges {
   @Input() runtimeId: string;
   @Input() assemblyName: string;
   @Input() subqueryTables: SubqueryTable[];
   @Input() preAggregateFields: DataRef[];
   @Input() postAggregateFields: DataRef[];
   @Input() preAggregateConditionList: any[];
   @Input() postAggregateConditionList: any[];
   @Input() rankingConditionList: any[];
   @Input() expressionFields: DataRef[];
   @Input() variableNames: string[];
   @Output() preAggregateConditionListChange: EventEmitter<any[]> = new EventEmitter<any[]>();
   @Output() postAggregateConditionListChange: EventEmitter<any[]> = new EventEmitter<any[]>();
   @Output() rankingConditionListChange: EventEmitter<any[]> = new EventEmitter<any[]>();
   rankingFields: DataRef[];
   prePostProvider: PrePostConditionItemPaneProvider;
   rankingProvider: RankingConditionItemPaneProvider;
   private _grayedOutFields: DataRef[];

   @Input()
   set grayedOutFields(grayedOutFields: DataRef[]) {
      this._grayedOutFields = grayedOutFields;

      if(this.prePostProvider) {
         this.prePostProvider.grayedOutFields = grayedOutFields;
      }

      if(this.rankingProvider) {
         this.rankingProvider.grayedOutFields = grayedOutFields;
      }
   }

   constructor(private http: HttpClient) {
   }

   ngOnChanges(changes: SimpleChanges) {
      if((changes.hasOwnProperty("preAggregateFields") ||
          changes.hasOwnProperty("postAggregateFields")) &&
         this.preAggregateFields && this.postAggregateFields)
      {
         const fields = this.postAggregateFields.concat(this.preAggregateFields);
         const names = [];
         this.rankingFields = [];

         fields.forEach(field => {
            if(!names.includes(field.view)) {
               this.rankingFields.push(field);
               names.push(field.view);
            }
         });
      }
   }

   ngOnInit(): void {
      this.prePostProvider = new PrePostConditionItemPaneProvider(
         this.http, this.runtimeId, this.assemblyName);
      this.prePostProvider.fields = this.expressionFields;
      this.prePostProvider.variableNames = this.variableNames;
      this.prePostProvider.grayedOutFields = this._grayedOutFields;

      this.rankingProvider = new RankingConditionItemPaneProvider(
         this.http, this.runtimeId, this.assemblyName);
      this.rankingProvider.fields = this.expressionFields;
      this.rankingProvider.variableNames = this.variableNames;
      this.rankingProvider.grayedOutFields = this._grayedOutFields;
   }
}
