package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.services.SystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * SystemApplicationListener 系统应用实践处理程序
 */
@Component
@Order(0)
public class SystemApplicationListener implements ApplicationListener<ApplicationReadyEvent> {
    private static final Logger log = LoggerFactory.getLogger(SystemApplicationListener.class);
    private final SystemService systemService;  //系统服务对象自动注入
    /** 保存系统初始化服务，准备完成后统一读取系统数据 */
    public SystemApplicationListener(SystemService systemService) {
        this.systemService = systemService;
    }


    /** 应用就绪后加载字典与系统配置，读取异常交给启动日志报告 */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info(event.toString());
        log.info("SystemInitStart");
        systemService.initDictionary();
        systemService.initSystem();
        log.info("systemInitEnd");

    }

}
