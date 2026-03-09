package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.*;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
    @JsonSubTypes.Type(DimensionFieldInfo.class),
    @JsonSubTypes.Type(MeasureFieldInfo.class)
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimpleFieldInfo extends FieldInfo {
   public Integer getOrder() {
      return order;
   }

   public void setOrder(Integer order) {
      this.order = order;
   }

   private Integer order;
}
