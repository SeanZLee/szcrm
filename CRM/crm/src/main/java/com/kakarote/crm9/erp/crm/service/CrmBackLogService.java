package com.kakarote.crm9.erp.crm.service;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.jfinal.aop.Aop;
import com.jfinal.aop.Inject;
import com.jfinal.kit.Kv;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Page;
import com.jfinal.plugin.activerecord.Record;
import com.kakarote.crm9.common.config.paragetter.BasePageRequest;
import com.kakarote.crm9.common.constant.BaseConstant;
import com.kakarote.crm9.erp.admin.entity.AdminConfig;
import com.kakarote.crm9.erp.admin.service.AdminSceneService;
import com.kakarote.crm9.erp.admin.service.AdminUserService;
import com.kakarote.crm9.utils.BaseUtil;
import com.kakarote.crm9.utils.R;

import java.util.ArrayList;
import java.util.List;

/**
 * @author wyq
 */
public class CrmBackLogService {
    @Inject
    AdminSceneService adminSceneService;

    /**
     * 代办事项数量统计
     */
    public R num(){
        Integer userId = BaseUtil.getUserId().intValue();
        Integer todayCustomer = Db.queryInt(Db.getSql("crm.backLog.todayCustomerNum"),userId);
        Integer followLeads = Db.queryInt(Db.getSql("crm.backLog.followLeadsNum"),userId);
        Integer followCustomer = Db.queryInt(Db.getSql("crm.backLog.followCustomerNum"),userId);
        Integer config = Db.queryInt("select status from 72crm_admin_config where name = 'expiringContractDays'");
        Integer checkReceivables = Db.queryInt(Db.getSql("crm.backLog.checkReceivablesNum"),userId);
        Integer remindReceivablesPlan = Db.queryInt(Db.getSql("crm.backLog.remindReceivablesPlanNum"),userId);
        AdminConfig adminConfig = AdminConfig.dao.findFirst("select * from 72crm_admin_config where name = 'expiringContractDays'");
        Integer endContract = 0;
        if (1 == adminConfig.getStatus()){
            endContract = Db.queryInt(Db.getSql("crm.backLog.endContractNum"),adminConfig.getValue(),userId);
        }
        Kv kv = Kv.by("todayCustomer",todayCustomer).set("followLeads",followLeads).set("followCustomer",followCustomer)
                .set("checkReceivables",checkReceivables).set("remindReceivablesPlan",remindReceivablesPlan).set("endContract",endContract);
        if (config == 1){
            Integer checkContract = Db.queryInt(Db.getSql("crm.backLog.checkContractNum"),userId);
            kv.set("checkContract",checkContract);
        }
        return R.ok().put("data",kv);
    }

    /**
     * 今日需联系客户
     * 今日需要联系为下次联系时间是今天且没有跟进的客户
     * 已逾期是过了下次联系时间那天的且未跟进的客户
     * 已联系是下次联系时间是今天且已经跟进的客户
     */
    public R todayCustomer(BasePageRequest basePageRequest){
        JSONObject jsonObject = basePageRequest.getJsonObject();
        Integer type = jsonObject.getInteger("type");
        Integer isSub = jsonObject.getInteger("isSub");
        StringBuffer stringBuffer = new StringBuffer("select customer_id from 72crm_crm_customer as a where ");
        if (type == 1){
            stringBuffer.append(" a.customer_id not in (IFNULL((select GROUP_CONCAT(types_id) from 72crm_admin_record where types = 'crm_customer' and to_days(create_time) = to_days(now())),0)) and to_days(a.next_time) = to_days(now())");
        }else if (type == 2){
            stringBuffer.append(" a.customer_id not in (IFNULL((select GROUP_CONCAT(types_id) from 72crm_admin_record where types = 'crm_customer' and to_days(create_time) >= to_days(a.next_time)),0)) and to_days(a.next_time) < to_days(now())");
        }else if (type == 3){
            stringBuffer.append(" a.customer_id = any(select types_id from 72crm_admin_record where types = 'crm_customer' and to_days(create_time) = to_days(now())) and to_days(a.next_time) = to_days(now())");
        }else {
            return R.error("type类型不正确");
        }
        if (isSub == 1){
            stringBuffer.append(" and a.owner_user_id = ").append(BaseUtil.getUserId());
        }else if (isSub == 2){
            String ids = adminSceneService.getSubUserId(BaseUtil.getUserId().intValue(), BaseConstant.AUTH_DATA_RECURSION_NUM).substring(1);
            stringBuffer.append(" and a.owner_user_id in (").append(ids).append(")");
        }else {
            return R.error("isSub参数不正确");
        }
        List<Integer> customerIds = Db.query(stringBuffer.toString());
        if(customerIds.size()==0){
            return R.ok().put("data",new ArrayList<>());
        }
        JSONObject data = jsonObject.getJSONObject("data")!=null ? jsonObject.getJSONObject("data") : new JSONObject();
        data.fluentPut("backLog",new JSONObject().fluentPut("name","customer_id").fluentPut("condition","in").fluentPut("value",StrUtil.join(",",customerIds)));
        jsonObject.fluentPut("data",data).fluentPut("search","").fluentPut("type",2);
        basePageRequest.setJsonObject(jsonObject);
        return adminSceneService.getCrmPageList(basePageRequest);
    }

    /**
     * 标记线索为已跟进
     */
    public R setLeadsFollowup(String ids){
        if(StrUtil.isNotEmpty(ids)){
            Db.update(Db.getSqlPara("crm.backLog.setLeadsFollowup",Kv.by("ids",ids.split(","))));
        }
        return R.ok();
    }

    /**
     * 分配给我的线索
     */
    public R followLeads(BasePageRequest basePageRequest){
        JSONObject jsonObject = basePageRequest.getJsonObject();
        Integer type = jsonObject.getInteger("type");
        Integer isSub = jsonObject.getInteger("isSub");
        StringBuffer stringBuffer = new StringBuffer("select leads_id from 72crm_crm_leads as a where");
        if (type == 1){
            stringBuffer.append(" a.followup = 0 and a.is_transform = 0");
        }else if (type == 2){
            stringBuffer.append(" a.followup = 1 and a.is_transform = 0");
        }else {
            return R.error("type类型不正确");
        }
        if (isSub == 1){
            stringBuffer.append(" and a.owner_user_id = ").append(BaseUtil.getUserId());
        }else if (isSub == 2){
            String ids = adminSceneService.getSubUserId(BaseUtil.getUserId().intValue(), BaseConstant.AUTH_DATA_RECURSION_NUM).substring(1);
            stringBuffer.append(" and a.owner_user_id in (").append(ids).append(")");
        }else {
            return R.error("isSub参数不正确");
        }
        List<Integer> leadsIds = Db.query(stringBuffer.toString());
        JSONObject data = jsonObject.getJSONObject("data")!=null ? jsonObject.getJSONObject("data") : new JSONObject();
        if(leadsIds.size()==0){
            return R.ok().put("data",new ArrayList<>());
        }
        data.fluentPut("backLog",new JSONObject().fluentPut("name","leads_id").fluentPut("condition","in").fluentPut("value",StrUtil.join(",",leadsIds)));
        jsonObject.fluentPut("data",data).fluentPut("search","").fluentPut("type",1);
        basePageRequest.setJsonObject(jsonObject);
        return adminSceneService.getCrmPageList(basePageRequest);
    }

    /**
     * 标记客户为已跟进
     */
    public R setCustomerFollowup(String ids){
        List<String> stringList=StrUtil.splitTrim(ids,",");
        if(stringList.size()>0){
            Db.update(Db.getSqlPara("crm.backLog.setCustomerFollowup",Kv.by("ids",stringList)));
        }
        return R.ok();
    }

    /**
     *分配给我的客户
     */
    public R followCustomer(BasePageRequest basePageRequest){
        JSONObject jsonObject = basePageRequest.getJsonObject();
        Integer type = jsonObject.getInteger("type");
        Integer isSub = jsonObject.getInteger("isSub");
        StringBuilder stringBuffer = new StringBuilder("select customer_id from 72crm_crm_customer as a where");
        if (type == 1){
            stringBuffer.append(" a.followup = 0");
        }else if (type == 2){
            stringBuffer.append(" a.followup = 1");
        }else {
            return R.error("type类型不正确");
        }
        if (isSub == 1){
            stringBuffer.append(" and a.owner_user_id = ").append(BaseUtil.getUserId());
        }else if (isSub == 2){
            String ids = adminSceneService.getSubUserId(BaseUtil.getUserId().intValue(), BaseConstant.AUTH_DATA_RECURSION_NUM).substring(1);
            stringBuffer.append(" and a.owner_user_id in (").append(ids).append(")");
        }else {
            return R.error("isSub参数不正确");
        }
        List<Integer> customerIds = Db.query(stringBuffer.toString());
        if(customerIds.size()==0){
            return R.ok().put("data",new ArrayList<>());
        }
        JSONObject data = jsonObject.getJSONObject("data")!=null ? jsonObject.getJSONObject("data") : new JSONObject();
        data.fluentPut("backLog",new JSONObject().fluentPut("name","customer_id").fluentPut("condition","in").fluentPut("value",StrUtil.join(",",customerIds)));
        jsonObject.fluentPut("data",data).fluentPut("search","").fluentPut("type",2);
        basePageRequest.setJsonObject(jsonObject);
        return adminSceneService.getCrmPageList(basePageRequest);
    }

    /**
     *待审核合同
     */
    public R checkContract(BasePageRequest basePageRequest){
        JSONObject jsonObject = basePageRequest.getJsonObject();
        Integer type = jsonObject.getInteger("type");
        Integer isSub = jsonObject.getInteger("isSub");
        StringBuffer stringBuffer = new StringBuffer("select contract_id from 72crm_crm_contract as a inner join 72crm_admin_examine_record as b on a.examine_record_id = b.record_id left join 72crm_admin_examine_log as c on b.record_id = c.record_id where c.is_recheck != 1 and ifnull(b.examine_step_id, 1) = ifnull(c.examine_step_id, 1) and");
        if (type == 1){
            stringBuffer.append(" a.check_status in (0,1)");
        }else if (type == 2){
            stringBuffer.append(" a.check_status in (2,3)");
        }else {
            return R.error("type类型不正确");
        }
        if (isSub == 1){
            stringBuffer.append(" and c.examine_user = ").append(BaseUtil.getUserId());
        }else if (isSub == 2){
            String ids = adminSceneService.getSubUserId(BaseUtil.getUserId().intValue(), BaseConstant.AUTH_DATA_RECURSION_NUM).substring(1);
            stringBuffer.append(" and c.examine_user in (").append(ids).append(")");
        }else {
            return R.error("isSub参数不正确");
        }
        List<Integer> contactsIds = Db.query(stringBuffer.toString());
        if(contactsIds.size()==0){
            return R.ok().put("data",new ArrayList<>());
        }
        JSONObject data = jsonObject.getJSONObject("data")!=null ? jsonObject.getJSONObject("data") : new JSONObject();
        data.fluentPut("backLog",new JSONObject().fluentPut("name","contract_id").fluentPut("condition","in").fluentPut("value",StrUtil.join(",",contactsIds)));
        jsonObject.fluentPut("data",data).fluentPut("search","").fluentPut("type",6);
        basePageRequest.setJsonObject(jsonObject);
        Long userId=BaseUtil.getUserId();
        List<Long> longs = Aop.get(AdminUserService.class).queryUserByAuth(userId);
        if(longs != null && longs.size() > 0){
            stringBuffer.append(" and (owner_user_id in (").append(StrUtil.join(",", longs)).append(")");
            stringBuffer.append(" or ro_user_id like CONCAT('%,','").append(userId).append("',',%')").append(" or rw_user_id like CONCAT('%,','").append(userId).append("',',%')");
            stringBuffer.append(")");
        }
        List<Integer> contactsIds2 = Db.query(stringBuffer.toString());
        R r = adminSceneService.getCrmPageList(basePageRequest);
        if(contactsIds2.size()!=contactsIds.size()){
            r.put("authWarning",true);
        }
        return r;
    }

    /**
     *待审核回款
     */
    public R checkReceivables(BasePageRequest basePageRequest){
        JSONObject jsonObject = basePageRequest.getJsonObject();
        Integer type = jsonObject.getInteger("type");
        Integer isSub = jsonObject.getInteger("isSub");
        StringBuilder stringBuffer = new StringBuilder("select receivables_id from 72crm_crm_receivables as a inner join 72crm_admin_examine_record as b on a.examine_record_id = b.record_id left join 72crm_admin_examine_log as c on b.record_id = c.record_id where ifnull(b.examine_step_id, 1) = ifnull(c.examine_step_id, 1) and");
        if (type == 1){
            stringBuffer.append(" a.check_status in (0,1)");
        }else if (type == 2){
            stringBuffer.append(" a.check_status in (2,3)");
        }else {
            return R.error("type类型不正确");
        }
        if (isSub == 1){
            stringBuffer.append(" and c.examine_user = ").append(BaseUtil.getUserId());
        }else if (isSub == 2){
            String ids = adminSceneService.getSubUserId(BaseUtil.getUserId().intValue(), BaseConstant.AUTH_DATA_RECURSION_NUM).substring(1);
            stringBuffer.append(" and c.examine_user in (").append(ids).append(")");
        }else {
            return R.error("isSub参数不正确");
        }
        List<Integer> receivablesIds = Db.query(stringBuffer.toString());
        if(receivablesIds.size()==0){
            return R.ok().put("data",new ArrayList<>());
        }
        JSONObject data = jsonObject.getJSONObject("data")!=null ? jsonObject.getJSONObject("data") : new JSONObject();
        data.fluentPut("backLog",new JSONObject().fluentPut("name","receivables_id").fluentPut("condition","in").fluentPut("value",StrUtil.join(",",receivablesIds)));
        jsonObject.fluentPut("data",data).fluentPut("search","").fluentPut("type",7);
        basePageRequest.setJsonObject(jsonObject);
        Long userId=BaseUtil.getUserId();
        List<Long> longs = Aop.get(AdminUserService.class).queryUserByAuth(userId);
        if(longs != null && longs.size() > 0){
            stringBuffer.append(" and (owner_user_id in (").append(StrUtil.join(",", longs)).append("))");
        }
        List<Integer> receivablesIds2 = Db.query(stringBuffer.toString());
        R r = adminSceneService.getCrmPageList(basePageRequest);
        if(receivablesIds2.size()!=receivablesIds.size()){
            r.put("authWarning",true);
        }
        return r;
    }

    /**
     *待回款提醒
     */
    public R remindReceivables(BasePageRequest basePageRequest){
        JSONObject jsonObject = basePageRequest.getJsonObject();
        Integer type = jsonObject.getInteger("type");
        Integer isSub = jsonObject.getInteger("isSub");
        StringBuffer stringBuffer = new StringBuffer("from 72crm_crm_receivables_plan as a inner join 72crm_crm_customer as b on a.customer_id = b.customer_id inner join 72crm_crm_contract as c on a.contract_id = c.contract_id where");
        if (type == 1){
            stringBuffer.append(" to_days(a.return_date) >= to_days(now()) and to_days(a.return_date) <= to_days(now())+a.remind and receivables_id is null");
        }else if (type == 2){
            stringBuffer.append(" receivables_id is not null");
        }else if (type == 3){
            stringBuffer.append(" to_days(a.return_date) < to_days(now()) and receivables_id is null");
        }else {
            return R.error("type类型不正确");
        }
        if (isSub == 1){
            stringBuffer.append(" and c.owner_user_id = ").append(BaseUtil.getUserId());
        }else if (isSub == 2){
            String ids = adminSceneService.getSubUserId(BaseUtil.getUserId().intValue(), BaseConstant.AUTH_DATA_RECURSION_NUM).substring(1);
            stringBuffer.append(" and c.owner_user_id in (").append(ids).append(")");
        }else {
            return R.error("isSub参数不正确");
        }
        JSONObject data = jsonObject.getJSONObject("data")!=null ? jsonObject.getJSONObject("data") : new JSONObject();
        if (data != null){
            stringBuffer.append(getConditionSql(data));
        }
        Page<Record> page = Db.paginate(basePageRequest.getPage(),basePageRequest.getLimit(),"select a.num,a.customer_id,b.customer_name,a.contract_id,c.num as contractNum,a.money,a.return_date,a.return_type,a.remind,a.remark",stringBuffer.toString());
        return R.ok().put("data",page);
    }

    /**
     *即将到期的合同
     */
    public R endContract(BasePageRequest basePageRequest){
        JSONObject jsonObject = basePageRequest.getJsonObject();
        Integer type = jsonObject.getInteger("type");
        Integer isSub = jsonObject.getInteger("isSub");
        AdminConfig adminConfig = AdminConfig.dao.findFirst("select * from 72crm_admin_config where name = 'expiringContractDays'");
        StringBuffer stringBuffer = new StringBuffer("select contract_id from 72crm_crm_contract as a where");
        if (type == 1){
            if (adminConfig.getStatus() == 0 || ObjectUtil.isNull(adminConfig)){
                return R.ok().put("data",new Page<>());
            }
            stringBuffer.append(" to_days(end_time) > to_days(now()) and to_days(end_time) <= to_days(now())+").append(adminConfig.getValue());
        }else if (type == 2){
            stringBuffer.append(" to_days(end_time) <= to_days(now())");
        }else {
            return R.error("type类型不正确");
        }
        if (isSub == 1){
            stringBuffer.append(" and owner_user_id = ").append(BaseUtil.getUserId());
        }else if (isSub == 2){
            String ids = adminSceneService.getSubUserId(BaseUtil.getUserId().intValue(), BaseConstant.AUTH_DATA_RECURSION_NUM);
            stringBuffer.append(" and owner_user_id in (").append(ids).append(")");
        }else {
            return R.error("isSub参数不正确");
        }
        List<Integer> contractIds = Db.query(stringBuffer.toString());
        if(contractIds.size()==0){
            return R.ok().put("data",new ArrayList<>());
        }
        JSONObject data = jsonObject.getJSONObject("data")!=null ? jsonObject.getJSONObject("data") : new JSONObject();
        data.fluentPut("backLog",new JSONObject().fluentPut("name","contract_id").fluentPut("condition","in").fluentPut("value",StrUtil.join(",",contractIds)));
        jsonObject.fluentPut("data",data).fluentPut("search","").fluentPut("type",6);
        basePageRequest.setJsonObject(jsonObject);
        return adminSceneService.getCrmPageList(basePageRequest);
    }

    /**
     * @author wyq
     * 待进入客户池提醒
     */
    public R putInPoolRemind(BasePageRequest basePageRequest){
        JSONObject jsonObject = basePageRequest.getJsonObject();
        Integer type = jsonObject.getInteger("type");
        Integer isSub = jsonObject.getInteger("isSub");
        Integer poolSetting = Db.queryInt("select status from 72crm_admin_config where name = 'customerPoolSetting' limit 1");
        String dealDays = Db.queryStr("select value from 72crm_admin_config where name = 'customerPoolSettingDealDays' limit 1");
        String followUpDays = Db.queryStr("select value from 72crm_admin_config where name = 'customerPoolSettingFollowupDays' limit 1");
        AdminConfig remindConfig = AdminConfig.dao.findFirst("select * from 72crm_admin_config where name = 'putInPoolRemindDays'");
        StringBuffer stringBuffer = new StringBuffer("select customer_id from 72crm_crm_customer as a");
        if (type == 1){
            if (poolSetting != 1 || remindConfig.getStatus() == 0 || ObjectUtil.isNull(remindConfig)){
                return R.ok().put("data",new Page<>());
            }
            Integer remindDays = Integer.parseInt(remindConfig.getValue());
            Integer startDealDays = Integer.parseInt(dealDays) - remindDays;
            Integer startFollowUpDays = Integer.parseInt(followUpDays) - remindDays;
            stringBuffer.append(" where owner_user_id is not null and deal_status ='未成交' and is_lock = 0 and ((unix_timestamp(now()) - unix_timestamp(IFNULL((SELECT car.create_time FROM 72crm_admin_record as car where car.types = 'crm_customer' and car.types_id = a.customer_id ORDER BY car.create_time DESC LIMIT 1),a.create_time))) between ").append(startFollowUpDays).append(" and ").append(followUpDays).append(" or (unix_timestamp(now()) - unix_timestamp(create_time)) between ").append(startDealDays).append(" and ").append(dealDays).append(")");
            if (isSub == 1){
                stringBuffer.append(" and owner_user_id = ").append(BaseUtil.getUserId());
            }else if (isSub == 2){
                String ids = adminSceneService.getSubUserId(BaseUtil.getUserId().intValue(), BaseConstant.AUTH_DATA_RECURSION_NUM);
                stringBuffer.append(" and owner_user_id in (").append(ids).append(")");
            }else {
                return R.error("isSub参数不正确");
            }
        }else if (type == 2){
            stringBuffer.append(" left join 72crm_crm_owner_record as b on a.customer_id = b.type_id where b.type = 8 and post_owner_user_id is null");
            if (isSub == 1){
                stringBuffer.append(" and pre_owner_user_id = ").append(BaseUtil.getUserId());
            }else if (isSub == 2){
                String ids = adminSceneService.getSubUserId(BaseUtil.getUserId().intValue(), BaseConstant.AUTH_DATA_RECURSION_NUM);
                stringBuffer.append(" and pre_owner_user_id in (").append(ids).append(")");
            }else {
                return R.error("isSub参数不正确");
            }
        }else {
            return R.error("type类型不正确");
        }
        List<Integer> customerIds = Db.query(stringBuffer.toString());
        if(customerIds.size()==0){
            return R.ok().put("data",new ArrayList<>());
        }
        JSONObject data = jsonObject.getJSONObject("data")!=null ? jsonObject.getJSONObject("data") : new JSONObject();
        data.fluentPut("backLog",new JSONObject().fluentPut("name","customer_id").fluentPut("condition","in").fluentPut("value",StrUtil.join(",",customerIds)));
        jsonObject.fluentPut("data",data).fluentPut("search","").fluentPut("type",2);
        basePageRequest.setJsonObject(jsonObject);
        return adminSceneService.getCrmPageList(basePageRequest);
    }

    public String getConditionSql(JSONObject data){
        List<JSONObject> jsonObjectList = new ArrayList<>();
        if (data != null) {
            data.forEach((k, v) -> {
                jsonObjectList.add(JSON.parseObject(v.toString()));
            });
        }
        StringBuffer conditionSqlSb = new StringBuffer();
        for (JSONObject jsonObject : jsonObjectList) {
            String condition = jsonObject.getString("condition");
            String value = jsonObject.getString("value");
            String formType = jsonObject.getString("formType");
            if (StrUtil.isNotEmpty(value) || StrUtil.isNotEmpty(jsonObject.getString("start")) || StrUtil.isNotEmpty(jsonObject.getString("end")) || "business_type".equals(jsonObject.getString("formType"))) {
                conditionSqlSb.append(" and ").append("a.").append(jsonObject.getString("name"));
                if ("is".equals(condition)) {
                    conditionSqlSb.append(" = '").append(value).append("'");
                } else if ("isNot".equals(condition)) {
                    conditionSqlSb.append(" != '").append(value).append("'");
                } else if ("contains".equals(condition)) {
                    conditionSqlSb.append(" like '%").append(value).append("%'");
                } else if ("notContains".equals(condition)) {
                    conditionSqlSb.append(" not like '%").append(value).append("%'");
                } else if ("isNull".equals(condition)) {
                    conditionSqlSb.append(" is null");
                } else if ("isNotNull".equals(condition)) {
                    conditionSqlSb.append(" is not null");
                } else if ("gt".equals(condition)) {
                    conditionSqlSb.append(" > ").append(value);
                } else if ("egt".equals(condition)) {
                    conditionSqlSb.append(" >= ").append(value);
                } else if ("lt".equals(condition)) {
                    conditionSqlSb.append(" < ").append(value);
                } else if ("elt".equals(condition)) {
                    conditionSqlSb.append(" <= ").append(value);
                } else if ("in".equals(condition)) {
                    conditionSqlSb.append(" in (").append(value).append(")");
                }
                if ("datetime".equals(formType)) {
                    conditionSqlSb.append(" between '").append(jsonObject.getString("start")).append("' and '").append(jsonObject.getString("end")).append("'");
                }
                if ("date".equals(formType)) {
                    conditionSqlSb.append(" between '").append(jsonObject.getString("startDate")).append("' and '").append(jsonObject.getString("endDate")).append("'");
                }
            }
        }
        return conditionSqlSb.toString();
    }
}
