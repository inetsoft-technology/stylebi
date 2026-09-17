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
package inetsoft.web.admin.ai.general;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Response for {@code GET /api/wiz/v1/admin/general/settings} -- one entry per requested sub-model,
 * keyed by short name (all six when {@code subModel} was omitted, exactly one otherwise). The four
 * {@code email} credential fields are masked; see {@code GeneralJson.EMAIL_SECRET_FIELDS}.
 *
 * <p>No {@code scope} field, unlike {@code PresentationGetResult}: every service behind this area
 * reads and writes global {@code SreeEnv} state and takes no org-scope argument.
 *
 * <p>{@code readErrors} carries a per-sub-model failure message instead of failing the whole
 * response. {@code PerformanceSettingsService.getModel} parses four properties with no default, so
 * a deployment where {@code query.runtime.timeout} is unset or non-numeric would otherwise take
 * the other five sub-models down with it -- an operator asking "what are my mail settings?" should
 * not get nothing because an unrelated property is unparseable. Null when every read succeeded.
 */
public record GeneralGetResult(Map<String, JsonNode> subModels, Map<String, String> readErrors) {
}
