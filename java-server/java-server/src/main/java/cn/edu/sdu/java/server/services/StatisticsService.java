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

@Service
public class StatisticsService {
    private final UserRepository userRepository;
    private final StatisticsDayRepository statisticsDayRepository;
    public StatisticsService(UserRepository userRepository, StatisticsDayRepository statisticsDayRepository) {
        this.userRepository = userRepository;
        this.statisticsDayRepository = statisticsDayRepository;
    }

    public DataResponse getMainPageData(DataRequest dataRequest) {
        Date day = new Date();
        Date monthDay = DateTimeTool.prevMonth(day);
        int i;
        Integer id;
        Object[] a;
        Long l;
        String name;
        long total = userRepository.count();
        Integer monthCount = userRepository.countLastLoginTime(DateTimeTool.parseDateTime(monthDay,"yyyy-MM-dd")+" 00:00:00");
        Integer dayCount = userRepository.countLastLoginTime(DateTimeTool.parseDateTime(day,"yyyy-MM-dd")+" 00:00:00");
        Map<String,Object> data = new HashMap<>();
        Map<String,Object> m = new HashMap<>();
        m.put("total", (int) total);
        m.put("monthCount",monthCount);
        m.put("dayCount",dayCount);
        data.put("onlineUser", m);
        List<?> nList = userRepository.getCountList();
        List<Map<String,Object>> userTypeList = new ArrayList<>();
        for(i= 0;i < nList.size();i++) {
            m = new HashMap<>();
            a = (Object[])nList.get(i);
            id = (Integer)a[0];
            l = (Long)a[1];
            if(id == 1)
                name = "管理员";
            else if(id == 2)
                name = "游戏用户";
            else
                name = "";
            m.put("name", name);
            m.put("value",l.intValue());
            userTypeList.add(m);
        }
        data.put("userTypeList", userTypeList);
        List<StatisticsDay>sList = statisticsDayRepository.findListByDay(DateTimeTool.parseDateTime(monthDay,"yyyyMMdd"),DateTimeTool.parseDateTime(day,"yyyyMMdd"));
        List<String> dayList = new ArrayList<>();
        List<String> lList = new ArrayList<>();
        List<String> rList = new ArrayList<>();
        List<String> cList = new ArrayList<>();
        List<String> mList = new ArrayList<>();
        for(StatisticsDay s:sList) {
            dayList.add(s.getDay());
            lList.add(""+s.getLoginCount());
            rList.add(""+s.getRequestCount());
            cList.add(""+s.getCreateCount());
            mList.add(""+s.getLoginCount());
        }
        m = new HashMap<>();
        m.put("value",dayList);
        m.put("label1",lList);
        m.put("label2",rList);
        data.put("requestData", m);
        m = new HashMap<>();
        m.put("value",dayList);
        m.put("label1",cList);
        m.put("label2",mList);
        data.put("operateData", m);

        return CommonMethod.getReturnData(data);
    }

}
