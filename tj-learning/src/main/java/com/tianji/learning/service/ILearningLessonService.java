package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;

import java.util.List;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author mirac
 * @since 2024-06-24
 */
public interface ILearningLessonService extends IService<LearningLesson> {
    /**
     * 添加用户课程
     */
    void addUserLesson(Long userId, List<Long> courseIds);

    /**
     * 查询我的课程
     * @param query
     * @return
     */
    PageDTO<LearningLessonVO> queryMyLessons(PageQuery query);

    /**
     * 查询正在学习的课程
     * @return
     */
    LearningLessonVO queryMyCurrentLesson();

    /**
     * 查询课程是否有效
     * @param courseId
     * @return
     */
    Long isLessonValid(Long courseId);

    /**
     * 查询用户课表中指定课程状态
     * @param courseId
     * @return
     */
    LearningLessonVO queryLearningRecordByCourse(Long courseId);

    /**
     * 创建学习计划
     * @param dto
     */
    void createLearningPlan(LearningPlanDTO dto);

    /**
     * 查询学习计划
     * @param query
     * @return
     */
    LearningPlanPageVO queryMyPlans(PageQuery query);
}
