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
import { Component, EventEmitter, Input, Output } from "@angular/core";

export type NewDataModelChoice = "physical" | "logical" | "vpm";

@Component({
   selector: "new-data-model-dialog",
   templateUrl: "./new-data-model-dialog.component.html",
   styleUrls: ["./new-data-model-dialog.component.scss"]
})
export class NewDataModelDialogComponent {
   @Input() canCreateLogicalModel: boolean = false;
   @Input() vpmEnabled: boolean = false;
   @Output() onCommit = new EventEmitter<NewDataModelChoice>();
   @Output() onCancel = new EventEmitter<void>();

   select(choice: NewDataModelChoice): void {
      if((choice === "logical" && !this.canCreateLogicalModel) ||
         (choice === "vpm" && !this.canCreateVpm))
      {
         return;
      }

      this.onCommit.emit(choice);
   }

   cancel(): void {
      this.onCancel.emit();
   }

   get canCreateVpm(): boolean {
      return this.canCreateLogicalModel && this.vpmEnabled;
   }

   get logicalModelHint(): string {
      if(this.canCreateLogicalModel) {
         return "_#(Define business-facing fields and metrics on top of a physical view.)";
      }

      return "_#(Create a physical view first.)";
   }

   get vpmHint(): string {
      if(!this.canCreateLogicalModel) {
         return "_#(Create a physical view first.)";
      }

      if(!this.vpmEnabled) {
         return "_#(Available when VPM is enabled for this environment.)";
      }

      return "_#(Apply row-level filtering and data access rules for governed use.)";
   }
}
