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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { Condition } from "../../common/data/condition/condition";
import { ConditionItemPaneProvider } from "../../common/data/condition/condition-item-pane-provider";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { JunctionOperator } from "../../common/data/condition/junction-operator";
import { JunctionOperatorType } from "../../common/data/condition/junction-operator-type";
import { SubqueryTable } from "../../common/data/condition/subquery-table";
import { DataRef } from "../../common/data/data-ref";
import { Tool } from "../../../../../shared/util/tool";
import { ConditionItemPane } from "./condition-item-pane.component";
import { ConditionOperationPipe } from "./condition-operation.pipe";
import { ConditionValuePipe } from "./condition-value.pipe";

export interface ClauseRow {
   conjLabel: string;
   field: DataRef;
   negated: boolean;
   isEqualOp: boolean;
   operation: ConditionOperation;
   valueLabel: string;
}

@Component({
    selector: "simple-condition-pane",
    templateUrl: "simple-condition-pane.component.html",
    styleUrls: ["simple-condition-pane.component.scss"],
    imports: [FormsModule, ConditionItemPane, ConditionOperationPipe]
})
export class SimpleConditionPane implements OnInit {
   public ConditionOperation = ConditionOperation;

   @Input() subqueryTables: SubqueryTable[];
   @Input() fields: DataRef[];
   @Input() provider: ConditionItemPaneProvider;
   @Input() isVSContext = true;
   @Input() variableNames: string[];
   @Output() conditionListChange = new EventEmitter<any[]>();

   draftConj: "and" | "or" = "and";
   draftCondition: Condition = null;

   private _conditionList: any[] = [];
   private _clauseRowsCache: ClauseRow[] | null = null;
   private readonly valPipe = new ConditionValuePipe();

   @Input()
   set conditionList(list: any[]) {
      this._conditionList = Tool.clone(list) ?? [];
      this._clauseRowsCache = null;
   }

   get conditionList(): any[] {
      return this._conditionList;
   }

   get clauseRows(): ClauseRow[] {
      if(!this._clauseRowsCache) {
         this._clauseRowsCache = this._buildClauseRows();
      }

      return this._clauseRowsCache;
   }

   private _buildClauseRows(): ClauseRow[] {
      return this._conditionList
         .filter((_, i) => i % 2 === 0)
         .map((cond: Condition, ci: number) => {
            const junction: JunctionOperator = ci > 0 ? this._conditionList[ci * 2 - 1] : null;
            const conjLabel = ci === 0 ? "WHERE"
               : junction?.type === JunctionOperatorType.OR ? "OR" : "AND";

            const isEqualOp = cond.operation === ConditionOperation.EQUAL_TO
               || cond.operation === ConditionOperation.NONE;

            const allLabels = (cond.values ?? [])
               .map(v => this.valPipe.transform(v))
               .filter(s => s !== "");
            const isNumeric = (s: string) => s !== "" && !isNaN(Number(s));
            const valueLabel = allLabels.length === 0 ? ""
               : allLabels.length === 1
                  ? (isNumeric(allLabels[0]) ? allLabels[0] : `"${allLabels[0]}"`)
                  : `(${allLabels.slice(0, 3).map(s => isNumeric(s) ? s : `"${s}"`).join(", ")}${allLabels.length > 3 ? "…" : ""})`;

            return {
               conjLabel,
               field: cond.field,
               negated: cond.negated,
               isEqualOp,
               operation: cond.operation,
               valueLabel
            };
         });
   }

   ngOnInit(): void {
      if(!this._conditionList) {
         this._conditionList = [];
      }

      this._initDraft();
   }

   addClause(): void {
      if(!this.draftCondition) {
         return;
      }

      const list = [...this._conditionList];

      if(list.length > 0) {
         list.push(<JunctionOperator>{
            jsonType: "junction",
            type: this.draftConj === "or" ? JunctionOperatorType.OR : JunctionOperatorType.AND,
            level: 0
         });
      }

      list.push(Tool.clone(this.draftCondition));
      this._conditionList = list;
      this._clauseRowsCache = null;
      this._initDraft();
      this.conditionListChange.emit(list);
   }

   removeClause(clauseIndex: number): void {
      const list = [...this._conditionList];
      const pos = clauseIndex * 2;

      if(pos === 0 && list.length > 1) {
         list.splice(0, 2);
      }
      else if(pos > 0) {
         list.splice(pos - 1, 2);
      }
      else {
         list.splice(0, 1);
      }

      this._conditionList = list;
      this._clauseRowsCache = null;
      this.conditionListChange.emit(list);
   }

   private _initDraft(): void {
      const firstField = this.fields?.[0] ?? null;
      const probe = <Condition>{
         jsonType: "condition",
         field: firstField,
         operation: ConditionOperation.EQUAL_TO,
         values: [],
         level: 0,
         equal: false,
         negated: false
      };
      const ops = this.provider?.getConditionOperations(probe) ?? [ConditionOperation.EQUAL_TO];
      this.draftCondition = { ...probe, operation: ops[0] ?? ConditionOperation.EQUAL_TO };
   }
}
