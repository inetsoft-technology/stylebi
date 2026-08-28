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
package inetsoft.web.admin.ai.licensing;

import com.fasterxml.jackson.annotation.JsonInclude;
import inetsoft.web.admin.ai.RollbackFailure;

import java.util.List;

/**
 * Result of applying a whole license changeset -- identical shape to the shared
 * {@code inetsoft.web.admin.ai.ApplyResult} except {@code results} carries
 * {@link LicenseApplyOutcome}. {@code rollbackFailures} reuses the shared {@code RollbackFailure}
 * unmodified.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LicenseApplyResult(String transactionId, String status, String backupRef,
                                 List<LicenseApplyOutcome> results,
                                 List<RollbackFailure> rollbackFailures)
{
}
