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

import inetsoft.web.admin.schedule.model.ServerLocation;
import inetsoft.web.viewsheet.model.dialog.schedule.TimeRangeModel;

import java.util.List;

/**
 * The response of {@code GET /api/wiz/v1/admin/schedule/config} -- deliberately a NARROWED view of
 * {@code ScheduleConfigurationModel} (server locations and time ranges only), not the whole model.
 * Track B (scheduler options) owns the other ~18 scalar fields of that same model and exposes them
 * through the generic properties area instead, so there is no competing read tool/shape to
 * reconcile against here (spec: {@code 03-reconcile.md} "GET-endpoint scope").
 */
public record ScheduleConfigView(List<ServerLocation> serverLocations, List<TimeRangeModel> timeRanges) {
}
