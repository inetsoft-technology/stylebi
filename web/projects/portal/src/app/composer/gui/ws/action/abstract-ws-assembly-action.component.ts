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
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { FeatureFlagsService } from "../../../../../../../shared/feature-flags/feature-flags.service";

import { WSAssemblyActions } from "./ws-assembly.actions";
import { WSAssembly } from "../../../data/ws/ws-assembly";
import { WSTableActions } from "./ws-table.actions";
import { Worksheet } from "../../../data/ws/worksheet";
import { ModelService } from "../../../../widget/services/model.service";
import { AbstractTableAssembly } from "../../../data/ws/abstract-table-assembly";
import { WSVariableActions } from "./ws-variable.actions";
import { WSVariableAssembly } from "../../../data/ws/ws-variable-assembly";
import { WSGroupingActions } from "./ws-grouping.actions";
import { WSGroupingAssembly } from "../../../data/ws/ws-grouping-assembly";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";

export abstract class AbstractWSAssemblyActionComponent {
   public actions: WSAssemblyActions;
   public abstract assembly: WSAssembly;
   public abstract worksheet: Worksheet;
   protected abstract modelService: ModelService;
   protected abstract modalService: DialogService;
   protected abstract ngbModal: NgbModal;
   protected sqlEnabled = true;
   protected freeFormSqlEnabled = true;
   protected abstract featureFlagsService: FeatureFlagsService;

   public abstract onCut: EventEmitter<WSAssembly>;
   public abstract onCopy: EventEmitter<WSAssembly>;
   public abstract onRemove: EventEmitter<WSAssembly>;
   public abstract onSelectDependent: EventEmitter<void>;

   private subscriptions: Subscription[] = [];

   protected updateActions() {
      this.unsubscribeAll();

      switch(this.assembly.classType){
         case "TableAssembly":
            this.actions = new WSTableActions(
               this.assembly as AbstractTableAssembly, this.worksheet,
               this.modalService, this.ngbModal, this.modelService, this.sqlEnabled,
               this.freeFormSqlEnabled, this.featureFlagsService);
            break;
         case "VariableAssembly":
            this.actions = new WSVariableActions(
               this.assembly as WSVariableAssembly, this.worksheet,
               this.modalService, this.modelService, this.featureFlagsService);
            break;
         case "GroupingAssembly":
            this.actions = new WSGroupingActions(
               this.assembly as WSGroupingAssembly, this.worksheet,
               this.modalService, this.modelService, this.featureFlagsService);
            break;
         default:
            console.error("Cannot determine assembly actions");
      }

      if(this.actions) {
         this.subscriptions.concat([
            this.actions.onCut.subscribe((a) => this.onCut.emit(a)),
            this.actions.onCopy.subscribe((a) => this.onCopy.emit(a)),
            this.actions.onRemove.subscribe((a) => this.onRemove.emit(a)),
            this.actions.onSelectDependent.subscribe(() => this.onSelectDependent.emit())
         ]);
      }
   }

   private unsubscribeAll() {
      this.subscriptions.forEach((sub) => {
         if(!sub.closed) {
            sub.unsubscribe();
         }
      });

      this.subscriptions = [];
   }
}
