package com.tianji.remark.service;

import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 服务类
 * </p>
 *
 * @author mirac
 * @since 2024-07-04
 */
public interface ILikedRecordService extends IService<LikedRecord> {

    /**
     * 用户点赞或取消赞
     * @param dto
     */
    void addLikeRecord(LikeRecordFormDTO dto);

    /**
     * 批量查询点赞状态
     * @param bizIds
     * @return
     */
    Set<Long> getLikesStatusByBizIds(List<Long> bizIds);
}
