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

/**
 * Shared async helpers for portal *.tl.spec.ts under Zone + vitest-patch.
 * Prefer these over Promise.resolve / await setTimeout(0), which can hang a
 * loaded Vitest worker when macrotasks never drain.
 */

/** Sync thenable for dialog/submit mocks (RxJS `from(promise)` completes in-stack). */
export function syncResolve<T>(value: T): Promise<T> {
   return {
      then(onFulfilled?: ((v: T) => any) | null, onRejected?: ((e: any) => any) | null) {
         try {
            if(!onFulfilled) {
               return syncResolve(value as any);
            }

            const next = onFulfilled(value);

            if(next && typeof (next as PromiseLike<unknown>).then === "function") {
               return next;
            }

            return syncResolve(next);
         }
         catch(err) {
            if(onRejected) {
               return syncResolve(onRejected(err));
            }

            throw err;
         }
      },
   } as Promise<T>;
}

export function syncReject(reason: unknown = "dismissed"): Promise<never> {
   return {
      then(_onFulfilled?: any, onRejected?: ((e: any) => any) | null) {
         if(!onRejected) {
            throw reason;
         }

         const next = onRejected(reason);

         if(next && typeof (next as PromiseLike<unknown>).then === "function") {
            return next;
         }

         return syncResolve(next);
      },
   } as Promise<never>;
}

/** Microtask-only flush — never use setTimeout(0) in TL specs. */
export async function flushMicrotasks(): Promise<void> {
   await Promise.resolve();
   await Promise.resolve();
}
