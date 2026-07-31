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
package inetsoft.web.admin.general;

/**
 * Outcome of a storage backup.
 *
 * @param status the human-readable status message, as before.
 * @param path   the external-storage path of the backup that was written, or {@code null} when the
 *               backup failed. Callers that must reference the artifact later - the admin-chat
 *               feature records it in its audit trail - need the actual path, which was previously
 *               computed inside doBackup and discarded.
 */
public record BackupResult(String status, String path) {
}
