#namespace("crm.receivables")
     #sql("getReceivablesPageList")
      select * from receivablesview
     #end
     #sql("totalRowSql")
      select count(*) from receivablesview
     #end
     #sql("queryById")
        select  scr.* ,scc.customer_name as customerName,scco.num as contractNum,sccp.num as planNum
         from 72crm_crm_receivables as scr
        LEFT JOIN 72crm_crm_customer as scc on scc.customer_id = scr.customer_id
         LEFT JOIN 72crm_crm_contract as scco on scco.contract_id = scr.contract_id
         LEFT JOIN 72crm_crm_receivables_plan as sccp on sccp.receivables_id = scr.receivables_id
        where scr.receivables_id = ?
     #end
     #sql ("deleteByIds")
     delete from 72crm_crm_receivables where receivables_id = ? and check_status != 1 and check_status != 2
     #end
     #sql("queryReceivablesPageList")
       select  rec.receivables_id,rec.number as receivables_num,scco.name contract_name,scco.money as contract_money
            ,b.realname as owner_user_name,
                case rec.check_status
                when 1 then '审核中'
                  when 3 then '审核未通过'
                  when 2 then '审核通过'
                  when 4 then '已撤回'
                  ELSE '未审核' END
                                      as check_status,rec.return_time,rec.money as receivables_money,a.num
        FROM `72crm_crm_receivables` as rec left join `72crm_crm_receivables_plan` a on rec.plan_id = a.plan_id
                                            left join 72crm_crm_contract as scco on scco.contract_id = rec.contract_id
                                            left join `72crm_admin_user` b on rec.owner_user_id = b.user_id
        where rec.contract_id = ?
     #end
     #sql("queryReceivablesByContractIds")
       select * from 72crm_crm_receivables where contract_id in (
            #for(contractId:contractIds)
              #(for.index == 0 ? "" : ",")
                  #para(contractId)
            #end
       )
     #end
     #sql("queryReceivablesById")
     select a.*,b.customer_name,c.name as contractName,c.num as contractNum,c.money as contractMoney,d.realname as ownerUserName from
     (select * from 72crm_crm_receivables where receivables_id = ?)as a left join 72crm_crm_customer as b on a.customer_id = b.customer_id
     left join 72crm_crm_contract as c on a.contract_id = c.contract_id
     left join 72crm_admin_user as d on a.owner_user_id = d.user_id
     #end
     #sql("queryReceivablesByContractId")
       select * from 72crm_crm_receivables where contract_id = ?
     #end
     #sql ("queryByNumber")
       select * from 72crm_crm_receivables where number = ?
     #end
     #sql ("updateCheckStatusById")
      update 72crm_crm_receivables set check_status = ? where receivables_id = ?
    #end
#end
