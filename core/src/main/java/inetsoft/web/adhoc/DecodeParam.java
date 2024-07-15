/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.adhoc;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DecodeParam {
   /**
    * The name of the request parameter to be decoded.
    */
   String value() default "";

   /**
    * Whether the parameter is required.
    * <p>Default is {@code true}, leading to an exception thrown in case
    * of the parameter missing in the request. Switch this to {@code false}
    * if you prefer a {@code null} in case of the parameter missing.
    * <p>Alternatively, provide a {@link #defaultValue() defaultValue},
    * which implicitly sets this flag to {@code false}.
    */
   boolean required() default true;
}