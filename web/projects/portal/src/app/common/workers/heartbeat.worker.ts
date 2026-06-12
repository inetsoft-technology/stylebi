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

const intervals = new Map<string, ReturnType<typeof setInterval>>();

addEventListener("message", (event: MessageEvent) => {
   const { type, id, intervalMs } = event.data;

   if(!id || !type) return;

   if(type === "start") {
      if(!intervalMs || intervalMs <= 0) return;

      // A second start for the same id replaces the existing interval (timer is restarted).
      // Callers must use unique ids per concurrent heartbeat to avoid unintended resets.
      if(intervals.has(id)) {
         clearInterval(intervals.get(id)!);
      }

      intervals.set(id, setInterval(() => {
         postMessage({ type: "tick", id });
      }, intervalMs));
   }
   else if(type === "stop") {
      if(intervals.has(id)) {
         clearInterval(intervals.get(id)!);
         intervals.delete(id);
      }
   }
   else {
      console.warn(`[heartbeat.worker] unknown message type: ${type}`);
   }
});
