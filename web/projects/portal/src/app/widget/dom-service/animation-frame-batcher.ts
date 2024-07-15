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
export class AnimationFrameBatcher {
   private reads;
   private writes;
   private rafScheduled;
   private animationID;

   constructor() {
      this.reads = [];
      this.writes = [];
      this.rafScheduled = false;
      this.animationID = -1;
   }

   public requestRead(cb: () => void): number {
      this.reads.push(cb);
      return this.scheduleAnimationFrame();
   }

   public requestWrite(cb: () => void): number {
      this.writes.push(cb);
      return this.scheduleAnimationFrame();
   }

   public cancelAnimationFrame(): void {
      cancelAnimationFrame(this.animationID);
   }

   private scheduleAnimationFrame(): number {
      if(!this.rafScheduled) {
         try {
            cancelAnimationFrame(this.animationID);
            this.animationID = requestAnimationFrame(() => {
               cancelAnimationFrame(this.animationID);
               this.flush();
            });

            this.rafScheduled = true;
         }
         catch(e) {
            this.rafScheduled = false;
            throw e;
         }
      }

      return this.animationID;
   }

   private flush(): void {
      this.reads.forEach((cb) => cb());
      this.writes.forEach((cb) => cb());
      this.reads = [];
      this.writes = [];
      this.rafScheduled = false;
      this.animationID = -1;
   }
}

