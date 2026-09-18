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

import java.util.Map;

/**
 * One requested change to either a Server Location or a Time Range -- the two collection fields
 * {@code ScheduleConfigurationModel} carries alongside Track B's own scalar fields. {@code
 * unitType} is the discriminator (mirrors Viewsheets' own {@code unitType}-first convention rather
 * than schedule-tasks' single-unit shape, since this area genuinely has two unit shapes sharing one
 * underlying model/service). {@code spec} is untyped on the wire -- its shape depends on {@code
 * unitType} ({@code ServerLocation} vs. {@code TimeRangeModel}), so it is resolved into the correct
 * type by {@link ScheduleConfigChangePlanService}, not by Jackson polymorphism.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleConfigChangeRequest {
   public static final String UNIT_SERVER_LOCATION = "serverLocation";
   public static final String UNIT_TIME_RANGE = "timeRange";
   public static final String VERB_CREATE = "create";
   public static final String VERB_UPDATE = "update";
   public static final String VERB_DELETE = "delete";

   public String getUnitType() { return unitType; }
   public void setUnitType(String v) { this.unitType = v; }

   public String getVerb() { return verb; }
   public void setVerb(String v) { this.verb = v; }

   /** The existing entry's own key -- a Server Location's {@code path}, or a Time Range's {@code
    * name} -- required for {@code update}/{@code delete}; not used for {@code create} (whose key is
    * derived from {@code spec}). */
   public String getKey() { return key; }
   public void setKey(String v) { this.key = v; }

   /** Required for {@code create}/{@code update}; not used for {@code delete}. Untyped here --
    * {@link ScheduleConfigChangePlanService} converts it to {@code ServerLocation}/{@code
    * TimeRangeModel} once {@code unitType} is known. */
   public Map<String, Object> getSpec() { return spec; }
   public void setSpec(Map<String, Object> v) { this.spec = v; }

   /**
    * Only meaningful for {@code unitType=timeRange}, {@code verb=delete} or an identity-changing
    * {@code update} (name/startTime/endTime differs from the currently stored entry): when a live
    * schedule task's time condition currently resolves to this range, the plan is refused by
    * default, naming the affected task(s) and what {@code TaskBalancer} would silently reassign
    * them to. {@code force=true} allows the plan through, carrying that same advisory forward on
    * the result instead of refusing. Not used for Server Locations -- tracing the actual data flow
    * found no live back-reference a Server Location delete/rename could silently break (see
    * {@code 01-design.md}'s revision section), so no such gate exists for that unit type.
    */
   public boolean isForce() { return force; }
   public void setForce(boolean v) { this.force = v; }

   private String unitType;
   private String verb;
   private String key;
   private Map<String, Object> spec;
   private boolean force;
}
