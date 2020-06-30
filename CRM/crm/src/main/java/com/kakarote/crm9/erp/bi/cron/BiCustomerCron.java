package com.kakarote.crm9.erp.bi.cron;

import cn.hutool.core.date.DateUtil;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import com.kakarote.crm9.erp.crm.entity.CrmCustomerStats;
import com.kakarote.crm9.utils.BaseUtil;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author wyq
 */
public class BiCustomerCron implements Runnable{
    @Override
    public void run() {
        List<Record> hostIdList = Db.find("SELECT id,phone from 72crm_admin_hosts WHERE super = 1");
        hostIdList.forEach(host ->{
            BaseUtil.setHost(host);
            List<Record> recordList = Db.find(Db.getSql("bi.customer.biCustomerCron"));
            recordList.forEach(record -> {
                record.set("create_time", DateUtil.date());
                CrmCustomerStats crmCustomerStats = new CrmCustomerStats()._setAttrs(record.getColumns());
                crmCustomerStats.save();
            });
        });
    }
}
