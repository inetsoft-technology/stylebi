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
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   Output,
   SimpleChanges
} from "@angular/core";

@Component({
   selector: "reorder-subtables-dialog",
   templateUrl: "reorder-subtables-dialog.component.html",
   styleUrls: ["reorder-subtables-dialog.component.scss"]
})
export class ReorderSubtablesDialogComponent implements OnChanges {
   @Input() tableNames: string[];
   @Output() onCommit: EventEmitter<string[]> = new EventEmitter<string[]>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   localTableNames: string[];
   selectedIndex: number;

   ngOnChanges(changes: SimpleChanges) {
      this.localTableNames = [...this.tableNames];
   }

   moveTableToTop() {
      let name = this.localTableNames[this.selectedIndex];
      this.localTableNames.splice(this.selectedIndex, 1);
      this.localTableNames.splice(0, 0, name);
      this.selectedIndex = 0;
   }

   moveTableToBottom() {
      let name = this.localTableNames[this.selectedIndex];
      this.localTableNames.splice(this.selectedIndex, 1);
      this.localTableNames.splice(this.localTableNames.length, 0, name);
      this.selectedIndex = this.localTableNames.length - 1;
   }

   moveTableUp() {
      this.swap(this.selectedIndex, this.selectedIndex - 1);
      this.selectedIndex--;
   }

   moveTableDown() {
      this.swap(this.selectedIndex, this.selectedIndex + 1);
      this.selectedIndex++;
   }

   upDisabled() {
      return this.selectedIndex == null || this.selectedIndex === 0;
   }

   downDisabled() {
      return this.selectedIndex == null || this.selectedIndex === this.localTableNames.length - 1;
   }

   ok() {
      this.onCommit.emit(this.localTableNames);
   }

   cancel() {
      this.onCancel.emit();
   }

   private swap(currIndex: number, destIndex: number): void {
      let temp = this.localTableNames[currIndex];
      this.localTableNames[currIndex] = this.localTableNames[destIndex];
      this.localTableNames[destIndex] = temp;
   }
}
