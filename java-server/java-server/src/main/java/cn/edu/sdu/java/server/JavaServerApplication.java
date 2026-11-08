package cn.edu.sdu.java.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Java服务器应用主类
 *
 * 启动后端组件
 * 配置或外部服务异常由Spring启动日志报告
 */
@SpringBootApplication
public class JavaServerApplication {

	/**
	 * 主方法
	 * 将启动参数交给Spring，应用生命周期由框架管理
	 */
	public static void main(String[] args) {
		SpringApplication.run(JavaServerApplication.class, args);
	}

}
