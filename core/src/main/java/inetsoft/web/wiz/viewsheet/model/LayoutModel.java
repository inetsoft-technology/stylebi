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

import java.util.List;

/**
 * One print or device layout, as the agent sees it — {@code get_layout}'s full response.
 *
 * <p>{@code mobileOnly}/{@code selectedDevices} are {@code null} for a print layout ({@code type
 * == "print"}) — those fields exist only on a {@code ViewsheetLayout} (a device layout), never on
 * a {@code PrintLayout}.
 *
 * <p>{@code printSettings} is the opposite: populated only for a print layout, and only once one
 * has actually been configured on the viewsheet -- {@code null} for a device layout, and also
 * {@code null} for a print layout that has never had {@code set_print_layout} called on it (as
 * opposed to a zeroed-out {@link PrintLayoutSettingsModel}, which would look like a real "every
 * field at its default" configuration).
 */
public record LayoutModel(String name, String type, Boolean mobileOnly,
                           List<String> selectedDevices, PrintLayoutSettingsModel printSettings,
                           List<LayoutObjectModel> objects) {
}
