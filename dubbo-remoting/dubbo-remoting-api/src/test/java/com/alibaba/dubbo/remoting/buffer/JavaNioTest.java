package com.alibaba.dubbo.remoting.buffer;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

/**
 * @author chensy
 * @date 2018/10/7
 * Dubbo 相关NIO知识
 */
public class JavaNioTest {
    public static void main(String[] args) {
       // test1();
        //test2();
        //test3();
        //test4();
        test5();
    }

    public static void  test1(){
        ByteBuffer buffer = ByteBuffer.allocate(10);
//        byte []arr = new byte[5];
//        for(int i=0;i<arr.length;i++){
//            arr[i] = (byte)i;
//        }
        //history-h2 为什么此处debug position为4
        System.out.println(buffer.position());
//        buffer.put(arr);
        buffer.put((byte)1);
        buffer.put((byte)2);

        ByteBuffer data = buffer.duplicate();
        data.limit(0).position();
        //data.put(src, srcIndex, length);
    }

    public static void test2(){
        CharBuffer charbuffer1 = CharBuffer.allocate(10);
        //duplicate浅拷贝，共享数据元素
        //新缓冲区的内容将是这个缓冲区的内容。 对这个缓冲区内容的更改将在新的缓冲区中可见，反之亦然
        CharBuffer charbuffer2 = charbuffer1.duplicate();

        charbuffer1.put('a').put('b').put('c');
        charbuffer1.flip();
        //charbuffer2.flip();

        //charbuffer2.put(3,'e').put(4,'f'); 按绝对位置设置，charbuffer1没改变，位置是独立的
        charbuffer2.put('g');
        //history-h2 为什么charbuffer2输出乱码
        //abc
        //abc       
        System.out.println(charbuffer1);

        System.out.println(charbuffer2);
    }

    public static void  test3(){
        CharBuffer charbuffer1 = CharBuffer.allocate(10);
        charbuffer1.position(2).limit(5);
        //slice方法其实是用于分割缓存区的，该方法创建了一个从原始缓冲区的当前位置开始的新缓冲区，
        // 并且其容量是原始缓冲区的剩余元素数量（limit-position）；
        //该缓存区与原始缓存区共享一段序列;就是新的缓冲区开始位置指向老的当前位置，容量是老的剩余数量
        CharBuffer charbuffer2 = charbuffer1.slice();
        charbuffer2.put('2');
        charbuffer2.put('5');
    }

    public static void test4(){
        ByteBuffer buffer = ByteBuffer.allocate(50);
        String value = "Netty权威指南";
        buffer.put(value.getBytes());
        //flip翻转缓冲区，将limit设置为position，position设置为0
        //get读取的内容是从position到limit的内容
        buffer.flip();//如果不使用flip，将读到不正确内容
        byte [] vArray = new byte[buffer.remaining()];
        buffer.get(vArray);
        String decodeValue = new String(vArray);
        System.out.println(decodeValue);
    }


    //history-h3 测试slice与duplicate，比较异同
    //history-h3 很神奇，这是不同的对象，是怎样实现内容共享的
    private static void test5(){
        ByteBuffer old = ByteBuffer.allocate(10);
        old.put((byte) 'a');
        old.put((byte) 'b');
        old.put((byte) 'c');

        //slice与duplicate 都能实现内容共享、只是下标索引处理不一样
        ByteBuffer newObj = old.slice();
        ByteBuffer newObj2 = old.duplicate();

        //共享内容，不管复制的对象和被复制的对象都会改变
        newObj.put((byte)'g');

        newObj2.put((byte)'h');

    }
}
