package com.tianji.learning;

import org.junit.jupiter.api.Test;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ExecutorTest {

    @Test
    public void executors() {
        ThreadPoolExecutor poolExecutor = new ThreadPoolExecutor(4,
                4,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(10));
        poolExecutor.submit(new Runnable() {
            @Override
            public void run() {
                System.out.println("aaa");
            }
        });
    }
}
