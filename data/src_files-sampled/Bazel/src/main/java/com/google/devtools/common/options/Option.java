package com.google.devtools.common.options;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Option {
  String name();

  char abbrev() default '\0';

  String help() default "";

  String valueHelp() default "";

  String defaultValue();

  @Deprecated
  String category() default "misc";

  OptionDocumentationCategory documentationCategory();

  OptionEffectTag[] effectTags();

  OptionMetadataTag[] metadataTags() default {};

  @SuppressWarnings({"unchecked", "rawtypes"})
  Class<? extends Converter> converter() default Converter.class;

  boolean allowMultiple() default false;

  String[] expansion() default {};

  Class<? extends ExpansionFunction> expansionFunction() default ExpansionFunction.class;

  String[] implicitRequirements() default {};

  String deprecationWarning() default "";

  String oldName() default "";
}
