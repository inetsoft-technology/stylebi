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
package inetsoft.web.wiz.request;

/**
 * Body of {@code POST /api/wiz/tabular/probe/open}.
 *
 * @param datasource full repository path of the connector instance the probe run is for. The
 *                   temporary worksheet itself is not bound to a data source — it is named here so
 *                   the READ gate can run before a runtime is opened, rather than one probe later.
 */
public record WizTabularProbeOpenRequest(String datasource) {
}
