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
package inetsoft.web.admin.ai.mv;

/**
 * Thrown when an {@code analysisId} is unknown to {@code MVSupportService}'s cluster map -- either
 * it never existed, or its 20-minute idle TTL has already swept it. Mapped to a distinct HTTP 410
 * (not the generic 400/409 a stale {@code planHash} gets), because the caller's only remedy is to
 * re-run {@code analyze_mv} from scratch and re-review -- there is no "current" plan to reconcile
 * against the way a {@code planHash} conflict has.
 */
public class AnalysisExpiredException extends RuntimeException {
   public AnalysisExpiredException(String analysisId) {
      super("analysisId \"" + analysisId + "\" is unknown or has expired (analyses are only kept " +
            "for 20 minutes of inactivity) -- re-run analyze_mv and re-review before applying");
   }
}
