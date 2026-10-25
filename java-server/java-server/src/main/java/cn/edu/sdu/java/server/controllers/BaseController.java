package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.payload.response.MyTreeNode;
import cn.edu.sdu.java.server.payload.response.OptionItemList;
import cn.edu.sdu.java.server.services.BaseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.*;

/**
 * BaseController 主要时为前台框架的基本数据管理提供的Web请求服务
 */
// origins： 允许可访问的域列表
// maxAge:准备响应前的缓存持续的最大时间（以秒为单位）。
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/base")
@Slf4j
public class BaseController {
    private final BaseService baseService;
    public BaseController(BaseService baseService) {
        this.baseService = baseService;
    }


    /**
     * 获取菜单列表
     * 前台请求参数 userTypeId 用户类型主键ＩＤ，如果为空或缺的当前登录用户的类型的ID
     * 返回前端存储菜单数据的 MapList
     *
     *
     */

    @PostMapping("/getMenuList")
    public DataResponse getMenuList(@Valid @RequestBody DataRequest dataRequest) {
        return baseService.getMenuList(dataRequest);
    }

    @PostMapping("/getDataBaseUserName")
    public DataResponse getDataBaseUserName(@Valid @RequestBody DataRequest dataRequest) {
        return baseService.getDataBaseUserName();
    }

    /**
     * 获取所有角色信息的列表
     * 前台请求参数 无
     * 返回前端存储角色信息的OptionItem的List
     *
     *
     */

    @PostMapping("/getRoleOptionItemList")
    @PreAuthorize("hasRole('ADMIN')")
    public OptionItemList getRoleOptionItemList(@Valid @RequestBody DataRequest dataRequest) {
        return baseService.getRoleOptionItemList(dataRequest);
    }

    /**
     * 获取某个用户类型 userTypeId 菜单树 信息
     * 前台请求参数 无
     * 返回前端某个用户类型 userTypeId 菜单树对象MyTreeNode（这个是一个递归的树节点对象）
     *
     *
     */

    @PostMapping("/getMenuTreeNodeList")
    @PreAuthorize("hasRole('ADMIN')")
    public List<MyTreeNode> getMenuTreeNodeList(@Valid @RequestBody DataRequest dataRequest) {
        return baseService.getMenuTreeNodeList();
    }



    /**
     * 删除菜单
     * 前台请求参数 id 要删除的菜单的主键 menu_id
     * 返回前端 操作正常
     *
     *
     */
    @PostMapping("/menuDelete")
    @PreAuthorize("hasRole('ADMIN')")
    public DataResponse menuDelete(@Valid @RequestBody DataRequest dataRequest) {
        return baseService.menuDelete(dataRequest);

    }

    /**
     * 保存菜单信息
     * 前台请求参数 id 要修改菜单的主键 menu_id  name 菜单名字  title 菜单标题
     * 返回前端 操作正常
     *
     *
     */

    @PostMapping("/menuSave")
    @PreAuthorize("hasRole('ADMIN')")
    public DataResponse menuSave(@Valid @RequestBody DataRequest dataRequest) {
        return baseService.menuSave(dataRequest);
    }

    /**
     * 获取某个数据字典树表信息
     * 前台请求参数 无
     * 返回前端 数据字典数节点对象 MyTreeNode（这个是一个递归的树节点对象）
     *
     *
     */
    @PostMapping("/getDictionaryTreeNodeList")
    @PreAuthorize("hasRole('ADMIN')")
    public List<MyTreeNode> getDictionaryTreeNodeList(@Valid @RequestBody DataRequest dataRequest) {
        return baseService.getDictionaryTreeNodeList();
    }

    /**
     * 删除字典
     * 前台请求参数 id 要删除的字典的主键 id
     * 返回前端 操作正常
     *
     *
     */
    @PostMapping("/dictionaryDelete")
    @PreAuthorize("hasRole('ADMIN')")
    public DataResponse deleteDictionary(@Valid @RequestBody DataRequest dataRequest) {
        return baseService.deleteDictionary(dataRequest);
    }

    /**
     * 保存字典信息
     * 前台请求参数 id 要修改菜单的主键 id  value 地点值  label 字典名
     * 返回前端 操作正常
     *
     *
     */

    @PostMapping("/dictionarySave")
    @PreAuthorize("hasRole('ADMIN')")
    public DataResponse dictionarySave(@Valid @RequestBody DataRequest dataRequest) {
        return baseService.dictionarySave(dataRequest);
    }

    /**
     * 获取某种数据类型的数据字典列表
     * 前台请求参数  code 数据类型的值
     * 返回前端存储数据字典信息的OptionItem的List
     *
     *
     */

    @PostMapping("/getDictionaryOptionItemList")
    public OptionItemList getDictionaryOptionItemList(@Valid @RequestBody DataRequest dataRequest) {
        return baseService.getDictionaryOptionItemList(dataRequest);
    }

    /**
     * 获取服务器端的图片文件的数据
     * 前台请求参数  文件路径
     * 返回前端图片数据的二进制数据留
     *
     *
     */
    @PostMapping("/getFileByteData")
    public ResponseEntity<StreamingResponseBody> getFileByteData(@Valid @RequestBody DataRequest dataRequest) {
        return baseService.getFileByteData(dataRequest);
    }


    /**
     * 上传文件服务
     * 前台请求参数  uploader 信息  remoteFile 服务器文件路径  fileName 前端上传的文件名
     * 返回前端 正常操作信心和异常操作信息
     *
     *
     */
    @PostMapping(path = "/uploadPhoto")
    public DataResponse uploadPhoto(@RequestBody byte[] barr,
                                    @RequestParam(name = "uploader") String uploader,
                                    @RequestParam(name = "remoteFile") String remoteFile,
                                    @RequestParam(name = "fileName") String fileName) {
        return baseService.uploadPhoto(barr, remoteFile);
    }

    @PostMapping("/getBlobByteData")
    public ResponseEntity<StreamingResponseBody> getBlobByteData(@Valid @RequestBody DataRequest dataRequest) {
        return baseService.getBlobByteData(dataRequest);
    }


    /**
     * 上传文件服务
     * 前台请求参数  uploader 信息  remoteFile 服务器文件路径  fileName 前端上传的文件名
     * 返回前端 正常操作信心和异常操作信息
     *
     *
     */
    @PostMapping(path = "/uploadPhotoBlob")
    public DataResponse uploadPhotoBlob(@RequestBody byte[] barr,
                                    @RequestParam(name = "uploader") String uploader,
                                    @RequestParam(name = "remoteFile") String userId,
                                    @RequestParam(name = "fileName") String fileName) {
        return baseService.uploadPhotoBlob(barr, userId);
    }

    /**
     * 修改登录用户的密码
     * 前台请求参数  oldPassword 用户的旧密码  newPassword 用户的新密码
     * 返回前端 正常操作
     *
     *
     */
    //  修改密码
    //账户页面提交旧密码和新密码，后端验证旧密码后保存新密码。
    @PostMapping("/updatePassword")
    @PreAuthorize("hasRole('ADMIN') or hasRole('GAME_USER')")
    public DataResponse updatePassword(@Valid @RequestBody DataRequest dataRequest) {
        return baseService.updatePassword(dataRequest);
    }

    /**
     * 上传Html字符串流， 用于生成html字符流和PDF数据流的源HTML， 保存的内存MAP中
     * 前台请求参数  html 前端传过来的 html字符串
     * 返回前端 字符串保存的MAP的key,用于下载html，PDF的主键
     *
     *
     */

    @PostMapping("/uploadHtmlString")
    @PreAuthorize(" hasRole('ADMIN') ")
    public DataResponse uploadHtmlString(@Valid @RequestBody DataRequest dataRequest) {
        return baseService.uploadHtmlString(dataRequest);
    }
    /**
     * 获取Html页面数据，
     * 前台请求参数  htmlCount 获取原始的前端传送后端保存的Html的主键
     * 返回前端 html页面， 前端WebPage可以直接访问的页面
     *
     *
     */

    @GetMapping("/getHtmlPage")
    public ResponseEntity<StreamingResponseBody> htmlGetBaseHtmlPage(HttpServletRequest request) {
       return baseService.htmlGetBaseHtmlPage(request);
    }



    //  Web 请求
    @PostMapping("/getPhotoImageStr")
    public DataResponse getPhotoImageStr(@Valid @RequestBody DataRequest dataRequest) {
        return baseService.getPhotoImageStr(dataRequest);
    }

    @PostMapping(value = "/uploadPhotoWeb", consumes = {"multipart/form-data"})
    @PreAuthorize("hasRole('GAME_USER') or hasRole('ADMIN')")
    public DataResponse uploadPhotoWeb(@RequestParam Map<String,Object> pars, @RequestParam("file") MultipartFile file) {
        return baseService.uploadPhotoWeb(pars, file);
    }
    @PostMapping(value = "/uploadPhotoBlobWeb", consumes = {"multipart/form-data"})
    @PreAuthorize("hasRole('GAME_USER') or hasRole('ADMIN')")
    public DataResponse uploadPhotoBlobWeb(@RequestParam Map<String,Object> pars, @RequestParam("file") MultipartFile file) {
        return baseService.uploadPhotoBlobWeb(pars, file);
    }
}
