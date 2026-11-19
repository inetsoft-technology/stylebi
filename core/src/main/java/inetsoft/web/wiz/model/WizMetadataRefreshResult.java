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


package inetsoft.web.wiz.model;

/**
 * The outcome of refreshing a data source's metadata.
 *
 * @param ok      whether the refresh ran.
 * @param reason  null when ok; {@code DATASOURCE_LOST} (no data source at the path) or
 *                {@code UNKNOWN} (the engine call threw; see message).
 * @param message the engine's text when {@code UNKNOWN}.
 */
public record WizMetadataRefreshResult(boolean ok, String reason, String message) {
   public static final String DATASOURCE_LOST = "DATASOURCE_LOST";
   public static final String UNKNOWN = "UNKNOWN";
}
