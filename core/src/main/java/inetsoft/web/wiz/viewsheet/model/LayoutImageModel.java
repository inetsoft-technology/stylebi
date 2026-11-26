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

import java.util.Map;

/**
 * The picture and options of a layout-only image object, as the Composer's print-layout image
 * dialog shows them.
 *
 * <p>{@code image} is the stored image value ({@code ^UPLOADED^name}, {@code ^SKIN^name},
 * {@code ^SERVER^path}), {@code null} while no picture has been set -- such an object prints as an
 * empty box. {@code alpha} is the opacity percentage. {@code insets} (keys {@code top},
 * {@code left}, {@code bottom}, {@code right}) are the scale-9 insets, in effect only when
 * {@code scaleImage} is on and {@code maintainAspectRatio} is off.
 */
public record LayoutImageModel(String image, int alpha, boolean scaleImage,
                               boolean maintainAspectRatio, boolean tile,
                               Map<String, Integer> insets) {
}
