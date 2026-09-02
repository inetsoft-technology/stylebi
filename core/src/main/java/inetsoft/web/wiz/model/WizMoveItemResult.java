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

package inetsoft.web.wiz.model;

/**
 * One item's outcome from a {@code /datasources/move} call.
 *
 * @param oldPath the item's path before the move.
 * @param newPath the path it would have (or now has, when {@code ok}) after the move.
 * @param ok      whether the item was moved.
 * @param reason  null when {@code ok}. Otherwise one of {@code PERMISSION_DENIED},
 *                {@code DUPLICATE_NAME}, {@code SELF_DESCENDANT}, {@code UNKNOWN}.
 *                {@code DUPLICATE_NAME} is not currently producible by the underlying move call —
 *                the pre-check endpoint covers that case — but the reason is kept for forward
 *                compatibility.
 */
public record WizMoveItemResult(String oldPath, String newPath, boolean ok, String reason) {
   public static final String PERMISSION_DENIED = "PERMISSION_DENIED";
   public static final String DUPLICATE_NAME = "DUPLICATE_NAME";
   public static final String SELF_DESCENDANT = "SELF_DESCENDANT";
   public static final String UNKNOWN = "UNKNOWN";
}
