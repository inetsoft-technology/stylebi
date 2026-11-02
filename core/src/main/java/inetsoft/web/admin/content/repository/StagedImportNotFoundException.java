/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.admin.content.repository;

/**
 * Thrown when an import id names no staged import the caller may use (never staged, expired, already
 * imported or discarded, or owned by another user or organization). It stays an
 * {@link IllegalStateException} so existing callers keep their behavior, while letting a caller
 * that can answer with a client error tell this case apart from a genuine server fault.
 */
public class StagedImportNotFoundException extends IllegalStateException {
   public StagedImportNotFoundException(String message) {
      super(message);
   }
}
