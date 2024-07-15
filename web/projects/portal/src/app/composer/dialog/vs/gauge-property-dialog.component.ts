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
   EventEmitter,
   Input,
   OnInit,
   Output,
   ViewChild
} from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { TrapInfo } from "../../../common/data/trap-info";
import { UIContextService } from "../../../common/services/ui-context.service";
import { VSTrapService } from "../../../vsobjects/util/vs-trap.service";
import { ScriptPaneTreeModel } from "../../../widget/dialog/script-pane/script-pane-tree-model";
import { GaugePropertyDialogModel } from "../../data/vs/gauge-property-dialog-model";
import { PropertyDialogService } from "../../../vsobjects/util/property-dialog.service";
import { PropertyDialog } from "./property-dialog.component";

const CHECK_TRAP_URI: string = "../api/composer/vs/gauge-property-dialog-model/checkTrap/";

@Component({
   selector: "gauge-property-dialog",
   templateUrl: "gauge-property-dialog.component.html",
})
export class GaugePropertyDialog extends PropertyDialog implements OnInit {
   @Input() model: GaugePropertyDialogModel;
   @Input() scriptTreeModel: ScriptPaneTreeModel;
   @Input() linkUri: string;
   @ViewChild("okButton") okButton: ElementRef;
   form: UntypedFormGroup;
   generalTab: string = "gauge-property-dialog-general-tab";
   scriptTab: string = "gauge-property-dialog-script-tab";
   formValid = () => this.model && this.form && this.form.valid;

   public constructor(protected uiContextService: UIContextService,
                      protected trapService: VSTrapService,
                      protected propertyDialogService: PropertyDialogService)
   {
      super(uiContextService, trapService, propertyDialogService);
   }

   ngOnInit(): void {
      super.ngOnInit();
      this.form = new UntypedFormGroup({
         gaugeGeneralPaneForm: new UntypedFormGroup({})
      });
   }

   get defaultTab(): string {
      return this.openToScript ? this.scriptTab
         : this.uiContextService.getDefaultTab("gauge-property-dialog", this.generalTab);
   }

   set defaultTab(tab: string) {
      this.uiContextService.setDefaultTab("gauge-property-dialog", tab);
   }

   checkTrap(isApply: boolean, collapse: boolean = false): void {
      const trapInfo = new TrapInfo(CHECK_TRAP_URI, this.assemblyName, this.runtimeId,
         this.model);
      const payload = {collapse: collapse, result: this.model};

      this.trapService.checkTrap(
         trapInfo,
         () => isApply ? this.onApply.emit(payload) : this.onCommit.emit(this.model),
         () => {},
         () => isApply ? this.onApply.emit(payload) : this.onCommit.emit(this.model),
      );
   }

   isBulletGraphGauge(): boolean {
      return this.model.gaugeGeneralPaneModel.facePaneModel.face === 90820;
   }

   getNumRangesToDisplay(): number {
      return this.isBulletGraphGauge() ? 3 : 5;
   }

   protected closing(isApply: boolean, collapse: boolean = false) {
      this.checkTrap(isApply, collapse);
   }

   protected getScripts(): string[] {
      return [this.model.vsAssemblyScriptPaneModel.expression];
   }
}
