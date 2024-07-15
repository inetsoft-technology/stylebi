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

import { AbstractTableAssembly } from "../../data/ws/abstract-table-assembly";
import { ViewsheetClientService } from "../../../common/viewsheet-client/viewsheet-client.service";
import { WSConcatenateEvent } from "../../gui/ws/socket/ws-concatenate-event";
import { WorksheetTableOperator } from "../../data/ws/ws-table.operators";

@Component({
   selector: "concatenate-tables-dialog",
   templateUrl: "concatenate-tables-dialog.component.html"
})
export class ConcatenateTablesDialog {
   @Input() tables: AbstractTableAssembly[];
   @Input() socket: ViewsheetClientService;
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   private readonly controller: string = "/events/composer/worksheet/concatenate-tables";
   concatType: "intersect" | "union" | "minus" = "intersect";

   /** Returns operator corresponding to the concat type. */
   getOperator(): number {
      switch(this.concatType) {
         case "intersect":
            return WorksheetTableOperator.INTERSECT;
         case "union":
            return WorksheetTableOperator.UNION;
         case "minus":
            return WorksheetTableOperator.MINUS;
         default:
            throw new Error(`Invalid value for concatType: ${this.concatType}`);
      }
   }

   ok() {
      let event = new WSConcatenateEvent();
      event.setOperator(this.getOperator());
      event.setTables(this.tables.map(el => el.name));

      this.socket.sendEvent(this.controller, event);
      this.onCommit.emit();
   }

   close() {
      this.onCancel.emit();
   }
}