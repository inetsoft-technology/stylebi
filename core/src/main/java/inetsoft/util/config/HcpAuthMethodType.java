package inetsoft.util.config;

public enum HcpAuthMethodType {
   TOKEN("token"),
   APPROLE("approle"),
   USERPASS("userpass");

   private final String value;

   HcpAuthMethodType(String value) {
      this.value = value;
   }

   public String getValue() {
      return value;
   }

   public static HcpAuthMethodType fromValue(String value) {
      for(HcpAuthMethodType type : values()) {
         if(type.value.equalsIgnoreCase(value)) {
            return type;
         }
      }

      return null;
   }
}
