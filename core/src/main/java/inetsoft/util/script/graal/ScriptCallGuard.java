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
package inetsoft.util.script.graal;

import java.util.function.Supplier;

/**
 * Implemented by a scriptable that must observe every {@link ScriptFunction} call made on
 * it, e.g. to react to the state the call changed. The receiver of a function registered with
 * {@code addFunctionProperty} that implements this interface runs the call through
 * {@link #guardCall}.
 */
public interface ScriptCallGuard {
   /**
    * Run a script function call.
    *
    * @param function the name of the called function.
    * @param call performs the call and returns its result.
    *
    * @return the result of the call.
    */
   Object guardCall(String function, Supplier<Object> call);
}
