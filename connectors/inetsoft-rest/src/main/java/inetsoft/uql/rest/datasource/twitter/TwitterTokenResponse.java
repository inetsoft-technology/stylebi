/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
package inetsoft.uql.rest.datasource.twitter;

public class TwitterTokenResponse {
   public TwitterTokenResponse(String oauthToken,
                               String oauthTokenSecret,
                               boolean oauthCallbackConfirmed)
   {
      this.oauth_token = oauthToken;
      this.oauth_token_secret = oauthTokenSecret;
      this.oauth_callback_confirmed = oauthCallbackConfirmed;
   }

   public static TwitterTokenResponse parseFromString(String response) {
      final String oauth_token = parseToken(response, "oauth_token");
      final String oauth_token_secret = parseToken(response, "oauth_token_secret");
      final boolean oauth_callback_confirmed =
         Boolean.parseBoolean(parseToken(response, "oauth_callback_confirmed"));
      return new TwitterTokenResponse(oauth_token, oauth_token_secret, oauth_callback_confirmed);
   }

   private static String parseToken(String response, String parameter) {
      final int index = response.indexOf(parameter);
      final int equalsIndex = response.indexOf('=', index);
      int valueIndex = response.indexOf('&', equalsIndex);

      if(valueIndex == -1) {
         valueIndex = response.length();
      }

      return response.substring(equalsIndex + 1, valueIndex);
   }

   public String getOauthToken() {
      return oauth_token;
   }

   public String getOauthTokenSecret() {
      return oauth_token_secret;
   }

   public boolean isOauthCallbackConfirmed() {
      return oauth_callback_confirmed;
   }

   private String oauth_token;
   private String oauth_token_secret;
   private boolean oauth_callback_confirmed;
}
