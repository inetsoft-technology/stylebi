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

/**
 * Outcome of the one attempted verb in a plan (this area allows at most one entry per plan, see
 * {@link SchedulerStatusChangePlanService}).
 *
 * @param verb   {@code "start"}, {@code "stop"}, or {@code "restart"}.
 * @param before the status label ({@code "Running"}/{@code "Stopped"}) read at plan-resolve time.
 * @param after  the status label read back after the action, or {@code null} when the underlying
 *               call threw before a read-back could be taken.
 * @param status {@code AdminChangeRecord.STATUS_VERIFIED}/{@code STATUS_FAILED} only -- this area
 *               never produces a rolled-back outcome (there is no rollback: the "undo" of any verb
 *               is calling this same tool again with the complementary verb).
 * @param error  non-null only when {@code status} is {@code "failed"}.
 */
public record SchedulerStatusApplyOutcome(String verb, String before, String after, String status,
                                          String error)
{
}
