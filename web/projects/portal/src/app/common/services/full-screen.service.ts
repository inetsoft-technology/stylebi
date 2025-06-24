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
import { EventEmitter, Injectable, OnDestroy } from "@angular/core";
import fscreen from "fscreen";

declare const window;

@Injectable()
export class FullScreenService implements OnDestroy {
   private readonly listener = (event) => this.onFullScreenChange(event);
   fullScreenChange = new EventEmitter<any>();

   get fullScreenMode(): boolean {
      return !!fscreen.fullscreenElement;
   }

   constructor() {
      fscreen.addEventListener("fullscreenchange", this.listener);
   }

   ngOnDestroy(): void {
      fscreen.removeEventListener("fullscreenchange", this.listener);
      this.fullScreenChange.complete();
   }

   enterFullScreen(): void {
      // Bug #20774, use the document element for fullscreen, see
      // https://developers.google.com/web/fundamentals/native-hardware/fullscreen/
      fscreen.requestFullscreen(window.document.documentElement);
   }

   enterFullScreenForElement(target: Element): void {
      fscreen.requestFullscreen(target);
   }

   exitFullScreen(): void {
      fscreen.exitFullscreen();
   }

   private onFullScreenChange(event: any): void {
      this.fullScreenChange.emit(event);
   }
}
