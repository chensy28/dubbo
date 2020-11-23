/*
 * Copyright 1999-2011 Alibaba Group.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.dubbo.rpc;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * RPC Result.
 * 返回结果包含：结果result、异常exception、附加参数attachments
 *
 * @author qianlei
 * @serial Don't change the class name and properties.
 */
public class RpcResult implements Result, Serializable {

    private static final long serialVersionUID = -6925924956850004727L;

    private Object result; // 结果值

    private Throwable exception; //异常

    private Map<String, String> attachments = new HashMap<String, String>(); //附加参数

    public RpcResult() {
    }

    public RpcResult(Object result) {
        this.result = result;
    }

    public RpcResult(Throwable exception) {
        this.exception = exception;
    }

    public Object recreate() throws Throwable {
        if (exception != null) {
            throw exception;
        }
        return result;
    }

    /**
     * @see com.alibaba.dubbo.rpc.RpcResult#getValue()
     * @deprecated Replace to getValue()
     */
    @Deprecated
    public Object getResult() {
        return getValue();
    }

    /**
     * @see com.alibaba.dubbo.rpc.RpcResult
     * @deprecated Replace to setValue()
     */
    @Deprecated
    public void setResult(Object result) {
        setValue(result);
    }

    public Object getValue() {
        return result;
    }

    public void setValue(Object value) {
        this.result = value;
    }

    public Throwable getException() {
        return exception;
    }

    public void setException(Throwable e) { //设置异常信息
        this.exception = e;
    }

    public boolean hasException() {
        return exception != null;
    }

    public Map<String, String> getAttachments() {
        return attachments;
    }

    public void setAttachments(Map<String, String> map) {
        if (map != null && map.size() > 0) {
            attachments.putAll(map);
        }
    }

    public String getAttachment(String key) {
        return attachments.get(key);
    }

    public String getAttachment(String key, String defaultValue) {
        String result = attachments.get(key);
        if (result == null || result.length() == 0) {
            result = defaultValue;
        }
        return result;
    }

    public void setAttachment(String key, String value) {
        attachments.put(key, value);
    }

    @Override
    public String toString() {
        // 输出结果如：RpcResult [result=java.lang.Object@6f94fa3e, exception=java.lang.Throwable]
        return "RpcResult [result=" + result + ", exception=" + exception + "]";
    }
}