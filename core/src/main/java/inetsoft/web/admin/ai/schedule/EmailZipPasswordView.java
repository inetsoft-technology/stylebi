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
 * What the read side reports about the zip password of one viewsheet action: only whether one is
 * stored, never the value. Mirrors what the portal Action tab shows as configured.
 *
 * @param actionIndex   the index of the action in the task's action list.
 * @param passwordSet   whether a password (not a secret id) is stored.
 * @param useCredential whether the password comes from a vault secret.
 * @param secretId      the vault secret id, {@code null} when not used.
 */
public record EmailZipPasswordView(int actionIndex, boolean passwordSet, boolean useCredential,
                                   String secretId)
{
}
