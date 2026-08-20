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
package inetsoft.web.wiz.binding.model;

import java.util.List;

/**
 * One source table and the columns it offers.
 *
 * @param current whether this is the table the scoped assembly is bound to right now, or
 *                {@code null} when no assembly was named. Every field on an assembly's shelves has
 *                to come from its single source — the Composer enforces that by deleting any bound
 *                field absent from a newly chosen source — and listing all the tables is still
 *                correct, since an assembly may be repointed to any of them. So the flag is what
 *                makes the constraint followable without a second call to read the binding.
 *                {@code null} rather than {@code false} for an unscoped call: there is no assembly
 *                to be current for, and {@code false} would assert the opposite of the truth.
 */
public record BindableTable(String name, Boolean current, List<BindableField> fields) {}
