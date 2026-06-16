package inetsoft.web.wiz.worksheet;

/** A single-use authorization binding an agent pairing code to an open worksheet runtime. */
public record PairingGrant(String code, String runtimeId, String ownerIdentity,
                           String socketSessionId, long createdAt, long ttlMillis)
{
   public boolean isExpired(long now) {
      return now - createdAt > ttlMillis;
   }
}
