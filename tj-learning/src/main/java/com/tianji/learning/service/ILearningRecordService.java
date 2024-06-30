package com.tianji.learning.service;

import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningRecord;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 学习记录表 服务类
 * </p>
 *
 * @author mirac
 * @since 2024-06-27
 */
public interface ILearningRecordService extends IService<LearningRecord> {

    /**
     * 根据课程id查询学习记录
     * @param courseId
     * @return
     */
    LearningLessonDTO queryLearningRecordByCourse(Long courseId);

    /**
     * 提交学习记录
     * @param dto
     */
    void addLearningRecord(LearningRecordFormDTO dto);


    /**
     * 提交学习记录V2
     * @param dto
     */
    void addLearningRecordV2(LearningRecordFormDTO dto);
}
