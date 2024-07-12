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
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnDestroy,
   Output,
   SimpleChanges
} from "@angular/core";
import { AssemblyActionEvent } from "../../../../common/action/assembly-action-event";
import { AssemblyActionFactory } from "../../../../vsobjects/action/assembly-action-factory.service";
import { VSObjectModel } from "../../../../vsobjects/model/vs-object-model";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { AbstractActionComponent } from "./abstract-action-component";

@Component({
   selector: "assembly-context-menu-items",
   templateUrl: "assembly-context-menu-items.component.html"
})
export class AssemblyContextMenuItemsComponent
   extends AbstractActionComponent implements OnChanges, OnDestroy
{
   @Input() model: VSObjectModel;
   @Input() viewsheet: Viewsheet;
   @Output() onAssemblyActionEvent: EventEmitter<AssemblyActionEvent<VSObjectModel>> =
      new EventEmitter<AssemblyActionEvent<VSObjectModel>>();

   constructor(actionFactory: AssemblyActionFactory) {
      super(actionFactory);
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.hasOwnProperty("model")) {
         super.updateActions(this.model, this.viewsheet);
      }
   }

   ngOnDestroy(): void {
      super.unsubscribeAll();
   }
}