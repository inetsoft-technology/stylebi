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
package inetsoft.uql.rest.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory cache of the access tokens issued by the OAuth 2.0 client credentials grant.
 *
 * The data source and the authenticator are recreated for every HTTP request, so the tokens are
 * cached here, keyed by the values that determine the token. A changed secret, scope or token URI
 * therefore uses a new entry. The tokens are never written to the data source, because a new one
 * can always be requested with the client credentials.
 *
 * Only one thread requests a token for a key at a time, the others wait for and then use the
 * token it received, so that concurrent queries do not each make a token request. If the request
 * fails, the threads that were waiting for it fail with the same error instead of each repeating
 * it.
 *
 * The cache is local to the node, each cluster node requests its own token.
 */
public class ClientCredentialsTokenCache {
   ClientCredentialsTokenCache(Clock clock) {
      this.clock = clock;
   }

   public static ClientCredentialsTokenCache getInstance() {
      return INSTANCE;
   }

   /**
    * Gets a valid access token, requesting a new one if there is no cached token or it has
    * expired.
    *
    * @param request the token request.
    * @param fetcher requests a new token from the token endpoint.
    *
    * @return the access token.
    */
   public String getAccessToken(ClientCredentialsRequest request, TokenFetcher fetcher)
      throws IOException, InterruptedException
   {
      final Holder holder = tokens.computeIfAbsent(request.cacheKey(), k -> new Holder());
      // read the cached token without the lock, every API request gets the token and they
      // shouldn't wait for each other or for a token request in progress while it's valid
      final CachedToken cached = holder.current;

      if(cached != null && clock.instant().isBefore(cached.expiration())) {
         return cached.accessToken();
      }

      final int failures = holder.failures;
      final String accessToken;

      // wait interruptibly, so that a cancelled or timed out query isn't held up by a token
      // request in progress on another thread
      holder.lock.lockInterruptibly();

      try {
         final Instant now = clock.instant();
         final CachedToken current = holder.current;

         if(current != null && now.isBefore(current.expiration())) {
            return current.accessToken();
         }

         // a token request failed while this thread was waiting, report that failure instead of
         // making each waiting thread repeat the request in turn
         if(holder.failures != failures && holder.lastFailure != null) {
            throw new IOException(holder.lastFailure.getMessage(), holder.lastFailure);
         }

         holder.current = null;
         final TokenResponse response;

         try {
            response = fetcher.fetch(request);

            if(response == null || response.accessToken() == null ||
               response.accessToken().isEmpty())
            {
               throw new IOException(
                  "The token endpoint did not return an access token: " + request.tokenUri());
            }
         }
         catch(IOException e) {
            holder.lastFailure = e;
            holder.failures++;
            throw e;
         }

         accessToken = response.accessToken();
         holder.current = new CachedToken(
            accessToken, getExpiration(now, response.expiresIn()), holder.rejected ? now : null);
         holder.lastFailure = null;
         holder.rejected = false;
      }
      finally {
         holder.lock.unlock();
      }

      removeExpired();
      return accessToken;
   }

   /**
    * Discards a token that the server rejected, so that the next request uses a new token.
    *
    * The token is only discarded if it is still the cached token, so that when several requests
    * are rejected at once a token that another thread has already replaced it with isn't
    * discarded too. A token that was itself requested because the previous one was rejected isn't
    * discarded again for a while, because a rejection that a new token doesn't fix has another
    * cause, such as an insufficient scope for the endpoint, and requesting a new token for every
    * rejected request would only load the authorization server.
    *
    * @return true if the request should be retried, i.e. a different token will be used.
    */
   public boolean invalidate(ClientCredentialsRequest request, String rejectedToken)
      throws InterruptedException
   {
      final Holder holder = tokens.get(request.cacheKey());

      if(holder == null) {
         return false;
      }

      holder.lock.lockInterruptibly();

      try {
         final CachedToken current = holder.current;

         if(current == null) {
            // another thread discarded the rejected token and is requesting a new one
            return holder.rejected;
         }

         if(!Objects.equals(rejectedToken, current.accessToken())) {
            // already replaced by another thread, retry with the replacement
            return true;
         }

         if(current.renewedAfterRejection() != null && clock.instant().isBefore(
            current.renewedAfterRejection().plus(REJECTION_RETRY_INTERVAL)))
         {
            return false;
         }

         holder.current = null;
         holder.rejected = true;
         return true;
      }
      finally {
         holder.lock.unlock();
      }
   }

   /**
    * Verifies the client credentials by requesting a new token, without using or replacing the
    * cached token. The data source status refresh calls this for every data source, so a
    * successful verification is reused for a short time instead of requesting a token for every
    * call. A failure isn't reused, so that testing again after correcting the client in the
    * authorization server shows the result straight away.
    */
   public void verify(ClientCredentialsRequest request, TokenFetcher fetcher)
      throws IOException, InterruptedException
   {
      final Holder holder = tokens.computeIfAbsent(request.cacheKey(), k -> new Holder());
      holder.verifyLock.lockInterruptibly();

      try {
         final Instant now = clock.instant();

         if(holder.verified != null && now.isBefore(holder.verified.plus(VERIFY_INTERVAL))) {
            return;
         }

         holder.verified = null;
         final TokenResponse response = fetcher.fetch(request);

         if(response == null || response.accessToken() == null ||
            response.accessToken().isEmpty())
         {
            throw new IOException(
               "The token endpoint did not return an access token: " + request.tokenUri());
         }

         holder.verified = now;
      }
      finally {
         holder.verifyLock.unlock();
      }
   }

   private Instant getExpiration(Instant now, long expiresIn) {
      final Duration lifetime =
         expiresIn > 0 ? Duration.ofSeconds(expiresIn) : DEFAULT_LIFETIME;
      // renew the token before it expires so that it isn't rejected while a request is in flight
      final Duration margin = lifetime.compareTo(EXPIRATION_MARGIN.multipliedBy(2)) > 0 ?
         EXPIRATION_MARGIN : lifetime.dividedBy(2);
      return now.plus(lifetime).minus(margin);
   }

   private void removeExpired() {
      if(tokens.size() > MAX_SIZE) {
         final Instant now = clock.instant();
         // skip the locked entries, a token is being requested or rejected for them
         tokens.values().removeIf(holder -> {
            if(!holder.lock.tryLock()) {
               return false;
            }

            try {
               final CachedToken current = holder.current;
               return current == null || !now.isBefore(current.expiration());
            }
            finally {
               holder.lock.unlock();
            }
         });
      }
   }

   int size() {
      return tokens.size();
   }

   /**
    * @return the number of threads waiting to get or discard the token for a request.
    */
   int getWaitingThreadCount(ClientCredentialsRequest request) {
      final Holder holder = tokens.get(request.cacheKey());
      return holder == null ? 0 : holder.lock.getQueueLength();
   }

   /**
    * The parameters of a client credentials token request.
    */
   public record ClientCredentialsRequest(String tokenUri, String clientId, String clientSecret,
                                          String scope, String audience,
                                          ClientAuthMethod clientAuthMethod)
   {
      String cacheKey() {
         // the secret is hashed so that it isn't held in the key, and each part is length
         // prefixed so that the concatenation is unambiguous
         return encode(tokenUri) + encode(clientId) + encode(hash(clientSecret)) +
            encode(scope) + encode(audience) + encode(String.valueOf(clientAuthMethod));
      }

      @Override
      public String toString() {
         // never include the secret
         return "ClientCredentialsRequest[tokenUri=" + tokenUri + ", clientId=" + clientId +
            ", scope=" + scope + ", audience=" + audience +
            ", clientAuthMethod=" + clientAuthMethod + "]";
      }

      private static String encode(String value) {
         return value == null ? "-1|" : value.length() + "|" + value;
      }

      private static String hash(String value) {
         if(value == null) {
            return null;
         }

         try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
               .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
         }
         catch(NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
         }
      }
   }

   /**
    * The token endpoint response.
    *
    * @param accessToken the access token.
    * @param expiresIn   the lifetime of the token in seconds, or 0 if the endpoint did not
    *                    return one.
    */
   public record TokenResponse(String accessToken, long expiresIn) {
      @Override
      public String toString() {
         return "TokenResponse[expiresIn=" + expiresIn + "]";
      }
   }

   @FunctionalInterface
   public interface TokenFetcher {
      TokenResponse fetch(ClientCredentialsRequest request)
         throws IOException, InterruptedException;
   }

   /**
    * A cached token.
    *
    * @param renewedAfterRejection when the token was requested because the previous one was
    *                              rejected, or null if it wasn't.
    */
   private record CachedToken(String accessToken, Instant expiration,
                              Instant renewedAfterRejection)
   {
      @Override
      public String toString() {
         return "CachedToken[expiration=" + expiration + "]";
      }
   }

   private static final class Holder {
      private final ReentrantLock lock = new ReentrantLock();
      // read without the lock, written while holding it
      private volatile CachedToken current;
      // the previous token was rejected, set until the next token is received
      private boolean rejected;
      private IOException lastFailure;
      // the number of failed token requests, read before waiting for the lock
      private volatile int failures;
      // the last successful verification of the credentials, guarded by verifyLock
      private final ReentrantLock verifyLock = new ReentrantLock();
      private Instant verified;
   }

   private final Clock clock;
   private final Map<String, Holder> tokens = new ConcurrentHashMap<>();

   // used when the token endpoint does not return expires_in
   static final Duration DEFAULT_LIFETIME = Duration.ofMinutes(5);
   static final Duration EXPIRATION_MARGIN = Duration.ofSeconds(30);
   // how long before a token requested after a rejection can itself be discarded on a rejection
   static final Duration REJECTION_RETRY_INTERVAL = Duration.ofMinutes(5);
   // how long a successful verification of the credentials is reused
   static final Duration VERIFY_INTERVAL = Duration.ofMinutes(1);
   private static final int MAX_SIZE = 1000;
   private static final ClientCredentialsTokenCache INSTANCE =
      new ClientCredentialsTokenCache(Clock.systemUTC());
}
