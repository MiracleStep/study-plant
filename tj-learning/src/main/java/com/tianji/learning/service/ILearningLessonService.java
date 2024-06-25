package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.LearningLessonVO;

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
}
