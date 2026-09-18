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
package inetsoft.web.admin.ai.datasource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * A live JDBC connectivity probe against a PENDING (not-yet-applied) set of connection fields
 * (bug 76599, Gap 1). Same id/name resolution rules as {@link DataSourceChangeRequest}, and the
 * same {@code spec} partial-update shape {@code preview}/{@code apply} already accept -- this is
 * deliberately not a {@link DataSourceChangeRequest} itself since a connectivity test has no verb,
 * performs no mutation, and is never part of a changeset/plan hash.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataSourceTestConnectionRequest {
   public String getId() { return id; }
   public void setId(String v) { this.id = v; }

   public String getName() { return name; }
   public void setName(String v) { this.name = v; }

   /** Same partial-update shape {@code DataSourceChangeRequest.spec} accepts for {@code update}
    * -- only the fields being tested need to be present; everything else is merged in from the
    * data source's current (real, unmasked) connection fields. */
   public Map<String, Object> getSpec() { return spec; }
   public void setSpec(Map<String, Object> v) { this.spec = v; }

   private String id;
   private String name;
   private Map<String, Object> spec;
}
