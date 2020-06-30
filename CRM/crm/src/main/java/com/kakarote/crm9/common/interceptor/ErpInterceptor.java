package com.kakarote.crm9.common.interceptor;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ClassUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.jfinal.aop.Before;
import com.jfinal.aop.Interceptor;
import com.jfinal.aop.Invocation;
import com.jfinal.core.Controller;
import com.jfinal.log.Log;
import com.kakarote.crm9.common.annotation.DownloadExcel;
import com.kakarote.crm9.common.annotation.HttpEnum;
import com.kakarote.crm9.common.annotation.NotNullValidate;
import com.kakarote.crm9.common.config.cache.ClusterRedis;
import com.kakarote.crm9.utils.BaseUtil;
import com.kakarote.crm9.utils.R;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.regex.Pattern;

public class ErpInterceptor implements Interceptor{
    @Override
    public void intercept(Invocation invocation){
        try{
            HttpServletRequest request = invocation.getController().getRequest();
            String method = request.getMethod();
            Controller controller = invocation.getController();
            Before[] befores = invocation.getMethod().getAnnotationsByType(Before.class);
            boolean get = false;
            if(ArrayUtil.isNotEmpty(befores)){
                Class<? extends Interceptor>[] value = befores[0].value();
                get =  ClassUtil.equals(value[0],"GET",true);
            }
            if(!"POST".equalsIgnoreCase(method) && ("GET".equalsIgnoreCase(method) && !get)){
                controller.renderJson(R.error(500, "请使用post请求！"));
                return;
            }

            String token = controller.getHeader("Admin-Token") != null ? controller.getHeader("Admin-Token") : "";
            if(! ClusterRedis.use().exists(token)){
                DownloadExcel downloadExcel=invocation.getMethod().getAnnotation(DownloadExcel.class);
                if(downloadExcel!=null){
                    token=BaseUtil.getCookieValue(request, "Admin-Token");
                    if(! ClusterRedis.use().exists(token)){
                        controller.renderJson(R.error(302, "请先登录！"));
                        return;
                    }
                    request.setAttribute("get",1);
                }else {
                    controller.renderJson(R.error(302, "请先登录！"));
                    return;
                }
            }
            BaseUtil.setRequest(request);
            BaseUtil.setHost();
            BaseUtil.userExpire(token);
            NotNullValidate[] validates = invocation.getMethod().getAnnotationsByType(NotNullValidate.class);
            if(ArrayUtil.isNotEmpty(validates)){
                if(HttpEnum.PARA.equals(validates[0].type())){
                    for(NotNullValidate validate : validates){
                        if(controller.getPara(validate.value()) == null){
                            controller.renderJson(R.error(500, validate.message()));
                            return;
                        }
                    }
                }else if(HttpEnum.JSON.equals(validates[0].type())){
                    JSONObject jsonObject = JSON.parseObject(controller.getRawData());
                    for(NotNullValidate validate : validates){
                        if(! jsonObject.containsKey(validate.value()) || jsonObject.get(validate.value()) == null){
                            controller.renderJson(R.error(500, validate.message()));
                            return;
                        }
                    }
                }
            }
            invocation.invoke();
        }catch(Exception e){
            invocation.getController().renderJson(R.error("服务器响应异常"));
            Log.getLog(invocation.getController().getClass()).error("响应错误", e);
        }finally{
            BaseUtil.removeThreadLocal();
        }

    }
    private Pattern pattern= Pattern.compile("\\b(and|exec|insert|select|drop|grant|alter|delete|update|count|chr|mid|master|truncate|char|declare|or)\\b|(\\*|;|\\+|'|%)");
    /**
     * 判断参数是否含有攻击串
     * @param value
     * @return
     */
    public boolean judgeSQLInject(String value){
        if(value == null || "".equals(value)){
            return false;
        }
        return pattern.matcher(value).find();
    }

    /**
     * 处理跨站xss字符转义
     *
     * @param value
     * @return
     */
    private String clearXss(String value){
        if(value == null || "".equals(value)){
            return value;
        }
        value = value.replaceAll("<", "<").replaceAll(">", ">");
        value = value.replaceAll("\\(", "(").replace("\\)", ")");
        value = value.replaceAll("'", "'");
        value = value.replaceAll("eval\\((.*)\\)", "");
        value = value.replaceAll("[\\\"\\\'][\\s]*javascript:(.*)[\\\"\\\']",
                "\"\"");
        value = value.replace("script", "");
        return value;
    }
}
