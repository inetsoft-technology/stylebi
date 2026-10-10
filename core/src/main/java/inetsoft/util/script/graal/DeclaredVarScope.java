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
 * A root scope on which a script's own top-level {@code var} wins over a same-named
 * member of the scope chain (Bug #78247), as a freehand table's cell formulas run on its
 * {@code CalcTableScope}: a cell's {@code var max} or {@code var value} is the cell's
 * variable, not the table's {@code max} summary function or the assembly's {@code value},
 * so a read never sees the member and a write never replaces it on the scope every cell
 * and row of the table shares. On any other root the member still wins, as on the exec
 * scope in Rhino. Only the names the running script itself declares are affected, so a
 * function declared elsewhere (a library function, an onInit function) that the script
 * calls still sees the member. See {@link BindingRootProxy#DECLARED_VARS_MEMBER}.
 */
public interface DeclaredVarScope {
}
