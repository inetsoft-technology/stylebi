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
   Input,
   OnChanges,
   OnInit,
   Output, SimpleChanges, ViewChild,
} from "@angular/core";
import { ScriptModel } from "../../../data/script/script";
import { ModelService } from "../../../../widget/services/model.service";
import { TreeNodeModel } from "../../../../widget/tree/tree-node-model";
import { ScriptTreeNodeData } from "../../../../widget/formula-editor/script-tree-node-data";
import { FormulaEditorDialog } from "../../../../widget/formula-editor/formula-editor-dialog.component";
import { Tool } from "../../../../../../../shared/util/tool";
import { ScriptPane } from "../../../../widget/dialog/script-pane/script-pane.component";
import { HttpClient, HttpParams } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ScriptService } from "../script.service";
import { NotificationsComponent } from "../../../../widget/notifications/notifications.component";
import { ScriptTreePaneModel } from "../../../data/script/script-tree-pane-model";
import { FontService } from "../../../../widget/services/font.service";

/**
 * The worksheet pane of the worksheet composer.
 * <p>Its purpose is to contain the worksheet environment.
 */
@Component({
   selector: "script-edit-pane",
   templateUrl: "script-edit-pane.component.html",
   styleUrls: ["script-edit-pane.component.scss"],
})
export class ScriptEditPaneComponent implements OnInit, OnChanges {
   @Input() model: ScriptModel;
   @Input() scriptTreePaneModel: ScriptTreePaneModel;
   @Input() scriptFontSize: number;
   @Input() viewChecked: boolean = false;
   expression: string;
   private codemirrorInstance: any;
   private _originalText: string;
   cursor: {line: number, ch: number};
   @ViewChild("notifications") notifications: NotificationsComponent;

   constructor(private modelService: ModelService,
               private http: HttpClient,
               private modalService: NgbModal,
               private scriptService: ScriptService,
               private fontService: FontService)
   {
   }

   showNotifications(){
      this.notifications.success("_#(js:common.script.saveSuccess)");
   }

   get originalText(): string {
      return this._originalText;
   }

   @Input()
   set originalText(value: string) {
      this._originalText = value;
   }

   ngOnInit(): void {
      this._originalText = this.model.text;
      this.scriptService.getClickedNode().subscribe(event => {
         this.itemClicked(event.node, event.target);
      });
   }

   getAssemblyName(): string {
      return null;
   }

   ngOnChanges(changes: SimpleChanges): void {
   }

   itemClicked(node: TreeNodeModel, target: string) {
      // make sure focus is on text area
      setTimeout(() => this.codemirrorInstance.focus(), 200);

      this.expressionChange({
         target,
         node,
         expression: this.codemirrorInstance.getValue(),
         selection: {
            from: this.codemirrorInstance.getCursor("from"),
            to: this.codemirrorInstance.getCursor("to")
         }
      });

      this.model.isModified = false;
   }

   public expressionChange(event: any): void {
      this.model.isModified = true;
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

               text = text && text.indexOf(".") >= 0 ?
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
         else if(data.parentLabel == "User Defined") {
            text = event.node.label;
         }
         else {
            text = text + suffix;
         }

         this.expression = ScriptPane.insertText(this.expression, text, event.selection);
         this.cursor = {
            line: event.selection.from.line,
            ch: event.selection.from.ch + text.length
         };
      }

      this.model.text = this.expression;

      if(this.expression == this._originalText) {
         this.model.isModified = false;
      }
   }

   codemirrorInstanceEmitter(codemirrorInstance: any) {
      if(codemirrorInstance) {
         this.codemirrorInstance = codemirrorInstance;
      }
   }

   getScriptStyle() {
      let style = {
         "font-size": this.scriptFontSize + "" + "px",
      };

      if(this.viewChecked && this.codemirrorInstance) {
         this.codemirrorInstance.refresh();
      }

      return style;
   }
}
