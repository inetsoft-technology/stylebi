/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import {
   Component, ElementRef, EventEmitter, Input, Output, ViewChild,
} from "@angular/core";

@Component({
   selector: "search-list",
   templateUrl: "search-list.component.html",
   styleUrls: ["search-list.component.scss"],
})
export class SearchListComponent {
   @ViewChild("searchInput") input: ElementRef;
   @Input() multiSelect: boolean = false;
   @Input() searchStr: string = "";
   @Output() doSearch = new EventEmitter<string>();
   @Output() changeSearchString = new EventEmitter<string>();
   @Output() onCloseSearch = new EventEmitter();
   @Output() downInSearch = new EventEmitter<boolean>();
   _visible: boolean = false;
   private searchTimer: any;
   searchPending: boolean = false;

   @Input()
   set visible(vis: boolean) {
      this._visible = vis;

      if(vis) {
         // Set time out to make sure the input is already loaded
         setTimeout(() => this.input.nativeElement.focus(), 0);
      }
   }

   get visible(): boolean {
      return this._visible;
   }

   changeValue(value: string) {
      clearTimeout(this.searchTimer);

      this.searchTimer = setTimeout(() => {
         this.changeSearchString.emit(value);
      }, 500);
   }

   onSearchKeyUp(evt: KeyboardEvent) {
      if("Enter" == evt.key) {
         evt.stopImmediatePropagation();
         evt.stopPropagation();
         evt.preventDefault();
      }
   }

   closeSearch(evt: MouseEvent) {
      this.onCloseSearch.emit();
   }
}
