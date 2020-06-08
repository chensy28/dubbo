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
package com.alibaba.dubbo.registry.zookeeper;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.ConcurrentHashSet;
import com.alibaba.dubbo.common.utils.UrlUtils;
import com.alibaba.dubbo.registry.NotifyListener;
import com.alibaba.dubbo.registry.support.FailbackRegistry;
import com.alibaba.dubbo.remoting.zookeeper.ChildListener;
import com.alibaba.dubbo.remoting.zookeeper.StateListener;
import com.alibaba.dubbo.remoting.zookeeper.ZookeeperClient;
import com.alibaba.dubbo.remoting.zookeeper.ZookeeperTransporter;
import com.alibaba.dubbo.rpc.RpcException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * ZookeeperRegistry
 *
 * @author william.liangf
 */
public class ZookeeperRegistry extends FailbackRegistry {

    private final static Logger logger = LoggerFactory.getLogger(ZookeeperRegistry.class);

    private final static int DEFAULT_ZOOKEEPER_PORT = 2181;

    private final static String DEFAULT_ROOT = "dubbo";

    private final String root;

    private final Set<String> anyServices = new ConcurrentHashSet<String>();

    /**@c 线程安全的map 以对象最为键 比较少见*/
    private final ConcurrentMap<URL, ConcurrentMap<NotifyListener, ChildListener>> zkListeners = new ConcurrentHashMap<URL, ConcurrentMap<NotifyListener, ChildListener>>();

    private final ZookeeperClient zkClient;

    /**
     * 构造函数，构建ZookeeperRegistry实例
     * 1）基于url对继承的父类属性进行设置
     *   1.1）调用父类FailbackRegistry构造函数，对属性进行设置
     *     1.1.1）获取retry.period对应的重试时间，默认是5秒，也就是注册失败后，每5秒重新进行注册
     *     1.1.2）基于重试时间，构造重试的定时任务retryExecutor，触发重试retry()
     */
    public ZookeeperRegistry(URL url, ZookeeperTransporter zookeeperTransporter) { //url如：zookeeper://localhost:2181/com.alibaba.dubbo.registry.RegistryService?application=api_demo&dubbo=2.0.0&interface=com.alibaba.dubbo.registry.RegistryService&pid=32489&timestamp=1564672337238
        super(url);
        if (url.isAnyHost()) {
            throw new IllegalStateException("registry address == null");
        }
        String group = url.getParameter(Constants.GROUP_KEY, DEFAULT_ROOT); //若没设置group，就以dubbo为根目录
        if (!group.startsWith(Constants.PATH_SEPARATOR)) { //若不带分隔符，则添加分隔符
            group = Constants.PATH_SEPARATOR + group;
        }
        this.root = group; //zk中的根目录
        zkClient = zookeeperTransporter.connect(url); //在运行调用时进入自适应扩展，获取到指定扩展名，得到指定实例，然后调用对应实例中方法,默认zkclient客户端
        zkClient.addStateListener(new StateListener() {
            public void stateChanged(int state) {
                if (state == RECONNECTED) { //当监听到重连的状态变更时，重新注册和订阅
                    try {
                        recover();
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        });
    }

    static String appendDefaultPort(String address) {
        if (address != null && address.length() > 0) {
            int i = address.indexOf(':');
            if (i < 0) {
                return address + ":" + DEFAULT_ZOOKEEPER_PORT;
            } else if (Integer.parseInt(address.substring(i + 1)) == 0) {
                return address.substring(0, i + 1) + DEFAULT_ZOOKEEPER_PORT;
            }
        }
        return address;
    }

    public boolean isAvailable() {
        return zkClient.isConnected();
    }

    public void destroy() {
        super.destroy();
        try {
            zkClient.close();
        } catch (Exception e) {
            logger.warn("Failed to close zookeeper client " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    /**
     * zookeeper 创建节点
     * 1）从url中获取dynamic（动态的）的值
     *    dynamic与zookeeper中ephemeral（短暂的，临时的）的对应关系
     *    dynamic=true（动态的）-》ephemeral=true（临时节点），dynamic=false（非动态的）-》ephemeral=true（永久节点）
     * 2）构建节点的路径 toUrlPath(url)
     * 3）创建指定路径的节点，并指定是否是临时节点
     */
    protected void doRegister(URL url) { //注册节点数据
        try {
            //此处清空zk，看变化
            zkClient.create(toUrlPath(url), url.getParameter(Constants.DYNAMIC_KEY, true)); //创建节点数据，前面的节点是持久节点，最后一个是临时节点
        } catch (Throwable e) {
            throw new RpcException("Failed to register " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    /**
     * zookeeper 删除节点
     * 1）获取url对应的节点路径 toUrlPath(url)
     * 2）删除指定路径的节点
     */
    protected void doUnregister(URL url) {
        try {
            zkClient.delete(toUrlPath(url));
        } catch (Throwable e) {
            throw new RpcException("Failed to unregister " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    /**
     * zookeeper 订阅节点
     * 判断是否是泛型接口，即url中interface值是否是"*"
     * 1）是泛型接口
     *   1.1）获取根目录
     *   1.2）从本地缓存Map中ConcurrentMap<URL, ConcurrentMap<NotifyListener, ChildListener>>
     *       获取到监听者与子监听者的映射关系ConcurrentMap<NotifyListener, ChildListener>
     *   1.3）若监听者的映射关系为空，初始化对应的映射关系
     *   1.4）从映射集合中获取到子监听者ChildListener，若子监听者为空，则进行构建
     *     1.4.1）构建子监听者：使用匿名实现创建ChildListener的实现类，重写childChanged方法，遍历当前的子节点列表
     *            处理子监听者的变化事件：
     *            1.4.1.1）对子节点解码，判断是否在Set<String> anyServices集合中，若不在则添加到anyServices
     *            1.4.1.2）设置url的路径为child，为url添加interface、check参数，并做订阅subscribe(URL url, NotifyListener listener)
     * todo pause 2
     * 2）不是泛型接口
     *
     */
    protected void doSubscribe(final URL url, final NotifyListener listener) {
        try {
            if (Constants.ANY_VALUE.equals(url.getServiceInterface())) { //泛型接口
                String root = toRootPath();
                ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.get(url);
                if (listeners == null) {
                    zkListeners.putIfAbsent(url, new ConcurrentHashMap<NotifyListener, ChildListener>());
                    listeners = zkListeners.get(url);
                }
                ChildListener zkListener = listeners.get(listener);
                if (zkListener == null) {
                    listeners.putIfAbsent(listener, new ChildListener() {
                        public void childChanged(String parentPath, List<String> currentChilds) {
                            for (String child : currentChilds) {
                                child = URL.decode(child);
                                if (!anyServices.contains(child)) {
                                    anyServices.add(child);
                                    subscribe(url.setPath(child).addParameters(Constants.INTERFACE_KEY, child,
                                            Constants.CHECK_KEY, String.valueOf(false)), listener);
                                }
                            }
                        }
                    });
                    zkListener = listeners.get(listener);
                }
                zkClient.create(root, false);/**@c 创建持久节点*/
                List<String> services = zkClient.addChildListener(root, zkListener);
                if (services != null && services.size() > 0) {
                    for (String service : services) {
                        service = URL.decode(service);
                        anyServices.add(service);
                        subscribe(url.setPath(service).addParameters(Constants.INTERFACE_KEY, service,
                                Constants.CHECK_KEY, String.valueOf(false)), listener);
                    }
                }
            } else { //不是泛型接口
                List<URL> urls = new ArrayList<URL>();
                for (String path : toCategoriesPath(url)) { //创建分类节点，并建立当前节点与子节点的监听器
                    ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.get(url); //todo @csy-h1 当前节点与子节点的监听器吗？
                    if (listeners == null) { //监听map为空时，初始化map
                        zkListeners.putIfAbsent(url, new ConcurrentHashMap<NotifyListener, ChildListener>());
                        listeners = zkListeners.get(url);
                    }
                    ChildListener zkListener = listeners.get(listener);
                    if (zkListener == null) {
                        listeners.putIfAbsent(listener, new ChildListener() {
                            public void childChanged(String parentPath, List<String> currentChilds) { //todo @csy-h1 ZookeeperRegistry.this.notify() 这种调用方式待了解？
                                ZookeeperRegistry.this.notify(url, listener, toUrlsWithEmpty(url, parentPath, currentChilds));
                            }
                        });
                        zkListener = listeners.get(listener);
                    }
                    zkClient.create(path, false); //创建持久节点 ，如：/dubbo/com.alibaba.dubbo.demo.ApiDemo/configurators
                    List<String> children = zkClient.addChildListener(path, zkListener); //子节点url列表
                    if (children != null) {
                        urls.addAll(toUrlsWithEmpty(url, path, children));
                    }
                }
                notify(url, listener, urls); //通知
            }
        } catch (Throwable e) {
            throw new RpcException("Failed to subscribe " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    protected void doUnsubscribe(URL url, NotifyListener listener) {
        ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.get(url);
        if (listeners != null) {
            ChildListener zkListener = listeners.get(listener);
            if (zkListener != null) {
                zkClient.removeChildListener(toUrlPath(url), zkListener);
            }
        }
    }

    public List<URL> lookup(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("lookup url == null");
        }
        try {
            List<String> providers = new ArrayList<String>();
            for (String path : toCategoriesPath(url)) {
                List<String> children = zkClient.getChildren(path);
                if (children != null) {
                    providers.addAll(children);
                }
            }
            return toUrlsWithoutEmpty(url, providers);
        } catch (Throwable e) {
            throw new RpcException("Failed to lookup " + url + " from zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    /**
     * 转换至根路径root
     *  判断根路径是否等于"/"，如等于直接返回，不等于如"/dubbo"，则附加上分隔符，如"/dubbo/"
     *  转换的结果如： "/" 或 "/dubbo/"
     */
    private String toRootDir() { //获取根目录  root的值如 /
        if (root.equals(Constants.PATH_SEPARATOR)) {
            return root;
        }
        return root + Constants.PATH_SEPARATOR; //根目录： /dubbo/
    }

    /**
     * 转换至根路径
     */
    private String toRootPath() {
        return root;
    }

    /**
     * 将url转换至带有接口服务service中的path
     * 1）获取服务接口名
     * 2）若接口名是ANY_VALUE = "*"，返回根目录
     *    若不是：则接口服务path为 根路径 + 接口名的编码
     * 转化的接口如：/dubbo/com.alibaba.dubbo.demo.ApiDemo
     */
    private String toServicePath(URL url) { //服务路径： 根目录 + 接口名编码
        String name = url.getServiceInterface();
        if (Constants.ANY_VALUE.equals(name)) {
            return toRootPath();
        }
        return toRootDir() + URL.encode(name); //如 /dubbo/com.alibaba.dubbo.demo.ApiDemo
    }

    private String[] toCategoriesPath(URL url) { //获取分类对应的路径
        String[] categroies;
        if (Constants.ANY_VALUE.equals(url.getParameter(Constants.CATEGORY_KEY))) { //若分类设置为任意分类，则包含providers、consumers、routers、configurators
            categroies = new String[]{Constants.PROVIDERS_CATEGORY, Constants.CONSUMERS_CATEGORY,
                    Constants.ROUTERS_CATEGORY, Constants.CONFIGURATORS_CATEGORY};
        } else { //获取url设置的一个分类参数，默认providers
            categroies = url.getParameter(Constants.CATEGORY_KEY, new String[]{Constants.DEFAULT_CATEGORY});
        }
        /**
         * 路径如：
         * path[0] = "/dubbo/com.alibaba.dubbo.demo.ApiDemo/providers";
         * path[1] = "/dubbo/com.alibaba.dubbo.demo.ApiDemo/configurators";
         */
        String[] paths = new String[categroies.length];
        for (int i = 0; i < categroies.length; i++) {
            paths[i] = toServicePath(url) + Constants.PATH_SEPARATOR + categroies[i];
        }
        return paths;
    }

    /**
     * 将url转换至带有分类category中的path
     * 1）转换至服务path，如/dubbo/com.alibaba.dubbo.demo.ApiDemo（根目录 + 接口名）
     * 2）拼接路径分隔符 "/"
     * 3）从url获取分类，并拼接，默认为providers，分类包含consumers, configurators, routers, providers
     * 最后拼接的带有分类的路径如：/dubbo/com.alibaba.dubbo.demo.ApiDemo/providers
     */
    private String toCategoryPath(URL url) {
        return toServicePath(url) + Constants.PATH_SEPARATOR + url.getParameter(Constants.CATEGORY_KEY, Constants.DEFAULT_CATEGORY); //默认分类providers
    }

    /**
     * 将url转换至url中的path
     */
    private String toUrlPath(URL url) {
        return toCategoryPath(url) + Constants.PATH_SEPARATOR + URL.encode(url.toFullString()); //返回值，如：/dubbo/com.alibaba.dubbo.demo.ApiDemo/providers/dubbo%3A%2F%2F10.118.32.189%3A20881%2Fcom.alibaba.dubbo.demo.ApiDemo...
    }

    private List<URL> toUrlsWithoutEmpty(URL consumer, List<String> providers) {
        List<URL> urls = new ArrayList<URL>();
        if (providers != null && providers.size() > 0) { //遍历providers列表，判断consumer是否与provider相等，若相等则添加到url列表
            for (String provider : providers) {
                provider = URL.decode(provider);
                if (provider.contains("://")) {
                    URL url = URL.valueOf(provider);
                    if (UrlUtils.isMatch(consumer, url)) {
                        urls.add(url);
                    }
                }
            }
        }
        return urls;
    }

    private List<URL> toUrlsWithEmpty(URL consumer, String path, List<String> providers) { //将url置为空协议empty
        List<URL> urls = toUrlsWithoutEmpty(consumer, providers);
        if (urls == null || urls.isEmpty()) {
            int i = path.lastIndexOf('/');
            String category = i < 0 ? path : path.substring(i + 1); //todo @csy-h1 empty协议的用途？
            URL empty = consumer.setProtocol(Constants.EMPTY_PROTOCOL).addParameter(Constants.CATEGORY_KEY, category);
            urls.add(empty);
        }
        return urls;
    }

}