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
package inetsoft.web.viewsheet;

import java.lang.annotation.*;

/**
 * Annotation that is used to clear loading after controller execution.
 *
 * @since 12.3
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoadingMask {
   /**
    * Flag that controls if the loading mask will always be displayed for the endpoint, regardless
    * of the execution time.
    */
   boolean value() default false;

   /**
    * Flag that indicates if the annotated method calls an asynchronous proxy method.
    */
   boolean asyncProxy() default false;
}
