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

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Result of applying a whole cluster changeset. Unlike every prior area's own apply result, there is
 * no {@code rollbackFailures} field at all -- this area never rolls back (01-spec.md section 6): each
 * server's pause/resume is independent and self-inverse, so a per-server failure is reported via
 * {@code results}, never compensated.
 *
 * @param status     {@code "applied"} (every entry verified), {@code "partial"} (a mix -- the one new
 *                   changeset-status value this area introduces), or {@code "failed"} (every entry
 *                   failed). Never {@code "rolled-back"}/{@code "rollback-failed"} (03-reconcile.md).
 * @param backupRef  always {@code null} -- no verb in this area requires a Tier-2 snapshot (section
 *                   7), carried as an explicit field rather than omitted so the shape stays uniform
 *                   with every other area's own apply result.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClusterApplyResult(String transactionId, String status, String backupRef,
                                 List<ClusterApplyOutcome> results)
{
}
