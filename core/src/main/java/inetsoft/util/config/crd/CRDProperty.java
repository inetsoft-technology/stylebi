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

package inetsoft.util.config.crd;

import java.lang.annotation.*;

/**
 * Annotation used to map a configuration bean property to a CRD property.
 */
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CRDProperty {
   /**
    * The name of the mapped property in the CRD.
    */
   String name() default "";

   /**
    * A flag indicating if the property is volume mount point.
    */
   boolean mountPoint() default false;

   /**
    * A flag indicating if the property may be referenced from a secret.
    */
   boolean secret() default false;

   /**
    * The child fields to be mapped to CRD properties.
    */
   Select[] select() default {};

   /**
    * A description of the CRD property.
    */
   String description() default "";

   /**
    * The allowed values for the CRD property.
    */
   String[] allowedValues() default {};

   /**
    * The type of the property. This is only used in a {@link CRDResource}.
    */
   Class<?>[] type() default {};

   /**
    * Maps a child property to a property in the generated CRD.
    */
   @Target({ElementType.ANNOTATION_TYPE})
   @Retention(RetentionPolicy.RUNTIME)
   @interface Select {
      /**
       * The name of the field in the annotated field's class.
       */
      String field();

      /**
       * The name of the mapped property in the CRD.
       */
      String name() default "";

      /**
       * A flag indicating if the property is volume mount point.
       */
      boolean mountPoint() default false;

      /**
       * A flag indicating if the property may be referenced from a secret.
       */
      boolean secret() default false;

      /**
       * A description of the CRD property.
       */
      String description() default "";

      /**
       * The allowed values for the CRD property.
       */
      String[] allowedValues() default {};
   }
}
