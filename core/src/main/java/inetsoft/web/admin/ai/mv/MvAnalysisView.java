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

import inetsoft.web.admin.model.NameLabelTuple;

import java.util.List;

/**
 * Poll response for {@code GET /api/wiz/v1/admin/mv/analyze/{analysisId}}. Folds
 * check-analysis/get-model/show-plan/exceptions into one response (all four read from the same
 * analysisId-scoped cluster-map entry; no reason to make the caller round-trip four times).
 * {@code completed:false} until the background analysis job finishes -- the caller re-polls.
 */
public record MvAnalysisView(String analysisId, boolean completed, boolean exception,
                             List<MvCandidateView> candidates, List<String> exceptionReasons,
                             List<NameLabelTuple> availableCycles, String defaultCycle,
                             boolean onDemand, boolean serverRunInBackground)
{
}
