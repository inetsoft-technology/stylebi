/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.admin.ai.schedule;

import java.util.List;

/**
 * Response body for {@code POST /api/wiz/v1/admin/schedule/import/stage} -- uploads and parses a
 * task-export XML file, writes nothing. {@code stagingToken} is a bearer for the parsed content,
 * held in an in-memory, single-node cache with a 30-minute idle timeout (mirroring the "review
 * window" scope {@code analysisId} uses for Materialized Views) -- NOT persisted to real storage,
 * gone on server restart, and (like the MV analysis cache) not itself cluster-replicated, matching
 * the same "plain in-JVM map, single-node scope" precedent {@code SheetSessionService} already
 * establishes elsewhere in this codebase for comparable short-lived session state.
 *
 * @param timeRangesInFile count of {@code <timeRange>} entries the uploaded file also carries.
 *                         This area does NOT import them (see {@code AdminScheduleTransferController}'s
 *                         own javadoc for why) -- a non-zero count here is a disclosed, deliberate
 *                         scope boundary, not a bug: use {@code preview_schedule_config_changes}/
 *                         {@code apply_schedule_config_changes} to manage Time Ranges instead.
 */
public record ScheduleTaskStagingResult(String stagingToken, List<StagedScheduleTask> tasks,
                                        int timeRangesInFile)
{
}
