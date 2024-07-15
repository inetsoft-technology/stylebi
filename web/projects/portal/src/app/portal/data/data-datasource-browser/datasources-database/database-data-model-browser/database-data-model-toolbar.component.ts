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
const SEARCH_DATA_MODEL_URI: string = "../api/data/search/dataModel";
const SEARCH_VPM_URI: string = "../api/data/vpm/search";

import { AssetListBrowseModel } from "../../../model/datasources/database/asset-list-browse-model";
import { Component, ElementRef, EventEmitter, Input, Output, Renderer2, ViewChild } from "@angular/core";
import { DatabaseAsset } from "../../../model/datasources/database/database-asset";
import { Observable, of } from "rxjs";
import { catchError, debounceTime, distinctUntilChanged, map, switchMap } from "rxjs/operators";
import { SearchCommand } from "../../../commands/search-command";
import { HttpClient } from "@angular/common/http";

@Component({
   selector: "database-data-model-toolbar",
   templateUrl: "./database-data-model-toolbar.component.html",
   styleUrls: ["./database-data-model-toolbar.component.scss"]
})
export class DatabaseDataModelToolbarComponent {
   @Input() database: string;
   @Input() model: AssetListBrowseModel;
   @Input() searchVisible: boolean = false;
   @Input() searchQuery: string = "";
   @Input() isvpm: boolean = false;
   @Input() moveDisable: boolean = false;
   @Input() selectedItems: DatabaseAsset[] = [];
   @Input() isRoot: boolean = false;
   @Output() onAddPhysicalView = new EventEmitter();
   @Output() onAddVPM = new EventEmitter();
   @Output() onAddLM = new EventEmitter();
   @Output() onAddFolder = new EventEmitter();
   @Output() onDeleteSelected = new EventEmitter();
   @Output() onMoveSelected = new EventEmitter();
   @Output() onToggleSelection = new EventEmitter<boolean>();
   @Output() onSearch = new EventEmitter<string>();
   @ViewChild("searchInput") searchInput: ElementRef;
   selectionOn: boolean = false;
   searchFunc: (text: Observable<string>) => Observable<any> = (text: Observable<string>) =>
      text.pipe(
         debounceTime(300),
         distinctUntilChanged(),
         switchMap((term) =>
            this.httpClient.post((this.isvpm ? SEARCH_VPM_URI : SEARCH_DATA_MODEL_URI) + "/names",
            new SearchCommand(term, "/", 0, this.database))),
         catchError(() => of([])),
         map((data: AssetListBrowseModel) => {
            return !!data && !!data.names ? data.names.slice(0, 8) : [];
         })
      );

   constructor(private renderer: Renderer2, private httpClient: HttpClient) {
   }

   toggleSearch(event: any) {
      if(!this.searchVisible) {
         this.searchVisible = true;

         let collapseSearchListener: any = this.renderer.listen("document", "click",
            (targetEvent: any) => {
               if(event !== targetEvent && targetEvent.target != this.searchInput?.nativeElement) {
                  this.searchVisible = false;
                  collapseSearchListener();
               }
            });

         // since searchInput is hidden at time of toggle, need to set timeout so it is focused correctly
         setTimeout(() => {
            this.searchInput.nativeElement.focus();
         });
      }
      else {
         this.searchVisible = false;

         if(!!this.searchQuery) {
            this.search();
         }
      }
   }

   search(query: string = null): void {
      if(!!query) {
         this.searchQuery = query;
      }

      if(!this.searchQuery) {
         return;
      }

      this.onSearch.emit(this.searchQuery);
      this.searchQuery = null;
   }

   addPhysicalView() {
      if(this.editable) {
         this.onAddPhysicalView.emit();
      }
   }

   addLogicalModel() {
      if(this.editable) {
         this.onAddLM.emit();
      }
   }

   addDataModelFolder() {
      if(this.editable) {
         this.onAddFolder.emit();
      }
   }

   deleteSelected() {
      this.onDeleteSelected.emit();
   }

   moveSelected() {
      this.onMoveSelected.emit();
   }

   addVPMModel() {
      if(this.editable) {
         this.onAddVPM.emit();
      }
   }

   get editable(): boolean {
      return !!this.model?.editable;
   }

   get deletable(): boolean {
      return !!this.model?.deletable;
   }

   /**
    * Return tooltip for the add new model button.
    * @returns {string} tooltip to display
    */
   get addButtonTooltip(): string {
      return "_#(js:data.datasources.newVPM)";
   }

   get selectionDeletable(): boolean {
      return !this.selectedItems.some(item => (!item.deletable));
   }

   /**
    * Turn selection state on or off.
    */
   toggleSelectionState(): void {
      this.selectionOn = !this.selectionOn;
      this.onToggleSelection.emit(this.selectionOn);
   }

   /**
    * Get tooltip string for the toggle selection button.
    * @returns {string} the tooltip string
    */
   get toggleSelectTooltip(): string {
      if(this.selectionOn) {
         return "_#(js:data.datasets.selectOff)";
      }
      else {
         return "_#(js:data.datasets.selectOn)";
      }
   }

   get canCreateLogicalModel(): boolean {
      return this.model.dbPartitionCount && this.model.dbPartitionCount > 0;
   }
}
