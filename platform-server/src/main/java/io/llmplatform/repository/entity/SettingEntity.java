package io.llmplatform.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 键值设置行。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("settings")
public class SettingEntity {

    @TableId(type = IdType.INPUT)
    private String key;

    private String value;
}
