package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.DictionaryInfo;
import cn.edu.sdu.java.server.models.ModifyLog;
import cn.edu.sdu.java.server.models.SystemInfo;
import cn.edu.sdu.java.server.payload.response.OptionItem;
import cn.edu.sdu.java.server.repositorys.DictionaryInfoRepository;
import cn.edu.sdu.java.server.repositorys.ModifyLogRepository;
import cn.edu.sdu.java.server.repositorys.SystemInfoRepository;
import cn.edu.sdu.java.server.util.ComDataUtil;
import cn.edu.sdu.java.server.util.CommonMethod;
import cn.edu.sdu.java.server.util.DateTimeTool;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * SystemService 系统服务行数
 */
@Service
public class SystemService {
    private final DictionaryInfoRepository dictionaryInfoRepository; //数据数据操作自动注入
    private final SystemInfoRepository systemInfoRepository; //数据数据操作自动注入

    private final ModifyLogRepository modifyLogRepository; //数据数据操作自动注入
    public SystemService(DictionaryInfoRepository dictionaryInfoRepository, SystemInfoRepository systemInfoRepository, ModifyLogRepository modifyLogRepository) {
        this.dictionaryInfoRepository = dictionaryInfoRepository;
        this.systemInfoRepository = systemInfoRepository;
        this.modifyLogRepository = modifyLogRepository;
    }
    /**
     *  initDictionary 初始数据字典 在系统初始时将数据字典加载内存，业务处理是直接从内从中获取数数据字典列表和数据字典名称
     */
    public void initDictionary() {
        List<OptionItem> itemList;
        OptionItem item;
        Map<String, String> sMap;
        String value;
        Map<String, List<OptionItem>> dictListMap = ComDataUtil.getInstance().getDictListMap();
        Map<String, Map<String, String>> dictMapMap = ComDataUtil.getInstance().getDictMapMap();
        List<DictionaryInfo>  dList =dictionaryInfoRepository.findRootList();
        List<DictionaryInfo> sList;
        for(DictionaryInfo df : dList) {
            value = df.getValue();
            sMap = new HashMap<String, String>();
            dictMapMap.put(value, sMap);
            itemList = new ArrayList<>();
            dictListMap.put(value, itemList);
            sList = dictionaryInfoRepository.findByPid(df.getId());
            for (DictionaryInfo d : sList) {
                sMap.put(d.getValue(), d.getLabel());
                item = new OptionItem(d.getId(), d.getValue(), d.getLabel());
                itemList.add(item);
            }
        }
        ComDataUtil pi = ComDataUtil.getInstance();
        pi.setDictListMap(dictListMap);
        pi.setDictMapMap(dictMapMap);
    }
    public void initSystem() {
        List<SystemInfo> sList = systemInfoRepository.findAll();
        Map<String,String> map = new HashMap<>();
        for(SystemInfo s:sList) {
            map.put(s.getName(),s.getValue());
        }
        ComDataUtil pi = ComDataUtil.getInstance();
        pi.setSystemMap(map);
    }
    public void modifyLog(Object o, boolean isCreate) {
        String info = CommonMethod.ObjectToJSon(o);
        if(info == null)
            return;
        String tableName = o.getClass().getName();
        int index = tableName.lastIndexOf('.');
        if(index > 0) {
            tableName = tableName.substring(index+1);
        }
        ModifyLog l = new ModifyLog();
        l.setTableName(tableName);
        if(isCreate)
            l.setType("0");
        else
            l.setType("1");
        l.setInfo(info);
        l.setOperateTime(DateTimeTool.parseDateTime(new Date()));
        l.setOperatorId(CommonMethod.getUserId());
        modifyLogRepository.save(l);
    }
}

