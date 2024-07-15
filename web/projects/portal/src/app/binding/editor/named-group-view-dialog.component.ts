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
import { Component, Input, Output, EventEmitter, ViewChild } from "@angular/core";
import { NamedGroupPane } from "./named-group-pane.component";
import { GroupCondition } from "../data/named-group-info";

@Component({
   selector: "named-group-view",
   templateUrl: "named-group-view-dialog.component.html",
})
export class NamedGroupView {
   @ViewChild("namedGroupPane", {static: true}) property: NamedGroupPane;
   @Input() oldGroups: GroupCondition[];
   @Input() ngValues: any[];
   groups: GroupCondition[] = [];
   @Output() onCommit: EventEmitter<GroupCondition[]> = new EventEmitter<GroupCondition[]>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();

   okClicked(evt: MouseEvent): void {
      evt.stopPropagation();
      evt.preventDefault();
      this.onCommit.emit(this.groups);
   }

   cancelClicked(evt: MouseEvent): void {
      evt.stopPropagation();
      this.onCancel.emit("cancel");
   }
}
