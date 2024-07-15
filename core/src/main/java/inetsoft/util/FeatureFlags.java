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
package inetsoft.util;

import inetsoft.sree.SreeEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.lang.annotation.*;
import java.util.*;

/**
 * Feature flags can be used to control whether a feature is enabled/disabled. By default
 * the features are disabled. To enable a feature, add a property in sree.properties
 * with the following syntax:
 * <p>
 * feature.flag.{FEATURE_FLAG_NAME}=true
 * <p>
 * <p>
 * For example, if a feature flag name is "parabox.graph" then the property to enable the
 * feature would look as such:
 * <p>
 * feature.flag.parabox.graph=true
 */
public class FeatureFlags {
   public static FeatureFlags getInstance() {
      return SingletonManager.getInstance(FeatureFlags.class);
   }

   /**
    * Check if feature with the given flag is enabled.
    *
    * @param flag the feature flag.
    *
    * @return true if the feature is enabled; otherwise false
    */
   public boolean isFeatureEnabled(Value flag) {
      return SreeEnv.getBooleanProperty(PROPERTY_PREFIX + flag.getPropertyValue());
   }

   /**
    * Get a list of enabled features
    *
    * @return enabled feature list
    */
   public EnumSet<Value> getEnabledFeatures() {
      EnumSet<Value> flags = EnumSet.noneOf(Value.class);
      Properties props = SreeEnv.getProperties();

      for(String name : props.stringPropertyNames()) {
         if(name.startsWith(PROPERTY_PREFIX) && SreeEnv.getBooleanProperty(name)) {
            try {
               flags.add(Value.forPropertyValue(name.substring(PROPERTY_PREFIX.length())));
            }
            catch(Exception e) {
               LOG.error(e.getMessage(), e);
            }
         }
      }

      return flags;
   }

   private static final String PROPERTY_PREFIX = "feature.flag.";

   /**
    * Enumeration of feature flag values.
    */
   public enum Value {
      PLACEHOLDER_DONT_USE("placeholder-dont-use"),
      FEATURE_58615_CHANGE_QUERY_DATASOURCE("feature-58615-change-query-datasource"),
      IMPORT_TARGET_SOURCE_AUTO_RENAME("import-target-source-auto-rename"),
      FEATURE_63601_UX_WORKFLOW("feature-63601-ux-workflow");

      private final String propertyValue;

      Value(String propertyValue) {
         this.propertyValue = propertyValue;
      }

      public String getPropertyValue() {
         return propertyValue;
      }

      public static Value forPropertyValue(String propertyValue) {
         for(Value value : values()) {
            if(value.getPropertyValue().equals(propertyValue)) {
               return value;
            }
         }

         throw new IllegalArgumentException("Invalid feature flag property: " + propertyValue);
      }
   }

   /**
    * Annotation that marks code that should be removed when a feature flag is merged into the main
    * product code.
    */
   @Target({
      ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR,
      ElementType.LOCAL_VARIABLE
   })
   @Retention(RetentionPolicy.SOURCE)
   public @interface RemoveWhenMerged {
      Value value();
   }

   /**
    * Annotation that is used to enable Spring beans if a feature flag is enabled.
    */
   @Target({ ElementType.TYPE, ElementType.METHOD})
   @Retention(RetentionPolicy.RUNTIME)
   @Conditional(IsEnabled.class)
   public @interface Enabled {
      /**
       * The required feature flag.
       */
      Value value();

      /**
       * Flag indicating if the flag is required to be enabled (true) or disabled (false).
       */
      boolean enabled() default true;
   }

   public static final class IsEnabled implements Condition {
      @Override
      public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
         Map<String, Object> attrs = metadata.getAnnotationAttributes(Enabled.class.getName());

         if(attrs != null) {
            Value flag = (Value) attrs.get("value");
            boolean enabled = getInstance().isFeatureEnabled(flag);
            boolean negated = !((Boolean) attrs.get("enabled"));
            return enabled && !negated || !enabled && negated;
         }

         return true;
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(FeatureFlags.class);
}
