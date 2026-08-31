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
 * One object's placement inside a layout, reported in <b>both</b> coordinate spaces so a caller
 * can tell them apart:
 *
 * <ul>
 *   <li>{@code layoutX/Y/Width/Height} — where this layout places the object, read directly off
 *   the layout's own {@code VSAssemblyLayout} entry.</li>
 *   <li>{@code viewsheetX/Y/Width/Height} — where the <b>same</b> assembly currently sits on the
 *   live (Master) viewsheet, read off the master, never a layout preview clone — independent of
 *   whatever this or any other layout says, and 0 for a layout-only object (e.g. a Text/Image/
 *   PageBreak object that exists only inside this layout and has no assembly on the viewsheet at
 *   all).</li>
 * </ul>
 *
 * <p>{@code tableLayout} is this object's own {@code VSAssemblyLayout.getTableLayout()} value
 * (the same int {@code set_layout_table_options} writes) — reported for every object regardless
 * of {@code supportsTableLayout}, since the field always has a value even where this tool refuses
 * to *write* it.
 *
 * <p>{@code pageIndex} is the page this object lands on within a <b>print</b> layout, derived
 * from its layout position and the print layout's page size — {@code null} for a device-layout
 * object (a device layout has no pages) and also {@code null} for a print layout whose
 * {@code PrintInfo} has never been configured (mirroring {@link LayoutModel#printSettings()}'s
 * own null-vs-zeroed convention, rather than a sentinel int that could be mistaken for a real
 * page).
 */
public record LayoutObjectModel(String name, int layoutX, int layoutY, int layoutWidth,
                                int layoutHeight, int viewsheetX, int viewsheetY,
                                int viewsheetWidth, int viewsheetHeight,
                                boolean supportsTableLayout, int tableLayout, Integer pageIndex) {
}
