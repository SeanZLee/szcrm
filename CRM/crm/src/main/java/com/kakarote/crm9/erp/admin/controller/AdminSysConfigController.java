package com.kakarote.crm9.erp.admin.controller;

import com.alibaba.fastjson.JSON;
import com.jfinal.aop.Before;
import com.jfinal.aop.Clear;
import com.jfinal.aop.Inject;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.tx.Tx;
import com.kakarote.crm9.common.config.cache.ClusterRedis;
import com.kakarote.crm9.common.config.cache.RedisClusterCache;
import com.kakarote.crm9.erp.admin.entity.AdminConfig;
import com.kakarote.crm9.erp.admin.service.AdminFileService;
import com.kakarote.crm9.utils.BaseUtil;
import com.kakarote.crm9.utils.R;
import com.jfinal.core.Controller;
import com.jfinal.kit.Kv;
import com.jfinal.upload.UploadFile;

import java.util.Map;

/**
 * 系统配置
 * @author hmb
 */
public class AdminSysConfigController extends Controller {

    private static final String SYS_CONFIG_KEY = "SysConfig";

    @Inject
    private AdminFileService adminFileService;

    /**
     * 设置系统配置
     * @author hmb
     */
    @Before(Tx.class)
    public void setSysConfig(){
        Db.delete("DELETE FROM `72crm_admin_config` WHERE `name` = ?",SYS_CONFIG_KEY);
        String prefix=BaseUtil.getDate();
        UploadFile file = getFile("file", prefix);
        Kv kv = getKv();
        if(file!=null){
            R r=adminFileService.upload(file,null,"file","/"+prefix);
            kv.set("logo",r.get("url"));
        }
        AdminConfig adminConfig=new AdminConfig();
        adminConfig.setStatus(1);
        adminConfig.setName(SYS_CONFIG_KEY);
        adminConfig.setValue(JSON.toJSONString(kv));
        adminConfig.setDescription("系统LOGO配置");
        adminConfig.save();
        renderJson(R.ok());
    }

    /**
     * 查询系统配置
     * @author hmb
     */
    public void querySysConfig(){
        AdminConfig adminConfig=AdminConfig.dao.findFirst("SELECT * FROM `72crm_admin_config` where name=?",SYS_CONFIG_KEY);
        if (adminConfig == null){
            renderJson(R.ok().put("data",Kv.by("logo","").set("name","")));
            return;
        }
        String data = adminConfig.getValue();
        Map map = JSON.parseObject(data, Map.class);
        renderJson(R.ok().put("data",map));
    }
}
