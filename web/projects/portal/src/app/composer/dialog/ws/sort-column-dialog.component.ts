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
import { HttpParams } from "@angular/common/http";
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { Tool } from "../../../../../../shared/util/tool";
import { ModelService } from "../../../widget/services/model.service";
import { SortColumnDialogModel } from "../../data/ws/sort-column-dialog-model";

@Component({
   selector: "sort-column-dialog",
   templateUrl: "sort-column-dialog.component.html",
   styleUrls: ["sort-column-dialog.component.scss"]
})
export class SortColumnDialog implements OnInit {
   @Input() runtimeId: string;
   @Input() tableName: string;
   @Input() showColumnName: boolean;
   @Output() onCommit = new EventEmitter<any>();
   @Output() onCancel = new EventEmitter<string>();
   model: SortColumnDialogModel;
   private readonly restController = "../api/composer/ws/dialog/sort-column-dialog-model/";
   private readonly socketController = "/events/ws/dialog/sort-column-dialog-model";

   constructor(private modelService: ModelService) {
   }

   ngOnInit(): void {
      const params = new HttpParams().set("table", this.tableName);

      this.modelService.getModel(this.restController + Tool.byteEncode(this.runtimeId), params)
         .subscribe((data: SortColumnDialogModel) => {
            this.model = data;
         },
         () => {
            console.error("Could not fetch sort information for " + this.tableName);
            this.cancel();
         }
      );
   }

   ok(): void {
      this.onCommit.emit({model: this.model, controller: this.socketController});
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }
}
