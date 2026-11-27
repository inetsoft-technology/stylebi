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
 * The outcome of loading an XMLA data source's cube metadata.
 *
 * @param ok        whether the cubes were loaded and saved.
 * @param reason    null when ok; otherwise {@code DATASOURCE_LOST}, {@code CONNECTION_FAILED},
 *                  {@code ENDPOINT_ERROR} (the endpoint answered with an HTTP error),
 *                  {@code NO_CUBES}, {@code CATALOG_REQUIRED} or {@code PERSIST_FAILED}.
 * @param cubeCount the number of cubes saved, null unless ok.
 * @param url       the endpoint that was contacted, null when none was.
 * @param message   the cause when the refresh failed.
 */
public record WizXmlaRefreshResult(boolean ok, String reason, Integer cubeCount, String url,
                                   String message)
{
   public static WizXmlaRefreshResult failed(String reason) {
      return new WizXmlaRefreshResult(false, reason, null, null, null);
   }

   public static final String DATASOURCE_LOST = "DATASOURCE_LOST";
}
