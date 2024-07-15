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
   AfterViewInit,
   Directive,
   ElementRef,
   EventEmitter,
   OnDestroy,
   SimpleChange
} from "@angular/core";
import { Notification } from "../../../../common/data/notification";
import { Point } from "../../../../common/data/point";
import { WSAssembly } from "../../../data/ws/ws-assembly";
import { AbstractWSAssemblyActionComponent } from "../action/abstract-ws-assembly-action.component";

let timer: any;
const timerFunctions: (() => void)[] = [];

@Directive()
export abstract class WSAssemblyThumbnail extends AbstractWSAssemblyActionComponent
   implements AfterViewInit, OnDestroy
{
   protected abstract thumbnail: ElementRef;
   abstract onRefreshAssembly: EventEmitter<[string, any]>;
   abstract onRegisterAssembly: EventEmitter<[WSAssembly, string]>;
   abstract onDragPasteAssemblies: EventEmitter<Point>;
   abstract onMoveAssemblies: EventEmitter<Point>;
   abstract onDestroy: EventEmitter<WSAssembly>;
   abstract onSetDraggable: EventEmitter<[any, any]>;
   abstract onStartEditName: EventEmitter<void>;
   abstract onEditName: EventEmitter<string>;
   abstract onNotify: EventEmitter<Notification>;
   assembly: WSAssembly;
   titleTooltip: string;
   readonly width = 150;
   protected destroyed: boolean = false; // true after ngOnDestroy lifecycle happens

   onChange(assemblyChange: SimpleChange): void {
      if(!assemblyChange) {
         return;
      }

      this.assembly = assemblyChange.currentValue;
      this.updateActions();
      this.updateTitleTooltip();

      if(!assemblyChange.isFirstChange()) {
         this.onRefreshAssembly.emit(
            [assemblyChange.previousValue.name, assemblyChange.currentValue]);
         this.queueUpdateDimensions();
      }
   }

   ngAfterViewInit(): void {
      this.jspInit();
      this.onRegisterAssembly.emit([this.assembly, this.thumbnail.nativeElement.id]);
      this.queueUpdateDimensions();
   }

   ngOnDestroy(): void {
      this.destroyed = true;
      this.onDestroy.emit(this.assembly);
   }

   startEditName(): void {
      this.onStartEditName.emit();
   }

   editName(newName: string): void {
      this.onEditName.emit(newName);
   }

   notify(notification: Notification): void {
      this.onNotify.emit(notification);
   }

   private queueUpdateDimensions(): void {
      timerFunctions.push(() => {
         if(!this.destroyed && !!this.thumbnail &&
            this.thumbnail.nativeElement.offsetParent != null)
         {
            const rect = this.thumbnail.nativeElement.getBoundingClientRect();
            this.assembly.width = rect.width;
            this.assembly.height = rect.height;
         }
      });

      if(timer == null) {
         timer = setTimeout(() => {
            timer = null;
            timerFunctions.forEach((fn) => fn());
            timerFunctions.splice(0, timerFunctions.length);
         });
      }
   }

   private jspInit(): void {
      this.setDraggable();
   }

   /** Set thumbnails as draggable and have them update assembly pos on drag end. */
   private setDraggable(): void {
      // Drag happens for all selected draggables, so dragLeader allows to
      // discriminate on the main one
      let dragLeader: boolean = false;

      const updatePos = (params: any) => {
         if(dragLeader) {
            const x: number = params.pos[0];
            const y: number = params.pos[1];
            const offsetTop = y - this.assembly.top;
            const offsetLeft = x - this.assembly.left;
            const offset = new Point(offsetLeft, offsetTop);

            if(offsetTop !== 0 || offsetLeft !== 0) {
               this.onMoveAssemblies.emit(offset);
            }

            dragLeader = false;
         }
      };

      const checkDragLeader = (params: any) => {
         const event: MouseEvent = params.e;

         // If params has mouse event e, then this assembly should be the "leader"; this assembly
         // is the one being dragged.
         if(event) {
            if(event.ctrlKey || event.metaKey) {
               params.drag.abort();
               this.onDragPasteAssemblies.emit(new Point(event.clientX, event.clientY));
            }
            else {
               dragLeader = true;
            }
         }
      };

      this.onSetDraggable.emit([this.thumbnail.nativeElement, {
         start: checkDragLeader,
         stop: updatePos,
         consumeStartEvent: false,
         handle: ".jsplumb-draggable-handle, .jsplumb-draggable-handle *"
      }]);
   }

   protected setAssembly(assembly: WSAssembly): void {
      this.assembly = assembly;
   }

   protected updateTitleTooltip(): void {
      let titleTooltip: string = this.assembly.name;

      if(this.assembly.primary) {
         titleTooltip += " (_#(js:Primary))";
      }

      this.titleTooltip = titleTooltip;
   }
}
