package com.heima.model.admin.dtos;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@ApiModel("登录请求对象")
@Data
public class AdUserDto
{

    /**
     * 用户名
     */
    @ApiModelProperty(value = "用户名",
                      required = true)
    private String name;

    /**
     * 密码
     */
    @ApiModelProperty(value = "密码",
                      required = true)
    private String password;
}
