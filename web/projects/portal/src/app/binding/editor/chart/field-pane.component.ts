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
   Input,
   HostListener
} from "@angular/core";
import { DragEvent } from "../../../common/data/drag-event";
import { ChartRef } from "../../../common/data/chart-ref";
import { ChartEditorService } from "../../services/chart/chart-editor.service";
import { BindingDropTarget } from "../../../common/data/dnd-transfer";
import { BindingService } from "../../services/binding.service";
import { DndService } from "../../../common/dnd/dnd.service";
import { GraphTypes } from "../../../common/graph-types";
import { ChartBindingModel } from "../../data/chart/chart-binding-model";
import { Tool } from "../../../../../../shared/util/tool";
import { ObjectType } from "../../../common/data/dnd-transfer";

@Component({
   selector: "field-pane",
   templateUrl: "field-pane.component.html",
   styleUrls: ["./aesthetic/aesthetic-field-mc.scss", "../data-editor.component.scss"]
})
export class FieldPane {
   @Input() field: ChartRef;
   @Input() fieldType: string;
   @Input() displayLabel: string;
   @Input() binding: ChartBindingModel;
   @Input() grayedOutValues: string[] = [];
   @Input() isPrimaryField: boolean = false;

   constructor(private editorService: ChartEditorService,
               protected bindingService: BindingService,
               private dndService: DndService)
   {
   }

   get assemblyName() {
      return Tool.byteEncode(this.bindingService.assemblyName, false);
   }

   get objectType() {
      return Tool.byteEncode(this.bindingService.objectType).toLowerCase();
   }

   dragOver(event: DragEvent): void {
      event.preventDefault();
      this.dndService.setDragOverStyle(event, this.isDropPaneAccept());
   }

   private isDropPaneAccept(): boolean {
      return this.editorService.isDropPaneAccept(this.dndService, this.binding, this.fieldType);
   }

   @HostListener("drop", ["$event"])
   public onDrop(event: DragEvent): void {
      event.preventDefault();

      if(!this.isDropPaneAccept()) {
         return;
      }

      let dropType: number = this.editorService.getDNDType(this.fieldType);

      if(dropType < 0) {
         return;
      }

      let dtarget: BindingDropTarget = new BindingDropTarget(
         dropType + "", 0, false, <ObjectType> this.objectType, this.assemblyName);
      this.dndService.processOnDrop(event, dtarget);
   }

   convert(event: any): void {
      this.editorService.convert(event.name, event.type, this.binding);
   }
}
