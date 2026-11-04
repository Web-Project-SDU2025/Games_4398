package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.StatisticsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


/** 保留原统计接口，统计读取范围由角色权限控制 */
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {
    private final StatisticsService statisticsService;
    /** 保存统计服务，响应字段沿用已有图表协议 */
    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /** 返回账户和日统计，空记录由服务整理成空列表 */
    @PostMapping("/getMainPageData")
    @PreAuthorize("hasRole('ADMIN') or hasRole('GAME_USER')")
    public DataResponse getMainPageData(@Valid @RequestBody DataRequest dataRequest) {
        return statisticsService.getMainPageData(dataRequest);
    }

}
