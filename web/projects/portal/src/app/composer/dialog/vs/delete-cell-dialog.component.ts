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
import { Component, OnInit, Output, EventEmitter } from "@angular/core";
import { ModelService } from "../../../widget/services/model.service";
import { DeleteCellDialogModel } from "../../data/vs/delete-cell-dialog-model";

@Component({
   selector: "delete-cell-dialog",
   templateUrl: "delete-cell-dialog.component.html"
})

export class DeleteCellDialog implements OnInit {
   model: DeleteCellDialogModel;
   controller: string = "";
   @Output() onCommit: EventEmitter<DeleteCellDialogModel> =
      new EventEmitter<DeleteCellDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();

   constructor(private modelService: ModelService) { }

   ngOnInit(): void {
      this.modelService.getModel(this.controller).subscribe((data) => {
         this.model = <DeleteCellDialogModel> data;
      });
   }

   ok(): void {
      this.onCommit.emit(this.model);
   }

   close(): void {
      this.onCancel.emit("cancel");
   }
}
