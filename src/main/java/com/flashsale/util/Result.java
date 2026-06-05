package com.flashsale.util;

import com.flashsale.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回结果封装类
 * 
 * 设计规范：
 * 1. 所有接口必须返回此格式
 * 2. code: 业务状态码，0 表示成功，非 0 表示失败
 * 3. message: 响应消息
 * 4. data: 响应数据
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 业务状态码
     * 0: 成功
     * 非 0: 失败
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 私有构造函数，强制使用静态方法
     */
    private Result() {
    }

    /**
     * 成功返回结果
     * 
     * @param data 数据
     * @param <T> 数据类型
     * @return 统一返回体
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(0);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    /**
     * 成功返回结果（无数据）
     * 
     * @return 统一返回体
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 失败返回结果
     * 
     * @param errorCode 错误码枚举
     * @return 统一返回体
     */
    public static <T> Result<T> error(ErrorCode errorCode) {
        Result<T> result = new Result<>();
        result.setCode(errorCode.getCode());
        result.setMessage(errorCode.getMessage());
        return result;
    }

    /**
     * 失败返回结果（自定义消息）
     * 
     * @param message 错误消息
     * @return 统一返回体
     */
    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.setCode(ErrorCode.SYSTEM_ERROR.getCode());
        result.setMessage(message);
        return result;
    }

    /**
     * 失败返回结果（自定义状态码和消息）
     * 
     * @param code 状态码
     * @param message 错误消息
     * @return 统一返回体
     */
    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    /**
     * 判断是否成功
     * 
     * @return true-成功，false-失败
     */
    public boolean isSuccess() {
        return this.code != null && this.code == 0;
    }
}
