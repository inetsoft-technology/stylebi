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

import java.util.List;

/**
 * One requested Scheduled Cycle change (bug #76848, design §3.2): {@code create} (a new cycle
 * named {@code spec.name} with {@code spec.conditions}), {@code update} (change {@code name}'s
 * own conditions and/or rename it via {@code spec.name} -- a PARTIAL merge, decision D5: omitting
 * a {@code spec} field leaves it unchanged, never a whole-record replace), or {@code delete}
 * ({@code name}). {@code name} is the CURRENT identity, used for update/delete only -- create's
 * identity lives in {@code spec.name} (matching every other area's own "verb determines which
 * fields are used" convention, e.g. {@link ScheduleFolderChangeRequest}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScheduleCycleChangeRequest(String verb, String name, ScheduleCycleSpec spec) {
   public static final String VERB_CREATE = "create";
   public static final String VERB_UPDATE = "update";
   public static final String VERB_DELETE = "delete";

   /**
    * {@code create}: {@code name} required non-blank, {@code conditions} required non-empty.
    * {@code update}: both optional, but at least one must be present -- omitting either leaves
    * it unchanged (decision D5).
    */
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record ScheduleCycleSpec(String name, List<inetsoft.web.api.schedule.TimeCondition> conditions) {
   }
}
