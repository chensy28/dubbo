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

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.utils.NetUtils;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Thread local context. (API, ThreadLocal, ThreadSafe)
 * <p>
 * 注意：RpcContext是一个临时状态记录器，当接收到RPC请求，或发起RPC请求时，RpcContext的状态都会变化。
 * 比如：A调B，B再调C，则B机器上，在B调C之前，RpcContext记录的是A调B的信息，在B调C之后，RpcContext记录的是B调C的信息。
 *
 * 上下文：会记住调用信息，如invoker、methodName等，还会记录localAddress、remoteAddress调用地址信息等
 *
 * @author qian.lei
 * @author william.liangf
 * @export
 * @see com.alibaba.dubbo.rpc.filter.ContextFilter
 */
public class RpcContext {  //history 10/01 是在何处设置进入的，结合官网流程图看下

    /**
     * ThreadLocal学习实践：不同线程中维护的变量互不干扰，同一个线程中作为线程上下文，不同方法中可以引用
     */
    private static final ThreadLocal<RpcContext> LOCAL = new ThreadLocal<RpcContext>() { // 10/29 ThreadLocal源码查看
        @Override
        protected RpcContext initialValue() { //初始化值对象值，若不重新，默认返回null
            return new RpcContext();
        }
    };
    private final Map<String, String> attachments = new HashMap<String, String>();  //附加参数
    private final Map<String, Object> values = new HashMap<String, Object>(); //用途及含义：存储上下文的值，键值可自定义
    /**
     * Future学习实践，Runnable不能返回执行接口，用Future获取返回结果
     * Future represents the result of an asynchronous computation（Future表示一个异步计算的结果）
     * 能够中断线程cancel(..)、以及判断线程是否取消isCancelled()等
     */
    private Future<?> future;

    private List<URL> urls;

    private URL url;

    private String methodName; //从调用信息Invocation中获取到的

    private Class<?>[] parameterTypes;

    private Object[] arguments;

    /**
     * InetSocketAddress学习实践
     * 此类实现IP套接字地址（IP地址 + 端口号）。它还可以是一个对（主机名 + 端口号），在此情况下，将尝试解析主机名。
     */
    private InetSocketAddress localAddress;

    private InetSocketAddress remoteAddress;
    @Deprecated
    private List<Invoker<?>> invokers;
    @Deprecated
    private Invoker<?> invoker; //通过ContextFilter设置
    @Deprecated
    private Invocation invocation; //通过ContextFilter设置

    protected RpcContext() { //没有公有的构造函数
    }

    /**
     * get context.
     *
     * @return context
     */
    public static RpcContext getContext() { //从ThreadLocal获取当前线程维护的对象值
        return LOCAL.get();
    }

    /**
     * remove context.（移除上下文信息）
     *
     * @see com.alibaba.dubbo.rpc.filter.ContextFilter
     */
    public static void removeContext() {
        LOCAL.remove();
    }

    /**
     * is provider side.
     * 怎样根据IP地址判断提供方还是消费方？
     * 将URL中的远端port、ip与RpcContext中存储内容进行比较，若都相等则为消费端，不相等则为提供端
     * （站在消费者角度进行比较，将消费者的远程地址与url进行比较）
     *
     * @return provider side.
     */
    public boolean isProviderSide() {
        URL url = getUrl();
        if (url == null) {
            return false;
        }
        InetSocketAddress address = getRemoteAddress();
        if (address == null) {
            return false;
        }
        String host;
        if (address.getAddress() == null) {
            host = address.getHostName();
        } else {
            host = address.getAddress().getHostAddress();
        }
        return url.getPort() != address.getPort() || //当前url的port、ip与remote的地址信息不相同，即为服务端
                !NetUtils.filterLocalHost(url.getIp()).equals(NetUtils.filterLocalHost(host));
    }

    /**
     * is consumer side.（是否是消费者）
     *
     * @return consumer side.
     */
    public boolean isConsumerSide() {
        URL url = getUrl();
        if (url == null) {
            return false;
        }
        InetSocketAddress address = getRemoteAddress();
        if (address == null) {
            return false;
        }
        String host;
        if (address.getAddress() == null) {
            host = address.getHostName();
        } else {
            host = address.getAddress().getHostAddress();
        }
        return url.getPort() == address.getPort() && //当前url的port、ip与remote的地址信息相同，即为消费端
                NetUtils.filterLocalHost(url.getIp()).equals(NetUtils.filterLocalHost(host));
    }

    /**
     * get future.
     *
     * @param <T>
     * @return future
     */
    @SuppressWarnings("unchecked")
    public <T> Future<T> getFuture() {
        return (Future<T>) future;
    }

    /**
     * set future.
     *
     * @param future
     */
    public void setFuture(Future<?> future) {
        this.future = future;
    }

    public List<URL> getUrls() {
        return urls == null && url != null ? (List<URL>) Arrays.asList(url) : urls;
    }

    public void setUrls(List<URL> urls) {
        this.urls = urls;
    }

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    /**
     * get method name.
     *
     * @return method name.
     */
    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    /**
     * get parameter types.
     *
     * @serial
     */
    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(Class<?>[] parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    /**
     * get arguments.
     *
     * @return arguments.
     */
    public Object[] getArguments() {
        return arguments;
    }

    public void setArguments(Object[] arguments) {
        this.arguments = arguments;
    }

    /**
     * set local address.
     *
     * @param host
     * @param port
     * @return context
     */
    public RpcContext setLocalAddress(String host, int port) {
        if (port < 0) {
            port = 0;
        }
        this.localAddress = InetSocketAddress.createUnresolved(host, port); //根据主机名和端口号创建未解析的套接字地址（创建未解析的地址）
        return this;
    }

    /**
     * get local address.
     *
     * @return local address
     */
    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    /**
     * set local address.
     *
     * @param address
     * @return context
     */
    public RpcContext setLocalAddress(InetSocketAddress address) {
        this.localAddress = address;
        return this;
    }

    public String getLocalAddressString() {
        return getLocalHost() + ":" + getLocalPort();
    }

    /**
     * get local host name.
     *
     * @return local host name
     */
    public String getLocalHostName() {
        String host = localAddress == null ? null : localAddress.getHostName();
        if (host == null || host.length() == 0) {
            return getLocalHost();
        }
        return host;
    }

    /**
     * set remote address.
     *
     * @param host
     * @param port
     * @return context
     */
    public RpcContext setRemoteAddress(String host, int port) {
        if (port < 0) {
            port = 0;
        }
        //创建未解析的套接字地址，不会尝试将主机名解析为InetAddress
        this.remoteAddress = InetSocketAddress.createUnresolved(host, port);
        return this;
    }

    /**
     * get remote address.
     *
     * @return remote address
     */
    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    /**
     * set remote address.
     *
     * @param address
     * @return context
     */
    public RpcContext setRemoteAddress(InetSocketAddress address) {
        this.remoteAddress = address;
        return this;
    }

    /**
     * get remote address string.
     *
     * @return remote address string.
     */
    public String getRemoteAddressString() {
        return getRemoteHost() + ":" + getRemotePort();
    }

    /**
     * get remote host name.
     *
     * @return remote host name
     */
    public String getRemoteHostName() {
        return remoteAddress == null ? null : remoteAddress.getHostName();
    }

    /**
     * get local host.
     *
     * @return local host
     */
    public String getLocalHost() {
        String host = localAddress == null ? null :
                localAddress.getAddress() == null ? localAddress.getHostName()
                        : NetUtils.filterLocalHost(localAddress.getAddress().getHostAddress());
        if (host == null || host.length() == 0) {
            return NetUtils.getLocalHost();
        }
        return host;
    }

    /**
     * get local port.
     *
     * @return port
     */
    public int getLocalPort() {
        return localAddress == null ? 0 : localAddress.getPort();
    }

    /**
     * get remote host.
     *
     * @return remote host
     */
    public String getRemoteHost() {
        return remoteAddress == null ? null :
                remoteAddress.getAddress() == null ? remoteAddress.getHostName()
                        : NetUtils.filterLocalHost(remoteAddress.getAddress().getHostAddress());
    }

    /**
     * get remote port.
     *
     * @return remote port
     */
    public int getRemotePort() {
        return remoteAddress == null ? 0 : remoteAddress.getPort();
    }

    /**
     * get attachment.
     *
     * @param key
     * @return attachment
     */
    public String getAttachment(String key) {
        return attachments.get(key);
    }

    /**
     * set attachment.（设置附加参数）
     *
     * @param key
     * @param value
     * @return context
     */
    public RpcContext setAttachment(String key, String value) { //在设置的时候返回变更后的对象，即可对值设置，也可以通过返回对象进行下一次设置，可连续设置值
        if (value == null) { //确保附加参数的值不为空
            attachments.remove(key);
        } else {
            attachments.put(key, value);
        }
        return this; //返回当前对象，  此种方式和平常set方式不一样
    }

    /**
     * remove attachment.
     *
     * @param key
     * @return context
     */
    public RpcContext removeAttachment(String key) {
        attachments.remove(key);
        return this;
    }

    /**
     * get attachments.
     *
     * @return attachments
     */
    public Map<String, String> getAttachments() {
        return attachments;
    }

    /**
     * set attachments
     *
     * @param attachment
     * @return context
     */
    public RpcContext setAttachments(Map<String, String> attachment) {
        this.attachments.clear();
        if (attachment != null && attachment.size() > 0) {
            this.attachments.putAll(attachment);
        }
        return this;
    }

    public void clearAttachments() {
        this.attachments.clear();
    }

    /**
     * get values.
     *
     * @return values
     */
    public Map<String, Object> get() {
        return values;
    }

    /**
     * set value.
     *
     * @param key
     * @param value
     * @return context
     */
    public RpcContext set(String key, Object value) {
        if (value == null) { // 设置值时判断value是否为空，若为空则移除，否则添加key、value
            values.remove(key);
        } else {
            values.put(key, value);
        }
        return this;
    }

    /**
     * remove value.
     *
     * @param key
     * @return value
     */
    public RpcContext remove(String key) {
        values.remove(key);
        return this;
    }

    /**
     * get value.
     *
     * @param key
     * @return value
     */
    public Object get(String key) {
        return values.get(key);
    }

    /**
     * @deprecated Replace to isProviderSide()
     */
    @Deprecated
    public boolean isServerSide() {
        return isProviderSide();
    }

    /**
     * @deprecated Replace to isConsumerSide()
     */
    @Deprecated
    public boolean isClientSide() {
        return isConsumerSide();
    }

    /**
     * @deprecated Replace to getUrls()
     */
    @Deprecated
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Invoker<?>> getInvokers() { //invokers、invoker是否为空，不为空则选择对应的值
        return invokers == null && invoker != null ? (List) Arrays.asList(invoker) : invokers;
    }

    public RpcContext setInvokers(List<Invoker<?>> invokers) { //设置invokers以及invoker中的url列表
        this.invokers = invokers;
        if (invokers != null && invokers.size() > 0) {
            List<URL> urls = new ArrayList<URL>(invokers.size());
            for (Invoker<?> invoker : invokers) {
                urls.add(invoker.getUrl());
            }
            setUrls(urls);
        }
        return this;
    }

    /**
     * @deprecated Replace to getUrl()
     */
    @Deprecated
    public Invoker<?> getInvoker() {
        return invoker;
    }

    public RpcContext setInvoker(Invoker<?> invoker) {
        this.invoker = invoker;
        if (invoker != null) {
            setUrl(invoker.getUrl());
        }
        return this;
    }

    /**
     * @deprecated Replace to getMethodName(), getParameterTypes(), getArguments()
     */
    @Deprecated
    public Invocation getInvocation() {
        return invocation;
    }

    public RpcContext setInvocation(Invocation invocation) {
        this.invocation = invocation;
        if (invocation != null) { //设置调用信息：方法名、参数类型、参数值
            setMethodName(invocation.getMethodName());
            setParameterTypes(invocation.getParameterTypes());
            setArguments(invocation.getArguments());
        }
        return this;
    }

    /**
     * 异步调用 ，需要返回值，即使步调用Future.get方法，也会处理调用超时问题.
     *  10/29 与FutureFilter中的asyncCallback有啥区别？
     * 回调与异步调用的区别， 目前只看到测试用例中有引用，通常的异步调用不用这个吗？
     *
     * @param callable
     * @return 通过future.get()获取返回结果.
     */
    @SuppressWarnings("unchecked")
    public <T> Future<T> asyncCall(Callable<T> callable) { //异步调用，并返回结果   9.1 上下文中的异步调用
        try {
            try {
                setAttachment(Constants.ASYNC_KEY, Boolean.TRUE.toString());
                final T o = callable.call();
                //local调用会直接返回结果.
                if (o != null) {
                    FutureTask<T> f = new FutureTask<T>(new Callable<T>() { //执行任务  10/29 FutureTask、Callable、Runnable了解&实践
                        public T call() throws Exception {
                            return o; // 返回结果
                        }
                    });
                    f.run();
                    return f;
                } else {

                }
            } catch (Exception e) {
                throw new RpcException(e);
            } finally {
                removeAttachment(Constants.ASYNC_KEY);
            }
        } catch (final RpcException e) {
            return new Future<T>() { //使用向上转型
                public boolean cancel(boolean mayInterruptIfRunning) {
                    return false;
                }

                public boolean isCancelled() {
                    return false;
                }

                public boolean isDone() {
                    return true;
                }

                public T get() throws InterruptedException, ExecutionException {
                    throw new ExecutionException(e.getCause());
                }

                public T get(long timeout, TimeUnit unit)
                        throws InterruptedException, ExecutionException,
                        TimeoutException {
                    return get();
                }
            };
        }
        return ((Future<T>) getContext().getFuture());
    }

    /**
     * oneway调用，只发送请求，不接收返回结果.
     *
     */
    public void asyncCall(Runnable runable) {
        try {
            setAttachment(Constants.RETURN_KEY, Boolean.FALSE.toString());
            runable.run();
        } catch (Throwable e) {
            //old system 异常是否应该放在future中？
            throw new RpcException("oneway call error ." + e.getMessage(), e);
        } finally {
            removeAttachment(Constants.RETURN_KEY);
        }
    }
}