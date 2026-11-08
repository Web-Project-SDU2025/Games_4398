package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.DictionaryInfo;
import cn.edu.sdu.java.server.models.SystemInfo;
import cn.edu.sdu.java.server.payload.response.OptionItem;
import cn.edu.sdu.java.server.repositorys.DictionaryInfoRepository;
import cn.edu.sdu.java.server.repositorys.SystemInfoRepository;
import cn.edu.sdu.java.server.util.ComDataUtil;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 系统配置服务
 *
 * 负责在应用启动时把数据字典和系统配置加载到内存缓存
 * 这样业务代码就能快速读取配置，避免频繁查数据库
 */
@Service
public class SystemService {
    private final DictionaryInfoRepository dictionaryInfoRepository; //数据数据操作自动注入
    private final SystemInfoRepository systemInfoRepository; //数据数据操作自动注入

    /**
     * 依赖注入
     * 保存字典和系统配置仓库，启动初始化时会用到
     */
    public SystemService(DictionaryInfoRepository dictionaryInfoRepository, SystemInfoRepository systemInfoRepository) {
        this.dictionaryInfoRepository = dictionaryInfoRepository;
        this.systemInfoRepository = systemInfoRepository;
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
    /**
     * 初始化系统配置
     * 把系统配置表的数据加载到内存Map里，空表就生成空Map
     */
    public void initSystem() {
        List<SystemInfo> sList = systemInfoRepository.findAll();
        Map<String,String> map = new HashMap<>();
        for(SystemInfo s:sList) {
            map.put(s.getName(),s.getValue());
        }
        ComDataUtil pi = ComDataUtil.getInstance();
        pi.setSystemMap(map);
    }
}

