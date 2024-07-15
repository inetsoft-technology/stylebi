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
import { Directive, Injector, Input, OnDestroy } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { VSObjectModel } from "../../vsobjects/model/vs-object-model";
import { VSTableModel } from "../../vsobjects/model/vs-table-model";
import { VSTableActionHandler } from "../../vsobjects/binding/vs-table-action-handler";
import { TableActions } from "../../vsobjects/action/table-actions";
import { ViewsheetClientService } from "../../common/viewsheet-client/viewsheet-client.service";
import { VSUtil } from "../../vsobjects/util/vs-util";
import { ModelService } from "../../widget/services/model.service";
import { AssemblyActionEvent } from "../../common/action/assembly-action-event";
import { DialogService } from "../../widget/slide-out/dialog-service.service";
import { ContextProvider } from "../../vsobjects/context-provider.service";

@Directive({
   selector: "[bTableActionHandler]"
})
export class BTableActionHandlerDirective implements OnDestroy {
   @Input() model: VSTableModel;
   private subscription: Subscription;
   private actionHandler: VSTableActionHandler;

   @Input()
   set actions(value: TableActions) {
      this.unsubscribe();

      if(value) {
         this.subscription = value.onAssemblyActionEvent.subscribe(
            (event) => this.handleEvent(event));
      }
   }

   constructor(private viewsheetClient: ViewsheetClientService,
               private modalService: DialogService, private modelService: ModelService,
               private injector: Injector, protected context: ContextProvider) {
      this.actionHandler = new VSTableActionHandler(
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

   private handleEvent(event: AssemblyActionEvent<VSTableModel>): void {
      let vsObjects: VSObjectModel[] = [];
      vsObjects[0] = this.model;

      this.actionHandler.handleEvent(event,
         VSUtil.getVariableList(vsObjects, this.model.absoluteName));
   }

}
