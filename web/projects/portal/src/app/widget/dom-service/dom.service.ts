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
import { Injectable, NgZone } from "@angular/core";
import { AnimationFrameBatcher } from "./animation-frame-batcher";

@Injectable({
   providedIn: "root"
})
export class DomService {
   private batcher: AnimationFrameBatcher;

   constructor(private zone: NgZone) {
      this.batcher = new AnimationFrameBatcher();
   }

   public requestRead(cb: () => void): number {
      return this.zone.runOutsideAngular(() => this.batcher.requestRead(cb));
   }

   public requestWrite(cb: () => void): number {
      return this.zone.runOutsideAngular(() => this.batcher.requestWrite(cb));
   }

   public cancelAnimationFrame() {
      this.batcher.cancelAnimationFrame();
      this.batcher = new AnimationFrameBatcher();
   }
}
