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
import { HttpClient } from "@angular/common/http";
import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from "@angular/core";
import { MatDialog, MatDialogConfig } from "@angular/material/dialog";
import { MatPaginator } from "@angular/material/paginator";
import { MatSnackBar } from "@angular/material/snack-bar";
import { MatSort, Sort } from "@angular/material/sort";
import { MatTableDataSource } from "@angular/material/table";
import { GeneralSettingsChanges } from "../general-settings-page/general-settings-page.component";
import {
   LocalizationModel,
   LocalizationSettingsModel
} from "./localization-settings-model";
import { LocalizationDialogComponent } from "./localization-dialog/localization-dialog.component";
import { Searchable } from "../../../searchable";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { GeneralSettingsType } from "../general-settings-page/general-settings-type.enum";
import { ContextHelp } from "../../../context-help";

@Searchable({
   route: "/settings/general#localization",
   title: "Localization",
   keywords: ["em.settings", "em.settings.general", "em.settings.localization"]
})
@ContextHelp({
   route: "/settings/general#localization",
   link: "EMGeneralLocalization"
})
@Component({
   selector: "em-localization-settings-view",
   templateUrl: "./localization-settings-view.component.html",
   styleUrls: ["./localization-settings-view.component.scss"]
})
export class LocalizationSettingsViewComponent implements OnInit {
   @Output() modelChanged = new EventEmitter<GeneralSettingsChanges>();
   @ViewChild(MatPaginator) paginator: MatPaginator;
   @ViewChild(MatSort) sort: MatSort;

   @Input()
   get model(): LocalizationSettingsModel {
      return this._model;
   }

   set model(model: LocalizationSettingsModel) {
      this._model = model;

      if(this.model) {
         this.loadLocales();
      }
   }

   displayedColumns: string[] = ["select", "language", "country", "label"];
   selection: SelectionModel<LocalizationModel> = new SelectionModel(true, []);
   selected: LocalizationModel;
   dataSource: MatTableDataSource<LocalizationModel> = new MatTableDataSource<LocalizationModel>();
   sortedData: LocalizationModel[];

   adding: boolean = false;
   editing: boolean = false;

   private _model: LocalizationSettingsModel;

   constructor(private http: HttpClient, public snackbar: MatSnackBar,
               private dialog: MatDialog,
               private downloadService: DownloadService) {
   }

   ngOnInit() {
   }

   openBottomSheet(): void {
      const ref = this.dialog.open(LocalizationDialogComponent, <MatDialogConfig> {
         role: "dialog",
         width: "500px",
         maxWidth: "100%",
         maxHeight: "100%",
         disableClose: true,
         data: {
            selectedItem: this.selection.selected[0]
         }});
      ref.afterClosed().subscribe((result: any) => {
         if(result) {
            if(this.adding) {
               this.handleDuplicateAdd(result);
            }

            if(this.editing) {
               this.filterSelected();
            }

            this.model.locales.push(<LocalizationModel>{
               language: result.language,
               country: result.country,
               label: result.label
            });

            this.dataSource = new MatTableDataSource<LocalizationModel>(this.model.locales);
            this.dataSource.sort = this.sort;
            setTimeout(() => this.dataSource.paginator = this.paginator);

            this.emitModel();
         }

         this.toggleClose();
      });
   }

   loadLocales() {
      this.dataSource.data = this.model.locales;
      this.dataSource.sort = this.sort;
      setTimeout(() => this.dataSource.paginator = this.paginator);
   }

   deleteSelected() {
      this.filterSelected();

      this.dataSource.data = this.model.locales;
      this.dataSource.sort = this.sort;
      setTimeout(() => this.dataSource.paginator = this.paginator);
      this.selection.clear();

      this.adding = false;
      this.editing = false;

      this.emitModel();
   }

   getUserBundle() {
      this.downloadService.download("../em/general/settings/localization/generateBundle");
   }

   private emitModel() {
      this.modelChanged.emit(<GeneralSettingsChanges>{
         model: this.model,
         modelType: GeneralSettingsType.LOCALIZATION_SETTINGS_MODEL,
         valid: true
      });
   }

   reload() {
      this.http.get("../api/em/general/settings/localization/locale").subscribe();
   }

   handleDuplicateAdd(dialogResult) {
      for(let locale of this.model.locales) {
         if(locale.language === dialogResult.language && locale.country === dialogResult.country) {
            this.adding = false;
            this.editing = true;
            this.selection.select(locale);
         }
      }
   }

   filterSelected() {
      let localeCopy = this.model.locales.slice();
      this.model.locales = localeCopy.filter((locale: LocalizationModel) => {
         let include = true;
         this.selection.selected.forEach(sel => {
            if(sel.language === locale.language
               && sel.country === locale.country
               && sel.label === locale.label) {
               include = false;
            }
         });
         return include;
      });
   }

   toggleAdd() {
      this.adding = true;
      this.editing = false;
      this.selection.clear();

      this.openBottomSheet();
   }

   toggleEdit() {
      this.editing = true;
      this.adding = false;

      this.openBottomSheet();
   }

   toggleClose() {
      this.editing = false;
      this.adding = false;

      this.selection.clear();
   }

   sortData(sort: Sort) {
      this.toggleClose();
      const data = this.model.locales.slice();

      if(!sort.active || sort.direction === "") {
         this.sortedData = data;
         this.dataSource.data = this.sortedData;
         return;
      }

      this.sortedData = data.sort((a: LocalizationModel, b: LocalizationModel) => {
         const isAsc = sort.direction === "asc";
         switch(sort.active) {
         case "language":
            return this.compare(a.language, b.language, isAsc);
         case "country":
            return this.compare(a.country, b.country, isAsc);
         case "label":
            return this.compare(a.label, b.label, isAsc);
         default:
            return null;
         }
      });

      this.dataSource.data = this.sortedData;
   }

   compare(a: string, b: string, isAsc: boolean): number {
      return (a < b ? -1 : 1) * (isAsc ? 1 : -1);
   }

   // returns true if all rows on page are selected
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
}
