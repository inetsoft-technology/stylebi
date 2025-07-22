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
import { Input, NgZone } from "@angular/core";
import { Subscription } from "rxjs";
import { RemoveVSObjectCommand } from "../../vsobjects/command/remove-vs-object-command";
import { ViewsheetClientService } from "./viewsheet-client.service";
import { ViewsheetCommandMessage } from "./viewsheet-command-message";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Tool } from "../../../../../shared/util/tool";
import { MessageCommand } from "../viewsheet-client/message-command";
import { ComponentTool } from "../util/component-tool";

/**
 * Base class for components that process commands received by the viewsheet client
 * service. In order to process a type of command, the sub-class must implement a
 * processing method with the signature
 * <tt>processViewsheetCommandSubclass(command: ViewsheetCommandSubclass): void</tt>.
 */
export abstract class CommandProcessor {
   private subscription: Subscription;
   private timestampMap: Map<string, number>;

   constructor(viewsheetClientService: ViewsheetClientService,
               zone: NgZone,
               handleGlobal: boolean = false)
   {
      this.subscription = viewsheetClientService && viewsheetClientService.commands ?
         this.dispatchCommands(viewsheetClientService, zone, handleGlobal) : null;
      this.timestampMap = new Map<string, number>();
   }

   protected cleanup() {
      if(this.subscription && !this.subscription.closed) {
         this.subscription.unsubscribe();
      }
   }

   /**
    * Gets the absolute name of the assembly.
    */
   abstract getAssemblyName(): string;

   // check if command should be executed in zone
   protected isInZone(messageType: string): boolean {
      return true;
   }

   /**
    * Handles dispatching commands from the client to the command processing methods of
    * this class.
    *
    * @param viewsheetClientService the service that produces the commands to be
    *                               processed.
    * @param zone                   the angular zone
    * @param handleGlobal           flag that indicates if global commands should be
    *                               processed or assembly-specific commands.
    */
   private dispatchCommands(viewsheetClientService: ViewsheetClientService,
                            zone: NgZone, handleGlobal: boolean): Subscription
   {
      return viewsheetClientService.commands.subscribe(
         (message: ViewsheetCommandMessage) => {
            if(handleGlobal && !message.assembly ||
               !handleGlobal && this.getAssemblyName() &&
               (this.getAssemblyName() === message.assembly ||
               message.type == "RemoveVSObjectCommand" &&
               (message.command as RemoveVSObjectCommand).name.startsWith(this.getAssemblyName() + "")))
            {
               const method: string = "process" + message.type;

               if(this[method]) {
                  if(this.isWizardExpiredCommand(message)) {
                     return;
                  }

                  if(this.isInZone(message.type)) {
                     zone.run(() => {
                        this[method].call(this, message.command);
                     });
                  }
                  else {
                     this[method].call(this, message.command);
                  }
               }
               else if(handleGlobal && !message.assembly) {
   //               console.warn(
   //                  "Unhandled global command [" + message.type + "]: ", message.command);
               }
               else if(!handleGlobal && this.getAssemblyName() &&
                       this.getAssemblyName() === message.assembly)
               {
   //               console.warn(
   //                  "Unhandled command [" + message.type + "] in assembly [" +
   //                  message.assembly + "]: ", message.command);
               }
            }
         }
      );
   }

   protected isWizardExpiredCommand(message: any): boolean {
      if(!message || !message.command || !message.command.wizard) {
         return false;
      }

      let wizardcommand = message.command;

      if(wizardcommand.timestamp === undefined) {
         return false;
      }

      if(this.timestampMap.get(message.type) == null) {
         this.timestampMap.set(message.type, wizardcommand.timestamp);
      }

      return wizardcommand.timestamp < this.timestampMap.get(message.type);
   }

   protected processProgress(command: MessageCommand): void {
   }

   protected processMessageCommand0(command: MessageCommand, modalService: NgbModal,
                                    viewsheetClient: ViewsheetClientService) {
      let title = "";
      let isConfirm: boolean;
      let isOverride: boolean;

      switch(command.type) {
      case "CONFIRM":
         title = "_#(js:Confirm)";
         isConfirm = true;
         break;
      case "PROGRESS":
         this.processProgress(command);
         return;
       case "OVERRIDE":
          title = ComponentTool.getDialogTitle(command.type);
          isOverride = true;
          break;
      default:
         title = ComponentTool.getDialogTitle(command.type);
      }

      let message = Tool.getLimitedMessage(command.message);

      if(message != "") {
         if(isConfirm) {
            const buttons = {"ok": "_#(js:Yes)", "cancel": "_#(js:No)"};
            ComponentTool.showConfirmDialog(modalService, title, message, buttons)
               .then((result: string) => {
                  if(result === "ok") {
                     // process confirm
                     for(let key in command.events) {
                        if(command.events.hasOwnProperty(key)) {
                           let evt: any = command.events[key];
                           evt.confirmed = true;
                           viewsheetClient.sendEvent(key, evt);
                        }
                     }
                  }
                  else if(result === "cancel" && !!command.noEvents) {
                     for(let key in command.noEvents) {
                        if(command.noEvents.hasOwnProperty(key)) {
                           let evt: any = command.noEvents[key];
                           evt.confirmed = true;
                           viewsheetClient.sendEvent(key, evt);
                        }
                     }
                  }
               });
         }
         else if(isOverride) {
            ComponentTool.showMessageDialog(modalService, title, message,
               {"yes": "_#(js:Yes)", "no": "_#(js:No)"});
         }
         else {
            ComponentTool.showMessageDialog(modalService, title, message);
         }
      }
   }
}
