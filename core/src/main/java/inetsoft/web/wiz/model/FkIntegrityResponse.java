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
 * The two ways an INNER join from the fact table to its foreign-key target could change an
 * aggregate. The join is safe to inject only when <em>both</em> counts are zero — a zero drop
 * count alone is not sufficient, because a non-unique target key inflates every aggregate while
 * dropping nothing.
 *
 * <p>This body is only ever produced when both queries actually ran and returned a row. Every
 * failure mode is an error response instead, because a body carrying zeros means "safe to join"
 * to the caller.</p>
 *
 * @param droppedRowCount         rows the join would drop: rows whose FK is NULL, plus rows whose
 *                                FK has no matching target row. Non-zero deflates aggregates.
 * @param duplicateTargetKeyCount target key values occurring more than once. Non-zero means the
 *                                join fans rows out and inflates aggregates.
 */
public record FkIntegrityResponse(long droppedRowCount, long duplicateTargetKeyCount) {
}
