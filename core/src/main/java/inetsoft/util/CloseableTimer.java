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
package inetsoft.util;

import java.util.Timer;

public class CloseableTimer extends Timer implements AutoCloseable {
   public CloseableTimer() {
   }

   public CloseableTimer(boolean isDaemon) {
      super(isDaemon);
   }

   public CloseableTimer(String name) {
      super(name);
   }

   public CloseableTimer(String name, boolean isDaemon) {
      super(name, isDaemon);
   }

   @Override
   public void close() throws Exception {
      cancel();
   }
}
