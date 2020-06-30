package com.kakarote.crm9.common.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
@Inherited
@Documented
public @interface DownloadExcel {
}
