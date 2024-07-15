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
   ElementRef,
   EventEmitter,
   HostListener,
   Input,
   OnInit,
   Output,
   ViewChild
} from "@angular/core";
import { DebounceService } from "../services/debounce.service";
import { GuiTool } from "../../common/util/gui-tool";

const DROPDOWN_TAB_WIDTH = 50;

@Component({
   selector: "responsive-tabs",
   templateUrl: "responsive-tabs.component.html",
   styleUrls: ["responsive-tabs.component.scss"]
})
export class ResponsiveTabsComponent implements OnInit {
   @ViewChild("tabSet") tabSet: ElementRef;
   @Input() selectedIndex: number = -1;
   @Input() labelFunction: (tab: any) => string;
   @Output() selectedIndexChange = new EventEmitter<number>();
   _tabs: any[];
   firstTabDropdownIndex: number = -1;
   mobile: boolean;

   constructor(private debounceService: DebounceService, private elementRef: ElementRef) {
   }

   ngOnInit(): void {
      this.mobile = GuiTool.isMobileDevice();

      Promise.resolve(null).then(() => {
         this.updateView();
      });
   }

   selectTab(index: number) {
      this.selectedIndex = index;
      this.selectedIndexChange.emit(index);
   }

   @Input()
   set tabs(tabs: any[]) {
      this._tabs = tabs;
      Promise.resolve(null).then(() => {
         this.updateView();
      });
   }

   get tabs(): any[] {
      return this._tabs;
   }

   private updateView(): void {
      // need to unhide the tabs to figure out if they can fit in cases where
      // the window size gets larger
      if(this.firstTabDropdownIndex !== -1) {
         this.firstTabDropdownIndex = -1;
         setTimeout(() => {
            this.updateView();
         }, 10);
         return;
      }

      const width = this.elementRef.nativeElement.getBoundingClientRect().width;
      const tabs = this.tabSet.nativeElement.querySelectorAll("li.tab");
      let totalWidth = 0;

      for(let i = 0; i < tabs.length; i++) {
         const tabWidth = tabs[i].getBoundingClientRect().width;
         totalWidth += tabWidth;

         if(totalWidth > width) {
            this.firstTabDropdownIndex = i;

            // if there is no space for the dropdown tab then put the previous
            // tab inside of the dropdown as well
            if(totalWidth - tabWidth + DROPDOWN_TAB_WIDTH > width) {
               this.firstTabDropdownIndex--;
            }

            return;
         }
      }
   }

   @HostListener("window:resize", ["$event"])
   onResize(event) {
      this.updateView();
   }

   getTabLabel(tab: any): string {
      return this.labelFunction ? this.labelFunction(tab) : tab;
   }
}
