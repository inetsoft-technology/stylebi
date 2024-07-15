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
import { Component, Input, OnInit } from "@angular/core";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { ScriptTreeNodeData } from "../../../widget/formula-editor/script-tree-node-data";
import { ScriptPaneTreeModel } from "../../../widget/dialog/script-pane/script-pane-tree-model";
import { ClickableScriptPaneModel } from "../../data/vs/clickable-script-pane-model";
import { ScriptPane } from "../../../widget/dialog/script-pane/script-pane.component";
import { UIContextService } from "../../../common/services/ui-context.service";
import { FormulaEditorDialog } from "../../../widget/formula-editor/formula-editor-dialog.component";
import { Tool } from "../../../../../../shared/util/tool";

@Component({
   selector: "clickable-script-pane",
   templateUrl: "clickable-script-pane.component.html",
   styleUrls: ["clickable-script-pane.component.scss"]
})
export class ClickableScriptPane implements OnInit{
   columnTree: TreeNodeModel;
   @Input() model: ClickableScriptPaneModel;
   @Input() scriptTreeModel: ScriptPaneTreeModel;
   @Input() preventEscape = false;
   @Input() enableEnter = false;
   cursor: { line: number, ch: number };
   onClick: boolean = false;
   expression: string = "";

   constructor(private uiContextService: UIContextService) {
      this.onClick = uiContextService.getDefaultTab("vs.onClick", "false") == "true";
   }

   ngOnInit(): void {
      if(this.model.onClickExpression != null &&
         this.model.onClickExpression.trim().length > 0)
      {
         this.onClick = true;
      }

      this.expression = this.onClick ? this.model.onClickExpression :
         this.model.scriptExpression;
   }

   get scriptLabel() {
      return this.enableEnter ? "_#(js:onEnter)" : "_#(js:onClick)";
   }

   toggleTextarea(): void {
      this.onClick = !this.onClick;
      this.uiContextService.setDefaultTab("vs.onClick", this.onClick + "");

      if(this.onClick) {
         this.expression = this.model.onClickExpression;
      }
      else {
         this.expression = this.model.scriptExpression;
      }
   }

   public onExpressionChange(event: any): void {
      this.expression = event.expression || "";
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

               if(text && text.indexOf(".") >= 0) {
                  text = `${parentLabel}[${quote}${text}${suffix}${quote}]`;
               }
               else {
                  text = `${parentLabel}.${text}${suffix}`;
               }
            }
            else if(data.parentName === "parameter") {
               if(!Tool.isIdentifier(text)) {
                  text = `${data.parentData}[${quote}${text}${quote}]`;
               }
               else {
                  text = `${data.parentData}.${text}`;
               }
            }
            else if(data.name === "COLUMN" && (data.parentName === "TABLE" ||
               data.parentName === "PHYSICAL_TABLE" || isDateField))
            {
               if(data.parentName === "TABLE" && data.name === "COLUMN" && data.parentData.indexOf(" ") >= 0) {
                  text = `viewsheet[${quote}${data.parentData}${quote}][${quote}${text}${quote}]`;
               }
               else if(!Tool.isIdentifier(data.parentData)) {
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
               text = "data[${quote}${sdata}${quote}]";
            }
            else if(data.name === "bindingInfo") {
               text = `${data.parentLabel}.bindingInfo.${text}`;
            }
            else if(data.name === "layoutInfo") {
               text = `${data.parentLabel}.layoutInfo.${text}`;
            }
            else if(data.name === "yAxis") {
               text = `${data.parentLabel}.yAxis.${text}`;
            }
            else if(data.name === "yA2xis") {
               text = `${data.parentLabel}.y2Axis.${text}`;
            }
            else if(data.name === "xAxis") {
               text = `${data.parentLabel}.xAxis.${text}`;
            }
            else if(data.name === "axis") {
               text = `${data.parentLabel}.axis[].${text}`;
            }
            else if(data.name === "valueFormats") {
               text = `${data.parentLabel}.valueFormats[].${text}`;
            }
            else if(data.name === "colorLegends") {
               text = `${data.parentLabel}.colorLegends[].${text}`;
            }
            else if(data.name === "shapeLegends") {
               text = `${data.parentLabel}.shapeLegends[].${text}`;
            }
            else if(data.name === "sizeLegends") {
               text = `${data.parentLabel}.sizeLegends[].${text}`;
            }
            else if(data.name === "colorLegend") {
               text = `${data.parentLabel}.colorLegend.${text}`;
            }
            else if(data.name === "shapeLegend") {
               text = `${data.parentLabel}.shapeLegend.${text}`;
            }
            else if(data.name === "sizeLegend") {
               text = `${data.parentLabel}.sizeLegend.${text}`;
            }
            else if(data.name === "xTitle") {
               text = `${data.parentLabel}.xTitle.${text}`;
            }
            else if(data.name === "x2Title") {
               text = `${data.parentLabel}.x2Title.${text}`;
            }
            else if(data.name === "yTitle") {
               text = `${data.parentLabel}.yTitle.${text}`;
            }
            else if(data.name === "y2Title") {
               text = `${data.parentLabel}.y2Title.${text}`;
            }
            else if(data.name === "highlighted") {
               text = `${data.parentLabel}.highlighted[${quote}${text}${quote}]`;
            }
            else if(data.name === "graph") {
               text = `${data.parentLabel}.graph.${text}`;
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

         this.expression =
            ScriptPane.insertText(this.expression, text, event.selection);
         this.cursor = {
            line: event.selection.from.line,
            ch: event.selection.from.ch + text.length
         };
      }

      if(this.onClick) {
         this.model.onClickExpression = this.expression;
      }
      else {
         this.model.scriptExpression = this.expression;
      }
   }
}
