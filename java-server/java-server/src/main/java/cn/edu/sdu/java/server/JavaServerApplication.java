package cn.edu.sdu.java.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 启动后端组件，配置或外部服务异常由 Spring 启动日志报告 */
@SpringBootApplication
public class JavaServerApplication {

	/** 将启动参数交给 Spring，应用生命周期由框架管理 */
	public static void main(String[] args) {
		SpringApplication.run(JavaServerApplication.class, args);
	}

}
