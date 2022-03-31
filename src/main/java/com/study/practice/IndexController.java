package com.study.practice;

import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RestController
public class IndexController {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private Redisson redisson;

    /**
     * 压测展示-分布式锁问题：【多个进程客户端，并发扣除库存，导致重复扣除。】
     */
    @GetMapping("/test1")
    public void test1() {
        synchronized (this) {
            reduceStock();
        }
    }


    /**
     * SETNX+EX过期时间-加锁；
     * 业务代码执行；
     * 释放锁。
     *
     * 问题点：可能释放别人加的锁，并发锁失效。
     * Jmeter压力测试 + 锁过期时间3s + 核心业务代码随机等待[0,6]秒
     * 结果重复扣除同一个库存：
     * 客户端1：扣减成功，剩余库存：38、35、33
     * 客户端2：扣减成功，剩余库存：38、35、33、
     *
     */
    @GetMapping("/test2")
    public void test2() {

        //synchronized保证同一个应用，线程之间的并发正确性
        synchronized (this) {
            String stockKey = "product_1001";
            try {
                // 1.不存在key则设置，存在直接返回
                Boolean result = redisTemplate.opsForValue().setIfAbsent(stockKey, "1",3,TimeUnit.SECONDS);

                if (!result) {
                    System.out.println("---未争抢到分布式锁---");
                    return;
                }
                //2.获取到锁
                System.out.println("---拿到分布式锁---");
                reduceStock();

            } finally {
                //3.删除key（释放锁）
                redisTemplate.delete(stockKey);
            }
        }
    }


    /**
     * 利用UUID来解决，可能释放别人的锁问题。
     * Jmeter压力测试 + 锁过期时间3s + 核心业务代码随机等待[0,6]秒
     * 控制台结果：没有再出现重复扣除同一库存的问题了。结果正常！
     *
     * 新问题：
     * 1.锁过期还未彻底解决；
     * 2.Redis主从切换，锁丢失。
     */
    @GetMapping("/test3")
    public void test3() {

        //synchronized保证同一个应用，线程之间的并发正确性
        synchronized (this) {
            String stockKey = "product_1001";   //分布式锁粒度：某一件具体商品库存
            String requestUUID = UUID.randomUUID().toString();

            try {
                // 1.不存在key则设置，存在直接返回
                Boolean result = redisTemplate.opsForValue().setIfAbsent(stockKey, requestUUID,3,TimeUnit.SECONDS);

                if (!result) {
                    System.out.println("---未争抢到分布式锁---");
                    return;
                }
                //2.获取到锁
                System.out.println("---拿到分布式锁---");
                reduceStock();

            } finally {
                //3.删除key（释放锁）---根据UUID，只有锁是自己的才去释放。
                if (requestUUID.equals(redisTemplate.opsForValue().get(stockKey))){
                    redisTemplate.delete(stockKey);
                }
            }
        }
    }

    /**
     * 解决问题：
     * 1. lock.lock();  守护线程自动续期，保证可以业务代码正常执行完毕。
     * 2. 提供了Redis多种模式【单例(本代码使用单例)、主从-哨兵、集群】下的，分布式锁
     */
    @GetMapping("/test4")
    public void test4() throws Exception {
        String stockKey = "product_1001";
        RLock lock = redisson.getLock(stockKey);  //默认30秒自动过期，释放锁

        //Way1: 最常见的使用方法如下
        /*try {
            // 加锁
            lock.lock();
            // 执行业务逻辑代码
            reduceStock();
        } finally {
            // 释放锁
            lock.unlock();
        }*/


        //Way2：异步-尝试-加锁：最多等待5秒，上锁以后3秒自动解锁
        //已验证：锁的租赁时间是3s，如果执行时间超过3秒【会把剩余代码执行完毕】
        // 即：锁的租赁时间设置，并不会影响分布式业务安全性！---是否租赁时间设置没太大用处？
        Future<Boolean> res = lock.tryLockAsync(5, 3, TimeUnit.SECONDS);
        if (res.get()){
            try{
                // 执行业务代码
                reduceStock();
            }finally {
                // 释放锁
                lock.unlock();
            }
        }else{
            System.out.println("---tryLockAsync-等待10秒钟还未获得分布式锁，直接返回---");
        }
    }



    /**
     * 扣减库存-核心业务代码
     *
     */
    private void reduceStock() {

        //模拟真实环境，随机的业务执行时间---当前线程随机睡眠[0,6]秒时间
        Random random = new Random();
        int sleepSeconds = random.nextInt(6) * 1000;
        try {
            Thread.sleep(sleepSeconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        int stock = Integer.parseInt(redisTemplate.opsForValue().get("stock"));
        if (stock > 0) {
            int realStock = stock - 1;
            // 提前将stock的值设置到redis，redis客户端执行 set stock 50
            redisTemplate.opsForValue().set("stock", String.valueOf(realStock));
            System.out.println("扣减成功，剩余库存：" + realStock);
        } else {
            System.out.println("扣减失败，库存不足");
        }
    }

}

