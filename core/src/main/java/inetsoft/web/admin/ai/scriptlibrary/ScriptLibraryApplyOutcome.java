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
package inetsoft.web.admin.ai.scriptlibrary;

/**
 * Outcome of one attempted change -- identical shape to the shared
 * {@code inetsoft.web.admin.ai.ApplyOutcome} plus one field, {@code advisory}, matching
 * {@code RecycleBinApplyOutcome}'s own "replicate, don't generalize" precedent.
 *
 * @param advisory non-error, non-null-only-when-relevant disclosure the caller must relay to the
 *                 human verbatim: e.g. a {@code delete} that proceeded over dependents via
 *                 {@code force: true}, naming what still references the deleted script.
 */
public record ScriptLibraryApplyOutcome(String property, String before, String after,
                                        String status, String error, String advisory)
{
}
