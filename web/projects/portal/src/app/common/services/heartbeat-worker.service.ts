/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import { Injectable, OnDestroy } from "@angular/core";
import { Observable } from "rxjs";

@Injectable({
   providedIn: "root"
})
export class HeartbeatWorkerService implements OnDestroy {
   private worker: Worker | null = null;

   /**
    * Returns an Observable that emits once per `intervalMs` milliseconds using a Web Worker
    * timer, which continues firing even when the browser tab is backgrounded.
    *
    * When a Web Worker is in use, `id` must be unique per concurrent heartbeat within this
    * service instance — it is used to route tick messages back to the correct subscriber.
    * On the `setInterval` fallback path (when Workers are unavailable), each subscription
    * gets its own independent interval and `id` is not used for routing.
    * On unsubscribe the worker interval (or fallback timer) is stopped automatically.
    */
   createHeartbeat(id: string, intervalMs: number): Observable<void> {
      const worker = this.getWorker();

      if(!worker) {
         return new Observable<void>(subscriber => {
            const intervalId = setInterval(() => subscriber.next(), intervalMs);
            return () => clearInterval(intervalId);
         });
      }

      return new Observable<void>(subscriber => {
         const onMessage = (event: MessageEvent) => {
            if(event.data?.type === "tick" && event.data?.id === id) {
               subscriber.next();
            }
         };

         worker.addEventListener("message", onMessage);
         worker.postMessage({ type: "start", id, intervalMs });

         return () => {
            worker.removeEventListener("message", onMessage);
            try { worker.postMessage({ type: "stop", id }); } catch { /* worker already terminated */ }
         };
      });
   }

   ngOnDestroy(): void {
      if(this.worker) {
         this.worker.terminate();
         this.worker = null;
      }
   }

   private getWorker(): Worker | null {
      if(this.worker) {
         return this.worker;
      }

      if(typeof Worker === "undefined") {
         return null;
      }

      try {
         this.worker = new Worker(new URL("../workers/heartbeat.worker", import.meta.url));
         this.worker.addEventListener("error", () => {
            this.worker?.terminate();
            this.worker = null;
         });
         return this.worker;
      }
      catch {
         return null;
      }
   }
}
