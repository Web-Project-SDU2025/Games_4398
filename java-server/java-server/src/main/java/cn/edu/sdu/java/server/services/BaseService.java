package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.*;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.payload.response.MyTreeNode;
import cn.edu.sdu.java.server.payload.response.OptionItem;
import cn.edu.sdu.java.server.payload.response.OptionItemList;
import cn.edu.sdu.java.server.repositorys.*;
import cn.edu.sdu.java.server.util.ComDataUtil;
import cn.edu.sdu.java.server.util.CommonMethod;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BaseService {
    private static final Logger log = LoggerFactory.getLogger(BaseService.class);
    @Value("${attach.folder}")    //环境配置变量获取
    private String attachFolder;  //服务器端数据存储
    @Value("${spring.datasource.username}")    //环境配置变量获取
    private String dataBaseUserName;  //服务器端数据存储
    private final PasswordEncoder encoder;  //密码服务自动注入
    private final UserRepository userRepository;  //用户数据操作自动注入
    private final MenuInfoRepository menuInfoRepository; //菜单数据操作自动注入
    private final DictionaryInfoRepository dictionaryInfoRepository;  //数据字典数据操作自动注入
    private final UserTypeRepository userTypeRepository;   //用户类型数据操作自动注入

    public BaseService(PasswordEncoder encoder, UserRepository userRepository, MenuInfoRepository menuInfoRepository, DictionaryInfoRepository dictionaryInfoRepository, UserTypeRepository userTypeRepository) {
        this.encoder = encoder;
        this.userRepository = userRepository;
        this.menuInfoRepository = menuInfoRepository;
        this.dictionaryInfoRepository = dictionaryInfoRepository;
        this.userTypeRepository = userTypeRepository;
    }

    public DataResponse getDataBaseUserName() {
        return CommonMethod.getReturnData(dataBaseUserName);
    }
    /**
     *  getDictionaryTreeNode 获取数据字典节点树根节点
     * @return MyTreeNode 数据字典树根节点
     */
    public List<MyTreeNode> getDictionaryTreeNodeList() {
        List<MyTreeNode> childList = new ArrayList<MyTreeNode>();
        List<DictionaryInfo> sList = dictionaryInfoRepository.findRootList();
        if(sList == null)
            return childList;
        for (DictionaryInfo dictionaryInfo : sList) {
            childList.add(getDictionaryTreeNode(null, dictionaryInfo, null));
        }
        return childList;
    }

    /**
     * 获得数据字典的MyTreeNode
     */
    public MyTreeNode getDictionaryTreeNode( Integer pid, DictionaryInfo d,String parentTitle) {
        MyTreeNode  node = new MyTreeNode(d.getId(),d.getValue(),d.getLabel(),null);
        node.setLabel(d.getValue()+"-"+d.getLabel());
        node.setParentTitle(parentTitle);
        node.setPid(pid);
        List<MyTreeNode> childList = new ArrayList<MyTreeNode>();
        node.setChildren(childList);
        List<DictionaryInfo> sList = dictionaryInfoRepository.findByPid(d.getId());
        if(sList == null)
            return node;
        for (DictionaryInfo dictionaryInfo : sList) {
            childList.add(getDictionaryTreeNode(node.getId(), dictionaryInfo, node.getValue()));
        }
        return node;
    }

    /**
     * MyTreeNode getMenuTreeNode(Integer userTypeId) 获得角色的菜单树根节点
     */
    public List<MyTreeNode> getMenuTreeNodeList() {
        List<MyTreeNode> childList = new ArrayList<MyTreeNode>();
        List<MenuInfo> sList = menuInfoRepository.findByUserTypeIds("");
        if(sList == null)
            return childList;
        for (MenuInfo menuInfo : sList) {
            childList.add(getMenuTreeNode(null, menuInfo, ""));
        }
        return childList;
    }
    /**
     * MyTreeNode getMenuTreeNode(Integer userTypeId) 获得角色的某个菜单的菜单树根节点
     */
    public MyTreeNode getMenuTreeNode(Integer pid, MenuInfo d,String parentTitle) {
        MyTreeNode  node = new MyTreeNode(d.getId(),d.getName(),d.getTitle(),null);
        node.setLabel(d.getId()+"-"+d.getTitle());
        node.setUserTypeIds(d.getUserTypeIds());
        node.setParentTitle(parentTitle);
        node.setPid(pid);
        List<MyTreeNode> childList = new ArrayList<MyTreeNode>();
        node.setChildren(childList);
        List<MenuInfo> sList = menuInfoRepository.findByUserTypeIds("",d.getId());
        if(sList == null)
            return node;
        for (MenuInfo menuInfo : sList) {
            childList.add(getMenuTreeNode(node.getId(), menuInfo, node.getTitle()));
        }
        return node;
    }



    public List<Map<String, Object>> getMenuList(Integer userTypeId, Integer pid) {
        List<Map<String, Object>> sList = new ArrayList<>();
        Map<String, Object> ms;
        List<MenuInfo> msList = menuInfoRepository.findByUserTypeIds(userTypeId + "", pid);
        String name, path;
        if (msList != null) {
            for (MenuInfo info : msList) {
                ms = new HashMap<>();
                name = info.getName();
                if(name!= null&& !name.isEmpty()) {
                    path = name.substring(0,1).toLowerCase()+ name.substring(1);
                }else {
                    path = "";
                }
                ms.put("id", info.getId());
                ms.put("name", name);
                ms.put("path", path);
                ms.put("title", info.getTitle());
                ms.put("sList", getMenuList(userTypeId, info.getId()));
                sList.add(ms);
            }
        }
        return sList;
    }

    public DataResponse getMenuList(DataRequest dataRequest) {
        List<Map<String, Object>> dataList = new ArrayList<>();
        Integer userTypeId = dataRequest.getInteger("userTypeId");
        if (userTypeId == null) {
            Integer userId = CommonMethod.getUserId();
            if (userId == null)
                return CommonMethod.getReturnData(dataList);
            userTypeId = userRepository.findById(userId).get().getUserType().getId();
        }
        List<MenuInfo> mList = menuInfoRepository.findByUserTypeIds(userTypeId + "");
        Map<String, Object> m;
        List<Map<String, Object>> sList;
        String name, path;
        for (MenuInfo info : mList) {
            m = new HashMap<>();
            name = info.getName();
            if(name!= null&& !name.isEmpty()) {
                path = name.substring(0,1).toLowerCase()+ name.substring(1);
            }else {
                path = "";
            }
            m.put("id", info.getId());
            m.put("path", path);
            m.put("name", name);
            m.put("title", info.getTitle());
            sList = getMenuList(userTypeId, info.getId());
            m.put("sList", sList);
            dataList.add(m);
        }
        return CommonMethod.getReturnData(dataList);
    }


    public OptionItemList getRoleOptionItemList(@Valid @RequestBody DataRequest dataRequest) {
        List<UserType> uList = userTypeRepository.findAll();
        List<OptionItem> itemList = new ArrayList<>();
        for (UserType ut : uList) {
            itemList.add(new OptionItem(ut.getId(), null, ut.getName()));
        }
        return new OptionItemList(0, itemList);
    }


    public DataResponse menuDelete(DataRequest dataRequest) {
        Integer id = dataRequest.getInteger("id");
        int count  = menuInfoRepository.countMenuInfoByPid(id);
        if(count > 0) {
            return CommonMethod.getReturnMessageError("存在子菜单，不能删除！");
        }
        Optional<MenuInfo> op = menuInfoRepository.findById(id);
        op.ifPresent(menuInfoRepository::delete);
        return CommonMethod.getReturnMessageOK();
    }

    public DataResponse menuSave(DataRequest dataRequest) {
        Integer editType = dataRequest.getInteger("editType");
        Map<String, Object> node = dataRequest.getMap("node");
        Integer pid = CommonMethod.getInteger(node,"pid");
        Integer id = CommonMethod.getInteger(node,"id");
        String name = CommonMethod.getString(node,"value");
        String title = CommonMethod.getString(node,"title");
        String userTypeIds = CommonMethod.getString(node,"userTypeIds");
        Optional<MenuInfo> op;
        MenuInfo m = null;
        if (id != null) {
            op = menuInfoRepository.findById(id);
            if(op.isPresent()) {
                if(editType == 0 || editType == 1)
                    return CommonMethod.getReturnMessageError("主键已经存在，不能添加");
                m = op.get();
            }
        }
        if (m == null)
            m = new MenuInfo();
        m.setId(id);
        m.setTitle(title);
        m.setName(name);
        m.setPid(pid);
        m.setUserTypeIds(userTypeIds);
        menuInfoRepository.save(m);
        return CommonMethod.getReturnMessageOK();
    }

    public DataResponse deleteDictionary(DataRequest dataRequest) {
        Integer id = dataRequest.getInteger("id");
        int count = dictionaryInfoRepository.countDictionaryInfoByPid(id);
        if(count > 0) {
            return CommonMethod.getReturnMessageError("存在数据项，不能删除！");
        }
        Optional<DictionaryInfo> op = dictionaryInfoRepository.findById(id);
        op.ifPresent(dictionaryInfoRepository::delete);
        return CommonMethod.getReturnMessageOK();
    }

    public DataResponse dictionarySave(DataRequest dataRequest) {
        Integer id = dataRequest.getInteger("id");
        Integer pid = dataRequest.getInteger("pid");
        String value = dataRequest.getString("value");
        String title = dataRequest.getString("title");
        DictionaryInfo m = null;
        if(id != null) {
            Optional<DictionaryInfo> op = dictionaryInfoRepository.findById(id);
            if (op.isPresent()) {
                m = op.get();
            }
        }
        if(m == null) {
            m = new DictionaryInfo();
            m.setPid(pid);
        }
        m.setLabel(title);
        m.setValue(value);
        dictionaryInfoRepository.save(m);
        return CommonMethod.getReturnMessageOK();
    }

    public OptionItemList getDictionaryOptionItemList(DataRequest dataRequest) {
        String code = dataRequest.getString("code");
        List<DictionaryInfo> dList = dictionaryInfoRepository.getDictionaryInfoList(code);
        OptionItem item;
        List<OptionItem> itemList = new ArrayList<>();
        for (DictionaryInfo d : dList) {
            itemList.add(new OptionItem(d.getId(), d.getValue(), d.getLabel()));
        }
        return new OptionItemList(0, itemList);
    }

    public ResponseEntity<StreamingResponseBody> getFileByteData(DataRequest dataRequest) {
        String fileName = dataRequest.getString("fileName");
        try {
            File file = new File(attachFolder + fileName);
            int len = (int) file.length();
            byte[] data = new byte[len];
            FileInputStream in = new FileInputStream(file);
            int size = in.read(data);
            in.close();
            MediaType mType = new MediaType(MediaType.APPLICATION_OCTET_STREAM);
            StreamingResponseBody stream = outputStream -> {
                outputStream.write(data);
            };
            return ResponseEntity.ok()
                    .contentType(mType)
                    .body(stream);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return ResponseEntity.internalServerError().build();
    }


    public DataResponse uploadPhoto(byte[] barr,String remoteFile) {
        try {
            OutputStream os = new FileOutputStream(new File(attachFolder + remoteFile));
            os.write(barr);
            os.close();
            return CommonMethod.getReturnMessageOK();
        } catch (Exception e) {
            return CommonMethod.getReturnMessageError("上传错误");
        }
    }
    public ResponseEntity<StreamingResponseBody> getBlobByteData(DataRequest dataRequest) {
        Integer userId = dataRequest.getInteger("userId");
        try {
            byte [] data;
            Optional<User> op = userRepository.findById(userId);
            if(op.isPresent()) {
                User user = op.get();
                data = user.getPhoto();
                if(data == null)
                    return ResponseEntity.notFound().build();
            }else {
                return ResponseEntity.internalServerError().build();
            }
            MediaType mType = new MediaType(MediaType.APPLICATION_OCTET_STREAM);
            StreamingResponseBody stream = outputStream -> {
                outputStream.write(data);
            };
            return ResponseEntity.ok()
                    .contentType(mType)
                    .body(stream);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return ResponseEntity.internalServerError().build();
    }

    public DataResponse uploadPhotoBlob(byte[] barr,String userId ) {
        try {
            Optional<User> op = userRepository.findById(Integer.parseInt(userId));
            if(op.isEmpty())
                return CommonMethod.getReturnMessageError("用户不存在！");
            User user = op.get();
            user.setPhoto(barr);
            userRepository.save(user);
            return CommonMethod.getReturnMessageOK();
        } catch (Exception e) {
            return CommonMethod.getReturnMessageError("上传错误");
        }
    }

    public DataResponse updatePassword(DataRequest dataRequest) {
        String oldPassword = dataRequest.getString("oldPassword");  //获取oldPassword
        String newPassword = dataRequest.getString("newPassword");  //获取newPassword
        if (oldPassword == null || oldPassword.isEmpty() || newPassword == null || newPassword.isEmpty()) {
            return CommonMethod.getReturnMessageError("旧密码和新密码不能为空");
        }
        if (newPassword.length() > 20) {
            return CommonMethod.getReturnMessageError("密码长度最大为20位");
        }
        Optional<User> op = userRepository.findById(Objects.requireNonNull(CommonMethod.getUserId()));
        if (op.isEmpty())
            return CommonMethod.getReturnMessageError("账户不存在！");  //通知前端操作正常
        User u = op.get();
        if (!encoder.matches(oldPassword, u.getPassword())) {
            return CommonMethod.getReturnMessageError("原始密码不正确！");
        }
        u.setPassword(encoder.encode(newPassword));
        userRepository.save(u);
        return CommonMethod.getReturnMessageOK();  //通知前端操作正常
    }


    @PostMapping("/uploadHtmlString")
    @PreAuthorize(" hasRole('ADMIN') ")
    public DataResponse uploadHtmlString(DataRequest dataRequest) {
        String str = dataRequest.getString("html");
        String html = new String(Base64.getDecoder().decode(str.getBytes(StandardCharsets.UTF_8)));
        int htmlCount = ComDataUtil.getInstance().addHtmlString(html);
        return CommonMethod.getReturnData(htmlCount);
    }

    public ResponseEntity<StreamingResponseBody> htmlGetBaseHtmlPage(HttpServletRequest request) {
        String htmlCountStr = request.getParameter("htmlCount");
        int htmlCount = Integer.parseInt(htmlCountStr);
        String html = ComDataUtil.getInstance().getHtmlString(htmlCount);
        MediaType mType = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);
        try {
            byte[] data = html.getBytes();
            StreamingResponseBody stream = outputStream -> {
                outputStream.write(data);
            };
            return ResponseEntity.ok()
                    .contentType(mType)
                    .body(stream);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }



    //  Web 请求
    public DataResponse getPhotoImageStr(DataRequest dataRequest) {
        String fileName = dataRequest.getString("fileName");
        String str = "";
        try {
            File file = new File(attachFolder + fileName);
            int len = (int) file.length();
            byte[] data = new byte[len];
            FileInputStream in = new FileInputStream(file);
            len = in.read(data);
            in.close();
            String imgStr = "data:image/png;base64,";
            String s = new String(Base64.getEncoder().encode(data));
            imgStr = imgStr + s;
            return CommonMethod.getReturnData(imgStr);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return CommonMethod.getReturnMessageError("下载错误！");
    }

    public DataResponse uploadPhotoWeb(Map<String,Object> pars, MultipartFile file) {
        try {
            String remoteFile = CommonMethod.getString(pars, "remoteFile");
            InputStream in = file.getInputStream();
            int size = (int) file.getSize();
            byte[] data = new byte[size];
            int len =  in.read(data);
            in.close();
            OutputStream os = new FileOutputStream(new File(attachFolder + remoteFile));
            os.write(data);
            os.close();
            return CommonMethod.getReturnMessageOK();
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return CommonMethod.getReturnMessageOK();
    }
    public DataResponse uploadPhotoBlobWeb(Map<String,Object> pars, MultipartFile file) {
        try {
            String userId = CommonMethod.getString(pars, "remoteFile");
            InputStream in = file.getInputStream();
            int size = (int) file.getSize();
            byte[] data = new byte[size];
            int len =  in.read(data);
            in.close();
            return uploadPhotoBlob(data,userId);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return CommonMethod.getReturnMessageOK();
    }

}
