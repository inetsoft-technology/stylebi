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
import { Component, Input } from "@angular/core";
import { ScriptTreeNodeData } from "../../formula-editor/script-tree-node-data";
import { TreeNodeModel } from "../../tree/tree-node-model";
import { ScriptPaneTreeModel } from "../script-pane/script-pane-tree-model";
import { ScriptPane } from "../script-pane/script-pane.component";
import { VSAssemblyScriptPaneModel } from "./vsassembly-script-pane-model";
import { FormulaEditorDialog } from "../../formula-editor/formula-editor-dialog.component";
import { Tool } from "../../../../../../shared/util/tool";

@Component({
   selector: "vsassembly-script-pane",
   templateUrl: "vsassembly-script-pane.component.html",
})
export class VSAssemblyScriptPane {
   @Input() view: string;
   @Input() model: VSAssemblyScriptPaneModel;
   @Input() scriptTreeModel: ScriptPaneTreeModel;
   cursor: {line: number, ch: number};

   private queryPath(root: TreeNodeModel, label: string, end: string): string {
      if(!root || !label || label.length === 0) {
         return null;
      }

      let endNode: TreeNodeModel = this.findFolderByLabel(root, end);
      endNode = !!endNode ? endNode : root;

      let node: TreeNodeModel = null;
      let path: string = null;
      do {
         node = this.findFolderByLabel(endNode, label);

         if(!!node) {
            path = !!path ? node.label + "." + path : node.label;
            label = !!node.data ? node.data.parentLabel : null;

            if(label === end) {
               break;
            }
         }
      } while (!!node);

      return path;
   }

   private findFolderByLabel(node: TreeNodeModel, label: string): TreeNodeModel {
      if(!!node && !!label && (node.label === label)) {
         return node;
      }

      if(!!node && !!label && !!node.children && node.children.length > 0) {
         let childrens: TreeNodeModel[] = node.children;
         for(let i = 0; i < childrens.length; i++) {
            let target: TreeNodeModel = this.findFolderByLabel(childrens[i], label);

            if(!!target && !target.leaf) {
               return target;
            }
         }
      }

      return null;
   }

   public onExpressionChange(event: any): void {
      this.model.expression = event.expression || "";
      const node: TreeNodeModel = event.node;

      if(node) {
         if(!node.leaf) {
            return;
         }

         const data: ScriptTreeNodeData = node.data;

         if(!data) {
            return;
         }

         let text: string = data.expression;
         let suffix: string = data.suffix || "";

         if(!text || text.length === 0) {
            text = data.data;
         }

         let isDateField: boolean = node.type === FormulaEditorDialog.DATE_PART_COLUMN;
         let datePart: string = null;

         if(isDateField && text.indexOf("(") > 0) {
            let idx = text.indexOf("(");
            datePart = text.substring(0, idx);
            text = text.substring(idx + 1, text.length - 1);
         }

         const quote: string = (text.indexOf("'") >= 0) ? '"' : "'";

         if(event.target === "columnTree") {
            if(data.parentName === "component") {
               let parentLabel = data.parentLabel;

               if(data.parentLabel.indexOf(" ") >= 0) {
                  parentLabel = "viewsheet['" + data.parentLabel + "']";
               }

               text = text && text.indexOf(".") >= 0 && text != "data.size" && text != "data.length" ?
                  `${parentLabel}[${quote}${text}${suffix}${quote}]` :
                  `${parentLabel}.${text}${suffix}`;
            }
            else if(data.parentName === "parameter") {
               text = !Tool.isIdentifier(text) ?
                  `${data.parentData}[${quote}${text}${quote}]` :
                  `${data.parentData}.${text}`;
            }
            else if(data.name === "COLUMN" && (data.parentName === "TABLE" ||
               data.parentName === "PHYSICAL_TABLE" || data.parentName === "LOGIC_MODEL" ||
               isDateField))
            {
               if(!Tool.isIdentifier(data.parentData)) {
                  text = `viewsheet[${quote}${data.parentData}${quote}][${quote}${text}${quote}]`;
               }
               else {
                  text = `${data.parentData}[${quote}${text}${quote}]`;
               }

               if(isDateField) {
                  text = datePart + "(" + text + ")";
               }
            }
            else if(data.name === "field") {
               text = `data[${quote}${text}${quote}]`;
            }
            else if(data.name === "highlighted") {
               text = `${data.parentLabel}.${data.name}[${quote}${text}${quote}]`;
            }
            else if(data.name === "axis"
               || data.name === "valueFormats"
               || data.name === "colorLegends"
               || data.name === "shapeLegends"
               || data.name === "sizeLegends")
            {
               text = `${data.parentLabel}.${data.name}[].${text}`;
            }
            else if(data.name === "colorLegend"
               || data.name === "shapeLegend"
               || data.name === "sizeLegend"
               || data.name === "xTitle"
               || data.name === "x2Title"
               || data.name === "yTitle"
               || data.name === "y2Title"
               || data.name === "xAxis"
               || data.name === "graph"
               || data.name === "yAxis"
               || data.name === "y2Axis"
               || data.name === "bindingInfo"
               || data.name === "layoutInfo")
            {
               text = `${data.parentLabel}.${data.name}.${text}`;
            }

            if(data.name === "component") {
               const dot = text.indexOf(".");

               if(dot >= 0) {
                  let component = text.substring(0, dot);

                  if(component.indexOf(" ") >= 0) {
                     let after = text.substring(dot + 1, text.length);
                     text = `viewsheet[${quote}${component}${quote}].${after}`;
                  }
               }
            }
         }
         else {
            text = text + suffix;
         }

         this.model.expression =
            ScriptPane.insertText(this.model.expression, text, event.selection);
         this.cursor = {
            line: event.selection.from.line,
            ch: event.selection.from.ch + text.length
         };
      }
   }
}
