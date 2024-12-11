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
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Tool } from "../../../../../shared/util/tool";
import { ComponentTool } from "../../common/util/component-tool";
import { FileFormatPaneModel } from "../model/file-format-pane-model";
import { FileFormatType } from "../model/file-format-type";
import { UntypedFormGroup } from "@angular/forms";
import { FeatureFlagValue } from "../../../../../shared/feature-flags/feature-flags.service";

@Component({
   selector: "file-format-pane",
   templateUrl: "file-format-pane.component.html",
   styleUrls: ["file-format-pane.component.scss"]
})
export class FileFormatPane implements OnInit {
   FeatureFlag = FeatureFlagValue;
   @Input() model: FileFormatPaneModel;
   @Input() exportTypes: {label: string, value: string}[] = [];
   @Input() email: boolean = false;
   @Input() parentForm: UntypedFormGroup;
   @Input() supportCSVTableSelect: boolean = false;
   FileFormatType = FileFormatType;
   types: any[];

   constructor(private modalService: NgbModal) {
   }

   ngOnInit() {
      this.types = [];
      let found = false;

      for(let i = 0; i < this.exportTypes.length; i++) {
         const etype = this.exportTypes[i].value;
         const label = this.exportTypes[i].label;
         this.types.push({ label: this.email && etype == "PNG" ? "_#(js:Embedded PNG)" : label,
            value: this.getExport(etype) });
         found = found || this.model.formatType == this.getExport(etype);
      }

      if(!found && this.types != null && this.types.length > 0) {
         this.model.formatType = this.types[0].value;
      }
   }

   changeFormatType(type: number) {
      this.model.formatType = type;

      if(type == FileFormatType.EXPORT_TYPE_HTML) {
         this.model.includeCurrent = true;
         this.model.selectedBookmarks = [];
      }

      if(this.model && type == FileFormatType.EXPORT_TYPE_CSV &&
         Tool.isEmpty(this.model.tableDataAssemblies))
      {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:common.repletAction.exportFailed.cvs)");
      }
   }

   updateOnlyDataComponents(matchLayout: boolean) {
      if(matchLayout) {
         this.model.onlyDataComponents = false;
      }
   }

   getExport(type: string) {
      if(type.toLowerCase() == "excel") {
         return FileFormatType.EXPORT_TYPE_EXCEL;
      }
      else if(type.toLowerCase() == "powerpoint") {
         return FileFormatType.EXPORT_TYPE_POWERPOINT;
      }
      else if(type.toLowerCase() == "pdf") {
         return FileFormatType.EXPORT_TYPE_PDF;
      }
      else if(type.toLowerCase() == "snapshot") {
         return FileFormatType.EXPORT_TYPE_SNAPSHOT;
      }
      else if(type.toLowerCase() == "png") {
         return FileFormatType.EXPORT_TYPE_PNG;
      }
      else if(type.toLowerCase() == "html") {
         return FileFormatType.EXPORT_TYPE_HTML;
      }
      else if(type.toLowerCase() == "csv") {
         return FileFormatType.EXPORT_TYPE_CSV;
      }

      return FileFormatType.EXPORT_TYPE_EXCEL;
   }

   selectBookmark(bookmark: string, index: number, event: MouseEvent): void {
      if(this.model.formatType == FileFormatType.EXPORT_TYPE_HTML) {
         return;
      }

      let selectedIndex: number = this.model.selectedBookmarks.indexOf(bookmark);

      if(event.shiftKey) {
         let firstIndex: number = 0;

         if(this.model.selectedBookmarks.length > 0) {
            let startIndex: number = this.model.allBookmarks.indexOf(this.model.selectedBookmarks[0]);

            if(startIndex < index) {
               firstIndex = startIndex;
            }
            else {
               firstIndex = index;
               index = startIndex;
            }
         }

         this.model.selectedBookmarks = [];

         for(let i: number = firstIndex; i <= index; i++) {
            this.model.selectedBookmarks.push(this.model.allBookmarks[i]);
         }
      }
      else if(event.ctrlKey || event.metaKey) {
         if(selectedIndex == -1) {
            this.model.selectedBookmarks.push(bookmark);
         }
         else {
            this.model.selectedBookmarks.splice(selectedIndex, 1);
         }
      }
      else {
         this.model.selectedBookmarks = [bookmark];
      }
   }

   selectAll(): void {
      this.model.selectedBookmarks = this.model.allBookmarks.concat([]);
   }

   clearAll(): void {
      this.model.selectedBookmarks = [];
   }

   get matchLayoutVisible(): boolean {
      return this.model.formatType != FileFormatType.EXPORT_TYPE_HTML &&
         this.model.formatType != FileFormatType.EXPORT_TYPE_CSV &&
         (this.model.formatType != FileFormatType.EXPORT_TYPE_PDF || !this.model.hasPrintLayout);
   }
}
