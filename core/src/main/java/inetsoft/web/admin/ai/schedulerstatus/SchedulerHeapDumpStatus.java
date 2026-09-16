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

/**
 * Response of {@code GET .../scheduler/heap-dump/{token}/status} -- same four-field shape
 * ({@code token}/{@code complete}/{@code failed}/{@code error}) the repository-maintenance area's
 * {@code RepositoryMaintenanceStatus} already established for a kick-off+poll pair, plus
 * {@code storagePath} once complete and successful.
 *
 * <p>Once {@code complete} is {@code true} for a given token, {@link SchedulerDiagnosticsService}
 * caches the result and returns it again on any later poll for the same token, WITHOUT calling
 * {@code isHeapDumpComplete}/{@code getHeapDumpInfo}/{@code disposeHeapDump} a second time --
 * chosen (over refusing a repeat poll with an error) because whether calling
 * {@code disposeHeapDump} twice for the same id is itself safe was not confirmed against live
 * behavior at build time (track-status/01-design.md revision, "repeat-poll-after-disposal"
 * question); an idempotent repeat response is the safer default of the two options either way.
 *
 * @param storagePath the external-storage path the resulting {@code .hprof.gz} was written to,
 *                    non-null only when {@code complete} is {@code true} and {@code failed} is
 *                    {@code false}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SchedulerHeapDumpStatus(String token, boolean complete, boolean failed, String error,
                                      String storagePath)
{
}
