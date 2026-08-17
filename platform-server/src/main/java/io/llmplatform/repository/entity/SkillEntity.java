package io.llmplatform.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 技能包行。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("skills")
public class SkillEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String name;

    private String directory;

    private Boolean enabled;
}
