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
package inetsoft.web.admin.ai;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Result of applying a whole changeset.
 *
 * @param status            {@code applied} — every change verified; {@code rolled-back} — something
 *                          failed and every applied change was undone; {@code rollback-failed} —
 *                          the server is left partially changed and needs operator attention,
 *                          either because at least one undo did not succeed, or because an apply
 *                          threw with no verifiable before/after evidence to undo from in the
 *                          first place (no undo was even attempted for that property).
 * @param rollbackFailures  present only when {@code status} is {@code rollback-failed}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplyResult(String transactionId, String status, String backupRef,
                          List<ApplyOutcome> results, List<RollbackFailure> rollbackFailures)
{
}
