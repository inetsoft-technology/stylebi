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
   Input,
   OnInit,
   EventEmitter,
   Output,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ValueMode } from "../../dynamic-combo-box/dynamic-combo-box-model";
import { Tool } from "../../../../../../shared/util/tool";
import { StrategyInfo } from "../../target/target-info";

@Component({
   selector: "computation-combo-box",
   templateUrl: "computation-combo-box.component.html",
})
export class ComputationComboBox implements OnInit {
   public ValueMode = ValueMode;
   @Input() model: StrategyInfo;
   @Input() variables: string[] = [];
   @Input() hideDcombox: boolean = false;
   @Input() vsId: string = null;
   @Output() modelChange: EventEmitter<StrategyInfo> = new EventEmitter<StrategyInfo>();
   selectedIndex: number;
   computationList: any[] = [
      { name: "Confidence Interval", label: "_#(js:Confidence Interval)", value: "95" },
      { name: "Percentage", label: "_#(js:Percentage)", value: "80" },
      { name: "Percentiles", label: "_#(js:Percentiles)", value: "50" },
      { name: "Quantiles", label: "_#(js:Quantiles)", value: "4" },
      { name: "Standard Deviation", label: "_#(js:Standard Deviation)", value: "-1,1" },
   ];
   dialogModel: StrategyInfo;
   @ViewChild("computationDialog") computationDialog: TemplateRef<any>;

   constructor(private modalService: NgbModal) {
   }

   ngOnInit() {
      this.updateList();
   }

   // keeps the list in sync with the model
   updateList() {
      let counter: number = 0;
      for(let item of this.computationList) {
         if(item.name == this.model.name) {
            this.computationList[counter].value = this.model.value;
            this.selectedIndex = counter;
            break;
         }

         counter++;
      }
   }

   // keeps the model in sync with the list
   updateModel(value: string) {
      // we have to force the value which is a string to a number since selectedIndex
      // is expected to be a number for comparison reasons.
      this.selectedIndex = Number(value);
      this.model.label = this.computationList[this.selectedIndex].label;
      this.model.name = this.computationList[this.selectedIndex].name;
      this.model.value = this.computationList[this.selectedIndex].value;
   }

   editComputation(): void {
      this.dialogModel = Tool.clone(this.model);
      this.modalService.open(this.computationDialog, {backdrop: "static"}).result.then(
         (result: StrategyInfo) => {
            this.model = result;
            this.updateList();
            this.modelChange.emit(this.model);
         },
         (reason: String) => {
            //Cancel
         }
      );
   }
}
