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
package inetsoft.util.script.graal;

/**
 * Lets tests outside this package set {@link ScriptTimeoutGuard}'s test hook (bug #76960).
 */
public final class ScriptTimeoutGuardTestHooks {
   private ScriptTimeoutGuardTestHooks() {
   }

   /**
    * Run {@code hook} in the interrupt task right after it claims an exec's token and before
    * it interrupts; {@code null} to clear.
    */
   public static void setBeforeInterrupt(Runnable hook) {
      ScriptTimeoutGuard.beforeInterruptHook = hook;
   }
}
