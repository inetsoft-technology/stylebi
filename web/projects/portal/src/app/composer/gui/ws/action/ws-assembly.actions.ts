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
import { Type } from "@angular/core";
import { Subject } from "rxjs";
import {
   FeatureFlagsService,
   FeatureFlagValue
} from "../../../../../../../shared/feature-flags/feature-flags.service";
import { AssemblyActionGroup } from "../../../../common/action/assembly-action-group";
import { ComponentTool } from "../../../../common/util/component-tool";

import { AssemblyActions } from "../../../../vsobjects/action/assembly-actions";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { SlideOutOptions } from "../../../../widget/slide-out/slide-out-options";
import { Worksheet } from "../../../data/ws/worksheet";
import { WSAssembly } from "../../../data/ws/ws-assembly";
import { WSAssemblyEvent } from "../socket/ws-assembly-event";

const SET_PRIMARY_SOCKET_URI = "/events/composer/worksheet/set-primary";
const UPDATE_OUTER_MIRROR_SOCKET_URI = "/events/composer/worksheet/update-mirror";

export class WSAssemblyActions extends AssemblyActions<WSAssembly> {
   private assemblyToolbarActions: AssemblyActionGroup[];
   private assemblyMenuActions: AssemblyActionGroup[];

   public onCut: Subject<WSAssembly> = new Subject<WSAssembly>();
   public onCopy: Subject<WSAssembly> = new Subject<WSAssembly>();
   public onRemove: Subject<WSAssembly> = new Subject<WSAssembly>();
   public onSelectDependent: Subject<void> = new Subject<void>();

   constructor(protected assembly: WSAssembly, protected worksheet: Worksheet,
               protected modalService: DialogService,
               protected featureFlagsService: FeatureFlagsService)
   {
      super();
   }

   public get toolbarActions(): AssemblyActionGroup[] {
      if(!this.assemblyToolbarActions) {
         this.assemblyToolbarActions = this.createToolbarActions([]);
      }

      return this.assemblyToolbarActions;
   }

   public get menuActions(): AssemblyActionGroup[] {
      if(!this.assemblyMenuActions) {
         this.assemblyMenuActions = this.createMenuActions([]);
      }

      return this.assemblyMenuActions;
   }

   /**
    * Creates the menuActions for this type of assembly.
    */
   protected createMenuActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      groups.push(
         new AssemblyActionGroup([{
            id: () => "worksheet assembly select-dependent",
            label: () => "_#(js:Select Dependent)",
            icon: () => "",
            enabled: () => true,
            visible: () => true,
            action: () => this.selectDependent()
         }
         ])
      );

      groups.push(
         new AssemblyActionGroup([
            {
               id: () => "worksheet assembly cut",
               label: () => "_#(js:Cut)",
               icon: () => "fa fa-cut",
               enabled: () => true,
               visible: () => true,
               action: () => this.cut()
            },
            {
               id: () => "worksheet assembly copy",
               label: () => "_#(js:Copy)",
               icon: () => "fa fa-copy",
               enabled: () => true,
               visible: () => true,
               action: () => this.copy()
            },
            {
               id: () => "worksheet assembly remove",
               label: () => "_#(js:Remove)",
               icon: () => "fa fa-trash",
               enabled: () => true,
               visible: () => true,
               action: () => this.remove()
            }
         ]));

      groups.push(new AssemblyActionGroup([
         {
            id: () => "worksheet assembly set-primary",
            label: () => "_#(js:Set as Primary)",
            icon: () => "fa fa-star",
            enabled: () => true,
            visible: () => true,
            action: () => this.setAsPrimary()
         }
      ]));

      return groups;
   }

   /**
    * Creates the toolbar actions for this type of assembly.
    */
   protected createToolbarActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      return groups;
   }

   protected updateMirrorVisible() {
      return !!this.assembly.info.mirrorInfo && this.assembly.info.mirrorInfo.outerMirror
         && !this.assembly.info.mirrorInfo.autoUpdate;
   }

   protected updateMirror() {
      const event = new WSAssemblyEvent();
      event.setAssemblyName(this.assembly.name);
      this.worksheet.socketConnection.sendEvent(UPDATE_OUTER_MIRROR_SOCKET_URI, event);
   }

   /**
    * Shows a modal dialog.
    *
    * @param dialogType    the type of the dialog content component.
    * @param options       the modal options.
    * @param onCommit      the handler for the on commit event.
    * @param onCancel      the handler for the on cancel event.
    * @param commitEmitter the name of the emitter of the on commit event.
    * @param cancelEmitter the name of the emitter of the on cancel event.
    *
    * @returns the dialog content component.
    */
   protected showDialog<D>(dialogType: Type<D>, options: SlideOutOptions = {},
                           onCommit: (value: any) => any = () => {},
                           onCancel: (value: any) => any = () => {},
                           commitEmitter: string = "onCommit",
                           cancelEmitter: string = "onCancel"): D
   {
      return ComponentTool.showDialog(this.modalService, dialogType, onCommit, options,
         onCancel, commitEmitter, cancelEmitter);
   }

   private setAsPrimary() {
      let event = new WSAssemblyEvent();
      event.setAssemblyName(this.assembly.name);
      this.worksheet.socketConnection.sendEvent(SET_PRIMARY_SOCKET_URI, event);
   }

   private cut() {
      this.onCut.next(this.assembly);
   }

   private copy() {
      this.onCopy.next(this.assembly);
   }

   private remove() {
      this.onRemove.next(this.assembly);
   }

   private selectDependent() {
      this.onSelectDependent.next();
   }
}
