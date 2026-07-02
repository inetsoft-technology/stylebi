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

import java.io.Serializable;

/**
 * Response for {@code POST /api/wiz/vs/condition/browse-data}: the distinct column values, plus the
 * live {@code runtimeId} echoed only when a reaped runtime was transparently restored during the
 * browse (its id changed). The client adopts the echoed id so the next edit targets the live runtime
 * instead of triggering a second restore.
 */
public class WizBrowseDataResponse implements Serializable {
   public Object[] getValues() {
      return values;
   }

   public void setValues(Object[] values) {
      this.values = values;
   }

   public String getRuntimeId() {
      return runtimeId;
   }

   public void setRuntimeId(String runtimeId) {
      this.runtimeId = runtimeId;
   }

   private Object[] values;
   private String runtimeId;

   private static final long serialVersionUID = 1L;
}
