package com.kakarote.crm9.common.config;

import cn.hutool.core.util.ClassLoaderUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.jfinal.aop.Aop;
import com.kakarote.crm9.common.config.cache.CaffeineCache;
import com.kakarote.crm9.common.config.druid.DruidConfig;
import com.kakarote.crm9.common.config.druid.DruidFilter;
import com.kakarote.crm9.common.config.druid.MysqlDialectTo;
import com.kakarote.crm9.common.config.json.ErpJsonFactory;
import com.kakarote.crm9.common.config.paragetter.BasePageRequest;
import com.kakarote.crm9.common.config.paragetter.PageParaGetter;
import com.kakarote.crm9.common.config.plugin.RedisClusterPlugin;
import com.kakarote.crm9.common.config.render.ErpRenderFactory;
import com.kakarote.crm9.common.constant.BaseConstant;
import com.kakarote.crm9.common.interceptor.AuthInterceptor;
import com.kakarote.crm9.common.interceptor.ErpInterceptor;
import com.kakarote.crm9.erp._MappingKit;
import com.kakarote.crm9.erp.admin.common.AdminRouter;
import com.kakarote.crm9.erp.admin.service.AdminFieldService;
import com.kakarote.crm9.erp.bi.common.BiRouter;
import com.kakarote.crm9.erp.crm.common.CrmDirective;
import com.kakarote.crm9.erp.crm.common.CrmRouter;
import com.kakarote.crm9.erp.oa.common.OaRouter;
import com.kakarote.crm9.erp.work.common.WorkRouter;
import com.jfinal.config.*;
import com.jfinal.core.paragetter.ParaProcessorBuilder;
import com.jfinal.kit.PathKit;
import com.jfinal.kit.Prop;
import com.jfinal.kit.PropKit;
import com.jfinal.plugin.activerecord.ActiveRecordPlugin;
import com.jfinal.plugin.cron4j.Cron4jPlugin;
import com.jfinal.plugin.druid.DruidPlugin;
import com.jfinal.plugin.druid.DruidStatViewHandler;
import com.jfinal.render.RenderManager;
import com.jfinal.template.Engine;
import redis.clients.jedis.HostAndPort;

import java.io.File;
import java.nio.charset.Charset;
import java.util.HashSet;

/**
 * API 引导式配置
 */
public class JfinalConfig extends JFinalConfig {

    public static Prop prop = PropKit.use("config/crm9-config.txt");

    /**
     * 配置常量
     */
    @Override
    public void configConstant(Constants me) {
        me.setDevMode(prop.getBoolean("jfinal.devMode", true));
        me.setInjectDependency(true);
        //设置上传文件到哪个目录
        if(ClassLoaderUtil.isPresent("com.jfinal.server.undertow.UndertowServer")){
            me.setBaseUploadPath(BaseConstant.UPLOAD_PATH);
            me.setBaseDownloadPath(BaseConstant.UPLOAD_PATH);
        }
        me.setJsonFactory(new ErpJsonFactory());
        //限制上传100M
        me.setMaxPostSize(104857600);
    }

    /**
     * 配置路由
     */
    @Override
    public void configRoute(Routes me) {
        me.add(new AdminRouter());
        me.add(new BiRouter());
        me.add(new CrmRouter());
        me.add(new OaRouter());
        me.add(new WorkRouter());
    }

    @Override
    public void configEngine(Engine me) {

    }

    /**
     * 配置插件
     */
    @Override
    public void configPlugin(Plugins me) {
        ParaProcessorBuilder.me.regist(BasePageRequest.class, PageParaGetter.class, null);
        // 配置 druid 数据库连接池插件
        DruidPlugin druidPlugin = createDruidPlugin();
        druidPlugin.setFilters(null).addFilter(new DruidFilter());
        druidPlugin.setInitialSize(0);
        druidPlugin.setMinIdle(0);
        druidPlugin.setMaxActive(2000);
        druidPlugin.setTimeBetweenEvictionRunsMillis(5000);
        druidPlugin.setValidationQuery("select 1");
        druidPlugin.setTimeBetweenEvictionRunsMillis(60000);
        druidPlugin.setMinEvictableIdleTimeMillis(30000);
        me.add(druidPlugin);
        // 配置ActiveRecord插件
        ActiveRecordPlugin arp = new ActiveRecordPlugin(druidPlugin);
        arp.setCache(CaffeineCache.ME);
        arp.setDialect(new MysqlDialectTo());
        arp.setShowSql(true);
        arp.getEngine().addDirective("CrmAuth", CrmDirective.class);
        me.add(arp);
        //扫描sql模板
        getSqlTemplate(PathKit.getRootClassPath() + "/template", arp);
        //Redis以及缓存插件
        JSONObject json= JSONUtil.readJSONObject(new File(PathKit.getRootClassPath()+"/config/redis.json"), Charset.defaultCharset());
        System.out.println(json.toString());
        HashSet<HostAndPort> host=new HashSet<>();
        json.getJSONArray("host").forEach(obj->{
            String[] hosts=obj.toString().split(":");
            host.add(new HostAndPort(hosts[0],Integer.valueOf(hosts[1])));
        });
        //Redis以及缓存插件
        RedisClusterPlugin redisSentinelPlugin=new RedisClusterPlugin(host);
        me.add(redisSentinelPlugin);
        //cron定时器
        me.add(new Cron4jPlugin(PropKit.use("config/cron4j.txt")));
        //model映射
        _MappingKit.mapping(arp);
    }

    public static DruidPlugin createDruidPlugin() {
        return new DruidPlugin(prop.get("mysql.jdbcUrl"), prop.get("mysql.user"), prop.get("mysql.password").trim()).setInitialSize(1).setMinIdle(1).setMaxActive(2000).setTimeBetweenEvictionRunsMillis(5000).setValidationQuery("select 1").setTimeBetweenEvictionRunsMillis(60000).setMinEvictableIdleTimeMillis(30000).setFilters("stat,wall");
    }

    /**
     * 配置全局拦截器
     */
    @Override
    public void configInterceptor(Interceptors me) {
        //添加全局拦截器
        me.addGlobalActionInterceptor(new ErpInterceptor());
        me.add(new AuthInterceptor());
    }

    /**
     * 配置处理器
     */
    @Override
    public void configHandler(Handlers me) {
        //配置数据库监控
        me.add(new DruidStatViewHandler("/druid", new DruidConfig()));
        //自定义渲染工厂
        RenderManager.me().setRenderFactory(new ErpRenderFactory());
    }

    @Override
    public void onStart() {
    }

    @Override
    public void onStop() {

    }

    private void getSqlTemplate(String path, ActiveRecordPlugin arp) {
        File file = new File(path);
        if (file.exists()) {
            File[] files = file.listFiles();
            if (files != null && files.length > 0) {
                for (File childFile : files) {
                    if (childFile.isDirectory()) {
                        getSqlTemplate(childFile.getAbsolutePath(), arp);
                    } else {
                        if (childFile.getName().toLowerCase().endsWith(".sql")) {
                            arp.addSqlTemplate(childFile.getAbsolutePath().replace(PathKit.getRootClassPath(), "").replace("\\", "/"));
                        }
                    }
                }
            }
        }
    }
}