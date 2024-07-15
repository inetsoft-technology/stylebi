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
import { Directive, Injector, Input, OnDestroy } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { VSObjectModel } from "../../vsobjects/model/vs-object-model";
import { ViewsheetClientService } from "../../common/viewsheet-client/viewsheet-client.service";
import { VSUtil } from "../../vsobjects/util/vs-util";
import { ModelService } from "../../widget/services/model.service";
import { AssemblyActionEvent } from "../../common/action/assembly-action-event";
import { VSCrosstabModel } from "../../vsobjects/model/vs-crosstab-model";
import { CrosstabActions } from "../../vsobjects/action/crosstab-actions";
import { VSCrosstabActionHandler } from "../../vsobjects/binding/vs-crosstab-action-handler";
import { DialogService } from "../../widget/slide-out/dialog-service.service";
import { ContextProvider } from "../../vsobjects/context-provider.service";

@Directive({
   selector: "[bCrosstabActionHandler]"
})
export class BCrosstabActionHandlerDirective implements OnDestroy {
   @Input() model: VSCrosstabModel;
   private subscription: Subscription;
   private actionHandler: VSCrosstabActionHandler;

   @Input()
   set actions(value: CrosstabActions) {
      this.unsubscribe();

      if(value) {
         this.subscription = value.onAssemblyActionEvent.subscribe(
            (event) => this.handleEvent(event));
      }
   }

   constructor(private viewsheetClient: ViewsheetClientService,
               private modalService: DialogService, private modelService: ModelService,
               private injector: Injector,
               protected context: ContextProvider) {
      this.actionHandler = new VSCrosstabActionHandler(
         modelService, viewsheetClient, modalService, injector, context);
   }

   ngOnDestroy(): void {
      this.unsubscribe();
   }

   private unsubscribe() {
      if(this.subscription) {
         this.subscription.unsubscribe();
         this.subscription = null;
      }
   }

   private handleEvent(event: AssemblyActionEvent<VSCrosstabModel>): void {
      let vsObjects: VSObjectModel[] = [];
      vsObjects[0] = this.model;

      this.actionHandler.handleEvent(event,
         VSUtil.getVariableList(vsObjects, this.model.absoluteName));
   }

}
