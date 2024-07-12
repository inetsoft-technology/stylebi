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
import { HttpErrorResponse } from "@angular/common/http";
import { NgbModal, NgbModalOptions, NgbModalRef } from "@ng-bootstrap/ng-bootstrap";
import { SlideOutRef } from "../../widget/slide-out/slide-out-ref";
import { SlideOutOptions } from "../../widget/slide-out/slide-out-options";
import { Observable, Subject, Subscription } from "rxjs";
import {
   DialogOption,
   MessageDialog
} from "../../widget/dialog/message-dialog/message-dialog.component";
import { FixedDropdownService } from "../../widget/fixed-dropdown/fixed-dropdown.service";
import { AssemblyActionGroup } from "../action/assembly-action-group";
import { DropdownOptions } from "../../widget/fixed-dropdown/dropdown-options";
import { ActionsContextmenuComponent } from "../../widget/fixed-dropdown/actions-contextmenu.component";
import { Point } from "../data/point";
import { Type } from "@angular/core";
import { Tool } from "../../../../../shared/util/tool";

/**
 * Common utility methods
 */
export namespace ComponentTool {
   export const MESSAGEDIALOG_MESSAGE_CONNECTION = "_*";

   export function showHttpError(message: string, error: HttpErrorResponse, modal: NgbModal): void {
      let msg: string;
      let err = error.error;

      try {
         if(typeof err == "string") {
            err = JSON.parse(err);
         }
      }
      catch(e) {
         ComponentTool.showMessageDialog(modal, "_#(js:Error)", err);

         return;
      }

      if(err.message) {
         // A client-side or network error occurred. Handle it accordingly.
         msg = `${message}: ${err.message}`;
      }
      else {
         msg = `${message} [${err.status}]: \n${err.error}`;
      }

      console.error(msg);
      ComponentTool.showMessageDialog(modal, "_#(js:Error)", msg);
   }

   /**
    * Shows a modal dialog.
    *
    * @param modalService  the modal service used to open the modal
    * @param dialogType    the type of the dialog content component.
    * @param options       the modal options.
    * @param onCommit      the handler for the on commit event.
    * @param onCancel      the handler for the on cancel event.
    * @param commitEmitter the name of the emitter of the on commit event.
    * @param cancelEmitter the name of the emitter of the on cancel event.
    *
    * @returns the dialog content component.
    */
   export function showDialog<D>(modalService: any,
                                 dialogType: Type<D>,
                                 onCommit: (value: any) => any,
                                 options: SlideOutOptions = {},
                                 onCancel: (value: any) => any = () => {},
                                 commitEmitter: string = "onCommit",
                                 cancelEmitter: string = "onCancel",
                                 applyEmitter: string = "onApply"): D
   {
      if(!options) {
         options = {};
      }

      const modal: NgbModalRef = modalService.open(dialogType, options);
      const slideout: SlideOutRef = <SlideOutRef> <any> modal;
      let commitSubscription: Subscription;
      let cancelSubscription: Subscription;
      let applySubscription: Subscription;

      commitSubscription = modal.componentInstance[commitEmitter].subscribe((v: any) => {
         commitSubscription.unsubscribe();
         cancelSubscription.unsubscribe();

         if(applySubscription) {
            applySubscription.unsubscribe();
         }

         if(options.objectId) {
            modalService.objectChange(options.objectId, slideout.title);
            slideout.changedByOthers = false;
         }

         // wait for next cycle to let blockMouse to receive the event before then
         // dialog is destroyed
         setTimeout(() => modal.close(v), 0);
      });

      cancelSubscription = modal.componentInstance[cancelEmitter].subscribe((v: any) => {
         commitSubscription.unsubscribe();
         cancelSubscription.unsubscribe();

         if(applySubscription) {
            applySubscription.unsubscribe();
         }

         setTimeout(() => modal.dismiss(v), 0);
      });

      if(modal.componentInstance[applyEmitter]) {
         applySubscription = modal.componentInstance[applyEmitter].subscribe((v: any) => {
            if(v.collapse != null && v.result != null) {
               if(v.collapse && (<any> modal).setExpanded) {
                  setTimeout(() => slideout.setExpanded(false), 0);
               }

               v = v.result;
            }

            if(options.objectId) {
               modalService.objectChange(options.objectId, slideout.title);
               slideout.changedByOthers = false;
            }

            onCommit(v);
         });
      }

      modal.result.then((v: any) => onCommit(v), (v: any) => onCancel(v));
      return modal.componentInstance;
   }

   /**
    * Shows a message dialog.
    *
    * @param modalService the modal service
    * @param title   the dialog title.
    * @param message the message text.
    * @param buttonOptions the button options, represented as a map of symbolic names to button
    *                labels. The symbolic name will be the value resolved in the returned
    *                promise. By default this is {"ok": "OK"}.
    * @param modalOptions the modal options.
    *
    * @returns a promise that resolves to the symbolic name of the button clicked by the
    *          user.
    */
   export function showMessageDialog(
      modalService: NgbModal,
      title: string,
      message: string,
      buttonOptions: {[key: string]: string} = {"ok": "_#(js:OK)"},
      modalOptions: NgbModalOptions = {backdrop: "static" },
      closeSubject: Subject<any> = null): Promise<any>
   {
      // ignore duplicate messages
      if(message == MessageDialog.lastMessage && MessageDialog.lastMessageTS > Date.now() - 500) {
         return Promise.reject("Duplicate message ignored");
      }

      MessageDialog.lastMessage = message;
      MessageDialog.lastMessageTS = Date.now();

      const modal: NgbModalRef = modalService.open(MessageDialog, modalOptions);
      const dialog: MessageDialog = <MessageDialog> modal.componentInstance;
      const options: DialogOption[] = [];

      for(let symbol in buttonOptions) {
         if(buttonOptions.hasOwnProperty(symbol)) {
            options.push(<DialogOption> {
               symbol: symbol,
               label: buttonOptions[symbol]
            });
         }
      }

      let messMix: string[] = !!message ?
         (message + "").split(MESSAGEDIALOG_MESSAGE_CONNECTION) : [];

      dialog.options = options;
      dialog.title = title;
      dialog.message = messMix[0];

      if(messMix.length > 1) {
         dialog.expandValues = messMix[1].split(",");
         dialog.expandValues = dialog.expandValues
            .filter(value => value || value === "")
            .map(value => Tool.byteDecode(value));
      }

      if(closeSubject) {
         dialog.showProgress = true;
         const closeSubscription = closeSubject.subscribe(() => {
            closeSubscription.unsubscribe();
            modal.close();
         });
      }

      const commitSubscription = modal.componentInstance["onCommit"]
         .subscribe((symbol: string) => {
            commitSubscription.unsubscribe();
            modal.close(symbol);
         });

      return modal.result;
   }

   /**
    * Convenience method to show a message dialog with OK/Cancel options. See the
    * documentation of showMessageDialog() for details.
    *
    * @param title   the dialog title.
    * @param message the message text.
    * @param buttonOptions the button options.
    * @param modalOptions the modal options.
    *
    * @returns a promise that resolves to the symbolic name of the button clicked by the
    *          user.
    */
   export function showConfirmDialog(
      modalService: NgbModal,
      title: string,
      message: string,
      buttonOptions: {[key: string]: string} = {"ok": "_#(js:OK)", "cancel": "_#(js:Cancel)"},
      modalOptions: NgbModalOptions = {backdrop: "static" }): Promise<any>
   {
      return showMessageDialog(modalService, title, message, buttonOptions, modalOptions);
   }

   export function showTrapAlert(
      modalService: NgbModal,
      undoable: boolean = true,
      msg: string = null,
      options: NgbModalOptions = {backdrop: "static" }): Promise<any>
   {
      let buttonOptions = undoable ? {"undo": "_#(js:Undo)", "continue": "_#(js:Keep and Continue)"} :
         {"yes": "_#(js:Yes)", "no": "_#(js:No)"};
      let message: string =
         (undoable ? "_#(js:designer.binding.trapFound)" : "_#(js:designer.binding.continueTrap)") +
         (msg ? "\n" + msg : "");

      return showMessageDialog(modalService, "_#(js:Trap)", message, buttonOptions, options);
   }

   /**
    * Show a warning that a date-type data source is being applied to a selection list
    * or a selection tree and it should be applied to a range slider.
    */
   export function showConfirmDateTypeBindingDialog(modalService: NgbModal): Promise<boolean>{
      const message = "_#(js:dateBindingSuggestion)";

      return showMessageDialog(modalService, "_#(js:Confirm)", message,
         {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
         .then((optionSelected) => {
            return Promise.resolve(optionSelected == "yes");
         });
   }

   /**
    * Show a warning that annotations on the page have unsaved changes
    *
    * @param {NgbModal} modalService the modal service used to show the dialog
    *
    * @returns {Promise<boolean>} promise that resolves to true if the user presses yes
    */
   export function showAnnotationChangedDialog(modalService: NgbModal): Promise<boolean>
   {
      const message = "_#(js:viewer.viewsheet.bookmark.modified)";
      return showMessageDialog(modalService, "_#(js:Confirm)", message, {
         "Yes": "_#(js:Yes)",
         "No": "_#(js:No)"
      }).then((value) => {
         return Promise.resolve(value === "Yes");
      });
   }

   export function checkModelTrap(
      subject: Observable<any>,
      modalService: NgbModal,
      undoable: boolean = true): Promise<any>
   {
      let promise: Promise<any> = Promise.resolve(null);

      subject.subscribe(res => {
         if(!!res.body) {
            promise = promise.then(() => ComponentTool.showTrapAlert(modalService)
               .then((result: string) =>
                  (undoable ? result === "undo" : result === "yes") ? res.body : null));
         }
      });

      return promise;
   }

   /**
    * Opens a context menu populated with the given actions.
    *
    * @param dropdownService the specified dropdown service
    * @param actions the context menu actions to display
    * @param contextmenuEvent the original context menu event
    * @param options dropdownOptions to use
    *
    * @returns the created contextmenu component
    */
   export function openActionsContextmenu(
      dropdownService: FixedDropdownService,
      actions: AssemblyActionGroup[],
      contextmenuEvent: MouseEvent,
      options?: DropdownOptions): ActionsContextmenuComponent
   {
      contextmenuEvent.preventDefault();
      contextmenuEvent.stopPropagation();

      const dropdownOptions: DropdownOptions = {
         position: new Point(contextmenuEvent.clientX, contextmenuEvent.clientY),
         contextmenu: true,
         autoClose: true,
         closeOnOutsideClick: true,
         closeOnWindowResize: true,
      };

      if(!!options) {
         Object.assign(dropdownOptions, options);
      }

      const contextmenu: ActionsContextmenuComponent = dropdownService
         .open(ActionsContextmenuComponent, dropdownOptions).componentInstance;
      contextmenu.actions = actions;
      contextmenu.sourceEvent = contextmenuEvent;
      return contextmenu;
   }


   /**
    * Get the title for the slide out pane for an assembly dialog.
    * @param {string} assemblyName  the assembly's name
    * @param {string} dialogName    the dialog name
    * @returns {string} the title for the slide out pane
    */
   export function getAssemblySlideOutTitle(assemblyName: string,
                                            dialogName: string): string
   {
      return assemblyName.substring(assemblyName.lastIndexOf("/") + 1) + " " + dialogName;
   }

   /**
    * Get title based on type of dialog.
    * @param {string} type  the dialog type.
    * @returns {string} the title for dialog.
    */
   export function getDialogTitle(type: string): string {
      let title = "";

      switch(type) {
      case "OK":
         title = "_#(js:Info)";
         break;
      case "INFO":
         title = "_#(js:Info)";
         break;
      case "WARNING":
         title = "_#(js:Warning)";
         break;
      case "ERROR":
         title = "_#(js:Error)";
         break;
      case "OVERRIDE":
         title = "_#(js:Override)";
         break;
      default:
         title = "_#(js:Info)";
      }

      return title;
   }
}
