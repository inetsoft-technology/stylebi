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
 * Outcome of one attempted task import -- identical shape to the shared
 * {@code inetsoft.web.admin.ai.ApplyOutcome} plus one field, {@code advisory}, matching this
 * plugin's own "replicate, don't generalize" precedent (e.g. {@code RecycleBinApplyOutcome}).
 *
 * @param advisory non-null only when relevant: e.g. the imported task's own recorded folder path
 *                 no longer exists on this server (or existed but this area does not attempt to
 *                 re-file into it -- see the owning service's own javadoc), or the file also
 *                 contained Time Range definitions this area does not import.
 */
public record ScheduleTaskImportApplyOutcome(String property, String before, String after,
                                             String status, String error, String advisory)
{
}
