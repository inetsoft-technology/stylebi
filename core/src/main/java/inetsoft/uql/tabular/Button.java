/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.tabular;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation that represents a button. It's the value of button attribute
 * of View1.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Button {
   /**
    * The type of the button
    */
   ButtonType type() default ButtonType.URL;

   /**
    * The style for the authorization button.
    */
   ButtonStyle style() default ButtonStyle.PLAIN;

   /**
    * The url that this buttons points to
    */
   String url() default "";

   /**
    * The name of the method that is called when the button is clicked
    */
   String method() default "";

   /**
    * The OAuth button configuration.
    */
   OAuth oauth() default @OAuth;

   /**
    * The list of properties on which the enabled status depends.
    */
   String[] dependsOn() default {};

   /**
    * The name of the method used to determine if the editor should be enabled.
    */
   String enabledMethod() default "";

   /**
    * Annotation used to configure the OAuth authorization properties.
    */
   @interface OAuth {
      /**
       * The name of the property containing the user.
       */
      String user() default "user";

      /**
       * The name of the property containing the user's password.
       */
      String password() default "password";

      /**
       * The name of the pre-configured service to use.
       */
      String serviceName() default "";

      /**
       * The name of the property containing the client ID.
       */
      String clientId() default "clientId";

      /**
       * The name of the property containing the client secret.
       */
      String clientSecret() default "clientSecret";

      /**
       * The name of the property containing the scope.
       */
      String scope() default "scope";

      /**
       * The name of the property containing the authorization URI.
       */
      String authorizationUri() default "authorizationUri";

      /**
       * The name of the property containing the token URI.
       */
      String tokenUri() default "tokenUri";

      /**
       * The name of the property containing the OAuth flags.
       */
      String flags() default "oauthFlags";

      /**
       * Additional parameters received on the redirect request from the OAuth authorize endpoint
       * that should be included in the request to the OAuth token endpoint. The standard
       * {@code code} and {@code state} parameters do not need to be mapped.
       */
      Parameter[] additionalParameters() default {};
   }

   /**
    * Annotation used to add an additional OAuth parameter.
    */
   @interface Parameter {
      /**
       * The name of the parameter on the redirect request from the OAuth server.
       */
      String from() default "";

      /**
       * The name of the parameter sent to the OAuth token endpoint.
       */
      String to() default "";
   }
}
