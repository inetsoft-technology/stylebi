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
import { HttpClient } from "@angular/common/http";
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { DataRef } from "../../../common/data/data-ref";
import { OrderModel } from "../../data/table/order-model";
import { CalcConditionItemPaneProvider } from "./calc-condition-item-pane-provider";

@Component({
   selector: "calc-named-group-dialog",
   templateUrl: "calc-named-group-dialog.component.html",
   styleUrls: ["calc-named-group-dialog.component.scss"]
})
export class CalcNamedGroupDialog implements OnInit{
   @Input() order: OrderModel;
   @Input() field: DataRef;
   @Input() runtimeId: string;
   @Input() table: string;
   @Input() assemblyName: string;
   @Input() variableValues: string[];
   @Output() onCommit: EventEmitter<OrderModel> = new EventEmitter<OrderModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   provider: CalcConditionItemPaneProvider;

   constructor(private http: HttpClient) {
   }

   apply(evt: any) {
      if(!evt) {
         return;
      }

      this.order.info = evt.namedGroupInfo;
      this.order.others = evt.others;
      this.onCommit.next(this.order);
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   ngOnInit(): void {
      this.provider = new CalcConditionItemPaneProvider(this.http,
         this.runtimeId, this.table, this.assemblyName, this.variableValues);
   }
}
