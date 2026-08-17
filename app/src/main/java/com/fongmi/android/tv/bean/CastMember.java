package com.fongmi.android.tv.bean;

/**
 * 演职人员数据模型
 * 用于表示视频作品中的演员或导演信息
 */
public class CastMember {
    
    private String name;
    private CastType type;
    
    /**
     * 演职人员类型枚举
     */
    public enum CastType {
        ACTOR,      // 演员
        DIRECTOR    // 导演
    }
    
    /**
     * 构造函数
     * @param name 演职人员名字
     * @param type 演职人员类型（演员或导演）
     */
    public CastMember(String name, CastType type) {
        this.name = name;
        this.type = type;
    }
    
    /**
     * 获取演职人员名字
     * @return 名字
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取演职人员类型
     * @return 类型（演员或导演）
     */
    public CastType getType() {
        return type;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CastMember)) return false;
        CastMember other = (CastMember) obj;
        return name.equals(other.name) && type == other.type;
    }
    
    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + type.hashCode();
        return result;
    }
    
    @Override
    public String toString() {
        return "CastMember{name='" + name + "', type=" + type + "}";
    }
}
