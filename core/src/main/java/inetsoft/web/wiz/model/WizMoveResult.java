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

import java.util.List;

/**
 * The outcome of a {@code /datasources/move} call — one entry per requested item, in request
 * order. Each surviving item is moved with its own native call, one at a time, specifically so one
 * item's failure does not prevent attempting the rest — the native move call aborts its whole
 * batch on the first exception, which this endpoint deliberately does not replicate.
 *
 * @param results one outcome per requested item.
 */
public record WizMoveResult(List<WizMoveItemResult> results) {
}
