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
import { HttpClient } from "@angular/common/http";
import { AfterViewInit, Component, OnInit, ViewChild } from "@angular/core";
import { UntypedFormControl } from "@angular/forms";
import { MatInput } from "@angular/material/input";
import { MatPaginator } from "@angular/material/paginator";
import { MatSort } from "@angular/material/sort";
import { merge as mergeObservables, Observable } from "rxjs";
import { delay, map, tap } from "rxjs/operators";
import { ContextHelp } from "../../../context-help";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Searchable } from "../../../searchable";
import { Secured } from "../../../secured";
import { PropertySettingsDatasourceService } from "../property-settings-services/property-settings-datasource.service";
import { PropertiesTool } from "./properties-tool";

export interface PropertySetting {
   propertyName: string;
   propertyValue: string;
   editing: boolean;
   newRow?: boolean;
}

const DEFAULT_PROPERTY: PropertySetting = {
   propertyName: "",
   propertyValue: "",
   editing: true
};

@Secured({
   route: "/settings/properties",
   label: "All Properties",
   hiddenForMultiTenancy: true
})
@Searchable({
   route: "/settings/properties",
   title: "em.propertyEditor.title",
   keywords: ["em.keyword.properties", "em.keyword.editor"]
})
@ContextHelp({
   route: "/settings/properties",
   link: "EMSettingsProperties"
})
@Component({
   selector: "em-property-settings-view",
   templateUrl: "./property-settings-view.component.html",
   styleUrls: ["./property-settings-view.component.scss"]
})
export class PropertySettingsViewComponent implements OnInit, AfterViewInit {

   @ViewChild(MatPaginator, { static: true }) paginator: MatPaginator;
   @ViewChild(MatSort, { static: true }) sort: MatSort;
   @ViewChild("nameInput") nameInput: MatInput;

   nameInputControl: UntypedFormControl = new UntypedFormControl("");
   searchFilter: string = "";
   columnNames: string[] = ["propertyName", "propertyValue"];
   dataSource: PropertySetting[];
   addingNewRow: boolean = false;
   editingRow: PropertySetting = Object.assign({}, DEFAULT_PROPERTY);
   previousRow: PropertySetting = Object.assign({}, DEFAULT_PROPERTY);
   currentTimeout: any;
   defaultProperties: PropertySetting[];
   editPropertyAutocomplete: Observable<PropertySetting[]>;
   valueOptions: { label: string, value: string }[];

   constructor(private pageTitle: PageHeaderService,
               private dataService: PropertySettingsDatasourceService,
               private http: HttpClient)
   {
   }

   ngOnInit() {
      this.pageTitle.title = "_#(js:All Properties)";

      if(this.sort) {
         this.sort.active = "propertyName";
         this.sort.direction = this.sort.start;
      }

      this.fetchData();
      this.dataService.connect().subscribe(value => this.dataSource = value);

      this.http.get("../api/admin/properties/defaults").pipe(
         map(property => {
            let propertyArray: PropertySetting[] = [];
            Object.keys(property).forEach((key) => {
               let val = property[key];
               propertyArray.push(<PropertySetting> {
                  editing: false,
                  propertyValue: val,
                  propertyName: key
               });
            });

            return propertyArray;
         })
      ).subscribe(propertySettings => this.defaultProperties = propertySettings);

      this.editPropertyAutocomplete = this.nameInputControl.valueChanges.pipe(
         tap((change: string) => {
            this.updateValueOptions(change);
         }),
         map((input: string) =>
            this.defaultProperties.filter(property =>
               input && property.propertyName.toLowerCase().includes(input.toLowerCase()))
         ));
   }

   private updateValueOptions(change: string) {
      this.editingRow.propertyName = change;
      let options = PropertiesTool.getPropertyOptions(change);
      this.valueOptions = options ? options : [];
   }

   ngAfterViewInit() {
      //reset paginator after sorting
      this.sort.sortChange.subscribe(() => this.paginator.pageIndex = 0);

      if(this.paginator && this.sort) {
         mergeObservables(this.paginator.page, this.sort.sortChange)
            .pipe(
               tap(() => {
                  this.fetchData();
               })
            )
            .subscribe();
      }
   }

   addRow() {
      this.cancelEditingRow();
      let obs = this.dataService.addRow();
      obs.subscribe((propertyRow) => {
         this.editingRow = propertyRow;
         this.addingNewRow = true;
      });
      this.nameInputControl.setValue("");
      setTimeout(() => !!this.nameInput ? this.nameInput.focus() : null, 50);
      this.editingRow.propertyValue = "";
      this.fetchData();
   }

   editRow(row: PropertySetting) {
      if(this.dataService.rowBeingAdded) {
         //if adding new row and attempt to edit existing, clear all editing
         for(let r of this.dataSource) {
            if(r.editing) {
               r.editing = false;
               this.cancelEditingRow(r);
            }
         }
      }
      else {
         this.updateEditingRow(row);
      }
   }

   private updateEditingRow(row: PropertySetting) {
      row.editing = true;
      this.editingRow = Object.assign({}, row);

      if(this.previousRow.propertyName != row.propertyName) {
         this.previousRow.editing = false;
      }

      this.dataService.cancelRow();
      this.previousRow = row;
      this.updateValueOptions(this.editingRow.propertyName);
   }

   setPropertyName(prop: PropertySetting) {
      this.editingRow.propertyName = prop.propertyName;
      this.editingRow.propertyValue =
         this.valueOptions[0] ? this.valueOptions[0].value : prop.propertyValue;
   }

   acceptChanges() {
      this.previousRow.editing = false;
      this.editingRow.editing = false;
      const name = this.editingRow.propertyName;
      const value = this.editingRow.propertyValue;
      this.dataService.changeRow(this.editingRow)
         .subscribe(() => this.insertOrReplace(name, value));
      this.nameInputControl.setValue("");
      this.editingRow = Object.assign({}, DEFAULT_PROPERTY);
      this.dataService.cancelRow();
   }

   // when a property is added or edited, don't refresh data otherwise the new property
   // may be pushed to a different page and seems lost. just add to the top of current
   // page or replace the existing value if it already exists.
   private insertOrReplace(name: string, value: any) {
      const existing = this.dataSource.filter(d => d.propertyName == name);

      if(existing.length) {
         existing[0].propertyValue = value;
      }
      else {
         this.dataSource.splice(1, 0, {propertyName : name, propertyValue: value, editing: false});
      }

      this.dataSource = [...this.dataSource];
   }

   cancelEditingRow(row: PropertySetting = this.editingRow) {
      row.editing = false;
      this.editingRow.editing = false;
      this.previousRow.editing = false;
      this.dataService.cancelRow();
      this.fetchData();
   }

   deleteRow(row: PropertySetting) {
      this.dataService.deleteRow(row)
         .pipe(
            tap(() => {
               this.dataSource = this.dataSource.filter(p => p.propertyName != row.propertyName);
            }),
            // SreeEnv debounces change notifications, so if the fetchData call goes to a different
            // instance it is likely that its local capy doesn't have the row removed yet.
            delay(1000)
         )
         .subscribe(() => this.fetchData());
   }

   fetchData() {
      this.dataService.fetchData(
         this.sort.active,
         this.sort.direction,
         this.paginator.pageIndex,
         this.paginator.pageSize,
         !!this.searchFilter ? this.searchFilter.trim() : "",
         false
      );
   }

   getDataLength(): number {
      if(this.dataService.data) {
         return this.dataService.data.length;
      }

      return 0;
   }

   applyFilter(searchFilter: string) {
      this.searchFilter = searchFilter;

      if(!!this.currentTimeout) {
         clearTimeout(this.currentTimeout);
      }

      this.currentTimeout = setTimeout(() => this.fetchData(), 300);
   }
}
