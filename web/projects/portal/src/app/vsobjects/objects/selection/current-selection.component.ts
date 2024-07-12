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
import { Component, ElementRef, EventEmitter, Input, OnDestroy, Output, Renderer2 } from "@angular/core";
import { Subscription } from "rxjs";
import { ViewsheetClientService } from "../../../common/viewsheet-client/index";
import { AssemblyActionEvent } from "../../../common/action/assembly-action-event";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { CurrentSelectionActions } from "../../action/current-selection-actions";
import { ContextProvider } from "../../context-provider.service";
import { VSFormatModel } from "../../model/vs-format-model";
import { VSObjectModel } from "../../model/vs-object-model";
import { VSObjectEvent } from "../../event/vs-object-event";
import {
   OuterSelection,
   VSSelectionContainerModel
} from "../../model/vs-selection-container-model";
import { GuiTool } from "../../../common/util/gui-tool";

const URI_UPDATE_TITLE_RATIO: string = "/events/composer/viewsheet/currentSelection/titleRatio/";

@Component({
   selector: "current-selection",
   templateUrl: "current-selection.component.html",
   styleUrls: ["current-selection.component.scss"]
})
export class CurrentSelection implements OnDestroy {
   @Input() titleHeight: number;
   @Input() titleFormat: VSFormatModel;
   @Input() titleRatio: number = 1;
   @Input() selection: OuterSelection;
   @Input() model: VSSelectionContainerModel = null;
   @Output() onAssemblyActionEvent: EventEmitter<AssemblyActionEvent<VSObjectModel>> =
      new EventEmitter<AssemblyActionEvent<VSObjectModel>>();
   private _actions: CurrentSelectionActions;
   private actionSubscription: Subscription;
   editState: boolean = false;
   private outsideClickListener;
   mobileDevice: boolean = GuiTool.isMobileDevice();

   constructor(private elementRef: ElementRef,
               private renderer: Renderer2,
               private context: ContextProvider,
               protected viewsheetClient: ViewsheetClientService)
   {
   }

   @Input()
   set actions(value: CurrentSelectionActions) {
      if(this.actionSubscription) {
         this.actionSubscription.unsubscribe();
         this.actionSubscription = null;
      }

      this._actions = value;

      if(value) {
         this.actionSubscription = value.onAssemblyActionEvent.subscribe((event) => {
            switch(event.id) {
            case "current-selection unselect":
               this.onUnselect();
               break;
            default:
               this.onAssemblyActionEvent.emit(event);
            }
         });
      }
   }

   get actions(): CurrentSelectionActions {
      return this._actions;
   }

   @Input()
   set container(name: string) {
      this.elementRef.nativeElement.setAttribute("data-container", name);
   }

   get viewer(): boolean {
      return this.context.viewer || this.context.preview;
   }

   ngOnDestroy(): void {
      if(this.actionSubscription) {
         this.actionSubscription.unsubscribe();
         this.actionSubscription = null;
      }
   }

   onUnselect(): void {
      this.viewsheetClient.sendEvent(
         "/events/currentSelection/unSelectChild/" + this.selection.name);
   }

   getActions(): AssemblyActionGroup[] {
      return this._actions ? this._actions.toolbarActions : [];
   }

   updateEditState(isEditing: boolean) {
      this.editState = isEditing;

      this.outsideClickListener = this.renderer.listen("document", "click",
         (event: MouseEvent) => {
            if(!this.elementRef.nativeElement.contains(event.target)) {
               this.editState = false;

               // remove the listener
               this.outsideClickListener();
               this.outsideClickListener = null;
            }
         });
   }

   dragStart(event: any): void {
      if(this.editState) {
         event.stopPropagation();
         event.preventDefault();
      }
   }

   title2ResizeMove(event: any): void {
      const width: number = event.rect.width;
      const objwidth: number = this.model.objectFormat.width;
      this.model.titleRatio = (objwidth - width) / objwidth;
   }

   title2ResizeEnd(): void {
      this.updateTitleRatio(this.model.titleRatio);
   }

   updateTitleRatio(ratio: number): void {
      let event: VSObjectEvent = new VSObjectEvent(this.model.absoluteName);
      this.viewsheetClient.sendEvent(URI_UPDATE_TITLE_RATIO + ratio, event);
   }
}
