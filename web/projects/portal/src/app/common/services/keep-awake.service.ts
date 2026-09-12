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

/**
 * Keeps the current tab from being frozen/"slept" by Chrome "Energy Saver"
 * and Edge "Sleeping Tabs" by holding a Web Lock for the lifetime of an open
 * runtime viewsheet. A held Web Lock is one of the documented conditions under
 * which Chromium will not freeze a backgrounded tab, so the viewsheet's
 * heartbeat keeps firing and the server-side session does not expire.
 *
 * Note: this exploits a freeze-exemption that Chromium has signalled may be
 * replaced by a sanctioned opt-out in future. It is isolated here so it can be
 * removed cleanly. It does NOT prevent Memory Saver tab *discard*, which fully
 * unloads the page.
 */
@Injectable()
export class KeepAwakeService implements OnDestroy {
   private abortController: AbortController | null = null;

   /**
    * Acquire and hold a uniquely-named Web Lock until released. Releases any
    * previously-held lock first. No-op if the Web Locks API is unavailable
    * (old browsers / non-secure contexts).
    */
   keepAwake(id: string): void {
      if(typeof navigator === "undefined" || !navigator.locks) {
         return;
      }

      this.release();
      this.abortController = new AbortController();

      // The lock is held for as long as the callback's promise is unsettled;
      // it never resolves, so the lock is held until the signal is aborted.
      navigator.locks.request(
         `viewsheet-keep-awake-${id}`,
         { signal: this.abortController.signal },
         () => new Promise<void>(() => { /* held until aborted */ })
      ).catch((err: any) => {
         if(err?.name !== "AbortError") {
            console.warn("Unexpected Web Locks error:", err);
         }
      });
   }

   /** Release the held lock, if any. */
   release(): void {
      if(this.abortController) {
         this.abortController.abort();
         this.abortController = null;
      }
   }

   ngOnDestroy(): void {
      this.release();
   }
}
