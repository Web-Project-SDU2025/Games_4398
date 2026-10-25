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
    public SystemApplicationListener(SystemService systemService) {
        this.systemService = systemService;
    }


    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info(event.toString());
        log.info("SystemInitStart");
        systemService.initDictionary();
        systemService.initSystem();
        log.info("systemInitEnd");

    }

}