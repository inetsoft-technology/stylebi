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
package inetsoft.util.stall;

import java.util.List;

/**
 * A source of stall signals that are not a registered wait (bug #76967), such as the
 * worksheet script context pool's counters. The watchdog polls every probe once per scan,
 * called under the watchdog's monitor on its only thread: a probe that blocks stops all stall
 * detection and freezes the health flag. A probe must therefore not block or take any lock a
 * query thread may hold (the watchdog's lock order relies on it), and should only read
 * counters that are already maintained. A finding never fails a query and never turns the
 * health check DOWN: the watchdog logs it once per episode, and writes a rate-limited thread
 * dump if the finding asks for one.
 */
@FunctionalInterface
public interface StallProbe {
   /**
    * @return what this probe finds now; empty, or {@code null}, if nothing.
    */
   List<Finding> scan();

   /**
    * One signal. Findings with the same key in consecutive scans are one episode, which is
    * logged (and dumped) once.
    *
    * @param key     identifies the episode.
    * @param message the text logged.
    * @param dump    whether to write a thread dump when the episode starts.
    */
   record Finding(String key, String message, boolean dump) {
   }
}
