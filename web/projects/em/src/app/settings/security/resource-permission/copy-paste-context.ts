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

/**
 * Canonical context keys for the copy-paste clipboard in em-resource-permission.
 *
 * Each host component exposes its constant as a `protected readonly` field and
 * passes it to the child via a property binding:
 *
 *   protected readonly copyPasteContext = COPY_PASTE_CONTEXT_REPOSITORY;
 *
 *   <em-resource-permission [copyPasteContext]="copyPasteContext" ...>
 *
 * This file is the single source of truth. Changing a constant here is
 * sufficient — all component fields and template bindings update automatically.
 */
export const COPY_PASTE_CONTEXT_REPOSITORY = "repository";
export const COPY_PASTE_CONTEXT_SECURITY_ACTIONS = "security-actions";

/** Union of all valid copy-paste context keys. */
export type CopyPasteContext =
   typeof COPY_PASTE_CONTEXT_REPOSITORY |
   typeof COPY_PASTE_CONTEXT_SECURITY_ACTIONS;
