import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class GenerationUserInfo {
    @Test
    public void generationUserInfo() throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream("./user.txt");
        for (int userInfo = 0; userInfo < 2000; userInfo++) {
            fileOutputStream.write((userInfo + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    public void generationSnowflakeId() throws IOException {
        Snowflake snowflake = IdUtil.getSnowflake(1, 1);
        System.out.println(snowflake.nextId());
    }
}
