package cn.edu.sdu.java.server.util;

import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.UserDetailsImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.math.BigDecimal;
import java.util.*;

/**
 * CommonMethod 公共处理方法实例类
 */
public class CommonMethod {
    private static final ObjectMapper mapper = new ObjectMapper();
    public static final MediaType exelType = new MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    public static DataResponse getReturnData(Object obj, String msg){
        return new   DataResponse(0,obj,msg);
    }
    public static DataResponse getReturnMessage(Integer code, String msg){
        return new   DataResponse(code,null,msg);
    }
    public static  DataResponse getReturnData(Object obj){
        return getReturnData(obj,null);
    }
    public static DataResponse getReturnMessageOK(String msg){
        return getReturnMessage(0, msg);
    }
    public static DataResponse getReturnMessageOK(){
        return getReturnMessage(0, null);
    }
    public static DataResponse getReturnMessageError(String msg){
        return getReturnMessage(1, msg);
    }

    /**
     * Integer getUserId() 获得用户的userId 该static 方法可以在任何的Controller和Service中使用，可以获得当前登录用户的用户ID，可以通过在Web请求服务中使用该方法获取当前请求客户的信息
     * @return
     */
    public static Integer getUserId(){
        Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(!(obj instanceof UserDetailsImpl userDetails))
            return null;
        return userDetails.getId();
    }
    public static String getUsername(){
        Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(!(obj instanceof UserDetailsImpl userDetails))
            return null;
        return userDetails.getUsername();
    }
    public static String getRoleName(){
        Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(!(obj instanceof UserDetailsImpl userDetails))
            return null;
        return  userDetails.getAuthorities().iterator().next().toString();
    }

    public static String getNextNum2(String num) {
        String str;
        String prefix;
        if(num.length() == 2) {
            str = num;
            prefix= "";
        }
        else {
            str = num.substring(num.length() - 2);
            prefix = num.substring(0,num.length() - 2);
        }
        int c;
        if(str.charAt(0)=='0') {
            c = str.charAt(1)-'0';
        }else {
            c = (str.charAt(0)-'0')*10 + str.charAt(1)-'0';
        }
        c++;
        if(c < 10) {
            return prefix+"0" + c;
        }else {
            return prefix+ c;
        }
    }
    public static String getNextNum3( String num) {
        String str;
        String prefix;
        if(num.length() == 3) {
            str = num;
            prefix= "";
        }
        else {
            str = num.substring(num.length() - 3);
            prefix = num.substring(0,num.length() - 3);
        }
        int c;
        if(str.charAt(0)=='0') {
            if(str.charAt(1)=='0') {
                c = str.charAt(2)-'0';
            }else {
                c = (str.charAt(1)-'0')*10 + str.charAt(2)-'0';
            }
        }else {
            c = (str.charAt(0)-'0')*100  +(str.charAt(1)-'0')*10 + str.charAt(2)-'0';
        }
        c++;
        if(c < 10) {
            return prefix+"00" + c;
        }else if(c < 100) {
            return prefix+"0" + c;
        }else {
            return prefix+ c;
        }
    }
    public static String getNextNum4( String num) {
        String str;
        String prefix;
        if(num.length() == 4) {
            str = num;
            prefix= "";
        }
        else {
            str = num.substring(num.length() - 4);
            prefix = num.substring(0,num.length() - 4);
        }
        int c;
        if(str.charAt(0)=='0') {
            if (str.charAt(1) == '0') {
                if (str.charAt(2) == '0') {
                    c = str.charAt(3) - '0';
                } else {
                    c = (str.charAt(2) - '0') * 10 + str.charAt(3) - '0';
                }
            } else {
                c = (str.charAt(1) - '0') * 100 + (str.charAt(2) - '0') * 10 + str.charAt(3) - '0';
            }
        }else {
            c = (str.charAt(0) - '0') * 1000 + (str.charAt(1) - '0') * 100 + (str.charAt(2) - '0') * 10 + str.charAt(3) - '0';
        }
        c++;
        if(c < 10) {
            return prefix+"000" + c;
        }else if(c < 100) {
            return prefix+"00" + c;
        }else if(c < 1000){
            return prefix + "0" + c;
        }else {
            return prefix+ c;
        }
    }
    public static String[] getStrings(Map<String,Object> data,String key){
        Object obj = data.get(key);
        if(obj == null)
            return new String[]{};
        if(obj instanceof String[])
            return (String[])obj;
        return new String[]{};
    }

    public static String getString(Map<String,Object> data,String key){
        Object obj = data.get(key);
        if(obj == null)
            return "";
        if(obj instanceof String)
            return (String)obj;
        return obj.toString();
    }
    public static Boolean getBoolean(Map<String,Object> data,String key){
        Object obj = data.get(key);
        if(obj == null)
            return false;
        if(obj instanceof Boolean)
            return (Boolean)obj;
        return "true".equals(obj.toString());
    }
    public static List<?> getList(Map<String,Object> data, String key){
        Object obj = data.get(key);
        if(obj == null)
            return new ArrayList<>();
        if(obj instanceof List)
            return (List<?>)obj;
        else
            return new ArrayList<>();
    }
    public static Map<String,Object> getMap(Map<String,Object> data,String key){
        Object obj = data.get(key);
        if(obj == null)
            return new HashMap<>();
        if(obj instanceof Map)
            return (Map<String, Object>) obj;
        else
            return new HashMap<>();
    }

    public static Integer getInteger(Map<String,Object> data,String key) {
        Object obj = data.get(key);
        if(obj == null)
            return null;
        if(obj instanceof Integer)
            return (Integer)obj;
        String str = obj.toString();
        try {
            return (int)Double.parseDouble(str);
        }catch(Exception e) {
            return null;
        }
    }
    public static Integer getInteger0(Map<String,Object> data,String key) {
        Object obj = data.get(key);
        if(obj == null)
            return 0;
        if(obj instanceof Integer)
            return (Integer)obj;
        String str = obj.toString();
        try {
            return (int)Double.parseDouble(str);
        }catch(Exception e) {
            return 0;
        }
    }
    public static Long getLong(Map<String,Object> data,String key) {
        Object obj = data.get(key);
        if(obj == null)
            return null;
        if(obj instanceof Long)
            return (Long)obj;
        String str = obj.toString();
        try {
            return Long.parseLong(str);
        }catch(Exception e) {
            return null;
        }
    }

    public static Double getDouble(Map<String,Object> data,String key) {
        Object obj = data.get(key);
        if(obj == null)
            return null;
        if(obj instanceof Double)
            return (Double)obj;
        String str = obj.toString();
        try {
            return Double.parseDouble(str);
        }catch(Exception e) {
            return null;
        }
    }
    public static Double getDouble0(Map<String,Object> data,String key) {
        Double d0 = 0d;
        Object obj = data.get(key);
        if(obj == null)
            return d0;
        if(obj instanceof Double)
            return (Double)obj;
        String str = obj.toString();
        try {
            return Double.parseDouble(str);
        }catch(Exception e) {
            return d0;
        }
    }
    public static Date getDate(Map<String,Object> data, String key) {
        Object obj = data.get(key);
        if(obj == null)
            return null;
        if(obj instanceof Date)
            return (Date)obj;
        String str = obj.toString();
        return DateTimeTool.formatDateTime(str,"yyyy-MM-dd");
    }
    public static Date getTime(Map<String,Object> data,String key) {
        Object obj = data.get(key);
        if(obj == null)
            return null;
        if(obj instanceof Date)
            return (Date)obj;
        String str = obj.toString();
        return DateTimeTool.formatDateTime(str,"yyyy-MM-dd HH:mm:ss");
    }

    public static String replaceNameValue(String html, Map<String,String>m) {
        if(html== null || html.isEmpty())
            return html;
        StringBuilder buf = new StringBuilder();
        StringTokenizer sz = new StringTokenizer(html,"$");
        if(sz.countTokens()<=1)
            return html;
        String str,key,value;
        int index;
        while(sz.hasMoreTokens()) {
            str = sz.nextToken();
            if(str.charAt(0)== '{') {
                index = str.indexOf("}",1);
                key = str.substring(1,index);
                value = m.get(key);
                if(value== null){
                    value = "";
                }
                buf.append(value).append(str, index + 1, str.length());
            }else {
                buf.append(str);
            }
        }
        return buf.toString();
    }

    public static String addHeadInfo(String html,String head) {
        int index0 = html.indexOf("<head>");
        int index1 = html.indexOf("</head>");
        return html.substring(0,index0+6)+head + html.substring(index1);
    }

    public static String removeErrorString(String html,String ... subs) {
        if(html== null || html.isEmpty())
            return html;
        StringBuilder buf;
        int index;
        int oldIndex;
        int slen;
        String content = html;
        for(String sub : subs) {
            slen = sub.length();
            buf = new StringBuilder();
            index = 0;
            oldIndex = 0;
            while(index >= 0) {
                index = content.indexOf(sub,oldIndex);
                if(index > 0) {
                    buf.append(content, oldIndex, index);
                    oldIndex = index+slen;
                }else {
                    buf.append(content, oldIndex, content.length());
                }
            }
            content = buf.toString();
        }
        return content;
    }

    public static String getLevelFromScore(Double score) {
        if(score == null)
            return "";
        if(score >=89.5)
            return "优";
        if(score >=79.5)
            return "良";
        if(score >=69.5)
            return "中";
        if(score >=59.5)
            return "及格";
        return "不及格";
    }
    public static ResponseEntity<StreamingResponseBody> getByteDataResponseBodyPdf(byte[] data) {
        if (data == null || data.length == 0)
            return ResponseEntity.internalServerError().build();
        try {
            StreamingResponseBody stream = outputStream -> {
                outputStream.write(data);
            };
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(stream);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    public static Double getDouble2(Double f) {
        if (f == null)
            return 0d;
        BigDecimal bg = new BigDecimal(f);
        return bg.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
    }
    public static String ObjectToJSon(Object o){
        try {
            //p是指定要转换的对象
            String json = mapper.writeValueAsString(o);
            return json;
        }catch(Exception e){
            return "";
        }
    }
    public static XSSFCellStyle createCellStyle(XSSFWorkbook workbook, int fontSize) {
        XSSFFont font = workbook.createFont();
        //在对应的workbook中新建字体
        font.setFontName("微软雅黑");
        //字体微软雅黑
        font.setFontHeightInPoints((short) fontSize);
        //设置字体大小
        XSSFCellStyle style = workbook.createCellStyle();
        //新建Cell字体
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
