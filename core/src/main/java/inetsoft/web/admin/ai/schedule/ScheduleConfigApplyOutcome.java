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

/**
 * Outcome of one attempted Server Location/Time Range change -- identical shape to the shared
 * {@code inetsoft.web.admin.ai.ApplyOutcome} plus one field, {@code advisory}, matching this
 * plugin's own "replicate, don't generalize" precedent (e.g. {@link
 * ScheduleTaskImportApplyOutcome}).
 *
 * @param advisory non-null only when relevant: a force-allowed Time Range update/delete whose old
 *                 name is still referenced by tasks that will be silently reassigned.
 */
public record ScheduleConfigApplyOutcome(String property, String before, String after,
                                         String status, String error, String advisory)
{
}
