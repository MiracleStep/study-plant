package com.tianji.learning.mq;

import com.tianji.api.dto.msg.LikedTimesDTO;
import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.service.IInteractionReplyService;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor//使用构造器 Lombook是在编译器生成相应的方法
public class LikedRecordListener {

    private final IInteractionReplyService replyService;

    /**
     * QA问答系统 消费者
     * @param dto
     */
    @RabbitListener(bindings = @QueueBinding(value = @Queue(value = "qa.liked.times.queue", durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.LIKE_RECORD_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.QA_LIKED_TIMES_KEY))
    public void onMsg(LikedTimesDTO dto){
        log.debug("LikedRecordListener 监听到消息 {}", dto);
        InteractionReply reply = replyService.getById(dto.getBizId());
        if (reply == null) {
            return;
        }
        reply.setLikedTimes(dto.getLikedTimes());
        replyService.updateById(reply);
    }
}
