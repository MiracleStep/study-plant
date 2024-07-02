package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;

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

    /**
     * 分页查询互动问题-用户端
     * @param query
     * @return
     */
    PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query);

    /**
     * 查询问题详情-用户端
     * @param id
     * @return
     */
    QuestionVO queryQuestionById(Long id);
}
