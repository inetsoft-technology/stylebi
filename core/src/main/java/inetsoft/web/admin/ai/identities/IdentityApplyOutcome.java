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
package inetsoft.web.admin.ai.identities;

/**
 * Outcome of one attempted identity change -- identical shape to the shared
 * {@code inetsoft.web.admin.ai.ApplyOutcome} plus one field, {@code advisory}. Kept as a separate
 * record in this package rather than adding a field to the shared class, which is used by every
 * other admin-chat area and would need every one of their call sites touched (out of scope for this
 * track) -- "replicate, don't generalize" (carry-forward item 5) applied to a shared record, not
 * just to the service classes (spec/plan section 6).
 *
 * @param advisory non-error, non-null-only-when-relevant disclosure the caller must relay to the
 *                 human verbatim, never summarized away (spec section 6/11): the generated-password
 *                 notice for a rolled-back user create-turned-delete-then-recreate, and the
 *                 {@code getDeleteTaskImpacts} result for a user/group delete, whether it applied
 *                 cleanly or rolled back.
 */
public record IdentityApplyOutcome(String property, String before, String after, String status,
                                   String error, String advisory)
{
}
