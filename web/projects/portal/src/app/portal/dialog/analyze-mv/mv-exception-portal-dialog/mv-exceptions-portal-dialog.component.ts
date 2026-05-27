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
import { Component, EventEmitter, Inject, OnInit, Output } from "@angular/core";
import { MVExceptionModel } from "../../../../../../../shared/util/model/mv/mv-exception-model";
import { DialogButtonsDirective } from "../../../../widget/standard-dialog/dialog-buttons.directive";
import { NgFor, NgIf } from "@angular/common";
import { ScrollableFlexTableDirective } from "../../../../widget/scrollable-table/scrollable-flex-table.directive";
import { DialogContentDirective } from "../../../../widget/standard-dialog/dialog-content.directive";
import { StandardDialogComponent } from "../../../../widget/standard-dialog/standard-dialog.component";

@Component({
    selector: "mv-exceptions-portal-dialog",
    templateUrl: "./mv-exceptions-portal-dialog.component.html",
    styleUrls: ["./mv-exceptions-portal-dialog.component.scss"],
    standalone: true,
    imports: [StandardDialogComponent, DialogContentDirective, ScrollableFlexTableDirective, NgFor, NgIf, DialogButtonsDirective]
})
export class MVExceptionsPortalDialogComponent {
   @Output() onCommit: EventEmitter<string> = new EventEmitter<string>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   exceptions: MVExceptionModel[];
   submitOnEnter = () => true;

   okClicked(): void {
      this.onCommit.emit("ok");
   }

   closeDialog(): void {
      this.onCancel.emit("cancel");
   }
}
