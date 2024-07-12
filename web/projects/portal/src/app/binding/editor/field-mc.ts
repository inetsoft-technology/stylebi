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
import { OnInit, ViewChild, Directive } from "@angular/core";
import { BindingService } from "../services/binding.service";
import { Tool } from "../../../../../shared/util/tool";
import { UIContextService } from "../../common/services/ui-context.service";
import { FixedDropdownDirective } from "../../widget/fixed-dropdown/fixed-dropdown.directive";

@Directive()
export class FieldMC implements OnInit {
   constructor(public bindingService: BindingService,
               protected uiContextService: UIContextService) {
   }

   private obindingModel: string;
   @ViewChild(FixedDropdownDirective) dropdown: FixedDropdownDirective;

   ngOnInit() {
      this.bindingUpdated();
   }

   // should be called when binding is submitted
   protected bindingUpdated(): void {
      this.obindingModel = JSON.stringify(this.bindingService.bindingModel);
   }

   protected isBindingChanged(): boolean {
      return this.obindingModel != JSON.stringify(this.bindingService.bindingModel);
   }

   get vsId() {
      return this.bindingService.runtimeId;
   }

   get assemblyName() {
      return this.bindingService.assemblyName;
   }

   get objectType() {
      return this.bindingService.objectType.toLowerCase();
   }

   get variables(): string[] {
      return this.bindingService.variableValues;
   }

   /**
    * Check whether the value is dynaimcValue or not.
    */
   isEmptyDynamicValue(str: string): boolean {
      return !str || Tool.isDynamic(str) && str.length == 1;
   }

   isCube(): boolean {
      let bind = this.bindingService.bindingModel;

      if(bind != null && bind.source != null) {
         return bind.source.source.startsWith("___inetsoft_cube_");
      }

      return false;
   }

   isSqlServer(): boolean {
      let bind = this.bindingService.bindingModel;

      if(bind != null && bind.source != null) {
         return bind.source.sqlServer;
      }

      return false;
   }

   // abstract method
   get cellValue(): string {
      return "";
   }

   isNormalColumn(): boolean {
      return this.bindingService.bindingModel.availableFields?.some(ref => ref.view == this.cellValue);
   }

   isDynamicValue(): boolean {
      return !this.isNormalColumn && Tool.isDynamic(this.cellValue);
   }
}
