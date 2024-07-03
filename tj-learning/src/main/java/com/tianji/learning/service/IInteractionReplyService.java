package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionReply;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;

/**
 * <p>
 * 互动问题的回答或评论 服务类
 * </p>
 *
 * @author mirac
 * @since 2024-07-01
 */
public interface IInteractionReplyService extends IService<InteractionReply> {

    /**
     * 新增回答或评论
     * @param dto
     */
    void saveReply(ReplyDTO dto);

    /**
     * 分页查询回答或评论
     * @param pageQuery
     * @param b
     * @return
     */
    PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery pageQuery, boolean b);
}
