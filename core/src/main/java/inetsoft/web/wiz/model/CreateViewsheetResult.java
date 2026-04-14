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

package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Response model for POST /viewsheet/create.
 * Contains aggregated chart data so the caller does not need a separate data-fetch request.
 * {@code headers} and {@code rows} are null when the assembly is not a chart or data is unavailable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateViewsheetResult {
   public List<String> getHeaders() {
      return headers;
   }

   public void setHeaders(List<String> headers) {
      this.headers = headers;
   }

   public List<Map<String, Object>> getRows() {
      return rows;
   }

   public void setRows(List<Map<String, Object>> rows) {
      this.rows = rows;
   }

   private List<String> headers;
   private List<Map<String, Object>> rows;
}
