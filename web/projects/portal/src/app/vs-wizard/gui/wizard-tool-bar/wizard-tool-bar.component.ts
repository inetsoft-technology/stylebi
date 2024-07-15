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
   Component,
   EventEmitter,
   Input,
   OnDestroy,
   OnInit,
   Output,
} from "@angular/core";
import { NgbModal, NgbTooltipConfig } from "@ng-bootstrap/ng-bootstrap";
import { ContextProvider } from "../../../vsobjects/context-provider.service";
import { Viewsheet } from "../../../composer/data/vs/viewsheet";
import { ComponentTool } from "../../../common/util/component-tool";
import { ToolbarActionGroup } from "../../../widget/toolbar/toolbar-action-group";
import { ToolbarAction } from "../../../widget/toolbar/toolbar-action";

declare const window;

@Component({
   selector: "wizard-tool-bar",
   templateUrl: "wizard-tool-bar.component.html",
   styleUrls: ["wizard-tool-bar.component.scss",
      "../../../composer/gui/toolbar/composer-toolbar.component.scss"]
})
export class WizardToolBarComponent implements OnInit, OnDestroy {
   @Input() sheet: Viewsheet;
   @Input() hiddenNewBlock: boolean = false;
   @Output() onClose: EventEmitter<boolean> = new EventEmitter<boolean>();
   @Output() onHiddenNewBlockChanged = new EventEmitter<null>();
   editCollapsed: boolean = false;

   constructor(private context: ContextProvider,
               private modalService: NgbModal,
               private tooltipConfig: NgbTooltipConfig)
   {
      tooltipConfig.container = "body";
   }

   public ngOnInit(): void {
   }

   ngOnDestroy(): void {
   }

   get undoEnabled(): boolean {
      return this.sheet && this.sheet.current > 0 && !this.sheet.loading;
   }

   get redoEnabled(): boolean {
      return this.sheet && this.sheet.current < this.sheet.points - 1 && !this.sheet.loading;
   }

   isUndoRedoVisible() {
      return this.context.vsWizard;
   }

   redo() {
      this.sheet.socketConnection.sendEvent("/events/redo");
   }

   undo() {
      this.sheet.socketConnection.sendEvent("/events/undo");
   }

   get hiddenComposerIcon(): boolean {
      return window.innerWidth < 350;
   }

   get editOperations(): ToolbarActionGroup {
      return <ToolbarActionGroup> {
         label: "_#(js:Edit)",
         iconClass: "edit-icon",
         buttonClass: "edit-button",
         enabled: () => true,
         visible: () => true,
         action: () => {},
         actions: this.getEditActions
      };
   }

   get closeOperation(): ToolbarActionGroup {
      return <ToolbarActionGroup> {
         label: "_#(js:Close)",
         iconClass: "close-icon",
         buttonClass: "close-button",
         enabled: () => true,
         visible: () => true,
         action: () => {},
         actions: this.getCloseActions
      };
   }

   getEditActions: ToolbarAction[] =
      [
         {
            label: "_#(js:Undo)",
            iconClass: "undo-icon",
            buttonClass: "undo-button",
            tooltip: () => "<b>_#(js:composer.action.undoToolTip)</b>",
            enabled: () => this.undoEnabled,
            visible: () => this.isUndoRedoVisible(),
            action: () => this.undo()
         },
         {
            label: "_#(js:Redo)",
            iconClass: "redo-icon",
            buttonClass: "redo-button",
            tooltip: () => "<b>_#(js:composer.action.redoToolTip)</b>",
            enabled: () => this.redoEnabled,
            visible: () => this.isUndoRedoVisible(),
            action: () => this.redo()
         },
      ];

   getCloseActions: ToolbarAction[] =
      [
         {
            label: "_#(js:Continue)",
            iconClass: "submit-icon",
            buttonClass: "finish-button",
            tooltip: () => "<b>_#(js:composer.action.finishToolTip)</b>",
            enabled: () => true,
            visible: () => true,
            action: () => this.done()
         },
         {
            label: "_#(js:Cancel)",
            iconClass: "close-icon",
            buttonClass: "close-button",
            tooltip: () => "<b>_#(js:composer.action.cancelToolTip)</b>",
            enabled: () => true,
            visible: () => true,
            action: () => this.cancel()
         }
      ];

   done() {
      this.onClose.emit(true);
   }

   cancel() {
      if(this.sheet.vsObjects.length > 0) {
         const message = "_#(js:unsave.changes.message)";
         const options = {"yes": "_#(js:Yes)", "no": "_#(js:No)"};
         const title = "_#(js:Confirm)";
         ComponentTool.showMessageDialog(this.modalService, title, message, options)
            .then((buttonClicked) => {
               switch(buttonClicked) {
                  case "no":
                     return;
                  case "yes":
                  default:
                     this.onClose.emit(false);
                     break;
               }
            });
      }
      else {
         this.onClose.emit(false);
      }
   }

   hiddenNewBlockChanged(): void {
      this.onHiddenNewBlockChanged.emit();
   }
}
