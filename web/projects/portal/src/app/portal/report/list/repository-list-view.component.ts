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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { RepositoryEntry } from "../../../../../../shared/data/repository-entry";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";

@Component({
   selector: "p-repository-list-view",
   templateUrl: "./repository-list-view.component.html",
   styleUrls: ["./repository-list-view.component.scss"]
})
export class RepositoryListViewComponent implements OnInit {
   @Input() rootNode: TreeNodeModel;
   @Input() childRouteShown: boolean;
   @Input() wizardShown: boolean;
   @Output() entryOpened = new EventEmitter<RepositoryEntry>();
   @Output() editViewsheet = new EventEmitter<RepositoryEntry>();

   constructor() {
   }

   ngOnInit(): void {
   }
}
