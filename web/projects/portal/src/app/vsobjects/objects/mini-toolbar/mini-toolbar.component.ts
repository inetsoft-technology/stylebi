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
import { Component, ElementRef, HostListener, Input, OnDestroy } from "@angular/core";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { GuiTool } from "../../../common/util/gui-tool";
import { AbstractVSActions } from "../../action/abstract-vs-actions";
import { ContextProvider } from "../../context-provider.service";
import { NavigationKeys } from "../navigation-keys";
import { AssemblyAction } from "../../../common/action/assembly-action";
import { Observable ,  Subscription } from "rxjs";
import { VSObjectModel } from "../../model/vs-object-model";
import { MiniToolbarService } from "./mini-toolbar.service";
import { FocusObjectEventModel } from "../../model/focus-object-event-model";
import { ToolbarActionsHandler } from "../../toolbar-actions-handler";

/**
 * Mini-toolbar usage: (see vs-calendar.copmonent.html)
 * 1. Include mini-toolbar in the vsobject html.
 *   a. Make sure class 'mini-toolbar' is on the mini-toolbar tag.
 * 2. Add vs-object to the class of the top div of the vsobject.
 * 3. Implement getActions() in the vsobject class.
 */

@Component({
   selector: "mini-toolbar",
   templateUrl: "mini-toolbar.component.html",
   styleUrls: ["mini-toolbar.component.scss"]
})
export class MiniToolbar implements OnDestroy {
   @Input() actions: AbstractVSActions<any>;
   @Input() miniToolbarActions: AssemblyActionGroup[];
   @Input() top: number;
   @Input() left: number;
   @Input() width: number;
   @Input() assembly: string;
   @Input() forceAbove: boolean = false;
   @Input() visible: boolean = true;
   @Input() forceHide: boolean = false;
   @Input() set forceShow(value: boolean) {
      this.focused = value;

      if(value) {
         this.focusedGroupIndex = 0;
         this.focusedActionIndex = -1;
         this.getNextAction();
      }
      else {
         this.focusedGroupIndex = -1;
         this.focusedActionIndex = -1;
         this.focusedElement = null;
      }
   }
   @Input() set keyNavigation(
      observable: Observable<FocusObjectEventModel>)
   {
      if(!!observable) {
         this.subscription = observable
            .subscribe((data: { focused: VSObjectModel, key: NavigationKeys }) => {
               if(data && data.focused && this.assembly == data.focused.absoluteName &&
                  this.focusedGroupIndex > -1 && this.focusedActionIndex > -1)
               {
                  if(this.focused && (data.key == NavigationKeys.LEFT ||
                        data.key == NavigationKeys.RIGHT ||
                        data.key == NavigationKeys.SPACE))
                  {
                     this.navigate(data.key);
                  }
               }
            });
      }
   }
   mobileDevice: boolean = GuiTool.isMobileDevice();
   private focusedGroupIndex: number = -1;
   private focusedActionIndex: number = -1;
   private focused: boolean = false;
   private subscription: Subscription;
   private focusedElement: any;

   constructor(private contextProvider: ContextProvider,
               private element: ElementRef,
               private miniToolbarService: MiniToolbarService) {
   }

   ngOnDestroy() {
      if(this.subscription) {
         this.subscription.unsubscribe();
      }
   }

   getActions(): AssemblyActionGroup[] {
      return this.actions ? this.actions.showingActions :
         this.miniToolbarActions ? this.miniToolbarActions : [];
   }

   get binding(): boolean {
      return this.contextProvider.binding;
   }

   get alignLeft(): boolean {
      const width = this.miniToolbarService.getActionsWidth(this.getActions());
      return this.left + this.width - width < 0;
   }

   get miniToolbarHeight(): number {
      return GuiTool.MINI_TOOLBAR_HEIGHT;
   }

   /**
    * Navigate between the toolbar actions.
    * @param {NavigationKeys} key
    */
   navigate(key: NavigationKeys): void {
      if(key == NavigationKeys.LEFT) {
         this.getPreviousAction();
      }
      else if(key == NavigationKeys.RIGHT) {
         this.getNextAction();
      }
      else if(key == NavigationKeys.SPACE) {
         this.getActions()[this.focusedGroupIndex]
            .actions[this.focusedActionIndex].action(null);
      }
   }

   /**
    * Retrieve the visible action before the current selected one.
    */
   private getPreviousAction(): void {
      const actions: AssemblyActionGroup[] = this.getActions();

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
                     this.focusPreviousItem();
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
      const actions: AssemblyActionGroup[] = this.getActions();
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
                     this.focusNextItem();
                     return;
                  }
               }
            }
         }
      }
   }

   private focusNextItem(): void {
      if(!this.focusedElement || !this.focusedElement.nextElementSibling) {
         setTimeout(() => {
            this.focusedElement =
               this.element.nativeElement.querySelector(".bd-selected-cell");
            this.focusedElement.focus();
         });
      }
      else {
         this.focusedElement = this.focusedElement.nextElementSibling;
         this.focusedElement.focus();
      }
   }

   private focusPreviousItem(): void {
      if(!this.focusedElement || !this.focusedElement.previousElementSibling) {
         setTimeout(() => {
            this.focusedElement =
               this.element.nativeElement.querySelector(".bd-selected-cell");
            this.focusedElement.focus();
         });
      }
      else {
         this.focusedElement = this.focusedElement.previousElementSibling;
      }
   }

   doAction(action: AssemblyAction, event: MouseEvent): void {
      event.stopPropagation();
      action.action(event);
   }

   @HostListener("window:keyup.esc", [])
   onKeyUp() {
      if(window.getComputedStyle(this.element.nativeElement.querySelector(".mini-toolbar")).visibility == "hidden") {
         return;
      }

      this.miniToolbarService.hideMiniToolbar(this.assembly, true);
   }

   /**
    * Check if the action should be focused on.
    * @param {number} group
    * @param {number} action
    * @returns {boolean}
    */
   isFocused(group: number, action: number): boolean {
      return group == this.focusedGroupIndex && action == this.focusedActionIndex;
   }

   get topY(): number {
      // don't cover resize handle in composer
      const adj = this.contextProvider.composer && !this.contextProvider.vsWizard ? 3 : 0;
      const minTop = 20;
      return this.top > minTop || this.forceAbove ? this.top - this.miniToolbarHeight - adj
        : this.top;
   }
}
