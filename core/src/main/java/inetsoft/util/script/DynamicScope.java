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
package inetsoft.util.script;

import inetsoft.util.script.graal.ScriptScope;

/**
 * Mark a scriptable that should be treated as dynamic scope for script function.
 */
public interface DynamicScope extends ScriptScope {
   /**
    * Set the next scope in the lookup chain. Used by
    * {@link inetsoft.util.script.JavaScriptEngine#addToPrototype} to chain
    * cooperating dynamic scopes (e.g. a viewsheet scope to its worksheet
    * scope). The default is a no-op; implementations that participate in
    * scope chaining override this together with {@link #getParentScope()}.
    */
   default void setParentScope(ScriptScope parent) {
   }
}
