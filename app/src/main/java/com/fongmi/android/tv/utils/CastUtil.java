package com.fongmi.android.tv.utils;

import android.text.Html;
import android.text.TextUtils;

import com.fongmi.android.tv.bean.CastMember;

import java.util.ArrayList;
import java.util.List;

/**
 * 演职人员工具类
 * 用于解析和处理视频作品中的演员、导演信息
 */
public class CastUtil {
    
    /**
     * 解析演职人员字符串为 CastMember 列表
     * 支持多种分隔符格式，智能识别
     * 自动清理 HTML 标签和前后空格
     * 过滤空字符串
     * 
     * @param text 演职人员字符串（可能包含多个人名）
     * @param type 演职人员类型（演员或导演）
     * @return 解析后的 CastMember 列表
     */
    public static List<CastMember> parseCastMembers(String text, CastMember.CastType type) {
        List<CastMember> members = new ArrayList<>();
        
        // 如果输入为空，直接返回空列表
        if (TextUtils.isEmpty(text)) {
            return members;
        }
        
        // 清理 HTML 标签
        String cleanText = sanitizeHtml(text);
        
        // 如果清理后为空，返回空列表
        if (TextUtils.isEmpty(cleanText)) {
            return members;
        }
        
        // 智能分割：尝试多种分隔符
        String[] names = null;
        
        // 1. 优先尝试逗号分隔
        if (cleanText.contains(",")) {
            names = cleanText.split(",");
        }
        // 2. 尝试斜杠分隔
        else if (cleanText.contains("/")) {
            names = cleanText.split("/");
        }
        // 3. 尝试中文顿号分隔
        else if (cleanText.contains("、")) {
            names = cleanText.split("、");
        }
        // 4. 尝试多个空格分隔（2个或以上）
        else if (cleanText.matches(".*\\s{2,}.*")) {
            names = cleanText.split("\\s{2,}");
        }
        // 5. 尝试单个空格分隔（但要小心，可能是名字中的空格）
        else if (cleanText.contains(" ")) {
            // 如果有多个空格分隔的词，且每个词长度合理（2-10个字符），则认为是多个名字
            String[] parts = cleanText.split("\\s+");
            if (parts.length > 1 && parts.length <= 20) {
                boolean allReasonable = true;
                for (String part : parts) {
                    if (part.length() < 2 || part.length() > 10) {
                        allReasonable = false;
                        break;
                    }
                }
                if (allReasonable) {
                    names = parts;
                }
            }
        }
        
        // 如果没有找到分隔符，整个字符串作为一个名字
        if (names == null) {
            names = new String[]{cleanText};
        }
        
        // 遍历所有人名
        for (int i = 0; i < names.length; i++) {
            String name = names[i].trim();
            
            // 过滤空字符串
            if (!name.isEmpty()) {
                members.add(new CastMember(name, type));
            }
        }
        
        return members;
    }
    
    /**
     * 清理 HTML 标签
     * 将 HTML 格式的文本转换为纯文本
     * 
     * @param text 可能包含 HTML 标签的文本
     * @return 清理后的纯文本
     */
    private static String sanitizeHtml(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        
        // 使用 Android 的 Html.fromHtml() 方法清理 HTML 标签
        String cleaned = Html.fromHtml(text).toString();
        
        // 去除前后空格
        return cleaned.trim();
    }
    
    /**
     * 验证演职人员名字是否有效
     * 有效的名字不能为 null、空字符串或仅包含空格
     * 
     * @param name 演职人员名字
     * @return true 如果名字有效，否则返回 false
     */
    public static boolean isValidCastName(String name) {
        return name != null && !name.trim().isEmpty();
    }
}
