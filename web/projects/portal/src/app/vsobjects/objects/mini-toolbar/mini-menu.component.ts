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
import { Component, EventEmitter, Input, OnDestroy, Output } from "@angular/core";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { DropdownOptions } from "../../../widget/fixed-dropdown/dropdown-options";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { ActionsContextmenuComponent } from "../../../widget/fixed-dropdown/actions-contextmenu.component";
import { NavigationKeys } from "../navigation-keys";
import { VSObjectModel } from "../../model/vs-object-model";
import { Observable, Subscription } from "rxjs";
import { DropdownRef } from "../../../widget/fixed-dropdown/fixed-dropdown-ref";
import { AssemblyAction } from "../../../common/action/assembly-action";
import { FocusObjectEventModel } from "../../model/focus-object-event-model";

@Component({
   selector: "mini-menu",
   templateUrl: "mini-menu.component.html",
   styleUrls: ["mini-menu.component.scss"]
})
export class MiniMenu implements OnDestroy {
   @Input() actions: AssemblyActionGroup[];
   @Input() keyNav: boolean = false;
   @Input() keyNavigation: Observable<FocusObjectEventModel>;
   @Input() smallIcon: boolean = false;
   @Input() isVertical: boolean = false;
   @Output() onClose: EventEmitter<boolean> = new EventEmitter<boolean>();
   private focusedGroupIndex: number = -1;
   private focusedActionIndex: number = -1;
   private subscription: Subscription;
   private dropdown: DropdownRef;

   constructor(private dropdownService: FixedDropdownService) {
   }

   ngOnDestroy() {
      if(this.subscription) {
         this.subscription.unsubscribe();
      }
   }

   openMenu(event: MouseEvent) {
      let options: DropdownOptions = {
         position: {x: event.clientX + 1, y: event.clientY},
         contextmenu: true,
      };

      this.dropdown =
         this.dropdownService.open(ActionsContextmenuComponent, options);
      let contextmenu: ActionsContextmenuComponent = this.dropdown.componentInstance;
      contextmenu.sourceEvent = event;
      contextmenu.actions = this.actions;
      this.dropdown.closeEvent.subscribe(() => {
         this.onClose.emit(true);
      });

      // If keyboard nav, focus first item.
      if(this.keyNav) {
         this.navigate(NavigationKeys.DOWN);
      }

      if(!!this.keyNavigation) {
         this.subscription = this.keyNavigation
            .subscribe((data: { focused: VSObjectModel, key: NavigationKeys }) => {
               if(this.keyNav && !!this.dropdown) {
                  this.navigate(data.key);
               }
            });
      }
   }

   /**
    * Navigate between the toolbar actions.
    * @param {NavigationKeys} key
    */
   navigate(key: NavigationKeys): void {
      if(key == NavigationKeys.UP) {
         this.getPreviousAction();
         this.updateIndexes();
      }
      else if(key == NavigationKeys.DOWN) {
         this.getNextAction();
         this.updateIndexes();
      }
      else if(key == NavigationKeys.SPACE) {
         const event = this.dropdown.componentInstance.sourceEvent;
         this.actions[this.focusedGroupIndex]
            .actions[this.focusedActionIndex].action(event);
         this.focusedGroupIndex = -1;
         this.focusedActionIndex = -1;
         this.dropdown.close();
         this.onClose.emit(true);
         this.dropdown = null;

         if(this.subscription) {
            this.subscription.unsubscribe();
         }
      }
   }

   /**
    * Retrieve the visible action before the current selected one.
    */
   private getPreviousAction(): void {
      const actions: AssemblyActionGroup[] = this.actions;

      for(let i = this.focusedGroupIndex; i >= 0; i--) {
         if(actions[i].visible) {
            const length: number = actions[i].actions.length;

            for(let j = length - 1; j >= 0; j--) {
               const action: AssemblyAction = actions[i].actions[j];

               if(this.focusedGroupIndex != i ||
                  (this.focusedGroupIndex == i && j < this.focusedActionIndex))
               {
                  if(action.visible() && action.enabled()) {
                     this.focusedGroupIndex = i;
                     this.focusedActionIndex = j;
                     return;
                  }
               }
            }
         }
      }
   }

   /**
    * Retrieve the next visible action after the current selected one.
    */
   private getNextAction(): void {
      this.focusedGroupIndex = this.focusedGroupIndex == -1 ? 0 : this.focusedGroupIndex;
      const actions: AssemblyActionGroup[] = this.actions;
      const groupCount: number = actions.length;

      for(let i = this.focusedGroupIndex; i < groupCount; i++) {
         if(actions[i].visible) {
            const length: number = actions[i].actions.length;

            for(let j = 0; j < length; j++) {
               const action: AssemblyAction = actions[i].actions[j];

               if(this.focusedGroupIndex != i ||
                  (this.focusedGroupIndex == i && j > this.focusedActionIndex))
               {
                  if(action.visible() && action.enabled()) {
                     this.focusedGroupIndex = i;
                     this.focusedActionIndex = j;
                     return;
                  }
               }
            }
         }
      }
   }

   private updateIndexes(): void {
      if(this.dropdown && this.dropdown.componentInstance) {
         this.dropdown.componentInstance.focused = {
            group: this.focusedGroupIndex,
            action: this.focusedActionIndex
         };
      }
   }
}
