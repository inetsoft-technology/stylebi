package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
   @JsonSubTypes.Type(ChartBinding.class),
   @JsonSubTypes.Type(TableBinding.class),
   @JsonSubTypes.Type(CrosstabBinding.class),
   @JsonSubTypes.Type(OutputBinding.class),
   @JsonSubTypes.Type(ImageBinding.class)
})
public interface BindingInfo {
}
