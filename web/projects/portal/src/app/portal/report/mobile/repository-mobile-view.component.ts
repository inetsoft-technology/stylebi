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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { RepositoryEntry } from "../../../../../../shared/data/repository-entry";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { ReportTabModel } from "../report-tab-model";
import { RepositoryEntryType } from "../../../../../../shared/data/repository-entry-type.enum";

@Component({
   selector: "p-repository-mobile-view",
   templateUrl: "./repository-mobile-view.component.html",
   styleUrls: ["./repository-mobile-view.component.scss"]
})
export class RepositoryMobileViewComponent implements OnInit {
   @Input() model: ReportTabModel;
   @Input() rootNode: TreeNodeModel;
   @Input() selectedEntry: RepositoryEntry;

   @Input()
   set childRouteShown(value: boolean) {
       if(value != this._childRouteShown) {
          this._childRouteShown = value;

          if(value) {
             this.activePane = "Viewer";
          }
       }
   }

   get childRouteShown(): boolean {
      return this._childRouteShown;
   }

   @Output() entryOpened = new EventEmitter<RepositoryEntry>();
   @Output() editViewsheet = new EventEmitter<RepositoryEntry>();

   activePane: "Repository" | "Viewer" = "Repository";
   private _childRouteShown = false;

   constructor() {
   }

   ngOnInit(): void {
   }

   onEntryOpened(entry: RepositoryEntry): void {
      if(entry.type === RepositoryEntryType.VIEWSHEET) {
         this.activePane = "Viewer";
      }

      this.entryOpened.emit(entry);
   }
}