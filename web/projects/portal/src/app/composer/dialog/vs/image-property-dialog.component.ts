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
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { Subscription } from "rxjs";
import { TrapInfo } from "../../../common/data/trap-info";
import { UIContextService } from "../../../common/services/ui-context.service";
import { VSTrapService } from "../../../vsobjects/util/vs-trap.service";
import { ScriptPaneTreeModel } from "../../../widget/dialog/script-pane/script-pane-tree-model";
import { ImagePropertyDialogModel } from "../../data/vs/image-property-dialog-model";
import { PropertyDialogService } from "../../../vsobjects/util/property-dialog.service";
import { PropertyDialog } from "./property-dialog.component";

const CHECK_TRAP_URI: string = "../api/composer/vs/image-property-dialog-model/checkTrap/";

@Component({
   selector: "image-property-dialog",
   templateUrl: "image-property-dialog.component.html",
})
export class ImagePropertyDialog extends PropertyDialog implements OnInit {
   @Input() model: ImagePropertyDialogModel;
   @Input() scriptTreeModel: ScriptPaneTreeModel;
   form: UntypedFormGroup;
   generalTab: string = "image-property-dialog-general-tab";
   scriptTab: string = "image-property-dialog-script-tab";
   formValid = () => this.form && this.form.valid || this.layoutObject;

   public constructor(protected uiContextService: UIContextService,
                      protected trapService: VSTrapService,
                      protected propertyDialogService: PropertyDialogService)
   {
      super(uiContextService, trapService, propertyDialogService);
   }

   ngOnInit(): void {
      super.ngOnInit();
      this.form = new UntypedFormGroup({
         imageGeneralPaneForm: new UntypedFormGroup({})
      });
   }

   public get selectedImage(): string {
      // get current image selected in preview pane
      const image = this.model.imageGeneralPaneModel.staticImagePaneModel
         .imagePreviewPaneModel.selectedImage;

      if(image) {
         return image;
      }
      else {
         return null;
      }
   }

   get defaultTab(): string {
      return this.openToScript ? this.scriptTab
         : this.uiContextService.getDefaultTab("image-property-dialog", this.generalTab);
   }

   set defaultTab(tab: string) {
      this.uiContextService.setDefaultTab("image-property-dialog", tab);
   }

   checkTrap(isApply: boolean, isCollapse: boolean = false): void {
      const trapInfo = new TrapInfo(CHECK_TRAP_URI, this.assemblyName, this.runtimeId,
         this.model);

      const payload = {collapse: isCollapse, result: this.model};

      this.trapService.checkTrap(
         trapInfo,
         () => isApply ? this.onApply.emit(payload) : this.onCommit.emit(this.model),
         () => {},
         () => isApply ? this.onApply.emit(payload) : this.onCommit.emit(this.model),
      );
   }

   protected closing(isApply: boolean, collapse: boolean = false) {
      this.checkTrap(isApply, collapse);
   }

   protected getScripts(): string[] {
      return [this.model.clickableScriptPaneModel.scriptExpression,
              this.model.clickableScriptPaneModel.onClickExpression];
   }
}
