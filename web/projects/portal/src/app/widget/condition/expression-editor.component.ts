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
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnDestroy,
   Output,
   SimpleChanges,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable ,  Subscription } from "rxjs";
import { ExpressionType } from "../../common/data/condition/expression-type";
import { ExpressionValue } from "../../common/data/condition/expression-value";
import { TreeNodeModel } from "../tree/tree-node-model";
import { DataRef } from "../../common/data/data-ref";

@Component({
   selector: "expression-editor",
   templateUrl: "expression-editor.component.html"
})
export class ExpressionEditor implements OnChanges, OnDestroy {
   public ExpressionType = ExpressionType;
   @Input() columnTreeFunction: (value: ExpressionValue) => Observable<TreeNodeModel>;
   @Input() scriptDefinitionsFunction: (value: ExpressionValue) => Observable<any>;
   @Input() expressionTypes: ExpressionType[];
   @Input() value: ExpressionValue;
   @Input() isVSContext: boolean = true;
   @Input() isHighlight: boolean = false;
   @Input() isHyperLink: boolean = false;
   @Input() vsId: string;
   @Input() showOriginalName: boolean = false;
   @Input() columns: DataRef[];
   @Output() valueChange = new EventEmitter<ExpressionValue>();
   @ViewChild("formulaEditorDialog") formulaEditorDialog: TemplateRef<any>;
   expressionType: ExpressionType;
   columnTreeModel: TreeNodeModel;
   columnTreeSub: Subscription;
   scriptDefinitions: any;
   scriptDefinitionsSub: Subscription;

   constructor(private modalService: NgbModal) {
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(this.value == null) {
         this.expressionType = this.expressionType != null ?
            this.expressionType : this.expressionTypes[0];
         this.value = <ExpressionValue> {
            expression: null,
            type: this.expressionType
         };
      }
      else {
         this.expressionType = this.value.type;
      }

      let expressionTypeChanged = false;

      if(changes.hasOwnProperty("value")) {
         const value = changes["value"];
         const oldValue: ExpressionValue = value.previousValue;
         const newValue: ExpressionValue = value.currentValue;

         if(!!newValue && !!oldValue) {
            expressionTypeChanged = oldValue.type !== newValue.type;
         }
      }

      if(changes.hasOwnProperty("columnTreeFunction") || expressionTypeChanged) {
         this.updateColumnTree();
      }
   }

   updateColumnTree() {
      if(this.columnTreeFunction) {
         const obs = this.columnTreeFunction(this.value);

         if(obs) {
            this.closeColumnTreeSubscription();
            this.columnTreeSub = obs.subscribe(t => this.columnTreeModel = t);
         }
         else {
            console.warn("Unimplemented column tree provider");
         }
      }

      if(this.scriptDefinitionsFunction) {
         const obs = this.scriptDefinitionsFunction(this.value);

         if(obs) {
            this.closeScriptDefinitionsSubscription();
            this.scriptDefinitionsSub = obs.subscribe((defs) => this.scriptDefinitions = defs);
         }
      }
   }

   private closeColumnTreeSubscription() {
      if(this.columnTreeSub && !this.columnTreeSub.closed) {
         this.columnTreeSub.unsubscribe();
      }
   }

   private closeScriptDefinitionsSubscription(): void {
      if(this.scriptDefinitionsSub && !this.scriptDefinitionsSub.closed) {
         this.scriptDefinitionsSub.unsubscribe();
         this.scriptDefinitionsSub = null;
      }
   }

   ngOnDestroy() {
      this.closeColumnTreeSubscription();
      this.closeScriptDefinitionsSubscription();
   }

   changeExpressionType(): void {
      this.expressionType = this.expressionType === ExpressionType.SQL ?
         ExpressionType.JS : ExpressionType.SQL;
      this.value = Object.assign({expression: null}, this.value,
                                 {type: this.expressionType});
      this.valueChange.emit(this.value);
   }

   openFormulaWindow(): void {
      this.modalService.open(this.formulaEditorDialog,
                             {size: "lg", backdrop: false, keyboard: false})
         .result.then(
         (result: any) => {
            this.value = <ExpressionValue> {
               expression: result.expression,
               type: result.formulaType
            };

            this.valueChange.emit(this.value);
         }, () => {
         }
      );
   }

   isMV(): boolean {
      if(this.columnTreeModel && this.columnTreeModel.children.length > 0) {
         for(let child of this.columnTreeModel.children) {
            if(child.data === "MV") {
               return true;
            }
         }
      }

      return false;
   }
}
