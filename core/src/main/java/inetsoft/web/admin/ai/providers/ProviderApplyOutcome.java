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
package inetsoft.web.admin.ai.providers;

/**
 * Outcome of one attempted provider change -- identical shape to the shared
 * {@code inetsoft.web.admin.ai.ApplyOutcome} plus one field, {@code advisory}, matching
 * {@code IdentityApplyOutcome}'s own "replicate, don't generalize" precedent (01-spec.md section 6).
 *
 * @param advisory non-error, non-null-only-when-relevant disclosure the caller must relay to the
 *                 human verbatim, never summarized away (01-spec.md section 6/11): the
 *                 "restored at the end of the chain, not its original position" notice on a
 *                 delete-rollback for either chain.
 */
public record ProviderApplyOutcome(String property, String before, String after, String status,
                                   String error, String advisory)
{
}
