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
   AfterViewChecked,
   AfterViewInit,
   Component,
   Input, OnDestroy,
   OnInit,
   ViewChild
} from "@angular/core";
import { NgbDropdown } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";
import { takeUntil } from "rxjs/operators";
import { DropdownObserver } from "../../services/dropdown-observer.service";
import { ToolbarActionGroup } from "../toolbar-action-group";
import { ToolbarAction } from "../toolbar-action";

@Component({
   selector: "toolbar-group",
   templateUrl: "toolbar-group.component.html",
   styleUrls: ["toolbar-group.component.scss"]
})
export class ToolbarGroup implements OnInit, AfterViewInit, OnDestroy {
   @Input() asMenu: boolean = false;
   @Input() actionGroup: ToolbarActionGroup;
   @Input() mergeActionGroup: ToolbarActionGroup;
   @Input() snapToObjects: boolean = false;
   @Input() snapToGrid: boolean = false;
   @Input() childGroupToolbar: boolean = false;
   @Input() placeOnRight: boolean = false;
   @Input() showTooltips: boolean = false;
   @ViewChild(NgbDropdown) dropdown: NgbDropdown;

   dropdownId: string;
   private destroy$ = new Subject<void>();

   constructor(private dropdownObserver: DropdownObserver) {}

   ngOnInit(): void {
      this.dropdownId = "toolbar-group-" + (new Date()).getTime();
   }

   ngAfterViewInit(): void {
      if(this.dropdown) {
         this.dropdown.openChange
            .pipe(takeUntil(this.destroy$))
            .subscribe(open => {
               if(open) {
                  this.dropdownObserver.onDropdownOpened();
               }
               else {
                  this.dropdownObserver.onDropdownClosed();
               }
            });
      }
   }

   ngOnDestroy(): void {
      if(this.dropdown && this.dropdown.isOpen()) {
         this.dropdownObserver.onDropdownClosed();
      }

      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   getStyle(secondLevelParent: number) {
      let butClass: string = "";

      switch(secondLevelParent){
         case 0:
            butClass = this.childGroupToolbar ? "dropdown-item" :
               "btn composer-btn toolbar-btn pb-1 ps-1 pe-1";
            break;
         case 1:
            butClass = this.childGroupToolbar ? "left" : this.bottomPlacement;
            break;
         case 2:
            butClass = this.childGroupToolbar ? "second-dropdown-item" : "dropdown-item";
            break;
         case 3:
            butClass = this.childGroupToolbar ? "second-item-label" : "item-label";
            break;
      }

      return butClass;
   }

   get bottomPlacement(): string {
      return this.placeOnRight ? "bottom-right" : "bottom-left";
   }

   click(event: any) {
      if(this.childGroupToolbar) {
         event.stopPropagation();
      }
   }

   isCheckboxInput(action: ToolbarAction) {
      return action.iconClass === "form-check-input";
   }

   getSnapToModel(action: ToolbarAction): boolean {
      return action.label === "Snap to grid" ? this.snapToGrid : this.snapToObjects;
   }
}
