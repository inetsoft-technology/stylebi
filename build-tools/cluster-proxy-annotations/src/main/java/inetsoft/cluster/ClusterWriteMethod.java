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
package inetsoft.cluster;

import java.lang.annotation.*;

/**
 * Marks a {@link ClusterProxyMethod} method as a mutation — one that modifies the
 * RuntimeSheet state and requires the session to be written back to the distributed cache.
 * {@code SheetWriteAspect} intercepts calls to methods with this annotation and
 * triggers an async write-back via {@code WorksheetService.writeSheet()}.
 *
 * <p><strong>Contract:</strong> the annotated method must have a
 * {@code @ClusterProxyKey String} as its first parameter. {@code SheetWriteAspect}
 * extracts the sheet ID from {@code args[0]}; if {@code args[0]} is not a
 * {@code String}, the write is skipped and a warning is logged.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ClusterWriteMethod {
}
