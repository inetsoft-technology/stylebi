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

   /**
    * Overrides the global loading-mask watchdog timeout (in milliseconds, see
    * {@code loadingmask.watchdog.timeout}) for this endpoint. A value of {@code 0} disables
    * the watchdog for this endpoint entirely -- use this for endpoints that, like the
    * product's own {@code query.runtime.timeout=0} default, are expected to legitimately run
    * unbounded (e.g. executing a runtime viewsheet/worksheet query). The default, {@code -1},
    * means "use the global {@code loadingmask.watchdog.timeout} property".
    */
   long watchdogTimeout() default -1;
}
