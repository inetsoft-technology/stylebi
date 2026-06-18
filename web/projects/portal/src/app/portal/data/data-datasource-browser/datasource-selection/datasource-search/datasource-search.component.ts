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
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from "@angular/core";
import { FormsModule } from "@angular/forms";

@Component({
    selector: "datasource-search",
    templateUrl: "datasource-search.component.html",
    styleUrls: ["datasource-search.component.scss"],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FormsModule]
})
export class DatasourceSearchComponent {
   @Output() searchStringChange = new EventEmitter<string>();
   draftSearchString: string = "";
   private _searchString: string = "";

   @Input()
   get searchString(): string {
      return this._searchString;
   }

   set searchString(value: string) {
      this._searchString = value || "";
      this.draftSearchString = this._searchString;
   }

   changeSearchString(searchString: string): void {
      this.draftSearchString = searchString;
   }

   search(): void {
      this._searchString = this.draftSearchString || "";
      this.searchStringChange.emit(this._searchString);
   }
}
