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
import {
   Component,
   Input,
   Output,
   EventEmitter,
   TemplateRef,
   ViewChild,
   OnInit
} from "@angular/core";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { HighlightDialogModel } from "./highlight-dialog-model";
import { HighlightModel } from "./highlight-model";
import { VSConditionDialogModel } from "../../common/data/condition/vs-condition-dialog-model";
import { AddHighlightDialog } from "./add-highlight-dialog.component";
import { FontInfo } from "../../common/data/format-info-model";
import { Tool } from "../../../../../shared/util/tool";
import { ComponentTool } from "../../common/util/component-tool";

@Component({
   selector: "highlight-pane",
   templateUrl: "highlight-pane.component.html"
})
export class HighlightPane implements OnInit {
   @Input() model: HighlightDialogModel;
   @Input() selectedHighlight: HighlightModel;
   @Output() onCommit = new EventEmitter<void>();
   @Output() onEditCondition = new EventEmitter<void>();
   @Output() onSelectHighlight = new EventEmitter<HighlightModel>();
   renameIndex: number = -1;
   private conditionsChanged: boolean = false;

   constructor(private modalService: NgbModal) {
   }

   ngOnInit(): void {
   }

   selectHighlight(highlight: HighlightModel) {
      this.onSelectHighlight.emit(highlight);
   }

   showAddHighlightDialog(rename: boolean = false): void {
      if(rename) {
         this.renameIndex = this.model.highlights.indexOf(this.selectedHighlight);
      }
      else {
         this.renameIndex = -1;
      }

      const dialog = ComponentTool.showDialog(this.modalService, AddHighlightDialog,
         (result: any) => {
            this.conditionsChanged = true;

            if(result.renameIndex == -1) {
               this.model.highlights.push(this.createNewHighlight(result.name));
               this.selectHighlight(
                  this.model.highlights[this.model.highlights.length - 1]);
            }
         }, {backdrop: "static"}
      );

      dialog.renameIndex = this.renameIndex;
      dialog.highlights = this.model.highlights;
   }

   createNewHighlight(name: string): HighlightModel {
      let model: HighlightModel = <HighlightModel> {};
      model.name = name;
      let vsConditionDialogModel: VSConditionDialogModel = <VSConditionDialogModel> {};
      vsConditionDialogModel.tableName = this.model.tableName;
      vsConditionDialogModel.fields = this.model.fields;
      vsConditionDialogModel.conditionList = [];
      model.vsConditionDialogModel = vsConditionDialogModel;
      return model;
   }

   deleteHighlight() {
      let index = this.model.highlights.indexOf(this.selectedHighlight);
      this.model.highlights.splice(index, 1);
      let selectedHighlight = !!this.model.highlights &&
         this.model.highlights.length > 0 ?
         index < this.model.highlights.length ?
            this.model.highlights[index] :
            this.model.highlights[index - 1]
         : null;
      this.selectHighlight(selectedHighlight);
      this.conditionsChanged = true;
   }

   isUpEnable() {
      let index = this.model.highlights.indexOf(this.selectedHighlight);
      let upEnable = true;

      if(index > 0) {
         let selectedHighlight: HighlightModel = this.model.highlights[index];
         let lastHighlight: HighlightModel = this.model.highlights[index - 1];

         if(!lastHighlight.applyRow && selectedHighlight.applyRow) {
            upEnable = false;
         }
      }

      return this.selectedHighlight && this.model.highlights.indexOf(this.selectedHighlight) != 0 && upEnable;
   }

   isDownEnable() {
      let index = this.model.highlights.indexOf(this.selectedHighlight);
      let downEnable = true;

      if(index > 0 && index < this.model.highlights.length - 1) {
         let selectedHighlight: HighlightModel = this.model.highlights[index];
         let nextHighlight: HighlightModel = this.model.highlights[index + 1];

         if(nextHighlight.applyRow && !selectedHighlight.applyRow) {
            downEnable = false;
         }
      }

      return this.selectedHighlight && index != this.model.highlights.length - 1 && downEnable;
   }

   up() {
      let index = this.model.highlights.indexOf(this.selectedHighlight);
      const temp = this.model.highlights[index - 1];
      this.model.highlights[index - 1] = this.model.highlights[index];
      this.model.highlights[index] = temp;
   }

   down() {
      let index = this.model.highlights.indexOf(this.selectedHighlight);
      const temp = this.model.highlights[index + 1];
      this.model.highlights[index + 1] = this.model.highlights[index];
      this.model.highlights[index] = temp;
   }

   get font(): FontInfo {
      if(!this.selectedHighlight) {
         return null;
      }

      if(this.selectedHighlight.fontInfo == null) {
         this.selectedHighlight.fontInfo = new FontInfo();
      }

      return this.selectedHighlight.fontInfo;
   }

   set font(f: FontInfo) {
      if(this.selectedHighlight) {
         this.selectedHighlight.fontInfo = f;
      }
   }

   getFontText(): string {
      if(!this.selectedHighlight || !this.selectedHighlight.fontInfo ||
         !this.selectedHighlight.fontInfo.fontFamily)
      {
         return "";
      }

      const font = this.selectedHighlight.fontInfo;
      let fontStr: string = "";
      fontStr += font.fontFamily;
      fontStr += font.fontSize == null ? "-11" : ("-" + font.fontSize);

      if(font.fontStyle) {
         fontStr += "-" + font.fontStyle;
      }

      if(font.fontWeight) {
         fontStr += "-" + font.fontWeight;
      }

      if(font.fontUnderline && font.fontUnderline.indexOf("underline") >= 0) {
         fontStr += "-underline";
      }

      if(font.fontStrikethrough && font.fontStrikethrough.indexOf("strikethrough") >= 0) {
         fontStr += "-strikethrough";
      }

      return fontStr;
   }

   get foreground(): string {
      return this.selectedHighlight ? this.selectedHighlight.foreground : null;
   }

   set foreground(c: string) {
      if(this.selectedHighlight) {
         this.selectedHighlight.foreground = c;
      }
   }

   get background(): string {
      return this.selectedHighlight ? this.selectedHighlight.background : null;
   }

   set background(c: string) {
      if(this.selectedHighlight) {
         this.selectedHighlight.background = c;
      }
   }

   get applyRow(): boolean {
      return this.selectedHighlight ? this.selectedHighlight.applyRow : false;
   }

   set applyRow(c: boolean) {
      if(this.selectedHighlight) {
         this.selectedHighlight.applyRow = c;
      }
   }
}
