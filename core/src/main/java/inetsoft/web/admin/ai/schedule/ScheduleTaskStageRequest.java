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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body for {@code POST /api/wiz/v1/admin/schedule/import/stage} -- base64-in-JSON, not
 * multipart, unlike {@code AdminAssetImportController#stage}'s own zip upload: a schedule-task
 * export file is small XML text (the same size class {@code lookAndFeel}'s {@code logoFile}/
 * {@code viewsheetFile} already send base64-in-JSON), not a multi-megabyte binary archive.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleTaskStageRequest {
   public String getXml() { return xml; }
   public void setXml(String v) { this.xml = v; }

   private String xml;
}
