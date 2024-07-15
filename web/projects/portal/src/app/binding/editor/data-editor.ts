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
import { ChangeDetectorRef, ElementRef, Input, QueryList, Directive } from "@angular/core";
import { BindingDropTarget, ObjectType } from "../../common/data/dnd-transfer";
import { DragEvent } from "../../common/data/drag-event";
import { DndDropOption, DndService } from "../../common/dnd/dnd.service";
import { BindingService } from "../services/binding.service";

@Directive()
export abstract class DataEditor {
   @Input() grayedOutValues: string[] = [];
   activeIdx: number = -1;
   replaceField: boolean = false;
   private inCnt: number = 0;

   constructor(protected dservice: DndService,
               protected bindingService: BindingService,
               protected changeRef: ChangeDetectorRef)
   {
   }

   get assemblyName(): string {
      return this.bindingService.assemblyName;
   }

   get objectType(): string {
      return this.bindingService.objectType.toLowerCase();
   }

   protected clearActiveIdx(): void {
      this.activeIdx = -1;
      this.replaceField = false;
   }

   /**
    * Clear the activeIdx when dragleave the data editor.
    */
   public dragLeave(event: DragEvent): void {
      this.inCnt--;

      if(this.inCnt == 0) {
         this.clearActiveIdx();
      }
   }

   /**
    * Update the activeIdx if dragover to insert or replace columns.
    */
   public dragOverField(event: DragEvent, idx: number, replaceField: boolean): void {
      event.stopPropagation();
      event.preventDefault();
      this.inCnt++;
      const dropAccept = this.isDropAccept();

      if(dropAccept) {
         if(this.activeIdx != idx || this.replaceField != replaceField) {
            this.activeIdx = idx;
            this.replaceField = replaceField;
            this.changeRef.detectChanges();
         }
      }

      this.dservice.setDragOverStyle(event, dropAccept);
   }

   public onDrop(event: DragEvent, option?: DndDropOption): void {
      this.inCnt--; // drop will not generate a leave event
      event.preventDefault();

      if(!this.checkDropValid() || this.activeIdx == -1) {
         return;
      }

      let dtarget: BindingDropTarget = new BindingDropTarget(
         this.getDropType(), this.activeIdx, this.replaceField, <ObjectType> this.objectType,
         this.assemblyName);
      this.dservice.processOnDrop(event, dtarget, option);
      this.clearActiveIdx();
   }

   protected abstract getDropType(): string;
   protected abstract isDropAccept(): boolean;
   protected abstract checkDropValid(): boolean;
   protected abstract getFieldComponents(): QueryList<ElementRef>;
}
