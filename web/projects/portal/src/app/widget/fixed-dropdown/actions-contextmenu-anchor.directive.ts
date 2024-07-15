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
   Directive,
   ElementRef,
   EventEmitter,
   Input,
   OnDestroy,
   OnInit,
   Output,
   Renderer2
} from "@angular/core";
import { Subscription } from "rxjs";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { Point } from "../../common/data/point";
import { ActionsContextmenuComponent } from "./actions-contextmenu.component";
import { DropdownOptions } from "./dropdown-options";
import { DropdownRef } from "./fixed-dropdown-ref";
import { FixedDropdownService } from "./fixed-dropdown.service";

@Directive({
   selector: "[actionsContextmenuAnchor]"
})
export class ActionsContextmenuAnchorDirective implements OnInit, OnDestroy {
   @Input() position: Point = new Point();
   @Output() onContextmenuOpened = new EventEmitter<MouseEvent>();
   @Output() onContextmenuClosed = new EventEmitter<any>();
   private _actions: AssemblyActionGroup[];
   private dropdownRef: DropdownRef | null;
   private dropdownSubscription: Subscription = new Subscription();
   private childClickedSubscription: Subscription;
   private _enabled = true;
   private listener: () => void = null;

   @Input()
   set actions(actions: AssemblyActionGroup[]) {
      this._actions = actions;

      if(this.dropdownRef != null && !this.dropdownRef.closed) {
         const instance: ActionsContextmenuComponent = this.dropdownRef.componentInstance;
         instance.actions = actions;
         instance.changeDetectorRef.markForCheck();
      }
   }

   get actions(): AssemblyActionGroup[] {
      return this._actions;
   }

   @Input()
   set contextmenuEnabled(value: boolean) {
      if(this._enabled && !value) {
         this.removeListener();
      }
      else if(!this._enabled && value) {
         this.addListener();
      }

      this._enabled = value;
   }

   get contextmenuEnabled(): boolean {
      return this._enabled;
   }

   constructor(private dropdownService: FixedDropdownService,
               private renderer: Renderer2,
               private element: ElementRef)
   {
   }

   ngOnInit(): void {
      if(this._enabled) {
         this.addListener();
      }
      else {
         this.removeListener();
      }
   }

   ngOnDestroy(): void {
      this.close();
      this.removeListener();

      if(this.dropdownSubscription) {
         this.dropdownSubscription.unsubscribe();
         this.dropdownSubscription = null;
      }

      if(this.childClickedSubscription) {
         this.childClickedSubscription.unsubscribe();
         this.childClickedSubscription = null;
      }
   }

   private onContextmenu(e: MouseEvent) {
      e.preventDefault();
      e.stopPropagation();

      let options: DropdownOptions = {
         position: {x: e.clientX, y: e.clientY},
         contextmenu: true,
         closeOnWindowResize: true,
      };

      this.position.x = options.position.x;
      this.position.y = options.position.y;
      this.dropdownRef = this.dropdownService.open(ActionsContextmenuComponent, options);
      let instance: ActionsContextmenuComponent = this.dropdownRef.componentInstance;
      instance.sourceEvent = e;
      instance.actions = this.actions;
      this.dropdownSubscription.add(this.dropdownRef.closeEvent.subscribe(
         evt => this.onContextmenuClosed.emit(evt)));
      // event on clicking menu item
      this.dropdownSubscription.add(this.dropdownRef.componentInstance.onClose.subscribe(
         evt => this.onContextmenuClosed.emit(evt)));
      this.childClickedSubscription = instance.onClose.subscribe(
         (event) => this.close());
      this.onContextmenuOpened.emit(e);
   }

   close() {
      if(!!this.dropdownRef) {
         this.dropdownRef.close();
         this.dropdownRef = null;
      }
   }

   private addListener(): void {
      if(!this.listener && this.element.nativeElement) {
         this.listener = this.renderer.listen(
            this.element.nativeElement, "contextmenu", (event) => this.onContextmenu(event));
      }
   }

   private removeListener(): void {
      if(this.listener) {
         this.listener();
         this.listener = null;
      }
   }
}
