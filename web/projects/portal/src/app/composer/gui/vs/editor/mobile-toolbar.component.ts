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
   moduleId: module.id,
   selector: "mobile-toolbar",
   templateUrl: "mobile-toolbar.component.html"
})
export class MobileToolbarComponent
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