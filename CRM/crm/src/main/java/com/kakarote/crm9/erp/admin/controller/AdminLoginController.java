package com.kakarote.crm9.erp.admin.controller;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import com.jfinal.aop.Before;
import com.jfinal.core.paragetter.Para;
import com.jfinal.kit.Kv;
import com.jfinal.plugin.activerecord.Record;
import com.jfinal.plugin.activerecord.tx.Tx;
import com.kakarote.crm9.common.config.cache.ClusterRedis;
import com.kakarote.crm9.common.config.cache.RedisClusterCache;
import com.kakarote.crm9.common.constant.BaseConstant;
import com.kakarote.crm9.erp.admin.entity.*;
import com.kakarote.crm9.erp.admin.service.AdminRoleService;
import com.kakarote.crm9.utils.BaseUtil;
import com.kakarote.crm9.utils.R;
import com.jfinal.aop.Clear;
import com.jfinal.aop.Inject;
import com.jfinal.core.Controller;
import com.jfinal.log.Log;
import com.jfinal.plugin.activerecord.Db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * 用户登录
 *
 * @author z
 */
@Clear
public class AdminLoginController extends Controller {

    @Inject
    private AdminRoleService adminRoleService;

    public void index() {
        redirect("/index.html");
    }

    /**
     * @param username 用户名
     * @param password 密码
     * @author zhangzhiwei
     * 用户登录
     */
    public void login(@Para("username") String username, @Para("password") String password) {
        String key = BaseConstant.USER_LOGIN_ERROR_KEY+username;
        RedisClusterCache redis = ClusterRedis.use();
        long beforeTime = System.currentTimeMillis() - 60 * 5 * 1000;
        if(redis.exists(key)){
            if(redis.zcount(key, beforeTime, System.currentTimeMillis()) >= 5){
                Set zrevrange = redis.zrevrange(key, 4, 5);
                Long time = (Long) zrevrange.iterator().next() + 60 * 5 * 1000;
                long expire = (time - System.currentTimeMillis()) / 1000;
                renderJson(R.error("密码错误次数过多，请等" + expire + "秒后在重试！"));
                return;
            }
        }
        redis.zadd(key, System.currentTimeMillis(), System.currentTimeMillis());
        if (StrUtil.isEmpty(username) || StrUtil.isEmpty(password)) {
            renderJson(R.error("请输入用户名和密码！"));
            return;
        }
        BaseUtil.setRequest(getRequest());
        BaseUtil.setHost(username);
        AdminUser user = AdminUser.dao.findFirst(Db.getSql("admin.user.queryByUserName"), username.trim());
        if (user == null) {
            renderJson(R.error("用户名或密码错误！"));
        } else {
            if (user.getStatus() == 0) {
                renderJson(R.error("账户被禁用！"));
                return;
            }
            if (BaseUtil.verify(username + password, user.getSalt(), user.getPassword())) {
                if (user.getStatus().equals(2)) {
                    user.setStatus(1);
                }
                redis.del(key);
                String token = IdUtil.simpleUUID();
                user.setLastLoginIp(BaseUtil.getLoginAddress(getRequest()));
                user.setLastLoginTime(new Date());
                user.update();
                user.setRoles(adminRoleService.queryRoleIdsByUserId(user.getUserId()));
                redis.setex(token, 3600, user);
                BaseUtil.setToken(user.getUserId(),token);
                user.remove("password", "salt");
                setCookie("Admin-Token", token, 360000);
                renderJson(R.ok().put("Admin-Token", token).put("user", user).put("auth", adminRoleService.auth(user.getUserId())));
            } else {
                Log.getLog(getClass()).warn("用户登录失败");
                renderJson(R.error("用户名或密码错误！"));
            }
        }
    }


    public void logout() {
        String token = BaseUtil.getToken(getRequest());
        if (StrUtil.isNotEmpty(token)) {
            ClusterRedis.use().del(token);
            removeCookie("Admin-Token");
        }
        renderJson(R.ok());
    }

    public void version() {
        renderJson(R.ok().put("name", BaseConstant.NAME).put("version", BaseConstant.VERSION));
    }

    /**
     * 注册用户
     * @param phone 手机号
     * @param password 密码
     */
    @Before(Tx.class)
    public void register(@Para("phone") String phone, @Para("password") String password) {
        if (StrUtil.isEmpty(phone) || StrUtil.isEmpty(password)) {
            renderJson(R.error("手机号或密码不可为空！"));
            return;
        }
        if(!ReUtil.isMatch("^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[~!@#$%^&*()_+`\\-={}:\";'<>?,.\\/]).{8,20}$", password)){
            renderJson(R.error("密码必须由 8-20位字母、数字、特殊符号线组成"));
            return;
        }
        BaseUtil.setRequest(getRequest());
        Record record = Db.findFirstByCache("hosts", "queryByPhone:"+phone, "SELECT * FROM `72crm_admin_hosts` where phone=?", phone);
        if (record != null) {
            renderJson(R.error("手机号已被注册"));
        } else {
            record=new Record();
            record.set("phone",phone).set("super",1).set("create_time",new Date()).set("host",IdUtil.simpleUUID());
            boolean b=Db.save("72crm_admin_hosts","id",record);
            BaseUtil.setRequest(getRequest());
            BaseUtil.setHost(record);
            if (b) {
                String salt=IdUtil.simpleUUID();
                AdminDept adminDept=new AdminDept();
                adminDept.setPid(0);
                adminDept.setName("办公室");
                adminDept.setNum(1);
                adminDept.save();
                AdminUser adminUser=new AdminUser();
                adminUser.setUsername(phone);
                adminUser.setPassword(BaseUtil.sign(phone+password,salt));
                adminUser.setSalt(salt);
                adminUser.setCreateTime(new Date());
                adminUser.setRealname("admin");
                adminUser.setMobile(phone);
                adminUser.setDeptId(adminDept.getDeptId());
                adminUser.setPost("标准岗位");
                adminUser.setStatus(1);
                adminUser.setParentId(0L);
                adminUser.save();
                AdminRole adminRole=new AdminRole();
                adminRole.setRoleName("超级管理员");
                adminRole.setRoleType(1);
                adminRole.setRoleType(1);
                adminRole.setStatus(1);
                adminRole.setDataType(5);
                adminRole.setRemark("ADMIN");
                adminRole.save();
                AdminUserRole adminUserRole=new AdminUserRole();
                adminUserRole.setRoleId(adminRole.getRoleId());
                adminUserRole.setUserId(adminUser.getUserId());
                adminUserRole.save();
                saveCloud();
                redirect("/index.html");
            } else {
                renderJson(R.error("请稍后再试"));
            }
        }
    }


    public void ping() {
        List<String> arrays = new ArrayList<>();
        Connection connection = null;
        try {
            connection = Db.use().getConfig().getConnection();
            if (connection != null) {
                arrays.add("数据库连接成功");
            }
        } catch (Exception e) {
            arrays.add("数据库连接异常");
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }

        }
        try {
            if (ClusterRedis.use()!=null) {
                arrays.add("Redis配置成功");
            } else {
                arrays.add("Redis配置失败");
            }
        } catch (Exception e) {
            arrays.add("Redis配置失败");
        }
        renderJson(R.ok().put("data", arrays));
    }

    private void saveCloud(){
        List<String> sqlList=new ArrayList<>();
        Integer hostId=BaseUtil.getHost().getInt("id");
        sqlList.add("INSERT INTO `72crm_admin_config` VALUES (null, '1', 'customerPoolSetting', '', '客户公海规则设置','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_config` VALUES (null, '1', 'expiringContractDays', '3', '合同到期提醒','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_config` VALUES (null, '0', 'customerPoolSettingDealDays', '3', '客户公海规则设置未成交天数','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_config` VALUES (null, '0', 'customerPoolSettingFollowupDays', '31', '客户公海规则设置未跟进天数','"+hostId+"')");

        sqlList.add("INSERT INTO `72crm_admin_config` VALUES (null, '0', 'followRecordOption', '打电话', '跟进记录选项','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_config` VALUES (null, '0', 'followRecordOption', '发邮件', '跟进记录选项','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_config` VALUES (null, '0', 'followRecordOption', '发短信', '跟进记录选项','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_config` VALUES (null, '0', 'followRecordOption', '见面拜访', '跟进记录选项','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_config` VALUES (null, '0', 'followRecordOption', '活动', '跟进记录选项','"+hostId+"')");

        sqlList.add("INSERT INTO `72crm_admin_examine` VALUES (null, '2', '2', '回款审批流程', null, null, '2019-05-11 16:27:35', '3', '2019-05-11 16:27:35', '3', '1', '','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_examine` VALUES (null, '1', '2', '合同审批流程', null, null, '2019-05-11 16:27:11', '3', '2019-05-11 16:27:44', '3', '1', '说明','"+hostId+"')");

        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'leads_name', '线索名称', 1, 1, NULL, NULL, 255, '', 0, 1, 0, NULL, 1, '2019-6-28 11:36:44', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, '线索来源', '线索来源', 3, 1, NULL, NULL, NULL, '', 0, 0, 1, '促销,搜索引擎,广告,转介绍,线上注册,线上询价,预约上门,陌拜,电话咨询,邮件咨询', 1, '2019-6-26 11:49:16', NULL, 0,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'telephone', '电话', 1, 1, NULL, NULL, 255, '', 0, 0, 2, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'mobile', '手机', 7, 1, NULL, NULL, 255, '', 0, 0, 3, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, '客户行业', '客户行业', 3, 1, NULL, NULL, NULL, '', 0, 0, 4, 'IT,金融业,房地产,商业服务,运输/物流,生产,政府,文化传媒', 1, '2019-6-26 11:49:16', NULL, 0,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, '客户级别', '客户级别', 3, 1, NULL, NULL, NULL, '', 0, 0, 5, 'A（重点客户）,B（普通客户）,C（非优先客户）', 1, '2019-6-26 11:49:23', NULL, 0,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'address', '地址', 1, 1, NULL, NULL, 255, '', 0, 0, 6, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'next_time', '下次联系时间', 13, 1, NULL, NULL, NULL, '', 0, 0, 7, NULL, 1, '2019-6-28 11:36:56', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'remark', '备注', 1, 1, NULL, NULL, 255, '', 0, 0, 8, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'customer_name', '客户名称', 1, 2, NULL, NULL, 255, '', 0, 0, 0, NULL, 1, '2019-6-28 11:37:02', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'mobile', '手机', 7, 2, NULL, NULL, 255, '', 0, 0, 1, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'telephone', '电话', 1, 2, NULL, NULL, 255, '', 0, 0, 2, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'website', '网址', 1, 2, NULL, NULL, 255, '', 0, 0, 6, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'deal_status', '成交状态', 3, 2, NULL, NULL, NULL, '', 0, 0, 7, '未成交,已成交', 1, '2019-6-28 11:37:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'next_time', '下次联系时间', 13, 2, NULL, NULL, NULL, '', 0, 0, 8, NULL, 1, '2019-6-28 11:37:12', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'remark', '备注', 1, 2, NULL, NULL, 255, '', 0, 0, 9, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, '客户级别', '客户级别', 3, 2, NULL, NULL, NULL, '', 0, 0, 5, 'A（重点客户）,B（普通客户）,C（非优先客户）', 1, '2019-6-26 11:49:25', NULL, 0,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, '客户来源', '客户来源', 3, 2, NULL, NULL, NULL, '', 0, 0, 3, '促销,搜索引擎,广告,转介绍,线上注册,线上询价,预约上门,陌拜,电话咨询,邮件咨询', 1, '2019-6-26 11:49:26', NULL, 0,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, '客户行业', '客户行业', 3, 2, NULL, NULL, NULL, '', 0, 0, 4, 'IT,金融业,房地产,商业服务,运输/物流,生产,政府,文化传媒', 1, '2019-6-26 11:49:27', NULL, 0,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'name', '姓名', 1, 3, NULL, NULL, 255, '', 1, 1, 0, NULL, 1, '2019-6-27 13:43:30', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'customer_id', '客户名称', 15, 3, NULL, NULL, NULL, '', 0, 1, 1, NULL, 1, '2019-6-28 11:37:18', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'mobile', '手机', 7, 3, NULL, NULL, 255, '', 0, 0, 2, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'telephone', '电话', 1, 3, NULL, NULL, 255, '', 0, 0, 3, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'email', '电子邮箱', 14, 3, NULL, NULL, 255, '', 0, 0, 4, NULL, 1, '2019-6-26 17:08:40', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'post', '职务', 1, 3, NULL, NULL, 255, '', 0, 0, 5, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'address', '地址', 1, 3, NULL, NULL, 255, '', 0, 0, 6, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'next_time', '下次联系时间', 13, 3, NULL, NULL, NULL, '', 0, 0, 7, NULL, 1, '2019-6-28 11:37:24', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'remark', '备注', 1, 3, NULL, NULL, 255, '', 0, 0, 8, NULL, 1, '2019-6-26 13:23:49', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, '是否关键决策人', '是否关键决策人', 3, 3, NULL, NULL, NULL, '', 0, 0, 9, '是,否', 1, '2019-6-26 13:23:38', NULL, 0,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, '性别', '性别', 3, 3, NULL, NULL, NULL, '', 0, 0, 10, '男,女', 1, '2019-6-26 11:45:07', NULL, 0,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'name', '产品名称', 1, 4, NULL, NULL, 255, '', 0, 0, 0, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'category_id', '产品类型', 19, 4, NULL, NULL, NULL, '', 0, 0, 1, NULL, 1, '2019-6-28 11:37:29', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'num', '产品编码', 5, 4, NULL, NULL, 255, '', 0, 0, 2, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'price', '价格', 6, 4, NULL, NULL, 255, '', 0, 0, 3, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'description', '产品描述', 1, 4, NULL, NULL, 255, '', 0, 0, 4, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, '是否上下架', '是否上下架', 3, 4, NULL, NULL, NULL, '', 0, 1, 5, '上架,下架', 1, '2019-6-26 13:23:57', NULL, 0,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, '单位', '单位', 3, 4, NULL, NULL, NULL, '', 0, 0, 6, '个,块,只,把,枚,瓶,盒,台,吨,千克,米,箱,套', 1, '2019-6-26 13:24:03', NULL, 0,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'business_name', '商机名称', 1, 5, NULL, NULL, 255, '', 0, 0, 0, NULL, 1, '2019-6-28 11:37:36', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'customer_id', '客户名称', 15, 5, NULL, NULL, NULL, '', 0, 0, 1, NULL, 1, '2019-6-28 11:37:39', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'money', '商机金额', 6, 5, NULL, NULL, 255, '', 0, 0, 2, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'deal_date', '预计成交日期', 13, 5, NULL, NULL, NULL, '', 0, 0, 3, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'remark', '备注', 1, 5, NULL, NULL, 255, '', 0, 0, 4, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'num', '回款编号', 5, 6, NULL, NULL, 255, '', 0, 0, 0, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'name', '合同名称', 1, 6, NULL, NULL, 255, '', 0, 0, 1, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'customer_id', '客户名称', 15, 6, NULL, NULL, NULL, '', 0, 0, 2, NULL, 1, '2019-6-28 11:37:51', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'business_id', '商机名称', 16, 6, NULL, NULL, NULL, '', 0, 0, 3, NULL, 1, '2019-6-28 11:38:00', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'order_date', '下单时间', 4, 6, NULL, NULL, NULL, '', 0, 0, 4, NULL, 1, '2019-6-28 11:38:03', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'money', '合同金额', 6, 6, NULL, NULL, 255, '', 0, 0, 5, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'start_time', '合同开始时间', 4, 6, NULL, NULL, NULL, '', 0, 0, 6, NULL, 1, '2019-6-28 11:38:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'end_tme', '合同结束时间', 4, 6, NULL, NULL, NULL, '', 0, 0, 7, NULL, 1, '2019-6-28 11:38:11', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'contacts_id', '客户签约人', 17, 6, NULL, NULL, NULL, '', 0, 0, 8, NULL, 1, '2019-6-28 11:38:14', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'company_user_id', '公司签约人', 10, 6, NULL, NULL, NULL, '', 0, 0, 9, NULL, 1, '2019-6-28 11:38:21', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'remark', '备注', 1, 6, NULL, NULL, 255, '', 0, 0, 10, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'number', '回款编号', 5, 7, NULL, NULL, 255, '', 0, 0, 0, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'customer_id', '客户名称', 15, 7, NULL, NULL, NULL, '', 0, 0, 1, NULL, 1, '2019-6-28 11:38:28', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'contract_id', '合同编号', 20, 7, NULL, NULL, NULL, '', 0, 0, 2, NULL, 1, '2019-6-28 11:38:33', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'return_time', '回款日期', 4, 7, NULL, NULL, NULL, '', 0, 0, 3, NULL, 1, '2019-6-28 11:38:38', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'money', '回款金额', 6, 7, NULL, NULL, 255, '', 0, 0, 4, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'plan_id', '期数', 21, 7, NULL, NULL, NULL, '', 0, 0, 5, NULL, 1, '2019-6-28 11:38:45', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'remark', '备注', 1, 7, NULL, NULL, 255, '', 0, 0, 6, NULL, 1, '2019-6-26 11:45:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, '回款方式', '回款方式', 3, 7, NULL, NULL, NULL, '', 0, 0, 7, '支票,现金,邮政汇款,电汇,网上转账,支付宝,微信支付,其他', 1, '2019-6-26 13:24:11', NULL, 0,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'customer_id', '客户名称', 15, 8, NULL, NULL, NULL, '', 0, 0, 1, NULL, 1, '2019-6-28 16:52:13', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'contract_id', '合同编号', 20, 8, NULL, NULL, 11, '', 0, 0, 2, NULL, 1, '2019-6-28 16:55:17', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'money', '计划回款金额', 6, 8, NULL, NULL, NULL, '', 0, 0, 3, NULL, 1, '2019-6-28 16:53:04', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'return_date', '计划回款日期', 4, 8, NULL, NULL, NULL, '', 0, 0, 4, NULL, 1, '2019-6-28 16:54:01', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'remind', '提前几天提醒', 5, 8, NULL, NULL, 11, '', 0, 0, 5, NULL, 1, '2019-6-28 16:55:13', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'remark', '备注', 2, 8, NULL, NULL, 1000, '', 0, 0, 6, NULL, 1, '2019-6-28 16:55:07', NULL, 1,'"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, '邮箱', '邮箱', 14, 1, NULL, '', NULL, '', 0, 0, 9, NULL, 0, '2019-6-29 18:12:45', NULL, 0,'"+hostId+"')");

        sqlList.add("INSERT INTO `72crm_admin_role` VALUES (null, '系统设置管理员', '1', null, '1', '2','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_role` VALUES (null, '部门与员工管理员', '1', null, '1', '5','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_role` VALUES (null, '审批流管理员', '1', null, '1', '5','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_role` VALUES (null, '工作台管理员', '1', null, '1', '5','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_role` VALUES (null, '客户管理员', '1', null, '1', '5','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_role` VALUES (null, '公告管理员', '1', null, '1', '5','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_role` VALUES (null, '销售经理角色', '2', null, '1', '5','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_role` VALUES (null, '行政管理', '3', null, '1', '1','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_role` VALUES (null, '财务角色', '4', null, '1', '1','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_role` VALUES (null, '技术研发', '5', null, '1', '1','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_role` VALUES (null, '销售员角色', '2', null, '1', '5','"+hostId+"')");

        sqlList.add("INSERT INTO `72crm_crm_business_type` VALUES (null, '默认商机组', '', '3', '2019-05-11 16:25:09', null, '1','"+hostId+"')");
        sqlList.add("set @business_status=(SELECT LAST_INSERT_ID())");
        sqlList.add("INSERT INTO `72crm_crm_business_status` VALUES (null, (select @business_status), '验证客户', '20', '1','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_crm_business_status` VALUES (null, (select @business_status), '需求分析', '30', '2','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_crm_business_status` VALUES (null, (select @business_status), '方案/报价', '80', '3','"+hostId+"')");

        sqlList.add("INSERT INTO `72crm_crm_product_category` VALUES (null, '默认', '0','"+hostId+"')");

        sqlList.add("INSERT INTO `72crm_oa_examine_category` VALUES (null, '普通审批', '普通审批', '1', '3', '1', '1', '2', '', '', '2019-04-26 15:06:34', '2019-04-26 15:06:34', '0', null, null,'"+hostId+"')");
        sqlList.add("set @category_id=(SELECT LAST_INSERT_ID())");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'content', '审批内容', '1', '10', null, '', null, '', '0', '1', '0', null, '1',null,(select @category_id), '1','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'remark', '备注', '2', '10', null, '', '1000', '', '0', '0', '1', null, '1',null,(select @category_id), '1','"+hostId+"')");

        sqlList.add("INSERT INTO `72crm_oa_examine_category` VALUES (null, '请假审批', '请假审批', '2', '3', '1', '1', '2', '', '', '2019-04-17 18:52:44', '2019-04-17 18:52:44', '0', null, null,'"+hostId+"')");
        sqlList.add("set @category_id=(SELECT LAST_INSERT_ID())");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'type_id', '请假类型', '3', '10', null, '', null, '年假', '0', '1', '0', '年假,事假,病假,产假,调休,婚假,丧假,其他', '1', null, (select @category_id), '1','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'content', '审批内容', '1', '10', null, '', null, '', '0', '1', '1', null, '1',null, (select @category_id), '1','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'start_time', '开始时间', '13', '10', null, '', null, '', '0', '1', '2', null, '1',null, (select @category_id), '1','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'end_time', '结束时间', '13', '10', null, '', null, '', '0', '1', '3', null, '1',null, (select @category_id), '1','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'duration', '时长', '6', '10', null, '', null, '', '0', '1', '4', null, '1', null, (select @category_id), '1','"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'remark', '备注', '2', '10', null, '', '1000', '', '0', '0', '5', null, '1', null, (select @category_id), '1','"+hostId+"')");

        sqlList.add("INSERT INTO `72crm_oa_examine_category` VALUES (null, '出差审批', '出差审批', '3', '3', '1', '1', '2', '', '', '2019-04-17 18:52:50', '2019-04-17 18:52:50', '0', null, null,'"+hostId+"')");
        sqlList.add("set @category_id=(SELECT LAST_INSERT_ID())");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'content', '出差事由', '1', '10', null, '', null, '', '0', '1', '0', null, '1', '2019-06-30 18:13:08', (select @category_id), '1', '"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'remark', '备注', '2', '10', null, '', '1000', '', '0', '0', '1', null, '1', '2019-06-30 18:13:08', (select @category_id), '1', '"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'duration', '出差总天数', '6', '10', null, '', null, '', '0', '1', '2', null, '1', '2019-06-30 18:13:08', (select @category_id), '1', '"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'cause', '行程明细', '22', '10', null, '', null, '', '0', '1', '2', null, '1', '2019-06-30 18:13:08', (select @category_id), '1', '"+hostId+"')");

        sqlList.add("INSERT INTO `72crm_oa_examine_category` VALUES (null, '加班审批', '加班审批', '4', '3', '1', '1', '2', '', '', '2019-04-17 18:52:59', '2019-04-17 18:52:59', '0', null, null,'"+hostId+"')");
        sqlList.add("set @category_id=(SELECT LAST_INSERT_ID())");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'content', '加班原因', '1', '10', null, '', null, '', '0', '1', '0', null, '1', '2019-06-30 18:13:08', (select @category_id), '1', '"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'start_time', '开始时间', '13', '10', null, '', null, '', '0', '1', '1', null, '1', '2019-06-30 18:13:08', (select @category_id), '1', '"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'end_time', '结束时间', '13', '10', null, '', null, '', '0', '1', '2', null, '1', '2019-06-30 18:13:08', (select @category_id), '1', '"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'duration', '加班总天数', '6', '10', null, '', null, '', '0', '1', '3', null, '1', '2019-06-30 18:13:08', (select @category_id), '1', '"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'remark', '备注', '2', '10', null, '', '1000', '', '0', '0', '4', null, '1', '2019-06-30 18:13:08', (select @category_id), '1', '"+hostId+"')");

        sqlList.add("INSERT INTO `72crm_oa_examine_category` VALUES (null, '差旅报销', '差旅报销', '5', '3', '1', '1', '2', '', '', '2019-04-17 18:53:13', '2019-04-17 18:53:13', '0', null, null,'"+hostId+"')");
        sqlList.add("set @category_id=(SELECT LAST_INSERT_ID())");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'content', '差旅事由', '1', '10', null, '', null, '', '0', '1', '0', null, '1', '2019-06-30 18:13:08', (select @category_id), '1', '"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'money', '报销总金额', '6', '10', null, '', '0', '', '0', '1', '1', null, '1', '2019-06-30 18:13:08', (select @category_id), '1', '"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'remark', '备注', '2', '10', null, '', '1000', '', '0', '0', '2', null, '1', '2019-06-30 18:13:08', (select @category_id), '1', '"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'cause', '费用明细', '23', '10', null, '', '1000', '', '0', '0', '2', null, '1', '2019-06-30 18:13:08', (select @category_id), '1', '"+hostId+"')");

        sqlList.add("INSERT INTO `72crm_oa_examine_category` VALUES (null, '借款申请', '借款申请', '6', '3', '1', '1', '2', '', '', '2019-04-17 18:54:44', '2019-04-17 18:54:44', '0', null, null,'"+hostId+"')");
        sqlList.add("set @category_id=(SELECT LAST_INSERT_ID())");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'content', '借款事由', '1', '10', null, '', null, '', '0', '1', '0', null, '1', '2019-06-30 18:13:08', (select @category_id), '1', '"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'money', '借款金额（元）', '6', '10', null, '', '0', '', '0', '1', '1', null, '1', '2019-06-30 18:13:08', (select @category_id), '0', '"+hostId+"')");
        sqlList.add("INSERT INTO `72crm_admin_field` VALUES (null, 'remark', '备注', '2', '10', null, '', '1000', '', '0', '0', '2', null, '1', '2019-06-30 18:13:08', (select @category_id), '1', '"+hostId+"')");
        Db.batch(sqlList,200);
    }


    /**
     * 重置密码
     * @param phone 需要重置的手机号
     * @param smsCode 短信验证码
     * @param password 新的密码，新密码验证自行处理
     */
    public void resetpwd(@Para("phone") String phone, @Para("smscode") String smsCode,@Para("password") String password) {
        RedisClusterCache redis = ClusterRedis.use();
        //验证短信验证码是否正确，或者其他的验证逻辑
        if (StrUtil.isEmpty(smsCode) || !smsCode.equals(redis.get("send:sms:" + phone))) {
            renderJson(R.error("手机验证码出错！"));
            return;
        }
        //查询需要重置的账号
        AdminUser adminUser = AdminUser.dao.findFirst(Db.getSql("admin.user.queryByUserName"),phone);
        //用户不存在直接返回，发送短信时也可以验证下
        if (adminUser == null) {
            renderJson(R.error("用户不存在！"));
            return;
        }
        //设置密码
        adminUser.setPassword(BaseUtil.sign(phone + password, adminUser.getSalt()));
        adminUser.setStatus(1);
        adminUser.update();
        renderJson(R.ok());
    }

}
