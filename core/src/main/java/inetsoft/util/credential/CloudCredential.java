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

package inetsoft.util.credential;

import inetsoft.util.*;
import org.slf4j.LoggerFactory;

public interface CloudCredential extends Credential {
   default void fetchCredential() {
      if(Tool.isEmptyString(getId())) {
         // no credential configured, which is not a failure
         setCredentialUnavailable(false);
         return;
      }

      try {
         Credential credential = getSecretsManager().getCredential(this);

         if(credential != null) {
            refreshCredential(credential);
            setCredentialUnavailable(false);
         }
         else {
            setCredentialUnavailable(true);
         }
      }
      catch(Exception ex) {
         // a SecretsUnavailableException is already logged with the full context by the secrets
         // manager, anything else is not. it is not propagated so that the data source still
         // loads and remains listable and editable, the unavailable state is reported when the
         // credential is actually used.
         if(!(ex instanceof SecretsUnavailableException)) {
            LoggerFactory.getLogger(CloudCredential.class).error(
               "Failed to fetch secret \"{}\" for {}", getId(), getClass().getSimpleName(), ex);
         }

         setCredentialUnavailable(true);
      }
   }

   /**
    * Determines if this credential references a secret that could not be resolved from the
    * secrets manager. This is distinct from an empty credential id, which means that no
    * credential is configured at all.
    *
    * @return <tt>true</tt> if the credential is configured but unavailable.
    */
   default boolean isCredentialUnavailable() {
      return false;
   }

   /**
    * Sets whether the referenced secret could not be resolved from the secrets manager.
    */
   default void setCredentialUnavailable(boolean unavailable) {
   }

   /**
    * Determines if enough time has passed since the last failed fetch to attempt another one.
    * This keeps a data source whose secret is genuinely missing from calling the secrets manager,
    * and logging the failure, on every single connection attempt.
    */
   default boolean isFetchRetryDue() {
      return true;
   }

   /**
    * Re-fetches the credential if a previous attempt failed, so that a corrected secret is
    * picked up without requiring the data source to be reloaded. At most one fetch is made, and
    * only if the retry interval has elapsed.
    *
    * @return <tt>true</tt> if the credential is usable, either because it resolved or because
    *         none is configured.
    */
   default boolean ensureCredentialAvailable() {
      if(Tool.isEmptyString(getId()) || !isCredentialUnavailable()) {
         return true;
      }

      // this credential is shared by every thread that connects to the data source that owns it.
      // only one thread re-fetches, and the others carry on with the current state rather than
      // blocking on a secrets manager call that may be slow or hung.
      if(isFetchRetryDue() && beginFetchRetry()) {
         try {
            fetchCredential();
         }
         finally {
            endFetchRetry();
         }
      }

      return !isCredentialUnavailable();
   }

   /**
    * Claims the right to re-fetch this credential.
    *
    * @return <tt>true</tt> if the caller may fetch, <tt>false</tt> if another thread already is.
    */
   default boolean beginFetchRetry() {
      return true;
   }

   /**
    * Releases the claim taken by {@link #beginFetchRetry()}.
    */
   default void endFetchRetry() {
   }

   void refreshCredential(Credential credential);

   default AbstractSecretsManager getSecretsManager() {
      PasswordEncryption encryption = PasswordEncryption.newInstance();

      if(encryption instanceof AbstractSecretsManager) {
         return (AbstractSecretsManager) encryption;
      }

      throw new RuntimeException("There's no secrets manager for cloud password credential!");
   }

   /**
    * Create a new local credential.
    */
   Credential createLocal();

   /**
    * Copy the credential to the new local credential.
    *
    * @param credential new local credential.
    */
   void copyToLocal(Credential credential);
}
