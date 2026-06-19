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

/**
 * A scriptable scope that also exposes JS array (indexed) semantics. Replaces
 * the use of Rhino's indexed {@code get(int, Scriptable)} for objects that
 * behave as JS arrays. Implementations are bridged to GraalJS by
 * {@link ArrayProxy}, which exposes both the array elements (via
 * {@link #getArraySize()}/{@link #getArrayElement(long)}) and the named members
 * inherited from {@link ScriptScope}.
 */
public interface ScriptArrayScope extends ScriptScope {
   /** Number of indexed elements in this array-shaped scope. */
   long getArraySize();

   /** Get the indexed element at the given position. */
   Object getArrayElement(long index);

   /**
    * Set the indexed element at the given position. Replaces Rhino's indexed
    * {@code put(int, Scriptable, Object)}. The default is a no-op so scopes
    * that were read-only under Rhino stay read-only; scopes that accepted
    * indexed writes override this. The {@code value} is already host-converted.
    */
   default void setArrayElement(long index, Object value) {
   }
}
