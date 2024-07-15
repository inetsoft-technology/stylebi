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
import { SelectionModel } from "@angular/cdk/collections";
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { MatTableDataSource } from "@angular/material/table";
import { ServerLocation } from "../../../../../../../shared/schedule/model/server-location";
import { ServerLocationEditorComponent } from "../server-location-editor/server-location-editor.component";

@Component({
   selector: "em-server-locations-view",
   templateUrl: "./server-locations-view.component.html",
   styleUrls: ["./server-locations-view.component.scss"]
})
export class ServerLocationsViewComponent implements OnInit, OnChanges {
   @Output()
   serverLocationsChange = new EventEmitter<ServerLocation[]>();

   @Input()
   get serverLocations(): ServerLocation[] {
      return this._serverLocations;
   }

   set serverLocations(locations: ServerLocation[]) {
      this._serverLocations = locations || [];
      this.dataSource.data = this._serverLocations;
   }

   displayedColumns = ["select", "label", "path"];
   dataSource = new MatTableDataSource<ServerLocation>([]);
   selection = new SelectionModel<ServerLocation>(true, []);

   private _serverLocations: ServerLocation[] = [];

   constructor(private dialog: MatDialog) {
   }

   ngOnInit(): void {
      this.displayedColumns = ["select", "label", "path", "username", "password"];
   }

   ngOnChanges(changes: SimpleChanges): void {
      this.selection.clear();
   }

   isAllSelected(): boolean {
      const numSelected = this.selection.selected.length;
      const numRows = this.dataSource.data.length;
      return numSelected === numRows;
   }

   masterToggle(): void {
      if(this.isAllSelected()) {
         this.selection.clear();
      }
      else {
         this.dataSource.data.forEach(row => this.selection.select(row));
      }
   }

   addServerLocation(): void {
      this.openServerLocationEditor();
   }

   editServerLocation(location: ServerLocation): void {
      this.selection.clear();
      this.selection.select(location);
      this.openServerLocationEditor(location);
   }

   removeServerLocation(): void {
      const selected = this.selection.selected.slice();
      selected.forEach(row => {
         const idx = this.serverLocations.indexOf(row);

         if(idx >= 0) {
            this.serverLocations.splice(idx, 1);
         }
      });
      this.dataSource.data = this.serverLocations;
      this.selection.clear();
      this.serverLocationsChange.emit(this.serverLocations);
   }

   private openServerLocationEditor(oldLocation?: ServerLocation): void {
      const config = {
         maxWidth: "100%",
         maxHeight: "100%",
         disableClose: true,
         data: {
            location: oldLocation || this.createServerLocation(),
            locations: this.serverLocations
         }
      };
      this.dialog.open(ServerLocationEditorComponent, config).afterClosed().subscribe(
         (location) => {
            if(location) {
               if(oldLocation) {
                  Object.assign(oldLocation, location);
               }
               else {
                  this.serverLocations.push(location);
               }

               this.dataSource.data = this.serverLocations;
               this.serverLocationsChange.emit(this.serverLocations);
            }
         });
   }

   private createServerLocation(): ServerLocation {
      return {
         label: null,
         path: null
      };
   }
}
