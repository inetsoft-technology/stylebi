/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import { Component, HostListener, NgZone, OnDestroy, OnInit, Renderer2 } from "@angular/core";
import { AiAssistantService } from "./ai-assistant.service";

type PanelMode = "side" | "bottom";

const LS_MODE_KEY = "ai-assistant-panel-mode";
const LS_SIDE_WIDTH_KEY = "ai-assistant-panel-side-width";
const LS_BOTTOM_HEIGHT_KEY = "ai-assistant-panel-bottom-height";
const DEFAULT_SIDE_WIDTH = 680;
const DEFAULT_BOTTOM_HEIGHT = 380;
const MIN_SIZE = 300;
const MAX_BOTTOM_HEIGHT = 800;

@Component({
   selector: "ai-assistant-panel",
   templateUrl: "./ai-assistant-panel.component.html",
   styleUrls: ["./ai-assistant-panel.component.scss"]
})
export class AiAssistantPanelComponent implements OnInit, OnDestroy {
   mode: PanelMode = "side";
   sideWidth: number = DEFAULT_SIDE_WIDTH;
   bottomHeight: number = DEFAULT_BOTTOM_HEIGHT;

   private dragging = false;
   private dragStartPos = 0;
   private dragStartSize = 0;
   private unlisten: (() => void)[] = [];

   get panelOpen(): boolean {
      return this.aiAssistantService.panelOpen;
   }

   constructor(
      public aiAssistantService: AiAssistantService,
      private renderer: Renderer2,
      private zone: NgZone
   ) {}

   ngOnInit(): void {
      try {
         const savedMode = localStorage.getItem(LS_MODE_KEY) as PanelMode;

         if(savedMode === "side" || savedMode === "bottom") {
            this.mode = savedMode;
         }

         const savedWidth = Number(localStorage.getItem(LS_SIDE_WIDTH_KEY));
         const maxWidth = Math.floor(window.innerWidth * 0.8);

         if(savedWidth >= MIN_SIZE && savedWidth <= maxWidth) {
            this.sideWidth = savedWidth;
         }

         const savedHeight = Number(localStorage.getItem(LS_BOTTOM_HEIGHT_KEY));

         if(savedHeight >= MIN_SIZE && savedHeight <= MAX_BOTTOM_HEIGHT) {
            this.bottomHeight = savedHeight;
         }
      }
      catch {
         // localStorage unavailable (e.g. private browsing with strict settings) — use defaults.
      }
   }

   ngOnDestroy(): void {
      this.unlisten.forEach(fn => fn());
   }

   close(): void {
      this.aiAssistantService.panelOpen = false;
   }

   toggleMode(): void {
      this.mode = this.mode === "side" ? "bottom" : "side";
      try { localStorage.setItem(LS_MODE_KEY, this.mode); } catch { /* ignore */ }
   }

   @HostListener("window:resize")
   onResize(): void {
      const maxWidth = Math.floor(window.innerWidth * 0.8);

      if(this.mode === "side" && this.sideWidth > maxWidth) {
         this.sideWidth = maxWidth;
      }
   }

   startDrag(event: MouseEvent): void {
      event.preventDefault();
      this.unlisten.forEach(fn => fn());
      this.unlisten = [];
      this.dragging = true;
      this.dragStartPos = this.mode === "side" ? event.clientX : event.clientY;
      this.dragStartSize = this.mode === "side" ? this.sideWidth : this.bottomHeight;

      const moveUnsub = this.renderer.listen("document", "mousemove", (e: MouseEvent) => {
         this.zone.run(() => this.onDrag(e));
      });
      const upUnsub = this.renderer.listen("document", "mouseup", () => {
         this.zone.run(() => this.stopDrag());
      });
      this.unlisten = [moveUnsub, upUnsub];
   }

   private onDrag(event: MouseEvent): void {
      if(!this.dragging) {
         return;
      }

      if(this.mode === "side") {
         const delta = this.dragStartPos - event.clientX;
         const maxWidth = Math.floor(window.innerWidth * 0.8);
         this.sideWidth = Math.min(maxWidth, Math.max(MIN_SIZE, this.dragStartSize + delta));
      }
      else {
         const delta = this.dragStartPos - event.clientY;
         this.bottomHeight = Math.min(MAX_BOTTOM_HEIGHT, Math.max(MIN_SIZE, this.dragStartSize + delta));
      }
   }

   private stopDrag(): void {
      if(!this.dragging) {
         return;
      }

      this.dragging = false;
      this.unlisten.forEach(fn => fn());
      this.unlisten = [];
      try {
         localStorage.setItem(LS_SIDE_WIDTH_KEY, String(this.sideWidth));
         localStorage.setItem(LS_BOTTOM_HEIGHT_KEY, String(this.bottomHeight));
      }
      catch { /* ignore */ }
   }
}
