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

import org.graalvm.polyglot.Value;

/**
 * A scope that owns the top-level {@code var} names of the scripts it runs, as a formula
 * table owns the vars of its formulas (Testing #77123). An owned name resolves in this scope
 * (it reports the name in {@code hasMember} from the start), a script write of it is stored
 * here by {@link BindingRootProxy} as the guest value, and the declaration hoist of the
 * engine does not copy it to the global scope, so the value lives exactly as long as the
 * owner, whatever compile path the script takes and whichever context runs it.
 */
public interface OwnedVarScope {
   /**
    * @return whether {@code name} is a var this scope owns.
    */
   boolean ownsVar(String name);

   /**
    * Store a script write of an owned var.
    *
    * @param value the written guest value, not a host copy: a script object keeps its
    *              identity and in-place changes, see {@link ScriptValueConverter#toOwnedVar}.
    */
   void putOwnedVar(String name, Value value);
}
