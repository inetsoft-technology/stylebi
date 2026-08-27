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
package inetsoft.web.admin.ai.cluster;

/**
 * Outcome of one attempted pause/resume, one entry per (verb, server) in the plan. {@code status} is
 * {@code AdminChangeRecord.STATUS_VERIFIED}/{@code STATUS_FAILED} only -- this area never produces a
 * rolled-back outcome (01-spec.md section 6, 03-reconcile.md's required addition: each server's
 * pause/resume is independent and self-inverse, so a per-server failure is reported, not compensated).
 *
 * @param property the server name (matches {@code PlanChange.property()}).
 * @param before   the status label read at plan-resolve time.
 * @param after    the status label read back fresh, immediately after the pause/resume call -- the
 *                 direct fix for {@code stylebi#76343} (the raw endpoint discards this signal today).
 *                 {@code null} when the attempt threw before a read-back could be taken.
 * @param status   {@code "verified"} when {@code after} matches the plan's proposed state,
 *                 {@code "failed"} otherwise (including the pre-existing silent-failure case: the
 *                 node was unreachable, or the pause/resume message did not complete).
 * @param error    non-null only when {@code status} is {@code "failed"}.
 */
public record ClusterApplyOutcome(String property, String before, String after, String status,
                                  String error)
{
}
