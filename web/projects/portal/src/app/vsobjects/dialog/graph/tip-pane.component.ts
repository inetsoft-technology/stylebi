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
   ElementRef,
   Input,
   OnDestroy,
   Optional,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { UIContextService } from "../../../common/services/ui-context.service";
import { Tool } from "../../../../../../shared/util/tool";
import { CheckCycleDependencyService } from "../../../composer/gui/check-cycle-dependency.service";
import { TipCustomizeDialogModel } from "../../../widget/dialog/tip-customize-dialog/tip-customize-dialog-model";
import { TipPaneModel } from "../../model/tip-pane-model";
import { DataTipDependencyCheckResult } from "./data-tip-dependency-check-result";
import { ComponentTool } from "../../../common/util/component-tool";

@Component({
   selector: "tip-pane",
   templateUrl: "tip-pane.component.html",
   styleUrls: ["tip-pane.component.scss"]
})
export class TipPane implements OnDestroy {
   @Input() model: TipPaneModel;
   @Input() tooltipOnly: boolean = false;
   @Input() hideTip: boolean = false;

   @ViewChild("tipCustomizeDialog") tipCustomizeDialog: TemplateRef<any>;
   dialogModel: TipCustomizeDialogModel;
   alphaInvalid: boolean = false;
   addRemoveSub: Subscription = null;
   objectAddRemoved: boolean = false;

   constructor(@Optional() private checkCycleDependencyService: CheckCycleDependencyService,
               private uiContextService: UIContextService,
               private modalService: NgbModal,
               private elem: ElementRef)
   {
      this.addRemoveSub = uiContextService.getObjectChange().subscribe(msg => {
         if((msg.action == "delete" || msg.action == "rename") &&
            this.model.flyoverComponents.indexOf(msg.objectId) >= 0)
         {
            this.objectAddRemoved = true;
         }
      });
   }

   ngOnDestroy() {
      this.addRemoveSub.unsubscribe();
   }

   editTip(): void {
      this.dialogModel = Tool.clone(this.model.tipCustomizeDialogModel);
      this.modalService.open(this.tipCustomizeDialog).result.then(
         (result: TipCustomizeDialogModel) => {
            this.model.tipCustomizeDialogModel = result;
         },
         (reason: String) => {
            //Do nothing if cancel
         }
      );
   }

   updateFlyOverView(component: string, checked: boolean): void {
      if(checked) {
         this.model.flyOverViews.push(component);

         if(this.model.tipOption && this.model.tipView === component) {
            this.model.tipOption = false;
            this.model.tipView = null;
         }
      }
      else {
         let index = this.model.flyOverViews.indexOf(component);

         if(index != -1) {
            this.model.flyOverViews.splice(index, 1);
         }
      }
   }

   dataTipChanged(): void {
      if(this.model.tipView == "null") {
         this.model.tipView = null;
      }

      if(this.model.tipOption && this.model.flyOverViews) {
         this.model.flyOverViews = this.model.flyOverViews.filter((flyover) => {
            return flyover !== this.model.tipView;
         });
      }

      if(this.model.tipView != null && this.model.tipOption != false
         && this.checkCycleDependencyService != null) {
         this.checkCycleDependencyService.checkCycleDependency(this.model.tipView)
            .subscribe((data: DataTipDependencyCheckResult) => {
               if(data.cycle){
                  //Blur the select element to address Angular bug in
                  // https://github.com/ng-bootstrap/ng-bootstrap/issues/1252
                  this.elem.nativeElement.querySelector("select.form-control").blur();
                  ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", data.message, {"ok": "_#(js:OK)"},
                     {backdrop: false});
                  this.model.tipView = null;
               }
            });
      }
   }

   selectAll(): void {
      for(let component of this.model.flyoverComponents) {
         if(this.model.flyOverViews.indexOf(component) == -1) {
            this.model.flyOverViews.push(component);
         }
      }

      if(this.model.flyOverViews.find(f => f == this.model.tipView)) {
         this.model.tipView = null;
      }
   }

   clearAll(): void {
      this.model.flyOverViews = [];
   }

   changeAlphaWarning(event) {
      this.alphaInvalid = event;
   }
}
