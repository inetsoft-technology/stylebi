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

/**
 * Outcome of one attempted general sub-model change. Area-local rather than the shared
 * {@code inetsoft.web.admin.ai.ApplyOutcome} only insofar as {@code advisory} is added, matching
 * {@code PresentationApplyOutcome} and {@code LicenseApplyOutcome}'s "replicate, don't generalize"
 * precedent.
 *
 * @param property  the bare sub-model key, e.g. {@code "email"}. Not the composite
 *                  {@code <subModel>:<scope>} presentation uses, because this area has no scope.
 * @param before    JSON text of the pre-change value, email-masked where applicable.
 * @param after     JSON text of the post-change value, email-masked where applicable.
 * @param advisory  a non-error disclosure the caller must relay to the human verbatim. Used for
 *                  the two side effects a rollback does not undo: {@code performance} clearing
 *                  {@code AssetDataCache}, and {@code mv} rewriting the data-cycle registry.
 */
public record GeneralApplyOutcome(String property, String before, String after, String status,
                                  String error, String advisory)
{
}
