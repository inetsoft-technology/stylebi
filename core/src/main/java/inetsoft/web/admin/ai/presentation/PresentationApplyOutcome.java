/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.ai.presentation;

/**
 * Outcome of one attempted presentation sub-model change -- area-local (not the shared
 * {@code inetsoft.web.admin.ai.ApplyOutcome}) only insofar as {@code advisory} is added, matching
 * {@code LicenseApplyOutcome}'s own "replicate, don't generalize" precedent. {@code before}/
 * {@code after} are JSON text (the same projection {@link PresentationChangePlanService} puts in
 * {@code PlanChange.currentValue}/{@code proposedValue}, webMap-masked where applicable) -- kept as
 * {@code String}, matching the shared record's own field type, per this build's deviation from
 * 01-spec.md Flagged Decision 5 (see {@link PresentationChangePlanService}'s own javadoc).
 *
 * @param property  composite {@code <subModel>:<scope>} key, e.g. {@code "lookAndFeel:global"}.
 * @param advisory  non-error, non-null-only-when-relevant disclosure the caller must relay to the
 *                  human verbatim -- currently only used for the storage-scope non-compensable
 *                  notice (01-spec.md section 6).
 */
public record PresentationApplyOutcome(String property, String before, String after, String status,
                                       String error, String advisory)
{
}
