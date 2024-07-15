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
import { Component, EventEmitter, Input, Optional, Output } from "@angular/core";
import { TableEditorService } from "../../services/table/table-editor.service";
import { UIContextService } from "../../../common/services/ui-context.service";

@Component({
   selector: "detail-option",
   templateUrl: "detail-option.component.html",
   styleUrls: ["./detail-option.component.scss"]
})
export class DetailOption {
   @Input() field: any;
   @Input() fieldType: string;
   @Input() col: number;
   @Input() embedded: boolean;
   @Output() onPopUpWarning: EventEmitter<any> = new EventEmitter<any>();

   public constructor(private editorService: TableEditorService,
                      private uiContextService: UIContextService)
   {
   }

   get order(): number {
      if(this.isVS()) {
         return this.field.order;
      }

      return 0;
   }

   get visible(): boolean {
      if(this.isVS()) {
         return this.field.visible;
      }

      return false;
   }

   changeOrder(val: number, mouseEvent: MouseEvent) {
      mouseEvent.stopPropagation();

      if(this.isVS()) {
         this.field.order = val;
         this.editorService.sortColumn(this.col, mouseEvent.ctrlKey);
      }
   }

   changeVisible(val: boolean) {
      if(this.isVS()) {
         this.field.visible = val;
         this.editorService.setBindingModel();
      }
   }

   isVS() {
      return this.uiContextService.isVS();
   }
}
