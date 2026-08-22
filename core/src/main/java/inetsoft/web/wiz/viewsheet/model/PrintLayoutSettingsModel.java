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
 * A print layout's paper/margin/scale settings, as {@code get_layout} reports them for a
 * {@code type == "print"} layout -- the read-side mirror of the patch keys {@code
 * set_print_layout} ({@code PrintDeviceLayoutPropertyService#applyPrintLayoutPatch}) accepts.
 *
 * <p>{@link LayoutModel#printSettings()} is {@code null} when the viewsheet's print layout has
 * never been configured ({@code screensPane().getPrintLayout()} reads back {@code null}) --
 * never a zeroed-out instance of this record -- so a caller can tell "unconfigured" apart from
 * "configured with every field at its default".
 */
public record PrintLayoutSettingsModel(String paperSize, double marginTop, double marginLeft,
                                        double marginBottom, double marginRight,
                                        float footerFromEdge, float headerFromEdge,
                                        boolean landscape, float scaleFont, int numberingStart,
                                        double customWidth, double customHeight, String units) {
}
