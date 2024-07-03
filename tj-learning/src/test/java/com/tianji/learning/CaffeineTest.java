package com.tianji.learning;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tianji.api.cache.CategoryCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class CaffeineTest {

    @Autowired
    private CategoryCache categoryCache;

    @Test
    void testBasicOps() {
        // 构建cache对象
        Cache<String, String> cache = Caffeine.newBuilder().build();

        // 存数据
        cache.put("gf", "迪丽热巴");

        // 取数据
        String gf = cache.getIfPresent("gf");
        System.out.println("gf = " + gf);

        // 取数据，包含两个参数：
        // 参数一：缓存的key
        // 参数二：Lambda表达式，表达式参数就是缓存的key，方法体是查询数据库的逻辑
        // 优先根据key查询JVM缓存，如果未命中，则执行参数二的Lambda表达式
        String defaultGF = cache.get("defaultGF", key -> {
            // 根据key去数据库查询数据
            return "柳岩";
        });
        System.out.println("defaultGF = " + defaultGF);
    }

    @Test
    public void test() {
        String categoryNames = categoryCache.getCategoryNames(List.of(1001L, 2002L, 3007L));
        System.out.println(categoryNames);
    }
}
