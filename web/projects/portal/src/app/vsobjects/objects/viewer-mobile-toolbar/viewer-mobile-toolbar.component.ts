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
import {
   Component, EventEmitter, Input, Output, ElementRef, ViewChild,
} from "@angular/core";
import { AbstractVSActions } from "../../action/abstract-vs-actions";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DropdownOptions } from "../../../widget/fixed-dropdown/dropdown-options";
import { DropdownRef } from "../../../widget/fixed-dropdown/fixed-dropdown-ref";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { ToolbarActionsHandler } from "../../toolbar-actions-handler";

@Component({
   selector: "viewer-mobile-toolbar",
   templateUrl: "viewer-mobile-toolbar.component.html",
   styleUrls: ["viewer-mobile-toolbar.component.scss"]
})
export class ViewerMobileToolbarComponent {
   _actions: AbstractVSActions<any>;
   @Output() closeMobileToolbar: EventEmitter<any> = new EventEmitter<any>();
   @ViewChild("mobileSandwichButton") mobileSandwichElement: ElementRef;
   @ViewChild("closeButton") closeButtonElement: ElementRef;
   sandwichMenuOpen: boolean = false;
   mobileSandwichRef: DropdownRef;
   hasToolbarActions: boolean;
   showing: AssemblyActionGroup[] = [];
   more: AssemblyActionGroup[] = [];

   /**
    * Determines if mobile toolbar actions will fit across the screen,
    * if this returns false, the buttons should be compressed
    */
   @Input() set actions(actions: AbstractVSActions<any>) {
      this._actions = actions;
   }

   get actions(): AbstractVSActions<any> {
      return this._actions;
   }

   constructor(private dropdownService: FixedDropdownService) {
   }

   get hasMenuAction(): boolean {
      return this.actions && this.actions.menuActions.some(m => m.visible);
   }

   showMobileSandwichDropdown(component: any): void {
      if(!this.sandwichMenuOpen) {
         const bounds = this.mobileSandwichElement.nativeElement.getBoundingClientRect();
         let options: DropdownOptions = {
            position: {
               x: bounds.left,
               y: bounds.top + this.mobileSandwichElement.nativeElement.offsetHeight
            },
            contextmenu: true,
            autoClose: true,
            closeOnOutsideClick: true,
            zIndex: 1000
         } as DropdownOptions;
         this.mobileSandwichRef = this.dropdownService.open(component, options);
         this.sandwichMenuOpen = true;
      }
      else {
         this.mobileSandwichRef.close();
         this.sandwichMenuOpen = false;
      }
   }

   allowedActionsNum(): number {
      let defaultButtons = 1;

      if(this.hasMenuAction) {
         defaultButtons++;
      }

      return Math.floor(window.innerWidth / ToolbarActionsHandler.MOBILE_BUTTON_WIDTH) - defaultButtons;
   }

   get showingActions(): AssemblyActionGroup[] {
      const actions = ToolbarActionsHandler.getShowingActions(this.actions.toolbarActions,
         this.allowedActionsNum());
      ToolbarActionsHandler.copyActions(actions, this.showing);
      this.hasToolbarActions = actions.some(group =>
         group.actions.filter(action => action.visible()).length > 0);

      return this.showing;
   }

   get moreActions(): AssemblyActionGroup[] {
      const actions = ToolbarActionsHandler.getMoreActions(this.actions.toolbarActions,
         this.allowedActionsNum());
      ToolbarActionsHandler.copyActions(actions, this.more);

      return this.more;
   }
}
