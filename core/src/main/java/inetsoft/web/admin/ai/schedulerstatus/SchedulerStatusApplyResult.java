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
package inetsoft.web.admin.ai.schedulerstatus;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Result of applying a scheduler status changeset. Unlike {@code ClusterApplyResult}, there is no
 * {@code "partial"} status -- a plan carries exactly one entry, so the overall result collapses to
 * {@code "applied"} (verified) or {@code "failed"} (not verified); a plan-drift 409 is a thrown
 * {@code PlanHashMismatchException}, never a value of this field. There is also no
 * {@code "rolled-back"}/{@code "rollback-failed"} value -- this area never rolls back.
 *
 * @param backupRef always {@code null} -- no verb in this area requires a Tier-2 snapshot.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SchedulerStatusApplyResult(String transactionId, String status, String backupRef,
                                         List<SchedulerStatusApplyOutcome> results)
{
}
