package com.caishi;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.caishi.base.v3.MybatisPlusSuperClass;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * @author by keray
 * date:2019/7/25 15:33
 */
@Data
@MybatisPlusSuperClass
public class BaseEntityV3 extends Model<BaseEntityV3> {
    /**
     * 主键id
     */
    @ApiModelProperty(value = "主键id", name = "id")
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 创建时间
     */
    @ApiModelProperty(value = "创建时间", name = "createTime")
    private LocalDateTime createTime;

    /**
     * 修改时间
     */
    @ApiModelProperty(value = "修改时间", name = "modifyTime")
    private LocalDateTime modifyTime;

    /**
     * 是否删除
     */
    @ApiModelProperty(value = "是否删除（0/未删除 1删除）", name = "deleted")
    @TableLogic(delval = "1", value = "0")
    private Boolean deleted = false;

    /**
     * 删除时间
     */
    @ApiModelProperty(value = "删除时间", name = "deleteTime")
    private LocalDateTime deleteTime;

    /**
     * 创建来源
     */
    @ApiModelProperty(value = "创建来源", name = "createBy")
    private String createBy;

    /**
     * 修改来源
     */
    @ApiModelProperty(value = "修改来源", name = "updateBy")
    private String updateBy;

    @Override
    protected Serializable pkVal() {
        return this.id;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        } else {
            return obj instanceof BaseEntityV3 && StrUtil.equals(this.getId(), ((BaseEntityV3) obj).getId());
        }
    }


    /**
     * <p>
     * <h3>作者 keray</h3>
     * <h3>时间： 2019/9/4 15:00</h3>
     * 清除实体中的空字符串
     * </p>
     *
     * @param
     * @return <p> {@link T} </p>
     * @throws
     */
    public <T> T clearEmptyStringField(Class<T> clazz) {
        List<Field> fields = scanFields(this.getClass(), null);
        for (Field field : fields) {
            try {
                if (field.getType() == String.class) {
                    Method get = scanMethod(clazz, "get" + StrUtil.upperFirst(field.getName()));
                    String result = (String) get.invoke(this);
                    if ("".equals(result)) {
                        Method set = scanMethod(clazz, "set" + StrUtil.upperFirst(field.getName()),String.class);
                        set.invoke(this, (Object) null);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return (T) this;
    }

    private Method scanMethod(Class<?> p, String name,Class<?> ... type) throws NoSuchMethodException {
        if (p == null) {
            return null;
        }
        Method method = p.getMethod(name,type);
        return method == null ? scanMethod(p.getSuperclass(), name) : method;
    }

    private List<Field> scanFields(Class<?> p, List<Field> fields) {
        if (fields == null) {
            fields = new LinkedList<>();
        }
        fields.addAll(Arrays.asList(p.getDeclaredFields()));
        if (p.getSuperclass() != null) {
            scanFields(p.getSuperclass(), fields);
        }
        return fields;
    }
}
