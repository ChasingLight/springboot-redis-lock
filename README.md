##SpringBoot+Redission分布式锁实战
1.基于本地Redis-单机架构模式


2.使用【Nginx负载均衡-upstream、SpringBoot并行运行、Jmeter压力测试、Redis中存放库存】


3.业务主入口：IndexController
下面有4个映射方法：

/test1：
压测展示-分布式锁问题：【多个进程客户端，并发扣除库存，导致重复扣除。】

/test2：SETNX+EX过期时间-加锁；
            * 业务代码执行；
            * 释放锁。

/test3：利用UUID来解决，可能释放别人的锁问题。

/test4：Redission完美解决分布式锁。


4.核心参考链接：

【Redission分布式锁实战】https://www.cnblogs.com/jerry0612/p/14510831.html