package com.tianji.learning.service;

import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 互动提问的问题表 服务类
 * </p>
 *
 * @author mirac
 * @since 2024-07-01
 */
public interface IInteractionQuestionService extends IService<InteractionQuestion> {

    /**
     * 新增互动问题
     * @param dto
     */
    void saveQuestion(QuestionFormDTO dto);

    /**
     * 修改互动问题
     * @param id
     * @param dto
     */
    void updateQuestion(Long id, QuestionFormDTO dto);
}
