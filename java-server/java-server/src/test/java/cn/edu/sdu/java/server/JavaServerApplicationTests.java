package cn.edu.sdu.java.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** 验证后端容器装配，外部服务读取沿用开发配置 */
@SpringBootTest
class JavaServerApplicationTests {

	/** 容器启动成功即通过，装配失败由测试框架报告 */
	@Test
	void contextLoads() {
	}

}
