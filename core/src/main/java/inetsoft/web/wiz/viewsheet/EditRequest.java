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
package inetsoft.web.wiz.viewsheet;

import java.util.List;

/**
 * One structural edit. {@code op} is the discriminator; the remaining fields are populated
 * per op and validated by {@link ViewsheetEditService}, which fails loud rather than
 * defaulting a missing value.
 */
public record EditRequest(String op,
                          String assembly,
                          Integer x,
                          Integer y,
                          Integer width,
                          Integer height,
                          Integer zIndex,
                          String title,
                          Boolean locked,
                          String container,
                          List<String> assemblies,
                          String newName,
                          Integer type,
                          String axis) {}
