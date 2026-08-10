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
import { Component, ElementRef, HostListener, Input, OnChanges, OnDestroy, SimpleChanges } from "@angular/core";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { GuiTool } from "../../../common/util/gui-tool";
import { AbstractVSActions } from "../../action/abstract-vs-actions";
import { ContextProvider } from "../../context-provider.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { NavigationKeys } from "../navigation-keys";
import { AssemblyAction } from "../../../common/action/assembly-action";
import { Observable, Subscription } from "rxjs";
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
    styleUrls: ["mini-toolbar.component.scss"],
    imports: []
})
export class MiniToolbar implements OnChanges, OnDestroy {
   @Input() actions: AbstractVSActions<any>;
   @Input() miniToolbarActions: AssemblyActionGroup[];
   @Input() top: number;
   @Input() left: number;
   @Input() width: number;
   // Overrides the CSS z-index constant on .mini-toolbar. The toolbar is a sibling of the
   // assembly's own ".vs-object-parent-container", whose z-index is server-assigned and can be
   // arbitrarily large (e.g. embedded-viewsheet or max-mode assemblies), so a fixed CSS z-index
   // can end up lower than the assembly's own content and be painted underneath it.
   @Input() zIndex: number = null;
   // Not read directly -- its only purpose is to give ngOnChanges a signal to refresh
   // displayActions when maxMode toggles, since maxMode is set by mutating the shared vsObject
   // model in place (see viewer-app/vs-viewsheet onMaxModeChanged), which doesn't change the
   // `actions` input's object identity and so wouldn't otherwise be observed here.
   @Input() maxMode: boolean = false;
   @Input() assembly: string;
   @Input() forceAbove: boolean = false;
   // Set by the host when the strip is positioned inside the assembly rather than floating above it.
   // The host has already resolved the anchored top/left, so this component must not adjust them.
   @Input() anchorInTitleLane: boolean = false;
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
   displayActions: AssemblyActionGroup[] = [];
   mobileDevice: boolean = GuiTool.isMobileDevice();
   private focusedGroupIndex: number = -1;
   private focusedActionIndex: number = -1;
   private focused: boolean = false;
   private subscription: Subscription;
   private focusedElement: any;

   constructor(private contextProvider: ContextProvider,
               private element: ElementRef,
               private miniToolbarService: MiniToolbarService,
               private popComponentService: PopComponentService) {
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes["actions"] || changes["miniToolbarActions"] || changes["width"] ||
         changes["maxMode"])
      {
         this.displayActions = this.getActions();
      }
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

   /**
    * The action-button groups to render inside the mobile-guarded @for in .mini-toolbar-container.
    *
    * Not anchored: returns displayActions untouched, so template output is byte-identical to
    * before this method existed — any pre-existing overflow kebab (from width alone, on any
    * assembly type) keeps rendering inline as the last button in its group, exactly as before.
    *
    * Anchored (gate on, the anchored set only — see AbstractVSActions.resident): the resident
    * kebab is always the trailing action of the last group, appended by
    * AbstractVSActions.showingActions.
    * Trimming it off here, rather than filtering displayActions itself, keeps every remaining
    * button at the same (i, j) it already had, so isFocused()/getNextAction()/getPreviousAction()
    * — all of which read indices against getActions(), not this getter — need no changes.
    */
   get actionButtonGroups(): AssemblyActionGroup[] {
      return this.anchorInTitleLane ? this.kebabSplit.groups : this.displayActions;
   }

   /**
    * The resident kebab action. Rendered as its own button group inside .mini-toolbar-container,
    * alongside the action-button groups but outside their @if (!mobileDevice) guard, so it
    * inherits the container's flex layout, right-alignment, pinned 24px height and button chrome
    * (border/background overrides) instead of Bootstrap's bare btn-sm metrics, and so touch
    * (which never renders the action-button groups) still gets a route to the toolbar. Null when
    * not anchored, or when displayActions doesn't end in the "more actions" action (e.g. below
    * the 32px control floor, where AbstractVSActions.showingActions suppresses all chrome).
    */
   get kebabAction(): AssemblyAction {
      return this.anchorInTitleLane ? this.kebabSplit.kebab : null;
   }

   /**
    * Whether .mini-toolbar-container should render at all.
    *
    * Not anchored: unchanged from before this task — the container renders whenever the device
    * isn't mobile, regardless of content, exactly as it always has.
    *
    * Anchored: below the 32px control floor, AbstractVSActions.showingActions suppresses every
    * action (actionButtonGroups is empty) and there is no kebab, so without this guard the
    * container would still render as an empty bordered, backgrounded pill once the assembly-hover
    * reveal set its opacity to 1 — exactly the rung the fit ladder says should have no chrome.
    */
   get showToolbarContainer(): boolean {
      if(!this.anchorInTitleLane) {
         return !this.mobileDevice;
      }

      return (this.actionButtonGroups && this.actionButtonGroups.length > 0) || !!this.kebabAction;
   }

   private get kebabSplit(): { groups: AssemblyActionGroup[], kebab: AssemblyAction } {
      const groups = this.displayActions;
      const lastIndex = groups ? groups.length - 1 : -1;
      const lastGroup = lastIndex >= 0 ? groups[lastIndex] : null;
      const lastActions = lastGroup && lastGroup.actions;

      if(!lastActions || lastActions.length === 0) {
         return { groups, kebab: null };
      }

      const lastAction = lastActions[lastActions.length - 1];

      if(lastAction.id() !== "more actions") {
         return { groups, kebab: null };
      }

      const trimmedGroup = new AssemblyActionGroup(
         lastActions.slice(0, -1), lastGroup.label, lastGroup.icon);
      const trimmedGroups = [...groups.slice(0, lastIndex), trimmedGroup];

      return { groups: trimmedGroups, kebab: lastAction };
   }

   /**
    * Whether the resident kebab occupies the group/action slot current keyboard focus points at.
    * getNextAction()/getPreviousAction() set focusedGroupIndex/focusedActionIndex against
    * getActions() (== displayActions), where the kebab is still the trailing action of the last
    * group, so its slot there — not any index in the trimmed actionButtonGroups — is what the
    * separately-rendered kebab button must check.
    */
   isKebabFocused(): boolean {
      if(!this.kebabAction || !this.displayActions || this.displayActions.length === 0) {
         return false;
      }

      const lastIndex = this.displayActions.length - 1;
      const lastGroup = this.displayActions[lastIndex];

      return this.isFocused(lastIndex, lastGroup.actions.length - 1);
   }

   get binding(): boolean {
      return this.contextProvider.binding;
   }

   get alignLeft(): boolean {
      const width = this.miniToolbarService.getActionsWidth(this.displayActions);
      return this.left + this.width - width < 0;
   }

   get miniToolbarHeight(): number {
      return GuiTool.getMiniToolbarHeight();
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
      const toolbar = this.element.nativeElement.querySelector(".mini-toolbar");

      if(window.getComputedStyle(toolbar).visibility == "hidden") {
         return;
      }

      // Anchored strips are visibility: visible at rest (see mini-toolbar.component.scss), so the
      // check above never trips for them and every Esc keyup — closing an unrelated dialog,
      // leaving a selection — would otherwise silently dismiss this chart's strip regardless of
      // whether it was ever actually shown to the user. What "revealed" means for an anchored strip
      // is whether the action groups are in layout: they are display: none at rest and inline-flex
      // on hover/focus-within. No action group at all (touch, or the kebab-only height band) counts
      // as not revealed — there is nothing there for Esc to dismiss but the resting kebab.
      if(this.anchorInTitleLane) {
         const group = toolbar.querySelector(
            ".mini-toolbar-button-group:not(.mini-toolbar-kebab-group)");

         if(!group || window.getComputedStyle(group).display == "none") {
            return;
         }
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
      if(this.isPopComponent) {
         return Number.NaN;
      }

      // Anchored: the host placed us inside the assembly's title lane, so there is no height to
      // subtract and no viewport clamping to do — an anchored strip cannot leave the assembly.
      if(this.anchorInTitleLane) {
         return this.top;
      }

      // don't cover resize handle in composer
      const adj = this.contextProvider.composer && !this.contextProvider.vsWizard ? 3 : 0;
      const minTop = 20;
      return this.top > minTop || this.forceAbove ? this.top - this.miniToolbarHeight - adj
        : this.top;
   }

   get isPopComponent(): boolean {
      return this.popComponentService.isPopComponentShow(this.assembly);
   }
}
