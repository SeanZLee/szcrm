package com.kakarote.crm9.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.jfinal.kit.PropKit;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import com.kakarote.crm9.common.config.JfinalConfig;
import com.kakarote.crm9.common.config.cache.CaffeineCache;
import com.kakarote.crm9.common.config.cache.ClusterRedis;
import com.kakarote.crm9.common.config.cache.RedisClusterCache;
import com.kakarote.crm9.erp.admin.entity.AdminUser;
import com.jfinal.kit.Prop;
import org.apache.commons.codec.digest.DigestUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.Date;

public class BaseUtil {
    private static ThreadLocal<HttpServletRequest> threadLocal = new ThreadLocal<>();

    private static final String USER_TOKEN="ADMIN_USER_TOKEN:";

    /**
     * 获取当前系统是开发开始正式
     *
     * @return true代表为真
     */
    public static boolean isDevelop() {
        return JfinalConfig.prop.getBoolean("jfinal.devMode", Boolean.TRUE);
    }

    /**
     * 获取当前是否是windows系统
     *
     * @return true代表为真
     */
    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    /**
     * 签名数据
     *
     * @param key  key
     * @param salt 盐
     * @return 加密后的字符串
     */
    public static String sign(String key, String salt) {
        return DigestUtils.md5Hex((key + "erp" + salt).getBytes());
    }

    /**
     * 验证签名是否正确
     *
     * @param key  key
     * @param salt 盐
     * @param sign 签名
     * @return 是否正确 true为正确
     */
    public static boolean verify(String key, String salt, String sign) {
        return sign.equals(sign(key, salt));
    }

    /**
     * 获取当前年月的字符串
     *
     * @return yyyyMMdd
     */
    public static String getDate() {
        return DateUtil.format(new Date(), "yyyyMMdd");
    }

    public static String getIpAddress() {
        Prop prop = PropKit.use("config/undertow.txt");
        try {
            if (isDevelop()) {
                return "http://" + InetAddress.getLocalHost().getHostAddress() + ":" + prop.get("undertow.port", "8080") + "/";
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        HttpServletRequest request=getRequest();
        /**
         * TODO nginx反向代理下手动增加一个请求头 proxy_set_header proxy_url "代理映射路径";
         * 如 location /api/ {
         *     proxy_set_header proxy_url "api"
         *     proxy_redirect off;
         * 	   proxy_set_header Host $host:$server_port;
         *     proxy_set_header X-Real-IP $remote_addr;
         * 	   proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
         * 	   proxy_set_header X-Forwarded-Proto  $scheme;
         * 	   proxy_connect_timeout 60;
         * 	   proxy_send_timeout 120;
         * 	   proxy_read_timeout 120;
         *     proxy_pass http://127.0.0.1:8080/;
         *    }
         */
        String proxy = request.getHeader("proxy_url") != null ? "/" + request.getHeader("proxy_url") : "";
        String path= "http://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + proxy + "/";
        return JfinalConfig.prop.get("jfinal.uploadPath",path);
    }


    public static String getLoginAddress(HttpServletRequest request){
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip.contains(",")) {
            return ip.split(",")[0];
        } else {
            return ip;
        }
    }

    public static String getLoginAddress() {
        return getLoginAddress(getRequest());
    }

    public static void setRequest(HttpServletRequest request) {
        threadLocal.set(request);
    }

    public static HttpServletRequest getRequest() {
        return threadLocal.get();
    }


    public static AdminUser getUser() {
        return ClusterRedis.use().get(getToken());
    }

    public static Long getUserId() {
        return getUser().getUserId();
    }

    public static void removeThreadLocal() {
        threadLocal.remove();
    }

    public static String getToken() {
        return getToken(getRequest());
    }

    public static String getToken(HttpServletRequest request) {
        String token=request.getHeader("Admin-Token") != null ? request.getHeader("Admin-Token") :"";
        if(StrUtil.isEmpty(token)&&request.getAttribute("get")!=null){
            token=getCookieValue(request,"Admin-Token");
        }
        return token;
    }

    public static Record getHost() {
        return getRequest()!=null?(Record) getRequest().getAttribute("hosts"):CaffeineCache.ME.get("hosts");
    }

    public static Integer getCompanyId() {
        return getHost().getInt("id");
    }

    public static void setHost(String username) {
        Record record = Db.findFirstByCache("hosts", "queryByPhone:" + username, "SELECT * FROM 72crm_admin_hosts WHERE host=(SELECT host FROM 72crm_admin_hosts WHERE phone=?) ORDER BY super desc LIMIT 1", username);
        if (record != null) {
            setHost(record);
        }
    }

    public static void setHost(Record record){
        if(getRequest() != null){
            getRequest().setAttribute("hosts", record);
        }else {
            CaffeineCache .ME.put("hosts", record);
        }
    }


    public static void setHost() {
        setHost(BaseUtil.getUser().getUsername());
    }
    public static boolean isSuperUser(){
        return isSuperUser(getUserId());
    }
    public static boolean isSuperUser(Long userId){
        return getSuperUser().equals(userId);
    }
    public static Long getSuperUser(){
        return Db.findFirstByCache("hosts","queryByHost:"+getHost().getStr("host"),"SELECT * FROM `72crm_admin_user` where username=? LIMIT 1",getHost().getStr("phone")).getLong("user_id");
    }

    public static Integer getSuperRole(){
        return Db.findFirstByCache("hosts","queryRole:"+getHost().getStr("host"),"SELECT * FROM `72crm_admin_role` where remark='ADMIN' LIMIT 1").getInt("role_id");
    }

    public static void userExit(Long userId){
        RedisClusterCache redis = ClusterRedis.use();
        if(redis.exists(USER_TOKEN+userId)){
            String token=redis.get(USER_TOKEN+userId);
            redis.del(USER_TOKEN+userId);
            redis.del(token);
        }
    }
    public static void userExpire(String token){
        RedisClusterCache redis = ClusterRedis.use();
        if(redis.exists(token)){
            redis.expire(token,3600);
            redis.expire(USER_TOKEN+getUserId(),3600);
        }
    }
    public static void setToken(Long userId,String token){
        userExit(userId);
        RedisClusterCache redis = ClusterRedis.use();
        if(redis.exists(token)){
            redis.setex(USER_TOKEN+userId,redis.ttl(token).intValue(),token);
        }

    }
    public static String getCookieValue(HttpServletRequest request, String name) {
        String cookieValue = "";
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    cookieValue = cookie.getValue();
                    break;
                }
            }
        }
        return cookieValue;
    }
}
