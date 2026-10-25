# 控制器测试

此目录用于存放控制器层（Controller）的单元测试。

## 测试要点

1. 测试各个接口的请求和响应
2. 验证参数校验是否生效
3. 测试异常情况的处理
4. 验证返回状态码和数据格式

## 示例

```java
@WebMvcTest(UserController.class)
class UserControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private UserService userService;
    
    @Test
    void testGetUserById_Success() throws Exception {
        mockMvc.perform(get("/api/user/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }
}
```
