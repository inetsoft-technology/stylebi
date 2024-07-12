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
   ChangeDetectionStrategy,
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   forwardRef,
   Inject,
   Input,
   NgZone,
   OnDestroy,
   Optional,
   Output
} from "@angular/core";
import { Subscription } from "rxjs";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { DropdownOptions } from "./dropdown-options";
import { DropdownRef } from "./fixed-dropdown-ref";
import { FixedDropdownService } from "./fixed-dropdown.service";
import { AssemblyAction } from "../../common/action/assembly-action";

@Component({
   selector: "actions-contextmenu",
   templateUrl: "actions-contextmenu.component.html",
   styleUrls: ["actions-contextmenu.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class ActionsContextmenuComponent implements OnDestroy {
   @Input() sourceEvent: MouseEvent | TouchEvent;
   @Input() forceTab: boolean = false;
   @Input() set focused(value: any) {
      // When this component is created dynamically,
      // need to manually mark for change detection.
      this.changeDetectorRef.markForCheck();
      this.focusedGroup = value.group;
      this.focusedAction = value.action;
   }
   @Input() actionVisible: (action: AssemblyAction) => boolean;
   @Output() onClose = new EventEmitter<void>();
   visibleActions: AssemblyActionGroup[] = [];
   instance: ActionsContextmenuComponent;
   dropdownRef: DropdownRef;
   private _actions: AssemblyActionGroup[];
   private focusedGroup: number = -1;
   private focusedAction: number = -1;
   private childSubscription: Subscription;

   @Input() set actions(actions: AssemblyActionGroup[]) {
      this._actions = actions;

      if(actions != null) {
         this.visibleActions = actions.filter((group) => group && group.getVisible(this.actionVisible));
      }
      else {
         this.visibleActions = [];
      }
   }

   get actions(): AssemblyActionGroup[] {
      return this._actions;
   }

   constructor(public changeDetectorRef: ChangeDetectorRef,
      @Optional() @Inject(forwardRef(() => FixedDropdownService)) private dropdownService: FixedDropdownService,
               private zone: NgZone)
   {
   }

   ngOnDestroy(): void {
      if(this.childSubscription) {
         this.childSubscription.unsubscribe();
         this.childSubscription = null;
      }
   }

   onClick(action: any) {
      action.action(this.sourceEvent);
      this.onClose.emit();
   }

   /**
    * Check if the action should be focused on.
    */
   isFocused(group: number, action: number): boolean {
      return group == this.focusedGroup && action == this.focusedAction;
   }

   oozOpenChild(e: MouseEvent, action: any) {
      e.preventDefault();
      e.stopPropagation();
      let hasChild = action.childAction;
      let closed = false;

      if(this.dropdownRef) {
         closed = this.closeDescendants(<ActionsContextmenuComponent>this.instance);
      }

      let opened = false;

      if(hasChild) {
         let target = e.composedPath()[0] || e.target;
         let elem: ElementRef = new ElementRef(target);
         let top = this.getElementAbsoluteTop(elem);
         let left = this.getElementAbsoluteLeft(elem);

         let options: DropdownOptions = {
            position: {x: left, y: top},
            contextmenu: true,
            closeOnWindowResize: true,
         };

         if(!this.dropdownRef && !this.instance) {
            this.dropdownRef  = this.dropdownService.open(ActionsContextmenuComponent, options);
            opened = true;
            this.instance = this.dropdownRef.componentInstance;
            this.instance.sourceEvent = e;
            this.instance.actions = action.childAction();
            this.childSubscription = this.instance.onClose.subscribe(
              (event) => this.closeAll());
        }
      }

      if(closed || opened) {
         // Calling NgZone#run instead of ChangeDetectorRef#detectChanges because the newly attached or detached child
         // is not a child of this view, so we need to call this to refresh from the root.
         this.zone.run(() => {});
      }
   }

   closeSelf() {
      this.onClose.emit();
   }

   getElementAbsoluteTop(elem: ElementRef) {
      let nativeElement = elem.nativeElement;
      let nativeElementC = nativeElement;
      let top = 0;

      while(nativeElementC) {
         top += nativeElementC.offsetTop;
         nativeElementC = nativeElementC.offsetParent;
      }

      return top;
   }

   getElementAbsoluteLeft(elem: ElementRef) {
      let nativeElement = elem.nativeElement;
      let nativeElementC = nativeElement;
      let width = nativeElement.offsetWidth;
      let left = 0;

      while(nativeElementC) {
        left += nativeElementC.offsetLeft;
        nativeElementC = nativeElementC.offsetParent;
      }

      return left += width;
   }

   /**
    * Return true if closed, false otherwise.
    */
   public closeChild(): boolean {
      if(this.dropdownRef) {
         const closed = this.dropdownRef.close();
         this.dropdownRef = null;
         this.instance = null;

         return closed;
      }

      return false;
   }

   closeAll(){
      this.closeChild();
      this.onClose.emit();
   }

   /**
    * Return true if any descendants were closed, false otherwise.
    */
   closeDescendants(context: ActionsContextmenuComponent): boolean {
      let closed = false;

      while(context) {
        closed = context.closeChild() || closed;
        context = context.instance;
      }

      closed = this.closeChild() || closed;
      return closed;
   }

   itemVisible(action: AssemblyAction): boolean {
      if(this.actionVisible) {
         return action.action && action.visible() && this.actionVisible(action);
      }

      return action.action && action.visible();
   }
}
