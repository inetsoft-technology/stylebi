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
import { Component, OnChanges, Input, Output, EventEmitter } from "@angular/core";
import { ValueLabelModel } from "../data/value-label-model";

@Component({
   selector: "manual-ordering-dialog",
   templateUrl: "manual-ordering-dialog.component.html",
   styleUrls: ["manual-ordering-dialog.component.scss"]
})
export class ManualOrderingDialog implements OnChanges {
   @Input() manualOrders: string[];
   @Input() valueLabelList: ValueLabelModel[];
   @Input() helpLinkKey: string;
   @Output() onCommit: EventEmitter<String[]> = new EventEmitter<String[]>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   selIndex: number = 0;

   ngOnChanges() {
      this.selIndex = this.manualOrders ? this.manualOrders.length - 1 : -1;
   }

   okClicked(evt: MouseEvent): void {
      evt.stopPropagation();
      evt.preventDefault();
      this.onCommit.emit(this.manualOrders);
   }

   cancelClicked(evt: MouseEvent): void {
      evt.stopPropagation();
      this.onCancel.emit("cancel");
   }

   upClick(evt: MouseEvent) {
      let manualOrder = this.manualOrders[this.selIndex - 1];
      this.manualOrders[this.selIndex - 1] = this.manualOrders[this.selIndex];
      this.manualOrders[this.selIndex] = manualOrder;
      this.selIndex--;
   }

   downClick(evt: MouseEvent) {
      let manualOrder = this.manualOrders[this.selIndex + 1];
      this.manualOrders[this.selIndex + 1] = this.manualOrders[this.selIndex];
      this.manualOrders[this.selIndex] = manualOrder;
      this.selIndex++;
   }

   getLabel(val: any) {
      let pair = this.valueLabelList.find((valLabel) => valLabel.value == val);

      if(!!pair) {
         return pair.label;
      }
      else {
         return val + "";
      }
   }

   get manualOrders5000(): string[] {
      return this.manualOrders.length > 5000 ? this.manualOrders.slice(0, 5000) : this.manualOrders;
   }
}
