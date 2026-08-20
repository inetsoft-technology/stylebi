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
package inetsoft.web.wiz.viewsheet.model;

/**
 * One entry from the read-only device catalogue ({@code DeviceRegistry}) — id/label/min/max
 * width, exactly as {@code DeviceRegistry.getDevices()} reports it. Global Constraint 7: this
 * plugin family reads the catalogue but never writes to it, so there is no corresponding "create
 * a device" model or tool.
 */
public record DeviceCatalogEntry(String id, String label, int minWidth, int maxWidth) {
}
