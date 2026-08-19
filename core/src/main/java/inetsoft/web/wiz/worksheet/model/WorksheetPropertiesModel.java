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
package inetsoft.web.wiz.worksheet.model;

/**
 * Read-model DTO for the worksheet's own properties, as seen by the agent.
 *
 * <p>These are exactly the four fields behind the Composer's Worksheet Property dialog
 * ({@code WorksheetOptionPaneModel}), no more: the dialog is what defines what "worksheet
 * property" means in this product, and returning extra {@code WorksheetInfo} internals
 * ({@code designMaxRows}, {@code previewMaxRow}, {@code messageLevels}) would invent a
 * vocabulary the UI does not have.</p>
 *
 * <p>Deliberately a separate DTO from {@link WorksheetModel} rather than a field on it: this
 * describes the sheet, not the sheet's table structure.</p>
 *
 * @param name        the asset name; read-only
 * @param alias       display name; writable via the properties POST
 * @param description free-text description; writable via the properties POST
 * @param dataSource  whether the worksheet is exposed as a report data source; read-only here
 */
public record WorksheetPropertiesModel(String name, String alias, String description,
                                       boolean dataSource) {}
