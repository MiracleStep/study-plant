package com.tianji.api.dto.msg;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统计某业务id下总的点赞数量  用于同步给业务方
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class LikedTimesDTO {
    /**
     * 点赞的业务id
     */
    private Long bizId;
    /**
     * 总的点赞次数
     */
    private Integer likedTimes;
}
