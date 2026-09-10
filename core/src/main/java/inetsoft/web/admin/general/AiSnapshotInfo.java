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
package inetsoft.web.admin.general;

/**
 * One surviving ai-snapshot file for a given admin-chat transaction, as reported by
 * {@link DataSpaceSettingsService#listAiSnapshots}.
 *
 * @param path      the full external-storage path of the snapshot, e.g.
 *                  {@code "ai-snapshots/admin-chg-1-20260101120000.zip"}.
 * @param timestamp the 14-digit {@code yyyyMMddHHmmss} timestamp embedded in the snapshot's
 *                  filename at creation time, as a long.
 */
public record AiSnapshotInfo(String path, long timestamp) {
}
