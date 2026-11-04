package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.StatisticsDay;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.StatisticsDayRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.util.CommonMethod;
import cn.edu.sdu.java.server.util.DateTimeTool;
import org.springframework.stereotype.Service;
import java.util.*;

/** 汇总原首页统计，返回字段继续供已有图表接口使用 */
@Service
public class StatisticsService {
    private final UserRepository userRepository;
    private final StatisticsDayRepository statisticsDayRepository;
    /** 保存账户和日统计仓库，空统计表交给列表整理流程处理 */
    public StatisticsService(UserRepository userRepository, StatisticsDayRepository statisticsDayRepository) {
        this.userRepository = userRepository;
        this.statisticsDayRepository = statisticsDayRepository;
    }

    /** 返回账户、角色和日统计，字段名称保持原接口格式 */
    public DataResponse getMainPageData(DataRequest dataRequest) {
        Date today = new Date();
        Date previousMonth = DateTimeTool.prevMonth(today);
        int index;
        Integer roleId;
        Object[] roleCount;
        Long count;
        String name;
        long total = userRepository.count();
        Integer monthCount = userRepository.countLastLoginTime(DateTimeTool.parseDateTime(previousMonth,"yyyy-MM-dd")+" 00:00:00");
        Integer dayCount = userRepository.countLastLoginTime(DateTimeTool.parseDateTime(today,"yyyy-MM-dd")+" 00:00:00");
        Map<String,Object> data = new HashMap<>();
        Map<String,Object> statistics = new HashMap<>();
        statistics.put("total", (int) total);
        statistics.put("monthCount",monthCount);
        statistics.put("dayCount",dayCount);
        data.put("onlineUser", statistics);
        List<?> roleCounts = userRepository.getCountList();
        List<Map<String,Object>> userTypeList = new ArrayList<>();
        for(index= 0;index < roleCounts.size();index = index + 1) {
            statistics = new HashMap<>();
            roleCount = (Object[])roleCounts.get(index);
            roleId = (Integer)roleCount[0];
            count = (Long)roleCount[1];
            if(roleId == 1)
                name = "管理员";
            else if(roleId == 2)
                name = "游戏用户";
            else
                name = "";
            statistics.put("name", name);
            statistics.put("value",count.intValue());
            userTypeList.add(statistics);
        }
        data.put("userTypeList", userTypeList);
        List<StatisticsDay>dailyStatistics = statisticsDayRepository.findListByDay(DateTimeTool.parseDateTime(previousMonth,"yyyyMMdd"),DateTimeTool.parseDateTime(today,"yyyyMMdd"));
        List<String> dayList = new ArrayList<>();
        List<String> loginCounts = new ArrayList<>();
        List<String> requestCounts = new ArrayList<>();
        List<String> createCounts = new ArrayList<>();
        List<String> operationCounts = new ArrayList<>();
        for(StatisticsDay daily:dailyStatistics) {
            dayList.add(daily.getDay());
            loginCounts.add(""+daily.getLoginCount());
            requestCounts.add(""+daily.getRequestCount());
            createCounts.add(""+daily.getCreateCount());
            operationCounts.add(""+daily.getLoginCount());
        }
        statistics = new HashMap<>();
        statistics.put("value",dayList);
        statistics.put("label1",loginCounts);
        statistics.put("label2",requestCounts);
        data.put("requestData", statistics);
        statistics = new HashMap<>();
        statistics.put("value",dayList);
        statistics.put("label1",createCounts);
        statistics.put("label2",operationCounts);
        data.put("operateData", statistics);

        return CommonMethod.getReturnData(data);
    }

}
