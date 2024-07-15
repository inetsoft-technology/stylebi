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
import { Type } from "@angular/core";
import { Tool } from "../../../../../../../shared/util/tool";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { SlideOutOptions } from "../../../../widget/slide-out/slide-out-options";
import { VSObjectModel } from "../../../../vsobjects/model/vs-object-model";
import { ComponentTool } from "../../../../common/util/component-tool";
import { ContextProvider } from "../../../../vsobjects/context-provider.service";

export abstract class AbstractActionHandler {
   constructor(protected modalService: DialogService,
               protected context: ContextProvider) {
   }

   /**
    * Shows a modal dialog.
    *
    * @param dialogType    the type of the dialog content component.
    * @param options       the modal options.
    * @param onCommit      the handler for the on commit event.
    * @param onCancel      the handler for the on cancel event.
    * @param commitEmitter the name of the emitter of the on commit event.
    * @param cancelEmitter the name of the emitter of the on cancel event.
    *
    * @returns the dialog content component.
    */
   public showDialog<D>(dialogType: Type<D>, options: SlideOutOptions = {},
                        onCommit: (value: any) => any,
                        onCancel: (value: any) => any = () => {},
                        commitEmitter: string = "onCommit",
                        cancelEmitter: string = "onCancel"): D
   {
      // viewer may be embedded in iframe, and also often shown on mobile, slide out pane
      // is not very easy to use on those media
      if(options.popup == null && this.context && (this.context.viewer || this.context.preview)) {
         options.popup = true;
      }

      return ComponentTool.showDialog(this.modalService, dialogType, onCommit, options,
         onCancel, commitEmitter, cancelEmitter);
   }

   // get slide out title for dialog
   protected getTitle(model: VSObjectModel, dialogName: string): string {
      return ComponentTool.getAssemblySlideOutTitle(model.absoluteName, dialogName);
   }
}
