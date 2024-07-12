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
import { SelectionModel } from "@angular/cdk/collections";
import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { MatPaginator, PageEvent } from "@angular/material/paginator";
import { MatSort, Sort } from "@angular/material/sort";
import { MatTableDataSource } from "@angular/material/table";
import { Observable } from "rxjs";
import { Searchable } from "../../../searchable";
import { PresentationSettingsType } from "../presentation-settings-view/presentation-settings-type.enum";
import { PresentationSettingsChanges } from "../presentation-settings-view/presentation-settings-view.component";
import { EditFontMappingDialogData } from "./edit-font-mapping-dialog/edit-font-mapping-dialog-data";
import { EditFontMappingDialogComponent } from "./edit-font-mapping-dialog/edit-font-mapping-dialog.component";
import {
   PresentationFontMappingModel,
   PresentationFontMappingSettingsModel
} from "./presentation-font-mapping-settings-model";
import { ContextHelp } from "../../../context-help";

@Searchable({
   route: "/settings/presentation/settings#font-mapping",
   title: "Font Mapping",
   keywords: ["em.settings", "em.settings.presentation", "em.settings.font"]
})
@ContextHelp({
   route: "/settings/presentation/settings#font-mapping",
   link: "EMPresentationFontMapping"
})
@Component({
   selector: "em-presentation-font-mapping-settings-view",
   templateUrl: "./presentation-font-mapping-settings-view.component.html",
   styleUrls: ["./presentation-font-mapping-settings-view.component.scss"]
})
export class PresentationFontMappingSettingsViewComponent implements OnInit {
   @ViewChild(MatPaginator, { static: true }) paginator: MatPaginator;
   @ViewChild(MatSort, { static: true }) sort: MatSort;
   @Output() modelChanged = new EventEmitter<PresentationSettingsChanges>();

   @Input()
   get model(): PresentationFontMappingSettingsModel {
      return this._model;
   }

   set model(model: PresentationFontMappingSettingsModel) {
      this._model = model;

      if(this.selection.hasValue()) {
         this.selection.clear();
      }

      if(this.model) {
         this.dataSource.data = model.fontMappings;
      }
      else {
         this.dataSource.data = [];
      }
   }

   displayedColumns: string[] = ["select", "trueTypeFont", "cidFont"];
   selection: SelectionModel<PresentationFontMappingModel> = new SelectionModel(true, []);
   dataSource = new MatTableDataSource<PresentationFontMappingModel>([]);
   pageSize: number;
   pageEvent: PageEvent;

   private _model: PresentationFontMappingSettingsModel;

   constructor(private dialog: MatDialog) {
   }

   ngOnInit() {
      this.dataSource.paginator = this.paginator;
      this.dataSource.sort = this.sort;
   }

   sortData(sort: Sort) {
      if(!sort.active || sort.direction === "") {
         this.dataSource.data = this.model.fontMappings;
         return;
      }

      const fontMappings = this.model.fontMappings.slice();
      fontMappings.sort((a: PresentationFontMappingModel, b: PresentationFontMappingModel) => {
         const isAsc = sort.direction === "asc";
         let result = 0;

         switch(sort.active) {
         case "trueTypeFont":
            result = a.trueTypeFont.localeCompare(b.trueTypeFont);
            break;
         case "cidFont":
            result = a.cidFont.localeCompare(b.cidFont);
         }

         return isAsc ? result : -result;
      });

      this.dataSource.data = fontMappings;
   }

   isAllSelected(): boolean {
      let pageIndex = this.paginator.pageIndex;
      let pageSize = this.paginator.pageSize;
      let pgStart = pageIndex * pageSize;
      // current page items
      let pageItems = this.dataSource.data.slice(pgStart, pgStart + pageSize);
      let all = true;
      // if empty selected list
      if(!this.selection.selected.length) {
         return false;
      }
      // check if entire page is on selection model
      pageItems.forEach(
         row => {
            if(!this.selection.selected.includes(row)) {
               all = false;
            }
         }
      );

      return all;
   }

   masterToggle(): void {
      if(this.isAllSelected()) {
         this.selection.clear();
      }
      else {
         let pageIndex = this.paginator.pageIndex;
         let pageSize = this.paginator.pageSize;
         let pgStart = pageIndex * pageSize;

         let pageItems = this.dataSource.data.slice(pgStart, pgStart + pageSize);
         pageItems.forEach(
            row => this.selection.select(row)
         );
      }
   }

   addFontMapping(): void {
      this.openFontMappingDialog().subscribe(result => {
         if(result) {
            this.model.fontMappings.push(result);
            this.dataSource.data = this.model.fontMappings;
            this.emitModel();
         }
      });
   }

   editFontMapping(): void {
      const font = this.selection.selected[0];
      const index = this.model.fontMappings.findIndex(f => f.trueTypeFont === font.trueTypeFont);
      this.openFontMappingDialog(font).subscribe(result => {
         if(result) {
            const oldFont = this.model.fontMappings[index];
            const selected = this.selection.selected.slice();
            const selectionIndex = selected.findIndex(f => f.trueTypeFont === oldFont.trueTypeFont);

            let fonts = this.model.fontMappings;
            fonts[index] = result;
            this.model.fontMappings = fonts;
            this.dataSource.data = fonts;
            this.selection.clear();

            if(selectionIndex >= 0) {
               selected[selectionIndex] = result;
               this.selection.select(...selected);
            }

            this.emitModel();
         }
      });
   }

   deleteFontMapping(): void {
      this.selection.selected.forEach(font => {
         const index = this.model.fontMappings.findIndex(f => f.trueTypeFont === font.trueTypeFont);

         if(index >= 0) {
            this.model.fontMappings.splice(index, 1);
         }
      });
      this.dataSource.data = this.model.fontMappings;
      this.selection.clear();
      this.emitModel();
   }

   private openFontMappingDialog(model?: PresentationFontMappingModel): Observable<PresentationFontMappingModel> {
      const data = new EditFontMappingDialogData(
         model ? model.trueTypeFont : null, model ? model.cidFont : null);
      return this.dialog.open(EditFontMappingDialogComponent, {
         role: "dialog",
         width: "500px",
         maxWidth: "100%",
         maxHeight: "100%",
         disableClose: true,
         data: data
      }).afterClosed();
   }

   private emitModel(): void {
      this.modelChanged.emit({
         model: this.model,
         modelType: PresentationSettingsType.FONT_MAPPING_SETTINGS_MODEL,
         valid: true
      });
   }
}
