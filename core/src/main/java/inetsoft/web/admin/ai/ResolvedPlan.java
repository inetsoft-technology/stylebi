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
package inetsoft.web.admin.ai;

import java.util.List;

/**
 * A fully resolved change plan plus the hash an {@code apply} must echo.
 *
 * @param requiresStorageBackup true when any change needs a Tier-2 backup to be reversible.
 * @param requiresAgentSignoff  true when any change is high risk.
 * @param planHash              SHA-256 over the canonical plan, including CURRENT values, so an
 *                              apply after drift is refused.
 */
public record ResolvedPlan(String task, List<PlanChange> changes, boolean requiresStorageBackup,
                           boolean requiresAgentSignoff, String planHash)
{
}
