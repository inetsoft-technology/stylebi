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
import { EventEmitter } from "@angular/core";
import { Subscription } from "rxjs";
import { AbstractVSActions } from "../../../../vsobjects/action/abstract-vs-actions";
import { VSObjectModel } from "../../../../vsobjects/model/vs-object-model";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { GroupVSObjectsEvent } from "../objects/event/group-vs-objects-event";
import { AssemblyActionFactory } from "../../../../vsobjects/action/assembly-action-factory.service";
import { AssemblyActionEvent } from "../../../../common/action/assembly-action-event";

/**
 * Base class for components that present assembly-specific actions.
 */
export abstract class AbstractActionComponent {
   actions: AbstractVSActions<any>;
   hasMenuAction: boolean = true;
   abstract onAssemblyActionEvent: EventEmitter<AssemblyActionEvent<VSObjectModel>>;
   private subscription: Subscription;

   constructor(private actionFactory: AssemblyActionFactory) {
   }

   protected updateActions(model: VSObjectModel, viewsheet: Viewsheet): void {
      this.unsubscribeAll();
      this.actions = null;

      if(model) {
         this.actions = this.actionFactory.createActions(model);
         this.hasMenuAction = false;

         if(this.actions) {
            this.subscription = this.actions.onAssemblyActionEvent.subscribe((event) => {
               if("vs-object group" === event.id) {
                  this.group(viewsheet);
               }
               else if("vs-object ungroup" === event.id) {
                  this.ungroup(event.model, viewsheet);
               }
               else {
                  this.onAssemblyActionEvent.emit(event);
               }
            });

            this.hasMenuAction = this.actions.menuActions.some(m => m.visible);
         }
      }
   }

   protected unsubscribeAll(): void {
      if(this.subscription) {
         this.subscription.unsubscribe();
         this.subscription = null;
      }
   }

   private group(vs: Viewsheet): void {
      const objects: string[] = vs.currentFocusedAssemblies.map((object) => object.absoluteName);
      const event: GroupVSObjectsEvent = new GroupVSObjectsEvent(objects);

      vs.newGroup = true;
      vs.socketConnection.sendEvent("/events/composer/viewsheet/group", event);
   }

   private ungroup(model: VSObjectModel, vs: Viewsheet): void {
      vs.socketConnection.sendEvent("/events/composer/viewsheet/ungroup/" + model.absoluteName);
   }
}
