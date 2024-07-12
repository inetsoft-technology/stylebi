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
import {Component, ElementRef, Inject, Input, OnInit, Renderer2} from "@angular/core";
import { TreeNodeModel } from "../../../../portal/src/app/widget/tree/tree-node-model";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { ScriptTreeNode } from "./script-tree-node";
import { FormulaEditorService } from "./formula-editor.service";
import { ScriptTreeNodeData } from "../../../../portal/src/app/widget/formula-editor/script-tree-node-data";
import { FlatTreeNode } from "../common/util/tree/flat-tree-model";
import { FormulaEditorDialogModel } from "../../../../portal/src/app/widget/formula-editor/formula-editor-dialog-model";
import { ScriptPaneComponent } from "./script-pane.component";
import { BaseResizeableDialogComponent } from "../common/util/base-dialog/resize-dialog/base-resizeable-dialog.component";

@Component({
   selector: "em-formula-editor-dialog",
   templateUrl: "./formula-editor-dialog.component.html",
   styleUrls: ["./formula-editor-dialog.component.scss"]
})
export class FormulaEditorDialogComponent extends BaseResizeableDialogComponent implements OnInit {
   @Input() vsId: string;
   @Input() task: boolean = false;
   @Input() expression: string;
   @Input() submitCallback: (_?: FormulaEditorDialogModel) => Promise<boolean> =
      () => Promise.resolve(true);
   functionTreeRoot: ScriptTreeNode;
   operationTreeRoot: ScriptTreeNode;
   cursor: {line: number, ch: number};
   private _scriptDefinitions: any = null;

   @Input()
   set scriptDefinitions(value: any) {
      this._scriptDefinitions = value;
   }

   get scriptDefinitions(): any {
      return this._scriptDefinitions;
   }

   constructor(private editorService: FormulaEditorService,
               private dialogRef: MatDialogRef<FormulaEditorDialogComponent>,
               protected renderer: Renderer2, protected element: ElementRef,
               @Inject(MAT_DIALOG_DATA) public data: any)
   {
      super(renderer, element);
      this.expression = data.expression;
      this.submitCallback = data.submitCallback;
      this.task = data.task;
   }

   ngOnInit() {
      this.populateTrees();
   }

   expressionChange(obj: any) {
      let fexpress: string = obj.expression;
      let target: string = obj.target;
      let node: FlatTreeNode = <FlatTreeNode> obj.node;
      let scriptData: ScriptTreeNodeData = <ScriptTreeNodeData> obj.data || obj.node;

      if(node){
         if(!(<ScriptTreeNode> node.data).leaf) {
            return;
         }

         fexpress = scriptData.expression;

         if(fexpress == null || fexpress.length == 0) {
            if(typeof scriptData.data == "object") {
               fexpress = <string> scriptData.data.data.data;
            }
            else {
               fexpress = <string> scriptData.data.data;
            }
         }

         if(target) {
            let len = fexpress.length;
            fexpress = ScriptPaneComponent.insertText(this.expression || "", fexpress, obj.selection);

            this.cursor = {
               line: obj.selection.from.line,
               ch: obj.selection.from.ch + len
            };
         }
      }

      this.expression = fexpress;
   }

   populateTrees() {
      this.populateFunctionTree();
      this.populateOperationTree();
      this.populateScriptDefinitions();
   }

   private populateScriptDefinitions(): void {
      if(this.task) {
         this.editorService.getTaskScriptDefinitions().subscribe(defs => this.scriptDefinitions = defs);
      }
      else if(this.vsId) {
         this.editorService.getScriptDefinitions(this.vsId)
            .subscribe((defs) => {
               this.scriptDefinitions = defs;
            });
      }
   }

   private populateFunctionTree(): void {
      this.editorService.getFunctionTreeNode(this.vsId, this.task).subscribe((data: TreeNodeModel) => {
         if(data != null) {
            this.functionTreeRoot = this.transformTreeNodeModel(data);
         }
      });
   }

   private populateOperationTree() {
      this.editorService.getOperationTreeNode(this.vsId, this.task).subscribe((data: TreeNodeModel) => {
         if(data != null) {
            this.operationTreeRoot = this.transformTreeNodeModel(data);
         }
      });
   }

   transformTreeNodeModel(data: TreeNodeModel): ScriptTreeNode {
      if(data == null) {
         return null;
      }

      let children: ScriptTreeNode[] = [];
      let model: ScriptTreeNode = new ScriptTreeNode(children, data.label, data.data, data.icon, data.leaf);

      if(data.children && data.children.length > 0) {
         for(let child of data.children) {
            children.push(this.transformTreeNodeModel(child));
         }
      }

      return model;
   }

   get validExpression(): boolean {
      return !!this.expression === true;
   }

   ok() {
      let model: FormulaEditorDialogModel = {
         expression: this.expression,
      };

      this.submitCallback(model).then((valid) => {
         if(valid) {
            this.dialogRef.close(model);
         }
      });
   }
}
