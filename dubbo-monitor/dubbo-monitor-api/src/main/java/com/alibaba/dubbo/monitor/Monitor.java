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
package com.alibaba.dubbo.monitor;

import com.alibaba.dubbo.common.Node;

/**
 * Monitor. (SPI, Prototype, ThreadSafe)
 *
 * @author william.liangf
 * @see com.alibaba.dubbo.monitor.MonitorFactory#getMonitor(com.alibaba.dubbo.common.URL)
 */
//继承关系：Monitor接口是一个Node，并且包含MonitorService功能，它的实现类是DubboMonitor
public interface Monitor extends Node, MonitorService {/**@c */

}