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
package com.alibaba.dubbo.rpc.filter;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.RpcResult;

/**
 * EchoInvokerFilter
 *
 * @author william.liangf
 */
@Activate(group = Constants.PROVIDER, order = -110000)
public class EchoFilter implements Filter {//read finish
    //回响测试主要用来检测服务是否正常
    public Result invoke(Invoker<?> invoker, Invocation inv) throws RpcException { //10/22 功能待使用，解：所有服务都实现EchoService接口，只需将任意服务引用强制转型为 EchoService，即可使用
        if (inv.getMethodName().equals(Constants.$ECHO) && inv.getArguments() != null && inv.getArguments().length == 1)
            return new RpcResult(inv.getArguments()[0]); //回声传入什么值，就返回什么值
        return invoker.invoke(inv);
    }

}